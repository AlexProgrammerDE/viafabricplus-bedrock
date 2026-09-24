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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.NonNull;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

/** Selects a classic Bedrock skin and cape for the next connection. */
public final class BedrockDressingRoomScreen extends VFPScreen {

    public static final Component TITLE = Component.translatable("screen.viafabricplus.bedrock_dressing_room");

    private static final Identifier PREVIEW_TEXTURE = Identifier.fromNamespaceAndPath("viafabricplus-bedrock", "dressing_room/preview");
    private static final Identifier CAPE_PREVIEW_TEXTURE = Identifier.fromNamespaceAndPath("viafabricplus-bedrock", "dressing_room/cape");
    private static final ByteBuffer PNG_FILTER_NAME = MemoryUtil.memUTF8("PNG images");
    private static final ByteBuffer PNG_FILTER_PATTERN = MemoryUtil.memUTF8("png");
    private static final SDL_DialogFileFilter.Buffer PNG_FILTER = SDL_DialogFileFilter.calloc(1);
    private static final AtomicLong NEXT_DIALOG_ID = new AtomicLong();
    private static final Map<Long, FileDialogRequest> OPEN_DIALOGS = new ConcurrentHashMap<>();
    private static final SDL_DialogFileCallback FILE_DIALOG_CALLBACK = SDL_DialogFileCallback.create((dialogId, files, _) -> {
        final FileDialogRequest request = OPEN_DIALOGS.remove(dialogId);
        if (request == null) {
            return;
        }
        final String error = files == 0 ? SDLError.SDL_GetError() : null;
        final long firstFile = files == 0 ? 0 : MemoryUtil.memGetAddress(files);
        final String selected = firstFile == 0 ? null : MemoryUtil.memUTF8(firstFile);
        request.screen.minecraft.schedule(() -> request.screen.finishFileDialog(request.cape, selected, error));
    });

    static {
        // SDL keeps the filter until the asynchronous file dialog callback completes.
        PNG_FILTER.get(0).name(PNG_FILTER_NAME).pattern(PNG_FILTER_PATTERN);
    }

    private final BedrockAppearanceStore store = ViaFabricPlusBedrock.impl().appearances();
    private final String accountId = BedrockAppearanceStore.accountId(ViaFabricPlusBedrock.impl().account().get());

    private Appearance appearance;
    private Component status = Component.translatable("bedrock_dressing_room.viafabricplus.reconnect");
    private PlayerSkin previewSkin;
    private Button customButton;
    private Button modelButton;
    private Button removeCapeButton;
    private boolean previewRegistered;
    private boolean capePreviewRegistered;
    private boolean filePickerOpen;

    public BedrockDressingRoomScreen() {
        super(TITLE, true);
    }

    @Override
    protected void init() {
        super.init();
        final int left = this.width / 2 - 150;
        final int top = this.contentTop();
        final int buttonWidth = 96;
        this.addRenderableWidget(Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.steve"), _ -> this.select(Selection.STEVE))
            .bounds(left, top, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.alex"), _ -> this.select(Selection.ALEX))
            .bounds(left + 102, top, buttonWidth, 20).build());
        this.customButton = this.addRenderableWidget(Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.custom"),
            _ -> this.select(Selection.CUSTOM)).bounds(left + 204, top, buttonWidth, 20).build());

        this.modelButton = this.addRenderableWidget(Button.builder(Component.empty(), _ -> this.toggleModel())
            .bounds(left + 157, top + 53, 143, 20).build());

        this.addFooter(
            Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.import_skin"), _ -> this.chooseFile(false)).build(),
            Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.import_cape"), _ -> this.chooseFile(true)).build(),
            this.removeCapeButton = Button.builder(Component.translatable("bedrock_dressing_room.viafabricplus.remove_cape"), _ -> this.removeCape()).build()
        );

        try {
            this.showAppearance(this.store.load(this.accountId));
            final PlayerSkinWidget preview = new PlayerSkinWidget(145, this.previewHeight(), this.minecraft.getEntityModels(), () -> this.previewSkin);
            preview.setX(left);
            preview.setY(top + 30);
            this.addRenderableWidget(preview);
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
        final int previewTop = this.contentTop() + 30;
        if (this.appearance != null) {
            graphics.text(this.font, Component.translatable("bedrock_dressing_room.viafabricplus.selected",
                Component.translatable("bedrock_dressing_room.viafabricplus." + this.appearance.selection().name().toLowerCase(Locale.ROOT))),
                left + 157, previewTop + 4, -1);
            graphics.text(this.font, Component.translatable(this.appearance.cape() == null
                ? "bedrock_dressing_room.viafabricplus.no_cape" : "bedrock_dressing_room.viafabricplus.cape_selected"),
                left + 157, previewTop + 56, -1);
            if (this.capePreviewRegistered && this.previewHeight() >= 130) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, CAPE_PREVIEW_TEXTURE, left + 157, previewTop + 74, 0, 0,
                    64, 32, this.appearance.cape().getWidth(), this.appearance.cape().getHeight(),
                    this.appearance.cape().getWidth(), this.appearance.cape().getHeight());
            }
        }
        graphics.textWithWordWrap(this.font, this.status, left, previewTop + this.previewHeight() + 6, 300, ACCENT_COLOR);
    }

    private int contentTop() {
        return Math.max(88, (this.height - 250) / 2);
    }

    private int previewHeight() {
        return Math.min(200, Math.max(60, this.height - this.contentTop() - 90));
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
        if (this.filePickerOpen) {
            return;
        }
        this.filePickerOpen = true;
        final long dialogId = NEXT_DIALOG_ID.incrementAndGet();
        OPEN_DIALOGS.put(dialogId, new FileDialogRequest(this, cape));
        try {
            SDLDialog.SDL_ShowOpenFileDialog(FILE_DIALOG_CALLBACK, dialogId, this.minecraft.getWindow().handle(), PNG_FILTER, (CharSequence) null, false);
        } catch (RuntimeException | LinkageError e) {
            OPEN_DIALOGS.remove(dialogId);
            this.filePickerOpen = false;
            this.failed(new IOException("Could not open the file picker", e));
        }
    }

    private void finishFileDialog(final boolean cape, final String selected, final String error) {
        this.filePickerOpen = false;
        if (this.minecraft.gui.screen() != this) {
            return;
        }
        if (error != null) {
            this.failed(new IOException("Could not open the file picker: " + error));
        } else if (selected != null) {
            this.importFile(Path.of(selected), cape);
        }
    }

    private record FileDialogRequest(BedrockDressingRoomScreen screen, boolean cape) {
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
        this.previewSkin = PlayerSkin.insecure(new ClientAsset.ResourceTexture(PREVIEW_TEXTURE, PREVIEW_TEXTURE),
            appearance.cape() == null ? null : new ClientAsset.ResourceTexture(CAPE_PREVIEW_TEXTURE, CAPE_PREVIEW_TEXTURE),
            null, appearance.effectiveSlim() ? PlayerModelType.SLIM : PlayerModelType.WIDE);
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
