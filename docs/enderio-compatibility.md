# Ender IO Compatibility

Auto Storage conditionally loads this Compat Kit module only when `enderio` is
present. Ender IO remains an optional player dependency.

## Reproducible audit evidence

- target: Ender IO `8.2.11-beta`;
- official source tag: `v8.2.11-beta`;
- source commit: `fac4150bb6185566ff7159b0439452c515fb150d`;
- Modrinth jar SHA-256:
  `e01af48907781f2d5ccdfa8d71975b611c33f295be11b7021cb91be06ce8070c`;
- Maven coordinate: `maven.modrinth:enderio:Tfs8aJPH`;
- audit: `compat/audits/enderio/8.2.11-beta.json`;
- reviewed contract: `compat/contracts/enderio.json`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Ender IO version on players and does not claim a multi-version matrix.
Changes outside this fixture are handled from concrete reports and a new delta.

JarJar embeds `endercore` and related libraries inside the representative jar,
so the reviewed contract declares no additional runtime dependencies.

## Accepted family

Exact class/type:

- `com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe`;
- `enderio:alloy_smelting`.

Logical station:

- descriptor `auto_storage:enderio_alloy_smelting`;
- player-facing family name `Alloy Smelting`;
- station item `enderio:alloy_smelter`;
- Processing category;
- live work rate from loaded `MachinesConfig.COMMON.ENERGY.ALLOY_SMELTER_USAGE`
  under Basic Capacitor level 1 (`usage * 1^2`; default `20` work/tick).

Transaction:

- each non-empty simple sized item input is consumed;
- NeoForge Energy equal to `recipe.energy` is consumed from the Core ledger;
- matching station work equal to `recipe.energy` is consumed;
- exact item output count/components are written as the primary output.

Eligibility rejects `is_smelting` holders (vanilla batch copies injected by
Ender IO's recipe-manager mixin, plus any datapack `is_smelting` recipes), empty
or non-simple inputs, empty outputs, non-positive energy, and more than three
inputs. Higher capacitors, loot capacitors, machine mode, and unused experience
are not inferred from a plain Alloy Smelter item.

## Explicit exclusions

Every other audited recipe candidate remains rejected, including Sag Mill
(random outputs), Slicing (tool durability), Painting, Soul Binding, Fermenting,
Enchanter, Tank, Fire Crafting, Weather Change, Shaped Entity Storage, JEI/datagen
helpers, and nested value types. See `compat/contracts/enderio.json`.

Ender IO owns its EMI/JEI category and workstation metadata. Auto Storage does
not register Ender IO workstations into EMI.

## Verification

`./gradlew runEnderIoGameTestServer` loads the representative Modrinth artifact
and runs 6 GameTests for exact registration/rate, official Conductive Alloy
crafting, FE/work/ingredient shortages, destination overflow rollback, and
rejection of smelting-batch plus Sag Mill holders. The fixture datapack recipe
`enderio:smelting/auto_storage_enderio_fixture/rejection` proves the
`is_smelting` fail-closed boundary when mixin-generated smelting copies are not
present in the GameTest recipe manager.

This representative CI artifact is compatibility evidence, not an exact player
dependency pin. Other versions are accepted; incompatible versions are handled
from user reports rather than a multi-version matrix.
