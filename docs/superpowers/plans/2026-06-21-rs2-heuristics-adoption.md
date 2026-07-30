# RS2 Heuristics Adoption — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adopt the "fits-our-philosophy" Refined Storage 2 design heuristics (A1–A7 from `docs/rs2-design-gap.md`) to make Auto Storage more robust, scalable, and pleasant — without changing the recipe-book / infinite-quantity / crafting-energy philosophy.

**Architecture:** Contract-first + phased. Foundational contracts (Actor, change-events, comparison modes, view-settings sync) land first in the data layer; UI/consumers build on them; the two large rewrites (incremental grid delta, incremental network graph) come last. Most heuristics funnel through `StorageCoreBlockEntity` + the terminal menus/screens, so work-units are sequenced by file ownership to stay disjoint per dispatch batch.

**Tech Stack:** NeoForge 1.21.1, Java 21, FastUtil, hand-rolled GUI (`AbstractContainerScreen`), GameTest + SelfTest. Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew compileJava` / `runGameTestServer`.

---

## Status (updated 2026-07-12)

- **Done + verified** (this plan's 0.1.8 baseline was SelfTest 117, GameTest 180, Python 61; current counts live in `docs/plan.md`):
  - A5b fuzzy match (Task 3/6) — commit `c94d37f`
  - A5a view-settings sync (Task 4) + A4 identity selection (Task 5) + A7 craft preview — commit `116664d`
  - A1 incremental network growth, **safe scope** (Task 8: incremental on place, full rebuild on break) — commit `3433e76`
  - A2 Action/Actor storage contract (Task 1) + A3 change-event foundation (Task 2) — `Action`, `Actor`, `StorageListener`, execute-only delta events.
  - P0/P1/P2 RS2 parity polish:bus cached-core disconnect invalidation plus currently-loaded path validation, conflicted core no extract, bridge placement/full-removal rebuild fallback, terminal access + exact-Core UUID remote `stillValid`, server-synced crafting metadata/live preview, player/topology fingerprint refresh, one-snapshot preview planning, deferred-listener atomic commit, server-side craftable-only filter, EMI input-source contract excluding hidden slots, predicate-preserving overlapping-ingredient joint reservation, exact static recipe contract + current-manager revalidation, visible-36-slot player inventory paths, item-level/stacked fuel remainders, energy-driven preview refresh, and the server-authoritative dedicated Fuel page.
- **Remaining**: **P3 incremental grid delta (Task 7)** only. A3 listener foundation exists, but the current paged grid is ≤81 slots and vanilla menu sync is already incremental; keep P3 deferred unless the UI changes to a whole-list client grid.

## Dispatch strategy (read before spawning agents)

- **Subagents keep failing with API 529 when many spawn at once** — dispatch **at most 2 agents per batch**, on **disjoint files**, and be ready to fall back to main-thread.
- **Contract-first**: Phase 1 defines interfaces other phases depend on. Do not parallelize a phase that edits a file another in-flight agent edits.
- **Each task is TDD**: write the failing GameTest/SelfTest first, watch it fail, implement, watch it pass, commit. Tests must be **dist-neutral** if in SelfTest (no `*Screen` refs).
- **Verify gate** per task: `./gradlew compileJava` then the relevant `runGameTestServer`. Visual-only changes: note "needs `runClient`".
- Cite the RS2 pattern in the commit body / `docs/notes.md` (license differs — patterns only).

## Shared contracts (defined in Phase 1, consumed later)

```java
// Actor: who performed a storage op (for future automation / not-pulling-from-self).
public interface Actor { String name(); }
// long insertItem(ItemStack, Action, Actor) / extractItem(ItemKey, long, Action, Actor)
// where Action = SIMULATE | EXECUTE (replaces the current boolean simulate; keep a boolean-less overload bridge).

// Change events: Core notifies listeners of per-resource deltas instead of "rebuild everything".
public interface StorageListener { void onChanged(ItemKey key, long delta, long newAmount, Actor actor); }
// Core: addListener/removeListener; fire on execute insert/extract.

// Comparison: how two ItemKeys are matched (exact vs fuzzy).
public enum MatchMode { EXACT, IGNORE_NBT, IGNORE_DAMAGE }
// ItemKey.matches(other, MatchMode) + Core.extract/count by (Item, MatchMode) for fuzzy.
```

---

## Phase 1 — Foundation (data layer; one agent owns `StorageCoreBlockEntity.java` + `ItemKey.java` + new enum/interface files)

### Task 1: Action + Actor on storage ops (A2)

**Files:**
- Create: `src/main/java/com/swearprom/magicstorage/magic_storage/Action.java` (enum SIMULATE, EXECUTE)
- Create: `.../Actor.java` (sealed interface + PlayerActor/BusActor/CraftingActor records)
- Modify: `.../StorageCoreBlockEntity.java` (insert/extract signatures + keep boolean bridge)
- Test: `.../gametest/PersistenceTests.java` (or a new StorageApiTests)

- [x] **Step 1: failing test** — `core.insertItem(stack, Action.SIMULATE, Actor.bus())` returns accepted count but does NOT change `getItemCount`; `Action.EXECUTE` does. Assert both.
- [x] **Step 2: run, expect FAIL** (`insertItem(ItemStack,Action,Actor)` undefined). `runGameTestServer`.
- [x] **Step 3: implement** — add `Action`/`Actor`; make the real impl take `(stack, Action, Actor)`; keep `insertItem(stack, boolean)` / `insertItem(stack)` / `extractItem(key, amount[, boolean])` as bridges delegating with `Actor` = a default. Actor currently only recorded (no behavior yet).
- [x] **Step 4: run, expect PASS.**
- [ ] **Step 5: commit** `feat(core): Action+Actor on storage ops (RS2 Storage<T> w/ Source)`.

### Task 2: Change-event listeners (A3a — foundation for incremental grid)

**Files:** Create `.../StorageListener.java`; Modify `.../StorageCoreBlockEntity.java`; Test `.../gametest/StorageApiTests.java`

- [x] **Step 1: failing test** — register a listener, `insertItem(STONE×5)`, assert listener got `onChanged(STONE, +5, 5)`; extract 2 → `onChanged(STONE, -2, 3)`.
- [x] **Step 2: run, expect FAIL.**
- [x] **Step 3: implement** — `List<StorageListener>` + add/remove; fire from execute insert/extract (delta + new amount + actor). Keep `cacheDirty` for now (grid still full-rebuilds until Phase 3).
- [x] **Step 4: run, expect PASS.**
- [ ] **Step 5: commit** `feat(core): per-resource change listeners (RS2 ResourceList events)`.

### Task 3: Comparison / fuzzy match (A5b — foundation for crafting match + grid filters)

**Files:** Create `.../MatchMode.java`; Modify `.../ItemKey.java` (+ `StorageCoreBlockEntity` count/extract-by-Item+mode); Test `.../gametest/StorageApiTests.java`

- [ ] **Step 1: failing test** — store an enchanted + a plain diamond sword; `core.countMatching(new ItemStack(DIAMOND_SWORD), MatchMode.IGNORE_NBT)` returns both (2); `EXACT` returns 1.
- [ ] **Step 2: run, expect FAIL.**
- [ ] **Step 3: implement** — `MatchMode`; `ItemKey.matches(ItemStack, MatchMode)`; Core helper to sum/extract across variants of an Item by MatchMode (uses the Tier-1 `Item → variants` index).
- [ ] **Step 4: run, expect PASS.**
- [ ] **Step 5: commit** `feat(core): fuzzy/ignore-NBT matching (RS2 comparison modes)`.

> After Phase 1: `runGameTestServer` full green. Phase 1 is the only phase touching `StorageCoreBlockEntity` until Phase 4 — schedule accordingly.

---

## Phase 2 — Consumers (TWO disjoint work-units, dispatchable in parallel)

### WU-2A (agent owns `StorageTerminalMenu.java` + `StorageTerminalScreen.java` + `GhostSlot.java`)

#### Task 4: Sync sort/search/match state via data slots (A5a + fixes bug #7)

> 2026-07-22 extension complete：沿RS2 grid config模式新增physical-client全域持久化。Common四項與Crafting四項在開啟時送server套用，等data-slot acknowledgement後才保存後續變更；query/scroll/selection/layout仍不持久化。

**Files:** Modify `StorageTerminalMenu.java` (add sortMode/sortOrder/searchMode/matchMode data slots in `addTypeDataSlots` — keep client/server parity!), `StorageTerminalScreen.java` (read synced state; button label/tooltip reflect it). Test: `.../gametest/TerminalFlowTests.java`.

- [ ] **Step 1: failing test** — toggle sort via `clickMenuButton(12)`, then a fresh client-style menu reads the synced sort mode (via the data slot getter); assert it matches. Also `data-slot parity` test (server vs buf ctor counts equal — extend existing).
- [ ] **Step 2: run, expect FAIL.**
- [ ] **Step 3: implement** — add the 3–4 enums as synced ints in `addTypeDataSlots` (both ctors); server is authority; screen reads them for button glyph/tooltip; remove the client-only `searchMode` field.
- [ ] **Step 4: run, expect PASS** + `runClient` visual: button labels change with state.
- [ ] **Step 5: commit** `feat(grid): server-synced view settings (RS2 synced grid config)`.

### WU-2B (agent owns `CraftingTerminalMenu.java` + `CraftingTerminalScreen.java`)

#### Task 5: Selected item by identity, not slot index (A4 + fixes bug #5)

**Files:** Modify `CraftingTerminalMenu.java` (store selected `ItemKey`, not `selectedItemIndex`; resolve recipes from it; re-validate on refresh), `CraftingTerminalScreen.java` (render recipe from synced identity; drop the parallel `findRecipesClient` divergence). Test: `.../gametest/CraftingTests.java`.

- [ ] **Step 1: failing test** — select item at grid slot 0, change sort so the grid reorders, assert the menu's selected recipe still corresponds to the originally selected item (not whatever now sits at slot 0).
- [ ] **Step 2: run, expect FAIL.**
- [ ] **Step 3: implement** — replace `selectedItemIndex` with a selected `ItemKey` (synced); recipe lookup keyed off it; clear when the item leaves storage.
- [ ] **Step 4: run, expect PASS.**
- [ ] **Step 5: commit** `feat(crafting): select by resource identity (RS2 selected-resource)`.

#### Task 6: Fuzzy ingredient match + craft preview (A5b consumer + A7)

**Files:** Modify `CraftingTerminalMenu.java` (ingredient match via `Ingredient.test` across variants using Phase-1 Core helpers; add `craftablePreview(count)` → {craftable:int, missing:List}), `CraftingTerminalScreen.java` (show "×N possible / missing: …"). Test: `.../gametest/CraftingTests.java`.

- [ ] **Step 1: failing test** — store 32 oak + 32 spruce planks; craft something needing 64 planks → succeeds (currently fails, exact-match). Also `preview` reports craftable=K, missing=[] when enough; missing list non-empty when short.
- [ ] **Step 2: run, expect FAIL.**
- [ ] **Step 3: implement** — switch `canConsumeIngredient`/`doConsumeIngredient` to `Ingredient.test` + Phase-1 fuzzy aggregation; add preview computed from the existing simulate path.
- [ ] **Step 4: run, expect PASS** + `runClient` visual: preview text.
- [ ] **Step 5: commit** `feat(crafting): fuzzy ingredient match + craft preview (RS2 ingredient resolution + preview)`.

---

## Phase 3 — Incremental grid delta (A3b; sequential — touches Core + menus + screens)

### Task 7: Grid receives deltas, not full rebuilds (deferred)

**Files:** Modify `StorageCoreBlockEntity.java` (expose deltas via the Phase-1 listener), `StorageTerminalMenu.java` (track a delta buffer; sync only changed entries), `StorageTerminalScreen.java` (apply deltas to the view; keep sort/filter client-side over the maintained list). New packet `GridDeltaPacket.java`. Test: `.../gametest/TerminalFlowTests.java`.

- [ ] **Step 0: re-evaluate value first** — only proceed if the UI changes to a whole-list client grid or large-list profiling shows the current paged grid is a bottleneck. With ≤81 visible slots, vanilla slot sync already avoids most P3 value.
- [ ] **Step 1: failing test** — after inserting 1 new type into a populated core, the menu's outgoing sync carries 1 changed entry, not the whole list (assert a delta-count accessor).
- [ ] **Step 2: run, expect FAIL.**
- [ ] **Step 3: implement** — server accumulates deltas per tick from the listener; `GridDeltaPacket` (S2C) carries changed (ItemKey, newAmount); client applies to a maintained map then re-sorts the view. Full-snapshot still sent on open.
- [ ] **Step 4: run, expect PASS** + `runClient`: large inventory stays responsive.
- [ ] **Step 5: commit** `feat(grid): incremental delta sync (RS2 GridView deltas)`.

---

## Phase 4 — Incremental network graph (A1; sequential — touches MagicStorage + Core + buses; largest)

### Task 8: Persistent node graph, incremental on block add/remove

**Files:** Create `.../StorageNetwork.java` (adjacency + member set, held by the core), Modify `MagicStorage.java` (place/break events update the graph incrementally instead of full BFS; keep full BFS only as the initial build / fallback), `StorageCoreBlockEntity.java` (`rebuildNetwork` becomes "ensure graph"), buses query the graph. Test: `.../gametest/TerminalFlowTests.java` + a perf-shaped assertion.

- [ ] **Step 1: failing test** — build core + 3 units; break the middle unit; assert capacity updates correctly AND `rebuildNetwork` full-BFS is NOT invoked (a counter / spy), only an incremental update.
- [ ] **Step 2: run, expect FAIL.**
- [ ] **Step 3: implement** — graph holds member positions + adjacency; on place, attach the new node + its edges; on break (post-removal, via the existing `server.execute` defer), detach + re-validate the affected component; buses read the graph; cap retained via `MAX_NETWORK_BLOCKS`.
- [ ] **Step 4: run, expect PASS.**
- [ ] **Step 5: commit** `feat(network): incremental node graph (RS2 NodeGraph + visitors)`.

---

## A6 (simulate-then-commit everywhere) — convention, not a task

Already applied to import bus + crafting. Treat as a **review checklist item** for every task above: any new move/craft path must simulate-all then commit; never mutate before validating; honor returned amounts.

## Self-review (done)

- Spec coverage: A1=safe-scope done/Task8 optional, A2=Task1 done, A3=Task2 done + Task7 optional, A4=Task5 done, A5=Task3(modes)+Task4(sync)+Task6(consumer) done, A6=convention guarded, A7=Task6 done.
- Placeholder scan: interfaces/signatures are concrete; per-task exact files + test assertions given. Graph/delta internal code is designed at execution (flagged) rather than faked here.
- Type consistency: `Action`, `Actor`, `StorageListener.onChanged(ItemKey,delta,newAmount,Actor)`, `MatchMode`, `craftablePreview` used consistently across tasks.

## Suggested dispatch order (≤2 agents/batch, disjoint files)

1. **Phase 1** — 1 agent (Core/ItemKey). Must finish before Phases 2–4 (they depend on the contracts).
2. **Phase 2** — 2 agents in parallel: WU-2A (base terminal) ‖ WU-2B (crafting). Disjoint files.
3. **Phase 3** — 1 agent (touches Core+menus+screens; sequential after Phase 2).
4. **Phase 4** — 1 agent (network; sequential, largest).
