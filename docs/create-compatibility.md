# Create compatibility

## Status

Create remains optional. Magic Storage links the compatibility class only after `ModList` confirms that Create is loaded. Normal dedicated servers do not require Create.

The representative CI artifact is Create `6.0.10+mc1.21.1` (`UjX6dr61`). It is reproducible test evidence, not an exact player dependency pin.

## Supported deterministic families

| Family | Installed station | Cost and resources |
|---|---|---|
| Milling | Millstone | exact item and crafting remainder; recipe processing duration as station work |
| Crushing | Crushing Wheel | exact item; deterministic item outputs merged by exact key; recipe duration |
| Cutting | Mechanical Saw | exact item and deterministic outputs; recipe duration |
| Filling | Spout | exact item + sized fluid; fixed Create filling time |
| Emptying | Item Drain | exact item to deterministic item + sized fluid output; fixed Create draining time |

Each station is one logical process descriptor with a normalized `1 work/tick` rate and an installed-count ceiling of `Integer.MAX_VALUE`. This is Magic Storage's deterministic workstation abstraction; it does not simulate Create RPM or stress.

All inputs, crafting remainders, station work, item outputs, and fluid outputs use one server-owned simulate-then-commit transaction. Duplicate deterministic outputs are merged with checked arithmetic before the plan is accepted.

## Fail-closed boundaries

Magic Storage rejects:

- any `ProcessingOutput` whose chance is not exactly `1.0`;
- empty, custom, or non-enumerable ingredients;
- non-positive timed processing duration;
- heated recipes;
- Pressing because its live kinetic cycle has no exact recipe duration;
- Mixing and Compacting because their basin/machine/heat requirements need multiple simultaneous stations;
- Deploying and Sandpaper because tool durability changes are not represented by the retained-tool contract;
- Splashing and Haunting because they use world catalysts;
- Mechanical Crafting because machine count and layout matter;
- Sequenced Assembly because multi-step planning remains separate work.

Magic Storage does not register Create workstations into EMI. Create owns its recipe-viewer metadata.

## Verification

The isolated fixture loads the real representative mod. Thirteen GameTests cover all five exact families, station registration and Core reload, item remainder, duplicate-output merge, preservation of distinct deterministic outputs, item/fluid transactions, work/fluid shortages, output overflow, chance rejection, unsupported families, and zero-duration rejection.

```bash
./gradlew runCreateGameTestServer
```
