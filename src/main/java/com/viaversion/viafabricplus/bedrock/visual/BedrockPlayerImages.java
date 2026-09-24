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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;

/** Draws the player's Xbox game picture, with a Minecraft head while it loads or is unavailable. */
public final class BedrockPlayerImages {

    private static final Map<String, String> PICTURES = new ConcurrentHashMap<>();

    private BedrockPlayerImages() {
    }

    public static void remember(final String xuid, final String pictureUrl) {
        if (!xuid.isBlank() && !pictureUrl.isBlank()) PICTURES.put(xuid, pictureUrl);
    }

    public static void draw(final GuiGraphicsExtractor graphics, final String xuid,
                            final int x, final int y, final int size) {
        if (BedrockImageCache.drawRemote(graphics, PICTURES.getOrDefault(xuid, ""), x, y, size, size)) return;
        final UUID skinId = UUID.nameUUIDFromBytes(xuid.getBytes(StandardCharsets.UTF_8));
        PlayerFaceExtractor.extractRenderState(graphics, DefaultPlayerSkin.get(skinId), x, y, size);
    }

}
