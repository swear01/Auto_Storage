package com.swear.autostorage.compat.ae2;

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
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class Ae2GeneratedCompat {
    private Ae2GeneratedCompat() {
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
            throw new IllegalArgumentException("Generated descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("Generated family register targets the wrong registry");
        }
        if (!transformProviders.getRegistryKey().equals(TransformProviderApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("Generated transform provider register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())
                || !machineDescriptors.getNamespace().equals(transformProviders.getNamespace())) {
            throw new IllegalArgumentException("Generated descriptors, families, and transform providers must share one namespace");
        }
        if (!machineDescriptors.getNamespace().equals("auto_storage")) {
            throw new IllegalArgumentException("Generated descriptor namespace must be auto_storage");
        }
        ResourceLocation inscriber_recipeDescriptor = id("auto_storage", "ae2_inscriber");
        machineDescriptors.register(inscriber_recipeDescriptor.getPath(), () ->
                MachineDescriptor.installableVariants(
                        inscriber_recipeDescriptor,
                        Component.translatable("gui.auto_storage.station.ae2_inscriber"),
                        () -> List.of(
                        MachineVariant.of(new ItemStack(requiredItem(id("ae2", "inscriber"))), MachineWorkRate.of(2L, 1L))),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        recipeFamilies.register("ae2_inscriber", () ->
                RecipeFamilyFactories.deterministicResources(
                        appeng.recipes.handlers.InscriberRecipe.class,
                        () -> BuiltInRegistries.RECIPE_TYPE.get(id("ae2", "inscriber")),
                        inscriber_recipeDescriptor,
                        recipe -> com.swear.autostorage.compat.ae2.Ae2Compat.supports(recipe),
                        (recipe, registries) -> com.swear.autostorage.compat.ae2.Ae2Compat.plan(recipe, registries),
                        recipe -> com.swear.autostorage.compat.ae2.Ae2Compat.cost(recipe),
                        RecipePresentationKind.CRAFTING));
    }

    private static long exactPositiveIntegral(Number value, String name) {
        Objects.requireNonNull(value, name);
        try {
            long exact = new BigDecimal(value.toString()).longValueExact();
            if (exact <= 0) throw new ArithmeticException();
            return exact;
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IllegalStateException(name + " must be an exact positive integer: " + value, error);
        }
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalStateException("Missing station item " + id);
        return item;
    }

    private static Block requiredBlock(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR) throw new IllegalStateException("Missing station block " + id);
        return block;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
