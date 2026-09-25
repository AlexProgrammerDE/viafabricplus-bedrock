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

import com.viaversion.viafabricplus.bedrock.render.CustomEntityRenderStore;
import java.util.List;
import net.raphimc.viabedrock.api.model.entity.CustomEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CustomEntity.class, remap = false)
public abstract class MixinCustomEntity {

    @Shadow
    private List<CustomEntity.EvaluatedModel> models;

    @Shadow
    private boolean spawned;

    @Inject(method = "evaluateRenderControllerChange", at = @At("RETURN"))
    private void publishRenderModels(final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            CustomEntityRenderStore.update(((CustomEntity) (Object) this).javaUuid(), this.models);
        }
    }

    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
    private void useNativeRenderer(final CallbackInfo ci) {
        this.spawned = true;
        ci.cancel();
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void removeRenderModels(final CallbackInfo ci) {
        CustomEntityRenderStore.remove(((CustomEntity) (Object) this).javaUuid());
    }

}
