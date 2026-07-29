# Dynamic Fuel, Craftable Catalog, EMI Magic Crafting, and Adaptive UI

> Status: Tasks 1–9 implemented by strict RED → GREEN TDD and deployed as the historical 0.1.14 baseline. Current station/tool, expanded-recipe, output-safety, Fuel-layout, and texture work extends this contract in `2026-07-13-stations-recipes-output-textures.md`; 0.1.15 final automated gates are complete, while Prism deployment/fullscreen verdict are deferred.
>
> Replaces the 0.1.8 user-visible fuel naming, fixed fuel-value table, fixed cooking-cost table, and fixed Crafting Terminal geometry. Internal energy IDs and saved-data keys remain stable.

## Goals

1. The user-facing `furnace_fuel` pool is named **Fuel**, not Cooking Energy.
2. Every furnace-compatible stack accepted by NeoForge/vanilla can enter the Fuel input; an oak log is the minimum acceptance example.
3. Cooking recipes consume their own current datapack `cookingtime`, not a hardcoded per-type number.
4. Craftable mode lists currently craftable recipe outputs even when the output count in storage is zero; stored logs can therefore expose charcoal.
5. EMI can select and execute one-level Auto Storage crafting by exact recipe identity without requiring the output to be stored or visible.
6. The Crafting Terminal uses one adaptive geometry source and a restrained vanilla-container visual language instead of scattered fixed coordinates.
7. A selected recipe always shows every required item plus any process/Fuel requirement as **available / required for one craft**; the overall ready count remains visible.
8. Remove Compact Grid completely. Grid identity remains full `ItemKey`, and screen size adapts automatically without a user mode toggle.
9. Replace Items + Craftable Only with explicit **Storage / Craftable / Fuel** pages, visually separate page navigation from page controls, and use semantic search glyphs.
10. Make Fuel content count-driven and overflow-safe for future machine/energy integrations; remove exposed production formulas and all shadowed status text.
11. Quantity buttons reflect the live server preview: impossible exact amounts are dimmed, and **Max** commits the largest currently legal exact craft count.

## Researched Reference Patterns

- NeoForge exposes `ItemStack#getBurnTime(recipeType)` as the runtime fuel truth; zero means the stack is not fuel. Use this API rather than duplicating vanilla/modded fuel tables.
- NeoForge cooking recipes carry arbitrary `cookingtime` values in ticks. Preview and execution must read the concrete recipe's value from the current server `RecipeManager`.
- Refined Storage 2 computes visible rows from available screen height, keeps fixed header/footer bands, stretches the middle in 18-pixel rows, and reports the resized geometry to the menu.
- Tom's Simple Storage keeps controls outside the main frame and wraps the rail into another column when vertical space is insufficient.
- EMI communicates `Type`, `Destination`, and `amount`; Refined Storage's EMI integration supplies recipe intent to a server-authoritative storage menu rather than trusting client inventory assumptions.

Reference source URLs:

- https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/common/extensions/IItemStackExtension.html
- https://docs.neoforged.net/docs/1.21.1/resources/server/recipes/builtin/
- https://docs.neoforged.net/docs/1.21.1/resources/server/recipes/
- https://github.com/refinedmods/refinedstorage2/blob/develop/refinedstorage-common/src/main/java/com/refinedmods/refinedstorage/common/support/stretching/AbstractStretchingScreen.java
- https://github.com/tom5454/Toms-Storage/blob/master/NeoForge/src/platform-shared/java/com/tom/storagemod/screen/CraftingTerminalScreen.java
- https://github.com/emilyploszaj/emi/blob/1.21/xplat/src/main/java/dev/emi/emi/api/recipe/handler/EmiCraftContext.java
- https://github.com/refinedmods/refinedstorage-emi-integration/blob/develop/refinedstorage-emi-integration-common/src/main/java/com/refinedmods/refinedstorage/emi/common/CraftingGridEmiRecipeHandler.java

Patterns are references only; no source is copied verbatim.

## Product Contracts

### 1. Fuel truth and names

- Internal `EnergyType.FURNACE_FUEL`, serialized ID `furnace_fuel`, menu encoding, and old world data do not migrate.
- Every user-facing label for that pool becomes **Fuel**. The Fuel-page target is `Auto / Fuel / Brew / Bottle`.
- The authoritative Fuel value for an `ItemStack` is `stack.getBurnTime(null)` at the moment of server validation.
- A positive runtime burn time yields exactly that many `FURNACE_FUEL` ticks per item. A zero value is rejected. Negative values are not expected from the API and fail closed if encountered.
- This automatically supports logs, tags/data maps, modded fuels, and stack-sensitive NeoForge overrides without a Auto Storage whitelist.
- Explicit Brew/Bottle mappings remain isolated overlays until production brewing exists; they must not replace or suppress a valid runtime Fuel value.
- Auto retains the scarcity rule. Runtime Fuel supplier breadth is evaluated from current registered default stacks when Auto must compare pools; explicit purpose pools keep their registered supplier counts. Ties remain current stored amount, then stable `EnergyType` order.
- Slot acceptance is only an early UX check. `StorageCoreBlockEntity.addFuel` re-resolves the current stack/value server-side immediately before exact overflow validation and consumption.
- Container remainder, conflict, stale Core, overflow, and no-silent-fallback rules are unchanged.

### 2. Recipe-derived processing cost

- `RecipeEnergyTable` resolves an `EnergyCost` from the concrete current recipe, not only its `RecipeType`.
- For `AbstractCookingRecipe`, both the matching machine-process amount and Fuel amount equal `recipe.getCookingTime()`:
  - smelting → `SMELTING_ENERGY + FURNACE_FUEL`
  - blasting → `BLASTING_ENERGY + FURNACE_FUEL`
  - smoking → `SMOKING_ENERGY + FURNACE_FUEL`
  - campfire cooking → `CAMPFIRE_ENERGY + FURNACE_FUEL`
- A non-positive cooking time is unsupported and fails closed; no default timing is substituted.
- Crafting and stonecutting remain instant/no-energy. The 0.1.15 extension added exact component-matched/preserving `SmithingTransformRecipe` plus deterministic vanilla default-state axe strip/scrape/wax-off; at that revision mod block hooks, Smithing Trim, dynamic/special subclasses, context-dependent transformations, and Brewing remained outside the executable contract. GitHub #2's 2026-07-17 slice later added exact concrete Smithing Trim only; the other exclusions remain current.
- Preview and execution both resolve the recipe ID again from the current server `RecipeManager` and call the same cost resolver. Cached `RecipeHolder` instances are never trusted after reload.
- Machine production remains installed machine count per server tick. A recipe therefore spends the exact processing time accumulated by its matching machines.

### 3. Server-side Craftable output catalog

- Storage is a dedicated page containing only stored network stacks.
- Craftable is a dedicated page whose candidates come from supported server recipes, not from existing output stacks in storage.
- The catalog stores recipe IDs/output identities only. Every preview/select/execute resolves the ID against the current `RecipeManager`.
- The catalog is rebuilt when the `RecipeManager` instance changes. It indexes supported recipes by possible ingredient item so current Core/player resources seed a bounded candidate set instead of rescanning and simulating every recipe on each machine-energy tick.
- Each candidate still passes the full existing joint-reservation preview with current Core items, optional player inventory, conflict/topology state, machine energy, and Fuel.
- Outputs are deduplicated by full `ItemKey`. Search, Search Sync, sort mode/order, scrolling, and current 81-slot paging still apply. Component variants are never collapsed by a Compact Grid mode.
- A zero-storage output appears only when at least one exact recipe currently has `craftableCount > 0`. Its displayed amount represents currently craftable output, not stored quantity.
- A synthetic Craftable entry can be selected but can never be extracted, Shift-moved, cloned, thrown, or treated as stored inventory.
- Selection is recipe/output identity based. It is not cleared merely because Core output count is zero or the player visits another terminal tab; Fuel simply hides the recipe workspace. It is cleared when the recipe disappears, becomes unsupported, or no longer matches the output.
- Minimum acceptance scenario: with logs plus enough matching machine energy and Fuel, charcoal appears at zero stored charcoal, selects the exact smelting recipe, and one craft consumes one log plus that recipe's exact cooking time from both pools.

### 4. EMI one-level Auto Storage crafting

- Scope is the user's requested **automatic-crafting action**, not full RS2 pattern scheduling.
- No recursive dependency tree, pattern provider, crafting CPU, queued job, or intermediate autocrafting is introduced.
- EMI no longer searches for the output in visible terminal slots. A request carries exact `{containerId, recipeId, amount, destination}`.
- `Destination.NONE` selects the exact recipe and opens the server-synced Auto Storage preview without consuming resources.
- `Destination.CURSOR` and `Destination.INVENTORY` request immediate one-level crafting. `amount` is an upper bound; the server chooses the largest legal amount not exceeding current ingredients, energy, and destination capacity, then commits that exact amount atomically.
- The server revalidates active menu/page, Core identity/connectivity/conflict, current recipe ID/class/output, ingredients, actual recipe-derived energy, amount bounds, and destination capacity. Zero legal crafts is a no-op with no consumption.
- Cursor delivery never overwrites an incompatible carried stack. Inventory delivery fills the post-consumption player inventory first and plans overflow back to the Core; if both destinations cannot hold every output/remainder, the whole craft rejects before mutation. Requested output is never silently dropped or voided.
- Same-output recipes remain distinct by ID. Stale, unsupported, forged, Fuel-page, wrong-container, or removed-datapack requests fail closed.
- EMI's full synthetic-favorites resource discovery is not part of this task: syncing an entire network inventory to the client would be the previously deferred full-list/P3 design. This integration covers exact recipe selection and one-level execution from EMI recipe UI.
- The plugin registers the adaptive terminal/left-rail bounds as EMI exclusion areas so overlays do not cover controls.

### 5. Adaptive vanilla-like layout

- Expand the existing dist-neutral `TerminalLayout` into one calculator for terminal kind/page. Inputs are scaled screen width/height and active page; outputs are immutable rectangles/points for frame, grid, search, scrollbar, recipe workspace, player inventory, Fuel cards, slots, rail buttons, and EMI exclusion bounds.
- The calculator is the only geometry source used by rendering, widget bounds, slot reconstruction, hit testing, tooltips, scroll track, and EMI exclusion registration.
- Keep Minecraft's 18-pixel slot rhythm, fixed header/player-inventory bands, and explicit outer breathing room. Do not choose the layout from a raw `screenWidth >= 480` breakpoint and do not maximize rows until the frame is one pixel from the screen edge.
- Follow the researched RS2 pattern for height: calculate rows from the space left after the complete fixed bands, reserve row-equivalent outer padding, then clamp. Because Crafting has a variable recipe/Fuel workspace, select the largest row count whose **complete candidate geometry** fits the target top/bottom margins rather than subtracting an incomplete constant.
- Width is based on usable space after the left rail and two outer margins:
  - **Narrow fallback** uses the 210-pixel frame and stacks the workspace. It exists only when the full side-by-side frame cannot fit; it is not a user-selectable Compact Grid mode.
  - **Side-by-side** uses a 378–390-pixel frame. The item side remains on the 9-column rhythm and the recipe/Fuel workspace receives the remaining width. The frame shrinks within that range before falling back to narrow.
- Center the combined `rail + frame` group, not the frame alone. Frame, rail, and every EMI exclusion rectangle must remain inside the scaled viewport with tested margins.
- Rail controls remain left of the frame. When height cannot fit one column, they wrap into a second left column instead of overlapping the player inventory or leaving the screen.
- Fuel uses the full content width above the player inventory. Installed Stations uses two descriptor-driven flow rows; reserve tiles remain count-driven, and both expose deterministic panel-local wheel paging only when content exceeds visible capacity. Wide layout fills the previous lower-right gap with a control panel aligned to the player inventory for the target selector and Fuel input. No UI array or layout loop may assume five machines or three reserve pools.
- Machine tiles show the install slot and accumulated reserve without explaining the production formula in permanent copy. Exact machine identity, reserve identity, and rate are discoverable through localized hover tooltips.
- All centered Fuel status text is positioned manually and rendered with `drawString(..., false)`; the shadowed `drawCenteredString` path is forbidden for these values.
- Keep the vanilla-container palette. Replace Unicode/single-letter rail labels with custom pixel icons drawn in the same fixed 12×12 canvas inside identical 18×18 vanilla buttons. Dynamic state changes the icon design/accent, never its canvas or button size; every button retains a localized tooltip and narration label.
- Search value/focus, page, target, and server-synced view settings survive `init`/resize. Post-vanilla-click focus clearing remains, so rail buttons never retain the white keyboard-focus outline.
- Because `Slot.x/y` are final, client slot reconstruction remains mandatory and delegates all original active/place/pickup/max-stack validation. Original semantic delegates are retained by menu index so repeated `init()` is idempotent and never nests wrapper slots.
- Only visible row count is sent to the server; client width mode never becomes authoritative storage state. Both menu constructors retain identical slot/data counts.

### 6. Recipe resource table and Compact Grid removal

- `CraftingTerminalMenu` remains authoritative. The client must not inspect `RecipeManager`, Core storage, player inventory, or recipe energy rules.
- For the selected exact recipe, the server syncs up to nine ingredient predicates in recipe order. Equal `Ingredient` predicates may be grouped into one row with a larger required count; predicates that merely share the same display stack must stay separate.
- Every ingredient row contains a representative `ItemStack`, saturated `long available`, and `required for one craft`. Availability includes the Core plus the 36 visible player inventory slots only when that server setting is enabled.
- Cooking recipes also expose their matching process-energy and Fuel rows, each with current `long available` and the concrete recipe's per-one `cookingtime` requirement. Crafting, stonecutting, and Smithing add no fake energy rows; axe transformations add the installed tool's raw remaining durability as an item resource requiring one per craft.
- The right workspace always renders the output name/type, recipe position, overall `Ready ×N`, and a table headed `Available / Required`. Resource rows use green/red availability state and item/energy tooltips. The layout may use deterministic columns in short workspaces, but it may not silently omit a requirement.
- Ingredient representatives continue through hidden vanilla slots; numeric availability/requirement values use split 16-bit menu data fields so values survive `advanced_container_set_data` signed-short transport. Both menu constructors call the same registration path and parity tests guard the resulting counts.
- Remove the Compact Grid button, state, data slot, button handler, grouping function, lang/Patchouli/docs references, and tests. Existing worlds need no migration because the value was menu-session-only and never persisted.

### 7. Explicit pages, grouped rail, and exact craft actions

- `CraftingTerminalPage` has exactly `STORAGE`, `CRAFTABLE`, and `FUEL`. There is no `showOnlyCraftable` boolean, toggle button, or dual-meaning Items page.
- The first rail group is always the three page buttons. On item pages, a larger geometry-defined group gap follows before sort/order/search controls and Use Player Inventory remains a final separate group. On Fuel, the rail ends after the three page buttons.
- Storage entries remain extractable. Craftable entries remain synthetic and selectable but cannot be extracted, quick-moved, cloned, or thrown. EMI exact-recipe selection is accepted on either item page and rejected on Fuel.
- Superseded 2026-07-23: the search field now parses Name / `#tag` / `@mod` syntax directly. The rail selector cycles Auto / EMI / EMI two-way synchronization, using magnifier, one-way, and two-way icons; localized tooltips name only the current sync mode.
- The recipe footer has `×1`, `×8`, `×64`, and `Max`. Exact buttons are active only when the latest server-synced `craftableCount` meets their amount. Max is active for any positive count.
- Max is not a trusted client amount. The server re-resolves the selected recipe and computes the current legal maximum independently of the 9,999 wire/display preview cap, then commits exactly that amount through the existing simulate-then-commit path. A stale or forged request with no legal craft is a no-op.
- Fuel target historically used one current-value vanilla `CycleButton` in the Energy Reserves header, followed by a bounded popup revision. The server still receives only Auto or exact target IDs and retired previous/next IDs remain no-ops. GitHub #23 supersedes both client presentations with a persistent searchable Transform target sidebar, whole-card exact-input uses, and middle-click reset.
- Button active state is only feedback; every server handler independently revalidates page, recipe, ingredients, energy, topology, and output delivery.

## Strict TDD Work Order

### Task 1 — RED/GREEN dynamic Fuel

Write failing SelfTest/GameTests before editing `FuelTable` or Core code:

1. oak log direct Fuel-slot placement succeeds and converts to its runtime burn time;
2. oak log Fuel-page Shift-click succeeds;
3. Core direct commit revalidates the runtime value;
4. non-fuel rejection, per-item stack multiplication, exact overflow rejection, component/remainder preservation;
5. Auto picks the less broadly supplied compatible purpose pool and remains deterministic.

Run the smallest focused GameTest filter and record the expected RED, then implement the resolver and re-run GREEN.

### Task 2 — RED/GREEN recipe timing

1. Add a test recipe with non-default cooking time.
2. Assert preview uses that time for process and Fuel.
3. Assert execution deducts exactly the same values.
4. Assert a changed/removed recipe after reload does not use a stale cost or holder.
5. Assert non-positive/unsupported timing fails closed.

Only after confirmed RED, change `RecipeEnergyTable` and its callers.

### Task 3 — RED/GREEN Craftable catalog

1. Zero stored charcoal + stored logs + sufficient energy/Fuel shows charcoal.
2. Missing ingredients, process energy, or Fuel omits it under current-craftable semantics.
3. Search, sort, and paging operate on synthetic outputs while every full `ItemKey` variant remains distinct.
4. Normal click selects; all extraction/Shift/pick/drop paths are no-ops.
5. Exact same-output recipes retain recipe identity.
6. Recipe reload rebuilds the catalog and clears stale selection.
7. Player-inventory toggle changes candidates and preview immediately.
8. Candidate indexing is guarded by a regression proving unrelated recipes are not fully simulated every tick.

### Task 4 — RED/GREEN EMI intent

1. Add packet codec and server-menu tests for exact recipe ID, amount, and destination.
2. Test output absent/offscreen, same-output identity, stale/unsupported ID, wrong container, Fuel page, conflict, missing ingredients/energy, cursor incompatibility, and inventory capacity.
3. Test NONE selects without consuming; CURSOR/INVENTORY commit only the simulated legal amount.
4. Keep EMI classes out of dedicated-server SelfTest; use dist-neutral request/menu tests plus Python static integration checks for client registration and mapping.
5. Only after server contracts are GREEN update `MagicStorageEmiPlugin`.

### Task 5 — RED/GREEN adaptive layout and visuals

1. Dist-neutral layout tests cover representative 320×240, 415×291, 416×291, 423×291, 480×270, 854×480, and every integer height 240–600.
2. Assert frame/rail stay onscreen, no rectangles overlap illegally, every slot follows 18-pixel rhythm, Storage rows clamp to 3–9, Crafting rows clamp to 1–9, rail wraps deterministically, repeated layout/init is idempotent, and the narrow/side-by-side boundary is stable.
3. Python static tests enforce one layout source, delegated reconstructed slots, focus clearing order, and EMI exclusion registration.
4. After RED, update screen rendering/widgets/hitboxes and localized labels.

### Task 6 — RED/GREEN fullscreen correction

1. First update this active contract and mark the 0.1.9 fullscreen GUI gate failed.
2. Add GameTests for per-one ingredient representatives, grouped equal predicates, distinct overlapping predicates, saturated available counts, optional player inventory, concrete process/Fuel rows, live storage/energy changes, recipe navigation/reload clearing, and server/client menu parity.
3. Add SelfTests for complete-geometry fitting at guiScale-equivalent widths/heights, combined rail/frame centering, target margins, side-by-side shrink/fallback boundaries, Fuel cards, deterministic resource-grid capacity, and no Compact Grid contract.
4. Add Python static regressions proving the Compact Grid path is gone, custom rail buttons use a fixed icon canvas, recipe resource rendering reads only menu-synced data, and all hitboxes/exclusions still derive from `TerminalLayout.Geometry`.
5. Confirm each focused test fails for the expected missing behavior before touching production code; then make the smallest implementation pass and rerun the full gates.

### Task 7 — RED/GREEN three-page and scalable Fuel correction

1. Update the active contract and dynamic Prism checklist first; record the supplied 0.1.10 screenshots as a failed fullscreen gate.
2. Add GameTests for default Storage, explicit Craftable/Fuel switching, Storage-versus-Craftable extraction rules, signed-short page sync/parity, fresh server-side Max, and forged/stale Max no-op. Confirm RED.
3. Add SelfTests for grouped rail spacing and count-driven Fuel flow at zero, current, wrapped, and overflowing descriptor counts. Confirm RED.
4. Add Python static tests forbidding `showOnlyCraftable`, fixed screen descriptor arrays, `machine_rate_hint`, Fuel `drawCenteredString`, and combined search glyphs; require four craft buttons with live active-state updates. Confirm RED.
5. Implement the smallest server/menu/page changes, then the one-source layout and screen rendering changes. Derive current descriptors from `MachineEnergyTable.entries()` and energy metadata rather than parallel UI arrays.
6. Rerun focused tests, complete automated gates, automatic patch deployment, and only then hand the fullscreen scenario to the user. Do not automate the visual verdict.

### Task 8 — RED/GREEN representative reserves and bounded amount text

1. Record the 0.1.11 fullscreen failure and inspect RS2's current `ItemGridResource#getDisplayedAmount` plus `ResourceSlotRendering#renderAmount` pattern without copying source.
2. Add SelfTests for compact-format boundaries and every `EnergyType` representative item; confirm compile RED because both APIs are absent.
3. Add Python contracts requiring display-slot custom amount rendering, a 16px normal/0.5-scale branch, cell-local right alignment, representative reserve/target items, Fuel type capacity, and an updated Prism checklist; confirm RED.
4. Extend the existing 94-data-slot Fuel wire test to prove page/target/energy plus base stored/max type values survive the same server-to-client sync without adding client-owned state.
5. Add a live Fuel-page type count/capacity test; confirm RED after topology growth (`menu=10`, `core=35`), then refresh display metadata on topology revision changes.
6. Implement the smallest common formatter/metadata and client renderer changes, then run focused GREEN, full gates, automatic patch deployment, and a new fullscreen handoff.

### Task 9 — RED/GREEN balanced Fuel flow and one target selector

1. Record the 0.1.12 fullscreen failure and preserve the supplied screenshot under its GUI run artifact.
2. Add a GameTest proving retired previous/next button IDs no longer mutate the server target; direct Auto/exact target IDs remain authoritative.
3. Add SelfTests requiring Fuel rail to contain only the three page tabs, selector geometry to stay inside the Fuel header, and sparse machine/reserve cells to span their full flow bounds while overflow remains paged.
4. Add Python contracts requiring one vanilla `CycleButton`, forbidding Auto/previous/next rail widgets and dynamic current-item rendering on an action button, and updating the Prism checklist.
5. Implement the historical selector in the Energy Reserves header with normal-click forward and Shift-click/hovered-wheel reverse; the active shared-control replacement is documented in `2026-07-14-terminal-platform-emi-recipe-axe.md`. Reconcile its optimistic value from the server-synced selected target on every target change.
6. Derive flow columns from the visible descriptor count, up to the bounds-derived maximum. Compute proportional cell edges so the last cell reaches the exact right/bottom bounds without cumulative integer remainder.
7. Keep direct Auto/exact target server IDs and the unchanged 94-data-slot contract. Remove retired previous/next IDs and `cycleFuelTarget`; no compatibility flag or silent bridge.
8. Run focused GREEN, full gates, automatic patch deployment, and a new fullscreen handoff.

## Documentation and Release Gates

- Update `PLAN.md`, `README.md`, `docs/overview.md`, `docs/structure.md`, `docs/notes.md`, `docs/plan.md`, `docs/roadmap.md`, `docs/rs2-design-gap.md`, Patchouli entries, and `en_us.json` with the final behavior.
- Task 6 deployment produced the rejected 0.1.10 baseline. Task 7 reused the automatic patch bump, then transactionally refreshed the same exact `magic_storage-0.1.11.jar` after the Max/overflow review fixes; build/deployed SHA-256 is `09fd5ca4e2b2d41c1163daa255da58232be021f1a131f30e53d80748f37b6d1d`.
- Task 8 automatic deployment bumped 0.1.11 to 0.1.12; build/deployed SHA-256 is `f7b0e78e630c03f77331fa9aad4c4d3f04512d069b327c9bad6942a0c8844309`, with exactly one Prism dev Auto Storage jar.
- Task 9's final test-strengthened deployment is 0.1.14; the 0.1.13 intermediate was replaced. Build/deployed SHA-256 is `97069ca985797ce237b421df2fc5c08c44cc2ca4dedf1957d0f756c2c92dae75`, with exactly one Prism dev Auto Storage jar.
- Task 9 automated evidence: SelfTest 40406, GameTest 210, Python 70/70, build, runData, 77 JSON parses, and datagen no-new-drift all passed. Do not use these historical counts as the 0.1.15 release gate.
- Required automated gates:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew compileJava --console=plain --no-daemon
./gradlew build --console=plain --no-daemon
./gradlew runGameTestServer --console=plain --no-daemon
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts
./gradlew runData --console=plain --no-daemon
```

- Required fullscreen Prism checklist after exact-jar deployment:
  1. adaptive narrow/side-by-side geometry leaves visible outer breathing room and the combined rail/frame group is centered and unclipped;
  2. rail controls remain left, wrap correctly, and do not retain a white focus border;
  3. Fuel is named Fuel everywhere;
  4. an oak log enters Fuel and adds the displayed runtime burn time;
  5. a non-default cooking recipe displays/consumes its own time;
  6. Storage shows only stored stacks; the separate Craftable tab shows zero-storage charcoal with valid logs/energy/Fuel and cannot extract it;
  7. EMI NONE selects the exact recipe; EMI cursor/inventory action performs one-level server-authoritative crafting;
  8. EMI overlays do not cover the adaptive frame or rail;
  9. `latest.log` contains the expected SelfTest count and no container-sync, packet, ERROR, FATAL, or unexpected auth-console failure.
  10. the selected recipe shows all item/process/Fuel rows as available/required-for-one, no Compact Grid control exists, and every rail icon has the same visual canvas;
  11. page tabs form a clearly separated rail group, Fuel has no permanent rate formula or shadowed values, and raw Name/`#tag`/`@mod` queries work directly and Auto Focus is independent and Search Sync uses Off/one-way/two-way icons;
  12. `×8` and `×64` are dim when unavailable, `Max` is present, and every currently registered Fuel tile is reachable without clipping at the tested fullscreen size.
  13. every Energy Reserve and explicit target uses its representative item, large grid amounts stay compact/right-aligned inside their own slot, and Fuel shows stored/max type capacity.
  14. Fuel rail contains only Storage/Craftable/Fuel; Energy Reserves has one clearly labeled current-value selector, and sparse machine/reserve entries distribute across the available panel width instead of clustering left.

The runner opens the offline native client and hands control to the user. It does not automate visual judgment. All runner-owned Prism/Minecraft/Gradle processes are closed after the verification session; no Terminal.app windows are opened or left behind.

## Non-goals

- Recursive RS2-style autocrafting or queued jobs.
- Full client-side mirror of the network inventory for EMI synthetic favorites.
- For this historical revision: Smithing Trim, dynamic/context-dependent transformations, and production brewing support were non-goals. Exact component-preserving Smithing Transform was covered by 0.1.15; exact concrete Smithing Trim was added later by GitHub #2, while the dynamic/context/brewing exclusions remain.
- Saved-data ID migration or removal of Brew/Bottle reserved pools.
- Runtime third-party machine/energy descriptor registration in 0.1.15. This was intentionally outside this revision; the later server-owned registry, client synchronization, fixed 256-slot parity, and stable-ID migration contract is now implemented and documented in `docs/machine-descriptor-api.md`.
- A new GUI library or copied third-party assets/source.
