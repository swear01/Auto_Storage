# EvilCraft Compatibility

> Status: automated complete for GitHub [#16](https://github.com/swear01/Auto_Storage/issues/16). The combined EMI/TMRV GUI pack deliberately excludes EvilCraft; this is an upstream recipe-viewer lifecycle boundary, not missing Auto Storage server coverage.

## Supported

- EvilCraft Blood is an ordinary NeoForge fluid. Core storage, Fluid Terminal display, persistence, containers, and Buses use the existing exact `FluidStack` resource path.
- The Blood Infuser is an installable timed station with one work unit per installed machine per tick.
- Exact Blood Infuser recipes may consume one simple item ingredient and/or one exact fluid amount, retain a required Promise tier, consume recipe duration as station work, and produce one exact item output.
- A tier-N recipe accepts Promise tier N through 3. Promises are retained across the whole batch.

All item, Blood, Promise, station-work, and output changes use the shared simulate-then-commit transaction.

## Fail-closed boundaries

- Positive or negative XP output is rejected because the shared ledger has no fractional XP resource kind.
- Tag-selected output, tier above 3, empty input, invalid duration, and unsupported output shapes are rejected.
- The Purifier is action-registry, component, enchantment, entity, and chance driven rather than an exact recipe family, so it is not registered.
- Optional speed and efficiency Promises alter live machine behavior and are not installed-station recipe inputs. The current station uses the base duration and Blood cost rather than guessing a modifier combination.

The official 1.2.91 data set currently leaves Bloody Cobblestone as the one exact no-XP Blood Infuser recipe accepted by these rules. Deterministic data-pack recipes with the same contract are also accepted.

## CI policy

CI uses EvilCraft 1.2.91 (`Shx1BSHZ`) plus Cyclops Core 1.29.1 (`vEjxRv40`) as one representative CI artifact pair. These coordinates are reproducible test evidence, not an exact player dependency pin. Other compatible versions are accepted; incompatibilities are handled from user reports rather than a multi-version matrix.

`runEvilCraftGameTestServer` runs ten isolated tests covering conditional registration, unsupported-recipe rejection, standard Blood fluid capability behavior, terminal display and persistence, tiered batching, retained Promises, consumed-input/Promise overlap rejection, fluid-only and official recipes, shortages, and overflow rollback.

## Combined client-pack boundary

TMRV 0.9.0 declares a `jei` stub. With EvilCraft 1.2.91, the integrated server then sends `evilcraftcompat:jei_spirit_furnace_recipe` before TMRV has created EvilCraft's JEI registrar, producing a real client `FATAL` with `SPIRIT_FURNACE_RECIPES_REGISTRAR is null`. This must not be whitelisted.

The 18-jar Prism EMI/TMRV pack therefore omits EvilCraft and Cyclops Core and removes stale copies transactionally. A diagnostic real-JEI/JEMI run avoided that exception but did not reach the fixed-world handoff reliably, so it is not a silent replacement. EvilCraft compatibility remains verified by the isolated fixture above; a combined GUI check waits for an upstream TMRV/EvilCraft lifecycle fix.

```bash
./gradlew runEvilCraftGameTestServer
```

## Remaining-machines audit (#134)

Seventeen EvilCraft block entities were re-verified against the Transform scope
(one-way deterministic item-to-stored-resource conversions). Only the Blood
Infuser qualifies and is supported above. Everything else is storage,
world/entity, or non-deterministic mechanics and stays fail-closed:

| Block entity | Verdict |
|---|---|
| Blood Infuser | supported (installable timed station, see above) |
| BloodChest / ColossalBloodChest | storage + auto-feeding, not a conversion |
| DarkTank | fluid tank, passive storage |
| EnvironmentalAccumulator (+ Sanguinary) | weather/ritual-dependent, non-deterministic |
| Purifier | action-registry / component / enchantment / chance, not an exact recipe family |
| Spirit Furnace | soul reanimation (entity/world mutation) |
| Spirit Portal / Reanimator | entity revival, world mutation |
| Entangled Chalice / DisplayStand / Eternal Water / BloodStain / Box of Eternal Closure / Invisible Redstone | storage / world / render mechanics |
