package sharkus.extras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Elemente mit gedrückter linker Maustaste verschieben.
 * Größe ändern: Maus auf das Element halten und Pfeil hoch / runter drücken.
 */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private Hud.El drag;
    private int offX, offY;
    private boolean wasDown;
    private long lastScaleMs;
    private boolean dirty;

    public HudEditScreen(Screen parent) {
        super(Component.literal("HUD verschieben"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = Config.I;
        addRenderableWidget(Button.builder(Component.literal("Zurücksetzen"), b -> {
            c.resetPositions();
            Config.save();
        }).bounds(width / 2 - 104, height - 28, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(width / 2 + 4, height - 28, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Config c = Config.I;
        g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("Ziehen = verschieben   |   Maus auf Element + Pfeil hoch/runter = Größe"),
                width / 2, 20, 0xFFAAAAAA);

        long win = minecraft.getWindow().handle();
        boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // Element unter der Maus
        Hud.El hover = null;
        for (Hud.El e : Hud.ELS) {
            int x = Hud.X(e.key(), width), y = Hud.Y(e.key(), height);
            float s = c.sc(e.key());
            if (mouseX >= x && mouseX <= x + e.w() * s && mouseY >= y && mouseY <= y + e.h() * s) hover = e;
        }

        if (down) {
            if (drag == null && !wasDown && hover != null && mouseY < height - 34) {
                drag = hover;
                offX = mouseX - Hud.X(hover.key(), width);
                offY = mouseY - Hud.Y(hover.key(), height);
            }
            if (drag != null) {
                float s = c.sc(drag.key());
                int nx = Math.max(0, Math.min((int) (width - drag.w() * s), mouseX - offX));
                int ny = Math.max(0, Math.min((int) (height - drag.h() * s), mouseY - offY));
                c.hudPos.put(drag.key(), new int[]{nx, ny});
                dirty = true;
            }
        } else if (drag != null) {
            drag = null;
        }
        wasDown = down;

        // Größe per Pfeiltasten
        Hud.El target = drag != null ? drag : hover;
        if (target != null) {
            boolean up = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS;
            boolean dn = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS;
            long now = System.currentTimeMillis();
            if ((up || dn) && now - lastScaleMs > 120) {
                float s = c.sc(target.key()) + (up ? 0.1f : -0.1f);
                s = Math.max(0.5f, Math.min(3.0f, Math.round(s * 10f) / 10f));
                c.hudScale.put(target.key(), s);
                lastScaleMs = now;
                dirty = true;
            }
        }
        if (!down && dirty) {
            Config.save();
            dirty = false;
        }

        for (Hud.El e : Hud.ELS) {
            int x = Hud.X(e.key(), width), y = Hud.Y(e.key(), height);
            float s = c.sc(e.key());
            int w = (int) (e.w() * s), h = (int) (e.h() * s);
            int col = e == drag ? 0xFFFFFF55 : (e == hover ? 0xFFFFFFFF : 0xFF55AAFF);
            g.fill(x, y, x + w, y + 1, col);
            g.fill(x, y + h - 1, x + w, y + h, col);
            g.fill(x, y, x + 1, y + h, col);
            g.fill(x + w - 1, y, x + w, y + h, col);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x3355AAFF);
            g.text(font, e.label() + " x" + String.format("%.1f", s), x + 2, Math.max(0, y - 10), col);
        }
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.gui.setScreen(parent);
    }
}
