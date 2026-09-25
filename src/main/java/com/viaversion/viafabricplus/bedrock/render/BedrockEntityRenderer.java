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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Interaction;
import net.raphimc.viabedrock.api.model.entity.CustomEntity;
import net.raphimc.viabedrock.api.util.StringUtil;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;

/** Renders ViaBedrock custom actors while leaving ordinary interactions invisible. */
public final class BedrockEntityRenderer extends EntityRenderer<Interaction, BedrockEntityRenderer.State> {

    private final Map<String, Model<EntityRenderState>> models = new HashMap<>();
    private final Map<List<CustomEntity.EvaluatedModel>, List<RenderedModel>> resolvedModelSets = new HashMap<>();
    private ResourcePackStorage currentPacks;

    public BedrockEntityRenderer(final EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRender(final Interaction entity, final Frustum frustum, final double x, final double y, final double z, final float partialTicks) {
        if (CustomEntityRenderStore.get(entity.getUUID()) != null) {
            return x * x + y * y + z * z <= 96D * 96D;
        }
        return super.shouldRender(entity, frustum, x, y, z, partialTicks);
    }

    @Override
    public void extractRenderState(final Interaction entity, final State state, final float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.models = List.of();
        final UserConnection connection = ViaFabricPlus.api().userConnection();
        if (connection == null || !connection.has(ResourcePackStorage.class)) {
            this.currentPacks = null;
            this.models.clear();
            this.resolvedModelSets.clear();
            return;
        }
        final List<CustomEntity.EvaluatedModel> evaluatedModels = CustomEntityRenderStore.get(entity.getUUID());
        if (evaluatedModels == null) {
            return;
        }
        final ResourcePackStorage packs = connection.get(ResourcePackStorage.class);
        if (this.currentPacks != packs) {
            this.currentPacks = packs;
            this.models.clear();
            this.resolvedModelSets.clear();
        }
        state.models = this.resolvedModelSets.computeIfAbsent(evaluatedModels, models -> this.resolveModels(models, packs));
        state.yaw = entity.getYRot();
    }

    private List<RenderedModel> resolveModels(
        final List<CustomEntity.EvaluatedModel> evaluatedModels,
        final ResourcePackStorage packs
    ) {
        final List<RenderedModel> resolved = new ArrayList<>();
        for (var evaluated : evaluatedModels) {
            final BedrockGeometryModel geometry = packs.getModels().entityModels().get(evaluated.geometryValue());
            if (geometry == null) {
                continue;
            }
            final Model<EntityRenderState> model = this.models.computeIfAbsent(evaluated.geometryValue(), _ -> BedrockGeometry.entityModel(geometry));
            final String path = "textures/item/entities/" + StringUtil.makeIdentifierValueSafe(evaluated.textureValue().replace("textures/", "")) + ".png";
            resolved.add(new RenderedModel(model, Identifier.fromNamespaceAndPath("viabedrock", path)));
        }
        return List.copyOf(resolved);
    }

    @Override
    public void submit(final State state, final PoseStack poseStack, final SubmitNodeCollector nodes, final CameraRenderState camera) {
        for (RenderedModel rendered : state.models) {
            poseStack.pushPose();
            poseStack.rotateDegrees(Axis.YP, 180F - state.yaw);
            poseStack.scale(-1F, -1F, 1F);
            poseStack.translate(0F, -1.5F, 0F);
            nodes.submitModel(rendered.model, state, poseStack, rendered.model.renderType(rendered.texture), state.lightCoords, OverlayTexture.NO_OVERLAY, -1);
            poseStack.popPose();
        }
    }

    public static final class State extends EntityRenderState {
        private List<RenderedModel> models = List.of();
        private float yaw;
    }

    private record RenderedModel(Model<EntityRenderState> model, Identifier texture) {
    }

}
