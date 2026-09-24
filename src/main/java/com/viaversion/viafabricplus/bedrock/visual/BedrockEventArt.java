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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

/** Finds Realm event illustrations in an installed Bedrock client, without distributing its artwork. */
public final class BedrockEventArt {

    private static final Map<String, Path> ART = findArt();

    private BedrockEventArt() {
    }

    public static boolean draw(final GuiGraphicsExtractor graphics, final String event,
                               final int x, final int y, final int width, final int height) {
        final Path path = ART.get(event);
        return path != null && BedrockImageCache.drawLocal(graphics, path, x, y, width, height);
    }

    private static Map<String, Path> findArt() {
        final Path assets = assetFolder();
        if (assets == null) return Map.of();
        try (Stream<Path> files = Files.list(assets)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().matches("[A-Za-z][A-Za-z0-9]+-[a-f0-9]+\\.png"))
                .collect(Collectors.toMap(path -> path.getFileName().toString().split("-")[0],
                    path -> path, (first, second) -> first));
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
