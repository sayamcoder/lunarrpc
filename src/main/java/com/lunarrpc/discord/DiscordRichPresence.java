package com.lunarrpc.discord;

/**
 * Native Discord Rich Presence wrapper using JNA.
 *
 * This class provides a pure-JNA implementation of the Discord RPC
 * protocol, avoiding the need for the native discord-rpc.dll / libdiscord-rpc.so
 * that the official Java wrapper requires. It communicates with the local
 * Discord client via the IPC named pipe.
 *
 * On Windows the pipe is: \\.\pipe\discord-ipc-0
 * On macOS / Linux the pipe is a Unix domain socket.
 */
public class DiscordRichPresence {

    // ========================================================================
    // Discord RPC constants
    // ========================================================================
    private static final int RPC_VERSION = 1;
    private static final String APPLICATION_ID = "1167568919534755980"; // Lunar Client App ID

    // Opcode types for the Discord IPC protocol
    private static final int OPCODE_HANDSHAKE = 0;
    private static final int OPCODE_FRAME = 1;
    private static final int OPCODE_CLOSE = 2;
    private static final int OPCODE_PING = 3;
    private static final int OPCODE_PONG = 4;

    private static DiscordIPC ipcClient;
    private static boolean connected = false;
    private static Thread callbackThread;

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Connects to the local Discord client and sends a handshake.
     * Must be called before {@link #updatePresence}.
     */
    public static synchronized void connect() {
        if (connected) {
            return;
        }

        try {
            ipcClient = new DiscordIPC();
            if (!ipcClient.connect()) {
                System.err.println("[LunarRPC] Failed to connect to Discord IPC");
                return;
            }

            // Send handshake
            String handshake = "{\"v\":" + RPC_VERSION + ",\"client_id\":\"" + APPLICATION_ID + "\"}";
            ipcClient.send(OPCODE_HANDSHAKE, handshake);

            // Read the READY event
            DiscordIPC.IPCMessage response = ipcClient.read();
            if (response == null || response.opcode == OPCODE_CLOSE) {
                System.err.println("[LunarRPC] Discord rejected the handshake");
                ipcClient.close();
                return;
            }

            connected = true;
            System.out.println("[LunarRPC] Connected to Discord Rich Presence");

            // Start callback reader thread (Discord expects us to keep reading)
            callbackThread = new Thread(() -> {
                while (connected && ipcClient != null) {
                    try {
                        DiscordIPC.IPCMessage msg = ipcClient.read();
                        if (msg == null || msg.opcode == OPCODE_CLOSE) {
                            System.out.println("[LunarRPC] Discord closed the connection");
                            connected = false;
                            break;
                        }
                        if (msg.opcode == OPCODE_PING) {
                            ipcClient.send(OPCODE_PONG, msg.payload);
                        }
                    } catch (Exception e) {
                        if (connected) {
                            System.err.println("[LunarRPC] IPC read error: " + e.getMessage());
                            connected = false;
                        }
                        break;
                    }
                }
            }, "LunarRPC-Callback");
            callbackThread.setDaemon(true);
            callbackThread.start();

        } catch (Exception e) {
            System.err.println("[LunarRPC] Error connecting to Discord: " + e.getMessage());
            connected = false;
        }
    }

    /**
     * Updates the Rich Presence shown on your Discord profile.
     *
     * @param state       The small text under the game name (e.g. "Playing on server")
     * @param details     The larger detail text (e.g. "Minecraft 1.21.4")
     * @param largeImage  Key of the large image registered on the Discord App
     * @param largeText   Tooltip for the large image
     * @param smallImage  Key of the small image registered on the Discord App
     * @param smallText   Tooltip for the small image
     * @param startTimestamp Epoch seconds for "elapsed" timer; 0 = no timer
     */
    public static synchronized void updatePresence(
            String state,
            String details,
            String largeImage,
            String largeText,
            String smallImage,
            String smallText,
            long startTimestamp
    ) {
        if (!connected || ipcClient == null) return;

        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":")
                .append(getPID())
                .append(",\"activity\":{\"state\":")
                .append(escapeJson(state))
                .append(",\"details\":")
                .append(escapeJson(details));

            if (startTimestamp > 0) {
                json.append(",\"timestamps\":{\"start\":").append(startTimestamp).append("}");
            }

            // Large image
            if (largeImage != null && !largeImage.isEmpty()) {
                json.append(",\"assets\":{\"large_image\":").append(escapeJson(largeImage));
                if (largeText != null && !largeText.isEmpty()) {
                    json.append(",\"large_text\":").append(escapeJson(largeText));
                }
                // Small image
                if (smallImage != null && !smallImage.isEmpty()) {
                    json.append(",\"small_image\":").append(escapeJson(smallImage));
                    if (smallText != null && !smallText.isEmpty()) {
                        json.append(",\"small_text\":").append(escapeJson(smallText));
                    }
                }
                json.append("}");
            }

            json.append("}}}");

            String frame = json.toString();
            ipcClient.send(OPCODE_FRAME, frame);

        } catch (Exception e) {
            System.err.println("[LunarRPC] Error updating presence: " + e.getMessage());
        }
    }

    /**
     * Sends a SET_ACTIVITY with empty activity to clear the presence.
     */
    public static synchronized void clearPresence() {
        if (!connected || ipcClient == null) return;

        try {
            String frame = "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":" + getPID() + ",\"activity\":null}}";
            ipcClient.send(OPCODE_FRAME, frame);
        } catch (Exception e) {
            System.err.println("[LunarRPC] Error clearing presence: " + e.getMessage());
        }
    }

    /**
     * Disconnects from the Discord IPC.
     */
    public static synchronized void disconnect() {
        if (!connected) return;
        connected = false;

        try {
            if (ipcClient != null) {
                ipcClient.send(OPCODE_CLOSE, "");
                ipcClient.close();
            }
        } catch (Exception e) {
            // Ignore close errors
        }
        ipcClient = null;
        System.out.println("[LunarRPC] Disconnected from Discord Rich Presence");
    }

    public static boolean isConnected() {
        return connected;
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static String escapeJson(String value) {
        if (value == null) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private static int getPID() {
        try {
            return (int) ProcessHandle.current().pid();
        } catch (Exception e) {
            return 0;
        }
    }
}
