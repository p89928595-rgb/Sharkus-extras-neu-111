package sharkus.extras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Farben für Hitbox Colors und Highlighter, dazu die Item-Counter-Liste. */
public class ColorsScreen extends Screen {
    private final Screen parent;

    private record Row(String label, IntSupplier get, IntConsumer set) { }

    private final Row[] left;
    private final Row[] right;

    public ColorsScreen(Screen parent) {
        super(Component.literal("Hitbox- & Highlighter-Farben"));
        this.parent = parent;
        Config c = Config.I;
        left = new Row[]{
                new Row("Hitbox Spieler", () -> c.hbPlayerRgb, v -> c.hbPlayerRgb = v),
                new Row("Hitbox Team", () -> c.hbTeamRgb, v -> c.hbTeamRgb = v),
                new Row("Hitbox Monster", () -> c.hbHostileRgb, v -> c.hbHostileRgb = v),
                new Row("Hitbox Tiere", () -> c.hbPassiveRgb, v -> c.hbPassiveRgb = v),
                new Row("Hitbox Items", () -> c.hbItemRgb, v -> c.hbItemRgb = v),
                new Row("Hitbox Geschosse", () -> c.hbProjectileRgb, v -> c.hbProjectileRgb = v)
        };
        right = new Row[]{
                new Row("Waffen", () -> c.hlWeaponRgb, v -> c.hlWeaponRgb = v),
                new Row("Rüstung", () -> c.hlArmorRgb, v -> c.hlArmorRgb = v),
                new Row("Totem", () -> c.hlTotemRgb, v -> c.hlTotemRgb = v),
                new Row("Perlen", () -> c.hlPearlRgb, v -> c.hlPearlRgb = v),
                new Row("Äpfel", () -> c.hlAppleRgb, v -> c.hlAppleRgb = v),
                new Row("Tränke", () -> c.hlPotionRgb, v -> c.hlPotionRgb = v),
                new Row("Verzaubert", () -> c.hlEnchantedRgb, v -> c.hlEnchantedRgb = v)
        };
    }

    private void addRows(Row[] rows, int labelX) {
        int boxX = labelX + 100;
        for (int i = 0; i < rows.length; i++) {
            Row r = rows[i];
            EditBox box = new EditBox(font, boxX, 52 + i * 22, 58, 20, Component.literal(r.label()));
            box.setMaxLength(6);
            box.setValue(String.format("%06X", r.get().getAsInt() & 0xFFFFFF));
            box.setResponder(v -> {
                try {
                    if (v.length() == 6) { r.set().accept(Integer.parseInt(v, 16)); Config.save(); }
                } catch (NumberFormatException ignored) { }
            });
            addRenderableWidget(box);
        }
    }

    @Override
    protected void init() {
        int cx = width / 2;
        addRows(left, cx - 232);
        addRows(right, cx + 8);

        EditBox items = new EditBox(font, cx - 150, 52 + 7 * 22 + 14, 300, 20, Component.literal("Item Counter"));
        items.setMaxLength(300);
        items.setValue(Config.I.itemCounterIds);
        items.setResponder(v -> { Config.I.itemCounterIds = v; Config.save(); });
        addRenderableWidget(items);

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(cx - 50, 52 + 7 * 22 + 40, 100, 20).build());
    }

    private void drawRows(GuiGraphicsExtractor g, Row[] rows, int labelX) {
        for (int i = 0; i < rows.length; i++) {
            int y = 52 + i * 22;
            g.text(font, rows[i].label(), labelX, y + 6, 0xFFFFFFFF);
            g.fill(labelX + 162, y, labelX + 182, y + 20, 0xFF000000 | (rows[i].get().getAsInt() & 0xFFFFFF));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int cx = width / 2;
        g.centeredText(font, title, cx, 12, 0xFFFFFFFF);
        g.text(font, "Hitbox Colors (Hex)", cx - 232, 38, 0xFFAAAAAA);
        g.text(font, "Highlighter: Inventar-Farben (Hex)", cx + 8, 38, 0xFFAAAAAA);
        drawRows(g, left, cx - 232);
        drawRows(g, right, cx + 8);
        g.text(font, "Item Counter: Items (Komma getrennt, z.B. minecraft:ender_pearl)", cx - 150, 52 + 7 * 22 + 2, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.gui.setScreen(parent);
    }
}
