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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import org.cube.converter.model.element.Cube;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.util.element.UVMap;
import org.cube.converter.util.element.UVMap.UVType;

/** Builds client models from the geometry that ViaBedrock has already parsed. */
public final class BedrockGeometry {

    private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180D);
    private static final List<Direction> CUBE_FACES = List.of(
        Direction.DOWN, Direction.UP, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH
    );
    private static final List<String> PLAYER_PARTS = List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
    private static final Map<String, String> PLAYER_OVERLAYS = Map.of(
        "hat", "head", "jacket", "body", "right_sleeve", "right_arm", "left_sleeve", "left_arm",
        "right_pants", "right_leg", "left_pants", "left_leg"
    );

    private BedrockGeometry() {
    }

    public static Model<EntityRenderState> entityModel(final BedrockGeometryModel geometry) {
        return new Model<>(root(geometry, false), RenderTypes::entityTranslucent) {
        };
    }

    public static PlayerModel playerModel(final BedrockGeometryModel geometry, final boolean slim) {
        return new PlayerModel(root(geometry, true), slim);
    }

    private static ModelPart root(final BedrockGeometryModel geometry, final boolean player) {
        final Map<String, Bone> bones = new HashMap<>();
        final float textureWidth = geometry.getTextureSize().getX();
        final float textureHeight = geometry.getTextureSize().getY();

        for (Parent parent : geometry.getParents()) {
            final Map<String, ModelPart> children = new HashMap<>();
            final ModelPart part = new ModelPart(List.of(), children);
            part.setRotation(parent.getRotation().getX() * DEGREES_TO_RADIANS, parent.getRotation().getY() * DEGREES_TO_RADIANS, parent.getRotation().getZ() * DEGREES_TO_RADIANS);
            int cubeNumber = 0;
            for (Cube cube : parent.getCubes().values()) {
                children.put("cube_" + cubeNumber++, cubePart(cube, parent, textureWidth, textureHeight));
            }
            bones.put(parent.getName(), new Bone(parent, part, children));
        }

        final Map<String, ModelPart> roots = new HashMap<>();
        final Map<String, Map<String, ModelPart>> playerChildren = new HashMap<>();
        for (Bone bone : bones.values()) {
            final String name = player ? playerPartName(bone.definition.getName()) : bone.definition.getName();
            final Bone parent = bones.get(bone.definition.getParent());
            final boolean directPlayerPart = player && (PLAYER_PARTS.contains(name) || PLAYER_OVERLAYS.containsKey(name));
            final float parentX = directPlayerPart || parent == null ? 0 : parent.definition.getPivot().getX();
            final float parentY = directPlayerPart || parent == null ? 24 : parent.definition.getPivot().getY();
            final float parentZ = directPlayerPart || parent == null ? 0 : parent.definition.getPivot().getZ();
            bone.part.setPos(bone.definition.getPivot().getX() - parentX, parentY - bone.definition.getPivot().getY(), bone.definition.getPivot().getZ() - parentZ);
            bone.part.setInitialPose(bone.part.storePose());
            if (player && PLAYER_OVERLAYS.containsKey(name)) {
                continue;
            }
            if (directPlayerPart || parent == null) {
                roots.put(name, bone.part);
                if (player && PLAYER_PARTS.contains(name)) {
                    playerChildren.put(name, bone.children);
                }
            } else {
                parent.children.put(name, bone.part);
            }
        }
        if (player) {
            for (String name : PLAYER_PARTS) {
                if (!roots.containsKey(name)) {
                    final Map<String, ModelPart> children = new HashMap<>();
                    roots.put(name, new ModelPart(List.of(), children));
                    playerChildren.put(name, children);
                }
            }
            for (Map.Entry<String, String> overlay : PLAYER_OVERLAYS.entrySet()) {
                final Bone bone = bones.values().stream()
                    .filter(value -> playerPartName(value.definition.getName()).equals(overlay.getKey()))
                    .findFirst().orElse(null);
                final ModelPart overlayPart = bone == null ? new ModelPart(List.of(), Map.of()) : bone.part;
                if (bone != null) {
                    final Bone body = bones.values().stream()
                        .filter(value -> playerPartName(value.definition.getName()).equals(overlay.getValue()))
                        .findFirst().orElse(null);
                    final float parentX = body == null ? 0 : body.definition.getPivot().getX();
                    final float parentY = body == null ? 24 : body.definition.getPivot().getY();
                    final float parentZ = body == null ? 0 : body.definition.getPivot().getZ();
                    overlayPart.setPos(bone.definition.getPivot().getX() - parentX, parentY - bone.definition.getPivot().getY(), bone.definition.getPivot().getZ() - parentZ);
                    overlayPart.setInitialPose(overlayPart.storePose());
                }
                playerChildren.get(overlay.getValue()).put(overlay.getKey(), overlayPart);
            }
        }
        return new ModelPart(List.of(), roots);
    }

    private static ModelPart cubePart(final Cube cube, final Parent bone, final float textureWidth, final float textureHeight) {
        final float pivotX = cube.getPivot().getX();
        final float pivotY = cube.getPivot().getY();
        final float pivotZ = cube.getPivot().getZ();
        final float x = cube.getPosition().getX() - pivotX;
        final float y = pivotY - cube.getPosition().getY() - cube.getSize().getY();
        final float z = cube.getPosition().getZ() - pivotZ;
        final float sizeX = cube.getSize().getX();
        final float sizeY = cube.getSize().getY();
        final float sizeZ = cube.getSize().getZ();
        final UVMap uv = cube.getUvMap();
        final Set<Direction> faces = EnumSet.noneOf(Direction.class);
        for (Direction direction : Direction.values()) {
            if (uv.getUvMap().containsKey(sourceFace(uv, direction))) {
                faces.add(direction);
            }
        }
        final ModelPart.Cube partCube = new ModelPart.Cube(0, 0, x, y, z, sizeX, sizeY, sizeZ, cube.getInflate(), cube.getInflate(), cube.getInflate(), cube.isMirror(), textureWidth, textureHeight, faces);
        setFaceUvs(partCube, uv, faces, textureWidth, textureHeight, cube.isMirror());
        final ModelPart part = new ModelPart(List.of(partCube), Map.of());
        part.setPos(pivotX - bone.getPivot().getX(), bone.getPivot().getY() - pivotY, pivotZ - bone.getPivot().getZ());
        part.setRotation(cube.getRotation().getX() * DEGREES_TO_RADIANS, cube.getRotation().getY() * DEGREES_TO_RADIANS, cube.getRotation().getZ() * DEGREES_TO_RADIANS);
        part.setInitialPose(part.storePose());
        return part;
    }

    private static void setFaceUvs(final ModelPart.Cube cube, final UVMap uv, final Set<Direction> faces, final float width, final float height, final boolean mirror) {
        int index = 0;
        for (Direction direction : CUBE_FACES) {
            if (faces.contains(direction)) {
                final ModelPart.Vertex[] vertices = cube.polygons[index].vertices().clone();
                if (mirror) {
                    for (int i = 0; i < vertices.length / 2; i++) {
                        final ModelPart.Vertex opposite = vertices[vertices.length - 1 - i];
                        vertices[vertices.length - 1 - i] = vertices[i];
                        vertices[i] = opposite;
                    }
                }
                final Float[] face = uv.getUvMap().get(sourceFace(uv, direction));
                final boolean rotate = uv.getUvType() == UVType.BOX && (direction == Direction.DOWN || direction == Direction.UP);
                cube.polygons[index++] = new ModelPart.Polygon(vertices,
                    rotate ? face[2] : face[0], rotate ? face[3] : face[1],
                    rotate ? face[0] : face[2], rotate ? face[1] : face[3],
                    width, height, mirror, direction);
            }
        }
    }

    private static org.cube.converter.util.element.Direction sourceFace(final UVMap uv, final Direction direction) {
        final Direction source = uv.getUvType() == UVType.BOX ? switch (direction) {
            case DOWN -> Direction.UP;
            case UP -> Direction.DOWN;
            case WEST -> Direction.EAST;
            case EAST -> Direction.WEST;
            default -> direction;
        } : direction;
        return org.cube.converter.util.element.Direction.valueOf(source.name());
    }

    private static String playerPartName(final String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "leftarm" -> "left_arm";
            case "rightarm" -> "right_arm";
            case "leftleg" -> "left_leg";
            case "rightleg" -> "right_leg";
            case "leftsleeve" -> "left_sleeve";
            case "rightsleeve" -> "right_sleeve";
            case "leftpants" -> "left_pants";
            case "rightpants" -> "right_pants";
            default -> name.toLowerCase(Locale.ROOT);
        };
    }

    private record Bone(Parent definition, ModelPart part, Map<String, ModelPart> children) {
    }
}
