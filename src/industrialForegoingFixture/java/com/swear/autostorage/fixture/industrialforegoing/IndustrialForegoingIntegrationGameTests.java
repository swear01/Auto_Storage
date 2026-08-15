package com.swear.autostorage.fixture.industrialforegoing;

import com.swear.autostorage.MachineCategory;
import com.buuz135.industrial.config.machine.core.DissolutionChamberConfig;
import com.buuz135.industrial.config.machine.resourceproduction.MaterialStoneWorkFactoryConfig;
import com.swear.autostorage.Action;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(IndustrialForegoingFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class IndustrialForegoingIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final ResourceLocation DISSOLUTION =
            stationId("dissolution_chamber");
    private static final ResourceLocation STONEWORK =
            stationId("material_stonework_factory");
    private static final ResourceLocation PINK_SLIME_BALL =
            ifRecipe("dissolution_chamber/pink_slime_ball");
    private static final ResourceLocation XP_BOTTLES =
            ifRecipe("dissolution_chamber/xp_bottles");
    private static final ResourceLocation OBSIDIAN =
            ifRecipe("stonework_generate/obsidian");
    private static final ResourceLocation NETHERRACK =
            ifRecipe("stonework_generate/netherrack");
    private static final ResourceLocation CRUSH_COBBLESTONE =
            ifRecipe("crusher/cobblestone");

    private IndustrialForegoingIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void registers_only_audited_families_and_base_stations(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), IndustrialForegoingIntegrationGameTests.class);
        MachineDescriptor dissolution = MachineEnergyTable.get(DISSOLUTION);
        MachineDescriptor stonework = MachineEnergyTable.get(STONEWORK);
        if (!validStation(dissolution, "dissolution_chamber")
                || !validStation(stonework, "material_stonework_factory")
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(DISSOLUTION)
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                        stationId("stonework_generate"))
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                        stationId("crusher"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                        stationId("fluid_extractor"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                        stationId("laser_drill_ore"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                        stationId("laser_drill_fluid"))) {
            helper.fail("Industrial Foregoing registrations did not match the audited boundary");
            return;
        }
        if (!supports(helper, PINK_SLIME_BALL)
                || !supports(helper, XP_BOTTLES)
                || !supports(helper, OBSIDIAN)
                || !supports(helper, CRUSH_COBBLESTONE)
                || supports(helper, NETHERRACK)
                || supports(helper, fixtureRecipe("empty_output"))
                || supports(helper, fixtureRecipe("zero_time"))
                || supports(helper, fixtureRecipe("ambiguous_crusher"))) {
            helper.fail("Industrial Foregoing recipe eligibility did not fail closed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void dissolution_commits_item_fluid_fe_and_work_atomically(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.GLASS_PANE, 1);
            StorageResourceKey pinkSlime = fluidKey(context, ifId("pink_slime"));
            StorageResourceKey water = fluidKey(context, ResourceLocation.withDefaultNamespace("water"));
            long energy = 200L * DissolutionChamberConfig.powerPerTick;
            seedResource(context.core(), pinkSlime, 300);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            if (!installStation(context, ifItem("dissolution_chamber"))) {
                helper.fail("Could not install the Dissolution Chamber");
                return;
            }
            tick(context.core(), 200);
            if (!craft(context, PINK_SLIME_BALL, 1)
                    || itemCount(context.core(), Items.GLASS_PANE) != 0
                    || itemCount(context.core(), ifItem("pink_slime")) != 1
                    || context.core().getResourceAmount(pinkSlime) != 0
                    || context.core().getResourceAmount(water) != 150
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(DISSOLUTION) != 0) {
                helper.fail("Dissolution Chamber committed the wrong typed transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void dissolution_groups_eight_slots_without_crafting_remainders(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedResource(
                    context.core(), itemKey(context, new ItemStack(Items.WATER_BUCKET)), 8);
            StorageResourceKey water = fluidKey(
                    context, ResourceLocation.withDefaultNamespace("water"));
            seedResource(context.core(), water, 100);
            seedResource(
                    context.core(),
                    StorageResourceKey.neoforgeEnergy(),
                    10L * DissolutionChamberConfig.powerPerTick);
            installStation(context, ifItem("dissolution_chamber"));
            tick(context.core(), 10);
            if (!craft(context, fixtureRecipe("eight_buckets"), 1)
                    || itemCount(context.core(), Items.WATER_BUCKET) != 0
                    || itemCount(context.core(), Items.BUCKET) != 0
                    || itemCount(context.core(), Items.LAPIS_LAZULI) != 2
                    || context.core().getResourceAmount(water) != 0) {
                helper.fail("Dissolution slot grouping or no-remainder behavior was wrong");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void dissolution_accepts_fluid_tag_without_item_input(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            StorageResourceKey essence = fluidKey(context, ifId("essence"));
            seedResource(context.core(), essence, 250);
            seedResource(
                    context.core(),
                    StorageResourceKey.neoforgeEnergy(),
                    5L * DissolutionChamberConfig.powerPerTick);
            installStation(context, ifItem("dissolution_chamber"));
            tick(context.core(), 5);
            if (!craft(context, XP_BOTTLES, 1)
                    || context.core().getResourceAmount(essence) != 0
                    || itemCount(context.core(), Items.EXPERIENCE_BOTTLE) != 1) {
                helper.fail("Dissolution fluid-tag recipe committed the wrong resources");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void stonework_retains_threshold_fluid_and_consumes_other_fluid(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            StorageResourceKey water = fluidKey(
                    context, ResourceLocation.withDefaultNamespace("water"));
            StorageResourceKey lava = fluidKey(
                    context, ResourceLocation.withDefaultNamespace("lava"));
            long energy = (long) MaterialStoneWorkFactoryConfig.maxProgress
                    * MaterialStoneWorkFactoryConfig.powerPerTick;
            seedResource(context.core(), water, 1_000);
            seedResource(context.core(), lava, 1_000);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installStation(context, ifItem("material_stonework_factory"));
            tick(context.core(), MaterialStoneWorkFactoryConfig.maxProgress);
            if (!craft(context, OBSIDIAN, 1)
                    || context.core().getResourceAmount(water) != 1_000
                    || context.core().getResourceAmount(lava) != 0
                    || itemCount(context.core(), Items.OBSIDIAN) != 1
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(STONEWORK) != 0) {
                helper.fail("Stonework fluid threshold/consume transaction was wrong");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void stonework_partial_threshold_rejects_without_mutation(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            StorageResourceKey water = fluidKey(
                    context, ResourceLocation.withDefaultNamespace("water"));
            StorageResourceKey lava = fluidKey(
                    context, ResourceLocation.withDefaultNamespace("lava"));
            seedResource(context.core(), water, 250);
            seedResource(context.core(), lava, 400);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), 3_600);
            installStation(context, ifItem("material_stonework_factory"));
            tick(context.core(), 60);
            if (craft(context, NETHERRACK, 1)
                    || context.core().getResourceAmount(water) != 250
                    || context.core().getResourceAmount(lava) != 400
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 3_600
                    || context.core().getStationWork(STONEWORK) != 60) {
                helper.fail("Rejected partial-threshold Stonework recipe mutated storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void crusher_uses_material_stonework_factory_work_and_fe(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            long energy = (long) MaterialStoneWorkFactoryConfig.maxProgress
                    * MaterialStoneWorkFactoryConfig.powerPerTick;
            seedItem(context.core(), Items.COBBLESTONE, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installStation(context, ifItem("material_stonework_factory"));
            tick(context.core(), MaterialStoneWorkFactoryConfig.maxProgress);
            if (!craft(context, CRUSH_COBBLESTONE, 1)
                    || itemCount(context.core(), Items.COBBLESTONE) != 0
                    || itemCount(context.core(), Items.GRAVEL) != 1
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(STONEWORK) != 0) {
                helper.fail("Crusher did not use the Material Stonework Factory transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_fe_or_work_rolls_back_every_input(GameTestHelper helper) {
        withCore(helper, context -> {
            StorageResourceKey pinkSlime = fluidKey(context, ifId("pink_slime"));
            seedItem(context.core(), Items.GLASS_PANE, 1);
            seedResource(context.core(), pinkSlime, 300);
            seedResource(
                    context.core(),
                    StorageResourceKey.neoforgeEnergy(),
                    200L * DissolutionChamberConfig.powerPerTick - 1);
            installStation(context, ifItem("dissolution_chamber"));
            tick(context.core(), 199);
            if (craft(context, PINK_SLIME_BALL, 1)
                    || itemCount(context.core(), Items.GLASS_PANE) != 1
                    || context.core().getResourceAmount(pinkSlime) != 300
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy())
                    != 200L * DissolutionChamberConfig.powerPerTick - 1
                    || context.core().getStationWork(DISSOLUTION) != 199) {
                helper.fail("Industrial Foregoing shortage partially mutated storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void output_overflow_rolls_back_item_fluid_fe_and_work(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            StorageResourceKey pinkSlime = fluidKey(context, ifId("pink_slime"));
            StorageResourceKey output = itemKey(
                    context, new ItemStack(ifItem("pink_slime")));
            seedItem(context.core(), Items.GLASS_PANE, 1);
            seedResource(context.core(), pinkSlime, 300);
            seedResource(
                    context.core(),
                    StorageResourceKey.neoforgeEnergy(),
                    200L * DissolutionChamberConfig.powerPerTick);
            seedResource(context.core(), output, Long.MAX_VALUE);
            installStation(context, ifItem("dissolution_chamber"));
            tick(context.core(), 200);
            if (craft(context, PINK_SLIME_BALL, 1)
                    || itemCount(context.core(), Items.GLASS_PANE) != 1
                    || context.core().getResourceAmount(pinkSlime) != 300
                    || context.core().getResourceAmount(output) != Long.MAX_VALUE
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy())
                    != 200L * DissolutionChamberConfig.powerPerTick
                    || context.core().getStationWork(DISSOLUTION) != 200) {
                helper.fail("Industrial Foregoing output overflow partially mutated storage");
                return;
            }
            helper.succeed();
        });
    }

    private static boolean validStation(MachineDescriptor descriptor, String itemPath) {
        ItemStack station = new ItemStack(ifItem(itemPath));
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

    @GameTest(template = "craftingtests.platform")
    public static void pitiful_generator_converts_fuel_to_fe_over_exact_work(GameTestHelper helper) {
        withCore(helper, context -> {
            var menu = transformMenu(context, new ItemStack(Items.COAL));
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "industrial_foregoing_pitiful_generator")))
                    .findFirst()
                    .orElse(null);
            if (use == null) {
                helper.fail("Generator transform use is missing");
                return;
            }
            selectTransform(menu, context, ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "industrial_foregoing_pitiful_generator"));
            if (menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("Generator must reject without an installed machine");
                return;
            }
            installStation(context, ifItem("pitiful_generator"));
            tick(context.core(), 1600);
            if (!menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 1_600L * 30L
                    || context.core().getStationWork(ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "industrial_foregoing_pitiful_generator")) != 0) {
                helper.fail("Generator committed the wrong FE/work transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void mycelial_furnace_converts_fuel_to_fe_over_exact_work(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            var menu = transformMenu(context, new ItemStack(Items.COAL));
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "industrial_foregoing_mycelial_furnace")))
                    .findFirst()
                    .orElse(null);
            if (use == null || use.amountPerItem() != 1_600L * 80L
                    || use.stationWorkPerItem() != 1600L) {
                helper.fail("Mycelial furnace transform use is missing or wrong");
                return;
            }
            selectTransform(menu, context, ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "industrial_foregoing_mycelial_furnace"));
            if (menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("Mycelial furnace must reject without an installed machine");
                return;
            }
            installStation(context, ifItem("mycelial_furnace"));
            tick(context.core(), 1600);
            if (!menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 1_600L * 80L
                    || context.core().getStationWork(ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "industrial_foregoing_mycelial_furnace")) != 0) {
                helper.fail("Mycelial furnace committed the wrong FE/work transaction");
                return;
            }
            helper.succeed();
        });
    }

    private static final int TRANSFORM_PAGE_BUTTON = 15;

    private static CraftingTerminalMenu transformMenu(
            FixtureContext context,
            ItemStack input
    ) {
        var menu = new CraftingTerminalMenu(
                962, context.player().getInventory(), context.core());
        menu.clickMenuButton(context.player(), TRANSFORM_PAGE_BUTTON);
        menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).set(input);
        return menu;
    }

    private static void selectTransform(
            CraftingTerminalMenu menu,
            FixtureContext context,
            ResourceLocation transformId
    ) {
        int useIndex = -1;
        var uses = menu.getVisibleTransformUses();
        for (int index = 0; index < uses.size(); index++) {
            if (uses.get(index).id().equals(transformId)) {
                useIndex = index;
                break;
            }
        }
        if (useIndex < 0 || !menu.clickMenuButton(
                context.player(), CraftingTerminalMenu.transformUseButtonId(useIndex))) {
            throw new IllegalStateException("Could not select transform " + transformId);
        }
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

    private static boolean installStation(FixtureContext context, Item stationItem) {
        ItemStack station = new ItemStack(stationItem);
        var menu = new CraftingTerminalMenu(
                914, context.player().getInventory(), context.core());
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
            return ItemStack.isSameItemSameComponents(slot.getItem(), station);
        }
        menu.clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
        return false;
    }

    private static boolean craft(
            FixtureContext context,
            ResourceLocation recipeId,
            int crafts
    ) {
        var menu = new CraftingTerminalMenu(
                915, context.player().getInventory(), context.core());
        if (!menu.handleRecipeRequest(
                context.level(), recipeId, 1, CraftingDestination.NONE, context.player())) {
            return false;
        }
        if (menu.computeCraftPreview(context.core(), context.player()).craftable() < crafts) {
            return false;
        }
        return menu.handleRecipeRequest(
                context.level(),
                recipeId,
                crafts,
                CraftingDestination.STORAGE,
                context.player());
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

    private static StorageResourceKey itemKey(FixtureContext context, ItemStack stack) {
        return StorageResourceKey.item(stack, context.level().registryAccess());
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

    private static Item ifItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(ifId(path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Industrial Foregoing item " + path);
        }
        return item;
    }

    private static ResourceLocation ifId(String path) {
        return ResourceLocation.fromNamespaceAndPath("industrialforegoing", path);
    }

    private static ResourceLocation ifRecipe(String path) {
        return ifId(path);
    }

    private static ResourceLocation fixtureRecipe(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                IndustrialForegoingFixtureMod.MODID, path);
    }

    private static ResourceLocation stationId(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                AutoStorage.MODID, "industrial_foregoing_" + path);
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
