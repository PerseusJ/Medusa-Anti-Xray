# Anti-Xray Plugin — Implementation Plan

> **Derived from:** BLUEPRINT.md v1.0.0-draft
> **Purpose:** Actionable, step-by-step implementation plan organized by phase
> **Constraint:** No code is written as part of this plan. This document is purely a planning artifact.
---

## Table of Contents

1. [Pre-Phase 0: Project Scaffolding](#pre-phase-0-project-scaffolding)
2. [Phase 1: Core Engine](#phase-1-core-engine)
3. [Phase 2: Deobfuscation](#phase-2-deobfuscation)
4. [Phase 3: Cache & Async](#phase-3-cache--async)
5. [Phase 4: Statistical Detection](#phase-4-statistical-detection)
6. [Phase 5: Resource-Pack Countermeasures](#phase-5-resource-pack-countermeasures)
7. [Phase 6: API & Commands](#phase-6-api--commands)
8. [Phase 7: Polish & Release](#phase-7-polish--release)
9. [Cross-Phase Concerns](#cross-phase-concerns)
10. [Decision Tracker](#decision-tracker)
11. [Risk Register](#risk-register)

---

## Pre-Phase 0: Project Scaffolding

Before any phase begins, the project must be bootstrapped with its build system, repository structure, and baseline configuration files.

### Step 0.1: Initialize Gradle Project

| Task | Detail |
|---|---|
| Create `build.gradle` | Gradle Kotlin DSL. Java 17 toolchain. Shadow plugin for fat JAR. Dependency relocation under `com.antixray.lib`. |
| Create `settings.gradle` | Project name `antixray`. Declare plugin repositories (SpigotMC, Paper, Maven Central, JitPack). |
| Create `gradle.properties` | Plugin version `0.1.0-SNAPSHOT`. Dependency version constants (Spigot API, ProtocolLib, Caffeine, JUnit, Mockito). |
| Create `.editorconfig` | Consistent formatting: 4-space indent, UTF-8, LF line endings, 120 char wrap. |
| Create `.gitignore` | Standard Gradle + IntelliJ + Eclipse + OS ignores. `build/`, `.gradle/`, `*.iml`, `.idea/`, etc. |

### Step 0.2: Create Project Directory Structure

Create the full directory tree as specified in BLUEPRINT Section 17:

```
src/main/java/com/antixray/          — All source packages (initially empty)
src/main/resources/                   — plugin.yml, config.yml, languages/
src/test/java/com/antixray/           — All test packages (initially empty)
docs/                                 — Documentation (BLUEPRINT.md already exists)
.github/workflows/                    — CI pipeline (build.yml)
```

Create all Java package directories:
- `config/`, `engine/`, `deobfuscation/`, `cache/`, `async/`, `detection/`, `packet/`, `nms/`, `nms/v1_19/`, `nms/v1_20/`, `nms/v1_21/`, `api/`, `commands/`, `permissions/`, `listener/`, `util/`

### Step 0.3: Create Resource Files

| File | Content |
|---|---|
| `plugin.yml` | Plugin name `AntiXray`, main class `com.antixray.AntiXrayPlugin`, version `${version}`, API version `1.19`, description, dependencies (ProtocolLib: soft), commands (`/antixray`), permissions (all nodes from BLUEPRINT Section 11). |
| `config.yml` | Full default configuration as specified in BLUEPRINT Section 10.2, with inline comments explaining each option. |
| `languages/en.yml` | English message templates for all command responses, alerts, and log messages. |

### Step 0.4: Create CI Pipeline

| File | Content |
|---|---|
| `.github/workflows/build.yml` | GitHub Actions workflow: checkout → setup Java 17 Temurin → `./gradlew shadowJar` → `./gradlew test` → upload artifact. Release job on tag push. |

### Step 0.5: Create Main Plugin Class Stub

| File | Content |
|---|---|
| `AntiXrayPlugin.java` | Extends `JavaPlugin`. Empty `onEnable()` and `onDisable()` with log messages. Singleton `getInstance()`. Placeholder `getAPI()` returning null. |

### Pre-Phase 0 Deliverableskilo

- [ ] Gradle project compiles: `./gradlew build` succeeds
- [ ] Fat JAR is produced: `build/libs/AntiXray-0.1.0-SNAPSHOT.jar`
- [ ] `plugin.yml` is valid and included in JAR
- [ ] JAR loads on a Paper 1.21.4 test server (logs "AntiXray enabled")
- [ ] GitHub Actions CI runs green on push

---

## Phase 1: Core Engine

**Goal:** Functional chunk obfuscation that transforms chunk packets before they are sent to clients. Three engine modes operational. Configuration loads and validates. NMS adapter selects correctly.

**Dependencies:** Pre-Phase 0 complete.
**Estimated Effort:** Large
**Key Risks:** NMS version differences may require more adapters than anticipated. Palette manipulation edge cases (single-value palette, direct palette, bit-width transitions).

### Step 1.1: NMS Abstraction Layer

Build the version-independent interface and the factory that selects the correct adapter at runtime.

#### 1.1.1: Define `NmsAdapter` Interface

Create `nms/NmsAdapter.java` with all methods from BLUEPRINT Section 14.5:

| Method | Return Type | Purpose |
|---|---|---|
| `getChunkSections(Object packet)` | `List<Object>` | Extract chunk section data from a chunk packet (NMS `LevelChunkSection` objects) |
| `getPaletteEntries(Object chunkSection)` | `List<Integer>` | Extract block state IDs from a section's palette |
| `getPackedIndices(Object chunkSection)` | `long[]` | Extract the packed block index array from a section |
| `setPaletteEntries(Object chunkSection, List<Integer> entries)` | `void` | Replace palette entries in a section |
| `setPackedIndices(Object chunkSection, long[] indices, int bitsPerEntry)` | `void` | Replace packed indices and update bit width in a section |
| `getBlockStateAt(World world, int x, int y, int z)` | `int` | Get the global block state ID at a position (for air-exposure checks) |
| `getSectionNonEmptyCount(Object chunkSection)` | `int` | Get the block count (non-air) for a section |
| `createBlockUpdatePacket(Location loc, int blockStateId)` | `Object` | Construct a Block Update packet (0x09) |
| `createMultiBlockUpdatePacket(World world, int chunkX, int chunkZ, Map<Location, Integer> changes)` | `Object` | Construct an Update Section Blocks packet (0x3C) |
| `getPaletteBitsPerEntry(Object chunkSection)` | `int` | Get current bits-per-entry for a section's packed array |
| `isSingleValuePalette(Object chunkSection)` | `boolean` | Check if a section uses a single-value palette |
| `getSingleValue(Object chunkSection)` | `int` | Get the single block state ID for a single-value palette section |
| `upgradeToIndirectPalette(Object chunkSection, int singleValue, int replacementValue)` | `void` | Upgrade a single-value palette to indirect palette (needed for Mode 2/3 fake injection) |

#### 1.1.2: Define `NmsAdapterFactory`

Create `nms/NmsAdapterFactory.java`:
- Read `CraftServer.class.getPackage().getName()` to detect NMS version string (e.g., `v1_21_R3`)
- Attempt to load `com.antixray.nms.v1_21.NmsAdapter_v1_21_R3` via `Class.forName()`
- If not found, attempt to load the latest available adapter as a forward-compatibility fallback
- If no adapter loads, log error and disable the plugin

#### 1.1.3: Implement NMS Adapters

Implement one adapter per major version range. Each adapter translates the `NmsAdapter` interface calls to the corresponding NMS method calls.

| Adapter | Version Range | Key NMS Classes |
|---|---|---|
| `NmsAdapter_v1_21_R3` | 1.21.4+ | `PalettedContainer`, `SingleValuePalette`, `LinearPalette`, `HashMapPalette`, `LevelChunkSection`, `ClientboundLevelChunkPacketData` |
| `NmsAdapter_v1_21_R2` | 1.21.2–1.21.3 | Same as above with minor method signature differences |
| `NmsAdapter_v1_21_R1` | 1.21.0–1.21.1 | Same as above with minor method signature differences |
| `NmsAdapter_v1_20_R3` | 1.20.5–1.20.6 | Potential item component changes affecting chunk data |
| `NmsAdapter_v1_20_R2` | 1.20.3–1.20.4 | Minor packet format differences |
| `NmsAdapter_v1_20_R1` | 1.20.0–1.20.2 | Baseline 1.20.x adapter |
| `NmsAdapter_v1_19_R3` | 1.19.4 | Baseline 1.19.x adapter |

**Implementation priority:** Implement `v1_21_R3` first (most current), then `v1_20_R2` and `v1_19_R3`. Remaining adapters can be implemented by diffing against the closest existing adapter.

**Compile strategy:** Each adapter is compiled against the corresponding Paper weight JAR (compile-only dependency). All adapters are bundled into a single fat JAR via Shadow.

### Step 1.2: Utility Classes

These are pure-Java utilities with no Minecraft dependencies. Build them first since the engine depends on them.

#### 1.2.1: `util/BlockPosition.java`

Immutable value class holding `(worldName, x, y, z)`. Implements `equals()`, `hashCode()`, `toString()`. Used as key in per-player revealed-sets and cache invalidation maps.

#### 1.2.2: `util/MaterialSet.java`

Pre-computed sets of block categories, populated at startup:
- `TRANSPARENT_BLOCKS` — blocks considered transparent for air-exposure checks (list from BLUEPRINT Section 3.2)
- `HIDDEN_BLOCK_TYPES` — from configuration
- `TILE_ENTITY_BLOCKS` — spawner, chest, ender_chest, trapped_chest (hardcoded + configurable)
- `OVERWORLD_REPLACEMENT` / `NETHER_REPLACEMENT` / `END_REPLACEMENT` — from configuration

All sets store NMS global block state IDs (ints), not Material enums, for fast lookup during obfuscation.

#### 1.2.3: `util/SeededRandom.java`

Deterministic RNG seeded from `(chunkX, chunkZ, serverSalt)`. Used for fake ore placement in Modes 2/3 to ensure identical output for the same chunk across obfuscation calls (required for cache consistency).

Algorithm: XOR-shift or simple LCG seeded from `chunkX * 341873128712L + chunkZ * 132897987541L + serverSalt`. Must produce the same sequence for the same inputs across JVM instances.

#### 1.2.4: `util/VersionUtil.java`

Server version detection:
- `getNmsVersion()` — returns version string (e.g., `v1_21_R3`)
- `isPaper()` — checks for Paper-specific classes
- `isFolia()` — checks for Folia-specific classes
- `getMinecraftVersion()` — returns `(major, minor, patch)` tuple

### Step 1.3: Block Classification

#### 1.3.1: `engine/AirExposureChecker.java`

Determines whether a block is air-exposed by checking its 6 face-adjacent neighbors.

**Algorithm:**
1. For a given block position `(x, y, z)`, check 6 neighbors: `(x±1, y, z)`, `(x, y±1, z)`, `(x, y, z±1)`
2. For each neighbor, get its block state ID via `NmsAdapter.getBlockStateAt()`
3. If any neighbor's block state ID is in `MaterialSet.TRANSPARENT_BLOCKS`, return `true` (air-exposed)
4. Special handling: if `lava-obscures` config is true, lava block state IDs are NOT considered transparent
5. Special handling: if the neighbor is in an unloaded chunk (chunk border), treat as opaque (conservative — keep block hidden)

**Performance:** 6 set lookups per block. Pre-computed `TRANSPARENT_BLOCKS` set as `IntSet` (fast primitive int lookup). Expected cost: ~0.05ms per section (BLUEPRINT Section 13.1).

**Caching consideration:** Air-exposure results are NOT cached independently — they are computed as part of obfuscation and the result (the entire obfuscated chunk) is cached. If the chunk changes, the cache entry is invalidated and re-obfuscation recomputes air-exposure.

#### 1.3.2: `engine/BlockClassifier.java`

Classifies each block in a chunk section into one of four categories:

| Classification | Condition | Obfuscation Action |
|---|---|---|
| `HIDDEN` | Block is in `hidden-blocks` AND NOT air-exposed AND NOT a tile entity | Replace with replacement block (Mode 1) or fake ore (Modes 2/3) |
| `HIDDEN_TILE_ENTITY` | Block is in `hidden-blocks` AND is a tile entity | Always replace with replacement block, regardless of air exposure |
| `AIR_EXPOSED` | Block is in `hidden-blocks` AND is air-exposed AND is NOT a tile entity | Leave as-is (visible to players legitimately) |
| `NORMAL` | Block is not in `hidden-blocks` | Never modify |

**Input:** Palette entries + packed indices for a chunk section + world context
**Output:** For each of the 4096 block positions, a classification enum value

### Step 1.4: Palette Manipulation

#### 1.4.1: `engine/PaletteManipulator.java`

Core utility for modifying chunk section palettes and packed indices. All other engine modes delegate palette work to this class.

**Operations:**

| Method | Description |
|---|---|
| `ensureInPalette(List<Integer> palette, int blockStateId)` | If `blockStateId` is not in the palette, append it. Returns its palette index. |
| `remapIndex(long[] packed, int oldIndex, int newIndex, int bitsPerEntry)` | For every entry in the packed array that equals `oldIndex`, replace with `newIndex`. |
| `remapIndices(long[] packed, Map<Integer, Integer> indexRemapping, int bitsPerEntry)` | Batch remap: apply a map of old→new index for all entries. |
| `setIndex(long[] packed, int position, int index, int bitsPerEntry)` | Set a single index at a specific block position in the packed array. |
| `getIndex(long[] packed, int position, int bitsPerEntry)` | Read a single index at a specific block position. |
| `reencode(long[] packed, int oldBitsPerEntry, int newBitsPerEntry)` | Re-encode the packed array when bits-per-entry changes due to palette expansion. |
| `compact(List<Integer> palette, long[] packed, int bitsPerEntry)` | Remove unreferenced palette entries, re-index, and re-encode. Returns updated palette + packed + bits. |
| `upgradeSingleValue(int singleValue, int replacementValue)` | Create a new indirect palette from a single-value palette section. Returns new palette + packed array. |

**Bit-packing details:** Minecraft encodes packed indices as a `long[]` array where each entry is `bitsPerEntry` bits wide, packed contiguously. Entries may span long boundaries. The manipulator must correctly handle:
- 4-bit entries (minimum indirect palette)
- Variable bit widths up to 15 (direct palette)
- Entries that cross `long` boundaries

**Unit tests:** This class requires the most thorough testing. Test cases:
- Read/write individual indices at all positions (0–4095)
- Remap single index
- Remap multiple indices
- Re-encode from 4-bit to 5-bit, 5-bit to 6-bit
- Compact removes unreferenced entries
- Single-value palette upgrade
- Edge: all entries same index, all entries different indices

### Step 1.5: Obfuscation Engine Modes

#### 1.5.1: `engine/ObfuscationMode.java`

Enum: `MODE_1`, `MODE_2`, `MODE_3`. Each mode has a corresponding engine class.

#### 1.5.2: `engine/Mode1Engine.java` — Simple Replacement

**Algorithm (per chunk section):**
1. Extract palette entries and packed indices from the section via `NmsAdapter`
2. Use `BlockClassifier` to classify each block position
3. For positions classified as `HIDDEN` or `HIDDEN_TILE_ENTITY`:
   - Determine replacement block state ID based on dimension and Y-level
   - Ensure replacement block state is in the palette (via `PaletteManipulator.ensureInPalette`)
   - Remap the position's packed index to the replacement block's palette index
4. For positions classified as `AIR_EXPOSED` or `NORMAL`: no change
5. Apply palette compaction (remove unreferenced entries)
6. Re-encode packed array if bits-per-entry changed
7. Write modified palette and packed indices back to the section via `NmsAdapter`

**Dimension-aware replacement logic:**
- Overworld + Y >= `deepslate-below-y` (default 0): replacement = `stone` block state ID
- Overworld + Y < `deepslate-below-y`: replacement = `deepslate` block state ID
- Nether: replacement = `netherrack` block state ID
- End: replacement = `end_stone` block state ID

#### 1.5.3: `engine/Mode2Engine.java` — Random Fake Ore Injection

**Algorithm (per chunk section):**
1. Perform Mode 1 replacement first (all hidden blocks → replacement block)
2. Initialize `SeededRandom` with `(chunkX, chunkZ, sectionY, serverSalt)`
3. For each block position in the section:
   - If the block's current state is the replacement block (e.g., stone) AND it is NOT air-exposed:
     - Draw a random double from `SeededRandom`
     - If `random < fake-ore-chance` (default 0.07):
       - Select a random ore type from `hidden-blocks` (uniform or weighted)
       - Ensure the selected ore's block state ID is in the palette
       - Set the position's packed index to the ore's palette index
4. Apply palette compaction
5. Re-encode if bits-per-entry changed
6. Write back

**Determinism:** The same `(chunkX, chunkZ, sectionY, serverSalt, configHash)` always produces identical output. This is critical for cache validity.

#### 1.5.4: `engine/Mode3Engine.java` — Layer-Based Obfuscation

**Algorithm (per chunk section):**
1. Initialize `SeededRandom` with `(chunkX, chunkZ, sectionY, serverSalt)`
2. For each of the 16 Y-layers (layer 0–15 within the section):
   - Select one random ore type from `hidden-blocks` using `SeededRandom` — this is the "layer fake block"
3. For each block position:
   - If classified as `HIDDEN` or `HIDDEN_TILE_ENTITY`: replace with the layer's fake block (NOT the replacement block)
   - If classified as `NORMAL` AND the block is the replacement block type AND NOT air-exposed:
     - With `fake-ore-chance` probability, replace with the layer's fake block
4. Apply palette compaction
5. Re-encode if bits-per-entry changed
6. Write back

**Advantage over Mode 2:** Only 16 RNG calls per section (one per Y-layer) instead of up to 4096. Palette grows by at most 1 entry per layer, and layers often share the same fake type.

#### 1.5.5: `engine/ObfuscationEngine.java`

Facade that:
- Receives a chunk packet + world context
- Looks up the world's engine mode from configuration
- Dispatches to the appropriate mode engine
- Returns the modified chunk packet

**Block-height check:** If the chunk section's base Y > `max-block-height`, skip obfuscation for that section entirely (pass through unmodified).

### Step 1.6: Packet Interception Layer

#### 1.6.1: `packet/PacketInterceptor.java`

Interface with methods:
- `register()` — start intercepting packets
- `unregister()` — stop intercepting packets
- `isAvailable()` — check if the interception method is functional

#### 1.6.2: `packet/NmsInterceptor.java` — Paper NMS Mode

**Mechanism:** Override Paper's `ChunkPacketBlockController` abstract class to inject the obfuscation engine into the chunk packet send path.

**Steps:**
1. At `onEnable`, if `VersionUtil.isPaper()` returns true:
   - Use reflection to replace the server's `ChunkPacketBlockController` instance with a custom implementation that calls `ObfuscationEngine.obfuscate()` for each chunk packet
   - The custom controller's `modifyBlocksPacket()` method:
     - Extracts chunk sections from the outgoing packet
     - Calls `ObfuscationEngine.obfuscate()` on each section
     - Returns the modified packet
2. At `onDisable`, restore the original controller (or let it be garbage collected with the plugin)

**Error handling:** If the NMS hook fails to load (class not found, method signature changed), log a warning and fall back to ProtocolLib mode.

#### 1.6.3: `packet/ProtocolLibInterceptor.java` — ProtocolLib Fallback

**Mechanism:** Register a `PacketAdapter` with ProtocolLib to intercept `PacketType.Play.Server.MAP_CHUNK`.

**Steps:**
1. At `onEnable`, if ProtocolLib is present and NMS mode is unavailable:
   - Register `PacketAdapter` with `ListenerPriority.NORMAL`
   - In `onPacketSending()`:
     - Read the chunk data byte array from the packet
     - Deserialize chunk sections (palette + packed indices)
     - Call `ObfuscationEngine.obfuscate()` on each section
     - Reserialize and write back to the packet
2. At `onDisable`, unregister the adapter

**Performance note:** ProtocolLib mode requires deserialization and reserialization of chunk packet data, which is slower than NMS in-place modification. The performance gap should be measured during Phase 1 testing.

#### 1.6.4: Interception Selection Logic

At `onEnable()`:
1. Attempt NMS mode: if Paper + chunk-send hook available → use NMS
2. Else attempt ProtocolLib mode: if ProtocolLib plugin present → use ProtocolLib
3. Else: log error "No packet interception method available. Install Paper or ProtocolLib." and disable gracefully.

**Mode is locked at startup.** Runtime switching requires server restart.

### Step 1.7: Configuration System

#### 1.7.1: `config/WorldConfig.java`

Immutable data class holding all configuration values for a single world (after merging global + per-world overrides). Fields:

| Field | Type | Default |
|---|---|---|
| `enabled` | `boolean` | `true` |
| `engineMode` | `ObfuscationMode` | `MODE_3` |
| `hiddenBlocks` | `Set<Integer>` | (all ores + chests + spawners as block state IDs) |
| `replacementOverworld` | `int` | stone block state ID |
| `replacementOverworldDeep` | `int` | deepslate block state ID |
| `deepslateBelowY` | `int` | `0` |
| `replacementNether` | `int` | netherrack block state ID |
| `replacementEnd` | `int` | end_stone block state ID |
| `maxBlockHeight` | `int` | `64` |
| `fakeOreChance` | `double` | `0.07` |
| `lavaObscures` | `boolean` | `true` |
| `leavesAreTransparent` | `boolean` | `true` |
| `bypassPermission` | `String` | `"antixray.bypass"` |

#### 1.7.2: `config/ConfigurationManager.java`

- Loads `config.yml` from plugin data folder
- Builds a `WorldConfig` for each loaded world by merging global defaults with per-world overrides (deep merge)
- Stores `Map<String, WorldConfig>` — world name to resolved config
- Provides `WorldConfig getWorldConfig(World world)` — returns config for the world, or global default if no per-world override
- On `/antixray reload`: reload from disk, revalidate, rebuild all `WorldConfig` instances, compute new `configHash`, clear caches if mode or hidden-blocks changed

#### 1.7.3: `config/ConfigValidator.java`

Validates all config values on load (BLUEPRINT Section 10.4). For each invalid value:
- Log `WARN` with field name, invalid value, and fallback
- Replace with safe default
- Return count of validation issues

### Step 1.8: Event Listeners (Minimal — Phase 1)

#### 1.8.1: `listener/PlayerEventListener.java`

- `PlayerJoinEvent`: Initialize per-player data structures (for later phases). Log plugin status.
- `PlayerQuitEvent`: Clean up per-player data.
- `PlayerChangedWorldEvent`: Reset per-player state for new world.

#### 1.8.2: `listener/WorldEventListener.java`

- `WorldLoadEvent`: Load per-world config, initialize world-specific state.
- `WorldUnloadEvent`: Clean up world-specific state.

### Step 1.9: Basic Commands

#### 1.9.1: `commands/AntiXrayCommand.java`

Root command handler with tab completion. Dispatches to subcommand handlers.

#### 1.9.2: `commands/ReloadCommand.java`

`/antixray reload`:
1. Call `ConfigurationManager.reload()`
2. If engine mode or hidden-blocks changed: clear all caches
3. Send success/failure message to sender

#### 1.9.3: `permissions/PermissionConstants.java`

All permission node constants as static final strings:
- `BYPASS = "antixray.bypass"`
- `ADMIN = "antixray.admin"`
- `NOTIFY = "antixray.notify"`
- `RELOAD = "antixray.reload"`
- `STATS = "antixray.stats"`
- `CHECK = "antixray.check"`
- `MODE = "antixray.mode"`
- `CACHE = "antixray.cache"`
- `TOGGLE = "antixray.toggle"`

### Step 1.10: Unit Tests

| Test Class | Test Cases |
|---|---|
| `PaletteManipulatorTest` | Read/write indices; remap single/batch; re-encode 4→5→6 bit; compact removes unreferenced; single-value upgrade; edge: 4096 same index; edge: every index different |
| `BlockClassifierTest` | All four classifications; tile entity always hidden; air-exposed with transparent neighbor; non-air-exposed with solid neighbors; border chunk (unloaded neighbor → opaque) |
| `AirExposureCheckerTest` | 6-face check; corner-adjacent blocks do NOT count; lava-obscures option; leaves-are-transparent option; all-solid neighbors → not exposed |
| `Mode1EngineTest` | Stone section with hidden diamond_ore → replaced with stone; air-exposed diamond_ore → left as-is; tile entity chest → always replaced; dimension-aware replacement (overworld/nether/end); deepslate Y threshold |
| `Mode2EngineTest` | Fake ores injected at expected probability; deterministic output for same seed; air-exposed ores not replaced; palette expansion; tile entities not used as fake blocks |
| `Mode3EngineTest` | All blocks in same Y-layer use same fake type; different layers may use different types; deterministic; palette growth bounded by 16 |
| `ConfigValidatorTest` | Invalid engine mode → fallback to 3; empty hidden-blocks → warning; invalid material name → skip; out-of-range values → defaults |
| `ConfigurationManagerTest` | Global config loads; per-world override merges; deep merge (nested keys); missing optional fields → defaults; configHash computation |

### Step 1.11: Integration Testing (Manual)

| Test | Method | Pass Criteria |
|---|---|---|
| Plugin loads on Paper 1.21.4 | Install JAR, start server | No errors in log; "AntiXray enabled" message |
| NMS adapter selected | Check log | "Using NMS adapter: v1_21_R3" |
| Chunk obfuscation works (Mode 1) | Join server with X-ray mod | Hidden ores not visible; air-exposed ores visible |
| Chunk obfuscation works (Mode 2) | Switch to mode 2, relog | Fake ores visible to X-ray; mix of real and fake |
| Chunk obfuscation works (Mode 3) | Switch to mode 3, relog | Layer-band pattern visible to X-ray |
| Config reload | `/antixray reload` | "Configuration reloaded" message; changes take effect |
| ProtocolLib fallback | Test on Spigot + ProtocolLib | Same obfuscation results as NMS mode |
| Graceful disable | Remove ProtocolLib on Spigot, restart | "No packet interception method available" warning; plugin disables |

### Phase 1 Deliverables

- [ ] `NmsAdapter` interface + 7 version-specific adapters (at minimum `v1_21_R3`, `v1_20_R2`, `v1_19_R3`)
- [ ] `PaletteManipulator` with full test coverage
- [ ] `BlockClassifier` + `AirExposureChecker`
- [ ] `Mode1Engine`, `Mode2Engine`, `Mode3Engine`
- [ ] `ObfuscationEngine` facade
- [ ] `NmsInterceptor` + `ProtocolLibInterceptor` with runtime selection
- [ ] `ConfigurationManager` + `WorldConfig` + `ConfigValidator`
- [ ] `plugin.yml` + `config.yml` with all Phase 1 options
- [ ] `/antixray reload` command
- [ ] Unit tests for all engine components (target: >90% line coverage on non-NMS code)
- [ ] Manual test pass on Paper 1.21.4: all 3 modes functional

---

## Phase 2: Deobfuscation

**Goal:** Blocks are revealed to players as they approach or interact with them. Per-player tracking operational. Edge cases handled.

**Dependencies:** Phase 1 (obfuscation engine must be functional).
**Estimated Effort:** Medium-Large
**Key Risks:** Packet spam from excessive deobfuscation updates. Frustum/raycast performance under load.

### Step 2.1: Per-Player Revealed Blocks Tracking

#### 2.1.1: `deobfuscation/RevealedBlocksSet.java`

Per-player data structure tracking which obfuscated blocks have been revealed.

**Specification:**
- Internal: `Long2LongMap` — encodes `(x, y, z)` as a long key, `lastRevealedTick` as a long value
- Bounded: maximum `max-revealed-per-player` entries (default 10,000). Evicts oldest (lowest `lastRevealedTick`) entries when limit is exceeded.
- Thread-safe: `ConcurrentHashMap`-backed or synchronized access

**Methods:**
| Method | Description |
|---|---|
| `add(BlockPosition pos, long tick)` | Mark a block as revealed at the given tick |
| `contains(BlockPosition pos)` | Check if a block is currently revealed |
| `remove(BlockPosition pos)` | Remove a revealed block (re-obfuscation) |
| `getRevealedBeforeTick(long tick)` | Return all positions revealed before the given tick (for re-obfuscation sweep) |
| `clear()` | Clear all revealed positions (used on world change, teleport) |
| `size()` | Current entry count |

#### 2.1.2: Per-Player Data Manager

Create a per-player data holder that stores:
- `RevealedBlocksSet revealedBlocks`
- `Location lastCheckedPosition` — last position where proximity check was run
- `long lastCheckTick` — tick of last proximity check

Managed via `Map<UUID, PlayerData>` in `AntiXrayPlugin`. Created on `PlayerJoinEvent`, removed on `PlayerQuitEvent`.

### Step 2.2: Proximity Deobfuscation

#### 2.2.1: `deobfuscation/ProximityTracker.java`

**Core algorithm (runs on scheduled interval, default every 5 ticks):**

1. Get player's current eye position
2. If distance from `lastCheckedPosition` < `movement-threshold` (default 0.5 blocks): skip this check
3. Compute bounding box: `[pos - update-radius, pos + update-radius]` on each axis
4. For each chunk section overlapping the bounding box:
   - For each block position in the section within the bounding box:
     - Get the block's real state from the world
     - If the block is in `hidden-blocks` AND NOT already in `player.revealedBlocks`:
       - Add to `toReveal` batch
5. Send batched deobfuscation update packets for all `toReveal` positions
6. Add all `toReveal` positions to `player.revealedBlocks` with `currentTick`
7. Update `lastCheckedPosition`

**Scheduling:** Use `BukkitScheduler.runTaskTimer()` with the configured `check-interval`. On Paper, consider using `BukkitScheduler.runTaskTimerAsynchronously()` for the position-check logic, then send packets on the main thread.

#### 2.2.2: `deobfuscation/VisibilityResolver.java`

Optional advanced checks that filter which blocks in the proximity radius should actually be revealed.

**Frustum culling (disabled by default):**
1. Compute player's yaw and pitch
2. Construct view frustum (4 planes) with configurable FOV
3. For each candidate block: test if block center is inside the frustum
4. Blocks outside frustum are skipped

**Ray-cast line-of-sight (disabled by default):**
1. Cast ray from player eye to candidate block center
2. Use DDA/Bresenham voxel traversal
3. If ray hits solid block before reaching candidate: skip (occluded)
4. If ray reaches candidate: reveal (visible)

**Decision needed:** Select ray-cast algorithm. DDA is recommended for balance of accuracy and performance. Amanatides & Woo voxel traversal is more precise but more complex. (See Decision Tracker item D1.)

#### 2.2.3: `util/Frustum.java`

View frustum computation from yaw, pitch, and FOV. Produces 4 clipping planes. Provides `boolean isInside(Vector3f point)` method.

#### 2.2.4: `util/Raycast.java`

Voxel ray-cast implementation. Given origin, direction, and max distance: traverse voxels along the ray, checking for solid blocks. Returns the first solid block hit, or empty if the ray reaches max distance unobstructed.

### Step 2.3: Deobfuscation Triggers (Event Listeners)

#### 2.3.1: `listener/BlockEventListener.java`

| Event | Action |
|---|---|
| `BlockBreakEvent` | After block is broken, check all 6 face-adjacent neighbors. If any neighbor is a hidden block now air-exposed (the broken block was opaque → now air): add to deobfuscation queue. |
| `BlockPlaceEvent` | After block is placed, check if any neighboring hidden block is now air-exposed from the new block's sides. (Low priority — placed blocks usually don't expose new ores.) |
| `BlockFromToEvent` | Water/lava flow: check adjacent blocks in the destination chunk for newly air-exposed hidden blocks. |
| `BlockPistonExtendEvent` | Check blocks exposed by piston extension for newly air-exposed hidden blocks. |
| `BlockPistonRetractEvent` | Check blocks exposed by piston retraction. |
| `EntityExplodeEvent` | After explosion, check all blocks in the explosion radius + all 6 neighbors of each destroyed block for newly air-exposed hidden blocks. |

All events trigger `DeobfuscationManager.queueDeobfuscation(player, blockPositions)`.

#### 2.3.2: `listener/PlayerEventListener.java` (Phase 2 additions)

| Event | Action |
|---|---|
| `PlayerMoveEvent` | If distance from `lastCheckedPosition` > `movement-threshold`: mark player for proximity check on next interval. Do NOT process deobfuscation synchronously in this handler. |
| `PlayerTeleportEvent` | Immediately clear `revealedBlocks`. Deobfuscate all hidden blocks within `update-radius` of destination. |
| `PlayerRespawnEvent` | Clear `revealedBlocks`. Rebuild from respawn point. |
| `PlayerGameModeChangeEvent` | If switching to/from spectator: clear `revealedBlocks`. In spectator mode, skip proximity deobfuscation (configurable). |

#### 2.3.3: `listener/ExplosionEventListener.java`

Separate listener for `EntityExplodeEvent` (called before `BlockBreakEvent` for explosions). Collects all destroyed block positions, then checks all neighboring positions for newly-exposed hidden blocks. Batches into a single deobfuscation update.

### Step 2.4: Deobfuscation Manager

#### 2.4.1: `deobfuscation/DeobfuscationManager.java`

Central orchestrator. Responsibilities:
- Accept deobfuscation requests from event listeners and proximity tracker
- Batch requests by tick and by chunk section
- Select appropriate packet type (Block Update vs Multi-Block vs Chunk Data)
- Send packets to the target player

**Batching logic:**
1. Accumulate deobfuscation requests per tick in a `Map<Player, List<BlockPosition>>`
2. At end of tick (or on next tick start), flush the batch:
   - Group positions by chunk section
   - For sections with 1–3 positions: send individual Block Update packets (0x09)
   - For sections with 4–64 positions: send one Update Section Blocks packet (0x3C)
   - For sections with >64 positions: re-send the chunk section via Chunk Data packet (0x27)
3. Rate limit: max 64 deobfuscation updates per player per tick (configurable). Excess is deferred to next tick.

#### 2.4.2: `deobfuscation/UpdatePacketBuilder.java`

Constructs deobfuscation update packets via `NmsAdapter`:
- `buildBlockUpdate(Location, int blockStateId)` → Block Update packet
- `buildMultiBlockUpdate(World, int chunkX, int chunkZ, Map<Location, Integer> changes)` → Update Section Blocks packet

### Step 2.5: Re-Obfuscation

#### 2.5.1: Delayed Re-Obfuscation Implementation

**Algorithm (runs on each proximity check interval):**
1. For each player with `re-obfuscate` enabled:
   - Get `currentTick`
   - Iterate `player.revealedBlocks.getRevealedBeforeTick(currentTick - re-obfuscate-delay)`
   - For each such position:
     - If the position is OUTSIDE the player's current `update-radius`:
       - Remove from `revealedBlocks`
       - Queue a re-obfuscation packet (send the obfuscated block state, i.e., replacement block or fake ore)
2. Batch re-obfuscation packets the same way as deobfuscation packets

**Tradeoff:** Delayed re-obfuscation (default 200 ticks = 10 seconds) prevents flickering while maintaining security. No re-obfuscation is simpler but less secure. Full re-obfuscation causes flickering. The delay is configurable.

### Step 2.6: Edge Cases

| Edge Case | Implementation |
|---|---|
| **Chunk borders** | Air-exposure check: if neighbor chunk not loaded, treat border as opaque (conservative). |
| **Teleporting** | `PlayerTeleportEvent` → clear `revealedBlocks`, immediately deobfuscate around destination. |
| **Login** | `PlayerJoinEvent` → send fully obfuscated chunks. Begin proximity checks after 20-tick delay (to allow chunk loading). |
| **World change** | `PlayerChangedWorldEvent` → clear `revealedBlocks`, start fresh in new world. |
| **Spectator mode** | Skip proximity deobfuscation. Players in spectator see obfuscated chunks. |
| **Respawn** | `PlayerRespawnEvent` → clear `revealedBlocks`, rebuild from spawn point. |
| **Rapid movement (elytra)** | Adaptive: if player velocity > threshold, expand `update-radius` temporarily by 50%. |
| **Multiple players** | Each player has independent `RevealedBlocksSet`. A block deobfuscated for Player A is still obfuscated for Player B. |

### Step 2.7: Unit Tests

| Test Class | Test Cases |
|---|---|
| `ProximityTrackerTest` | Radius check includes correct blocks; radius excludes distant blocks; Y-radius separate from X/Z radius; movement threshold filtering |
| `VisibilityResolverTest` | Frustum: blocks in front included, blocks behind excluded; Raycast: solid wall blocks visibility; Raycast: clear line-of-sight passes |
| `RevealedBlocksSetTest` | Add/contains/remove; eviction when exceeding max size; `getRevealedBeforeTick` returns correct entries; clear empties set |
| `DeobfuscationManagerTest` | Batch grouping by section; packet type selection (1–3 → single, 4–64 → multi, >64 → chunk); rate limiting defers excess; re-obfuscation after delay |

### Step 2.8: Integration Testing (Manual)

| Test | Method | Pass Criteria |
|---|---|---|
| Walk toward hidden ore | Walk to within 4 blocks of a known diamond ore | Ore appears (real block state sent) |
| Walk away from revealed ore | Walk >4 blocks away, wait 10 seconds | Ore re-obfuscates (appears as stone) |
| Mine stone near hidden ore | Break stone blocks adjacent to a hidden diamond | Diamond ore is revealed when stone is removed |
| Explosion reveals ores | Trigger TNT near hidden ores | All ores in blast radius revealed |
| Teleport | `/tp` to a new location | Ores around destination revealed; old area re-obfuscates |
| Nether portal | Enter nether portal | World changes, revealed set resets, ores around portal revealed |
| Elytra fly | Fly with elytra at high speed | Ores appear smoothly (adaptive radius working) |
| Multiple players | Two players near same hidden ore | Each player sees ore independently revealed |

### Phase 2 Deliverables

- [ ] `RevealedBlocksSet` with eviction
- [ ] Per-player data management (create on join, destroy on quit)
- [ ] `ProximityTracker` with configurable radius and interval
- [ ] `VisibilityResolver` with frustum culling (off by default) and ray-cast (off by default)
- [ ] `Frustum` and `Raycast` utility classes
- [ ] `BlockEventListener`, `PlayerEventListener` (Phase 2 additions), `ExplosionEventListener`
- [ ] `DeobfuscationManager` with batching and rate limiting
- [ ] `UpdatePacketBuilder`
- [ ] Delayed re-obfuscation logic
- [ ] All edge cases handled
- [ ] Unit tests for deobfuscation components
- [ ] Manual test pass: proximity, block break, explosion, teleport, world change

---

## Phase 3: Cache & Async

**Goal:** Obfuscated chunks are cached to avoid recomputation. Obfuscation work is offloaded to async threads. Backpressure prevents overload.

**Dependencies:** Phase 1 (cache stores obfuscation results), Phase 2 (cache invalidation triggered by block changes).
**Estimated Effort:** Large
**Key Risks:** Cache coherency bugs. L2 disk cache corruption. Thread pool sizing.

### Step 3.1: Cache Key and Entry

#### 3.1.1: `cache/CacheKey.java`

Composite key:
- `worldName: String`
- `chunkX: int`
- `chunkZ: int`
- `engineMode: int`
- `configHash: int` — hash of hidden-blocks list + fake-ore-chance + other config values that affect obfuscation output

Implements `equals()`, `hashCode()`. The `configHash` ensures that config changes automatically invalidate all cache entries (their key's configHash won't match the new config's hash).

#### 3.1.2: `cache/CacheEntry.java`

Stores the obfuscated chunk data:
- `byte[] obfuscatedData` — serialized obfuscated chunk section data (palette + packed indices for all sections)
- `long timestamp` — creation time (for expiry)
- `int sectionCount` — number of non-empty sections

Estimated size: ~2 KB per cached chunk.

### Step 3.2: L1 In-Memory Cache

#### 3.2.1: `cache/L1MemoryCache.java`

- Uses Caffeine library for LRU eviction with time-based expiry
- `Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(expirySeconds, SECONDS).build()`
- Thread-safe by default (Caffeine uses concurrent internals)
- Methods: `get(CacheKey)`, `put(CacheKey, CacheEntry)`, `invalidate(CacheKey)`, `invalidateAll()`, `size()`, `hitRate()`

**Base cache + overlay strategy:** The L1 cache stores the "fully obfuscated" version of each chunk (same for all players). Per-player deobfuscation overlays (the `RevealedBlocksSet` from Phase 2) are applied on top when constructing the packet to send to a specific player. This avoids per-player cache duplication.

**Overlay application:** When sending a chunk to a player:
1. Retrieve base obfuscated chunk from L1 cache
2. For each position in the player's `RevealedBlocksSet` that falls within this chunk:
   - Replace the obfuscated block state with the real block state
3. Re-encode the modified sections
4. Send the result

This overlay step is done synchronously (it's fast — typically < 10 positions per chunk).

### Step 3.3: L2 Disk Cache

#### 3.3.1: `cache/L2DiskCache.java`

Region-file-based persistent cache.

**File format:** One file per 32×32 chunk region, stored at `plugins/AntiXray/cache/<world>/r.<regionX>.<regionZ>.mca`

**Region file structure (simplified):**
- Header: 1024 entries (32×32), each 4 bytes — offset to chunk data within the file
- Chunk data: length (4 bytes) + compression type (1 byte) + compressed obfuscated data

**Write strategy:**
- Async writes via a dedicated IO thread (`ExecutorService` single-thread)
- On obfuscation completion: queue `(CacheKey, CacheEntry)` for write
- IO thread batch-writes: collects pending writes, appends to region files
- Never blocks the main thread or async obfuscation threads

**Read strategy:**
- On L1 cache miss (in async obfuscation thread): attempt L2 read
- Synchronous within the async thread (blocks only the obfuscation thread, not main thread)
- If L2 hit: promote to L1 and return
- If L2 miss: perform obfuscation, store in L1, queue for L2 write

**Disk budget:**
- Configurable per world (default: 500 MB)
- When budget exceeded: delete oldest region files (by last-modified time)
- Log warning at 80% budget usage

**Invalidation:**
- Same triggers as L1 (block changes, config changes)
- L2 invalidation is queued: mark entries as "dirty" and delete on next IO thread sweep
- On config change (full cache clear): delete all region files for affected worlds

**Startup:**
- Scan L2 directory for valid region files
- Pre-load recently-accessed chunks (within last `expiry-seconds`) into L1
- This accelerates initial chunk sends after server restart

**Decision needed:** Custom region-file format vs. adopting Minecraft's Anvil format directly. Custom is simpler to implement; Anvil is well-tested. (See Decision Tracker item D2.)

### Step 3.4: Cache Facade

#### 3.4.1: `cache/ObfuscationCache.java`

Facade that unifies L1 and L2:
1. `get(CacheKey)` → check L1 → if miss, check L2 → if miss, return null
2. `put(CacheKey, CacheEntry)` → store in L1, queue for L2 write
3. `invalidate(CacheKey)` → remove from L1, queue L2 deletion
4. `invalidateChunk(World, int chunkX, int chunkZ)` → invalidate all entries for a specific chunk
5. `invalidateAll()` → clear L1 + delete all L2 region files
6. `getHitRate()` → returns L1 hit rate for monitoring

### Step 3.5: Async Processing Pipeline

#### 3.5.1: `async/ObfuscationTask.java`

Task wrapper:
- `CacheKey key` — chunk to obfuscate
- `Priority priority` — CRITICAL, HIGH, MEDIUM, LOW
- `Consumer<CacheEntry> callback` — called when obfuscation completes
- `long enqueueTime` — for tracking wait time
- `AtomicBoolean cancelled` — for cancellation by backpressure

#### 3.5.2: `async/ThreadPoolManager.java`

- Creates `ThreadPoolExecutor` with `PriorityBlockingQueue<ObfuscationTask>`
- Core pool size: configurable, default `max(2, availableProcessors - 2)`
- Fixed-size pool (core = max)
- Thread naming: `AntiXray-Worker-{n}`
- Thread priority: `Thread.NORM_PRIORITY - 1`
- Rejection policy: `CallerRunsPolicy` (natural backpressure)
- `resize(int newSize)` — add or remove workers (used on config reload)

#### 3.5.3: `async/AsyncProcessor.java`

Main async pipeline:

1. `enqueue(ObfuscationTask)` — add task to priority queue
2. Worker thread loop:
   a. Take next task from queue
   b. Check if task is cancelled (backpressure may have cancelled it)
   c. Check per-tick budget (CRITICAL tasks only)
   d. Check chunk timeout
   e. Check L1/L2 cache — if hit, return cached result
   f. Perform obfuscation via `ObfuscationEngine`
   g. Store result in `ObfuscationCache`
   h. Call `task.callback(result)`
3. Track metrics: tasks completed, tasks timed out, average wait time, average process time

#### 3.5.4: `async/TickBudgetTracker.java`

- Tracks cumulative obfuscation time per tick for CRITICAL priority tasks
- `onTickStart()` — reset cumulative time
- `canProcessMore()` — returns `true` if cumulative time < `per-tick-budget-ms` (default 5ms)
- `recordProcessingTime(long ms)` — add to cumulative total
- Called by `AsyncProcessor` before each CRITICAL task

#### 3.5.5: `async/BackpressureHandler.java`

Monitors queue size and applies degradation:

| Queue Fill Level | Action |
|---|---|
| > 75% | Cancel all LOW priority tasks |
| > 90% | Cancel all MEDIUM priority tasks |
| > 95% | Send unobfuscated chunks for non-CRITICAL tasks |
| > 98% for 10 consecutive ticks | Log severe warning (suggest reducing render distance) |
| 100% (saturated) | Circuit breaker: disable obfuscation for 5 seconds, then gradually re-enable |

**Circuit breaker implementation:**
- `AtomicBoolean circuitOpen` — when true, all obfuscation is skipped (unobfuscated chunks sent)
- After 5 seconds: close circuit, allow 50% of tasks through
- After 10 seconds: allow 75%
- After 15 seconds: fully re-enabled
- If circuit opens 3 times in 60 seconds: log critical error, keep circuit open permanently (requires manual `/antixray toggle` or restart)

### Step 3.6: Integrate Cache + Async with Engine

#### 3.6.1: Modify Packet Interception to Use Async Pipeline

**NMS interceptor flow (updated):**
1. Chunk packet about to be sent to player
2. Check `ObfuscationCache.get(key)`:
   - If hit: apply player's deobfuscation overlay, return modified packet → done (fast path)
   - If miss: continue to step 3
3. For CRITICAL priority: perform obfuscation synchronously (with timeout), cache result, return
4. For lower priority: return unobfuscated packet, enqueue async obfuscation for future use

**ProtocolLib interceptor flow (updated):**
1. Packet intercepted in `onPacketSending`
2. Check cache → if hit, apply overlay, modify packet → done
3. If miss: perform obfuscation in the packet handler (ProtocolLib requires synchronous modification)
4. Cache result for future packets

**Decision needed:** Should the NMS interceptor ever send unobfuscated chunks on cache miss (for non-CRITICAL priority), or always wait for obfuscation? Tradeoff: latency vs. security. (See Decision Tracker item D3.)

### Step 3.7: Cache Invalidation Integration

Wire block event listeners from Phase 2 to call `ObfuscationCache.invalidateChunk()`:

| Event | Cache Action |
|---|---|
| `BlockBreakEvent` | `invalidateChunk(world, chunkX, chunkZ)` |
| `BlockPlaceEvent` | `invalidateChunk(world, chunkX, chunkZ)` |
| `BlockFromToEvent` | `invalidateChunk` for all affected chunks |
| `BlockPistonExtendEvent` / `BlockPistonRetractEvent` | `invalidateChunk` for affected chunks |
| `EntityExplodeEvent` | `invalidateChunk` for all chunks in explosion radius |
| Config reload (engine-mode or hidden-blocks changed) | `invalidateAll()` |

### Step 3.8: Paper Async Chunk Pre-Obfuscation

On Paper, register a callback on Paper's async chunk load API:
1. When a chunk is loaded asynchronously, enqueue an obfuscation task at MEDIUM priority
2. By the time a player needs the chunk, it may already be in the cache
3. This reduces login latency significantly

### Step 3.9: Unit Tests

| Test Class | Test Cases |
|---|---|
| `L1MemoryCacheTest` | Put/get; eviction when size exceeded; time-based expiry; invalidate key; invalidateAll; hit rate tracking |
| `L2DiskCacheTest` | Write and read back; region file format correctness; budget enforcement deletes oldest; invalidation; startup pre-load |
| `CacheInvalidationTest` | Block break invalidates correct chunk; explosion invalidates multiple chunks; config change clears all; configHash mismatch auto-invalidates |
| `ObfuscationTaskTest` | Priority ordering in queue; cancellation; timeout detection |
| `BackpressureHandlerTest` | Queue at 75% drops LOW; 90% drops MEDIUM; 100% opens circuit; circuit re-closes after delay; permanent open after 3 triggers |
| `TickBudgetTrackerTest` | Budget tracking per tick; `canProcessMore` returns false when exceeded; reset on new tick |

### Step 3.10: Performance Benchmarking

| Benchmark | Target | Method |
|---|---|---|
| Obfuscation per section | < 0.5ms | Microbenchmark: obfuscate 1000 sections, measure average |
| Obfuscation per chunk | < 8ms | Microbenchmark: obfuscate 100 chunks, measure average |
| L1 cache hit rate | > 80% | Simulate 100-player login on pre-generated world |
| L2 disk read latency | < 5ms (SSD) | Measure on test hardware |
| Circuit breaker response | < 1 tick | Fill queue to 100%, verify circuit opens |
| TPS impact (100 players) | < 0.5 TPS loss | Load test server with Spark profiling |

### Phase 3 Deliverables

- [ ] `CacheKey` + `CacheEntry`
- [ ] `L1MemoryCache` (Caffeine-based)
- [ ] `L2DiskCache` (region-file format)
- [ ] `ObfuscationCache` facade
- [ ] `AsyncProcessor` with priority queue
- [ ] `ThreadPoolManager` with configurable pool
- [ ] `TickBudgetTracker`
- [ ] `BackpressureHandler` with circuit breaker
- [ ] Cache invalidation wired to all block event listeners
- [ ] Paper async chunk pre-obfuscation
- [ ] L1/L2 integration tested
- [ ] Performance benchmarks meet targets
- [ ] Manual test: 100-player simulated login completes without TPS drop > 0.5

---

## Phase 4: Statistical Detection

**Goal:** Detect suspicious mining patterns and alert administrators. Statistics persisted to SQLite.

**Dependencies:** Phase 1 (block break events). Semi-independent — can be developed in parallel with Phases 2–3.
**Estimated Effort:** Medium
**Key Risks:** Threshold calibration requires real-world data. May need iterative post-release tuning.

### Step 4.1: Player Statistics Tracking

#### 4.1.1: `detection/PlayerStatistics.java`

Per-player in-memory statistics. Updated on every `BlockBreakEvent`.

**Tracked metrics:**

| Metric | Type | Update Trigger |
|---|---|---|
| `stoneMined` | `long` | Every stone/deepslate/netherrack/end_stone break |
| `oresMined` | `Map<Material, Long>` | Every ore break (by type) |
| `totalOresMined` | `long` | Every ore break (aggregate) |
| `playTimeMinutes` | `long` | Updated periodically from player's play time |
| `airAdjacentMined` | `long` | Mined blocks adjacent to air (for play style classification) |
| `totalMined` | `long` | Every block break |
| `directionChanges` | `long` | When player changes mining direction (heading delta > 45°) |
| `directionChangesTowardOre` | `long` | Direction changes that move toward a nearby hidden ore |
| `minedPositions` | `List<BlockPosition>` | Recent mining positions (last 1000, ring buffer) for spatial analysis |
| `yLevelHistogram` | `int[384]` | Count of ores mined per Y-level |
| `biomeHistogram` | `Map<Biome, Long>` | Count of ores mined per biome |
| `shortWindowBlocks` | `long` | Blocks mined in last 1000-block window (exponential moving average) |
| `shortWindowOres` | `long` | Ores mined in last 1000-block window |
| `longWindowBlocks` | `long` | Blocks mined in last 10000-block window |
| `longWindowOres` | `long` | Ores mined in last 10000-block window |

**Computed ratios (updated on each block break):**

| Ratio | Computation |
|---|---|
| `oreToStoneRatio` | `totalOresMined / max(1, stoneMined)` |
| `diamondToStoneRatio` | `diamondsMined / max(1, stoneMined)` |
| `orePerHour` | `totalOresMined / max(1, playTimeMinutes / 60.0)` |
| `diamondPerHour` | `diamondsMined / max(1, playTimeMinutes / 60.0)` |
| `straightToOreRatio` | `directionChangesTowardOre / max(1, directionChanges)` |
| `valuableOreRatio` | `(diamonds + emeralds + ancientDebris) / max(1, totalOresMined)` |
| `shortWindowOreRatio` | `shortWindowOres / max(1, shortWindowBlocks)` |
| `longWindowOreRatio` | `longWindowOres / max(1, longWindowBlocks)` |

#### 4.1.2: `detection/PlayStyleClassifier.java`

Classifies player's mining style based on `airAdjacentMined / totalMined`:
- If > 0.60: classify as **CAVING** — higher ore/stone ratio thresholds
- Otherwise: classify as **BRANCH_MINING** — lower ore/stone ratio thresholds

Classification is recomputed every 100 blocks mined. Applied as a multiplier to detection thresholds.

### Step 4.2: Detection Engine

#### 4.2.1: `detection/DetectionEngine.java`

Evaluates a player's statistics against configured thresholds on every `BlockBreakEvent` (if the broken block is an ore).

**Alert level determination:**

| Condition | Level |
|---|---|
| 1 metric slightly above warning threshold | `INFO` |
| 2+ metrics above warning threshold, OR 1 metric significantly above | `WARNING` |
| 3+ metrics above critical threshold, OR clear X-ray pattern (all short + long windows exceed critical) | `CRITICAL` |

**False positive guards:**
- `totalMined < minimum-sample-size` (default 100) → skip evaluation entirely
- `playTimeMinutes < grace-period-minutes` (default 30) → never flag CRITICAL (only INFO/WARNING)
- Both short AND long windows must exceed threshold for CRITICAL
- Play style classification adjusts thresholds

**Rate limiting:** At most 1 alert per player per 60 seconds to avoid spam.

### Step 4.3: Alert System

#### 4.3.1: `detection/AlertManager.java`

Dispatches alerts through configured channels:

| Channel | Implementation |
|---|---|
| **In-game chat** | Send formatted message to all online players with `antixray.notify` permission. Include player name, alert level, triggering metrics. |
| **Console log** | Write to server log at `WARN` level with structured format. |
| **Webhook** | Send HTTP POST to configured URL. Support plain JSON and Discord-embed formats. Async HTTP call with 5-second timeout. |
| **Log file** | Append to `plugins/AntiXray/detection.log` with rotation (max 10 MB, keep 5 files). |

#### 4.3.2: `detection/ActionExecutor.java`

When a player reaches CRITICAL level, execute configured actions:

| Action | Implementation |
|---|---|
| `log-only` | No additional action (already logged by AlertManager) |
| `warn` | Send the player a configurable warning message |
| `kick` | `player.kickPlayer(kickMessage)` |
| `ban` | If Vault is present: use Vault's ban system. If not: `Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, expiry, source)` |
| `command` | `Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandTemplate.replace("{player}", playerName))` |

Multiple actions can execute in sequence. Actions are configurable per alert level in `config.yml`.

### Step 4.4: Data Persistence

#### 4.4.1: `detection/StatisticsStorage.java`

SQLite database with the following schema:

```
TABLE player_stats:
  uuid TEXT PRIMARY KEY,
  last_known_name TEXT,
  stone_mined BIGINT,
  total_ores_mined BIGINT,
  diamonds_mined BIGINT,
  emeralds_mined BIGINT,
  ancient_debris_mined BIGINT,
  total_mined BIGINT,
  air_adjacent_mined BIGINT,
  play_time_minutes BIGINT,
  short_window_blocks BIGINT,
  short_window_ores BIGINT,
  long_window_blocks BIGINT,
  long_window_ores BIGINT,
  last_updated BIGINT

TABLE ore_breaks:
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  uuid TEXT,
  material TEXT,
  world TEXT,
  x INT, y INT, z INT,
  biome TEXT,
  timestamp BIGINT

TABLE alerts:
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  uuid TEXT,
  player_name TEXT,
  alert_level TEXT,
  triggering_metrics TEXT,
  timestamp BIGINT
```

**Write strategy:**
- `player_stats`: updated in-memory on every block break, persisted to SQLite every 60 seconds (periodic save task) and on player quit
- `ore_breaks`: inserted on every ore break (async write via single-thread executor)
- `alerts`: inserted when alert fires (async write)
- SQLite WAL mode for concurrent reads during writes

**MySQL support:** Optional. If `detection.storage.type=mysql`, use JDBC MySQL driver instead of SQLite. Same schema. Connection pooling via HikariCP (bundled and relocated).

### Step 4.5: Integration with Block Events

`BlockEventListener.BlockBreakEvent` handler (updated):
1. If broken block is stone/deepslate/netherrack: increment `stoneMined`
2. If broken block is an ore: increment `oresMined[material]`, `totalOresMined`, record in `ore_breaks`
3. Update `airAdjacentMined` (check if broken block is next to air)
4. Update exponential moving averages
5. Call `DetectionEngine.evaluate(player)` — this returns an alert level or null
6. If alert level is not null: call `AlertManager.dispatch(player, level, metrics)`
7. If alert level is CRITICAL: call `ActionExecutor.execute(player, actions)`

### Step 4.6: Unit Tests

| Test Class | Test Cases |
|---|---|
| `PlayerStatisticsTest` | Ratio computations; exponential moving average update; play style classification; direction-change detection |
| `DetectionEngineTest` | Below threshold → no alert; warning threshold → INFO/WARNING; critical threshold → CRITICAL; minimum sample size blocks alerts; grace period blocks CRITICAL; play style adjustment; rate limiting (1 per 60s) |
| `AlertManagerTest` | Chat message format; console log format; webhook HTTP call; log file rotation |
| `ActionExecutorTest` | Each action type executes correctly; multiple actions in sequence; command template substitution |
| `StatisticsStorageTest` | SQLite write + read roundtrip; periodic save; player quit triggers save; ore_breaks insertion; alerts query |

### Step 4.7: Integration Testing (Manual)

| Test | Method | Pass Criteria |
|---|---|---|
| Normal mining | Mine stone and ores normally | No alerts triggered (below thresholds) |
| Suspicious mining | Use X-ray mod, mine only ores | WARNING alert triggered within 5 minutes |
| High-rate diamond mining | Mine 20+ diamonds/hour | CRITICAL alert triggered |
| Grace period | New player mines lucky diamonds | No CRITICAL alert in first 30 minutes |
| Alert actions | Trigger CRITICAL alert with `kick` action | Player is kicked |
| Statistics persist | Mine ores, restart server, rejoin | Previous statistics loaded from SQLite |
| `/antixray stats` | Run command on a player | Displays current statistics |

### Phase 4 Deliverables

- [ ] `PlayerStatistics` with all tracked metrics and computed ratios
- [ ] `PlayStyleClassifier` (caving vs branch mining)
- [ ] `DetectionEngine` with threshold evaluation and false positive guards
- [ ] `AlertManager` (chat, console, webhook, log file)
- [ ] `ActionExecutor` (log, warn, kick, ban, command)
- [ ] `StatisticsStorage` (SQLite + optional MySQL)
- [ ] Integration with `BlockEventListener`
- [ ] Unit tests for detection components
- [ ] Manual test pass: alert triggering, false positive mitigation, persistence

---

## Phase 5: Resource-Pack Countermeasures

**Goal:** Counter resource-pack X-ray through air obfuscation and forced server resource packs.

**Dependencies:** Phase 1 (air obfuscation extends Mode 2/3).
**Estimated Effort:** Small-Medium
**Key Risks:** FPS impact of air obfuscation. Forced resource pack URL availability.

### Step 5.1: Air in Hidden-Blocks Support

#### 5.1.1: Modify Mode2Engine and Mode3Engine

When `air` is in the `hidden-blocks` list:
- Air blocks adjacent to ores are treated as `HIDDEN` (they get replaced with fake ores or the replacement block)
- Some real ores that are air-exposed are replaced with the replacement block (hidden)
- This creates "noise" in resource-pack X-ray view: fake ores appear, real ores disappear

**Implementation changes:**
- `BlockClassifier`: add `AIR` as a special classification when `hidden-blocks` contains air
- `Mode2Engine` / `Mode3Engine`: when a position is classified as `AIR` (air block in hidden-blocks), replace it with a random fake ore at `fake-ore-chance` probability, or with the replacement block otherwise
- `AirExposureChecker`: when air is in hidden-blocks, air blocks themselves can be obfuscated (they are no longer just "exposing" neighbors — they are also targets)

#### 5.1.2: Configuration

```yaml
hidden-blocks:
  - air        # Enables resource-pack countermeasure
  - diamond_ore
  - emerald_ore
  - ancient_debris
fake-ore-chance: 0.05  # Lower to reduce FPS impact
```

### Step 5.2: Forced Server Resource Pack

#### 5.2.1: Resource Pack Enforcement

Leverage Bukkit's built-in resource pack API:

| Method | Purpose |
|---|---|
| `Player.setResourcePack(URL, String)` | Send resource pack URL + SHA-1 hash to player |
| `PlayerResourcePackStatusEvent` | Listen for player's acceptance/decline/rejection |
| `Player.setResourcePack(URL, String, String, boolean)` | Paper: with `required` flag and prompt message |

**Flow:**
1. On `PlayerJoinEvent`, if `resource-pack.force-pack` is true:
   - Call `player.setResourcePack(packUrl, packHash)` with configured URL and hash
2. On `PlayerResourcePackStatusEvent`:
   - If status is `DECLINED` and `kick-on-decline` is true:
     - Kick player with `kick-message`
   - If status is `ACCEPTED`:
     - Log acceptance. Player can proceed.
   - If status is `FAILED_DOWNLOAD`:
     - Log error. Optionally kick player.
3. If `delay-join-until-loaded` is true:
   - Prevent player interaction (freeze movement, hide chat) until pack status is received
   - Timeout: if no status received within 30 seconds, kick player

### Step 5.3: Testing

| Test | Method | Pass Criteria |
|---|---|---|
| Air obfuscation (Mode 2) | Join with X-ray resource pack; Mode 2 + air in hidden-blocks | Fake ores visible in air pockets; some real ores hidden; confusing mix |
| Air obfuscation (Mode 3) | Same with Mode 3 | Layer-band pattern visible in air; real ores partially hidden |
| FPS impact | Measure client FPS with/without air obfuscation | FPS drop documented; configurable so operators can disable |
| Forced resource pack | Join server, accept pack | Pack applied; transparent textures overridden |
| Decline resource pack | Join server, decline pack | Player kicked with configured message |
| Normal gameplay | Play without X-ray, with air obfuscation | No visual artifacts beyond occasional "pop-in" of ores; acceptable FPS |

### Phase 5 Deliverables

- [ ] Air-in-hidden-blocks support in Mode 2 and Mode 3 engines
- [ ] Forced resource pack enforcement via Bukkit API
- [ ] `PlayerResourcePackStatusEvent` listener
- [ ] Configuration options for resource pack URL, hash, kick-on-decline, delay-join
- [ ] FPS impact documented
- [ ] Manual test pass: air obfuscation, forced pack, decline handling

---

## Phase 6: API & Commands

**Goal:** Stable public API for third-party integration. Full command set with tab completion. Permission enforcement.

**Dependencies:** Phases 1–4 (API exposes all prior functionality).
**Estimated Effort:** Medium
**Key Risks:** API design decisions are hard to reverse.

### Step 6.1: Public API

#### 6.1.1: `api/AntiXrayAPI.java`

Interface (not class) for third-party access:

| Method | Return | Description |
|---|---|---|
| `isObfuscated(Location)` | `boolean` | Is the block at this location currently obfuscated? |
| `getEngineMode(World)` | `int` | Current engine mode (1/2/3) for the world |
| `isEnabled(World)` | `boolean` | Is the plugin enabled for the world? |
| `registerCustomHiddenBlock(Material)` | `void` | Add a material to the runtime hidden-blocks set |
| `unregisterCustomHiddenBlock(Material)` | `void` | Remove a material from the runtime hidden-blocks set |
| `getObfuscationProvider(World)` | `ObfuscationProvider` | Get the provider for custom obfuscation logic |

Access: `AntiXrayPlugin.getInstance().getAPI()`

#### 6.1.2: `api/ObfuscationProvider.java`

Interface for custom obfuscation logic:

| Method | Description |
|---|---|
| `boolean shouldObfuscate(BlockState, World, int x, int y, int z)` | Return true to obfuscate, false to skip |
| `Material getReplacementBlock(World, int y)` | Return replacement material for this Y level |

Default implementation: uses configuration rules. Third-party plugins can provide custom implementations.

#### 6.1.3: `api/BlockVisibilityEvent.java`

Bukkit event fired when a block is deobfuscated for a player:

| Field | Type |
|---|---|
| `player` | `Player` |
| `location` | `Location` |
| `realMaterial` | `Material` |
| `obfuscatedMaterial` | `Material` |
| `isCancelled` | `boolean` (cancellable) |

#### 6.1.4: `api/PlayerXraySuspicionEvent.java`

Bukkit event fired when detection module flags a player:

| Field | Type |
|---|---|
| `player` | `Player` |
| `alertLevel` | `AlertLevel` (enum: INFO, WARNING, CRITICAL) |
| `triggeringMetrics` | `Map<String, Double>` |
| `isCancelled` | `boolean` (cancellable) |

### Step 6.2: Full Command Set

#### 6.2.1: `commands/StatsCommand.java`

`/antixray stats [player]`
- If no player specified: show own stats
- Display: ore/stone ratio, diamond/hour, total ores, total stone, play style classification, current alert level
- Permission: `antixray.stats`

#### 6.2.2: `commands/CheckCommand.java`

`/antixray check <player>`
- Detailed review: all metrics, recent ore break positions, Y-level distribution, biome distribution
- Highlight metrics that exceed warning/critical thresholds in yellow/red
- Permission: `antixray.check`

#### 6.2.3: `commands/ModeCommand.java`

`/antixray mode <1|2|3> [world]`
- Change engine mode at runtime
- If world specified: change only for that world
- If no world: change global default
- Clears affected caches
- Permission: `antixray.mode`

#### 6.2.4: `commands/CacheCommand.java`

`/antixray cache clear [world]`
- Clear obfuscation cache
- If world specified: clear only that world's cache
- If no world: clear all caches
- Permission: `antixray.cache`

#### 6.2.5: `commands/ToggleCommand.java`

`/antixray toggle [world]`
- Enable or disable the plugin for a world (or globally)
- Toggles the current state
- Permission: `antixray.toggle`

#### 6.2.6: `commands/StatusCommand.java`

`/antixray status`
- Display: enabled/disabled per world, engine mode per world, L1 cache size, L2 cache size, async queue size, active threads, circuit breaker state
- Permission: `antixray.admin`

#### 6.2.7: `commands/TimingsCommand.java`

`/antixray timings`
- Display: average obfuscation time per chunk/section, cache hit rate, packets modified per tick, async pipeline metrics, deobfuscation metrics
- Permission: `antixray.admin`

### Step 6.3: Tab Completion

Full tab completion for all commands as specified in BLUEPRINT Section 11.2. Implemented in `AntiXrayCommand.onTabComplete()`.

### Step 6.4: API Stability Guarantees

- `com.antixray.api` package is public API. Stable within a MAJOR version.
- All other packages are internal. No stability guarantee.
- Events are additive: new fields may be added, existing fields not removed/renamed.
- Deprecated methods: `@Deprecated` + Javadoc `@deprecated`. Functional for at least 1 MINOR version before removal.

### Step 6.5: Unit Tests

| Test Class | Test Cases |
|---|---|
| `AntiXrayAPITest` | `isObfuscated` returns correct value; `getEngineMode` returns world config; `registerCustomHiddenBlock` adds to set |
| `BlockVisibilityEventTest` | Event fires; cancellation prevents deobfuscation; fields accessible |
| `PlayerXraySuspicionEventTest` | Event fires; cancellation prevents action; metrics map correct |

### Phase 6 Deliverables

- [ ] `AntiXrayAPI` interface with implementation
- [ ] `ObfuscationProvider` interface with default implementation
- [ ] `BlockVisibilityEvent` + `PlayerXraySuspicionEvent` custom events
- [ ] All 8 commands with tab completion
- [ ] `PermissionConstants` enforced on all commands
- [ ] API unit tests
- [ ] Manual test: third-party plugin can use API to register custom hidden blocks

---

## Phase 7: Polish & Release

**Goal:** Production-ready plugin with complete documentation, i18n, multi-version testing, and published releases.

**Dependencies:** All prior phases.
**Estimated Effort:** Medium
**Key Risks:** Undiscovered bugs in less-tested NMS versions. Documentation gaps.

### Step 7.1: Internationalization (i18n)

#### 7.1.1: Message System

Create a simple i18n system:
- Load messages from `languages/<locale>.yml`
- Support per-player locale (detect from client locale sent in settings)
- Fallback to `en.yml` for missing translations
- Message keys for all command responses, alerts, kick messages, log messages

#### 7.1.2: Language Files

Create initial language files:
- `en.yml` — English (primary)
- `es.yml` — Spanish
- `de.yml` — German
- `fr.yml` — French
- `ja.yml` — Japanese
- `zh.yml` — Chinese (Simplified)
- `ko.yml` — Korean

### Step 7.2: Documentation

| Document | Content |
|---|---|
| `docs/CONFIGURATION.md` | Detailed guide for operators: every config option explained with examples, recommended presets (minimal/balanced/maximum), troubleshooting, performance tuning tips |
| `docs/API.md` | Developer API documentation: all public methods, events, usage examples, versioning policy |
| `docs/COMPATIBILITY.md` | Platform and version compatibility matrix, known incompatibilities, dependency requirements |

### Step 7.3: Multi-Version Testing

Test the plugin on each supported Minecraft version:

| Version | Test Server | Key Check |
|---|---|---|
| 1.19.4 | Paper 1.19.4 | NMS adapter loads, all 3 modes work |
| 1.20.1 | Paper 1.20.1 | NMS adapter loads, all 3 modes work |
| 1.20.4 | Paper 1.20.4 | NMS adapter loads, all 3 modes work |
| 1.20.6 | Paper 1.20.6 | NMS adapter loads, all 3 modes work |
| 1.21.1 | Paper 1.21.1 | NMS adapter loads, all 3 modes work |
| 1.21.3 | Paper 1.21.3 | NMS adapter loads, all 3 modes work |
| 1.21.4+ | Paper 1.21.4 | Full test suite (primary test version) |
| Latest Spigot | Spigot + ProtocolLib | ProtocolLib fallback works |

### Step 7.4: Folia Compatibility Testing

- Test on Folia server with all features
- Verify region task scheduler integration
- Document known Folia limitations
- If critical issues found: mark Folia as "experimental" in documentation

### Step 7.5: Performance Tuning

- Run full benchmark suite from Phase 3 on all supported versions
- Tune default config values based on benchmark results
- Verify benchmark targets are met (BLUEPRINT Section 13.6)
- Adjust `per-tick-budget-ms`, `pool-size`, cache sizes as needed

### Step 7.6: Conflict Detection

At `onEnable`, detect and warn about:
- Paper built-in Anti-Xray (`paper-world-defaults.yml` → `anti-xray.enabled: true`) → log warning, suggest disabling
- Orebfuscator plugin present → log warning about conflict
- Any plugin with name containing "xray", "orebfuscator", "anti-xray" → log informational warning

### Step 7.7: Release

| Step | Action |
|---|---|
| 1 | Update `gradle.properties` version to `1.0.0` (remove `-SNAPSHOT`) |
| 2 | Create git tag `v1.0.0` |
| 3 | Push tag → GitHub Actions builds and creates GitHub Release |
| 4 | Upload JAR to SpigotMC |
| 5 | Upload JAR to Modrinth |
| 6 | Upload JAR to Hangar |
| 7 | Write release notes summarizing all features |

### Phase 7 Deliverables

- [ ] i18n system with 7 language files
- [ ] `CONFIGURATION.md`, `API.md`, `COMPATIBILITY.md`
- [ ] Multi-version test pass on 1.19.4, 1.20.x, 1.21.x
- [ ] Folia compatibility tested and documented
- [ ] Performance benchmarks meet all targets
- [ ] Conflict detection for Paper Anti-Xray, Orebfuscator
- [ ] Version set to `1.0.0`
- [ ] Published on GitHub Releases, SpigotMC, Modrinth, Hangar

---

## Cross-Phase Concerns

### Thread Safety

All shared mutable state must use thread-safe data structures:
- `ConcurrentHashMap` for caches, per-player maps
- `AtomicBoolean`, `AtomicLong` for flags and counters
- `volatile` for configuration references (swap on reload)
- `ReentrantReadWriteLock` for L2 disk cache region file access

### Error Handling

- All NMS calls wrapped in try-catch. On failure: log error, send unmodified packet (safe fallback).
- If palette manipulation fails: log error, send unmodified chunk (safe fallback).
- If cache read fails: treat as cache miss, re-obfuscate.
- If cache write fails: log error, continue (data is still in L1).

### Logging

Consistent logging levels:
- `INFO`: Plugin enable/disable, configuration loaded, mode changes
- `WARN`: Config validation errors, cache misses (high rate), performance budget exceeded
- `ERROR`: NMS adapter failures, palette manipulation errors, uncaught exceptions
- `DEBUG` (if enabled): Per-chunk obfuscation details, cache hit/miss details

### Backward Compatibility

- Config: Missing keys → use defaults (never error on missing optional keys)
- API: Public methods never removed in minor/patch versions
- Events: Fields are additive only
- NMS: Each adapter is independent; adding new adapters doesn't break old ones

---

## Decision Tracker

Items from BLUEPRINT Section 20.1 that need resolution before or during implementation.

| ID | Decision | Options | Recommendation | Needed By | Status |
|---|---|---|---|---|---|
| D1 | Ray-cast algorithm for line-of-sight | A) DDA, B) Bresenham, C) Amanatides & Woo voxel traversal | A) DDA — best balance of accuracy and simplicity for short-range checks | Phase 2 | Open |
| D2 | L2 disk cache file format | A) Custom region-file format, B) Minecraft's Anvil format | A) Custom — simpler, no dependency on NMS Anvil code, purpose-built for obfuscated data | Phase 3 | Open |
| D3 | NMS interceptor behavior on cache miss for non-CRITICAL | A) Send unobfuscated, enqueue async, B) Always wait (synchronous) | A) Send unobfuscated — avoids delaying chunk send; async result cached for next request | Phase 3 | Open |
| D4 | Mode 3 layer fake block selection | A) Uniform random, B) Weighted by real ore distribution at Y-level | B) Weighted — produces more realistic fake patterns; weight data can be derived from world generator configs | Phase 1 | Open |
| D5 | Webhook alert format | A) Generic JSON, B) Discord embeds, C) Both | C) Both — support `format: "plain"` and `format: "discord-embed"` in config | Phase 4 | Open |
| D6 | Proximity cache strategy | A) Base cache + overlay, B) Per-player cache | A) Base cache + overlay — lower memory, acceptable reconstruction CPU | Phase 3 | Open |
| D7 | ProtocolLib vs NMS performance gap significance | Need benchmark data | Benchmark during Phase 1; if gap < 20%, consider dropping dual-mode to reduce maintenance | Phase 1 | Open |
| D8 | Chunk packet modification approach | A) In-place NMS modification, B) Deserialize → modify → reserialize | A) In-place — zero-copy, faster; NMS adapter handles the complexity | Phase 1 | Open |

---

## Risk Register

Risks from BLUEPRINT Section 19, prioritized and with mitigation tracking.

| ID | Risk | Severity | Likelihood | Mitigation | Phase Affected | Status |
|---|---|---|---|---|---|---|
| R1 | NMS version fragility — plugin breaks on new MC version | High | High | NMS abstraction layer; proactive snapshot testing; rapid patch releases | All | Open |
| R2 | Palette manipulation bugs — corrupted packets | High | Medium | Exhaustive unit tests; fuzz testing; fallback: send unmodified packet on error | Phase 1 | Open |
| R3 | Cache coherency bugs — stale data | High | Medium | Strict invalidation on all block events; configHash auto-invalidation; periodic integrity sampling | Phase 3 | Open |
| R4 | Thread safety issues | High | Medium | Concurrent data structures; extensive multi-threaded testing; read/write locks for L2 | Phase 3 | Open |
| R5 | Deobfuscation packet spam | Medium | High | Batching; rate limiting (max N per player per tick); adaptive frequency | Phase 2 | Open |
| R6 | Performance under load | Medium | Medium | Per-tick budget; backpressure; circuit breaker; configurable pool size; benchmarking | Phase 3 | Open |
| R7 | World seed leakage | Critical | Medium | Seed protection recommendations; feature seed randomization warnings; detect `/seed` permission leak | Phase 1+ | Open |
| R8 | Statistical detection false positives | Medium | Medium | Minimum sample size; grace period; play style classification; exponential smoothing; both windows required for CRITICAL | Phase 4 | Open |
| R9 | Config misconfiguration | Medium | High | Comprehensive validation; safe defaults; `/antixray status` shows effective config | Phase 1 | Open |
| R10 | Other anti-xray plugin conflict | High | Medium | Detect at startup; refuse to enable if Paper Anti-Xray is active; warn for others | Phase 7 | Open |
| R11 | Air obfuscation FPS impact | Medium | Medium | Document FPS impact; default off; configurable; lower `fake-ore-chance` recommended | Phase 5 | Open |
| R12 | Detection threshold calibration | Medium | High | Conservative defaults; requires real-world data post-release; plan iterative tuning | Phase 4+ | Open |

---

*End of Implementation Plan*
