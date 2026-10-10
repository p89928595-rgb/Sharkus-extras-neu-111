package sharkus.extras;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Highlighter (Items einfärben) und Pre Inventory (gespeichertes Inventar als Geister-Items). */
public final class ItemFx {
    /** Wird true, sobald der Inventar-Mixin gelaufen ist (für die Statusanzeige). */
    public static volatile boolean screenHook = false;

    private static final Map<Integer, ItemStack> kitStacks = new HashMap<>();
    private static int kitBuiltFor = -1;

    private ItemFx() { }

    public static String key(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    private static int colorFor(ItemStack st) {
        Config c = Config.I;
        String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        if (path.contains("totem")) return c.hlTotemRgb;
        if (path.equals("ender_pearl")) return c.hlPearlRgb;
        if (path.contains("golden_apple")) return c.hlAppleRgb;
        if (path.contains("potion")) return c.hlPotionRgb;
        if (path.endsWith("_sword") || path.endsWith("_axe") || path.equals("mace") || path.equals("trident")
                || path.equals("bow") || path.equals("crossbow")) return c.hlWeaponRgb;
        if (path.endsWith("_helmet") || path.endsWith("_chestplate") || path.endsWith("_leggings")
                || path.endsWith("_boots") || path.equals("elytra") || path.equals("shield")) return c.hlArmorRgb;
        if (st.isEnchanted()) return c.hlEnchantedRgb;
        return -1;
    }

    public static void highlight(GuiGraphicsExtractor g, ItemStack st, int x, int y) {
        if (!Config.I.highlighter || st.isEmpty()) return;
        int rgb = colorFor(st);
        if (rgb < 0) return;
        rgb &= 0xFFFFFF;
        g.fill(x, y, x + 16, y + 16, (0x40 << 24) | rgb);
        int solid = 0xFF000000 | rgb;
        g.fill(x, y, x + 16, y + 1, solid);
        g.fill(x, y + 15, x + 16, y + 16, solid);
        g.fill(x, y, x + 1, y + 16, solid);
        g.fill(x + 15, y, x + 16, y + 16, solid);
    }

    // ---------------- Pre Inventory ----------------

    private static void rebuildKit() {
        kitStacks.clear();
        for (Map.Entry<Integer, String> e : Config.I.kit.entrySet()) {
            try {
                ItemStack st = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(e.getValue())));
                if (!st.isEmpty()) kitStacks.put(e.getKey(), st);
            } catch (Throwable ignored) { }
        }
        kitBuiltFor = Config.I.kit.hashCode();
    }

    public static ItemStack kitStack(int slot) {
        if (kitBuiltFor != Config.I.kit.hashCode()) rebuildKit();
        return kitStacks.get(slot);
    }

    public static int kitSize() {
        return Config.I.kit.size();
    }

    /** Speichert das aktuelle Inventar (Hotbar, Inventar, Rüstung, Zweithand). */
    public static void saveKit(Minecraft mc) {
        if (mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        Config.I.kit.clear();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty()) Config.I.kit.put(i, key(st));
        }
        kitBuiltFor = -1;
        Config.save();
    }

    public static void clearKit() {
        Config.I.kit.clear();
        kitBuiltFor = -1;
        Config.save();
    }

    public static void ghost(GuiGraphicsExtractor g, int invIndex, ItemStack cur, int x, int y) {
        if (!Config.I.preInv) return;
        ItemStack want = kitStack(invIndex);
        if (want == null) return;
        if (cur.isEmpty()) {
            g.item(want, x, y);
            g.fill(x, y, x + 16, y + 16, 0xA0303030);
        } else if (!cur.is(want.getItem())) {
            int red = 0xFFFF3030;
            g.fill(x, y, x + 16, y + 1, red);
            g.fill(x, y + 15, x + 16, y + 16, red);
            g.fill(x, y, x + 1, y + 16, red);
            g.fill(x + 15, y, x + 16, y + 16, red);
        }
    }
}
