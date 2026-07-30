# Out-of-Chunk Core Storage Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace chunk-owned Core payloads and recovery snapshots with a server-owned UUID repository whose inventory serialization uses segments of at most 63 types.

**Architecture:** `StorageCoreBlockEntity` persists only a storage UUID reference and aliases the mutable state owned by one attached `CoreStorageRecord`. One overworld `CoreStorageRepository` SavedData owns records, runtime attachment leases, and recovery UUID-to-storage UUID mappings; recovery items carry only the mapping UUID and summary. Record encoding preserves complete ItemStacks, `long` counts, unresolved raw NBT, and fixed 63-entry segments while all missing/corrupt/duplicate states fail closed.

**Tech Stack:** Java 21, NeoForge 1.21.1 block entities and SavedData, Minecraft NBT and GameTest, Python `unittest`, Gradle ModDevGradle.

---

## File map

- Create `src/main/java/com/swear/autostorage/CoreStorageRecord.java`: owns and encodes one Core's durable inventory, energy, machine, descriptor, and unresolved state.
- Create `src/main/java/com/swear/autostorage/CoreStorageRepository.java`: overworld SavedData, UUID lookup, attachment leases, raw-record preservation, packing, claiming, reissue, and empty cleanup.
- Delete `src/main/java/com/swear/autostorage/CoreRecoverySavedData.java`: full-snapshot recovery is replaced, with no migration.
- Modify `StorageCoreBlockEntity.java`, `StorageCoreBlock.java`, `StorageCoreBlockItem.java`, `WrenchActions.java`, and `AutoStorage.java`: fixed reference lifecycle and token-only recovery.
- Modify `TerminalBlock.java`, `RemoteTerminalItem.java`, `StorageTerminalMenu.java`, `ImportBusBlockEntity.java`, and `ExportBusBlockEntity.java`: reject unavailable storage before identity or mutation access.
- Modify `gametest/PersistenceTests.java` and affected setup in `BehavioralTests.java`, `CraftingTests.java`, `FuelPageTests.java`, `TerminalFlowTests.java`, and `WrenchTests.java`.
- Modify `scripts/test_static_regressions.py`, English/Traditional Chinese lang files, and the active overview/structure/notes/roadmap/design documents.

### Task 1: Lock the persistence boundary with RED tests

**Files:**
- Modify: `src/main/java/com/swear/autostorage/gametest/PersistenceTests.java`
- Modify: `scripts/test_static_regressions.py`

- [ ] **Step 1: Add compile-time and static failing tests**

Add GameTests for a 785-type record producing Core NBT with only `storageId` and `storageSchema`, and for 0/1/63/64/785/786 entries producing segments no larger than 63:

```java
@GameTest(template = "platform")
public static void core_chunk_nbt_stays_fixed_for_785_types(GameTestHelper helper) {
    StorageCoreBlockEntity core = placeCore(helper, new BlockPos(1, 3, 1));
    CoreStorageRecord record = core.storageRecordForTesting();
    record.replaceInventoryForTesting(createDistinctEntries(helper.getLevel().registryAccess(), 785));
    CompoundTag tag = new CompoundTag();
    core.saveAdditional(tag, helper.getLevel().registryAccess());
    if (!tag.hasUUID("storageId") || tag.getInt("storageSchema") != 1
            || tag.contains("networkId") || tag.contains("inventory")
            || tag.contains("energy") || tag.contains("machineDescriptors")
            || tag.contains("descriptorConsumables")) {
        helper.fail("Core chunk NBT contains repository payload: " + tag.getAllKeys());
        return;
    }
    helper.succeed();
}
```

Update Python assertions to require `CoreStorageRepository`, `CoreStorageRecord`, `inventorySegments`, and the 63-entry bound, while rejecting `CoreRecoverySavedData`, Core `BLOCK_ENTITY_DATA`, and inline payload writes.

- [ ] **Step 2: Run RED verification**

```bash
./gradlew compileJava
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.test_static_regressions
```

Expected: Java fails on missing record/test access symbols; Python fails on missing repository ownership.

- [ ] **Step 3: Commit RED**

```bash
git add src/main/java/com/swear/autostorage/gametest/PersistenceTests.java scripts/test_static_regressions.py
git commit -m "test: require out-of-chunk core storage"
```

### Task 2: Implement bounded record encoding

**Files:**
- Create: `src/main/java/com/swear/autostorage/CoreStorageRecord.java`
- Modify: `src/main/java/com/swear/autostorage/gametest/PersistenceTests.java`

- [ ] **Step 1: Add failing round-trip tests**

Build a record containing a fixed network UUID, every energy pool, component-bearing ItemKeys with `long` counts, installed machines, finite/infinite descriptor values, and raw unresolved inventory/machine/descriptor entries. Save and reload it, then assert values and raw compounds:

```java
CoreStorageRecord.LoadResult decoded = CoreStorageRecord.load(encoded, registries);
if (!decoded.success()) helper.fail(decoded.error());
CoreStorageRecord restored = decoded.record();
```

- [ ] **Step 2: Run RED**

```bash
./gradlew compileJava
```

Expected: missing `CoreStorageRecord` codec methods.

- [ ] **Step 3: Implement the record**

```java
final class CoreStorageRecord {
    static final int SCHEMA_VERSION = 1;
    static final int MAX_SEGMENT_TYPES = 63;

    static CoreStorageRecord fresh(UUID storageId);
    static LoadResult load(CompoundTag tag, HolderLookup.Provider registries);
    CompoundTag save(HolderLookup.Provider registries);

    UUID storageId();
    UUID networkId();
    Map<EnergyType, Long> energy();
    SimpleContainer machines();
    Map<ResourceLocation, Long> descriptorAmounts();
    Set<ResourceLocation> infiniteDescriptors();
    List<CompoundTag> unresolvedDescriptorEntries();
    List<CompoundTag> unresolvedMachineEntries();
    Map<Item, Object2LongOpenHashMap<ItemKey>> inventory();
    List<CompoundTag> unresolvedInventoryEntries();
    int typeCount();
    long itemCount();
    boolean isEmpty();
    void setDirtyCallback(Runnable callback);
    void setMachineMutationCallback(Runnable callback);
    void clearMachineMutationCallback();

    record LoadResult(CoreStorageRecord record, UUID storageId, CompoundTag raw, String error) {
        boolean success() { return record != null; }
    }
}
```

Always emit all mandatory v1 fields. Preserve unparseable items/descriptors as raw compounds. Return failed `LoadResult` for malformed mandatory identity/shape; never manufacture an empty state. Sort resolved entries deterministically and partition the combined resolved/raw entries in groups of at most 63.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew compileJava
./gradlew runGameTestServer
git add src/main/java/com/swear/autostorage/CoreStorageRecord.java src/main/java/com/swear/autostorage/gametest/PersistenceTests.java
git commit -m "feat: add bounded core storage records"
```

Expected: record and segmentation tests pass.

### Task 3: Implement the SavedData repository and leases

**Files:**
- Create: `src/main/java/com/swear/autostorage/CoreStorageRepository.java`
- Modify: `src/main/java/com/swear/autostorage/gametest/PersistenceTests.java`

- [ ] **Step 1: Add failing repository tests**

Cover fresh creation, lookup without implicit creation, missing UUID, corrupt raw-record rewrite, invalid-UUID orphan rewrite, exact-owner release, duplicate attachment, packed stale attachment, latest same-tick recovery, same-ID reissue, first-only claim, exact-origin cancellation, and empty-only deletion.

- [ ] **Step 2: Run RED**

```bash
./gradlew compileJava
```

Expected: missing repository API.

- [ ] **Step 3: Implement the repository**

```java
final class CoreStorageRepository extends SavedData {
    static final String DATA_NAME = AutoStorage.MODID + "_core_storages";

    static CoreStorageRepository get(ServerLevel level);
    static CoreStorageRepository load(CompoundTag tag, HolderLookup.Provider registries);
    CoreStorageRecord createFresh(CoreLocation location, UUID ownerToken);
    AttachResult attachExisting(UUID storageId, CoreLocation location, UUID ownerToken);
    void release(UUID storageId, CoreLocation location, UUID ownerToken);
    boolean removeIfEmpty(UUID storageId, CoreLocation location, UUID ownerToken);
    Optional<RecoverySummary> prepareRecovery(UUID storageId, CoreLocation location,
            UUID ownerToken, UUID owner, long createdAt);
    Optional<RecoverySummary> summary(UUID recoveryId);
    Optional<RecoverySummary> reissueLatest(UUID owner);
    boolean hasRecovery(UUID recoveryId);
    ClaimResult claimIntoFresh(UUID recoveryId, UUID freshStorageId,
            CoreLocation location, UUID ownerToken);
    CompoundTag save(CompoundTag tag, HolderLookup.Provider registries);

    record CoreLocation(ResourceKey<Level> dimension, BlockPos pos) {}
    record AttachResult(CoreStorageRecord record, AttachFailure reason) {
        boolean success() { return record != null; }
    }
    record ClaimResult(CoreStorageRecord record, AttachFailure reason) {
        boolean success() { return record != null; }
    }
    record RecoverySummary(UUID id, int typeCount, long itemCount, long createdAt) {}
}
```

Keep attachment leases runtime-only and owner-token-specific. Persist schema v1, decoded records, UUID-keyed raw records, raw orphan compounds, and ordered recovery mappings. An unsupported root schema is retained and rewritten raw while exposing no records. Only the persisted origin may cancel an interrupted pack.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew compileJava
./gradlew runGameTestServer
git add src/main/java/com/swear/autostorage/CoreStorageRepository.java src/main/java/com/swear/autostorage/gametest/PersistenceTests.java
git commit -m "feat: add core storage world repository"
```

### Task 4: Bind Core state to repository records

**Files:**
- Modify: `src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java`
- Modify: `src/main/java/com/swear/autostorage/StorageCoreBlock.java`
- Modify: all affected GameTest setup files listed in the file map.

- [ ] **Step 1: Add failing lifecycle tests**

Verify that server `setBlock` creates one fresh record, chunk save/load persists only the reference, unknown UUID remains unavailable without growing the repository, duplicate positions cannot mutate one UUID, a non-owner cannot release a lease, and every storage mutation refuses an unavailable Core.

- [ ] **Step 2: Run RED**

```bash
./gradlew runGameTestServer
```

Expected: fixed-reference assertions fail against inline Core NBT.

- [ ] **Step 3: Replace inline ownership**

Add:

```java
private static final String TAG_STORAGE_ID = "storageId";
private static final String TAG_STORAGE_SCHEMA = "storageSchema";
private static final int STORAGE_SCHEMA = 1;
private final UUID attachmentToken = UUID.randomUUID();
private UUID storageId;
private CoreStorageRecord storageRecord;
private StorageAvailability storageAvailability = StorageAvailability.UNINITIALIZED;
private boolean storageChangedInBatch;

void initializeFreshStorage(ServerLevel level);
boolean claimRecovery(ServerLevel level, UUID recoveryId);
public boolean isStorageAvailable();
CoreStorageRecord storageRecordForTesting();
private void attach(CoreStorageRecord record);
private void markStorageChanged();
void removeStorageForBlockRemoval(ServerLevel level);
```

`saveAdditional` writes only reference keys. `loadAdditional` reads only those keys. `onLoad` attaches an existing UUID, `setRemoved` releases only the exact lease, and `StorageCoreBlock.onPlace` performs fresh initialization. Durable collections and machine container are aliases to the attached record; caches/listeners/topology stay on the block entity. The outer mutation batch marks SavedData dirty once.

Delete the legacy bottle-fuel, slot-machine, axe-field, and inline payload migration branches. Rewrite tests to mutate attached records or use normal operations; remove tests whose only contract is the explicitly retired migration format.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew compileJava
./gradlew runGameTestServer
git add src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java src/main/java/com/swear/autostorage/StorageCoreBlock.java src/main/java/com/swear/autostorage/gametest
git commit -m "refactor: bind cores to repository records"
```

### Task 5: Replace full recovery snapshots with record claims

**Files:**
- Delete: `src/main/java/com/swear/autostorage/CoreRecoverySavedData.java`
- Modify: `StorageCoreBlock.java`, `StorageCoreBlockItem.java`, `WrenchActions.java`, `AutoStorage.java`
- Modify: `PersistenceTests.java`, `WrenchTests.java`

- [ ] **Step 1: Add failing no-copy movement tests**

Assert survival, creative, explosion, and wrench packing preserve the same storage/network UUID; recovery NBT contains storage UUID but no `contents`; drops contain no `BLOCK_ENTITY_DATA`; invalid placement keeps the mapping; reissue shares the recovery UUID; duplicate tokens fail after first claim; empty removal deletes its record without recovery.

- [ ] **Step 2: Run RED**

```bash
./gradlew runGameTestServer
```

Expected: tests detect the full `contents` recovery snapshot and inline loot fallback.

- [ ] **Step 3: Route packing and claiming through the repository**

`prepareRecoveryDrop` calls `CoreStorageRepository.prepareRecovery`. Drop construction reads only `RecoverySummary`; empty Cores drop normally. Remove `BlockItem.setBlockEntityData`. `setPlacedBy` calls `claimRecovery` only after the new BE exists. `StorageCoreBlockItem.place` validates an unclaimed mapping before placement. The command calls `reissueLatest`.

Delete the retired class with:

```bash
git rm src/main/java/com/swear/autostorage/CoreRecoverySavedData.java
```

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew compileJava
./gradlew runGameTestServer
git add -A src/main/java/com/swear/autostorage
git commit -m "fix: keep packed core contents in world storage"
```

### Task 6: Guard all player and automation entry points

**Files:**
- Modify: `TerminalBlock.java`, `RemoteTerminalItem.java`, `StorageTerminalMenu.java`, `ImportBusBlockEntity.java`, `ExportBusBlockEntity.java`
- Modify: `src/main/resources/assets/auto_storage/lang/en_us.json`
- Modify: `src/main/resources/assets/auto_storage/lang/zh_tw.json`
- Modify: `TerminalFlowTests.java`, `BehavioralTests.java`

- [ ] **Step 1: Add failing fail-closed tests**

Create unavailable references and verify local terminal opening, Remote binding/use, menu resolution, passive/active Import, and Export refuse access without mutation. Direct player paths must report `msg.auto_storage.core_storage_unavailable`.

- [ ] **Step 2: Run RED**

```bash
./gradlew runGameTestServer
```

Expected: one or more callers access identity/state before checking availability.

- [ ] **Step 3: Add shared guards and localization**

Before `getNetworkId`, menu construction, or bus transfer, require `core.isStorageAvailable()`. Player paths use:

```java
player.displayClientMessage(
        Component.translatable("msg.auto_storage.core_storage_unavailable"), true);
```

English is `Core storage data unavailable`; Traditional Chinese is `核心儲存資料目前無法使用`. Log UUID, dimension, position, and stable failure enum once per attachment-state transition.

- [ ] **Step 4: Run GREEN and commit**

```bash
./gradlew compileJava
./gradlew runGameTestServer
git add src/main/java/com/swear/autostorage src/main/resources/assets/auto_storage/lang
git commit -m "fix: fail closed when core storage is unavailable"
```

### Task 7: Update current docs and static contracts

**Files:**
- Modify: `scripts/test_static_regressions.py`
- Modify: `docs/overview.md`, `docs/structure.md`, `docs/notes.md`, `docs/roadmap.md`
- Modify: `docs/superpowers/specs/2026-07-14-connected-progression-fuel-guide-design.md`

- [ ] **Step 1: Run static RED**

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.test_static_regressions
```

Expected: retired recovery/inline assertions and stale active docs fail.

- [ ] **Step 2: Update exact current truth**

Document: Core chunks contain only storage UUID plus schema; `auto_storage_core_storages.dat` owns all server state; persistence segments contain at most 63 types without changing gameplay capacity; recovery UUIDs point to the same record; missing/corrupt/duplicate attachment fails closed; no old-format migration exists.

Static tests require those boundaries and reject `contents` snapshots, inline Core inventory payload, and the retired Java file.

- [ ] **Step 3: Run GREEN and commit**

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.test_static_regressions
rg -n "CoreRecoverySavedData|完整 Core NBT寫" docs --glob '!archive/**'
git diff --check
git add scripts/test_static_regressions.py docs
git commit -m "docs: describe core world storage ownership"
```

Expected: Python passes; the scan finds no stale current claim; diff check is clean.

### Task 8: Full verification and final review

**Files:**
- Modify only files required by failures found here.

- [ ] **Step 1: Compile and build**

```bash
./gradlew compileJava
./gradlew build
```

Expected: exit 0.

- [ ] **Step 2: Run dedicated GameTests**

```bash
./gradlew runGameTestServer
```

Expected: all SelfTests and GameTests pass.

- [ ] **Step 3: Run every Python regression**

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts
```

Expected: all pass.

- [ ] **Step 4: Run datagen and prove no new drift**

```bash
before=$(git status --porcelain=v1)
./gradlew runData
after=$(git status --porcelain=v1)
test "$before" = "$after"
```

Expected: exit 0 and identical status.

- [ ] **Step 5: Mechanically review invariants**

```bash
rg -n 'tag\.put\("inventory"|tag\.put\("energy"|TAG_NETWORK_ID|CoreRecoverySavedData|BLOCK_ENTITY_DATA' src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java src/main/java/com/swear/autostorage/StorageCoreBlock.java src/main/java/com/swear/autostorage/StorageCoreBlockItem.java
git diff --check
git status --short
```

Expected: no retired Core payload/recovery path and a clean worktree after any necessary verification-fix commit. Do not deploy or tag because the requested scope is implementation plus testing, not release publication.
