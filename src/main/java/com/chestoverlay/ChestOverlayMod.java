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

        java.lang.reflect.Constructor<?>[] ctors = KeyBinding.class.getDeclaredConstructors();

        for (java.lang.reflect.Constructor<?> ctor : ctors) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length < 2 || p[0] != String.class) continue;
            try {
                if (p.length == 3 && p[2] == String.class) {
                    if (p[1] == InputUtil.Key.class) {
                        return (KeyBinding) ctor.newInstance(id, key, category);
                    }
                    if (p[1] == int.class || p[1] == Integer.class) {
                        return (KeyBinding) ctor.newInstance(id, code, category);
                    }
                }
                if (p.length == 4 && p[3] == String.class) {
                    if (p[1] == InputUtil.Type.class && (p[2] == int.class || p[2] == Integer.class)) {
                        return (KeyBinding) ctor.newInstance(id, InputUtil.Type.KEYSYM, code, category);
                    }
                    if (p[1] == InputUtil.Key.class) {
                        return (KeyBinding) ctor.newInstance(id, key, p[2].cast(null), category);
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        StringBuilder sb = new StringBuilder("No usable KeyBinding constructor found. Available:");
        for (java.lang.reflect.Constructor<?> c : ctors) sb.append("\n  ").append(c);
        throw new RuntimeException(sb.toString());
    }
}
