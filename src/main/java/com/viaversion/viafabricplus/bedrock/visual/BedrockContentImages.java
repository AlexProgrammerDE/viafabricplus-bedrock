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

import com.viaversion.viafabricplus.bedrock.profile.BedrockProfileService.Statistic;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Client-derived illustrations that explain Timeline or identify a statistic. */
public final class BedrockContentImages {

    private static final String RESOURCE_ROOT = "/assets/viafabricplus-bedrock/content/";

    private BedrockContentImages() {
    }

    public static void drawTimelineExample(final GuiGraphicsExtractor graphics,
                                           final int x, final int y, final int width, final int height) {
        BedrockImageCache.drawBundled(graphics, RESOURCE_ROOT + "timeline-opt-in.png", x, y, width, height);
    }

    public static void drawStatisticIcon(final GuiGraphicsExtractor graphics, final Statistic stat,
                                         final int x, final int y, final int size) {
        final String icon = switch (stat) {
            case MINUTES_PLAYED -> "timer";
            case BLOCKS_BROKEN -> "pickaxe";
            case MOBS_DEFEATED -> "sword";
            case DISTANCE_TRAVELED -> "boots";
        };
        BedrockImageCache.drawBundledSilhouette(graphics, RESOURCE_ROOT + icon + "@0.5x.icon.png",
            0xFFE0E0E0, x, y, size);
    }

}
