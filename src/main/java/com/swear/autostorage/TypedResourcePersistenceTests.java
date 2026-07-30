package com.swear.autostorage;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(AutoStorage.MODID)
@PrefixGameTestTemplate(false)
public final class TypedResourcePersistenceTests {
    private TypedResourcePersistenceTests() {
    }

    @GameTest(template = "behavioraltests.platform")
    public static void legacy_work_balances_migrate_once_into_the_typed_ledger(
            GameTestHelper helper
    ) {
        CoreStorageRecord original = CoreStorageRecord.fresh(UUID.randomUUID());
        CompoundTag legacy = original.save(helper.getLevel().registryAccess());
        legacy.remove(CoreStorageRecord.TAG_RESOURCE_LEDGER);
        legacy.getCompound(CoreStorageRecord.TAG_ENERGY)
                .putLong(EnergyType.FURNACE_FUEL.getId(), 3_200);
        CompoundTag axe = new CompoundTag();
        axe.putString(CoreStorageRecord.TAG_DESCRIPTOR_ID, MachineEnergyTable.AXE_ID.toString());
        axe.putLong(CoreStorageRecord.TAG_AMOUNT, 17);
        axe.putBoolean(CoreStorageRecord.TAG_INFINITE, false);
        legacy.getList(CoreStorageRecord.TAG_DESCRIPTOR_CONSUMABLES, Tag.TAG_COMPOUND).add(axe);

        CoreStorageRecord.LoadResult migrated = CoreStorageRecord.load(
                legacy, helper.getLevel().registryAccess());
        if (!migrated.success()
                || migrated.record().resourceLedger().amount(
                StorageResourceBridge.energyKey(EnergyType.FURNACE_FUEL)) != 3_200
                || migrated.record().resourceLedger().amount(
                StorageResourceBridge.descriptorKey(MachineEnergyTable.AXE_ID)) != 17) {
            helper.fail("Legacy Fuel/Axe balances did not migrate into the typed ledger");
            return;
        }

        CompoundTag saved = migrated.record().save(helper.getLevel().registryAccess());
        if (saved.getCompound(CoreStorageRecord.TAG_ENERGY)
                .getLong(EnergyType.FURNACE_FUEL.getId()) != 0
                || !saved.getList(
                CoreStorageRecord.TAG_DESCRIPTOR_CONSUMABLES, Tag.TAG_COMPOUND).isEmpty()) {
            helper.fail("Migrated work balances remained duplicated in legacy persistence");
            return;
        }
        CoreStorageRecord.LoadResult reloaded = CoreStorageRecord.load(
                saved, helper.getLevel().registryAccess());
        if (!reloaded.success()
                || reloaded.record().resourceLedger().amount(
                StorageResourceBridge.energyKey(EnergyType.FURNACE_FUEL)) != 3_200
                || reloaded.record().resourceLedger().amount(
                StorageResourceBridge.descriptorKey(MachineEnergyTable.AXE_ID)) != 17) {
            helper.fail("Typed work migration was not idempotent");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void storage_energy_view_lists_fuel_as_a_typed_resource(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            ItemStack coal = new ItemStack(Items.COAL);
            if (!core.addFuel(coal, EnergyType.FURNACE_FUEL)) {
                helper.fail("Could not seed typed Fuel");
                return;
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(910, player.getInventory(), core);
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            ItemStack display = menu.getSlot(0).getItem();
            if (menu.getResourceView() != TerminalResourceView.ENERGY
                    || menu.getTotalItemTypes() != 1
                    || TerminalDisplayStack.amount(display) != 1_600
                    || !TerminalResourceDisplay.key(display).orElseThrow().equals(
                    StorageResourceBridge.energyKey(EnergyType.FURNACE_FUEL))) {
                helper.fail("Energy view did not list the exact typed Fuel balance");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void typed_item_output_rejects_a_different_presentation_item(
            GameTestHelper helper
    ) {
        StorageResourceKey diamond = StorageResourceKey.of(
                StorageResourceKindApi.ITEM_KIND,
                BuiltInRegistries.ITEM.getKey(Items.DIAMOND),
                new CompoundTag());
        try {
            TypedRecipePlan.builder()
                    .input(TypedRecipeInput.consume(StorageResourceKey.neoforgeEnergy(), 1))
                    .output(TypedRecipeOutput.primary(diamond, 1))
                    .presentationOutput(new ItemStack(Items.EMERALD))
                    .layout(1, 1, true)
                    .build();
        } catch (IllegalArgumentException expected) {
            helper.succeed();
            return;
        }
        helper.fail("Typed item output accepted a different presentation item identity");
    }

    @GameTest(template = "behavioraltests.platform")
    public static void schema_two_unregistered_resource_filter_stays_unavailable_and_round_trips_raw_nbt(
            GameTestHelper helper
    ) {
        ResourceLocation missingKind = ResourceLocation.fromNamespaceAndPath(
                "missing_provider", "mana");
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(
                "missing_provider", "blue");
        if (AutoStorage.RESOURCE_KIND_REGISTRY.get(missingKind) != null) {
            helper.fail("Test resource kind unexpectedly exists: " + missingKind);
            return;
        }

        CompoundTag variant = new CompoundTag();
        variant.putString("grade", "luminous");
        CompoundTag nestedVariant = new CompoundTag();
        nestedVariant.putLong("charge", 9_223_372_036L);
        variant.put("properties", nestedVariant);

        CompoundTag resource = new CompoundTag();
        resource.putString("kind", missingKind.toString());
        resource.putString("resourceId", resourceId.toString());
        resource.put("variant", variant.copy());
        CompoundTag rawRule = new CompoundTag();
        rawRule.putString("type", "resource");
        rawRule.put("resource", resource);
        ListTag filterRules = new ListTag();
        filterRules.add(rawRule.copy());

        CompoundTag schemaTwo = new CompoundTag();
        schemaTwo.putInt("schema", 2);
        schemaTwo.putString("mode", "directional");
        schemaTwo.putInt("sideMask", BusConfiguration.ALL_SIDES_MASK);
        schemaTwo.putBoolean("unsidedAccess", false);
        schemaTwo.putBoolean("automationEnabled", true);
        schemaTwo.putString("filterMode", "allow");
        schemaTwo.put("filterRules", filterRules);
        schemaTwo.putLong("configRevision", 17);
        CompoundTag root = new CompoundTag();
        root.put(BusConfiguration.TAG_BUS_CONFIG, schemaTwo);

        BusConfiguration loaded = BusConfiguration.load(
                root, BusKind.EXPORT, helper.getLevel().registryAccess());
        if (!loaded.supported() || loaded.schema() != 2 || loaded.filterRules().size() != 1) {
            helper.fail("Schema-two Bus config with a missing resource kind did not load");
            return;
        }
        BusFilterRule loadedRule = loaded.filterRules().getFirst();
        if (loadedRule.type() != BusFilterRule.Type.UNAVAILABLE
                || loadedRule.available()
                || loadedRule.resourceKey().isPresent()) {
            helper.fail("Missing resource kind did not become an unavailable filter rule");
            return;
        }

        StorageResourceKey missingKey = StorageResourceKey.of(
                missingKind, resourceId, variant);
        StorageResourceKey knownKey = StorageResourceBridge.fluidKey(
                new FluidStack(Fluids.WATER, 1), helper.getLevel().registryAccess());
        BusFilterPolicy policy = BusFilterPolicy.compile(
                loaded, helper.getLevel().registryAccess());
        if (policy.allows(missingKey)
                || policy.allows(knownKey)
                || !policy.orderedResourceCandidates(
                        java.util.List.of(missingKey, knownKey)).isEmpty()) {
            helper.fail("Unavailable resource filter matched a StorageResourceKey");
            return;
        }

        CompoundTag savedRoot = new CompoundTag();
        loaded.save(savedRoot, helper.getLevel().registryAccess());
        ListTag savedRules = savedRoot.getCompound(BusConfiguration.TAG_BUS_CONFIG)
                .getList("filterRules", Tag.TAG_COMPOUND);
        if (savedRules.size() != 1 || !savedRules.getCompound(0).equals(rawRule)) {
            helper.fail("Unavailable resource filter did not preserve its raw NBT");
            return;
        }
        CompoundTag savedResource = savedRules.getCompound(0).getCompound("resource");
        if (!savedResource.getString("kind").equals(missingKind.toString())
                || !savedResource.getString("resourceId").equals(resourceId.toString())
                || !savedResource.getCompound("variant").equals(variant)) {
            helper.fail("Unavailable resource kind, resource ID, or variant did not round-trip");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void core_record_persists_resources_and_fails_closed_on_corruption(
            GameTestHelper helper
    ) {
        CoreStorageRecord original = CoreStorageRecord.fresh(UUID.randomUUID());
        StorageResourceKey fluid = StorageResourceBridge.fluidKey(
                new FluidStack(Fluids.WATER, 1), helper.getLevel().registryAccess());
        original.resourceLedger().insert(
                fluid, 4_000, StorageTypeCapacity.unlimitedCapacity(), Action.EXECUTE);
        original.resourceLedger().insert(
                StorageResourceBridge.ENERGY_KEY,
                9_000,
                StorageTypeCapacity.unlimitedCapacity(),
                Action.EXECUTE);

        CompoundTag saved = original.save(helper.getLevel().registryAccess());
        CoreStorageRecord.LoadResult loaded = CoreStorageRecord.load(
                saved, helper.getLevel().registryAccess());
        if (!loaded.success()
                || loaded.record().resourceLedger().amount(fluid) != 4_000
                || loaded.record().resourceLedger().amount(StorageResourceBridge.ENERGY_KEY) != 9_000) {
            helper.fail("Core record did not preserve fluid and power ledger entries");
            return;
        }

        CompoundTag previousSchema = saved.copy();
        previousSchema.remove(CoreStorageRecord.TAG_RESOURCE_LEDGER);
        CoreStorageRecord.LoadResult previousLoaded = CoreStorageRecord.load(
                previousSchema, helper.getLevel().registryAccess());
        if (!previousLoaded.success() || !previousLoaded.record().resourceLedger().isEmpty()) {
            helper.fail("Previous item-only Core record did not load with an empty typed ledger");
            return;
        }

        CompoundTag corrupt = saved.copy();
        CompoundTag ledger = corrupt.getCompound(CoreStorageRecord.TAG_RESOURCE_LEDGER);
        ledger.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putLong("amount", -1);
        CoreStorageRecord.LoadResult corruptLoaded = CoreStorageRecord.load(
                corrupt, helper.getLevel().registryAccess());
        if (corruptLoaded.success() || !corruptLoaded.raw().equals(corrupt)) {
            helper.fail("Corrupt typed ledger did not fail closed with raw record preservation");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void core_record_persists_maximum_installed_machine_count(
            GameTestHelper helper
    ) {
        CoreStorageRecord original = CoreStorageRecord.fresh(UUID.randomUUID());
        int machineSlot = MachineEnergyTable.FURNACE_SLOT;
        original.machines().setItem(
                machineSlot, new ItemStack(Items.FURNACE, Integer.MAX_VALUE));
        if (original.machines().getItem(machineSlot).getCount() != Integer.MAX_VALUE) {
            helper.fail("Core machine container did not accept Integer.MAX_VALUE installed machines");
            return;
        }

        CompoundTag saved;
        try {
            saved = original.save(helper.getLevel().registryAccess());
        } catch (RuntimeException exception) {
            helper.fail("Core machine persistence used the vanilla 99-count ItemStack codec: "
                    + exception.getMessage());
            return;
        }
        CoreStorageRecord.LoadResult loaded = CoreStorageRecord.load(
                saved, helper.getLevel().registryAccess());
        ItemStack restored = loaded.success()
                ? loaded.record().machines().getItem(machineSlot)
                : ItemStack.EMPTY;
        if (!restored.is(Items.FURNACE) || restored.getCount() != Integer.MAX_VALUE) {
            helper.fail("Core record did not restore Integer.MAX_VALUE installed Furnaces: "
                    + restored + ", load=" + loaded.error());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void items_share_the_live_typed_ledger_and_mixed_transactions_are_atomic(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            ItemKey logs = ItemKey.of(new ItemStack(Items.OAK_LOG));
            ItemKey charcoal = ItemKey.of(new ItemStack(Items.CHARCOAL));
            StorageResourceKey logResource = StorageResourceBridge.itemKey(
                    logs, level.registryAccess());
            StorageResourceKey charcoalResource = StorageResourceBridge.itemKey(
                    charcoal, level.registryAccess());
            StorageResourceKey water = StorageResourceBridge.fluidKey(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());

            if (core.insertItemCount(logs, 2, Action.EXECUTE, Actor.EMPTY) != 2
                    || core.insertResource(water, 1_000, Action.EXECUTE) != 1_000
                    || core.getResourceAmount(logResource) != 2) {
                helper.fail("Existing item API did not write into the typed ledger");
                return;
            }

            Map<StorageResourceKey, Long> impossible = Map.of(
                    logResource, -2L,
                    water, -1_001L,
                    charcoalResource, 1L);
            if (core.applyResourceTransaction(impossible, Action.EXECUTE, Actor.EMPTY)
                    || core.getItemCount(logs) != 2
                    || core.getResourceAmount(water) != 1_000
                    || core.getItemCount(charcoal) != 0) {
                helper.fail("Failed mixed transaction partially mutated the Core");
                return;
            }

            Map<StorageResourceKey, Long> exact = Map.of(
                    logResource, -2L,
                    water, -1_000L,
                    charcoalResource, 1L);
            if (!core.applyResourceTransaction(exact, Action.EXECUTE, Actor.EMPTY)
                    || core.getItemCount(logs) != 0
                    || core.getResourceAmount(water) != 0
                    || core.getItemCount(charcoal) != 1
                    || core.getTypeCount() != 1) {
                helper.fail("Exact mixed transaction did not commit every resource together");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void exact_item_components_round_trip_through_the_universal_ledger(
            GameTestHelper helper
    ) {
        ItemStack namedDiamond = new ItemStack(Items.DIAMOND);
        namedDiamond.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Ledger Variant"));
        ItemKey itemKey = ItemKey.of(namedDiamond);
        StorageResourceKey resourceKey = StorageResourceBridge.itemKey(
                itemKey, helper.getLevel().registryAccess());
        CoreStorageRecord original = CoreStorageRecord.fresh(UUID.randomUUID());
        original.putItem(itemKey, 73, helper.getLevel().registryAccess());
        if (original.resourceLedger().amount(resourceKey) != 73) {
            helper.fail("Record item insertion did not use the universal ledger");
            return;
        }

        CompoundTag saved = original.save(helper.getLevel().registryAccess());
        CoreStorageRecord.LoadResult loaded = CoreStorageRecord.load(
                saved, helper.getLevel().registryAccess());
        if (!loaded.success()
                || loaded.record().resourceLedger().amount(resourceKey) != 73
                || loaded.record().getItemCount(itemKey, helper.getLevel().registryAccess()) != 73) {
            helper.fail("Exact item components did not round-trip through the universal ledger");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void terminal_lists_live_typed_resources_without_treating_them_as_items(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey water = StorageResourceBridge.fluidKey(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());
            if (core.insertItem(new ItemStack(Items.OAK_PLANKS)) != 1
                    || core.insertResource(water, 1_000, Action.EXECUTE) != 1_000
                    || core.insertResource(StorageResourceBridge.ENERGY_KEY, 2_000, Action.EXECUTE) != 2_000) {
                helper.fail("Could not seed terminal typed resources");
                return;
            }
            StorageResourceKey missingProvider = StorageResourceKey.of(
                    ResourceLocation.fromNamespaceAndPath("missing_provider", "mana"),
                    ResourceLocation.fromNamespaceAndPath("missing_provider", "blue"),
                    new CompoundTag());
            if (core.storageRecordForTesting().resourceLedger().insert(
                    missingProvider,
                    500,
                    StorageTypeCapacity.unlimitedCapacity(),
                    Action.EXECUTE) != 500) {
                helper.fail("Could not seed an unavailable provider resource");
                return;
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(902, player.getInventory(), core);
            if (menu.getTotalItemTypes() != 1
                    || !menu.getSlot(0).getItem().is(Items.OAK_PLANKS)) {
                helper.fail("Default Item view did not isolate stored items");
                return;
            }
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            ItemStack displayedWater = menu.getSlot(0).getItem();
            if (menu.getResourceView() != TerminalResourceView.FLUID
                    || menu.getTotalItemTypes() != 1
                    || !TerminalResourceDisplay.key(displayedWater).orElseThrow().equals(water)) {
                helper.fail("Fluid view did not isolate the stored fluid");
                return;
            }
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            if (menu.getResourceView() != TerminalResourceView.ENERGY
                    || menu.getTotalItemTypes() != 1
                    || !TerminalResourceDisplay.key(menu.getSlot(0).getItem()).orElseThrow()
                    .equals(StorageResourceBridge.ENERGY_KEY)) {
                helper.fail("Energy view did not isolate stored power");
                return;
            }
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            if (menu.getResourceView() != TerminalResourceView.STATION_WORK
                    || menu.getTotalItemTypes() != 0) {
                helper.fail("Selector did not expose the built-in Processing view");
                return;
            }

            menu.clickMenuButton(player, StorageTerminalMenu.RESET_RESOURCE_VIEW_BUTTON);
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            if (core.insertResource(water, 250, Action.EXECUTE) != 250) {
                helper.fail("Could not update displayed fluid amount");
                return;
            }
            menu.broadcastChanges();
            ItemStack updatedWater = menu.getSlot(0).getItem();
            if (TerminalDisplayStack.amount(updatedWater) != 1_250) {
                helper.fail("Open terminal did not refresh a typed resource mutation");
                return;
            }
            menu.clicked(0, 0, ClickType.PICKUP, player);
            if (!menu.getCarried().isEmpty() || core.getResourceAmount(water) != 1_250) {
                helper.fail("Typed display entry was incorrectly extracted as its icon item");
                return;
            }

            menu.clickMenuButton(player, StorageTerminalMenu.RESET_RESOURCE_VIEW_BUTTON);
            menu.clickMenuButton(player, StorageTerminalMenu.PREVIOUS_RESOURCE_VIEW_BUTTON);
            menu.applyFilter(core, "#minecraft:planks");
            ItemStack allViewPlanks = menu.getSlot(0).getItem();
            if (menu.getResourceView() != TerminalResourceView.ALL
                    || menu.getTotalItemTypes() != 1
                    || !allViewPlanks.is(Items.OAK_PLANKS)
                    || TerminalResourceDisplay.isTyped(allViewPlanks)) {
                helper.fail("All view did not retain the ordinary tagged item display path");
                return;
            }
            menu.clicked(0, 0, ClickType.PICKUP, player);
            if (!menu.getCarried().is(Items.OAK_PLANKS)
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0) {
                helper.fail("All view item entry did not use ordinary item withdrawal");
                return;
            }
            menu.setCarried(ItemStack.EMPTY);
            if (core.insertItem(new ItemStack(Items.OAK_PLANKS)) != 1) {
                helper.fail("Could not restore the All-view item");
                return;
            }

            var craftingMenu = new CraftingTerminalMenu(903, player.getInventory(), core);
            craftingMenu.clickMenuButton(
                    player, StorageTerminalMenu.PREVIOUS_RESOURCE_VIEW_BUTTON);
            craftingMenu.applyFilter(core, "#minecraft:planks");
            ItemStack craftingAllPlanks = craftingMenu.getSlot(0).getItem();
            if (craftingMenu.getResourceView() != TerminalResourceView.ALL
                    || craftingMenu.getTotalItemTypes() != 1
                    || !craftingAllPlanks.is(Items.OAK_PLANKS)
                    || TerminalResourceDisplay.isTyped(craftingAllPlanks)) {
                helper.fail("Crafting Terminal All view did not retain the ordinary item path");
                return;
            }
            craftingMenu.clicked(0, 0, ClickType.PICKUP, player);
            if (!craftingMenu.getSelectedStack().is(Items.OAK_PLANKS)
                    || !craftingMenu.getCarried().isEmpty()) {
                helper.fail("Crafting Terminal All view item did not select normally");
                return;
            }
            ItemStack selectedPlanks = craftingMenu.getSelectedStack().copy();
            craftingMenu.applyFilter(core, "");
            craftingMenu.clickMenuButton(
                    player, StorageTerminalMenu.RESET_RESOURCE_VIEW_BUTTON);
            craftingMenu.clickMenuButton(
                    player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            if (craftingMenu.getResourceView() != TerminalResourceView.FLUID
                    || craftingMenu.getTotalItemTypes() != 1) {
                helper.fail("Crafting Terminal Storage page did not share the fluid view");
                return;
            }
            craftingMenu.clicked(0, 0, ClickType.PICKUP, player);
            if (!ItemStack.isSameItemSameComponents(
                    craftingMenu.getSelectedStack(), selectedPlanks)
                    || !craftingMenu.getCarried().isEmpty()) {
                helper.fail("Crafting Terminal typed entry changed the ordinary item selection");
                return;
            }
            craftingMenu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            if (!craftingMenu.clickMenuButton(
                    player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON)
                    || craftingMenu.getResourceView() != TerminalResourceView.ENERGY) {
                helper.fail("Craftable page did not accept the shared typed resource selector");
                return;
            }
            craftingMenu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            if (craftingMenu.clickMenuButton(
                    player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON)) {
                helper.fail("Fuel page accepted a forged resource-view transition");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void terminal_fills_a_held_fluid_container_from_the_selected_resource(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey water = StorageResourceBridge.fluidKey(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());
            if (core.insertResource(water, 1_000, Action.EXECUTE) != 1_000) {
                helper.fail("Could not seed terminal water");
                return;
            }

            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            var menu = new StorageTerminalMenu(904, player.getInventory(), core);
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            menu.setCarried(new ItemStack(Items.BUCKET));
            menu.handleHeldContainerTransfer(new TerminalHeldContainerTransferPacket(
                    menu.containerId,
                    menu.getStateId(),
                    0,
                    TerminalResourceView.FLUID,
                    TerminalContainerTransferDirection.WITHDRAW), player);

            if (!menu.getCarried().is(Items.WATER_BUCKET)) {
                helper.fail("Terminal did not fill the held bucket from the selected fluid");
                return;
            }
            if (core.getResourceAmount(water) != 0) {
                helper.fail("Terminal did not extract exactly the filled fluid amount");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void terminal_empties_a_held_fluid_container_into_an_empty_resource_view(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey water = StorageResourceBridge.fluidKey(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            var menu = new StorageTerminalMenu(905, player.getInventory(), core);
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            menu.setCarried(new ItemStack(Items.WATER_BUCKET));
            menu.handleHeldContainerTransfer(new TerminalHeldContainerTransferPacket(
                    menu.containerId,
                    menu.getStateId(),
                    0,
                    TerminalResourceView.FLUID,
                    TerminalContainerTransferDirection.DEPOSIT), player);

            if (!menu.getCarried().is(Items.BUCKET)) {
                helper.fail("Terminal did not empty the held fluid container");
                return;
            }
            if (core.getResourceAmount(water) != 1_000) {
                helper.fail("Terminal did not insert exactly the drained fluid amount");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void crafting_terminal_storage_page_uses_the_shared_fluid_container_transfer(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey water = StorageResourceBridge.fluidKey(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());
            if (core.insertResource(water, 1_000, Action.EXECUTE) != 1_000) {
                helper.fail("Could not seed Crafting Terminal water");
                return;
            }

            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            var menu = new CraftingTerminalMenu(906, player.getInventory(), core);
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            menu.setCarried(new ItemStack(Items.BUCKET));
            menu.handleHeldContainerTransfer(new TerminalHeldContainerTransferPacket(
                    menu.containerId,
                    menu.getStateId(),
                    0,
                    TerminalResourceView.FLUID,
                    TerminalContainerTransferDirection.WITHDRAW), player);

            if (!menu.getCarried().is(Items.WATER_BUCKET)
                    || core.getResourceAmount(water) != 0
                    || !menu.getSelectedStack().isEmpty()) {
                helper.fail("Crafting Terminal did not share Storage typed-container behavior");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void terminal_moves_one_result_to_inventory_for_stacked_fluid_containers(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey water = StorageResourceBridge.fluidKey(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());
            if (core.insertResource(water, 1_000, Action.EXECUTE) != 1_000) {
                helper.fail("Could not seed stacked-container water");
                return;
            }

            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            var menu = new StorageTerminalMenu(907, player.getInventory(), core);
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            menu.setCarried(new ItemStack(Items.BUCKET, 2));
            boolean[] listenerSawCompleteTransfer = {false};
            core.addListener(new StorageListener() {
                @Override
                public void onChanged(ItemKey key, long delta, long newAmount, Actor actor) {
                }

                @Override
                public void onResourceChanged(
                        StorageResourceKey key,
                        long delta,
                        long newAmount,
                        Actor actor
                ) {
                    int filledBuckets = 0;
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.is(Items.WATER_BUCKET)) filledBuckets += stack.getCount();
                    }
                    listenerSawCompleteTransfer[0] = core.getResourceAmount(water) == 0
                            && menu.getCarried().is(Items.BUCKET)
                            && menu.getCarried().getCount() == 1
                            && filledBuckets == 1;
                    throw new IllegalStateException("terminal container listener sentinel");
                }
            });
            boolean listenerThrew = false;
            try {
                menu.handleHeldContainerTransfer(new TerminalHeldContainerTransferPacket(
                        menu.containerId,
                        menu.getStateId(),
                        0,
                        TerminalResourceView.FLUID,
                        TerminalContainerTransferDirection.WITHDRAW), player);
            } catch (IllegalStateException exception) {
                if (!"terminal container listener sentinel".equals(exception.getMessage())) {
                    throw exception;
                }
                listenerThrew = true;
            }

            int filledBuckets = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(Items.WATER_BUCKET)) filledBuckets += stack.getCount();
            }
            if (!menu.getCarried().is(Items.BUCKET)
                    || menu.getCarried().getCount() != 1
                    || filledBuckets != 1
                    || core.getResourceAmount(water) != 0
                    || !listenerThrew
                    || !listenerSawCompleteTransfer[0]) {
                helper.fail("Stacked transfer or listener observed a half-committed container transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void terminal_keeps_stacked_container_and_resource_when_result_has_no_inventory_space(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey water = StorageResourceBridge.fluidKey(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());
            if (core.insertResource(water, 1_000, Action.EXECUTE) != 1_000) {
                helper.fail("Could not seed full-inventory water");
                return;
            }

            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                player.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
            }
            var menu = new StorageTerminalMenu(909, player.getInventory(), core);
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            menu.setCarried(new ItemStack(Items.BUCKET, 2));
            boolean transferred = menu.handleHeldContainerTransfer(
                    new TerminalHeldContainerTransferPacket(
                            menu.containerId,
                            menu.getStateId(),
                            0,
                            TerminalResourceView.FLUID,
                            TerminalContainerTransferDirection.WITHDRAW),
                    player);

            if (transferred
                    || !menu.getCarried().is(Items.BUCKET)
                    || menu.getCarried().getCount() != 2
                    || core.getResourceAmount(water) != 1_000) {
                helper.fail("Full inventory mutated the stacked container or stored resource");
                return;
            }
            for (ItemStack stack : player.getInventory().items) {
                if (!stack.is(Items.STONE) || stack.getCount() != 64) {
                    helper.fail("Full inventory changed despite rejected container transfer");
                    return;
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void terminal_container_packet_rejects_stale_state_wrong_view_and_hidden_slot(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey water = StorageResourceBridge.fluidKey(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());
            if (core.insertResource(water, 1_000, Action.EXECUTE) != 1_000) {
                helper.fail("Could not seed packet-validation water");
                return;
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            var menu = new StorageTerminalMenu(908, player.getInventory(), core);
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            menu.setCarried(new ItemStack(Items.BUCKET));

            boolean wrongView = menu.handleHeldContainerTransfer(
                    new TerminalHeldContainerTransferPacket(
                            menu.containerId,
                            menu.getStateId(),
                            0,
                            TerminalResourceView.ENERGY,
                            TerminalContainerTransferDirection.WITHDRAW),
                    player);
            boolean stale = menu.handleHeldContainerTransfer(
                    new TerminalHeldContainerTransferPacket(
                            menu.containerId,
                            menu.getStateId() + 1,
                            0,
                            TerminalResourceView.FLUID,
                            TerminalContainerTransferDirection.WITHDRAW),
                    player);
            boolean hidden = menu.handleHeldContainerTransfer(
                    new TerminalHeldContainerTransferPacket(
                            menu.containerId,
                            menu.getStateId(),
                            StorageTerminalMenu.DISPLAY_COLS * 6,
                            TerminalResourceView.FLUID,
                            TerminalContainerTransferDirection.WITHDRAW),
                    player);
            if (wrongView || stale || hidden
                    || !menu.getCarried().is(Items.BUCKET)
                    || core.getResourceAmount(water) != 1_000) {
                helper.fail("Forged terminal container packet mutated cursor or Core");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void terminal_container_packet_rejects_spectator_and_out_of_range_player(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey water = StorageResourceBridge.fluidKey(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());
            if (core.insertResource(water, 2_000, Action.EXECUTE) != 2_000) {
                helper.fail("Could not seed authorization-validation water");
                return;
            }

            var spectator = helper.makeMockPlayer(GameType.SPECTATOR);
            var spectatorMenu = new StorageTerminalMenu(
                    909, spectator.getInventory(), core, corePos, true);
            spectatorMenu.clickMenuButton(spectator, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            spectatorMenu.setCarried(new ItemStack(Items.BUCKET));
            boolean spectatorAccepted = spectatorMenu.handleHeldContainerTransfer(
                    new TerminalHeldContainerTransferPacket(
                            spectatorMenu.containerId,
                            spectatorMenu.getStateId(),
                            0,
                            TerminalResourceView.FLUID,
                            TerminalContainerTransferDirection.WITHDRAW),
                    spectator);

            var distantPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
            distantPlayer.setPos(corePos.getX() + 100.0, corePos.getY(), corePos.getZ());
            var distantMenu = new StorageTerminalMenu(
                    910, distantPlayer.getInventory(), core, corePos, false);
            distantMenu.clickMenuButton(distantPlayer, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            distantMenu.setCarried(new ItemStack(Items.BUCKET));
            if (distantMenu.getCore(level) != core || distantMenu.stillValid(distantPlayer)) {
                helper.fail("Out-of-range packet test did not isolate the menu validity gate");
                return;
            }
            boolean distantAccepted = distantMenu.handleHeldContainerTransfer(
                    new TerminalHeldContainerTransferPacket(
                            distantMenu.containerId,
                            distantMenu.getStateId(),
                            0,
                            TerminalResourceView.FLUID,
                            TerminalContainerTransferDirection.WITHDRAW),
                    distantPlayer);

            if (spectatorAccepted || distantAccepted
                    || !spectatorMenu.getCarried().is(Items.BUCKET)
                    || !distantMenu.getCarried().is(Items.BUCKET)
                    || core.getResourceAmount(water) != 2_000) {
                helper.fail("Unauthorized terminal container packet mutated cursor or Core");
                return;
            }
            helper.succeed();
        });
    }
}
