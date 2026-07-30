package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@GameTestHolder(AutoStorage.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeAdapterTests {
    private RecipeAdapterTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void non_exhaustive_candidates_are_not_omitted_from_craftable_catalog(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            core.getMachineContainer().setItem(
                    MachineEnergyTable.CRAFTING_TABLE_SLOT, new ItemStack(Items.CRAFTING_TABLE));
            core.insertItem(new ItemStack(Items.STONE));

            Ingredient ingredient = new Ingredient(new NonExhaustiveStoneIngredient());
            RecipeHolder<ShapelessRecipe> holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath("test_mod", "non_exhaustive_catalog"),
                    new ShapelessRecipe("", CraftingBookCategory.MISC,
                            new ItemStack(Items.EMERALD),
                            NonNullList.of(Ingredient.EMPTY, ingredient)));
            var manager = level.getRecipeManager();
            var original = java.util.List.copyOf(manager.getRecipes());
            var registered = new java.util.ArrayList<>(original);
            registered.add(holder);
            try {
                manager.replaceRecipes(registered);
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(170, player.getInventory(), core);
                menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
                menu.refreshDisplayItems(core);
                boolean found = false;
                for (int slot = 0; slot < StorageTerminalMenu.DISPLAY_SLOTS; slot++) {
                    if (menu.getSlot(slot).getItem().is(Items.EMERALD)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    helper.fail("A non-exhaustive displayed-item list omitted a matching recipe");
                    return;
                }
            } finally {
                manager.replaceRecipes(original);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void shaped_family_contract(GameTestHelper helper) {
        runFamilyCase(helper, Family.SHAPED);
    }

    @GameTest(template = "craftingtests.platform")
    public static void shapeless_family_contract(GameTestHelper helper) {
        runFamilyCase(helper, Family.SHAPELESS);
    }

    @GameTest(template = "craftingtests.platform")
    public static void smelting_family_contract(GameTestHelper helper) {
        runFamilyCase(helper, Family.SMELTING);
    }

    @GameTest(template = "craftingtests.platform")
    public static void blasting_family_contract(GameTestHelper helper) {
        runFamilyCase(helper, Family.BLASTING);
    }

    @GameTest(template = "craftingtests.platform")
    public static void smoking_family_contract(GameTestHelper helper) {
        runFamilyCase(helper, Family.SMOKING);
    }

    @GameTest(template = "craftingtests.platform")
    public static void campfire_family_contract(GameTestHelper helper) {
        runFamilyCase(helper, Family.CAMPFIRE);
    }

    @GameTest(template = "craftingtests.platform")
    public static void stonecutting_family_contract(GameTestHelper helper) {
        runFamilyCase(helper, Family.STONECUTTING);
    }

    @GameTest(template = "craftingtests.platform")
    public static void smithing_family_contract(GameTestHelper helper) {
        runFamilyCase(helper, Family.SMITHING);
    }

    @GameTest(template = "craftingtests.platform")
    public static void axe_family_contract(GameTestHelper helper) {
        runFamilyCase(helper, Family.AXE);
    }

    private static void runFamilyCase(GameTestHelper helper, Family family) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found for " + family);
                return;
            }
            FamilyCase testCase = createFamilyCase(family);
            var manager = level.getRecipeManager();
            var original = List.copyOf(manager.getRecipes());
            if (testCase.holder() != null) {
                var registered = new ArrayList<>(original);
                registered.removeIf(holder -> holder.id().equals(testCase.holder().id()));
                registered.add(testCase.holder());
                manager.replaceRecipes(registered);
            }
            try {
                core.rebuildNetwork(level);
                int stationSlot = MachineEnergyTable.findSlot(testCase.stationDescriptorId());
                if (!testCase.axe()) {
                    require(helper, stationSlot >= 0, "Missing station slot for " + family);
                    MachineDescriptor descriptor = MachineEnergyTable.get(testCase.stationDescriptorId());
                    require(helper, descriptor != null, "Missing station descriptor for " + family);
                    core.getMachineContainer().setItem(
                            stationSlot, descriptor.representativeStack());
                }
                for (ItemStack input : testCase.inputs()) {
                    require(helper, core.insertItem(input.copy()) == input.getCount(),
                            "Could not seed exact input for " + family + ": " + input);
                }
                if (testCase.energyPerCraft() > 0) {
                    for (int tick = 0; tick < testCase.energyPerCraft(); tick++) core.tick();
                    require(helper, core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL),
                            "Could not seed Fuel for " + family);
                }
                if (testCase.axe()) {
                    ItemStack finite = new ItemStack(Items.IRON_AXE);
                    finite.setDamageValue(finite.getMaxDamage() - 1);
                    require(helper, core.addAxeEnergy(finite), "Could not seed finite Axe Energy");
                }

                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(171 + family.ordinal(), player.getInventory(), core);
                RecipeHolder<?> currentHolder;
                if (testCase.axe()) {
                    menu.lookUpRecipes(level, testCase.expectedOutput());
                    currentHolder = menu.getCurrentRecipes().stream()
                            .filter(holder -> BuiltInRecipeAdapters.registry().classify(holder)
                                    .map(match -> match.adapterId().equals(testCase.adapterId()))
                                    .orElse(false))
                            .findFirst()
                            .orElse(null);
                } else {
                    currentHolder = manager.byKey(testCase.holder().id()).orElse(null);
                }
                require(helper, currentHolder != null, "Fresh holder not found for " + family);
                require(helper, !currentHolder.id().getNamespace().equals("minecraft"),
                        "Family fixture must not use a minecraft recipe ID: " + currentHolder.id());
                RecipeAdapterMatch match = BuiltInRecipeAdapters.registry()
                        .classify(currentHolder).orElse(null);
                require(helper, match != null && match.adapterId().equals(testCase.adapterId()),
                        "Exact adapter selection mismatch for " + family);
                require(helper, match.stationDescriptorId().equals(testCase.stationDescriptorId()),
                        "Stable station descriptor mismatch for " + family);
                int multiplicity = match.orderedInputs().stream()
                        .filter(input -> !input.isEmpty())
                        .mapToInt(RecipeAdapterMatch.Input::multiplicity)
                        .sum();
                require(helper, multiplicity == testCase.inputs().size(),
                        "Ordered input multiplicity mismatch for " + family + ": " + multiplicity);
                require(helper, match.validatesSimulation(currentHolder)
                                && match.validatesCommit(currentHolder),
                        "Simulation/commit validation mismatch for " + family);
                EnergyCost matchEnergy = match.cost().energyCost().orElse(null);
                if (testCase.energyPerCraft() > 0) {
                    require(helper, matchEnergy != null
                                    && matchEnergy.processType() == testCase.processType()
                                    && matchEnergy.processAmount() == testCase.energyPerCraft()
                                    && matchEnergy.fuelType() == EnergyType.FURNACE_FUEL
                                    && matchEnergy.fuelAmount() == testCase.energyPerCraft(),
                            "Concrete cooking cost mismatch for " + family + ": " + matchEnergy);
                } else {
                    require(helper, matchEnergy == null, "Unexpected process/Fuel cost for " + family);
                }
                RecipeAdapterMatch.ToolCost matchTool = match.cost().toolCost().orElse(null);
                require(helper, testCase.axe()
                                ? matchTool != null
                                && matchTool.descriptorId().equals(MachineEnergyTable.AXE_ID)
                                && matchTool.amountPerCraft() == 1
                                : matchTool == null,
                        "Tool cost mismatch for " + family + ": " + matchTool);

                require(helper, menu.handleRecipeRequest(
                                level, currentHolder.id(), 1, CraftingDestination.NONE, player),
                        "Exact recipe selection failed for " + family);
                require(helper, menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex()).id()
                                .equals(currentHolder.id()),
                        "Selected recipe ID mismatch for " + family);
                CraftingTerminalMenu.CraftPreview preview = menu.computeCraftPreview(core, player);
                require(helper, preview.craftable() == 1,
                        "Expected exactly one preview craft for " + family + ", got " + preview);
                RecipePresentation presentation = menu.getRecipePresentation();
                require(helper, presentation.recipeId().equals(currentHolder.id())
                                && presentation.kind() == match.presentation().kind()
                                && ItemStack.isSameItemSameComponents(
                                presentation.output(), testCase.expectedOutput())
                                && presentation.output().getCount() == testCase.expectedOutput().getCount(),
                        "Exact presentation mismatch for " + family + ": " + presentation);
                MachineDescriptor stationDescriptor = MachineEnergyTable.get(testCase.stationDescriptorId());
                require(helper, stationDescriptor != null
                                && ItemStack.isSameItemSameComponents(
                                presentation.station(), stationDescriptor.representativeStack()),
                        "Presentation station mismatch for " + family + ": " + presentation.station());

                long processBefore = testCase.processType() == null
                        ? 0 : core.getEnergy(testCase.processType());
                long fuelBefore = core.getEnergy(EnergyType.FURNACE_FUEL);
                long toolBefore = core.getDescriptorAmount(MachineEnergyTable.AXE_ID);
                ItemStack stationBefore = stationSlot < 0
                        ? ItemStack.EMPTY : core.getMachineContainer().getItem(stationSlot).copy();
                menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
                menu.craftItem(1, player);

                long exactOutput = core.getItemCount(ItemKey.of(testCase.expectedOutput()));
                require(helper, exactOutput == testCase.expectedOutput().getCount(),
                        "Exact output count/components mismatch for " + family + ": " + exactOutput);
                long sameItemOutputs = core.getDisplayStacks().stream()
                        .filter(stack -> stack.is(testCase.expectedOutput().getItem()))
                        .mapToLong(stack -> core.getItemCount(ItemKey.of(stack)))
                        .sum();
                require(helper, sameItemOutputs == exactOutput,
                        "Unexpected output component variant for " + family + ": " + sameItemOutputs);
                for (ItemStack input : testCase.inputs()) {
                    require(helper, core.getItemCount(ItemKey.of(input)) == 0,
                            "Input was not consumed exactly for " + family + ": " + input);
                }
                require(helper, core.getItemCount(ItemKey.of(new ItemStack(Items.BUCKET)))
                                == testCase.remainderCount(),
                        "Remainder count mismatch for " + family);
                if (testCase.energyPerCraft() > 0) {
                    require(helper, core.getEnergy(testCase.processType())
                                    == processBefore - testCase.energyPerCraft()
                                    && core.getEnergy(EnergyType.FURNACE_FUEL)
                                    == fuelBefore - testCase.energyPerCraft(),
                            "Energy delta mismatch for " + family);
                } else {
                    require(helper, core.getEnergy(EnergyType.FURNACE_FUEL) == fuelBefore,
                            "Free family changed Fuel for " + family);
                }
                if (testCase.axe()) {
                    require(helper, toolBefore == 1
                                    && core.getDescriptorAmount(MachineEnergyTable.AXE_ID) == 0,
                            "Finite Axe Energy must spend one unit per craft");
                    ItemStack infinite = new ItemStack(Items.IRON_AXE);
                    infinite.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
                    require(helper, core.addAxeEnergy(infinite), "Could not seed infinite Axe Energy");
                    require(helper, core.insertItem(testCase.inputs().getFirst().copy()) == 1,
                            "Could not seed infinite-tool input");
                    require(helper, menu.handleRecipeRequest(
                                    level, currentHolder.id(), 1, CraftingDestination.NONE, player),
                            "Could not reselect Axe recipe for infinite tool validation");
                    menu.craftItem(1, player);
                    require(helper, core.hasInfiniteDescriptor(MachineEnergyTable.AXE_ID)
                                    && core.getItemCount(ItemKey.of(testCase.expectedOutput())) == 2,
                            "Infinite Axe Energy must validate each craft without decrementing");
                } else {
                    require(helper, stationSlot >= 0
                                    && ItemStack.isSameItemSameComponents(
                                    stationBefore, core.getMachineContainer().getItem(stationSlot))
                                    && stationBefore.getCount()
                                    == core.getMachineContainer().getItem(stationSlot).getCount(),
                            "Installed station changed during " + family);
                }
                helper.succeed();
            } finally {
                if (testCase.holder() != null) manager.replaceRecipes(original);
            }
        });
    }

    private static FamilyCase createFamilyCase(Family family) {
        Ingredient bucket = Ingredient.of(Items.WATER_BUCKET);
        return switch (family) {
            case SHAPED -> {
                ItemStack output = namedStack(Items.DIAMOND, 2, "adapter-shaped");
                yield new FamilyCase(
                        new RecipeHolder<>(testId("shaped"), new ShapedRecipe(
                                "", CraftingBookCategory.MISC,
                                new ShapedRecipePattern(
                                        1,
                                        2,
                                        NonNullList.of(Ingredient.EMPTY, bucket, bucket),
                                        java.util.Optional.empty()),
                                output)),
                        BuiltInRecipeAdapters.SHAPED_ID,
                        MachineEnergyTable.CRAFTING_TABLE_ID,
                        List.of(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.WATER_BUCKET)),
                        output,
                        2,
                        null,
                        0,
                        false);
            }
            case SHAPELESS -> {
                ItemStack output = namedStack(Items.EMERALD, 3, "adapter-shapeless");
                yield new FamilyCase(
                        new RecipeHolder<>(testId("shapeless"), new ShapelessRecipe(
                                "", CraftingBookCategory.MISC, output,
                                NonNullList.of(Ingredient.EMPTY, bucket))),
                        BuiltInRecipeAdapters.SHAPELESS_ID,
                        MachineEnergyTable.CRAFTING_TABLE_ID,
                        List.of(new ItemStack(Items.WATER_BUCKET)),
                        output,
                        1,
                        null,
                        0,
                        false);
            }
            case SMELTING -> {
                ItemStack output = namedStack(Items.STONE, 2, "adapter-smelting");
                yield cookingCase(
                        "smelting",
                        new SmeltingRecipe("", CookingBookCategory.MISC, bucket, output, 0, 11),
                        output,
                        BuiltInRecipeAdapters.SMELTING_ID,
                        MachineEnergyTable.FURNACE_ID,
                        EnergyType.SMELTING_ENERGY,
                        11);
            }
            case BLASTING -> {
                ItemStack output = namedStack(Items.IRON_INGOT, 2, "adapter-blasting");
                yield cookingCase(
                        "blasting",
                        new BlastingRecipe("", CookingBookCategory.MISC, bucket, output, 0, 7),
                        output,
                        BuiltInRecipeAdapters.BLASTING_ID,
                        MachineEnergyTable.BLAST_FURNACE_ID,
                        EnergyType.BLASTING_ENERGY,
                        7);
            }
            case SMOKING -> {
                ItemStack output = namedStack(Items.COOKED_BEEF, 2, "adapter-smoking");
                yield cookingCase(
                        "smoking",
                        new SmokingRecipe("", CookingBookCategory.MISC, bucket, output, 0, 5),
                        output,
                        BuiltInRecipeAdapters.SMOKING_ID,
                        MachineEnergyTable.SMOKER_ID,
                        EnergyType.SMOKING_ENERGY,
                        5);
            }
            case CAMPFIRE -> {
                ItemStack output = namedStack(Items.BAKED_POTATO, 2, "adapter-campfire");
                yield cookingCase(
                        "campfire",
                        new CampfireCookingRecipe("", CookingBookCategory.MISC, bucket, output, 0, 13),
                        output,
                        BuiltInRecipeAdapters.CAMPFIRE_COOKING_ID,
                        MachineEnergyTable.CAMPFIRE_ID,
                        EnergyType.CAMPFIRE_ENERGY,
                        13);
            }
            case STONECUTTING -> {
                ItemStack output = namedStack(Items.STONE_BRICKS, 4, "adapter-stonecutting");
                yield new FamilyCase(
                        new RecipeHolder<>(testId("stonecutting"), new StonecutterRecipe(
                                "", bucket, output)),
                        BuiltInRecipeAdapters.STONECUTTING_ID,
                        MachineEnergyTable.STONECUTTER_ID,
                        List.of(new ItemStack(Items.WATER_BUCKET)),
                        output,
                        1,
                        null,
                        0,
                        false);
            }
            case SMITHING -> {
                ItemStack base = namedStack(Items.DIAMOND_SWORD, 1, "adapter-smithing-base");
                ItemStack output = namedStack(Items.NETHERITE_SWORD, 1, "adapter-smithing-base");
                yield new FamilyCase(
                        new RecipeHolder<>(testId("smithing"), new SmithingTransformRecipe(
                                bucket,
                                Ingredient.of(Items.DIAMOND_SWORD),
                                Ingredient.of(Items.NETHERITE_INGOT),
                                new ItemStack(Items.NETHERITE_SWORD))),
                        BuiltInRecipeAdapters.SMITHING_TRANSFORM_ID,
                        MachineEnergyTable.SMITHING_TABLE_ID,
                        List.of(new ItemStack(Items.WATER_BUCKET), base,
                                new ItemStack(Items.NETHERITE_INGOT)),
                        output,
                        1,
                        null,
                        0,
                        false);
            }
            case AXE -> new FamilyCase(
                    null,
                    BuiltInRecipeAdapters.AXE_TRANSFORMATION_ID,
                    MachineEnergyTable.AXE_ID,
                    List.of(new ItemStack(Items.OAK_LOG)),
                    new ItemStack(Items.STRIPPED_OAK_LOG),
                    0,
                    null,
                    0,
                    true);
        };
    }

    private static FamilyCase cookingCase(
            String path,
            Recipe<?> recipe,
            ItemStack output,
            ResourceLocation adapterId,
            ResourceLocation stationDescriptorId,
            EnergyType processType,
            int cookingTime
    ) {
        return new FamilyCase(
                new RecipeHolder<>(testId(path), recipe),
                adapterId,
                stationDescriptorId,
                List.of(new ItemStack(Items.WATER_BUCKET)),
                output,
                1,
                processType,
                cookingTime,
                false);
    }

    private static ItemStack namedStack(Item item, int count, String name) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ResourceLocation testId(String path) {
        return ResourceLocation.fromNamespaceAndPath("adapter_test", path);
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private enum Family {
        SHAPED,
        SHAPELESS,
        SMELTING,
        BLASTING,
        SMOKING,
        CAMPFIRE,
        STONECUTTING,
        SMITHING,
        AXE
    }

    private record FamilyCase(
            RecipeHolder<?> holder,
            ResourceLocation adapterId,
            ResourceLocation stationDescriptorId,
            List<ItemStack> inputs,
            ItemStack expectedOutput,
            int remainderCount,
            EnergyType processType,
            int energyPerCraft,
            boolean axe
    ) {
        private FamilyCase {
            inputs = inputs.stream().map(ItemStack::copy).toList();
            expectedOutput = expectedOutput.copy();
        }

        @Override
        public List<ItemStack> inputs() {
            return inputs.stream().map(ItemStack::copy).toList();
        }

        @Override
        public ItemStack expectedOutput() {
            return expectedOutput.copy();
        }
    }

    private static final class NonExhaustiveStoneIngredient implements ICustomIngredient {
        @Override
        public boolean test(ItemStack stack) {
            return stack.is(Items.STONE);
        }

        @Override
        public Stream<ItemStack> getItems() {
            return Stream.of(new ItemStack(Items.DIRT));
        }

        @Override
        public boolean isSimple() {
            return false;
        }

        @Override
        public IngredientType<?> getType() {
            throw new UnsupportedOperationException("Test ingredient is never serialized");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NonExhaustiveStoneIngredient;
        }

        @Override
        public int hashCode() {
            return NonExhaustiveStoneIngredient.class.hashCode();
        }
    }
}
