package sharkus.extras;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Färbt den Treffer-Blink um. Minecraft zeichnet ihn über eine kleine Textur (OverlayTexture),
 * die obere Hälfte dieser Textur ist der rote Blink. Wir schreiben dort unsere Farbe hinein.
 * Der Alpha-Wert ist der Anteil der Originalfarbe - kleiner Alpha = stärkerer Blink.
 */
public final class HitColor {
    public static volatile boolean hooked = false;
    public static volatile String status = "Mixin nicht aktiv";

    private static DynamicTexture tex;
    private static int lastKey = Integer.MIN_VALUE;

    private HitColor() { }

    public static void attach(DynamicTexture t) {
        tex = t;
        hooked = true;
        lastKey = Integer.MIN_VALUE;
        status = "verbunden";
        apply();
    }

    public static void tick() {
        apply();
    }

    private static void apply() {
        if (tex == null) return;
        Config c = Config.I;
        int strength = Math.max(0, Math.min(100, c.hitAlpha));
        int argb = c.hitColor
                ? (((int) Math.round(255 * (1.0 - strength / 100.0))) << 24) | (c.hitRgb & 0xFFFFFF)
                : 0xB2FF0000; // Original-Rot
        if (argb == lastKey) return;
        try {
            NativeImage img = tex.getPixels();
            if (img == null) {
                status = "Textur hat keine Pixel";
                return;
            }
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 16; x++) img.setPixel(x, y, argb);
            }
            tex.upload();
            lastKey = argb;
            status = c.hitColor ? "aktiv" : "verbunden (Hit Color aus)";
        } catch (Throwable t) {
            status = "Fehler: " + t.getClass().getSimpleName();
            lastKey = argb;
        }
    }
}
