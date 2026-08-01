# Mystical Agriculture Compatibility

Auto Storage's Compat Kit review of Mystical Agriculture `8.0.27` accepts the
exact deterministic Seed Reprocessor family and rejects every other audited
recipe-class candidate. This is an evidence-backed mixed result (outcome **B**).

The present-mod module loads only when `mysticalagriculture` is installed and
compiles in the isolated `compatMysticalagriculture` source set against the
public Auto Storage API plus Mystical Agriculture and Cucumber.

Vanilla-class Mystical Agriculture recipes remain covered by Auto Storage's
built-in exact crafting/smelting families without a custom module.

## Reproducible audit evidence

- target: Mystical Agriculture `8.0.27` (`mysticalagriculture`);
- Modrinth version ID `izIaJr8V` /
  `maven.modrinth:mystical-agriculture:izIaJr8V`;
- download URL used for local SHA verification:
  `https://cdn.modrinth.com/data/C95ReXie/versions/izIaJr8V/MysticalAgriculture-1.21.1-8.0.27.jar`;
- jar SHA-256:
  `d67bb701fbe4ade2efeb0aafd477f569b5e6a5a7c8ac696a8a1f658f8477eb99`;
- ATM10 modlist SHA-1 cross-check:
  `e051735be23652a3371dc791ceda97f46bc95936`;
- official source: https://github.com/BlakeBr0/MysticalAgriculture
  commit `e39b0e2a1130ea7868e247c6ed9b4cc820e014b5` (`Release 8.0.27` on branch
  `1.21`);
- required runtime companion: Cucumber `8.0.16`
  (`maven.modrinth:cucumber:8421rqFF`);
- audit: `compat/audits/mysticalagriculture/8.0.27.json`;
- reviewed contract: `compat/contracts/mysticalagriculture.json`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Mystical Agriculture version on players and does not claim a
multi-version matrix.

## Accepted family

Exact class/type:

- `com.blakebr0.mysticalagriculture.crafting.recipe.ReprocessorRecipe`;
- `mysticalagriculture:reprocessor`
  (`ModRecipeTypes.REPROCESSOR`).

Logical station:

- descriptor `auto_storage:mysticalagriculture_reprocessor`;
- station item `mysticalagriculture:seed_reprocessor`;
- player-facing Processing label follows the JEI category
  `Seed Reprocessing` / `種子再處理`;
- Processing category, 1 work per tick, 200 work per craft;
- `MachineUpgradeTier` operation-time and fuel-usage multipliers are internal
  machine state and are not inferred from a plain station `ItemStack`.

Transaction:

- one simple item input: consumed once;
- result: exact output item/components/count;
- energy: `200 × 20 FE` from the base
  `ReprocessorTileEntity.OPERATION_TIME` /
  `FUEL_USAGE` contract.

The family is unavailable when the ingredient is missing, empty, or non-simple,
or when the result is empty.

## Explicit exclusions

Compat Kit enumerated 36 recipe-class candidates. Every candidate except
`ReprocessorRecipe` is rejected in the committed contract:

| Family | Result | Reason |
|---|---|---|
| Infusion Altar | rejected | live world multiblock pedestals, activation/redstone state, and pedestal inventory mutation |
| Awakening Altar | rejected | live world multiblock pedestals plus essence vessels with partial vessel stack mutation |
| Enchanter | rejected | output enchantment level and compatibility depend on available ingredient counts and existing tool enchantments; `getResultItem` is empty |
| Soul Extractor | rejected | mutates Soul Jar `MobSoulType` double amounts and requires a non-full jar in the output slot; no MobSoul typed-resource contract |
| Soulium Spawner | rejected | weighted `EntityType` selection via `RandomSource` and world entity spawn |
| Farmland till / Soul Jar empty | rejected | special crafting remainders, not installed-station transactions |
| JEI Crux / datagen / serializers / interfaces / caches | rejected | viewer, builder, registry, or helper surfaces, not independent deterministic families |

No Mystical Agriculture-only typed resource kind is registered. Chance,
entity/world, and multiblock paths remain fail closed.

## Verification

```bash
./gradlew runMysticalagricultureGameTestServer
```

Eight present-mod GameTests cover Reprocessor registration/rate, exact
seed/FE/work consumption, missing-seed / insufficient-FE / insufficient-work
atomic no-ops, destination overflow rollback, and fail-closed coverage for
Infusion, Enchanter, Soul Extraction, Soulium Spawner, and Awakening recipe
types. The all-mod compatibility matrix also loads the representative artifact
and locks coexistence of the accepted Reprocessor family.
