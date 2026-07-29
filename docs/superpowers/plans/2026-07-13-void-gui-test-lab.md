# Void GUI Test Lab Implementation Plan

> **Current revision (2026-07-23):** This completed plan preserves the original fixed-lab implementation history. The active schema-5 runner is scenario-scoped; `crafting-fuel-page` now preloads one matching server repository/Core reference and gives the player only hotbar 1/2 navigation, with no destructive reset or manual setup queue. Follow `docs/notes.md` and `docs/superpowers/plans/2026-07-23-polymorphic-stations-and-mod-recipes.md` for current behavior.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a transactionally regenerated true-void Prism test world with a deterministic Magic Storage laboratory, preloaded Core baseline, fixed navigation hotbar, and reusable reset.

**Architecture:** Keep `New World/level.dat` as a compatibility metadata source, but rewrite only the overworld generator to the exact vanilla flat-void codec shape and strip all copied runtime state before installing the generated datapack. Keep layout, preload, player kit, commands, and manifest generated from immutable Python data so coordinates and documentation cannot drift independently.

**Tech Stack:** Python 3 standard library, gzip NBT reader/writer already in `scripts/prepare_prism_gui_world.py`, Minecraft 1.21.1 datapack functions, Prism offline runner, `unittest`, Gradle/NeoForge gates.

---

## File map

- Modify `scripts/prepare_prism_gui_world.py`: void NBT rewrite, template-state stripping, lab constants, datapack bodies, manifest.
- Modify `scripts/test_prepare_prism_gui_world.py`: representative worldgen fixture and focused behavior tests.
- Modify `scripts/run_prism_gui_session.py`: current station terminology and void-baseline checklist language only.
- Modify `scripts/test_run_prism_gui_session.py`: static checklist expectations.
- Modify `docs/notes.md`, `docs/plan.md`, `docs/roadmap.md`, `docs/structure.md`: current workflow, coordinates, progress, file map.

### Task 1: Lock the true-void world contract

**Files:**
- Modify: `scripts/test_prepare_prism_gui_world.py`
- Modify: `scripts/prepare_prism_gui_world.py`

- [x] **Step 1: Add a realistic NBT fixture and failing rewrite test**

Extend `minimal_level_dat` with a `WorldGenSettings/dimensions/minecraft:overworld/generator` noise compound. Add:

```python
def test_update_level_dat_rewrites_overworld_to_true_void_flat_generator(self):
    mod = self.load_script()
    with tempfile.TemporaryDirectory() as tmp:
        level_dat = Path(tmp) / "level.dat"
        level_dat.write_bytes(minimal_level_dat())
        mod.update_level_dat(level_dat, "MagicStorageGuiTest", allow_commands=True)
        self.assertEqual(
            {
                "type": "minecraft:flat",
                "biome": "minecraft:the_void",
                "layers": [{"height": 1, "block": "minecraft:air"}],
                "features": False,
                "lakes": False,
                "structure_overrides": [],
            },
            mod.read_void_generator_summary(level_dat),
        )
```

- [x] **Step 2: Verify RED**

Run:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  scripts.test_prepare_prism_gui_world.PreparePrismGuiWorldTests.test_update_level_dat_rewrites_overworld_to_true_void_flat_generator
```

Expected: failure because the existing updater does not rewrite the generator and `read_void_generator_summary` does not exist.

- [x] **Step 3: Implement the minimal NBT rewrite**

Add helpers that require the existing compounds and replace the overworld generator with:

```python
void_settings = [
    (TAG_LIST, "structure_overrides", (TAG_STRING, [])),
    (TAG_LIST, "layers", (TAG_COMPOUND, [[
        (TAG_INT, "height", 1),
        (TAG_STRING, "block", "minecraft:air"),
    ]])),
    (TAG_BYTE, "lakes", 0),
    (TAG_BYTE, "features", 0),
    (TAG_STRING, "biome", "minecraft:the_void"),
]
generator = [
    (TAG_STRING, "type", "minecraft:flat"),
    (TAG_COMPOUND, "settings", void_settings),
]
```

`update_level_dat(path, level_name, allow_commands=True)` must always call this before writing. The summary reader must validate every exact field instead of filling defaults.

- [x] **Step 4: Verify GREEN and fail-closed behavior**

Add a second test that removes `WorldGenSettings` and expects `ValueError("WorldGenSettings")`. Run the two focused tests and expect 2 passes.

### Task 2: Strip mutable source-world state transactionally

**Files:**
- Modify: `scripts/test_prepare_prism_gui_world.py`
- Modify: `scripts/prepare_prism_gui_world.py`

- [x] **Step 1: Add the failing copied-state test**

Create source paths `region`, `entities`, `poi`, `data`, `datapacks`, `DIM-1`, `DIM1`, `dimensions`, `serverconfig`, `playerdata`, `advancements`, `stats`, `session.lock`, `level.dat_old`, and `icon.png`; place sentinels in each. Add a `Data/Player` compound to the source `level.dat`. After `prepare_world`, assert no copied path or embedded Player snapshot exists in the target while every source sentinel and source NBT remain unchanged.

- [x] **Step 2: Verify RED**

Run the focused test. Expected: copied `region` and other state still exist in the target.

- [x] **Step 3: Implement minimal stripping**

Define the exact tuple:

```python
COPIED_RUNTIME_PATHS = (
    "region", "entities", "poi", "data", "datapacks",
    "DIM-1", "DIM1", "dimensions", "serverconfig",
    "playerdata", "advancements", "stats",
    "session.lock", "level.dat_old", "icon.png",
)
```

After `copytree`, remove directories with `shutil.rmtree` and files with `unlink`, then remove `Data/Player` while rewriting the staged `level.dat`. Do not mutate the source and do not weaken the marker/open-world/swap rollback checks.

- [x] **Step 4: Verify GREEN**

Run all `test_prepare_prism_gui_world` transaction tests. Expected: pass.

### Task 3: Lock the laboratory, preload, and navigation contract

**Files:**
- Modify: `scripts/test_prepare_prism_gui_world.py`
- Modify: `scripts/prepare_prism_gui_world.py`

- [x] **Step 1: Replace old-rig assertions with failing lab assertions**

Assert:

```python
self.assertEqual([0, 80, 0], manifest["targets"]["storage_core"]["block"])
self.assertEqual([-1, 80, 0], manifest["targets"]["storage_terminal"]["block"])
self.assertEqual([1, 80, 0], manifest["targets"]["crafting_terminal"]["block"])
self.assertEqual("view_texture_gallery", manifest["hotbar_views"]["7"]["function"])
self.assertEqual("home", manifest["hotbar_views"]["8"]["function"])
self.assertEqual(8192, manifest["baseline"]["stored_items"]["minecraft:cobblestone"])
self.assertEqual("minecraft:crafting_table", manifest["baseline"]["installed_stations"]["5"])
self.assertTrue(all(value == 0 for value in manifest["baseline"]["energy"].values()))
```

Also assert exact setup commands for the platform, active blocks, six-unit spine, facing buses, isolated gallery, Core `inventory`/`machines`/`energy` NBT, and fixed player `item replace` commands.

- [x] **Step 2: Verify RED**

Run the datapack test. Expected: old Y=64 coordinates, no keys 7/8, no baseline manifest.

- [x] **Step 3: Implement immutable lab data and generated commands**

Add `LAB`, `TARGETS`, `GALLERY`, `BASELINE`, and `PLAYER_KIT` constants. The original plan preloaded Core block-entity payload; the 2026-07-17 repository revision supersedes that step. Generate setup with a plain Core so its server placement path creates a fresh repository record:

```mcfunction
fill -18 79 -12 18 79 12 minecraft:polished_blackstone_bricks outline
fill -17 79 -11 17 79 11 minecraft:smooth_stone
setblock -1 80 -1 magic_storage:import_bus[facing=west]
setblock 1 80 -1 magic_storage:export_bus[facing=east]
setblock 0 80 0 magic_storage:storage_core
```

`BASELINE` now records empty stored items/stations and zero energy. Station pairs, ingredients, fuels, and axes come from `PLAYER_KIT` and are exercised through normal terminal actions; no inline inventory/machine/energy NBT is written.

Place gallery samples two blocks apart. Keep the gallery disconnected from the active network.

- [x] **Step 4: Verify GREEN**

Run the focused datapack test. Expected: pass.

### Task 4: Make readiness and reset deterministic

**Files:**
- Modify: `scripts/test_prepare_prism_gui_world.py`
- Modify: `scripts/prepare_prism_gui_world.py`

- [x] **Step 1: Add failing bootstrap/reset tests**

Assert load calls a dedicated `load` function, the timer objective is initialized once, tick reaches `player_ready` only at `ms_gui_timer=3..`, `player_ready` calls `prime_hotbar_latch`, hotbar `9` calls `reset_from_hotbar`, and reset calls the same `setup` without directly calling `player_ready`.

- [x] **Step 2: Verify RED**

Expected: old load tag points to setup and reset immediately invokes player-ready.

- [x] **Step 3: Implement minimal load/tick/latch flow**

Use:

```mcfunction
# load
scoreboard objectives add ms_gui_timer dummy
function magic_storage_gui_test:setup

# tick
execute as @a[tag=!ms_gui_ready] unless score @s ms_gui_timer matches 0.. run scoreboard players set @s ms_gui_timer 0
scoreboard players add @a[tag=!ms_gui_ready] ms_gui_timer 1
execute as @a[tag=!ms_gui_ready,scores={ms_gui_timer=3..}] run function magic_storage_gui_test:player_ready
execute as @a[tag=ms_gui_ready] run function magic_storage_gui_test:hotbar_views
```

Generate `prime_hotbar_latch` from `HOTBAR_VIEWS` and call it before adding `ms_gui_ready`.

- [x] **Step 4: Verify GREEN**

Run the focused bootstrap tests, then all preparer tests.

### Task 5: Keep runner/checklist and active docs current

**Files:**
- Modify: `scripts/test_run_prism_gui_session.py`
- Modify: `scripts/run_prism_gui_session.py`
- Modify: `docs/notes.md`
- Modify: `docs/plan.md`
- Modify: `docs/roadmap.md`
- Modify: `docs/structure.md`

- [x] **Step 1: Write failing runner assertions**

Replace `Installed Machines` expectation with `Installed Stations`; require the checklist to mention the true-void ready baseline, hotbar `7` gallery, `8` overview, and `9` atomic reset.

- [x] **Step 2: Verify RED**

Run `scripts.test_run_prism_gui_session`. Expected: old terminology and missing void controls.

- [x] **Step 3: Update runner text and docs**

Do not add fixed GUI automation. Document the generator, full coordinate table, baseline, timer-based readiness, and reset. Mark the previous terrain rig superseded, not still active.

- [x] **Step 4: Run Python suite**

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts
```

Expected: all tests pass with an increased count.

### Task 6: Full verification and real-client replacement

**Files:**
- Runtime only: Prism `MagicStorageGuiTest` and `build/gui-runs/`

- [x] **Step 1: Run project gates**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew build --console=plain --no-daemon
./gradlew runGameTestServer --console=plain --no-daemon
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts
./gradlew runData --console=plain --no-daemon
git diff --check
```

Expected: build success, SelfTest 40608/40608, GameTest 222/222, all Python tests pass, no datagen drift.

- [x] **Step 2: Stop only the current runner-started Prism/Minecraft tree**

Use the exact recorded PIDs/process ancestry; send TERM, wait conditionally, then KILL only if still alive. Do not close unrelated Terminal or user applications.

- [x] **Step 3: Rebuild and inspect without launching**

Run `prepare_prism_gui_world.py`, verify the manifest's void summary, confirm target runtime directories came only from the new client load, and confirm the active exact 0.1.15 jar hash still matches `build/libs`.

- [x] **Step 4: Launch the visual scenario offline**

```bash
PYTHONDONTWRITEBYTECODE=1 python3 scripts/run_prism_gui_session.py --scenario crafting-fuel-page
```

Wait for current-run SelfTest, Patchouli 17 entries, and `MS_GUI_TEST_READY`; reject every non-whitelisted error. Keep Minecraft open for the user and report the new checklist path. GUI remains unverified until the user passes fullscreen and gives a verdict.

- [x] **Step 5: Commit implementation and docs**

Review the complete diff and commit only related files with required HAPI trailers. Do not push unless requested.
