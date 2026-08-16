# World Crafting-Station Block Plan

> Status: design (issue #137). User decisions 2026-08-16: one block variant
> per mechanism recipe, styled like the original block with the Auto Storage
> house style; placed at the original interact position (bottom base
> required, e.g. bedrock for Flux); fixed 1 input -> 1 output per terminal
> operation with batch crafting via the existing batch controls; world block
> is a second station-installation source sharing the MachineDescriptor
> availability check; low-frequency detection to avoid performance cost.

## Problem

Mechanisms whose conversion is deterministic but gated on world structure
(FluxNetworks Flux Recipe: left-click obsidian with bedrock/flux-block base
+ redstone ItemEntitys -> flux dust 1:1) are fail-closed today. The world
structure is the only blocker; the conversion itself is fixed and safe.

## Design

### 1. Block variants (one per mechanism recipe)

Each supported mechanism gets its own Auto Storage block variant, e.g.
`auto_storage:flux_station`. Block identity (not NBT) encodes the family so
the availability check is a cheap block-state lookup and Block Metadata
(blockstate/registry) carries the mapping.

- Registration: one `MachineDescriptor` per variant (category PROCESS or a
  new category), same registry flow as installable stations.
- Visual: base texture of the original mechanism block (obsidian for Flux)
  with the Auto Storage unified frame/trim style, so it reads as "Auto
  Storage's version of this station". Textures are datagen-owned assets.
- Placement rule mirrors the original interact position: the block replaces
  the block the player would interact with (Flux: the obsidian position),
  and the original base requirement applies (Flux: two below must be
  bedrock; the original code also accepts flux_block — keep the original
  condition set unless the user says otherwise).

### 2. Detection: server-side placement registry (low frequency)

- A server-side `WorldStationRegistry` per dimension key:
  `Map<ResourceKey<Level>, Set<BlockPos>>`.
- `BlockEvent.EntityPlaceEvent` / `BlockEvent.BreakEvent` (and chunk
  unload/load persistence) update the set; lookup is O(1) memory, no
  per-tick or per-open world scan.
- Persistence: positions are saved to the world's Auto Storage data
  (`auto_storage` NBT per dimension) so a server restart restores the set
  without scanning chunks; a placed station works even when its chunk is
  unloaded (same semantics as "installed").
- Availability: `isStationAvailable` consults the registry for the
  descriptor's variant block when the station has no installable-variant
  item (or in addition to it); sharing the existing MachineDescriptor check
  means the Craftable gate, Transform targets, and station display all work
  unchanged.

### 3. Execution semantics

Fixed 1 input -> 1 output per terminal operation (redstone 1 -> flux dust
1); batch count comes from the existing terminal batch controls. Execution
consumes from storage and deposits into storage in the existing
simulate-then-commit transaction (no world mutation at craft time).

- Represented through the existing recipe-family/transform API with a
  custom deterministic resolver (no runtime reflection; contract-style
  fixed outputs). Whether it surfaces as a Transform target or a Craftable
  family depends on the item->item shape (Flux is item->item, so a
  deterministic-resource family with a fixed plan is the fit; Transform
  stays for item->stored-resource).

### 4. Failure modes

- Absent block (never placed / broken) -> station unavailable -> recipe
  fail-closed.
- Wrong base (no bedrock below) -> block still places but the station does
  not register as available (mirror the original condition).
- Registry empty after world wipe -> nothing installed.

### 5. Contract & docs

- FluxNetworks contract gains one accepted family (redstone -> flux dust,
  fixed 1:1) with evidence tied to the new GameTests; the world-mechanic
  risk stays recorded but is now bounded by the station block.
- `docs/notes.md` re-audit entry updated; new compatibility doc section.

## Test plan (GameTests)

1. Place station block on bedrock -> family supported, craft executes
   (storage redstone -> storage dust), base/first station remains.
2. Break the station block -> fail-closed.
3. Place without base -> station not available (fail-closed).
4. Batch craft through the terminal batch controls (e.g. 64 redstone ->
   64 dust) in one transaction.
5. Server-restart persistence: placed station still available after
   reload without scanning the world.
6. Registry eviction on block break + dimension isolation.

## Open items

- Whether the original Flux condition also accepts `flux_block` as base
  (keep original set, default yes) or only bedrock per user's wording.
- Exact house-style trim for the variant textures (needs a visual pass in
  the Prism GUI checklist before release).
- Which mechanism gets the second variant (Botania world-consumption
  flowers re-evaluated per recipe after Flux lands).
