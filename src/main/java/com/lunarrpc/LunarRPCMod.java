package com.lunarrpc;

import com.lunarrpc.discord.DiscordRichPresence;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lunar RPC — Fabric mod entry point.
 *
 * Connects to Discord Rich Presence on game startup and continuously
 * updates the status to show "Playing Minecraft with Lunar Client"
 * along with contextual information (server name, game mode, etc.).
 */
public class LunarRPCMod implements ClientModInitializer {

    public static final String MOD_ID = "lunar-rpc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** How often (in ticks) to refresh the presence. 20 ticks = 1 second. */
    private static final int UPDATE_INTERVAL_TICKS = 200; // every ~10 seconds

    private static LunarRPCConfig config;
    private int tickCounter = 0;
    private long gameStartTimestamp = 0;
    private String lastState = "";
    private String lastDetails = "";
    private boolean reconnectScheduled = false;
    private int reconnectDelay = 0;
    private boolean hasNotifiedPlayer = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[LunarRPC] Initializing Lunar RPC mod...");

        // Load configuration
        config = new LunarRPCConfig();

        // Record game start time for the elapsed timer
        gameStartTimestamp = System.currentTimeMillis() / 1000;

        // Register shutdown hook for clean disconnect
        LunarRPCShutdownHook.register();

        // Connect to Discord on client ticks (ensures MC is fully loaded)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (config.isEnabled()) {
                onTick(client);
            }
        });

        LOGGER.info("[LunarRPC] Mod initialized. Will connect to Discord on first tick.");
    }

    /**
     * Called every client tick. Handles:
     * - Initial connection to Discord
     * - Periodic presence updates
     * - Reconnection if the IPC connection drops
     */
    private void onTick(MinecraftClient client) {
        // --- Reconnection logic ---
        if (reconnectScheduled) {
            reconnectDelay--;
            if (reconnectDelay <= 0) {
                reconnectScheduled = false;
                LOGGER.info("[LunarRPC] Attempting reconnection...");
                DiscordRichPresence.connect();
                if (DiscordRichPresence.isConnected()) {
                    hasNotifiedPlayer = false;
                    updatePresence(client);
                }
            }
            return;
        }

        // --- Initial connection ---
        if (!DiscordRichPresence.isConnected()) {
            DiscordRichPresence.connect();
            if (DiscordRichPresence.isConnected()) {
                updatePresence(client);
                // Notify the player once
                if (!hasNotifiedPlayer && client.player != null) {
                    client.player.sendMessage(
                        Text.literal("\u00a7b[LunarRPC] \u00a7aDiscord Rich Presence connected! \u00a77Showing: Playing Minecraft with Lunar Client"),
                        false
                    );
                    hasNotifiedPlayer = true;
                }
            }
            return;
        }

        // --- Periodic presence update ---
        tickCounter++;
        if (tickCounter >= UPDATE_INTERVAL_TICKS) {
            tickCounter = 0;
            updatePresence(client);

            // Check if we're still connected
            if (!DiscordRichPresence.isConnected()) {
                hasNotifiedPlayer = false;
                scheduleReconnect(200); // try again in ~10 seconds
            }
        }
    }

    /**
     * Builds and sends the Rich Presence payload.
     *
     * The presence shows:
     *   - Details: configurable, defaults to "Playing Minecraft with Lunar Client"
     *   - State:   contextual info (singleplayer / server name / menu)
     *   - Large image: configurable key (default: "lunar")
     *   - Elapsed timer from game start (if enabled in config)
     */
    private void updatePresence(MinecraftClient client) {
        try {
            String details = config.getDetails();
            String state = buildState(client);

            // Only send if the state actually changed (reduces IPC traffic)
            if (state.equals(lastState) && details.equals(lastDetails)) {
                return;
            }
            lastState = state;
            lastDetails = details;

            long timestamp = config.isShowTimer() ? gameStartTimestamp : 0;

            DiscordRichPresence.updatePresence(
                state,                                // state  (small text under details)
                details,                              // details (large bold text)
                config.getLargeImageKey(),            // large image key
                config.getLargeImageText(),           // large image tooltip
                "minecraft",                          // small image key
                "Minecraft",                          // small image tooltip
                timestamp                             // elapsed timer start (0 = no timer)
            );

            LOGGER.debug("[LunarRPC] Updated presence: {} | {}", details, state);
        } catch (Exception e) {
            LOGGER.error("[LunarRPC] Failed to update presence", e);
        }
    }

    /**
     * Determines what to show as the "state" line in Discord.
     *
     * @return a human-readable description of the current game context
     */
    private String buildState(MinecraftClient client) {
        // In the main menu (no world loaded)
        if (client.world == null) {
            return "In Main Menu";
        }

        // Singleplayer
        if (client.isInSingleplayer()) {
            String worldName = "Singleplayer";
            if (client.getServer() != null && client.getServer().getSaveProperties() != null) {
                String levelName = client.getServer().getSaveProperties().getLevelName();
                if (levelName != null && !levelName.isEmpty()) {
                    worldName = "SP: " + levelName;
                }
            }
            return worldName;
        }

        // Multiplayer
        if (client.getCurrentServerEntry() != null) {
            String serverName = client.getCurrentServerEntry().name;
            String serverAddress = client.getCurrentServerEntry().address;
            if (serverName != null && !serverName.isEmpty() && !serverName.equals(serverAddress)) {
                return "On " + serverName;
            }
            if (serverAddress != null && !serverAddress.isEmpty()) {
                return "On " + serverAddress;
            }
        }

        return "In Multiplayer";
    }

    /**
     * Schedules a reconnection attempt after the specified number of ticks.
     */
    private void scheduleReconnect(int ticks) {
        reconnectScheduled = true;
        reconnectDelay = ticks;
        LOGGER.warn("[LunarRPC] Connection lost. Reconnecting in {} ticks...", ticks);
    }

    /**
     * Called when the game is closing to cleanly disconnect from Discord.
     */
    public static void shutdown() {
        LOGGER.info("[LunarRPC] Shutting down Discord RPC connection...");
        DiscordRichPresence.clearPresence();
        DiscordRichPresence.disconnect();
    }

    /**
     * Returns the mod configuration instance.
     */
    public static LunarRPCConfig getConfig() {
        return config;
    }
}
