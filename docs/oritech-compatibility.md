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
  `qj2pTqCr` (`4.6.6`);
- audit: `compat/audits/oritech/1.2.9.json`;
- reviewed contract: `compat/contracts/oritech.json`.

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
- live work rate from
  `OritechConfig.processingMachines.pulverizerData.energyPerTick`
  (default `32` work/tick), deferred until config load via
  `MachineVariant.derived`.

Transaction:

- every simple non-empty item ingredient is consumed once;
- empty/zero `fluidInput` only; fluid pulverizer holders stay unsupported;
- `results[0]` is the primary item output;
- further `results[i]` are deterministic item remainders;
- FE and station work each equal `energyPerTick × recipe.time` at base addon
  multipliers (`1`).

`OritechRecipe.isSpecial()` is true, so the family uses the public
`deterministicResources` eligibility overload. Eligibility never calls
`Recipe#getIngredients()` for inference; it reads Oritech's public
`getInputs()` / `getResults()` / `getFluidInput()` / `getTime()` surfaces.

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

## Verification

```bash
./gradlew runOritechGameTestServer
```

Eight real GameTests cover pulverizer registration and grinder exclusion,
adamant FE/work craft, raw-iron multi-output remainder, missing ingredient,
insufficient FE, insufficient work, destination overflow/`Long.MAX_VALUE`
rollback, and live energy-to-FE/work mapping. The all-mod compatibility matrix
protects coexistence.
