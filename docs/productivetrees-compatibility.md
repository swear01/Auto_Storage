# Productive Trees Compatibility

Auto Storage's Compat Kit review of Productive Trees `1.21.1-1.0.0` accepts
the deterministic **sawmill** recipe family and rejects the remaining world
mechanics.

## Reproducible audit evidence

- target: Productive Trees `1.21.1-1.0.0` (`productivetrees`);
- CurseForge file `8190022` / Curse Maven
  `curse.maven:productivetrees-867074:8190022`;
- jar SHA-256:
  `bddbff2dbf41e22d14d5a1c080762c5a7f0882b7574f055420e7de7f1892bc9d`;
- official source: https://github.com/JDKDigital/productivetrees
  (`dev-1.21.0` branch);
- audit: `compat/audits/productivetrees/1.0.0.json`;
- reviewed contract: `compat/contracts/productivetrees.json`.

The scanner-format-17 audit binds 158 target classes, the exact 3,504-recipe
data inventory, and six reachable ancestry artifacts (normalized NeoForge/
Minecraft platform binary, productivelib 0.2.0 jarjar extraction, Productive
Bees 13.13.5, Almost Unified, EMI, and JEI). Productive Lib has no standalone
Maven coordinate; the reviewed jarjar extraction lives at
`compat/local/productivelib-1.21.1-0.2.0.jar` and is resolved through the
root `flatDir` repository.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Productive Trees version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

| Family | Result | Reason |
|---|---|---|
| Sawmill (`productivetrees:sawmill`, 173 recipes) | accepted | deterministic single-tag-ingredient input, one explicit primary ItemStack plus secondary/tertiary remainder ItemStacks with no chance rolls; work = recipeTime(200)/tickRate(10) = 20 ticks at one work/tick; the sawmill block is the station; time upgrades are not inferred |
| Log stripping | rejected | interactive Stripper block process gated on a loaded axe with live durability; no `log_stripping` data recipes ship in the jar |
| Tree fruiting | rejected | attaches fruit to growing world trees; world mutation |
| Tree pollination | rejected | modifies world leaves/drops from live bee state; world mutation |

## Station

`auto_storage:productivetrees_sawmill` is a PROCESS station installable with
the `productivetrees:sawmill` block item (built-in rendering, one work/tick).
Sawmill crafts consume one log from storage, deliver the planks as the selected
primary, and route sawdust/tertiary as exact remainders in the same transaction.
The isolated fixture also exercises a non-empty tertiary output so that path is
covered by a real commit, not only by the family contract.

## CI policy

`runProductivetreesGameTestServer` runs eight isolated tests covering the
descriptor inventory digest, conditional registration, sawmill support,
full triple-output craft execution, shortage atomicity, station gating,
full-destination rollback, and fail-closed world recipes. The isolated
inventory digest covers the 2,684 live `productivetrees:*` recipes in the
fixture world (botany-pots/Mekanism/Productive Bees/TreeTap conditional
recipes excluded by `neoforge:mod_loaded`).
