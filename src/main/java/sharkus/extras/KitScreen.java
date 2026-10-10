package sharkus.extras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pre Inventory: Inventar speichern, fehlende Items erscheinen später als Geister-Items. */
public class KitScreen extends Screen {
    private final Screen parent;

    public KitScreen(Screen parent) {
        super(Component.literal("Pre Inventory"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.literal("Aktuelles Inventar speichern"), b -> {
            ItemFx.saveKit(minecraft);
        }).bounds(cx - 100, 90, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Gespeichertes löschen"), b -> ItemFx.clearKit())
                .bounds(cx - 100, 114, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Anzeige: " + (Config.I.preInv ? "AN" : "AUS")), b -> {
            Config.I.preInv = !Config.I.preInv;
            b.setMessage(Component.literal("Anzeige: " + (Config.I.preInv ? "AN" : "AUS")));
            Config.save();
        }).bounds(cx - 100, 138, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx - 50, 170, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int cx = width / 2;
        g.centeredText(font, title, cx, 14, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("1. Inventar so einräumen, wie du es immer haben willst"), cx, 38, 0xFFAAAAAA);
        g.centeredText(font, Component.literal("2. Hier auf \"speichern\" klicken"), cx, 50, 0xFFAAAAAA);
        g.centeredText(font, Component.literal("3. Fehlt später etwas, erscheint es abgedunkelt am alten Platz"), cx, 62, 0xFFAAAAAA);
        g.centeredText(font, Component.literal("(falsches Item im Slot = roter Rahmen)"), cx, 74, 0xFFAAAAAA);
        g.centeredText(font, Component.literal("Gespeichert: " + ItemFx.kitSize() + " Slots"), cx, 198, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.gui.setScreen(parent);
    }
}
