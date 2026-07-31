package com.swear.autostorage.fixture.enderio;

import com.enderio.enderio.config.machines.MachinesConfig;
import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(EnderioFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class EnderioIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final long RECIPE_ENERGY = 3_200L;
    private static final ResourceLocation ALLOY_SMELTING =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "enderio_alloy_smelting");
    private static final ResourceLocation CONDUCTIVE_ALLOY =
            enderioId("alloy_smelting/conductive_alloy_ingot");
    private static final ResourceLocation VANILLA_SMELTING_COPY =
            enderioId("smelting/auto_storage_enderio_fixture/rejection");
    private static final ResourceLocation SAG_MILL =
            enderioId("sag_milling/iron");

    private EnderioIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void alloy_smelting_registers_exact_rate_and_rejects_unsafe(
            GameTestHelper helper
    ) {
        MachineDescriptor descriptor = MachineEnergyTable.get(ALLOY_SMELTING);
        ItemStack smelter = new ItemStack(enderioItem("alloy_smelter"));
        int usage = MachinesConfig.COMMON.ENERGY.ALLOY_SMELTER_USAGE.get();
        if (descriptor == null
                || descriptor.category() != MachineCategory.PROCESS
                || descriptor.maxInstalledCount() != MachineDescriptorApi.MAX_INSTALLED_COUNT
                || descriptor.variants().size() != 1
                || !descriptor.accepts(smelter)
                || !descriptor.rateFor(smelter).orElseThrow().equals(
                MachineWorkRate.of(usage, 1))
                || usage != 20
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(ALLOY_SMELTING)
                || !supports(helper, CONDUCTIVE_ALLOY)
                || supports(helper, VANILLA_SMELTING_COPY)
                || supports(helper, SAG_MILL)) {
            helper.fail(
                    "Ender IO Alloy Smelting registration or rejection boundary was incorrect");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void conductive_alloy_consumes_items_fe_work_without_remainders(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedItem(context.core(), Items.COPPER_INGOT, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), RECIPE_ENERGY);
            installAlloySmelter(context);
            tick(context.core(), ticksFor(RECIPE_ENERGY));
            if (!craft(context, CONDUCTIVE_ALLOY)
                    || itemCount(context.core(), Items.IRON_INGOT) != 0
                    || itemCount(context.core(), Items.COPPER_INGOT) != 0
                    || itemCount(context.core(), enderioItem("conductive_alloy_ingot")) != 2
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(ALLOY_SMELTING) != 0) {
                helper.fail(
                        "Ender IO Alloy Smelting catalyst/tool/remainder behavior was incorrect");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_fe_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedItem(context.core(), Items.COPPER_INGOT, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), RECIPE_ENERGY - 1);
            installAlloySmelter(context);
            tick(context.core(), ticksFor(RECIPE_ENERGY));
            if (craft(context, CONDUCTIVE_ALLOY)
                    || itemCount(context.core(), Items.IRON_INGOT) != 1
                    || itemCount(context.core(), Items.COPPER_INGOT) != 1
                    || itemCount(context.core(), enderioItem("conductive_alloy_ingot")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != RECIPE_ENERGY - 1
                    || context.core().getStationWork(ALLOY_SMELTING) != RECIPE_ENERGY) {
                helper.fail("Ender IO insufficient-FE transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_ingredient_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), RECIPE_ENERGY);
            installAlloySmelter(context);
            tick(context.core(), ticksFor(RECIPE_ENERGY));
            if (craft(context, CONDUCTIVE_ALLOY)
                    || itemCount(context.core(), Items.IRON_INGOT) != 1
                    || itemCount(context.core(), Items.COPPER_INGOT) != 0
                    || itemCount(context.core(), enderioItem("conductive_alloy_ingot")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != RECIPE_ENERGY
                    || context.core().getStationWork(ALLOY_SMELTING) != RECIPE_ENERGY) {
                helper.fail(
                        "Ender IO missing-ingredient transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_station_work_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedItem(context.core(), Items.COPPER_INGOT, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), RECIPE_ENERGY);
            installAlloySmelter(context);
            tick(context.core(), ticksFor(RECIPE_ENERGY) - 1);
            if (craft(context, CONDUCTIVE_ALLOY)
                    || itemCount(context.core(), Items.IRON_INGOT) != 1
                    || itemCount(context.core(), Items.COPPER_INGOT) != 1
                    || itemCount(context.core(), enderioItem("conductive_alloy_ingot")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != RECIPE_ENERGY
                    || context.core().getStationWork(ALLOY_SMELTING)
                    != RECIPE_ENERGY - MachinesConfig.COMMON.ENERGY.ALLOY_SMELTER_USAGE.get()) {
                helper.fail("Ender IO insufficient-work transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void destination_overflow_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedItem(context.core(), Items.COPPER_INGOT, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), RECIPE_ENERGY);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(enderioItem("conductive_alloy_ingot")),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            installAlloySmelter(context);
            tick(context.core(), ticksFor(RECIPE_ENERGY));
            if (craft(context, CONDUCTIVE_ALLOY)
                    || itemCount(context.core(), Items.IRON_INGOT) != 1
                    || itemCount(context.core(), Items.COPPER_INGOT) != 1
                    || itemCount(context.core(), enderioItem("conductive_alloy_ingot"))
                    != Long.MAX_VALUE
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != RECIPE_ENERGY
                    || context.core().getStationWork(ALLOY_SMELTING) != RECIPE_ENERGY) {
                helper.fail(
                        "Ender IO full destination transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    private static boolean supports(GameTestHelper helper, ResourceLocation recipeId) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null) {
            throw new IllegalStateException("Missing recipe " + recipeId);
        }
        return CraftingTerminalMenu.supportsRecipeHolder(holder);
    }

    private static int ticksFor(long work) {
        int usage = MachinesConfig.COMMON.ENERGY.ALLOY_SMELTER_USAGE.get();
        if (usage <= 0) {
            throw new IllegalStateException("Alloy Smelter usage must be positive");
        }
        return Math.toIntExact((work + usage - 1) / usage);
    }

    private static void withCore(GameTestHelper helper, FixtureAssertion assertion) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(
                corePos,
                AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(
                corePos.south(),
                AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(
                    corePos.getX() + 0.5,
                    corePos.getY() + 0.5,
                    corePos.getZ() + 0.5);
            assertion.run(new FixtureContext(level, core, player));
        });
    }

    private static void installAlloySmelter(FixtureContext context) {
        ItemStack station = new ItemStack(enderioItem("alloy_smelter"));
        var menu = new CraftingTerminalMenu(
                940, context.player().getInventory(), context.core());
        menu.clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START
                     + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = menu.getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) {
                continue;
            }
            slot.set(station.copy());
            slot.setChanged();
            menu.clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
            return;
        }
        throw new IllegalStateException("Could not install Ender IO Alloy Smelter");
    }

    private static boolean craft(FixtureContext context, ResourceLocation recipeId) {
        var menu = new CraftingTerminalMenu(
                941, context.player().getInventory(), context.core());
        if (!menu.handleRecipeRequest(
                context.level(), recipeId, 1,
                CraftingDestination.NONE, context.player())) {
            return false;
        }
        if (menu.computeCraftPreview(context.core(), context.player()).craftable() < 1) {
            return false;
        }
        return menu.handleRecipeRequest(
                context.level(), recipeId, 1,
                CraftingDestination.STORAGE, context.player());
    }

    private static void seedItem(StorageCoreBlockEntity core, Item item, int amount) {
        ItemStack stack = new ItemStack(item, amount);
        if (core.insertItem(stack) != amount) {
            throw new IllegalStateException("Could not seed " + item + " x" + amount);
        }
    }

    private static void seedResource(
            StorageCoreBlockEntity core,
            StorageResourceKey key,
            long amount
    ) {
        if (core.insertResource(key, amount, Action.EXECUTE) != amount) {
            throw new IllegalStateException("Could not seed " + key + " x" + amount);
        }
    }

    private static long itemCount(StorageCoreBlockEntity core, Item item) {
        return core.getItemCount(ItemKey.of(new ItemStack(item)));
    }

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            core.tick();
        }
    }

    private static Item enderioItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(enderioId(path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Ender IO item " + path);
        }
        return item;
    }

    private static ResourceLocation enderioId(String path) {
        return ResourceLocation.fromNamespaceAndPath("enderio", path);
    }

    private record FixtureContext(
            net.minecraft.server.level.ServerLevel level,
            StorageCoreBlockEntity core,
            net.minecraft.world.entity.player.Player player
    ) {
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(FixtureContext context);
    }
}
