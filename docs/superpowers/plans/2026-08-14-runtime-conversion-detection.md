# Runtime Datapack-Driven Conversion Detection Plan

> Status: design. GitHub #116 tracks the epic. The Transform page currently
> mixes hardcoded per-module values (DE base rate, EnderIO burn formula,
> AA static 20) with live reads (config-backed IF/Oritech/JDT/PB resolvers,
> Mekanism Chemical Conversion rebuilt from the recipe manager on server
> start, GeneratorGalore live JSON registry + SolidFuelMap datamap). Goal:
> no conversion recipe value is ever hardcoded in Auto Storage — the module
> scans installed mods' conversion recipes itself, reflects datapack edits,
> and caches by recipe identity.

## Goals

1. A server-owned `ConversionScanner` discovers item→resource conversions
   from the live recipe manager, chemical datamaps, and config at server
   start and after every datapack reload — never from hardcoded tables.
2. Players editing datapack recipes see the new values immediately (next
   reload), and the shared Transform cache invalidates by recipe identity.
3. Detection patterns are pluggable (`ConversionPattern`); the user owns
   the pattern list and can add families without touching Auto Storage.
4. Detected uses coexist with descriptor/contract-backed uses in one
   server registry; every use still goes through the exact
   simulate-then-commit atomic transaction.
5. The Transform target picker becomes input-driven when output resources
   are numerous: place an input item → available conversions are listed,
   so the player never browses hundreds of output resources.

## Current boundary

- Mekanism Chemical Conversion is already runtime-driven
  (`MekanismTransformCompat.onServerStarted` rebuilds a static map from
  `ItemStackToChemicalRecipe` holders); it is the reference pattern.
- Mekanism Energy Conversion is hardcoded (10,000 / 90,000 verified
  values) and must move into the same scanner.
- Generator resolvers that read `ModConfigSpec` at resolve time (IF
  Pitiful/Mycelial, Oritech, JDT, Productive Bees, RFTools Coal) are
  already live for config edits; their fuel *sets* are still code (tag or
  `is()` checks) where the machine itself accepts the full burnable set.
- Hardcoded values to migrate: Draconic Evolution base 40 FE/tick,
  EnderIO Stirling burn formula (0.375 × 0.8), Actually Additions 20
  FE/tick static, Botania Thermalily/Endoflame mana values (config-driven
  in Botania), Powah Magmator formula.
- `transform-candidates` (dev-time) and `dump_conversion_recipes.py`
  already surface conversion recipes; the runtime scanner reuses the same
  pattern vocabulary at runtime.

## Design

### ConversionScanner (server)

- `ConversionScanner` runs on `ServerStartedEvent` and
  `OnDatapackSyncEvent`/`AddReloadListenerEvent`, owning a
  `Map<ResourceLocation patternId, ConversionPattern>`.
- Each `ConversionPattern` implements:
  `Optional<TransformProviderApi.Result> resolve(ItemStack input)` plus a
  `revisionKey()` returning a digest of everything it reads (recipe
  holder ids + payload hashes for recipe-manager patterns, config values
  for config patterns, datamap digests for datamap patterns).
- The scanner publishes a combined `TransformProvider` per produced target
  kind; Transform uses that already exist (descriptor/contract-backed)
  remain untouched. Cache identity = per-pattern revision key; the shared
  Transform result invalidates when any key changes.
- Fail-closed rules from docs/compat-kit.md still apply: only one-way,
  deterministic, discrete item→stored-resource conversions with exact
  output amounts are emitted; chance/live-state/world inputs are never
  scanned.

### Built-in patterns (user-owned list, first slice)

| Pattern | Source | Output |
|---|---|---|
| `mekanism:chemical_conversion` | `ItemStackToChemicalRecipe` holders | mekanism chemical |
| `mekanism:energy_conversion` | `ItemStackToEnergyRecipe` holders | FE |
| `mekanism:chemical_fuel` | `ChemicalFuel` datamap (gas) | FE |
| `generatorgalore:solid_fuel_map` | block `SolidFuelMap` datamap + generator JSON registry | FE |
| `vanilla:burn_time_rate` | `ItemStack.getBurnTime` + config rate per machine | FE |

Additional patterns (user-provided) register through the public registry
without code changes to Auto Storage.

### Transform target picker UX (#121)

- Current sidebar lists produced targets; with many output resources the
  player cannot find the conversion they want by browsing outputs.
- Change: the input slot drives the picker — placing an item shows every
  conversion that accepts it (target group + use cards), i.e. an
  input-first search. Empty input keeps the existing browse mode.
- Generators must appear in the picker regardless of output count.

## Migration order

1. #117 ConversionScanner core (registry + reload + revision cache). DONE
2. #118 Mekanism chemical/energy conversion patterns. DONE
3. #119 Generator fuel patterns: `chemical_fuel` (gas generator),
   `solid_fuel_map` (GeneratorGalore). DONE
4. #120/#122 live-value audit: config-backed resolvers (IF/Oritech/JDT/
   Productive Bees/RFTools/ID) were already live; DE (40 FE/tick), AA
   (20), EnderIO (0.375×0.8), and Botania (27,000 mana, burnTime×3) are
   mod code constants with no config or recipe — the machine itself
   hardcodes them, so there is nothing to read live; documented as
   machine truth in each compat doc. Closed as no-op.
5. #121 Picker UX input-first search. DONE
6. #124 all generator modules unified onto ConversionPattern (enderio/
   AA/ID/DE/IF/Oritech/Powah/PB/RFTools/JDT patterns; fixtures unchanged
   green). DONE
6. ~~#123~~ closed: no custom agent framework — the user already runs
   DeepSeek V4 Flash through the existing Hapi fleet tooling.

## Out of scope

- Recipe-family crafting (item→item) stays on the Craftable page; the
  scanner covers only item→stored-resource conversions.
- Chance outputs, multiblocks, passive generators, bidirectional
  converters, and world/entity inputs remain fail-closed per spec.
