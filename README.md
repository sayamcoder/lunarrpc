# Lunar RPC — Minecraft Fabric Mod

> Displays **"Playing Minecraft with Lunar Client"** on Discord Rich Presence when playing Minecraft through SKLauncher.

## Features

- 🔗 **Discord Rich Presence** — Shows "Playing Minecraft with Lunar Client" on your Discord profile
- ⏱️ **Elapsed Timer** — Displays how long you've been playing
- 🌍 **Contextual State** — Shows whether you're in singleplayer, multiplayer, or the main menu
- 🔄 **Auto-Reconnect** — Automatically reconnects if the Discord IPC connection drops
- ⚙️ **Configurable** — Edit `config/lunar-rpc.properties` to customize the display text
- 🧹 **Clean Shutdown** — Properly disconnects from Discord when Minecraft closes

## How It Looks on Discord

```
🎮 Playing Minecraft with Lunar Client
   ├─ Details:  Playing Minecraft with Lunar Client
   ├─ State:    On Hypixel  (or "SP: My World", "In Main Menu")
   ├─ Timer:    01:23:45 elapsed
   └─ Images:   Lunar Client icon + Minecraft icon
```

## Requirements

| Requirement       | Version           |
|-------------------|-------------------|
| Minecraft         | 1.21.4            |
| Fabric Loader     | 0.16.9+           |
| Fabric API        | 0.112.2+          |
| Java              | 21+               |
| Discord Desktop   | Running           |

## Installation

### Option A: Pre-built JAR (Recommended)

1. Download the latest `lunar-rpc-mod-1.0.0.jar` from the releases page
2. Open your SKLauncher instance folder
3. Navigate to the `mods` folder (create it if it doesn't exist)
4. Copy the JAR file into the `mods` folder
5. Launch Minecraft through SKLauncher — the mod will auto-connect to Discord

### Option B: Build from Source

#### Prerequisites
- Java Development Kit (JDK) 21 or newer
- Git (optional, for cloning)

#### Build Steps

```bash
# 1. Clone or download this project
git clone <repo-url> lunar-rpc-mod
cd lunar-rpc-mod

# 2. Build the mod
./gradlew build

# 3. The compiled JAR will be at:
#    build/libs/lunar-rpc-mod-1.0.0.jar
```

#### Install the Built JAR

1. Find your SKLauncher instance directory (usually `.sklauncher/instances/<instance-name>/`)
2. Place the JAR in the `mods/` subdirectory
3. Also ensure Fabric API is installed in the same `mods/` folder
4. Launch the game through SKLauncher

## Configuration

The mod creates a config file at `<game_dir>/config/lunar-rpc.properties` on first launch.

```properties
# lunar-rpc.properties

# The "details" line shown on Discord (the bold top line)
details=Playing Minecraft with Lunar Client

# Discord asset key for the large image
# (must match an image registered on the Discord Application)
largeImageKey=lunar

# Tooltip when hovering over the large image
largeImageText=Lunar Client

# Whether to show the elapsed time timer
showTimer=true

# Master toggle — set to false to disable the mod entirely
enabled=true
```

**Note:** Restart Minecraft after changing the config for changes to take effect.

## How It Works

1. **On game launch**, the mod connects to the local Discord client via the IPC named pipe
2. **It sends a handshake** with the Lunar Client Discord Application ID (`1167568919534755980`)
3. **Every ~10 seconds**, it sends a `SET_ACTIVITY` command with your current game state
4. **On game exit**, it clears the presence and cleanly disconnects

The mod uses a pure Java IPC implementation — no native libraries (`.dll`/`.so`) are required.

## Discord Application ID

This mod uses the **Lunar Client** Discord Application ID: `1167568919534755980`

This is what makes Discord display "Playing Lunar Client" as the application name. If you want to use a different application (e.g., your own Discord bot), change the `APPLICATION_ID` in `DiscordRichPresence.java` and register Rich Presence assets on the [Discord Developer Portal](https://discord.com/developers/applications).

## Troubleshooting

### Discord doesn't show the status
- Make sure the **Discord desktop app** is running (not the web version)
- Check that Discord has **"Display current activity"** enabled in Settings → Activity Privacy
- Try restarting both Discord and Minecraft
- Check the Minecraft log for `[LunarRPC]` messages

### "Failed to connect to Discord IPC"
- Ensure Discord is fully started before launching Minecraft
- On Linux, check that `XDG_RUNTIME_DIR` is set (`echo $XDG_RUNTIME_DIR`)
- On Linux with Flatpak Discord, you may need to allow socket access

### The mod doesn't load
- Verify you have **Fabric Loader** and **Fabric API** installed
- Check that the JAR is in the correct `mods/` folder for your SKLauncher instance
- Ensure your Java version is 21+

## Project Structure

```
lunar-rpc-mod/
├── build.gradle                          # Gradle build configuration
├── settings.gradle                       # Gradle settings
├── gradle.properties                     # Mod version & dependency versions
├── src/
│   └── main/
│       ├── java/com/lunarrpc/
│       │   ├── LunarRPCMod.java          # Main mod entry point
│       │   ├── LunarRPCConfig.java       # Configuration loader/saver
│       │   ├── LunarRPCShutdownHook.java # Clean shutdown handler
│       │   └── discord/
│       │       ├── DiscordRichPresence.java  # High-level RPC API
│       │       └── DiscordIPC.java           # Low-level IPC protocol
│       └── resources/
│           ├── fabric.mod.json           # Fabric mod metadata
│           └── lunar-rpc.mixins.json     # Mixin configuration
└── README.md
```

## License

MIT — Free to use, modify, and distribute.
