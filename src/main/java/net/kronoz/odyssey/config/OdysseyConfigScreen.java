package net.kronoz.odyssey.config;

import eu.midnightdust.lib.config.MidnightConfig;
import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.config.OdysseyConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class OdysseyConfigScreen extends Screen {
    private final Screen parent;

    private final List<Row> scrollRows = new ArrayList<>();
    private int contentStartY;
    private int contentHeight;
    private double scroll;
    private static final int VIEW_TOP_MARGIN = 8;
    private static final int VIEW_BOTTOM_MARGIN = 28;

    private CheckboxWidget enableOverride;
    private CheckboxWidget renderOverlay;

    public OdysseyConfigScreen(Screen parent) {
        super(Text.literal("Odyssey — First-Person Arm Tuning"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int w = this.width;
        final int h = this.height;
        final int colW = 160;
        final int gap = 12;
        final int startX = (w - (colW * 2 + gap)) / 2;

        this.addDrawableChild(new Label(
                w / 2 - 100, h / 6 - 18, 200, 10,
                Text.literal("First-person arm & item pose").formatted(Formatting.GOLD)
        ));

        int x = startX;
        int y = h / 6;
        contentStartY = y;

        enableOverride = CheckboxWidget.builder(Text.literal("Enable FP override"), this.textRenderer)
                .pos(x, y).checked(OdysseyConfig.enableFirstPersonOverride).build();
        addScrollable(enableOverride, x, y);

        renderOverlay = CheckboxWidget.builder(Text.literal("Render arm overlay (FP)"), this.textRenderer)
                .pos(x + colW + gap, y).checked(OdysseyConfig.renderArmOverlay).build();
        addScrollable(renderOverlay, x + colW + gap, y);

        y += 26;

        x = startX;
        y = addSectionLabel("Base Offsets (Held Item)", x, y);
        y = addFloatSlider(x, y, colW, "Base X", -1.5f, 1.5f, OdysseyConfig.heldBaseX, v -> OdysseyConfig.heldBaseX = v);
        y = addFloatSlider(x, y, colW, "Base Y", -1.5f, 1.0f,  OdysseyConfig.heldBaseY, v -> OdysseyConfig.heldBaseY = v);
        y = addFloatSlider(x, y, colW, "Base Z", -2.0f, -0.1f, OdysseyConfig.heldBaseZ, v -> OdysseyConfig.heldBaseZ = v);
        y = addFloatSlider(x, y, colW, "Scale",   0.5f,  1.5f,  OdysseyConfig.heldScale, v -> OdysseyConfig.heldScale = v);

        y = addSectionLabel("Follow-Down Curve", x, y + 6);
        y = addFloatSlider(x, y, colW, "Drop Y Max",   0.00f, 0.60f, OdysseyConfig.dropYMax,   v -> OdysseyConfig.dropYMax = v);
        y = addFloatSlider(x, y, colW, "Push Z Max",   0.00f, 0.80f, OdysseyConfig.pushZMax,   v -> OdysseyConfig.pushZMax = v);
        y = addFloatSlider(x, y, colW, "Inward X Max", 0.00f, 0.30f, OdysseyConfig.inwardXMax, v -> OdysseyConfig.inwardXMax = v);

        int x2 = startX + colW + gap;
        int y2 = h / 6 + 26;

        y2 = addSectionLabel("Swing / Equip Translations (Held)", x2, y2);
        y2 = addFloatSlider(x2, y2, colW, "Swing X", -0.30f, 0.30f, OdysseyConfig.swingX, v -> OdysseyConfig.swingX = v);
        y2 = addFloatSlider(x2, y2, colW, "Swing Y", -0.30f, 0.30f, OdysseyConfig.swingY, v -> OdysseyConfig.swingY = v);
        y2 = addFloatSlider(x2, y2, colW, "Swing Z", -0.50f, 0.30f, OdysseyConfig.swingZ, v -> OdysseyConfig.swingZ = v);
        y2 = addFloatSlider(x2, y2, colW, "Equip X", -0.30f, 0.30f, OdysseyConfig.equipX, v -> OdysseyConfig.equipX = v);
        y2 = addFloatSlider(x2, y2, colW, "Equip Y", -0.30f, 0.30f, OdysseyConfig.equipY, v -> OdysseyConfig.equipY = v);
        y2 = addFloatSlider(x2, y2, colW, "Equip Z", -0.50f, 0.30f, OdysseyConfig.equipZ, v -> OdysseyConfig.equipZ = v);

        y2 = addSectionLabel("Intensities", x2, y2 + 6);
        y2 = addFloatSlider(x2, y2, colW, "Swing Intensity", 0.0f, 3.0f, OdysseyConfig.swingIntensity, v -> OdysseyConfig.swingIntensity = v);
        y2 = addFloatSlider(x2, y2, colW, "Equip Intensity", 0.0f, 3.0f, OdysseyConfig.equipIntensity, v -> OdysseyConfig.equipIntensity = v);

        y2 = addSectionLabel("Rotations (deg)", x2, y2 + 6);
        y2 = addFloatSlider(x2, y2, colW, "Base Rot X", -90f, 90f, OdysseyConfig.heldRotX, v -> OdysseyConfig.heldRotX = v);
        y2 = addFloatSlider(x2, y2, colW, "Base Rot Y", -90f, 90f, OdysseyConfig.heldRotY, v -> OdysseyConfig.heldRotY = v);
        y2 = addFloatSlider(x2, y2, colW, "Base Rot Z", -90f, 90f, OdysseyConfig.heldRotZ, v -> OdysseyConfig.heldRotZ = v);
        y2 = addFloatSlider(x2, y2, colW, "Swing Rot X", 0f, 120f, OdysseyConfig.swingRotXDeg, v -> OdysseyConfig.swingRotXDeg = v);
        y2 = addFloatSlider(x2, y2, colW, "Swing Rot Y", 0f, 120f, OdysseyConfig.swingRotYDeg, v -> OdysseyConfig.swingRotYDeg = v);
        y2 = addFloatSlider(x2, y2, colW, "Swing Rot Z", 0f, 120f, OdysseyConfig.swingRotZDeg, v -> OdysseyConfig.swingRotZDeg = v);
        y2 = addFloatSlider(x2, y2, colW, "Equip Roll",  0f, 45f,  OdysseyConfig.equipRollDeg, v -> OdysseyConfig.equipRollDeg = v);
        y2 = addFloatSlider(x2, y2, colW, "Pitch X",     0f, 90f,  OdysseyConfig.pitchXDeg,    v -> OdysseyConfig.pitchXDeg = v);

        y2 = addSectionLabel("Arm (FP Overlay) — Base", x2, y2 + 6);
        y2 = addFloatSlider(x2, y2, colW, "Arm Base X", -1.0f, 1.0f, OdysseyConfig.armBaseX, v -> OdysseyConfig.armBaseX = v);
        y2 = addFloatSlider(x2, y2, colW, "Arm Base Y", -1.0f, 1.0f, OdysseyConfig.armBaseY, v -> OdysseyConfig.armBaseY = v);
        y2 = addFloatSlider(x2, y2, colW, "Arm Base Z", -1.5f, 0.3f, OdysseyConfig.armBaseZ, v -> OdysseyConfig.armBaseZ = v);
        y2 = addFloatSlider(x2, y2, colW, "Arm Scale",   0.5f, 1.5f,  OdysseyConfig.armScale, v -> OdysseyConfig.armScale = v);

        y2 = addSectionLabel("Arm (FP Overlay) — Rotations (deg)", x2, y2 + 6);
        y2 = addFloatSlider(x2, y2, colW, "Base Rot X", -90f, 90f, OdysseyConfig.armRotX, v -> OdysseyConfig.armRotX = v);
        y2 = addFloatSlider(x2, y2, colW, "Base Rot Y", -90f, 90f, OdysseyConfig.armRotY, v -> OdysseyConfig.armRotY = v);
        y2 = addFloatSlider(x2, y2, colW, "Base Rot Z", -90f, 90f, OdysseyConfig.armRotZ, v -> OdysseyConfig.armRotZ = v);

        y2 = addSectionLabel("Arm (FP Overlay) — Swing/Equip/Pitch (deg)", x2, y2 + 6);
        y2 = addFloatSlider(x2, y2, colW, "Swing Rot X", 0f, 120f, OdysseyConfig.armSwingRotXDeg, v -> OdysseyConfig.armSwingRotXDeg = v);
        y2 = addFloatSlider(x2, y2, colW, "Swing Rot Y", 0f, 120f, OdysseyConfig.armSwingRotYDeg, v -> OdysseyConfig.armSwingRotYDeg = v);
        y2 = addFloatSlider(x2, y2, colW, "Swing Rot Z", 0f, 120f, OdysseyConfig.armSwingRotZDeg, v -> OdysseyConfig.armSwingRotZDeg = v);
        y2 = addFloatSlider(x2, y2, colW, "Equip Roll",  0f, 45f,  OdysseyConfig.armEquipRollDeg,  v -> OdysseyConfig.armEquipRollDeg = v);
        y2 = addFloatSlider(x2, y2, colW, "Pitch X",     0f, 90f,  OdysseyConfig.armPitchXDeg,     v -> OdysseyConfig.armPitchXDeg = v);

        // Done (non-scrolled)
        int bottomY = Math.max(y, y2) + 16;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done").formatted(Formatting.GREEN), b -> closeAndSave())
                .dimensions(w / 2 - 75, Math.min(bottomY, h - 24), 150, 20).build());

        contentHeight = Math.max(y, y2) - contentStartY;
        clampScroll();
        applyScrollToWidgets();
    }

    private int addSectionLabel(String title, int x, int y) {
        Label lab = new Label(x, y, 160, 10, Text.literal(title).formatted(Formatting.YELLOW));
        addScrollable(lab, x, y);
        return y + 12;
    }

    private int addFloatSlider(int x, int y, int width, String label, float min, float max, float current, Consumer<Float> onChange) {
        FloatSlider s = new FloatSlider(x, y, width, 20, Text.literal(label), current, min, max, onChange);
        addScrollable(s, x, y);
        return y + 24;
    }

    private void addScrollable(ClickableWidget w, int baseX, int baseY) {
        this.addDrawableChild(w);
        scrollRows.add(new Row(w, baseX, baseY));
    }

    private void closeAndSave() {
        OdysseyConfig.enableFirstPersonOverride = enableOverride.isChecked();
        OdysseyConfig.renderArmOverlay = renderOverlay.isChecked();
        MidnightConfig.write(Odyssey.MODID);
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override public void close() { closeAndSave(); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int viewTop = contentStartY - VIEW_TOP_MARGIN;
        int viewBottom = this.height - VIEW_BOTTOM_MARGIN;
        if (mouseY >= viewTop && mouseY <= viewBottom) {
            scroll -= verticalAmount * 16.0;
            clampScroll();
            applyScrollToWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void clampScroll() {
        int viewHeight = (this.height - VIEW_BOTTOM_MARGIN) - (contentStartY - VIEW_TOP_MARGIN);
        int max = Math.max(0, contentHeight - viewHeight);
        if (scroll < 0) scroll = 0;
        if (scroll > max) scroll = max;
    }

    private void applyScrollToWidgets() {
        int viewTop = contentStartY - VIEW_TOP_MARGIN;
        int viewBottom = this.height - VIEW_BOTTOM_MARGIN;

        for (Row r : scrollRows) {
            int newY = (int)Math.round(r.baseY - scroll);
            r.w.setX(r.baseX);
            r.w.setY(newY);
            r.w.visible = newY + r.w.getHeight() >= viewTop && newY <= viewBottom;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, contentStartY - 18, 0xFFFFFF);
        drawScrollbar(ctx);
    }

    private void drawScrollbar(DrawContext ctx) {
        int viewTop = contentStartY - VIEW_TOP_MARGIN;
        int viewBottom = this.height - VIEW_BOTTOM_MARGIN;
        int viewHeight = viewBottom - viewTop;
        if (contentHeight <= viewHeight) return;

        int barX = this.width - 6;
        int barW = 3;

        double ratio = viewHeight / (double) contentHeight;
        int thumbH = Math.max(16, (int)Math.round(viewHeight * ratio));
        int maxScroll = contentHeight - viewHeight;
        int thumbY = (int)Math.round(viewTop + (scroll / maxScroll) * (viewHeight - thumbH));

        ctx.fill(barX, viewTop, barX + barW, viewBottom, 0x55000000);
        ctx.fill(barX, thumbY, barX + barW, thumbY + thumbH, 0xFFAAAAAA);
    }

    private static final class Row {
        final ClickableWidget w;
        final int baseX, baseY;
        Row(ClickableWidget w, int baseX, int baseY) { this.w = w; this.baseX = baseX; this.baseY = baseY; }
    }

    private static final class Label extends ClickableWidget {
        private final Text text;
        Label(int x, int y, int w, int h, Text text) {
            super(x, y, w, h, text);
            this.text = text;
            this.active = false;
            this.visible = true;
        }
        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.drawText(MinecraftClient.getInstance().textRenderer, text, getX(), getY(), 0xFFFFFF, false);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {

        }
    }

    private static final class FloatSlider extends SliderWidget {
        private final String label;
        private final float min, max;
        private float valueF;
        private final Consumer<Float> onChange;

        FloatSlider(int x, int y, int w, int h, Text label, float current, float min, float max, Consumer<Float> onChange) {
            super(x, y, w, h, label, normalize(current, min, max));
            this.label = label.getString();
            this.min = min;
            this.max = max;
            this.valueF = current;
            this.onChange = onChange;
            updateMessage();
        }
        @Override protected void updateMessage() {
            this.setMessage(Text.literal(label + ": " + String.format("%.3f", valueF)));
        }
        @Override protected void applyValue() {
            this.valueF = denormalize(this.value, min, max);
            onChange.accept(this.valueF);
            updateMessage();
        }
        private static double normalize(float v, float min, float max) {
            return Math.max(0d, Math.min(1d, (v - min) / (max - min)));
        }
        private static float denormalize(double n, float min, float max) {
            return (float)(min + n * (max - min));
        }
    }
}
