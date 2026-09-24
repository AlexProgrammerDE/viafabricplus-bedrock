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

package com.viaversion.viafabricplus.bedrock.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viaversion.viafabricplus.bedrock.appearance.BedrockAppearanceStore.Selection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BedrockAppearanceStoreTest {

    @TempDir
    Path directory;

    @Test
    void keepsSelectionsAndCapesSeparateByAccount() throws IOException {
        final BedrockAppearanceStore store = this.store();
        final Path skin = this.image(64, 64);
        final Path cape = this.image(64, 32);

        final var imported = store.importSkin("123", skin);
        assertEquals(Selection.CUSTOM, imported.selection());
        assertEquals(Selection.STEVE, store.load("456").selection());

        store.setSlim("123", true);
        final var withCape = store.importCape("123", cape);
        assertTrue(withCape.effectiveSlim());
        assertEquals(64, withCape.cape().getWidth());

        store.select("123", Selection.ALEX);
        assertTrue(store.load("123").effectiveSlim());
        assertNotEquals(withCape.skinId(), store.load("123").skinId());
        store.select("123", Selection.CUSTOM);
        assertTrue(store.load("123").effectiveSlim());
        assertTrue(store.hasCustomSkin("123"));

        assertEquals(Selection.CUSTOM, this.store().load("123").selection());
        assertEquals(Selection.STEVE, this.store().load("456").selection());
        assertNull(store.removeCape("123").cape());
    }

    @Test
    void rejectsInvalidSkinWithoutChangingSelection() throws IOException {
        final BedrockAppearanceStore store = this.store();
        final Path invalid = this.image(64, 32);

        assertThrows(IOException.class, () -> store.importSkin("123", invalid));
        assertEquals(Selection.STEVE, store.load("123").selection());
        assertFalse(store.hasCustomSkin("123"));
    }

    private BedrockAppearanceStore store() {
        return new BedrockAppearanceStore(this.directory.resolve("appearances"),
            _ -> new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
    }

    private Path image(final int width, final int height) throws IOException {
        final Path path = this.directory.resolve(width + "x" + height + ".png");
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", path.toFile());
        return path;
    }

}
