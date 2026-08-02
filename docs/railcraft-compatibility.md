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
  `maven.modrinth:railcraft-reborn:BrIwB6GH`;
- download URL used for local SHA verification:
  `https://cdn.modrinth.com/data/rO6kKst6/versions/BrIwB6GH/railcraft-reborn-1.21.1-1.2.10.jar`;
- exact target size / SHA-256: `5,290,986` bytes /
  `7de3dfeac277da57f9897822824332c99e53b9d36956143b38c0966f39144328`;
- official source: https://github.com/railcraft-reborn/railcraft tag `1.2.10`
  / clean commit `7b89837df369bb0552d81016c46840792bd13d23`;
- scanner format `16`: 1,013 target classes, 1,205 target/reachable
  ancestry graph records, and 85 hierarchy records;
- six reachable ancestry artifacts: the normalized NeoForge/Minecraft binary
  plus five exact source compile coordinates:
  `com.google.guava:guava:28.2-jre`,
  `curse.maven:jade-api-324717:6853386`,
  `dev.emi:emi-neoforge:1.1.22+1.21.1:api`,
  `mezz.jei:jei-1.21.1-common-api:19.25.0.322`, and
  `net.neoforged:bus:8.0.5`;
- normalized NeoForge/Minecraft size / SHA-256: `56,279,815` bytes /
  `2382ea29e50ff9deb46fa393d1e49c3a54b5d6273c252d0208d3fed903e8eb5f`;
- exact target-jar recipe-data inventory: 701 declared/effective recipes,
  SHA-256 `a982f1cb9a9cb03ea3c35b302fa0075d009e71c15073733246f0333da432390d`;
- audit: `compat/audits/railcraft/1.2.10.json`;
- reviewed contract: `compat/contracts/railcraft.json`.

Complete validation reopens the exact target jar and all six ancestry jars.
The representative version records reproducible evidence; it does not claim a
multi-version compatibility matrix.

## Audited recipe candidates

The legacy scanner-format-7 audit classified 52 name-shaped entries as recipe
families. Exact format-16 structural scanning removed 39 builders, providers,
serializers, viewer wrappers, and base helpers. `migrate-contract` reopened the
13 actual `Recipe` implementations, and each was manually reviewed again
against the exact official source before retaining its rejection:

| Actual candidate | Result | Source-backed reason |
|---|---|---|
| `CrusherRecipe` | rejected | `CrusherModule.pollOutputs` rolls each probability with `RandomSource` and the real machine also consumes world Charge state |
| `BlastFurnaceRecipe` | rejected | formed multiblock execution depends on live burn time, item fuel semantics, fuel remainders, and slag |
| `CokeOvenRecipe` | rejected | formed multiblock execution depends on a live creosote tank, fluid-container processing, and machine multiplier state |
| `RollingRecipe` | rejected | manual execution owns a persistent craft matrix, progress, slot balancing, and previous-recipe state; powered execution reads world Charge distribution |
| `StoneTieRecipe`, `WoodenTieRecipe` | rejected | special crafting probes and executes item fluid-capability mutation for an exact 1,000 mB drain; the public item contract cannot model that mutation |
| `RotorRepairRecipe` | rejected | output depends on the live rotor damage and blade count |
| `TicketDuplicateRecipe` | rejected | special crafting copies the live ticket data component |
| `LocomotivePaintingRecipe` | rejected | special crafting copies all live input components before mutating locomotive colors |
| `ChestMinecartDisassemblyRecipe`, `VoidChestMinecartDisassemblyRecipe`, `WorldSpikeMinecartDisassemblyRecipe` | rejected | special crafting returns minecart remainders and has no truthful independent machine station |
| `PatchouliBookCrafting` | rejected | special crafting delegates output construction to the loaded Patchouli API and has no independent station |

No exact source-backed deterministic semantics satisfy the public SDK, so no
station, typed resource kind, or recipe family is registered. Auto Storage also
does not register Railcraft workstations into EMI; Railcraft owns its viewer
categories.

## Declarative matrix and coexistence evidence

The Railcraft descriptor and reviewed contract own the same matrix declaration:
the `railcraft` mod is present; descriptors, resource kinds, and accepted
recipes remain empty; `rejectedDescriptors` locks the four never-registered
IDs `auto_storage:railcraft_crusher`, `auto_storage:railcraft_blast_furnace`,
`auto_storage:railcraft_coke_oven`, and `auto_storage:railcraft_rolling`. The
isolated fixture inventory of 639 `railcraft:*` recipes is locked by SHA-256
`5fa9e922337c24f2a2d4d86da0d68ebe8205a89dcd70ece1e952f75707107060`.
Generic coexistence evidence uses
`manifest.assertCoexistence(helper, "Descriptor matrix coexistence")`; no
Railcraft-specific shared Java list was added.

Cross-namespace coexistence effects of loading Railcraft with peer modules are
recorded only in the compatibility-matrix report
(`build/reports/terminal-scale-*.json`). This module does not pin peer Create
digests, rewrite peer descriptors, or commit global coexistence/unclaimed
expected SHA values.

## Craftable catalog performance

The current-main `CraftableRecipeCatalog` weak shared index, recipe-snapshot
invalidation, ingredient index, and candidate bitsets remain necessary and
current-main-safe. Typed plan/contract caches and catalog entry lazy match
graphs are only transient through first shared Craftable listing, then released
by production `releaseTransientMatches()`, so steady-state
`shared_index_retained_bytes` stays the compact catalog plus shared listing.
The Railcraft branch does not duplicate or replace those production changes.
With Productive Bees and every earlier representative module loaded, the current
combined inventory contains 14,408 recipes. A contended local run still measured
3.809 ms first prefetch, 0.547 ms switch p95, a 9,155,000-byte shared index, and
114,906 bytes per menu, but its 111.297 ms prepare time correctly failed the 50 ms
gate while the host load exceeded 90. Clean current-head CI, not the contended run
or a pre-Productive number, is the authoritative performance gate.

## Verification

```bash
./gradlew runRailcraftGameTestServer
./gradlew runCompatibilityMatrixGameTestServer
```

Eight present-mod GameTests cover registry absence, chance Crusher, Blast
Furnace, Coke Oven, Rolling, fluid-tie/rotor-repair special crafting, ticket /
minecart-disassembly / Patchouli-book special crafting, and an exhaustive scan
that every loaded recipe in each audited machine type remains fail closed. The
three-test all-mod matrix verifies descriptor-owned registrations, namespace-claim
structure, records actual coexistence/unclaimed digests in the matrix report,
and runs the shared Craftable performance/heap gates.

`build/compat-kit/railcraft-current-report.json` is historical local evidence
from an earlier branch head. It is not current-head verification and must not be
used in place of the PR's exact-head GitHub CI, including the Railcraft fixture
and combined matrix gates above.

## Future acceptance boundary

Support can be reconsidered only when exact source-backed semantics fit the
public simulate-then-commit SDK without relying on chance, multiblock/world
state, live fuel or fluid capability mutation, mutable component-copy output,
or special-crafting helpers. Until then, outcome C remains fail closed.
