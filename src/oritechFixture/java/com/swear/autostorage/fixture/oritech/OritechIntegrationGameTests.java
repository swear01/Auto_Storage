package com.swear.autostorage.fixture.oritech;

import com.swear.autostorage.Action;
import com.swear.autostorage.Actor;
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
import com.swear.autostorage.StorageResourceTransaction;
import com.swear.autostorage.StorageTerminalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rearth.oritech.init.OritechConfig;
import rearth.oritech.init.recipes.OritechRecipe;
import rearth.oritech.init.recipes.RecipeContent;
import rearth.oritech.util.FluidIngredient;

import java.util.List;

@GameTestHolder(OritechFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class OritechIntegrationGameTests {
    private static final int CRAFTABLE_PAGE_BUTTON = 6;
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final ResourceLocation PULVERIZER =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "oritech_pulverizer");
    private static final ResourceLocation GRINDER =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "oritech_grinder");
    private static final ResourceLocation ADAMANT = oritechRecipe("pulverizer/adamant");
    private static final ResourceLocation RAW_IRON = oritechRecipe("pulverizer/raw/iron");
    private static final ResourceLocation PLATINUM = oritechRecipe("pulverizer/ore/platinum");
    private static final ResourceLocation GRINDER_IRON = oritechRecipe("grinder/ore/iron");
    private static final ResourceLocation FLUID_OUTPUT =
            fixtureRecipe("pulverizer_fluid_output");
    private static final ResourceLocation DUPLICATE_OUTPUTS =
            fixtureRecipe("pulverizer_duplicate_outputs");
    private static final ResourceLocation TOO_MANY_INPUTS =
            fixtureRecipe("pulverizer_too_many_inputs");

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
        if (OritechConfig.processingMachines == null
                || OritechConfig.processingMachines.pulverizerData == null
                || OritechConfig.processingMachines.pulverizerData.energyPerTick == null) {
            helper.fail("Oritech pulverizer config holders were unexpectedly null");
            return;
        }
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

    @GameTest(template = "craftingtests.platform")
    public static void fluid_output_pulverizer_fails_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(FLUID_OUTPUT).orElse(null);
        if (holder == null
                || !(holder.value() instanceof rearth.oritech.init.recipes.OritechRecipe)) {
            helper.fail("Fluid-output pulverizer fixture was not loaded");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Pulverizer accepted a fluid-output recipe without a fluid plan");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void duplicate_exact_outputs_merge_into_one_key(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedEnergy(DUPLICATE_OUTPUTS, helper);
            seedItem(context.core(), Items.STONE, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installPulverizer(context);
            tick(context.core(), recipeTime(DUPLICATE_OUTPUTS, helper));
            if (!supports(helper, DUPLICATE_OUTPUTS)
                    || !craft(context, DUPLICATE_OUTPUTS)
                    || itemCount(context.core(), Items.STONE) != 0
                    || itemCount(context.core(), Items.GRAVEL) != 5
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(PULVERIZER) != 0) {
                helper.fail("Oritech pulverizer did not merge duplicate exact outputs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void oversized_ingredient_plus_fe_layout_fails_closed(
            GameTestHelper helper
    ) {
        var holder = helper.getLevel().getRecipeManager().byKey(TOO_MANY_INPUTS).orElse(null);
        if (holder == null) {
            helper.fail("Oversized pulverizer fixture was not loaded");
            return;
        }
        if (!(holder.value() instanceof rearth.oritech.init.recipes.OritechRecipe recipe)
                || recipe.getInputs() == null
                || recipe.getInputs().size() < 9) {
            helper.fail("Oversized pulverizer fixture did not expose nine item inputs");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail(
                    "Pulverizer accepted an oversized ingredient+FE layout that cannot plan");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void mixed_item_fe_post_commit_failure_rolls_back_core_deltas(
            GameTestHelper helper
    ) {
        withCore(helper, originalContext -> {
            RollbackStorageCoreBlockEntity rollbackCore = replaceWithRollbackCore(
                    originalContext);
            FixtureContext context = new FixtureContext(
                    originalContext.level(), rollbackCore, originalContext.player());
            long energy = expectedEnergy(ADAMANT, helper);
            seedItem(context.core(), oritechItem("adamant_ingot"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installPulverizer(context);
            tick(context.core(), recipeTime(ADAMANT, helper));
            rollbackCore.armMixedCommitFailure();
            if (craft(context, ADAMANT)
                    || !rollbackCore.mixedCommitObserved()
                    || itemCount(context.core(), oritechItem("adamant_ingot")) != 1
                    || itemCount(context.core(), oritechItem("adamant_dust")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(PULVERIZER) != energy - 1
                    || inventoryCount(
                    context.player().getInventory(), oritechItem("adamant_dust")) != 0) {
                helper.fail(
                        "Oritech post-extraction cost failure did not roll back mixed item/FE deltas");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform", batch = "oritech_config")
    public static void config_reload_and_zero_fe_mode_use_current_exact_costs(
            GameTestHelper helper
    ) {
        int original = energyPerTick();
        withCore(helper, context -> {
            try {
                if (recipeTime(PLATINUM, helper) != 150) {
                    helper.fail("Oritech platinum fixture did not retain its 150-tick duration");
                    return;
                }
                seedItem(context.core(), oritechItem("deepslate_platinum_ore"), 2);
                installPulverizer(context);
                selectPreview(context, PLATINUM);

                setEnergyPerTick(7);
                MachineDescriptor descriptor = MachineEnergyTable.get(PULVERIZER);
                ItemStack station = new ItemStack(oritechItem("pulverizer_block"));
                if (descriptor == null || !descriptor.rateFor(station).orElseThrow().equals(
                        MachineWorkRate.of(7, 1))) {
                    helper.fail("Oritech pulverizer did not reload its configured rate");
                    return;
                }
                seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), 1_050);
                tick(context.core(), 150);
                if (!craft(context, PLATINUM)
                        || itemCount(context.core(), oritechItem("raw_platinum")) != 2
                        || context.core().getResourceAmount(
                        StorageResourceKey.neoforgeEnergy()) != 0
                        || context.core().getStationWork(PULVERIZER) != 0) {
                    helper.fail("Oritech cached stale 7 FE/t pulverizer costs");
                    return;
                }

                setEnergyPerTick(0);
                if (!descriptor.rateFor(station).orElseThrow().equals(
                        MachineWorkRate.of(1, 1))) {
                    helper.fail("Oritech zero-FE pulverizer did not retain one work per tick");
                    return;
                }
                tick(context.core(), 150);
                if (!craft(context, PLATINUM)
                        || itemCount(context.core(), oritechItem("raw_platinum")) != 4
                        || context.core().getResourceAmount(
                        StorageResourceKey.neoforgeEnergy()) != 0
                        || context.core().getStationWork(PULVERIZER) != 0) {
                    helper.fail("Oritech zero-FE pulverizer did not use exact recipe-time work");
                    return;
                }
                helper.succeed();
            } finally {
                setEnergyPerTick(original);
            }
        });
    }

    @GameTest(template = "craftingtests.platform", batch = "oritech_catalog_config")
    public static void zero_fe_transition_keeps_oversized_recipe_out_of_craftable_catalog(
            GameTestHelper helper
    ) {
        int originalEnergy = energyPerTick();
        withCore(helper, context -> {
            var manager = context.level().getRecipeManager();
            var originalRecipes = List.copyOf(manager.getRecipes());
            try {
                setEnergyPerTick(0);
                for (Item item : List.of(
                        Items.COBBLESTONE,
                        Items.DIRT,
                        Items.SAND,
                        Items.GRAVEL,
                        Items.CLAY_BALL,
                        Items.FLINT,
                        Items.STICK,
                        Items.COAL,
                        Items.CHARCOAL)) {
                    seedItem(context.core(), item, 1);
                }
                installPulverizer(context);
                tick(context.core(), 100);
                manager.replaceRecipes(originalRecipes);

                var menu = new CraftingTerminalMenu(
                        933, context.player().getInventory(), context.core());
                menu.clickMenuButton(context.player(), CRAFTABLE_PAGE_BUTTON);
                menu.refreshDisplayItems(context.core());
                boolean visibleAtZero = findDisplaySlot(menu, Items.DIAMOND) >= 0;

                setEnergyPerTick(7);
                boolean transitionFailed = false;
                boolean visibleAtSeven = false;
                try {
                    menu.refreshDisplayItems(context.core());
                    visibleAtSeven = findDisplaySlot(menu, Items.DIAMOND) >= 0;
                } catch (IllegalArgumentException exception) {
                    transitionFailed = true;
                }
                if (visibleAtZero || visibleAtSeven || transitionFailed) {
                    helper.fail(
                            "Oritech config transition changed oversized Craftable eligibility");
                    return;
                }
                helper.succeed();
            } finally {
                setEnergyPerTick(originalEnergy);
                manager.replaceRecipes(originalRecipes);
            }
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void null_fluid_outputs_fail_closed(GameTestHelper helper) {
        OritechRecipe recipe = syntheticRecipe(
                100,
                List.of(Ingredient.of(Items.STONE)),
                List.of(new ItemStack(Items.GRAVEL)),
                FluidIngredient.EMPTY,
                null);
        if (CraftingTerminalMenu.supportsRecipeHolder(
                new RecipeHolder<>(fixtureRecipe("null_fluid_outputs"), recipe))) {
            helper.fail("Oritech pulverizer accepted null fluid outputs");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void null_fluid_output_element_fails_closed(GameTestHelper helper) {
        OritechRecipe recipe = syntheticRecipe(
                100,
                List.of(Ingredient.of(Items.STONE)),
                List.of(new ItemStack(Items.GRAVEL)),
                FluidIngredient.EMPTY,
                java.util.Collections.singletonList(null));
        if (CraftingTerminalMenu.supportsRecipeHolder(
                new RecipeHolder<>(fixtureRecipe("null_fluid_output_element"), recipe))) {
            helper.fail("Oritech pulverizer accepted a null fluid output element");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void non_simple_ingredient_fails_closed(GameTestHelper helper) {
        Ingredient ingredient = DataComponentIngredient.of(
                true, new ItemStack(Items.STONE));
        if (ingredient.isSimple()) {
            helper.fail("Oritech non-simple ingredient fixture unexpectedly became simple");
            return;
        }
        OritechRecipe recipe = syntheticRecipe(ingredient, FluidIngredient.EMPTY);
        if (CraftingTerminalMenu.supportsRecipeHolder(
                new RecipeHolder<>(fixtureRecipe("non_simple_ingredient"), recipe))) {
            helper.fail("Oritech pulverizer accepted a non-simple ingredient");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void nonempty_fluid_input_fails_closed(GameTestHelper helper) {
        FluidIngredient water = new FluidIngredient()
                .withContent(Fluids.WATER)
                .withAmount(1_000);
        OritechRecipe recipe = syntheticRecipe(Ingredient.of(Items.STONE), water);
        if (CraftingTerminalMenu.supportsRecipeHolder(
                new RecipeHolder<>(fixtureRecipe("nonempty_fluid_input"), recipe))) {
            helper.fail("Oritech pulverizer accepted a nonempty fluid input");
            return;
        }
        helper.succeed();
    }

    private static boolean supports(GameTestHelper helper, ResourceLocation recipeId) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        return CraftingTerminalMenu.supportsRecipeHolder(holder);
    }

    private static OritechRecipe syntheticRecipe(
            Ingredient ingredient,
            FluidIngredient fluidInput
    ) {
        return syntheticRecipe(
                100,
                List.of(ingredient),
                List.of(new ItemStack(Items.GRAVEL)),
                fluidInput,
                List.of());
    }

    private static OritechRecipe syntheticRecipe(
            int time,
            List<Ingredient> inputs,
            List<ItemStack> results,
            FluidIngredient fluidInput,
            List<?> fluidOutputs
    ) {
        try {
            for (var constructor : OritechRecipe.class.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 6
                        && parameters[0] == int.class
                        && parameters[1] == List.class
                        && parameters[2] == List.class
                        && parameters[3] == rearth.oritech.init.recipes.OritechRecipeType.class
                        && parameters[4] == FluidIngredient.class
                        && parameters[5] == List.class) {
                    return (OritechRecipe) constructor.newInstance(
                            time, inputs, results, RecipeContent.PULVERIZER,
                            fluidInput, fluidOutputs);
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not build synthetic Oritech recipe", exception);
        }
        throw new IllegalStateException("Oritech list-output constructor was not found");
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

    private static void setEnergyPerTick(int value) {
        OritechConfig.processingMachines.pulverizerData.energyPerTick.set(value);
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

    private static RollbackStorageCoreBlockEntity replaceWithRollbackCore(
            FixtureContext context
    ) {
        CompoundTag saved = context.core().saveWithoutMetadata(
                context.level().registryAccess());
        BlockPos pos = context.core().getBlockPos();
        context.level().removeBlockEntity(pos);
        RollbackStorageCoreBlockEntity replacement = new RollbackStorageCoreBlockEntity(
                pos, context.level().getBlockState(pos));
        replacement.loadWithComponents(saved, context.level().registryAccess());
        context.level().setBlockEntity(replacement);
        replacement.onLoad();
        replacement.rebuildNetwork(context.level());
        if (!replacement.isStorageAvailable()) {
            throw new IllegalStateException("Rollback Core test fixture did not attach storage");
        }
        return replacement;
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

    private static void selectPreview(
            FixtureContext context,
            ResourceLocation recipeId
    ) {
        var menu = new CraftingTerminalMenu(
                932, context.player().getInventory(), context.core());
        if (!menu.handleRecipeRequest(
                context.level(), recipeId, 1,
                CraftingDestination.NONE, context.player())) {
            throw new IllegalStateException("Could not select Oritech recipe " + recipeId);
        }
        menu.computeCraftPreview(context.core(), context.player());
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

    private static int findDisplaySlot(CraftingTerminalMenu menu, Item item) {
        for (int slot = 0; slot < StorageTerminalMenu.DISPLAY_SLOTS; slot++) {
            if (menu.getSlot(slot).getItem().is(item)) return slot;
        }
        return -1;
    }

    private static long inventoryCount(
            net.minecraft.world.entity.player.Inventory inventory,
            Item item
    ) {
        long count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
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

    private static ResourceLocation fixtureRecipe(String path) {
        return ResourceLocation.fromNamespaceAndPath(OritechFixtureMod.MODID, path);
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

    private static final class RollbackStorageCoreBlockEntity
            extends StorageCoreBlockEntity {
        private boolean armed;
        private boolean mixedCommitObserved;

        private RollbackStorageCoreBlockEntity(
                BlockPos pos,
                net.minecraft.world.level.block.state.BlockState state
        ) {
            super(pos, state);
        }

        private void armMixedCommitFailure() {
            armed = true;
        }

        private boolean mixedCommitObserved() {
            return mixedCommitObserved;
        }

        @Override
        public boolean applyResourceTransaction(
                StorageResourceTransaction transaction,
                Action action,
                Actor actor
        ) {
            boolean applied = super.applyResourceTransaction(transaction, action, actor);
            if (!applied || !armed || action != Action.EXECUTE
                    || !actor.name().equals("auto_storage_crafting")
                    || !isMixedOritechCommit(transaction)) {
                return applied;
            }
            armed = false;
            mixedCommitObserved = true;
            if (!consumeStationWork(PULVERIZER, 1)) {
                throw new IllegalStateException(
                        "Could not inject Oritech post-extraction cost failure");
            }
            return true;
        }

        private boolean isMixedOritechCommit(StorageResourceTransaction transaction) {
            if (getLevel() == null) return false;
            StorageResourceKey input = StorageResourceKey.item(
                    new ItemStack(oritechItem("adamant_ingot")),
                    getLevel().registryAccess());
            StorageResourceKey output = StorageResourceKey.item(
                    new ItemStack(oritechItem("adamant_dust")),
                    getLevel().registryAccess());
            Long inputDelta = transaction.deltas().get(input);
            Long outputDelta = transaction.deltas().get(output);
            Long energyDelta = transaction.deltas().get(
                    StorageResourceKey.neoforgeEnergy());
            return inputDelta != null && inputDelta == -1
                    && outputDelta != null && outputDelta == 1
                    && energyDelta != null && energyDelta < 0;
        }
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(FixtureContext context);
    }
}
