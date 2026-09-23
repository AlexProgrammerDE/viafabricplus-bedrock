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

package com.viaversion.viafabricplus.bedrock.screen;

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.friends.BedrockFriendsService;
import com.viaversion.viafabricplus.bedrock.friends.BedrockFriendsService.FriendWorld;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.BedrockConnectionUtil;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPList;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viafabricplus.screen.base.list.VFPTextEntry;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/** Lists Xbox friends' joinable Bedrock worlds. */
public final class BedrockFriendsScreen extends VFPScreen {

    public static final Component TITLE = Component.translatable("screen.viafabricplus.bedrock_friends");

    private @Nullable List<FriendWorld> worlds;
    private Component status = Component.translatable("bedrock_friends.viafabricplus.loading");
    private boolean loading;
    private boolean requested;
    private boolean joining;
    private SlotList list;
    private Button joinButton;
    private Button refreshButton;

    public BedrockFriendsScreen() {
        super(TITLE, true);
    }

    @Override
    protected void init() {
        this.list = this.addRenderableWidget(new SlotList(this.minecraft, this.width, this.height, CONTENT_TOP, FOOTER_HEIGHT,
            (this.font.lineHeight + 2) * 3));

        this.joinButton = Button.builder(Component.translatable("bedrock_friends.viafabricplus.join"), _ -> this.join()).build();
        this.refreshButton = Button.builder(Component.translatable("bedrock_friends.viafabricplus.refresh"), _ -> this.load()).build();
        this.addFooter(this.joinButton, this.refreshButton);
        super.init();

        if (!this.requested) {
            this.load();
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.joinButton.active = !this.joining && this.list.getFocused() instanceof SlotEntry;
        this.refreshButton.active = !this.loading && !this.joining;
    }

    private void load() {
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null || this.loading) {
            return;
        }
        this.loading = true;
        this.requested = true;
        this.status = Component.translatable("bedrock_friends.viafabricplus.loading");
        BedrockFriendsService.worlds(account)
            .thenAcceptAsync(loaded -> {
                if (ViaFabricPlusBedrock.impl().account().get() != account) {
                    this.loading = false;
                    return;
                }
                this.worlds = loaded;
                this.loading = false;
                this.rebuildWidgets();
            }, Minecraft.getInstance())
            .exceptionally(error -> this.fail("Failed to load Bedrock friends' worlds", error));
    }

    private void join() {
        if (!(this.list.getFocused() instanceof SlotEntry entry) || this.joining) {
            return;
        }
        final FriendWorld world = entry.world;
        if (world.protocol() != 0 && world.protocol() != ProtocolConstants.BEDROCK_PROTOCOL_VERSION) {
            showToast(Component.translatable("bedrock_friends.viafabricplus.incompatible"));
            return;
        }
        if (world.maxPlayers() > 0 && world.players() >= world.maxPlayers()) {
            showToast(Component.translatable("bedrock_friends.viafabricplus.full"));
            return;
        }
        final BedrockAuthManager account = ViaFabricPlusBedrock.impl().account().get();
        if (account == null) {
            return;
        }
        this.joining = true;
        BedrockFriendsService.join(account, world)
            .thenAcceptAsync(joined -> BedrockConnectionUtil.connectNetherNet(joined.address()), Minecraft.getInstance())
            .exceptionally(error -> this.fail("Failed to join a Bedrock friend's world", error));
    }

    private Void fail(final String message, final Throwable error) {
        ViaFabricPlusBedrock.impl().logger().error(message, error);
        Minecraft.getInstance().execute(() -> {
            if (this.joining) {
                BedrockFriendsService.leaveCurrent();
            }
            this.loading = false;
            this.joining = false;
            this.status = Component.translatable("bedrock_friends.viafabricplus.failed");
            showToast(this.status);
            this.rebuildWidgets();
        });
        return null;
    }

    private final class SlotList extends VFPList {

        private static double scrollAmount;

        private SlotList(final Minecraft minecraft, final int width, final int height, final int top, final int bottom, final int entryHeight) {
            super(minecraft, width, height, top, bottom, entryHeight);
            if (BedrockFriendsScreen.this.worlds == null || BedrockFriendsScreen.this.worlds.isEmpty()) {
                this.addEntry(new VFPTextEntry(BedrockFriendsScreen.this.worlds == null
                    ? BedrockFriendsScreen.this.status
                    : Component.translatable("bedrock_friends.viafabricplus.empty")));
            } else {
                BedrockFriendsScreen.this.worlds.forEach(world -> this.addEntry(new SlotEntry(this, world)));
            }
            this.setScrollAmount(scrollAmount);
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, this.width - 20);
        }

        @Override
        protected void updateSlotAmount(final double amount) {
            scrollAmount = amount;
        }

    }

    private static final class SlotEntry extends VFPListEntry {

        private final SlotList list;
        private final FriendWorld world;

        private SlotEntry(final SlotList list, final FriendWorld world) {
            this.list = list;
            this.world = world;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.literal(this.world.worldName() + ", " + this.world.hostName());
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor graphics, final int entryWidth, final int entryHeight) {
            final Font font = Minecraft.getInstance().font;
            graphics.text(font, this.world.worldName(), SLOT_MARGIN, SLOT_MARGIN,
                this.list.getFocused() == this ? ACCENT_COLOR : -1);
            graphics.text(font, this.world.hostName(), SLOT_MARGIN, SLOT_MARGIN + font.lineHeight + 2, -1);
            final String players = this.world.players() + "/" + this.world.maxPlayers();
            graphics.text(font, players, entryWidth - font.width(players) - SLOT_MARGIN, SLOT_MARGIN, -1);
            graphics.text(font, this.world.version(), entryWidth - font.width(this.world.version()) - SLOT_MARGIN,
                SLOT_MARGIN + font.lineHeight + 2, -1);
        }

    }

}
