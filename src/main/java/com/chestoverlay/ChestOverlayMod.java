package com.chestoverlay;

import com.chestoverlay.config.ChestOverlayConfig;
import com.chestoverlay.gui.ChestOverlayScreen;
import com.chestoverlay.render.ChestBoxRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ChestOverlayMod implements ClientModInitializer {

    public static KeyBinding OPEN_GUI;

    private static boolean insertWasDown = false;

    @Override
    public void onInitializeClient() {
        ChestOverlayConfig.load();

        try {
            OPEN_GUI = KeyBindingHelper.registerKeyBinding(makeKeyBinding(
                "key.chestoverlay.open_gui",
                GLFW.GLFW_KEY_INSERT,
                "category.chestoverlay"
            ));
        } catch (Exception e) {
            System.err.println("[ChestOverlay] Key binding registration failed (mod still works, INSERT hardcoded): " + e.getMessage());
            OPEN_GUI = null;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (OPEN_GUI != null) {
                while (OPEN_GUI.wasPressed()) {
                    if (client.currentScreen == null) client.setScreen(new ChestOverlayScreen());
                }
            } else {
                // Fallback when keybind couldn't be registered: poll INSERT directly
                try {
                    boolean down = InputUtil.isKeyPressed(
                        client.getWindow().getHandle(),
                        GLFW.GLFW_KEY_INSERT
                    );
                    if (down && !insertWasDown && client.currentScreen == null)
                        client.setScreen(new ChestOverlayScreen());
                    insertWasDown = down;
                } catch (Exception ignored) {}
            }
        });

        WorldRenderEvents.LAST.register(ChestBoxRenderer::render);
    }

    private static KeyBinding makeKeyBinding(String id, int code, String category) {
        InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(code);

        for (java.lang.reflect.Constructor<?> ctor : KeyBinding.class.getDeclaredConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length < 3 || p[0] != String.class) continue;
            ctor.setAccessible(true);
            try {
                Object catArg = (p[p.length - 1] == String.class)
                    ? category
                    : instantiateCategory(p[p.length - 1], category);
                if (catArg == null) continue;

                if (p.length == 3 && (p[1] == int.class || p[1] == Integer.class))
                    return (KeyBinding) ctor.newInstance(id, code, catArg);
                if (p.length == 3 && p[1] == InputUtil.Key.class)
                    return (KeyBinding) ctor.newInstance(id, key, catArg);
                if (p.length == 4 && p[1] == InputUtil.Type.class && (p[2] == int.class || p[2] == Integer.class))
                    return (KeyBinding) ctor.newInstance(id, InputUtil.Type.KEYSYM, code, catArg);
            } catch (Exception ignored) {
            }
        }

        // Build a detailed error so we can see exactly what Category looks like
        StringBuilder sb = new StringBuilder("No usable KeyBinding constructor.");
        sb.append("\nKeyBinding constructors:");
        for (java.lang.reflect.Constructor<?> c : KeyBinding.class.getDeclaredConstructors()) sb.append("\n  ").append(c);
        for (java.lang.reflect.Constructor<?> c : KeyBinding.class.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length > 0 && p[p.length - 1] != String.class) {
                Class<?> cat = p[p.length - 1];
                sb.append("\nCategory class: ").append(cat.getName());
                sb.append("\n  constructors:");
                for (java.lang.reflect.Constructor<?> cc : cat.getDeclaredConstructors()) sb.append("\n    ").append(cc);
                sb.append("\n  declared methods:");
                for (java.lang.reflect.Method m : cat.getDeclaredMethods()) sb.append("\n    ").append(m);
                sb.append("\n  declared fields:");
                for (java.lang.reflect.Field f : cat.getDeclaredFields()) sb.append("\n    ").append(f);
                break;
            }
        }
        throw new RuntimeException(sb.toString());
    }

    private static Object instantiateCategory(Class<?> categoryClass, String translationKey) {
        // 1. Try every declared constructor with sensible defaults for unknown param types
        for (java.lang.reflect.Constructor<?> ctor : categoryClass.getDeclaredConstructors()) {
            ctor.setAccessible(true);
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length == 0 || p[0] != String.class) continue;
            try {
                if (p.length == 1)
                    return ctor.newInstance(translationKey);
                if (p.length == 2 && (p[1] == int.class || p[1] == Integer.class))
                    return ctor.newInstance(translationKey, 100);
                if (p.length == 2 && p[1] == boolean.class)
                    return ctor.newInstance(translationKey, false);
                if (p.length == 2 && p[1] == String.class)
                    return ctor.newInstance(translationKey, translationKey);
            } catch (Exception ignored) {
            }
        }
        // 2. Static factory methods (String → Category)
        for (java.lang.reflect.Method m : categoryClass.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            if (!categoryClass.isAssignableFrom(m.getReturnType())) continue;
            m.setAccessible(true);
            try {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class)
                    return m.invoke(null, translationKey);
                if (m.getParameterCount() == 2 && m.getParameterTypes()[0] == String.class
                        && (m.getParameterTypes()[1] == int.class || m.getParameterTypes()[1] == Integer.class))
                    return m.invoke(null, translationKey, 100);
            } catch (Exception ignored) {
            }
        }
        // 3. Steal an existing Category instance from KeyBinding's own static fields
        //    (MC pre-registers categories like MOVEMENT, GAMEPLAY, MISC, etc.)
        for (java.lang.reflect.Field f : KeyBinding.class.getDeclaredFields()) {
            if (!categoryClass.isAssignableFrom(f.getType())) continue;
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object val = f.get(null);
                if (val != null) return val;
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
