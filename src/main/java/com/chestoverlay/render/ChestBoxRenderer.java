package com.chestoverlay.render;

import com.chestoverlay.config.ChestOverlayConfig;
import net.minecraft.block.entity.*;
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
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ChestBoxRenderer {

    private static final long START_TIME   = System.currentTimeMillis();
    private static final int  RENDER_RANGE = 64;

    // ── Render-layer lookup ───────────────────────────────────────────────────

    private static RenderLayer linesLayer   = null;
    private static boolean     layerChecked = false;

    private static RenderLayer getLinesLayer() {
        if (layerChecked) return linesLayer;
        layerChecked = true;

        // Strategy 1: direct bytecode access.
        // Loom remaps "RenderLayer.LINES" to the intermediary field reference at
        // compile time. Works in 1.21.1; throws NoSuchFieldError in 1.21.11 if
        // the field was moved to a different class.
        try {
            linesLayer = RenderLayer.LINES;
            System.err.println("[ChestOverlay] LINES via direct access: " + linesLayer);
            return linesLayer;
        } catch (Throwable ignored) {}

        // Strategy 2: scan all static fields declared on RenderLayer.class.
        // "RenderLayer.class" IS Loom-remapped to the correct intermediary class.
        // Field names at runtime are intermediary (field_XXXX), but we identify the
        // right one by checking whether the field value's toString() contains "line".
        try {
            for (Field f : RenderLayer.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!RenderLayer.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                try {
                    Object v = f.get(null);
                    if (v instanceof RenderLayer rl) {
                        String s = rl.toString().toLowerCase();
                        System.err.println("[ChestOverlay] static RenderLayer field: "
                                + f.getName() + " -> " + s);
                        if (s.contains("line")) {
                            linesLayer = rl;
                            System.err.println("[ChestOverlay] LINES chosen by scan: " + rl);
                            return linesLayer;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // Strategy 3: no-arg static methods on RenderLayer returning RenderLayer
        try {
            for (java.lang.reflect.Method m : RenderLayer.class.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0) continue;
                if (!RenderLayer.class.isAssignableFrom(m.getReturnType())) continue;
                m.setAccessible(true);
                try {
                    Object v = m.invoke(null);
                    if (v instanceof RenderLayer rl) {
                        String s = rl.toString().toLowerCase();
                        System.err.println("[ChestOverlay] static RenderLayer method: "
                                + m.getName() + "() -> " + s);
                        if (s.contains("line")) {
                            linesLayer = rl;
                            return linesLayer;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        System.err.println("[ChestOverlay] No LINES layer found – using raw GL fallback");
        return null;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void renderDirect() {
        if (!ChestOverlayConfig.enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return;
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) return;

        Vec3d    camPos = client.gameRenderer.getCamera().getPos();
        BlockPos pPos   = client.player.getBlockPos();
        List<Box> boxes = collectBoxes(client, pPos);
        if (boxes.isEmpty()) return;

        float[] rgb = currentColor();
        float r = rgb[0], g = rgb[1], b = rgb[2];

        RenderLayer layer = getLinesLayer();
        if (layer != null) {
            renderVCP(client, layer, boxes, camPos, r, g, b);
        } else {
            renderGL(client, boxes, camPos, r, g, b);
        }
    }

    // ── VCP path (1.21.1 + wherever LINES layer is found) ────────────────────

    private static void renderVCP(MinecraftClient client, RenderLayer layer,
                                   List<Box> boxes, Vec3d camPos,
                                   float r, float g, float b) {
        try {
            VertexConsumerProvider.Immediate consumers =
                    client.getBufferBuilders().getEntityVertexConsumers();
            MatrixStack ms = new MatrixStack();
            ms.translate(-camPos.x, -camPos.y, -camPos.z);
            Matrix4f pose = ms.peek().getPositionMatrix();
            VertexConsumer buf = consumers.getBuffer(layer);
            for (Box box : boxes) {
                addBox(buf, pose,
                    (float)box.minX, (float)box.minY, (float)box.minZ,
                    (float)box.maxX, (float)box.maxY, (float)box.maxZ, r, g, b);
            }
            consumers.draw(layer);
        } catch (Throwable e) {
            System.err.println("[ChestOverlay] VCP error: " + e);
        }
    }

    // ── Raw GL path (fallback for 1.21.11 where LINES layer was removed) ─────

    private static int  glProg   = 0;   // 0=uninit  -1=failed  >0=ready
    private static int  glVao    = 0;
    private static int  glVbo    = 0;
    private static int  glLocMVP = -1;

    private static final String VERT =
        "#version 150 core\n" +
        "in vec3 Position;\nin vec4 Color;\nuniform mat4 MVP;\nout vec4 vColor;\n" +
        "void main(){ gl_Position = MVP * vec4(Position,1.0); vColor = Color; }\n";
    private static final String FRAG =
        "#version 150 core\n" +
        "in vec4 vColor;\nout vec4 fragColor;\n" +
        "void main(){ fragColor = vColor; }\n";

    private static boolean initGL() {
        if (glProg > 0) return true;
        if (glProg < 0) return false;
        try {
            int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vs, VERT);
            GL20.glCompileShader(vs);
            if (GL20.glGetShaderi(vs, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[ChestOverlay] vert: " + GL20.glGetShaderInfoLog(vs));
                return (glProg = -1) > 0;
            }
            int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fs, FRAG);
            GL20.glCompileShader(fs);
            if (GL20.glGetShaderi(fs, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[ChestOverlay] frag: " + GL20.glGetShaderInfoLog(fs));
                return (glProg = -1) > 0;
            }
            glProg = GL20.glCreateProgram();
            GL20.glAttachShader(glProg, vs);
            GL20.glAttachShader(glProg, fs);
            GL20.glBindAttribLocation(glProg, 0, "Position");
            GL20.glBindAttribLocation(glProg, 1, "Color");
            GL20.glLinkProgram(glProg);
            if (GL20.glGetProgrami(glProg, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                System.err.println("[ChestOverlay] link: " + GL20.glGetProgramInfoLog(glProg));
                return (glProg = -1) > 0;
            }
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            glLocMVP = GL20.glGetUniformLocation(glProg, "MVP");

            glVao = GL30.glGenVertexArrays();
            glVbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(glVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, glVbo);
            // stride = 7 floats × 4 bytes = 28
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 28, 0L);   // xyz
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 28, 12L);  // rgba
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);

            System.err.println("[ChestOverlay] GL renderer ready (prog=" + glProg + ")");
            return true;
        } catch (Throwable e) {
            System.err.println("[ChestOverlay] GL init failed: " + e);
            glProg = -1;
            return false;
        }
    }

    private static void renderGL(MinecraftClient client, List<Box> boxes,
                                  Vec3d camPos, float r, float g, float b) {
        if (!initGL()) return;

        // Build CPU vertex buffer: 7 floats/vertex, 2 verts/segment, 12 segments/box
        int vertCount = boxes.size() * 12 * 2;
        float[] data  = new float[vertCount * 7];
        int idx = 0;
        for (Box box : boxes) {
            // Already camera-relative (world pos − camPos)
            float x1=(float)(box.minX-camPos.x), y1=(float)(box.minY-camPos.y), z1=(float)(box.minZ-camPos.z);
            float x2=(float)(box.maxX-camPos.x), y2=(float)(box.maxY-camPos.y), z2=(float)(box.maxZ-camPos.z);
            idx=seg(data,idx, x1,y1,z1, x2,y1,z1, r,g,b); idx=seg(data,idx, x2,y1,z1, x2,y1,z2, r,g,b);
            idx=seg(data,idx, x2,y1,z2, x1,y1,z2, r,g,b); idx=seg(data,idx, x1,y1,z2, x1,y1,z1, r,g,b);
            idx=seg(data,idx, x1,y2,z1, x2,y2,z1, r,g,b); idx=seg(data,idx, x2,y2,z1, x2,y2,z2, r,g,b);
            idx=seg(data,idx, x2,y2,z2, x1,y2,z2, r,g,b); idx=seg(data,idx, x1,y2,z2, x1,y2,z1, r,g,b);
            idx=seg(data,idx, x1,y1,z1, x1,y2,z1, r,g,b); idx=seg(data,idx, x2,y1,z1, x2,y2,z1, r,g,b);
            idx=seg(data,idx, x2,y1,z2, x2,y2,z2, r,g,b); idx=seg(data,idx, x1,y1,z2, x1,y2,z2, r,g,b);
        }

        // Build MVP.  Vertices are already camera-relative, so View = R^{-1} only.
        int fw = client.getWindow().getFramebufferWidth();
        int fh = client.getWindow().getFramebufferHeight();
        if (fw <= 0 || fh <= 0) return;

        float fovDeg = 70f;
        try { fovDeg = (float)(int) client.options.getFov().getValue(); } catch (Throwable ignored) {}

        Matrix4f proj  = new Matrix4f().perspective(
                (float) Math.toRadians(fovDeg), (float)fw / fh, 0.05f, 1024f);
        Quaternionf rot = client.gameRenderer.getCamera().getRotation();
        Matrix4f view   = new Matrix4f().rotation(rot.conjugate(new Quaternionf()));
        float[] mvpArr  = new float[16];
        proj.mul(view, new Matrix4f()).get(mvpArr);

        try {
            int prevProg = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVao  = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

            GL30.glBindVertexArray(glVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, glVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            GL20.glUseProgram(glProg);
            GL20.glUniformMatrix4fv(glLocMVP, false, mvpArr);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDrawArrays(GL11.GL_LINES, 0, vertCount);

            GL30.glBindVertexArray(prevVao);
            GL20.glUseProgram(prevProg);
        } catch (Throwable e) {
            System.err.println("[ChestOverlay] GL draw error: " + e);
        }
    }

    private static int seg(float[] d, int i,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r,  float g,  float b) {
        d[i++]=x1;d[i++]=y1;d[i++]=z1;d[i++]=r;d[i++]=g;d[i++]=b;d[i++]=1f;
        d[i++]=x2;d[i++]=y2;d[i++]=z2;d[i++]=r;d[i++]=g;d[i++]=b;d[i++]=1f;
        return i;
    }

    // ── VCP box drawing ───────────────────────────────────────────────────────

    private static void addBox(VertexConsumer buf, Matrix4f pose,
                                float x1,float y1,float z1, float x2,float y2,float z2,
                                float r, float g, float b) {
        line(buf,pose, x1,y1,z1, x2,y1,z1, r,g,b); line(buf,pose, x2,y1,z1, x2,y1,z2, r,g,b);
        line(buf,pose, x2,y1,z2, x1,y1,z2, r,g,b); line(buf,pose, x1,y1,z2, x1,y1,z1, r,g,b);
        line(buf,pose, x1,y2,z1, x2,y2,z1, r,g,b); line(buf,pose, x2,y2,z1, x2,y2,z2, r,g,b);
        line(buf,pose, x2,y2,z2, x1,y2,z2, r,g,b); line(buf,pose, x1,y2,z2, x1,y2,z1, r,g,b);
        line(buf,pose, x1,y1,z1, x1,y2,z1, r,g,b); line(buf,pose, x2,y1,z1, x2,y2,z1, r,g,b);
        line(buf,pose, x2,y1,z2, x2,y2,z2, r,g,b); line(buf,pose, x1,y1,z2, x1,y2,z2, r,g,b);
    }

    private static void line(VertexConsumer buf, Matrix4f pose,
                               float x1,float y1,float z1, float x2,float y2,float z2,
                               float r, float g, float b) {
        float dx=x2-x1,dy=y2-y1,dz=z2-z1;
        float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(len==0) return;
        buf.vertex(pose,x1,y1,z1).color(r,g,b,1f).normal(dx/len,dy/len,dz/len);
        buf.vertex(pose,x2,y2,z2).color(r,g,b,1f).normal(dx/len,dy/len,dz/len);
    }

    // ── Color ─────────────────────────────────────────────────────────────────

    private static float[] currentColor() {
        long elapsed = System.currentTimeMillis() - START_TIME;
        return switch (ChestOverlayConfig.colorMode) {
            case RGB -> {
                float hue=(elapsed%3000L)/3000f;
                int argb=Color.HSBtoRGB(hue,1f,1f);
                yield new float[]{((argb>>16)&0xFF)/255f,((argb>>8)&0xFF)/255f,(argb&0xFF)/255f};
            }
            case CUSTOM -> new float[]{
                ChestOverlayConfig.color1R/255f,ChestOverlayConfig.color1G/255f,ChestOverlayConfig.color1B/255f};
            case PULSE -> {
                long cycleMs=(long)(ChestOverlayConfig.pulseSpeed*1000f);
                float t=(elapsed%cycleMs)/(float)cycleMs;
                float blend=(float)(Math.sin(t*Math.PI*2)*0.5+0.5);
                yield new float[]{
                    lerp(ChestOverlayConfig.color1R/255f,ChestOverlayConfig.color2R/255f,blend),
                    lerp(ChestOverlayConfig.color1G/255f,ChestOverlayConfig.color2G/255f,blend),
                    lerp(ChestOverlayConfig.color1B/255f,ChestOverlayConfig.color2B/255f,blend)};
            }
        };
    }

    private static float lerp(float a,float b,float t){return a+(b-a)*t;}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<Box> collectBoxes(MinecraftClient client, BlockPos pPos) {
        List<Box> boxes = new ArrayList<>();
        int chunkRange = RENDER_RANGE/16+1;
        ChunkPos center = new ChunkPos(pPos);
        for (int cx=center.x-chunkRange; cx<=center.x+chunkRange; cx++) {
            for (int cz=center.z-chunkRange; cz<=center.z+chunkRange; cz++) {
                WorldChunk chunk = client.world.getChunkManager().getWorldChunk(cx,cz);
                if (chunk==null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!isStorage(be)) continue;
                    BlockPos pos=be.getPos();
                    if (pos.getSquaredDistance(pPos)>(double)RENDER_RANGE*RENDER_RANGE) continue;
                    boxes.add(getHitbox(client,pos));
                }
            }
        }
        return boxes;
    }

    private static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
            || be instanceof ShulkerBoxBlockEntity || be instanceof EnderChestBlockEntity;
    }

    private static Box getHitbox(MinecraftClient client, BlockPos pos) {
        try {
            VoxelShape shape=client.world.getBlockState(pos).getOutlineShape(client.world,pos);
            if (!shape.isEmpty()) return shape.getBoundingBox().offset(pos);
        } catch (Exception ignored) {}
        return new Box(pos);
    }
}
