# Out-of-Chunk Core Storage Repository Design

## Status

Approved in conversation on 2026-07-17. This design intentionally provides no migration from the current inline `StorageCoreBlockEntity` payload or the current full-snapshot `CoreRecoverySavedData` format because the mod is still in early development and existing development worlds may be discarded.

Implementation must still follow strict TDD. This document fixes the behavior and persistence contracts; it does not authorize a compatibility fallback.

## Problem

`StorageCoreBlockEntity.saveAdditional` currently writes the complete network identity, energy pools, descriptor state, installed machines, unresolved add-on data, and every stored item type into the block entity tag. Minecraft stores that block entity tag in its owning chunk, so the chunk grows with the Core inventory. The recovery system avoids putting the same payload on the dropped item, but `CoreRecoverySavedData` still creates a second full NBT snapshot whenever a populated Core is dismantled.

This has three unacceptable consequences:

- a high-type-count network makes one chunk disproportionately large;
- dismantling temporarily duplicates the complete persistent payload;
- a missing or corrupt payload can be mistaken for an empty Core unless every lookup fails closed.

The new design makes the Core block entity a fixed-size reference and moves the durable payload into server-owned world data.

## Research basis

The design deliberately combines two independently useful patterns instead of copying either mod wholesale.

### Applied Energistics 2

AE2 1.21.1 stores a cell inventory in the cell ItemStack's `STORAGE_CELL_INV` data component, limits a basic cell inventory to 63 types, and lets a Drive persist only its bounded set of cell ItemStacks. Cell contents are encoded when Minecraft persists the stack rather than rewriting serialized NBT for every inventory mutation. AE2 also uses fault-tolerant codecs so unavailable content can remain represented instead of silently becoming an empty cell.

Relevant upstream source:

- [`BasicCellInventory` type bound and persistent component](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/fd8b717a405672ce4f65ba540f1db8c91317daa4/src/main/java/appeng/me/cells/BasicCellInventory.java#L55-L89)
- [`AEComponents.STORAGE_CELL_INV`](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/fd8b717a405672ce4f65ba540f1db8c91317daa4/src/main/java/appeng/api/ids/AEComponents.java#L248-L253)
- [`DriveBlockEntity` persistence of its cell stacks](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/fd8b717a405672ce4f65ba540f1db8c91317daa4/src/main/java/appeng/blockentity/storage/DriveBlockEntity.java#L416-L423)

Literal AE2-style cells are not sufficient here: putting a large Auto Storage payload into an ItemStack component inside the Core chunk would still grow that chunk. Auto Storage adopts the bounded-segment and delayed-encoding ideas, not the chunk ownership model.

### Refined Storage 2

RS2 stores inventories in an overworld `SavedData` repository keyed by UUID. A Storage Block block entity persists only its storage UUID and resolves the actual inventory through the repository.

Relevant upstream source:

- [`StorageRepositoryImpl` UUID-to-storage SavedData repository](https://github.com/refinedmods/refinedstorage2/blob/0643e6f4bc25f12dc01893916c53d393ecdc08d0/refinedstorage-common/src/main/java/com/refinedmods/refinedstorage/common/storage/StorageRepositoryImpl.java#L24-L100)
- [`StorageBlockBlockEntity` UUID reference](https://github.com/refinedmods/refinedstorage2/blob/0643e6f4bc25f12dc01893916c53d393ecdc08d0/refinedstorage-common/src/main/java/com/refinedmods/refinedstorage/common/storage/storageblock/StorageBlockBlockEntity.java#L49-L120)

Auto Storage adopts this world-repository ownership model, then adds bounded inventory segments, explicit runtime attachment ownership, and recovery-token indirection.

## Goals

- Keep Core block-entity NBT fixed-size regardless of stored type or item count.
- Preserve all durable Core state across ordinary save/load, chunk unload/reload, server restart, and safe dismantling.
- Keep the repository and every mutation server-owned.
- Preserve the same storage UUID and network UUID when a populated Core is moved.
- Avoid copying the payload into a recovery entry or dropped ItemStack.
- Preserve raw inventory and descriptor NBT when an add-on item or descriptor is unavailable.
- Reject missing, corrupt, packed, or concurrently attached records instead of manufacturing an empty replacement.
- Keep recovery tokens one-time claimable while allowing `/auto_storage recover_core` to reissue another reference to the same unclaimed recovery.

## Non-goals

- Migrating existing development worlds, inline Core NBT, or the old `magic_storage_core_recovery.dat` snapshot format.
- Storing the inventory client-side or mirroring the complete repository to clients.
- Changing player-visible storage type capacity or making 63 types a new gameplay limit.
- Introducing configurable persistence backends or multiple data files in this revision.
- Changing terminal layout, Patchouli layout, or other GUI geometry.

## Chosen architecture

```text
StorageCoreBlockEntity
  \- storageId: UUID
        |
        v
CoreStorageRepository (overworld SavedData)
  |- records: storage UUID -> CoreStorageRecord
  |- recoveries: recovery UUID -> storage UUID + metadata
  \- runtime attachments: storage UUID -> dimension + block position

CoreStorageRecord
  |- network UUID
  |- item inventory, serialized in segments of at most 63 type entries
  |- energy pools
  |- installed machine descriptor stacks
  |- descriptor amounts and infinity state
  |- unresolved inventory entry NBT
  \- unresolved machine/descriptor entry NBT
```

`CoreStorageRepository`, `CoreStorageRecord`, and related names describe the intended implementation boundaries. The repository is the sole durable owner. `StorageCoreBlockEntity` holds a runtime reference to its attached record but does not own a second persistent copy.

The first implementation uses one overworld SavedData named `auto_storage_core_storages`, stored by Minecraft as `data/auto_storage_core_storages.dat`. Record access and encoding remain isolated behind the repository boundary so a later design can shard files without changing Core or terminal behavior. No sharding switch or unused backend abstraction is added now.

## Persistent and runtime state boundaries

The repository record owns every durable field that currently belongs to a Core:

- `networkId`, so already-bound Remote Terminals keep their identity after moving the Core;
- all item keys, data components, and `long` counts;
- every `EnergyType` amount;
- installed machine stacks indexed by descriptor identity;
- descriptor amounts and infinite flags;
- raw unresolved inventory entries;
- raw unresolved machine or descriptor entries.

The block entity continues to own only runtime-derived state:

- connected block positions and calculated type capacity;
- conflict status and topology revisions;
- flat caches, listeners, deferred notifications, and mutation-batch depth;
- its current repository attachment status.

None of those runtime fields is used as a second durable copy of repository data.

## Block-entity NBT contract

After this change, `StorageCoreBlockEntity.saveAdditional` may write only:

- `storageId`: the UUID of an existing repository record;
- `storageSchema`: the fixed reference-schema version.

It must not write `networkId`, `inventory`, `energy`, `machines`, `machineDescriptors`, `descriptorConsumables`, recovery contents, or unresolved add-on payloads. A Core containing 785 types must therefore produce the same block-entity key set and fixed-size value shapes as a Core containing zero types.

The two block-entity fields are a reference contract, not a migration format. An unsupported `storageSchema`, a loaded Core with no `storageId`, or a UUID whose repository record does not exist is unavailable. Only an explicit new-placement path may create a fresh record.

## Repository NBT contract

The SavedData root contains:

- `schemaVersion`;
- `storages`, a list of UUID-keyed record compounds;
- `recoveries`, a list of recovery-token compounds.

Each storage compound contains its `storageId`, `networkId`, energy and descriptor state, machine descriptor entries, and `inventorySegments`.

Each inventory segment contains an `entries` list with at most 63 compounds. Every entry stores a complete one-count ItemStack encoding plus a positive `long` count. Raw entries whose ItemStack cannot currently be decoded stay in their original raw NBT form, still occupy a type entry, and still contribute their non-negative count to recovery summaries. Resolved runtime entries may be repartitioned deterministically when SavedData is next written; no segment may exceed 63 entries.

The 63-entry boundary is a persistence segmentation rule only. More than 63 types create another segment; it does not reduce the capacity supplied by connected Storage Units and is not exposed as a terminal page limit.

An unavailable machine descriptor or add-on ItemStack remains as raw entry NBT. If the dependency later returns, a subsequent repository load may resolve that raw entry. Until then, the server must neither operate on it nor discard it.

If an entire record cannot be decoded, the repository retains the raw record compound, marks the UUID unavailable, logs the reason, and writes the raw compound back unchanged. It must not replace that record with an empty `CoreStorageRecord`. Root entries that cannot be associated with a valid UUID are retained as raw orphan entries and logged rather than silently skipped.

## Creation and attachment lifecycle

### Fresh Core

`StorageCoreBlockEntity` construction never creates a repository entry and never invents a network identity during chunk decoding.

An ordinary new placement explicitly creates one fresh storage record, generates its storage UUID and network UUID, binds the placed block entity to that UUID, and acquires the runtime attachment. Non-item server placement must use the same explicit new-placement initialization after the block has been placed. A chunk-loaded Core missing its reference was not proven to be new and therefore fails closed.

### Existing Core load

On server load, a Core with a valid reference asks the repository to attach that exact UUID. Lookup and creation are separate operations: `attachExisting`-style behavior may never call `computeIfAbsent` for a missing UUID.

The repository keeps a non-persistent attachment map keyed by storage UUID. An attachment identifies the dimension and block position that currently owns mutation access. Reattaching the same Core location is idempotent. A different loaded location using the same UUID is rejected as a duplicate attachment; that block remains unavailable for its complete load lifetime and does not automatically take over if the first Core later unloads.

Chunk unload or block removal releases an attachment only when the releasing dimension and position match the current owner. A stale Core cannot detach another Core's lease.

### Mutation and saving

Inventory, energy, machine, and descriptor mutations update the attached `CoreStorageRecord` in memory. They do not assemble an NBT snapshot and do not mark the owning chunk dirty for payload changes.

The outermost successful mutation batch marks `CoreStorageRepository` dirty once after all atomic changes succeed. Non-batched single mutations mark it dirty once. Listener events remain deferred until the existing mutation batch commits. Minecraft calls the SavedData encoder during world save; that is when the current records are serialized into bounded segments.

Changing the block entity's `storageId` binding is the only persistence operation in this subsystem that requires its chunk reference tag to be marked dirty.

## Recovery lifecycle

Recovery is an indirection inside the same repository, not a second snapshot.

### Packing

When a non-empty Core is dismantled through survival breaking, creative breaking, explosion loot, or a tagged wrench:

1. the repository verifies that the Core owns the active attachment;
2. it creates one recovery UUID mapped to the existing storage UUID plus owner, type-count summary, item-count summary, creation time, and origin locator;
3. it marks that storage record packed so ordinary chunk references cannot attach to it;
4. the normal Core drop receives only the recovery UUID and summary fields;
5. block removal releases the original runtime attachment but does not delete or copy the storage record.

Repeated preparation for the same break is idempotent and returns the same recovery UUID. There is no `BLOCK_ENTITY_DATA` payload fallback for a populated Core.

If a save interruption leaves the original block at the recorded origin before the drop exists, that exact origin may cancel its pending pack and reattach the record on load. A copied Core at another position cannot use this rule. This favors retaining the original live Core over creating an empty replacement or a second mutable owner.

### Claiming

A recovery-token placement first validates that the recovery mapping, referenced storage record, and target attachment are available. Normal block placement must succeed and the new `StorageCoreBlockEntity` must exist before the repository atomically attaches the existing storage UUID and removes the recovery mapping. A placement rejected before those conditions are met does not consume the token.

The placed Core keeps the same storage UUID and network UUID. The recovery UUID is only a claim capability and is not adopted as either identity.

Copied recovery-token ItemStacks all reference the same mapping. The first successful claim removes that mapping; every later copy fails with the existing localized consumed-token behavior and cannot create a blank Core.

`/auto_storage recover_core` reissues another ItemStack containing the same unclaimed recovery UUID and current summary. It does not clone the record, create another recovery mapping, or change claim order.

### Empty Core removal

A Core whose record contains no inventory entries, machine stacks, descriptor value, infinite descriptor, unresolved add-on entry, or positive energy drops as an ordinary Core item. On completed removal the repository releases and deletes that empty record. It does not create a recovery entry.

## Fail-closed behavior

The Core exposes an explicit unavailable attachment state when any of these conditions occurs:

- its chunk reference is missing or uses an unsupported reference schema;
- the referenced repository record is missing;
- the record or required identity cannot be decoded;
- the record is packed for recovery at another location;
- another loaded Core already owns the storage UUID;
- a recovery token references a missing record or an already-consumed mapping.

An unavailable Core must reject insert, extract, fuel, machine, crafting, automation, and terminal operations. It must not return successful empty results that look like a valid zero-content network. Player interaction reports a localized `Core storage data unavailable` message, while the server logs the storage UUID when present, dimension, block position, and stable reason once per attachment-state transition rather than once per tick.

Fresh creation, existing attachment, and recovery claim are separate repository operations. No failure path may silently call fresh creation.

## Atomicity and ownership invariants

- Exactly one loaded dimension/position may mutate a storage UUID.
- Exactly one repository record owns a storage payload.
- Packing adds only an indirection to that record; it never copies its payload.
- A packed record cannot be attached through stale chunk NBT.
- A recovery mapping is removed only by a successful claim or exact-origin pack cancellation.
- A record is deleted only after confirmed removal of an empty Core.
- Missing add-on content and undecodable records survive a load/save cycle as raw NBT.
- Client packets can request operations but cannot resolve, create, attach, or mutate repository records directly.

## TDD and acceptance tests

Production code must not change until the smallest relevant test has been added and observed failing for the expected reason. Required regression coverage includes:

1. **Fixed chunk payload:** populate a repository record with 785 distinct type entries, save its Core block entity, and assert the block tag contains only `storageId` and `storageSchema` beyond base block-entity metadata. Assert none of the former payload keys is present.
2. **Repository round trip:** encode and decode network UUID, every energy pool, resolved items with data components and `long` counts, installed machines, descriptor amounts/infinity, and unresolved raw entries without loss.
3. **Bounded segments:** serialize 0, 1, 63, 64, 785, and 786 entries; every segment is at most 63 entries and the combined entries/counts are unchanged.
4. **Fresh versus missing:** a new placement creates one record, while a loaded unknown UUID and a loaded missing UUID reference both remain unavailable and create no record.
5. **Attachment lease:** two loaded Core positions referencing the same storage UUID cannot both attach or mutate it; unloading a non-owner cannot release the owner.
6. **Move without copying:** packing and placing a populated Core preserves the same storage UUID, network UUID, record object identity at the repository level, inventory, energy, and machines; the recovery entry contains no payload compound.
7. **Failed placement:** an occupied or otherwise invalid placement target does not consume the recovery mapping or mutate the record.
8. **One-time claim and reissue:** reissued stacks share one recovery UUID; the first placement succeeds and all later copies fail without creating storage records.
9. **Packed stale reference:** a second Core carrying the packed storage UUID cannot attach before claim.
10. **Exact-origin interruption:** only the recorded origin can cancel an interrupted pack and recover the original active record.
11. **Unavailable operation guard:** insert, extract, crafting, descriptor, machine, terminal, import, and export paths all refuse an unavailable Core and surface the localized error where a player initiated the action.
12. **Missing add-on preservation:** unresolved inventory and descriptor entries remain byte-equivalent NBT across repository load/save and continue to occupy safe capacity/count accounting.
13. **Corrupt record preservation:** an undecodable UUID record remains raw in SavedData, the Core is unavailable, and a save does not replace it with an empty record.
14. **Empty cleanup:** removing an empty Core deletes its record and produces no recovery mapping; removing a non-empty Core never deletes its record.

Focused Java SelfTests or GameTests should cover repository semantics and server lifecycle. Existing Python static tests must be updated to reject former payload keys and the inline `BLOCK_ENTITY_DATA` Core fallback. The final implementation still runs the complete project CI-equivalent gates.

## Documentation impact

The behavior change must update, in the same implementation change:

- `docs/overview.md` for the world-repository ownership model;
- `docs/structure.md` for the new repository/record classes and removed snapshot owner;
- `docs/notes.md` for persistence, recovery, failure, and operational contracts;
- `docs/roadmap.md` to replace the historical statement that Core payloads live in chunk NBT or full recovery snapshots;
- the older connected-progression design where it describes `CoreRecoverySavedData` as current behavior.

No terminal geometry, texture, Patchouli page, or GUI layout changes are part of this design. The new unavailable-state localization is behavior feedback, not a visual-layout revision.

## Delivery gates

After the RED/GREEN TDD sequence, run at minimum:

- focused SelfTests/GameTests for repository persistence and recovery;
- `./gradlew compileJava`;
- `./gradlew build`;
- `./gradlew runGameTestServer`;
- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts`;
- `./gradlew runData` followed by the repository's datagen drift check;
- documentation reference scan and `git diff --check`.

Because this design does not alter GUI layout or visual assets, it does not by itself require a Prism visual-approval scenario. Any later implementation that changes a screen or Patchouli page re-enters the normal GUI gate.

## Risks and deferred work

One SavedData file removes unbounded payload growth from chunks but centralizes all Core records in one world data file. Minecraft may rewrite that file when the repository is dirty, so a very large server could eventually need UUID-prefix sharding or per-record files. That is a later measured optimization, not an option in this revision.

Crash consistency still follows Minecraft's world-save boundary: mutations since the last completed save can be lost just like other SavedData. The design prevents deliberate duplicate snapshots and silent empty replacement; it does not implement a separate write-ahead journal.

Runtime attachment leases are intentionally not persisted. After a restart, loaded chunks reacquire them, and duplicate Core references remain fail-closed according to load order. Repair tooling for intentionally corrupted or manually duplicated world data is outside this revision; raw data preservation and explicit logs keep such tooling possible later.
