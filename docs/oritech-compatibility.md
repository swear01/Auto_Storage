# Oritech Compatibility

Auto Storage conditionally loads this module only when `oritech` is present.
Architectury API and GeckoLib remain required companions of that optional target.

## Reproducible audit evidence

- target: Oritech `1.2.9`;
- official source tag: `v1.2.9`;
- source commit: `fdbab1b00602a2d74bb94250bf0cec99baf54616`;
- Modrinth jar SHA-256:
  `fcb30b4f6ae89d115164b82f3c1f9938e9b8a80662047e204562361845764963`;
- dependency coordinate: `maven.modrinth:oritech:gMBPdWrE`;
- runtime companions: Architectury API `ZxYGwlk0` (`13.0.8`), GeckoLib
  `RVIo5f6E` (`4.8.2`);
- audit: `compat/audits/oritech/1.2.9.json` (scanner format 16);
- reviewed contract: `compat/contracts/oritech.json`;
- isolated-fixture recipe-inventory SHA-256 for namespace `oritech`:
  `abc0f6addf6b1f9922e7d05a2ca57b8c210071692030dfaa083b578a59a221f8`
  (613 loaded recipes). Cross-module additions are observed only in the
  generated compatibility-matrix report and never rewrite peer descriptors.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Oritech version on players and does not claim a multi-version matrix.
Changes outside this fixture are handled from concrete reports and a new delta.

## Accepted family

Exact class/type:

- `rearth.oritech.init.recipes.OritechRecipe`;
- `oritech:pulverizer` (`RecipeContent.PULVERIZER`).

Logical station:

- descriptor `auto_storage:oritech_pulverizer`;
- station item `oritech:pulverizer_block`;
- Processing category;
- stable rate of one recipe-work unit per server tick. Oritech increments the
  plain Pulverizer's progress once per eligible tick and accounts configured
  FE/t separately; changing FE/t therefore does not revalue accrued work.

Transaction:

- every simple non-empty item ingredient is consumed once;
- empty/zero `fluidInput` only; `fluidOutputs` must be a non-null list of empty
  stacks, while null or nonempty output data fails closed because the
  pulverizer plan does not emit fluids;
- at most eight item ingredients; eligibility always reserves one possible FE
  input against the 3×3 plan bound so crossing zero cannot change whether the
  recipe belongs to the shared Craftable catalog;
- `results[0]` is the primary item output;
- further `results[i]` are deterministic item remainders; identical
  component-exact keys merge amounts before the plan is built;
- FE equals `energyPerTick × recipe.time` and is omitted when the loaded value
  is zero; station work equals `recipe.time`.

`OritechRecipe.isSpecial()` is true, so the family uses the public
`dynamicDeterministicResources` eligibility overload. It supplies the current
`energyPerTick` as the required dynamic-state token: its plan and cost are
re-resolved after reload, shared Craftable output caches are invalidated when
that token changes, and exact item candidates remain indexed. Eligibility never calls
`Recipe#getIngredients()` for inference; it reads Oritech's public
`getInputs()` / `getResults()` / `getFluidInput()` / `getFluidOutputs()` /
`getTime()` surfaces. Fluid outputs use Architectury's public typed
`FluidStack` API directly; reflection is not used. Architectury artifact
`ZxYGwlk0` is therefore recorded as exact compile ancestry in the format-16
audit and emitted into both bundled and independent-addon compile classpaths,
rather than being hidden by a repository-only Gradle declaration.

## Explicit exclusions

Every other audited candidate remains rejected, including datagen builders,
EMI/JEI helpers, Augment data payloads, RecipeType helpers, and every other
`RecipeContent` type that reuses `OritechRecipe` (grinder, assembler, foundry,
atomic forge, centrifuge, cooler, refinery, generators, deep drill, laser,
particle collision, reactor, steam engine). Compat Kit binds one audited class
to one recipe type/station, and those types need distinct fluid, generator,
multiblock, or live-addon contracts.

Speed/efficiency/burst/yield addon machine state is not inferred from a plain
pulverizer `ItemStack`. Combine-small-dusts post-craft inventory compaction is
not modeled.

Oritech owns its EMI category and workstation metadata. Auto Storage does not
register Oritech workstations into EMI.

## Craftable catalog performance

The post-rebase matrix contains 15,822 recipes. Its first current-head run
correctly failed the unchanged 9 MiB shared-index gate at 9,532,520 bytes.
Catalog entries now reuse the holder's recipe ID and do not retain fixed
variant lists between invalidated rebuilds; the server-owned shared result
cache still keeps ordinary page switches from rebuilding variants. The same
15,822-recipe matrix now passes with 9,250,944 retained bytes, 0.918 ms first
switch, 0.458 ms prefetched-switch p95, and 114,859 bytes per menu.

## Verification

```bash
./gradlew runOritechGameTestServer
./gradlew runCompatibilityMatrixGameTestServer
```

Twenty real GameTests cover pulverizer registration and grinder exclusion,
adamant FE/work craft, raw-iron multi-output remainder, missing ingredient,
insufficient FE/work, destination overflow, a true post-commit mixed Item+FE
rollback, config reload at 7 FE/t, legal zero-FE progress, and the 150-tick
platinum duration, stable accrued recipe ticks across a config reload, and
shared Craftable result invalidation when FE/t changes. The same config boundary
also proves that crossing zero FE does not change oversized-family eligibility.
Fail-closed checks cover nonempty and null fluid data, non-simple
ingredients, oversized ingredient+FE layouts, while another test merges
duplicate exact item outputs. The all-mod compatibility matrix protects coexistence.
