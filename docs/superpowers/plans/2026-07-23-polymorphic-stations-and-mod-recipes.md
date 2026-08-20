# Polymorphic Stations and Mod Recipes Implementation Plan

> Current replacement: GitHub #23 removes the historical Consumables group and three-panel Fuel layout. Transform now owns exact-input conversions; Stations contains only height-adaptive Processing and Instant groups.

## Task 1: Lock public station-variant contracts — complete

1. Add RED compile/SelfTests for `MachineWorkRate`, `MachineVariant`, variant descriptor factories, validation, exact lookup, and client snapshot round-trip.
2. Run the focused compile/SelfTest path and confirm failures are missing API/behavior failures.
3. Implement the smallest immutable API and snapshot codec.
4. Re-run focused tests.

## Task 2: Add exact fractional Core generation — complete

1. Add RED GameTests for fractional accumulation, multiple installed blocks, replacement/rate-change reset, saturation, and save/reload.
2. Add descriptor-keyed work and remainder persistence without changing existing EnergyType IDs.
3. Route each PROCESS variant to either its legacy energy pool or descriptor work pool.
4. Re-run focused and persistence tests.

## Task 3: Add station-work recipe cost — complete

1. Add RED API/GameTests for station work plus optional Fuel and typed-resource atomicity.
2. Extend recipe cost, preview rows, maximum-craft calculation, simulate, commit, and rollback.
3. Keep the existing `EnergyCost` path unchanged for vanilla recipes.
4. Re-run crafting, Fuel, and menu parity suites.

## Task 4: Add preview variant cycling — complete

1. Add RED model/static tests for station descriptor metadata, deterministic variant ordering, actual-installed-first ordering, cycle wrap, and one-item stability.
2. Synchronize materialized variant snapshots and add client-only display selection.
3. Keep execution independent from the display index.
4. Record the GUI change as pending the batched fullscreen gate.

## Task 5: Integrate Iron Furnaces — complete

1. Add a representative Modrinth compile/runtime dependency and isolated fixture run.
2. Add RED absent/present tests and live-config rate assertions.
3. Load the direct typed compat class only when `ironfurnaces` is present.
4. Derive rates from active cook-time config; exclude multi-output special furnaces.
5. Add the fixture task to CI/release and run it alone.

## Task 6: Add the bounded multi-item family API — complete

1. Add RED different-package compile fixture and addon GameTests for 1..9 predicate/count item inputs, fixed item output, remainders, station work, and rollback.
2. Implement the public immutable input contract and factory through the existing adapter transaction engine.
3. Update `recipe-family-api.md` before moving to real mods.

## Task 7: Integrate Farmer's Delight — complete

1. Add representative runtime to the optional-mod fixture.
2. Add RED tests using real Cooking Pot recipes for ingredients, serving container, cook time, remainder, discovery, full destination, and reload.
3. Register one isolated Cooking Pot family and station descriptor.
4. Run the fixture and base absent-mod server.

## Task 8: Integrate Mekanism recipes — complete

1. Extend the existing representative Mekanism fixture RED tests for Crushing, Enriching, Smelting, Combining, and deterministic item-primary or chemical-only Pressurized Reaction.
2. Register exact public recipe classes/types and station descriptors.
3. Use typed plans for Reaction's fluid/chemical inputs, exact `energyRequired × duration` FE input, duration work, and separated primary/remainder outputs; preserve exact chemical-only identity/amount, force direct Storage, and reject non-item Player/Cursor/EMI-inventory destinations atomically.
4. Re-run all Mekanism chemical capability and recipe tests.

## Task 9: Finish docs and gates — complete

1. Update active overview, structure, roadmap, plan, notes, machine descriptor API, recipe family API, typed-resource behavior, and CI docs.
2. Update GitHub #1/#10 checklists only for verified acceptance criteria.
3. Run build, EMI minimum/latest compile, base GameTest, recipe-addon GameTest, optional-mod fixture, Mekanism fixture, Python, runData drift, and diff checks serially.
4. Commit and push checkpoints. Do not run or claim Prism visual verification yet.

## Task 10: Readability, provider-aware views, and focused GUI lab — in progress

1. RED-first regressions require decimal machine rates (`1.25`, positive sub-hundredths as `<0.01`), full-size two-line recipe amounts, and a 56px minimum ledger cell.
2. Resource view cycling uses a stable order but skips unavailable providers; both persisted and canonical chemical IDs map to Gas. Auto Storage removes its third-party EMI workstation registration and relies on the owning mod's JEI metadata exposed to EMI by TMRV in the GUI support pack.
3. Prism schema 5 requires an explicit scenario and creates only that checklist's blocks, targets, functions, hotbar, inventory, and server-owned baseline. `crafting-fuel-page` stages the 19-jar representative support pack, including hash-pinned MacFix 0.1.0, core Mekanism plus Mekanism Generators, Create, Extended Crafting, and Cucumber; it excludes PneumaticCraft because it has no accepted visible contract, excludes EvilCraft/Cyclops Core because TMRV 0.9.0 triggers EvilCraft 1.2.91's Spirit Furnace JEI packet before its registrar exists, and rejects JEI because TMRV is mutually exclusive. It then writes one matching Core repository record/BE reference preloaded with all items, stations, reserves, typed resources, and fluid/gas/energy/mana inputs與19種Ultimate Singularity component材料 required by the checklist. Its player inventory stays empty apart from hotbar 1/2 navigation and the one explicit Transform input, it exposes no destructive reset, and the handoff contains visual inspection only.
4. Run the complete serialized automated gate, deploy one new patch build, then hand the F11 fullscreen visual verdict to the user. Do not claim GUI approval from automation.

## Task 11: Factory throughput, large installed counts, and Fuel paging — automated complete; fullscreen pending

1. Add RED Mekanism fixture tests for all nine factory-backed families, their basic machine, and Basic/Advanced/Elite/Ultimate Factory variants at exact 1/3/5/7/9 work-per-tick rates.
2. Add RED GameTests for repeated normal-stack Shift+left-click to at least 130 installed timed machines, persistence, and exact descriptor routing while another Fuel category sub-page is visible.
3. Expand the installable-count contract without changing one-logical-descriptor identity, then make Core persistence, menu insertion, sync, generation, and overflow behavior agree on the same bound.
4. Add small previous/next controls to Consumables, Timed Stations, and Instant Stations while retaining panel-local wheel paging and the current three-panel layout.
5. Make the timed amount tooltip identify the concrete installed variant/count and show its total work increase per tick.
6. Superseded by the 2026-07-24 player requirement: keep panel-local paging as the default, but add a bottom-right Fuel search toggle. While active, replace the upper three panels with one unified paged result grid across reserves, consumables, timed stations, and instant stations; reuse name/`@mod`/`#tag` parsing and match every polymorphic concrete variant.
7. Complete automated evidence: `build`, EMI minimum/latest-compatible compile, SelfTest 264525/264525, base 380/380, recipe-addon 17/17, Mekanism 47/47, Botania 12/12, Iron Furnaces 3/3, Farmer's Delight 7/7, Python 236/236, `runData` no resource drift, and `git diff --check`. GitHub #11/#12 record the same evidence; only the current F11 fullscreen user-owned visual verdict remains for the Fuel/Mekanism UI.

## Task 12: Deterministic Mekanism single-block fluid/chemical families — automated complete; fullscreen pending

1. Add RED-first real-Mekanism fixture coverage for Chemical Oxidizer, Chemical Infuser, Electrolytic Separator, Chemical Dissolution Chamber, Chemical Washer, Chemical Crystallizer, Isotopic Centrifuge, Antiprotonic Nucleosynthesizer, Pigment Extractor, Pigment Mixer, and Painting Machine.
2. Use exact item/fluid/chemical inputs, checked per-tick multiplication, exact machine or recipe duration, Electrolysis energy-multiplier work, deterministic N-output delivery, and Storage-only non-item primary output.
3. Support one-way Rotary holders in either direction. Reject Mekanism 10.7.19's shipped bidirectional holders until a generic conditional-plan API can return a different output for each allocated input.
4. Keep Nutritional Liquifier unregistered: its food-derived synthetic recipe has a null recipe type/serializer and is absent from `RecipeManager`. A future generic synthetic-recipe discovery contract must preserve both Nutritional Paste and the exact optional item remainder.
5. GitHub #12 and active recipe docs record these fail-closed boundaries. The fixture-count build gate and full shared-worktree integration gates are green; only the later batched fullscreen presentation verdict remains.

## Task 13: Industrial Foregoing deterministic single-block families — automated complete; fullscreen pending

1. Add RED-first real-mod fixture coverage for Dissolution Chamber, Material Stonework Factory, and Crusher.
2. Preserve exact item/fluid inputs, live-config FE, recipe work, retained catalysts, deterministic item/fluid outputs, and atomic rollback.
3. Reject partial Stonework thresholds, ambiguous Crusher output, custom output callbacks, laser/extractor world-state families, and client-synthetic recipes.
4. Keep Industrial Foregoing/Titanium optional, run the isolated fixture in CI/release, and defer its GUI verdict to the batched F11 fullscreen review.

## Task 14: PneumaticCraft pressure and Air audit — fail-closed CI complete

1. Audit official 8.2.22 Air, pressure, heat, multiblock, and machine recipe contracts before registration.
2. Register no Air kind or recipe family because `IAirHandler` has no simulation/result contract, permits negative Air, and cannot preserve exact atomicity.
3. Load the real mod in an isolated eight-test fixture and prove Pressure Chamber, Thermo Plant, Fluid Mixer, Assembly, Refinery, Heat Frame, and Explosion remain unsupported.
4. Reconsider only after a generic retained-station-state and simulation-safe Air contract exists; never add a PneumaticCraft-only approximation.

## Task 15: Create deterministic processing families — automated complete; fullscreen pending

1. Add RED-first real-mod fixture coverage for Milling, Crushing, Cutting, Filling, and Emptying.
2. Preserve exact item/fluid inputs, crafting remainders, deterministic multi-output, checked merge, and recipe/fixed station work.
3. Reject chance, heat, RPM-dependent, multi-station, tool-durability, world-catalyst, Mechanical Crafting, and Sequenced Assembly shapes.
4. Keep Create optional, run the isolated fixture in CI/release, and defer its visible station/recipe verdict to the batched F11 review.
