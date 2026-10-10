package sharkus.extras;

import net.minecraft.client.Minecraft;

/** Kamera-Mathe: rechnet Welt-Punkte in Bildschirm-Pixel um und liefert Billboard-Richtungen. */
final class Cam {
    final double ex, ey, ez, rx, rz, ux, uy, uz, fx, fy, fz, f;
    final int sw, sh;

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double lerpYaw(double o, double n, double t) {
        double d = ((n - o + 540.0) % 360.0) - 180.0;
        return o + d * t;
    }

    Cam(Minecraft mc, float pt, int sw, int sh) {
        var p = mc.player;
        this.sw = sw;
        this.sh = sh;
        ex = lerp(p.xo, p.getX(), pt);
        ey = lerp(p.yo, p.getY(), pt) + p.getEyeHeight();
        ez = lerp(p.zo, p.getZ(), pt);
        double yaw = Math.toRadians(p.getViewYRot(pt));
        double pitch = Math.toRadians(p.getViewXRot(pt));
        fx = -Math.sin(yaw) * Math.cos(pitch);
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * Math.cos(pitch);
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        double fov = Math.toRadians(mc.options.fov().get());
        f = (sh / 2.0) / Math.tan(fov / 2.0);
    }

    /** {x, y, tiefe} in Pixeln oder null, wenn der Punkt hinter dir liegt. */
    double[] proj(double wx, double wy, double wz) {
        double dx = wx - ex, dy = wy - ey, dz = wz - ez;
        double cz = dx * fx + dy * fy + dz * fz;
        if (cz < 0.05) return null;
        double cx = dx * rx + dz * rz;
        double cy = dx * ux + dy * uy + dz * uz;
        return new double[]{sw / 2.0 + cx / cz * f, sh / 2.0 - cy / cz * f, cz};
    }

    /** Positiv = rechts von dir. */
    double side(double wx, double wz) {
        return (wx - ex) * rx + (wz - ez) * rz;
    }
}
