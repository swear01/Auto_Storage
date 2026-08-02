# Railcraft Reborn Compatibility

## Status

Railcraft Reborn `1.2.10` currently contributes **zero production recipe
families**. This is a deliberate fail-closed result of the Compat Kit audit
(outcome **C**), not an absent-mod fallback and not an empty recipe adapter.

Railcraft remains optional. The representative CI artifact proves that Auto
Storage and a normal dedicated server load safely with the mod present; it is
not an exact player dependency pin. Vanilla-class datapack recipes under the
`railcraft` namespace still flow through Auto Storage's built-in exact crafting
families when they are non-special and concrete.

## Reproducible audit evidence

- target: Railcraft Reborn `1.2.10` (`railcraft`);
- Modrinth version `BrIwB6GH` /
  `https://cdn.modrinth.com/data/rO6kKst6/versions/BrIwB6GH/railcraft-reborn-1.21.1-1.2.10.jar`;
- Modrinth Maven coordinate `maven.modrinth:railcraft-reborn:BrIwB6GH`;
- jar SHA-256:
  `7de3dfeac277da57f9897822824332c99e53b9d36956143b38c0966f39144328`;
- official source: https://github.com/railcraft-reborn/railcraft tag `1.2.10`
  / commit `7b89837df369bb0552d81016c46840792bd13d23`;
- scanner format 16 audit: `compat/audits/railcraft/1.2.10.json`;
- reviewed contract: `compat/contracts/railcraft.json`.

## Why every family is rejected

Compat Kit classified 13 concrete `Recipe` implementations. Every candidate is
rejected:

| Family / candidate group | Result | Reason |
|---|---|---|
| Crusher | rejected | `CrusherModule.pollOutputs` uses `RandomSource` against per-output probability |
| Blast Furnace | rejected | formed furnace multiblock plus live `burnTime` / `ItemStack.getBurnTime` fuel and slag output |
| Coke Oven | rejected | formed furnace multiblock plus live creosote tank and fluid-container processing state |
| Rolling | rejected | live craft-matrix progress/`balanceSlots`; powered variant drains world `Charge.distribution` |
| Tie / rotor repair / ticket / painting / cart disassembly / Patchouli book | rejected | special crafting with live fluid-capability drain, damage, component copy, or helper book semantics; not independent station families |

Auto Storage does not register Railcraft workstations into EMI. Railcraft owns
its own EMI/JEI categories.

## Accepted families

None.

## Verification

```bash
./gradlew runRailcraftGameTestServer
```

Eight present-mod GameTests cover registry absence, chance Crusher, Blast
Furnace, Coke Oven, Rolling, fluid-tie/rotor-repair special crafting, ticket /
minecart-disassembly / Patchouli-book special crafting, and an exhaustive scan
that every loaded recipe in each audited machine type remains fail closed. The
all-mod compatibility matrix asserts the Railcraft fail-closed boundary and the
reviewed recipe-inventory digest under the shared Craftable `<9 MiB` index gate.

Compat Kit passing report (local gate):
`build/compat-kit/railcraft-report.json`.
