# Immersive Engineering Compatibility

Auto Storage's Compat Kit review of Immersive Engineering `12.4.2-194`
accepts two deterministic machine families: Arc Furnace and Bottling Machine.
Sawmill, Crusher, Alloy Smelter, and Metal Press remain fail-closed because their
sawblade/fuel/mold/tag-output state is not represented by the exact transaction.
The remaining custom recipe classes and live multiblock mechanisms remain
fail-closed.

The present-mod module entrypoint loads only when `immersiveengineering` is
installed and registers the two reviewed master-block stations and their
bounded recipe families. Vanilla-class recipes that Immersive Engineering
ships under its namespace (crafting, smelting, smoking, stonecutting) remain
covered by Auto Storage's built-in exact adapters.

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
(legacy format-7 name-shaped inventory of 128 is superseded). Two deterministic
subsets are accepted in the committed contract; the remaining candidates are
rejected. The runtime machine families behind that inventory include:

| Family | Result | Reason |
|---|---|---|
| Alloy Smelter | rejected | live vanilla furnace fuel is not represented by the exact transaction |
| Coke Oven | rejected | stone multiblock; creosote tank capacity/batching is live state |
| Blast Furnace / BlastFurnaceFuel | rejected | stone multiblock; separate live burn state; tag preference outputs |
| Crusher | rejected | public TagOutput origin is not exposed, so tag-backed output preference cannot be fail-closed |
| Arc Furnace | accepted subset | master-block station; explicit additives/output, base energy, and work time; slag/chance boundaries rejected |
| Arc recycling | rejected | live recycling and multiblock state |
| Metal Press | rejected | mold retention and public TagOutput origin are not represented by the exact transaction |
| Metal Press packing helpers | rejected | retained mold and unsupported helper semantics |
| Bottling Machine | accepted subset | master-block station; exactly one item input plus exact fluid input, output, base energy, and work time |
| Sawmill | rejected | sawblade-dependent stripped/output modes and secondary products are not represented by the station abstraction |
| Fermenter / Squeezer / Mixer / Refinery | rejected | multiblock with live multipliers and/or fluid inventory state |
| Blueprint crafting | rejected | typed Engineers Blueprint plus workbench/auto-workbench multiblock path |
| Garden Cloche / ClocheFertilizer | rejected | `StackWithChance` plus `ApiUtils.RANDOM` outputs / live growth modifiers |
| GeneratorFuel / ThermoelectricSource / WindmillBiome / MineralMix | rejected | live energy/world configuration, not craftable Auto Storage families |
| Special crafting / fluid-aware / jerrycan / repair / shader helpers | rejected | not independent deterministic Auto Storage machine families |

The accepted families use the existing item, NeoForge Energy, station-work,
and engineer's-hammer tool transaction; Arc Furnace also consumes one
`auto_storage:graphite_electrode` descriptor-work unit per craft. Multiblock
composition, live fuel/tank state, config-backed `DoubleSupplier` multipliers,
chance outputs, and `TagOutput` preference resolution remain rejected where they
cannot be reduced to a simulate-then-commit plan without approximating those
conditions away.

## Future acceptance boundary

Support can be reconsidered only after a generic contract can express retained
multiblock composition, exact fuel/tank/catalyst state without live BE mutation,
deterministic non-chance outputs (no `TagOutput` preference guessing), and
energy/time that do not depend on mutable `RecipeMultiplier` suppliers.

## Verification

```bash
./gradlew runImmersiveengineeringGameTestServer
```

Ten present-mod GameTests prove the two reviewed stations/families register,
representative accepted machine recipes execute or classify, Sawmill/Crusher/
Alloy Smelter/Metal Press boundaries remain fail closed, unsafe Coke Oven/Blast
Furnace/Cloche boundaries remain unsupported, and every loaded recipe in each of
those three rejected custom recipe types remains fail closed.
The isolated fixture also verifies the owning descriptor's `immersiveengineering` namespace
`recipeInventory` digest
`b733a4b670dbb507a71df7f819c2296f627d42f2ed89240c6040c9c55c445c7d`
(1100 loaded recipe ids) via `IsolatedRecipeInventoryEvidence`. The all-mod
compatibility matrix loads the representative artifact through the module
descriptor and checks exact representative IDs for both accepted families;
combined coexistence and unclaimed inventories are recorded only in the matrix
report.

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
Create Enchantment Industry and Mystical Agriculture on current main
`1102032`), the fixed gates stay unchanged (do **not** widen them). The earlier
`craftable_prepare_ms≈81.38` result was initially attributed only to host
contention because its ≈424 candidates / 98 variants / 86 outputs matched
quieter peers. PR #62 CI run `30796564023` disproved that conclusion by failing
the same unchanged gate twice at 52.990 and 68.603 ms. The latter run had
20,012 recipes, 453 candidates, 98 variants, and 86 outputs; 44 ms was in
variant resolution rather than a full scan of IE's ~1,100 unclaimed recipes.

The root cause was stack-independent catalog matches repeatedly calling
`RecipeAdapterMatch.resolveVariantsFromSnapshot`, plus repeated per-item source
lookups while resolving component-sensitive variants. `CatalogEntry.resolveVariants`
now reuses every stack-independent listing-local base match; stack-dependent
smithing/dynamic variants retain the existing resolution path, as do dynamic
families with a pending typed plan. Exhaustive item coverage uses exact
representative totals as a fast success path and scans matching sources when
those totals are insufficient; non-exhaustive/custom ingredients still scan
matching sources. Core item sources and item totals are memoized lazily within
one listing. Actual selection and craft execution continue to revalidate the
live holder and full transaction.
This does not restore `fixedVariants` or any recipe-keyed retained cache; the
next-tick `releaseTransientMatches()` lifetime from #79 remains unchanged.

Quiet exclusive `runCompatibilityMatrixGameTestServer` after rebase onto
`1102032` (three holder processes reserved three heavy-Gradle slots and the
matrix acquired the fourth) measured:

- `craftable_prepare_ms` = 26.484 (< 50);
- recipes = 21,022 / craftable_outputs = 87;
- `shared_index_retained_bytes` = 4,103,576 (< 9 MiB; no fixed-variant
  retention was added);
- per-menu retained = 116,437 bytes (< 128 KiB);
- All 3 required matrix tests passed.

Historical pre-fix quiet readings were 35.36 ms / 3,907,776 shared bytes at
20,012 recipes and 16.351 ms / 3,898,384 shared bytes at 19,750 recipes. An
earlier post-fix exclusive reading at 20,012 recipes was 14.065 ms with a
nonnegative GC-delta shared floor of 0 bytes. They remain useful baselines, but
no longer justify labeling the CI failures as contention-only.

Shared retained-index gate remains `9L * 1024L * 1024L` (=9,437,184 bytes).

The current combined matrix now uses concrete representative recipe IDs rather
than recipe-type IDs for all accepted families. The latest 10,000-type run
passed with `craftable_prepare_ms = 36.344`,
`shared_index_retained_bytes = 5,894,704`, and per-menu retained bytes of
119,526. The 30,000-type stress run passed with `craftable_prepare_ms = 32.742`,
`shared_index_retained_bytes = 5,862,608`, and per-menu retained bytes of
120,001.
