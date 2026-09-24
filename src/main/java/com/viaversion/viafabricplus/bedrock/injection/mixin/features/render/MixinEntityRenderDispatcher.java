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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.bedrock.render.BedrockEntityRenderer;
import com.viaversion.viafabricplus.bedrock.render.BedrockPlayerSkins;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @WrapOperation(method = "onResourceManagerReload", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderers;createEntityRenderers(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;"))
    private Map<EntityType<?>, EntityRenderer<?, ?>> bedrockInteractionRenderer(
        final EntityRendererProvider.Context context,
        final Operation<Map<EntityType<?>, EntityRenderer<?, ?>>> original
    ) {
        final Map<EntityType<?>, EntityRenderer<?, ?>> renderers = new HashMap<>(original.call(context));
        renderers.put(EntityTypes.INTERACTION, new BedrockEntityRenderer(context));
        BedrockPlayerSkins.rendererContext(context);
        return renderers;
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void bedrockPlayerRenderer(final T entity, final CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        if (entity instanceof AbstractClientPlayer player) {
            final AvatarRenderer<?> renderer = BedrockPlayerSkins.renderer(player.getUUID());
            if (renderer != null) {
                cir.setReturnValue((EntityRenderer<? super T, ?>) renderer);
            }
        }
    }

    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;", at = @At("HEAD"), cancellable = true)
    private void bedrockPlayerRenderState(final AvatarRenderState state, final CallbackInfoReturnable<AvatarRenderer<?>> cir) {
        final AvatarRenderer<?> renderer = BedrockPlayerSkins.renderer(state.skin);
        if (renderer != null) {
            cir.setReturnValue(renderer);
        }
    }

}
