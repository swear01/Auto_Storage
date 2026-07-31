package com.swear.autostorage.fixture.ae2;

import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
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

@GameTestHolder(Ae2FixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class Ae2IntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final ResourceLocation INSCRIBER =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "ae2_inscriber");
    private static final ResourceLocation CHARGER =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "ae2_charger");
    private static final ResourceLocation INSCRIBE_RECIPE = ae2Id(
            "inscriber/logic_processor_print");
    private static final ResourceLocation PRESS_RECIPE = ae2Id(
            "inscriber/logic_processor");

    private Ae2IntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void inscriber_registers_exact_rate_and_charger_stays_excluded(
            GameTestHelper helper
    ) {
        MachineDescriptor descriptor = MachineEnergyTable.get(INSCRIBER);
        ItemStack inscriber = new ItemStack(ae2Item("inscriber"));
        if (descriptor == null
                || descriptor.category() != MachineCategory.PROCESS
                || descriptor.maxInstalledCount() != MachineDescriptorApi.MAX_INSTALLED_COUNT
                || descriptor.variants().size() != 1
                || !descriptor.accepts(inscriber)
                || !descriptor.rateFor(inscriber).orElseThrow().equals(
                MachineWorkRate.of(2, 1))
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(INSCRIBER)
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(CHARGER)) {
            helper.fail("AE2 Inscriber registration or Charger exclusion was incorrect");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void inscribe_retains_press_and_consumes_middle_fe_and_work(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            long energy = expectedEnergy();
            seedItem(context.core(), Items.GOLD_INGOT, 1);
            seedItem(context.core(), ae2Item("logic_processor_press"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installInscriber(context);
            tick(context.core(), 100);
            if (!craft(context, INSCRIBE_RECIPE)
                    || itemCount(context.core(), Items.GOLD_INGOT) != 0
                    || itemCount(context.core(), ae2Item("logic_processor_press")) != 1
                    || itemCount(context.core(), ae2Item("printed_logic_processor")) != 1
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(INSCRIBER) != 0) {
                helper.fail("AE2 INSCRIBE did not preserve its press or consume exact costs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void press_consumes_all_three_items_fe_and_work(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy();
            seedPressInputs(context.core());
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installInscriber(context);
            tick(context.core(), 100);
            if (!craft(context, PRESS_RECIPE)
                    || itemCount(context.core(), Items.REDSTONE) != 0
                    || itemCount(context.core(), ae2Item("printed_logic_processor")) != 0
                    || itemCount(context.core(), ae2Item("printed_silicon")) != 0
                    || itemCount(context.core(), ae2Item("logic_processor")) != 1
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(INSCRIBER) != 0) {
                helper.fail("AE2 PRESS did not consume exact inputs and costs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_fe_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy() - 1;
            seedPressInputs(context.core());
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installInscriber(context);
            tick(context.core(), 100);
            if (craft(context, PRESS_RECIPE)
                    || !pressInputsEqual(context.core(), 1)
                    || itemCount(context.core(), ae2Item("logic_processor")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(INSCRIBER) != 200) {
                helper.fail("AE2 insufficient-FE transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_press_middle_ingredient_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy();
            seedItem(context.core(), ae2Item("printed_logic_processor"), 1);
            seedItem(context.core(), ae2Item("printed_silicon"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installInscriber(context);
            tick(context.core(), 100);
            if (craft(context, PRESS_RECIPE)
                    || itemCount(context.core(), Items.REDSTONE) != 0
                    || itemCount(context.core(), ae2Item("printed_logic_processor")) != 1
                    || itemCount(context.core(), ae2Item("printed_silicon")) != 1
                    || itemCount(context.core(), ae2Item("logic_processor")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(INSCRIBER) != 200) {
                helper.fail("AE2 missing-ingredient transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_station_work_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy();
            seedPressInputs(context.core());
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installInscriber(context);
            tick(context.core(), 99);
            if (craft(context, PRESS_RECIPE)
                    || !pressInputsEqual(context.core(), 1)
                    || itemCount(context.core(), ae2Item("logic_processor")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(INSCRIBER) != 198) {
                helper.fail("AE2 insufficient-work transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void destination_overflow_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy();
            seedPressInputs(context.core());
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(ae2Item("logic_processor")),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            installInscriber(context);
            tick(context.core(), 100);
            if (craft(context, PRESS_RECIPE)
                    || !pressInputsEqual(context.core(), 1)
                    || itemCount(context.core(), ae2Item("logic_processor")) != Long.MAX_VALUE
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(INSCRIBER) != 200) {
                helper.fail("AE2 full destination transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void runtime_power_configuration_maps_to_exact_fe(GameTestHelper helper) {
        double configuredAe = PowerMultiplier.CONFIG.multiply(2_000.0D);
        double convertedFe = PowerUnit.AE.convertTo(PowerUnit.FE, configuredAe);
        long expected = expectedEnergy();
        if (!Double.isFinite(convertedFe)
                || convertedFe != Math.rint(convertedFe)
                || expected != (long) convertedFe
                || expected <= 0) {
            helper.fail("AE2 runtime power configuration was not represented as exact FE");
            return;
        }
        helper.succeed();
    }

    private static long expectedEnergy() {
        double converted = PowerUnit.AE.convertTo(
                PowerUnit.FE, PowerMultiplier.CONFIG.multiply(2_000.0D));
        if (!Double.isFinite(converted)
                || converted <= 0
                || converted > Long.MAX_VALUE
                || converted != Math.rint(converted)) {
            throw new IllegalStateException(
                    "AE2 Inscriber power cost cannot be represented as exact FE: " + converted);
        }
        return (long) converted;
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

    private static void installInscriber(FixtureContext context) {
        ItemStack station = new ItemStack(ae2Item("inscriber"));
        var menu = new CraftingTerminalMenu(
                930, context.player().getInventory(), context.core());
        menu.clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START
                     + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = menu.getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) continue;
            slot.set(station.copy());
            slot.setChanged();
            menu.clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
            return;
        }
        throw new IllegalStateException("Could not install AE2 Inscriber");
    }

    private static boolean craft(FixtureContext context, ResourceLocation recipeId) {
        var menu = new CraftingTerminalMenu(
                931, context.player().getInventory(), context.core());
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

    private static void seedPressInputs(StorageCoreBlockEntity core) {
        seedItem(core, Items.REDSTONE, 1);
        seedItem(core, ae2Item("printed_logic_processor"), 1);
        seedItem(core, ae2Item("printed_silicon"), 1);
    }

    private static boolean pressInputsEqual(StorageCoreBlockEntity core, long amount) {
        return itemCount(core, Items.REDSTONE) == amount
                && itemCount(core, ae2Item("printed_logic_processor")) == amount
                && itemCount(core, ae2Item("printed_silicon")) == amount;
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
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item ae2Item(String path) {
        Item item = BuiltInRegistries.ITEM.get(ae2Id(path));
        if (item == Items.AIR) throw new IllegalStateException("Missing AE2 item " + path);
        return item;
    }

    private static ResourceLocation ae2Id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ae2", path);
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
