/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
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

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.friends.BedrockFriendsService;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import net.raphimc.viabedrock.protocol.types.primitive.ImageType;
import net.raphimc.viabedrock.protocol.provider.SkinProvider;

/** Sends the selected appearance and any friend-world session nonce in Bedrock's client data. */
public final class ViaFabricPlusSkinProvider extends SkinProvider {

    @Override
    public Map<String, Object> getClientPlayerSkin(final UserConnection user) {
        final Map<String, Object> claims = super.getClientPlayerSkin(user);
        try {
            final var addon = ViaFabricPlusBedrock.impl();
            final String accountId = BedrockAppearanceStore.accountId(addon.account().get());
            final var appearance = addon.appearances().load(accountId);
            final var skin = appearance.skin();
            final boolean slim = appearance.effectiveSlim();
            claims.put("SkinId", appearance.skinId());
            claims.put("SkinData", Base64.getEncoder().encodeToString(ImageType.getImageData(skin)));
            claims.put("SkinImageWidth", skin.getWidth());
            claims.put("SkinImageHeight", skin.getHeight());
            claims.put("SkinResourcePatch", Base64.getEncoder().encodeToString(("{\"geometry\":{\"default\":\"geometry.humanoid.custom"
                + (slim ? "Slim" : "") + "\"}}").getBytes(StandardCharsets.UTF_8)));
            claims.put("ArmSize", slim ? "slim" : "wide");
            if (appearance.cape() != null) {
                claims.put("CapeId", appearance.capeId());
                claims.put("CapeData", Base64.getEncoder().encodeToString(ImageType.getImageData(appearance.cape())));
                claims.put("CapeImageWidth", appearance.cape().getWidth());
                claims.put("CapeImageHeight", appearance.cape().getHeight());
                claims.put("CapeOnClassicSkin", true);
            }
        } catch (IOException e) {
            ViaFabricPlusBedrock.impl().logger().warn("Could not load the selected Bedrock appearance; using Steve", e);
        }
        final String nonce = BedrockFriendsService.nonceFor(user.getChannel().remoteAddress());
        if (nonce != null) {
            claims.put("Nonce", nonce);
        }
        return claims;
    }

}
