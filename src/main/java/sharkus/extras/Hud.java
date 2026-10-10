package sharkus.extras;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Hud {
    /** Verschiebbare HUD-Elemente: Schlüssel, Name, Standard-Position, Größe bei Maßstab 1. */
    public record El(String key, String label, int dx, int dy, int w, int h) { }

    public static final List<El> ELS = List.of(
            new El("fps", "FPS", 4, 4, 50, 10),
            new El("cps", "CPS", 4, 14, 50, 10),
            new El("ping", "Ping", 4, 24, 70, 10),
            new El("sprint", "Sprint", 4, 34, 90, 10),
            new El("armor", "Armor", 4, 48, 50, 72),
            new El("totem", "Totems", 60, 4, 60, 16),
            new El("combo", "Combo", 60, 22, 60, 10),
            new El("reach", "Reach", 60, 32, 70, 10),
            new El("jr", "Jump Reset", 60, 42, 90, 10),
            new El("items", "Item Counter", 60, 56, 110, 40),
            new El("inv", "Inventar", 190, 4, 166, 58),
            new El("keys", "Keystrokes", 4, 130, 64, 74),
            new El("shield", "Schild", -1, -1, 40, 30));

    private static final ArrayDeque<Long> clicks = new ArrayDeque<>();
    private static boolean wasDown;

    // Schild-Cooldown
    private static boolean shieldCdPrev;
    private static long shieldCdStart;
    private static final long SHIELD_CD_MS = 5000;

    private static boolean sprintWasOn;

    // Zähler
    private static long tickN;
    private static LivingEntity pendTarget;
    private static long pendTick;
    private static int pendHurt;
    private static double pendReach;
    private static int comboN;
    private static long lastHitMs;
    private static double lastReach = -1;
    private static long lastReachMs;
    private static int prevPlayerHurt;
    private static int jrHits, jrDone;
    private static long jrWindowEnd = -1;
    private static boolean jrCounted;
    private static long lastJumpTick = -100;
    private static double prevVy;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // ------------------------------------------------------------------
    // Positionen
    // ------------------------------------------------------------------

    public static int X(String key, int sw) {
        int[] p = Config.I.hudPos.get(key);
        if (p != null) return p[0];
        for (El e : ELS) if (e.key().equals(key)) return e.dx() == -1 ? sw / 2 - 20 : e.dx();
        return 4;
    }

    public static int Y(String key, int sh) {
        int[] p = Config.I.hudPos.get(key);
        if (p != null) return p[1];
        for (El e : ELS) if (e.key().equals(key)) return e.dy() == -1 ? sh - 90 : e.dy();
        return 4;
    }

    private static void at(GuiGraphicsExtractor g, String key, int sw, int sh, Runnable r) {
        float s = Config.I.sc(key);
        Matrix3x2fStack m = g.pose();
        m.pushMatrix();
        m.translate((float) X(key, sw), (float) Y(key, sh));
        m.scale(s, s);
        r.run();
        m.popMatrix();
    }

    // ------------------------------------------------------------------
    // Tick: Klicks, Sprint, Zähler
    // ------------------------------------------------------------------

    public static void tick(Minecraft mc) {
        if (mc.getWindow() == null) return;
        Config c = Config.I;
        boolean was = wasDown;
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !was) clicks.add(System.currentTimeMillis());
        wasDown = down;
        boolean rising = down && !was;
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peek() > 1000) clicks.poll();

        if (mc.player != null && mc.level != null) {
            tickN++;

            boolean cd = mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD));
            if (cd && !shieldCdPrev) shieldCdStart = now;
            shieldCdPrev = cd;

            // Toggle Sprint
            if (c.toggleSprint) {
                if (mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown()) {
                    mc.options.keySprint.setDown(true);
                }
                sprintWasOn = true;
            } else if (sprintWasOn) {
                mc.options.keySprint.setDown(false);
                sprintWasOn = false;
            }

            // --- Angriff erkennen (Combo, Reach, Haltbarkeits-Warnung) ---
            if (rising) {
                LivingEntity t = target(mc);
                if (t != null) {
                    pendTarget = t;
                    pendTick = tickN;
                    pendHurt = t.hurtTime;
                    if (mc.hitResult instanceof EntityHitResult ehr) {
                        pendReach = ehr.getLocation().distanceTo(mc.player.getEyePosition());
                    }
                }
            }
            if (pendTarget != null) {
                if (pendTarget.hurtTime > pendHurt) {
                    comboN++;
                    lastHitMs = now;
                    lastReach = pendReach;
                    lastReachMs = now;
                    if (c.durWarn) warnTool(mc);
                    pendTarget = null;
                } else if (tickN - pendTick > 4) {
                    pendTarget = null;
                }
            }
            if (comboN > 0 && now - lastHitMs > 3000) comboN = 0;

            // --- Selbst getroffen (Combo-Ende, Jump Reset, Rüstungs-Warnung) ---
            int ph = mc.player.hurtTime;
            if (ph > prevPlayerHurt && ph > 0) {
                comboN = 0;
                jrHits++;
                jrWindowEnd = tickN + 4;
                jrCounted = false;
                if (tickN - lastJumpTick <= 1) {
                    jrDone++;
                    jrCounted = true;
                }
                if (c.durWarn) warnArmor(mc);
            }
            prevPlayerHurt = ph;

            // --- Sprung erkennen ---
            double vy = mc.player.getDeltaMovement().y;
            if (vy > 0.35 && prevVy <= 0.35) {
                lastJumpTick = tickN;
                if (tickN <= jrWindowEnd && !jrCounted) {
                    jrDone++;
                    jrCounted = true;
                }
            }
            prevVy = vy;
        }
        HitColor.tick();
    }

    // ------------------------------------------------------------------
    // Haltbarkeits-Warnung (Break-Geräusch ab 30 Haltbarkeit)
    // ------------------------------------------------------------------

    private static boolean low(ItemStack st) {
        if (st.isEmpty() || !st.isDamageableItem()) return false;
        int left = st.getMaxDamage() - st.getDamageValue();
        return left > 0 && left <= 30;
    }

    private static void warnTool(Minecraft mc) {
        if (low(mc.player.getMainHandItem())) breakSound(mc);
    }

    private static void warnArmor(Minecraft mc) {
        for (EquipmentSlot s : ARMOR) {
            if (low(mc.player.getItemBySlot(s))) {
                breakSound(mc);
                return;
            }
        }
    }

    private static void breakSound(Minecraft mc) {
        try {
            Object o = SoundEvents.class.getField("ITEM_BREAK").get(null);
            SoundEvent se = o instanceof Holder<?> h ? (SoundEvent) h.value() : (SoundEvent) o;
            mc.player.playSound(se, 1.0f, 1.0f);
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("sharkus-extras", "hud"), Hud::render);
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR,
                original -> (g, d) -> renderCrosshair(original, g, d));
    }

    public static boolean isTeammate(Minecraft mc, Player p, Config c) {
        if (p.getTeam() != null && mc.player.getTeam() != null && p.isAlliedTo(mc.player)) return true;
        String n = p.getName().getString();
        for (String s : c.teamNames.split(",")) {
            if (!s.isBlank() && s.trim().equalsIgnoreCase(n)) return true;
        }
        return false;
    }

    private static LivingEntity target(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) return le;
        return null;
    }

    // ------------------------------------------------------------------
    // Fadenkreuz (Studio)
    // ------------------------------------------------------------------

    private static Method samCache;

    private static void callOriginal(HudElement original, GuiGraphicsExtractor g, DeltaTracker d) {
        try {
            if (samCache == null) {
                for (Method m : HudElement.class.getMethods()) {
                    if (Modifier.isAbstract(m.getModifiers())) {
                        samCache = m;
                        break;
                    }
                }
            }
            if (samCache != null) samCache.invoke(original, g, d);
        } catch (Throwable ignored) { }
    }

    private static void renderCrosshair(HudElement original, GuiGraphicsExtractor g, DeltaTracker d) {
        Config c = Config.I;
        if (!(c.crosshairStudio && c.csHideVanilla)) callOriginal(original, g, d);
        if (!c.crosshairStudio) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int cx = (sw - 15) / 2 + 7, cy = (sh - 15) / 2 + 7;
        int color = 0xFF000000 | (c.csRgb & 0xFFFFFF);
        if (c.crosshairIndicator && target(mc) != null) {
            color = mc.player.getAttackStrengthScale(0f) >= 1.0f ? 0xFF30FF30 : 0xFFFF3030;
        }
        CrosshairDraw.draw(g, c.csShape, c.csSize, c.csThick, c.csGap, color, c.csOutline, c.csDot, cx, cy);
    }

    // ------------------------------------------------------------------

    private static void key(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w, int h, boolean on) {
        g.fill(x, y, x + w, y + h, on ? 0xCCFFFFFF : 0x80000000);
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2 + 1, on ? 0xFF000000 : 0xFFFFFFFF);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Config c = Config.I;
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float pt = delta.getGameTimeDeltaPartialTick(false);
        LivingEntity target = target(mc);
        Inventory inv = mc.player.getInventory();
        long nowMs = System.currentTimeMillis();

        // ---------- FPS / CPS / Ping ----------
        if (c.fps) at(g, "fps", sw, sh, () -> g.text(font, "FPS: " + mc.getFps(), 0, 0, 0xFFFFFFFF));
        if (c.cps) at(g, "cps", sw, sh, () -> g.text(font, "CPS: " + clicks.size(), 0, 0, 0xFFFFFFFF));
        if (c.ping && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            String s = "Ping: " + (info == null ? "?" : info.getLatency()) + " ms";
            at(g, "ping", sw, sh, () -> g.text(font, s, 0, 0, 0xFFFFFFFF));
        }

        // ---------- Toggle Sprint ----------
        if (c.toggleSprint) {
            boolean sp = mc.player.isSprinting();
            at(g, "sprint", sw, sh, () -> g.text(font, sp ? "Sprint: AN (Toggle)" : "Sprint: Toggle", 0, 0,
                    sp ? 0xFF55FF55 : 0xFFAAAAAA));
        }

        // ---------- Armor Display ----------
        if (c.armorDisplay) {
            at(g, "armor", sw, sh, () -> {
                int y = 0;
                for (EquipmentSlot slot : ARMOR) {
                    ItemStack stack = mc.player.getItemBySlot(slot);
                    if (stack.isEmpty()) continue;
                    g.item(stack, 0, y);
                    if (stack.isDamageableItem()) {
                        int left = stack.getMaxDamage() - stack.getDamageValue();
                        g.text(font, String.valueOf(left), 20, y + 4, 0xFFFFFFFF);
                    }
                    y += 18;
                }
            });
        }

        // ---------- Totem Counter / Item Counter ----------
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, ItemStack> samples = new LinkedHashMap<>();
        if (c.totemCounter || c.itemCounter) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty()) continue;
                String k = ItemFx.key(st);
                counts.merge(k, st.getCount(), Integer::sum);
                samples.putIfAbsent(k, st.copyWithCount(1));
            }
        }
        if (c.totemCounter) {
            int n = counts.getOrDefault("minecraft:totem_of_undying", 0);
            at(g, "totem", sw, sh, () -> {
                g.item(new ItemStack(Items.TOTEM_OF_UNDYING), 0, 0);
                g.text(font, "x" + n, 18, 4, n > 0 ? 0xFFFFFFFF : 0xFFFF5555);
            });
        }
        if (c.itemCounter) {
            at(g, "items", sw, sh, () -> {
                int y = 0;
                for (String raw : c.itemCounterIds.split(",")) {
                    String id = raw.trim();
                    if (id.isEmpty()) continue;
                    if (!id.contains(":")) id = "minecraft:" + id;
                    int n = counts.getOrDefault(id, 0);
                    ItemStack smp = samples.get(id);
                    if (smp != null) g.item(smp, 0, y);
                    String name = id.substring(id.indexOf(':') + 1).replace('_', ' ');
                    g.text(font, name + " x" + n, 18, y + 4, n > 0 ? 0xFFFFFFFF : 0xFFFF5555);
                    y += 18;
                }
            });
        }

        // ---------- Combo / Reach / Jump Reset ----------
        if (c.combo) {
            at(g, "combo", sw, sh, () -> g.text(font, "Combo: " + comboN, 0, 0, comboN > 0 ? 0xFFFFAA00 : 0xFFAAAAAA));
        }
        if (c.reach) {
            boolean fresh = nowMs - lastReachMs < 3000;
            String s = lastReach < 0 ? "Reach: -" : String.format("Reach: %.2f", lastReach);
            at(g, "reach", sw, sh, () -> g.text(font, s, 0, 0, fresh ? 0xFF55FFFF : 0xFFAAAAAA));
        }
        if (c.jumpReset) {
            String s = "Jump Reset: " + jrDone + "/" + jrHits
                    + (jrHits > 0 ? " (" + (jrDone * 100 / jrHits) + "%)" : "");
            at(g, "jr", sw, sh, () -> g.text(font, s, 0, 0, 0xFFFFFFFF));
        }

        // ---------- Inventar-Vorschau ----------
        if (c.invHud) {
            at(g, "inv", sw, sh, () -> {
                g.fill(-2, -2, 164, 56, 0x66000000);
                for (int r = 0; r < 3; r++) {
                    for (int col = 0; col < 9; col++) {
                        g.fill(col * 18, r * 18, col * 18 + 17, r * 18 + 17, 0x40FFFFFF);
                        ItemStack st = inv.getItem(9 + r * 9 + col);
                        if (st.isEmpty()) continue;
                        g.item(st, col * 18 + 1, r * 18 + 1);
                        if (st.getCount() > 1) {
                            String n = String.valueOf(st.getCount());
                            g.text(font, n, col * 18 + 17 - font.width(n), r * 18 + 9, 0xFFFFFFFF);
                        }
                    }
                }
            });
        }

        // ---------- Keystrokes ----------
        if (c.keystrokes) {
            long win = mc.getWindow().handle();
            boolean lmb = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean rmb = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            at(g, "keys", sw, sh, () -> {
                key(g, font, "W", 22, 0, 20, 20, mc.options.keyUp.isDown());
                key(g, font, "A", 0, 22, 20, 20, mc.options.keyLeft.isDown());
                key(g, font, "S", 22, 22, 20, 20, mc.options.keyDown.isDown());
                key(g, font, "D", 44, 22, 20, 20, mc.options.keyRight.isDown());
                key(g, font, "SPACE", 0, 44, 64, 14, mc.options.keyJump.isDown());
                key(g, font, "LMB", 0, 60, 31, 14, lmb);
                key(g, font, "RMB", 33, 60, 31, 14, rmb);
            });
        }

        // ---------- Highlighter + Pre Inventory auf der Hotbar ----------
        if (c.highlighter || c.preInv) {
            for (int i = 0; i < 9; i++) {
                int x = sw / 2 - 90 + i * 20 + 2, y = sh - 19;
                ItemStack st = inv.getItem(i);
                ItemFx.highlight(g, st, x, y);
                ItemFx.ghost(g, i, st, x, y);
            }
        }

        // ---------- Shield Status ----------
        if (c.shieldStatus) {
            ItemStack main = mc.player.getMainHandItem();
            ItemStack off = mc.player.getOffhandItem();
            ItemStack held = main.is(Items.SHIELD) ? main : (off.is(Items.SHIELD) ? off : ItemStack.EMPTY);
            boolean cd = mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD));
            if (!held.isEmpty() || cd) {
                ItemStack icon = held.isEmpty() ? new ItemStack(Items.SHIELD) : held;
                long rem = cd ? Math.max(0, SHIELD_CD_MS - (nowMs - shieldCdStart)) : 0;
                double ratio = cd ? 1.0 - rem / (double) SHIELD_CD_MS : 1.0;
                int rgb = java.awt.Color.HSBtoRGB((float) (ratio * 0.33), 1f, 1f) & 0xFFFFFF;
                String txt = cd ? String.format("%.1fs", rem / 1000.0) : (mc.player.isBlocking() ? "BLOCKT" : "BEREIT");
                at(g, "shield", sw, sh, () -> {
                    g.item(icon, 12, 0);
                    g.fill(12, 0, 28, 16, (0x70 << 24) | rgb);
                    g.text(font, txt, 20 - font.width(txt) / 2, 19, 0xFF000000 | rgb);
                });
            }
        }

        // ---------- Crosshair Indicator ----------
        if (c.crosshairIndicator) {
            float strength = mc.player.getAttackStrengthScale(0f);
            int cx = (sw - 15) / 2 + 7, cy = (sh - 15) / 2 + 7;
            if (target != null && !c.crosshairStudio) {
                int color = strength >= 1.0f ? 0xFF30FF30 : 0xFFFF3030;
                drawIndicator(g, c.crosshairStyle, cx, cy, color);
            }
            if (c.crosshairBar && strength < 1.0f) {
                g.fill(cx - 8, cy + 10, cx + 9, cy + 12, 0x80000000);
                g.fill(cx - 7, cy + 10, cx - 7 + (int) (15 * strength), cy + 12, 0xFFFFFFFF);
            }
        }

        // ---------- Team Tracker ----------
        if (c.teamTracker) {
            Cam cam = new Cam(mc, pt, sw, sh);
            int ty = 4;
            for (Player p : mc.level.players()) {
                if (p == mc.player || !isTeammate(mc, p, c)) continue;
                double dist = mc.player.distanceTo(p);
                String name = Names.apply(p.getName().getString());

                String s = name + "  " + (int) dist + "m  " + (int) p.getHealth() + " HP";
                g.text(font, s, sw - font.width(s) - 4, ty, 0xFF55FF55);
                ty += 10;

                double ex = Cam.lerp(p.xo, p.getX(), pt), ey = Cam.lerp(p.yo, p.getY(), pt), ez = Cam.lerp(p.zo, p.getZ(), pt);
                String label = name + " " + (int) dist + "m";
                double[] pr = cam.proj(ex, ey + p.getBbHeight() + 1.9, ez);
                int half = font.width(label) / 2;
                int sx, sy;
                String shown = label;
                if (pr == null) {
                    boolean right = cam.side(ex, ez) >= 0;
                    sx = right ? sw - 14 - half : 14 + half;
                    sy = sh / 2;
                    shown = right ? label + " >" : "< " + label;
                } else {
                    sx = (int) Math.max(14 + half, Math.min(sw - 14 - half, pr[0]));
                    sy = (int) Math.max(14, Math.min(sh - 14, pr[1]));
                }
                g.text(font, shown, sx - font.width(shown) / 2, sy, 0xFF55FF55);
            }
        }
    }

    private static void drawIndicator(GuiGraphicsExtractor g, int style, int cx, int cy, int color) {
        switch (style) {
            case 1 -> CrosshairDraw.draw(g, 2, 3, 1, 0, color, false, false, cx, cy);
            case 2 -> CrosshairDraw.draw(g, 3, 5, 1, 2, color, false, false, cx, cy);
            case 3 -> CrosshairDraw.draw(g, 4, 4, 1, 3, color, false, false, cx, cy);
            case 4 -> CrosshairDraw.draw(g, 5, 4, 1, 2, color, false, false, cx, cy);
            default -> CrosshairDraw.draw(g, 0, 6, 1, 0, color, false, false, cx, cy);
        }
    }
}
