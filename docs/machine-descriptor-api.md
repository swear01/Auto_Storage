# Machine Descriptor API

Magic Storage exposes a NeoForge custom registry at `magic_storage:machine_descriptor` for addon-owned station and Transform descriptors. Registration is performed during the normal mod registry lifecycle; descriptor order, persistence, deterministic conversion, and live values remain server-owned.

## Register from an addon

```java
public static final ResourceLocation COPPER_PRESS_ID =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "copper_press");

public static final DeferredRegister<MachineDescriptor> MACHINE_DESCRIPTORS =
        MachineDescriptorApi.createDeferredRegister(MOD_ID);

public static final DeferredHolder<MachineDescriptor, MachineDescriptor> COPPER_PRESS =
        MACHINE_DESCRIPTORS.register("copper_press", () -> MachineDescriptor.installable(
                COPPER_PRESS_ID,
                new ItemStack(ModItems.COPPER_PRESS.get()),
                Ingredient.of(ModItems.COPPER_PRESS.get()),
                MachineEnergyTable.Category.PROCESS,
                MachineDescriptorApi.MAX_INSTALLED_COUNT,
                EnergyType.SMELTING_ENERGY,
                2));

public AddonMod(IEventBus modBus) {
    MACHINE_DESCRIPTORS.register(modBus);
}
```

The registry key and `MachineDescriptor.id()` must be identical. IDs are persistent data keys and must never be reused for a different machine. All installed descriptors share a fixed bank of 256 menu/Core slots; Magic Storage refuses to start if the combined registry exceeds that limit.

## Descriptor kinds

- `PROCESS`: installable aggregate, maximum 1–`Integer.MAX_VALUE` (2,147,483,647). It may generate one existing `EnergyType`, or descriptor-keyed station work when `energyType` is `null`.
- `INSTANT`: installable stack, normally maximum 1. It unlocks an adapter-defined action without generating energy.
- `CONSUMABLE`: transient input. Its `ConsumableValue` callback runs only in the server-owned Core mutation path and returns a finite `long` amount or explicit infinity.

Example consumable:

```java
MachineDescriptor.consumable(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "charged_crystal"),
        new ItemStack(ModItems.CHARGED_CRYSTAL.get()),
        Ingredient.of(ModItems.CHARGED_CRYSTAL.get()),
        stack -> new MachineDescriptor.ConsumableAmount(1_000L * stack.getCount(), false));
```

The callback must be deterministic from the supplied stack and must not mutate the world, player, or stack. Returning a negative value, or a nonzero amount together with `infinite=true`, is rejected.

## Transform providers and shared targets

Use `TransformProviderApi.register(...)` when an addon conversion needs a typed output and optional station-work cost instead of a `MachineDescriptor.CONSUMABLE` value:

```java
TransformProviderApi.register(
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "generator_recipe"),
        StorageResourceKindApi.ENERGY_KIND,
        new ItemStack(Items.REDSTONE),
        Component.translatable("gui.magic_storage.resource_view.energy"),
        input -> resolveGeneratorUse(input));
```

The first ID identifies one selectable conversion card. It must be unique and stable. `targetId` identifies the produced target shown in the persistent Transform sidebar; multiple provider cards may share one target. `targetLabel` names that produced resource independently of the representative icon. The resolver receives a one-count copy of the exact inserted item and returns either no use or a positive typed output plus an optional matching station ID/work cost. It must be deterministic and side-effect free.

Auto lists every compatible exact-input use without selecting or executing one. An explicit target filters those same uses by `targetId`; it is not a global recipe catalog. The server validates the visible card index, stable use ID, current exact input, output capacity, and station work again before simulate-then-commit.

## Polymorphic station variants

Use one logical descriptor when several concrete blocks satisfy the same recipe station. Each `MachineVariant` has one exact item identity and a normalized rational `MachineWorkRate`; variants in the same slot may run at different rates.

```java
MachineDescriptor.installableVariants(
        COPPER_PRESS_ID,
        () -> List.of(
                MachineVariant.of(
                        new ItemStack(ModItems.COPPER_PRESS.get()),
                        MachineWorkRate.ONE),
                MachineVariant.derived(
                        new ItemStack(ModItems.REINFORCED_PRESS.get()),
                        () -> MachineWorkRate.of(200, AddonConfig.pressTicks.get()))),
        MachineEnergyTable.Category.PROCESS,
        64,
        null);
```

The supplier is materialized after registry/config loading and synchronized as values; the client never executes it. Capturing a config holder is allowed, but reading its value during a DeferredRegister callback is not. Empty lists, more than 64 concrete variants, duplicate items, zero-rate PROCESS variants, or nonzero INSTANT rates fail explicitly. The 64-variant limit is independent of the installed aggregate count. Work generation keeps an exact fractional remainder per descriptor plus installed item/rate. Changing the concrete item or live configured rate discards only the incompatible remainder before applying the new rate.

The recipe preview always starts with the actually installed variant, then cycles the remaining synchronized variants in descriptor order every 1,000 milliseconds. This index is display-only. Station availability, accumulated work, rate, recipe execution, and persistence remain server-owned.

## Server authority and compatibility

- The server freezes and caches the ordered registry snapshot after registration, then writes that exact snapshot into every Crafting or Remote Terminal menu payload. Core ticks reuse the same immutable list instead of rebuilding and sorting it.
- Installable aggregates, descriptor-keyed station work, and fractional remainder persist by descriptor `ResourceLocation`, never by ordinal. Machine NBT stores a one-count item prototype plus a separate `long count`; this avoids vanilla 1.21.1's bounded disk `ItemStack` codec while the live aggregate remains an exact non-negative `int`. If a descriptor is missing, changes category/ingredient, exceeds its current maximum, or its addon item is unavailable, the original machine/work-entry NBT remains in the server-owned Core storage record and is retried on later loads. Legacy machine entries without the separate count still load from their stack count. Raw ordinary-inventory entries are preserved the same way; this early-development repository format does not migrate either case into another slot or silently delete it.
- Transform amounts persist by descriptor ID. Live finite/infinite state is sent through `MachineDescriptorStatePacket`; it is not inferred from client inventory.
- Menu slot count always remains fixed at 256, so adding or removing addon descriptors cannot produce client/server container-index drift.
- This is registry-time addon registration, not runtime hot registration after NeoForge registries freeze. Any representative or accepted addon item must still exist on the client like other player-visible mod content.

Registering a descriptor only adds its installation or Transform behavior and terminal presentation. A recipe from any namespace that resolves to one of Magic Storage's exact supported vanilla recipe classes automatically reuses that family's built-in station descriptor; no per-recipe or per-mod station registration is needed, although the player must still install the corresponding station in the system. Descriptors and recipe families are registered once per station/family, not once per recipe ID.

Current built-in optional integrations use this boundary in four ways: Iron Furnaces contributes supported furnace blocks and derives `200 / configuredCookTicks` live rates; Farmer's Delight registers one Cooking Pot descriptor/family; Mekanism registers all nine factory-backed families—Smelting, Enriching, Crushing, Compressing, Combining, Purifying, Injecting, Infusing, and Sawing—plus Pressurized Reaction and twelve deterministic single-block fluid/chemical station descriptors; Botania registers Mana Pool, Runic Altar, Terrestrial Agglomeration Plate, Petal Apothecary, and Elven Gateway as max-one instant stations. Each factory-backed Mekanism descriptor accepts its basic machine and Basic/Advanced/Elite/Ultimate Factory at the loaded tier's 1/3/5/7/9 parallel-process rate. Representative optional-mod CI artifacts are evidence only and are not player-facing version pins.

On the Stations page, Shift+left-click routes an accepted machine to its exact descriptor even when that group's local page is showing another descriptor. Installation fills only the remaining aggregate capacity; a near-cap Shift-click that reaches `Integer.MAX_VALUE` keeps the exact unaccepted remainder in the source stack without overflow or loss. Removal presents the oversized aggregate to vanilla player slots in ordinary item-stack-sized chunks, so partial player stacks cannot overflow vanilla's `int` merge sum; an inventory-full remainder stays installed. Processing and Instant groups each keep wheel paging and previous/next buttons. Hovering a processing station's amount shows the installed machine and total work generated per tick. Station search replaces both groups with one unified paged result grid of stations while preserving one logical descriptor/slot identity.

Custom exact classes/types use the public NeoForge `magic_storage:recipe_family` registry and bounded deterministic factories described in [`recipe-family-api.md`](recipe-family-api.md). `singleItemToItem` and `deterministicResources` map a complete family to a descriptor without exposing Core/player mutation authority. Client EMI state is never authoritative. External-machine processing patterns and asynchronous send-and-wait orchestration remain outside the product.
