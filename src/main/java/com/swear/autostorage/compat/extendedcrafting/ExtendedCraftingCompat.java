package com.swear.autostorage.compat.extendedcrafting;

import com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.UltimateSingularityRecipe;
import com.blakebr0.extendedcrafting.init.ModBlocks;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.singularity.SingularityRegistry;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineVariant;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.RecipeFamilyCost;
import com.swear.autostorage.RecipeFamilyFactories;
import com.swear.autostorage.RecipePresentationKind;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.TypedRecipeInput;
import com.swear.autostorage.TypedRecipeOutput;
import com.swear.autostorage.TypedRecipePlan;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExtendedCraftingCompat {
    public static final String REGISTRY_PATH = "extended_crafting_table";
    private static final String ULTIMATE_SINGULARITY_REGISTRY_PATH =
            "extended_crafting_ultimate_singularity";
    private static final int MAX_POSITIONS = 81;
    private static final Field TRANSFORMER = transformerField();

    private ExtendedCraftingCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)
                || !recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)
                || !machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException("Extended Crafting registers target incompatible registries");
        }

        ResourceLocation descriptorId = descriptorId(machineDescriptors.getNamespace());
        machineDescriptors.register(REGISTRY_PATH, () -> MachineDescriptor.installableVariants(
                descriptorId,
                new ItemStack(ModBlocks.ULTIMATE_TABLE.get()).getHoverName(),
                () -> List.of(MachineVariant.of(
                        new ItemStack(ModBlocks.ULTIMATE_TABLE.get()), MachineWorkRate.ZERO)),
                MachineEnergyTable.Category.INSTANT,
                1,
                null));
        recipeFamilies.register(REGISTRY_PATH, () -> RecipeFamilyFactories.deterministicResources(
                ShapedTableRecipe.class,
                ModRecipeTypes.TABLE,
                descriptorId,
                ExtendedCraftingCompat::supports,
                ExtendedCraftingCompat::plan,
                recipe -> RecipeFamilyCost.free(),
                RecipePresentationKind.CRAFTING));
        recipeFamilies.register(
                ULTIMATE_SINGULARITY_REGISTRY_PATH,
                () -> RecipeFamilyFactories.deterministicResources(
                        UltimateSingularityRecipe.class,
                        ModRecipeTypes.TABLE,
                        descriptorId,
                        recipe -> supportsIngredients(recipe.getIngredients()),
                        (recipe, registries) -> plan(
                                recipe.getIngredients(),
                                recipe.getResultItem(registries),
                                registries),
                        recipe -> RecipeFamilyCost.free(),
                        RecipePresentationKind.CRAFTING));
    }

    public static ResourceLocation descriptorId(String namespace) {
        return ResourceLocation.fromNamespaceAndPath(namespace, REGISTRY_PATH);
    }

    public static void refreshRuntimeData() {
        SingularityRegistry.getInstance().loadSingularities();
    }

    private static boolean supports(ShapedTableRecipe recipe) {
        if (recipe.getWidth() < 1 || recipe.getWidth() > 9
                || recipe.getHeight() < 1 || recipe.getHeight() > 9
                || recipe.getIngredients().size() > MAX_POSITIONS
                || hasTransformer(recipe)) return false;
        LinkedHashMap<InputSignature, Integer> groups = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            InputSignature signature = signature(ingredient);
            if (signature == null) return false;
            groups.merge(signature, 1, Math::addExact);
        }
        return !groups.isEmpty();
    }

    private static boolean supportsIngredients(List<Ingredient> ingredients) {
        if (ingredients.isEmpty() || ingredients.size() > MAX_POSITIONS) return false;
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty() || signature(ingredient) == null) return false;
        }
        return true;
    }

    private static TypedRecipePlan plan(
            ShapedTableRecipe recipe,
            HolderLookup.Provider registries
    ) {
        if (!supports(recipe)) {
            throw new IllegalArgumentException("Unsupported Extended Crafting shaped table recipe");
        }
        return plan(recipe.getIngredients(), recipe.getResultItem(registries), registries);
    }

    private static TypedRecipePlan plan(
            List<Ingredient> ingredients,
            ItemStack result,
            HolderLookup.Provider registries
    ) {
        LinkedHashMap<InputSignature, Integer> groups = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.isEmpty()) groups.merge(signature(ingredient), 1, Math::addExact);
        }

        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        for (Map.Entry<InputSignature, Integer> entry : groups.entrySet()) {
            List<StorageResourceKey> alternatives = entry.getKey().options().stream()
                    .map(option -> StorageResourceKey.item(option.stack(), registries))
                    .toList();
            LinkedHashMap<StorageResourceKey, TypedRecipeOutput> remainders = new LinkedHashMap<>();
            for (int index = 0; index < alternatives.size(); index++) {
                ItemStack remainder = entry.getKey().options().get(index).remainder();
                if (!remainder.isEmpty()) {
                    remainders.put(
                            alternatives.get(index),
                            TypedRecipeOutput.remainder(
                                    StorageResourceKey.item(remainder, registries),
                                    remainder.getCount()));
                }
            }
            builder.input(remainders.isEmpty()
                    ? TypedRecipeInput.consumeAny(alternatives, entry.getValue())
                    : TypedRecipeInput.consumeAnyWithRemainders(
                            alternatives, entry.getValue(), remainders));
        }

        ItemStack output = result.copy();
        StorageResourceKey outputKey = StorageResourceKey.item(output, registries);
        int width = Math.min(3, groups.size());
        int height = Math.min(3, (groups.size() + width - 1) / width);
        return builder
                .output(TypedRecipeOutput.primary(outputKey, output.getCount()))
                .presentationOutput(output)
                .layout(width, height, true)
                .build();
    }

    private static InputSignature signature(Ingredient ingredient) {
        List<Option> options = Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .map(stack -> new Option(stack, stack.hasCraftingRemainingItem()
                        ? stack.getCraftingRemainingItem() : ItemStack.EMPTY))
                .distinct()
                .toList();
        return options.isEmpty() ? null : new InputSignature(options);
    }

    private static boolean hasTransformer(ShapedTableRecipe recipe) {
        try {
            return TRANSFORMER.get(recipe) != null;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect Extended Crafting remainder transformer", exception);
        }
    }

    private static Field transformerField() {
        try {
            Field field = ShapedTableRecipe.class.getDeclaredField("transformer");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Extended Crafting shaped recipe API is incompatible", exception);
        }
    }

    private record InputSignature(List<Option> options) {
        private InputSignature {
            options = List.copyOf(options);
        }
    }

    private record Option(ItemStack stack, ItemStack remainder) {
        private Option {
            stack = stack.copyWithCount(1);
            remainder = remainder.copy();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Option option
                    && ItemStack.isSameItemSameComponents(stack, option.stack)
                    && stack.getCount() == option.stack.getCount()
                    && ItemStack.isSameItemSameComponents(remainder, option.remainder)
                    && remainder.getCount() == option.remainder.getCount();
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    ItemStack.hashItemAndComponents(stack), stack.getCount(),
                    ItemStack.hashItemAndComponents(remainder), remainder.getCount());
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }

        @Override
        public ItemStack remainder() {
            return remainder.copy();
        }
    }
}
