# Theurgy compatibility

## Status

Theurgy remains optional. Auto Storage links the compatibility class only after `ModList` confirms that Theurgy is loaded. Normal dedicated servers do not require Theurgy.

The representative CI artifact is Theurgy `1.21.1-neoforge-1.73.1` (`KvM1ocNj`), SHA-256 `6cbe0abe5fa53ba3d9308c7fe2b9a8f2df4d568f69fdb99a2fe6c6d1e59fdbc5`. It is reproducible test evidence, not an exact player dependency pin. Runtime companions are Modonomicon `1.105.0` and GeckoLib `4.5.8`.

The scanner-format-17 audit binds all 603 target classes, six reachable ancestry artifacts with five exact external coordinates, 10 structural `Recipe` candidates, and 1,730 target recipe records. Migration from the legacy format-7 name scan removed 81 serializer, input/result helper, provider, viewer, registry, and other non-recipe candidates. The same exact source revision and unchanged public signatures were re-reviewed: Calcination, Distillation, and Liquefaction remain accepted, while the other seven real recipe classes remain explicitly rejected under the boundaries below.

## Supported deterministic families

| Family | Installed station | Cost and resources |
|---|---|---|
| Calcination | Calcination Oven | exact sized item input; recipe time as station work |
| Distillation | Distiller | exact sized item input; recipe time as station work |
| Liquefaction | Liquefaction Cauldron | exact sized item + sized fluid solvent; recipe time as station work |

Each station is one logical process descriptor with a normalized `1 work/tick` rate and an installed-count ceiling of `Integer.MAX_VALUE`. Adjacent pyromantic brazier / caloric flux heat is abstracted by the installed Processing station and is not simulated as live machine heat.

Empty, non-simple, or non-enumerable ingredients and non-positive recipe times are rejected. All accepted inputs, solvent fluid, station work, and item outputs use one server-owned simulate-then-commit transaction.

## Fail-closed boundaries

Auto Storage rejects:

- Accumulation (optional evaporant/solute and fluid-primary live tank rules);
- Catalysation / Mercury Flux generation;
- Digestion and Fermentation;
- Incubation (`TagRecipeResult` / non-exact result risk);
- Reformation (multiblock Mercury Flux plus component copy);
- Divination Rod and other helper/serializer/datagen/JEI/EMI surfaces;
- any recipe whose sized ingredient is empty, custom, or non-simple;
- non-positive `recipe.time`.

Auto Storage does not register Theurgy workstations into EMI. Theurgy owns its recipe-viewer metadata.

## Verification

The isolated fixture loads the real representative mod. Nine GameTests cover Calcination, Distillation, and Liquefaction registration, rejected-family exclusion, exact item/solvent/work commits, ingredient and solvent shortage rollback, insufficient work, destination overflow, and exact `Long.MAX_VALUE` seed preservation.

```bash
./gradlew runTheurgyGameTestServer
```
