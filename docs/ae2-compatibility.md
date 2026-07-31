# Applied Energistics 2 Compatibility

Auto Storage's first Compat Kit dogfood module supports the exact deterministic
AE2 Inscriber slice. It loads only when `ae2` is present and compiles in the
isolated `compatAe2` source set against the public Auto Storage API plus AE2.

## Reproducible audit evidence

- target: Applied Energistics 2 `19.2.17`;
- official source tag: `neoforge/v19.2.17`;
- source commit: `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a`;
- Maven Central jar SHA-256:
  `460d779a0609b81409907d9956de8f6f70a1b0912257e3e5c3c7e75ac9630e95`;
- audit: `compat/audits/ae2/19.2.17.json`;
- reviewed contract: `compat/contracts/ae2.json`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact AE2 version on players and does not claim a multi-version matrix.
Changes outside this fixture are handled from concrete reports and a new delta.

The update-path dogfood compares that audit with official Maven Central
`19.2.16` (SHA-256
`a625ecf1a47e0674e05099652ee222c227de9315b9731f4052616ace22aeccb9`).
The compact delta reports no recipe/resource/station signature or risk changes
but `contract_affected=true` because the target jar bytes differ. This validates
the conservative delta workflow only; 19.2.16 is not a second CI fixture or a
compatibility promise.

Scanner format v8 adds structural Recipe/RecipeSerializer classification and a
bounded recipe-data inventory. The committed AE2 dogfood audit remains the
explicitly supported v7 legacy format until it is regenerated from the same
reviewed artifact/source evidence. Scanner format v7 audits named nested classes while excluding anonymous/local
classes by name and JVM `ACC_SYNTHETIC` class files by access flag. It also
recognizes the method-descriptor form that `javap` emits for chance,
randomness, generic-ingredient, and capability-mutation calls. The reviewed
contract explicitly rejects all 25 new nested recipe-name candidates as UI,
datagen, codec, result, condition, or value helpers;
`InscriberRecipe$Ingredients` remains owned by the accepted
parent `InscriberRecipe` contract rather than becoming another family. The
reviewed runtime list also carries GuideME through exact Modrinth artifact
`rduAfwb7`; both bundled and generated-addon fixtures therefore reproduce AE2's
required runtime instead of relying on a hand-edited descriptor.

## Accepted family

Exact class/type:

- `appeng.recipes.handlers.InscriberRecipe`;
- `appeng.recipes.AERecipeTypes.INSCRIBER`.

Logical station:

- descriptor `auto_storage:ae2_inscriber`;
- station item `ae2:inscriber`;
- Processing category, 2 work per tick, 200 work per craft;
- speed cards are internal machine state and are not inferred from a plain
  station item.

Transaction:

- middle ingredient: consumed once;
- non-empty top/bottom in `INSCRIBE`: retained catalysts;
- non-empty top/bottom in `PRESS`: consumed once;
- result: exact output item/components/count;
- energy: `200 × 10 AE`, multiplied by AE2's loaded
  `PowerMultiplier.CONFIG`, then converted with
  `PowerUnit.AE.convertTo(PowerUnit.FE, ...)`.

The family is unavailable when any ingredient is non-simple, the output is
empty, or the loaded conversion is non-finite, non-positive, out of `long`
range, or fractional. Auto Storage never rounds an AE cost.

## Explicit exclusions

- Charger: probabilistic completion timing plus manual-crank power has no exact
  synthetic transaction cost.
- Speed-card variants: not representable by an unconfigured Inscriber
  `ItemStack`.
- Every other AE2 recipe candidate: rejected until it has its own complete
  deterministic contract.
- AE2 networks, storage, spatial, world/entity, and live-machine operations:
  outside this recipe-family module.

## Verification

```bash
./gradlew runAe2GameTestServer
```

Eight real GameTests cover registration/Charger exclusion, retained INSCRIBE
presses, consumed PRESS inputs, exact FE/work, missing middle ingredient,
insufficient FE, insufficient work, destination overflow rollback, and runtime
conversion representation. The missing-ingredient case keeps both remaining
inputs, FE, station work, and output unchanged.
The base
`exact_recipe_selection_accepts_supported_backing_recipe_and_rejects_stale_id`
GameTest also executes the absent-target no-classload assertion, so its contract
marker is bound inside an actual annotated GameTest method's braces. Method
declarations are not accepted as evidence. The all-mod compatibility matrix
protects coexistence.
