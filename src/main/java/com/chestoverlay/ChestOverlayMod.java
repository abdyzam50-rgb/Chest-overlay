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

        // Keybind shows up under "Chest Overlay" in Options → Controls.
        // Default: INSERT — can be rebound there like any vanilla keybind.
        OPEN_GUI = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.chestoverlay.open_gui",
            InputUtil.Type.KEYSYM,
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
}
