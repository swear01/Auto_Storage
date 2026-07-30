package com.swear.autostorage;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class RecipeFamily {
    private final Class<? extends Recipe<?>> exactRecipeClass;
    private final Supplier<? extends RecipeType<?>> recipeType;
    private final ResourceLocation stationDescriptorId;
    private final Function<Recipe<?>, Ingredient> input;
    private final BiFunction<Recipe<?>, HolderLookup.Provider, ItemStack> output;
    private final BiFunction<Recipe<?>, HolderLookup.Provider, TypedRecipePlan> typedPlan;
    private final TypedPlanVariants typedPlanVariants;
    private final Predicate<Recipe<?>> eligibility;
    private final boolean allowSpecial;
    private final Function<Recipe<?>, RecipeFamilyCost> cost;
    private final RecipePresentationKind presentationKind;
    private final Map<Recipe<?>, TypedRecipePlan> typedPlanCache =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Recipe<?>, RecipeAdapterMatch.Contract> typedContractCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    RecipeFamily(
            Class<? extends Recipe<?>> exactRecipeClass,
            Supplier<? extends RecipeType<?>> recipeType,
            ResourceLocation stationDescriptorId,
            Function<Recipe<?>, Ingredient> input,
            BiFunction<Recipe<?>, HolderLookup.Provider, ItemStack> output,
            Function<Recipe<?>, RecipeFamilyCost> cost,
            RecipePresentationKind presentationKind
    ) {
        this.exactRecipeClass = Objects.requireNonNull(exactRecipeClass, "exactRecipeClass");
        this.recipeType = Objects.requireNonNull(recipeType, "recipeType");
        this.stationDescriptorId = Objects.requireNonNull(stationDescriptorId, "stationDescriptorId");
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
        this.typedPlan = null;
        this.typedPlanVariants = null;
        this.eligibility = recipe -> true;
        this.allowSpecial = false;
        this.cost = Objects.requireNonNull(cost, "cost");
        this.presentationKind = Objects.requireNonNull(presentationKind, "presentationKind");
    }

    RecipeFamily(
            Class<? extends Recipe<?>> exactRecipeClass,
            Supplier<? extends RecipeType<?>> recipeType,
            ResourceLocation stationDescriptorId,
            Predicate<Recipe<?>> eligibility,
            BiFunction<Recipe<?>, HolderLookup.Provider, TypedRecipePlan> typedPlan,
            Function<Recipe<?>, RecipeFamilyCost> cost,
            RecipePresentationKind presentationKind,
            boolean allowSpecial
    ) {
        this.exactRecipeClass = Objects.requireNonNull(exactRecipeClass, "exactRecipeClass");
        this.recipeType = Objects.requireNonNull(recipeType, "recipeType");
        this.stationDescriptorId = Objects.requireNonNull(stationDescriptorId, "stationDescriptorId");
        this.input = null;
        this.output = null;
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.allowSpecial = allowSpecial;
        this.typedPlan = Objects.requireNonNull(typedPlan, "typedPlan");
        this.typedPlanVariants = null;
        this.cost = Objects.requireNonNull(cost, "cost");
        this.presentationKind = Objects.requireNonNull(presentationKind, "presentationKind");
    }

    RecipeFamily(
            Class<? extends Recipe<?>> exactRecipeClass,
            Supplier<? extends RecipeType<?>> recipeType,
            ResourceLocation stationDescriptorId,
            Predicate<Recipe<?>> eligibility,
            TypedPlanVariants typedPlanVariants,
            Function<Recipe<?>, RecipeFamilyCost> cost,
            RecipePresentationKind presentationKind,
            boolean allowSpecial
    ) {
        this.exactRecipeClass = Objects.requireNonNull(exactRecipeClass, "exactRecipeClass");
        this.recipeType = Objects.requireNonNull(recipeType, "recipeType");
        this.stationDescriptorId = Objects.requireNonNull(stationDescriptorId, "stationDescriptorId");
        this.input = null;
        this.output = null;
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.allowSpecial = allowSpecial;
        this.typedPlan = null;
        this.typedPlanVariants = Objects.requireNonNull(
                typedPlanVariants, "typedPlanVariants");
        this.cost = Objects.requireNonNull(cost, "cost");
        this.presentationKind = Objects.requireNonNull(presentationKind, "presentationKind");
    }

    RecipeType<?> recipeType() {
        return Objects.requireNonNull(recipeType.get(), "recipeType supplier result");
    }

    ResourceLocation stationDescriptorId() {
        return stationDescriptorId;
    }

    RecipeFamilyKey key() {
        return new RecipeFamilyKey(exactRecipeClass, recipeType());
    }

    RecipeAdapter adapter(ResourceLocation id, int priority) {
        return new FamilyAdapter(Objects.requireNonNull(id, "id"), priority);
    }

    private Ingredient inputFor(Recipe<?> recipe) {
        if (input == null) throw new IllegalStateException("Typed recipe family has no legacy input");
        Ingredient ingredient = Objects.requireNonNull(input.apply(recipe), "recipe family input");
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException("Recipe family input cannot be empty");
        }
        return ingredient;
    }

    private ItemStack outputFor(Recipe<?> recipe, HolderLookup.Provider registries) {
        if (output == null) throw new IllegalStateException("Typed recipe family has no legacy output");
        ItemStack stack = Objects.requireNonNull(
                output.apply(recipe, registries), "recipe family output");
        return stack.copy();
    }

    private TypedRecipePlan typedPlanFor(
            Recipe<?> recipe,
            HolderLookup.Provider registries
    ) {
        if (typedPlan == null) throw new IllegalStateException("Legacy recipe family has no typed plan");
        return typedPlanCache.computeIfAbsent(recipe, ignored ->
                Objects.requireNonNull(typedPlan.apply(recipe, registries), "typed recipe plan"));
    }

    private List<TypedRecipePlan> typedPlansFor(
            Recipe<?> recipe,
            List<ItemStack> availableStacks,
            HolderLookup.Provider registries
    ) {
        if (typedPlan != null) return List.of(typedPlanFor(recipe, registries));
        return List.copyOf(Objects.requireNonNull(
                typedPlanVariants.apply(recipe, List.copyOf(availableStacks), registries),
                "typed recipe plan variants"));
    }

    private boolean isTyped() {
        return typedPlan != null || typedPlanVariants != null;
    }

    private RecipeFamilyCost costFor(Recipe<?> recipe) {
        return Objects.requireNonNull(cost.apply(recipe), "recipe family cost");
    }

    private final class FamilyAdapter implements RecipeAdapter {
        private final ResourceLocation id;
        private final int priority;

        private FamilyAdapter(ResourceLocation id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean supports(RecipeHolder<?> holder) {
            Recipe<?> recipe = holder.value();
            return recipe.getClass() == exactRecipeClass
                    && recipe.getType() == recipeType()
                    && (allowSpecial || !recipe.isSpecial())
                    && (isTyped() || !recipe.isIncomplete())
                    && eligibility.test(recipe);
        }

        @Override
        public RecipeCandidateIndex candidateIndex(RecipeHolder<?> holder) {
            Recipe<?> recipe = checkedRecipe(holder);
            if (isTyped()) return RecipeCandidateIndex.nonExhaustive(List.of());
            Ingredient ingredient = inputFor(recipe);
            List<ItemStack> representatives = Arrays.stream(ingredient.getItems())
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> stack.copyWithCount(1))
                    .toList();
            return ingredient.isSimple()
                    ? RecipeCandidateIndex.exhaustive(representatives)
                    : RecipeCandidateIndex.nonExhaustive(representatives);
        }

        @Override
        public RecipeCandidateIndex candidateIndex(
                RecipeHolder<?> holder,
                Level level
        ) {
            Recipe<?> recipe = checkedRecipe(holder);
            if (typedPlan == null || level == null) return candidateIndex(holder);
            TypedRecipePlan plan = typedPlanFor(recipe, level.registryAccess());
            typedContractCache.computeIfAbsent(recipe, ignored -> typedContract(recipe, plan));
            return typedCandidateIndex(plan, level.registryAccess());
        }

        private RecipeCandidateIndex typedCandidateIndex(
                TypedRecipePlan plan,
                HolderLookup.Provider registries
        ) {
            List<ItemStack> representatives = new java.util.ArrayList<>();
            boolean indexed = false;
            for (TypedRecipeInput input : plan.inputs()) {
                if (input.alternatives().stream().anyMatch(
                        key -> !key.kindId().equals(StorageResourceKindApi.ITEM_KIND))) continue;
                List<ItemStack> inputRepresentatives = input.alternatives().stream()
                        .map(key -> StorageResourceBridge.itemKey(key, registries)
                                .map(item -> item.toStack(1))
                                .orElse(ItemStack.EMPTY))
                        .filter(stack -> !stack.isEmpty())
                        .toList();
                if (inputRepresentatives.size() != input.alternatives().size()) continue;
                indexed = true;
                representatives.addAll(inputRepresentatives);
            }
            return indexed
                    ? RecipeCandidateIndex.exhaustive(representatives)
                    : RecipeCandidateIndex.nonExhaustive(representatives);
        }

        @Override
        public RecipeAdapterMatch.Contract contract(RecipeHolder<?> holder) {
            Recipe<?> recipe = checkedRecipe(holder);
            if (isTyped()) {
                RecipeAdapterMatch.Contract cached = typedContractCache.get(recipe);
                if (cached != null) return cached;
                RecipeAdapterMatch.Presentation presentation = new RecipeAdapterMatch.Presentation(
                        presentationKind,
                        1,
                        1,
                        false,
                        (inputs, level) -> ItemStack.EMPTY);
                RecipeAdapterMatch.HolderValidator validator = this::supports;
                return RecipeAdapterMatch.Contract.pendingTyped(
                        stationDescriptorId, costFor(recipe).toInternal(stationDescriptorId), presentation, validator);
            }
            Ingredient ingredient = inputFor(recipe);
            RecipeAdapterMatch.Input adapterInput = RecipeAdapterMatch.Input.of(
                    ingredient,
                    ingredient,
                    Arrays.asList(ingredient.getItems()),
                    ingredient.isSimple(),
                    ingredient.isSimple(),
                    1);
            RecipeAdapterMatch.OutputResolver outputResolver = (allocations, crafts, level) -> {
                if (level == null) return Optional.empty();
                ItemStack result = outputFor(recipe, level.registryAccess());
                if (result.isEmpty()) return Optional.empty();
                try {
                    long amount = Math.multiplyExact((long) result.getCount(), crafts);
                    return Optional.of(new RecipeAdapterMatch.CheckedOutput(
                            Map.of(ItemKey.of(result), amount), Map.of()));
                } catch (ArithmeticException exception) {
                    return Optional.empty();
                }
            };
            RecipeAdapterMatch.Presentation presentation = new RecipeAdapterMatch.Presentation(
                    presentationKind,
                    1,
                    1,
                    false,
                    (inputs, level) -> level == null
                            ? ItemStack.EMPTY
                            : outputFor(recipe, level.registryAccess()));
            RecipeAdapterMatch.HolderValidator validator = this::supports;
            return new RecipeAdapterMatch.Contract(
                    List.of(adapterInput),
                    stationDescriptorId,
                    costFor(recipe).toInternal(stationDescriptorId),
                    outputResolver,
                    presentation,
                    validator,
                    validator);
        }

        @Override
        public List<RecipeAdapterMatch.Contract> resolveVariants(
                RecipeHolder<?> holder,
                RecipeAdapterMatch.Contract baseContract,
                List<ItemStack> availableStacks,
                Level level
        ) {
            Recipe<?> recipe = checkedRecipe(holder);
            if (isTyped()) {
                if (level == null) return List.of();
                if (typedPlan != null) {
                    if (baseContract.typedRecipePlan() != null) return List.of(baseContract);
                    return List.of(typedContract(
                            recipe, typedPlanFor(recipe, level.registryAccess())));
                }
                return typedPlansFor(recipe, availableStacks, level.registryAccess()).stream()
                        .map(plan -> typedContract(recipe, plan))
                        .toList();
            }
            if (level == null || outputFor(recipe, level.registryAccess()).isEmpty()) return List.of();
            return List.of(baseContract);
        }

        @Override
        public boolean requiresAvailableStacksForVariants() {
            return typedPlanVariants != null;
        }

        @Override
        public boolean matchesLookupOutput(
                RecipeHolder<?> holder,
                RecipeAdapterMatch.Contract variantContract,
                ItemStack requestedOutput,
                Level level
        ) {
            Recipe<?> recipe = checkedRecipe(holder);
            if (level == null || requestedOutput.isEmpty()) return false;
            if (isTyped()) {
                TypedRecipePlan plan = variantContract.typedRecipePlan();
                if (plan == null) return false;
                StorageResourceKey requestedKey = TerminalResourceDisplay.key(
                        requestedOutput).orElse(null);
                if (requestedKey != null) {
                    return requestedKey.equals(plan.selectionOutputKey());
                }
                return plan.selectionOutputKey().kindId().equals(StorageResourceKindApi.ITEM_KIND)
                        && ItemStack.isSameItemSameComponents(
                        plan.presentationOutput(), TerminalDisplayStack.strip(requestedOutput));
            }
            ItemStack result = outputFor(recipe, level.registryAccess());
            return !result.isEmpty() && ItemStack.isSameItemSameComponents(result, requestedOutput);
        }

        private RecipeAdapterMatch.Contract typedContract(
                Recipe<?> recipe,
                TypedRecipePlan plan
        ) {
            RecipeAdapterMatch.OutputResolver outputResolver =
                    (allocations, crafts, currentLevel) ->
                            checkedTypedOutput(plan, allocations, crafts, currentLevel);
            RecipeAdapterMatch.Presentation presentation = new RecipeAdapterMatch.Presentation(
                    presentationKind,
                    plan.width(),
                    plan.height(),
                    plan.shapeless(),
                    (inputs, currentLevel) -> plan.presentationOutput());
            RecipeAdapterMatch.HolderValidator validator = this::supports;
            return new RecipeAdapterMatch.Contract(
                    List.of(),
                    stationDescriptorId,
                    costFor(recipe).toInternal(stationDescriptorId),
                    outputResolver,
                    presentation,
                    validator,
                    validator,
                    plan,
                    false);
        }

        private Optional<RecipeAdapterMatch.CheckedOutput> checkedTypedOutput(
                TypedRecipePlan plan,
                List<Map<ItemKey, Long>> allocations,
                long crafts,
                Level level
        ) {
            if (level == null || crafts <= 0 || !allocations.isEmpty()) return Optional.empty();
            Map<ItemKey, Long> primary = new java.util.LinkedHashMap<>();
            Map<ItemKey, Long> remainders = new java.util.LinkedHashMap<>();
            Map<StorageResourceKey, Long> resourcePrimary = new java.util.LinkedHashMap<>();
            Map<StorageResourceKey, Long> resourceRemainders = new java.util.LinkedHashMap<>();
            try {
                for (TypedRecipeOutput output : plan.outputs()) {
                    long amount = Math.multiplyExact(output.amount(), crafts);
                    if (output.key().kindId().equals(StorageResourceKindApi.ITEM_KIND)) {
                        ItemKey key = StorageResourceBridge.itemKey(
                                output.key(), level.registryAccess()).orElse(null);
                        if (key == null) return Optional.empty();
                        Map<ItemKey, Long> target = output.role() == TypedRecipeOutput.Role.PRIMARY
                                ? primary : remainders;
                        target.merge(key, amount, Math::addExact);
                    } else {
                        Map<StorageResourceKey, Long> target =
                                output.role() == TypedRecipeOutput.Role.PRIMARY
                                        ? resourcePrimary : resourceRemainders;
                        target.merge(output.key(), amount, Math::addExact);
                    }
                }
            } catch (ArithmeticException exception) {
                return Optional.empty();
            }
            if (primary.isEmpty() && resourcePrimary.isEmpty()) return Optional.empty();
            return Optional.of(new RecipeAdapterMatch.CheckedOutput(
                    primary, remainders, resourcePrimary, resourceRemainders));
        }

        @Override
        public Optional<RecipeFamilyKey> exactFamilyKey() {
            return Optional.of(key());
        }

        private Recipe<?> checkedRecipe(RecipeHolder<?> holder) {
            if (!supports(holder)) {
                throw new IllegalArgumentException("Recipe holder is not supported by family " + id);
            }
            return holder.value();
        }
    }

    @FunctionalInterface
    interface TypedPlanVariants {
        List<TypedRecipePlan> apply(
                Recipe<?> recipe,
                List<ItemStack> availableStacks,
                HolderLookup.Provider registries
        );
    }

}

record RecipeFamilyKey(Class<? extends Recipe<?>> recipeClass, RecipeType<?> recipeType) {
    RecipeFamilyKey {
        Objects.requireNonNull(recipeClass, "recipeClass");
        Objects.requireNonNull(recipeType, "recipeType");
    }
}
