package sharkus.extras;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Zeichnet die Fadenkreuz-Formen des Crosshair Studios. */
final class CrosshairDraw {
    static final String[] SHAPES = {"Plus", "T-Form", "Punkt", "Ring", "Ecken", "X"};

    private CrosshairDraw() { }

    static void draw(GuiGraphicsExtractor g, int shape, int size, int thick, int gap, int argb,
                     boolean outline, boolean dot, int cx, int cy) {
        List<int[]> r = new ArrayList<>();
        int t = Math.max(1, thick);
        int o = t / 2;
        int x0 = cx - o, y0 = cy - o;
        size = Math.max(1, size);
        gap = Math.max(0, gap);

        switch (shape) {
            case 0, 1 -> {
                r.add(new int[]{x0 + t + gap, y0, x0 + t + gap + size, y0 + t});
                r.add(new int[]{x0 - gap - size, y0, x0 - gap, y0 + t});
                r.add(new int[]{x0, y0 + t + gap, x0 + t, y0 + t + gap + size});
                if (shape == 0) r.add(new int[]{x0, y0 - gap - size, x0 + t, y0 - gap});
            }
            case 2 -> {
                int half = size / 2;
                r.add(new int[]{cx - half, cy - half, cx - half + size, cy - half + size});
            }
            case 3 -> {
                int rad = gap + size;
                for (int a = 0; a < 360; a += 3) {
                    int x = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * rad);
                    int y = cy + (int) Math.round(Math.sin(Math.toRadians(a)) * rad);
                    r.add(new int[]{x - o, y - o, x - o + t, y - o + t});
                }
            }
            case 4 -> {
                int d = gap + size;
                int l = Math.max(2, size / 2 + 1);
                r.add(new int[]{cx - d, cy - d, cx - d + l, cy - d + t});
                r.add(new int[]{cx - d, cy - d, cx - d + t, cy - d + l});
                r.add(new int[]{cx + d - l, cy - d, cx + d, cy - d + t});
                r.add(new int[]{cx + d - t, cy - d, cx + d, cy - d + l});
                r.add(new int[]{cx - d, cy + d - t, cx - d + l, cy + d});
                r.add(new int[]{cx - d, cy + d - l, cx - d + t, cy + d});
                r.add(new int[]{cx + d - l, cy + d - t, cx + d, cy + d});
                r.add(new int[]{cx + d - t, cy + d - l, cx + d, cy + d});
            }
            case 5 -> {
                for (int i = gap; i <= gap + size; i++) {
                    r.add(new int[]{cx - i - o, cy - i - o, cx - i - o + t, cy - i - o + t});
                    r.add(new int[]{cx + i - o, cy - i - o, cx + i - o + t, cy - i - o + t});
                    r.add(new int[]{cx - i - o, cy + i - o, cx - i - o + t, cy + i - o + t});
                    r.add(new int[]{cx + i - o, cy + i - o, cx + i - o + t, cy + i - o + t});
                }
            }
            default -> { }
        }
        if (dot && shape != 2) r.add(new int[]{x0, y0, x0 + t, y0 + t});

        if (outline) {
            for (int[] q : r) g.fill(q[0] - 1, q[1] - 1, q[2] + 1, q[3] + 1, 0xFF000000);
        }
        for (int[] q : r) g.fill(q[0], q[1], q[2], q[3], argb);
    }
}
