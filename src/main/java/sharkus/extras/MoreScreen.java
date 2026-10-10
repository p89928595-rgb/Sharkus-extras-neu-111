package sharkus.extras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MoreScreen extends Screen {
    private final Screen parent;

    public MoreScreen(Screen parent) {
        super(Component.literal("Einstellungen"));
        this.parent = parent;
    }

    private void button(String text, int row, Runnable r) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> r.run())
                .bounds(width / 2 - 100, 32 + row * 22, 200, 20).build());
    }

    @Override
    protected void init() {
        button("Nametag-Einstellungen...", 0, () -> minecraft.gui.setScreen(new NametagScreen(this)));
        button("Farben, Stile & Team...", 1, () -> minecraft.gui.setScreen(new SettingsScreen(this)));
        button("Hitbox- & Highlighter-Farben...", 2, () -> minecraft.gui.setScreen(new ColorsScreen(this)));
        button("Crosshair Studio...", 3, () -> minecraft.gui.setScreen(new CrosshairStudioScreen(this)));
        button("Pre Inventory...", 4, () -> minecraft.gui.setScreen(new KitScreen(this)));
        button("HUD verschieben & Größe...", 5, () -> minecraft.gui.setScreen(new HudEditScreen(this)));
        button("Fertig", 7, this::onClose);
    }

    private void status(GuiGraphicsExtractor g, String text, boolean ok, int y) {
        g.text(font, text, width / 2 - 100, y, ok ? 0xFF55FF55 : 0xFFFF5555);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);

        int y = 32 + 8 * 22 + 4;
        status(g, "Text-Hook (Nametags): " + (Names.hookCalls > 0 ? "aktiv" : "NICHT aktiv"), Names.hookCalls > 0, y);
        status(g, "Hit-Color-Hook: " + HitColor.status, HitColor.hooked, y + 11);
        status(g, "Inventar-Hook (Highlighter, Pre Inv.): " + (ItemFx.screenHook ? "aktiv" : "noch nicht gesehen"),
                ItemFx.screenHook, y + 22);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
