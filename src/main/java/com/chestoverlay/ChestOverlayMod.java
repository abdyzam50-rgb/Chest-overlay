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
     * The KeyBinding constructor signature changed in 1.21.4:
     *   old (≤1.21.1): KeyBinding(String, InputUtil.Type, int, String)
     *   new (≥1.21.4): KeyBinding(String, int, String)
     * Reflection lets the same JAR work on both.
     */
    private static KeyBinding makeKeyBinding(String id, int code, String category) {
        try {
            // New API (1.21.4+ / 1.21.11): no InputUtil.Type parameter
            return KeyBinding.class
                .getConstructor(String.class, int.class, String.class)
                .newInstance(id, code, category);
        } catch (NoSuchMethodException ignored) {
            // Old API (≤1.21.1)
            return new KeyBinding(id, InputUtil.Type.KEYSYM, code, category);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not create KeyBinding", e);
        }
    }
}
