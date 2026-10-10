package sharkus.extras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CrosshairStudioScreen extends Screen {
    private final Screen parent;

    public CrosshairStudioScreen(Screen parent) {
        super(Component.literal("Crosshair Studio"));
        this.parent = parent;
    }

    private static int num(String v, int old, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(v.trim())));
        } catch (NumberFormatException ignored) { }
        return old;
    }

    private static String on(String name, boolean b) {
        return name + ": " + (b ? "AN" : "AUS");
    }

    @Override
    protected void init() {
        Config c = Config.I;
        int cx = width / 2;

        addRenderableWidget(Button.builder(Component.literal(on("Studio", c.crosshairStudio)), b -> {
            c.crosshairStudio = !c.crosshairStudio;
            b.setMessage(Component.literal(on("Studio", c.crosshairStudio)));
            Config.save();
        }).bounds(cx - 152, 100, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Form: " + CrosshairDraw.SHAPES[c.csShape % 6]), b -> {
            c.csShape = (c.csShape + 1) % CrosshairDraw.SHAPES.length;
            b.setMessage(Component.literal("Form: " + CrosshairDraw.SHAPES[c.csShape]));
            Config.save();
        }).bounds(cx + 2, 100, 150, 20).build());

        EditBox size = new EditBox(font, cx - 152, 142, 72, 20, Component.literal("Groesse"));
        size.setMaxLength(2);
        size.setValue(String.valueOf(c.csSize));
        size.setResponder(v -> { c.csSize = num(v, c.csSize, 1, 30); Config.save(); });
        addRenderableWidget(size);

        EditBox thick = new EditBox(font, cx - 76, 142, 72, 20, Component.literal("Dicke"));
        thick.setMaxLength(1);
        thick.setValue(String.valueOf(c.csThick));
        thick.setResponder(v -> { c.csThick = num(v, c.csThick, 1, 6); Config.save(); });
        addRenderableWidget(thick);

        EditBox gap = new EditBox(font, cx, 142, 72, 20, Component.literal("Luecke"));
        gap.setMaxLength(2);
        gap.setValue(String.valueOf(c.csGap));
        gap.setResponder(v -> { c.csGap = num(v, c.csGap, 0, 20); Config.save(); });
        addRenderableWidget(gap);

        EditBox hex = new EditBox(font, cx + 76, 142, 76, 20, Component.literal("Farbe"));
        hex.setMaxLength(6);
        hex.setValue(String.format("%06X", c.csRgb & 0xFFFFFF));
        hex.setResponder(v -> {
            try {
                if (v.length() == 6) { c.csRgb = Integer.parseInt(v, 16); Config.save(); }
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(hex);

        addRenderableWidget(Button.builder(Component.literal(on("Umrandung", c.csOutline)), b -> {
            c.csOutline = !c.csOutline;
            b.setMessage(Component.literal(on("Umrandung", c.csOutline)));
            Config.save();
        }).bounds(cx - 152, 170, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal(on("Mittelpunkt", c.csDot)), b -> {
            c.csDot = !c.csDot;
            b.setMessage(Component.literal(on("Mittelpunkt", c.csDot)));
            Config.save();
        }).bounds(cx + 2, 170, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal(on("Vanilla ausblenden", c.csHideVanilla)), b -> {
            c.csHideVanilla = !c.csHideVanilla;
            b.setMessage(Component.literal(on("Vanilla ausblenden", c.csHideVanilla)));
            Config.save();
        }).bounds(cx - 152, 194, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx + 2, 194, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Config c = Config.I;
        int cx = width / 2;
        g.centeredText(font, title, cx, 12, 0xFFFFFFFF);

        // Vorschau
        g.fill(cx - 40, 28, cx + 40, 92, 0xFF303030);
        g.fill(cx - 39, 29, cx + 39, 91, 0xFF606060);
        CrosshairDraw.draw(g, c.csShape, c.csSize, c.csThick, c.csGap, 0xFF000000 | (c.csRgb & 0xFFFFFF),
                c.csOutline, c.csDot, cx, 60);

        g.text(font, "Groesse", cx - 152, 131, 0xFFAAAAAA);
        g.text(font, "Dicke", cx - 76, 131, 0xFFAAAAAA);
        g.text(font, "Luecke", cx, 131, 0xFFAAAAAA);
        g.text(font, "Farbe (Hex)", cx + 76, 131, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.gui.setScreen(parent);
    }
}
