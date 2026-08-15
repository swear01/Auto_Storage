package com.swear.autostorage.compat.draconicevolution;

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineVariant;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.api.AutoStorageApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Objects;

public final class DraconicevolutionCompat {
    private static final ResourceLocation GENERATOR_ITEM =
            ResourceLocation.fromNamespaceAndPath("draconicevolution", "generator");

    private DraconicevolutionCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes,
            DeferredRegister<TransformProvider> transforms
    ) {
        com.swear.autostorage.ConversionScanner.register(GENERATOR_PATTERN);
        Objects.requireNonNull(machines, "machines");
        Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(transforms, "transforms");
        if (!machines.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Draconic Evolution descriptor register targets the wrong registry");
        }
        if (!recipes.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Draconic Evolution family register targets the wrong registry");
        }
        if (!transforms.getRegistryKey().equals(TransformProviderApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Draconic Evolution transform provider register targets the wrong registry");
        }
        if (!machines.getNamespace().equals(recipes.getNamespace())
                || !machines.getNamespace().equals(transforms.getNamespace())) {
            throw new IllegalArgumentException(
                    "Draconic Evolution descriptors, families, and transforms must share one namespace");
        }

        ResourceLocation generatorId = ResourceLocation.fromNamespaceAndPath(
                machines.getNamespace(), "draconicevolution_generator");
        machines.register(generatorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        generatorId,
                        Component.translatable(
                                "gui.auto_storage.station.draconicevolution_generator"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(GENERATOR_ITEM)),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        transforms.register(generatorId.getPath(), () ->
                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.station.draconicevolution_generator"),
                        GENERATOR_PATTERN::resolve));
    }
    private static final GeneratorPattern GENERATOR_PATTERN = new GeneratorPattern();
    private static final class GeneratorPattern
            implements com.swear.autostorage.ConversionPattern {
        @Override
        public ResourceLocation patternId() {
            return ResourceLocation.fromNamespaceAndPath(
                    "draconicevolution", "generator");
        }

        @Override
        public TransformProviderApi.Result resolve(ItemStack input) {        int burnTime = input.getBurnTime(RecipeType.SMELTING);
        if (burnTime <= 0) return null;
        // Base NORMAL mode: 40 FE/tick while fuel burns. In-world the
        // production scales with the stored buffer (1..40 FE/tick); the
        // transform models the base rate with exact burn-time work.
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                Math.multiplyExact((long) burnTime, 40L),
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorageApi.MOD_ID, "draconicevolution_generator"),
                burnTime);
        }

        @Override
        public String revisionKey() {
            return "40";
        }
    }
    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Draconic Evolution item " + id);
        }
        return item;
    }
}
