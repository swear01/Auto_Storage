# Integrated Crafting Compatibility

## Status

Integrated Crafting `1.4.6` currently contributes **zero production recipe
families**. This is a deliberate fail-closed result of the Compat Kit audit,
not an absent-mod fallback.

Integrated Crafting remains optional. The representative CI artifact proves
that Auto Storage and a normal dedicated server load safely with the mod
present; it is not an exact player dependency pin.

## Audited representative version

- Integrated Crafting: `1.4.6` (Modrinth `se5g7ewq`);
- official source tag: `1.21.1-1.4.6`;
- source commit: `352bce1cfd1d57aef79aeb17b38895aaac0cac39`;
- jar SHA-256:
  `1c49c774bc8fa28d344592b65ae5d0082c497b21dd5250fcc2aadefedb964cec`;
- audit: `compat/audits/integratedcrafting/1.4.6.json`;
- reviewed contract: `compat/contracts/integratedcrafting.json`.

Runtime companions declared by the contract:

- Integrated Dynamics Modrinth `tG3ZKTep` (`1.33.3`);
- Cyclops Core Modrinth `vEjxRv40` (`1.29.1`);
- Common Capabilities Modrinth `c50bCinZ` (`2.11.5`).

The Integrated Dynamics companion jar is the same GameTestHolder-stripped
runtime copy documented in
[`integrated-dynamics-compatibility.md`](integrated-dynamics-compatibility.md).

## Why no families are registered

Integrated Crafting automates vanilla `CRAFTING` / `SMELTING` / `SMITHING` /
`STONECUTTING` recipes through live network crafting interfaces and recipe
indexes with streaming outputs. Those surfaces are outside Auto Storage's
installed-station transaction contract. Vanilla exact recipe classes already
work without this module.

The only custom recipe candidate, `RecipeDeadBush`, is a special crafting
recipe used for upstream game tests. Shears durability uses player/entity
mutation and `CommonHooks` remainders, so it has no complete Auto Storage
transaction contract.

## Declarative matrix and isolated evidence

The Integrated Crafting descriptor owns the isolated `integratedcrafting`
recipe-inventory digest (`35939862…9aa7`). The isolated fixture calls
`IsolatedRecipeInventoryEvidence.assertMatchesDescriptor` against that digest.
Coexistence effects belong only in the compatibility-matrix report; this module
does not rewrite peer descriptors or commit global expected SHA values.

## Verification

```bash
./gradlew runIntegratedCraftingGameTestServer
```

Four GameTests load the real representative mod and assert that no
Integrated Crafting family/descriptor is registered and that the DeadBush
special recipe remains fail closed.
