package com.chestoverlay.render;

import com.chestoverlay.config.ChestOverlayConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;

public class ChestBoxRenderer {

    private static final long START_TIME  = System.currentTimeMillis();
    private static final int  RENDER_RANGE = 64;

    public static void render(WorldRenderContext context) {
        if (!ChestOverlayConfig.enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        float[] rgb = currentColor();

        Vec3d     cameraPos   = context.camera().getPos();
        VertexConsumer lines  = consumers.getBuffer(RenderLayer.LINES);

        BlockPos playerPos = client.player.getBlockPos();
        int chunkRange     = RENDER_RANGE / 16 + 1;
        ChunkPos center    = new ChunkPos(playerPos);

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        try {
            for (int cx = center.x - chunkRange; cx <= center.x + chunkRange; cx++) {
                for (int cz = center.z - chunkRange; cz <= center.z + chunkRange; cz++) {
                    WorldChunk chunk = client.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue;

                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (!isStorage(be)) continue;

                        BlockPos pos = be.getPos();
                        if (pos.getSquaredDistance(playerPos) > (double) RENDER_RANGE * RENDER_RANGE) continue;

                        Box box = getHitbox(client, pos);
                        WorldRenderer.drawBox(matrices, lines, box, rgb[0], rgb[1], rgb[2], 1.0f);
                    }
                }
            }
        } finally {
            matrices.pop();
        }
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
                // Smooth sine wave: 0 → 1 → 0 over one cycle
                float blend   = (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5);
                yield new float[]{
                    lerp(ChestOverlayConfig.color1R / 255.0f, ChestOverlayConfig.color2R / 255.0f, blend),
                    lerp(ChestOverlayConfig.color1G / 255.0f, ChestOverlayConfig.color2G / 255.0f, blend),
                    lerp(ChestOverlayConfig.color1B / 255.0f, ChestOverlayConfig.color2B / 255.0f, blend)
                };
            }
        };
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isStorage(BlockEntity be) {
        // TrappedChestBlockEntity extends ChestBlockEntity, so one check covers both
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
