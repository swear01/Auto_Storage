package com.swear.autostorage.fixture.theurgy;

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
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(TheurgyFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class TheurgyIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final Map<String, String> STATIONS = Map.of(
            "theurgy_calcination_oven", "calcination_oven",
            "theurgy_distiller", "distiller",
            "theurgy_liquefaction_cauldron", "liquefaction_cauldron");
    private static final ResourceLocation CALCINATION_RECIPE = theurgyId(
            "calcination/alchemical_salt_creature_from_plant_salt");
    private static final ResourceLocation DISTILLATION_RECIPE = theurgyId(
            "distillation/bread");
    private static final ResourceLocation LIQUEFACTION_RECIPE = theurgyId(
            "liquefaction/alchemical_sulfur_acacia_log_from_acacia_log");

    private TheurgyIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void registers_accepted_families_and_rejects_unsafe_slice(
            GameTestHelper helper
    ) {
        for (var entry : STATIONS.entrySet()) {
            if (!validStation(autoStorage(entry.getKey()), entry.getValue())
                    || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                    autoStorage(entry.getKey()))) {
                helper.fail(
                        "Theurgy Calcination/Distillation/Liquefaction registration or rejected-family exclusion was incorrect");
                return;
            }
        }
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                autoStorage("theurgy_incubation"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                autoStorage("theurgy_reformation"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                autoStorage("theurgy_catalysation"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                autoStorage("theurgy_digestion"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                autoStorage("theurgy_fermentation"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                autoStorage("theurgy_accumulation"))
                || supports(helper, theurgyId("catalysation/mercury_flux_from_mercury_shard"))
                || supports(helper, theurgyId("incubation/acacia_log"))
                || supports(helper, theurgyId(
                "reformation/alchemical_niter_animals_abundant_from_alchemical_niter_crops_abundant"))) {
            helper.fail(
                    "Theurgy Calcination/Distillation/Liquefaction registration or rejected-family exclusion was incorrect");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void calcination_consumes_sized_input_and_work(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), theurgyItem("alchemical_salt_plant"), 2);
            installStation(context, "calcination_oven");
            tick(context.core(), 100);
            if (!craft(context, CALCINATION_RECIPE)
                    || itemCount(context.core(), theurgyItem("alchemical_salt_plant")) != 0
                    || itemCount(context.core(), theurgyItem("alchemical_salt_creature")) != 1
                    || context.core().getStationWork(
                    autoStorage("theurgy_calcination_oven")) != 0) {
                helper.fail("Theurgy Calcination did not consume exact sized input and work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void distillation_consumes_item_and_work(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.BREAD, 1);
            installStation(context, "distiller");
            tick(context.core(), 100);
            if (!craft(context, DISTILLATION_RECIPE)
                    || itemCount(context.core(), Items.BREAD) != 0
                    || itemCount(context.core(), theurgyItem("mercury_shard")) != 1
                    || context.core().getStationWork(
                    autoStorage("theurgy_distiller")) != 0) {
                helper.fail("Theurgy Distillation did not consume exact item and work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void liquefaction_consumes_item_solvent_and_work(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            StorageResourceKey solvent = fluidKey(context, theurgyId("sal_ammoniac"));
            seedItem(context.core(), Items.ACACIA_LOG, 1);
            seedResource(context.core(), solvent, 10);
            installStation(context, "liquefaction_cauldron");
            tick(context.core(), 100);
            if (!craft(context, LIQUEFACTION_RECIPE)
                    || itemCount(context.core(), Items.ACACIA_LOG) != 0
                    || itemCount(context.core(), theurgyItem("alchemical_sulfur_acacia_log"))
                    != 1
                    || context.core().getResourceAmount(solvent) != 0
                    || context.core().getStationWork(
                    autoStorage("theurgy_liquefaction_cauldron")) != 0) {
                helper.fail(
                        "Theurgy Liquefaction did not consume exact item and solvent amounts");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_ingredient_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), theurgyItem("alchemical_salt_plant"), 1);
            installStation(context, "calcination_oven");
            tick(context.core(), 100);
            if (craft(context, CALCINATION_RECIPE)
                    || itemCount(context.core(), theurgyItem("alchemical_salt_plant")) != 1
                    || itemCount(context.core(), theurgyItem("alchemical_salt_creature")) != 0
                    || context.core().getStationWork(
                    autoStorage("theurgy_calcination_oven")) != 100) {
                helper.fail("Theurgy missing-ingredient transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_solvent_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            StorageResourceKey solvent = fluidKey(context, theurgyId("sal_ammoniac"));
            seedItem(context.core(), Items.ACACIA_LOG, 1);
            seedResource(context.core(), solvent, 9);
            installStation(context, "liquefaction_cauldron");
            tick(context.core(), 100);
            if (craft(context, LIQUEFACTION_RECIPE)
                    || itemCount(context.core(), Items.ACACIA_LOG) != 1
                    || itemCount(context.core(), theurgyItem("alchemical_sulfur_acacia_log"))
                    != 0
                    || context.core().getResourceAmount(solvent) != 9
                    || context.core().getStationWork(
                    autoStorage("theurgy_liquefaction_cauldron")) != 100) {
                helper.fail("Theurgy missing-ingredient transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_work_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.BREAD, 1);
            installStation(context, "distiller");
            tick(context.core(), 99);
            if (craft(context, DISTILLATION_RECIPE)
                    || itemCount(context.core(), Items.BREAD) != 1
                    || itemCount(context.core(), theurgyItem("mercury_shard")) != 0
                    || context.core().getStationWork(
                    autoStorage("theurgy_distiller")) != 99) {
                helper.fail("Theurgy insufficient-work transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void destination_overflow_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.BREAD, 1);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(theurgyItem("mercury_shard")),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            installStation(context, "distiller");
            tick(context.core(), 100);
            if (craft(context, DISTILLATION_RECIPE)
                    || itemCount(context.core(), Items.BREAD) != 1
                    || itemCount(context.core(), theurgyItem("mercury_shard"))
                    != Long.MAX_VALUE
                    || context.core().getStationWork(
                    autoStorage("theurgy_distiller")) != 100) {
                helper.fail("Theurgy full destination transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void long_overflow_seed_stays_exact(GameTestHelper helper) {
        withCore(helper, context -> {
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(theurgyItem("mercury_shard")),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            if (itemCount(context.core(), theurgyItem("mercury_shard")) != Long.MAX_VALUE) {
                helper.fail("Long.MAX_VALUE Theurgy seed was not preserved exactly");
                return;
            }
            helper.succeed();
        });
    }

    private static boolean validStation(ResourceLocation stationId, String itemPath) {
        MachineDescriptor descriptor = MachineEnergyTable.get(stationId);
        ItemStack station = new ItemStack(theurgyItem(itemPath));
        return descriptor != null
                && descriptor.category() == MachineCategory.PROCESS
                && descriptor.maxInstalledCount() == MachineDescriptorApi.MAX_INSTALLED_COUNT
                && descriptor.energyType() == null
                && descriptor.variants().size() == 1
                && descriptor.accepts(station)
                && descriptor.rateFor(station).orElseThrow().equals(MachineWorkRate.ONE);
    }

    private static boolean supports(GameTestHelper helper, ResourceLocation recipeId) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null) throw new IllegalStateException("Missing recipe " + recipeId);
        return CraftingTerminalMenu.supportsRecipeHolder(holder);
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

    private static void installStation(FixtureContext context, String itemPath) {
        ItemStack station = new ItemStack(theurgyItem(itemPath));
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
        throw new IllegalStateException("Could not install Theurgy station " + itemPath);
    }

    private static boolean craft(FixtureContext context, ResourceLocation recipeId) {
        var menu = new CraftingTerminalMenu(
                931, context.player().getInventory(), context.core());
        if (!menu.handleRecipeRequest(
                context.level(), recipeId, 1, CraftingDestination.NONE, context.player())) {
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

    private static StorageResourceKey fluidKey(
            FixtureContext context,
            ResourceLocation fluidId
    ) {
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid == null) throw new IllegalStateException("Missing fluid " + fluidId);
        return StorageResourceKey.fluid(
                new FluidStack(fluid, 1), context.level().registryAccess());
    }

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item theurgyItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(theurgyId(path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Theurgy item " + path);
        }
        return item;
    }

    private static ResourceLocation theurgyId(String path) {
        return ResourceLocation.fromNamespaceAndPath("theurgy", path);
    }

    private static ResourceLocation autoStorage(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path);
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
