# Installed Stations, Expanded Recipes, Safe Output, and Cohesive Textures

> Status: production implementation, focused RED → GREEN tests, and final automated gates are complete. 0.1.15 Prism deployment and the user-owned fullscreen visual verdict are explicitly deferred.
>
> Extends `2026-07-12-fuel-craftable-emi-adaptive-ui.md`; it does not replace that document's Fuel, Craftable, EMI packet, or adaptive-layout contracts.
>
> Historical scope note: Smithing Trim was intentionally unsupported in this 0.1.15 slice; GitHub #2's 2026-07-17 exact component-preserving slice supersedes that boundary. Current recipe truth lives in `docs/notes.md` and `docs/superpowers/plans/2026-07-14-modded-recipes-multistep-crafting.md`.

## User-visible goals

1. Installed Stations uses two flow rows and spends the available Fuel-page width instead of leaving a dead lower half.
2. Recipes are available only when the matching station or tool is installed in the Core, following Terraria Magic Storage's station-installation idea rather than exposing every instant recipe for free.
3. The recipe workspace uses the familiar input → operation → output and Available / Required grammar without embedding EMI's private recipe-screen implementation.
4. Craft output can never disappear when the player inventory fills.
5. Exact crafting, cooking, stonecutting, component-preserving smithing transforms, and deterministic axe transformations are executable.
6. Every model-referenced mod texture uses Minecraft's native 16×16 resolution and one coherent vanilla-adjacent palette.

## Reference decisions

- Terraria Magic Storage stores crafting stations in its crafting interface and exposes recipes through installed stations. Auto Storage adopts that gate, while keeping all inventory and recipe authority on the server.
- EMI was an optional compile-time API integration when this plan was written; the 2026-07-15 policy now requires EMI on clients with `[1.1.24,2)` while keeping dedicated servers independent. Its public handler carries exact recipe intent, amount, and destination; the terminal renders its own server-synced recipe panel and never depends on EMI internals.
- NeoForge's exact recipe classes and `ItemAbilities.AXE_STRIP`, `AXE_SCRAPE`, and `AXE_WAX_OFF` define the supported deterministic scope. Event-only, player-context, or arbitrary-world-state transformations fail closed.
- Refined Storage's destination-capacity pattern informs the output transaction: simulate the destination before consuming anything, then commit or reject the entire craft.

Reference URLs:

- https://terrariamods.fandom.com/wiki/Terraria_Mods_Wiki%3AMagic_Storage/Storage_Crafting_Interface
- https://terraria.wiki.gg/wiki/Crafting_stations
- https://github.com/emilyploszaj/emi
- https://docs.neoforged.net/docs/1.21.1/resources/server/recipes/builtin/
- https://github.com/refinedmods/refinedstorage2

Patterns are references only; no source is copied verbatim.

## Product contracts

### Installed station/tool registry

- `MachineEnergyTable` is the single ordered descriptor source for nine persistent Core slots: Furnace, Blast Furnace, Smoker, Campfire, Brewing Stand, Crafting Table, Stonecutter, Smithing Table, and one axe-capable tool.
- The first five entries generate their matching process-energy at installed stack count per server tick. Crafting Table, Stonecutter, Smithing Table, and axe generate no energy.
- Installed stacks are server-owned, NBT-persistent, do not consume Storage Unit type capacity, and remain installed across page changes and menu close.
- Recipe availability is revalidated server-side against the current exact installed stack. A client preview or stale recipe selection cannot bypass the gate.
- The Fuel page derives cells from descriptor count. Installed Stations has two flow rows; overflow remains deterministic panel-local paging. Wide layouts use the full inner width.
- The lower-right Fuel area is a dedicated control panel aligned with the player inventory. It owns the current target selector and Fuel input rather than remaining empty decorative space.

### Supported recipe scope

- Exact vanilla concrete classes: `ShapedRecipe`, `ShapelessRecipe`, `StonecutterRecipe`, `SmeltingRecipe`, `BlastingRecipe`, `SmokingRecipe`, `CampfireCookingRecipe`, and `SmithingTransformRecipe`.
- Synthetic internal axe recipes cover deterministic `minecraft` namespace default-state strip, scrape, and wax-off transformations found through NeoForge tool actions. Arbitrary mod block hooks are not invoked with the synthetic context.
- Crafting and stonecutting require their station and cost no energy. Cooking requires its matching installed machine and consumes the concrete recipe's cooking time from both process energy and Fuel.
- Exact smithing transforms require a Smithing Table. Template/base/addition matchers delegate to the concrete recipe against actual source stacks, including component-sensitive custom Ingredients; they are jointly reserved, and execution calls `SmithingTransformRecipe.assemble` for every exact allocated base identity so damage, name, enchantments, and other base components survive.
- Axe transformations require a currently installed compatible tool. Each craft consumes exactly one raw durability point; Unbreaking randomness is intentionally not simulated. Material and tool mutation are one atomic transaction.
- In this historical 0.1.15 slice, Smithing Trim shared the unsupported boundary with special/incomplete/dynamic subclasses, brewing execution, loom/cartography/grindstone/anvil/enchanting, arbitrary block-state recipes, and event/player-context-only transformations. Current exact concrete Smithing Trim support is documented in the replacement plan above; the other boundaries remain fail closed.
- Synthetic axe recipe holders are internal catalog entries. They are never registered as datapack serializers or sent through `RecipeManager` serialization.

### Recipe workspace

- The client renders only menu-synchronized data. It never reads Core storage or `RecipeManager` as client-owned truth.
- The panel presents station/type and recipe position, output, input resources, operation arrow, Ready/Missing status, Available / Required values, recipe navigation, and ×1/×8/×64/Max actions.
- Geometry for every render, widget, slot, hitbox, tooltip, wheel region, and EMI exclusion comes from `TerminalLayout.Geometry`.
- The UI uses an 18-pixel slot/control rhythm and equal 12×12 icon canvases. Narrow and wide layouts are chosen by complete-geometry fit, not a user toggle or scattered coordinates.

### Craft output transaction

- Inventory-destination crafting first fills compatible/empty slots in the visible 36-slot player inventory, after accounting for player ingredients consumed by the same craft.
- Any primary output or crafting remainder that does not fit is planned for the Storage Core.
- If the post-ingredient Core cannot accept every overflow type/count, the whole craft is rejected before ingredient, energy, tool, inventory, cursor, or Core mutation.
- Commit order revalidates exact player/Core/tool inputs, extracts Core ingredients, inserts planned Core output, consumes energy, applies tool damage, and finally replaces the planned player inventory/cursor state inside a deferred-listener mutation batch.
- Any Core-side commit mismatch explicitly rolls back inserted outputs and extracted ingredients. Requested craft output is never dropped to the world and never silently voided.
- Cursor delivery remains a single exact output identity and fails closed when the carried stack is incompatible or lacks capacity.

### Texture contract

- Every texture referenced by a block/item model is exactly 16×16 PNG.
- The runtime set uses a cohesive dark deepslate/blackstone body with restrained amethyst/cyan magic accents and readable tier progression.
- Generation prompts, chosen predictions, and previews live under `art/texture-generation/20260713-cohesive-16x16/`; metadata/preview sidecars never live in the runtime resource tree or enter the jar.
- Static tests resolve model texture references and reject wrong dimensions or metadata sidecars under runtime textures.
- Automated dimension checks do not constitute visual approval. The final jar still requires the fullscreen Prism checklist for block faces, item readability, tier distinction, GUI spacing, hover text, and focus state.

## Strict TDD evidence

- Output safety was driven by full-inventory, partial-inventory, Core-overflow, blocked-output, direct craft, EMI destination, Max, largest-deliverable fallback, and cross-`Integer.MAX_VALUE` craft/Core reservation/output regressions.
- Station gating was driven by missing/installed station tests, machine persistence/menu parity, and non-energy station assertions.
- Smithing began with unsupported/execution failures, then added exact transform, component preservation, distinct component output routing, and the then-current unsupported Trim/dynamic boundaries; later GitHub #2 coverage supersedes only the Trim boundary.
- Axe support began with strip/scrape/wax-off and blocked-output failures, then added raw durability, installed-tool identity, and atomic rollback coverage.
- Fuel layout began with missing geometry/static-contract failures, then added two-row descriptor flow, full-width wide panels, control-panel occupancy, and one-source hitbox/render checks.
- Texture tests first caught three referenced 32×32 assets, then passed after all model-referenced assets became 16×16.

## Release gate

Before this task is complete:

1. Bump and deploy exactly 0.1.15 through `python3 scripts/deploy_prism_dev.py` so the version increase and jar replacement remain transactional.
2. Pass `./gradlew build`, `./gradlew runGameTestServer`, Python unittest discovery, `./gradlew runData`, JSON parsing, datagen drift, and pre-push review on the final tree.
3. Launch `crafting-fuel-page` through the offline native Prism runner and hand control to the user.
4. The runner must start in automatic Minecraft F11 fullscreen; the user verifies the gate, two Installed Stations rows, the occupied lower-right Fuel control panel, the recipe workspace, × buttons, focus clearing, all relevant tooltips, and the new 16×16 world/item textures. macOS native fullscreen is forbidden.
5. Do not mark GUI verified until that user verdict is recorded.
