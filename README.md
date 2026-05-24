# Medusa-Anti-Xray

# Anti-Xray v1.0.0 - Official Release Notes
## Advanced Server-Side Chunk Obfuscation & Behavioral Anti-Cheat

Anti-Xray is a highly optimized, feature-rich Minecraft server plugin designed to secure your server's economy. It combines real-time packet-level chunk obfuscation with statistical behavior analysis to prevent both client-side X-ray mods and texture packs without degrading server performance.

---

## 🚀 Key Features

### 1. Chunk Obfuscation Engine (Core)
The core of Anti-Xray is its packet interception and manipulation engine, which scrambles block data before it leaves the server.

*   **Three Engine Modes:**
    *   **Mode 1 (Simple Replacement):** Replaces hidden blocks (e.g., diamond ore, chests, spawners) with stone, deepslate, netherrack, or end stone if they are not exposed to air. Best for low-CPU environments.
    *   **Mode 2 (Random Fake Ore Injection):** Replaces hidden blocks and injects random fake ores into solid stone/deepslate/netherrack to mislead X-ray users, rendering their ESP/overlays useless.
    *   **Mode 3 (Layer-Based Obfuscation - Recommended):** Similar to Mode 2, but groups fake ores in horizontal bands (same fake ore type per Y-layer). This minimizes palette expansion and significantly improves packet zlib compression, saving network bandwidth.
*   **Dual-Pipeline Interception:** 
    *   *Paper NMS Hook:* Direct bytecode/Netty pipeline interception for maximum performance on Paper, Purpur, or Pufferfish servers.
    *   *ProtocolLib Fallback:* Full Spigot support via ProtocolLib packet listeners.
*   **Smart Air-Exposure Classification:** Uses an optimized primitive int set lookup to verify the 6 faces of each block. If a block is adjacent to air or transparent blocks, it remains visible.
*   **Height & Dimension Aware:** Automatically switches replacement blocks (e.g., stone above Y=0, deepslate below Y=0 in the Overworld, netherrack in the Nether, end stone in the End).
*   **Tile Entity Protection:** Always hides spawners, chests, trapped chests, and ender chests (regardless of exposure) to block chest-ESP cheats. These are only revealed when players are directly in proximity.

### 2. Proximity Deobfuscation Manager
Restores the real state of blocks to players dynamically as they interact with the world.

*   **Proximity-Based Radius Checks:** Checks player eye positions on a configurable tick interval (e.g., every 5 ticks). If a player is within the update radius (default: 4 blocks), the real block state is sent.
*   **Advanced Visibility Filters:**
    *   *Frustum Culling (Optional):* Restricts block updates to the player's direct field of view (FOV), saving network bandwidth.
    *   *Ray-Cast Line-of-Sight Check (Optional):* Employs DDA/Bresenham voxel traversal to ensure blocks hidden behind solid walls are not revealed until the player has a clear line-of-sight.
*   **Delayed Re-Obfuscation:** Reverts revealed blocks back to hidden/fake states (default: 200 ticks / 10 seconds delay) after a player moves out of the proximity radius. This prevents players from "mapping out" cave systems by flying around.
*   **Action-Triggered Revelations:** Instantly reveals neighboring hidden blocks on key events, such as block breaking, block placement, fluid flow (water/lava), piston extension/retraction, and explosions (TNT, creepers).
*   **Packet Batching & Rate Limiting:** Groups updates within the same tick. Uses single Block Update packets (1-3 blocks), Update Section Blocks packets (4-64 blocks), or Chunk Data packets (>64 blocks) to prevent network congestion, capped at 64 updates per player/tick.

### 3. High-Performance Cache & Async Processing
Designed from the ground up for large player counts and maximum TPS stability.

*   **Two-Level Caching:**
    *   *L1 Memory Cache (LRU):* Powered by the Caffeine library. Stores base obfuscated chunk states. Player-specific deobfuscation is overlaid dynamically on-the-fly, avoiding per-player duplicate memory allocations.
    *   *L2 Disk Cache:* Saves obfuscated chunks asynchronously to region files (`.mca` format parallel to Minecraft regions) to speed up server restarts. Features disk usage budgets and preloading of recently-accessed chunks.
*   **Priority-Queued Async Pipeline:** Offloads all chunk manipulation from the main server thread. Tasks are prioritized based on player proximity (CRITICAL for immediate chunk sends, LOW for pre-caching).
*   **Backpressure Handler & Circuit Breaker:**
    *   Drops low-priority tasks when queue sizes grow.
    *   Returns raw chunk data if queue thresholds are exceeded or if a single chunk takes longer than `chunk-timeout-ms` (default: 50ms) to process, preventing server lag.
    *   Features a circuit breaker to temporarily bypass obfuscation in case of catastrophic overload.
*   **Tick Budget Tracker:** Restricts synchronous packet manipulation overhead to a strict millisecond limit per tick (default: 5ms) to prevent server ticks from running behind.

### 4. Behavioral Statistical Detection
Identifies X-ray users by monitoring real-time mining behavior and patterns, offering a defensive layer even against world-seed leaks.

*   **Playstyle Profiling Heuristic:** Dynamically categorizes players as "cave explorers" or "branch miners" based on the air-adjacency of their mined blocks, adjusting evaluation thresholds automatically to prevent false positives.
*   **Monitored Metrics:**
    *   *Ore-to-Stone & Diamond-to-Stone Ratios*
    *   *Valuable Ore Ratio:* Ratio of highly valuable blocks (diamonds, emeralds, ancient debris) to common ores.
    *   *Mining Efficiency (Straight-to-Ore Ratio):* Percentage of tunnels that go straight to ore deposits without deviation.
    *   *Tunnel Pattern Analysis:* Monitors heading changes toward hidden ores and backtracking behavior.
    *   *Spatial Clustering:* Detects suspicious cluster-mining patterns.
*   **Alert Levels & Actions:**
    *   *INFO:* Low-level flag for slight deviations.
    *   *WARNING:* Mid-level flag; triggers in-game staff alerts.
    *   *CRITICAL:* High-level flag; logs to security files, notifies staff, sends Discord Webhooks (plain/embed), and executes customizable commands (e.g., temporary bans/kicks).
*   **Smoothing and Protection:** Applies exponential moving averages to prevent alerts from lucky streaks, and enforces a minimum sample size (default: 100 blocks) and a grace period for new players.

### 5. Client Countermeasures & Seed Protection
*   **Forced Server Resource Pack:** Enforces a server resource pack with opaque textures to defeat texture-pack-based X-ray. Includes options to kick players who decline or delay their join until the pack is fully loaded.
*   **Seed Leak Audit:** Audits the server's configuration and alerts administrators on startup if features or structure seeds are not randomized in `spigot.yml`, protecting the world from external seed-cracking tools.

### 6. Public Developer API
Exposes deep hooks for third-party integrations:

*   **`AntiXrayAPI` Interface:** Fetch engine modes, check block obfuscation status, and register/unregister custom hidden blocks dynamically.
*   **Custom Obfuscation Providers:** Register a custom `ObfuscationProvider` per-world to implement custom block-hiding logic or replacement materials (perfect for minigames, custom spawners, or dungeons).
*   **Custom Bukkit Events:**
    *   `BlockVisibilityEvent` (Cancellable): Fired when an obfuscated block is revealed.
    *   `PlayerXraySuspicionEvent` (Cancellable): Fired when the detection module flags a player.

---

## 🛠️ Configuration & Commands

### Administrative Commands
Access to admin commands is protected by the `antixray.admin` permission:
*   `/antixray reload` — Reloads configuration from disk and flushes the cache if engine configurations changed.
*   `/antixray stats [player]` — View real-time mining ratios and metrics for players.
*   `/antixray check [player]` — Manually inspect a player's mining patterns and tunnels.
*   `/antixray mode <world> <mode>` — Temporarily adjust engine mode (1, 2, or 3) for a world.
*   `/antixray cache <clear|status>` — Manage the cache memory and view L1 hit rates.
*   `/antixray toggle <world>` — Quickly enable or disable the plugin in a specific world.

---

## 📋 Compatibility Matrix

*   **Minecraft Versions:** 1.19.4, 1.20.x, 1.21.x (tested up to 1.21.4).
*   **Software forks:** Paper, Purpur, Pufferfish (Full NMS optimization), Spigot (requires ProtocolLib), Folia (Experimental - uses regional-aware schedulers).
*   **Soft Dependencies:** ProtocolLib (for Spigot packet interception), Vault (for commands/actions economy integration), PlaceholderAPI (for customizable messages).
