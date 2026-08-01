# Productive Metalworks Compatibility

Auto Storage's Compat Kit review of Productive Metalworks `1.21.1-1.15.0`
accepts **zero production recipe families**. This is an evidence-backed
fail-closed result (outcome **C**), not an absent-mod fallback and not an empty
recipe adapter.

The present-mod module entrypoint loads only when `productivemetalworks` is
installed and registers no stations or recipe families. Vanilla-class recipes
that Productive Metalworks ships under its namespace remain covered by Auto
Storage's built-in exact crafting/smelting/blasting families without a custom
module.

## Reproducible audit evidence

- target: Productive Metalworks `1.21.1-1.15.0` (`productivemetalworks`);
- CurseForge file `7884786` / Curse Maven
  `curse.maven:productivemetalworks-1184570:7884786`;
- download URL used for local SHA verification:
  `https://mediafilez.forgecdn.net/files/7884/786/productivemetalworks-1.21.1-1.15.0.jar`;
- jar SHA-256:
  `1dcf9e10fc457c92d9ed466336104927169817cd509ca9ca69dec734f994d124`;
- official source: https://github.com/JDKDigital/productivemetalworks
  commit `7c6483c51e1a9def633a939ea75e0018dd079ffa` (`mod_version=1.21.1-1.15.0`);
- audit: `compat/audits/productivemetalworks/1.15.0.json`;
- reviewed contract: `compat/contracts/productivemetalworks.json`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Productive Metalworks version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

Compat Kit enumerated 23 recipe-class candidates. Every candidate is rejected
in the committed contract. The runtime machine families behind that inventory
are:

| Family | Result | Reason |
|---|---|---|
| Item melting | rejected | requires a formed Foundry multiblock plus live `FuelMap` temperature, speed, consumption, upgrades, and tank/capacitor fuel drain |
| Fluid alloying | rejected | executes inside Foundry tanks with upgrade-modified speed and live controller fuel/tank state |
| Item casting | rejected | live `CastingBlockEntity` coolingTime, `Config.foundryCoolingModifier`, optional `CastingRecipeEvent` injection, consumeCast/mold replacement, and Foundry-tap pouring |
| Block casting | rejected | same live casting-table/basin contract as item casting |
| Entity melting | rejected | consumes live entities; entity/world mutation |
| Datagen builders / JEI categories / serializers / `RecipeHelper` / `CastingRecipeEvent` / `ICastingRecipe` | rejected | not independent deterministic Auto Storage recipe families |

Typed resources were not introduced. Casting catalysts/remainders and Foundry
fuel cannot be reduced to a simulate-then-commit plan without retained
multiblock or live block-entity state.

## Future acceptance boundary

Support can be reconsidered only after a generic contract can express retained
Foundry multiblock composition, fuel temperature/speed/consumption, casting
cooling that is independent of mutable block-entity/`Config` state, and
event-injected casting recipes without approximating those conditions away.

## Declarative matrix evidence

The module descriptor and reviewed contract both declare the target mod present
with zero descriptors, resource kinds, accepted recipes, rejected registry IDs,
or Productive Metalworks-owned recipe families. Loaded `productivemetalworks:*`
recipes remain in `RecipeManager` for vanilla-class coverage and fail-closed
assertions; their exact inventory digest is locked by the descriptor-owned
matrix `recipeInventory` SHA-256. No shared workflow or matrix Java list is
extended for this module.

## Verification

```bash
./gradlew runProductivemetalworksGameTestServer
./gradlew runCompatibilityMatrixGameTestServer
```

Eight present-mod GameTests prove the module registers no Productive Metalworks
stations/families, representative melting/alloying/casting recipes stay
unsupported, entity melting stays absent, and every loaded recipe in each
audited custom recipe type remains fail closed. The descriptor-owned
compatibility matrix also loads the representative artifact and locks the same
zero-family boundary plus the per-namespace recipe inventory digest. Loading the present-mod jar still contributes Foundry/casting datapack recipes to `RecipeManager`; those IDs are locked by the descriptor-owned digest rather than a single global recipe count.
