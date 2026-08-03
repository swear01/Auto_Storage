# Immersive Engineering Compatibility

Auto Storage's Compat Kit review of Immersive Engineering `12.4.2-194`
accepts **zero production recipe families**. This is an evidence-backed
fail-closed result (outcome **C**), not an absent-mod fallback and not an empty
recipe adapter claiming support.

The present-mod module entrypoint loads only when `immersiveengineering` is
installed and registers no stations or recipe families. Vanilla-class recipes
that Immersive Engineering ships under its namespace (crafting, smelting,
smoking, stonecutting) remain covered by Auto Storage's built-in exact adapters
without a custom module.

## Reproducible audit evidence

- target: Immersive Engineering `12.4.2-194` (`immersiveengineering`);
- Modrinth version ID `uNRARSH2` / Maven
  `maven.modrinth:immersiveengineering:uNRARSH2`;
- download URL used for local SHA verification:
  `https://cdn.modrinth.com/data/tIm2nV03/versions/uNRARSH2/ImmersiveEngineering-1.21.1-12.4.2-194.jar`;
- jar SHA-256:
  `45942985a4a4aebf265b8e22a0c54a96208637471f36f2532ff5d4911322debc`;
- official source tag `12.4.2-194`, commit
  `583a182549d284a35c813b55ac2a0d1fddcf945a`;
- audit: `compat/audits/immersiveengineering/12.4.2-194.json`;
- reviewed contract: `compat/contracts/immersiveengineering.json`.

JarJar embeds BlockModelSplitter and DualCodecs inside the representative jar,
so the reviewed contract declares no additional runtime dependencies.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Immersive Engineering version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

Compat Kit enumerated 128 recipe-class candidates. Every candidate is rejected
in the committed contract. The runtime machine families behind that inventory
are:

| Family | Result | Reason |
|---|---|---|
| Alloy Smelter | rejected | stone multiblock; live vanilla furnace fuel burn; `TagOutput` may resolve via mod preference |
| Coke Oven | rejected | stone multiblock; creosote tank capacity/batching is live state |
| Blast Furnace | rejected | stone multiblock; separate `BlastFurnaceFuel` burn state; tag preference outputs |
| Crusher / Arc Furnace | rejected | multiblock; live `RecipeMultiplier` energy/time; chance secondaries |
| Metal Press | rejected | multiblock; retained mold; live multipliers |
| Bottling / Fermenter / Squeezer / Mixer / Refinery / Sawmill | rejected | multiblock with live multipliers and/or fluid inventory state |
| Blueprint crafting | rejected | typed Engineers Blueprint plus workbench/auto-workbench multiblock path |
| Garden Cloche | rejected | `StackWithChance` plus `ApiUtils.RANDOM` outputs |
| Special crafting / fluid-aware / jerrycan / serializers / JEI helpers | rejected | not independent deterministic Auto Storage machine families |

Typed resources were not introduced. Multiblock composition, live fuel/tank
state, config-backed `DoubleSupplier` multipliers, chance outputs, and
`TagOutput` preference resolution cannot be reduced to a simulate-then-commit
plan without approximating those conditions away.

## Future acceptance boundary

Support can be reconsidered only after a generic contract can express retained
multiblock composition, exact fuel/tank/catalyst state without live BE mutation,
deterministic non-chance outputs (no `TagOutput` preference guessing), and
energy/time that do not depend on mutable `RecipeMultiplier` suppliers.

## Verification

```bash
./gradlew runImmersiveengineeringGameTestServer
```

Eight present-mod GameTests prove the module registers no Immersive Engineering
stations/families, representative Alloy/Coke Oven/Blast Furnace/Crusher/Cloche/
Metal Press/Arc Furnace recipes stay unsupported, and every loaded recipe in
each audited custom recipe type remains fail closed. The isolated fixture also
verifies the owning descriptor's `immersiveengineering` namespace
`recipeInventory` digest
`b733a4b670dbb507a71df7f819c2296f627d42f2ed89240c6040c9c55c445c7d`
(1100 loaded recipe ids) via `IsolatedRecipeInventoryEvidence`. The all-mod
compatibility matrix loads the representative artifact through the module
descriptor and asserts the zero-family boundary via empty
`descriptors`/`acceptedRecipes`; combined coexistence and unclaimed inventories
are recorded only in the matrix report.

With Immersive Engineering present in the current full matrix, local
`build/reports/terminal-scale-10000.json` records
`shared_index_retained_bytes=9477112` (≈9.038 MiB) against the fixed
`MAX_BASELINE_INDEX_RETAINED_BYTES` of `9L * 1024L * 1024L` (=9437184), so the
shared retained-index gate fails by 39928 bytes. The same report records
`craftable_prepare_ms=86.36` against the fixed 50 ms first-Craftable budget,
`recipes=16272`, and `craftable_outputs=86`. Do not raise the 9 MiB gate.

```bash
tools/compat-kit/compat-kit verify \
  compat/contracts/immersiveengineering.json \
  --audit compat/audits/immersiveengineering/12.4.2-194.json \
  --jar build/compat-kit/artifacts/ImmersiveEngineering-1.21.1-12.4.2-194.jar \
  --bundled . \
  --output build/compat-kit/immersiveengineering-report.json
```

Passing report path: `build/compat-kit/immersiveengineering-report.json`.

The committed audit/contract currently remain scanner format 7. Compat Kit v2
complete verify requires a current scanner-format (16) audit; format-16
`scan`/`migrate-audit` is blocked by a verified scanner defect on IE client
class `blusunrize.immersiveengineering.client.models.ModelConveyor$1$Key`:

- `ModelConveyor$1` is anonymous (`EnclosingMethod` present, `inner_name=null`)
  and correctly excluded from inspectable metadata.
- `ModelConveyor$1$Key` is a named nested record (`inner_name=Key`,
  `outer_class=ModelConveyor$1`) and remains inspectable.
- `_candidate_source_class` then raises
  `named nested class owner is unresolved: ...ModelConveyor$1$Key`
  because the anonymous outer was omitted from `metadata_by_class`.

Do not bypass complete-verify's current-format requirement and do not patch the
scanner inside this module PR; keep the format-7 audit/contract as blocked
evidence until a scanner fix lands separately.

