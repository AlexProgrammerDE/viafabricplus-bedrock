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

package com.viaversion.viafabricplus.bedrock.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.libs.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.raphimc.viabedrock.protocol.model.SkinData;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;

/** Owns client textures and geometry for Bedrock player appearances. */
public final class BedrockPlayerSkins {

    private static final int MAX_TEXTURE_DIMENSION = 1024;
    private static final Map<UUID, Appearance> APPEARANCES = new HashMap<>();
    private static final Map<Identifier, UUID> PLAYER_BY_TEXTURE = new HashMap<>();
    private static volatile EntityRendererProvider.Context rendererContext;
    private static volatile long rendererEpoch;

    private BedrockPlayerSkins() {
    }

    public static void accept(final UserConnection connection, final UUID playerUuid, final SkinData skin) {
        if (skin == null || skin.skinData() == null || skin.persona() || !supportedImage(skin.skinData())) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            if (ViaFabricPlus.api().userConnection() != connection) {
                return;
            }
            final Appearance previous = APPEARANCES.remove(playerUuid);
            if (previous != null) {
                PLAYER_BY_TEXTURE.remove(previous.bodyId);
                release(previous);
            }
            final Identifier bodyId = textureId(playerUuid, "body");
            register(bodyId, skin.skinData());
            final Identifier capeId = supportedImage(skin.capeData()) ? textureId(playerUuid, "cape") : null;
            if (capeId != null) {
                register(capeId, skin.capeData());
            }
            final boolean slim = "slim".equalsIgnoreCase(skin.armSize()) || geometryName(skin).toLowerCase(Locale.ROOT).contains("slim");
            final PlayerModelType modelType = slim ? PlayerModelType.SLIM : PlayerModelType.WIDE;
            final PlayerSkin playerSkin = PlayerSkin.insecure(new ClientAsset.ResourceTexture(bodyId),
                capeId == null ? null : new ClientAsset.ResourceTexture(capeId), null, modelType);
            final BedrockGeometryModel geometry = geometry(skin);
            APPEARANCES.put(playerUuid, new Appearance(playerSkin, geometry, slim, bodyId, capeId));
            PLAYER_BY_TEXTURE.put(bodyId, playerUuid);
        });
    }

    public static PlayerSkin skin(final UUID playerUuid, final PlayerSkin fallback) {
        final Appearance appearance = APPEARANCES.get(playerUuid);
        return appearance == null ? fallback : appearance.skin;
    }

    public static AvatarRenderer<?> renderer(final UUID playerUuid) {
        final Appearance appearance = APPEARANCES.get(playerUuid);
        if (appearance == null || appearance.geometry == null || rendererContext == null) {
            return null;
        }
        if (appearance.renderer == null || appearance.rendererEpoch != rendererEpoch) {
            appearance.renderer = new BedrockPlayerRenderer(rendererContext, BedrockGeometry.playerModel(appearance.geometry, appearance.slim), appearance.slim);
            appearance.rendererEpoch = rendererEpoch;
        }
        return appearance.renderer;
    }

    public static AvatarRenderer<?> renderer(final PlayerSkin skin) {
        if (skin == null || skin.body() == null) {
            return null;
        }
        final UUID playerUuid = PLAYER_BY_TEXTURE.get(skin.body().id());
        return playerUuid == null ? null : renderer(playerUuid);
    }

    public static void rendererContext(final EntityRendererProvider.Context context) {
        rendererContext = context;
        rendererEpoch++;
    }

    public static void clear() {
        APPEARANCES.values().forEach(BedrockPlayerSkins::release);
        APPEARANCES.clear();
        PLAYER_BY_TEXTURE.clear();
    }

    private static void release(final Appearance appearance) {
        final var textureManager = Minecraft.getInstance().getTextureManager();
        textureManager.release(appearance.bodyId);
        if (appearance.capeId != null) {
            textureManager.release(appearance.capeId);
        }
    }

    private static Identifier textureId(final UUID playerUuid, final String kind) {
        return Identifier.fromNamespaceAndPath("viafabricplus-bedrock", "skins/" + playerUuid + '/' + kind);
    }

    private static boolean supportedImage(final BufferedImage image) {
        return image != null && image.getWidth() > 0 && image.getHeight() > 0
            && image.getWidth() <= MAX_TEXTURE_DIMENSION && image.getHeight() <= MAX_TEXTURE_DIMENSION;
    }

    private static void register(final Identifier id, final BufferedImage image) {
        final NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = image.getRGB(x, y);
                final int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >>> 16) & 0xFF);
                nativeImage.setPixelABGR(x, y, abgr);
            }
        }
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, nativeImage));
    }

    private static String geometryName(final SkinData skin) {
        try {
            return JsonParser.parseString(skin.skinResourcePatch()).getAsJsonObject()
                .getAsJsonObject("geometry").get("default").getAsString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static BedrockGeometryModel geometry(final SkinData skin) {
        if (skin.geometryData() == null || skin.geometryData().isBlank() || "null".equalsIgnoreCase(skin.geometryData())) {
            return null;
        }
        try {
            final JsonObject data = JsonParser.parseString(skin.geometryData()).getAsJsonObject();
            final List<BedrockGeometryModel> models = BedrockGeometryModel.fromJson(data);
            final String name = geometryName(skin);
            return models.stream().filter(model -> model.getIdentifier().equals(name)).findFirst()
                .orElseGet(() -> models.isEmpty() ? null : models.getFirst());
        } catch (RuntimeException e) {
            ViaFabricPlusBedrock.impl().logger().warn("Failed to parse Bedrock player geometry", e);
            return null;
        }
    }

    private static final class Appearance {
        private final PlayerSkin skin;
        private final BedrockGeometryModel geometry;
        private final boolean slim;
        private final Identifier bodyId;
        private final Identifier capeId;
        private AvatarRenderer<?> renderer;
        private long rendererEpoch;

        private Appearance(final PlayerSkin skin, final BedrockGeometryModel geometry, final boolean slim,
                           final Identifier bodyId, final Identifier capeId) {
            this.skin = skin;
            this.geometry = geometry;
            this.slim = slim;
            this.bodyId = bodyId;
            this.capeId = capeId;
        }
    }

}
