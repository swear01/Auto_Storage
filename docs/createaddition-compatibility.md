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
- one exact item output from `getResultStack()`;
- Create RPM/stress is abstracted exactly as Create milling.

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

Transaction:

- one simple item ingredient consumed once, with crafting remainders;
- exact FE consume;
- one exact item output from `getResultStack()`, including enchantment
  components when present;
- belt transport is abstracted away like Create spout/drain.

## Explicit exclusions

- Liquid burning: consumes fluid into Liquid Blaze Burner live burn-time /
  superheated heat state and emits no craft outputs.
- Tesla Coil entity-hurt, redstone zap, and capability item-charging without a
  `ChargingRecipe`.
- JEI/datagen/builder/serializer/params/condition helpers and Create recipe
  datagen providers that emit Create families already owned elsewhere.
- Runtime reflection, viewer authority, and third-party EMI workstation
  registration.

## Verification

```bash
./gradlew runCreateadditionGameTestServer
```

Eight real GameTests cover rolling/charging registration and liquid-burning
exclusion, exact rolling output, exact charging FE/work, missing ingredient,
insufficient FE, destination overflow, `Long.MAX_VALUE` overflow, and stale
holder rollback. The all-mod compatibility matrix protects coexistence.
