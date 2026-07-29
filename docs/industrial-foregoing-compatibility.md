# Industrial Foregoing compatibility

> Status: automated support complete for the audited deterministic server-recipe subset. Fullscreen GUI acceptance is deferred to the single batched optional-mod handoff.

## Dependency policy

Industrial Foregoing and Titanium remain optional. Their Java classes are linked only from the isolated compatibility class after `ModList` confirms that Industrial Foregoing is loaded. Normal dedicated servers do not need either mod.

CI resolves one representative CI artifact:

- Industrial Foregoing `1.21-3.6.39` (`7otXKx1D`)
- Titanium `4.0.45` (`7AWtSmd7`)

These IDs make CI reproducible; they are not an exact player dependency pin. The fixture accepts the upstream `1.21-3.6` line and later compatible releases. Other-version incompatibilities are handled from player reports, not a multi-version matrix.

## Supported families

| Family | Installed station | Atomic inputs | Outputs |
|---|---|---|---|
| `industrialforegoing:dissolution_chamber` | Dissolution Chamber, `1 work/tick` | grouped exact item slots, exact sized fluid alternatives, live `processingTime × powerPerTick` NeoForge Energy, `processingTime` station work | one exact item primary and optional exact fluid byproduct |
| `industrialforegoing:stonework_generate` | Material Stonework Factory, `1 work/tick` | water/lava threshold as retained catalyst when consumption is zero, otherwise exact full-threshold consumption; live factory FE and work | exact item |
| `industrialforegoing:crusher` | Material Stonework Factory, `1 work/tick` | one exact item alternative, live factory FE and work | one unambiguous exact item |

The Dissolution Chamber follows the machine implementation rather than vanilla crafting semantics: every occupied input slot consumes one item, so buckets and other crafting containers do not create a remainder. Repeated identical slots are grouped before the nine-input typed-plan limit is applied.

Industrial Foregoing FE is the shared `magic_storage:neoforge_energy` resource. It is not Magic Storage Fuel or a legacy process-energy pool. All items, fluids, FE, station work, primary output, and byproducts use one simulate-then-commit transaction.

## Fail-closed boundary

Recipes are rejected when any required input/output is empty or dynamic, a duration or live config value is non-positive, the typed input count exceeds nine, a Dissolution output relies on a custom `onCraftedBy` hook, or a Crusher output resolves to zero or multiple item variants.

Stonework recipes with `0 < consume < need` are rejected because the current typed contract cannot atomically express “retain a larger threshold while consuming a smaller amount” for the same key. The official netherrack recipe is the regression case.

The following families are intentionally unsupported:

- Fluid Extractor: world block mutation and break chance.
- Ore/Fluid Laser Drill: biome, dimension, height, entity, and weighted random context.
- client-only synthetic EMI categories such as Bioreactor, Washer, Fermentation, Sieve, Dye Mixer, Latex, Sewage, Sludge, and Spores.
- Titanium augment inventories: upgrade state belongs to a placed block entity and is not encoded in the station item.
- external-machine send-and-wait execution.

Magic Storage does not register Industrial Foregoing workstations into EMI. The recipe owner remains responsible for its viewer metadata.

## Verification

```bash
./gradlew compileJava compileIndustrialForegoingFixtureJava
./gradlew runIndustrialForegoingGameTestServer
```

The isolated fixture runs nine GameTests covering exact registration, official Dissolution/Stonework/Crusher recipes, fluid tags, repeated slots, no crafting remainder, live FE/work costs, unsupported thresholds/outputs, and atomic shortage/overflow rollback.
