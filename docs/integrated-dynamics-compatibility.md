# Integrated Dynamics Compatibility

Auto Storage supports the audited deterministic Integrated Dynamics machine
slice when `integrateddynamics` is present. The module compiles in the isolated
`compatIntegrateddynamics` source set against the public Auto Storage API plus
Integrated Dynamics, Cyclops Core, and Common Capabilities.

## Reproducible audit evidence

- target: Integrated Dynamics `1.33.3` (Modrinth `tG3ZKTep`);
- official source tag: `1.21.1-1.33.3`;
- source commit: `b232bc068c31b7ad98e437775e15b76b98dda6f7`;
- jar SHA-256:
  `7c508ebd4048a589812562740132d39802ea0034e11a011fbfd53188b39fdba2`;
- audit: `compat/audits/integrateddynamics/1.33.3.json` (scanner format 17;
  1,475 target classes; 157 matched source files; 10 structural recipe classes;
  18 reachable ancestry artifacts; 2 exact ancestry coordinates);
- reviewed contract: `compat/contracts/integrateddynamics.json`.

The committed audit uses the same normalized NeoForge/Minecraft binary-pipeline
platform jar as AE2 (`2382ea29…eb5f`). Exact Modrinth companions Cyclops Core
`vEjxRv40` and Common Capabilities `c50bCinZ` are the audited compile/runtime
coordinates. Complete Compat Kit verification reopens the exact target jar and
all eighteen ancestry jars.

The published jar embeds shadowed `integrateddynamicscompat` as a second
NeoForge `[[mods]]` entry. Compat Kit scan / migration therefore selects
`--mod-id integrateddynamics` (or the legacy audit target mod ID) while still
hashing the full representative jar.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Integrated Dynamics version on players.

Required runtime companions for the fixture:

- Cyclops Core Modrinth `vEjxRv40` (`1.29.1`);
- Common Capabilities Modrinth `c50bCinZ` (`2.11.5`).

The published jar embeds a `@GameTestHolder` Refined Storage aspect suite.
NeoForge `GameTestHooks` classloads every holder before namespace filtering, so
the reviewed contract declares one exact-SHA runtime artifact transform that
removes only `GameTestsAspectsRefinedStorage`. The shared Compat Kit transform
pipeline supplies that transformed jar to the Integrated Dynamics, Integrated
Crafting, and compatibility-matrix runtimes, while audit verification still
resolves the pristine Modrinth artifact. Refined Storage is not an Auto Storage
recipe target and is not loaded as a companion.

## Accepted families

| Family | Station | Cost and resources |
|---|---|---|
| `RecipeDryingBasin` | Drying Basin | optional exact item and/or sized fluid input/output; item is primary when present, otherwise fluid is primary; recipe duration as station work |
| `RecipeMechanicalDryingBasin` | Mechanical Drying Basin | same conditional item/fluid output roles; duration work plus `consumptionRate × duration` FE from loaded config |
| `RecipeMechanicalSqueezer` | Mechanical Squeezer | exact item input; only `chance == 1.0F` item outputs; fluid is primary only when no item output exists, otherwise remainder; duration work plus `consumptionRate × duration` FE only when that product is positive |

Each descriptor uses its localized logical recipe-family name in the Stations
page and Processing resource tooltip. The label does not come from a particular
installed item stack, so representative items or future variants cannot rename
the family.

Tag-derived `ItemStackFromIngredient` outputs, non-simple ingredients, empty
plans, non-positive duration, and negative FE totals fail closed. A zero loaded
consumption rate yields zero FE and omits the energy input rather than building
an invalid non-positive consume amount. Chance item outputs below `1.0F` fail
closed. A present drying item or fluid input that is not exact rejects the whole
recipe, including mixed exact-fluid plus non-exact-item cases. A declared
`ItemStackFromIngredient` output never becomes "absent" merely because an exact
fluid output is also present; both Drying Basin families reject that whole
recipe.

## Explicit exclusions

- Manual Squeezer: player-driven block height compression plus chance outputs;
- Squeezer/Mechanical Squeezer facades and serializers/configs;
- Special crafting recipes (NBT clear, energy-container combination, facade,
  variable copy, omni-directional);
- Logic-programmer recipe value types, operators, JEI/REI helpers, and network
  recipe-handler operators.

Format-17 keeps ten structural recipe classes. The seven non-accepted classes
above remain rejected in the reviewed contract; legacy format-7 name-shaped
helpers are no longer contract families.

## Declarative matrix and isolated evidence

The Integrated Dynamics descriptor owns the isolated `integrateddynamics`
recipe-inventory digest (`22990962…da9b` / 252 recipes). The isolated fixture calls
`IsolatedRecipeInventoryEvidence.assertMatchesDescriptor` against that digest.
Cross-module coexistence inventories are recorded only in the compatibility
matrix report (`build/reports/terminal-scale-*.json`); this module does not pin
peer Create digests or commit global coexistence/unclaimed expected SHA values.

## Verification

```bash
./gradlew runIntegratedDynamicsGameTestServer
```

Ten GameTests cover registration/manual-Squeezer exclusion, Drying Basin
fluid/duration commit, Mechanical Drying Basin fluid/FE/work commit,
Mechanical Squeezer item/fluid/FE/work commit and exact fluid remainder,
missing-ingredient and full item-destination atomic no-ops, `Long.MAX_VALUE`
fluid-output overflow rollback, chance-output and derived-item-plus-fluid
rejection, and loaded mechanical energy config.

Full Compat Kit CLI `verify` for this module must also pass with the committed
format-17 audit, exact jar, and staged ancestry classpath.

## Coal Generator (Transform)

`auto_storage:integrateddynamics_coal_generator` is a PROCESS descriptor (one
work/tick) plus a time-based Transform use: any smelting-burnable fuel
converts to FE over the exact burn duration. Verified against Integrated
Dynamics 1.33.3 bytecode: `BlockEntityCoalGeneratorConfig.energyPerTick`
(default 20) FE per burn tick, burn duration = Forge smelting burn time.
Coal: 1,600 ticks → 32,000 FE.
