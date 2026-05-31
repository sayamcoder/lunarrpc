package com.lunarrpc;

import java.io.*;
import java.util.Properties;

/**
 * Simple configuration for the Lunar RPC mod.
 *
 * The config file is stored at:
 *   <game_dir>/config/lunar-rpc.properties
 *
 * Supported properties:
 *   - details       : The "details" line shown on Discord (default: "Playing Minecraft with Lunar Client")
 *   - largeImageKey : Discord asset key for the large image (default: "lunar")
 *   - largeImageText: Tooltip for the large image (default: "Lunar Client")
 *   - showTimer     : Whether to show the elapsed timer (default: true)
 *   - enabled       : Master toggle for the mod (default: true)
 */
public class LunarRPCConfig {

    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "lunar-rpc.properties";

    // Default values
    private String details = "Playing Minecraft with Lunar Client";
    private String largeImageKey = "lunar";
    private String largeImageText = "Lunar Client";
    private boolean showTimer = true;
    private boolean enabled = true;

    public LunarRPCConfig() {
        load();
    }

    public void load() {
        File file = new File(CONFIG_DIR, CONFIG_FILE);
        if (!file.exists()) {
            save(); // Create default config on first run
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(is);

            details = props.getProperty("details", details);
            largeImageKey = props.getProperty("largeImageKey", largeImageKey);
            largeImageText = props.getProperty("largeImageText", largeImageText);
            showTimer = Boolean.parseBoolean(props.getProperty("showTimer", String.valueOf(showTimer)));
            enabled = Boolean.parseBoolean(props.getProperty("enabled", String.valueOf(enabled)));

        } catch (IOException e) {
            LunarRPCMod.LOGGER.warn("[LunarRPC] Failed to load config, using defaults", e);
        }
    }

    public void save() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(dir, CONFIG_FILE);
        try (OutputStream os = new FileOutputStream(file)) {
            Properties props = new Properties();
            props.setProperty("details", details);
            props.setProperty("largeImageKey", largeImageKey);
            props.setProperty("largeImageText", largeImageText);
            props.setProperty("showTimer", String.valueOf(showTimer));
            props.setProperty("enabled", String.valueOf(enabled));

            props.store(os, "Lunar RPC Configuration\n"
                    + "Modify these values to customize your Discord Rich Presence.\n"
                    + "Restart Minecraft to apply changes.");
        } catch (IOException e) {
            LunarRPCMod.LOGGER.warn("[LunarRPC] Failed to save config", e);
        }
    }

    // ================================================================
    // Getters
    // ================================================================

    public String getDetails() { return details; }
    public String getLargeImageKey() { return largeImageKey; }
    public String getLargeImageText() { return largeImageText; }
    public boolean isShowTimer() { return showTimer; }
    public boolean isEnabled() { return enabled; }

    // ================================================================
    // Setters
    // ================================================================

    public void setDetails(String details) { this.details = details; }
    public void setLargeImageKey(String key) { this.largeImageKey = key; }
    public void setLargeImageText(String text) { this.largeImageText = text; }
    public void setShowTimer(boolean show) { this.showTimer = show; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
