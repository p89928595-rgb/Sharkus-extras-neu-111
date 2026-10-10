package sharkus.extras;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Zeichnet direkt in die Spielwelt: Block-Outline, Hit-Color-Hülle, Herzen über Köpfen, Team-Marker.
 * Alles besteht aus einfarbigen Flächen (Quads) mit der Vanilla-Pipeline DEBUG_FILLED_BOX.
 */
public final class WorldFx {
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer stagedBuffer =
            new StagedVertexBuffer(() -> "Sharkus Extras Buffer", RenderType.SMALL_BUFFER_SIZE);

    private static volatile List<float[]> quads = List.of();

    private WorldFx() { }

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(WorldFx::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WorldFx::render);
    }

    public static void close() {
        stagedBuffer.close();
    }

    // ------------------------------------------------------------------
    // Extraktion: sammelt alle Flächen für diesen Frame
    // ------------------------------------------------------------------

    private static void extract(LevelExtractionContext context) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Config c = Config.I;
            if (mc.player == null || mc.level == null) {
                quads = List.of();
                return;
            }
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Cam cam = new Cam(mc, pt, 0, 0);
            List<float[]> out = new ArrayList<>();

            LivingEntity target = null;
            if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
                target = le;
            }

            // ---------- Block Outline ----------
            if (c.blockOutlines && mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = bhr.getBlockPos();
                VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
                double x0 = 0, y0 = 0, z0 = 0, x1 = 1, y1 = 1, z1 = 1;
                if (!shape.isEmpty()) {
                    x0 = shape.min(Direction.Axis.X);
                    y0 = shape.min(Direction.Axis.Y);
                    z0 = shape.min(Direction.Axis.Z);
                    x1 = shape.max(Direction.Axis.X);
                    y1 = shape.max(Direction.Axis.Y);
                    z1 = shape.max(Direction.Axis.Z);
                }
                outline(out, pos.getX() + x0, pos.getY() + y0, pos.getZ() + z0,
                        pos.getX() + x1, pos.getY() + y1, pos.getZ() + z1,
                        0xFF000000 | (c.outlineRgb & 0xFFFFFF),
                        0.006 * Math.max(1, Math.min(4, c.outlineThickness)));
            }

            // ---------- Spieler-bezogenes ----------
            List<LivingEntity> list = new ArrayList<>();
            for (Player p : mc.level.players()) {
                if (p != mc.player && p.isAlive() && !p.isInvisible()) list.add(p);
            }
            if (target != null && !list.contains(target)) list.add(target);
            list.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));

            int shown = 0;
            for (LivingEntity e : list) {
                double dist = mc.player.distanceTo(e);
                if (dist > 48 || shown >= 16) continue;
                shown++;
                double ex = Cam.lerp(e.xo, e.getX(), pt), ey = Cam.lerp(e.yo, e.getY(), pt), ez = Cam.lerp(e.zo, e.getZ(), pt);

                if (c.healthIndicator) {
                    health(out, cam, e, ex, ey + e.getBbHeight() + 0.9, ez, dist, c.healthStyle);
                }

                if (c.teamTracker && e instanceof Player pl && Hud.isTeammate(mc, pl, c)) {
                    marker(out, cam, ex, ey + e.getBbHeight() + 1.45, ez, dist);
                }
            }
            // ---------- Hitbox Colors ----------
            if (c.hitboxColors) {
                int count = 0;
                for (Entity ent : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(32.0), x -> true)) {
                    if (count++ > 120) break;
                    int rgb;
                    if (ent instanceof Player pl) rgb = Hud.isTeammate(mc, pl, c) ? c.hbTeamRgb : c.hbPlayerRgb;
                    else if (ent instanceof Enemy) rgb = c.hbHostileRgb;
                    else if (ent instanceof ItemEntity) rgb = c.hbItemRgb;
                    else if (ent instanceof Projectile) rgb = c.hbProjectileRgb;
                    else if (ent instanceof LivingEntity) rgb = c.hbPassiveRgb;
                    else continue;
                    double bx = Cam.lerp(ent.xo, ent.getX(), pt);
                    double by = Cam.lerp(ent.yo, ent.getY(), pt);
                    double bz = Cam.lerp(ent.zo, ent.getZ(), pt);
                    double w = ent.getBbWidth() / 2.0;
                    outline(out, bx - w, by, bz - w, bx + w, by + ent.getBbHeight(), bz + w,
                            0xFF000000 | (rgb & 0xFFFFFF), 0.008);
                }
            }
            quads = out;
        } catch (Throwable t) {
            quads = List.of();
        }
    }

    // ------------------------------------------------------------------
    // Flächen-Helfer
    // ------------------------------------------------------------------

    private static void addQuad(List<float[]> out, double[] p, int argb) {
        float a = ((argb >>> 24) & 255) / 255f;
        float r = ((argb >>> 16) & 255) / 255f;
        float g = ((argb >>> 8) & 255) / 255f;
        float b = (argb & 255) / 255f;
        int[][] orders = {{0, 1, 2, 3}, {0, 3, 2, 1}}; // beide Richtungen, damit nichts weggeschnitten wird
        for (int[] o : orders) {
            float[] q = new float[16];
            for (int i = 0; i < 4; i++) {
                q[i * 3] = (float) p[o[i] * 3];
                q[i * 3 + 1] = (float) p[o[i] * 3 + 1];
                q[i * 3 + 2] = (float) p[o[i] * 3 + 2];
            }
            q[12] = r; q[13] = g; q[14] = b; q[15] = a;
            out.add(q);
        }
    }

    private static final int[][] FACES = {
            {0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}
    };

    /** Quader, um die Y-Achse gedreht (yaw in Grad), lokale Koordinaten relativ zu (cx,cy,cz). */
    private static void orientedBox(List<float[]> out, double cx, double cy, double cz, double yawDeg,
                                    double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        double rad = Math.toRadians(yawDeg), s = Math.sin(rad), co = Math.cos(rad);
        double[][] corner = new double[8][3];
        for (int i = 0; i < 8; i++) {
            double lx = (i & 1) == 0 ? x0 : x1;
            double ly = (i & 2) == 0 ? y0 : y1;
            double lz = (i & 4) == 0 ? z0 : z1;
            corner[i][0] = cx + lx * co - lz * s;
            corner[i][1] = cy + ly;
            corner[i][2] = cz + lx * s + lz * co;
        }
        for (int[] f : FACES) {
            double[] p = new double[12];
            for (int k = 0; k < 4; k++) {
                p[k * 3] = corner[f[k]][0];
                p[k * 3 + 1] = corner[f[k]][1];
                p[k * 3 + 2] = corner[f[k]][2];
            }
            addQuad(out, p, argb);
        }
    }

    private static void box(List<float[]> out, double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        orientedBox(out, 0, 0, 0, 0, x0, y0, z0, x1, y1, z1, argb);
    }

    // ------------------------------------------------------------------
    // Block Outline
    // ------------------------------------------------------------------

    private static void outline(List<float[]> out, double x0, double y0, double z0,
                                double x1, double y1, double z1, int argb, double t) {
        double e = 0.003;
        x0 -= e; y0 -= e; z0 -= e; x1 += e; y1 += e; z1 += e;
        double[] xs = {x0, x1}, ys = {y0, y1}, zs = {z0, z1};
        for (double y : ys) for (double z : zs) box(out, x0 - t, y - t, z - t, x1 + t, y + t, z + t, argb);
        for (double x : xs) for (double z : zs) box(out, x - t, y0 - t, z - t, x + t, y1 + t, z + t, argb);
        for (double x : xs) for (double y : ys) box(out, x - t, y - t, z0 - t, x + t, y + t, z1 + t, argb);
    }

    // ------------------------------------------------------------------
    // Pixel-Grafiken als Billboard (immer zur Kamera gedreht)
    // ------------------------------------------------------------------

    private static final String[] HEART = {
            ".OOO.OOO.",
            "OHHRORRRO",
            "OHRRRRRDO",
            "ORRRRRRDO",
            "ORRRRRDDO",
            ".ORRRRDO.",
            "..ORRDO..",
            "...ORO...",
            "....O...."
    };

    private static final String[] DIGIT = {
            "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001001001001", "111101111101111", "111101111001111"
    };

    private static void heartInto(int[][] g, int ox, int oy, int state) {
        // state: 2 voll, 1 halb, 0 leer
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                char ch = HEART[r].charAt(c);
                if (ch == '.') continue;
                int col;
                if (ch == 'O') {
                    col = 0xFF000000;
                } else {
                    boolean filled = state == 2 || (state == 1 && c <= 4);
                    if (!filled) {
                        col = ch == 'H' ? 0xFF5A5A5A : (ch == 'D' ? 0xFF2B2B2B : 0xFF3C3C3C);
                    } else {
                        col = ch == 'H' ? 0xFFFF8A8A : (ch == 'D' ? 0xFF8F0F0F : 0xFFE62020);
                    }
                }
                g[oy + r][ox + c] = col;
            }
        }
    }

    private static void emitGrid(List<float[]> out, Cam cam, double bx, double by, double bz, double p, int[][] grid, double push) {
        bx += cam.fx * push;
        by += cam.fy * push;
        bz += cam.fz * push;
        int h = grid.length, w = grid[0].length;
        for (int r = 0; r < h; r++) {
            int x = 0;
            while (x < w) {
                int col = grid[r][x];
                if (col == 0) { x++; continue; }
                int x2 = x;
                while (x2 + 1 < w && grid[r][x2 + 1] == col) x2++;
                double u0 = (x - w / 2.0) * p, u1 = (x2 + 1 - w / 2.0) * p;
                double v1 = (h / 2.0 - r) * p, v0 = v1 - p;
                double[] pts = new double[12];
                double[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                for (int i = 0; i < 4; i++) {
                    pts[i * 3] = bx + cam.rx * uv[i][0] + cam.ux * uv[i][1];
                    pts[i * 3 + 1] = by + cam.uy * uv[i][1];
                    pts[i * 3 + 2] = bz + cam.rz * uv[i][0] + cam.uz * uv[i][1];
                }
                addQuad(out, pts, col);
                x = x2 + 1;
            }
        }
    }

    private static void health(List<float[]> out, Cam cam, LivingEntity e, double bx, double by, double bz,
                               double dist, int style) {
        float hp = Math.max(0f, e.getHealth());
        float max = Math.max(1f, e.getMaxHealth());
        if (max > 20f && style == 0) style = 1;
        int[][] grid;
        double p;
        if (style == 0) {
            int total = Math.min(10, (int) Math.ceil(max / 2f));
            grid = new int[9][total * 8 + 1];
            for (int i = 0; i < total; i++) {
                float v = hp - 2 * i;
                int state = v >= 1.5f ? 2 : (v >= 0.5f ? 1 : 0);
                heartInto(grid, i * 8, 0, state);
            }
            p = Math.max(0.010, 0.0016 * dist);
        } else if (style == 1) {
            String num = String.valueOf((int) Math.ceil(hp));
            int w = 9 + 2 + num.length() * 4 - 1;
            grid = new int[9][w];
            heartInto(grid, 0, 0, 2);
            for (int d = 0; d < num.length(); d++) {
                String pat = DIGIT[num.charAt(d) - '0'];
                for (int r = 0; r < 5; r++) {
                    for (int c = 0; c < 3; c++) {
                        if (pat.charAt(r * 3 + c) == '1') grid[2 + r][11 + d * 4 + c] = 0xFFFFFFFF;
                    }
                }
            }
            p = Math.max(0.020, 0.0028 * dist);
        } else {
            int w = 42, h = 7;
            grid = new int[h][w];
            for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) grid[r][c] = 0xFF000000;
            double ratio = Math.max(0, Math.min(1, hp / max));
            int fill = (int) Math.round(ratio * 40);
            int col = barColor(ratio);
            for (int r = 1; r < h - 1; r++) {
                for (int c = 1; c < w - 1; c++) grid[r][c] = (c - 1) < fill ? col : 0xFF3C3C3C;
            }
            p = Math.max(0.020, 0.0028 * dist);
        }
        int[][] bg = new int[grid.length + 4][grid[0].length + 4];
        for (int[] row : bg) java.util.Arrays.fill(row, 0x78000000);
        emitGrid(out, cam, bx, by, bz, p, bg, 0.004);
        emitGrid(out, cam, bx, by, bz, p, grid, 0.0);
    }

    private static int barColor(double ratio) {
        int r, g;
        if (ratio > 0.5) { r = (int) (255 * (1 - (ratio - 0.5) * 2)); g = 255; }
        else { r = 255; g = (int) (255 * ratio * 2); }
        return 0xFF000000 | (r << 16) | (g << 8) | 0x20;
    }

    private static void marker(List<float[]> out, Cam cam, double bx, double by, double bz, double dist) {
        String[] rows = {"1111111", ".11111.", "..111..", "...1..."};
        int[][] grid = new int[rows.length][7];
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < 7; c++) if (rows[r].charAt(c) == '1') grid[r][c] = 0xFF55FF55;
        }
        emitGrid(out, cam, bx, by, bz, Math.max(0.03, 0.0035 * dist), grid, 0.0);
    }

    // ------------------------------------------------------------------
    // Zeichnen (nach dem Muster der offiziellen Fabric-Doku für 26.2)
    // ------------------------------------------------------------------

    private static void render(LevelRenderContext context) {
        List<float[]> list = quads;
        if (list.isEmpty()) return;
        try {
            RenderPipeline renderPipeline = RenderPipelines.DEBUG_FILLED_BOX;
            VertexFormat formatBinding = renderPipeline.getVertexFormatBinding(0);
            assert formatBinding != null;

            PrimitiveTopology primitive = renderPipeline.getPrimitiveTopology();
            StagedVertexBuffer.Draw draw = stagedBuffer.appendDraw(formatBinding, primitive,
                    primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);

            PoseStack matrices = context.poseStack();
            Vec3 camera = context.levelState().cameraRenderState.pos;
            matrices.pushPose();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            Matrix4fc pose = matrices.last().pose();
            final var builder = stagedBuffer.getVertexBuilder(draw);
            for (float[] q : list) {
                for (int i = 0; i < 4; i++) {
                    builder.addVertex(pose, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(q[12], q[13], q[14], q[15]);
                }
            }
            matrices.popPose();

            stagedBuffer.upload();
            StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(draw);
            if (info != null) {
                draw(Minecraft.getInstance(), info, renderPipeline);
            }
            stagedBuffer.endFrame();
        } catch (Throwable t) {
            quads = List.of();
        }
    }

    private static void draw(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
        GpuTextureView colorTexture = mainTarget.getColorTextureView();

        assert colorTexture != null;

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "sharkus-extras world rendering", colorTexture, Optional.empty(),
                        mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
            renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());

            renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }
}
