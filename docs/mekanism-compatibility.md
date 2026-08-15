# Mekanism compatibility

## Status and version policy

Mekanism is an optional server-side integration. Auto Storage registers the
Mekanism-linked resource, station, and recipe adapters only when Mekanism is
loaded; the normal dedicated server remains independent of Mekanism classes.

The representative CI artifact is Mekanism `1.21.1-10.7.19.85`, resolved from
the Modrinth version ID in `gradle.properties`. It is compatibility evidence,
not an exact player dependency pin and not a multi-version promise. Other
Mekanism versions remain accepted; incompatibilities are handled from player
reports rather than by restricting the player version.

## Registered recipe-type inventory

Mekanism `10.7.19.85` registers 26 server `RecipeType`s. Auto Storage supports
22 of them through exact loaded recipe classes and explicitly excludes four.

### Nine factory-backed families

Each logical station accepts its basic machine and Basic, Advanced, Elite, and
Ultimate Factory variants. Their exact parallel rates are 1, 3, 5, 7, and 9
work per tick per installed block. The player-facing label uses Mekanism's
shared `FactoryType` translation, such as `Crushing`, rather than the base
machine item name (`Crusher`) or a tier-specific Factory name.

| Recipe type | Basic station | Typed shape |
|---|---|---|
| Crushing | Crusher | item -> item |
| Enriching | Enrichment Chamber | item -> item |
| Smelting | Energized Smelter | item -> item |
| Compressing | Osmium Compressor | item + chemical -> item |
| Combining | Combiner | item + item -> item |
| Purifying | Purification Chamber | item + chemical -> item |
| Injecting | Chemical Injection Chamber | item + chemical -> item |
| Metallurgic Infusing | Metallurgic Infuser | item + chemical -> item |
| Sawing | Precision Sawmill | item -> deterministic item outputs |

Sawing recipes with probabilistic outputs remain fail-closed.

### Additional supported deterministic single-block machines

| Recipe type | Station | Typed shape and boundary |
|---|---|---|
| Reaction | Pressurized Reaction Chamber | item + fluid + chemical + FE -> item and/or chemical |
| Rotary | Rotary Condensentrator | one-way fluid -> chemical or one-way chemical -> fluid |
| Oxidizing | Chemical Oxidizer | item -> chemical |
| Chemical Infusing | Chemical Infuser | chemical + chemical -> chemical |
| Separating | Electrolytic Separator | fluid + recipe energy multiplier -> two chemicals |
| Dissolution | Chemical Dissolution Chamber | item + per-tick chemical -> chemical |
| Washing | Chemical Washer | fluid + chemical -> chemical |
| Crystallizing | Chemical Crystallizer | chemical -> item |
| Centrifuging | Isotopic Centrifuge | chemical -> chemical |
| Nucleosynthesizing | Antiprotonic Nucleosynthesizer | item + per-tick chemical -> item, using recipe duration |
| Pigment Extracting | Pigment Extractor | item -> pigment |
| Pigment Mixing | Pigment Mixer | pigment + pigment -> pigment |
| Painting | Painting Machine | item + per-tick pigment -> item |

All sized item, fluid, chemical, FE, station-work, primary-output, co-output,
remainder, and retained-catalyst deltas share one server-owned
simulate-then-commit transaction. A chemical-only primary output is listed in
the Gases Craftable view and can commit only to Storage. A Rotary holder that
contains both directions is rejected because one fixed server recipe holder
cannot select a different output plan from the allocated input.

### Explicitly excluded registered recipe types

| Recipe type | Reason |
|---|---|
| Evaporating | Executed by the Thermal Evaporation multiblock |
| Activating | Solar Neutron Activator progress depends on live dimension, biome, weather, and sky state |
| Chemical Conversion | Virtual recipe-viewer conversion, not a concrete workstation execution contract |
| Chemical Conversion | Virtual recipe-viewer conversion, not a concrete workstation execution contract |

This accounts for all 26 registered Mekanism recipe types: 22 supported and
four excluded.

## Other audited machine boundaries

- Nutritional Liquifier recipes are synthesized from an input item's food
  properties by the machine. Their recipe type and serializer are `null`, so
  they never enter the server `RecipeManager`; the current exact family
  registry rejects them instead of fabricating a type or dropping the returned
  container.
- Thermal Evaporation, Boiler, SPS, Induction Matrix, Dynamic Tank, Industrial
  Turbine, and reactor structures are multiblocks and are outside the current
  station model.
- Digital Miner, Electric Pump, Fluidic Plenisher, and similar machines act on
  the external world rather than executing a deterministic stored-resource
  recipe.
- Transport, storage, generators, passive conversion, and external-machine
  send-and-wait behavior are not crafting stations.

## Resource storage and transfer

- Exact Mekanism chemicals use the Core's shared long-amount typed ledger.
- The terminal exposes the Gases resource group only while a chemical provider
  is registered.
- Every player-facing Gas entry, selector, native diagram, and resource ledger
  renders Mekanism's exact EMI chemical glyph and per-recipe long amount. The
  internal tank carrier is not a player icon, and its capacity is not a recipe
  unit.
- Held containers and Import/Export Buses use the optional Mekanism chemical
  capability without making Mekanism a required dependency.
- Item, fluid, NeoForge Energy, and chemical inputs may participate in the same
  atomic deterministic recipe plan.

## Verification

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew compileMekanismFixtureJava
./gradlew runMekanismGameTestServer
```

The Mekanism run requires `All 47 required tests passed` plus the current
SelfTest summary. It covers all nine basic/factory families, exact factory
throughput, the 13 additional supported recipe types, typed-resource
transactions, chemical-only output, overflow and rollback, recipe reload,
bidirectional Rotary rejection, and the Nutritional synthetic-recipe boundary.

## Energy Conversion (Transform)

`auto_storage:mekanism_energy_conversion` is an **instant** Transform use
(no station, no work): `c:dusts/redstone` converts to 10,000 FE and
`c:storage_blocks/redstone` to 90,000 FE per item, matching the
`mekanism:energy_conversion` datapack recipes (10.7.19.85: redstone →
10,000, redstone block → 90,000). Mekanism has no physical workstation for
these recipes; they are datapack-driven deterministic conversions, so the
transform is instant rather than station-work based.

## Gas-Burning Generator (Transform)

`auto_storage:mekanism_gas_generator` is a PROCESS descriptor (one
work/tick) plus a time-based Transform use: any chemical-tank item
carrying a gas with a ChemicalFuel datamap converts to FE = contents ×
`ChemicalFuel.energyDensity()` over ceil(contents / 256) work ticks at the
max 256 mb/tick burn rate, retaining the empty tank item. Verified against
Mekanism 10.7.19.85 + MekanismGenerators 10.7.19.85 bytecode
(`TileEntityGasGenerator.onUpdateServer`, `FuelTank.getFuel`, hard-coded
256 mb/tick `getToUse` ceiling). Hydrogen basic tank (32,000 mb): 125 work
→ 25,600,000 FE at default fuel values.
