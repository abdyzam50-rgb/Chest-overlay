package com.chestoverlay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public class ChestOverlayConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("chestoverlay.json");

    public static boolean enabled = true;
    public static ColorMode colorMode = ColorMode.RGB;

    // Color 1 (used by CUSTOM and PULSE modes)
    public static int color1R = 0;
    public static int color1G = 255;
    public static int color1B = 100;

    // Color 2 (used by PULSE mode only)
    public static int color2R = 0;
    public static int color2G = 100;
    public static int color2B = 255;

    // Pulse speed in seconds per full cycle (0.5 – 5.0)
    public static float pulseSpeed = 2.0f;

    public enum ColorMode { RGB, CUSTOM, PULSE }

    public static void save() {
        try (Writer w = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(new Data(), w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        if (!CONFIG_PATH.toFile().exists()) return;
        try (Reader r = new FileReader(CONFIG_PATH.toFile())) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null) return;
            enabled    = d.enabled;
            color1R    = clamp(d.color1R);
            color1G    = clamp(d.color1G);
            color1B    = clamp(d.color1B);
            color2R    = clamp(d.color2R);
            color2G    = clamp(d.color2G);
            color2B    = clamp(d.color2B);
            pulseSpeed = Math.max(0.5f, Math.min(5.0f, d.pulseSpeed));
            try { colorMode = ColorMode.valueOf(d.colorMode); } catch (Exception ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // Inner data class mirrors every field for Gson serialisation
    private static class Data {
        boolean enabled   = ChestOverlayConfig.enabled;
        String colorMode  = ChestOverlayConfig.colorMode.name();
        int color1R       = ChestOverlayConfig.color1R;
        int color1G       = ChestOverlayConfig.color1G;
        int color1B       = ChestOverlayConfig.color1B;
        int color2R       = ChestOverlayConfig.color2R;
        int color2G       = ChestOverlayConfig.color2G;
        int color2B       = ChestOverlayConfig.color2B;
        float pulseSpeed  = ChestOverlayConfig.pulseSpeed;
    }
}
