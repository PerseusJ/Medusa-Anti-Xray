# Developer API Guide

Anti-Xray provides a developer API allowing third-party plugins to query obfuscation state, register custom hidden blocks, control the obfuscation engine dynamically, and listen to custom security events.

---

## Table of Contents
1. [Integrating with Anti-Xray](#integrating-with-anti-xray)
2. [Accessing the API](#accessing-the-api)
3. [Core API Methods](#core-api-methods)
4. [Custom Obfuscation Providers](#custom-obfuscation-providers)
5. [Events](#events)
6. [API Stability & Versioning Policy](#api-stability--versioning-policy)

---

## Integrating with Anti-Xray

### Gradle
To compile against the Anti-Xray API, add the following to your `build.gradle.kts` (or equivalent in Maven/Gradle Groovy):

```kotlin
repositories {
    // Repository where Anti-Xray is hosted
    maven("https://repo.example.com/releases") 
}

dependencies {
    compileOnly("com.antixray:anti-xray-api:0.1.0-SNAPSHOT")
}
```

Make sure to specify Anti-Xray as a dependency in your `plugin.yml`:

```yaml
name: MyPlugin
version: 1.0.0
main: com.example.myplugin.MyPlugin
depend: [Anti-Xray]  # Or use softdepend if you make it optional
```

---

## Accessing the API

The entry point to all Anti-Xray API functionality is the [AntiXrayAPI](file:///C:/Projects/Anti-Xray/src/main/java/com/antixray/api/AntiXrayAPI.java) interface. You can fetch the active instance using the static accessor:

```java
import com.antixray.AntiXrayPlugin;
import com.antixray.api.AntiXrayAPI;

public class MyPluginLoader {
    public void checkApi() {
        if (Bukkit.getPluginManager().isPluginEnabled("Anti-Xray")) {
            AntiXrayAPI api = AntiXrayPlugin.getInstance().getAPI();
            // Use API here
        }
    }
}
```

---

## Core API Methods

The `AntiXrayAPI` interface provides the following methods:

### `isObfuscated`
Checks if a block at a given location is currently hidden/obfuscated for players.
* **Signature:** `boolean isObfuscated(Location location)`
* **Parameters:** `location` - The Bukkit location to inspect.
* **Returns:** `true` if the block is obfuscated, `false` otherwise.

### `getEngineMode`
Gets the active engine obfuscation mode (1, 2, or 3) for a specific world.
* **Signature:** `int getEngineMode(World world)`
* **Parameters:** `world` - The target Bukkit world.
* **Returns:** The engine mode (`1`, `2`, or `3`), or `-1` if Anti-Xray is disabled for the world.

### `isEnabled`
Checks whether Anti-Xray is active for a specific world.
* **Signature:** `boolean isEnabled(World world)`
* **Parameters:** `world` - The world to check.
* **Returns:** `true` if enabled and active, `false` otherwise.

### `registerCustomHiddenBlock`
Add a block type to the runtime hidden block list dynamically. Useful if another plugin registers custom ores/materials that need to be obfuscated.
* **Signature:** `void registerCustomHiddenBlock(Material material)`
* **Parameters:** `material` - The material type to hide.

### `unregisterCustomHiddenBlock`
Remove a block type from the runtime hidden block list.
* **Signature:** `void unregisterCustomHiddenBlock(Material material)`
* **Parameters:** `material` - The material type to stop hiding.

### `getObfuscationProvider`
Retrieve the custom logic provider for a world.
* **Signature:** `ObfuscationProvider getObfuscationProvider(World world)`
* **Returns:** The active `ObfuscationProvider` for the world, or `null` if none is set (meaning default rules apply).

### `setObfuscationProvider`
Register a custom obfuscation provider to override default config checks (e.g. for custom terrain generation, custom mines, or minigames).
* **Signature:** `void setObfuscationProvider(World world, ObfuscationProvider provider)`
* **Parameters:**
  * `world` - The Bukkit world to assign the provider to.
  * `provider` - The custom `ObfuscationProvider` implementation. Pass `null` to restore default behavior.

---

## Custom Obfuscation Providers

By implementing the `ObfuscationProvider` interface, you can override how and when blocks are obfuscated.

### Interface Definition
```java
package com.antixray.api;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;

public interface ObfuscationProvider {
    /**
     * Determine if a block should be obfuscated.
     */
    boolean shouldObfuscate(BlockState blockState, World world, int x, int y, int z);

    /**
     * Get the replacement block material for the given Y level.
     */
    Material getReplacementBlock(World world, int y);
}
```

### Implementing and Using a Provider
When implementing `shouldObfuscate()`, be aware that the method may be called asynchronously. **Never call thread-unsafe Bukkit APIs from this method.** Use the provided `BlockState` parameters (which may be a thread-safe proxy) to safely query block properties.

If you need to construct a thread-safe `BlockState` representation from custom data, use `BlockStateProxy`:

```java
import com.antixray.api.BlockStateProxy;
import org.bukkit.block.BlockState;

BlockState proxy = BlockStateProxy.create(world, x, y, z, Material.DIAMOND_ORE, blockData);
```

#### Example Implementation
```java
import com.antixray.api.ObfuscationProvider;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;

public class CustomLobbyObfuscation implements ObfuscationProvider {
    @Override
    public boolean shouldObfuscate(BlockState blockState, World world, int x, int y, int z) {
        // Protect diamond ores specifically in spawn zones
        if (blockState.getType() == Material.DIAMOND_ORE) {
            return isWithinSpawnZone(x, z);
        }
        return false;
    }

    @Override
    public Material getReplacementBlock(World world, int y) {
        return y < 0 ? Material.DEEPSLATE : Material.STONE;
    }

    private boolean isWithinSpawnZone(int x, int z) {
        return Math.abs(x) < 100 && Math.abs(z) < 100;
    }
}
```

---

## Events

Anti-Xray dispatches custom Bukkit events that can be handled using standard listener registrations.

### 1. `BlockVisibilityEvent`
Fired whenever an obfuscated block is deobfuscated (revealed) for a player.
* **Cancellable:** Yes. If cancelled, the player will continue to see the replacement block (filler) rather than the real block.
* **Important Methods:**
  * `Player getPlayer()` - The player triggering the deobfuscation.
  * `Location getLocation()` - Location of the block.
  * `Material getRealMaterial()` - The real material (e.g. `DIAMOND_ORE`).
  * `Material getObfuscatedMaterial()` - The replacement block material they were seeing (e.g. `STONE`).

#### Example Listener
```java
import com.antixray.api.BlockVisibilityEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class AuditListener implements Listener {
    @EventHandler
    public void onBlockReveal(BlockVisibilityEvent event) {
        if (event.getRealMaterial() == Material.DIAMOND_ORE) {
            event.getPlayer().sendMessage("You have uncovered a Diamond!");
        }
    }
}
```

### 2. `PlayerXraySuspicionEvent`
Fired when the statistical detection module flags a player for suspected X-ray usage.
* **Cancellable:** Yes. If cancelled, automated console action commands (such as bans) will not be run, although the alert will still be logged.
* **Important Methods:**
  * `Player getPlayer()` - The suspected player.
  * `AlertLevel getAlertLevel()` - `INFO`, `WARNING`, or `CRITICAL`.
  * `Map<String, Double> getTriggeringMetrics()` - Map containing metrics (e.g., `oreToStoneRatio`, `straightToOreRatio`) and their recorded values at the time of the alert.

#### Example Listener
```java
import com.antixray.api.PlayerXraySuspicionEvent;
import com.antixray.api.AlertLevel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class AlertNotificationHandler implements Listener {
    @EventHandler
    public void onSuspicion(PlayerXraySuspicionEvent event) {
        if (event.getAlertLevel() == AlertLevel.CRITICAL) {
            // Send alert to custom staff Slack channel
            SlackWebhook.send(event.getPlayer().getName() + " is flagged for CRITICAL X-ray suspicion!");
        }
    }
}
```

---

## API Stability & Versioning Policy

We strictly adhere to Semantic Versioning principles:
* **Package stability:** The `com.antixray.api` package is the only public API contract. All classes outside of this package are internal implementations and may change without notice.
* **Events stability:** Fields and methods will not be removed from API events within a major plugin version. New fields may be added in minor or patch releases.
* **Deprecation Lifecycle:** Deprecated elements in the API will be annotated with `@Deprecated` and explained via Javadoc. They will remain fully functional for at least one minor release before removal in a subsequent major release.
