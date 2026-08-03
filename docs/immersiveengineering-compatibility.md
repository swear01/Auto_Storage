# Immersive Engineering Compatibility

Auto Storage's Compat Kit review of Immersive Engineering `12.4.2-194`
accepts **zero production recipe families**. This is an evidence-backed
fail-closed result (outcome **C**), not an absent-mod fallback and not an empty
recipe adapter claiming support.

The present-mod module entrypoint loads only when `immersiveengineering` is
installed and registers no stations or recipe families. Vanilla-class recipes
that Immersive Engineering ships under its namespace (crafting, smelting,
smoking, stonecutting) remain covered by Auto Storage's built-in exact adapters
without a custom module.

## Reproducible audit evidence

- target: Immersive Engineering `12.4.2-194` (`immersiveengineering`);
- Modrinth version ID `uNRARSH2` / Maven
  `maven.modrinth:immersiveengineering:uNRARSH2`;
- download URL used for local SHA verification:
  `https://cdn.modrinth.com/data/tIm2nV03/versions/uNRARSH2/ImmersiveEngineering-1.21.1-12.4.2-194.jar`;
- jar SHA-256:
  `45942985a4a4aebf265b8e22a0c54a96208637471f36f2532ff5d4911322debc`;
- official source tag `12.4.2-194`, commit
  `583a182549d284a35c813b55ac2a0d1fddcf945a`;
- scanner format **17** / candidate classifier **4** audit:
  `compat/audits/immersiveengineering/12.4.2-194.json`;
- reviewed contract: `compat/contracts/immersiveengineering.json`;
- reachable ancestry: 10 artifacts / 9 exact Maven coordinates (NeoForge/Minecraft
  platform binary `2382ea29…eb5f`, plus bus, mixin, fastutil, guava, brigadier,
  JEI common API, Jade, The One Probe, and CC Tweaked core API);
- reviewed repositories include Modrinth and `https://maven.squiddev.cc` for the
  CC Tweaked compile ancestry coordinate.

JarJar embeds BlockModelSplitter and DualCodecs inside the representative jar,
so the reviewed contract declares no additional runtime dependencies beyond the
primary Immersive Engineering artifact.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Immersive Engineering version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

Compat Kit format-17 structural scan keeps **44** actual recipe-class candidates
(legacy format-7 name-shaped inventory of 128 is superseded). Every candidate is
rejected in the committed contract. The runtime machine families behind that
inventory include:

| Family | Result | Reason |
|---|---|---|
| Alloy Smelter | rejected | stone multiblock; live vanilla furnace fuel burn; `TagOutput` may resolve via mod preference |
| Coke Oven | rejected | stone multiblock; creosote tank capacity/batching is live state |
| Blast Furnace / BlastFurnaceFuel | rejected | stone multiblock; separate live burn state; tag preference outputs |
| Crusher / Arc Furnace / Arc recycling | rejected | multiblock; live `RecipeMultiplier` energy/time; chance secondaries |
| Metal Press / packing helpers | rejected | multiblock; retained mold; live multipliers |
| Bottling / Fermenter / Squeezer / Mixer / Refinery / Sawmill | rejected | multiblock with live multipliers and/or fluid inventory state |
| Blueprint crafting | rejected | typed Engineers Blueprint plus workbench/auto-workbench multiblock path |
| Garden Cloche / ClocheFertilizer | rejected | `StackWithChance` plus `ApiUtils.RANDOM` outputs / live growth modifiers |
| GeneratorFuel / ThermoelectricSource / WindmillBiome / MineralMix | rejected | live energy/world configuration, not craftable Auto Storage families |
| Special crafting / fluid-aware / jerrycan / repair / shader helpers | rejected | not independent deterministic Auto Storage machine families |

Typed resources were not introduced. Multiblock composition, live fuel/tank
state, config-backed `DoubleSupplier` multipliers, chance outputs, and
`TagOutput` preference resolution cannot be reduced to a simulate-then-commit
plan without approximating those conditions away.

## Future acceptance boundary

Support can be reconsidered only after a generic contract can express retained
multiblock composition, exact fuel/tank/catalyst state without live BE mutation,
deterministic non-chance outputs (no `TagOutput` preference guessing), and
energy/time that do not depend on mutable `RecipeMultiplier` suppliers.

## Verification

```bash
./gradlew runImmersiveengineeringGameTestServer
```

Eight present-mod GameTests prove the module registers no Immersive Engineering
stations/families, representative Alloy/Coke Oven/Blast Furnace/Crusher/Cloche/
Metal Press/Arc Furnace recipes stay unsupported, and every loaded recipe in
each audited custom recipe type remains fail closed. The isolated fixture also
verifies the owning descriptor's `immersiveengineering` namespace
`recipeInventory` digest
`b733a4b670dbb507a71df7f819c2296f627d42f2ed89240c6040c9c55c445c7d`
(1100 loaded recipe ids) via `IsolatedRecipeInventoryEvidence`. The all-mod
compatibility matrix loads the representative artifact through the module
descriptor and asserts the zero-family boundary via empty
`descriptors`/`acceptedRecipes`; combined coexistence and unclaimed inventories
are recorded only in the matrix report.

```bash
tools/compat-kit/compat-kit verify \
  compat/contracts/immersiveengineering.json \
  --audit compat/audits/immersiveengineering/12.4.2-194.json \
  --jar build/compat-kit/artifacts/ImmersiveEngineering-1.21.1-12.4.2-194.jar \
  --classpath <exact ancestry jars from the audit> \
  --bundled . \
  --output build/compat-kit/immersiveengineering-report.json
```

Passing report path: `build/compat-kit/immersiveengineering-report.json`.

### Matrix performance evidence

With Immersive Engineering present on the full descriptor matrix (including
Create Enchantment Industry), the fixed gates stay unchanged (do **not** widen
them). The earlier `craftable_prepare_ms≈81.38` result was initially attributed
only to host contention because its ≈424 candidates / 98 variants / 86 outputs
matched quieter peers. PR #62 CI run `30796564023` disproved that conclusion by
failing the same unchanged gate twice at 52.990 and 68.603 ms. The latter run
had 20,012 recipes, 453 candidates, 98 variants, and 86 outputs; 44 ms was in
variant resolution rather than a full scan of IE's ~1,100 unclaimed recipes.

The root cause was stack-independent, already-resolved typed catalog matches still
calling `RecipeAdapterMatch.resolveVariantsFromSnapshot`, which repeated
adapter resolution. `CatalogEntry.resolveVariants` now uses that listing-local
base match directly when it already contains a typed plan and the adapter reports
that variants do not require available stacks. Built-in and legacy item adapters
still run their level/output validation; pending typed plans and stack-dependent
smithing/dynamic variants retain the existing resolution path.
This does not restore `fixedVariants` or any recipe-keyed retained cache; the
next-tick `releaseTransientMatches()` lifetime from #79 remains unchanged.

Quiet exclusive `runCompatibilityMatrixGameTestServer` (three holder processes
reserved three heavy-Gradle slots and the matrix acquired the fourth) measured:

- `craftable_prepare_ms` = 14.065 (< 50);
- recipes = 20,012 / craftable_outputs = 86;
- `shared_index_retained_bytes` = 0 (the fixture's nonnegative full-GC delta
  floor; < 9 MiB and no fixed-variant retention was added);
- per-menu retained = 116,959 bytes (< 128 KiB);
- All 3 required matrix tests passed.

Historical pre-fix quiet readings were 35.36 ms / 3,907,776 shared bytes at
20,012 recipes and 16.351 ms / 3,898,384 shared bytes at 19,750 recipes. They
remain useful memory baselines, but no longer justify labeling the CI failures
as contention-only.

Shared retained-index gate remains `9L * 1024L * 1024L` (=9,437,184 bytes).
