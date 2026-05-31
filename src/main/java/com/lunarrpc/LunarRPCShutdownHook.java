package com.lunarrpc;

import com.lunarrpc.discord.DiscordRichPresence;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * Registers a shutdown hook so the Discord IPC connection is closed
 * cleanly when Minecraft exits.
 */
public class LunarRPCShutdownHook {

    public static void register() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LunarRPCMod.shutdown();
        });

        // Also register a JVM shutdown hook as a safety net
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (DiscordRichPresence.isConnected()) {
                LunarRPCMod.shutdown();
            }
        }, "LunarRPC-Shutdown"));
    }
}
