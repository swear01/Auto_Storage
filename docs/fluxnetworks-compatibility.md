# Flux Networks compatibility

The representative Flux Networks 8.0.0 artifact is audited as
`curse.maven:flux-networks-248020:6089446` with SHA-256
`29e511b2d76f85bd247aa213a92bb059ecc037fcc3fed82baffcff97b638d3a6`.
Its 15 data recipes remain covered by Auto Storage's existing vanilla-shaped
recipe adapters. `ConfigWipeRecipe` and `FluxStorageRecipe` are explicitly
rejected as separate Auto Storage families; the Flux network itself is
bidirectional storage/transfer and is not a Transform.

## Flux Station

When Flux Networks is loaded and its `enableFluxRecipe` server setting is
enabled, the bundled `fluxnetworks` module registers `auto_storage:flux_station`,
its block item, one synthetic recipe family, and one synthetic terminal recipe:

- input: 1 redstone
- output: 1 `fluxnetworks:flux_dust`
- station: the registered Flux Station descriptor
- batch: uses the existing terminal batch amount controls

The station block occupies the original Flux interaction position: the block
that would have been obsidian. Its base condition is preserved; the block two
positions below must be bedrock or `fluxnetworks:flux_block`. Auto Storage does
not consume ground item entities, remove world blocks, or emulate the original
left-click event. The conversion is deliberately only the fixed 1:1 storage
transaction.

The station has a survival recipe conditional on Flux Networks being loaded. Its
3x3 layout uses 2 `fluxnetworks:flux_dust`, 2 `minecraft:obsidian`, 2
`fluxnetworks:flux_block`, 2 `fluxnetworks:flux_core`, 1
`fluxnetworks:flux_controller`, and 1 `auto_storage:storage_core`. This makes
the station a deliberate bridge between the two mods rather than a creative-only
registration.

Placed stations are tracked server-side per dimension in `WorldStations` and
persisted through saved data. Validity is cached with a revision, refreshed on
station/base/chunk changes, and unknown, invalid, or unloaded entries fail
closed until revalidation. The terminal uses the cached revision rather than
scanning the world during craft;
Craftable and client Stations state are invalidated when it changes. Installing
the Flux Station item in the Core remains the descriptor's second availability
source.

The block drops its registered item programmatically, so the optional Flux
compatibility module does not leak a loot table referencing `flux_station` into
servers where Flux Networks is absent. When `enableFluxRecipe` is disabled,
the synthetic recipe and world availability are fail-closed.

The isolated `fluxnetworksFixture` runs nineteen GameTests covering registration,
synthetic recipe exposure, config gating, block drops, both accepted bases,
invalid-base/cache transitions, 1:1 conversion, exact batching,
installed-item availability, registry reload/removal behavior, and stale
persisted-state rejection.
