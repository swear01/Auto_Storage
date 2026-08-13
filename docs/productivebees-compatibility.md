# Productive Bees Compatibility

Auto Storage's Compat Kit review of Productive Bees `1.21.1-13.13.5`
accepts **zero production recipe families**. This is an evidence-backed
fail-closed result (outcome **C**), not an absent-mod fallback and not an empty
recipe adapter claiming machine support.

The present-mod module entrypoint loads only when `productivebees` is installed
and registers no stations, resource kinds, or recipe families. Exact vanilla
shaped, shapeless, smelting, and related recipe classes that Productive Bees
ships under its namespace remain covered by Auto Storage's built-in families.

## Reproducible audit evidence

- target: Productive Bees `1.21.1-13.13.5` (`productivebees`);
- CurseForge file `8022994` / exact Curse Maven coordinate
  `curse.maven:productivebees-377897:8022994`;
- reviewed repository: `https://www.cursemaven.com/`;
- download URL used for local SHA verification:
  `https://mediafilez.forgecdn.net/files/8022/994/productivebees-1.21.1-13.13.5.jar`;
- jar size: `5,216,551` bytes;
- jar SHA-256:
  `9d48d198bc6eacf3b7729f4d60b91e661cfa15d105264ba225dee87b1d547ba1`;
- complete 369-class inventory SHA-256:
  `bb1b2275248441aba2f367acf8983fa9205a2d763516925f9509ae47ecf5c7a4`;
- official source: https://github.com/JDKDigital/productive-bees at clean commit
  `3c818315d67abc16801626ce292bb207a7383f06` with 125 matched source files;
- audit: `compat/audits/productivebees/13.13.5.json`;
- reviewed contract: `compat/contracts/productivebees.json`.

The committed audit uses scanner format 17. It binds all 1,702 declared and
effective recipe JSON files across 74 serializers with data digest
`22b4ffb8f346abbddb694c431a664eae055670cb64467ec2b86a8ea31bbc87db`,
the complete target class inventory, official source, structural hierarchy,
public signatures, private-bytecode risks, and seven exact reachable ancestry
artifacts. Five independently resolvable ancestry APIs carry exact coordinates:

- `mezz.jei:jei-1.21.1-common-api:19.25.0.322`;
- `dev.emi:emi-neoforge:1.1.22+1.21.1:api`;
- `curse.maven:curios-309927:6529130`;
- `maven.modrinth:geckolib:qj2pTqCr`;
- `curse.maven:jade-324717:5444008`.

The other two reachable artifacts are the canonical normalized NeoForge /
Minecraft binary (`2382ea29…b5f`, `56,279,815` bytes) and jarJar-embedded
ProductiveLib `1.21.1-0.2.0` (`6671c8aa…0e7`, `154,249` bytes). ProductiveLib's
jarJar metadata identifies `cy.jdkdigital.productivelib:productivelib:1.21.1-0.2.0`,
but the reviewed upstream build embeds a flat-directory jar rather than
publishing a standalone dependency that the fixture can resolve. It therefore
remains exact reachable ancestry by bytes, not a fabricated external Maven
mapping. Complete validation reopens the target and all seven ancestry jars.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Productive Bees version on players and does not claim a multi-version
matrix.

## Audited recipe candidates

The legacy format-7 scan classified 71 name-shaped candidates. Current
format-17 structure identifies exactly 16 actual `Recipe` classes; all 16 were
manually re-reviewed against their official source and runtime use sites, then
rejected in the committed contract:

| Actual candidate(s) | Result | Source-backed boundary |
|---|---|---|
| `AdvancedBeehiveRecipe` | rejected | `BeeIngredient` identity, `ChancedOutput` random rolls, and live hive flower/upgrade modifiers |
| `BeeBombBeeCageRecipe` | rejected | special crafting embeds live cage bee components/NBT into a bomb; not an installed-station transaction |
| `BeeBreedingRecipe`, `BeeConversionRecipe`, `BeeFishingRecipe`, `BeeNBTChangerRecipe`, `BeeSpawningRecipe` | rejected | live bee/entity, biome, item, nest, or world state plus random selection/chance; NBT changer has no complete current matching path |
| `BlockConversionRecipe`, `ItemConversionRecipe` | rejected | chance-gated bee pollination mutates world blocks, feeder/hive items, or loose items |
| `BottlerRecipe` | rejected | fluid fill is entangled with piston bee-kill gene bottles, capability-fill fallback, block-state mutation, and no recipe-declared work/energy |
| `CentrifugeRecipe` | rejected | `ChancedOutput` rolls plus heated/powered upgrade, productivity, stability, time, and energy semantics have no guaranteed deterministic subset |
| `CombineGeneRecipe`, `ConfigurableCombBlockRecipe`, `ConfigurableHoneycombRecipe`, `HoneyTreatGeneRecipe` | rejected | special crafting dynamically reads/writes gene, purity, and `bee_type` components rather than an installed deterministic station contract |
| `IncubationRecipe` | rejected | runtime uses hard-coded egg/cage/gene paths with purity rolls, energy, upgrades, and live `BeeCage` entity capture rather than a complete recipe-owned transaction |

The other 55 legacy families were false positives: helper/interfaces, nested
serializers and factories, a plain flowering record, a timed interface,
EMI/JEI wrappers and categories, datagen builders/configs/providers, and recipe
registry bootstrap. They remain visible in the audit's correct structural
buckets but no longer masquerade as reviewed recipe families.

Typed resources were not introduced. Chance outputs and entity/world/live-machine
paths cannot be reduced to a public-SDK simulate-then-commit plan without
approximating source semantics away.

## Matrix isolation

The Productive Bees descriptor owns only its isolated `productivebees`
namespace recipe inventory for the module fixture. Cross-module coexistence and
unclaimed recipe inventories are computed by the compatibility-matrix GameTest
and recorded in `build/reports/terminal-scale-*.json`; they are not committed
baselines and do not rewrite peer descriptors when this module is added.

## Future acceptance boundary

Support can be reconsidered only after a public contract can express guaranteed
deterministic outputs without `RandomSource`, isolate bottler fluid/item fills
from entity-kill and capability-fill side paths, and model hive/incubator/
breeding state without live entity or world mutation.

## Verification

```bash
./gradlew runProductivebeesGameTestServer
./gradlew runCompatibilityMatrixGameTestServer
```

Eight isolated present-mod GameTests prove the module registers no Productive
Bees stations/families, representative centrifuge/bottler/beehive/breeding/
conversion/block-conversion recipes stay unsupported, and every loaded recipe
in each audited custom recipe type remains fail closed. The descriptor-owned
combined matrix loads the representative artifact and verifies the same
zero-family boundary through
`manifest.assertCoexistence(helper, "Descriptor matrix coexistence")`.

## Honey Generator (Transform)

`auto_storage:productivebees_honey_generator` is a PROCESS descriptor (one
work/tick) plus a time-based Transform use: honey bottles (250 mb → 125
work, retained glass bottle), honey blocks (1000 mb → 500 work), and honey
buckets (1000 mb → 500 work, retained bucket) convert to FE at
`generatorPowerGen` (default 60) FE/tick while consuming
`generatorHoneyUse` (default 2) mb/tick. Verified against Productive Bees
13.13.5 bytecode and config. Honey bottle: 125 work → 7,500 FE.
