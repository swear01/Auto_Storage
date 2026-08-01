# Actually Additions Compatibility

Auto Storage's Compat Kit module supports a deterministic Actually Additions slice.
It loads only when `actuallyadditions` is present and compiles in the isolated
`compatActuallyadditions` source set against the public Auto Storage API plus
Actually Additions.

## Reproducible audit evidence

- target: Actually Additions `1.3.26`;
- Modrinth version ID: `iNeJmgFj`;
- Maven coordinate: `maven.modrinth:actually-additions:iNeJmgFj`
  (Modrinth slug uses a hyphen; `actuallyadditions` 404s);
- jar SHA-256:
  `072451bb6069025e255a39216edc0f892cda00c12b9905b552e7dd4631d44a41`;
- official source tag: `v1.3.26`;
- source commit: `cd9d9c63254f471f966ee51b078afdf47af5f852`;
- audit: `compat/audits/actuallyadditions/1.3.26.json`;
- reviewed contract: `compat/contracts/actuallyadditions.json`;
- inventory digest:
  `98ce538f61594a755feb42a6fa98bd885c4c574d82c1936221bf8198deccf24a`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Actually Additions version on players and does not claim a multi-version
matrix. Changes outside this fixture are handled from concrete reports and a new
delta.

## Accepted families

### Crushing (`actuallyadditions:crushing`)

- class: `CrushingRecipe`;
- descriptor: `auto_storage:actuallyadditions_crushing`;
- station item: `actuallyadditions:crusher` only;
- rate: `TileEntityCrusher.ENERGY_USE` (40) work per tick;
- FE and station work: `ENERGY_USE × 100` = `4000`;
- consume one simple exact item input;
- primary: non-empty `getOutputOne()`;
- secondary: only when empty, or when exact and `getSecondChance() == 1.0F`
  (fractional chance recipes such as iron ore stay unavailable);
- `crusher_double` (150 ticks / 6000 FE) is not inferred from a plain station
  item.

### Pressing (`actuallyadditions:pressing`)

- class: `PressingRecipe`;
- descriptor: `auto_storage:actuallyadditions_pressing`;
- station item: `actuallyadditions:canola_press`;
- rate: `TileEntityCanolaPress.ENERGY_USE` (35) work per tick;
- FE and station work: `ENERGY_USE × TIME` = `1050`;
- consume one simple exact item; emit exact sized fluid.

### Fermenting (`actuallyadditions:fermenting`)

- class: `FermentingRecipe`;
- descriptor: `auto_storage:actuallyadditions_fermenting`;
- station item: `actuallyadditions:fermenting_barrel`;
- rate: 1 work per tick;
- station work: `recipe.getTime()` (no FE);
- consume exact sized fluid; emit exact sized fluid.

## Explicit exclusions

Fifty-one audited candidates remain rejected, including Empowerer multiblock
stands, Laser / Color Change / Mining Lens world beams, Liquid Fuel generator
recipes, Coffee effects, KeepData shaped/shapeless, datagen/JEI/helpers,
deprecated stubs, and nested serializer/result helpers. Vanilla-class recipes
under the Actually Additions namespace still flow through existing exact vanilla
adapters when present.

Actually Additions owns its EMI/JEI category and workstation metadata. Auto
Storage does not register Actually Additions workstations into EMI.

## Verification

```bash
./gradlew runActuallyadditionsGameTestServer
```

Ten real GameTests cover registration and Empowerer/Laser exclusion, blaze-rod
crushing with exact powder output, pressing canola oil, fermenting refined
canola, fractional-chance crushing rejection, missing-ingredient / insufficient
FE / insufficient work atomic no-ops, destination-capacity and long-overflow
rollback. Compat Kit verify writes
`build/compat-kit/actuallyadditions-report.json`. The all-mod compatibility
matrix protects coexistence and locks the 12,178-recipe baseline.
