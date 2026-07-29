# Modern Industrialization compatibility

## Status and version policy

Modern Industrialization is optional. Magic Storage registers its stations and
recipe families only when the mod is loaded; the normal dedicated server does
not link Modern Industrialization classes.

The representative CI artifact is Modern Industrialization `2.5.4`, resolved
from immutable Modrinth version ID `xDYiDP82`; GuideME `21.1.17` is present only
in that isolated fixture runtime. This representative run is not an exact player dependency pin
or a multi-version promise. Other installed versions
remain accepted, and incompatibilities are handled from player reports.

## Supported direct machine families

The adapter reads the loaded `MachineRecipe` contract and the station tiers'
published EU rates. Bronze, Steel, and Electric LV stations contribute 2, 4,
and 8 work per tick per installed block.

| Family | Installed station tiers | Maximum item/fluid inputs | Maximum item/fluid outputs |
|---|---|---:|---:|
| Assembler | Electric | 9 / 2 | 3 / 0 |
| Centrifuge | Electric | 1 / 1 | 4 / 4 |
| Chemical Reactor | Electric | 3 / 3 | 3 / 3 |
| Compressor | Bronze, Steel, Electric | 1 / 0 | 1 / 0 |
| Cutting Machine | Bronze, Steel, Electric | 1 / 1 | 1 / 0 |
| Distillery | Electric | 0 / 1 | 0 / 1 |
| Electrolyzer | Electric | 1 / 1 | 4 / 4 |
| Furnace | Bronze, Steel, Electric | 1 / 0 | 1 / 0 |
| Macerator | Bronze, Steel, Electric | 1 / 0 | 4 / 0 |
| Mixer | Bronze, Steel, Electric | 4 / 2 | 2 / 2 |
| Packer | Steel, Electric | 3 / 0 | 1 / 0 |
| Polarizer | Electric | 2 / 0 | 1 / 0 |
| Unpacker | Steel, Electric | 1 / 0 | 2 / 0 |
| Wiremill | Steel, Electric | 1 / 0 | 1 / 0 |

The official `2.5.4` data contains 1,793 direct single-block recipes in these
families. Thirty recipes use probabilistic outputs. Of the remaining 1,763,
Magic Storage accepts 1,738 whose EU requirement fits every listed station
variant; the other 25 require a higher tier than that family's lowest accepted
variant and therefore fail closed.

Each accepted plan atomically accounts for exact item and fluid alternatives,
retained zero-probability catalysts, EU (`recipe EU × duration`), station work,
the primary output, and every deterministic co-output. A zero-probability input
is retained; a one-probability input is consumed. Fractional input or output
probabilities, non-empty process conditions, invalid slot counts, over-tier
recipes, overflow, or missing representatives are rejected before mutation.

## Explicit boundaries

- The Furnace type is registered for direct `MachineRecipe` holders, but
  Modern Industrialization `2.5.4` supplies its normal furnace recipes through
  runtime proxy conversion rather than direct holders. Cutting stonecutting
  proxies and Centrifuge compostable proxies have the same boundary and are not
  inferred from recipe-viewer metadata.
- Forge Hammer recipes use a separate recipe class and are not part of this
  adapter.
- Steam efficiency, warm-machine overclocking, and installed electric upgrades
  are live machine behavior, not deterministic cold stored-resource recipes.
- Magic Storage does not drive an external machine or register third-party EMI
  workstation metadata.

## Verification

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
python3 -c 'import shutil; shutil.rmtree("run/world", ignore_errors=True)'
./gradlew runModernIndustrializationGameTestServer --console=plain --no-daemon
```

The isolated run requires the current SelfTest summary and
`All 7 required tests passed`. It covers all 14 registrations and tier rates,
real item and fluid recipes, retained catalysts, rejection of non-positive
amounts and retained/consumed key overlap, slot/probability/tier rejection,
mixed item/fluid/EU multi-output persistence and commit, plus full rollback when
one output cannot fit.
