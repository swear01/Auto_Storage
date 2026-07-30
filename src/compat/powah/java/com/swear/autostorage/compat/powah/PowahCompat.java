package com.swear.autostorage.compat.powah;

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
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TypedRecipeInput;
import com.swear.autostorage.TypedRecipeOutput;
import com.swear.autostorage.TypedRecipePlan;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;
import owmii.powah.Powah;
import owmii.powah.block.Tier;
import owmii.powah.block.energizing.EnergizingRecipe;
import owmii.powah.recipe.Recipes;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class PowahCompat {
    private static final String MOD_ID = "powah";

    private PowahCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            DeferredRegister<TransformProvider> transformProviders
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        Objects.requireNonNull(transformProviders, "transformProviders");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("Powah descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("Powah family register targets the wrong registry");
        }
        if (!transformProviders.getRegistryKey().equals(TransformProviderApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Powah transform register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Powah descriptors and families must share one namespace");
        }
        if (!machineDescriptors.getNamespace().equals(transformProviders.getNamespace())) {
            throw new IllegalArgumentException(
                    "Powah descriptors and transforms must share one namespace");
        }

        ResourceLocation descriptorId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "powah_energizing");
        ResourceLocation furnatorId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "powah_furnator");
        machineDescriptors.register(descriptorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        descriptorId,
                        Component.translatable("gui.auto_storage.station.powah_energizing"),
                        PowahCompat::rodVariants,
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        recipeFamilies.register(descriptorId.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        EnergizingRecipe.class,
                        Recipes.ENERGIZING::get,
                        descriptorId,
                        PowahCompat::supports,
                        PowahCompat::plan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.getScaledEnergy()),
                        RecipePresentationKind.CRAFTING));
        machineDescriptors.register(furnatorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        furnatorId,
                        Component.translatable("gui.auto_storage.station.powah_furnator"),
                        PowahCompat::furnatorVariants,
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        transformProviders.register(
                furnatorId.getPath(),
                () -> TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable("gui.auto_storage.station.powah_furnator"),
                        stack -> furnatorTransform(stack, furnatorId)));
    }

    private static List<MachineVariant> rodVariants() {
        return Arrays.stream(Tier.getNormalVariants())
                .map(tier -> MachineVariant.of(
                        new ItemStack(requiredItem("energizing_rod_" + tier.getName())),
                        MachineWorkRate.of(
                                Powah.config().devices.energizing_rods.getTransfer(tier),
                                1)))
                .toList();
    }

    private static List<MachineVariant> furnatorVariants() {
        return Arrays.stream(Tier.getNormalVariants())
                .map(tier -> MachineVariant.of(
                        new ItemStack(requiredItem("furnator_" + tier.getName())),
                        MachineWorkRate.of(
                                Powah.config().generators.furnators.getGeneration(tier),
                                1)))
                .toList();
    }

    private static TransformProviderApi.Result furnatorTransform(
            ItemStack stack,
            ResourceLocation furnatorId
    ) {
        int burnTicks = stack.getBurnTime(RecipeType.SMELTING);
        if (burnTicks <= 0) return null;
        long output = Math.multiplyExact(
                (long) burnTicks,
                Powah.config().general.energy_per_fuel_tick);
        if (output <= 0) return null;
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                output,
                furnatorId,
                output);
    }

    private static boolean supports(EnergizingRecipe recipe) {
        return recipe.getEnergy() > 0
                && recipe.getIngredients().size() <= 6
                && !recipe.getResultItem().isEmpty()
                && recipe.getIngredients().stream().allMatch(PowahCompat::exact);
    }

    private static TypedRecipePlan plan(
            EnergizingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        recipe.getIngredients().stream()
                .map(ingredient -> TypedRecipeInput.consumeAny(
                        representatives(ingredient).stream()
                                .map(stack -> StorageResourceKey.item(stack, registries))
                                .distinct()
                                .toList(),
                        1))
                .forEach(builder::input);
        builder.input(TypedRecipeInput.consume(
                StorageResourceKey.neoforgeEnergy(), recipe.getScaledEnergy()));

        ItemStack output = recipe.getResultItem().copy();
        int inputCount = recipe.getIngredients().size() + 1;
        int width = Math.min(3, inputCount);
        return builder
                .presentationOutput(output)
                .layout(width, (inputCount + width - 1) / width, true)
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output.copyWithCount(1), registries),
                        output.getCount()))
                .build();
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

    private static Item requiredItem(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalStateException("Missing Powah station item " + id);
        return item;
    }
}
