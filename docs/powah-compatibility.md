# Powah compatibility

Magic Storage conditionally loads this module only when `powah` is present.
Powah, Cloth Config, and GuideME remain optional player dependencies.

## Supported contract

- `powah:energizing` recipes with one to six simple, exact item ingredients
- Exact item output and output count
- Scaled FE cost from Powah's live `energizing_energy_ratio`
- One matching station-work cost for the same scaled FE amount
- Starter through Nitro Energizing Rod station variants
- Each rod's work/tick from Powah's loaded `energizing_rods` transfer config
- Shapeless multiplicity, batching, Max craft, persistence, and atomic rollback

Installing a rod represents the virtual Energizing setup. The Core consumes FE
from its shared NeoForge Energy ledger and uses the installed rod tier only to
set throughput. A separate Energizing Orb item is not consumed or retained.

## Fail-closed boundary

Magic Storage rejects recipes with zero or negative raw energy, more than six
ingredients, custom/dynamic ingredients, or an empty output. It does not model
the physical rod's one-time 20-tick warm-up, world links, reactors, generators,
or external-machine send-and-wait.

Powah owns its EMI category and workstation metadata. Magic Storage does not
register Powah workstations into EMI.

## Verification

`./gradlew runPowahGameTestServer` loads Powah `6.2.10`, Cloth Config
`15.0.140`, and GuideME `21.1.17` as one representative CI artifact set. The
fixture verifies live tier rates, the official Energized Steel recipe, six-input
batching, standard NeoForge Energy capability behavior, terminal/reload
persistence, FE/work shortages, and output-overflow rollback.

This representative CI artifact is compatibility evidence, not an exact player dependency pin.
Other versions are accepted; incompatible versions are handled from user
reports rather than a multi-version matrix.
