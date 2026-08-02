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
- audit: `compat/audits/integrateddynamics/1.33.3.json`;
- reviewed contract: `compat/contracts/integrateddynamics.json`.

The published jar embeds shadowed `integrateddynamicscompat` as a second
NeoForge `[[mods]]` entry. Compat Kit scan therefore requires
`--mod-id integrateddynamics` while still hashing the full representative jar.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Integrated Dynamics version on players.

Required runtime companions for the fixture:

- Cyclops Core Modrinth `vEjxRv40` (`1.29.1`);
- Common Capabilities Modrinth `c50bCinZ` (`2.11.5`).

The published jar embeds a `@GameTestHolder` Refined Storage aspect suite.
NeoForge `GameTestHooks` classloads every holder before namespace filtering, so
fixture/matrix runtimes use a byte-identical copy of the audited jar with only
`GameTestsAspectsRefinedStorage` removed. Audit SHA verification still resolves
the pristine Modrinth artifact. Refined Storage is not an Auto Storage recipe
target and is not loaded as a companion.

## Accepted families

| Family | Station | Cost and resources |
|---|---|---|
| `RecipeDryingBasin` | Drying Basin | optional exact item and/or sized fluid input; exact item output; recipe duration as station work |
| `RecipeMechanicalDryingBasin` | Mechanical Drying Basin | same IO rules; duration work plus `consumptionRate × duration` FE from loaded config |
| `RecipeMechanicalSqueezer` | Mechanical Squeezer | exact item input; only `chance == 1.0F` item outputs; optional exact fluid; duration work plus `consumptionRate × duration` FE |

Tag-derived `ItemStackFromIngredient` outputs, non-simple ingredients, empty
plans, non-positive duration, and non-positive FE totals fail closed. Chance
item outputs below `1.0F` fail closed.

## Explicit exclusions

- Manual Squeezer: player-driven block height compression plus chance outputs;
- Squeezer/Mechanical Squeezer facades and serializers/configs;
- Special crafting recipes (NBT clear, energy-container combination, facade,
  variable copy, omni-directional);
- Logic-programmer recipe value types, operators, JEI/REI helpers, and network
  recipe-handler operators.

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

Eight GameTests cover registration/manual-Squeezer exclusion, Drying Basin
fluid/duration commit, Mechanical Squeezer item/fluid/FE/work commit,
missing-ingredient and full-destination atomic no-ops, `Long.MAX_VALUE` energy,
chance-output rejection, and loaded mechanical energy config.
