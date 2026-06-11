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

    /**
     * KeyBinding constructor changed across MC versions:
     *   1.21.4+ / 1.21.11: KeyBinding(String, InputUtil.Key, String)
     *   ≤1.21.1:            KeyBinding(String, InputUtil.Type, int, String)
     *
     * Both paths use reflection so the same JAR handles either version.
     * InputUtil.Type.KEYSYM.createFromCode() is stable across all versions
     * and gives us the InputUtil.Key needed for the new constructor.
     */
    private static KeyBinding makeKeyBinding(String id, int code, String category) {
        // InputUtil.Key wraps type+code and exists in all relevant MC versions
        InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(code);

        // New API (1.21.4+): KeyBinding(String, InputUtil.Key, String)
        try {
            return (KeyBinding) KeyBinding.class
                .getConstructor(String.class, InputUtil.Key.class, String.class)
                .newInstance(id, key, category);
        } catch (NoSuchMethodException ignored) {
            // fall through to old API
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not create KeyBinding (new API)", e);
        }

        // Old API (≤1.21.1): KeyBinding(String, InputUtil.Type, int, String)
        try {
            return (KeyBinding) KeyBinding.class
                .getConstructor(String.class, InputUtil.Type.class, int.class, String.class)
                .newInstance(id, InputUtil.Type.KEYSYM, code, category);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not create KeyBinding (old API)", e);
        }
    }
}
