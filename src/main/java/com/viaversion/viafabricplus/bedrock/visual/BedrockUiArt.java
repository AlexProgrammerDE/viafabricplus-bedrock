/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viaversion.viafabricplus.bedrock.visual;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

/** Renders artwork from a locally installed Bedrock client without bundling its assets. */
public final class BedrockUiArt {

    private static final Pattern ASSET_NAME = Pattern.compile("(.+)-[a-f0-9]{16,}\\.(?:png|jpe?g)");
    private static final Map<String, Path> ART = findArt();

    private BedrockUiArt() {
    }

    public static boolean drawEvent(final GuiGraphicsExtractor graphics, final String event,
                                    final int x, final int y, final int width, final int height) {
        return draw(graphics, event, x, y, width, height);
    }

    public static boolean drawRealmsLogo(final GuiGraphicsExtractor graphics,
                                         final int x, final int y, final int width, final int height) {
        return draw(graphics, "RealmsTitleImage", x, y, width, height);
    }

    public static boolean drawRealmPreview(final GuiGraphicsExtractor graphics,
                                           final int x, final int y, final int width, final int height) {
        return draw(graphics, "Realms_Default_Thumbnail_3840", x, y, width, height)
            || draw(graphics, "realms_default_image", x, y, width, height);
    }

    public static boolean drawBackdrop(final GuiGraphicsExtractor graphics, final int width, final int height) {
        return draw(graphics, "background-main", 0, 0, width, height);
    }

    public static boolean drawIcon(final GuiGraphicsExtractor graphics, final String asset,
                                   final int x, final int y, final int size) {
        return draw(graphics, asset, x, y, size, size);
    }

    public static boolean drawTimelineExample(final GuiGraphicsExtractor graphics,
                                              final int x, final int y, final int width, final int height) {
        return draw(graphics, "timeline-opt-in", x, y, width, height);
    }

    private static boolean draw(final GuiGraphicsExtractor graphics, final String asset,
                                final int x, final int y, final int width, final int height) {
        final Path path = ART.get(asset);
        return path != null && BedrockImageCache.drawLocal(graphics, path, x, y, width, height);
    }

    private static Map<String, Path> findArt() {
        final Path assets = assetFolder();
        if (assets == null) return Map.of();
        try (Stream<Path> files = Files.list(assets)) {
            final Map<String, Path> art = new HashMap<>();
            files.filter(Files::isRegularFile).forEach(path -> {
                final Matcher name = ASSET_NAME.matcher(path.getFileName().toString());
                if (name.matches()) art.putIfAbsent(name.group(1), path);
            });
            return Map.copyOf(art);
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private static @Nullable Path assetFolder() {
        final String configured = System.getProperty("viafabricplus.bedrockUiAssets");
        if (configured != null && Files.isDirectory(Path.of(configured))) return Path.of(configured);
        final Path releases = Path.of(System.getProperty("user.home"),
            ".local", "share", "bedrock-on-linux", "games", "release");
        if (!Files.isDirectory(releases)) return null;
        try (Stream<Path> versions = Files.list(releases)) {
            return versions.filter(Files::isDirectory)
                .sorted(Comparator.comparing(Path::getFileName).reversed())
                .map(path -> path.resolve("data/gui/dist/hbui/assets"))
                .filter(Files::isDirectory).findFirst().orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

}
