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
import com.viaversion.viafabricplus.bedrock.realms.BedrockRealmsError;
import com.viaversion.viafabricplus.bedrock.visual.BedrockUiArt;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.raphimc.minecraftauth.extra.realms.model.RealmsServer;
import net.raphimc.minecraftauth.extra.realms.service.impl.BedrockRealmsService;
import org.jetbrains.annotations.Nullable;

/** Shows what Realm Timeline sharing means before the member chooses whether to opt in. */
public final class BedrockRealmTimelineScreen extends VFPScreen {

    private static final int PANEL = 0xFF303234;
    private static final int PANEL_BORDER = 0xFF101112;
    private static final int GRID = 0xFF252728;
    private static final int GRID_LINE = 0xFF191B1C;
    private static final int MUTED_TEXT = 0xFFD2D4D8;
    private static final int KEY_SPACE = 32;
    private static final int KEY_ENTER = 257;
    private static final int KEY_KEYPAD_ENTER = 335;
    private static final int[] ACTIVITY_COLORS = {0xFF46C5C5, 0xFF9A70E6, 0xFFFFB268, 0xFF8DDFFF};
    private static final int[][] ACTIVITY = {
        {0, 6, 0, 0, 8, 5, 7},
        {0, 4, 0, 8, 3, 7, 5},
        {4, 0, 7, 0, 6, 0, 5},
        {0, 0, 5, 0, 4, 6, 0}
    };

    private final BedrockRealmsService service;
    private final RealmsServer realm;
    private final Runnable join;
    private @Nullable Component status;
    private boolean saving;
    private boolean failed;
    private boolean closed;
    private Layout layout;
    private ChoiceButton actionButton;

    public BedrockRealmTimelineScreen(final BedrockRealmsService service, final RealmsServer realm, final Runnable join) {
        super(Component.translatable("bedrock_realms.viafabricplus.timeline.title"), true);
        this.service = service;
        this.realm = realm;
        this.join = join;
    }

    @Override
    protected void init() {
        this.layout = Layout.forScreen(this.width, this.height);
        this.addRenderableOnly((graphics, mouseX, mouseY, delta) -> this.drawPanels(graphics));

        final int buttonX = this.layout.textX + 12;
        final int buttonWidth = this.layout.textWidth - 24;
        final int buttonY = this.layout.buttonY();
        final int buttonHeight = this.layout.compact ? 18 : 20;
        this.actionButton = this.addRenderableWidget(new ChoiceButton(buttonX, buttonY, buttonWidth, buttonHeight,
            Component.translatable("bedrock_realms.viafabricplus.timeline.opt_in"), true, this::optIn));
        this.addRenderableWidget(new ChoiceButton(buttonX, buttonY + buttonHeight + (this.layout.compact ? 3 : 5),
            buttonWidth, buttonHeight,
            Component.translatable("bedrock_realms.viafabricplus.timeline.back"), false, this::onClose));
        super.init();
    }

    @Override
    public void tick() {
        super.tick();
        this.actionButton.active = !this.saving;
    }

    @Override
    public void renderTitle(final GuiGraphicsExtractor graphics) {
        // The heading belongs to the illustration panel.
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY,
                                  final float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        if (BedrockUiArt.drawBackdrop(graphics, this.width, this.height)) {
            graphics.fill(0, 0, this.width, this.height, 0x88303040);
        }
    }

    @Override
    public void onClose() {
        this.closed = true;
        super.onClose();
    }

    private void drawPanels(final GuiGraphicsExtractor graphics) {
        this.drawGraphic(graphics);
        this.drawExplanation(graphics);
    }

    private void drawGraphic(final GuiGraphicsExtractor graphics) {
        final Layout bounds = this.layout;
        panel(graphics, bounds.graphicX, bounds.graphicY, bounds.graphicWidth, bounds.graphicHeight);
        final Component heading = Component.translatable("bedrock_realms.viafabricplus.timeline.graphic_title");
        if (bounds.compact) {
            graphics.centeredText(this.font, heading, bounds.graphicX + bounds.graphicWidth / 2,
                bounds.graphicY + 5, 0xFFFFFFFF);
        } else {
            graphics.pose().pushMatrix();
            graphics.pose().scale(1.5F, 1.5F);
            graphics.centeredText(this.font, heading, (int) ((bounds.graphicX + bounds.graphicWidth / 2) / 1.5F),
                (int) ((bounds.graphicY + 15) / 1.5F), 0xFFFFFFFF);
            graphics.pose().popMatrix();
        }

        final int tableX = bounds.graphicX + (bounds.compact ? 8 : 18);
        final int tableY = bounds.graphicY + (bounds.compact ? 18 : 42);
        final int tableWidth = bounds.graphicWidth - (bounds.compact ? 16 : 36);
        final int tableHeight = bounds.graphicHeight - (bounds.compact ? 25 : 68);
        final int gutter = bounds.compact ? 18 : 32;
        final int headerHeight = bounds.compact ? 7 : 20;
        final int rows = bounds.compact ? 3 : 4;
        final int rowHeight = Math.max(1, (tableHeight - headerHeight) / rows);
        final int columnWidth = Math.max(1, (tableWidth - gutter) / 7);

        graphics.fill(tableX, tableY, tableX + tableWidth, tableY + tableHeight, GRID_LINE);
        graphics.fill(tableX + gutter, tableY + headerHeight,
            tableX + tableWidth - 1, tableY + tableHeight - 1, GRID);
        for (int column = 0; column < 7; column++) {
            final int cellX = tableX + gutter + column * columnWidth;
            graphics.fill(cellX + 1, tableY + 1, cellX + columnWidth, tableY + headerHeight - 1, 0xFF46484B);
            graphics.fill(cellX + columnWidth / 2 - 2, tableY + headerHeight / 2,
                cellX + columnWidth / 2 + 2, tableY + headerHeight / 2 + 1, MUTED_TEXT);
        }
        for (int row = 0; row < rows; row++) {
            final int rowY = tableY + headerHeight + row * rowHeight;
            graphics.fill(tableX + 1, rowY + 1, tableX + gutter - 1, rowY + rowHeight - 1,
                row % 2 == 0 ? 0xFF484A4D : 0xFF3E4043);
            if (rowHeight >= (bounds.compact ? 6 : 9)) {
                drawFace(graphics, tableX + (bounds.compact ? 6 : 7),
                    rowY + Math.max(1, (rowHeight - (bounds.compact ? 6 : 9)) / 2), row, bounds.compact);
            }
            if (!bounds.compact) {
                graphics.fill(tableX + 20, rowY + rowHeight / 2, tableX + gutter - 5,
                    rowY + rowHeight / 2 + 1, MUTED_TEXT);
            }
            for (int column = 0; column < 7; column++) {
                final int cellX = tableX + gutter + column * columnWidth;
                graphics.fill(cellX + 1, rowY + 1, cellX + columnWidth, rowY + rowHeight - 1,
                    row % 2 == 0 ? 0xFF333536 : 0xFF292B2C);
                final int activity = ACTIVITY[row][column];
                if (activity > 0 && rowHeight >= 7 && columnWidth >= 6) {
                    final int barWidth = Math.min(columnWidth - 4, Math.max(3, activity * columnWidth / 13));
                    final int barX = cellX + (columnWidth - barWidth) / 2;
                    graphics.fill(barX, rowY + 3, barX + barWidth, rowY + rowHeight - 3,
                        ACTIVITY_COLORS[row]);
                }
            }
        }
        if (!bounds.compact) {
            graphics.centeredText(this.font, Component.translatable("bedrock_realms.viafabricplus.timeline.example"),
                bounds.graphicX + bounds.graphicWidth / 2, bounds.graphicY + bounds.graphicHeight - 17, MUTED_TEXT);
        }
    }

    private static void drawFace(final GuiGraphicsExtractor graphics, final int x, final int y,
                                 final int row, final boolean compact) {
        final int skin = switch (row) {
            case 0 -> 0xFF9C6548;
            case 1 -> 0xFFDFAD83;
            case 2 -> 0xFFC58B61;
            default -> 0xFFE8BF92;
        };
        final int size = compact ? 6 : 9;
        graphics.fill(x, y, x + size, y + size, 0xFF171819);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, skin);
        graphics.fill(x + 1, y + 1, x + size - 1, y + (compact ? 2 : 3),
            row == 1 ? 0xFFF0B948 : 0xFF4C3328);
        graphics.fill(x + 2, y + (compact ? 3 : 4), x + 3, y + (compact ? 4 : 5), 0xFF26334E);
        graphics.fill(x + size - 3, y + (compact ? 3 : 4), x + size - 2,
            y + (compact ? 4 : 5), 0xFF26334E);
    }

    private void drawExplanation(final GuiGraphicsExtractor graphics) {
        final Layout bounds = this.layout;
        panel(graphics, bounds.textX, bounds.textY, bounds.textWidth, bounds.textHeight);
        final int innerX = bounds.textX + 12;
        final int innerWidth = bounds.textWidth - 24;
        graphics.centeredText(this.font, Component.translatable("bedrock_realms.viafabricplus.timeline.requirement"),
            bounds.textX + bounds.textWidth / 2, bounds.textY + (bounds.compact ? 6 : 10), 0xFFFFFFFF);
        final int dividerY = bounds.textY + (bounds.compact ? 20 : 29);
        graphics.fill(bounds.textX + 1, dividerY,
            bounds.textX + bounds.textWidth - 1, dividerY + 1, 0xFF65676A);

        final String explanationKey = this.height < 220
            ? "bedrock_realms.viafabricplus.timeline.explanation_tiny"
            : bounds.compact
                ? "bedrock_realms.viafabricplus.timeline.explanation_short"
                : "bedrock_realms.viafabricplus.timeline.explanation";
        final Component explanation = Component.translatable(explanationKey);
        final Component body = this.status != null && (this.failed || this.saving) ? this.status : explanation;
        final List<FormattedCharSequence> lines = this.font.split(body, innerWidth);
        final int lineStep = this.font.lineHeight + (bounds.compact ? 1 : 3);
        final int bodyBottom = bounds.buttonY() - 5;
        int y = bounds.textY + (bounds.compact ? 26 : 38);
        for (final FormattedCharSequence line : lines) {
            if (y + this.font.lineHeight > bodyBottom) {
                break;
            }
            graphics.text(this.font, line, innerX, y, this.failed ? 0xFFFF8F8F : MUTED_TEXT);
            y += lineStep;
        }
    }

    private static void panel(final GuiGraphicsExtractor graphics, final int x, final int y,
                              final int width, final int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BORDER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, PANEL);
    }

    private void optIn() {
        if (this.saving) {
            return;
        }
        this.saving = true;
        this.failed = false;
        this.status = Component.translatable("bedrock_realms.viafabricplus.timeline.saving");
        this.service.updateWorldStorySettingsAsync(this.realm, null, true).whenComplete((_, error) ->
            Minecraft.getInstance().execute(() -> {
                this.saving = false;
                if (error != null) {
                    this.failed = true;
                    ViaFabricPlusBedrock.impl().logger().error("Failed to opt in to Realm Timeline", error);
                    this.status = BedrockRealmsError.describe(error);
                    showToast(this.status);
                } else if (!this.closed) {
                    this.onClose();
                    this.join.run();
                }
            }));
    }

    private record Layout(int graphicX, int graphicY, int graphicWidth, int graphicHeight,
                          int textX, int textY, int textWidth, int textHeight, boolean compact) {

        private static Layout forScreen(final int width, final int height) {
            final boolean compact = width < 620 || height < 320;
            final int totalWidth = Math.min(width - 24, compact ? 500 : 1040);
            if (compact) {
                final int top = 29;
                final int graphicHeight = Math.max(46, Math.min(92, (height - 54) / 3));
                final int textHeight = height - top - graphicHeight - 10;
                final int left = (width - totalWidth) / 2;
                return new Layout(left, top, totalWidth, graphicHeight,
                    left, top + graphicHeight + 5, totalWidth, textHeight, true);
            }
            final int totalHeight = Math.min(360, height - 70);
            final int top = Math.max(38, (height - totalHeight) / 2);
            final int left = (width - totalWidth) / 2;
            final int graphicWidth = (totalWidth - 10) * 56 / 100;
            return new Layout(left, top, graphicWidth, totalHeight,
                left + graphicWidth + 10, top, totalWidth - graphicWidth - 10, totalHeight, false);
        }

        private int buttonY() {
            return this.textY + this.textHeight - (this.compact ? 43 : 54);
        }

    }

    private static final class ChoiceButton extends AbstractWidget {

        private final boolean primary;
        private final Runnable action;

        private ChoiceButton(final int x, final int y, final int width, final int height,
                             final Component message, final boolean primary, final Runnable action) {
            super(x, y, width, height, message);
            this.primary = primary;
            this.action = action;
        }

        @Override
        protected void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX,
                                                final int mouseY, final float delta) {
            final int x = this.getX();
            final int y = this.getY();
            final boolean highlighted = this.isHoveredOrFocused() && this.active;
            final int border = highlighted ? 0xFFFFFFFF : this.primary ? 0xFFBDA6FF : 0xFF73767A;
            final int fill = !this.active ? 0xFF55525C : this.primary
                ? highlighted ? 0xFF8055ED : 0xFF7042D6
                : highlighted ? 0xFFF1F2F5 : 0xFFD9DCE2;
            graphics.fill(x, y, x + this.width, y + this.height, border);
            graphics.fill(x + 2, y + 2, x + this.width - 2, y + this.height - 2, fill);
            graphics.centeredText(Minecraft.getInstance().font, this.getMessage(),
                x + this.width / 2, y + (this.height - Minecraft.getInstance().font.lineHeight) / 2,
                this.primary || !this.active ? 0xFFFFFFFF : 0xFF26282B);
        }

        @Override
        public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
            if (this.active) {
                this.action.run();
            }
        }

        @Override
        public boolean keyPressed(final KeyEvent event) {
            if (this.active && this.isFocused() && (event.key() == KEY_ENTER
                || event.key() == KEY_KEYPAD_ENTER || event.key() == KEY_SPACE)) {
                this.action.run();
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(final NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }

    }

}
