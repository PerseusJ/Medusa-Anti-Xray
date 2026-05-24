# Anti-Xray Plugin Blueprint

> **Document Type:** Master Planning Document — Architecture, Design Decisions, Data Flows, and Specifications  
> **Version:** 1.0.0-draft  
> **Last Updated:** 2026-05-04  
> **Status:** Pre-implementation  

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture Overview](#2-architecture-overview)
3. [Chunk Obfuscation Engine (Core)](#3-chunk-obfuscation-engine-core)
4. [Deobfuscation Manager](#4-deobfuscation-manager)
5. [Resource-Pack X-Ray Countermeasures](#5-resource-pack-x-ray-countermeasures)
6. [Seed Protection](#6-seed-protection)
7. [Statistical Detection Module](#7-statistical-detection-module)
8. [Cache Layer](#8-cache-layer)
9. [Async Processing Pipeline](#9-async-processing-pipeline)
10. [Configuration System](#10-configuration-system)
11. [Permissions & Commands](#11-permissions--commands)
12. [API Layer](#12-api-layer)
13. [Performance Considerations](#13-performance-considerations)
14. [Compatibility & Dependencies](#14-compatibility--dependencies)
15. [Testing Strategy](#15-testing-strategy)
16. [Build & Release](#16-build--release)
17. [Project Structure](#17-project-structure)
18. [Implementation Phases](#18-implementation-phases)
19. [Risk Assessment](#19-risk-assessment)
20. [Open Questions & Future Considerations](#20-open-questions--future-considerations)

---

## 1. Project Overview

### 1.1 Plugin Identity

| Field | Value |
|---|---|
| **Plugin Name** | AntiXray |
| **Internal ID** | `com.antixray` |
| **Purpose** | Server-side obfuscation of chunk data to neutralize X-ray cheats, combined with statistical behavioral detection of suspicious mining patterns |
| **Target Platforms** | Paper (primary), Spigot (secondary), Folia (experimental) |
| **Minecraft Versions** | 1.19.4, 1.20.x, 1.21.x (forward-compatible where possible) |
| **Java Version** | 17+ (minimum) |
| **License** | To be determined |

### 1.2 Goals

#### What the Plugin WILL Do

- Obfuscate hidden blocks (ores, spawners, chests, etc.) in chunk data packets so that mod-based X-ray clients see no useful information
- Support three obfuscation engine modes with distinct tradeoffs between security, performance, and network overhead
- Deobfuscate blocks in real-time as players legitimately approach or interact with them
- Provide proximity-based deobfuscation with optional frustum culling and line-of-sight ray-casting
- Cache obfuscated chunk data to minimize repeated computation
- Perform all obfuscation work asynchronously to avoid main-thread stalls
- Detect suspicious mining patterns through statistical behavioral analysis
- Alert administrators to probable X-ray use with configurable thresholds and actions
- Offer a public developer API for third-party integration
- Support per-world configuration overrides
- Counter resource-pack X-ray through configurable strategies

#### What the Plugin WILL NOT Do

- Modify the client or enforce client-side anti-cheat — the server has no control over client rendering
- Fully prevent resource-pack X-ray for air-exposed ores without accepting side effects (FPS drops, forced resource packs)
- Protect against world seed compromise — if the seed is known, ore positions are calculable externally
- Prevent X-ray through external map viewers, dynmap, or world downloads
- Intercept or modify voice chat, minimap, or non-chunk-data packet types
- Detect X-ray with 100% certainty — all statistical detection has false-positive rates
- Function without at least one supported packet interception method (NMS on Paper, or ProtocolLib)

### 1.3 Threat Model

| Threat Type | Mechanism | Countered By | Limitations |
|---|---|---|---|
| **Mod-based X-ray (full)** | Client mod disables depth test, renders only target blocks | Engine Modes 1/2/3 — hidden blocks replaced in chunk data | None if configured correctly |
| **Mod-based X-ray (ESP/overlay)** | Client mod draws overlay highlights on block positions | Engine Modes 1/2/3 — fake/hidden data means highlights are wrong or missing | Mode 1 only hides; modes 2/3 actively mislead |
| **Resource-pack X-ray (air-exposed only)** | Transparent textures reveal ores with faces touching air | Partially: Engine Mode 2/3 with air in hidden-blocks, or forced opaque resource pack | Air-obfuscation causes client FPS drops; forced packs can be declined |
| **Resource-pack X-ray (fullbright)** | Fullbright pack removes darkness, does not reveal hidden ores | Not countered — fullbright only improves visibility of legitimately visible blocks | Server cannot detect or prevent fullbright |
| **Seed-based prediction** | Attacker computes ore positions from known world seed | Seed protection recommendations; feature seed randomization | If seed is already leaked, obfuscation is the only defense |
| **World download / map viewers** | Attacker downloads world or views dynmap | Not countered — out of scope | Restrict world downloads and map access at server level |
| **Freecam / spectator bypass** | Attacker moves camera through blocks without moving player position | Proximity deobfuscation reveals blocks near player position, not freecam position | Cannot fully prevent without client-side enforcement |

---

## 2. Architecture Overview

### 2.1 System Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Minecraft Server                                │
│                                                                         │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────────┐  │
│  │  Chunk Data   │───▶│  Packet          │───▶│  Chunk Obfuscation   │  │
│  │  Generation   │    │  Interception    │    │  Engine (Core)       │  │
│  │  (Server)     │    │  Layer           │    │                      │  │
│  └──────────────┘    │                  │    │  • Mode 1: Replace   │  │
│                      │  • NMS (Paper)   │    │  • Mode 2: Fake Ore  │  │
│  ┌──────────────┐    │  • ProtocolLib   │    │  • Mode 3: Layer     │  │
│  │  Block Events │───▶│    (Fallback)    │    │                      │  │
│  │  (Break, etc) │    └────────┬─────────┘    └──────────┬───────────┘  │
│  └──────────────┘             │                         │              │
│                               ▼                         ▼              │
│                      ┌──────────────────┐    ┌──────────────────────┐  │
│                      │  Deobfuscation   │◀───│  Cache Layer         │  │
│                      │  Manager         │    │                      │  │
│                      │                  │    │  • L1: Memory (LRU)  │  │
│                      │  • Proximity     │    │  • L2: Disk          │  │
│                      │  • Block Updates │    │                      │  │
│                      │  • Frustum/Ray   │    └──────────┬───────────┘  │
│                      └────────┬─────────┘               │              │
│                               │                         │              │
│                               ▼                         ▼              │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────────┐  │
│  │  Player       │    │  Async           │    │  Configuration       │  │
│  │  Connections  │    │  Processing      │    │  System              │  │
│  │               │    │  Pipeline        │    │                      │  │
│  └──────────────┘    │                  │    │  • Global defaults   │  │
│                      │  • Thread pool   │    │  • Per-world override│  │
│  ┌──────────────┐    │  • Priority Q    │    │  • Runtime reload    │  │
│  │  Statistics   │    │  • Backpressure  │    └──────────────────────┘  │
│  │  & Detection  │    └──────────────────┘                              │
│  │  Module       │                                                      │
│  │               │    ┌──────────────────┐    ┌──────────────────────┐  │
│  │  • Ratios     │    │  API Layer       │    │  Permissions &       │  │
│  │  • Alerts     │───▶│                  │───▶│  Commands            │  │
│  │  • Actions    │    │  • Events        │    │                      │  │
│  └──────────────┘    │  • Interfaces    │    │  • /antixray ...     │  │
│                      └──────────────────┘    └──────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Core Modules and Responsibilities

| Module | Responsibility | Key Interfaces |
|---|---|---|
| **Chunk Obfuscation Engine** | Transform chunk packet data to hide valuable blocks. Apply mode-specific logic (replace, fake, layer). Manipulate palettes and packed indices. | `ObfuscationEngine`, `ObfuscationMode`, `PaletteManipulator` |
| **Deobfuscation Manager** | Track which blocks have been revealed per-player. Send real block data when players approach or interact. Handle block events, proximity checks, frustum/raycast. | `DeobfuscationManager`, `ProximityTracker`, `VisibilityResolver` |
| **Configuration System** | Load, validate, and provide hierarchical config. Support runtime reload. Merge global defaults with per-world overrides. | `ConfigurationManager`, `WorldConfig`, `ConfigValidator` |
| **Cache Layer** | Store obfuscated chunk data to avoid recomputation. L1 in-memory LRU, L2 persistent disk. Handle invalidation on block changes. | `ObfuscationCache`, `L1MemoryCache`, `L2DiskCache` |
| **Async Processing Pipeline** | Offload obfuscation work from the main thread. Manage thread pool, priority queue, per-tick budgets, backpressure. | `AsyncProcessor`, `ObfuscationTask`, `ThreadPoolManager` |
| **Statistics & Detection Module** | Track per-player mining statistics. Compute ratios and patterns. Flag suspicious players and trigger alerts/actions. | `DetectionEngine`, `PlayerStatistics`, `AlertManager` |
| **Packet Interception Layer** | Intercept chunk data packets before they are sent to clients. Support NMS (Paper) and ProtocolLib (cross-platform) modes. | `PacketInterceptor`, `NmsInterceptor`, `ProtocolLibInterceptor` |
| **NMS Abstraction Layer** | Provide version-independent interface to NMS internals. Handle palette manipulation, chunk section access, packet construction per version. | `NmsAdapter`, per-version implementations |
| **API Layer** | Expose events, interfaces, and utility methods for third-party plugins. Maintain backward compatibility guarantees. | `AntiXrayAPI`, `ObfuscationProvider`, custom events |
| **Permissions & Commands** | Define permission nodes, handle command parsing, execution, and tab-completion. | `CommandHandler`, `PermissionConstants` |

### 2.3 Data Flow — Chunk Send (Primary Path)

```
1. Server generates/loads chunk data
2. Server prepares Chunk Data packet (0x27) for player
3. Packet Interception Layer captures packet before send
4. Async Processing Pipeline enqueues obfuscation task
5. Chunk Obfuscation Engine:
   a. Read chunk section palette and packed block indices
   b. Classify each block: hidden, replacement, air-exposed, tile entity
   c. Apply configured engine mode transformations
   d. Manipulate palette entries and remap packed indices
   e. Return modified packet data
6. Cache Layer stores result (keyed by world, chunk coords, mode)
7. Modified packet is sent to player
```

### 2.4 Data Flow — Deobfuscation (Player Approaches Hidden Block)

```
1. Player moves within update-radius of a previously obfuscated block
2. Deobfuscation Manager detects proximity (via PlayerMoveEvent or tick check)
3. VisibilityResolver computes which blocks should now be visible:
   a. Distance check: block within configured radius
   b. Optional frustum check: block within player's field of view
   c. Optional raycast check: line-of-sight from player eye to block
4. Deobfuscation Manager sends Block Update packets with real block states
5. Per-player revealed-blocks set is updated
6. Cache is NOT invalidated (obfuscated version still valid for other players)
```

### 2.5 Plugin Lifecycle

| Event | Actions |
|---|---|
| **onEnable** | Load configuration. Initialize NMS adapter for current version. Start async thread pool. Initialize cache (L1 + L2 if enabled). Register packet interceptor. Register event listeners. Initialize detection module. Load per-world configs for already-loaded worlds. |
| **onDisable** | Shutdown async thread pool (drain queue, await completion with timeout). Flush L2 disk cache. Save detection statistics. Unregister all listeners and packet interceptors. Release resources. |
| **World Load** | Load per-world config override (if defined). Initialize world-specific cache partitions. |
| **World Unload** | Evict all cache entries for that world. Clean up per-world state. |
| **Player Join** | Initialize per-player deobfuscation tracking. Initialize per-player detection statistics. Begin obfuscating chunks within render distance. |
| **Player Quit** | Release per-player deobfuscation state. Persist detection statistics to disk (if configured). Release per-player cache entries. |
| **Player Changed World** | Reset per-player deobfuscation tracking for new world. Trigger re-obfuscation of chunks around new position. |

---

## 3. Chunk Obfuscation Engine (Core)

### 3.1 Obfuscation Modes

#### Mode 1: Simple Replacement

| Aspect | Specification |
|---|---|
| **Behavior** | Every hidden block that is NOT adjacent to air is replaced with the dimension-appropriate replacement block (stone, deepslate, netherrack, end_stone) |
| **What X-ray user sees** | Only air-exposed ores are visible. Hidden ores appear as stone. |
| **Security level** | Basic — reveals which ores are near caves/tunnels |
| **Performance cost** | Low — simple per-block replacement, minimal palette changes |
| **Network overhead** | Low — replacement block usually already in palette, so palette size often unchanged |
| **Use case** | Servers prioritizing performance over maximum security |

**Detailed behavior:**

1. For each chunk section (16×16×16), iterate all block positions
2. If a block is in the `hidden-blocks` list AND is NOT air-exposed (no face adjacent to air/transparent block), replace it with the dimension's `replacement-block`
3. If a block is in the `hidden-blocks` list AND IS air-exposed, leave it as-is (the player would see it legitimately)
4. Tile entity blocks in `hidden-blocks` (chests, furnaces, etc.) are always hidden regardless of air exposure (they cannot be faked, but revealing them to X-ray is worse)

**Palette impact:** The replacement block is almost certainly already in the palette (stone/deepslate dominate most sections), so palette size rarely increases. In rare cases where a section contains only ores and no stone (e.g., a section full of ore blocks only), the replacement block is added to the palette (palette grows by 1 entry).

#### Mode 2: Random Fake Ore Injection

| Aspect | Specification |
|---|---|
| **Behavior** | Hidden blocks are replaced (as Mode 1) AND fake hidden-block entries are injected at random positions where stone/deepslate/netherrack exists |
| **What X-ray user sees** | A mix of real and fake ores — indistinguishable without verification (breaking them) |
| **Security level** | High — X-ray users see many false positives, waste time breaking fake ores |
| **Performance cost** | Medium — requires randomization per-block, more palette entries |
| **Network overhead** | High — fake ores add entries to palette, reducing compression efficiency |
| **Use case** | Servers prioritizing maximum X-ray confusion |

**Detailed behavior:**

1. Perform Mode 1 replacement first
2. For each non-hidden, non-air block in the section that matches the replacement block type (e.g., stone):
   - With configurable probability `fake-ore-chance` (default: 0.07, i.e., 7%), replace it with a randomly selected block from the `hidden-blocks` list
   - The random selection is weighted (configurable) or uniform
3. Fake ores are placed using a seeded RNG derived from the chunk coordinates and a server-side salt (to ensure determinism for caching, while being unpredictable to clients)
4. Air-exposed real ores are still left visible (legitimate)

**Palette impact:** Every distinct ore type in `hidden-blocks` that appears as a fake injection must be added to the section's palette. This can significantly expand palette size. For a section with 5 ore types and 4096 blocks, worst case the palette grows from ~5 entries to ~10. The packed indices require more bits per entry, increasing the packet payload.

**Determinism requirement:** Fake ore placement MUST be deterministic for a given chunk coordinate + mode + config + server salt. This ensures cache validity — the same chunk always produces the same obfuscated output, and cache entries can be reused across players (when proximity mode is not active).

#### Mode 3: Layer-Based Obfuscation

| Aspect | Specification |
|---|---|
| **Behavior** | Same as Mode 2, but all fake ores within a single Y-layer (0–15 within the section) use the SAME fake ore block type, chosen randomly per layer |
| **What X-ray user sees** | Distinct horizontal bands of fake ore, but within each band the same ore type appears — harder to distinguish patterns |
| **Security level** | Medium-High — still confusing but slightly more patterned than Mode 2 |
| **Performance cost** | Low — only one RNG call per layer (16 per section vs up to 4096) |
| **Network overhead** | Lower than Mode 2 — fewer distinct palette entries per section since each layer uses one fake type |
| **Use case** | Best balance of security and performance. Recommended default. |

**Detailed behavior:**

1. For each of the 16 Y-layers within a chunk section:
   - Randomly select one block type from `hidden-blocks` (using seeded RNG from chunk coords + layer index + salt)
   - This is the "layer fake block" for this Y-layer
2. Hidden blocks not adjacent to air are replaced with the layer's fake block (NOT the dimension replacement block)
3. Non-hidden replacement blocks within the layer are also randomly replaced with the layer's fake block at `fake-ore-chance` probability
4. This means all fake blocks in a layer are the same type — the palette only grows by 1 entry per layer (at most 16 entries for a fully obfuscated section)

**Palette impact:** Minimal expansion. Each layer adds at most 1 new palette entry. Across 16 layers, worst case is +16 entries, but in practice many layers will share the same fake block type, so typically +3 to +8 entries.

**Compression advantage:** Since all blocks in a layer share the same fake type, runs of identical indices are longer, improving zlib compression of the packed array. This is the key performance advantage over Mode 2.

### 3.2 Block Classification Logic

#### Hidden Blocks

Blocks that should be concealed from X-ray users. Configurable per-world. Default list:

| Block | NMS Type | Tile Entity? | Notes |
|---|---|---|---|
| Coal Ore | `coal_ore` / `deepslate_coal_ore` | No | Common, lower priority to hide |
| Copper Ore | `copper_ore` / `deepslate_copper_ore` | No | |
| Iron Ore | `iron_ore` / `deepslate_iron_ore` | No | |
| Gold Ore | `gold_ore` / `deepslate_gold_ore` | No | |
| Diamond Ore | `diamond_ore` / `deepslate_diamond_ore` | No | Highest priority target |
| Emerald Ore | `emerald_ore` / `deepslate_emerald_ore` | No | Mountain biomes only |
| Lapis Ore | `lapis_ore` / `deepslate_lapis_ore` | No | |
| Redstone Ore | `redstone_ore` / `deepslate_redstone_ore` | No | |
| Nether Gold Ore | `nether_gold_ore` | No | Nether |
| Nether Quartz Ore | `nether_quartz_ore` | No | Nether |
| Ancient Debris | `ancient_debris` | No | Nether — highest value target |
| Budding Amethyst | `budding_amethyst` | No | |
| Spawner | `spawner` | Yes | Cannot be faked — always hide |
| Chest | `chest` | Yes | Cannot be faked — always hide |
| Ender Chest | `ender_chest` | Yes (sort of) | Cannot be faked — always hide |
| Trapped Chest | `trapped_chest` | Yes | Cannot be faked — always hide |

**Classification rules:**

1. If a block's material type is in `hidden-blocks`, classify as **HIDDEN**
2. If a HIDDEN block is also a tile entity, classify as **HIDDEN_TILE_ENTITY** — these are ALWAYS obfuscated regardless of air-exposure (cannot be faked without sending tile entity data, which would be inconsistent)
3. If a HIDDEN block has at least one face adjacent to air or a transparent block AND is not a tile entity, classify as **AIR_EXPOSED** — these are left visible in Mode 1 and revealed in all modes
4. All other blocks are **NORMAL** — never modified

#### Replacement Blocks (Dimension-Aware)

| Dimension | Y Range | Replacement Block | Rationale |
|---|---|---|---|
| Overworld | 0–(-1 to -64 depending on version) | `deepslate` | Deepslate generates below Y=0 |
| Overworld | 1+ | `stone` | Stone is the dominant block above deepslate |
| Nether | All | `netherrack` | Netherrack dominates the nether |
| End | All | `end_stone` | End stone is the dominant solid block |

**Height-aware replacement:** The engine must determine the correct replacement block based on the world's minimum Y and the block's Y coordinate. For 1.18+ worlds with deepslate layers below Y=0, the replacement block switches from `stone` to `deepslate`. This is configurable — operators can set the `deepslate-replacement-y` value (default: 0).

#### Air-Exposed Check

A block is considered **air-exposed** if any of its 6 face-adjacent neighbors (not corner-adjacent) is a transparent or non-solid block.

**Transparency/Non-solid classification:**

The engine maintains a set of block types that are considered "transparent" for air-exposure checks. This includes:

- Air, Cave Air, Void Air
- Water, Lava (configurable: `lava-obscures` option — if true, lava counts as opaque for this check)
- All sign types, all banner types
- Glass, tinted glass, glass panes
- Leaves (configurable)
- Snow, ice, packed ice, blue ice
- All carpet types
- All slab/stair types (only the non-solid side)
- Fences, fence gates, walls (partial transparency)
- Anvils, cauldrons, composters (partial)
- All rail types
- All plant/flower/mushroom types

**Performance optimization:** The transparency set is pre-computed at startup from a hardcoded list + config additions. The check is a simple set lookup per neighbor — 6 lookups per block maximum.

**Lava obscures option:** When `lava-obscures: true`, blocks adjacent to lava are treated as NOT air-exposed (they remain hidden). This prevents X-ray from revealing ores hidden behind lava curtains. Default: `true`.

#### Block Entity (Tile Entity) Handling

Tile entities pose a unique challenge: their block state cannot simply be replaced because the client expects accompanying tile entity data in the chunk packet. Sending a fake tile entity would require fabricating NBT data, which is complex and error-prone. Sending no tile entity data for a fake block would cause client-side errors or inconsistencies.

**Rules for tile entity hidden blocks:**

1. **Always obfuscate** — even if air-exposed. An exposed chest is still visible to normal players through the deobfuscation system (proximity or interaction reveals it). The brief moment of seeing stone instead of a chest is acceptable.
2. **Replace with the dimension-appropriate replacement block** (stone/deepslate/netherrack/end_stone) — never with a fake ore. Fake ores would only be plausible for non-tile-entity blocks.
3. **Do NOT include tile entity NBT data** for the replaced position in the chunk packet.
4. **Deobfuscate immediately on proximity** — tile entity blocks should be revealed at a slightly larger radius than ores to avoid the "chest flicker" problem where a player sees stone and then the chest appears.

### 3.3 Chunk Packet Interception Strategy

#### Option A: NMS-Level Modification (Paper-Specific)

| Aspect | Detail |
|---|---|
| **Mechanism** | Override Paper's `ChunkPacketBlockController` or use Paper's chunk-send event to modify chunk data before packet construction |
| **Dependencies** | None — uses Paper's internal APIs |
| **Performance** | Maximum — operates at the lowest level, no packet construction/reconstruction overhead |
| **Compatibility** | Paper only — Spigot and Folia lack these hooks (Folia may support via region scheduling) |
| **Maintenance** | High — NMS code changes between Minecraft versions. Requires per-version adapter modules. |

**Paper integration points:**

- Paper 1.19.4–1.20.x: `ChunkPacketBlockController` abstract class — override `modifyBlocksPacket` method
- Paper 1.20.4+: Potential changes to chunk packet API — needs version-specific testing
- The NMS adapter layer abstracts these differences

#### Option B: ProtocolLib Packet Interception

| Aspect | Detail |
|---|---|
| **Mechanism** | Register a packet listener via ProtocolLib that intercepts `PacketType.Play.Server.MAP_CHUNK` (0x27) before it is sent |
| **Dependencies** | ProtocolLib (required) |
| **Performance** | Good — ProtocolLib is well-optimized, but packet reconstruction adds overhead vs NMS |
| **Compatibility** | Cross-platform — works on Spigot, Paper, and Folia |
| **Maintenance** | Lower — ProtocolLib abstracts most version differences |

**ProtocolLib integration details:**

- Register `PacketAdapter` with `ConnectionSide.SERVER_SIDE` and `ListenerPriority.NORMAL`
- In `onPacketSending`, read the packet's chunk data, modify it, and write back
- ProtocolLib provides `PacketContainer` for safe packet manipulation
- Must handle both pre-flattening (1.12) and post-flattening (1.13+) packet formats — though minimum supported version is 1.19.4, so only post-flattening format is relevant

#### Recommended: Dual-Mode

The plugin implements both interception strategies and selects at runtime:

```
IF running on Paper AND Paper chunk-send hook is available:
    USE Option A (NMS-level) — zero dependencies, max performance
ELSE IF ProtocolLib is installed:
    USE Option B (ProtocolLib) — cross-platform fallback
ELSE:
    WARN on enable — plugin cannot function without at least one interception method
    DISABLE gracefully
```

**Detection logic:** At startup, the plugin checks:
1. Is the server a Paper derivative? (Check for `PaperConfig` class or `paper.yml` existence)
2. Does the Paper version expose the expected chunk packet hook? (Attempt to load the NMS adapter class)
3. Is ProtocolLib present? (Check for `ProtocolLib` plugin)

**Mode switching at runtime is NOT supported** — the interception method is locked at startup. A server restart is required to change methods.

### 3.4 Palette Manipulation Details

Chunk sections use palettes to efficiently encode block state data. Understanding palette types is essential for correct manipulation.

#### Palette Types

| Type | When Used | Structure | Manipulation Strategy |
|---|---|---|---|
| **Single-value palette** | Section contains only 1 block type (e.g., all stone) | No packed array — just one block state ID | If the single value is a hidden block: replace with replacement block. If adding fakes (Mode 2/3): must upgrade to indirect palette. |
| **Indirect palette** | Section contains ≤N distinct block types (N = 2^bits_per_entry, where bits is 4 or less for 1.20+) | Palette array of block state IDs + packed array of indices | Core manipulation target. Add/remove palette entries, remap indices in packed array. |
| **Direct palette** | Section contains >N distinct block types | No palette array — each entry is a full block state ID (15 bits) | Rare for typical terrain. Cannot efficiently modify — every entry is a full ID. Must scan all 4096 entries and replace hidden IDs. |

#### Manipulation Process (Indirect Palette)

```
INPUT:  palette = [state_0, state_1, ..., state_n]
        packed_indices = [idx, idx, ...]  (4096 entries, each referencing palette)
        hidden_set = {diamond_ore, ...}
        replacement_state = stone

STEP 1: Identify palette entries that are hidden blocks
        For each palette entry:
            IF entry ∈ hidden_set AND block at position is NOT air-exposed:
                Mark as HIDDEN_PALETTE_ENTRY

STEP 2: Ensure replacement block is in palette
        IF replacement_state ∉ palette:
            Append replacement_state to palette
        replacement_index = palette.indexOf(replacement_state)

STEP 3: Remap packed indices
        For each index in packed_indices:
            IF palette[index] is a HIDDEN_PALETTE_ENTRY:
                Replace index with replacement_index

STEP 4: (Mode 2/3) Inject fake ores
        Determine fake positions based on mode logic
        For each fake position:
            IF fake_block_state ∉ palette:
                Append fake_block_state to palette
            fake_index = palette.indexOf(fake_block_state)
            packed_indices[fake_position] = fake_index

STEP 5: Re-encode packed array
        bits_per_entry = ceil(log2(palette.length))
        IF bits_per_entry > max_indirect_bits:
            Upgrade to direct palette
        Re-pack all indices into new bit-packed array
```

#### Keeping Palette Size Minimal

Expanded palettes directly increase packet size because:
1. More palette entries = more bytes in the palette section
2. More palette entries = more bits per index in the packed array
3. More bits per index = larger packed array (4096 × bits_per_entry bits)

**Optimization strategies:**

- **Mode 3 (Layer)**: Each layer uses one fake type, so at most 16 new entries. In practice, layers often share types, resulting in fewer entries.
- **Reuse existing palette entries**: When injecting fake ores, prefer ore types that are already in the palette (e.g., if diamond_ore is already present because a real diamond ore exists in the section, use diamond_ore as the fake type for that layer).
- **Palette compaction**: After manipulation, remove any palette entries that are no longer referenced by any index. Re-index remaining entries and remap the packed array.
- **Single-value optimization**: If after manipulation the entire section is one block type, downgrade to single-value palette (0 bits per entry).

#### Palette Size Budget

| Mode | Expected Additional Palette Entries | Bits Per Entry Impact |
|---|---|---|
| Mode 1 | 0–1 (replacement block usually present) | +0 to +1 bit |
| Mode 2 | 3–8 (depends on hidden-blocks list size) | +1 to +2 bits |
| Mode 3 | 1–5 (layer types often overlap) | +0 to +1 bit |

---

## 4. Deobfuscation Manager

### 4.1 When and How Real Blocks Are Revealed

Deobfuscation is the process of sending the real block state to a player who has previously received an obfuscated version. It is triggered by specific events and proximity conditions.

#### Trigger Events

| Event | Trigger Condition | Deobfuscation Action | Priority |
|---|---|---|---|
| **Block Break** | `BlockBreakEvent` fires | Reveal the broken block (player sees the real block as they break it — actually the block is being removed, so this is about revealing NEIGHBORING hidden blocks that are now air-exposed) | Critical |
| **Block Place** | `BlockPlaceEvent` fires | Reveal neighboring hidden blocks if the placed block creates a new air-adjacent face | Medium |
| **Player Proximity** | Player moves within `update-radius` of a hidden block | Send real block state for all hidden blocks within radius | High |
| **Water/Lava Flow** | `FluidLevelChangeEvent` or `BlockFromToEvent` | Reveal hidden blocks adjacent to the new fluid position | Medium |
| **Piston Extend/Retract** | `BlockPistonExtendEvent` / `BlockPistonRetractEvent` | Reveal hidden blocks exposed by piston movement | Medium |
| **Explosion** | `EntityExplodeEvent` | Reveal all hidden blocks in explosion radius + blocks newly exposed by destroyed blocks | High |
| **Redstone Activation** | Player activates a mechanism near hidden blocks | Reveal hidden blocks adjacent to the activated block | Low |

#### Update Packet Strategy

| Packet | ID | Use Case | Throughput |
|---|---|---|---|
| **Block Update** | 0x09 | Single block deobfuscation | 1 block per packet |
| **Update Section Blocks** | 0x3C | Multiple blocks in same section | Up to 64 blocks per packet |
| **Chunk Data** (re-send) | 0x27 | Full section re-obfuscation/deobfuscation | Entire section at once |

**Packet selection logic:**

1. If deobfuscating 1–3 blocks in the same section → use Block Update packets
2. If deobfuscating 4–64 blocks in the same section → use Update Section Blocks packet
3. If deobfuscating >64 blocks or entire sections → re-send the chunk section via Chunk Data packet (this is rare and typically only occurs on world change or login)

**Batching:** Deobfuscation updates triggered within the same tick are batched into a single Update Section Blocks packet where possible. This avoids sending dozens of individual Block Update packets per tick, which causes network congestion and client-side rendering stutter.

### 4.2 Proximity Deobfuscation (Advanced)

Proximity deobfuscation is the most sophisticated revelation strategy. It ensures that players see real blocks only when they are close enough to interact with them legitimately.

#### Per-Player Revealed Blocks Tracking

Each player maintains a set of block positions that have been deobfuscated for them. This is essential because:

- Different players may be in different positions, so different blocks are revealed to each
- When a player leaves an area, their revealed blocks may need re-obfuscation
- Without tracking, a player could move through the world and gradually reveal everything

**Data structure:** `Map<UUID, Set<BlockPosition>>` — player UUID to set of revealed block coordinates. The set is bounded (evicts oldest entries when exceeding `max-revealed-per-player` limit, default 10000).

**Memory estimate:** Each `BlockPosition` = ~12 bytes (3 ints). 10,000 positions × 12 bytes = ~120 KB per player. With 100 concurrent players = ~12 MB. Acceptable.

#### Distance-Based Radius Check

| Parameter | Default | Description |
|---|---|---|
| `update-radius` | 4 | Radius in blocks around the player within which hidden blocks are revealed |
| `update-radius-y` | same as `update-radius` | Optional separate vertical radius (for performance tuning) |
| `check-interval` | 5 ticks | How often to check for newly-in-range blocks (every 250ms at 20 TPS) |

**Algorithm (per check interval):**

1. Get player's current eye position
2. Compute the bounding box: `[pos - radius, pos + radius]`
3. For each chunk section overlapping the bounding box:
   - Iterate blocks in the section that are within the bounding box
   - If a block is in the `hidden-blocks` set AND not yet in the player's revealed set:
     - Optionally apply frustum check
     - Optionally apply raycast check
     - If all checks pass, add to the "to-reveal" batch
4. Send batched update packets for all "to-reveal" blocks
5. Add revealed positions to player's revealed set

#### Optional Frustum Culling

**Purpose:** Only reveal blocks that are within the player's field of view. Blocks behind the player are not revealed until they turn around.

**Implementation:**

1. Compute player's yaw and pitch
2. Construct a view frustum (4 planes: left, right, top, bottom) with configurable FOV (default: 90°)
3. For each candidate block in the radius check, test if the block's center point is inside the frustum
4. Blocks outside the frustum are skipped for this check interval (they may be revealed on the next interval after the player turns)

**Tradeoff:** Frustum culling reduces the number of block updates per interval, saving network bandwidth. However, it creates a "pop-in" effect when the player turns quickly — ores appear suddenly. This can itself be a tell for X-ray users (they learn that turning reveals ores). **Recommendation:** Frustum culling is disabled by default. Enable only on servers with severe bandwidth constraints.

#### Optional Ray-Cast Line-of-Sight Check

**Purpose:** Only reveal blocks that the player can actually see — blocks behind solid walls are not revealed even if within radius.

**Implementation:**

1. Cast a ray from the player's eye position to each candidate block's center
2. Trace the ray through the world, checking for solid block intersections
3. If the ray reaches the candidate block without hitting a solid obstruction, the block is visible → reveal
4. If the ray hits a solid block before reaching the candidate, the block is occluded → skip

**Performance:** Ray-casting is expensive. With `update-radius=4`, up to ~500 candidate blocks per check interval, each requiring a ray trace of up to 4 blocks distance. At 5-tick intervals for 100 players, this is 100,000 ray traces per second in the worst case. **Must be done asynchronously** or with strict per-tick budgets.

**Optimization:**

- Use a simplified ray-cast (DDA/Bresenham) rather than full Voxel ray-tracing
- Limit ray length to `update-radius`
- Cache ray-cast results for blocks that haven't changed since last check
- Only ray-cast for blocks that pass the frustum check (if enabled)

**Recommendation:** Ray-cast is disabled by default. Enable only on servers where maximum anti-xray effectiveness is required and performance budget allows it.

#### Movement-Triggered Updates

Instead of checking at fixed intervals, deobfuscation can be triggered by player movement:

- Register a `PlayerMoveEvent` listener
- If the player moves more than `movement-threshold` blocks (default: 0.5 blocks) from their last checked position:
  - Run the proximity check from the new position
  - Update the last-checked position

**Advantage:** More responsive — blocks are revealed immediately as the player approaches, rather than waiting for the next check interval.

**Disadvantage:** `PlayerMoveEvent` fires very frequently (every tick for moving players). The movement threshold and batch processing prevent excessive computation.

**Recommended approach:** Combine both — use `PlayerMoveEvent` with a movement threshold to trigger checks, but process the actual deobfuscation on a scheduled interval (not synchronously in the event handler). This prevents event handler overhead from blocking the main thread.

### 4.3 Re-Obfuscation When Player Leaves Area

When a player moves away from previously revealed blocks, should those blocks be re-obfuscated (sent as fake/hidden again)?

| Strategy | Behavior | Pros | Cons |
|---|---|---|---|
| **No re-obfuscation** | Once revealed, stays revealed for that player's session | Simple; no flickering; low overhead | Player can "map" ores by flying around; revealed set grows unbounded |
| **Full re-obfuscation** | Blocks outside `update-radius` are re-obfuscated on each check | Maximum security; revealed set stays small | Causes visible flickering as blocks switch between real and fake; high packet overhead |
| **Delayed re-obfuscation** | Blocks are re-obfuscated after being outside radius for `re-obfuscate-delay` ticks (default: 200 = 10 seconds) | Good balance; no rapid flickering; reasonable security | Slightly more complex; still some overhead |

**Recommended:** Delayed re-obfuscation with configurable delay. Default delay of 200 ticks (10 seconds) means blocks revert to obfuscated state 10 seconds after the player leaves the area. This prevents rapid flickering while maintaining security.

**Implementation:** Track a `lastRevealedTick` per block in the player's revealed set. On each check interval, if `currentTick - lastRevealedTick > re-obfuscate-delay` AND the block is outside the update radius, send an obfuscation update and remove from the revealed set.

### 4.4 Edge Cases

| Edge Case | Handling Strategy |
|---|---|
| **Chunk borders** | Hidden blocks at chunk borders must consider neighbors in adjacent chunks for air-exposure checks. If the adjacent chunk is not loaded, treat the border as opaque (conservative — keep block hidden). |
| **Teleporting** | On teleport (including Ender Pearl, /tp, Nether portal), immediately deobfuscate blocks around the destination within `update-radius`. Clear the player's revealed set and rebuild from the new position. |
| **Login** | On player login, send fully obfuscated chunks within render distance. Begin proximity deobfuscation after the player spawns. |
| **World change** | On world change, clear per-player revealed set. Send obfuscated chunks for the new world. Begin proximity deobfuscation from the new position. |
| **Nether portal** | Same as teleporting — treat as a world change + position change. |
| **Spectator mode** | Spectators see obfuscated chunks (they cannot interact with blocks anyway). If the plugin detects a player in spectator mode, it may skip proximity deobfuscation (configurable). |
| **Respawn** | On player respawn (death), clear revealed set and rebuild from respawn point. |
| **Rapid movement (elytra, ice)** | Increase check frequency or expand radius to prevent ores from "popping in" late. Consider adaptive radius based on player velocity. |
| **Multiple players near same block** | Each player has independent revealed sets. A block deobfuscated for Player A remains obfuscated for Player B unless Player B is also in range. |

---

## 5. Resource-Pack X-Ray Countermeasures

### 5.1 The Problem

Resource-pack X-ray replaces opaque block textures with transparent PNGs. Unlike mod-based X-ray, it:

- Works on vanilla clients — no mod installation required
- Only reveals ores with at least one face exposed to air (due to Minecraft's face culling — faces between two solid blocks are not rendered)
- Cannot see through solid blocks — it only makes existing visible surfaces transparent

The fundamental issue: these air-exposed ores are **legitimately visible** to normal players too. The server sends their real block state because a player walking through a cave would see them. Anti-xray obfuscation that only hides non-air-exposed ores does not help here.

### 5.2 Strategy A: Air in Hidden-Blocks List (Engine Mode 2/3)

**Mechanism:** Add `air` to the `hidden-blocks` list. When Mode 2 or 3 is active, random air blocks near ores will be replaced with fake ores, and some real ores will be replaced with air.

**Effect on resource-pack X-ray:**

- Fake ores appear around real air pockets, making it harder to distinguish real from fake
- Some real air-exposed ores are hidden (replaced with stone), so resource-pack X-ray does not see them
- Creates "noise" in the X-ray overlay

**Side effects:**

| Issue | Severity | Description |
|---|---|---|
| **Client FPS drops** | High | Fake air pockets change lighting calculations. Client must recompute light for fake air → fake ore transitions. With many fakes, FPS can drop 20–40%. |
| **Lighting artifacts** | Medium | Fake air blocks may cause unexpected light patterns (bright spots in dark caves). |
| **Mob spawning changes** | Low | Fake air blocks are not actually air on the server — they only appear as air in the chunk packet. Server-side spawn calculations use real blocks. Client-side rendering may show mobs in unexpected positions. |
| **Sound artifacts** | Low | Fake air may cause footstep sounds to change (client thinks player is walking on air). |

**Configuration recommendation:**

```yaml
engine-mode: 2
hidden-blocks:
  - air          # <- enables resource-pack countermeasure
  - diamond_ore
  - emerald_ore
  - ancient_debris
  # ... other ores
fake-ore-chance: 0.05  # Lower than usual to reduce FPS impact
```

### 5.3 Strategy B: Forced Server Resource Pack

**Mechanism:** Configure the server to force-apply a resource pack that includes opaque textures for all blocks. Players who decline the resource pack are kicked.

**Effect:**

- Replaces all transparent textures with opaque ones
- Completely neutralizes resource-pack X-ray (the transparent textures are overridden)
- Does not affect mod-based X-ray (mods can ignore the resource pack)

**Configuration:**

| Parameter | Description |
|---|---|
| `resource-pack.force-pack` | Boolean — enable forced pack |
| `resource-pack.pack-url` | URL to the server's resource pack (must be a direct download link) |
| `resource-pack.pack-hash` | SHA-1 hash of the pack for verification |
| `resource-pack.kick-on-decline` | Boolean — kick players who decline the pack |
| `resource-pack.kick-message` | Message displayed to kicked players |
| `resource-pack.delay-join-until-loaded` | Boolean — prevent player from joining until pack is applied |

**Limitations:**

- Players can still extract the world seed and compute ore positions
- Some players may have legitimate reasons to use custom resource packs
- The forced pack must be maintained and updated with each Minecraft version
- Large pack files increase initial join time
- Not 100% foolproof — modified clients can ignore the server resource pack

### 5.4 Strategy C: Bright/Fullbright Detection

**Mechanism:** Attempt to detect players using fullbright resource packs or gamma modifications.

**Assessment:** Not reliably detectable server-side. The server does not receive information about the client's gamma setting, brightness, or applied resource packs (beyond the server-forced pack acceptance status). Fullbright does not give information that is not already available to a normally-sighted player in a well-lit area — it only removes the need for torches.

**Recommendation:** Do not implement. Not feasible and provides minimal security benefit.

### 5.5 Recommended Approach

**Default configuration uses Strategy B (forced resource pack) as the primary countermeasure, with Strategy A (air in hidden-blocks) as an optional enhancement.**

| Configuration Preset | Strategies Used | Tradeoff |
|---|---|---|
| **Minimal** | None | No resource-pack protection. Best performance. |
| **Balanced** | Strategy B only | Forces opaque pack. No FPS impact. Players must accept pack. |
| **Maximum** | Strategy B + Strategy A | Forces opaque pack + air obfuscation. Maximum confusion. FPS impact on clients. |

**Operators should be advised:**

- Resource-pack X-ray is a lower-severity threat than mod-based X-ray (it only reveals air-exposed ores, which are visible to legitimate players anyway)
- The most cost-effective countermeasure is forcing a server resource pack
- Air obfuscation should only be enabled if the server operator determines the FPS tradeoff is acceptable for their player base

---

## 6. Seed Protection

### 6.1 The Problem

Minecraft's world generation is entirely deterministic based on the world seed. If an attacker knows the seed, they can:

1. Run the same world generation algorithm externally
2. Compute the exact positions of all ore deposits, structures, and rare features
3. Navigate directly to the most valuable resources without any in-game X-ray tool

**This completely defeats all obfuscation strategies** — the attacker does not need to see ores in chunk data because they already know where they are.

### 6.2 Feature Seed Randomization

Paper (and some Spigot builds) expose configuration options to randomize the internal seeds used for feature generation (ores, structures, etc.):

**`spigot.yml` options:**

| Option | Effect | Default | Recommended |
|---|---|---|---|
| `world-settings.default.seed-structure` | Randomizes structure generation seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-mineshaft` | Randomizes mineshaft generation seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-village` | Randomizes village generation seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-outpost` | Randomizes outpost generation seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-desert` | Randomizes desert temple seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-igloo` | Randomizes igloo seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-jungle` | Randomizes jungle temple seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-swamp` | Randomizes swamp hut seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-monument` | Randomizes ocean monument seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-shipwreck` | Randomizes shipwreck seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-ocean` | Randomizes ocean ruin seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-endcity` | Randomizes end city seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-slime` | Randomizes slime chunk seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-nether` | Randomizes nether fortress seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-bastion` | Randomizes bastion seed | Uses world seed | Set to a random value |
| `world-settings.default.seed-fossil` | Randomizes fossil seed | Uses world seed | Set to a random value |

**Plugin integration:** On first enable, if the plugin detects that feature seeds are set to the world seed (default), it should warn the operator in the console log and suggest setting randomized values. The plugin does NOT automatically modify `spigot.yml` — this is an operator responsibility.

### 6.3 World Seed Hash Verification

**Problem:** Even if feature seeds are randomized, the world seed itself may be leaked through:

- `/seed` command accessible to non-admin players
- Dynmap, BlueMap, or other map plugins exposing world information
- Server logs or crash reports containing seed data
- Social engineering or accidental disclosure by admins
- World downloads

**Mitigation strategy — Seed leak detection:**

The plugin can optionally monitor for known seed-leak vectors:

| Vector | Detection | Mitigation |
|---|---|---|
| `/seed` command | Check if non-op players have `minecraft.command.seed` permission | Warn operator; recommend revoking permission |
| Map plugins | Check for known map plugin installations | Warn operator; recommend restricting map access |
| Crash reports | Cannot directly detect | Document risk in configuration guide |
| World downloads | Cannot directly detect | Document risk; recommend anti-download plugins |

### 6.4 Recommendations for Server Operators

The plugin's configuration guide should prominently state:

1. **Randomize all feature seeds** in `spigot.yml` — this is the single most effective seed protection measure
2. **Restrict `/seed` command** to admin-only (revoke `minecraft.command.seed` from all non-op players)
3. **Do not use map plugins** that expose world terrain to unauthenticated users
4. **Do not share or publish** the world seed under any circumstances
5. **Use a plugin to prevent world downloads** if running a public survival server
6. **If the seed is already compromised**, the only effective countermeasure is obfuscation — which the plugin provides

---

## 7. Statistical Detection Module

### 7.1 Overview

Statistical detection is a **complementary approach** to obfuscation. Rather than hiding ores, it monitors player behavior for patterns that indicate X-ray use. This is useful because:

- It can detect X-ray even when obfuscation is imperfect (e.g., seed-based cheating)
- It provides evidence for administrative action (not just prevention)
- It can detect novel X-ray methods that bypass obfuscation

### 7.2 Tracked Per-Player Statistics

#### Mining Ratio Analysis

| Metric | Computation | Normal Range | Suspicious Range |
|---|---|---|---|
| **Ore-to-Stone ratio** | `ores_mined / stone_mined` | 0.01–0.05 (branch mining) | > 0.10 |
| **Diamond-to-Stone ratio** | `diamonds_mined / stone_mined` | 0.001–0.005 | > 0.01 |
| **Ore-per-hour rate** | `ores_mined / play_time_hours` | 10–60 | > 100 |
| **Diamond-per-hour rate** | `diamonds_mined / play_time_hours` | 1–5 | > 15 |

#### Mining Efficiency Analysis

| Metric | Computation | Normal Range | Suspicious Range |
|---|---|---|---|
| **Straight-to-ore ratio** | `ores_found_in_straight_tunnel / total_ores_found` | 0.2–0.5 | > 0.7 (too directly targeted) |
| **Avg blocks between ores** | `total_stone_mined / ore_find_events` | 20–100 | < 10 (finding ores too quickly) |
| **Valuable ore ratio** | `(diamond + emerald + ancient_debris) / total_ores` | 0.05–0.15 | > 0.30 (disproportionate valuable finds) |

#### Tunnel Pattern Analysis

| Metric | Computation | Description |
|---|---|---|
| **Direction changes toward ore** | Count of direction changes where new heading moves toward a hidden ore | X-ray users tend to change direction when they "see" an ore, creating patterns like: mine straight → turn toward diamond → mine straight → turn toward diamond |
| **Excavation start points** | Track where players start new tunnels | X-ray users often start tunnels at positions that directly target nearby ores |
| **Backtracking rate** | Count of cases where player mines toward an ore, stops, and returns | Legitimate miners backtrack when they don't find expected resources; X-ray users rarely backtrack because they always find what they're looking for |

#### Distribution Analysis

| Metric | Computation | Description |
|---|---|---|
| **Y-level distribution** | Histogram of mined ores by Y-level | X-ray users may have unusually concentrated distributions (e.g., mining primarily at Y=-59 for diamonds) |
| **Biome distribution** | Histogram of mined ores by biome | X-ray users may disproportionately mine in specific biomes (e.g., mountains for emeralds) |
| **Chunk clustering** | Spatial clustering of mined ore positions | X-ray users may have suspiciously tight clusters of ore finds (mining everything in a small area) |

### 7.3 Alert System

#### Alert Levels

| Level | Condition | Default Action |
|---|---|---|
| **INFO** | One metric slightly above normal range | Log only |
| **WARNING** | 2+ metrics above normal range OR one metric significantly above | Notify admins with `antixray.notify` permission |
| **CRITICAL** | 3+ metrics significantly above normal range OR clear X-ray pattern detected | Notify admins + configurable action (kick, ban, etc.) |

#### Thresholds (Configurable)

```yaml
detection:
  thresholds:
    ore-to-stone-ratio:
      warning: 0.08
      critical: 0.15
    diamond-per-hour:
      warning: 10
      critical: 20
    straight-to-ore-ratio:
      warning: 0.65
      critical: 0.80
    valuable-ore-ratio:
      warning: 0.25
      critical: 0.40
    minimum-sample-size: 100  # blocks mined before any evaluation
```

#### Notification Channels

| Channel | Mechanism | Configurability |
|---|---|---|
| **In-game chat** | Send formatted message to all players with `antixray.notify` permission | Enable/disable, custom message format |
| **Console log** | Write to server log at WARN level | Always enabled |
| **Webhook** | Send HTTP POST to configurable URL (Discord, Slack, custom) | Enable/disable, URL, message format |
| **Log file** | Append to `plugins/AntiXray/detection.log` | Enable/disable, rotation policy |

#### Configurable Actions

When a player reaches CRITICAL alert level, the following actions can be configured:

| Action | Description | Default |
|---|---|---|
| `log-only` | Record the event, take no action | Default |
| `warn` | Send the player a warning message |  |
| `kick` | Kick the player from the server with a configurable message |  |
| `ban` | Ban the player (requires Vault for ban management) |  |
| `command` | Execute a configurable server command (e.g., `tempban {player} 1h Suspected X-ray`) |  |

Multiple actions can be configured for each alert level.

### 7.4 False Positive Mitigation

#### Minimum Sample Size

No alerts are generated until a player has mined at least `minimum-sample-size` blocks (default: 100). This prevents false positives from players who mine 3 diamonds in their first 5 minutes (which could happen legitimately with luck in a cave).

#### Play Style Profiles

Different play styles produce different statistical profiles:

| Play Style | Ore/Stone Ratio | Diamond/Hour | Pattern |
|---|---|---|---|
| **Caving** | Higher (0.03–0.08) | 3–10 | Air-exposed ores, winding paths, high ore efficiency |
| **Branch mining** | Lower (0.01–0.04) | 1–8 | Hidden ores, straight tunnels, lower ore efficiency |
| **Speed mining** | Variable | Variable | Very high blocks/hour, may trigger rate alerts |

**Mitigation:** The detection module classifies players into rough play-style categories based on their initial behavior. Different thresholds are applied per category:

- **Cave explorers** get higher ore/stone ratio thresholds (because they naturally find more ores per block mined)
- **Branch miners** get lower ore/stone ratio thresholds (because they should find fewer ores per block)

**Classification heuristic:** If a player's mined blocks are predominantly in air-adjacent positions (>60% of mined blocks are next to air), classify as "caving." Otherwise, classify as "branch mining."

#### Grace Period

New players (play time < `grace-period`, default: 30 minutes) are not flagged at CRITICAL level — only at INFO/WARNING. This prevents immediate bans for lucky new players while still logging suspicious behavior for admin review.

#### Statistical Smoothing

All metrics are computed as **exponential moving averages** rather than raw counts. This smooths out short-term fluctuations and provides a more stable indicator:

- Short window (last 1000 blocks): detects recent behavior changes
- Long window (last 10000 blocks): provides overall trend
- Both windows must exceed thresholds for CRITICAL alerts

### 7.5 Data Persistence

Player statistics are persisted to disk to survive server restarts. Storage format: lightweight JSON or SQLite database.

| Storage Option | Pros | Cons |
|---|---|---|
| **JSON files** (per-player) | Simple, human-readable, no dependencies | Slow for large player counts; no query capability |
| **SQLite** | Efficient queries, single file, built-in JDBC | Slightly more complex; need to manage connections |
| **MySQL** (optional) | Shared across multiple servers (BungeeCord/Velocity) | External dependency; network latency |

**Recommended:** SQLite by default. MySQL as an optional alternative for multi-server setups.

---

## 8. Cache Layer

### 8.1 Why Caching Is Needed

Chunk obfuscation is CPU-intensive — each chunk section requires:

- Iterating 4096 block positions
- 6 neighbor lookups per block for air-exposure checks (24,576 lookups per section)
- Palette manipulation and re-encoding
- Random number generation for fake ore placement (Modes 2/3)

A single chunk (16 sections vertically) can require ~400,000 operations. With a render distance of 10 chunks, a player may request ~625 chunks on login — that's ~250 million operations. Without caching, this would cause severe main-thread stalls and multi-second login delays.

Caching avoids re-obfuscating chunks that haven't changed since the last obfuscation.

### 8.2 Two-Level Cache Design

#### L1: In-Memory LRU Cache

| Aspect | Specification |
|---|---|
| **Data structure** | `ConcurrentHashMap<CacheKey, CacheEntry>` with LRU eviction via `LinkedHashMap` or Caffeine library |
| **Key** | `CacheKey(worldName, chunkX, chunkZ, engineMode)` — if proximity mode is enabled, key also includes `playerUUID` |
| **Value** | `CacheEntry(obfuscatedPalette, obfuscatedPackedIndices, timestamp, sectionCount)` |
| **Size limit** | Configurable, default: 5000 chunks |
| **Time-based expiry** | Configurable, default: 300 seconds (5 minutes) |
| **Thread safety** | All operations must be thread-safe (concurrent reads from async threads, writes from async threads) |
| **Eviction policy** | LRU (Least Recently Used) — evict least-accessed entries when size limit is reached |
| **Memory estimate** | ~2 KB per cached chunk (palette + packed indices for all sections). 5000 chunks × 2 KB = ~10 MB |

**Per-player variant cache:** When proximity deobfuscation is active, each player has a different view of the world (different blocks are revealed). This means the obfuscated chunk data differs per player. Two approaches:

| Approach | Description | Tradeoff |
|---|---|---|
| **Per-player cache** | Key includes `playerUUID` — each player gets their own cache entry | High memory usage (players × chunks); no sharing |
| **Base cache + overlay** | Cache the "fully obfuscated" version (same for all players). Per-player, store only the delta (which blocks are deobfuscated). Reconstruct the player-specific packet from base + delta. | Lower memory; more CPU for reconstruction; complex implementation |

**Recommended:** Base cache + overlay. The base obfuscated chunk is shared across all players. Per-player data is just the set of deobfuscated positions (already tracked by the Deobfuscation Manager). When sending a chunk to a player, the engine:

1. Retrieves the base obfuscated chunk from L1 cache
2. Applies the player's deobfuscation overlay (replaces obfuscated positions with real block states)
3. Sends the result

This means the L1 cache key does NOT include `playerUUID` — it only stores the base obfuscated version. Player-specific data lives in the Deobfuscation Manager's per-player tracking.

#### L2: Disk Cache

| Aspect | Specification |
|---|---|
| **Format** | Region-file-based format (similar to Minecraft's Anvil region files) — one file per region (32×32 chunks), entries stored as compressed NBT or binary blobs |
| **Path** | `plugins/AntiXray/cache/<world>/r.<regionX>.<regionZ>.mca` (parallel to world save structure) |
| **Write strategy** | Async writes — obfuscated data is queued for disk write and written by a dedicated IO thread; never blocks the main thread |
| **Read strategy** | Synchronous on cache miss (async thread), blocking only the async obfuscation thread, not the main thread |
| **Compression** | Zlib (level 6) — same as Minecraft's region file compression |
| **Invalidation** | Same triggers as L1 (see below) |
| **Disk budget** | Configurable, default: 500 MB per world. Oldest regions are deleted when budget is exceeded. |
| **Startup behavior** | L2 cache is scanned on startup; valid entries are pre-loaded into L1 for recently-accessed chunks |

**Why region-file format:** It's well-understood, efficient for random access, and handles partial updates well (only affected chunks need rewriting, not the entire file).

### 8.3 Cache Invalidation

#### Invalidation Triggers

| Trigger | Scope | Action |
|---|---|---|
| **Block break** | Single chunk | Invalidate L1 entry for that chunk. Queue L2 entry for deletion/update. |
| **Block place** | Single chunk | Same as block break |
| **Block physics (water/lava flow)** | Affected chunk(s) | Invalidate affected chunks |
| **Piston movement** | Affected chunk(s) | Invalidate affected chunks |
| **Explosion** | All chunks in explosion radius | Invalidate all affected chunks |
| **Chunk regeneration** | Single chunk | Invalidate and re-obfuscate |
| **World save** | No invalidation needed | World save does not change blocks |
| **Config change (engine mode)** | All chunks in all worlds | Clear entire L1 and L2 cache |
| **Config change (hidden-blocks)** | All chunks in all worlds | Clear entire L1 and L2 cache |
| **Player-specific deobfuscation** | No cache invalidation | Player-specific overlay is separate from cached base obfuscation |

#### Invalidation Implementation

- **Synchronous invalidation:** Block events on the main thread immediately remove the L1 cache entry. This is fast (HashMap remove).
- **Async L2 invalidation:** L2 entries are marked as "dirty" and queued for deletion on the IO thread. The IO thread batch-processes invalidations to minimize disk I/O.
- **Batch invalidation:** When an explosion affects multiple chunks, all invalidations are batched into a single operation.

### 8.4 Cache Key Design

```
CacheKey = {
    worldName: String,       // e.g., "world", "world_nether"
    chunkX: int,             // chunk X coordinate
    chunkZ: int,             // chunk Z coordinate
    engineMode: int,         // 1, 2, or 3 — different modes produce different output
    configHash: int          // hash of relevant config values (hidden-blocks, fake-ore-chance, etc.)
}
```

The `configHash` ensures that if the configuration changes (e.g., an ore is added to hidden-blocks), all cache entries are automatically invalid (their configHash won't match the current config's hash). This avoids the need for explicit full-cache clears on minor config changes.

---

## 9. Async Processing Pipeline

### 9.1 Why Async Is Essential

Chunk obfuscation involves:

- Iterating ~4000 blocks per section × ~16 sections per chunk
- 6 neighbor lookups per block
- Palette manipulation and bit-packing
- Random number generation

Even at 0.5ms per section (target), a single chunk takes ~8ms. With 100 players and render distance 10, the server sends ~625 chunks per player on login. That's 62,500 chunk obfuscations — at 8ms each, that's 500 seconds of CPU time. This MUST be parallelized and spread across multiple ticks.

### 9.2 Thread Pool Architecture

| Aspect | Specification |
|---|---|
| **Pool type** | `ThreadPoolExecutor` with configurable core pool size |
| **Core pool size** | Configurable, default: `max(2, availableProcessors - 2)` — leaves 2 cores for the main thread |
| **Max pool size** | Same as core pool size (fixed-size pool) |
| **Queue type** | `PriorityBlockingQueue<ObfuscationTask>` — priority queue for task ordering |
| **Thread naming** | `AntiXray-Worker-{n}` — for debugging and profiling |
| **Thread priority** | `Thread.NORM_PRIORITY - 1` — slightly below normal to avoid competing with the main thread |
| **Rejection policy** | `CallerRunsPolicy` — if the queue is full, the calling thread (main thread or async scheduler) runs the task itself. This provides natural backpressure. |

### 9.3 Task Priority Queue

Tasks are ordered by priority to ensure the most impactful work is done first:

| Priority Level | Value | Assigned To |
|---|---|---|
| **CRITICAL** | 0 | Chunks being sent to a player RIGHT NOW (packet interception synchronous path) |
| **HIGH** | 1 | Chunks within 3 chunks of a player's current position (nearby, will be needed soon) |
| **MEDIUM** | 2 | Chunks within render distance but distant from the player |
| **LOW** | 3 | Pre-caching tasks (chunks likely to be needed based on movement direction) |

**Priority assignment:** When a chunk obfuscation task is enqueued, its priority is determined by the distance from the nearest player who needs the chunk.

### 9.4 Per-Tick Budget

| Parameter | Default | Description |
|---|---|---|
| `per-tick-budget-ms` | 5 | Maximum milliseconds per tick that the async pipeline can spend on obfuscation tasks that feed into synchronous packet sending |
| `max-queue-size` | 10000 | Maximum number of pending obfuscation tasks before backpressure kicks in |
| `chunk-timeout-ms` | 50 | Maximum time to spend obfuscating a single chunk before giving up |

**Budget enforcement:**

The async pipeline tracks cumulative obfuscation time per tick. If the per-tick budget is exceeded, remaining tasks are deferred to the next tick. This prevents the pipeline from consuming too much CPU and causing TPS drops.

**Note:** The per-tick budget only applies to tasks that must complete before a packet can be sent (CRITICAL priority). Background pre-caching tasks are not budget-limited — they use whatever CPU time is available.

### 9.5 Chunk Timeout

If obfuscation of a single chunk takes longer than `chunk-timeout-ms` (default: 50ms):

1. The obfuscation task is interrupted
2. The original (unobfuscated) chunk data is sent to the player
3. A warning is logged (this indicates a performance problem)
4. The chunk is NOT cached (no point caching a timed-out result)

This ensures that even under extreme load, players always receive chunk data — it may be unobfuscated, but the server does not hang.

### 9.6 Integration with Server Schedulers

#### Paper: Async Chunk Scheduler

Paper supports asynchronous chunk loading and generation. The plugin can integrate with Paper's async chunk API to pre-obfuscate chunks as they are loaded/generated, rather than waiting for the packet-sending moment:

1. Register a callback on Paper's chunk load future
2. When a chunk is loaded asynchronously, enqueue an obfuscation task
3. By the time the chunk needs to be sent to a player, it may already be obfuscated (cache hit)

This significantly reduces the latency of chunk sending for players.

#### Folia: Region Task Scheduler

Folia divides the world into independent regions, each with its own tick loop. The plugin must:

1. Schedule obfuscation tasks on Folia's region task scheduler (not the global scheduler)
2. Ensure that chunk data access is confined to the region that owns the chunk
3. Handle cross-region player movement correctly

Folia support is **experimental** in Phase 1. Full Folia compatibility is targeted for a later release.

#### Spigot: Bukkit Scheduler with Async Tasks

On Spigot (without Paper's async chunk API), the plugin uses:

1. `BukkitScheduler.runTaskAsynchronously()` for obfuscation work
2. `BukkitScheduler.runTask()` for sending packets back on the main thread
3. This is the least efficient approach — no async chunk loading, no pre-caching

### 9.7 Backpressure Handling

When the obfuscation pipeline falls behind (queue size exceeds threshold, or per-tick budget is consistently exceeded):

| Strategy | Trigger | Action |
|---|---|---|
| **Drop LOW priority tasks** | Queue size > 75% | Discard all LOW priority pre-caching tasks |
| **Drop MEDIUM priority tasks** | Queue size > 90% | Discard MEDIUM priority tasks, keep only HIGH and CRITICAL |
| **Send unobfuscated chunks** | Queue size > 95% or per-tick budget exceeded for 3 consecutive ticks | Send original chunk data (unobfuscated) for non-CRITICAL tasks |
| **Reduce render distance** | Queue size > 98% for 10 consecutive ticks | Log a severe warning suggesting the operator reduce render distance or increase thread pool size |
| **Circuit breaker** | Queue size > 100% (saturated) | Temporarily disable obfuscation entirely for 5 seconds, then gradually re-enable. Log critical warning. |

The circuit breaker is the last resort. It prevents server crashes due to obfuscation overload but leaves the server temporarily unprotected.

---

## 10. Configuration System

### 10.1 Hierarchical Configuration

Configuration is applied in a layered hierarchy:

```
1. Built-in defaults (hardcoded in the plugin)
2. Global config file (config.yml) — overrides defaults
3. Per-world config overrides (worlds section in config.yml) — override global values
4. Runtime commands (/antixray mode, /antixray toggle) — override config values for the session
```

**Merge strategy:** Deep merge — per-world values override specific keys, unspecified keys fall through to the global config, and unspecified global keys fall through to defaults.

### 10.2 Config File Structure (YAML)

```yaml
# ============================================
# AntiXray Configuration
# ============================================

# Whether the plugin is enabled globally
enabled: true

# Engine mode:
#   1 = Simple replacement (hidden → stone/deepslate/netherrack)
#   2 = Random fake ore injection (hidden + random fakes)
#   3 = Layer-based obfuscation (same fake per Y-layer) [RECOMMENDED]
engine-mode: 3

# Blocks to hide from X-ray users
hidden-blocks:
  - coal_ore
  - deepslate_coal_ore
  - copper_ore
  - deepslate_copper_ore
  - iron_ore
  - deepslate_iron_ore
  - gold_ore
  - deepslate_gold_ore
  - diamond_ore
  - deepslate_diamond_ore
  - emerald_ore
  - deepslate_emerald_ore
  - lapis_ore
  - deepslate_lapis_ore
  - redstone_ore
  - deepslate_redstone_ore
  - nether_gold_ore
  - nether_quartz_ore
  - ancient_debris
  - budding_amethyst
  - spawner
  - chest
  - ender_chest
  - trapped_chest

# Replacement blocks per dimension
replacement-blocks:
  overworld:
    default: stone
    below-y: deepslate
    deepslate-below-y: 0
  nether: netherrack
  end: end_stone

# Maximum Y coordinate to apply obfuscation (blocks above this Y are not obfuscated)
# Default: 64 (ores don't generate above this in most worlds)
max-block-height: 64

# Probability of placing a fake ore in Modes 2 and 3 (0.0 to 1.0)
fake-ore-chance: 0.07

# Whether lava-adjacent blocks are considered hidden (not air-exposed)
lava-obscures: true

# Whether leaves are considered transparent for air-exposure checks
leaves-are-transparent: true

# Permission to bypass obfuscation (see real blocks)
bypass-permission: antixray.bypass

# ============================================
# Deobfuscation / Proximity Settings
# ============================================
proximity:
  # Whether proximity deobfuscation is enabled
  enabled: true

  # Radius in blocks within which hidden blocks are revealed
  update-radius: 4

  # Separate vertical radius (optional, defaults to update-radius)
  update-radius-y: 4

  # How often (in ticks) to check for newly-in-range blocks
  check-interval: 5

  # Movement threshold (in blocks) before triggering a proximity check
  movement-threshold: 0.5

  # Whether to use frustum culling (only reveal blocks in field of view)
  frustum-culling: false

  # Field of view for frustum culling (degrees)
  frustum-fov: 90

  # Whether to use ray-cast line-of-sight checks
  raycast-line-of-sight: false

  # Whether to re-obfuscate blocks when player leaves area
  re-obfuscate: true

  # Delay in ticks before re-obfuscating a block after player leaves area
  re-obfuscate-delay: 200

# ============================================
# Cache Settings
# ============================================
cache:
  # L1 in-memory cache settings
  l1:
    # Maximum number of chunks to keep in memory
    max-size: 5000

    # Time in seconds before a cached entry expires
    expiry-seconds: 300

  # L2 disk cache settings
  l2:
    # Whether disk caching is enabled
    enabled: true

    # Directory for disk cache (relative to plugin directory)
    path: "cache"

    # Maximum disk usage per world in MB
    max-disk-mb: 500

    # Whether to pre-load recent entries from L2 on startup
    preload-on-startup: true

# ============================================
# Async Processing Settings
# ============================================
async:
  # Number of worker threads (0 = auto-detect: max(2, processors - 2))
  pool-size: 0

  # Maximum milliseconds per tick for synchronous obfuscation work
  per-tick-budget-ms: 5

  # Maximum time in ms to spend obfuscating a single chunk before timeout
  chunk-timeout-ms: 50

  # Maximum pending tasks in the queue before backpressure
  max-queue-size: 10000

# ============================================
# Statistical Detection Settings
# ============================================
detection:
  # Whether the detection module is enabled
  enabled: true

  # Minimum number of blocks a player must mine before evaluation begins
  minimum-sample-size: 100

  # Grace period in minutes for new players (no CRITICAL alerts)
  grace-period-minutes: 30

  # Alert thresholds
  thresholds:
    ore-to-stone-ratio:
      warning: 0.08
      critical: 0.15
    diamond-per-hour:
      warning: 10
      critical: 20
    straight-to-ore-ratio:
      warning: 0.65
      critical: 0.80
    valuable-ore-ratio:
      warning: 0.25
      critical: 0.40

  # Actions to take at each alert level
  actions:
    warning:
      - log
      - notify
    critical:
      - log
      - notify
      - command:"tempban {player} 1h Suspected X-ray use"

  # Notification settings
  notifications:
    # In-game chat notifications
    in-game: true
    # Console log notifications
    console: true
    # Webhook notifications
    webhook:
      enabled: false
      url: ""
      # Format: "plain" or "discord-embed"
      format: "discord-embed"

  # Statistics storage
  storage:
    # "sqlite" (default) or "mysql"
    type: sqlite
    # MySQL connection settings (only if type=mysql)
    mysql:
      host: localhost
      port: 3306
      database: antixray
      username: ""
      password: ""

# ============================================
# Resource-Pack X-Ray Countermeasures
# ============================================
resource-pack:
  # Whether to force a server resource pack
  force-pack: false

  # URL to the resource pack (must be a direct download link)
  pack-url: ""

  # SHA-1 hash of the resource pack for verification
  pack-hash: ""

  # Whether to kick players who decline the resource pack
  kick-on-decline: true

  # Kick message for players who decline
  kick-message: "This server requires the official resource pack."

  # Whether to prevent joining until the pack is loaded
  delay-join-until-loaded: true

# ============================================
# Per-World Overrides
# ============================================
worlds:
  world_nether:
    engine-mode: 2
    hidden-blocks:
      - nether_gold_ore
      - nether_quartz_ore
      - ancient_debris
      - spawner
      - chest
    max-block-height: 128
    update-radius: 3
  world_the_end:
    enabled: false
```

### 10.3 Runtime Reload Support

The `/antixray reload` command triggers the following sequence:

1. Load `config.yml` from disk
2. Validate all values (see below)
3. Compute new `configHash`
4. If `engine-mode` or `hidden-blocks` changed: clear entire cache (L1 + L2)
5. If `proximity` settings changed: reset per-player deobfuscation tracking
6. If `detection` settings changed: reload detection thresholds (statistics are preserved)
7. If `async` pool-size changed: resize the thread pool (add or remove workers)
8. Log the reload event with a summary of changed settings

**Thread safety during reload:** Configuration is stored in a volatile reference. The async pipeline and event listeners always read the current reference. During reload, a new configuration object is constructed and the reference is swapped atomically. This ensures no thread reads a partially-updated configuration.

### 10.4 Config Validation on Load

On every config load (startup or reload), the following validations are performed:

| Validation | Error Condition | Action |
|---|---|---|
| `engine-mode` | Not 1, 2, or 3 | Log error, fall back to default (3) |
| `hidden-blocks` | Empty list | Log warning — obfuscation does nothing |
| `hidden-blocks` | Invalid material name | Log error for each invalid entry, skip it |
| `fake-ore-chance` | < 0 or > 1 | Log error, fall back to default (0.07) |
| `max-block-height` | < 0 or > 320 | Log error, fall back to default (64) |
| `update-radius` | < 1 or > 16 | Log error, fall back to default (4) |
| `pool-size` | < 0 | Log error, use auto-detect |
| `l1.max-size` | < 100 | Log error, fall back to default (5000) |
| `detection.thresholds` | Warning > critical | Log error, swap values |
| `resource-pack.pack-url` | Non-empty but invalid URL | Log error, disable force-pack |

**Error reporting:** All validation errors are logged at `WARN` level with clear messages indicating the field, the invalid value, and the fallback. A summary count of validation issues is logged at the end of the validation pass.

---

## 11. Permissions & Commands

### 11.1 Permissions

| Permission | Description | Default |
|---|---|---|
| `antixray.bypass` | Skip obfuscation for this player — they see real blocks | `false` (OP only) |
| `antixray.admin` | Access to all admin commands | `op` |
| `antixray.notify` | Receive X-ray detection alerts in chat | `op` |
| `antixray.reload` | Reload configuration via `/antixray reload` | `op` |
| `antixray.stats` | View detection statistics | `op` |
| `antixray.check` | Manual review of player mining patterns | `op` |
| `antixray.mode` | Change engine mode at runtime | `op` |
| `antixray.cache` | Manage the obfuscation cache | `op` |
| `antixray.toggle` | Enable/disable the plugin per world | `op` |

**Permission hierarchy:** `antixray.admin` implies all other `antixray.*` permissions except `antixray.bypass` (bypass must be explicitly granted).

### 11.2 Commands

| Command | Syntax | Description | Permission |
|---|---|---|---|
| `/antixray reload` | `/antixray reload` | Reload configuration from disk | `antixray.reload` |
| `/antixray stats` | `/antixray stats [player]` | View mining statistics for a player (or yourself if no player specified) | `antixray.stats` |
| `/antixray check` | `/antixray check <player>` | Detailed review of a player's mining patterns, ratios, and suspicion level | `antixray.check` |
| `/antixray mode` | `/antixray mode <1\|2\|3> [world]` | Change engine mode at runtime. If world is specified, change only for that world. | `antixray.mode` |
| `/antixray cache clear` | `/antixray cache clear [world]` | Clear the obfuscation cache. If world is specified, clear only that world's cache. | `antixray.cache` |
| `/antixray toggle` | `/antixray toggle [world]` | Enable or disable the plugin for a specific world (or globally if no world specified). Toggles the current state. | `antixray.toggle` |
| `/antixray status` | `/antixray status` | Display current plugin status: engine mode, cache size, queue size, active threads | `antixray.admin` |
| `/antixray timings` | `/antixray timings` | Display performance metrics: average obfuscation time per chunk, cache hit rate, packets modified per tick | `antixray.admin` |

**Tab completion:**

- `/antixray` → subcommand list: `reload`, `stats`, `check`, `mode`, `cache`, `toggle`, `status`, `timings`
- `/antixray stats` → online player names
- `/antixray check` → online player names
- `/antixray mode` → `1`, `2`, `3`
- `/antixray mode <1|2|3>` → loaded world names
- `/antixray cache clear` → loaded world names
- `/antixray toggle` → loaded world names

---

## 12. API Layer

### 12.1 Design Principles

- **Interface-based:** All public API is exposed through interfaces, not implementation classes
- **Event-driven:** Key plugin actions fire Bukkit events that third-party plugins can listen to
- **Backward-compatible:** Once a public API method is released, it will not be removed or have its signature changed in a minor or patch version
- **Minimal:** Only expose what third-party plugins are likely to need

### 12.2 Public API Interface

#### `AntiXrayAPI` (accessed via `AntiXrayPlugin.getAPI()`)

| Method | Return Type | Description |
|---|---|---|
| `isObfuscated(Location)` | `boolean` | Returns whether the block at the given location is currently obfuscated (hidden from players) |
| `getEngineMode(World)` | `int` | Returns the current engine mode (1, 2, or 3) for the given world |
| `isEnabled(World)` | `boolean` | Returns whether the plugin is enabled for the given world |
| `registerCustomHiddenBlock(Material)` | `void` | Registers a custom material to be treated as a hidden block (added to the runtime hidden-blocks set) |
| `unregisterCustomHiddenBlock(Material)` | `void` | Removes a custom material from the runtime hidden-blocks set |
| `getObfuscationProvider(World)` | `ObfuscationProvider` | Returns the obfuscation provider for the given world (for custom obfuscation logic) |

#### `ObfuscationProvider` Interface

| Method | Description |
|---|---|
| `boolean shouldObfuscate(BlockState blockState, World world, int x, int y, int z)` | Called for each block during obfuscation. Return `true` to obfuscate this block, `false` to leave it as-is. Third-party plugins can implement custom logic (e.g., only obfuscate during certain hours, or based on player activity). |
| `Material getReplacementBlock(World world, int y)` | Called to determine what material to use as the replacement for obfuscated blocks at the given Y level. Allows custom replacement logic. |

### 12.3 Events

#### `BlockVisibilityEvent`

| Field | Type | Description |
|---|---|---|
| `player` | `Player` | The player for whom the block is being revealed |
| `location` | `Location` | The block's location |
| `realMaterial` | `Material` | The real material of the block |
| `obfuscatedMaterial` | `Material` | The material that was shown instead (the fake/hidden version) |
| `isCancelled` | `boolean` | If cancelled by a listener, the block will NOT be deobfuscated (stays hidden) |

**Use case:** A PvP plugin might cancel deobfuscation for players in combat (to prevent X-ray-assisted targeting during fights). A territory plugin might only deobfuscate blocks in the player's own territory.

#### `PlayerXraySuspicionEvent`

| Field | Type | Description |
|---|---|---|
| `player` | `Player` | The flagged player |
| `alertLevel` | `AlertLevel` | INFO, WARNING, or CRITICAL |
| `triggeringMetrics` | `Map<String, Double>` | The metrics that triggered this alert (e.g., `ore_to_stone_ratio: 0.18`) |
| `isCancelled` | `boolean` | If cancelled, the configured actions (kick, ban, etc.) are NOT executed. The alert is still logged. |

**Use case:** A custom punishment plugin might cancel the default action and apply its own penalty. A logging plugin might capture this event for a web dashboard.

### 12.4 Versioning Strategy

| Aspect | Policy |
|---|---|
| **Version format** | Semver: `MAJOR.MINOR.PATCH` (e.g., `1.3.2`) |
| **Public API stability** | Public API (`com.antixray.api` package) is guaranteed stable within a MAJOR version. Breaking changes only in MAJOR releases. |
| **Internal API** | All other packages are internal. No stability guarantee. Third-party plugins should not depend on internal classes. |
| **Deprecation** | Deprecated API methods are marked with `@Deprecated` annotation and Javadoc `@deprecated` tag. They remain functional for at least one MINOR version after deprecation before removal in the next MAJOR version. |
| **Event stability** | Events are treated as public API. Fields are additive only (new fields may be added, existing fields are not removed or renamed). |

---

## 13. Performance Considerations

### 13.1 CPU Cost Analysis

| Operation | Cost Per Chunk Section | Cost Per Full Chunk (16 sections) | Notes |
|---|---|---|---|
| **Mode 1: Block iteration + replacement** | ~0.1ms | ~1.6ms | Simple per-block check and index replacement |
| **Mode 2: Block iteration + replacement + random fakes** | ~0.3ms | ~4.8ms | RNG per candidate block, more palette entries |
| **Mode 3: Block iteration + layer fakes** | ~0.15ms | ~2.4ms | RNG per layer (16) instead of per block |
| **Air-exposure check (6 neighbors per block)** | ~0.05ms | ~0.8ms | Array lookup per neighbor, amortized |
| **Palette manipulation + re-encoding** | ~0.05ms | ~0.8ms | Depends on palette size; larger palettes take more time |
| **Total Mode 1** | ~0.2ms | ~3.2ms | Within budget |
| **Total Mode 2** | ~0.4ms | ~6.4ms | Within budget |
| **Total Mode 3** | ~0.25ms | ~4.0ms | Within budget |

**Target:** Obfuscation per chunk section < 0.5ms (all modes are within this target).

### 13.2 Memory Analysis

| Component | Per-Player | 100 Players | Notes |
|---|---|---|---|
| **Revealed blocks set** | ~120 KB | ~12 MB | 10,000 entries × 12 bytes |
| **Detection statistics** | ~10 KB | ~1 MB | Per-player metrics and histograms |
| **L1 cache (shared)** | N/A | ~10 MB | 5000 chunks × 2 KB |
| **Async queue entries** | N/A | ~2 MB | 10,000 tasks × ~200 bytes |
| **NMS adapter state** | N/A | ~5 MB | Version-specific data structures |
| **Total** | ~130 KB | ~30 MB | Acceptable for modern servers |

### 13.3 Network Overhead Analysis

| Metric | Mode 1 | Mode 2 | Mode 3 |
|---|---|---|---|
| **Additional palette entries per section** | 0–1 | 3–8 | 1–5 |
| **Bits-per-entry increase** | 0–1 | 1–2 | 0–1 |
| **Packed array size increase** | 0–8% | 12–25% | 0–12% |
| **Packet size increase per chunk** | 0–5% | 10–30% | 5–15% |
| **Compression impact** | Negligible | Moderate (less compressible) | Low (layer patterns compress well) |

**Target:** Packet overhead < 20% increase. Mode 1 and Mode 3 are within this target. Mode 2 may exceed it in sections with many ore types — the plugin should warn operators if estimated packet overhead exceeds 25%.

### 13.4 I/O Analysis

| Operation | Frequency | Cost | Mitigation |
|---|---|---|---|
| **Config file load** | Startup + reload | <10ms | Negligible |
| **L2 disk cache read** | On L1 miss | ~1–5ms (SSD), ~10–50ms (HDD) | Async reads; SSD recommended |
| **L2 disk cache write** | On new obfuscation result | ~1–5ms (SSD), ~10–50ms (HDD) | Async writes; batched |
| **Detection database read** | On player join | ~1ms (SQLite) | Async |
| **Detection database write** | On block break, periodic save | ~1ms (SQLite) | Batched writes, WAL mode |

### 13.5 Profiling and Monitoring

#### `/antixray timings` Output

```
--- AntiXray Timings (last 5 minutes) ---
Obfuscation:
  Average per chunk: 3.2ms
  Average per section: 0.2ms
  95th percentile per chunk: 5.1ms
  Chunks obfuscated: 12,450
  Cache hit rate: 87.3%

Async Pipeline:
  Queue size: 23 / 10000
  Tasks completed: 12,450
  Tasks timed out: 2
  Tasks dropped (backpressure): 0
  Average wait time: 1.3ms
  Thread pool utilization: 62%

Deobfuscation:
  Block updates sent: 8,920
  Multi-block updates sent: 340
  Average updates per player per tick: 2.1
  Proximity checks per tick: 120

Detection:
  Players tracked: 85
  Active alerts: 2 WARNING, 0 CRITICAL
  Database size: 4.2 MB
```

#### Integration with Paper Timings

The plugin registers custom timings with Paper's timings system (`co.aikar.timings`):

- `AntiXray - Chunk Obfuscation`
- `AntiXray - Deobfuscation`
- `AntiXray - Cache Lookup`
- `AntiXray - Async Processing`
- `AntiXray - Detection Update`

These appear in Paper's `/timings` report, allowing operators to see the plugin's impact on overall server performance.

### 13.6 Benchmarking Targets

| Metric | Target | Stretch Goal |
|---|---|---|
| **Obfuscation per chunk section** | < 0.5ms | < 0.3ms |
| **Obfuscation per full chunk** | < 8ms | < 5ms |
| **Packet overhead increase** | < 20% | < 10% |
| **L1 cache hit rate** | > 80% | > 90% |
| **TPS impact (100 players)** | < 0.5 TPS loss | < 0.2 TPS loss |
| **Login time increase** | < 500ms | < 200ms |
| **Memory overhead** | < 50 MB | < 30 MB |

---

## 14. Compatibility & Dependencies

### 14.1 Server Platforms

| Platform | Support Level | Notes |
|---|---|---|
| **Paper** | Primary | Full NMS integration, async chunk scheduler, Paper-specific optimizations. Best performance and compatibility. |
| **Spigot** | Secondary | Requires ProtocolLib for packet interception. No async chunk pre-loading. Slightly lower performance. |
| **Folia** | Experimental | Requires region task scheduler integration. Some features may not work correctly with Folia's threading model. Community testing needed. |
| **Purpur** | Compatible | Purpur extends Paper; all Paper features work. |
| **CraftBukkit** | Not supported | Lacks necessary APIs. Use Spigot or Paper. |

### 14.2 Minecraft Versions

| Version | Status | Notes |
|---|---|---|
| **1.19.4** | Supported | Baseline version. Chunk packet format stable from this version. |
| **1.20.0–1.20.4** | Supported | Minor packet changes; NMS adapter handles differences. |
| **1.20.5–1.20.6** | Supported | Item component system changes; chunk packet format unchanged. |
| **1.21.0–1.21.4** | Supported | New blocks may need addition to hidden-blocks defaults. |
| **1.21.5+** | Forward-compatible | NMS adapter will be updated as new versions release. |

### 14.3 Dependencies

| Dependency | Required/Optional | Version | Purpose |
|---|---|---|---|
| **ProtocolLib** | Required (Spigot only; optional on Paper) | 5.0+ | Packet interception for non-Paper servers |
| **PlaceholderAPI** | Optional | 2.11+ | Placeholder support in alert messages |
| **Vault** | Optional | 1.7+ | Permission group integration for per-group obfuscation settings |

### 14.4 Known Incompatibilities

| Plugin/Tool | Issue | Mitigation |
|---|---|---|
| **Paper Anti-Xray** (built-in) | Both plugins modify the same chunk packets, causing conflicts | Disable Paper's built-in anti-xray (`paper.yml: anti-xray.enabled: false`) before enabling this plugin. The plugin detects and warns if Paper's anti-xray is active. |
| **Orebfuscator** | Same conflict as Paper Anti-Xray | Use only one anti-xray plugin. The plugin detects and warns if Orebfuscator is present. |
| **Chunk pre-generators** (e.g., Chunky) | Pre-generated chunks are sent to players without going through the plugin's interception if the pre-generator bypasses the normal chunk send path | Most pre-generators work fine because they only generate chunks server-side; players still request chunks normally. Test with specific pre-generator. |
| **Dynmap / BlueMap** | Map rendering reads real chunk data, not obfuscated data. This is BY DESIGN — map plugins should show real terrain. | No conflict. If the map is publicly accessible, ore positions are visible on the map regardless of the plugin. This is a seed-protection issue (see Section 6). |
| **ViaVersion / ViaBackwards** | Protocol translation may interfere with modified chunk packets | Test per version combination. The plugin should operate at a level below ViaVersion's translation. |
| **FastAsyncWorldEdit** | WorldEdit modifies blocks in bulk, which can cause cache invalidation storms | The plugin listens for WorldEdit's block change events and batches invalidations. May cause temporary cache misses in edited areas. |

### 14.5 Multi-Version Strategy

The plugin uses an NMS abstraction layer to handle version differences:

```
NmsAdapter (interface)
├── NmsAdapter_v1_19_R3  (1.19.4)
├── NmsAdapter_v1_20_R1  (1.20.0–1.20.2)
├── NmsAdapter_v1_20_R2  (1.20.3–1.20.4)
├── NmsAdapter_v1_20_R3  (1.20.5–1.20.6)
├── NmsAdapter_v1_21_R1  (1.21.0–1.21.1)
├── NmsAdapter_v1_21_R2  (1.21.2–1.21.3)
└── NmsAdapter_v1_21_R3  (1.21.4+)
```

**Adapter selection:** At startup, the plugin reads the server's package version (e.g., `v1_21_R3`) and loads the matching adapter class via reflection. If no matching adapter is found, the plugin attempts to use the latest available adapter (forward compatibility heuristic) and logs a warning.

**Adapter responsibilities:**

| Method | Description |
|---|---|
| `getChunkSections(PacketContainer)` | Extract chunk section data from the chunk packet |
| `getPalette(ChunkSection)` | Extract the palette entries from a chunk section |
| `getPackedIndices(ChunkSection)` | Extract the packed block index array |
| `setPalette(ChunkSection, List<BlockState>)` | Replace palette entries |
| `setPackedIndices(ChunkSection, long[])` | Replace packed indices |
| `getBlockState(World, int, int, int)` | Get the real block state at a position (for air-exposure checks) |
| `createBlockUpdatePacket(Location, BlockState)` | Construct a Block Update packet with the given block state |
| `createMultiBlockUpdatePacket(ChunkSection, List<Location>)` | Construct an Update Section Blocks packet |

---

## 15. Testing Strategy

### 15.1 Unit Tests

| Test Category | Test Cases | Framework |
|---|---|---|
| **Obfuscation logic** | Mode 1/2/3 block classification; air-exposure check correctness; tile entity handling; dimension-aware replacement; edge cases (all-air section, all-ore section, single-block palette) | JUnit 5 |
| **Palette manipulation** | Add/remove palette entries; remap packed indices; single-value → indirect upgrade; indirect → direct upgrade; palette compaction; bit width changes | JUnit 5 |
| **Cache invalidation** | L1 put/get/evict; L2 async write/read; invalidation on block change; config change full clear; cache key hash consistency | JUnit 5 |
| **Statistics calculations** | Ratio computation; exponential moving average; threshold crossing; false positive with small sample; different play style profiles; grace period enforcement | JUnit 5 |
| **Config validation** | Valid config loads correctly; invalid values fall back to defaults; per-world override merging; deep merge correctness; missing optional fields default correctly | JUnit 5 |
| **Deobfuscation logic** | Radius check; frustum culling; raycast line-of-sight; re-obfuscation delay; chunk border handling; per-player isolation | JUnit 5 |

**Mocking strategy:** NMS classes are mocked using Mockito. The `NmsAdapter` interface allows all engine logic to be tested without a running Minecraft server.

### 15.2 Integration Tests

| Test Category | Test Cases | Environment |
|---|---|---|
| **Chunk packet interception** | Verify that chunk packets are intercepted and modified correctly on Paper; verify ProtocolLib interception works; verify no packets are lost | Test server (Paper + ProtocolLib) |
| **Deobfuscation flow** | Player approaches hidden block → block is revealed; player leaves → block is re-obfuscated; block break reveals neighbors; explosion reveals area | Test server with bot player |
| **Multi-world** | Different configs per world; world change resets deobfuscation; nether portal transition | Test server with multiple worlds |
| **Permission bypass** | Player with `antixray.bypass` sees real blocks; player without permission sees obfuscated blocks | Test server with permission plugin |
| **Cache coherency** | Block change → cache invalidated → next chunk request re-obfuscates; L2 survives restart; cache hit rate under simulated load | Test server with automated block placement/breaking |

### 15.3 Performance Tests

| Test Category | Metric | Method |
|---|---|---|
| **Obfuscation throughput** | Chunks/second on each engine mode | Microbenchmark (JMH or custom) on isolated chunk sections |
| **Cache hit rate** | Percentage under realistic load | Simulated player movement patterns, measure L1/L2 hit/miss ratio |
| **Memory usage** | Heap consumption per player, cache size scaling | JVM monitoring (JMX, VisualVM) under 100 simulated players |
| **TPS impact** | TPS with/without plugin under load | Test server with 100 simulated players (Spark plugin for profiling) |
| **Packet size overhead** | Average packet size increase per mode | Packet capture (Wireshark or ProtocolLib packet logging) |

### 15.4 Manual Testing

| Scenario | Method | Pass Criteria |
|---|---|---|
| **Mod-based X-ray client** | Install X-ray mod on test client; connect to server with plugin; attempt to locate ores | Hidden ores are not visible; fake ores appear in Mode 2/3 |
| **Resource-pack X-ray** | Apply X-ray resource pack on test client; connect to server | With air-obfuscation: fake ores visible; without: only air-exposed ores visible (expected) |
| **Normal gameplay** | Play normally without X-ray; mine, explore, build | No visible artifacts; no FPS drops (except with air-obfuscation); no block flickering; no desync between client and server |
| **Proximity deobfuscation** | Walk toward a known hidden ore; observe when it becomes visible | Ore appears within `update-radius` blocks; no "pop-in" from far away |
| **Forced resource pack** | Configure forced pack; join server; decline pack | Player is kicked with configured message; accepting pack joins normally |

### 15.5 Test Server Setup

A Dockerized Paper server is used for automated integration and performance testing:

```
test-server/
├── docker-compose.yml
├── Dockerfile
├── server/
│   ├── paper.jar
│   ├── spigot.yml
│   ├── server.properties
│   └── plugins/
│       ├── AntiXray.jar (symlink to build output)
│       └── ProtocolLib.jar
└── test-world/
    └── region/  (pre-generated test world with known ore positions)
```

**CI integration:** GitHub Actions runs unit tests on every push. Integration tests run on PR merge to `main`. Performance benchmarks run on release branch creation.

---

## 16. Build & Release

### 16.1 Build System

| Aspect | Specification |
|---|---|
| **Build tool** | Gradle (Kotlin DSL preferred) |
| **Shadow plugin** | `com.github.johnrengelman.shadow` — produces a fat JAR with relocated dependencies |
| **Dependency relocation** | Relocate `com.google`, `org.apache`, and other bundled dependencies under `com.antixray.lib` to avoid conflicts with other plugins |
| **Java toolchain** | Java 17 (compile and target) |
| **Encoding** | UTF-8 |
| **Build output** | `build/libs/AntiXray-{version}.jar` |

### 16.2 Multi-Version Compilation

The plugin must work across multiple Minecraft/NMS versions. Strategy:

| Approach | Description |
|---|---|
| **Single JAR with reflection** | All NMS adapter code is included in one JAR. The correct adapter is selected at runtime via reflection. This avoids the complexity of multiple JAR builds. |
| **Version-specific modules** | NMS adapters for each version are in separate source sets (`src/main/java/com/antixray/nms/v1_19/`, `v1_20/`, etc.). They are compiled against the corresponding server JAR. |
| **Build configuration** | Each NMS version has a Gradle dependency on the corresponding Paper/Spigot weight JAR. The Shadow plugin bundles all adapters into a single output JAR. |

**Dependency management:**

```kotlin
// build.gradle.kts (simplified)
dependencies {
    // Core API (platform-independent)
    implementation("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")

    // NMS adapters (compile-only, version-specific)
    compileOnly("io.papermc.paper:paper-weight:1.19.4")
    compileOnly("io.papermc.paper:paper-weight:1.20.4")
    compileOnly("io.papermc.paper:paper-weight:1.21.4")

    // Optional dependencies
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.milkbowl:vault:1.7.1")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
}
```

### 16.3 CI Pipeline

```yaml
# .github/workflows/build.yml
name: Build & Test
on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build
        run: ./gradlew shadowJar
      - name: Test
        run: ./gradlew test
      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: AntiXray
          path: build/libs/AntiXray-*.jar

  release:
    if: startsWith(github.ref, 'refs/tags/')
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: build/libs/AntiXray-*.jar
```

### 16.4 Release Channels

| Channel | URL | Update Frequency | Audience |
|---|---|---|---|
| **SpigotMC** | `spigotmc.org/resources/anti-xray.XXXXX` | Stable releases only | Spigot/Paper server operators |
| **Modrinth** | `modrinth.com/plugin/antixray` | Stable + beta releases | Modern server operators |
| **Hangar** | `hangar.papermc.io/AntiXray` | Stable + beta releases | Paper-specific community |
| **GitHub Releases** | `github.com/org/antixray/releases` | All releases (including pre-releases) | Developers and advanced users |

### 16.5 Versioning

| Version Change | When | Example |
|---|---|---|
| **MAJOR** | Breaking API change; major architecture change; Minecraft version support dropped | 1.x → 2.0 |
| **MINOR** | New feature; new engine mode; new detection metric; new Minecraft version support | 1.3 → 1.4 |
| **PATCH** | Bug fix; performance improvement; config default change | 1.4.1 → 1.4.2 |

---

## 17. Project Structure

```
anti-xray/
├── build.gradle                  # Gradle build script (Kotlin DSL)
├── settings.gradle               # Gradle settings (project name, plugin repos)
├── gradle.properties             # Version properties, dependency versions
├── src/
│   ├── main/
│   │   ├── java/com/antixray/
│   │   │   ├── AntiXrayPlugin.java        # Main plugin class (JavaPlugin)
│   │   │   │                              #   - onEnable: load config, init NMS, start threads
│   │   │   │                              #   - onDisable: shutdown threads, flush cache, save data
│   │   │   │                              #   - Provides singleton access to all managers
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── ConfigurationManager.java  # Load, validate, reload config
│   │   │   │   ├── WorldConfig.java           # Per-world merged config
│   │   │   │   └── ConfigValidator.java       # Validation logic with error reporting
│   │   │   │
│   │   │   ├── engine/
│   │   │   │   ├── ObfuscationEngine.java     # Main engine: dispatches to mode-specific impl
│   │   │   │   ├── ObfuscationMode.java       # Enum: MODE_1, MODE_2, MODE_3
│   │   │   │   ├── Mode1Engine.java           # Simple replacement implementation
│   │   │   │   ├── Mode2Engine.java           # Random fake ore injection implementation
│   │   │   │   ├── Mode3Engine.java           # Layer-based obfuscation implementation
│   │   │   │   ├── BlockClassifier.java       # Classifies blocks: HIDDEN, AIR_EXPOSED, etc.
│   │   │   │   ├── PaletteManipulator.java    # Palette entry and index manipulation
│   │   │   │   └── AirExposureChecker.java    # 6-face neighbor transparency check
│   │   │   │
│   │   │   ├── deobfuscation/
│   │   │   │   ├── DeobfuscationManager.java  # Orchestrates deobfuscation events & proximity
│   │   │   │   ├── ProximityTracker.java      # Per-player distance-based tracking
│   │   │   │   ├── VisibilityResolver.java    # Frustum + raycast checks
│   │   │   │   ├── UpdatePacketBuilder.java   # Constructs block/multi-block update packets
│   │   │   │   └── RevealedBlocksSet.java     # Per-player set of deobfuscated positions
│   │   │   │
│   │   │   ├── cache/
│   │   │   │   ├── ObfuscationCache.java      # Cache facade: L1 lookup, L2 fallback
│   │   │   │   ├── L1MemoryCache.java         # In-memory LRU cache (Caffeine-based)
│   │   │   │   ├── L2DiskCache.java           # Region-file-based persistent cache
│   │   │   │   ├── CacheKey.java              # Composite key: world, coords, mode, configHash
│   │   │   │   └── CacheEntry.java            # Obfuscated palette + packed indices + timestamp
│   │   │   │
│   │   │   ├── async/
│   │   │   │   ├── AsyncProcessor.java        # Main async pipeline: enqueue, prioritize, execute
│   │   │   │   ├── ObfuscationTask.java       # Task wrapper: chunk coords, priority, callback
│   │   │   │   ├── ThreadPoolManager.java     # Thread pool lifecycle and sizing
│   │   │   │   ├── BackpressureHandler.java   # Queue overflow and circuit breaker logic
│   │   │   │   └── TickBudgetTracker.java     # Per-tick time budget enforcement
│   │   │   │
│   │   │   ├── detection/
│   │   │   │   ├── DetectionEngine.java       # Main detection: stats collection, threshold eval
│   │   │   │   ├── PlayerStatistics.java      # Per-player metrics: ratios, patterns, histograms
│   │   │   │   ├── AlertManager.java          # Alert dispatch: chat, console, webhook
│   │   │   │   ├── ActionExecutor.java        # Execute configured actions (kick, ban, command)
│   │   │   │   ├── StatisticsStorage.java     # SQLite/MySQL persistence
│   │   │   │   └── PlayStyleClassifier.java   # Caving vs branch mining classification
│   │   │   │
│   │   │   ├── packet/
│   │   │   │   ├── PacketInterceptor.java     # Interface for packet interception
│   │   │   │   ├── NmsInterceptor.java        # Paper NMS-based interception
│   │   │   │   └── ProtocolLibInterceptor.java # ProtocolLib-based interception
│   │   │   │
│   │   │   ├── nms/
│   │   │   │   ├── NmsAdapter.java            # Version-independent NMS interface
│   │   │   │   ├── NmsAdapterFactory.java     # Creates adapter for current server version
│   │   │   │   ├── v1_19/
│   │   │   │   │   └── NmsAdapter_v1_19_R3.java
│   │   │   │   ├── v1_20/
│   │   │   │   │   ├── NmsAdapter_v1_20_R1.java
│   │   │   │   │   ├── NmsAdapter_v1_20_R2.java
│   │   │   │   │   └── NmsAdapter_v1_20_R3.java
│   │   │   │   └── v1_21/
│   │   │   │       ├── NmsAdapter_v1_21_R1.java
│   │   │   │       ├── NmsAdapter_v1_21_R2.java
│   │   │   │       └── NmsAdapter_v1_21_R3.java
│   │   │   │
│   │   │   ├── api/
│   │   │   │   ├── AntiXrayAPI.java           # Public API entry point
│   │   │   │   ├── ObfuscationProvider.java   # Custom obfuscation logic interface
│   │   │   │   ├── BlockVisibilityEvent.java  # Event: block deobfuscated for player
│   │   │   │   └── PlayerXraySuspicionEvent.java # Event: player flagged by detection
│   │   │   │
│   │   │   ├── commands/
│   │   │   │   ├── AntiXrayCommand.java       # Root command handler + tab completer
│   │   │   │   ├── ReloadCommand.java         # /antixray reload
│   │   │   │   ├── StatsCommand.java          # /antixray stats [player]
│   │   │   │   ├── CheckCommand.java          # /antixray check <player>
│   │   │   │   ├── ModeCommand.java           # /antixray mode <1|2|3> [world]
│   │   │   │   ├── CacheCommand.java          # /antixray cache clear [world]
│   │   │   │   ├── ToggleCommand.java         # /antixray toggle [world]
│   │   │   │   ├── StatusCommand.java         # /antixray status
│   │   │   │   └── TimingsCommand.java        # /antixray timings
│   │   │   │
│   │   │   ├── permissions/
│   │   │   │   └── PermissionConstants.java   # All permission node constants
│   │   │   │
│   │   │   ├── listener/
│   │   │   │   ├── BlockEventListener.java    # Block break, place, physics, piston
│   │   │   │   ├── PlayerEventListener.java   # Join, quit, world change, move, teleport
│   │   │   │   ├── ExplosionEventListener.java # Entity explode events
│   │   │   │   └── WorldEventListener.java    # World load, unload
│   │   │   │
│   │   │   └── util/
│   │   │       ├── MaterialSet.java           # Pre-computed sets of block categories
│   │   │       ├── BlockPosition.java         # Immutable block coordinate
│   │   │       ├── Frustum.java               # View frustum computation
│   │   │       ├── Raycast.java               # DDA/Bresenham ray-cast implementation
│   │   │       ├── SeededRandom.java          # Deterministic RNG from chunk coords + salt
│   │   │       └── VersionUtil.java           # Server version detection utilities
│   │   │
│   │   └── resources/
│   │       ├── plugin.yml                     # Plugin metadata, commands, permissions
│   │       ├── config.yml                     # Default configuration (full, with comments)
│   │       └── languages/
│   │           ├── en.yml                     # English messages
│   │           ├── es.yml                     # Spanish messages
│   │           ├── de.yml                     # German messages
│   │           ├── fr.yml                     # French messages
│   │           ├── ja.yml                     # Japanese messages
│   │           ├── zh.yml                     # Chinese messages
│   │           └── ko.yml                     # Korean messages
│   │
│   └── test/
│       └── java/com/antixray/
│           ├── engine/
│           │   ├── Mode1EngineTest.java
│           │   ├── Mode2EngineTest.java
│           │   ├── Mode3EngineTest.java
│           │   ├── BlockClassifierTest.java
│           │   ├── PaletteManipulatorTest.java
│           │   └── AirExposureCheckerTest.java
│           ├── cache/
│           │   ├── L1MemoryCacheTest.java
│           │   ├── L2DiskCacheTest.java
│           │   └── CacheInvalidationTest.java
│           ├── detection/
│           │   ├── DetectionEngineTest.java
│           │   ├── PlayerStatisticsTest.java
│           │   └── PlayStyleClassifierTest.java
│           ├── deobfuscation/
│           │   ├── ProximityTrackerTest.java
│           │   ├── VisibilityResolverTest.java
│           │   └── RevealedBlocksSetTest.java
│           └── config/
│               ├── ConfigurationManagerTest.java
│               └── ConfigValidatorTest.java
│
├── docs/
│   ├── BLUEPRINT.md               # This document
│   ├── CONFIGURATION.md           # Detailed configuration guide for operators
│   ├── API.md                     # Developer API documentation
│   └── COMPATIBILITY.md           # Platform and version compatibility matrix
│
└── .github/
    └── workflows/
        └── build.yml              # CI pipeline
```

---

## 18. Implementation Phases

### Phase 1: Core Engine

| Aspect | Detail |
|---|---|
| **Scope** | Obfuscation modes 1–3, basic packet interception (NMS + ProtocolLib), configuration system, NMS abstraction layer for 1.19.4–1.21.x |
| **Dependencies** | None (first phase) |
| **Deliverables** | Functional chunk obfuscation on chunk send. Three engine modes operational. Config file loads and validates. NMS adapter selects correctly for server version. Basic `/antixray reload` command. |
| **Estimated Effort** | Large — this is the foundation. NMS adapters are the most labor-intensive component. |
| **Key Risks** | NMS version differences may require more adapters than anticipated. Palette manipulation edge cases. |

### Phase 2: Deobfuscation

| Aspect | Detail |
|---|---|
| **Scope** | Proximity deobfuscation system, block update event listeners, block physics event handling, per-player revealed-blocks tracking, frustum culling (optional), ray-cast line-of-sight (optional), re-obfuscation with configurable delay |
| **Dependencies** | Phase 1 (obfuscation engine must be functional to test deobfuscation) |
| **Deliverables** | Blocks revealed on proximity and interaction. Block updates sent via appropriate packet type. Per-player tracking operational. Edge cases handled (teleport, world change, login). |
| **Estimated Effort** | Medium-Large — proximity system is complex but well-defined. |
| **Key Risks** | Packet spam from excessive deobfuscation updates. Frustum/raycast performance. |

### Phase 3: Cache & Async

| Aspect | Detail |
|---|---|
| **Scope** | L1 in-memory LRU cache, L2 disk cache (region-file format), async thread pool with priority queue, per-tick budget enforcement, backpressure handling, chunk timeout |
| **Dependencies** | Phase 1 (cache stores obfuscation results), Phase 2 (cache invalidation triggered by deobfuscation events) |
| **Deliverables** | Cache hit rate > 80% under normal load. Async pipeline prevents main-thread stalls. Backpressure gracefully degrades under extreme load. Disk cache survives restarts. |
| **Estimated Effort** | Large — L2 disk cache is complex. Backpressure and budget enforcement require careful tuning. |
| **Key Risks** | Cache coherency bugs (stale entries after block changes). Disk cache corruption. Thread pool sizing. |

### Phase 4: Detection

| Aspect | Detail |
|---|---|
| **Scope** | Per-player mining statistics tracking, ratio and pattern analysis, alert system (chat, console, webhook), configurable actions, SQLite persistence, play style classification, false positive mitigation |
| **Dependencies** | Phase 1 (needs block break events which also trigger deobfuscation). Can be developed semi-independently. |
| **Deliverables** | Detection module flags suspicious players. Alerts sent to admins. Statistics persisted to SQLite. False positive rate within acceptable bounds. |
| **Estimated Effort** | Medium — statistics tracking is straightforward; threshold tuning requires real-world data. |
| **Key Risks** | Threshold calibration requires real player data (not available during development). May need iterative tuning post-release. |

### Phase 5: Resource-Pack Countermeasures

| Aspect | Detail |
|---|---|
| **Scope** | Air in hidden-blocks support (engine modes 2/3), forced server resource pack configuration, resource pack URL/hash management, kick-on-decline handling |
| **Dependencies** | Phase 1 (air obfuscation is a variant of Mode 2/3) |
| **Deliverables** | Air obfuscation operational. Forced resource pack works. Configuration guide documents tradeoffs. |
| **Estimated Effort** | Small-Medium — most infrastructure exists from Phase 1. |
| **Key Risks** | FPS impact of air obfuscation may be unacceptable for some servers. Forced resource pack URL availability. |

### Phase 6: API & Commands

| Aspect | Detail |
|---|---|
| **Scope** | Public API interface (`AntiXrayAPI`, `ObfuscationProvider`), custom events (`BlockVisibilityEvent`, `PlayerXraySuspicionEvent`), full command set with tab completion, permissions system, `/antixray stats`, `/antixray check`, `/antixray timings`, `/antixray status` |
| **Dependencies** | Phases 1–4 (API exposes functionality from all prior phases) |
| **Deliverables** | Stable public API. All commands operational with tab completion. Permission nodes enforced. Events fired at appropriate points. |
| **Estimated Effort** | Medium — API design requires careful thought about backward compatibility. Commands are straightforward. |
| **Key Risks** | API design decisions are hard to reverse. Must get right on first attempt. |

### Phase 7: Polish & Release

| Aspect | Detail |
|---|---|
| **Scope** | i18n message files (7 languages initially), full documentation (CONFIGURATION.md, API.md, COMPATIBILITY.md), performance tuning and benchmarking, multi-version testing (1.19.4–1.21.x), Folia compatibility testing, release to SpigotMC/Modrinth/Hangar/GitHub |
| **Dependencies** | All prior phases |
| **Deliverables** | Production-ready plugin JAR. Complete documentation. Published on all release channels. |
| **Estimated Effort** | Medium — mostly non-code work (docs, testing, publishing). |
| **Key Risks** | Undiscovered bugs in less-tested NMS versions. Documentation gaps. Release process issues. |

---

## 19. Risk Assessment

### 19.1 Technical Risks

| Risk | Severity | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| **NMS version fragility** | High | High | Plugin breaks on new Minecraft version | NMS abstraction layer isolates version-specific code. Proactive testing against snapshots. Rapid patch releases for breaking changes. |
| **Palette manipulation bugs** | High | Medium | Corrupted chunk packets → client crash or visual glitches | Exhaustive unit tests for all palette types. Fuzz testing with random chunk data. Fallback: if palette manipulation fails, log error and send unmodified packet. |
| **Performance under load** | Medium | Medium | TPS drops, login delays, player complaints | Per-tick budget enforcement. Async pipeline with backpressure. Circuit breaker for overload. Configurable pool size. Benchmarking targets. |
| **Cache coherency bugs** | High | Medium | Stale obfuscated data sent to players (ores appear as stone after mining) | Strict invalidation on all block change events. Cache key includes configHash. Periodic integrity checks (sample cache entries against real world state). |
| **Thread safety issues** | High | Medium | ConcurrentModificationException, data corruption, deadlocks | All shared state uses concurrent data structures. Read/write locks for L2 disk cache. Extensive multi-threaded testing. |
| **Deobfuscation packet spam** | Medium | High | Network congestion, client FPS drops from rapid block updates | Batching: combine updates into multi-block packets. Rate limiting: max N updates per player per tick. Adaptive: reduce update frequency if network queue is full. |
| **Folia incompatibility** | Low | High | Plugin does not work on Folia servers | Folia support marked as experimental. Clear documentation of Folia limitations. Community testing and bug reports. |

### 19.2 Security Risks

| Risk | Severity | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| **World seed leakage** | Critical | Medium | Complete bypass of obfuscation via external ore calculation | Seed protection recommendations (Section 6). Feature seed randomization. Detect and warn about common leak vectors. |
| **X-ray bypass via new Minecraft mechanics** | Medium | Low | New block types or rendering features expose previously hidden information | Monitor Minecraft changelogs. Update hidden-blocks defaults for new versions. Rapid patch releases. |
| **Permission escalation** | Low | Low | Non-admin players gain `antixray.bypass` and see real blocks | Standard Bukkit permission system. No custom permission logic. Operators must use a permission plugin for fine-grained control. |
| **Cache data exposure** | Low | Very Low | L2 disk cache files contain obfuscated chunk data that could be analyzed | Disk cache stores obfuscated (not real) data — no security risk. If an attacker can read server files, they have direct world access anyway. |
| **Statistical detection gaming** | Medium | Medium | Sophisticated X-ray users adjust behavior to stay below detection thresholds | Multiple independent metrics (hard to game all simultaneously). Periodic threshold adjustment. Manual admin review via `/antixray check`. |

### 19.3 Operational Risks

| Risk | Severity | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| **Config misconfiguration** | Medium | High | Plugin behaves unexpectedly (e.g., hides all blocks, reveals all blocks) | Comprehensive config validation with clear error messages. Fallback to safe defaults for invalid values. `/antixray status` shows effective config. |
| **Dependency conflicts** | Medium | Medium | ProtocolLib version mismatch → packet interception fails | Declare minimum ProtocolLib version. Test against multiple ProtocolLib versions. Graceful fallback to NMS mode on Paper. |
| **Server crashes from plugin** | Critical | Low | Server becomes unstable or crashes | Defensive coding: all NMS calls wrapped in try-catch. Fallback: send unmodified packets on error. Circuit breaker disables plugin on repeated failures. |
| **Disk space exhaustion (L2 cache)** | Medium | Medium | Server runs out of disk space | Configurable disk budget per world. Oldest regions auto-deleted. Warning logged at 80% budget. |
| **Upgrade incompatibility** | Medium | High | Plugin version upgrade changes behavior or breaks config | Backward-compatible config loading (missing keys use defaults, not errors). Migration logic for major config format changes. Release notes document breaking changes. |
| **Other anti-xray plugin conflict** | High | Medium | Double obfuscation causes severe packet corruption or client crashes | Detect known conflicting plugins at startup. Refuse to enable if Paper Anti-Xray is active. Log prominent warnings for other anti-xray plugins. |

---

## 20. Open Questions & Future Considerations

### 20.1 Items Needing Further Research or Design Decisions

| Item | Description | Decision Needed By |
|---|---|---|
| **Proximity cache strategy** | Should the base-cache-plus-overlay approach be implemented in Phase 3, or should a simpler per-player cache be used initially and optimized later? | Phase 3 design |
| **Ray-cast algorithm selection** | DDA vs. Bresenham vs. voxel traversal (Amanatides & Woo) — which provides the best performance/accuracy tradeoff for the deobfuscation use case? | Phase 2 implementation |
| **Detection threshold calibration** | What threshold values produce acceptable false-positive rates (<1%) on real servers? Requires data from Phase 4 deployment. | Post-Phase 4 tuning |
| **L2 cache file format** | Should the disk cache use a custom region-file format, or adopt Minecraft's Anvil format directly? Custom format is simpler but Anvil format is well-tested. | Phase 3 design |
| **Folia threading model** | Does Folia's region scheduling allow sufficient flexibility for the async pipeline, or does it impose restrictions that require a fundamentally different architecture? | Phase 7 testing |
| **ProtocolLib vs. NMS performance gap** | Quantify the exact performance difference between NMS-level interception and ProtocolLib interception. Is the gap significant enough to justify the maintenance cost of dual-mode? | Phase 1 benchmarking |
| **Chunk packet modification vs. reconstruction** | Is it more efficient to modify the existing packet in-place (NMS) or deserialize → modify → reserialize (ProtocolLib)? This affects the architecture of the packet layer. | Phase 1 design |
| **Mode 3 layer selection algorithm** | Should the per-layer fake block be selected uniformly at random, or weighted based on the real ore distribution in that Y-level? Weighted selection may produce more realistic-looking fake ore patterns. | Phase 1 design |
| **Air obfuscation performance** | What is the actual client FPS impact of adding `air` to hidden-blocks? Need empirical data from multiple client hardware configurations. | Phase 5 testing |
| **Webhook alert format** | Should the plugin support Discord-specific embeds, Slack-specific formatting, or generic JSON webhooks? | Phase 4 implementation |

### 20.2 Future Features (Post-1.0)

| Feature | Description | Priority |
|---|---|---|
| **Real-time admin map overlay** | Web-based map showing which blocks players have deobfuscated, highlighting suspicious mining patterns in real-time. Requires a web server component. | Low |
| **Machine learning detection** | Train a model on labeled datasets of X-ray vs. legitimate mining behavior. Could significantly improve detection accuracy and reduce false positives. Requires large dataset collection. | Low |
| **Per-block-type obfuscation toggle** | Allow operators to specify different obfuscation modes per block type (e.g., Mode 1 for coal, Mode 2 for diamonds). Increases config complexity but provides fine-grained control. | Medium |
| **Obfuscation profiling per world** | Automatically analyze a world's ore distribution and recommend optimal engine mode and hidden-blocks configuration. | Low |
| **BungeeCord/Velocity detection sharing** | Share detection statistics across servers in a proxy network. A player flagged on one server is flagged on all. Requires MySQL backend. | Medium |
| **Client mod detection heuristics** | Analyze client behavior patterns to detect known X-ray mods (e.g., specific movement patterns, inventory management patterns). | Low |
| **Chunk pre-encryption** | Encrypt chunk packet data to prevent packet sniffing by external tools. Requires client-side mod to decrypt — breaks vanilla compatibility. | Very Low (likely not viable) |
| **Adaptive obfuscation** | Dynamically adjust obfuscation intensity based on server load. Use Mode 1 during high-load periods, Mode 3 during normal load, Mode 2 during low-load periods. | Medium |
| **Spectator-mode awareness** | Detect when a player switches to spectator mode and immediately re-obfuscate all blocks for that player. | Low |
| **Packet-level bandwith monitoring** | Track per-player chunk data bandwidth to detect anomalous download patterns (e.g., a player requesting chunks at an unusually high rate may be using an external tool to map the world). | Low |

### 20.3 Minecraft Protocol Changes That May Affect the Plugin

| Change | Risk | Impact | Preparedness |
|---|---|---|---|
| **Chunk packet format change** | Medium | NMS adapters must be rewritten; ProtocolLib may abstract the change | NMS abstraction layer designed to absorb format changes |
| **New palette encoding** | Low | Palette manipulation logic must be updated | PaletteManipulator is isolated and testable |
| **Block state ID reassignment** | Low | Hidden-blocks and replacement-blocks must be updated with new IDs | The plugin uses material names (not IDs) in config; NMS adapter handles ID mapping |
| **New chunk packet type** | Low | New packet must be intercepted in addition to 0x27 | Packet layer designed to add new packet types |
| **Removal of Block Update packet** | Very Low | Deobfuscation must use alternative packet type | UpdatePacketBuilder can be modified to use different packets |
| **Encryption or compression changes** | Very Low | Packet interception may need to operate at a different layer | ProtocolLib handles encryption/compression; NMS mode operates before encryption |
| **Multi-dimensional chunk loading** | Low | If the server sends chunks for multiple dimensions in one packet, the interception logic must handle dimension context | Currently dimension is inferred from the player's world; monitor for changes |
| **Chunk data caching on client** | Medium | If the client caches chunk data more aggressively, deobfuscation packets may be ignored by the client | Test with each major version; adjust deobfuscation timing if needed |

---

*End of Blueprint Document*

*This document is a living artifact and should be updated as design decisions are finalized, implementation progresses, and new information emerges from testing and real-world deployment.*
