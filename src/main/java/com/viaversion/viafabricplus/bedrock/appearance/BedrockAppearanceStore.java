/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/ViaVersionAddons/viafabricplus-bedrock
 * Copyright (C) 2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.appearance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.function.Function;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.DataValues;

/** Stores the selected classic appearance separately for each Bedrock account. */
public final class BedrockAppearanceStore {

    private static final String METADATA = "appearance.json";
    private static final String CUSTOM_SKIN = "skin.png";
    private static final String CAPE = "cape.png";
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;

    private final Path root;
    private final Function<Selection, BufferedImage> presets;

    public BedrockAppearanceStore(final Path root) {
        this(root, selection -> BedrockProtocol.MAPPINGS.getBedrockSkinPacks().get(DataValues.VANILLA_SKIN_PACK_KEY)
            .content().getImage(selection == Selection.STEVE ? "steve.png" : "alex.png").getImage());
    }

    BedrockAppearanceStore(final Path root, final Function<Selection, BufferedImage> presets) {
        this.root = root;
        this.presets = presets;
    }

    public static String accountId(final BedrockAuthManager account) {
        if (account == null || !account.getMinecraftMultiplayerToken().hasValue()) {
            return "offline";
        }
        final String xuid = account.getMinecraftMultiplayerToken().getCached().getXuid();
        return xuid != null && xuid.matches("[0-9]+") ? xuid : "offline";
    }

    public synchronized Appearance load(final String accountId) throws IOException {
        final Path directory = this.directory(accountId);
        Selection selection = Selection.STEVE;
        boolean slim = false;
        String skinId = "";
        String capeId = "";
        final Path metadata = directory.resolve(METADATA);
        if (Files.isRegularFile(metadata)) {
            try {
                final JsonObject object = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
                selection = Selection.valueOf(object.get("selection").getAsString());
                slim = object.get("slim").getAsBoolean();
                skinId = object.get("skinId").getAsString();
                capeId = object.get("capeId").getAsString();
            } catch (RuntimeException e) {
                throw new IOException("The saved Bedrock appearance is invalid", e);
            }
        }

        BufferedImage skin = switch (selection) {
            case STEVE -> this.presets.apply(Selection.STEVE);
            case ALEX -> this.presets.apply(Selection.ALEX);
            case CUSTOM -> Files.isRegularFile(directory.resolve(CUSTOM_SKIN))
                ? readPng(directory.resolve(CUSTOM_SKIN), false) : null;
        };
        if (skin == null) {
            selection = Selection.STEVE;
            skin = this.presets.apply(Selection.STEVE);
        }
        final Path capePath = directory.resolve(CAPE);
        final BufferedImage cape = Files.isRegularFile(capePath) ? readPng(capePath, true) : null;
        return new Appearance(selection, slim,
            skin, cape, skinId.isBlank() ? UUID.randomUUID().toString() : skinId,
            cape != null && capeId.isBlank() ? UUID.randomUUID().toString() : capeId);
    }

    public boolean hasCustomSkin(final String accountId) {
        return Files.isRegularFile(this.directory(accountId).resolve(CUSTOM_SKIN));
    }

    public synchronized Appearance select(final String accountId, final Selection selection) throws IOException {
        final Appearance current = this.load(accountId);
        if (selection == Selection.CUSTOM && !Files.isRegularFile(this.directory(accountId).resolve(CUSTOM_SKIN))) {
            throw new IOException("Import a custom skin first");
        }
        final Appearance updated = new Appearance(selection, current.slim(), current.skin(), current.cape(),
            UUID.randomUUID().toString(), current.capeId());
        this.saveMetadata(accountId, updated);
        return this.load(accountId);
    }

    public synchronized Appearance setSlim(final String accountId, final boolean slim) throws IOException {
        final Appearance current = this.load(accountId);
        final Appearance updated = new Appearance(current.selection(), slim, current.skin(), current.cape(),
            UUID.randomUUID().toString(), current.capeId());
        this.saveMetadata(accountId, updated);
        return this.load(accountId);
    }

    public synchronized Appearance importSkin(final String accountId, final Path source) throws IOException {
        final BufferedImage image = readPng(source, false);
        final Appearance current = this.load(accountId);
        final Path directory = this.directory(accountId);
        Files.createDirectories(directory);
        writePng(image, directory.resolve(CUSTOM_SKIN));
        final Appearance updated = new Appearance(Selection.CUSTOM, current.slim(), image, current.cape(),
            UUID.randomUUID().toString(), current.capeId());
        this.saveMetadata(accountId, updated);
        return updated;
    }

    public synchronized Appearance importCape(final String accountId, final Path source) throws IOException {
        final BufferedImage image = readPng(source, true);
        final Appearance current = this.load(accountId);
        final Path directory = this.directory(accountId);
        Files.createDirectories(directory);
        writePng(image, directory.resolve(CAPE));
        final Appearance updated = new Appearance(current.selection(), current.slim(), current.skin(), image,
            current.skinId(), UUID.randomUUID().toString());
        this.saveMetadata(accountId, updated);
        return updated;
    }

    public synchronized Appearance removeCape(final String accountId) throws IOException {
        Files.deleteIfExists(this.directory(accountId).resolve(CAPE));
        final Appearance current = this.load(accountId);
        final Appearance updated = new Appearance(current.selection(), current.slim(), current.skin(), null,
            current.skinId(), "");
        this.saveMetadata(accountId, updated);
        return updated;
    }

    private Path directory(final String accountId) {
        return this.root.resolve(accountId != null && accountId.matches("[0-9]+") ? accountId : "offline");
    }

    private void saveMetadata(final String accountId, final Appearance appearance) throws IOException {
        final JsonObject object = new JsonObject();
        object.addProperty("selection", appearance.selection().name());
        object.addProperty("slim", appearance.slim());
        object.addProperty("skinId", appearance.skinId());
        object.addProperty("capeId", appearance.capeId());
        final Path directory = this.directory(accountId);
        Files.createDirectories(directory);
        final Path temporary = Files.createTempFile(directory, "appearance-", ".json");
        try {
            Files.writeString(temporary, object.toString(), StandardCharsets.UTF_8);
            moveIntoPlace(temporary, directory.resolve(METADATA));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static BufferedImage readPng(final Path path, final boolean cape) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_FILE_BYTES) {
            throw new IOException("The PNG file is missing or too large");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                throw new IOException("Could not read the PNG file");
            }
            final var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("The file is not a PNG image");
            }
            final ImageReader reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) {
                    throw new IOException("The file is not a PNG image");
                }
                reader.setInput(input);
                final int width = reader.getWidth(0);
                final int height = reader.getHeight(0);
                final boolean valid = cape ? width == height * 2 && height >= 32 && height <= 512
                    : width == height && width >= 64 && width <= 1024;
                if (!valid || Integer.bitCount(width) != 1) {
                    throw new IOException(cape ? "Capes must be 64x32 through 1024x512 PNGs"
                        : "Skins must be square 64x64 through 1024x1024 PNGs");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void writePng(final BufferedImage image, final Path destination) throws IOException {
        final Path temporary = Files.createTempFile(destination.getParent(), "appearance-", ".png");
        try {
            if (!ImageIO.write(image, "png", temporary.toFile())) {
                throw new IOException("Could not save the PNG image");
            }
            moveIntoPlace(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveIntoPlace(final Path source, final Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public enum Selection {
        STEVE, ALEX, CUSTOM
    }

    public record Appearance(Selection selection, boolean slim, BufferedImage skin, BufferedImage cape,
                             String skinId, String capeId) {

        public boolean effectiveSlim() {
            return this.selection == Selection.CUSTOM ? this.slim : this.selection == Selection.ALEX;
        }
    }

}
