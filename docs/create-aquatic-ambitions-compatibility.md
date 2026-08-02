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
- scanner format `16`: 79 target classes, 160 target/reachable ancestry graph
  records, and 29 classified candidates across all buckets;
- six reachable ancestry artifacts: the normalized binary-pipeline
  NeoForge/Minecraft artifact plus five exact Maven coordinates:
  `com.simibubi.create:create-1.21.1:6.0.10-281:slim`,
  `dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:1.0.6`,
  `dev.latvian.mods:kubejs-neoforge:2101.7.2-build.371`,
  `mezz.jei:jei-1.21.1-common-api:19.38.0.366`, and
  `net.createmod.ponder:ponder-neoforge:1.0.87+mc1.21.1`;
- normalized NeoForge/Minecraft SHA-256/size:
  `2382ea29e50ff9deb46fa393d1e49c3a54b5d6273c252d0208d3fed903e8eb5f`
  / `56,279,815` bytes, matching the cross-host binary pattern used by the
  current AE2 audit rather than a host-recompiled jar;
- exact target-jar recipe-data inventory: 96 declared/effective recipes,
  SHA-256 `ba4f866612b23b5d5bd4c34558ac65bf32007bd18ca404508e9d5bb67956e12c`;
- required Create runtime companion uses the existing Create CI fixture
  `maven.modrinth:create:${create_ci_version}` (`UjX6dr61`);
- audit: `compat/audits/create_aquatic_ambitions/2.0.4.json`;
- reviewed contract: `compat/contracts/create_aquatic_ambitions.json`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Create Aquatic Ambitions version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

The legacy scanner-format-7 audit classified 22 name-shaped entries as recipe
families. Exact format-16 structural scanning removed 20 non-recipe entries and
left exactly two actual `Recipe` candidates. Both were reopened by
`migrate-contract`, reviewed again, and rejected in the committed contract:

| Family / surface | Result | Reason |
|---|---|---|
| `ChannelingRecipe` / `create_aquatic_ambitions:channeling` | rejected | Encased Fan processing gated by world catalysts: active Conduit neighborhood, awakened Mechanical Conduit, or channeling block/fluid tags; entity Conduit Power side effect; many recipes include chance outputs |
| `CAAStandardRecipeGen.ModdedCookingRecipeOutputShim` | rejected | Private datagen-only wrapper; runtime recipe methods throw `AssertionError`, while its serializer temporarily injects registry identity only to encode another mod's cooking output |
| Resource API false positives (`ConduitPowerLevel`, fluid tags) | not accepted | Live conduit power / world fluid tags; no typed resource kind introduced |

Typed resources were not introduced. Channeling cannot be reduced to a
simulate-then-commit plan without approximating fan/world/kinetic catalysts
away, which is the same fail-closed class as Create Splashing/Haunting. Datagen,
builder, viewer, registry, serializer, and resource-name matches remain audit
buckets rather than contract recipe families.

## Declarative matrix evidence

The module descriptor and reviewed contract both declare the target mod present
with zero descriptors, resource kinds, accepted recipes, rejected registry IDs,
or CAA-owned recipe families. The isolated CAA fixture locks the 65 successfully
loaded `create_aquatic_ambitions:*` recipes by SHA-256
`5084d1ab9696fd443d49d14fe855d936451b5f8895f5ae1760fb7b636650d189`.

CAA also ships `create:milling/limestone`. That cross-namespace coexistence is
recorded only in the compatibility-matrix report
(`build/reports/terminal-scale-*.json`); this module does not name or pin the
Create descriptor digest. No shared workflow or matrix Java list is extended
for this module.

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
structurally validates the zero-family boundary plus namespace claims; actual
coexistence digests are recorded in the matrix report without committed
expected SHA values.
