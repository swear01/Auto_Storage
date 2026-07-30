package com.swear.autostorage.compat.botania;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.RecipeFamilyCost;
import com.swear.autostorage.RecipeFamilyFactories;
import com.swear.autostorage.RecipePresentationKind;
import com.swear.autostorage.StorageResourceContainerStrategy;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TypedRecipeInput;
import com.swear.autostorage.TypedRecipeOutput;
import com.swear.autostorage.TypedRecipePlan;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.api.recipe.StateIngredient;
import vazkii.botania.common.crafting.BlockTypeIngredient;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;
import vazkii.botania.common.crafting.ElvenTradeRecipe;
import vazkii.botania.common.crafting.ManaInfusionRecipe;
import vazkii.botania.common.crafting.PetalApothecaryRecipe;
import vazkii.botania.common.crafting.RunicAltarRecipe;
import vazkii.botania.common.crafting.StateIngredients;
import vazkii.botania.common.crafting.TerrestrialAgglomerationRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BotaniaCompat {
    private static final ResourceLocation MANA_TABLET_ID =
            ResourceLocation.fromNamespaceAndPath("botania", "mana_tablet");
    private static final long PETAL_WATER_AMOUNT = 1_000;
    private static final long ELVEN_TRADE_MANA = loadElvenTradeManaCost();

    private BotaniaCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Botania descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Botania recipe family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Botania descriptors and recipe families must share one namespace");
        }

        String namespace = machineDescriptors.getNamespace();
        registerStation(
                machineDescriptors,
                "botania_mana_pool",
                "mana_pool");
        recipeFamilies.register("botania_mana_pool", () ->
                RecipeFamilyFactories.deterministicResources(
                        ManaInfusionRecipe.class,
                        () -> BotaniaRecipeTypes.MANA_INFUSION_TYPE,
                        descriptorId(namespace, "botania_mana_pool"),
                        BotaniaCompat::supportsManaInfusion,
                        BotaniaCompat::manaInfusionPlan,
                        recipe -> RecipeFamilyCost.free(),
                        RecipePresentationKind.CRAFTING));

        registerStation(
                machineDescriptors,
                "botania_runic_altar",
                "runic_altar");
        recipeFamilies.register("botania_runic_altar", () ->
                RecipeFamilyFactories.deterministicResources(
                        RunicAltarRecipe.class,
                        () -> BotaniaRecipeTypes.RUNIC_ALTAR_TYPE,
                        descriptorId(namespace, "botania_runic_altar"),
                        BotaniaCompat::supportsRunicAltar,
                        BotaniaCompat::runicAltarPlan,
                        recipe -> RecipeFamilyCost.free(),
                        RecipePresentationKind.CRAFTING));

        registerStation(
                machineDescriptors,
                "botania_terrestrial_agglomeration_plate",
                "terrestrial_agglomeration_plate");
        recipeFamilies.register("botania_terrestrial_agglomeration_plate", () ->
                RecipeFamilyFactories.deterministicResources(
                        TerrestrialAgglomerationRecipe.class,
                        () -> BotaniaRecipeTypes.TERRA_PLATE_TYPE,
                        descriptorId(namespace, "botania_terrestrial_agglomeration_plate"),
                        BotaniaCompat::supportsTerrestrialAgglomeration,
                        BotaniaCompat::terrestrialAgglomerationPlan,
                        recipe -> RecipeFamilyCost.free(),
                        RecipePresentationKind.CRAFTING));

        registerStation(
                machineDescriptors,
                "botania_petal_apothecary",
                "petal_apothecary");
        recipeFamilies.register("botania_petal_apothecary", () ->
                RecipeFamilyFactories.deterministicResources(
                        PetalApothecaryRecipe.class,
                        () -> BotaniaRecipeTypes.PETAL_APOTHECARY_TYPE,
                        descriptorId(namespace, "botania_petal_apothecary"),
                        BotaniaCompat::supportsPetalApothecary,
                        BotaniaCompat::petalApothecaryPlan,
                        recipe -> RecipeFamilyCost.free(),
                        RecipePresentationKind.CRAFTING));

        registerStation(
                machineDescriptors,
                "botania_elven_gateway",
                "elven_gateway_core");
        recipeFamilies.register("botania_elven_gateway", () ->
                RecipeFamilyFactories.deterministicResources(
                        ElvenTradeRecipe.class,
                        () -> BotaniaRecipeTypes.ELVEN_TRADE_TYPE,
                        descriptorId(namespace, "botania_elven_gateway"),
                        BotaniaCompat::supportsElvenTrade,
                        BotaniaCompat::elvenTradePlan,
                        recipe -> RecipeFamilyCost.free(),
                        RecipePresentationKind.CRAFTING));
    }

    public static java.util.Optional<StorageResourceContainerStrategy.Transfer> planContainerDeposit(
            ItemStack singleContainer,
            HolderLookup.Provider registries
    ) {
        Objects.requireNonNull(registries, "registries");
        if (!isLocalManaTablet(singleContainer)) return java.util.Optional.empty();
        ItemStack copy = singleContainer.copyWithCount(1);
        ManaItem manaItem = ManaItem.LOOKUP.find(copy);
        if (manaItem == null) return java.util.Optional.empty();
        int before = manaItem.getMana();
        int max = manaItem.getMaxMana();
        if (before <= 0 || max <= 0 || before > max) return java.util.Optional.empty();
        manaItem.addMana(-before);
        if (manaItem.getMana() != 0 || manaItem.getMaxMana() != max) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new StorageResourceContainerStrategy.Transfer(
                manaKey(), before, copy));
    }

    public static java.util.Optional<StorageResourceContainerStrategy.Transfer> planContainerWithdraw(
            ItemStack singleContainer,
            StorageResourceKey key,
            long maxAmount,
            HolderLookup.Provider registries
    ) {
        Objects.requireNonNull(registries, "registries");
        if (!key.equals(manaKey())
                || maxAmount <= 0
                || !isLocalManaTablet(singleContainer)) {
            return java.util.Optional.empty();
        }
        ItemStack copy = singleContainer.copyWithCount(1);
        ManaItem manaItem = ManaItem.LOOKUP.find(copy);
        if (manaItem == null) return java.util.Optional.empty();
        int before = manaItem.getMana();
        int max = manaItem.getMaxMana();
        if (before < 0 || max <= 0 || before > max) return java.util.Optional.empty();
        int amount = (int) Math.min(Math.min(maxAmount, (long) max - before), Integer.MAX_VALUE);
        if (amount <= 0) return java.util.Optional.empty();
        manaItem.addMana(amount);
        if (manaItem.getMana() != before + amount || manaItem.getMaxMana() != max) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new StorageResourceContainerStrategy.Transfer(
                key, amount, copy));
    }

    private static void registerStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            String path,
            String itemPath
    ) {
        ResourceLocation id = descriptorId(machineDescriptors.getNamespace(), path);
        machineDescriptors.register(path, () -> {
            Item item = requiredItem(itemPath);
            return MachineDescriptor.installable(
                    id,
                    new ItemStack(item),
                    Ingredient.of(item),
                    MachineEnergyTable.Category.INSTANT,
                    1,
                    null,
                    0);
        });
    }

    private static boolean supportsManaInfusion(ManaInfusionRecipe recipe) {
        if (recipe.getManaToConsume() <= 0
                || recipe.getIngredients().size() != 1
                || !isExactIngredient(recipe.getIngredients().getFirst())
                || recipe.getResultItem(null).isEmpty()) {
            return false;
        }
        StateIngredient catalyst = recipe.getRecipeCatalyst();
        if (catalyst == StateIngredients.NONE) return true;
        if (catalyst == null || catalyst.getClass() != BlockTypeIngredient.class) return false;
        Item catalystItem = ((BlockTypeIngredient) catalyst).block().asItem();
        return catalystItem != Items.AIR
                && Arrays.stream(recipe.getIngredients().getFirst().getItems())
                .noneMatch(stack -> stack.is(catalystItem));
    }

    private static boolean supportsRunicAltar(RunicAltarRecipe recipe) {
        int inputCount = recipe.getIngredients().size()
                + recipe.getCatalysts().size()
                + 2;
        return recipe.getMana() > 0
                && inputCount <= 9
                && !recipe.getOutput().isEmpty()
                && allExact(recipe.getIngredients())
                && allExact(recipe.getCatalysts())
                && isExactIngredient(recipe.getReagent())
                && rolesAreDisjoint(
                recipe.getIngredients(), recipe.getCatalysts(), recipe.getReagent())
                && retainedIngredientsAreUnambiguous(recipe.getCatalysts());
    }

    private static boolean supportsTerrestrialAgglomeration(
            TerrestrialAgglomerationRecipe recipe
    ) {
        return recipe.getMana() > 0
                && !recipe.getOutput().isEmpty()
                && !recipe.getIngredients().isEmpty()
                && recipe.getIngredients().size() + 1 <= 9
                && allExact(recipe.getIngredients());
    }

    private static boolean supportsPetalApothecary(PetalApothecaryRecipe recipe) {
        return !recipe.getOutput().isEmpty()
                && !recipe.getIngredients().isEmpty()
                && recipe.getIngredients().size() + 2 <= 9
                && allExact(recipe.getIngredients())
                && isExactIngredient(recipe.getReagent());
    }

    private static boolean supportsElvenTrade(ElvenTradeRecipe recipe) {
        return !recipe.getIngredients().isEmpty()
                && recipe.getIngredients().size() <= 9
                && allExact(recipe.getIngredients())
                && !recipe.getOutputs().isEmpty()
                && recipe.getOutputs().stream().noneMatch(ItemStack::isEmpty);
    }

    private static TypedRecipePlan manaInfusionPlan(
            ManaInfusionRecipe recipe,
            HolderLookup.Provider registries
    ) {
        List<TypedRecipeInput> inputs = new ArrayList<>();
        inputs.add(consumed(recipe.getIngredients().getFirst(), registries));
        StateIngredient catalyst = recipe.getRecipeCatalyst();
        if (catalyst != StateIngredients.NONE) {
            BlockTypeIngredient block = (BlockTypeIngredient) catalyst;
            inputs.add(TypedRecipeInput.catalyst(
                    StorageResourceKey.item(new ItemStack(block.block()), registries), 1));
        }
        inputs.add(TypedRecipeInput.consume(manaKey(), recipe.getManaToConsume()));
        ItemStack output = recipe.getResultItem(registries).copy();
        return plan(inputs, List.of(output), output, registries);
    }

    private static TypedRecipePlan runicAltarPlan(
            RunicAltarRecipe recipe,
            HolderLookup.Provider registries
    ) {
        List<TypedRecipeInput> inputs = new ArrayList<>();
        recipe.getIngredients().forEach(ingredient ->
                inputs.add(consumed(ingredient, registries)));
        inputs.addAll(retained(recipe.getCatalysts(), registries));
        inputs.add(consumed(recipe.getReagent(), registries));
        inputs.add(TypedRecipeInput.consume(manaKey(), recipe.getMana()));
        ItemStack output = recipe.getResultItem(registries).copy();
        return plan(inputs, List.of(output), output, registries);
    }

    private static TypedRecipePlan terrestrialAgglomerationPlan(
            TerrestrialAgglomerationRecipe recipe,
            HolderLookup.Provider registries
    ) {
        List<TypedRecipeInput> inputs = new ArrayList<>();
        recipe.getIngredients().forEach(ingredient ->
                inputs.add(consumed(ingredient, registries)));
        inputs.add(TypedRecipeInput.consume(manaKey(), recipe.getMana()));
        ItemStack output = recipe.getResultItem(registries).copy();
        return plan(inputs, List.of(output), output, registries);
    }

    private static TypedRecipePlan petalApothecaryPlan(
            PetalApothecaryRecipe recipe,
            HolderLookup.Provider registries
    ) {
        List<TypedRecipeInput> inputs = new ArrayList<>();
        recipe.getIngredients().forEach(ingredient ->
                inputs.add(consumed(ingredient, registries)));
        inputs.add(consumed(recipe.getReagent(), registries));
        inputs.add(TypedRecipeInput.consume(
                StorageResourceKey.fluid(
                        new FluidStack(Fluids.WATER, 1), registries),
                PETAL_WATER_AMOUNT));
        ItemStack output = recipe.getResultItem(registries).copy();
        return plan(inputs, List.of(output), output, registries);
    }

    private static TypedRecipePlan elvenTradePlan(
            ElvenTradeRecipe recipe,
            HolderLookup.Provider registries
    ) {
        List<TypedRecipeInput> inputs = new ArrayList<>();
        recipe.getIngredients().forEach(ingredient ->
                inputs.add(consumed(ingredient, registries)));
        inputs.add(TypedRecipeInput.consume(manaKey(), ELVEN_TRADE_MANA));
        List<ItemStack> outputs = recipe.getOutputs().stream().map(ItemStack::copy).toList();
        return plan(inputs, outputs, outputs.getFirst(), registries);
    }

    private static long loadElvenTradeManaCost() {
        try {
            long cost = Class.forName(
                            "vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity")
                    .getField("MANA_COST")
                    .getLong(null);
            if (cost <= 0) {
                throw new IllegalStateException(
                        "Botania Elven Trade MANA_COST must be positive");
            }
            return cost;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Installed Botania does not expose Elven Trade MANA_COST", exception);
        }
    }

    private static TypedRecipePlan plan(
            List<TypedRecipeInput> inputs,
            List<ItemStack> outputStacks,
            ItemStack presentationOutput,
            HolderLookup.Provider registries
    ) {
        LinkedHashMap<StorageResourceKey, Long> outputs = new LinkedHashMap<>();
        for (ItemStack output : outputStacks) {
            if (output.isEmpty()) {
                throw new IllegalArgumentException("Botania recipe output cannot be empty");
            }
            outputs.merge(
                    StorageResourceKey.item(output.copyWithCount(1), registries),
                    (long) output.getCount(),
                    Math::addExact);
        }
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        inputs.forEach(builder::input);
        boolean primary = true;
        for (Map.Entry<StorageResourceKey, Long> output : outputs.entrySet()) {
            builder.output(primary
                    ? TypedRecipeOutput.primary(output.getKey(), output.getValue())
                    : TypedRecipeOutput.remainder(output.getKey(), output.getValue()));
            primary = false;
        }
        int width = Math.min(3, inputs.size());
        int height = (inputs.size() + width - 1) / width;
        return builder
                .presentationOutput(presentationOutput)
                .layout(width, height, true)
                .build();
    }

    private static TypedRecipeInput consumed(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        List<ItemStack> representatives = representatives(ingredient);
        List<StorageResourceKey> alternatives = representatives.stream()
                .map(stack -> StorageResourceKey.item(stack, registries))
                .toList();
        Map<StorageResourceKey, TypedRecipeOutput> remainders = new LinkedHashMap<>();
        for (int index = 0; index < representatives.size(); index++) {
            ItemStack stack = representatives.get(index);
            if (!stack.hasCraftingRemainingItem()) continue;
            ItemStack remainder = stack.getCraftingRemainingItem().copy();
            if (remainder.isEmpty()) continue;
            remainders.put(
                    alternatives.get(index),
                    TypedRecipeOutput.remainder(
                            StorageResourceKey.item(remainder, registries),
                            remainder.getCount()));
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
                    .toList();
            grouped.merge(alternatives, 1L, Math::addExact);
        }
        return grouped.entrySet().stream()
                .map(entry -> TypedRecipeInput.catalystAny(
                        entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<ItemStack> representatives(Ingredient ingredient) {
        LinkedHashMap<ItemStackKey, ItemStack> unique = new LinkedHashMap<>();
        Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .forEach(stack -> unique.putIfAbsent(new ItemStackKey(stack), stack));
        if (unique.isEmpty()) {
            throw new IllegalArgumentException(
                    "Botania recipe ingredient has no exact item representatives");
        }
        return List.copyOf(unique.values());
    }

    private static boolean allExact(List<Ingredient> ingredients) {
        return ingredients.stream().allMatch(BotaniaCompat::isExactIngredient);
    }

    private static boolean isExactIngredient(Ingredient ingredient) {
        return ingredient != null
                && !ingredient.isEmpty()
                && ingredient.isSimple()
                && Arrays.stream(ingredient.getItems())
                .anyMatch(stack -> !stack.isEmpty());
    }

    private static boolean rolesAreDisjoint(
            List<Ingredient> consumed,
            List<Ingredient> retained,
            Ingredient reagent
    ) {
        Set<Item> consumedItems = ingredientItems(consumed);
        consumedItems.addAll(ingredientItems(List.of(reagent)));
        Set<Item> retainedItems = ingredientItems(retained);
        return consumedItems.stream().noneMatch(retainedItems::contains);
    }

    private static boolean retainedIngredientsAreUnambiguous(List<Ingredient> ingredients) {
        List<Set<Item>> groups = ingredients.stream()
                .map(ingredient -> Set.copyOf(ingredientItems(List.of(ingredient))))
                .toList();
        for (int first = 0; first < groups.size(); first++) {
            for (int second = first + 1; second < groups.size(); second++) {
                Set<Item> overlap = new HashSet<>(groups.get(first));
                overlap.retainAll(groups.get(second));
                if (!overlap.isEmpty() && !groups.get(first).equals(groups.get(second))) {
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

    private static boolean isLocalManaTablet(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getCount() == 1
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(MANA_TABLET_ID);
    }

    private static Item requiredItem(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("botania", path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Botania station item " + id);
        }
        return item;
    }

    private static ResourceLocation descriptorId(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static StorageResourceKey manaKey() {
        return StorageResourceKey.of(
                StorageResourceKindApi.BOTANIA_MANA_KIND,
                StorageResourceKindApi.BOTANIA_MANA_KIND,
                new CompoundTag());
    }

    private record ItemStackKey(ItemStack stack) {
        private ItemStackKey {
            stack = stack.copyWithCount(1);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ItemStackKey key
                    && ItemStack.isSameItemSameComponents(stack, key.stack);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack);
        }
    }
}
