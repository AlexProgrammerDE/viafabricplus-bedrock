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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.raphimc.viabedrock.api.model.entity.CustomEntity;

/** Publishes immutable actor render state across the protocol and render threads. */
public final class CustomEntityRenderStore {

    private static final Map<UUID, List<CustomEntity.EvaluatedModel>> MODELS = new ConcurrentHashMap<>();

    private CustomEntityRenderStore() {
    }

    public static void update(final UUID uuid, final List<CustomEntity.EvaluatedModel> models) {
        MODELS.put(uuid, List.copyOf(models));
    }

    public static List<CustomEntity.EvaluatedModel> get(final UUID uuid) {
        return MODELS.get(uuid);
    }

    public static void remove(final UUID uuid) {
        MODELS.remove(uuid);
    }

    public static void clear() {
        MODELS.clear();
    }

}
