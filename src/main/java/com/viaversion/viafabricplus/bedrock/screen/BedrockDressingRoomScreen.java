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

package com.viaversion.viafabricplus.bedrock.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.appearance.BedrockAppearanceStore;
import com.viaversion.viafabricplus.bedrock.appearance.BedrockAppearanceStore.Appearance;
import com.viaversion.viafabricplus.bedrock.appearance.BedrockAppearanceStore.Selection;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/** Selects a classic Bedrock skin and cape for the next connection. */
public final class BedrockDressingRoomScreen extends VFPScreen {

    public static final Component TITLE = Component.translatable("screen.viafabricplus.bedrock_dressing_room");

    private static final Identifier PREVIEW_TEXTURE = Identifier.fromNamespaceAndPath("viafabricplus-bedrock", "dressing_room/preview");
    private static final Identifier CAPE_PREVIEW_TEXTURE = Identifier.fromNamespaceAndPath("viafabricplus-bedrock", "dressing_room/cape");

    private final BedrockAppearanceStore store = ViaFabricPlusBedrock.impl().appearances();
    private final String accountId = BedrockAppearanceStore.accountId(ViaFabricPlusBedrock.impl().account().get());

    private Appearance appearance;
    private Component status = Component.translatable("bedrock_dressing_room.viafabricplus.reconnect");
    private Model.Simple wideModel;
    private Model.Simple slimModel;
    private Button customButton;
    private Button modelButton;
    private Button removeCapeButton;
    private boolean previewRegistered;
    private boolean capePreviewRegistered;

    public BedrockDressingRoomScreen() {
        super(TITLE, true);
    }

    @Override
    protected void init() {
        super.init();
        this.wideModel = new Model.Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
        this.slimModel = new Model.Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);

        final int left = this.width / 2 - 150;
        final int buttonWidth = 96;
        this.addRenderableWidget(Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.steve"), _ -> this.select(Selection.STEVE))
            .bounds(left, 44, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.alex"), _ -> this.select(Selection.ALEX))
            .bounds(left + 102, 44, buttonWidth, 20).build());
        this.customButton = this.addRenderableWidget(Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.custom"),
            _ -> this.select(Selection.CUSTOM)).bounds(left + 204, 44, buttonWidth, 20).build());

        this.modelButton = this.addRenderableWidget(Button.builder(Component.empty(), _ -> this.toggleModel())
            .bounds(left + 157, 93, 143, 20).build());

        this.addFooter(
            Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.import_skin"), _ -> this.chooseFile(false)).build(),
            Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.import_cape"), _ -> this.chooseFile(true)).build(),
            this.removeCapeButton = Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.remove_cape"), _ -> this.removeCape()).build()
        );

        try {
            this.showAppearance(this.store.load(this.accountId));
        } catch (IOException e) {
            ViaFabricPlusBedrock.impl().logger().warn("Could not load the selected Bedrock appearance", e);
            this.status = Component.translatable("bedrock_dressing_room.viafabricplus.load_failed");
        }
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        this.renderScreenTitle(graphics);

        final int left = this.width / 2 - 150;
        if (this.appearance != null && this.previewRegistered) {
            graphics.skin(this.appearance.effectiveSlim() ? this.slimModel : this.wideModel, PREVIEW_TEXTURE,
                3.1F, 5, 25, 0, left + 8, 72, left + 145, Math.min(this.height - 60, 180));
        }
        if (this.appearance != null) {
            graphics.text(this.font, Component.translatable("bedrock_dressing_room.viafabricplus.selected",
                Component.translatable("bedrock_dressing_room.viafabricplus." + this.appearance.selection().name().toLowerCase(Locale.ROOT))),
                left + 157, 76, -1);
            graphics.text(this.font, Component.translatable(this.appearance.cape() == null
                ? "bedrock_dressing_room.viafabricplus.no_cape" : "bedrock_dressing_room.viafabricplus.cape_selected"),
                left + 157, 123, -1);
            if (this.capePreviewRegistered) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, CAPE_PREVIEW_TEXTURE, left + 157, 140, 0, 0,
                    64, 32, this.appearance.cape().getWidth(), this.appearance.cape().getHeight(),
                    this.appearance.cape().getWidth(), this.appearance.cape().getHeight());
            }
        }
        graphics.textWithWordWrap(this.font, this.status, left, this.height - 55, 300, ACCENT_COLOR);
    }

    @Override
    public void onFilesDrop(final List<Path> paths) {
        if (paths.size() == 1) {
            this.importFile(paths.getFirst(), false);
        } else {
            this.status = Component.translatable("bedrock_dressing_room.viafabricplus.one_file");
        }
    }

    @Override
    public void removed() {
        this.releasePreview();
        super.removed();
    }

    private void select(final Selection selection) {
        try {
            this.showAppearance(this.store.select(this.accountId, selection));
        } catch (IOException e) {
            this.failed(e);
        }
    }

    private void toggleModel() {
        if (this.appearance == null || this.appearance.selection() != Selection.CUSTOM) {
            return;
        }
        try {
            this.showAppearance(this.store.setSlim(this.accountId, !this.appearance.slim()));
        } catch (IOException e) {
            this.failed(e);
        }
    }

    private void removeCape() {
        try {
            this.showAppearance(this.store.removeCape(this.accountId));
        } catch (IOException e) {
            this.failed(e);
        }
    }

    private void chooseFile(final boolean cape) {
        Thread.ofPlatform().name("Bedrock appearance file picker").start(() -> {
            Frame frame = null;
            try {
                frame = new Frame();
                final FileDialog dialog = new FileDialog(frame, cape ? "Choose a cape PNG" : "Choose a skin PNG", FileDialog.LOAD);
                dialog.setFilenameFilter((_, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
                dialog.setVisible(true);
                if (dialog.getFile() != null) {
                    final Path path = Path.of(dialog.getDirectory(), dialog.getFile());
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().gui.screen() == this) {
                            this.importFile(path, cape);
                        }
                    });
                }
            } catch (RuntimeException e) {
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().gui.screen() == this) {
                        this.failed(new IOException("Could not open the file picker", e));
                    }
                });
            } finally {
                if (frame != null) {
                    frame.dispose();
                }
            }
        });
    }

    private void importFile(final Path path, final boolean cape) {
        try {
            this.showAppearance(cape ? this.store.importCape(this.accountId, path) : this.store.importSkin(this.accountId, path));
        } catch (IOException e) {
            this.failed(e);
        }
    }

    private void showAppearance(final Appearance appearance) {
        this.appearance = appearance;
        this.releasePreview();
        this.registerPreview(PREVIEW_TEXTURE, appearance.skin());
        this.previewRegistered = true;
        if (appearance.cape() != null) {
            this.registerPreview(CAPE_PREVIEW_TEXTURE, appearance.cape());
            this.capePreviewRegistered = true;
        }
        this.customButton.active = appearance.selection() == Selection.CUSTOM || this.store.hasCustomSkin(this.accountId);
        this.modelButton.active = appearance.selection() == Selection.CUSTOM;
        this.modelButton.setMessage(Component.translatable(appearance.effectiveSlim()
            ? "bedrock_dressing_room.viafabricplus.slim" : "bedrock_dressing_room.viafabricplus.wide"));
        this.removeCapeButton.active = appearance.cape() != null;
        this.status = Component.translatable("bedrock_dressing_room.viafabricplus.reconnect");
    }

    private void registerPreview(final Identifier id, final BufferedImage image) {
        final NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = image.getRGB(x, y);
                nativeImage.setPixelABGR(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >>> 16) & 0xFF));
            }
        }
        this.minecraft.getTextureManager().register(id, new DynamicTexture(id::toString, nativeImage));
    }

    private void releasePreview() {
        if (this.previewRegistered) {
            this.minecraft.getTextureManager().release(PREVIEW_TEXTURE);
            this.previewRegistered = false;
        }
        if (this.capePreviewRegistered) {
            this.minecraft.getTextureManager().release(CAPE_PREVIEW_TEXTURE);
            this.capePreviewRegistered = false;
        }
    }

    private void failed(final IOException e) {
        ViaFabricPlusBedrock.impl().logger().warn("Could not update the Bedrock appearance", e);
        this.status = Component.literal(e.getMessage());
    }

}
