# Connected Storage Family, Transform/Stations Workspace, Progression, and Guide Design

> Current replacement: GitHub #23 supersedes this document's Fuel Target cycle/list-button and fixed station-row presentation with the persistent Transform target sidebar/use cards and height-adaptive Stations contract. Progression, textures, wrench, recipes, and guide principles below remain current.

## Goal

Turn the functional baseline into a coherent player-facing release: storage tiers become visibly more ornate, adjacent network blocks share connected casing, every tagged wrench can rotate or safely dismantle the network, deterministic conversions live on Transform, installed machines live on a two-group Stations page, terminal controls follow one reset/status grammar, recipes form an affordable progression, and the Patchouli guide explains the complete system in English and Traditional Chinese.

This document replaces the visual, Fuel-layout, recipe-cost, and guide-writing parts of `2026-07-14-terminal-platform-emi-recipe-axe-design.md`. The server-authoritative storage, crafting transaction, menu parity, EMI, and Axe Energy contracts from that design remain unchanged.

## 0.1.17 fullscreen verdict

The automated 0.1.17 gate passed, but the current fullscreen user review failed. Evidence is stored under `build/gui-runs/20260714-172548-crafting-fuel-page/user-feedback/`.

Observed failures:

- the empty recipe prompt loses the final letter at the current width;
- the recipe amount actions look like four unrelated vanilla buttons rather than one terminal control strip;
- Craft Output: Storage receives a blue status marker even though it is a value selector, not an on/off toggle;
- the Fuel page mixes process machines, instant stations, and Axe Energy under `Stations & Axe Energy`;
- the mixed two-row machine panel and separate reserve/control panels leave unbalanced dead space;
- storage tiers remain differentiated mainly by capacity bars rather than progressively richer ornament.

No automated result may overwrite this failed visual verdict. A later version needs a new current-run fullscreen review.

## 0.1.19 fullscreen verdict and replacement composition

The 0.1.19 automated gate also passed, but the fullscreen user review failed. The two review captures show that the prior change altered panel ownership without changing the visual composition enough:

- the recipe diagram is followed by a single material row and then a large unexplained white ledger area;
- the pale workspace, pure-white bevel edges, and detached light amount strip conflict with the dark terminal block family;
- the Fuel Target selector consumes most of the Consumables header and visually competes with the category title;
- category names sit above their content, leaving three oversized horizontal bands with weak item-to-section grouping;
- empty timed-station slots are anonymous, and the Instant Stations panel reaches the player-inventory label band;
- the long `types` status text is treated as a sentence inside a compact item cell instead of a compact status value.

The later dark-workspace replacement was also rejected. The current visual contract keeps the successful compact geometry but uses Minecraft's vanilla container grammar throughout:

- workspace and semantic regions use light raised panels and inset wells;
- slots use the vanilla light slot-frame treatment;
- labels use standard dark-gray container text;
- navigation, amount actions, Fuel Target, and the list button use vanilla widget rendering and states;
- block-family deep-blue, cyan, slate, and dark-red palettes belong to world/item textures and must not define GUI surfaces.

The recipe body is a compact card above the fixed footer. Its diagram remains above its resource ledger. Ledger resources are top-aligned, use at most four columns, and add a third row when more than eight resources are present; current wire bounds allow up to twelve total item/energy/tool rows. Narrow layouts reserve the same three-row capacity while preserving the diagram, footer gap, and non-overlap guarantees.

The current replacement separates two different actions. Transform owns one transient input, Auto/exact target selection, and explicit ×1/×8/×64/Max conversion into typed resources. Stations owns exactly two top-aligned groups—Processing and Instant—with horizontal representative item plus installed amount, independent previous/next controls, and panel-local wheel paging. Empty station slots show a dim representative item behind the real slot. Stored types over total type capacity remains in the independent panel beside the player inventory.

## Fullscreen execution contract

The visual runner enables Minecraft F11 automatically through prepared `options.txt`. On macOS, `MacOsWindowMixin` changes that F11 path into a borderless Cocoa window: it hides Dock/menu bar but never attaches GLFW to a monitor or selects a display mode. The preparer removes stale `fullscreenResolution`, keeps 1280×720 only as windowed fallback, and records the desktop point/pixel/refresh/depth mode; the runner compares the recorded mode at READY and fails closed on any difference. macOS native fullscreen (green button or Control-Command-F) and combined native+Minecraft fullscreen are forbidden. The user still owns the visual gate and must confirm that the whole frame, hotbar, and GUI edges are visible before interacting. Normal close is F11 → a visible titled windowed window → Command-Q; direct Command-Q from F11 is forbidden, and `shutdown.json` records whether the exact-PID watchdog had to finish the test client. See `docs/macos-fullscreen-guide.md`.

## Research decisions

### Connected textures

Vanilla/NeoForge JSON models do not derive a face texture from adjacent block state. Implementing a project-owned custom model loader would add a large client rendering subsystem solely for this visual feature. Fusion already provides a documented connecting model/texture format and supports a five-tile `pieced` layout, so Magic Storage will use Fusion as an optional client enhancement instead of reimplementing that renderer.

The supported dependency is pinned to Fusion `1.2.12-neoforge-mc1.21.1`. Fusion 1.3.x requires a newer NeoForge build than the project and current Prism instance. Fusion is absent from both required client and dedicated-server requirements. The Prism deployment flow downloads the exact official Modrinth artifact, verifies its SHA-512, installs it atomically, and installs the exact version for visual validation, while released clients without Fusion use the vanilla fallback. The Fusion jar is never copied into this repository or bundled inside the Magic Storage jar.

The pinned 1.2.12 source and jar, not the current 1.3 wiki/examples, define the model schema. Its `match_block` predicate accepts exactly one `block` string. Cross-role casing therefore uses an array of eleven single-block predicates, which Fusion combines with OR. The 1.3-only `blocks` field and `true`/`false` predicates are forbidden. Bus top and side faces receive the predicate; their ordinary front texture needs no false predicate. The generated NeoForge metadata does not declare Fusion as a requirement. Main resources remain vanilla; a client-only built-in resource-pack overlay, registered only when `ModList` finds Fusion, supplies the connecting models and sheets. Development uses an isolated `fusionRuntime` source set only for the Gradle client and data runs; the normal main runtime, server, and GameTest runs remain Fusion-free. A mod jar must not be placed in ModDevGradle's `AdditionalRuntimeClasspath`, because that legacy path is for ordinary libraries and does not make Fusion discoverable as a mod.

Connected faces use Fusion's `pieced` layout. Item models keep ordinary 16x16 textures so inventory rendering never depends on neighboring blocks. Functional faces remain readable:

- Core, Storage Units, and terminal casing borders connect to any Magic Storage network block;
- Import/Export Bus side and top casing connect, while their directional front keeps the inward/outward flow motif;
- center motifs do not merge across blocks and continue to identify each role and tier.

### Wrench interoperability

AE2 and Refined Storage 2 both use the conventional `c:tools/wrench` item tag. Magic Storage therefore adds its own early-game Wrench and also accepts every item in that tag. Players who already have a compatible mod wrench do not need to craft another one.

Interaction grammar:

- main-hand right-click on a directional Magic Storage block rotates it around the clicked face;
- main-hand sneak-right-click on any Magic Storage network block dismantles it;
- non-directional blocks return `PASS` for ordinary wrench use instead of inventing a fake orientation;
- Export Bus wrench use is handled before filter assignment;
- spectator/off-hand/client-side execution never mutates the world.

Dismantling uses the block's normal loot path. This design originally preserved a Storage Core through inline `BLOCK_ENTITY_DATA`, then briefly used a full `CoreRecoverySavedData` snapshot. The current repository design makes `CoreStorageRepository` the permanent payload owner from Core creation onward; a populated Core drop carries a one-time capability that reattaches the same storage/network record without copying it. Drops are offered to the player's inventory first and any exact remainder is spawned at the block; nothing is deleted when the inventory is full. The operation removes the block only after the drop list has been obtained. The wrench itself is not damaged because the common tag does not define a shared durability protocol.

## Storage tier visual language

All runtime block/item textures remain native 16x16 pixel art and use the existing dark chassis plus cyan/amethyst family palette. Tier identity must not be a six-step fill meter.

The center ornament grows in structural complexity while staying horizontally mirrored around `x=7.5` and aligned to an even-grid 2x2 center. Vertical silhouettes may change by tier and are not forced into false top/bottom symmetry:

1. T1: one simple copper-bound storage cell;
2. T2: reinforced iron side braces and a second inner node;
3. T3: a gold/lapis lattice around the cell;
4. T4: a diamond/quartz cross-frame;
5. T5: a prismarine/ender halo with four balanced anchors;
6. T6: a netherite/amethyst crown circuit with the richest silhouette and highlight count.

Each adjacent pair must differ outside the old capacity-bar region. Automated asset tests measure declared ornament masks, bilateral symmetry, semantic accent colors, and monotonically increasing ornament detail. The test intentionally avoids equating higher cost with merely more lit pixels in one vertical bar.

## Terminal interaction grammar

### Status markers

Only binary on/off controls use a colored status light. `Use Player Inventory` may show the light because it is a boolean. Sort mode, sort order, Search Sync, Fuel Target, and Craft Output are value selectors and never show a green/blue on-state marker. Page tabs use a neutral selected-tab treatment, not an on/off light.

### Cycle controls and reset

Every cyclic value selector follows one shared contract:

- left click: next;
- right click: previous;
- wheel down/up: next/previous while hovered;
- middle click: reset to that control's documented default.

Defaults are Name sort, Ascending order, Search Sync Off, Auto Focus On, Auto Fuel Target, Player craft output, and the existing default for player-inventory ingredient sourcing. Middle reset is sent as an explicit server-approved action; the client never changes session state locally.

Tooltips remain concise: control name plus current value. Mouse gesture instructions belong in the guide, not persistent UI text.

### Empty recipe prompt

When no supported recipe is selected, the diagram and ledger bounds become one neutral prompt surface; the screen must not leave a darker empty ledger frame below the message. The prompt is centered using wrapped text within an inset rectangle spanning that union. It may use two lines at narrow widths and must never be truncated with `plainSubstrByWidth`. English and Traditional Chinese are tested at every supported narrow/wide layout breakpoint.

### Recipe amount strip

Previous/next recipe arrows remain a separate navigation group. `x1`, `x8`, `x64`, and `Max` remain equal-width actions and use normal vanilla button rendering, including hover, focus, and disabled states.

## Transform and Stations workspaces

The Crafting Terminal has four pages: Storage, Craftable, Transform, and Stations. The left rail keeps a clear gap between page selection and item-page controls.

### Transform

Transform is a deterministic resource-conversion shortcut, not a machine inventory and not an automatic background job. It has one server-owned transient input, the persistent searchable Auto/exact target sidebar defined by GitHub #23, exact-input use cards, and the shared ×1/×8/×64/Max strip. Auto discovers every compatible use but never preselects or consumes one. Closing or leaving the page returns every unconverted input.

Fuel, Brew Energy, Axe Energy, FE, Mana, and addon-defined resources are ordinary typed ledger entries visible through Storage resource groups. Runtime Fuel uses current burn time; tool transforms use exact remaining durability; optional providers may require installed Processing work before conversion. Every conversion simulates input, typed output, work, capacity, container remainder, and overflow before one atomic commit.

### Stations

Stations has exactly two groups:

- **Processing Stations** are stackable exact variants with provider-derived rational work rates. The persisted item prototype remains count one while the aggregate installed count is stored separately up to `Integer.MAX_VALUE`.
- **Instant Stations** accept at most one per descriptor and only unlock matching recipes. They do not display made-up work or energy.

Each group uses a horizontal representative item plus installed amount, explicit previous/next controls, and panel-local wheel paging. Search Stations replaces both groups with one result grid containing stations only; clearing search restores the two paged groups. Hovering an installed row reports the exact variant and combined decimal rate. Shift-click routing uses the exact descriptor regardless of the current group page. A dim representative behind an empty real slot explains what belongs there without pretending it is installed.

The player-inventory-side type-capacity panel remains independent. The Prism scenario preloads every repeatable resource and station; the only player item is one Coal in hotbar 3 because the Transform visual check itself needs an input.

## Recipe progression

The progression is measured as player milestones rather than making each tier eight times more expensive.

### Starter milestone

A Storage Core, Storage Terminal, and two T1 units require one diamond total and ordinary overworld materials: black concrete powder, obsidian, copper, redstone, glass, and wood; T1 does not require amethyst. A player should be able to build this after one or two focused mining trips.

### Functional midgame milestone

A Crafting Terminal, Wrench, Import Bus, Export Bus, Remote Terminal, and T1-T3 storage require no Netherite, Ancient City loot, Trial Chamber loot, or End completion. T1-T4 remain available from Overworld materials; T5 begins the ocean/Ender stage and T6 is the Nether prestige tier. This lets a normal midgame base use every core storage/automation function without first completing late-game exploration.

### Optional upper tiers

T4-T6 are capacity and prestige upgrades, not gates for terminal or automation features. They use increasingly magical modern-vanilla materials without the previous eight Diamond Blocks/eight Netherite Ingots wall.

Exact recipes:

| Output | Key cost |
| --- | --- |
| Storage Core | 4 Obsidian, 2 Copper Ingots, 2 Redstone, 1 Diamond |
| Storage Terminal | 2 Glass Panes, 1 Black Concrete Powder, 2 Redstone, 1 Redstone Torch, 1 Chest, 2 Copper Ingots |
| Crafting Terminal | Storage Terminal + Crafting Table + 4 Redstone + 2 Copper Ingots + 1 Gold Ingot |
| Remote Terminal | Crafting Terminal + 2 Obsidian + 1 Ender Pearl + 2 Redstone + 1 Compass |
| Wrench | Brush + Tripwire Hook |
| Guide Book | Book + Black Dye + Copper Ingot |
| 2x T1 | 4 Black Concrete Powder, 2 Copper Ingots, 2 Redstone, 1 Barrel |
| T2 | T1 + 4 Basalt, 2 Iron Ingots, 2 Redstone |
| T3 | T2 + 4 Obsidian, 2 Gold Ingots, 2 Lapis Lazuli |
| T4 | T3 + 4 Nether Quartz, 2 Diamonds, 2 Slimeballs |
| T5 | T4 + 4 Prismarine Crystals, 2 Honeycomb, 2 Ender Pearls |
| T6 | T5 + 4 Amethyst Blocks, 2 Netherite Scraps, 2 Eyes of Ender |

Storage Terminal remains part of the first network but now has an explicit redstone-torch control element and two copper contacts. Crafting Terminal is an early-midgame functional upgrade using gold, copper, redstone, and a Crafting Table rather than a nearly free shapeless conversion. Remote Terminal is a later mobility upgrade: it retains the Crafting Terminal, adds Ender travel, Obsidian protection, and a Compass identity. The Wrench deliberately stays cheap because safe rotation and dismantling are setup tools, not progression rewards.

Recipe regression tests verify the exact JSON matrices, all referenced item IDs, starter total budget, midgame forbidden-item list, upgrade-chain continuity, and the absence of Diamond Blocks or Netherite Ingots from Storage Unit recipes.

## Patchouli guide rewrite

The guide is rewritten as a player manual rather than a collection of implementation notes. English and Traditional Chinese have identical category/entry/page structure and recipe references. A dedicated Recipe Catalog owns every recipe display exactly once so recipes are directly discoverable instead of scattered across prose entries.

Book structure:

1. **Getting Started**
   - what Magic Storage does;
   - starter shopping list and first network walkthrough;
   - adjacency, one-Core rule, and the first storage/terminal test;
   - Wrench controls and safe moving.
2. **Storage**
   - type capacity versus item quantity;
   - Core persistence and safe dismantling;
   - T1-T6 progression, ornament language, and upgrade recipes;
   - connected casing and that Fusion is an optional visual enhancement.
3. **Terminals**
   - Storage/Craftable/Transform/Stations tabs;
   - exact amounts, searching, Name/Quantity/Mod/ID sorting;
   - left/right/wheel/middle-reset grammar;
   - recipe preview, amounts, Max, player/storage output, and EMI behavior.
4. **Transform and Stations**
   - explicit Transform input, Auto target discovery, and typed-resource outputs;
   - runtime Fuel values, tool durability, timed provider work, and atomic rollback;
   - Processing and Instant station groups with paging and search;
   - Unbreaking and Unbreakable Axe Energy behavior.
5. **Automation and Remote Access**
   - active and passive Import behavior;
   - directional Export and filters;
   - wrench rotation;
   - Remote binding and loaded-network limits.
6. **Troubleshooting and Reference**
   - conflicted/missing Core;
   - full type capacity;
   - unsupported/dynamic recipes;
   - output capacity and atomic no-op behavior;
   - concise glossary and progression table.

Where an in-game recipe exists, the guide uses Patchouli recipe pages referencing the actual Magic Storage recipe ID. Player text does not mention BFS, packets, hidden slots, implementation class names, wire parity, or test totals.

## Testing and delivery

Every production change starts with a focused failing SelfTest, GameTest, or Python unittest and the expected RED is recorded before implementation.

Required automated coverage:

- four non-overlapping Crafting Terminal pages at representative narrow/wide/fullscreen sizes;
- Transform input/target/action geometry plus two horizontal Stations groups with independent paging, exact hover bounds, and unchanged menu parity;
- player-inventory-side type-capacity panel remains contained and non-overlapping as station counts or pages grow;
- middle-click reset for every cyclic selector with explicit server defaults;
- no output-destination status light and no value-cycle status lights;
- wrapped empty prompt across a unified diagram/ledger surface, no empty ledger frame, and an integrated segmented craft strip;
- any `c:tools/wrench` item rotates buses and sneak-dismantles every network block;
- Core dismantle preserves and moves the exact server-owned repository record without a payload snapshot; full inventory produces exact world overflow without voiding;
- optional-Fusion metadata, exact Prism artifact/hash, vanilla fallback plus overlay model/mcmeta validity, ordinary item models, and dedicated-server independence;
- progressively ornate symmetric tiers with differences outside the retired capacity meter;
- exact progression recipe and material-budget contracts;
- Patchouli en_us/zh_tw category/entry/page parity, valid recipe IDs, required topics, and banned implementation language.

Final gates are `compileJava`, `build`, dedicated `runGameTestServer`, all Python tests, `runData` plus drift check, resource/model/texture validation, automatic patch bump, transactional Prism/Fusion deployment, fixed void-world boot, and a new user-owned fullscreen visual checklist.

## Out of scope

- A public runtime API for third-party station/fuel descriptors. This remained outside this design revision and was implemented later as the registry-time API in `docs/machine-descriptor-api.md`.
- Directionless Export behavior.
- Replacing EMI's recipe widgets or making EMI authoritative for crafting.
- Recursive RS2-style pattern autocrafting.
- Bundling Fusion or copying its source/assets into this repository.
