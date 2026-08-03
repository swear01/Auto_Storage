# Ars Nouveau compatibility

## Status and version policy

Ars Nouveau is optional. Auto Storage registers Source, stations, and recipe
families only when the mod is loaded; the normal dedicated server does not link
Ars Nouveau classes.

The representative CI artifact is Ars Nouveau `5.12.1`, resolved from immutable
Modrinth version ID `7IK2KsiH`. GeckoLib `4.8.2` (`RVIo5f6E`) and Curios
`9.3.1+1.21.1` are present only in that isolated fixture runtime. This
representative CI artifact is not an exact player dependency pin or a
multi-version promise. Other installed versions remain accepted, and
incompatibilities are handled from player reports.

## Source

Stored Source uses the public Ars Nouveau Source capability. Source Jars expose
it to directional Import and Export Buses through the generic typed-resource
bridge. Simulation and commit both use Ars Nouveau's own `receiveSource` and
`extractSource` operations; Source joins the same atomic Core transaction as
items, station work, remainders, and outputs.

Source appears in the terminal's Energy resource view only while Ars Nouveau is
loaded. Its amount, capacity, persistence, and transfer limits remain the
provider's native Source units.

## Supported recipe families

| Family | Installed station | Work per craft | Exact behavior |
|---|---|---:|---|
| Imbuement | Imbuement Chamber | 100 | Consumes the center ingredient and Source; pedestal ingredients are retained catalysts |
| Apparatus | Enchanting Apparatus | 210 | Consumes reagent and ordinary pedestal ingredients, retains `apparatus_not_consumed` catalysts, and returns crafting remainders |

Both families use exact ingredient alternatives and one simulate-then-commit
transaction. Apparatus recipes that preserve reagent components resolve a plan
from the exact stored reagent, copy its explicit component patch to the output,
and reset damage to zero.

## Fail-closed boundaries

- Empty, non-simple, dynamic, or world-dependent ingredients are not guessed.
- Imbuement plans above three pedestal ingredients and Apparatus plans above
  eight pedestal ingredients are rejected.
- Any plan above the shared nine-input contract is rejected.
- A pedestal alternative set that mixes retained and consumed items is rejected.
- Auto Storage does not drive external Ars Nouveau machines or register
  third-party EMI workstation metadata.

## Verification

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew runArsNouveauGameTestServer --console=plain --no-daemon
```

The Gradle task performs root-bounded fresh-world cleanup before Minecraft starts.

The isolated run requires the current SelfTest summary and
`All 11 required tests passed`. It covers conditional registration, exact
Source Jar simulation/commit, terminal listing and persistence, Imbuement
batching with retained catalysts, Apparatus remainders and component copying,
station and work persistence, insufficient-Source rejection, overflow rollback,
retained/consumed ingredient-overlap rejection, and dynamic/world/over-input
fail-closed behavior.
