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

import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Renders Bedrock UI artwork packaged with the mod. */
public final class BedrockUiArt {

    private static final String RESOURCE_ROOT = "/assets/viafabricplus-bedrock/bedrock_ui/";
    private static final Set<String> EVENT_ART = Set.of(
        "250HostileMobs", "AllMobNameEasterEggs", "CookEverything", "DefeatEnderdragon",
        "DefeatWither", "DiamondEverything", "FirstAbandonedMineshaftFound",
        "FirstAncientCityFound", "FirstBadlandsFound", "FirstConduit",
        "FirstCraftedNetherite", "FirstDiamondFound", "FirstEnchantment",
        "FirstEndPortal", "FirstEnderDragonDefeated", "FirstFullyExploredMap",
        "FirstMushroomFieldFound", "FirstNetherFortressFound", "FirstNetherPortalLit",
        "FirstPeakMountainFound", "FirstPillagerOutpostFound", "FirstPoweredBeacon",
        "FirstWitherDefeated", "FirstWoodlandMansionFound", "NamedMob", "NamedMobDies",
        "NewMember", "PillagerCaptainDefeated", "RealmCreated", "Session"
    );

    private BedrockUiArt() {
    }

    public static boolean drawEvent(final GuiGraphicsExtractor graphics, final String event,
                                    final int x, final int y, final int width, final int height) {
        return EVENT_ART.contains(event) && draw(graphics, event, "png", x, y, width, height);
    }

    public static boolean drawRealmsLogo(final GuiGraphicsExtractor graphics,
                                         final int x, final int y, final int width, final int height) {
        return draw(graphics, "RealmsTitleImage", "png", x, y, width, height);
    }

    public static boolean drawRealmPreview(final GuiGraphicsExtractor graphics,
                                           final int x, final int y, final int width, final int height) {
        return draw(graphics, "Realms_Default_Thumbnail_3840", "jpg", x, y, width, height)
            || draw(graphics, "realms_default_image", "jpg", x, y, width, height);
    }

    public static boolean drawBackdrop(final GuiGraphicsExtractor graphics, final int width, final int height) {
        return draw(graphics, "background-main", "png", 0, 0, width, height);
    }

    public static boolean drawIcon(final GuiGraphicsExtractor graphics, final String asset,
                                   final int x, final int y, final int size) {
        return draw(graphics, asset, "png", x, y, size, size);
    }

    public static boolean drawTimelineExample(final GuiGraphicsExtractor graphics,
                                              final int x, final int y, final int width, final int height) {
        return draw(graphics, "timeline-opt-in", "png", x, y, width, height);
    }

    private static boolean draw(final GuiGraphicsExtractor graphics, final String asset, final String extension,
                                final int x, final int y, final int width, final int height) {
        return BedrockImageCache.drawBundled(graphics, RESOURCE_ROOT + asset + '.' + extension,
            x, y, width, height);
    }

}
