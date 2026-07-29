# Shared Terminal Platform, EMI-First Recipes, and Axe Energy Design

> Partial replacement: visual assets, Transform/Stations layout, cycle reset/status grammar, progression recipes, wrench/connected casing, and Patchouli guide are now specified by `2026-07-14-connected-progression-fuel-guide-design.md`. Server-authoritative crafting/storage/EMI/Axe transaction contracts below remain current.

## Goal

Replace the split Storage/Crafting Terminal presentation with one capability-driven terminal platform, present selected recipes through EMI widgets when EMI is available with an explicit native fallback, correct Craftable-grid quantities and sorting, add a safe craft-output destination, and replace the installed axe with a consumable finite-or-infinite Axe Energy reserve.

## Chosen architecture

Storage Terminal and Crafting Terminal remain distinct menu/screen registrations, but both use one shared terminal shell. The shell owns adaptive geometry, panel rendering, the item grid, search, scrolling, amount overlays, left-rail controls, focus cleanup, hit testing, and EMI exclusion bounds. A terminal profile enables only the capabilities that terminal exposes:

- Storage Terminal: Storage page plus shared search, sorting, order, extraction, and player inventory.
- Crafting Terminal: Storage, Craftable, Transform, and Stations pages plus recipe selection, resource preview, ingredient-source toggle, output-destination toggle, deterministic resource targets, stations, and craft actions.
- Future terminals: compose a profile from the same capabilities instead of copying a screen or creating then hiding inherited widgets.

`TerminalLayout` has one profile-driven entry point. Rendering, widgets, reconstructed slots, mouse regions, tooltips, scrolling, popups, and EMI exclusions consume the same immutable geometry. The old separate Storage layout/control path and Crafting-only replacement rail are removed.

## Shared rail and interaction contract

All rail controls use one 18-pixel button and one 16-pixel icon system. Page controls and view controls are separated by the existing group gap. Semantic Minecraft item icons are preferred for pages and destinations; abstract operations use a single pixel-art sprite atlas.

The common cycle contract is:

- left click selects the next value;
- right click selects the previous value;
- wheel down selects next and wheel up selects previous when the control is hovered;
- the tooltip names only the control and current value;
- it never enumerates every possible value.

Recipe alternatives use two explicit left/right arrow buttons rather than a cycle glyph. Search, sorting, and player-inventory-source icons are redrawn; procedural Unicode and filled-shape rail glyphs are removed.

## Sorting and grid amounts

The shared sort modes are Name, Quantity, Mod, and ID.

- Name sorts by display name with registry ID as a deterministic tie-breaker.
- Quantity sorts by the exact server-owned amount.
- Mod sorts by registry namespace, then display name, then registry path.
- ID sorts by the complete registry identifier.

Storage and Craftable use the same comparator implementation.

Every visible network entry has a logical `ItemKey` and an exact display amount independent of `ItemStack#getCount`. Craftable entries carry the currently stored Core amount, not the number that could be crafted. A craftable output remains listed whenever at least one supported recipe can currently be crafted, including when that output is already stored. Zero stored amount leaves the icon visible but draws no count; positive amounts draw the exact compact representation.

The display amount travels as server-owned metadata on synthetic display stacks so it shares vanilla slot ordering and does not require a client-side storage mirror. One helper creates, reads, and strips this metadata; synthetic stacks remain non-extractable and the metadata must never become part of an `ItemKey` or a real inserted/output stack.

All network counts use one screen-wide font scale derived from the widest permitted compact string and the 16-pixel slot bound. The renderer no longer switches individual values between normal and half size. Exact long values remain available in tooltips.

## Recipe presentation

The server resolves the selected exact recipe and synchronizes a presentation model; the screen never scans `RecipeManager` or Core state. The model contains:

- exact recipe identity and supported presentation kind;
- shaped width/height, shapeless state, and up to nine positioned input representatives;
- exact output stack including per-craft output count;
- station identity;
- aggregated item-resource rows with available and required-for-one amounts;
- process, fuel, or tool-resource rows with available and required-for-one amounts.

The workspace is split vertically:

1. an EMI-style recipe diagram at the top;
2. a compact resource ledger below it;
3. recipe navigation, output destination, and craft-amount actions in the footer.

Crafting uses a positioned 3x3 grid, a processing arrow, a large output slot, and a shapeless marker where applicable. Cooking, stonecutting, smithing transform/trim, and synthetic axe transformations have dedicated presentations matching their real input roles. Smithing Trim reuses the three-role Smithing presentation but carries the selected source-backed exact output identity. The output slot displays the exact result count. Empty space is not divided into nine fixed resource cells.

The selected station is shown once as a subdued lower-right badge so it supplies context without reading as another ingredient. Selecting an item with no supported recipe keeps that item selected and shows a neutral localized no-recipe message instead of an error state.

When no presentation exists, the diagram and ledger bounds form one continuous neutral empty-state surface and the wrapped prompt is centered in that union. The darker ledger divider appears only for a valid recipe with resource rows; an empty ledger must not look like a second unexplained panel.

All resource rows use vanilla light raised panels. Availability controls the green/red amount text; resource kind remains identifiable from its icon and tooltip rather than a custom dark background.

## EMI-first renderer with explicit fallback

EMI is a required client-only dependency in released metadata with Maven range `[1.1.24,2)`. The exact `1.1.24+1.21.1` coordinate remains only the reproducible minimum compile/development baseline; dedicated servers do not require EMI. CI compiles against that minimum and the newest compatible Minecraft 1.21.1 EMI release, while client smoke stages the newest compatible full jar.

The base screen references only a Auto Storage client renderer interface. A guarded EMI compatibility bootstrap is loaded only when NeoForge reports EMI present, keeping dedicated-server classloading free of EMI references and preserving an explicit failure boundary during development.

For a selected standard recipe:

1. the server-synced exact recipe ID is used to locate the corresponding client EMI recipe;
2. Auto Storage supplies a public-API `WidgetHolder` implementation;
3. `EmiRecipe#addWidgets` populates the top recipe diagram;
4. Auto Storage translates and renders those widgets, forwards bounded mouse/key input, and renders their tooltips;
5. the native server-synced resource ledger and craft controls remain authoritative.

The native renderer is selected only when the exact selected recipe has no compatible EMI representation, including internal synthetic axe recipes. A released client missing EMI is rejected by NeoForge dependency validation rather than silently changing product behavior. Runtime exceptions do not silently switch renderers; they surface through the normal client error path. No EMI internal screen or `WidgetGroup` class is linked.

## Craft output destination

Crafting Terminal has a server-synced session toggle independent of `Use Player Inventory`:

- Player: use the post-ingredient player inventory first and plan any remainder into Core, preserving the existing no-drop safety contract.
- Storage: put all primary output and crafting remainders into Core only.

The control uses a Player Head icon for Player and the Storage Core icon for Storage; its tooltip supplies the localized destination name.

If the selected destination cannot accept the complete batch, the entire operation is a no-op. Direct buttons, Max, and exact EMI requests continue to use simulate-then-commit and exact recipe identity. EMI's explicit Cursor/Inventory destination remains authoritative for an EMI-initiated action; the terminal toggle governs the terminal's own craft buttons.

## Station categories

Station descriptors are classified by behavior rather than represented by one undifferentiated machine slot table.

### Process machine

Furnace, Blast Furnace, Smoker, Campfire, and Brewing Stand are stackable installed machines. Each installed block contributes its declared process energy per tick. They remain removable.

### Instant station

Crafting Table, Stonecutter, and Smithing Table unlock recipes, generate no energy, allow exactly one installed block of each kind, and remain removable.

### Consumable tool energy

Axes are no longer installed equipment. An accepted axe is converted atomically into Axe Energy and the item is consumed. The system can therefore accept many axes over time. An accepted axe never remains in a retrievable station slot; a rejected insertion remains with the player.

Finite axe value is:

`remaining durability * (vanilla Unbreaking level + 1)`

Mending adds no value because the stored reserve has no XP repair process. Other enchantments do not change axe uses. Addition is checked before consumption; overflow rejects the axe instead of saturating and losing value.

An item carrying the Unbreakable component sets an explicit infinite Axe Energy flag. Infinite is not encoded as `Long.MAX_VALUE`: axe recipes do not decrement it, it persists in Core NBT, and recipe metadata carries a separate infinity bit so the UI displays `∞` without confusing a finite `Long.MAX_VALUE` reserve. Once infinite, further axes are rejected without consumption.

This design originally specified one-time migration from the old persisted axe station and stacked instant stations. The 2026-07-17 Core repository design supersedes that compatibility path: the current early-development schema does not read old inline machine slots. New transient axe input is still converted atomically, rejected stacks remain unchanged, and installable descriptors accept only their current maximum.

## Transform target selector and station hit boxes

GitHub #23 replaces the old target cycle and popup with a persistent left target list. Auto is exact-input Show Uses only: it lists every compatible use but never preselects, consumes, or executes one. Explicit targets filter uses of that same inserted item by produced target. The list has localized target labels independent of representative item names, search, paging, full-row hit boxes, selected state, wheel navigation, and middle-click reset.

Each visible use is a whole horizontal card. Clicking it selects a server-validated stable use ID; amount actions remain disabled until selection. The transient input and shared amount strip stay in the upper band. Direct Fuel cards omit the redundant `Direct` line and vertically center their produced resource/amount; timed cards use the second line for the required station/work source. There is no detached lower selected preview. Search, list page, card page, and selected card are session presentation state; only the existing validated Transform target preference persists.

Processing and Instant remain separate and derive rows from available height. Processing receives the larger workspace and up to three readable columns; every page keeps fixed column widths and left/top order even when the last row is incomplete. The installed aggregate count overlays the item while the separate horizontal value is accumulated work. Instant is a binary unlock: it has no installed-count decoration or work value and uses a compact icon grid. The inventory-side panel always shows type capacity and never changes on hover. Each station item keeps its normal item tooltip; hovering a Processing value shows only its localized resource name and current aggregate rate, never duplicated installed/stored totals. Transform and Stations keep separate session queries, render blank search fields in the shared top header position, and show only the entered query.

## Retired Bottle Energy migration

Bottle Energy is not a live target or recipe resource. Glass Bottles and Potions remain normal storage items. The 2026-07-17 Core repository schema intentionally does not read or migrate historical `bottle_fuel`/escrow NBT; the retired four-word menu data region remains zero-filled only to preserve client/server data-slot parity.

## Bus direction contract

Import Bus keeps its directional active pull from the front-adjacent non-network inventory. It also exposes one stable insert-only item capability on every face and the null side so external automation can passively insert into the server-owned Core. Passive insertion stores no local stack, never permits extraction, never mutates the caller stack, and uses simulate-then-commit with the exact remainder. Active and passive resolution share a 10-tick negative cache after a missing-Core BFS so repeated automation probes cannot rescan the bounded network every call.

Export Bus remains directional: it pushes only into the inventory on its front face. A future directionless export mode is outside this revision.

## Texture and icon system

Runtime block/item textures remain native 16x16 pixels. The next asset pass uses family generation rather than independent prompts:

1. select one shared dark deepslate/blackstone chassis and one cyan/amethyst palette;
2. reuse the selected family image as the Replicate img2img reference with fixed seed/settings;
3. apply role-specific semantic motifs and a final pixel cleanup;
4. review one nearest-neighbor contact sheet before selecting runtime files;
5. retain generation metadata/previews only under `art/texture-generation/`.

The semantic families are Core rune crystal; Storage Unit cell plus tier bars; Storage Terminal 2x2 item-grid display; Crafting Terminal 3x3 crafting display; cyan inward Import flow; orange outward Export conduit; and handheld Remote display. Referenced faces must be visually distinct while retaining the common casing. Bus front/side/top faces must communicate their flow direction without depending on color alone.

Every centered block motif is mirrored about the even-grid axis at x=7.5 instead of pretending a single center pixel exists. Storage tiers use six visibly different fill bands. The centered Name-sort A and all other atlas glyphs use measured bounding boxes on their fixed 16x16 cells. Obsolete unreferenced runtime textures and all non-16x16 model-referenced textures are removed.

All player-facing runtime labels and tooltips use localization keys, with exact English and Traditional Chinese key parity. Player-facing controls follow the project-wide simple-is-better rule: labels communicate state and intent, while discoverable mouse gestures and implementation details stay in the guide rather than persistent tooltips.

## Testing and delivery

Every production behavior starts with a focused failing SelfTest, GameTest, or Python static test and is implemented only after the expected RED result is observed.

Required coverage includes:

- shared Storage/Crafting profile geometry, rail construction, click direction, focus, tooltip, and EMI exclusion contracts;
- Craftable zero/positive/long stored amounts, stored-and-craftable union, synthetic no-extraction, and metadata stripping;
- shared Name/Quantity/Mod/ID ordering and deterministic ties;
- shaped, shapeless, cooking, stonecutting, smithing, and axe presentation metadata plus exact output count;
- unified neutral no-recipe surface, one dim station badge, destination-specific icons, and foreground popup ordering;
- EMI-present public-widget path, no-EMI development/dedicated-server classloading boundary, known unsupported-recipe native path, and no broad exception fallback;
- Player/Core destination capacity, remainders, direct craft, Max, exact EMI requests, rollback, single-mutation long-count commits, and `Long.MAX_VALUE` reservations;
- process stack rates, instant station max-one, finite/multiple/Unbreaking/overflow axes, explicit finite-vs-infinite presentation, repository save/load, raw unavailable-entry retention, and deliberate legacy-format rejection;
- exact station hover bounds, scalable popup geometry/input, and no rail overlap;
- retired Bottle Energy removal, deliberate no-migration behavior, live-energy reset, and preserved zero-filled data-slot parity;
- directional active buses plus all-side insert-only passive Import capability, atomic remainder handling, and no caller mutation;
- English/Traditional-Chinese localization parity;
- one count scale, slot-local text bounds, centered Name glyph, 16x16 x-symmetric referenced textures, semantic asset families, and no generation artifacts in runtime resources.

Final gates are compileJava, build, dedicated GameTest server, all Python tests, runData plus datagen drift, JSON/model/texture checks, automatic patch-version bump, transactional Prism deployment, and a current-run fullscreen visual checklist owned by the user.

## Out of scope

- RS2 recursive pattern trees, crafting jobs, or crafting monitors.
- Bundling EMI in the Auto Storage jar or requiring it on dedicated servers.
- Client-side storage authority or a complete client mirror of the network inventory.
- Arbitrary modded durability-effect valuation without a future server-owned descriptor API.
- Hot descriptor registration after the NeoForge registry has frozen.

The first item remains future work rather than a rejected product goal; its architecture and TDD phases are defined in `../plans/2026-07-14-modded-recipes-multistep-crafting.md`. Third-party descriptors now register through the server-owned NeoForge registry during normal mod loading; post-freeze hot registration remains unsupported.
