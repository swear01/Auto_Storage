# Recipe Family API

Auto Storage automatically supports recipes from any namespace when their concrete recipe class is one of the built-in exact families. Addons only need this API for a custom `RecipeType` plus custom concrete recipe class.

## Registration

Create a NeoForge deferred register during normal mod construction. One family entry covers every current and future recipe ID whose holder has that exact class and exact `RecipeType`.

```java
private static final DeferredRegister<RecipeFamily> RECIPE_FAMILIES =
        RecipeFamilyApi.createDeferredRegister(MODID);

static {
    RECIPE_FAMILIES.register("grinding", () -> RecipeFamilyFactories.singleItemToItem(
            GrindingRecipe.class,
            GRINDING_RECIPE_TYPE,
            GRINDER_DESCRIPTOR_ID,
            GrindingRecipe::input,
            (recipe, registries) -> recipe.getResultItem(registries),
            recipe -> RecipeFamilyCost.free(),
            RecipePresentationKind.STONECUTTING));
}

public ExampleMod(IEventBus modEventBus) {
    AutoStorageAddon.register(MODID, modEventBus, addon ->
            addon.recipeFamilies(RECIPE_FAMILIES));
}
```

`GRINDING_RECIPE_TYPE` may be a `DeferredHolder`, because the factory stores the supplier and resolves it after registry registration. `GRINDER_DESCRIPTOR_ID` must already identify a registered [`MachineDescriptor`](machine-descriptor-api.md). A missing descriptor, duplicate family registry ID, or duplicate exact class/type pair fails startup instead of silently choosing one family.

Use one `AutoStorageAddon.register(...)` call when the same addon contributes
stations, resources, transfers, Transforms, or variants. The full Gradle setup,
facade contract, and release artifact policy are in
[`addon-development.md`](addon-development.md).

## Factory contracts

### `singleItemToItem`

The first public factory is intentionally narrow:

- exact recipe class and exact `RecipeType` identity;
- one non-empty `Ingredient`, consumed once per craft;
- one deterministic output stack; item components and count are preserved exactly;
- free cost, an existing Auto Storage `EnergyCost`, descriptor-keyed station work, or station work plus Fuel through `RecipeFamilyCost.energy(...)`, `stationWork(...)`, or `stationWorkAndEnergy(...)`; amounts must be non-negative, required work must be positive, at least one energy amount must be positive, and two positive energy amounts cannot target the same pool (`free()` represents zero cost);
- one existing `RecipePresentationKind`;
- no crafting remainder, chance output, source-dependent output, fluid, player/world/event callback, or arbitrary mutation hook.

The input, output, and cost functions must be deterministic and must not mutate their recipe, arguments, registries, storage, player, or world. Auto Storage owns candidate indexing, current-holder validation, checked count multiplication, station checks, simulate-then-commit, delivery, capacity planning, and rollback. Empty input/output or invalid registration is rejected; there is no generic reflection, serializer-name, `Recipe#getIngredients()`, or EMI-widget fallback.

If a custom family does not fit this complete contract, do not approximate it with this factory. A new reusable factory must first model all inputs, outputs, components, remainders, costs, and side effects server-authoritatively. External-machine send-and-wait processing is outside this mod's installed-station magic-crafting scope.

### `deterministicResources`

Use `RecipeFamilyFactories.deterministicResources(...)` when one exact recipe resolves to a bounded `TypedRecipePlan`. The resolver receives the exact recipe and current server registries. The plan builder requires:

- one to 81 exact `TypedRecipeInput` values; plans above nine inputs use a 3×3 shapeless presentation summary while the recipe viewer owns the full diagram;
- exactly one selectable primary identity: if any item primary exists, exactly one item primary is selected; otherwise the plan must contain exactly one primary resource of any registered kind;
- any additional item, fluid, power, chemical, or registered addon outputs;
- a non-empty exact presentation output and a `1..3 × 1..3` layout that fits all inputs; an item-kind selection output must use the same registered item identity as that presentation.

Input roles are explicit. `consume` removes `amount × crafts`; `catalyst` and `tool` require the declared amount but retain it, so one retained resource may serve the whole batch. Each input may declare ordered exact alternatives with `consumeAny`/`catalystAny`/`toolAny`; consumed alternatives may additionally map each chosen key to its own remainder through `consumeAnyWithRemainders`. Overlapping CONSUME alternatives are jointly allocated by a deterministic bounded max-flow plan, so one resource cannot satisfy two requirements. Retained roles may not overlap another role because no deterministic split is defined. When the terminal's Use Player Inventory setting is enabled, item-kind typed inputs use the same exact-component source snapshot as ordinary recipes: Core is allocated first, then the visible 36 player slots are reserved by exact slot and committed only after full delivery simulation. Fluid, power, chemical, and addon-kind inputs remain Core-only. Output roles are `primary` and `remainder`; the checked result preserves those roles separately for items and typed resources. Primary items follow Player/Storage delivery and item remainders follow the existing remainder policy. A non-item selected primary is Storage-only: direct terminal crafting forces effective Storage while keeping the saved item-output preference unchanged, and Cursor/Player/EMI-inventory requests reject without mutation. Other non-item outputs return to Core. Duplicate output keys are rejected. Checked multiplication, retained-resource checks, station work/Fuel, item delivery, type capacity, and all Core item/non-item deltas are validated before one atomic ledger commit.

The recipe-addon integration test executes a two-craft typed batch with exactly one declared catalyst amount and verifies that the same amount remains afterward. Built-in exact crafting adapters independently derive container remainders from the chosen input stacks; the base GameTest suite crafts the real vanilla Cake recipe and verifies that its three Milk Buckets return exactly three empty Buckets.

Use `StorageResourceKey.item(stack, registries)`, `fluid(stack, registries)`, and `neoforgeEnergy()` for canonical built-in keys. `itemStack(...)` and `fluidStack(...)` decode exact built-in keys without dropping components. Custom kinds use their registered kind ID, stable resource ID, and canonical variant compound.

```java
RECIPE_FAMILIES.register("infusion", () -> RecipeFamilyFactories.deterministicResources(
        InfusionRecipe.class,
        INFUSION_RECIPE_TYPE,
        INFUSER_DESCRIPTOR_ID,
        (recipe, registries) -> TypedRecipePlan.builder()
                .input(TypedRecipeInput.consume(
                        StorageResourceKey.item(recipe.input(), registries), 2))
                .input(TypedRecipeInput.consume(MANA_KEY, 100))
                .input(TypedRecipeInput.catalyst(CATALYST_KEY, 1))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(recipe.output(), registries), 3))
                .output(TypedRecipeOutput.remainder(MANA_KEY, 25))
                .presentationOutput(recipe.output().copyWithCount(3))
                .layout(3, 1, false)
                .build(),
        recipe -> RecipeFamilyCost.free(),
        RecipePresentationKind.STONECUTTING));
```

The resolver must be deterministic and side-effect free. Chance outputs, runtime machine polling, arbitrary Core/player/world callbacks, and external-machine send-and-wait are rejected by design. Multi-step planning remains a separate future server graph; EMI is presentation and selection only.

### `deterministicResourceVariants`

Use `RecipeFamilyFactories.deterministicResourceVariants(...)` only when one exact recipe holder can produce multiple deterministic plans from the exact item stacks currently available to the server. The resolver receives an immutable stack snapshot and registries, and returns zero or more complete `TypedRecipePlan` values under the same validation, ordering, transaction, and fail-closed rules as `deterministicResources`. It must not inspect client state or mutate its inputs. Fixed `deterministicResources` and legacy single-item families do not receive the large exact-stack snapshot during terminal listing; only this dynamic factory and component-sensitive built-ins such as smithing do. Ars Nouveau Apparatus uses this narrow path to preserve the selected reagent's exact component patch; ordinary fixed-output families should keep the simpler single-plan factory.

The overload without an eligibility predicate retains the global fail-closed rule for `Recipe#isSpecial()`. Some third-party public exact recipe classes report `isSpecial()` despite exposing a complete deterministic contract. Only the overload with an explicit `Predicate<R> eligibility` may opt that exact class/type into special-recipe support; the predicate must reject unsupported output shapes, overflow, chance/per-tick semantics, or any other non-deterministic member. This does not relax special handling for any other family.

### `dynamicDeterministicResources`

Use `RecipeFamilyFactories.dynamicDeterministicResources(...)` when one exact
holder still has one deterministic plan, but server configuration can change
its typed amounts or cost after registration. The family keeps its exact item
candidate index, but does not retain the per-recipe plan or final contract;
preview and commit therefore resolve the current server values and compare
them normally. The required `LongSupplier dynamicStateToken` must return the
current complete identity of every server value that can affect the plan or
cost. Equal behavior may reuse a token; changed behavior must not. Family
eligibility and candidate-index shape must remain stable across token changes;
use a recipe-manager reload rather than this factory when membership changes.
Shared Craftable result caches compare the token map before reuse, so a config
reload cannot restore outputs built from stale amounts. The supplier runs on
the authoritative server thread and must be deterministic, side-effect free,
and available after config loading. This is not a replacement for `deterministicResourceVariants`:
it receives no available-stack snapshot and cannot choose among several plans.
Oritech Pulverizer uses it so config reload and the legal zero-FE mode cannot
leave stale FE/work costs.

## Current optional integrations

- **Iron Furnaces:** contributes concrete Furnace station variants; recipes remain the existing exact smelting family while rate comes from live configured cook time. Recipe-viewer ownership stays with Iron Furnaces: its JEI plugin registers Smelting catalysts and the GUI support pack uses TMRV to expose them to EMI without installing JEI. Auto Storage never registers third-party EMI workstations.
- **Farmer's Delight:** Cooking Pot plans include 1–6 ingredients, exact serving container, alternative-specific container remainders, `cookTime` station work, matching Furnace Fuel, and one fixed item output.
- **Mekanism:** all nine factory-backed families are present. Crushing, Enriching, and Smelting use sized one-item inputs; Combining uses two sized item inputs; Compressing, Purifying, Injecting, and Infusing use sized item + chemical inputs; deterministic Sawing accepts no random secondary output, or an exact 100% secondary remainder. Per-tick chemical use is normalized across the basic machine's 200-tick work cost with checked multiplication. Pressurized Reaction uses sized item/fluid/chemical inputs, checked `energyRequired × duration` FE, duration station work, and deterministic item-primary, item-plus-chemical, or chemical-only output. The deterministic single-block extension adds Chemical Oxidizer, Chemical Infuser, Electrolytic Separator, Chemical Dissolution Chamber, Chemical Washer, Chemical Crystallizer, Isotopic Centrifuge, Antiprotonic Nucleosynthesizer, Pigment Extractor, Pigment Mixer, and Painting Machine. Their exact Mekanism ingredient amounts and item/fluid/chemical outputs use one typed transaction. The 5-second/10-second machine durations, recipe-specific Nucleosynthesizer duration, checked per-tick chemical multiplication, one-operation-per-tick families, and Electrolysis energy multiplier are preserved as descriptor work costs. Electrolysis commits both chemical outputs atomically; one is the selectable primary and the second is a deterministic remainder output. Chemical/fluid-only selection preserves the exact resource key and amount and is Storage-only. Probabilistic Sawing and unsupported output shapes remain fail closed. The owning Mekanism mod remains responsible for EMI workstation metadata.
- **Botania:** exact common Mana Infusion, Runic Altar, Terrestrial Agglomeration, Petal Apothecary, and Elven Trade classes each require their matching installed instant station. Mana Infusion retains its supported block catalyst; Runic Altar retains catalyst ingredients and returns exact crafting remainders; Petal Apothecary consumes 1000 units of stored water; Elven Trade commits every fixed output. Mana, water, items, catalysts, remainders, and outputs use one typed transaction. The plan stays within the shared nine-input contract and rejects unsupported custom ingredient/state semantics rather than approximating them.
- **Ars Nouveau:** Imbuement consumes its center input and Source while retaining pedestal catalysts. Enchanting Apparatus consumes its reagent and ordinary pedestal ingredients, retains tagged catalysts, returns exact crafting remainders, and uses `deterministicResourceVariants` only when the output must preserve the exact reagent component patch. Both require their installed process station and reject dynamic/world-dependent or over-nine-input plans.
- **EvilCraft:** Blood Infuser consumes an optional exact item and/or exact Blood amount, retains a required Promise tier, consumes recipe duration as station work, and emits one exact item output. XP, tag-selected outputs, unsupported tiers, live modifier combinations, and Purifier actions fail closed.
- **Powah:** Energizing consumes one to six exact items plus scaled NeoForge Energy, uses the same scaled amount as station work, and derives each installed rod tier rate from loaded Powah config. Dynamic/custom inputs, non-positive energy, physical warm-up, and world links fail closed.
- **Industrial Foregoing:** Dissolution Chamber plans consume grouped exact item slots, sized fluid, live-config FE, and station work while preserving deterministic item/fluid outputs. Material Stonework Factory plans preserve water/lava catalysts or consume exact full thresholds; Crusher accepts only one exact deterministic output. Partial thresholds, laser/extractor world state, custom output callbacks, and ambiguous outputs fail closed.
- **Create:** Milling, Crushing, and Cutting consume one exact item plus recipe duration; Filling consumes exact item + fluid; Emptying returns exact item + fluid. All outputs must be deterministic and all five stations use normalized station work. Chance, heat, RPM-dependent, multi-station, tool-durability, world-catalyst, Mechanical Crafting, and Sequenced Assembly shapes fail closed.
- **Create Aquatic Ambitions:** the present-mod module registers no station, resource kind, or recipe family. Channeling remains fail closed because its Encased Fan catalyst is selected from live Conduit/block/fluid world state, mutates entity Conduit Power, and may emit chance outputs. Exact vanilla/Create-class datapack recipes remain owned by the corresponding built-in/Create contracts rather than a CAA-specific approximation.
- **Extended Crafting:** exact shaped-table recipes from 3×3 through 9×9 and the real `UltimateSingularityRecipe` require one installed Ultimate Crafting Table. Up to 81 exact groups preserve alternatives, remainders, components, and atomic rollback; custom transformers fail closed; see [`extended-crafting-compatibility.md`](extended-crafting-compatibility.md).
- **Advanced AE:** the reviewed `1.6.11-1.21.1` target intentionally registers no custom station or recipe family. Reaction Chamber recipes depend on live AE-network power, speed-card state, and generic item/fluid stacks, so the present-mod fixture keeps them fail closed while exact vanilla-class recipes under the Advanced AE namespace remain covered by built-in families; see [`advanced-ae-compatibility.md`](advanced-ae-compatibility.md).
- **Oritech:** exact `OritechRecipe` holders of type `oritech:pulverizer` consume simple item inputs plus live `energyPerTick × time` FE, consume stable `recipe.time` station work, emit `results[0]` as primary and further results as deterministic remainders (identical component-exact keys merge), and re-resolve loaded Oritech config after reload. The plain station always advances one work/tick; its dynamic token invalidates open and shared Craftable results when FE/t changes without revaluing accrued work. Other shared-class RecipeContent types, invalid fluid data, oversized ingredient+FE layouts, and addon machine state fail closed; see [`oritech-compatibility.md`](oritech-compatibility.md).
- **PneumaticCraft:** present-mod CI intentionally registers no family. Pressure Chamber, Thermo Plant, Fluid Mixer, Assembly, Refinery, Heat Frame, and Explosion payloads depend on retained pressure/heat/multiblock/world state or non-simulatable Air mutation that the current exact transaction contract cannot represent.
- **Draconic Evolution:** present-mod CI intentionally registers no family. Fusion Crafting depends on live Fusion Crafting Core multiblock injectors, injector-local energy charging, `DEConfig` craft/charge times, generic ingredients, and optional live `IFusionDataTransfer` assemble; see [`draconicevolution-compatibility.md`](draconicevolution-compatibility.md).
- **Productive Bees:** present-mod CI intentionally registers no station, resource kind, or family. Format-16 review rejects all 16 actual recipe classes because chance, live bee/entity/world mutation, dynamic gene/bee components, or live machine paths prevent exact public-SDK simulate-then-commit semantics; see [`productivebees-compatibility.md`](productivebees-compatibility.md).
- **Productive Metalworks:** present-mod CI intentionally registers no custom family. Item melting, fluid alloying, item/block casting, and entity melting depend on Foundry multiblock fuel/temperature/upgrades, live casting-block cooling/`Config`, and/or entity mutation; see [`productivemetalworks-compatibility.md`](productivemetalworks-compatibility.md).

- **Railcraft Reborn:** present-mod CI intentionally registers no family. Crusher chance outputs, Blast Furnace / Coke Oven multiblock+live fuel/creosote, Rolling Machine live Charge/matrix, and special crafting fluid/damage/component/Patchouli helpers all fail closed; see [`railcraft-compatibility.md`](railcraft-compatibility.md).

Mekanism 10.7.19 still has two explicit fail-closed boundaries. A one-way Rotary recipe is supported in either fluid-to-chemical or chemical-to-fluid direction, but the shipped recipes put both directions in one `BasicRotaryRecipe`; the current one-plan-per-holder API cannot condition output on the selected input, so bidirectional Rotary recipes are rejected instead of duplicating output. Nutritional Liquifier recipes are synthetic `NutritionalLiquifierIRecipe` values whose `getType()` and `getSerializer()` are both `null` and which never enter the server `RecipeManager`. Their fluid output and optional container remainder therefore cannot be safely exposed by a registered exact recipe family. The representative fixture uses Mushroom Stew to prove the synthetic recipe returns both Nutritional Paste and the exact `usingConvertsTo()` Bowl; an ordinary crafting remainder alone is not this API contract. Supporting those two shapes requires a conditional-plan/synthetic-recipe discovery API rather than a Mekanism-only fallback.

Every optional recipe compatibility module loads only after `ModList` confirms the target mod. One representative artifact per mod is used in CI without restricting player metadata to that exact version. Botania CI uses official timestamped build31 `455-20260723.172746-31` from the `455-SNAPSHOT` series and accepts it only when the resolved jar matches the expected SHA-256; this is reproducible test evidence, not a player dependency pin. See [`botania-compatibility.md`](botania-compatibility.md).

## Lifecycle and sides

- Register on the mod event bus before NeoForge registries freeze. Auto Storage freezes and validates its immutable adapter snapshot at common setup; runtime hot registration is unsupported.
- The recipe-family registry is server policy. Dedicated servers never load EMI or screen classes through this API.
- External families are ordered after built-ins and then by full registry ID. Addons cannot supply a priority or shadow a built-in exact class/type pair.
- A CI-tested addon version is representative evidence only. Addon metadata should use the compatibility range it actually supports rather than copying a single CI artifact version as an exact player-facing pin.

## Repository verification fixture

`src/apiTest/` proves the fixed and dynamic deterministic factories, the explicit-eligibility overload, and the resource kind/handler/block/container APIs compile from a different Java package. `src/recipeAddonFixture/` is a separately loaded NeoForge mod with 17 GameTests covering the public transaction boundary. Each bundled module keeps its authoritative focused fixture and joins the descriptor-derived aggregate. Isolated fixtures verify each module's per-module `matrix.recipeInventory` digest against their own RecipeManager. `src/compatibilityMatrixFixture/` adds three GameTests over every descriptor-owned representative optional mod plus the reject-only companion: two verify registries, resource kinds, accepted families, shared descriptors, and fail-closed boundaries; the third structurally validates coexistence namespace claims, records actual coexistence and unclaimed recipe-inventory digests in `build/reports/terminal-scale-<types>.json`, then benchmarks 10,000 exact stored variants by default or 30,000 with `-PterminalScaleTypes=30000`. A recipe-inventory change cannot silently pass by preserving only a global total. Public handler/strategy simulation and amount rules are authoritative in [`typed-resource-storage.md`](typed-resource-storage.md#public-handler-and-strategy-contract).

```bash
./gradlew compileApiTestJava
./gradlew runRecipeAddonGameTestServer
./gradlew runCompatFixtureGameTestServers
./gradlew runPneumaticCraftGameTestServer
./gradlew runCompatibilityMatrixGameTestServer
```
