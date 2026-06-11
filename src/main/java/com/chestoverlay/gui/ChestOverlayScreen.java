package com.chestoverlay.gui;

import com.chestoverlay.config.ChestOverlayConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ChestOverlayScreen extends Screen {

    private static final int PANEL_W  = 220;
    private static final int BTN_H    = 20;
    private static final int ROW_GAP  = 22;

    // Stored during init(), consumed in render()
    private record LabelEntry(int x, int y, String text) {}
    private final List<LabelEntry> sectionLabels = new ArrayList<>();

    private boolean showC1Box, showC2Box;
    private int c1BoxX, c1BoxY;
    private int c2BoxX, c2BoxY;

    public ChestOverlayScreen() {
        super(Text.literal("Chest Overlay Settings"));
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        sectionLabels.clear();
        showC1Box = false;
        showC2Box = false;

        int cx  = width / 2;
        int bx  = cx - PANEL_W / 2;
        // Sliders are narrower to leave room for the 20 px colour-preview box
        int sw  = PANEL_W - 26;
        int y   = 35;

        // ── Enabled toggle ──────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Overlay: " + (ChestOverlayConfig.enabled ? "§aON" : "§cOFF")),
            b -> { ChestOverlayConfig.enabled = !ChestOverlayConfig.enabled; clearAndInit(); }
        ).dimensions(bx, y, PANEL_W, BTN_H).build());
        y += 26;

        // ── Mode cycle button ───────────────────────────────────────────────
        String modeStr = switch (ChestOverlayConfig.colorMode) {
            case RGB    -> "§bRGB Rainbow";
            case CUSTOM -> "§eCustom Color";
            case PULSE  -> "§dPulse";
        };
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Mode: " + modeStr + "  §8[click]"),
            b -> {
                ChestOverlayConfig.ColorMode[] modes = ChestOverlayConfig.ColorMode.values();
                ChestOverlayConfig.colorMode = modes[(ChestOverlayConfig.colorMode.ordinal() + 1) % modes.length];
                clearAndInit();
            }
        ).dimensions(bx, y, PANEL_W, BTN_H).build());
        y += 30;

        // ── Color 1 (Custom + Pulse) ────────────────────────────────────────
        if (ChestOverlayConfig.colorMode != ChestOverlayConfig.ColorMode.RGB) {
            String lbl = ChestOverlayConfig.colorMode == ChestOverlayConfig.ColorMode.PULSE
                    ? "— Color 1 —" : "— Color —";
            sectionLabels.add(new LabelEntry(cx, y, "§7" + lbl));
            y += 13;

            showC1Box = true;
            c1BoxX = bx + sw + 4;
            c1BoxY = y;

            addDrawableChild(new ColorSlider(bx, y, sw, BTN_H, "R",
                () -> ChestOverlayConfig.color1R, v -> ChestOverlayConfig.color1R = v));
            y += ROW_GAP;
            addDrawableChild(new ColorSlider(bx, y, sw, BTN_H, "G",
                () -> ChestOverlayConfig.color1G, v -> ChestOverlayConfig.color1G = v));
            y += ROW_GAP;
            addDrawableChild(new ColorSlider(bx, y, sw, BTN_H, "B",
                () -> ChestOverlayConfig.color1B, v -> ChestOverlayConfig.color1B = v));
            y += 28;
        }

        // ── Color 2 + pulse speed (Pulse only) ─────────────────────────────
        if (ChestOverlayConfig.colorMode == ChestOverlayConfig.ColorMode.PULSE) {
            sectionLabels.add(new LabelEntry(cx, y, "§7— Color 2 —"));
            y += 13;

            showC2Box = true;
            c2BoxX = bx + sw + 4;
            c2BoxY = y;

            addDrawableChild(new ColorSlider(bx, y, sw, BTN_H, "R",
                () -> ChestOverlayConfig.color2R, v -> ChestOverlayConfig.color2R = v));
            y += ROW_GAP;
            addDrawableChild(new ColorSlider(bx, y, sw, BTN_H, "G",
                () -> ChestOverlayConfig.color2G, v -> ChestOverlayConfig.color2G = v));
            y += ROW_GAP;
            addDrawableChild(new ColorSlider(bx, y, sw, BTN_H, "B",
                () -> ChestOverlayConfig.color2B, v -> ChestOverlayConfig.color2B = v));
            y += 28;

            sectionLabels.add(new LabelEntry(cx, y, "§7— Pulse Speed —"));
            y += 13;
            addDrawableChild(new PulseSpeedSlider(bx, y, PANEL_W, BTN_H));
            y += 26;
        }

        // ── Cancel / Done ───────────────────────────────────────────────────
        int doneY = Math.max(y + 8, height - 28);
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Cancel"),
            b -> { ChestOverlayConfig.load(); close(); }
        ).dimensions(cx - PANEL_W / 2, doneY, 106, BTN_H).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Done"),
            b -> { ChestOverlayConfig.save(); close(); }
        ).dimensions(cx - PANEL_W / 2 + 110, doneY, 110, BTN_H).build());
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);

        // Section divider labels
        for (LabelEntry l : sectionLabels) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(l.text()), l.x(), l.y(), 0xAAAAAA);
        }

        // Live colour preview boxes (cover all 3 R/G/B slider rows)
        if (showC1Box) {
            drawColorBox(ctx, c1BoxX, c1BoxY,
                ChestOverlayConfig.color1R, ChestOverlayConfig.color1G, ChestOverlayConfig.color1B);
        }
        if (showC2Box) {
            drawColorBox(ctx, c2BoxX, c2BoxY,
                ChestOverlayConfig.color2R, ChestOverlayConfig.color2G, ChestOverlayConfig.color2B);
        }
    }

    /** Draws a 20 × 66 px colour swatch with a dark border, next to the 3 sliders. */
    private void drawColorBox(DrawContext ctx, int x, int y, int r, int g, int b) {
        int fillH  = BTN_H + ROW_GAP + BTN_H + ROW_GAP + BTN_H; // 3 rows
        int color  = 0xFF000000 | (r << 16) | (g << 8) | b;
        // Border
        ctx.fill(x - 1, y - 1, x + 21, y + fillH + 1, 0xFF555555);
        // Swatch
        ctx.fill(x, y, x + 20, y + fillH, color);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ── Slider widgets ────────────────────────────────────────────────────────

    private static class ColorSlider extends SliderWidget {
        private final String label;
        private final IntConsumer setter;

        ColorSlider(int x, int y, int w, int h, String label, IntSupplier getter, IntConsumer setter) {
            super(x, y, w, h, Text.empty(), getter.getAsInt() / 255.0);
            this.label  = label;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ":  " + (int) (value * 255)));
        }

        @Override
        protected void applyValue() {
            setter.accept((int) (value * 255));
        }
    }

    private static class PulseSpeedSlider extends SliderWidget {
        private static final float MIN   = 0.5f;
        private static final float RANGE = 4.5f; // 0.5 → 5.0

        PulseSpeedSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), (ChestOverlayConfig.pulseSpeed - MIN) / RANGE);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(String.format("Speed:  %.1f s / cycle", value * RANGE + MIN)));
        }

        @Override
        protected void applyValue() {
            ChestOverlayConfig.pulseSpeed = (float) (value * RANGE + MIN);
        }
    }
}
