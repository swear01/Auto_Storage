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
- compile ancestry: Auto Storage normalized NeoForge `21.1.229` merged jar plus reachable Curios/Patchouli/PatchouliProvider/JEI API jars from the representative compile classpath (IE optional jars excluded to avoid duplicate class ownership);
- source commit: `cd9d9c63254f471f966ee51b078afdf47af5f852`;
- audit: `compat/audits/actuallyadditions/1.3.26.json`;
- reviewed contract: `compat/contracts/actuallyadditions.json`;
- class inventory digest:
  `cdf0b8250c2b1e92f8d0d09367ba7f525ea820e5cfc9ba2e2e69b57c09096d03`;
- recipe-data digest:
  `1baa31128d8a7e6d5469b7266838e37de876e8e09aa7aaf4436594bef44680ea`;
- scanner format: `17`;
- isolated recipe-inventory digest (descriptor-owned; verified by isolated fixture):
  `ee098fe82eb718eaad44abd60312e2a7e34be6cadca12900aeab6719edefddc1`
  (509 runtime recipes in the isolated fixture; jar JSON count is 540 before
  conditions; coexistence counts belong only to the matrix report);
- coexistence/unclaimed digests belong only to the compatibility-matrix
  build report, not peer descriptors or committed global baselines.

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
- primary: non-empty `getOutputOne()` with `getFirstChance() == 1.0F`;
- secondary: only when empty, or when exact and `getSecondChance() == 1.0F`
  (fractional primary or secondary chance recipes stay unavailable);
- guaranteed secondary outputs join the same atomic transaction as the primary;
  insufficient capacity for either output leaves every input, FE, work, and
  output unchanged;
- `crusher_double` (150 ticks / 6000 FE) is not inferred from a plain station
  item.

### Pressing (`actuallyadditions:pressing`)

- class: `PressingRecipe`;
- descriptor: `auto_storage:actuallyadditions_pressing`;
- station item: `actuallyadditions:canola_press`;
- rate: `TileEntityCanolaPress.ENERGY_USE` (35) work per tick;
- FE and station work: `ENERGY_USE × TIME` = `1050`;
- consume one simple exact item; emit exact sized fluid;
- the output fluid must expose a non-empty bucket item for terminal
  presentation; bucketless outputs fail closed before catalog construction.

### Fermenting (`actuallyadditions:fermenting`)

- class: `FermentingRecipe`;
- descriptor: `auto_storage:actuallyadditions_fermenting`;
- station item: `actuallyadditions:fermenting_barrel`;
- rate: 1 work per tick;
- station work: `recipe.getTime()` (no FE);
- consume exact sized fluid; emit exact sized fluid;
- the output fluid must expose a non-empty bucket item for terminal
  presentation; bucketless outputs fail closed before catalog construction.

## Explicit exclusions

Eight audited recipe-class candidates remain rejected, including Empowerer multiblock
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

Fourteen real GameTests cover registration and Empowerer/Laser exclusion,
blaze-rod crushing with exact powder output, guaranteed two-output crushing and
secondary-capacity rollback, fractional-primary and fractional-secondary
rejection, pressing canola oil, fermenting refined canola, bucketless-fluid
rejection, missing-ingredient /
insufficient FE / insufficient work atomic no-ops, destination-capacity and
long-overflow rollback. The isolated fixture also asserts the descriptor-owned
`actuallyadditions` recipe-inventory digest. Compat Kit verify writes
`build/compat-kit/actuallyadditions-report.json`. The all-mod compatibility
matrix structurally validates coexistence claims and records actual
coexistence/unclaimed digests only in `build/reports/terminal-scale-*.json`.

Processing labels use the logical recipe-family names Crushing, Pressing, and
Fermenting; installed station item tooltips retain the concrete machine names.
