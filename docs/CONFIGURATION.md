# Anti-Xray Configuration Guide

This guide provides a detailed explanation of every configuration option in `config.yml`, recommended presets for different server types, performance tuning tips, and troubleshooting advice.

---

## Table of Contents
1. [Global Settings](#global-settings)
2. [Obfuscation Engine Settings](#obfuscation-engine-settings)
3. [Proximity & Deobfuscation Settings](#proximity--deobfuscation-settings)
4. [Cache Settings](#cache-settings)
5. [Async Processing Settings](#async-processing-settings)
6. [Statistical Detection Settings](#statistical-detection-settings)
7. [Resource Pack Countermeasures](#resource-pack-countermeasures)
8. [Per-World Overrides](#per-world-overrides)
9. [Recommended Presets](#recommended-presets)
10. [Performance Tuning & Troubleshooting](#performance-tuning--troubleshooting)

---

## Global Settings

### `enabled`
* **Type:** Boolean
* **Default:** `true`
* **Description:** Globally enables or disables the Anti-Xray plugin. When set to `false`, all obfuscation, listeners, and detection engines are disabled.

### `bypass-permission`
* **Type:** String
* **Default:** `antixray.bypass`
* **Description:** The permission node that allows players to bypass block obfuscation (meaning they will see real blocks directly, without needing to stand close or mine them).

---

## Obfuscation Engine Settings

### `engine-mode`
* **Type:** Integer / String
* **Default:** `3` (or `MODE_3`)
* **Description:** Selects the obfuscation engine algorithm.
  * **`1` (MODE_1 - Simple Replacement):** Replaces hidden blocks (e.g., ores) with standard filler blocks (`stone`, `deepslate`, `netherrack`). This has the lowest CPU overhead, but it only hides ores that are completely enclosed by solid blocks. X-ray users can still see ores exposed to air pockets (like caves, ravines, and mineshafts).
  * **`2` (MODE_2 - Random Fake Ore Injection):** Replaces non-exposed blocks with fake ores randomly based on the `fake-ore-chance`. This completely blinds X-ray users, as they see a massive wall of fake ores. However, it increases network packet size and can look messy or "pop" visually during rapid mining.
  * **`3` (MODE_3 - Layer-based Obfuscation) [Recommended]:** Similar to Mode 2, but generates consistent fake ores based on the Y-layer coordinate using a seed-based hash. This ensures that when chunks are sent to the client, the fake blocks are identical across packet updates, maximizing network packet compression (saving bandwidth) and preventing visual flickering.

### `hidden-blocks`
* **Type:** List of Materials
* **Description:** A list of block types that the engine should hide from the client.
* **Example:**
```yaml
hidden-blocks:
  - diamond_ore
  - deepslate_diamond_ore
  - chest
  - spawner
```

### `replacement-blocks`
* **Type:** Configuration Section
* **Description:** Defines the filler blocks used to obfuscate the hidden blocks, categorized by environment.
* **Options:**
  * `overworld.default`: Standard filler block for the Overworld above deepslate level (default: `stone`).
  * `overworld.below-y`: Filler block used for deep Overworld areas (default: `deepslate`).
  * `overworld.deepslate-below-y`: The Y-level threshold below which the deep Overworld filler block is used (default: `0`).
  * `nether`: Filler block used in the Nether (default: `netherrack`).
  * `end`: Filler block used in the End (default: `end_stone`).
* **Example:**
```yaml
replacement-blocks:
  overworld:
    default: stone
    below-y: deepslate
    deepslate-below-y: 0
  nether: netherrack
  end: end_stone
```

### `max-block-height`
* **Type:** Integer
* **Default:** `64`
* **Description:** The maximum Y-coordinate where obfuscation is applied. Blocks above this height are never obfuscated. This saves CPU cycles since valuable ores rarely generate near the surface.

### `fake-ore-chance`
* **Type:** Double (0.0 to 1.0)
* **Default:** `0.07` (7%)
* **Description:** The probability of replacing a hidden block candidate with a fake ore in Modes 2 and 3. Higher values increase obfuscation density (blinding X-ray users more effectively) but increase network usage and packet size.

### `lava-obscures`
* **Type:** Boolean
* **Default:** `true`
* **Description:** If set to `true`, blocks adjacent to lava are treated as hidden (meaning they will be obfuscated). If set to `false`, blocks adjacent to lava are deobfuscated so that players can see ores through lava pools.

### `leaves-are-transparent`
* **Type:** Boolean
* **Default:** `true`
* **Description:** If set to `true`, blocks adjacent to leaves are treated as air-exposed and will be deobfuscated. This prevents players from looking through leaf blocks to spot hidden ores in jungle environments.

---

## Proximity & Deobfuscation Settings

Proximity settings govern how and when hidden blocks are revealed to players as they move around the world.

### `proximity.enabled`
* **Type:** Boolean
* **Default:** `true`
* **Description:** Enables proximity-based deobfuscation. When a player moves near an obfuscated block, the plugin sends block change packets to reveal its true identity.

### `proximity.max-revealed-per-player`
* **Type:** Integer
* **Default:** `10000`
* **Description:** The maximum number of revealed block coordinates tracked per player. When a player mines or moves extensively, old block mappings are evicted from memory. This acts as a safeguard against memory leaks.

### `proximity.update-radius` and `update-radius-y`
* **Type:** Integer
* **Default:** `4`
* **Description:** The horizontal (`update-radius`) and vertical (`update-radius-y`) distance (in blocks) around the player within which hidden blocks are revealed.
* **Note:** Setting this too high can allow X-ray users to spot ores just out of reach. Setting it too low can cause blocks to "pop in" visibly while running or mining.

### `proximity.check-interval`
* **Type:** Integer (Ticks)
* **Default:** `5`
* **Description:** Frequency (in server ticks) at which the plugin evaluates player movement and updates block visibility. (20 ticks = 1 second).

### `proximity.movement-threshold`
* **Type:** Double
* **Default:** `0.5`
* **Description:** The minimum distance (in blocks) a player must travel since their last proximity check before another check is triggered. Prevents redundant calculations when a player is standing still.

### `proximity.max-deobfuscation-updates-per-tick`
* **Type:** Integer
* **Default:** `64`
* **Description:** The maximum number of block update packets sent to a single player per tick. If a player triggers more updates (e.g. via explosions), the excess updates are queued and spread across subsequent ticks to prevent network bandwidth choke and client-side FPS lag.

### `proximity.frustum-culling`
* **Type:** Boolean
* **Default:** `false`
* **Description:** If set to `true`, blocks are only deobfuscated if they are within the player's field of view (frustum). This significantly reduces the number of block updates sent to the client but increases server-side CPU load.

### `proximity.frustum-fov`
* **Type:** Double (Degrees)
* **Default:** `90.0`
* **Description:** The Field of View angle used for frustum culling calculations.

### `proximity.raycast-line-of-sight`
* **Type:** Boolean
* **Default:** `false`
* **Description:** If set to `true`, the plugin performs raycast checks to verify if there is a direct line of sight between the player and the block. This represents the ultimate level of security (players cannot see ores through solid stone walls even if they are within the update radius), but it carries a high CPU cost.

### `proximity.re-obfuscate`
* **Type:** Boolean
* **Default:** `true`
* **Description:** Re-obfuscates blocks when a player walks away from them. This prevents players from permanently mapping out the caves.

### `proximity.re-obfuscate-delay`
* **Type:** Integer (Ticks)
* **Default:** `200` (10 seconds)
* **Description:** The delay in server ticks before a block is re-obfuscated after a player leaves its proximity radius.

---

## Cache Settings

Anti-Xray utilizes a two-tier caching system to store obfuscated chunk data, ensuring minimal CPU usage.

> [!IMPORTANT]
> In `AntiXrayPlugin.java`, cache settings are configured using flat config keys. Ensure you write them exactly as follows in your `config.yml` if you need to override the defaults.

### `cache.l1-max-size`
* **Type:** Long
* **Default:** `1000` (Note: default in `config.yml` is `5000`)
* **Description:** The maximum number of chunk configurations to store in the L1 (in-memory) cache. Powered by Caffeine cache.

### `cache.l1-expiry-seconds`
* **Type:** Long
* **Default:** `300` (5 minutes)
* **Description:** The time in seconds before an inactive chunk in L1 cache is evicted.

### `cache.l2-enabled`
* **Type:** Boolean
* **Default:** `false` (Note: default in `config.yml` is `true`)
* **Description:** Enables L2 (disk) caching. Obfuscated chunk data is saved to disk, so it does not need to be re-obfuscated when chunks unload and reload.

### `cache.l2-disk-budget-mb`
* **Type:** Long
* **Default:** `500`
* **Description:** The maximum storage space (in MB) allocated for the L2 disk cache. When the limit is reached, the oldest cached chunks are deleted (LRU policy).

---

## Async Processing Settings

Async processing decouples chunk obfuscation from the main server thread, preventing TPS drops.

### `async.pre-obfuscation-enabled`
* **Type:** Boolean
* **Default:** `true`
* **Description:** If enabled, chunks are enqueued for obfuscation in the background when they are loaded asynchronously. (Paper/Folia only).

### `async.pool-size`
* **Type:** Integer
* **Default:** `0` (Auto-detects CPU cores: `max(2, availableProcessors - 2)`)
* **Description:** The number of worker threads dedicated to the background obfuscation task pool.

### `async.tick-budget-ms`
* **Type:** Long (Milliseconds)
* **Default:** `8` (Note: in `config.yml` written as `per-tick-budget-ms: 5`)
* **Description:** The maximum amount of time in milliseconds the main thread is allowed to spend processing queued sync-obfuscation updates per tick.

### `async.chunk-timeout-ms`
* **Type:** Long (Milliseconds)
* **Default:** `5000` (Note: in `config.yml` written as `50`)
* **Description:** The maximum time allowed for a single chunk obfuscation task to finish before it times out and falls back to normal processing.

### `async.max-queue-size`
* **Type:** Integer
* **Default:** `4096` (Note: in `config.yml` written as `10000`)
* **Description:** The maximum number of pending chunk obfuscation tasks allowed in the queue. If exceeded, backpressure is applied, reverting updates to synchronous processing to avoid out-of-memory errors.

---

## Statistical Detection Settings

The statistical detection engine monitors mining patterns to identify users bypassing client-side obfuscation (e.g. using hacked clients with seed-crackers or historical mining databases).

### `detection.enabled`
* **Type:** Boolean
* **Default:** `true`
* **Description:** Toggle the statistical mining detection module.

### `detection.minimum-sample-size`
* **Type:** Integer
* **Default:** `100`
* **Description:** The minimum number of blocks a player must mine before the detection engine evaluates their mining ratios.

### `detection.grace-period-minutes`
* **Type:** Integer (Minutes)
* **Default:** `30`
* **Description:** New players are granted this grace period during which `CRITICAL` alerts will not trigger automated actions (such as bans or kicks), allowing staff members to review them manually.

### `detection.thresholds`
* **Type:** Configuration Section
* **Description:** Defines thresholds for various mining metrics. Exceeding these triggers `WARNING` or `CRITICAL` alerts.
* **Metrics:**
  * `ore-to-stone-ratio`: The ratio of mined ore blocks to stone/filler blocks.
  * `diamond-per-hour`: The number of diamond ores mined per hour of active play.
  * `straight-to-ore-ratio`: The proportion of mined blocks that lead directly to an ore block (indicative of straight-line strip mining directly to diamonds).
  * `valuable-ore-ratio`: The ratio of rare/valuable ores (diamond, ancient debris, emerald) compared to common ores (coal, copper).

### `detection.actions`
* **Type:** List of Actions
* **Description:** Actions to execute when a player triggers an alert.
* **Prefixes:**
  * `log`: Log the incident to the console and database.
  * `notify`: Notify online staff members in-game.
  * `command:<cmd>`: Run a console command. Supports `{player}` placeholder.
* **Example:**
```yaml
actions:
  warning:
    - log
    - notify
  critical:
    - log
    - notify
    - 'command:tempban {player} 1h Suspected X-ray use'
```

### `detection.notifications`
* **Type:** Configuration Section
* **Options:**
  * `in-game`: Notify online staff players (requires permission `antixray.alerts`).
  * `console`: Log alerts to the server console.
  * `webhook.enabled`: Enable Discord/HTTP webhook alerts.
  * `webhook.url`: Webhook integration URL.
  * `webhook.format`: Output format, either `plain` or `discord-embed`.

### `detection.storage`
* **Type:** Configuration Section
* **Description:** Storage settings for player mining statistics.
* **Options:**
  * `type`: Either `sqlite` (local database file) or `mysql` (external database server).
  * `mysql.host`, `mysql.port`, `mysql.database`, `mysql.username`, `mysql.password`: Connection credentials for MySQL storage.

---

## Resource Pack Countermeasures

Enforces a custom server resource pack that disables wireframe/X-ray textures, reinforcing obfuscation security.

### `resource-pack.force-pack`
* **Type:** Boolean
* **Default:** `false`
* **Description:** Enables enforcement of the resource pack.

### `resource-pack.pack-url`
* **Type:** String
* **Description:** Direct download link to the resource pack zip file.

### `resource-pack.pack-hash`
* **Type:** String
* **Description:** The SHA-1 hash of the resource pack zip file. Highly recommended so the Minecraft client can verify and cache the download.

### `resource-pack.kick-on-decline`
* **Type:** Boolean
* **Default:** `true`
* **Description:** Kicks players who decline to download the resource pack.

### `resource-pack.kick-message`
* **Type:** String
* **Default:** `This server requires the official resource pack.`
* **Description:** Message displayed to kicked players. Color codes (`&`) are supported.

---

## Per-World Overrides

Configure specific parameters for individual worlds in the `worlds:` section of the config. Any option not specified will inherit from the global settings.

* **Example:**
```yaml
worlds:
  world_nether:
    engine-mode: 2
    hidden-blocks:
      - nether_gold_ore
      - nether_quartz_ore
      - ancient_debris
    max-block-height: 128
  world_the_end:
    enabled: false  # Disable Anti-Xray in the End dimension
```

---

## Recommended Presets

### 1. Minimal (Low CPU / High Performance)
*Best for budget hosts, mini-game servers, or massive player-count servers.*
* **Engine Mode:** `1` (Simple replacement)
* **Proximity Radius:** `3`
* **L2 Cache:** Disabled (`cache.l2-enabled: false`)
* **Raycast/Frustum:** Disabled
* **Pros:** Extremely low CPU footprint, minimal RAM usage.
* **Cons:** X-rayers can see ores exposed inside open caves.

### 2. Balanced (Recommended)
*Best for typical survival servers.*
* **Engine Mode:** `3` (Layer-based)
* **Proximity Radius:** `4`
* **L2 Cache:** Enabled
* **Raycast/Frustum:** Disabled
* **Pros:** 100% security against cave-vision, clean mining, optimal bandwidth usage.
* **Cons:** Slight CPU impact when many chunks are generated/sent at once.

### 3. Maximum (Aggressive Security)
*Best for competitive survival, UHC, or factions servers.*
* **Engine Mode:** `3` (Layer-based)
* **Proximity Radius:** `5`
* **L2 Cache:** Enabled
* **Raycast Line of Sight:** `true`
* **Frustum Culling:** `true`
* **Resource Pack Enforcement:** Enabled
* **Pros:** Impenetrable defense. Players cannot see ores hidden behind solid stone, even if they stand adjacent.
* **Cons:** High CPU overhead due to raycasting and frustum calculations on the main thread.

---

## Performance Tuning & Troubleshooting

### CPU Usage is Too High
1. **Reduce Engine Mode:** If running Mode 3, try dropping to Mode 2, or Mode 1.
2. **Decrease Proximity Radius:** Lower `proximity.update-radius` from `4` to `3`.
3. **Disable Raycasting & Frustum Culling:** Set `proximity.raycast-line-of-sight: false` and `proximity.frustum-culling: false`.
4. **Adjust Async Pool Size:** Ensure `async.pool-size` is not set too high. A value of `0` is usually optimal, allowing the plugin to self-configure.

### Memory Issues / Out of Memory
1. **Reduce L1 Cache Size:** Set `cache.l1-max-size` to `1000` or lower.
2. **Reduce Max Revealed Blocks:** Set `proximity.max-revealed-per-player` to `5000`.
3. **Check Queue size:** Ensure `async.max-queue-size` is limited (e.g. `4096`).

### Players reporting "Ghost Blocks" / Visual Pops
1. **Increase Proximity Radius:** If blocks "pop" in late when players mine quickly, increase `proximity.update-radius` to `5` or `6`.
2. **Increase Packet Budget:** Increase `proximity.max-deobfuscation-updates-per-tick` to `128` to speed up block updates.
3. **Check Network Latency:** Mode 2 and 3 require sending block change packets. Ensure the server's network connection is stable.
