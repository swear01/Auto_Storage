# Create Aquatic Ambitions Compatibility

Auto Storage's Compat Kit review of Create Aquatic Ambitions `2.0.4`
accepts **zero production recipe families**. This is an evidence-backed
fail-closed result (outcome **C**), not an absent-mod fallback and not an empty
recipe adapter.

The present-mod module entrypoint loads only when `create_aquatic_ambitions` is
installed and registers no stations or recipe families. Vanilla-class recipes
that CAA ships under its namespace remain covered by Auto Storage's built-in
exact crafting/smelting families. Create-class milling/crushing datapack recipes
remain owned by Create compatibility when they meet that module's deterministic
contract.

## Reproducible audit evidence

- target: Create Aquatic Ambitions `2.0.4` (`create_aquatic_ambitions`);
- Modrinth version ID `DoI3PpXj` /
  `maven.modrinth:create-aquatic-ambitions:DoI3PpXj`;
- download URL used for local SHA verification:
  `https://cdn.modrinth.com/data/9SyaPzp7/versions/DoI3PpXj/create_aquatic_ambitions-1.21.1-2.0.4.jar`;
- jar SHA-256:
  `d50180fd30dc7f034ea4ad5185d18cfa652457be1d8e7a45f0b491d0e6642d44`;
- ATM10 modlist entry:
  `create_aquatic_ambitions-1.21.1-2.0.4.jar`;
- official source branch `neoforge-1.21.1`
  commit `c584e179ae64ce2373597899402bdd0cab9a22e7` (`mod_version=2.0.4`);
- required Create runtime companion uses the existing Create CI fixture
  `maven.modrinth:create:${create_ci_version}` (`UjX6dr61`);
- audit: `compat/audits/create_aquatic_ambitions/2.0.4.json`;
- reviewed contract: `compat/contracts/create_aquatic_ambitions.json`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Create Aquatic Ambitions version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

Compat Kit enumerated 22 recipe-class candidates. Every candidate is rejected
in the committed contract. The runtime surfaces behind that inventory are:

| Family / surface | Result | Reason |
|---|---|---|
| `ChannelingRecipe` / `create_aquatic_ambitions:channeling` | rejected | Encased Fan processing gated by world catalysts: active Conduit neighborhood, awakened Mechanical Conduit, or channeling block/fluid tags; entity Conduit Power side effect; many recipes include chance outputs |
| Channeling / Create processing datagen | rejected | Datagen providers, not runtime families |
| KubeJS Channeling schema / ProcessingOutput component | rejected | Script/viewer helpers; chance-aware component |
| `CAARecipeTypes` registry | rejected | Type/serializer registration only; Channeling remains rejected |
| Synthetic cooking shim nested classes | rejected | Datagen-only shims, not registered runtime families |
| Resource API false positives (`ConduitPowerLevel`, fluid tags) | not accepted | Live conduit power / world fluid tags; no typed resource kind introduced |

Typed resources were not introduced. Channeling cannot be reduced to a
simulate-then-commit plan without approximating fan/world/kinetic catalysts
away, which is the same fail-closed class as Create Splashing/Haunting.

## Declarative matrix evidence

The module descriptor and reviewed contract both declare the target mod present
with zero descriptors, resource kinds, accepted recipes, rejected registry IDs,
or CAA-owned recipe families. The combined runtime contains 65 successfully
loaded `create_aquatic_ambitions:*` recipes, locked by SHA-256
`5084d1ab9696fd443d49d14fe855d936451b5f8895f5ae1760fb7b636650d189`.

CAA also ships `create:milling/limestone`. Recipe inventory is grouped by the
actual recipe namespace, so that entry remains in the existing Create-owned
group; with CAA loaded, that group's exact digest is
`0d71e759fc4741b6af4f47fc5a8a1d83403f7e3907d08ad747756096fc5db99d`.
No shared workflow or matrix Java list is extended for this module.

## Future acceptance boundary

Support can be reconsidered only after a generic contract can express retained
world catalysts (conduit/block/fluid state) and exact deterministic output
subsets without chance rolls or entity side effects. Until then, no CAA-only
approximation is allowed.

## Verification

```bash
./gradlew runCreateAquaticAmbitionsGameTestServer
```

Four present-mod GameTests prove the module registers no CAA stations/families,
representative deterministic-looking and chanced Channeling recipes stay
unsupported, and every loaded recipe in the Channeling type remains fail closed.
The all-mod compatibility matrix also loads the representative artifact and
locks the same zero-family boundary plus both namespace-owned recipe inventories.
