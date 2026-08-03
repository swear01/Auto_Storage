# XyCraft Machines Compatibility

## Status

XyCraft Machines `0.7.53` currently contributes **zero production recipe
families** and no Xynergy resource kind. This is a deliberate fail-closed result
of the Compat Kit audit, not an absent-mod fallback.

XyCraft Machines remains optional. The representative CI artifact proves that
Auto Storage and a normal dedicated server load safely with the mod present; it
is not an exact player dependency pin.

## Reproducible audit evidence

- target: XyCraft Machines `0.7.53` (`xycraft_machines`);
- CurseForge file `6872530` /
  `https://www.curseforge.com/minecraft/mc-mods/xycraft-machines/files/6872530`;
- Curse Maven coordinate `curse.maven:xycraft-machines-653791:6872530`;
- jar SHA-256:
  `51e82887cf9b09654e3e34b230e8ff5532db624a9dd1f5c7dfa01c4841b7ffef`;
- required runtime companions at the same release line: XyCraft Core
  `curse.maven:xycraft-653786:6872525` and XyCraft World
  `curse.maven:xycraft-world-653789:6872532`;
- official Java source: unavailable (All Rights Reserved; public tracker only at
  `https://github.com/Soaryn/XyCraftTracker`);
- audit: `compat/audits/xycraft_machines/0.7.53.json`;
- reviewed contract: `compat/contracts/xycraft_machines.json`.
- runtime matrix: `src/compat/xycraft_machines/compat-module.json` claims `xycraft`/`xycraft_machines` recipe inventories, registers no families, and rejects `xycraft_machines:xynergy`.
- isolated fixture verifies the descriptor-owned `xycraft`/`xycraft_machines` recipe-inventory digest `76a97d1fd84fd8e5b38ea80c54f13dedbe27539424b38e99e3c22d0e390e4bdf` (364 recipes); combined coexistence and unclaimed digests are recorded only in the matrix report.

## Why every family is rejected

Compat Kit scanner format 17 classifies 15 concrete recipe classes (legacy name-bucket audits listed 70 candidates). Every recipe class is rejected:

| Family / candidate group | Result | Reason |
|---|---|---|
| Squasher / Blender / Centrifuge / Refinery | rejected | `ProducerTickSystem` multiblocks with live `ServerLevel` validation and Xynergy net power |
| Ark Melter / Cryo Chamber | rejected | recipe `entropy` compared to live machine heat plus producer multiblock/Xynergy execution |
| Crusher | rejected | `ChanceOutput` lists finalized with `RandomSource` (for example 100%/25% cobble→gravel/flint) |
| Extractor / Ore Tap / Isolator / Solidifier / Atmospheric Vacuum | rejected | world `BlockState`/`FluidState`/`IRule`/biome matching and harvest/placement semantics |
| Fluid Tank Fill / Drain | rejected | execute against the live MultiTank multiblock IO graph |
| Buildings | rejected | world API plus `extractItem` capability mutation |
| EMI/JEI helpers, builders, lists, datagen, nested Input/Container | rejected | not server recipe families |

`IXynergyHandler` extends NeoForge `IEnergyStorage`, but usable power lives in a
live `IXynergyNet` graph of block positions. Auto Storage does not register
`xycraft_machines:xynergy` or reinterpret Xynergy as FE/Fuel/Fluid.

## Accepted families

None.

## Verification

```bash
./gradlew runXycraftMachinesGameTestServer
```

Eight present-mod GameTests cover registry absence, Squasher/Blender, chance
Crusher, world Extractor/Ore Tap, MultiTank fill/drain plus Buildings, entropy/
biome producers, Centrifuge/Refinery/Solidifier, and an exhaustive scan seeded
with distinct `FluidTankFillRecipe`, `FluidTankDrainRecipe`, and
`BuildingsRecipe` representatives so every loaded recipe in each audited machine
type remains fail closed. The isolated fixture also asserts the descriptor-owned
recipe-inventory digest. The all-mod compatibility matrix asserts the XyCraft
fail-closed boundary through descriptor-owned matrix evidence under the shared
Craftable `<9 MiB` index gate.
