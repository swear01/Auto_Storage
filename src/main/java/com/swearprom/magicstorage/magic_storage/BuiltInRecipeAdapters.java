package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

final class BuiltInRecipeAdapters {
    private static final long MAX_SMITHING_VARIANT_COMBINATIONS = 65_536;

    static final ResourceLocation SHAPED_ID = id("shaped_crafting");
    static final ResourceLocation SHAPELESS_ID = id("shapeless_crafting");
    static final ResourceLocation SMELTING_ID = id("smelting");
    static final ResourceLocation BLASTING_ID = id("blasting");
    static final ResourceLocation SMOKING_ID = id("smoking");
    static final ResourceLocation CAMPFIRE_COOKING_ID = id("campfire_cooking");
    static final ResourceLocation STONECUTTING_ID = id("stonecutting");
    static final ResourceLocation SMITHING_TRANSFORM_ID = id("smithing_transform");
    static final ResourceLocation SMITHING_TRIM_ID = id("smithing_trim");
    static final ResourceLocation AXE_TRANSFORMATION_ID = id("axe_transformation");

    private static final ResourceLocation COMPATIBILITY_PROBE_ID = id("compatibility_probe");
    private static final List<RecipeType<?>> BUILT_IN_DISCOVERY_TYPES = List.of(
            RecipeType.CRAFTING,
            RecipeType.SMELTING,
            RecipeType.BLASTING,
            RecipeType.SMOKING,
            RecipeType.CAMPFIRE_COOKING,
            RecipeType.STONECUTTING,
            RecipeType.SMITHING
    );
    private static final Map<Recipe<?>, SmithingRepresentatives> SMITHING_INPUT_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<HolderLookup.Provider, Map<DataComponentMap, Optional<String>>>
            COMPONENT_IDENTITY_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final List<RecipeAdapter> BUILT_IN_ADAPTERS = List.of(
            exact(SHAPED_ID, 0, ShapedRecipe.class, RecipeType.CRAFTING,
                    recipe -> recipe.getWidth() <= 3
                            && recipe.getHeight() <= 3
                            && hasInput(recipe.getIngredients()),
                    recipe -> candidateIndex(recipe.getIngredients()),
                    BuiltInRecipeAdapters::singleVariant,
                    BuiltInRecipeAdapters::shapedContract),
            exact(SHAPELESS_ID, 0, ShapelessRecipe.class, RecipeType.CRAFTING,
                    recipe -> hasInput(recipe.getIngredients()),
                    recipe -> candidateIndex(recipe.getIngredients()),
                    BuiltInRecipeAdapters::singleVariant,
                    BuiltInRecipeAdapters::shapelessContract),
            exact(SMELTING_ID, 1, SmeltingRecipe.class, RecipeType.SMELTING,
                    recipe -> recipe.getCookingTime() > 0 && hasInput(recipe.getIngredients()),
                    recipe -> candidateIndex(recipe.getIngredients()),
                    BuiltInRecipeAdapters::singleVariant,
                    (recipe, validator) -> cookingContract(
                            recipe,
                            MachineEnergyTable.FURNACE_ID,
                            EnergyType.SMELTING_ENERGY,
                            validator)),
            exact(BLASTING_ID, 2, BlastingRecipe.class, RecipeType.BLASTING,
                    recipe -> recipe.getCookingTime() > 0 && hasInput(recipe.getIngredients()),
                    recipe -> candidateIndex(recipe.getIngredients()),
                    BuiltInRecipeAdapters::singleVariant,
                    (recipe, validator) -> cookingContract(
                            recipe,
                            MachineEnergyTable.BLAST_FURNACE_ID,
                            EnergyType.BLASTING_ENERGY,
                            validator)),
            exact(SMOKING_ID, 3, SmokingRecipe.class, RecipeType.SMOKING,
                    recipe -> recipe.getCookingTime() > 0 && hasInput(recipe.getIngredients()),
                    recipe -> candidateIndex(recipe.getIngredients()),
                    BuiltInRecipeAdapters::singleVariant,
                    (recipe, validator) -> cookingContract(
                            recipe,
                            MachineEnergyTable.SMOKER_ID,
                            EnergyType.SMOKING_ENERGY,
                            validator)),
            exact(CAMPFIRE_COOKING_ID, 4, CampfireCookingRecipe.class,
                    RecipeType.CAMPFIRE_COOKING,
                    recipe -> recipe.getCookingTime() > 0 && hasInput(recipe.getIngredients()),
                    recipe -> candidateIndex(recipe.getIngredients()),
                    BuiltInRecipeAdapters::singleVariant,
                    (recipe, validator) -> cookingContract(
                            recipe,
                            MachineEnergyTable.CAMPFIRE_ID,
                            EnergyType.CAMPFIRE_ENERGY,
                            validator)),
            exact(STONECUTTING_ID, 5, StonecutterRecipe.class, RecipeType.STONECUTTING,
                    recipe -> hasInput(recipe.getIngredients()),
                    recipe -> candidateIndex(recipe.getIngredients()),
                    BuiltInRecipeAdapters::singleVariant,
                    BuiltInRecipeAdapters::stonecuttingContract),
            exact(SMITHING_TRANSFORM_ID, 6, SmithingTransformRecipe.class, RecipeType.SMITHING,
                    recipe -> true,
                    BuiltInRecipeAdapters::smithingCandidateIndex,
                    BuiltInRecipeAdapters::smithingVariants,
                    BuiltInRecipeAdapters::smithingTransformLookup,
                    BuiltInRecipeAdapters::smithingContract),
            exact(SMITHING_TRIM_ID, 6, SmithingTrimRecipe.class, RecipeType.SMITHING,
                    recipe -> true,
                    BuiltInRecipeAdapters::smithingCandidateIndex,
                    BuiltInRecipeAdapters::smithingVariants,
                    BuiltInRecipeAdapters::smithingTrimContract),
            exact(AXE_TRANSFORMATION_ID, 7, AxeTransformationRecipe.class,
                    AxeTransformationRecipe.TYPE,
                    recipe -> !recipe.input().isEmpty(),
                    recipe -> candidateIndex(List.of(recipe.input())),
                    BuiltInRecipeAdapters::singleVariant,
                    BuiltInRecipeAdapters::axeContract)
    );

    private BuiltInRecipeAdapters() {
    }

    static RecipeAdapterRegistry registry() {
        return RecipeAdapters.snapshot().registry();
    }

    static List<RecipeType<?>> discoveryTypes() {
        return RecipeAdapters.snapshot().discoveryTypes();
    }

    static List<RecipeAdapter> builtInAdapters() {
        return BUILT_IN_ADAPTERS;
    }

    static List<RecipeType<?>> builtInDiscoveryTypes() {
        return BUILT_IN_DISCOVERY_TYPES;
    }

    static boolean supportsRecipe(Recipe<?> recipe) {
        return registry().classify(new RecipeHolder<>(COMPATIBILITY_PROBE_ID, recipe)).isPresent();
    }

    static EnergyCost energyCost(Recipe<?> recipe) {
        return registry().classify(new RecipeHolder<>(COMPATIBILITY_PROBE_ID, recipe))
                .flatMap(match -> match.cost().energyCost())
                .orElse(null);
    }

    private static RecipeAdapterMatch.Contract shapedContract(
            ShapedRecipe recipe,
            RecipeAdapterMatch.HolderValidator validator
    ) {
        return contract(
                ingredientInputs(recipe.getIngredients()),
                MachineEnergyTable.CRAFTING_TABLE_ID,
                RecipeAdapterMatch.Cost.free(),
                fixedOutput(recipe),
                new RecipeAdapterMatch.Presentation(
                        RecipePresentationKind.CRAFTING,
                        recipe.getWidth(),
                        recipe.getHeight(),
                        false,
                        fixedPresentationOutput(recipe)),
                validator);
    }

    private static RecipeAdapterMatch.Contract shapelessContract(
            ShapelessRecipe recipe,
            RecipeAdapterMatch.HolderValidator validator
    ) {
        return contract(
                ingredientInputs(recipe.getIngredients()),
                MachineEnergyTable.CRAFTING_TABLE_ID,
                RecipeAdapterMatch.Cost.free(),
                fixedOutput(recipe),
                new RecipeAdapterMatch.Presentation(
                        RecipePresentationKind.CRAFTING,
                        3,
                        3,
                        true,
                        fixedPresentationOutput(recipe)),
                validator);
    }

    private static RecipeAdapterMatch.Contract cookingContract(
            AbstractCookingRecipe recipe,
            ResourceLocation stationDescriptorId,
            EnergyType processType,
            RecipeAdapterMatch.HolderValidator validator
    ) {
        long cookingTime = recipe.getCookingTime();
        EnergyCost energyCost = new EnergyCost(
                processType,
                cookingTime,
                EnergyType.FURNACE_FUEL,
                cookingTime);
        return contract(
                ingredientInputs(recipe.getIngredients()),
                stationDescriptorId,
                RecipeAdapterMatch.Cost.energy(energyCost),
                fixedOutput(recipe),
                new RecipeAdapterMatch.Presentation(
                        RecipePresentationKind.COOKING,
                        1,
                        1,
                        false,
                        fixedPresentationOutput(recipe)),
                validator);
    }

    private static RecipeAdapterMatch.Contract stonecuttingContract(
            StonecutterRecipe recipe,
            RecipeAdapterMatch.HolderValidator validator
    ) {
        return contract(
                ingredientInputs(recipe.getIngredients()),
                MachineEnergyTable.STONECUTTER_ID,
                RecipeAdapterMatch.Cost.free(),
                fixedOutput(recipe),
                new RecipeAdapterMatch.Presentation(
                        RecipePresentationKind.STONECUTTING,
                        1,
                        1,
                        false,
                        fixedPresentationOutput(recipe)),
                validator);
    }

    private static RecipeAdapterMatch.Contract smithingContract(
            SmithingRecipe recipe,
            RecipeAdapterMatch.HolderValidator validator
    ) {
        return contract(
                smithingInputs(recipe),
                MachineEnergyTable.SMITHING_TABLE_ID,
                RecipeAdapterMatch.Cost.free(),
                (allocations, crafts, level) -> smithingOutput(recipe, allocations, level),
                new RecipeAdapterMatch.Presentation(
                        RecipePresentationKind.SMITHING,
                        3,
                        1,
                        false,
                        (inputs, level) -> smithingPresentationOutput(recipe, inputs, level)),
                validator);
    }

    private static RecipeAdapterMatch.Contract smithingTrimContract(
            SmithingTrimRecipe recipe,
            RecipeAdapterMatch.HolderValidator validator
    ) {
        return contract(
                smithingInputs(recipe),
                MachineEnergyTable.SMITHING_TABLE_ID,
                RecipeAdapterMatch.Cost.free(),
                (allocations, crafts, level) -> smithingOutput(recipe, allocations, level),
                new RecipeAdapterMatch.Presentation(
                        RecipePresentationKind.SMITHING,
                        3,
                        1,
                        false,
                        (inputs, level) -> smithingInputOutput(recipe, inputs, level)),
                validator);
    }

    private static RecipeAdapterMatch.Contract axeContract(
            AxeTransformationRecipe recipe,
            RecipeAdapterMatch.HolderValidator validator
    ) {
        return contract(
                ingredientInputs(List.of(recipe.input())),
                MachineEnergyTable.AXE_ID,
                RecipeAdapterMatch.Cost.tool(
                        new RecipeAdapterMatch.ToolCost(MachineEnergyTable.AXE_ID, 1)),
                fixedOutput(recipe),
                new RecipeAdapterMatch.Presentation(
                        RecipePresentationKind.AXE,
                        1,
                        1,
                        false,
                        fixedPresentationOutput(recipe)),
                validator);
    }

    private static RecipeAdapterMatch.Contract contract(
            List<RecipeAdapterMatch.Input> orderedInputs,
            ResourceLocation stationDescriptorId,
            RecipeAdapterMatch.Cost cost,
            RecipeAdapterMatch.OutputResolver outputResolver,
            RecipeAdapterMatch.Presentation presentation,
            RecipeAdapterMatch.HolderValidator validator
    ) {
        return new RecipeAdapterMatch.Contract(
                orderedInputs,
                stationDescriptorId,
                cost,
                outputResolver,
                presentation,
                validator,
                validator);
    }

    private static RecipeAdapterMatch.OutputResolver fixedOutput(Recipe<?> recipe) {
        return (allocations, crafts, level) -> {
            if (level == null) return Optional.empty();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) return Optional.empty();
            try {
                long amount = Math.multiplyExact((long) result.getCount(), crafts);
                return checkedOutput(Map.of(ItemKey.of(result), amount), allocations);
            } catch (ArithmeticException exception) {
                return Optional.empty();
            }
        };
    }

    private static RecipeAdapterMatch.PresentationOutputResolver fixedPresentationOutput(
            Recipe<?> recipe
    ) {
        return (inputs, level) -> level == null
                ? ItemStack.EMPTY
                : recipe.getResultItem(level.registryAccess()).copy();
    }

    private static Optional<RecipeAdapterMatch.CheckedOutput> smithingOutput(
            SmithingRecipe recipe,
            List<Map<ItemKey, Long>> allocations,
            Level level
    ) {
        if (level == null || allocations.size() != 3) return Optional.empty();
        ItemStack template = firstAllocatedStack(allocations.get(0));
        ItemStack addition = firstAllocatedStack(allocations.get(2));
        if (template.isEmpty() || addition.isEmpty()) return Optional.empty();
        Map<ItemKey, Long> outputs = new LinkedHashMap<>();
        try {
            for (Map.Entry<ItemKey, Long> base : allocations.get(1).entrySet()) {
                SmithingRecipeInput input = new SmithingRecipeInput(
                        template, base.getKey().toStack(1), addition);
                if (!recipe.matches(input, level)) return Optional.empty();
                ItemStack result = recipe.assemble(input, level.registryAccess());
                if (result.isEmpty()) return Optional.empty();
                long amount = Math.multiplyExact(base.getValue(), (long) result.getCount());
                outputs.merge(ItemKey.of(result), amount, Math::addExact);
            }
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
        return checkedOutput(outputs, allocations);
    }

    private static ItemStack smithingPresentationOutput(
            SmithingRecipe recipe,
            List<ItemStack> inputs,
            Level level
    ) {
        if (level == null) return ItemStack.EMPTY;
        if (inputs.size() >= 3
                && !inputs.get(0).isEmpty()
                && !inputs.get(1).isEmpty()
                && !inputs.get(2).isEmpty()) {
            SmithingRecipeInput input = new SmithingRecipeInput(
                    inputs.get(0), inputs.get(1), inputs.get(2));
            if (recipe.matches(input, level)) {
                ItemStack result = recipe.assemble(input, level.registryAccess());
                if (!result.isEmpty()) return result;
            }
        }
        return recipe.getResultItem(level.registryAccess()).copy();
    }

    private static ItemStack smithingInputOutput(
            SmithingRecipe recipe,
            List<ItemStack> inputs,
            Level level
    ) {
        if (level == null || inputs.size() < 3
                || inputs.get(0).isEmpty()
                || inputs.get(1).isEmpty()
                || inputs.get(2).isEmpty()) return ItemStack.EMPTY;
        SmithingRecipeInput input = new SmithingRecipeInput(
                inputs.get(0), inputs.get(1), inputs.get(2));
        if (!recipe.matches(input, level)) return ItemStack.EMPTY;
        return recipe.assemble(input, level.registryAccess());
    }

    private static <R extends Recipe<?>> List<RecipeAdapterMatch.Contract> singleVariant(
            R recipe,
            RecipeAdapterMatch.Contract contract,
            List<ItemStack> availableStacks,
            Level level
    ) {
        if (level == null || recipe.getResultItem(level.registryAccess()).isEmpty()) return List.of();
        return List.of(contract);
    }

    private static <R extends Recipe<?>> boolean exactVariantLookup(
            R recipe,
            RecipeAdapterMatch.Contract variantContract,
            ItemStack requestedOutput,
            Level level
    ) {
        if (level == null || requestedOutput.isEmpty()) return false;
        ItemStack output = variantContract.presentation().outputResolver()
                .resolve(List.of(), level);
        return !output.isEmpty()
                && ItemStack.isSameItemSameComponents(output, requestedOutput);
    }

    private static boolean smithingTransformLookup(
            SmithingTransformRecipe recipe,
            RecipeAdapterMatch.Contract variantContract,
            ItemStack requestedOutput,
            Level level
    ) {
        if (exactVariantLookup(recipe, variantContract, requestedOutput, level)) return true;
        if (level == null || requestedOutput.isEmpty()) return false;
        ItemStack target = recipe.getResultItem(level.registryAccess());
        return !target.isEmpty()
                && ItemStack.isSameItemSameComponents(target, requestedOutput);
    }

    private static List<RecipeAdapterMatch.Contract> smithingVariants(
            SmithingRecipe recipe,
            RecipeAdapterMatch.Contract baseContract,
            List<ItemStack> availableStacks,
            Level level
    ) {
        if (level == null) return List.of();
        if (smithingVariantLimitExceeded(recipe, availableStacks)) return List.of();
        Optional<List<ItemStack>> orderedStacks = uniqueExactStacks(availableStacks, level);
        if (orderedStacks.isEmpty()) return List.of();
        List<ItemStack> unique = orderedStacks.get();
        List<ItemStack> templates = unique.stream().filter(recipe::isTemplateIngredient).toList();
        List<ItemStack> bases = unique.stream().filter(recipe::isBaseIngredient).toList();
        List<ItemStack> additions = unique.stream().filter(recipe::isAdditionIngredient).toList();
        if (templates.isEmpty() || bases.isEmpty() || additions.isEmpty()) return List.of();
        long combinations;
        try {
            combinations = Math.multiplyExact(
                    Math.multiplyExact((long) templates.size(), bases.size()), additions.size());
        } catch (ArithmeticException exception) {
            return List.of();
        }
        if (combinations > MAX_SMITHING_VARIANT_COMBINATIONS) return List.of();

        Map<ItemKey, RecipeAdapterMatch.Contract> variants = new LinkedHashMap<>();
        for (ItemStack template : templates) {
            for (ItemStack base : bases) {
                for (ItemStack addition : additions) {
                    SmithingRecipeInput input = new SmithingRecipeInput(template, base, addition);
                    if (!recipe.matches(input, level)) continue;
                    ItemStack output = recipe.assemble(input, level.registryAccess());
                    if (output.isEmpty()) continue;
                    ItemKey outputKey = ItemKey.of(output);
                    variants.putIfAbsent(outputKey, smithingVariantContract(
                            recipe, baseContract, template, base, addition, output));
                }
            }
        }
        return List.copyOf(variants.values());
    }

    private static boolean smithingVariantLimitExceeded(
            SmithingRecipe recipe,
            List<ItemStack> availableStacks
    ) {
        Set<ItemKey> templates = new HashSet<>();
        Set<ItemKey> bases = new HashSet<>();
        Set<ItemKey> additions = new HashSet<>();
        for (ItemStack stack : availableStacks) {
            if (stack.isEmpty()) continue;
            ItemKey key = null;
            if (recipe.isTemplateIngredient(stack)) {
                key = ItemKey.of(stack);
                templates.add(key);
            }
            if (recipe.isBaseIngredient(stack)) {
                if (key == null) key = ItemKey.of(stack);
                bases.add(key);
            }
            if (recipe.isAdditionIngredient(stack)) {
                if (key == null) key = ItemKey.of(stack);
                additions.add(key);
            }
            if (!templates.isEmpty() && !bases.isEmpty() && !additions.isEmpty()
                    && templates.size() > MAX_SMITHING_VARIANT_COMBINATIONS
                    / bases.size() / additions.size()) return true;
        }
        return false;
    }

    private static RecipeAdapterMatch.Contract smithingVariantContract(
            SmithingRecipe recipe,
            RecipeAdapterMatch.Contract baseContract,
            ItemStack template,
            ItemStack base,
            ItemStack addition,
            ItemStack output
    ) {
        List<RecipeAdapterMatch.Input> inputs = List.of(
                exactSmithingVariantInput(recipe, 0, template, recipe::isTemplateIngredient),
                exactSmithingVariantInput(recipe, 1, base, recipe::isBaseIngredient),
                exactSmithingVariantInput(recipe, 2, addition, recipe::isAdditionIngredient));
        ItemStack exactOutput = output.copy();
        return new RecipeAdapterMatch.Contract(
                inputs,
                baseContract.stationDescriptorId(),
                baseContract.cost(),
                (allocations, crafts, level) -> smithingVariantOutput(
                        recipe, exactOutput, allocations, crafts, level),
                new RecipeAdapterMatch.Presentation(
                        baseContract.presentation().kind(),
                        baseContract.presentation().width(),
                        baseContract.presentation().height(),
                        baseContract.presentation().shapeless(),
                        (ignored, level) -> level == null ? ItemStack.EMPTY : exactOutput.copy()),
                baseContract.simulationValidator(),
                baseContract.commitValidator());
    }

    private static RecipeAdapterMatch.Input exactSmithingVariantInput(
            SmithingRecipe recipe,
            int role,
            ItemStack expected,
            Predicate<ItemStack> recipePredicate
    ) {
        ItemKey key = ItemKey.of(expected);
        return RecipeAdapterMatch.Input.of(
                new SmithingVariantInputIdentity(recipe, role, key),
                stack -> recipePredicate.test(stack) && key.equals(ItemKey.of(stack)),
                List.of(key.toStack(1)),
                true,
                1);
    }

    private static Optional<RecipeAdapterMatch.CheckedOutput> smithingVariantOutput(
            SmithingRecipe recipe,
            ItemStack expected,
            List<Map<ItemKey, Long>> allocations,
            long crafts,
            Level level
    ) {
        Optional<RecipeAdapterMatch.CheckedOutput> checked = smithingOutput(
                recipe, allocations, level);
        if (checked.isEmpty() || checked.get().primaryOutputs().size() != 1) return Optional.empty();
        long expectedAmount;
        try {
            expectedAmount = Math.multiplyExact((long) expected.getCount(), crafts);
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
        return checked.get().primaryOutputs().getOrDefault(ItemKey.of(expected), 0L) == expectedAmount
                ? checked
                : Optional.empty();
    }

    private static Optional<List<ItemStack>> uniqueExactStacks(
            List<ItemStack> stacks,
            Level level
    ) {
        Map<ItemKey, ItemStack> unique = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) unique.putIfAbsent(ItemKey.of(stack), stack.copyWithCount(1));
        }
        Map<ItemKey, String> componentIdentities = new HashMap<>();
        for (ItemKey key : unique.keySet()) {
            Optional<String> identity = componentIdentity(key, level);
            if (identity.isEmpty()) return Optional.empty();
            componentIdentities.put(key, identity.get());
        }
        List<ItemStack> ordered = new ArrayList<>(unique.values());
        ordered.sort(Comparator
                .comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .thenComparing(stack -> componentIdentities.get(ItemKey.of(stack))));
        return Optional.of(List.copyOf(ordered));
    }

    private static Optional<String> componentIdentity(ItemKey key, Level level) {
        synchronized (COMPONENT_IDENTITY_CACHE) {
            return COMPONENT_IDENTITY_CACHE
                    .computeIfAbsent(level.registryAccess(), ignored -> new WeakHashMap<>())
                    .computeIfAbsent(
                            key.components(),
                            ignored -> canonicalComponents(
                                    key.components(), level.registryAccess()));
        }
    }

    private static Optional<String> canonicalComponents(
            DataComponentMap components,
            HolderLookup.Provider registries
    ) {
        return DataComponentMap.CODEC.encodeStart(
                        RegistryOps.create(NbtOps.INSTANCE, registries), components)
                .result()
                .map(BuiltInRecipeAdapters::canonicalTag);
    }

    private static String canonicalTag(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            StringBuilder result = new StringBuilder("C{");
            compound.getAllKeys().stream().sorted().forEach(key -> {
                String value = canonicalTag(compound.get(key));
                result.append(key.length()).append(':').append(key)
                        .append(value.length()).append(':').append(value);
            });
            return result.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder result = new StringBuilder("L[");
            for (Tag entry : list) {
                String value = canonicalTag(entry);
                result.append(value.length()).append(':').append(value);
            }
            return result.append(']').toString();
        }
        String value = tag.toString();
        return tag.getId() + ":" + value.length() + ":" + value;
    }

    private static Optional<RecipeAdapterMatch.CheckedOutput> checkedOutput(
            Map<ItemKey, Long> primaryOutputs,
            List<Map<ItemKey, Long>> allocations
    ) {
        Map<ItemKey, Long> remainders = new LinkedHashMap<>();
        try {
            for (Map<ItemKey, Long> allocation : allocations) {
                for (Map.Entry<ItemKey, Long> entry : allocation.entrySet()) {
                    ItemStack consumed = entry.getKey().toStack(1);
                    if (!consumed.hasCraftingRemainingItem()) continue;
                    ItemStack remainder = consumed.getCraftingRemainingItem();
                    if (remainder.isEmpty()) continue;
                    long amount = Math.multiplyExact(entry.getValue(), (long) remainder.getCount());
                    remainders.merge(ItemKey.of(remainder), amount, Math::addExact);
                }
            }
            return Optional.of(new RecipeAdapterMatch.CheckedOutput(primaryOutputs, remainders));
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
    }

    private static ItemStack firstAllocatedStack(Map<ItemKey, Long> allocation) {
        for (Map.Entry<ItemKey, Long> entry : allocation.entrySet()) {
            if (entry.getValue() > 0) return entry.getKey().toStack(1);
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasInput(List<Ingredient> ingredients) {
        return ingredients.stream().anyMatch(ingredient -> !ingredient.isEmpty());
    }

    private static List<RecipeAdapterMatch.Input> ingredientInputs(List<Ingredient> ingredients) {
        List<RecipeAdapterMatch.Input> inputs = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            inputs.add(ingredient.isEmpty()
                    ? RecipeAdapterMatch.Input.empty(ingredient)
                    : RecipeAdapterMatch.Input.of(
                            ingredient,
                            ingredient,
                            Arrays.asList(ingredient.getItems()),
                            ingredient.isSimple(),
                            ingredient.isSimple(),
                            1));
        }
        return List.copyOf(inputs);
    }

    private static RecipeCandidateIndex candidateIndex(List<Ingredient> ingredients) {
        boolean exhaustive = true;
        List<ItemStack> representatives = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            exhaustive &= ingredient.isSimple();
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) representatives.add(stack.copyWithCount(1));
            }
        }
        return exhaustive
                ? RecipeCandidateIndex.exhaustive(representatives)
                : RecipeCandidateIndex.nonExhaustive(representatives);
    }

    private static List<RecipeAdapterMatch.Input> smithingInputs(SmithingRecipe recipe) {
        SmithingRepresentatives representatives = smithingRepresentatives(recipe);
        return List.of(
                RecipeAdapterMatch.Input.of(
                        new SmithingInputIdentity(recipe, 0),
                        recipe::isTemplateIngredient,
                        representatives.templates(),
                        true,
                        1),
                RecipeAdapterMatch.Input.of(
                        new SmithingInputIdentity(recipe, 1),
                        recipe::isBaseIngredient,
                        representatives.bases(),
                        true,
                        1),
                RecipeAdapterMatch.Input.of(
                        new SmithingInputIdentity(recipe, 2),
                        recipe::isAdditionIngredient,
                        representatives.additions(),
                        true,
                        1));
    }

    private static SmithingRepresentatives smithingRepresentatives(SmithingRecipe recipe) {
        synchronized (SMITHING_INPUT_CACHE) {
            return SMITHING_INPUT_CACHE.computeIfAbsent(recipe, ignored -> {
                List<ItemStack> templates = new ArrayList<>();
                List<ItemStack> bases = new ArrayList<>();
                List<ItemStack> additions = new ArrayList<>();
                for (var item : BuiltInRegistries.ITEM) {
                    ItemStack stack = item.getDefaultInstance();
                    if (stack.isEmpty()) continue;
                    if (recipe.isTemplateIngredient(stack)) templates.add(stack.copyWithCount(1));
                    if (recipe.isBaseIngredient(stack)) bases.add(stack.copyWithCount(1));
                    if (recipe.isAdditionIngredient(stack)) additions.add(stack.copyWithCount(1));
                }
                return new SmithingRepresentatives(templates, bases, additions);
            });
        }
    }

    private static RecipeCandidateIndex smithingCandidateIndex(SmithingRecipe recipe) {
        SmithingRepresentatives smithing = smithingRepresentatives(recipe);
        List<ItemStack> representatives = new ArrayList<>(
                smithing.templates().size() + smithing.bases().size() + smithing.additions().size());
        representatives.addAll(smithing.templates());
        representatives.addAll(smithing.bases());
        representatives.addAll(smithing.additions());
        return RecipeCandidateIndex.nonExhaustive(representatives);
    }

    private static <R extends Recipe<?>> RecipeAdapter exact(
            ResourceLocation id,
            int priority,
            Class<R> recipeClass,
            RecipeType<?> recipeType,
            Predicate<R> additionalContract,
            Function<R, RecipeCandidateIndex> candidateIndexFactory,
            VariantContractFactory<R> variantContractFactory,
            BiFunction<R, RecipeAdapterMatch.HolderValidator, RecipeAdapterMatch.Contract>
                    contractFactory
    ) {
        return exact(
                id,
                priority,
                recipeClass,
                recipeType,
                additionalContract,
                candidateIndexFactory,
                variantContractFactory,
                BuiltInRecipeAdapters::exactVariantLookup,
                contractFactory);
    }

    private static <R extends Recipe<?>> RecipeAdapter exact(
            ResourceLocation id,
            int priority,
            Class<R> recipeClass,
            RecipeType<?> recipeType,
            Predicate<R> additionalContract,
            Function<R, RecipeCandidateIndex> candidateIndexFactory,
            VariantContractFactory<R> variantContractFactory,
            LookupOutputMatcher<R> lookupOutputMatcher,
            BiFunction<R, RecipeAdapterMatch.HolderValidator, RecipeAdapterMatch.Contract>
                    contractFactory
    ) {
        return new ExactRecipeAdapter<>(
                id,
                priority,
                recipeClass,
                recipeType,
                additionalContract,
                candidateIndexFactory,
                variantContractFactory,
                lookupOutputMatcher,
                contractFactory);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, path);
    }

    private record SmithingInputIdentity(Recipe<?> recipe, int role) {
    }

    private record SmithingVariantInputIdentity(Recipe<?> recipe, int role, ItemKey key) {
    }

    private record SmithingRepresentatives(
            List<ItemStack> templates,
            List<ItemStack> bases,
            List<ItemStack> additions
    ) {
        private SmithingRepresentatives {
            templates = List.copyOf(templates);
            bases = List.copyOf(bases);
            additions = List.copyOf(additions);
        }
    }

    private record ExactRecipeAdapter<R extends Recipe<?>>(
            ResourceLocation id,
            int priority,
            Class<R> recipeClass,
            RecipeType<?> recipeType,
            Predicate<R> additionalContract,
            Function<R, RecipeCandidateIndex> candidateIndexFactory,
            VariantContractFactory<R> variantContractFactory,
            LookupOutputMatcher<R> lookupOutputMatcher,
            BiFunction<R, RecipeAdapterMatch.HolderValidator, RecipeAdapterMatch.Contract>
                    contractFactory
    ) implements RecipeAdapter {
        @Override
        public boolean supports(RecipeHolder<?> holder) {
            Recipe<?> candidate = holder.value();
            if (candidate.getClass() != recipeClass) return false;
            R recipe = recipeClass.cast(candidate);
            return recipe.getType() == recipeType
                    && !recipe.isSpecial()
                    && !recipe.isIncomplete()
                    && additionalContract.test(recipe);
        }

        @Override
        public RecipeCandidateIndex candidateIndex(RecipeHolder<?> holder) {
            if (!supports(holder)) {
                throw new IllegalArgumentException("Recipe holder is not supported by adapter " + id);
            }
            return candidateIndexFactory.apply(recipeClass.cast(holder.value()));
        }

        @Override
        public RecipeAdapterMatch.Contract contract(RecipeHolder<?> holder) {
            if (!supports(holder)) {
                throw new IllegalArgumentException("Recipe holder is not supported by adapter " + id);
            }
            return contractFactory.apply(recipeClass.cast(holder.value()), this::supports);
        }

        @Override
        public List<RecipeAdapterMatch.Contract> resolveVariants(
                RecipeHolder<?> holder,
                RecipeAdapterMatch.Contract baseContract,
                List<ItemStack> availableStacks,
                Level level
        ) {
            if (!supports(holder)) {
                throw new IllegalArgumentException("Recipe holder is not supported by adapter " + id);
            }
            R recipe = recipeClass.cast(holder.value());
            return List.copyOf(variantContractFactory.resolve(
                    recipe, baseContract, availableStacks, level));
        }

        @Override
        public boolean requiresAvailableStacksForVariants() {
            return id.equals(SMITHING_TRANSFORM_ID) || id.equals(SMITHING_TRIM_ID);
        }

        @Override
        public boolean matchesLookupOutput(
                RecipeHolder<?> holder,
                RecipeAdapterMatch.Contract variantContract,
                ItemStack requestedOutput,
                Level level
        ) {
            if (!supports(holder)) {
                throw new IllegalArgumentException("Recipe holder is not supported by adapter " + id);
            }
            return lookupOutputMatcher.matches(
                    recipeClass.cast(holder.value()), variantContract, requestedOutput, level);
        }

        @Override
        public Optional<RecipeFamilyKey> exactFamilyKey() {
            return Optional.of(new RecipeFamilyKey(recipeClass, recipeType));
        }
    }

    @FunctionalInterface
    private interface VariantContractFactory<R extends Recipe<?>> {
        List<RecipeAdapterMatch.Contract> resolve(
                R recipe,
                RecipeAdapterMatch.Contract baseContract,
                List<ItemStack> availableStacks,
                Level level
        );
    }

    @FunctionalInterface
    private interface LookupOutputMatcher<R extends Recipe<?>> {
        boolean matches(
                R recipe,
                RecipeAdapterMatch.Contract variantContract,
                ItemStack requestedOutput,
                Level level
        );
    }
}

final class RecipeEnergyTable {
    private RecipeEnergyTable() {
    }

    static EnergyCost getCost(Recipe<?> recipe) {
        return BuiltInRecipeAdapters.energyCost(recipe);
    }
}
