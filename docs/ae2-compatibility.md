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

The committed audit uses Scanner format v15 and binds all 92 unique jars needed
to resolve AE2's complete non-JDK compile ancestry, including its optional
viewer APIs. Every candidate retains its binary identity, source-level Java
type, and structural classification independently from the matching top-level
hierarchy inventory. The audit additionally preserves a compact direct
class-file graph for all 1,689 target classes and their reachable ancestry; the
artifact binds the exact target-class count and graph digest, and validation
reconstructs the twelve candidate classifications from that complete graph
rather than trusting the candidate list or two derived hierarchy copies. An artifact/classpath-bound
structural-candidate digest still binds the structural class inventory. Scanner
v15's executable JDK-21 verification and inherited
implementation-risk pass produce byte-identical evidence for this exact
artifact/classpath set. Class-file hierarchy inspection finds exactly twelve concrete
`Recipe` implementations:

Complete generation and verification also reopen the exact AE2 19.2.17 jar,
check its committed SHA/size, and rebuild all 1,689 sorted target class metadata
records. The JSON count/digest alone cannot authorize a reduced graph.

- `appeng.recipes.entropy.EntropyRecipe`;
- `appeng.recipes.game.AddItemUpgradeRecipe`;
- `appeng.recipes.game.CraftingUnitTransformRecipe`;
- `appeng.recipes.game.FacadeRecipe`;
- `appeng.recipes.game.RemoveItemUpgradeRecipe`;
- `appeng.recipes.game.StorageCellDisassemblyRecipe`;
- `appeng.recipes.game.StorageCellUpgradeRecipe`;
- `appeng.recipes.handlers.ChargerRecipe`;
- `appeng.recipes.handlers.InscriberRecipe`;
- `appeng.recipes.mattercannon.MatterCannonAmmo`;
- `appeng.recipes.quartzcutting.QuartzCuttingRecipe`;
- `appeng.recipes.transform.TransformRecipe`.

Builders, datagen classes, client/viewer wrappers, serializers, station
candidates, block entities, and resource APIs remain separate evidence buckets;
they no longer become recipe families merely because their names contain
`Recipe`. The bounded data inventory records 556 declared/effective recipes in
18 serializer groups. The migrated contract therefore contains twelve exact
decisions rather than the old name-based candidate surface: Inscriber is
accepted and the other eleven classes are rejected.

The reviewed runtime list carries GuideME through exact Modrinth artifact
`rduAfwb7`; bundled and generated-addon fixtures reproduce AE2's required
runtime instead of relying on a hand-edited descriptor.

`compat/generation/ae2.json` is bound to the canonical contract digest. It
generates `Ae2GeneratedCompat.java`, which owns direct typed descriptor and
recipe-family registration. `Ae2Compat` retains only the reviewed semantic
providers for eligibility, typed transaction planning, and exact cost. A
Python golden test regenerates the class byte-for-byte, and the isolated AE2
source set compiles the committed result. Neither side uses reflection.

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
- Entropy, the six custom crafting families, Quartz Cutting, Matter Cannon
  Ammo, and Transform: rejected until each has its own complete deterministic
  contract.
- AE2 networks, storage, spatial, world/entity, and live-machine operations:
  outside this recipe-family module.

## Verification

```bash
./gradlew runAe2GameTestServer
```

Eight real GameTests cover generated registration/Charger exclusion, retained INSCRIBE
presses, consumed PRESS inputs, exact FE/work, missing middle ingredient,
insufficient FE, insufficient work, destination overflow rollback, and runtime
conversion representation. The missing-ingredient case keeps both remaining
inputs, FE, station work, and output unchanged.
The base
`exact_recipe_selection_accepts_supported_backing_recipe_and_rejects_stale_id`
GameTest also executes the absent-target no-classload assertion, so its contract
marker is bound inside an actual annotated GameTest method's braces. Method
declarations are not accepted as evidence. Compat Kit also resolves
`AutoStorage.MODID` through its public constant chain and verifies that the
holder equals the `auto_storage` namespace enabled by `runGameTestServer`.
The all-mod compatibility matrix
protects coexistence.
