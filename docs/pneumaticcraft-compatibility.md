# PneumaticCraft compatibility

## Status

PneumaticCraft 8.2.22 currently contributes **zero production recipe families** and no Air resource kind. This is a deliberate fail-closed result of the upstream API audit, not an absent-mod fallback.

PneumaticCraft remains optional. The representative CI artifact proves that Magic Storage and a normal dedicated server load safely with the mod present; it is not an exact player dependency pin.

## Audited representative version

- PneumaticCraft: Repressurized `8.2.22` (`Dd6V8eOF`)
- official tag `v8.2.22`, commit `774a95139143965ce74ce8647be489e118575896`
- Minecraft `1.21.1`, NeoForge

Other installed versions are handled from player reports rather than a multi-version CI matrix.

## Why Air is not stored

`IAirHandler` exposes `getAir()` and `void addAir(int)`, permits negative air for vacuum, and has no simulate or accepted-amount result. Pressure is derived from air divided by mutable volume. Magic Storage therefore cannot provide its required simulate-then-commit guarantee for blocks or containers without guessing external mutation.

Magic Storage does not register `pneumaticcraft:air`, does not expose an Air Bus bridge, and does not reinterpret Air or pressure as FE, Fluid, Fuel, or station work.

## Recipe-family audit

| Family | Result | Reason |
|---|---|---|
| Pressure Chamber | rejected | pressure is retained multiblock state, not a consumed amount; recipe has no exact duration or air cost |
| Thermopneumatic Processing Plant | rejected | ambient heat, pressure, recipe speed, and live air multiplier affect execution |
| Fluid Mixer | rejected | processing consumes pressure-dependent Air from a live machine |
| Assembly Drill/Laser | rejected | multiblock composition, program, movement, upgrades, and per-tick Air determine execution |
| Refinery | rejected | temperature and installed output-block count are required live station state |
| Heat Frame Cooling | rejected | world temperature, container behavior, and optional bonus output are external state |
| Explosion Crafting | rejected | execution is a world explosion with configured loss |

The deterministic-looking item/fluid payload alone is insufficient. Registering only that payload would let the terminal bypass the machine conditions that define the recipe.

## Future acceptance boundary

Support can be reconsidered only after a generic contract can express retained station state and a simulation-safe Air source/sink without treating a pressure threshold as consumed Air. The contract must also represent multiblock composition and exact live execution cost. Until then, no PneumaticCraft-only approximation is allowed.

## Verification

The isolated fixture loads the real representative mod and runs nine GameTests covering registry absence, positive and multi-output Pressure Chamber recipes, negative Air/vacuum semantics, Thermo Plant, Fluid Mixer, Assembly, Refinery, Heat Frame, Explosion, and an exhaustive scan that every loaded recipe in each audited machine type remains fail closed.

```bash
./gradlew runPneumaticCraftGameTestServer
```
