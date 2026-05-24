# Platform & Version Compatibility Matrix

This document details the compatible Minecraft server platforms, game versions, dependency requirements, and known plugin conflicts.

---

## Server Software Compatibility

Anti-Xray is built on Spigot and Paper APIs, with specialized adapters using Netty and Mojang NMS.

| Platform | Compatibility | Recommended | Notes |
|---|---|---|---|
| **Paper** | **Full Support** | Yes (Highly Recommended) | Enables highly optimized background chunk pre-obfuscation and direct chunk packet manipulation. |
| **Spigot** | **Supported** | No | Requires **ProtocolLib** for packet interception, which may add a slight overhead compared to Paper. |
| **Folia** | **Experimental** | Yes | Region-aware. Uses thread-safe data structures and asynchronous task processors designed for Folia's regional scheduler. See known limitations below. |
| **Purpur / Pufferfish** | **Full Support** | Yes | Inherits all optimized Paper APIs and functions identically to Paper. |

---

## Minecraft & NMS Version Support

Anti-Xray utilizes internal NMS adapters to intercept and rewrite chunk data packets before they leave the server.

| Minecraft Version | Protocol Version | NMS Adapter | Status |
|---|---|---|---|
| **1.21.2 - 1.21.4** | 1.21.4 | `v1_21_R3` | **Stable (Fully Supported)** |
| **1.21 - 1.21.1** | 1.21 | `v1_21_R2` / `v1_21_R1` | **Stable (Fully Supported)** |
| **1.20.5 - 1.20.6** | 1.20.6 | `v1_20_R4` | **Stable (Fully Supported)** |
| **1.20.3 - 1.20.4** | 1.20.4 | `v1_20_R3` | **Stable (Fully Supported)** |
| **1.20.2** | 1.20.2 | `v1_20_R2` | **Stable (Fully Supported)** |
| **1.20 - 1.20.1** | 1.20.1 | `v1_20_R1` | **Stable (Fully Supported)** |
| **1.19.4** | 1.19.4 | `v1_19_R3` | **Stable (Fully Supported)** |

> [!TIP]
> If a newer minor Minecraft version is released and a specific NMS adapter class is missing, Anti-Xray will log a warning and automatically load the closest stable fallback adapter (e.g. `v1_21_R3` for 1.21.5+).

---

## Core System Requirements

* **Java Version:** Java 17 or higher (Java 21 is highly recommended for virtual thread optimization when using database statistics storage).
* **RAM:** Minimum 2GB allocated to the Minecraft server. 4GB+ recommended if using L1 in-memory caching with a high player count.

---

## Dependency Requirements

* **ProtocolLib (Optional / Conditional):**
  * **Spigot:** Required. The plugin cannot intercept packets on Spigot without ProtocolLib.
  * **Paper / Folia:** Optional. The plugin automatically detects and utilizes native NMS channel pipeline hooks, but will fall back to ProtocolLib if it is installed and preferred.
* **Vault (Optional):** Used for integration with economy systems if players are rewarded or fined by detection action console commands.
* **PlaceholderAPI (Optional):** Used to resolve placeholders within alert notification messages or commands.

---

## Known Incompatibilities & Conflicts

### 1. Paper's Built-in Anti-Xray
* **Description:** Paper contains a built-in Anti-Xray mechanism.
* **Compatibility:** **Incompatible**
* **Solution:** You must disable Paper's native Anti-Xray in your server configuration files (`paper-world-defaults.yml` or specific world settings) by setting `settings.anti-xray.enabled: false`. Running both simultaneously will lead to double packet processing, severe network lag, and visual block glitches on the client.

### 2. Other Obfuscation Plugins (e.g., Orebfuscator)
* **Description:** Multiple plugins rewriting the same chunk transmission pipelines.
* **Compatibility:** **Incompatible**
* **Solution:** Uninstall or disable Orebfuscator. Running multiple block obfuscation plugins causes conflicts in the Netty channel handlers, leading to client kicks or server crashes.

### 3. Server Seed Structure Leaks
* **Description:** Client-side X-ray mods that use seed cracking to locate ores based on generation noise.
* **Solution:** Changing the structure and feature seeds in your Spigot configuration (`spigot.yml`) is vital:
  ```yaml
  world-settings:
    default:
      seed-feature: 14357617 # Change to a custom random number
      seed-structure: 14357617 # Change to a custom random number
  ```
  If these are left at default values, clients can calculate your world seed and guess ore locations regardless of obfuscation.

### 4. Folia Platform Limitations
* **Description:** Folia is a regionized multithreaded server fork. Because of this, certain standard Bukkit practices are unsupported or restricted.
* **Compatibility:** **Supported (Experimental)**
* **Known Limitations & Constraints:**
  * **Thread Contexts:** Methods that check `Bukkit.isPrimaryThread()` will always return `false` on Folia. Thread-safe scheduling must be done through the regional/entity task schedulers rather than the global scheduler when operating on location-specific or entity-specific data.
  * **Async Timers:** Async timers scheduled via the async scheduler use wall-clock time (ticks converted at 50ms/tick) instead of server tick timing, meaning they do not slow down or speed up with the TPS of the server.
  * **Entity Scheduling:** Tasks scheduled for specific entities will automatically be cancelled or may fail to execute if the entity is unloaded or removed from the world before the task runs.
  * **Location-bound Tasks:** Scheduling tasks at specific locations uses chunk coordinates under the hood. If the region containing that location is unloaded, the task execution will be postponed or dropped.
  * **Cancellation:** Folia task handles use JVM reflection for cancellation; cancellation requests may take up to a tick to fully reflect.
