# Create Crafts & Additions Compatibility

Auto Storage's Compat Kit module supports the deterministic Rolling Mill and
Tesla Coil charging slices from Create Crafts & Additions. It loads only when
`createaddition` is present and compiles in the isolated `compatCreateaddition`
source set against the public Auto Storage API plus Create Crafts & Additions
and its required Create companion.

## Reproducible audit evidence

- target: Create Crafts & Additions `1.6.0`;
- Modrinth version ID: `qPr8V4G2`;
- immutable jar URL:
  `https://cdn.modrinth.com/data/kU1G12Nn/versions/qPr8V4G2/createaddition-1.6.0.jar`;
- jar SHA-256:
  `41876c3780b70365a1848994d146a73423cc19fbe86485885795d9e7d855e7e9`;
- ATM10 modlist SHA-1:
  `87b539d41ed238e98b26c607c0c81a973323147e`;
- official source branch `1.21.1`, commit
  `84c7b2ceafc0b382da4606ac0770085e63104c3a`;
- required runtime companion: Create `6.0.10+mc1.21.1` (`UjX6dr61`);
- scanner format `17`: 215 target classes and 3 actual `Recipe` candidates
  (`ChargingRecipe`, `LiquidBurningRecipe`, `RollingRecipe`); legacy format-7
  name-shaped datagen/JEI/builder/serializer false candidates are no longer
  contract families;
- six reachable ancestry artifacts: normalized NeoForge/Minecraft
  `2382ea29…eb5f` / `56,279,815` bytes plus five exact Maven coordinates
  (`cc.tweaked:cc-tweaked-1.21.1-core-api:1.115.1`,
  `com.simibubi.create:create-1.21.1:6.0.10-280:slim`,
  `dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:1.0.6`,
  `mezz.jei:jei-1.21.1-common-api:19.25.0.323`,
  `net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1`);
- exact target-jar recipe-data inventory: 152 declared/effective recipes,
  SHA-256 `08c912d27581a94ba005c8b58713e3f95942ffa1e2660b23f6db812e0d306034`;
- isolated fixture recipe-inventory digest for loaded `createaddition:*`
  recipes: `57916d79470225dd3db82f96c7e5c70a87192df1930c8d8efa4768add46fd0a3`
  (110 recipes);
- audit: `compat/audits/createaddition/1.6.0.json`;
- reviewed contract: `compat/contracts/createaddition.json`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Create Crafts & Additions version on players and does not claim a
multi-version matrix.

## Accepted families

### Rolling

Exact class/type:

- `com.mrh0.createaddition.recipe.rolling.RollingRecipe`;
- `createaddition:rolling` / `CARecipes.ROLLING_TYPE`.

Logical station:

- descriptor `auto_storage:createaddition_rolling_mill`;
- station item `createaddition:rolling_mill`;
- Processing category, `1` work per tick;
- station work equals loaded
  `CommonConfig.ROLLING_MILL_PROCESSING_DURATION` (default `120`).

Transaction:

- one simple item ingredient consumed once, with crafting remainders;
- one exact item output from the single guaranteed `ProcessingOutput`
  (`chance == 1.0`); inherited fluid ingredients/results and any extra or
  chance outputs fail closed before exposure;
- Create RPM/stress is abstracted exactly as Create milling.
- station work and Craftable cache identity follow
  `CommonConfig.ROLLING_MILL_PROCESSING_DURATION` through
  `dynamicDeterministicResources`, so a live config reload cannot leave a
  stale work cost. Recipe eligibility remains independent of that reloadable
  value; a non-positive current duration yields no usable variant during
  dynamic cost refresh without throwing through menu or server tick.

### Charging

Exact class/type:

- `com.mrh0.createaddition.recipe.charging.ChargingRecipe`;
- `createaddition:charging` / `CARecipes.CHARGING_TYPE`.

Logical station:

- descriptor `auto_storage:createaddition_tesla_coil`;
- station item `createaddition:tesla_coil`;
- Processing category, `1` work per tick;
- FE cost equals `recipe.energy`;
- station work equals
  `ceil(energy / min(CommonConfig.TESLA_COIL_RECIPE_CHARGE_RATE, recipe.maxChargeRate))`.
  Eligibility depends only on invariant recipe fields. If a live config reload
  makes the evaluated rate non-positive, dynamic cost refresh yields no usable
  variant without throwing through menu or server tick, instead of dividing by
  zero or inventing a rate.

Transaction:

- one simple item ingredient consumed once, with crafting remainders;
- exact FE consume;
- one exact item output from the single guaranteed `ProcessingOutput`
  (`chance == 1.0`), including enchantment components when present;
  inherited fluid ingredients/results and any extra or chance outputs fail
  closed before exposure;
- belt transport is abstracted away like Create spout/drain.
- FE charge-rate config participates in
  `dynamicDeterministicResources` so a live
  `TESLA_COIL_RECIPE_CHARGE_RATE` reload cannot leave a stale work cost.

## Explicit exclusions

- Liquid burning: consumes fluid into Liquid Blaze Burner live burn-time /
  superheated heat state and emits no craft outputs.
- Tesla Coil entity-hurt, redstone zap, and capability item-charging without a
  `ChargingRecipe`.
- JEI/datagen/builder/serializer/params/condition helpers and Create recipe
  datagen providers that emit Create families already owned elsewhere.
- Runtime reflection, viewer authority, and third-party EMI workstation
  registration.

## Declarative matrix evidence

The module descriptor and reviewed contract declare `createaddition` present
with Rolling Mill and Tesla Coil descriptors plus the two accepted recipe IDs
used by the fixture. The isolated Create Crafts & Additions fixture locks the
110 successfully loaded `createaddition:*` recipes by SHA-256
`57916d79470225dd3db82f96c7e5c70a87192df1930c8d8efa4768add46fd0a3`. Cross-namespace
Create datapack recipes shipped inside the jar remain Create-owned coexistence
evidence in the matrix report only.

## Verification

```bash
./gradlew runCreateadditionGameTestServer
tools/compat-kit/compat-kit verify compat/contracts/createaddition.json \
  --audit compat/audits/createaddition/1.6.0.json \
  --jar build/compat-kit/artifacts/createaddition-1.6.0.jar \
  --classpath …exact ancestry jars… \
  --bundled . \
  --output build/compat-kit/createaddition-report.json
./gradlew runCompatibilityMatrixGameTestServer
```

Nine real GameTests cover rolling/charging registration and liquid-burning
exclusion plus loaded chance-recipe presence and programmatic fluid-shape
fail-closed at both Create validation and Auto Storage classification, exact rolling remainder,
exact rolling output, exact charging FE/work, missing ingredient,
insufficient FE, destination overflow, `Long.MAX_VALUE` overflow, and stale
holder rollback. Bundled Compat Kit verify reports twelve exact checks across
five commands (`build`, base GameTests, recipe-addon, createaddition, matrix).
`catalyst_tool_remainder_exact` maps to the rolling remainder GameTest;
`multi_output_merge_exact` binds the generic public-SDK typed-family multi-output
evidence because accepted Rolling/Charging families are single-output only.
Processing labels use the JEI family names Rolling / Charging.
Both overflow cases assert the input stack, full output destination, and
accumulated station work remain unchanged.
Latest local matrix evidence after the Codex review recovery onto Immersive
Engineering `d8314fc`: **21,162** recipes, Craftable prepare **30.682 ms**,
first/shared p95/warm switch **0.925 / 0.360 / 0.277 ms**, storage interaction
p95 **13.128 ms**, shared index retained **4,113,648** bytes ≈ **3.922 MiB**,
per-menu **116,952** bytes, and report
`build/reports/terminal-scale-10000.json`. 9 MiB／50 ms／128 KiB gates unchanged.
