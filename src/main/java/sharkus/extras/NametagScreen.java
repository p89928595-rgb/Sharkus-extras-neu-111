package sharkus.extras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class NametagScreen extends Screen {
    private final Screen parent;
    private EditBox own, from, to;

    public NametagScreen(Screen parent) {
        super(Component.literal("Nametags"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = Config.I;
        int cx = width / 2;

        own = new EditBox(font, cx - 100, 50, 200, 20, Component.literal("Eigener Name"));
        own.setMaxLength(32);
        own.setValue(c.ownNameOverride == null ? "" : c.ownNameOverride);
        own.setResponder(v -> { c.ownNameOverride = v; Config.save(); });
        addRenderableWidget(own);

        from = new EditBox(font, cx - 100, 100, 95, 20, Component.literal("Original"));
        to = new EditBox(font, cx + 5, 100, 95, 20, Component.literal("Neu"));
        from.setMaxLength(32);
        to.setMaxLength(32);
        addRenderableWidget(from);
        addRenderableWidget(to);

        addRenderableWidget(Button.builder(Component.literal("Hinzufügen"), b -> {
            String f = from.getValue().trim();
            String t = to.getValue().trim();
            if (f.isEmpty() || t.isEmpty()) return;
            c.nameReplacements.put(f, t);
            Config.save();
            minecraft.gui.setScreen(new NametagScreen(parent));
        }).bounds(cx - 50, 126, 100, 20).build());

        int y = 156;
        for (String key : new ArrayList<>(c.nameReplacements.keySet())) {
            if (y > height - 50) break;
            addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                c.nameReplacements.remove(key);
                Config.save();
                minecraft.gui.setScreen(new NametagScreen(parent));
            }).bounds(cx + 90, y, 20, 20).build());
            y += 22;
        }

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx - 50, height - 30, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int cx = width / 2;
        g.centeredText(font, title, cx, 18, 0xFFFFFFFF);
        g.text(font, "Eigener Name (leer = normal)", cx - 100, 38, 0xFFAAAAAA);
        g.text(font, "Name ändern:  Original  ->  Neuer Name", cx - 100, 88, 0xFFAAAAAA);

        int y = 156;
        for (var e : Config.I.nameReplacements.entrySet()) {
            if (y > height - 50) break;
            g.text(font, e.getKey() + "  ->  " + e.getValue(), cx - 100, y + 6, 0xFFFFFFFF);
            y += 22;
        }
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.gui.setScreen(parent);
    }
}
