package com.swear.autostorage;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Optional;

final class StorageResourceBridge {
    static final ResourceLocation ITEM_KIND = StorageResourceKindApi.ITEM_KIND;
    static final ResourceLocation FLUID_KIND = StorageResourceKindApi.FLUID_KIND;
    static final ResourceLocation ENERGY_KIND = StorageResourceKindApi.ENERGY_KIND;
    static final ResourceLocation WORK_KIND = StorageResourceKindApi.WORK_KIND;
    private static final ResourceLocation DESCRIPTOR_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "descriptor");
    private static final ResourceLocation STATION_WORK_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "station_work");
    private static final String DESCRIPTOR_ID = "descriptorId";
    static final StorageResourceKey ENERGY_KEY = StorageResourceKey.of(
            ENERGY_KIND,
            ResourceLocation.fromNamespaceAndPath("neoforge", "energy"),
            new CompoundTag());

    private StorageResourceBridge() {
    }

    static StorageResourceKey energyKey(EnergyType type) {
        return StorageResourceKey.of(
                WORK_KIND,
                ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, type.getId()),
                new CompoundTag());
    }

    static Optional<EnergyType> energyType(StorageResourceKey key) {
        if (!key.kindId().equals(WORK_KIND) || !key.variantData().isEmpty()) {
            return Optional.empty();
        }
        for (EnergyType type : EnergyType.values()) {
            if (key.equals(energyKey(type))) return Optional.of(type);
        }
        return Optional.empty();
    }

    static StorageResourceKey descriptorKey(ResourceLocation descriptorId) {
        return workKey(DESCRIPTOR_RESOURCE, descriptorId);
    }

    static Optional<ResourceLocation> descriptorId(StorageResourceKey key) {
        return workDescriptorId(key, DESCRIPTOR_RESOURCE);
    }

    static StorageResourceKey stationWorkKey(ResourceLocation descriptorId) {
        return workKey(STATION_WORK_RESOURCE, descriptorId);
    }

    static Optional<ResourceLocation> stationWorkDescriptorId(StorageResourceKey key) {
        return workDescriptorId(key, STATION_WORK_RESOURCE);
    }

    private static StorageResourceKey workKey(
            ResourceLocation resourceId,
            ResourceLocation descriptorId
    ) {
        CompoundTag variant = new CompoundTag();
        variant.putString(DESCRIPTOR_ID, descriptorId.toString());
        return StorageResourceKey.of(WORK_KIND, resourceId, variant);
    }

    private static Optional<ResourceLocation> workDescriptorId(
            StorageResourceKey key,
            ResourceLocation resourceId
    ) {
        if (!key.kindId().equals(WORK_KIND) || !key.resourceId().equals(resourceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(
                key.variantData().getString(DESCRIPTOR_ID)));
    }

    static StorageResourceKey itemKey(
            ItemKey key,
            HolderLookup.Provider registries
    ) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(key.item());
        if (itemId == null) throw new IllegalArgumentException("Item is not registered");
        ItemStack stack = key.toStack(1);
        return StorageResourceKey.of(
                ITEM_KIND,
                itemId,
                encodeComponentPatch(stack.getComponentsPatch(), registries));
    }

    static Optional<ItemKey> itemKey(
            StorageResourceKey key,
            HolderLookup.Provider registries
    ) {
        if (!key.kindId().equals(ITEM_KIND)) return Optional.empty();
        var item = BuiltInRegistries.ITEM.getOptional(key.resourceId()).orElse(null);
        if (item == null) return Optional.empty();
        Optional<DataComponentPatch> componentPatch = decodeComponentPatch(key, registries);
        if (componentPatch.isEmpty()) return Optional.empty();
        ItemStack stack = new ItemStack(item);
        stack.applyComponents(componentPatch.get());
        return Optional.of(ItemKey.of(stack));
    }

    static StorageResourceKey fluidKey(
            FluidStack stack,
            HolderLookup.Provider registries
    ) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Cannot key an empty fluid stack");
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        if (fluidId == null) throw new IllegalArgumentException("Fluid is not registered");
        return StorageResourceKey.of(
                FLUID_KIND,
                fluidId,
                encodeComponents(stack.getComponents(), registries));
    }

    static Optional<FluidStack> fluidStack(
            StorageResourceKey key,
            int amount,
            HolderLookup.Provider registries
    ) {
        if (!key.kindId().equals(FLUID_KIND) || amount <= 0) return Optional.empty();
        var fluid = BuiltInRegistries.FLUID.getOptional(key.resourceId()).orElse(null);
        if (fluid == null) return Optional.empty();
        Optional<DataComponentMap> components = decodeComponents(key, registries);
        if (components.isEmpty()) return Optional.empty();
        FluidStack stack = new FluidStack(fluid, amount);
        stack.applyComponents(components.get());
        return Optional.of(stack);
    }

    private static Optional<DataComponentMap> decodeComponents(
            StorageResourceKey key,
            HolderLookup.Provider registries
    ) {
        return DataComponentMap.CODEC.parse(
                RegistryOps.create(NbtOps.INSTANCE, registries), key.variantData()).result();
    }

    private static Optional<DataComponentPatch> decodeComponentPatch(
            StorageResourceKey key,
            HolderLookup.Provider registries
    ) {
        return DataComponentPatch.CODEC.parse(
                RegistryOps.create(NbtOps.INSTANCE, registries), key.variantData()).result();
    }

    private static CompoundTag encodeComponentPatch(
            DataComponentPatch components,
            HolderLookup.Provider registries
    ) {
        Tag encoded = DataComponentPatch.CODEC.encodeStart(
                RegistryOps.create(NbtOps.INSTANCE, registries), components).getOrThrow();
        if (!(encoded instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Resource component patch did not encode as a compound");
        }
        return compound;
    }

    private static CompoundTag encodeComponents(
            DataComponentMap components,
            HolderLookup.Provider registries
    ) {
        Tag encoded = DataComponentMap.CODEC.encodeStart(
                RegistryOps.create(NbtOps.INSTANCE, registries), components).getOrThrow();
        if (!(encoded instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Resource components did not encode as a compound");
        }
        return compound;
    }
}
