package com.chestoverlay.render;

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

    private static final long START_TIME = System.currentTimeMillis();
    // Full RGB cycle every 3 seconds
    private static final float CYCLE_MS = 3000.0f;
    private static final int RENDER_RANGE = 64;

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();

        // Cycle through the full hue spectrum for RGB effect
        float hue = ((System.currentTimeMillis() - START_TIME) % (long) CYCLE_MS) / CYCLE_MS;
        int argb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;

        // RenderLayer.LINES uses LEQUAL depth test — occluded by walls naturally
        VertexConsumer lines = consumers.getBuffer(RenderLayer.LINES);

        BlockPos playerPos = client.player.getBlockPos();
        int chunkRange = RENDER_RANGE / 16 + 1;
        ChunkPos center = new ChunkPos(playerPos);

        for (int cx = center.x - chunkRange; cx <= center.x + chunkRange; cx++) {
            for (int cz = center.z - chunkRange; cz <= center.z + chunkRange; cz++) {
                WorldChunk chunk = client.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!isStorage(be)) continue;

                    BlockPos pos = be.getPos();
                    if (pos.getSquaredDistance(playerPos) > (double) RENDER_RANGE * RENDER_RANGE) continue;

                    Box box = getHitbox(client, pos);

                    matrices.push();
                    // Translate to camera-relative space before drawing
                    matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                    WorldRenderer.drawBox(matrices, lines, box, r, g, b, 1.0f);
                    matrices.pop();
                }
            }
        }
    }

    private static boolean isStorage(BlockEntity be) {
        // ChestBlockEntity covers both normal and trapped chests (TrappedChestBlockEntity extends it)
        return be instanceof ChestBlockEntity
            || be instanceof BarrelBlockEntity
            || be instanceof ShulkerBoxBlockEntity
            || be instanceof EnderChestBlockEntity;
    }

    private static Box getHitbox(MinecraftClient client, BlockPos pos) {
        try {
            VoxelShape shape = client.world.getBlockState(pos).getOutlineShape(client.world, pos);
            if (!shape.isEmpty()) {
                return shape.getBoundingBox().offset(pos);
            }
        } catch (Exception ignored) {}
        return new Box(pos);
    }
}
