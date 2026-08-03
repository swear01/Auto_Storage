# Hostile Neural Networks Compatibility

Auto Storage's Compat Kit review of Hostile Neural Networks `6.5.0`
accepts **zero production recipe families**. This is an evidence-backed
fail-closed result (outcome **C**), not an absent-mod fallback and not an empty
recipe adapter claiming Simulation Chamber or Loot Fabricator support.

The present-mod module entrypoint loads only when `hostilenetworks` is
installed and registers no stations or recipe families. Vanilla-class crafting
recipes that Hostile Neural Networks ships under its namespace remain covered by
Auto Storage's built-in exact crafting families without a custom module.

## Reproducible audit evidence

- target: Hostile Neural Networks `6.5.0` (`hostilenetworks`);
- Modrinth version `ZbsbtrNE` /
  `maven.modrinth:hostile-neural-networks:ZbsbtrNE`;
- download URL used for local SHA verification:
  `https://cdn.modrinth.com/data/6bLUlbZn/versions/ZbsbtrNE/HostileNeuralNetworks-1.21.1-6.5.0.jar`;
- jar SHA-256:
  `1fbe3fe6136fdd7938e176814c5d205f2d1c119743807b331e2f436513def357`;
- required runtime companion: Placebo `1.21.1-9.9.2`
  (`maven.modrinth:placebo:1Ypo4tf4`, SHA-256
  `1a844a5b081813b1edb82656329e54d38389ed470f6a6516a5887f5303d7daad`);
- official source: https://github.com/Shadows-of-Fire/Hostile-Neural-Networks
  commit `54a1d8e15abc215e164b2247d7a3b72ad0310ebe` (`version=6.5.0`);
- scanner-format-17 audit: `compat/audits/hostilenetworks/6.5.0.json`;
- reviewed contract: `compat/contracts/hostilenetworks.json`;
- reachable ancestry retained by the audit: Placebo, Jade, and JEI common API
  plus the shared NeoForge/Minecraft binary-pipeline platform jar.

The bundled module descriptor places the exact target plus all three reachable
coordinate-backed ancestry artifacts (Jade, Placebo, and JEI common API) on its
non-transitive compile classpath. Its runtime list remains narrower: only the
target and reviewed Placebo companion are installed. Compile evidence is not a
claim that Jade or JEI is required in a player's instance.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Hostile Neural Networks version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

Scanner format 17 classifies **zero** actual `Recipe` classes. Migration from
the legacy format-7 audit removed two name-shaped non-recipe surfaces that are
no longer treated as recipe-family candidates:

| Legacy surface | Current bucket | Reason |
|---|---|---|
| `HNNRecipeProvider` | datagen | emits vanilla shaped/shapeless crafting |
| `LootFabRecipe` | client/viewer | JEI display helper only |

Simulation Chamber and Loot Fabricator remain outside Auto Storage installed-
station scope:

- Simulation Chamber inference rolls prediction counts through
  `DataModelInstance.rollPredictions(RandomSource)` and mutates live model/
  inventory/energy block-entity state;
- Loot Fabricator converts prediction items using live `FabSelection` mode/
  queue state, redstone gating, and `HostileConfig.fabPowerCost`;
- Data Center combines those live behaviors across a multiblock layout.

Typed resources were not introduced. Vanilla living-matter crafting recipes
that do not require Twilight Forest remain supported through the built-in
crafting family.

## Declarative matrix evidence

The module descriptor and reviewed contract both declare the target mod present
with zero registered descriptors, resource kinds, accepted recipes, or Hostile
Neural Networks-owned recipe families. Their rejected-descriptor list explicitly
locks Simulation Chamber, Loot Fabricator, and Data Center IDs out of the
combined matrix. Loaded `hostilenetworks:*` recipes remain in `RecipeManager`
for vanilla-class coverage and fail-closed assertions; the isolated fixture
loads 30 unconditional
`hostilenetworks:*` recipes (7 Twilight Forest-conditioned recipes stay absent
without that mod), locked by SHA-256
`ca855354ff4d4e15f035911436d46a21721df92510463798ed6c5aef6a3038c6`. Combined
coexistence and unclaimed inventories are recorded only in the matrix report.
No shared workflow or matrix Java list is extended for this module.

## Future acceptance boundary

Support can be reconsidered only after a generic contract can express exact
non-random prediction production, retained Data Model iteration/upgrade state,
and Loot Fabricator selection/queue semantics without approximating live
block-entity or config state away.

## Verification

```bash
./gradlew runHostilenetworksGameTestServer
./gradlew runCompatibilityMatrixGameTestServer
```

Four present-mod GameTests prove the module registers no Hostile Neural Networks
stations/families (namespace and path), representative living-matter vanilla
crafting stays supported, Simulation Chamber and Loot Fabricator stay absent as
custom families, all 30 loaded HNN recipes use exact vanilla shaped/shapeless
classes, and the isolated recipe-inventory digest matches the descriptor. The
contract binds shortage, destination-capacity rollback, and checked-overflow
checks to existing behavioral GameTests rather than treating absence of an HNN
family as transaction evidence. The compatibility matrix loads the
representative artifact plus Placebo through the generated descriptor manifest
and locks the three rejected descriptor IDs without editing shared matrix Java
sources.
