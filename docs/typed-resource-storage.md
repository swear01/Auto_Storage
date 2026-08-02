# Typed Resource Storage Architecture

> Status: implemented foundation under GitHub [#9](https://github.com/swear01/Auto_Storage/issues/9). Item, fluid, NeoForge Energy, optional Mekanism chemical, Botania Mana, Ars Nouveau Source, and registered addon kinds now share one live ledger and transaction domain. Terminal listing and its resource selector, passive Bus capabilities, Creative unlimited type capacity, the public resource-kind API, and deterministic typed recipe families are connected.

Addon resource kinds and transfer strategies should be wired with the one-call
facade in [`addon-development.md`](addon-development.md); the low-level
registries below remain available for focused or advanced registration.

## Product boundary

Auto Storage owns stored resources and executes supported recipes immediately inside its server-owned transaction engine. It does not export ingredients to an external machine and wait for that machine to finish.

Items, fluids, NeoForge Energy, Mekanism chemicals, Botania Mana, and Ars Nouveau Source are the implemented kinds. They are not a closed enum. Future addons must be able to register other deterministic resources without adding another hardcoded Core field.

## Universal identity

Every stored entry has:

- a stable resource-kind ID;
- a stable resource ID inside that kind;
- exact amount-independent variant data;
- a non-negative `long` amount.

The initial internal key is deliberately independent from optional-mod Java classes. Persistence and atomic planning can therefore retain an unavailable kind as raw keyed data when its provider mod is missing. A kind-specific bridge converts between this universal identity and an `ItemStack`, `FluidStack`, energy value, `ChemicalStack`, or future addon value.

Variant data is part of identity. Item and fluid components cannot be discarded or normalized to registry defaults. Resource bridges must produce one canonical representation and must reject malformed or lossy values rather than guessing.

## Required resource kinds

### Item

- Identity: exact item registry ID plus exact data components.
- Amount: item count as `long`.
- Canonical variant NBT encodes the reconstructed one-count `ItemStack`'s `DataComponentPatch` relative to its item prototype, not the full effective `DataComponentMap`. Third-party prototypes may contain valid in-memory defaults whose codecs reject explicit serialization; writing only the patch preserves the same effective identity without serializing those defaults.
- `ItemKey` remains the exact item adapter, but live counts are stored only in the universal ledger. The old segmented item NBT is now a persistence compatibility encoding, not a second runtime map.

### Fluid

- Identity: exact fluid registry ID plus exact `FluidStack` components.
- Amount: NeoForge fluid units as `long` internally; each `IFluidHandler` call remains bounded to its `int` contract.
- External access uses NeoForge `Capabilities.FluidHandler` and honors `SIMULATE`/`EXECUTE` return values.

### Power

- Identity: the NeoForge Energy resource ID with no variant payload.
- Amount: FE as `long` internally; each `IEnergyStorage` call remains bounded to `int`.
- This pool is separate from Auto Storage's recipe process/Fuel reserves. They cannot silently convert into one another.

### Chemical

- Identity: exact Mekanism chemical registry ID.
- The persisted resource-kind ID remains `mekanism:chemical` for compatibility with 0.1.31 worlds. The built-in registry entry is exposed through a NeoForge alias from `mekanism:chemical` to `auto_storage:chemical`; both IDs are classified as the Gas view, and that view is registered only when the Mekanism provider is loaded. Do not rename either ID or silently migrate saved keys.
- Amount: chemical amount as `long`.
- The compatibility module uses `ChemicalStack` and `IChemicalHandler` only when Mekanism is present. The normal build and dedicated server must remain safe when it is absent.
- The player-facing dependency is not pinned to the one representative version exercised by CI. No Mekanism multi-version CI matrix is planned; incompatibilities outside that representative fixture are handled from user reports.

### Botania Mana

GitHub [#13](https://github.com/swear01/Auto_Storage/issues/13) now has its first safe production slice. `botania:mana` is a conditional variantless resource kind in the same long Core ledger and appears under Energy only while Botania is loaded. Held transfer currently accepts only a finite local Mana Tablet. It applies public `ManaItem.LOOKUP` to a private one-count copy, requires the exact requested delta to be observable after mutation, and then commits the cursor replacement and Core delta together. Creative/infinite tablets, Mana Mirrors bound to an external pool, and every other container whose exact local delta cannot be proven are rejected.

Mana recipe costs join the same typed transaction as items, water, catalysts, container remainders, and every output. The implemented families and exact fail-closed boundaries are documented in [`botania-compatibility.md`](botania-compatibility.md). Passive Bus transfer to Botania block receivers remains excluded: the audited `receiveMana(int)` contract has no simulation operation, so calling it during planning would violate simulate-then-commit.

### Ars Nouveau Source

`ars_nouveau:source` is a conditional variantless resource kind in the same long Core ledger and appears under Energy only while Ars Nouveau is loaded. Source Jar transfer uses Ars Nouveau's public `ISourceCap` simulation-aware `receiveSource` and `extractSource` operations, so directional Buses and recipe execution retain the shared simulate-then-commit contract. Imbuement and Enchanting Apparatus Source costs join the same atomic transaction as exact items, catalysts, remainders, station work, and outputs. See [`ars-nouveau-compatibility.md`](ars-nouveau-compatibility.md).

## Ledger and transaction rules

- A transaction is a finite map of exact resource keys to signed deltas.
- Simulation validates every delta, underflow, overflow, type-capacity transition, source, destination, and provider rule without mutation.
- Commit revalidates the same transaction and applies every kind or none.
- Failed mixed-kind transactions cannot leave an item consumed while a fluid, power, chemical, remainder, or output was not committed.
- Exact insertion/extraction APIs reject invalid amounts. Bounded capability APIs may return a smaller accepted amount only when their contract explicitly allows it.
- Every successful mutation marks the Core changed and emits listeners only after the mutation batch is complete.
- Type capacity counts every non-zero exact resource key, independent of kind. Total-capacity policy remains kind-specific and must be explicit; no implicit item-to-fluid conversion factor is allowed.

## Resource-kind registration boundary

`StorageResourceKindApi.createDeferredRegister(modId)` registers stable kind IDs in `auto_storage:resource_kind` during normal NeoForge loading. A kind declares whether exact variant payloads are legal and supplies a non-empty representative item. That representative is preserved exactly in Terminal listings. Prefer a provider's resource-specific placeholder when one exists; a generic crafting placeholder, transfer container, or workstation is not a resource identity. When no resource placeholder exists, use the clearest semantic material from that provider. Addons construct canonical `StorageResourceKey` values and may expose them through `StorageResourceCapabilities.BLOCK`; built-in helpers create and decode exact item/fluid keys and the singleton NeoForge Energy key. `StorageResourceBlockApi` and `StorageResourceContainerApi` register one deterministic bridge per kind for, respectively, adjacent block capabilities and a single held item container.

Registration does not grant Core/player mutation callbacks. `StorageResourceHandler` exposes bounded list/amount/insert/extract operations; `StorageResourceTransaction` is the only public multi-key mutation request. Auto Storage still owns validation, capacity, persistence, synchronization, and all-or-nothing commit. Missing providers remain raw on disk and are omitted from terminal presentation until their kind is registered again; new live mutations reject unregistered kinds instead of guessing or fabricating a representative.

An addon that also exposes its native sided capability from Auto Storage uses
`AutoStorageCapabilityApi.registerSidedResourceCapability(...)` inside the
facade's capability hook. The adapter receives only the public
`StorageResourceHandler` and queried `Direction`; Auto Storage registers the
wrapper on the Core and both Buses without exposing internal block entities.
The wrapper remains server-only and must preserve exact simulate/execute
semantics.

Exact client glyphs are optional presentation only. A client module may call
`TerminalResourceRendererApi.register(kindId, GuiGraphics.class, renderer)`
from client setup; the generic context keeps the common API artifact free of
client-only bytecode. Duplicate or over-limit renderers fail explicitly, and a
renderer returning `false` falls back to the registered resource-kind
representative. Registration closes before menu-screen registration; a later
call fails explicitly instead of modifying live terminal rendering. This hook
cannot read or mutate Core state.

Terminal entries carry the exact key and long amount in display-only metadata. A server-owned menu value selects one available presentation group in stable Item, Fluid, Energy, Gas, Processing, Other order, followed by an always-available All aggregate whose tooltip is `Show: All`. Energy is the broad player-facing group for FE, Mana, Source, Fuel, Brew Energy, and other recognized energy reserves. Processing contains only exact internal `station_work` keys accumulated by Processing stations. Other contains values without a better shared physical category, including the axe durability-derived **Axe Uses** reserve and independently registered addon kinds. The internal `AxeEnergy`/descriptor IDs remain stable implementation and persistence names. Both persisted and registry-canonical chemical IDs map to Gas. Missing provider-specific groups remain omitted. Player-facing entries name the stored resource, never its transfer container: Redstone represents **Forge Energy**, Mana Powder represents **Mana**, Source Gem represents **Source**, and Processing entries use `<logical family> Work`; Mana Tablet and Source Jar remain transfer containers only. Fluids use a teal frame, Energy a violet frame, Gas/Other a blue frame, and Processing an amber frame, all with opaque enough backgrounds and solid borders to remain visible. Each chemical entry keeps its exact Oxygen/Hydrogen/etc. translated name. The server/wire carrier may remain a Basic Chemical Tank, but every player-facing Terminal grid, selector, native diagram, and ledger renders EMI's exact colored chemical glyph and the exact recipe amount; neither a Tank icon nor its 64,000 capacity is a recipe unit. The selector appears on Storage and Craftable views in both terminal types, resets to Item, and is hidden/rejected on Transform and Stations. The All aggregate keeps Item entries as ordinary item display stacks, including normal `#tag` search, Storage withdrawal, and Crafting selection; it does not wrap Item keys as typed representatives. Clicking a non-item representative in Storage never extracts its icon or selects an item recipe; clicking one in Craftable may select the exact typed-output recipe represented by that key. Processing `@mod` search uses the representative workstation item namespace rather than the internal `auto_storage:station_work` resource ID. EMI excludes every non-item representative from item inputs. A player may deposit into or withdraw from a supported fluid, FE, chemical, or addon container held on the cursor; the server rejects spectator, stale, wrong-view, hidden-slot, out-of-range, and otherwise invalid-menu packets before mutation, plans against a private one-item copy, simulates Core capacity, then atomically commits the Core delta and exact replacement container. Cursor/inventory placement and the Core delta share one mutation batch, so listeners cannot observe a half-committed transfer. It never stores storage state on the client.

Typed recipe selection preserves the exact primary `StorageResourceKey` and exact positive long amount per craft. If a deterministic plan has an item primary, that item remains its selectable identity. Otherwise it must have exactly one non-item primary. A non-item selected primary is Storage-only: the direct terminal control displays Storage and is disabled for that selection without overwriting the saved item-output preference; explicit Storage requests commit to the Core, while Cursor, Player, Inventory, and EMI inventory requests reject before mutation. Primary and remainder roles are kept separate through checked multiplication and final transaction construction, including mixed item/non-item outputs and `Long.MAX_VALUE` overflow checks.

Directional Import and Export Buses scan the front block for both the generic addon capability and every matching native fluid/FE/chemical endpoint; a block exposing generic Mana or Source and native fluid at the same time therefore transfers both rather than letting the generic endpoint mask the native one. Passive Import is insert-only. Directionless Export exposes filtered extract-only generic/native capabilities plus a safe one-slot item view capped to a normal stack. Generic typed capability paths reject the Item kind so item automation cannot bypass `IItemHandler` stack limits. Existing item rules are not reinterpreted as typed filters: non-item resources pass under empty `DENY`, while item-only `ALLOW` rules match no typed resource. Creative Storage makes the same shared type domain unlimited for every kind.

### Public handler and strategy contract

Addon implementations are part of the transaction boundary and must obey all of these rules:

- `StorageResourceHandler.getStoredResources()` returns a non-null, finite snapshot of unique exact keys. Its order must be deterministic; the list and its keys must not change after return.
- `getAmount(key)` returns a non-negative exact amount for that key and `0` for an unsupported key.
- `insert` and `extract` accept only positive requests, return a value in `0..requested`, and return `0` for a wrong kind. `simulate=true` must not mutate any block, item, capability, cache, or listener-visible state. `simulate=false` mutates exactly the returned amount and never more.
- A handler must remain internally consistent for one call, but Auto Storage still revalidates mutable Core, topology, side, mode, filter, security, and capacity state between simulation and commit. A dishonest handler that mutates during simulation or reports an amount different from its mutation violates the addon API; Auto Storage does not guess how to repair an unknowable external state.
- `StorageResourceBlockStrategy.find(level, pos, side)` must honor the exact queried side, must not load a chunk, and returns empty when that kind is unavailable. Generic and native endpoints may coexist and are visited in stable registration/kind order. Re-resolving the same live capability during one simulate-to-commit attempt must return an endpoint whose handler is identity-stable or equality-stable; a strategy that creates unequal throwaway wrappers for the same unchanged endpoint violates the revalidation contract and is rejected as stale.
- `StorageResourceContainerStrategy` receives a private one-count `ItemStack` copy. It must return a positive exact amount, the same resource kind it registered, and zero or one replacement container. Planning may mutate only that private copy. Auto Storage makes another private copy for every strategy so one rejected strategy cannot poison the next.
- Optional-mod bridges must not link optional classes when the mod is absent. A present but binary-incompatible mod fails explicitly with a `binary-incompatible` error, including a `LinkageError` wrapped by reflective invocation; it is never silently disabled or replaced by another API. Mekanism `IChemicalHandler.isValid(tank, stack)` reports schema/type validity independently of the tank's current contents or capacity. The indexed execute-insert path separately enforces an already-filled tank's exact chemical identity; the generic insertion overload is the path that may select a tank for a new stored chemical type.

Capability objects are stable while their configured side/mode surface remains available. A cached wrapper rechecks the current BlockEntity identity, escrow state, mode, side, automation gate, loaded Core path, conflict state, filter, and actor on every call. A temporarily missing or disconnected Core therefore makes the existing wrapper inert rather than exposing stale storage; configured side/mode changes invalidate the NeoForge capability so discovery can obtain the new surface.

### Bus rollback and escrow

All item and typed Bus operations use simulate-then-commit and a structured `BusActor`. One directional cooldown independently attempts at most one ordinary item stack and one bounded typed-resource transfer; a successful item move does not starve fluid, power, chemical, or addon resources exposed by the same block. Recursive same-network or inverse re-entry is rejected by a `try/finally` execution guard. Ordinary items use `BusItemTransferPlan`; typed resources use the corresponding typed immutable plan. Both capture the exact Bus/config revision, Core/network identity, conflict state, topology revision, loaded path, endpoint block state, queried side, endpoint identity, and actor, then revalidate before commit and again after every external execute callback that can mutate the world. Import never restores into a source handler that was detached during execute; every item or typed amount already extracted is preserved through the current Bus escrow/recovery path. Export first reverse-extracts a replaced target's reversible accepted amount and returns it to Core; if the detached target refuses reclaim, the exact unreclaimed item or typed amount is emitted through the recovery path instead of being treated as delivered. A still-identical endpoint does not duplicate an accepted amount merely because another part of the plan became stale. Any remainder that Core cannot immediately restore is likewise preserved. Both Import and Export use the same ten-tick missing-Core negative cache. While escrow is pending, cached and fresh passive capabilities are inert. The Bus drains escrow back to the same Core with the structured Bus actor before any later automation, even when automation is disabled or the Bus is directionless.

Escrow is saved in the Bus BlockEntity and owner-stripped Bus drop. Each live Bus has a distinct recovery UUID that is copied into its drop solely for exact deduplication; two removals at the same position with identical escrow remain two operations, while loot-first plus `onRemove` for one Bus remains one drop. Wrench removal, normal breaking, Creative no-drop removal, explosions, direct replacement with air or another block, and failed removal all recover to the Core first or preserve the exact escrow in one recovery drop. A highest-priority global `BlockDropsEvent` handler removes an escrow-bearing Bus from the mutable event list and directly conserves it before later listeners can consume or cancel the drop. Emergency conservation is therefore independent of event cancellation and `doTileDrops`; ordinary Bus hardware still follows the final `BlockDropsEvent` result, so cancellation or replacement is not silently undone. An operation that cannot conserve escrow refuses removal. A stale execute callback must never write into a detached/replaced BlockEntity: if the original Bus no longer owns the position, the remainder becomes an owner-stripped recovery drop. Legacy owner claim occurs only after this conservation preflight succeeds, and no-op rotation never claims, so failed or ineffective Wrench actions cannot change ownership or revision. Unknown-kind ledger entries and malformed future escrow payloads are retained raw and keep the Bus disabled instead of being deleted. Escrow insertion is atomic at `long` overflow.

## Recipe-family relationship

`RecipeFamilyFactories.deterministicResources(...)` resolves a `TypedRecipePlan` from the current exact recipe holder and server registries. A plan declares one to 81 exact inputs, multiple exact outputs, a selectable primary item output, layout, and explicit roles. Inputs may have ordered exact alternatives; overlapping `CONSUME` alternatives use one deterministic max-flow allocation, and each chosen alternative may return its own exact remainder. `CONSUME` multiplies by craft count; `CATALYST` and `TOOL` are retained and reusable across a batch; `PRIMARY` item outputs follow the selected Player/Storage destination, while non-item outputs and typed remainders return to Core. Checked multiplication, descriptor station work/Fuel, destination capacity, every consumed item/non-item, and every Core output are simulated and committed as one ledger transaction.

Current optional recipe bridges cover Farmer's Delight, Mekanism, Botania, Modern Industrialization, Ars Nouveau, EvilCraft, Powah, Industrial Foregoing, and Create. Their exact family lists and fail-closed boundaries are maintained in the corresponding compatibility documents. EvilCraft Blood uses the existing Fluid kind rather than introducing another resource kind. Powah Energizing consumes the existing NeoForge Energy kind rather than introducing a Powah-specific power key. Industrial Foregoing reuses the existing Fluid and NeoForge Energy kinds; it does not create machine-specific resource keys. Create reuses Item and Fluid and does not invent RPM/stress/FE resources. PneumaticCraft Air is not registered because its handler has no simulation/result contract, permits negative values, and derives pressure from mutable volume. Draconic Evolution Fusion Crafting is not registered because it depends on live multiblock injectors, injector-local energy charging, and config-timed craft duration. Productive Bees adds no typed kind because all 16 actual recipe candidates remain fail closed on chance, live entity/world, dynamic component, or machine-state semantics. Every accepted plan uses the same typed transaction and preserves its provider's exact input, output, catalyst, remainder, work, and resource-unit contracts.

Chance, dynamic world/player callbacks, arbitrary mutation callbacks, and external-machine send-and-wait remain unsupported. Multi-step graph planning is still future work under GitHub [#1](https://github.com/swear01/Auto_Storage/issues/1).

## Delivery phases

1. **Implemented:** universal key, delta, ledger, persistence, simulation, and atomic commit algebra.
2. **Implemented:** item live-ledger bridge with exact components and legacy segmented-NBT compatibility.
3. **Implemented:** fluid storage plus `IFluidHandler` capability and transfer tests.
4. **Implemented:** NeoForge Energy storage plus `IEnergyStorage` capability and separation tests.
5. **Implemented foundation:** optional Mekanism chemical capability, absent-mod base run, and one representative present-mod GameTest fixture. This is intentionally not a multi-version matrix; other-version incompatibilities are fixed from user reports rather than turning CI into a version guarantee.
6. **Implemented:** typed terminal listings and held-container transfer, generic/native active and passive Bus automation, Creative unlimited type capacity, and addon resource-kind/block/container APIs.
7. **Implemented:** deterministic N-input/N-output typed recipe families. **Future:** bounded multi-step planning.

The current ledger is the Core's only live item/resource transaction domain. Persistence may still encode resolvable items in legacy segmented form so existing worlds remain readable, but load immediately migrates them into the same ledger and rejects duplicate dual representation.

## Verification gates

Every phase starts RED-first. The final phase gate remains:

```bash
./gradlew build
./gradlew runGameTestServer
./gradlew runRecipeAddonGameTestServer
./gradlew runMekanismGameTestServer
./gradlew runBotaniaGameTestServer
./gradlew runIronFurnacesGameTestServer
./gradlew runFarmersDelightGameTestServer
./gradlew runModernIndustrializationGameTestServer
./gradlew runArsNouveauGameTestServer
./gradlew runEvilCraftGameTestServer
./gradlew runPowahGameTestServer
./gradlew runIndustrialForegoingGameTestServer
./gradlew runCreateGameTestServer
./gradlew runPneumaticCraftGameTestServer
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts
./gradlew runData
```

GUI-visible resource work additionally requires the fullscreen Prism manual gate. Backend ledger work does not claim visual verification.

## API sources

- [NeoForge 1.21.1 `Capabilities`](https://github.com/neoforged/NeoForge/blob/1.21.x/src/main/java/net/neoforged/neoforge/capabilities/Capabilities.java)
- [NeoForge `IFluidHandler`](https://github.com/neoforged/NeoForge/blob/1.21.x/src/main/java/net/neoforged/neoforge/fluids/capability/IFluidHandler.java)
- [NeoForge `IEnergyStorage`](https://github.com/neoforged/NeoForge/blob/1.21.x/src/main/java/net/neoforged/neoforge/energy/IEnergyStorage.java)
- [Mekanism 1.21.1 `ChemicalStack`](https://github.com/mekanism/Mekanism/blob/v1.21.1-10.7.19.85/src/api/java/mekanism/api/chemical/ChemicalStack.java)
- [Mekanism 1.21.1 `IChemicalHandler`](https://github.com/mekanism/Mekanism/blob/v1.21.1-10.7.19.85/src/api/java/mekanism/api/chemical/IChemicalHandler.java)
