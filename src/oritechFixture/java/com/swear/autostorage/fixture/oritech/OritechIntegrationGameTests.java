package com.swear.autostorage.fixture.oritech;

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
import rearth.oritech.init.OritechConfig;

@GameTestHolder(OritechFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class OritechIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final ResourceLocation PULVERIZER =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "oritech_pulverizer");
    private static final ResourceLocation GRINDER =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "oritech_grinder");
    private static final ResourceLocation ADAMANT = oritechRecipe("pulverizer/adamant");
    private static final ResourceLocation RAW_IRON = oritechRecipe("pulverizer/raw/iron");
    private static final ResourceLocation GRINDER_IRON = oritechRecipe("grinder/ore/iron");

    private OritechIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void pulverizer_registers_exact_rate_and_non_pulverizer_stays_excluded(
            GameTestHelper helper
    ) {
        MachineDescriptor descriptor = MachineEnergyTable.get(PULVERIZER);
        ItemStack pulverizer = new ItemStack(oritechItem("pulverizer_block"));
        if (descriptor == null
                || descriptor.category() != MachineCategory.PROCESS
                || descriptor.maxInstalledCount() != MachineDescriptorApi.MAX_INSTALLED_COUNT
                || descriptor.variants().size() != 1
                || !descriptor.accepts(pulverizer)
                || !descriptor.rateFor(pulverizer).orElseThrow().equals(
                MachineWorkRate.of(energyPerTick(), 1))
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(PULVERIZER)
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(GRINDER)
                || !supports(helper, ADAMANT)
                || !supports(helper, RAW_IRON)
                || supports(helper, GRINDER_IRON)) {
            helper.fail(
                    "Oritech pulverizer registration or non-pulverizer exclusion was incorrect");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void adamant_consumes_item_fe_and_work(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy(ADAMANT, helper);
            seedItem(context.core(), oritechItem("adamant_ingot"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installPulverizer(context);
            tick(context.core(), recipeTime(ADAMANT, helper));
            if (!craft(context, ADAMANT)
                    || itemCount(context.core(), oritechItem("adamant_ingot")) != 0
                    || itemCount(context.core(), oritechItem("adamant_dust")) != 1
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(PULVERIZER) != 0) {
                helper.fail("Oritech pulverizer did not consume exact adamant costs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void raw_iron_emits_exact_multi_output_remainder(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy(RAW_IRON, helper);
            seedItem(context.core(), Items.RAW_IRON, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installPulverizer(context);
            tick(context.core(), recipeTime(RAW_IRON, helper));
            if (!craft(context, RAW_IRON)
                    || itemCount(context.core(), Items.RAW_IRON) != 0
                    || itemCount(context.core(), oritechItem("iron_dust")) != 1
                    || itemCount(context.core(), oritechItem("small_iron_dust")) != 3
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(PULVERIZER) != 0) {
                helper.fail("Oritech pulverizer did not emit exact multi-output remainder");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_fe_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy(ADAMANT, helper) - 1;
            long work = expectedEnergy(ADAMANT, helper);
            seedItem(context.core(), oritechItem("adamant_ingot"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installPulverizer(context);
            tick(context.core(), recipeTime(ADAMANT, helper));
            if (craft(context, ADAMANT)
                    || itemCount(context.core(), oritechItem("adamant_ingot")) != 1
                    || itemCount(context.core(), oritechItem("adamant_dust")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(PULVERIZER) != work) {
                helper.fail("Oritech insufficient-FE transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_ingredient_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy(ADAMANT, helper);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installPulverizer(context);
            tick(context.core(), recipeTime(ADAMANT, helper));
            if (craft(context, ADAMANT)
                    || itemCount(context.core(), oritechItem("adamant_ingot")) != 0
                    || itemCount(context.core(), oritechItem("adamant_dust")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(PULVERIZER) != energy) {
                helper.fail("Oritech missing-ingredient transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_station_work_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy(ADAMANT, helper);
            int ticks = recipeTime(ADAMANT, helper) - 1;
            seedItem(context.core(), oritechItem("adamant_ingot"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installPulverizer(context);
            tick(context.core(), ticks);
            if (craft(context, ADAMANT)
                    || itemCount(context.core(), oritechItem("adamant_ingot")) != 1
                    || itemCount(context.core(), oritechItem("adamant_dust")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(PULVERIZER)
                    != (long) energyPerTick() * ticks) {
                helper.fail("Oritech insufficient-work transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void destination_overflow_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy(ADAMANT, helper);
            seedItem(context.core(), oritechItem("adamant_ingot"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(oritechItem("adamant_dust")),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            installPulverizer(context);
            tick(context.core(), recipeTime(ADAMANT, helper));
            if (craft(context, ADAMANT)
                    || itemCount(context.core(), oritechItem("adamant_ingot")) != 1
                    || itemCount(context.core(), oritechItem("adamant_dust")) != Long.MAX_VALUE
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(PULVERIZER) != energy) {
                helper.fail("Oritech full destination transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void runtime_pulverizer_energy_maps_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        int perTick = energyPerTick();
        long adamant = expectedEnergy(ADAMANT, helper);
        long rawIron = expectedEnergy(RAW_IRON, helper);
        if (perTick <= 0
                || adamant != Math.multiplyExact((long) perTick, recipeTime(ADAMANT, helper))
                || rawIron != Math.multiplyExact((long) perTick, recipeTime(RAW_IRON, helper))
                || adamant <= 0
                || rawIron <= 0) {
            helper.fail("Oritech runtime pulverizer energy was not exact FE/work");
            return;
        }
        helper.succeed();
    }

    private static boolean supports(GameTestHelper helper, ResourceLocation recipeId) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        return CraftingTerminalMenu.supportsRecipeHolder(holder);
    }

    private static long expectedEnergy(ResourceLocation recipeId, GameTestHelper helper) {
        return Math.multiplyExact((long) energyPerTick(), recipeTime(recipeId, helper));
    }

    private static int recipeTime(ResourceLocation recipeId, GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null
                || !(holder.value() instanceof rearth.oritech.init.recipes.OritechRecipe recipe)
                || recipe.getTime() <= 0) {
            throw new IllegalStateException("Missing Oritech recipe " + recipeId);
        }
        return recipe.getTime();
    }

    private static int energyPerTick() {
        return OritechConfig.processingMachines.pulverizerData.energyPerTick.get();
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

    private static void installPulverizer(FixtureContext context) {
        ItemStack station = new ItemStack(oritechItem("pulverizer_block"));
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
        throw new IllegalStateException("Could not install Oritech Pulverizer");
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
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item oritechItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(oritechId(path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Oritech item " + path);
        }
        return item;
    }

    private static ResourceLocation oritechRecipe(String path) {
        return oritechId(path);
    }

    private static ResourceLocation oritechId(String path) {
        return ResourceLocation.fromNamespaceAndPath("oritech", path);
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
