package sharkus.extras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class MenuScreen extends Screen {
    private static int page = 0;
    private final Screen parent;

    public MenuScreen(Screen parent) {
        super(Component.literal("Sharkus Extras"));
        this.parent = parent;
    }

    private int colX(int col) {
        return width / 2 - 150 - 2 + col * 154;
    }

    private void toggle(String name, int col, int row, Supplier<Boolean> get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(label(name, get.get()), b -> {
            set.accept(!get.get());
            b.setMessage(label(name, get.get()));
            Config.save();
        }).bounds(colX(col), 32 + row * 22, 150, 20).build());
    }

    private void soon(String name, int col, int row) {
        Button b = Button.builder(Component.literal(name + " (bald)"), x -> { })
                .bounds(colX(col), 32 + row * 22, 150, 20).build();
        b.active = false;
        addRenderableWidget(b);
    }

    private static Component label(String n, boolean on) {
        return Component.literal(n + ": " + (on ? "AN" : "AUS"));
    }

    @Override
    protected void init() {
        Config c = Config.I;
        if (page == 0) {
            toggle("Armor Display", 0, 0, () -> c.armorDisplay, v -> c.armorDisplay = v);
            toggle("Health Indicator", 1, 0, () -> c.healthIndicator, v -> c.healthIndicator = v);
            toggle("Hit Color", 0, 1, () -> c.hitColor, v -> c.hitColor = v);
            toggle("Hitbox Colors", 1, 1, () -> c.hitboxColors, v -> c.hitboxColors = v);
            toggle("Block Outlines", 0, 2, () -> c.blockOutlines, v -> c.blockOutlines = v);
            toggle("Team Tracker", 1, 2, () -> c.teamTracker, v -> c.teamTracker = v);
            toggle("Shield Status", 0, 3, () -> c.shieldStatus, v -> c.shieldStatus = v);
            toggle("Highlighter", 1, 3, () -> c.highlighter, v -> c.highlighter = v);
            toggle("Crosshair Indicator", 0, 4, () -> c.crosshairIndicator, v -> c.crosshairIndicator = v);
            toggle("Crosshair Studio", 1, 4, () -> c.crosshairStudio, v -> c.crosshairStudio = v);
            toggle("Nametags", 0, 5, () -> c.nametags, v -> c.nametags = v);
            toggle("Toggle Sprint", 1, 5, () -> c.toggleSprint, v -> c.toggleSprint = v);
            soon("Item Physics", 0, 6);
            soon("Motion Blur", 1, 6);
        } else {
            toggle("FPS", 0, 0, () -> c.fps, v -> c.fps = v);
            toggle("CPS", 1, 0, () -> c.cps, v -> c.cps = v);
            toggle("Ping", 0, 1, () -> c.ping, v -> c.ping = v);
            toggle("Keystrokes", 1, 1, () -> c.keystrokes, v -> c.keystrokes = v);
            toggle("Totem Counter", 0, 2, () -> c.totemCounter, v -> c.totemCounter = v);
            toggle("Item Counter", 1, 2, () -> c.itemCounter, v -> c.itemCounter = v);
            toggle("Combo Counter", 0, 3, () -> c.combo, v -> c.combo = v);
            toggle("Reach Display", 1, 3, () -> c.reach, v -> c.reach = v);
            toggle("Jump Reset Counter", 0, 4, () -> c.jumpReset, v -> c.jumpReset = v);
            toggle("Durability Warnung", 1, 4, () -> c.durWarn, v -> c.durWarn = v);
            toggle("Inventar-Vorschau", 0, 5, () -> c.invHud, v -> c.invHud = v);
            toggle("Pre Inventory", 1, 5, () -> c.preInv, v -> c.preInv = v);
        }

        addRenderableWidget(Button.builder(Component.literal(page == 0 ? "Seite 2: Anzeigen >" : "< Seite 1: Kampf"), b -> {
            page = 1 - page;
            minecraft.gui.setScreen(new MenuScreen(parent));
        }).bounds(colX(0), 32 + 7 * 22 + 2, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Einstellungen..."),
                b -> minecraft.gui.setScreen(new MoreScreen(this)))
                .bounds(colX(1), 32 + 7 * 22 + 2, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(width / 2 - 50, 32 + 8 * 22 + 6, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.gui.setScreen(parent);
    }
}
