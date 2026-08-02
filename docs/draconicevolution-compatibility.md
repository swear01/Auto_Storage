# Draconic Evolution Compatibility

Auto Storage's Compat Kit review of Draconic Evolution `3.1.4.632`
accepts **zero production recipe families**. This is an evidence-backed
fail-closed result (outcome **C**), not an absent-mod fallback and not an empty
recipe adapter claiming Fusion Crafting support.

The present-mod module entrypoint loads only when `draconicevolution` is
installed and registers no stations or recipe families. Vanilla-class crafting
and smelting recipes that Draconic Evolution ships under its namespace remain
covered by Auto Storage's built-in exact families without a custom module.

## Reproducible audit evidence

- target: Draconic Evolution `3.1.4.632` (`draconicevolution`);
- Modrinth version `lBMsiWyw` /
  `maven.modrinth:draconic-evolution:lBMsiWyw`;
- download URL used for local SHA verification:
  `https://cdn.modrinth.com/data/nBqivi8H/versions/lBMsiWyw/Draconic-Evolution-1.21.1-3.1.4.632.jar`;
- jar SHA-256:
  `623d7d58e58428a206015b56bf67387c79ff6d97f7221cff23b1dad0bed9544e`;
- required runtime companions:
  - Brandon's Core `3.2.1.309` (`maven.modrinth:brandons-core:56nwe5IX`,
    SHA-256 `076b44de51c606e6cdd89694ac0a879fc028d4f0103880ef44f47dbcc69f1d23`);
  - CodeChicken Lib `4.6.1.526` (`maven.modrinth:codechicken-lib:gQ1srSKh`,
    SHA-256 `e35cb0ca3e3aeb87e1e171242454426d22edf517b06dc38e5c3d5b6d44d6bc04`);
- official source branch `1.21` commit
  `d297c2657cefa6ac2af19a96ee12a51ab07d36f5` (nearest clean revision at or
  before the 2026-02-06 representative jar publish; CI build numbers are not
  tagged);
- ATM10 representative jar names match
  `Draconic-Evolution-1.21.1-3.1.4.632.jar`,
  `BrandonsCore-1.21.1-3.2.1.309.jar`, and
  `CodeChickenLib-1.21.1-4.6.1.526.jar`;
- audit: `compat/audits/draconicevolution/3.1.4.632.json`;
- reviewed contract: `compat/contracts/draconicevolution.json`.

The committed audit uses scanner format 16. It binds 619 target classes and
226 matching-source files, records all 179 effective recipe JSONs across four
serializers, and keeps only the eight exact artifacts reachable from the
structural class graph. Five of those artifacts have exact Maven coordinates;
the normalized NeoForge/Minecraft binary is the same cross-host artifact used
by the canonical AE2 audit rather than a host-local recompilation.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Draconic Evolution version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

The format-16 structural scan identifies one actual runtime recipe class. It is
rejected in the committed contract:

| Candidate | Result | Reason |
|---|---|---|
| `FusionRecipe` | rejected | live Fusion Crafting Core multiblock, tiered injectors, injector-local energy charging, `DEConfig` craft/charge times, generic `Ingredient`/`StackIngredient` payloads, optional `IFusionDataTransfer` assemble |

The legacy format-7 audit had treated 12 additional name-shaped classes as
recipe candidates. Format 16 classifies them in their actual structural
buckets instead: nested values and APIs, serializer/codec surfaces, datagen
builders/providers, client JEI surfaces, a player menu helper, and constants.
They are not recipe families and therefore do not appear in the reviewed
contract.

Representative datapack inventory in the audited jar: 39
`draconicevolution:fusion_crafting` recipes plus 140 vanilla
shaped/shapeless/smelting recipes under the `draconicevolution` namespace.
The matrix locks those 179 live IDs with SHA-256
`3044e680f5bb1cc4ad408af81aa53d54ba3e2a82dc656ec4da67a57dca58f5c6`.
The fifteen-mod compatibility matrix loads `12,916` total recipes after loading
the representative DE jar with Brandon's Core and CodeChicken Lib (+180 versus
the prior fourteen-mod `12,736` baseline). The additional recipe is Industrial
Foregoing's conditional
`industrialforegoing:laser_drill_ore/ores/draconium`, enabled when Draconic
Evolution makes `c:ores/draconium` nonempty; the Industrial Foregoing namespace
digest therefore also reflects the combined representative runtime.

Typed resources were not introduced. Fusion energy stays on live injectors and
is not reinterpreted as NeoForge Energy, Fuel, or station work.

## Future acceptance boundary

Support can be reconsidered only after a generic contract can express retained
multiblock injector composition and tier, exact simulate-then-commit energy
charging without config-timed craft duration, and catalyst/`StackIngredient`
counts without approximating live fusion state away.

## Verification

```bash
./gradlew runDraconicevolutionGameTestServer
```

Six present-mod GameTests prove the module registers no Draconic Evolution
stations/families, representative vanilla crafting under the namespace stays
supported, representative fusion recipes stay rejected, and every loaded
`fusion_crafting` recipe remains fail closed. The all-mod compatibility matrix
also loads the representative artifact plus Brandon's Core and CodeChicken Lib
and locks the same zero-family boundary.
