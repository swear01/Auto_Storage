package com.swearprom.magicstorage.fixture.recipe;

import com.swearprom.magicstorage.magic_storage.CraftingDestination;
import com.swearprom.magicstorage.magic_storage.CraftingTerminalMenu;
import com.swearprom.magicstorage.magic_storage.BusConfiguration;
import com.swearprom.magicstorage.magic_storage.BusFilterMode;
import com.swearprom.magicstorage.magic_storage.BusFilterRule;
import com.swearprom.magicstorage.magic_storage.BusMode;
import com.swearprom.magicstorage.magic_storage.ExportBusBlock;
import com.swearprom.magicstorage.magic_storage.ExportBusBlockEntity;
import com.swearprom.magicstorage.magic_storage.ImportBusBlock;
import com.swearprom.magicstorage.magic_storage.ImportBusBlockEntity;
import com.swearprom.magicstorage.magic_storage.ItemKey;
import com.swearprom.magicstorage.magic_storage.MagicStorage;
import com.swearprom.magicstorage.magic_storage.RecipePresentation;
import com.swearprom.magicstorage.magic_storage.StorageCoreBlockEntity;
import com.swearprom.magicstorage.magic_storage.Action;
import com.swearprom.magicstorage.magic_storage.Actor;
import com.swearprom.magicstorage.magic_storage.StorageResourceKey;
import com.swearprom.magicstorage.magic_storage.StorageResourceCapabilities;
import com.swearprom.magicstorage.magic_storage.StorageTerminalMenu;
import com.swearprom.magicstorage.magic_storage.TerminalDisplayStack;
import com.swearprom.magicstorage.magic_storage.TerminalResourceDisplay;
import com.swearprom.magicstorage.magic_storage.TerminalResourceView;
import com.swearprom.magicstorage.magic_storage.TerminalContainerTransferDirection;
import com.swearprom.magicstorage.magic_storage.TerminalHeldContainerTransferPacket;
import com.swearprom.magicstorage.magic_storage.StorageResourceTransaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@GameTestHolder(FixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeFamilyIntegrationTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int NEXT_RESOURCE_VIEW_BUTTON = 26;
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            FixtureMod.MODID, "grinding_cobblestone");
    private static final ResourceLocation TYPED_RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            FixtureMod.MODID, "infuse_cobblestone");
    private static final ResourceLocation PROCESSING_RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            FixtureMod.MODID, "process_cobblestone");

    private RecipeFamilyIntegrationTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void registered_family_is_discovered_previewed_and_committed(
            GameTestHelper helper
    ) {
        withFixture(helper, context -> {
            if (context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1, CraftingDestination.NONE, context.player())) {
                helper.fail("Recipe must require its registered station");
                return;
            }
            if (!installStonecutter(context)) {
                helper.fail("Could not install Stonecutter through the public terminal slots");
                return;
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1, CraftingDestination.NONE, context.player())) {
                helper.fail("Registered custom RecipeType was not discovered");
                return;
            }

            var preview = context.menu().computeCraftPreview(context.core(), context.player());
            RecipePresentation presentation = context.menu().getRecipePresentation();
            if (preview.craftable() != 1
                    || !ItemStack.isSameItemSameComponents(presentation.output(), context.output())
                    || presentation.output().getCount() != 2
                    || !presentation.station().is(Items.STONECUTTER)) {
                helper.fail("Registered family preview did not preserve exact output and station");
                return;
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1, CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Registered family commit failed");
                return;
            }
            if (context.core().getItemCount(ItemKey.of(new ItemStack(Items.COBBLESTONE))) != 0
                    || inventoryCount(context.player().getInventory(), context.output()) != 2) {
                helper.fail("Registered family did not consume and deliver exact counts atomically");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void registered_family_rolls_back_when_inventory_is_full(
            GameTestHelper helper
    ) {
        withFixture(helper, context -> {
            if (!installStonecutter(context)) {
                helper.fail("Could not install Stonecutter through the public terminal slots");
                return;
            }
            if (context.core().insertItem(new ItemStack(Items.COBBLESTONE)) != 1) {
                helper.fail("Could not preserve the input type during rollback check");
                return;
            }
            for (var item : List.of(
                    Items.STONE,
                    Items.DIRT,
                    Items.SAND,
                    Items.OAK_LOG,
                    Items.IRON_INGOT,
                    Items.GOLD_INGOT,
                    Items.DIAMOND,
                    Items.EMERALD,
                    Items.COAL)) {
                if (context.core().insertItem(new ItemStack(item)) != 1) {
                    helper.fail("Could not fill storage type capacity for rollback check");
                    return;
                }
            }
            for (int slot = 0; slot < context.player().getInventory().getContainerSize(); slot++) {
                context.player().getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1, CraftingDestination.NONE, context.player())) {
                helper.fail("Could not select registered family before rollback check");
                return;
            }
            if (context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1, CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Full inventory unexpectedly accepted registered-family output");
                return;
            }
            if (context.core().getItemCount(ItemKey.of(new ItemStack(Items.COBBLESTONE))) != 2
                    || inventoryCount(context.player().getInventory(), context.output()) != 0) {
                helper.fail("Failed registered-family delivery changed storage or inventory");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void polymorphic_station_generates_exact_descriptor_work_at_each_variant_rate(
            GameTestHelper helper
    ) {
        withFixture(helper, context -> {
            context.menu().clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
            var machineSlot = findMachineSlot(context, new ItemStack(Items.COPPER_BLOCK));
            if (machineSlot == null) {
                helper.fail("Polymorphic fixture station had no installable terminal slot");
                return;
            }
            machineSlot.set(new ItemStack(Items.COPPER_BLOCK));
            machineSlot.setChanged();
            for (int tick = 0; tick < 9; tick++) context.core().tick();
            if (context.core().getStationWork(FixtureMod.FRACTIONAL_PROCESSOR_ID) != 10) {
                helper.fail("Slow polymorphic station lost fractional work");
                return;
            }
            machineSlot.set(new ItemStack(Items.IRON_BLOCK));
            machineSlot.setChanged();
            for (int tick = 0; tick < 4; tick++) context.core().tick();
            if (context.core().getStationWork(FixtureMod.FRACTIONAL_PROCESSOR_ID) != 15) {
                helper.fail("Changing polymorphic station variant did not use its own rate");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void descriptor_station_work_is_simulated_committed_and_consumed_atomically(
            GameTestHelper helper
    ) {
        withFixture(helper, context -> {
            context.menu().clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
            var machineSlot = findMachineSlot(context, new ItemStack(Items.COPPER_BLOCK));
            if (machineSlot == null) {
                helper.fail("Process fixture station had no installable terminal slot");
                return;
            }
            machineSlot.set(new ItemStack(Items.COPPER_BLOCK));
            machineSlot.setChanged();
            context.menu().clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
            if (context.menu().handleRecipeRequest(
                    context.level(), PROCESSING_RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Process recipe crafted without descriptor station work");
                return;
            }
            for (int tick = 0; tick < 9; tick++) context.core().tick();
            long work = context.core().getStationWork(FixtureMod.FRACTIONAL_PROCESSOR_ID);
            long cobblestone = context.core().getItemCount(
                    ItemKey.of(new ItemStack(Items.COBBLESTONE)));
            machineSlot.set(new ItemStack(Items.IRON_BLOCK));
            machineSlot.setChanged();
            if (!context.menu().handleRecipeRequest(
                    context.level(), PROCESSING_RECIPE_ID, 1,
                    CraftingDestination.NONE, context.player())) {
                helper.fail("Could not select process recipe for station preview");
                return;
            }
            RecipePresentation presentation = context.menu().getRecipePresentation();
            if (presentation.stationVariants().size() != 2
                    || !presentation.stationForCycle(0).is(Items.IRON_BLOCK)
                    || !presentation.stationForCycle(1).is(Items.COPPER_BLOCK)
                    || !presentation.stationForCycle(2).is(Items.IRON_BLOCK)) {
                helper.fail("Process recipe preview did not cycle from the installed station variant");
                return;
            }
            RecipePresentation.Resource stationResource = presentation.resources().stream()
                    .filter(resource -> resource.kind()
                            == RecipePresentation.ResourceKind.STATION_WORK)
                    .findFirst()
                    .orElse(null);
            if (stationResource == null
                    || stationResource.available() != 10
                    || stationResource.required() != 10) {
                helper.fail("Process recipe preview omitted descriptor station work amounts");
                return;
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), PROCESSING_RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Process recipe rejected sufficient descriptor station work: work="
                        + work + ", cobblestone=" + cobblestone);
                return;
            }
            if (context.core().getStationWork(FixtureMod.FRACTIONAL_PROCESSOR_ID) != 0
                    || inventoryCount(context.player().getInventory(), new ItemStack(Items.FLINT)) != 1) {
                helper.fail("Process recipe did not consume station work and deliver output atomically");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void registered_resource_kind_uses_the_public_atomic_core_api(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            CompoundTag blueVariant = new CompoundTag();
            blueVariant.putString("tone", "blue");
            StorageResourceKey blue = StorageResourceKey.of(
                    ResourceLocation.fromNamespaceAndPath(FixtureMod.MODID, "mana"),
                    ResourceLocation.fromNamespaceAndPath(FixtureMod.MODID, "blue"),
                    blueVariant);
            StorageResourceKey red = StorageResourceKey.of(
                    ResourceLocation.fromNamespaceAndPath(FixtureMod.MODID, "mana"),
                    ResourceLocation.fromNamespaceAndPath(FixtureMod.MODID, "red"),
                    new CompoundTag());
            StorageResourceKey unknown = StorageResourceKey.of(
                    ResourceLocation.fromNamespaceAndPath(FixtureMod.MODID, "unknown"),
                    ResourceLocation.fromNamespaceAndPath(FixtureMod.MODID, "value"),
                    new CompoundTag());

            if (core.insertResource(blue, 500, Action.EXECUTE) != 500
                    || core.insertResource(unknown, 1, Action.EXECUTE) != 0) {
                helper.fail("Public Core API did not accept registered and reject unknown kinds");
                return;
            }
            var resourceHandler = level.getCapability(
                    StorageResourceCapabilities.BLOCK, corePos, Direction.UP);
            if (resourceHandler == null
                    || resourceHandler.getAmount(blue) != 500
                    || !resourceHandler.getStoredResources().contains(blue)) {
                helper.fail("Registered addon kind was absent from the public Core capability");
                return;
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var terminal = new StorageTerminalMenu(402, player.getInventory(), core);
            for (int step = 0;
                 step < TerminalResourceView.values().length
                         && terminal.getResourceView() != TerminalResourceView.OTHER;
                 step++) {
                if (!terminal.clickMenuButton(player, NEXT_RESOURCE_VIEW_BUTTON)) {
                    helper.fail("Could not select the Other resource view");
                    return;
                }
            }
            if (terminal.getResourceView() != TerminalResourceView.OTHER) {
                helper.fail("Terminal resource selector did not reach Other");
                return;
            }
            boolean terminalEntry = false;
            for (int slot = 0; slot < StorageTerminalMenu.DISPLAY_SLOTS; slot++) {
                ItemStack display = terminal.getSlot(slot).getItem();
                if (TerminalResourceDisplay.key(display).orElse(null) != null
                        && TerminalResourceDisplay.key(display).orElseThrow().equals(blue)
                        && TerminalDisplayStack.amount(display) == 500
                        && TerminalDisplayStack.strip(display).is(Items.AMETHYST_SHARD)) {
                    terminalEntry = true;
                    break;
                }
            }
            if (!terminalEntry) {
                helper.fail("Registered addon kind did not keep its registered representative");
                return;
            }
            StorageResourceTransaction transaction = StorageResourceTransaction.builder()
                    .add(blue, -200)
                    .add(red, 125)
                    .build();
            if (!core.applyResourceTransaction(transaction, Action.SIMULATE, Actor.EMPTY)
                    || core.getResourceAmount(blue) != 500
                    || core.getResourceAmount(red) != 0) {
                helper.fail("Public resource transaction simulation mutated the Core");
                return;
            }
            if (!core.applyResourceTransaction(transaction, Action.EXECUTE, Actor.EMPTY)
                    || core.getResourceAmount(blue) != 300
                    || core.getResourceAmount(red) != 125) {
                helper.fail("Public resource transaction did not commit atomically");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void registered_block_strategy_drives_directional_import_and_export(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(2, 3, 1));
        BlockPos importPos = corePos.east();
        BlockPos sourcePos = importPos.east();
        BlockPos exportPos = corePos.west();
        BlockPos targetPos = exportPos.west();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(exportPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
        level.setBlock(sourcePos, Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(targetPos, Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity importer)
                    || !(level.getBlockEntity(exportPos) instanceof ExportBusBlockEntity exporter)) {
                helper.fail("Addon block-strategy Bus fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey mana = mana("blue");
            StorageResourceKey blockedMana = mana("red");
            var source = FixtureManaBlockStrategy.handler(level, sourcePos);
            var target = FixtureManaBlockStrategy.handler(level, targetPos);
            if (source.insert(mana, 2_500, false) != 2_500
                    || source.insert(blockedMana, 500, false) != 500) {
                helper.fail("Could not seed addon block strategy source");
                return;
            }
            BusConfiguration exportConfig = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.resource(mana)),
                    Optional.empty(),
                    1);
            BusConfiguration importConfig = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    true,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.resource(mana)),
                    Optional.empty(),
                    1);
            CompoundTag tag = new CompoundTag();
            exportConfig.save(tag, level.registryAccess());
            ItemStack carrier = new ItemStack(MagicStorage.EXPORT_BUS_ITEM.get());
            BlockItem.setBlockEntityData(carrier, MagicStorage.EXPORT_BUS_BE.get(), tag);
            CompoundTag importTag = new CompoundTag();
            importConfig.save(importTag, level.registryAccess());
            ItemStack importCarrier = new ItemStack(MagicStorage.IMPORT_BUS_ITEM.get());
            BlockItem.setBlockEntityData(importCarrier, MagicStorage.IMPORT_BUS_BE.get(), importTag);
            if (!BlockItem.updateCustomBlockEntityTag(level, null, exportPos, carrier)
                    || !BlockItem.updateCustomBlockEntityTag(
                    level, null, importPos, importCarrier)) {
                helper.fail("Could not configure addon typed Buses");
                return;
            }

            importer.tick();
            if (source.getAmount(mana) != 1_500
                    || source.getAmount(blockedMana) != 500
                    || core.getResourceAmount(mana) != 1_000
                    || core.getResourceAmount(blockedMana) != 0) {
                helper.fail("Directional Import did not move one addon resource batch: source="
                        + source.getAmount(mana) + ", core=" + core.getResourceAmount(mana));
                return;
            }
            exporter.tick();
            if (core.getResourceAmount(mana) != 0 || target.getAmount(mana) != 1_000) {
                helper.fail("Directional Export did not move the addon resource batch");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void directional_import_uses_native_fluid_and_energy_block_capabilities(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(2, 3, 1));
        BlockPos fluidBusPos = corePos.east();
        BlockPos fluidSourcePos = fluidBusPos.east();
        BlockPos energyBusPos = corePos.west();
        BlockPos energySourcePos = energyBusPos.west();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(fluidBusPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(energyBusPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
        level.setBlock(fluidSourcePos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(energySourcePos, Blocks.RED_GLAZED_TERRACOTTA.defaultBlockState(),
                Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(fluidBusPos) instanceof ImportBusBlockEntity fluidBus)
                    || !(level.getBlockEntity(energyBusPos) instanceof ImportBusBlockEntity energyBus)) {
                helper.fail("Native Import Bus fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            var fluidSource = FixtureNativeBlockStorage.fluid(level, fluidSourcePos);
            var energySource = FixtureNativeBlockStorage.energy(level, energySourcePos);
            if (level.getCapability(Capabilities.FluidHandler.BLOCK,
                    fluidSourcePos, Direction.WEST) != fluidSource
                    || level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    energySourcePos, Direction.EAST) != energySource) {
                helper.fail("Native Import target capabilities were not registered on the queried sides");
                return;
            }
            if (fluidSource.fill(new FluidStack(Fluids.WATER, 1_500),
                    IFluidHandler.FluidAction.EXECUTE) != 1_500
                    || energySource.receiveEnergy(1_500, false) != 1_500) {
                helper.fail("Could not seed native Import Bus sources");
                return;
            }
            BusConfiguration importConfig = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    true,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    1);
            if (!applyBusConfiguration(level, fluidBusPos, MagicStorage.IMPORT_BUS_BE.get(),
                    MagicStorage.IMPORT_BUS_ITEM.get(), importConfig)
                    || !applyBusConfiguration(level, energyBusPos,
                    MagicStorage.IMPORT_BUS_BE.get(), MagicStorage.IMPORT_BUS_ITEM.get(),
                    importConfig)) {
                helper.fail("Could not reset native Import Bus transfer cooldowns");
                return;
            }
            fluidBus.tick();
            energyBus.tick();
            StorageResourceKey water = StorageResourceKey.fluid(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());
            if (fluidSource.getFluidAmount() != 500
                    || energySource.getEnergyStored() != 500
                    || core.getResourceAmount(water) != 1_000
                    || core.getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 1_000) {
                helper.fail("Directional Import did not use native fluid and energy capabilities: "
                        + "fluidSource=" + fluidSource.getFluidAmount()
                        + ", energySource=" + energySource.getEnergyStored()
                        + ", coreFluid=" + core.getResourceAmount(water)
                        + ", coreEnergy="
                        + core.getResourceAmount(StorageResourceKey.neoforgeEnergy()));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void directional_export_uses_native_fluid_and_energy_block_capabilities(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(2, 3, 1));
        BlockPos fluidBusPos = corePos.east();
        BlockPos fluidTargetPos = fluidBusPos.east();
        BlockPos energyBusPos = corePos.west();
        BlockPos energyTargetPos = energyBusPos.west();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(fluidBusPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(energyBusPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
        level.setBlock(fluidTargetPos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(energyTargetPos, Blocks.RED_GLAZED_TERRACOTTA.defaultBlockState(),
                Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(fluidBusPos) instanceof ExportBusBlockEntity fluidBus)
                    || !(level.getBlockEntity(energyBusPos) instanceof ExportBusBlockEntity energyBus)) {
                helper.fail("Native Export Bus fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            if (level.getCapability(Capabilities.FluidHandler.BLOCK,
                    fluidTargetPos, Direction.WEST) == null
                    || level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    energyTargetPos, Direction.EAST) == null) {
                helper.fail("Native Export target capabilities were not registered on the queried sides");
                return;
            }
            StorageResourceKey water = StorageResourceKey.fluid(
                    new FluidStack(Fluids.WATER, 1), level.registryAccess());
            if (core.insertResource(water, 1_500, Action.EXECUTE) != 1_500
                    || core.insertResource(StorageResourceKey.neoforgeEnergy(), 1_500,
                    Action.EXECUTE) != 1_500) {
                helper.fail("Could not seed native Export Bus resources");
                return;
            }
            BusConfiguration fluidConfig = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.resource(water)),
                    Optional.empty(),
                    1);
            BusConfiguration energyConfig = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.resource(StorageResourceKey.neoforgeEnergy())),
                    Optional.empty(),
                    1);
            if (!applyBusConfiguration(level, fluidBusPos, MagicStorage.EXPORT_BUS_BE.get(),
                    MagicStorage.EXPORT_BUS_ITEM.get(), fluidConfig)
                    || !applyBusConfiguration(level, energyBusPos,
                    MagicStorage.EXPORT_BUS_BE.get(), MagicStorage.EXPORT_BUS_ITEM.get(),
                    energyConfig)) {
                helper.fail("Could not configure native Export Bus typed filters");
                return;
            }
            fluidBus.tick();
            energyBus.tick();
            var fluidTarget = FixtureNativeBlockStorage.fluid(level, fluidTargetPos);
            var energyTarget = FixtureNativeBlockStorage.energy(level, energyTargetPos);
            if (fluidTarget.getFluidAmount() != 1_000
                    || energyTarget.getEnergyStored() != 1_000
                    || core.getResourceAmount(water) != 500
                    || core.getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 500) {
                helper.fail("Directional Export did not use native fluid and energy capabilities: "
                        + "fluidTarget=" + fluidTarget.getFluidAmount()
                        + ", energyTarget=" + energyTarget.getEnergyStored()
                        + ", coreFluid=" + core.getResourceAmount(water)
                        + ", coreEnergy="
                        + core.getResourceAmount(StorageResourceKey.neoforgeEnergy()));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void typed_family_commits_multiple_inputs_outputs_and_retained_roles_atomically(
            GameTestHelper helper
    ) {
        withTypedFixture(helper, context -> {
            if (!installStonecutter(context)) {
                helper.fail("Could not install typed-family station");
                return;
            }
            StorageResourceKey blue = mana("blue");
            StorageResourceKey red = mana("red");
            StorageResourceKey water = StorageResourceKey.fluid(
                    new net.neoforged.neoforge.fluids.FluidStack(
                            net.minecraft.world.level.material.Fluids.WATER, 1),
                    context.level().registryAccess());
            if (context.core().insertItem(new ItemStack(Items.COBBLESTONE, 4)) != 4
                    || context.core().insertItem(new ItemStack(Items.IRON_PICKAXE)) != 1
                    || context.core().insertResource(blue, 2, Action.EXECUTE) != 2
                    || context.core().insertResource(red, 5, Action.EXECUTE) != 5) {
                helper.fail("Could not seed typed-family resources");
                return;
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), TYPED_RECIPE_ID, 1,
                    CraftingDestination.NONE, context.player())) {
                helper.fail("Typed family was not discovered");
                return;
            }
            CraftingTerminalMenu.CraftPreview preview = context.menu().computeCraftPreview(
                    context.core(), context.player());
            if (preview.craftable() != 2) {
                helper.fail("Typed family preview did not aggregate retained and consumed resources: "
                        + preview.craftable());
                return;
            }
            long cobblestoneRows = preview.ingredients().stream()
                    .filter(row -> row.stack().is(Items.COBBLESTONE))
                    .count();
            CraftingTerminalMenu.IngredientPreview cobblestone = preview.ingredients().stream()
                    .filter(row -> row.stack().is(Items.COBBLESTONE))
                    .findFirst()
                    .orElse(null);
            if (cobblestoneRows != 1 || cobblestone == null
                    || cobblestone.available() != 4 || cobblestone.required() != 2) {
                helper.fail("Duplicate typed ingredients were not merged into one 4 / 2 row: "
                        + preview.ingredients());
                return;
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), TYPED_RECIPE_ID, 2,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Typed family commit failed");
                return;
            }
            if (context.core().getItemCount(ItemKey.of(new ItemStack(Items.COBBLESTONE))) != 0
                    || context.core().getItemCount(ItemKey.of(new ItemStack(Items.IRON_PICKAXE))) != 1
                    || context.core().getResourceAmount(blue) != 50
                    || context.core().getResourceAmount(red) != 5
                    || context.core().getResourceAmount(water) != 500
                    || inventoryCount(context.player().getInventory(), new ItemStack(Items.GRAVEL)) != 6) {
                helper.fail("Typed family changed the wrong exact resources or output amounts");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void typed_family_capacity_failure_changes_no_input_output_or_player_state(
            GameTestHelper helper
    ) {
        withTypedFixture(helper, context -> {
            if (!installStonecutter(context)) {
                helper.fail("Could not install typed-family station");
                return;
            }
            StorageResourceKey blue = mana("blue");
            StorageResourceKey red = mana("red");
            if (context.core().insertItem(new ItemStack(Items.COBBLESTONE, 4)) != 4
                    || context.core().insertItem(new ItemStack(Items.IRON_PICKAXE)) != 1
                    || context.core().insertResource(blue, 2, Action.EXECUTE) != 2
                    || context.core().insertResource(red, 5, Action.EXECUTE) != 5) {
                helper.fail("Could not seed typed rollback resources");
                return;
            }
            for (var item : List.of(
                    Items.STONE, Items.DIRT, Items.SAND,
                    Items.COAL, Items.DIAMOND, Items.EMERALD)) {
                if (context.core().insertItem(new ItemStack(item)) != 1) {
                    helper.fail("Could not fill typed rollback capacity");
                    return;
                }
            }
            if (context.core().getTypeCount() != 10) {
                helper.fail("Typed rollback fixture did not fill all ten type slots");
                return;
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), TYPED_RECIPE_ID, 1,
                    CraftingDestination.NONE, context.player())) {
                helper.fail("Could not select typed family before rollback check");
                return;
            }
            if (context.menu().handleRecipeRequest(
                    context.level(), TYPED_RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Typed family ignored a full destination resource ledger");
                return;
            }
            if (context.core().getItemCount(ItemKey.of(new ItemStack(Items.COBBLESTONE))) != 4
                    || context.core().getResourceAmount(blue) != 2
                    || context.core().getResourceAmount(red) != 5
                    || inventoryCount(context.player().getInventory(), new ItemStack(Items.GRAVEL)) != 0) {
                helper.fail("Failed typed family commit partially mutated resources");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void typed_family_consumes_an_available_resource_alternative(
            GameTestHelper helper
    ) {
        withTypedFixture(helper, context -> {
            if (!installStonecutter(context)) {
                helper.fail("Could not install alternative typed-family station");
                return;
            }
            StorageResourceKey green = mana("green");
            StorageResourceKey blue = mana("blue");
            StorageResourceKey red = mana("red");
            StorageResourceKey yellow = mana("yellow");
            if (context.core().insertItem(new ItemStack(Items.COBBLESTONE, 2)) != 2
                    || context.core().insertItem(new ItemStack(Items.IRON_PICKAXE)) != 1
                    || context.core().insertResource(green, 1, Action.EXECUTE) != 1
                    || context.core().insertResource(red, 5, Action.EXECUTE) != 5) {
                helper.fail("Could not seed typed resource alternative");
                return;
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), TYPED_RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Typed family rejected an available non-primary alternative");
                return;
            }
            if (context.core().getResourceAmount(green) != 0
                    || context.core().getResourceAmount(blue) != 0
                    || context.core().getResourceAmount(yellow) != 25
                    || inventoryCount(context.player().getInventory(), new ItemStack(Items.GRAVEL)) != 3) {
                helper.fail("Typed family consumed or returned the wrong alternative-specific remainder");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void terminal_fe_deposit_and_withdraw_use_the_item_capability(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            var menu = new StorageTerminalMenu(403, player.getInventory(), core);
            menu.clickMenuButton(player, NEXT_RESOURCE_VIEW_BUTTON);
            menu.clickMenuButton(player, NEXT_RESOURCE_VIEW_BUTTON);
            ItemStack charged = new ItemStack(FixtureMod.ENERGY_CELL.get());
            FixtureEnergyStorage.setEnergy(charged, 5_000);
            menu.setCarried(charged);

            if (!menu.handleHeldContainerTransfer(new TerminalHeldContainerTransferPacket(
                    menu.containerId,
                    menu.getStateId(),
                    0,
                    TerminalResourceView.ENERGY,
                    TerminalContainerTransferDirection.DEPOSIT), player)) {
                helper.fail("Energy container deposit was rejected");
                return;
            }
            var emptyHandler = menu.getCarried().getCapability(Capabilities.EnergyStorage.ITEM);
            if (emptyHandler == null || emptyHandler.getEnergyStored() != 0
                    || core.getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 5_000) {
                helper.fail("Energy container deposit changed the wrong amount");
                return;
            }
            if (!menu.handleHeldContainerTransfer(new TerminalHeldContainerTransferPacket(
                    menu.containerId,
                    menu.getStateId(),
                    0,
                    TerminalResourceView.ENERGY,
                    TerminalContainerTransferDirection.WITHDRAW), player)) {
                helper.fail("Energy container withdraw was rejected");
                return;
            }
            var filledHandler = menu.getCarried().getCapability(Capabilities.EnergyStorage.ITEM);
            if (filledHandler == null || filledHandler.getEnergyStored() != 5_000
                    || core.getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("Energy container withdraw changed the wrong amount");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void terminal_uses_a_registered_addon_container_strategy_in_other_view(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            var menu = new StorageTerminalMenu(404, player.getInventory(), core);
            for (int step = 0;
                 step < TerminalResourceView.values().length
                         && menu.getResourceView() != TerminalResourceView.OTHER;
                 step++) {
                menu.clickMenuButton(player, NEXT_RESOURCE_VIEW_BUTTON);
            }
            ItemStack filled = new ItemStack(FixtureMod.MANA_CELL.get());
            FixtureManaContainer.set(filled, "blue", 300);
            menu.setCarried(filled);
            StorageResourceKey blue = mana("blue");

            if (!menu.handleHeldContainerTransfer(new TerminalHeldContainerTransferPacket(
                    menu.containerId,
                    menu.getStateId(),
                    0,
                    TerminalResourceView.OTHER,
                    TerminalContainerTransferDirection.DEPOSIT), player)
                    || FixtureManaContainer.amount(menu.getCarried()) != 0
                    || core.getResourceAmount(blue) != 300) {
                helper.fail("Registered addon strategy did not deposit its exact resource");
                return;
            }
            if (!menu.handleHeldContainerTransfer(new TerminalHeldContainerTransferPacket(
                    menu.containerId,
                    menu.getStateId(),
                    0,
                    TerminalResourceView.OTHER,
                    TerminalContainerTransferDirection.WITHDRAW), player)
                    || FixtureManaContainer.amount(menu.getCarried()) != 300
                    || !FixtureManaContainer.variant(menu.getCarried()).equals("blue")
                    || core.getResourceAmount(blue) != 0) {
                helper.fail("Registered addon strategy did not withdraw its exact resource");
                return;
            }
            helper.succeed();
        });
    }

    private static void withFixture(GameTestHelper helper, FixtureAssertion assertion) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            ItemStack output = new ItemStack(Items.GRAVEL, 2);
            output.set(DataComponents.CUSTOM_NAME, Component.literal("fixture-ground"));
            RecipeHolder<FixtureGrindingRecipe> holder = new RecipeHolder<>(
                    RECIPE_ID,
                    new FixtureGrindingRecipe(Ingredient.of(Items.COBBLESTONE), output));
            RecipeHolder<FixtureProcessingRecipe> processingHolder = new RecipeHolder<>(
                    PROCESSING_RECIPE_ID,
                    new FixtureProcessingRecipe(
                            Ingredient.of(Items.COBBLESTONE), new ItemStack(Items.FLINT)));
            var manager = level.getRecipeManager();
            List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
            List<RecipeHolder<?>> registered = new ArrayList<>(original);
            registered.removeIf(recipe -> recipe.id().equals(RECIPE_ID));
            registered.removeIf(recipe -> recipe.id().equals(PROCESSING_RECIPE_ID));
            registered.add(holder);
            registered.add(processingHolder);
            manager.replaceRecipes(registered);
            try {
                core.rebuildNetwork(level);
                if (core.insertItem(new ItemStack(Items.COBBLESTONE)) != 1) {
                    helper.fail("Could not seed fixture input");
                    return;
                }
                var player = helper.makeMockPlayer(GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(400, player.getInventory(), core);
                assertion.run(new FixtureContext(level, core, player, menu, output));
            } finally {
                manager.replaceRecipes(original);
            }
        });
    }

    private static void withTypedFixture(GameTestHelper helper, FixtureAssertion assertion) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            RecipeHolder<FixtureInfusionRecipe> holder = new RecipeHolder<>(
                    TYPED_RECIPE_ID,
                    new FixtureInfusionRecipe(
                            Ingredient.of(Items.COBBLESTONE), new ItemStack(Items.GRAVEL, 3)));
            var manager = level.getRecipeManager();
            List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
            List<RecipeHolder<?>> registered = new ArrayList<>(original);
            registered.removeIf(recipe -> recipe.id().equals(TYPED_RECIPE_ID));
            registered.add(holder);
            manager.replaceRecipes(registered);
            try {
                core.rebuildNetwork(level);
                var player = helper.makeMockPlayer(GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(401, player.getInventory(), core);
                assertion.run(new FixtureContext(
                        level, core, player, menu, new ItemStack(Items.GRAVEL, 3)));
            } finally {
                manager.replaceRecipes(original);
            }
        });
    }

    private static StorageResourceKey mana(String path) {
        return resource(
                ResourceLocation.fromNamespaceAndPath(FixtureMod.MODID, "mana"),
                FixtureMod.MODID,
                path);
    }

    private static StorageResourceKey resource(
            ResourceLocation kind,
            String namespace,
            String path
    ) {
        return StorageResourceKey.of(
                kind, ResourceLocation.fromNamespaceAndPath(namespace, path), new CompoundTag());
    }

    private static boolean installStonecutter(FixtureContext context) {
        context.menu().clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        ItemStack station = new ItemStack(Items.STONECUTTER);
        boolean installed = false;
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = context.menu().getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) continue;
            slot.set(station.copy());
            slot.setChanged();
            installed = true;
            break;
        }
        context.menu().clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
        return installed;
    }

    private static net.minecraft.world.inventory.Slot findMachineSlot(
            FixtureContext context,
            ItemStack station
    ) {
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = context.menu().getSlot(index);
            if (slot.isActive() && slot.mayPlace(station)) return slot;
        }
        return null;
    }

    private static boolean applyBusConfiguration(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            BlockEntityType<?> blockEntityType,
            net.minecraft.world.item.Item blockItem,
            BusConfiguration configuration
    ) {
        CompoundTag tag = new CompoundTag();
        configuration.save(tag, level.registryAccess());
        ItemStack carrier = new ItemStack(blockItem);
        BlockItem.setBlockEntityData(carrier, blockEntityType, tag);
        return BlockItem.updateCustomBlockEntityTag(level, null, pos, carrier);
    }

    private static int inventoryCount(net.minecraft.world.entity.player.Inventory inventory, ItemStack expected) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, expected)) count += stack.getCount();
        }
        return count;
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(FixtureContext context);
    }

    private record FixtureContext(
            net.minecraft.server.level.ServerLevel level,
            StorageCoreBlockEntity core,
            net.minecraft.world.entity.player.Player player,
            CraftingTerminalMenu menu,
            ItemStack output
    ) {
    }
}
