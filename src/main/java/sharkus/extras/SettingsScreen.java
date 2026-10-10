package sharkus.extras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends Screen {
    private static final String[] HEALTH = {"Herzen", "Kompakt", "Leiste"};
    private static final String[] CROSS = {"Kreuz", "Punkt", "Ring", "Ecken", "X"};

    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Farben, Stile & Team"));
        this.parent = parent;
    }

    private static int parseHex(String v, int old) {
        try {
            if (v.length() == 6) return Integer.parseInt(v, 16);
        } catch (NumberFormatException ignored) { }
        return old;
    }

    private static int parseNum(String v, int old, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(v.trim())));
        } catch (NumberFormatException ignored) { }
        return old;
    }

    @Override
    protected void init() {
        Config c = Config.I;
        int cx = width / 2;

        EditBox hitHex = new EditBox(font, cx - 100, 44, 95, 20, Component.literal("Hit Farbe"));
        hitHex.setMaxLength(6);
        hitHex.setValue(String.format("%06X", c.hitRgb & 0xFFFFFF));
        hitHex.setResponder(v -> { c.hitRgb = parseHex(v, c.hitRgb); Config.save(); });
        addRenderableWidget(hitHex);

        EditBox hitAlpha = new EditBox(font, cx + 5, 44, 60, 20, Component.literal("Staerke"));
        hitAlpha.setMaxLength(3);
        hitAlpha.setValue(String.valueOf(c.hitAlpha));
        hitAlpha.setResponder(v -> { c.hitAlpha = parseNum(v, c.hitAlpha, 0, 100); Config.save(); });
        addRenderableWidget(hitAlpha);

        EditBox outHex = new EditBox(font, cx - 100, 80, 95, 20, Component.literal("Outline Farbe"));
        outHex.setMaxLength(6);
        outHex.setValue(String.format("%06X", c.outlineRgb & 0xFFFFFF));
        outHex.setResponder(v -> { c.outlineRgb = parseHex(v, c.outlineRgb); Config.save(); });
        addRenderableWidget(outHex);

        EditBox outThick = new EditBox(font, cx + 5, 80, 60, 20, Component.literal("Dicke"));
        outThick.setMaxLength(1);
        outThick.setValue(String.valueOf(c.outlineThickness));
        outThick.setResponder(v -> { c.outlineThickness = parseNum(v, c.outlineThickness, 1, 4); Config.save(); });
        addRenderableWidget(outThick);

        EditBox team = new EditBox(font, cx - 100, 152, 200, 20, Component.literal("Team"));
        team.setMaxLength(200);
        team.setValue(c.teamNames);
        team.setResponder(v -> { c.teamNames = v; Config.save(); });
        addRenderableWidget(team);

        addRenderableWidget(Button.builder(Component.literal("Health-Stil: " + HEALTH[c.healthStyle % HEALTH.length]), b -> {
            c.healthStyle = (c.healthStyle + 1) % HEALTH.length;
            b.setMessage(Component.literal("Health-Stil: " + HEALTH[c.healthStyle]));
            Config.save();
        }).bounds(cx - 152, 182, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Indicator-Stil: " + CROSS[c.crosshairStyle % CROSS.length]), b -> {
            c.crosshairStyle = (c.crosshairStyle + 1) % CROSS.length;
            b.setMessage(Component.literal("Indicator-Stil: " + CROSS[c.crosshairStyle]));
            Config.save();
        }).bounds(cx + 2, 182, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Ladebalken: " + (c.crosshairBar ? "AN" : "AUS")), b -> {
            c.crosshairBar = !c.crosshairBar;
            b.setMessage(Component.literal("Ladebalken: " + (c.crosshairBar ? "AN" : "AUS")));
            Config.save();
        }).bounds(cx - 152, 206, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx + 2, 206, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Config c = Config.I;
        int cx = width / 2;
        g.centeredText(font, title, cx, 12, 0xFFFFFFFF);

        g.text(font, "Hit Color: Farbe (Hex)  |  Staerke 0-100", cx - 100, 33, 0xFFAAAAAA);
        g.fill(cx + 72, 44, cx + 92, 64, 0xFF000000 | (c.hitRgb & 0xFFFFFF));

        g.text(font, "Block Outline: Farbe (Hex)  |  Dicke 1-4", cx - 100, 69, 0xFFAAAAAA);
        g.fill(cx + 72, 80, cx + 92, 100, 0xFF000000 | (c.outlineRgb & 0xFFFFFF));

        g.text(font, "Team-Spieler (Namen, mit Komma getrennt)", cx - 100, 141, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.gui.setScreen(parent);
    }
}
