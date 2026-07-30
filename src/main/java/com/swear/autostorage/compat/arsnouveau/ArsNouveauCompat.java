package com.swear.autostorage.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
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
import com.swear.autostorage.StorageResourceHandler;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TypedRecipeInput;
import com.swear.autostorage.TypedRecipeOutput;
import com.swear.autostorage.TypedRecipePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class ArsNouveauCompat {
    private static final String MOD_ID = "ars_nouveau";
    private static final int IMBUEMENT_WORK = 100;
    private static final int APPARATUS_WORK = 210;
    private static final TagKey<Item> APPARATUS_NOT_CONSUMED = TagKey.create(
            Registries.ITEM, arsId("apparatus_not_consumed"));

    private ArsNouveauCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Ars Nouveau descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Ars Nouveau family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Ars Nouveau descriptors and families must share one namespace");
        }

        String namespace = machineDescriptors.getNamespace();
        ResourceLocation imbuementId = descriptorId(namespace, "imbuement_chamber");
        registerStation(machineDescriptors, imbuementId, "imbuement_chamber");
        recipeFamilies.register(imbuementId.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        ImbuementRecipe.class,
                        RecipeRegistry.IMBUEMENT_TYPE::get,
                        imbuementId,
                        ArsNouveauCompat::supportsImbuement,
                        ArsNouveauCompat::imbuementPlan,
                        recipe -> RecipeFamilyCost.stationWork(IMBUEMENT_WORK),
                        RecipePresentationKind.CRAFTING));

        ResourceLocation apparatusId = descriptorId(namespace, "enchanting_apparatus");
        registerStation(machineDescriptors, apparatusId, "enchanting_apparatus");
        recipeFamilies.register(apparatusId.getPath(), () ->
                RecipeFamilyFactories.deterministicResourceVariants(
                        EnchantingApparatusRecipe.class,
                        RecipeRegistry.APPARATUS_TYPE::get,
                        apparatusId,
                        ArsNouveauCompat::supportsApparatus,
                        ArsNouveauCompat::apparatusPlans,
                        recipe -> RecipeFamilyCost.stationWork(APPARATUS_WORK),
                        RecipePresentationKind.CRAFTING));
    }

    public static Optional<StorageResourceHandler> findSourceBlockHandler(
            Level level,
            BlockPos pos,
            Direction side
    ) {
        ISourceCap source = level.getCapability(
                CapabilityRegistry.SOURCE_CAPABILITY, pos, side);
        return source == null
                ? Optional.empty()
                : Optional.of(new SourceBlockHandler(source));
    }

    private static void registerStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            ResourceLocation descriptorId,
            String itemPath
    ) {
        machineDescriptors.register(descriptorId.getPath(), () -> {
            Item item = requiredItem(itemPath);
            return MachineDescriptor.installableVariants(
                    descriptorId,
                    new ItemStack(item).getHoverName(),
                    () -> List.of(MachineVariant.of(
                            new ItemStack(item), MachineWorkRate.ONE)),
                    MachineEnergyTable.Category.PROCESS,
                    MachineDescriptorApi.MAX_INSTALLED_COUNT,
                    null);
        });
    }

    private static boolean supportsImbuement(ImbuementRecipe recipe) {
        return recipe.getSource() >= 0
                && !recipe.getOutput().isEmpty()
                && recipe.getPedestalItems().size() <= 3
                && 1 + recipe.getPedestalItems().size()
                + (recipe.getSource() > 0 ? 1 : 0) <= 9
                && exact(recipe.getInput())
                && allExact(recipe.getPedestalItems())
                && retainedInputsAreCompatible(
                        List.of(recipe.getInput()), recipe.getPedestalItems());
    }

    private static boolean supportsApparatus(EnchantingApparatusRecipe recipe) {
        if (recipe.sourceCost() < 0
                || recipe.result().isEmpty()
                || recipe.pedestalItems().size() > 8
                || 1 + recipe.pedestalItems().size()
                + (recipe.sourceCost() > 0 ? 1 : 0) > 9
                || !exact(recipe.reagent())
                || !allExact(recipe.pedestalItems())) {
            return false;
        }
        for (Ingredient ingredient : recipe.pedestalItems()) {
            List<ItemStack> stacks = representatives(ingredient);
            boolean retained = stacks.getFirst().is(APPARATUS_NOT_CONSUMED);
            if (stacks.stream().anyMatch(stack ->
                    stack.is(APPARATUS_NOT_CONSUMED) != retained)) {
                return false;
            }
        }
        List<Ingredient> consumed = new ArrayList<>();
        consumed.add(recipe.reagent());
        List<Ingredient> retained = new ArrayList<>();
        for (Ingredient ingredient : recipe.pedestalItems()) {
            if (representatives(ingredient).getFirst().is(APPARATUS_NOT_CONSUMED)) {
                retained.add(ingredient);
            } else {
                consumed.add(ingredient);
            }
        }
        return retainedInputsAreCompatible(consumed, retained);
    }

    private static TypedRecipePlan imbuementPlan(
            ImbuementRecipe recipe,
            HolderLookup.Provider registries
    ) {
        List<TypedRecipeInput> inputs = new ArrayList<>();
        inputs.add(consumed(recipe.getInput(), registries));
        inputs.addAll(retained(recipe.getPedestalItems(), registries));
        addSource(inputs, recipe.getSource());
        return plan(inputs, recipe.getOutput(), registries);
    }

    private static List<TypedRecipePlan> apparatusPlans(
            EnchantingApparatusRecipe recipe,
            List<ItemStack> availableStacks,
            HolderLookup.Provider registries
    ) {
        if (!recipe.keepNbtOfReagent()) {
            return List.of(apparatusPlan(recipe, null, registries));
        }
        TreeMap<StorageResourceKey, ItemStack> reagents = new TreeMap<>();
        availableStacks.stream()
                .filter(stack -> !stack.isEmpty() && recipe.reagent().test(stack))
                .map(stack -> stack.copyWithCount(1))
                .forEach(stack -> reagents.putIfAbsent(
                        StorageResourceKey.item(stack, registries), stack));
        return reagents.values().stream()
                .map(reagent -> apparatusPlan(recipe, reagent, registries))
                .toList();
    }

    private static TypedRecipePlan apparatusPlan(
            EnchantingApparatusRecipe recipe,
            ItemStack exactReagent,
            HolderLookup.Provider registries
    ) {
        List<TypedRecipeInput> inputs = new ArrayList<>();
        inputs.add(exactReagent == null
                ? consumed(recipe.reagent(), registries)
                : consumed(exactReagent, registries));
        for (Ingredient ingredient : recipe.pedestalItems()) {
            List<ItemStack> stacks = representatives(ingredient);
            if (stacks.getFirst().is(APPARATUS_NOT_CONSUMED)) {
                inputs.add(catalyst(stacks, registries));
            } else {
                inputs.add(consumed(stacks, registries));
            }
        }
        addSource(inputs, recipe.sourceCost());

        ItemStack output = recipe.result().copy();
        if (exactReagent != null) {
            output.applyComponents(exactReagent.getComponentsPatch());
            if (output.has(DataComponents.DAMAGE)) output.setDamageValue(0);
        }
        return plan(inputs, output, registries);
    }

    private static TypedRecipePlan plan(
            List<TypedRecipeInput> inputs,
            ItemStack output,
            HolderLookup.Provider registries
    ) {
        StorageResourceKey outputKey = StorageResourceKey.item(
                output.copyWithCount(1), registries);
        int width = Math.min(3, inputs.size());
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        inputs.forEach(builder::input);
        return builder
                .presentationOutput(output)
                .layout(width, (inputs.size() + width - 1) / width, true)
                .output(TypedRecipeOutput.primary(outputKey, output.getCount()))
                .build();
    }

    private static void addSource(List<TypedRecipeInput> inputs, int amount) {
        if (amount > 0) {
            inputs.add(TypedRecipeInput.consume(sourceKey(), amount));
        }
    }

    private static TypedRecipeInput consumed(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        return consumed(representatives(ingredient), registries);
    }

    private static TypedRecipeInput consumed(
            ItemStack stack,
            HolderLookup.Provider registries
    ) {
        return consumed(List.of(stack.copyWithCount(1)), registries);
    }

    private static TypedRecipeInput consumed(
            List<ItemStack> stacks,
            HolderLookup.Provider registries
    ) {
        List<StorageResourceKey> alternatives = stacks.stream()
                .map(stack -> StorageResourceKey.item(stack, registries))
                .distinct()
                .toList();
        Map<StorageResourceKey, TypedRecipeOutput> remainders = new LinkedHashMap<>();
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (!stack.hasCraftingRemainingItem()) continue;
            ItemStack remainder = stack.getCraftingRemainingItem();
            if (!remainder.isEmpty()) {
                remainders.put(
                        StorageResourceKey.item(stack, registries),
                        TypedRecipeOutput.remainder(
                                StorageResourceKey.item(remainder, registries),
                                remainder.getCount()));
            }
        }
        return remainders.isEmpty()
                ? TypedRecipeInput.consumeAny(alternatives, 1)
                : TypedRecipeInput.consumeAnyWithRemainders(
                        alternatives, 1, remainders);
    }

    private static List<TypedRecipeInput> retained(
            List<Ingredient> ingredients,
            HolderLookup.Provider registries
    ) {
        LinkedHashMap<List<StorageResourceKey>, Long> grouped = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            List<StorageResourceKey> alternatives = representatives(ingredient).stream()
                    .map(stack -> StorageResourceKey.item(stack, registries))
                    .distinct()
                    .toList();
            grouped.merge(alternatives, 1L, Math::addExact);
        }
        return grouped.entrySet().stream()
                .map(entry -> TypedRecipeInput.catalystAny(
                        entry.getKey(), entry.getValue()))
                .toList();
    }

    private static TypedRecipeInput catalyst(
            List<ItemStack> stacks,
            HolderLookup.Provider registries
    ) {
        return TypedRecipeInput.catalystAny(
                stacks.stream()
                        .map(stack -> StorageResourceKey.item(stack, registries))
                        .distinct()
                        .toList(),
                1);
    }

    private static List<ItemStack> representatives(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .distinct()
                .toList();
    }

    private static boolean allExact(List<Ingredient> ingredients) {
        return ingredients.stream().allMatch(ArsNouveauCompat::exact);
    }

    private static boolean retainedInputsAreCompatible(
            List<Ingredient> consumed,
            List<Ingredient> retained
    ) {
        Set<Item> consumedItems = ingredientItems(consumed);
        List<Set<Item>> retainedGroups = retained.stream()
                .map(ingredient -> Set.copyOf(ingredientItems(List.of(ingredient))))
                .toList();
        if (retainedGroups.stream().flatMap(Set::stream).anyMatch(consumedItems::contains)) {
            return false;
        }
        for (int first = 0; first < retainedGroups.size(); first++) {
            for (int second = first + 1; second < retainedGroups.size(); second++) {
                Set<Item> overlap = new HashSet<>(retainedGroups.get(first));
                overlap.retainAll(retainedGroups.get(second));
                if (!overlap.isEmpty()
                        && !retainedGroups.get(first).equals(retainedGroups.get(second))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Set<Item> ingredientItems(List<Ingredient> ingredients) {
        Set<Item> items = new HashSet<>();
        ingredients.stream()
                .flatMap(ingredient -> Arrays.stream(ingredient.getItems()))
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::getItem)
                .forEach(items::add);
        return items;
    }

    private static boolean exact(Ingredient ingredient) {
        return ingredient != null
                && !ingredient.isEmpty()
                && ingredient.isSimple()
                && Arrays.stream(ingredient.getItems()).anyMatch(stack -> !stack.isEmpty());
    }

    private static Item requiredItem(String path) {
        ResourceLocation id = arsId(path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Ars Nouveau station item " + id);
        }
        return item;
    }

    private static ResourceLocation descriptorId(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(
                namespace, "ars_nouveau_" + path);
    }

    private static ResourceLocation arsId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static StorageResourceKey sourceKey() {
        return StorageResourceKey.of(
                StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND,
                StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND,
                new CompoundTag());
    }

    private record SourceBlockHandler(ISourceCap source) implements StorageResourceHandler {
        @Override
        public List<StorageResourceKey> getStoredResources() {
            return source.getSource() > 0 ? List.of(sourceKey()) : List.of();
        }

        @Override
        public long getAmount(StorageResourceKey key) {
            return key.equals(sourceKey()) ? source.getSource() : 0;
        }

        @Override
        public long insert(StorageResourceKey key, long amount, boolean simulate) {
            return key.equals(sourceKey()) && amount > 0
                    ? source.receiveSource((int) Math.min(amount, Integer.MAX_VALUE), simulate)
                    : 0;
        }

        @Override
        public long extract(StorageResourceKey key, long amount, boolean simulate) {
            return key.equals(sourceKey()) && amount > 0
                    ? source.extractSource((int) Math.min(amount, Integer.MAX_VALUE), simulate)
                    : 0;
        }
    }
}
