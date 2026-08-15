# Transform Page Target List and Resource Scope Plan

> Status: design. User feedback from ATM10 dogfood: the Transform page's
> transformable set feels limited; core resources (Mana, FE, energy)
> should be pinned at the top of the target sidebar; resource kinds like
> Pressure should be handled if possible.

## Current behavior (verified)

`TransformProviderApi.targets(descriptors)` builds the target sidebar in
this order:

1. Non-machine-generated `EnergyType` targets: `auto_storage:furnace_fuel`,
   `auto_storage:blaze_fuel` (the FuelTable fuel pools).
2. `TRANSFORM`-category machine descriptors (currently none).
3. Every registered provider's `targetId`, sorted by provider registry
   key: `auto_storage:neoforge_energy` (all FE generators),
   `auto_storage:botania_mana` (Thermalily/Endoflame),
   `auto_storage:chemical` (Mekanism conversion shapes).

With #121 the sidebar is input-first: when the input slot holds an item,
only targets that have a use for that exact input remain.

Observed limits:

- Core resources are not pinned: furnace_fuel/blaze_fuel come before
  FE/Mana, and the provider targets are registry-key ordered, so
  `chemical` can appear before `neoforge_energy`.
- Every FE generator collapses into one `neoforge_energy` target; the
  sidebar gives no per-station breakdown (the use cards do).
- Only three produced resource kinds exist as transform targets today:
  FE, Botania Mana, Mekanism chemical.

## Goals

1. Pin core universal resources (FE, Fuel, Mana, Brew, Axe) at the top of
   the target sidebar in a fixed, stable order; mod-specific produced
   kinds (chemical, addon kinds) follow, still sorted.
2. Make the sidebar informative in browse mode: show each target's
   available-use count and, for station-backed targets, whether a station
   is installed.
3. Keep the input-first filter (#121) exactly as-is; the pinned order
   applies within both browse and input-first modes.
4. Decide the resource-kind scope explicitly: which produced kinds can
   exist as transform targets, and document why Pressure/Source/Blood
   cannot (or can) be added.

## Design

### A. Target ordering (menu + screen)

- Introduce `TransformTargetPriority` on the Target record: CORE
  (universal resources) and MOD (module-produced kinds).
- CORE order is fixed: FE (`neoforge_energy`), FURNACE_FUEL, BLAZE_FUEL,
  MANA (`botania_mana`), then any future universal kinds (Brew, Axe).
- MOD targets sort after CORE by label.
- `targets()` emits CORE first, then MOD; input-first filtering (#121)
  preserves that order.
- Screen: keep the existing paged list; the pinned group renders
  identically (no visual split needed) but is never pushed below the fold
  by MOD targets.

### B. Browse-mode informativeness

- In browse mode (empty input) each target option gains a badge line with
  the count of uses available for that target and, for station-backed
  targets, installed-station state (from the descriptor snapshot).
- Input-first mode keeps the current compact listing.

### C. Resource-kind scope

| Kind | Transform target today | Feasibility |
|---|---|---|
| FE (`neoforge_energy`) | yes (all generators) | yes |
| Mana (`botania_mana`) | yes (Thermalily/Endoflame) | yes |
| Chemical (`mekanism:chemical`) | yes (conversion shapes) | yes |
| Fuel pools (furnace/blaze) | yes (FuelTable) | yes |
| Source (Ars Nouveau) | no | no item→Source conversion machine exists; Source is only a recipe input/output today. Adding a target requires a real one-way item→Source source (user-provided pattern). |
| Blood (EvilCraft) | no | no item→Blood machine (Blood Infuser consumes Blood); same rule. |
| Pressure / Air (PneumaticCraft) | no | NOT feasible: PneumaticCraft Air has no simulate/accepted amount contract, can be negative, and pressure/heat are live retained machine state (audit in docs/pneumaticcraft-compatibility.md). A transform target must be a storable typed resource with exact amounts. |
| Heat (Mekanism) | no | not storable (heat-differential, #102). |

Rule: a produced kind becomes a Transform target only when a real,
one-way, deterministic item→resource conversion exists (module pattern or
automatic discovery). Adding placeholder targets for kinds with no
conversion source is rejected — it would show dead targets.

### D. Input breadth

- The input-first design already means "place any item, see its
  conversions". The perceived limit comes from the number of conversion
  sources, not the UI. Expansion paths:
  - More conversion patterns (user-owned, #116 pattern list).
  - Automatic discovery already covers Mekanism API conversion shapes.
  - Built-in FuelTable covers vanilla fuel pools.
- No UI change is needed for breadth; the sidebar count badge (B) makes
  the available set visible.

## Out of scope

- New produced kinds without a real conversion source (Pressure, Source,
  Blood placeholders are rejected).
- Changing the atomic transaction or station-work model.
- Multi-output targets (one target = one produced kind).

## Implementation steps

1. ~~Target record gains priority + order; `targets()` and
   `getTransformTargetsForInput()` sort CORE-first.~~ DONE — CORE order
   (FE, furnace_fuel, blaze_fuel, Mana) pinned first via
   `CORE_TARGET_ORDER`, MOD kinds follow sorted by label; fixture asserts
   no non-core target precedes FE.
2. Menu exposes per-target use counts (browse mode) from `uses()`.
3. Screen renders the count badge; fixture asserts ordering + badge.
4. Docs: this plan, overview Transform paragraph, notes.
