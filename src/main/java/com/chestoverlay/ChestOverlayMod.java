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

    @Override
    public void onInitializeClient() {
        ChestOverlayConfig.load();

        OPEN_GUI = KeyBindingHelper.registerKeyBinding(makeKeyBinding(
            "key.chestoverlay.open_gui",
            GLFW.GLFW_KEY_INSERT,
            "category.chestoverlay"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_GUI.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ChestOverlayScreen());
                }
            }
        });

        WorldRenderEvents.LAST.register(ChestBoxRenderer::render);
    }

    private static KeyBinding makeKeyBinding(String id, int code, String category) {
        InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(code);

        for (java.lang.reflect.Constructor<?> ctor : KeyBinding.class.getDeclaredConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length < 3 || p[0] != String.class) continue;
            try {
                // The last param is either String (old) or KeyBinding.Category (1.21.11+)
                Object catArg = (p[p.length - 1] == String.class)
                    ? category
                    : instantiateCategory(p[p.length - 1], category);
                if (catArg == null) continue;

                if (p.length == 3 && (p[1] == int.class || p[1] == Integer.class)) {
                    return (KeyBinding) ctor.newInstance(id, code, catArg);
                }
                if (p.length == 3 && p[1] == InputUtil.Key.class) {
                    return (KeyBinding) ctor.newInstance(id, key, catArg);
                }
                if (p.length == 4 && p[1] == InputUtil.Type.class && (p[2] == int.class || p[2] == Integer.class)) {
                    return (KeyBinding) ctor.newInstance(id, InputUtil.Type.KEYSYM, code, catArg);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        StringBuilder sb = new StringBuilder("No usable KeyBinding constructor. Available:");
        for (java.lang.reflect.Constructor<?> c : KeyBinding.class.getDeclaredConstructors()) sb.append("\n  ").append(c);
        throw new RuntimeException(sb.toString());
    }

    /** Creates a KeyBinding.Category (MC 1.21.11+) from a translation-key string via reflection. */
    private static Object instantiateCategory(Class<?> categoryClass, String translationKey) {
        // Try a (String) constructor — likely a record canonical constructor
        try {
            return categoryClass.getDeclaredConstructor(String.class).newInstance(translationKey);
        } catch (ReflectiveOperationException ignored) {
        }
        // Try a static factory method that accepts a String and returns this type
        for (java.lang.reflect.Method m : categoryClass.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == String.class
                    && categoryClass.isAssignableFrom(m.getReturnType())) {
                try {
                    return m.invoke(null, translationKey);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }
}
