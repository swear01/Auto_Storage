package com.swear.autostorage.fixture.createaddition;

import com.mrh0.createaddition.config.CommonConfig;
import com.mrh0.createaddition.recipe.charging.ChargingRecipe;
import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
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

@GameTestHolder(CreateadditionFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class CreateadditionIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final ResourceLocation ROLLING_MILL =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "createaddition_rolling_mill");
    private static final ResourceLocation TESLA_COIL =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "createaddition_tesla_coil");
    private static final ResourceLocation LIQUID_BURNER =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "createaddition_liquid_blaze_burner");
    private static final ResourceLocation IRON_ROD = createadditionRecipe("rolling/iron_ingot");
    private static final ResourceLocation ELECTRUM_NUGGET =
            createadditionRecipe("charging/electrify_gold_nugget");
    private static final ResourceLocation PLANTOIL =
            createadditionRecipe("liquid_burning/plantoil");

    private CreateadditionIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void rolling_and_charging_register_and_liquid_burning_stays_excluded(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                CreateadditionIntegrationGameTests.class);
        MachineDescriptor rolling = MachineEnergyTable.get(ROLLING_MILL);
        MachineDescriptor charging = MachineEnergyTable.get(TESLA_COIL);
        ItemStack rollingMill = new ItemStack(createadditionItem("rolling_mill"));
        ItemStack teslaCoil = new ItemStack(createadditionItem("tesla_coil"));
        if (rolling == null
                || charging == null
                || rolling.category() != MachineCategory.PROCESS
                || charging.category() != MachineCategory.PROCESS
                || rolling.maxInstalledCount() != MachineDescriptorApi.MAX_INSTALLED_COUNT
                || charging.maxInstalledCount() != MachineDescriptorApi.MAX_INSTALLED_COUNT
                || rolling.variants().size() != 1
                || charging.variants().size() != 1
                || !rolling.accepts(rollingMill)
                || !charging.accepts(teslaCoil)
                || !rolling.rateFor(rollingMill).orElseThrow().equals(MachineWorkRate.ONE)
                || !charging.rateFor(teslaCoil).orElseThrow().equals(MachineWorkRate.ONE)
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(ROLLING_MILL)
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(TESLA_COIL)
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(LIQUID_BURNER)
                || !supports(helper, IRON_ROD)
                || !supports(helper, ELECTRUM_NUGGET)
                || supports(helper, PLANTOIL)) {
            helper.fail(
                    "Create Crafts & Additions rolling/charging registration was incorrect");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void rolling_iron_ingot_emits_exact_wire_output(GameTestHelper helper) {
        withCore(helper, context -> {
            long work = rollingDuration();
            seedItem(context.core(), Items.IRON_INGOT, 1);
            installStation(context, "rolling_mill", ROLLING_MILL);
            tick(context.core(), (int) work);
            if (!craft(context, IRON_ROD)
                    || itemCount(context.core(), Items.IRON_INGOT) != 0
                    || itemCount(context.core(), createadditionItem("iron_rod")) != 2
                    || context.core().getStationWork(ROLLING_MILL) != 0) {
                helper.fail("Create Crafts & Additions rolling did not emit exact wire output");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void charging_consumes_exact_fe_and_work(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = chargingEnergy(ELECTRUM_NUGGET, helper);
            long work = chargingWork(ELECTRUM_NUGGET, helper);
            seedItem(context.core(), Items.GOLD_NUGGET, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installStation(context, "tesla_coil", TESLA_COIL);
            tick(context.core(), (int) work);
            if (!craft(context, ELECTRUM_NUGGET)
                    || itemCount(context.core(), Items.GOLD_NUGGET) != 0
                    || itemCount(context.core(), createadditionItem("electrum_nugget")) != 1
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(TESLA_COIL) != 0) {
                helper.fail("Create Crafts & Additions charging did not consume exact FE/work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_ingredient_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long work = rollingDuration();
            installStation(context, "rolling_mill", ROLLING_MILL);
            tick(context.core(), (int) work);
            if (craft(context, IRON_ROD)
                    || itemCount(context.core(), Items.IRON_INGOT) != 0
                    || itemCount(context.core(), createadditionItem("iron_rod")) != 0
                    || context.core().getStationWork(ROLLING_MILL) != work) {
                helper.fail(
                        "Create Crafts & Additions missing-ingredient transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_fe_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = chargingEnergy(ELECTRUM_NUGGET, helper) - 1;
            long work = chargingWork(ELECTRUM_NUGGET, helper);
            seedItem(context.core(), Items.GOLD_NUGGET, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installStation(context, "tesla_coil", TESLA_COIL);
            tick(context.core(), (int) work);
            if (craft(context, ELECTRUM_NUGGET)
                    || itemCount(context.core(), Items.GOLD_NUGGET) != 1
                    || itemCount(context.core(), createadditionItem("electrum_nugget")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(TESLA_COIL) != work) {
                helper.fail(
                        "Create Crafts & Additions insufficient-FE transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void destination_overflow_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long work = rollingDuration();
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(createadditionItem("iron_rod")),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            installStation(context, "rolling_mill", ROLLING_MILL);
            tick(context.core(), (int) work);
            if (craft(context, IRON_ROD)
                    || itemCount(context.core(), Items.IRON_INGOT) != 1
                    || itemCount(context.core(), createadditionItem("iron_rod")) != Long.MAX_VALUE
                    || context.core().getStationWork(ROLLING_MILL) != work) {
                helper.fail(
                        "Create Crafts & Additions full destination transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void checked_overflow_rejects_long_max_seed(GameTestHelper helper) {
        withCore(helper, context -> {
            long work = rollingDuration();
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(createadditionItem("iron_rod")),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            installStation(context, "rolling_mill", ROLLING_MILL);
            tick(context.core(), (int) work);
            if (craft(context, IRON_ROD)
                    || itemCount(context.core(), createadditionItem("iron_rod")) != Long.MAX_VALUE) {
                helper.fail("Create Crafts & Additions Long.MAX_VALUE overflow was not rejected");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void stale_holder_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long work = rollingDuration();
            seedItem(context.core(), Items.IRON_INGOT, 1);
            installStation(context, "rolling_mill", ROLLING_MILL);
            tick(context.core(), (int) work);
            ResourceLocation missing = createadditionRecipe("rolling/missing_stale_holder");
            if (craft(context, missing)
                    || itemCount(context.core(), Items.IRON_INGOT) != 1
                    || itemCount(context.core(), createadditionItem("iron_rod")) != 0
                    || context.core().getStationWork(ROLLING_MILL) != work) {
                helper.fail(
                        "Create Crafts & Additions stale holder transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    private static boolean supports(GameTestHelper helper, ResourceLocation recipeId) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        return CraftingTerminalMenu.supportsRecipeHolder(holder);
    }

    private static long rollingDuration() {
        return CommonConfig.ROLLING_MILL_PROCESSING_DURATION.get();
    }

    private static long chargingEnergy(ResourceLocation recipeId, GameTestHelper helper) {
        return chargingRecipe(recipeId, helper).getEnergy();
    }

    private static long chargingWork(ResourceLocation recipeId, GameTestHelper helper) {
        ChargingRecipe recipe = chargingRecipe(recipeId, helper);
        long rate = Math.min(
                CommonConfig.TESLA_COIL_RECIPE_CHARGE_RATE.get(),
                recipe.getMaxChargeRate());
        long energy = recipe.getEnergy();
        return Math.addExact(energy, rate - 1L) / rate;
    }

    private static ChargingRecipe chargingRecipe(
            ResourceLocation recipeId,
            GameTestHelper helper
    ) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof ChargingRecipe recipe)) {
            throw new IllegalStateException("Missing charging recipe " + recipeId);
        }
        return recipe;
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

    private static void installStation(
            FixtureContext context,
            String itemPath,
            ResourceLocation descriptorId
    ) {
        ItemStack station = new ItemStack(createadditionItem(itemPath));
        var menu = new CraftingTerminalMenu(
                930, context.player().getInventory(), context.core());
        menu.clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START
                     + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = menu.getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) {
                continue;
            }
            MachineDescriptor descriptor = MachineEnergyTable.get(descriptorId);
            if (descriptor == null || !descriptor.accepts(station)) {
                continue;
            }
            slot.set(station.copy());
            slot.setChanged();
            menu.clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
            return;
        }
        throw new IllegalStateException("Could not install " + itemPath);
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

    private static Item createadditionItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(createadditionId(path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Create Crafts & Additions item " + path);
        }
        return item;
    }

    private static ResourceLocation createadditionRecipe(String path) {
        return createadditionId(path);
    }

    private static ResourceLocation createadditionId(String path) {
        return ResourceLocation.fromNamespaceAndPath("createaddition", path);
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
