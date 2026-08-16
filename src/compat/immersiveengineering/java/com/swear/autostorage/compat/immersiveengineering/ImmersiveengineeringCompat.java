package com.swear.autostorage.compat.immersiveengineering;

import blusunrize.immersiveengineering.api.crafting.IERecipeTypes;
import blusunrize.immersiveengineering.api.crafting.SawmillRecipe;
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
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.TypedRecipeInput;
import com.swear.autostorage.TypedRecipeOutput;
import com.swear.autostorage.TypedRecipePlan;
import com.swear.autostorage.api.AutoStorageApi;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Objects;

public final class ImmersiveengineeringCompat {
    private static final ResourceLocation SAWMILL_ITEM =
            ResourceLocation.fromNamespaceAndPath("immersiveengineering", "sawmill");
    private static final ResourceLocation SAWMILL_TYPE =
            ResourceLocation.fromNamespaceAndPath("immersiveengineering", "sawmill");

    private ImmersiveengineeringCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Immersive Engineering descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Immersive Engineering family register targets the wrong registry");
        }
        ResourceLocation sawmillId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "immersiveengineering_sawmill");
        machineDescriptors.register(sawmillId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        sawmillId,
                        Component.translatable(
                                "gui.auto_storage.station.immersiveengineering_sawmill"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(SAWMILL_ITEM)),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        recipeFamilies.register(sawmillId.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        SawmillRecipe.class,
                        ImmersiveengineeringCompat::sawmillType,
                        sawmillId,
                        ImmersiveengineeringCompat::supportsSawmill,
                        ImmersiveengineeringCompat::sawmillPlan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.getBaseTime()),
                        RecipePresentationKind.CRAFTING));
    }

    private static boolean supportsSawmill(SawmillRecipe recipe) {
        boolean ok = recipe != null
                && recipe.input != null
                && !recipe.input.isEmpty()
                && recipe.output != null
                && !recipe.output.get().isEmpty()
                && recipe.getBaseTime() > 0;
        org.slf4j.LoggerFactory.getLogger("IECompat")
                .info("sawmill supports called: ok={}", ok);
        return ok;
    }

    private static TypedRecipePlan sawmillPlan(
            SawmillRecipe recipe,
            HolderLookup.Provider registries
    ) {
        try {
            return sawmillPlanInternal(recipe, registries);
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger("IECompat")
                    .error("sawmill plan failed", failure);
            throw failure;
        }
    }

    private static TypedRecipePlan sawmillPlanInternal(
            SawmillRecipe recipe,
            HolderLookup.Provider registries
    ) {
        Ingredient ingredient = recipe.input;
        ItemStack output = recipe.output.get().copy();
        if (output.isEmpty()) {
            throw new IllegalStateException("Immersive Engineering sawmill result is empty");
        }
        int inputCount = 1 + (recipe.getBaseEnergy() > 0 ? 1 : 0);
        int width = Math.min(3, inputCount);
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder()
                .input(TypedRecipeInput.consumeAny(keys(ingredient, registries), 1))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output.copyWithCount(1), registries),
                        output.getCount()))
                .presentationOutput(output);
        if (recipe.getBaseEnergy() > 0) {
            builder.input(TypedRecipeInput.consume(
                    StorageResourceKey.neoforgeEnergy(),
                    recipe.getBaseEnergy()));
        }
        return builder
                .layout(width, (inputCount + width - 1) / width, true)
                .build();
    }

    private static List<StorageResourceKey> keys(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        return java.util.Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> StorageResourceKey.item(
                        stack.copyWithCount(1), registries))
                .distinct()
                .toList();
    }

    private static RecipeType<SawmillRecipe> sawmillType() {
        // IE registers recipes with its own IERecipeTypes instances (not the
        // vanilla RecipeType registry), so the family must bind the exact
        // static type instance instead of a registry lookup.
        return IERecipeTypes.SAWMILL.get();
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Immersive Engineering item " + id);
        }
        return item;
    }
}
