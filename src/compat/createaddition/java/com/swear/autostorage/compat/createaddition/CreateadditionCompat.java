package com.swear.autostorage.compat.createaddition;

import com.mrh0.createaddition.config.CommonConfig;
import com.mrh0.createaddition.index.CARecipes;
import com.mrh0.createaddition.recipe.charging.ChargingRecipe;
import com.mrh0.createaddition.recipe.rolling.RollingRecipe;
import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CreateadditionCompat {
    private static final String MOD_ID = "createaddition";
    private static final ResourceLocation ROLLING_MILL_ITEM =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "rolling_mill");
    private static final ResourceLocation TESLA_COIL_ITEM =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "tesla_coil");

    private CreateadditionCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Create Crafts & Additions descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Create Crafts & Additions family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Create Crafts & Additions descriptors and families must share one namespace");
        }

        ResourceLocation rollingId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "createaddition_rolling_mill");
        ResourceLocation chargingId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "createaddition_tesla_coil");

        machineDescriptors.register(rollingId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        rollingId,
                        Component.translatable(
                                "gui.auto_storage.station.createaddition_rolling_mill"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(ROLLING_MILL_ITEM)),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        recipeFamilies.register(rollingId.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        RollingRecipe.class,
                        CARecipes.ROLLING_TYPE::get,
                        rollingId,
                        CreateadditionCompat::supportsRolling,
                        CreateadditionCompat::rollingPlan,
                        recipe -> RecipeFamilyCost.stationWork(rollingDuration()),
                        RecipePresentationKind.CRAFTING));

        machineDescriptors.register(chargingId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        chargingId,
                        Component.translatable(
                                "gui.auto_storage.station.createaddition_tesla_coil"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(TESLA_COIL_ITEM)),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        recipeFamilies.register(chargingId.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        ChargingRecipe.class,
                        CARecipes.CHARGING_TYPE::get,
                        chargingId,
                        CreateadditionCompat::supportsCharging,
                        CreateadditionCompat::chargingPlan,
                        recipe -> RecipeFamilyCost.stationWork(chargingWork(recipe)),
                        RecipePresentationKind.CRAFTING));
    }

    private static boolean supportsRolling(RollingRecipe recipe) {
        return rollingDuration() > 0
                && exact(recipe.getIngredient())
                && !recipe.getResultStack().isEmpty();
    }

    private static TypedRecipePlan rollingPlan(
            RollingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = recipe.getResultStack().copy();
        return TypedRecipePlan.builder()
                .input(consumedWithRemainder(recipe.getIngredient(), registries))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output.copyWithCount(1), registries),
                        output.getCount()))
                .presentationOutput(output)
                .layout(1, 1, true)
                .build();
    }

    private static boolean supportsCharging(ChargingRecipe recipe) {
        return recipe.getEnergy() > 0
                && recipe.getMaxChargeRate() > 0
                && chargeRate(recipe) > 0
                && recipe.getIngredients().size() == 1
                && exact(recipe.getIngredients().getFirst())
                && !recipe.getResultStack().isEmpty();
    }

    private static TypedRecipePlan chargingPlan(
            ChargingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = recipe.getResultStack().copy();
        int inputCount = 2;
        int width = Math.min(3, inputCount);
        return TypedRecipePlan.builder()
                .input(consumedWithRemainder(recipe.getIngredients().getFirst(), registries))
                .input(TypedRecipeInput.consume(
                        StorageResourceKey.neoforgeEnergy(), recipe.getEnergy()))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output.copyWithCount(1), registries),
                        output.getCount()))
                .presentationOutput(output)
                .layout(width, (inputCount + width - 1) / width, true)
                .build();
    }

    private static TypedRecipeInput consumedWithRemainder(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        List<ItemStack> representatives = representatives(ingredient);
        List<StorageResourceKey> alternatives = itemKeys(representatives, registries);
        Map<StorageResourceKey, TypedRecipeOutput> remainders = new LinkedHashMap<>();
        for (int index = 0; index < representatives.size(); index++) {
            ItemStack stack = representatives.get(index);
            if (!stack.hasCraftingRemainingItem()) {
                continue;
            }
            ItemStack remainder = stack.getCraftingRemainingItem();
            if (remainder.isEmpty()) {
                continue;
            }
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

    private static long rollingDuration() {
        return CommonConfig.ROLLING_MILL_PROCESSING_DURATION.get();
    }

    private static long chargingWork(ChargingRecipe recipe) {
        long rate = chargeRate(recipe);
        long energy = recipe.getEnergy();
        return Math.addExact(energy, rate - 1L) / rate;
    }

    private static long chargeRate(ChargingRecipe recipe) {
        return Math.min(
                CommonConfig.TESLA_COIL_RECIPE_CHARGE_RATE.get(),
                recipe.getMaxChargeRate());
    }

    private static boolean exact(Ingredient ingredient) {
        return ingredient != null
                && !ingredient.isEmpty()
                && ingredient.isSimple()
                && !representatives(ingredient).isEmpty();
    }

    private static List<ItemStack> representatives(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .distinct()
                .toList();
    }

    private static List<StorageResourceKey> itemKeys(
            List<ItemStack> representatives,
            HolderLookup.Provider registries
    ) {
        return representatives.stream()
                .map(stack -> StorageResourceKey.item(stack, registries))
                .distinct()
                .toList();
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException(
                    "Missing Create Crafts & Additions station item " + id);
        }
        return item;
    }
}
