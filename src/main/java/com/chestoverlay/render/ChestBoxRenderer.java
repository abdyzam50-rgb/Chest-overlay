package com.chestoverlay.render;

import com.chestoverlay.config.ChestOverlayConfig;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ChestBoxRenderer {

    private static final long START_TIME   = System.currentTimeMillis();
    private static final int  RENDER_RANGE = 64;

    private static RenderLayer linesLayer = null;

    private static RenderLayer getLinesLayer() {
        if (linesLayer != null) return linesLayer;
        // Try RenderLayer.LINES first (MC 1.21.1), then RenderLayers.LINES (MC 1.21.11+)
        for (String className : new String[]{"net.minecraft.client.render.RenderLayer",
                                             "net.minecraft.client.render.RenderLayers"}) {
            try {
                Field f = Class.forName(className).getField("LINES");
                Object val = f.get(null);
                if (val instanceof RenderLayer rl) {
                    linesLayer = rl;
                    return linesLayer;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Called from GameRendererMixin at TAIL of GameRenderer.render(). */
    public static void renderDirect() {
        if (!ChestOverlayConfig.enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return;
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) return;

        RenderLayer layer = getLinesLayer();
        if (layer == null) return;

        Vec3d camPos  = client.gameRenderer.getCamera().getPos();
        BlockPos pPos = client.player.getBlockPos();
        float[]  rgb  = currentColor();

        List<Box> boxes = new ArrayList<>();
        int chunkRange  = RENDER_RANGE / 16 + 1;
        ChunkPos center = new ChunkPos(pPos);

        for (int cx = center.x - chunkRange; cx <= center.x + chunkRange; cx++) {
            for (int cz = center.z - chunkRange; cz <= center.z + chunkRange; cz++) {
                WorldChunk chunk = client.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!isStorage(be)) continue;
                    BlockPos pos = be.getPos();
                    if (pos.getSquaredDistance(pPos) > (double) RENDER_RANGE * RENDER_RANGE) continue;
                    boxes.add(getHitbox(client, pos));
                }
            }
        }

        if (boxes.isEmpty()) return;

        try {
            VertexConsumerProvider.Immediate consumers =
                    client.getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer buf = consumers.getBuffer(layer);

            MatrixStack matrices = new MatrixStack();
            matrices.translate(-camPos.x, -camPos.y, -camPos.z);
            Matrix4f pose = matrices.peek().getPositionMatrix();

            float r = rgb[0], g = rgb[1], b = rgb[2];

            for (Box box : boxes) {
                addBox(buf, pose,
                    (float) box.minX, (float) box.minY, (float) box.minZ,
                    (float) box.maxX, (float) box.maxY, (float) box.maxZ,
                    r, g, b);
            }

            consumers.draw(layer);
        } catch (Throwable e) {
            System.err.println("[ChestOverlay] renderDirect error: " + e);
        }
    }

    // ── Box drawing ───────────────────────────────────────────────────────────

    private static void addBox(VertexConsumer buf, Matrix4f pose,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r,  float g,  float b) {
        // bottom face
        line(buf, pose, x1,y1,z1, x2,y1,z1, r,g,b);
        line(buf, pose, x2,y1,z1, x2,y1,z2, r,g,b);
        line(buf, pose, x2,y1,z2, x1,y1,z2, r,g,b);
        line(buf, pose, x1,y1,z2, x1,y1,z1, r,g,b);
        // top face
        line(buf, pose, x1,y2,z1, x2,y2,z1, r,g,b);
        line(buf, pose, x2,y2,z1, x2,y2,z2, r,g,b);
        line(buf, pose, x2,y2,z2, x1,y2,z2, r,g,b);
        line(buf, pose, x1,y2,z2, x1,y2,z1, r,g,b);
        // vertical edges
        line(buf, pose, x1,y1,z1, x1,y2,z1, r,g,b);
        line(buf, pose, x2,y1,z1, x2,y2,z1, r,g,b);
        line(buf, pose, x2,y1,z2, x2,y2,z2, r,g,b);
        line(buf, pose, x1,y1,z2, x1,y2,z2, r,g,b);
    }

    private static void line(VertexConsumer buf, Matrix4f pose,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               float r,  float g,  float b) {
        // RenderLayer.LINES uses POSITION_COLOR_NORMAL format; normal points along the line
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len == 0) return;
        float nx = dx/len, ny = dy/len, nz = dz/len;
        buf.vertex(pose, x1, y1, z1).color(r, g, b, 1f).normal(nx, ny, nz);
        buf.vertex(pose, x2, y2, z2).color(r, g, b, 1f).normal(nx, ny, nz);
    }

    // ── Color calculation ─────────────────────────────────────────────────────

    private static float[] currentColor() {
        long elapsed = System.currentTimeMillis() - START_TIME;

        return switch (ChestOverlayConfig.colorMode) {
            case RGB -> {
                float hue  = (elapsed % 3000L) / 3000.0f;
                int   argb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
                yield new float[]{
                    ((argb >> 16) & 0xFF) / 255.0f,
                    ((argb >> 8)  & 0xFF) / 255.0f,
                    ( argb        & 0xFF) / 255.0f
                };
            }
            case CUSTOM -> new float[]{
                ChestOverlayConfig.color1R / 255.0f,
                ChestOverlayConfig.color1G / 255.0f,
                ChestOverlayConfig.color1B / 255.0f
            };
            case PULSE -> {
                long  cycleMs = (long) (ChestOverlayConfig.pulseSpeed * 1000.0f);
                float t       = (elapsed % cycleMs) / (float) cycleMs;
                float blend   = (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5);
                yield new float[]{
                    lerp(ChestOverlayConfig.color1R / 255.0f, ChestOverlayConfig.color2R / 255.0f, blend),
                    lerp(ChestOverlayConfig.color1G / 255.0f, ChestOverlayConfig.color2G / 255.0f, blend),
                    lerp(ChestOverlayConfig.color1B / 255.0f, ChestOverlayConfig.color2B / 255.0f, blend)
                };
            }
        };
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity
            || be instanceof BarrelBlockEntity
            || be instanceof ShulkerBoxBlockEntity
            || be instanceof EnderChestBlockEntity;
    }

    private static Box getHitbox(MinecraftClient client, BlockPos pos) {
        try {
            VoxelShape shape = client.world.getBlockState(pos).getOutlineShape(client.world, pos);
            if (!shape.isEmpty()) return shape.getBoundingBox().offset(pos);
        } catch (Exception ignored) {}
        return new Box(pos);
    }
}
