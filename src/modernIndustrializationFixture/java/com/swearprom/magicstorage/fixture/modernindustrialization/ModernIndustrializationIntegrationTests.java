package com.swearprom.magicstorage.fixture.modernindustrialization;

import com.swearprom.magicstorage.magic_storage.Action;
import com.swearprom.magicstorage.magic_storage.CraftingDestination;
import com.swearprom.magicstorage.magic_storage.CraftingTerminalMenu;
import com.swearprom.magicstorage.magic_storage.ItemKey;
import com.swearprom.magicstorage.magic_storage.MagicStorage;
import com.swearprom.magicstorage.magic_storage.MachineDescriptor;
import com.swearprom.magicstorage.magic_storage.MachineWorkRate;
import com.swearprom.magicstorage.magic_storage.StorageCoreBlockEntity;
import com.swearprom.magicstorage.magic_storage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(ModernIndustrializationFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ModernIndustrializationIntegrationTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final ResourceLocation MACERATOR = ResourceLocation.fromNamespaceAndPath(
            MagicStorage.MODID, "modern_industrialization_macerator");
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "materials/aluminum/macerator/blade");
    private static final ResourceLocation BRONZE_MACERATOR = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "bronze_macerator");
    private static final ResourceLocation STEEL_MACERATOR = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "steel_macerator");
    private static final ResourceLocation ELECTRIC_MACERATOR = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "electric_macerator");
    private static final ResourceLocation ALUMINUM_BLADE = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "aluminum_blade");
    private static final ResourceLocation ALUMINUM_TINY_DUST = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "aluminum_tiny_dust");
    private static final ResourceLocation DISTILLERY = ResourceLocation.fromNamespaceAndPath(
            MagicStorage.MODID, "modern_industrialization_distillery");
    private static final ResourceLocation DISTILLERY_RECIPE = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "oil/distillation/ethanol_from_sugar");
    private static final ResourceLocation SUGAR_SOLUTION = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "sugar_solution");
    private static final ResourceLocation ETHANOL = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "ethanol");
    private static final ResourceLocation MIXER = ResourceLocation.fromNamespaceAndPath(
            MagicStorage.MODID, "modern_industrialization_mixer");
    private static final ResourceLocation CATALYST_RECIPE = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "dyes/black/mixer/benzene");
    private static final ResourceLocation BENZENE = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "benzene");
    private static final ResourceLocation OVER_SLOT_RECIPE = ResourceLocation.fromNamespaceAndPath(
            ModernIndustrializationFixtureMod.MODID, "over_slot_compressor");
    private static final ResourceLocation RETAINED_INPUT_OVERLAP =
            ResourceLocation.fromNamespaceAndPath(
                    ModernIndustrializationFixtureMod.MODID, "retained_input_overlap");
    private static final ResourceLocation RANDOM_RECIPE = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "materials/antimony/macerator/raw_metal");
    private static final ResourceLocation OVER_TIER_RECIPE = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "materials/mixer/itnt");
    private static final ResourceLocation CHEMICAL_REACTOR = ResourceLocation.fromNamespaceAndPath(
            MagicStorage.MODID, "modern_industrialization_chemical_reactor");
    private static final ResourceLocation CHEMICAL_REACTOR_RECIPE =
            ResourceLocation.fromNamespaceAndPath(
                    "modern_industrialization", "oil/chemical_reactor/ethanol_to_butadiene");
    private static final ResourceLocation BUTADIENE = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "butadiene");
    private static final ResourceLocation HYDROGEN = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "hydrogen");
    private static final ResourceLocation CENTRIFUGE = ResourceLocation.fromNamespaceAndPath(
            MagicStorage.MODID, "modern_industrialization_centrifuge");
    private static final ResourceLocation CENTRIFUGE_RECIPE = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "materials/centrifuge/liquid_air");
    private static final ResourceLocation LIQUID_AIR = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "liquid_air");
    private static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "oxygen");
    private static final ResourceLocation NITROGEN = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "nitrogen");
    private static final ResourceLocation ARGON = ResourceLocation.fromNamespaceAndPath(
            "modern_industrialization", "argon");
    private static final List<String> SINGLE_BLOCK_FAMILIES = List.of(
            "assembler",
            "centrifuge",
            "chemical_reactor",
            "compressor",
            "cutting_machine",
            "distillery",
            "electrolyzer",
            "furnace",
            "macerator",
            "mixer",
            "packer",
            "polarizer",
            "unpacker",
            "wiremill");

    private ModernIndustrializationIntegrationTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void macerator_family_is_registered(GameTestHelper helper) {
        MachineDescriptor descriptor = MagicStorage.MACHINE_DESCRIPTOR_REGISTRY.get(MACERATOR);
        if (descriptor == null || !MagicStorage.RECIPE_FAMILY_REGISTRY.containsKey(MACERATOR)) {
            helper.fail("Modern Industrialization Macerator compatibility was not registered");
            return;
        }
        for (String family : SINGLE_BLOCK_FAMILIES) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    MagicStorage.MODID, "modern_industrialization_" + family);
            if (!MagicStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(id)
                    || !MagicStorage.RECIPE_FAMILY_REGISTRY.containsKey(id)) {
                helper.fail("Modern Industrialization family was not registered: " + family);
                return;
            }
        }
        ItemStack bronze = stack(BRONZE_MACERATOR);
        ItemStack steel = stack(STEEL_MACERATOR);
        ItemStack electric = stack(ELECTRIC_MACERATOR);
        if (!descriptor.accepts(bronze)
                || !descriptor.accepts(steel)
                || !descriptor.accepts(electric)
                || !descriptor.rateFor(bronze).orElseThrow().equals(MachineWorkRate.of(2, 1))
                || !descriptor.rateFor(steel).orElseThrow().equals(MachineWorkRate.of(4, 1))
                || !descriptor.rateFor(electric).orElseThrow().equals(MachineWorkRate.of(8, 1))) {
            helper.fail("Modern Industrialization Macerator variants did not preserve cold base rates");
            return;
        }

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
            ItemStack input = stack(ALUMINUM_BLADE);
            ItemKey inputKey = ItemKey.of(input);
            ItemStack output = stack(ALUMINUM_TINY_DUST);
            StorageResourceKey energy = StorageResourceKey.neoforgeEnergy();
            if (core.insertItem(input) != 1
                    || core.insertResource(energy, 400, Action.EXECUTE) != 400) {
                helper.fail("Could not seed Macerator item and energy inputs");
                return;
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(400, player.getInventory(), core);
            menu.clickMenuButton(player, STATIONS_PAGE_BUTTON);
            var machineSlot = findMachineSlot(menu, bronze);
            if (machineSlot == null) {
                helper.fail("Macerator had no installable terminal slot");
                return;
            }
            machineSlot.set(bronze);
            machineSlot.setChanged();
            for (int tick = 0; tick < 200; tick++) core.tick();
            menu.clickMenuButton(player, STORAGE_PAGE_BUTTON);
            if (!menu.handleRecipeRequest(
                    level, RECIPE_ID, 1, CraftingDestination.STORAGE, player)) {
                helper.fail("Deterministic Macerator recipe did not commit");
                return;
            }
            if (core.getItemCount(inputKey) != 0
                    || core.getResourceAmount(energy) != 0
                    || core.getItemCount(ItemKey.of(output)) != 5
                    || core.getStationWork(MACERATOR) != 0) {
                helper.fail("Macerator commit did not consume exact item, energy, and station work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void fluid_recipe_commits_in_one_typed_transaction(GameTestHelper helper) {
        MachineDescriptor descriptor = MagicStorage.MACHINE_DESCRIPTOR_REGISTRY.get(DISTILLERY);
        if (descriptor == null) {
            helper.fail("Modern Industrialization Distillery compatibility was not registered");
            return;
        }
        ItemStack station = stack(ResourceLocation.fromNamespaceAndPath(
                "modern_industrialization", "distillery"));
        if (!descriptor.accepts(station)
                || !descriptor.rateFor(station).orElseThrow().equals(MachineWorkRate.of(8, 1))) {
            helper.fail("Modern Industrialization Distillery did not preserve its cold base rate");
            return;
        }

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
            StorageResourceKey input = StorageResourceKey.fluid(
                    new FluidStack(BuiltInRegistries.FLUID.get(SUGAR_SOLUTION), 1),
                    level.registryAccess());
            StorageResourceKey output = StorageResourceKey.fluid(
                    new FluidStack(BuiltInRegistries.FLUID.get(ETHANOL), 1),
                    level.registryAccess());
            StorageResourceKey energy = StorageResourceKey.neoforgeEnergy();
            if (core.insertResource(input, 1_000, Action.EXECUTE) != 1_000
                    || core.insertResource(energy, 6_400, Action.EXECUTE) != 6_400) {
                helper.fail("Could not seed Distillery fluid and energy inputs");
                return;
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(401, player.getInventory(), core);
            menu.clickMenuButton(player, STATIONS_PAGE_BUTTON);
            var machineSlot = findMachineSlot(menu, station);
            if (machineSlot == null) {
                helper.fail("Distillery had no installable terminal slot");
                return;
            }
            machineSlot.set(station);
            machineSlot.setChanged();
            for (int tick = 0; tick < 800; tick++) core.tick();
            menu.clickMenuButton(player, STORAGE_PAGE_BUTTON);
            var holder = level.getRecipeManager().byKey(DISTILLERY_RECIPE).orElse(null);
            if (holder == null || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Deterministic Distillery recipe was not classified by its family");
                return;
            }
            boolean selected = menu.handleRecipeRequest(
                    level, DISTILLERY_RECIPE, 1, CraftingDestination.NONE, player);
            int craftable = selected ? menu.computeCraftPreview(core, player).craftable() : -1;
            if (!menu.handleRecipeRequest(
                    level, DISTILLERY_RECIPE, 1, CraftingDestination.STORAGE, player)) {
                helper.fail("Deterministic Distillery recipe did not commit: selected=" + selected
                        + ", craftable=" + craftable
                        + ", input=" + core.getResourceAmount(input)
                        + ", energy=" + core.getResourceAmount(energy)
                        + ", work=" + core.getStationWork(DISTILLERY));
                return;
            }
            if (core.getResourceAmount(input) != 0
                    || core.getResourceAmount(energy) != 0
                    || core.getResourceAmount(output) != 10
                    || core.getStationWork(DISTILLERY) != 0) {
                helper.fail("Distillery commit did not consume exact fluid, energy, and station work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void zero_probability_input_is_retained_as_a_catalyst(GameTestHelper helper) {
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
            ItemStack blackDye = new ItemStack(net.minecraft.world.item.Items.BLACK_DYE);
            ItemKey blackDyeKey = ItemKey.of(blackDye);
            StorageResourceKey benzene = StorageResourceKey.fluid(
                    new FluidStack(BuiltInRegistries.FLUID.get(BENZENE), 1),
                    level.registryAccess());
            StorageResourceKey energy = StorageResourceKey.neoforgeEnergy();
            if (core.insertItem(blackDye) != 1
                    || core.insertResource(benzene, 25, Action.EXECUTE) != 25
                    || core.insertResource(energy, 400, Action.EXECUTE) != 400) {
                helper.fail("Could not seed Mixer catalyst recipe");
                return;
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(402, player.getInventory(), core);
            menu.clickMenuButton(player, STATIONS_PAGE_BUTTON);
            ItemStack station = stack(ResourceLocation.fromNamespaceAndPath(
                    "modern_industrialization", "bronze_mixer"));
            var machineSlot = findMachineSlot(menu, station);
            if (machineSlot == null) {
                helper.fail("Mixer had no installable terminal slot");
                return;
            }
            machineSlot.set(station);
            machineSlot.setChanged();
            for (int tick = 0; tick < 200; tick++) core.tick();
            menu.clickMenuButton(player, STORAGE_PAGE_BUTTON);
            if (!menu.handleRecipeRequest(
                    level, CATALYST_RECIPE, 1, CraftingDestination.STORAGE, player)) {
                helper.fail("Mixer catalyst recipe did not commit");
                return;
            }
            if (core.getItemCount(blackDyeKey) != 2
                    || core.getResourceAmount(benzene) != 0
                    || core.getResourceAmount(energy) != 0
                    || core.getStationWork(MIXER) != 0) {
                helper.fail("Mixer catalyst was consumed instead of retained");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void incompatible_machine_recipes_fail_closed(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var overSlot = recipes.byKey(OVER_SLOT_RECIPE).orElse(null);
        var random = recipes.byKey(RANDOM_RECIPE).orElse(null);
        var overTier = recipes.byKey(OVER_TIER_RECIPE).orElse(null);
        if (overSlot == null || random == null || overTier == null) {
            helper.fail("Modern Industrialization rejection fixtures were not loaded");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(overSlot)
                || CraftingTerminalMenu.supportsRecipeHolder(random)
                || CraftingTerminalMenu.supportsRecipeHolder(overTier)) {
            helper.fail("Unsupported slot, chance, or machine-tier recipe was classified");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void retained_machine_input_overlap_fails_closed(
            GameTestHelper helper
    ) {
        var overlap = helper.getLevel().getRecipeManager()
                .byKey(RETAINED_INPUT_OVERLAP).orElse(null);
        if (overlap == null) {
            helper.fail("Modern Industrialization retained-input overlap fixture was not loaded");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(overlap)) {
            helper.fail("Machine recipe accepted one resource as consumed input and catalyst");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void multi_input_output_recipe_survives_reload_and_commits_atomically(
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
            ItemStack aluminum = stack(ALUMINUM_TINY_DUST);
            StorageResourceKey ethanol = fluid(ETHANOL, level);
            StorageResourceKey energy = StorageResourceKey.neoforgeEnergy();
            if (core.insertItem(aluminum) != 1
                    || core.insertResource(ethanol, 750, Action.EXECUTE) != 750
                    || core.insertResource(energy, 4_800, Action.EXECUTE) != 4_800) {
                helper.fail("Could not seed Chemical Reactor inputs");
                return;
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(403, player.getInventory(), core);
            menu.clickMenuButton(player, STATIONS_PAGE_BUTTON);
            ItemStack station = stack(ResourceLocation.fromNamespaceAndPath(
                    "modern_industrialization", "chemical_reactor"));
            var machineSlot = findMachineSlot(menu, station);
            if (machineSlot == null) {
                helper.fail("Chemical Reactor had no installable terminal slot");
                return;
            }
            machineSlot.set(station);
            machineSlot.setChanged();
            for (int tick = 0; tick < 150; tick++) core.tick();
            if (core.getStationWork(CHEMICAL_REACTOR) != 1_200) {
                helper.fail("Chemical Reactor did not accrue source-derived cold work");
                return;
            }
            var saved = core.saveWithoutMetadata(level.registryAccess());
            level.removeBlockEntity(corePos);
            var reloaded = new StorageCoreBlockEntity(
                    corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState());
            reloaded.loadWithComponents(saved, level.registryAccess());
            level.setBlockEntity(reloaded);
        });
        helper.runAfterDelay(4, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Reloaded Core not found");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey ethanol = fluid(ETHANOL, level);
            StorageResourceKey energy = StorageResourceKey.neoforgeEnergy();
            long restoredWork = core.getStationWork(CHEMICAL_REACTOR);
            if (core.getItemCount(ItemKey.of(stack(ALUMINUM_TINY_DUST))) != 1
                    || core.getResourceAmount(ethanol) != 750
                    || core.getResourceAmount(energy) != 4_800
                    || restoredWork < 1_200
                    || restoredWork >= 2_400
                    || restoredWork % 8 != 0) {
                helper.fail("Chemical Reactor typed state did not survive reload: item="
                        + core.getItemCount(ItemKey.of(stack(ALUMINUM_TINY_DUST)))
                        + ", ethanol=" + core.getResourceAmount(ethanol)
                        + ", energy=" + core.getResourceAmount(energy)
                        + ", work=" + restoredWork);
                return;
            }
            for (long work = restoredWork; work < 2_400; work += 8) core.tick();
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(404, player.getInventory(), core);
            if (!menu.handleRecipeRequest(
                    level, CHEMICAL_REACTOR_RECIPE, 1,
                    CraftingDestination.STORAGE, player)) {
                helper.fail("Chemical Reactor multi-resource recipe did not commit");
                return;
            }
            if (core.getItemCount(ItemKey.of(stack(ALUMINUM_TINY_DUST))) != 0
                    || core.getResourceAmount(ethanol) != 0
                    || core.getResourceAmount(energy) != 0
                    || core.getResourceAmount(fluid(BUTADIENE, level)) != 375
                    || core.getResourceAmount(fluid(ResourceLocation.withDefaultNamespace(
                    "water"), level)) != 750
                    || core.getResourceAmount(fluid(HYDROGEN, level)) != 250
                    || core.getStationWork(CHEMICAL_REACTOR) != 0) {
                helper.fail("Chemical Reactor changed the wrong exact typed resources");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void multi_output_capacity_failure_rolls_back_every_resource(
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
            StorageResourceKey liquidAir = fluid(LIQUID_AIR, level);
            StorageResourceKey energy = StorageResourceKey.neoforgeEnergy();
            if (core.insertResource(liquidAir, 3_000, Action.EXECUTE) != 3_000
                    || core.insertResource(energy, 14_400, Action.EXECUTE) != 14_400) {
                helper.fail("Could not seed Centrifuge typed inputs");
                return;
            }
            for (var item : List.of(
                    Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL,
                    Items.COAL, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND)) {
                if (core.insertItem(new ItemStack(item)) != 1) {
                    helper.fail("Could not fill Centrifuge rollback capacity");
                    return;
                }
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(405, player.getInventory(), core);
            menu.clickMenuButton(player, STATIONS_PAGE_BUTTON);
            ItemStack station = stack(ResourceLocation.fromNamespaceAndPath(
                    "modern_industrialization", "centrifuge"));
            var machineSlot = findMachineSlot(menu, station);
            if (machineSlot == null) {
                helper.fail("Centrifuge had no installable terminal slot");
                return;
            }
            machineSlot.set(station);
            machineSlot.setChanged();
            for (int tick = 0; tick < 600; tick++) core.tick();
            menu.clickMenuButton(player, STORAGE_PAGE_BUTTON);
            if (menu.handleRecipeRequest(
                    level, CENTRIFUGE_RECIPE, 1,
                    CraftingDestination.STORAGE, player)) {
                helper.fail("Capacity-rejected Centrifuge recipe unexpectedly committed");
                return;
            }
            if (core.getResourceAmount(liquidAir) != 3_000
                    || core.getResourceAmount(energy) != 14_400
                    || core.getResourceAmount(fluid(OXYGEN, level)) != 0
                    || core.getResourceAmount(fluid(NITROGEN, level)) != 0
                    || core.getResourceAmount(fluid(ARGON, level)) != 0
                    || core.getStationWork(CENTRIFUGE) != 4_800) {
                helper.fail("Centrifuge capacity failure changed input, output, energy, or work");
                return;
            }
            helper.succeed();
        });
    }

    private static net.minecraft.world.inventory.Slot findMachineSlot(
            CraftingTerminalMenu menu,
            ItemStack station
    ) {
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = menu.getSlot(index);
            if (slot.isActive() && slot.mayPlace(station)) return slot;
        }
        return null;
    }

    private static ItemStack stack(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private static StorageResourceKey fluid(
            ResourceLocation id,
            net.minecraft.world.level.Level level
    ) {
        return StorageResourceKey.fluid(
                new FluidStack(BuiltInRegistries.FLUID.get(id), 1),
                level.registryAccess());
    }
}
