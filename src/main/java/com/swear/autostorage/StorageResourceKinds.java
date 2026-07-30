package com.swear.autostorage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

final class StorageResourceKinds {
    private static final ResourceLocation CHEMICAL_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "chemical");
    private static final ResourceLocation BOTANIA_MANA_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "mana");
    private static final ResourceLocation ARS_NOUVEAU_SOURCE_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "source");

    private StorageResourceKinds() {
    }

    static void registerBuiltIns(DeferredRegister<StorageResourceKind> kinds) {
        kinds.register(StorageResourceKindApi.ITEM_KIND.getPath(), () ->
                StorageResourceKind.variantAware(() -> new ItemStack(Items.CHEST)));
        kinds.register(StorageResourceKindApi.FLUID_KIND.getPath(), () ->
                StorageResourceKind.variantAware(() -> new ItemStack(Items.BUCKET)));
        kinds.register(StorageResourceKindApi.ENERGY_KIND.getPath(), () ->
                StorageResourceKind.variantless(() -> named(
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource.neoforge_energy"))));
        kinds.register(StorageResourceKindApi.WORK_KIND.getPath(), () ->
                StorageResourceKind.variantAware(() -> new ItemStack(Items.CLOCK)));
    }

    static boolean isKindAvailable(ResourceLocation kindId) {
        return AutoStorage.RESOURCE_KIND_REGISTRY.get(kindId) != null;
    }

    static boolean isChemicalKindAvailable() {
        return isKindAvailable(StorageResourceKindApi.CHEMICAL_KIND)
                || isKindAvailable(CHEMICAL_REGISTRY_ID);
    }

    static boolean isChemicalKindId(ResourceLocation kindId) {
        return kindId.equals(StorageResourceKindApi.CHEMICAL_KIND)
                || kindId.equals(CHEMICAL_REGISTRY_ID);
    }

    static boolean hasOtherKind() {
        return isKindAvailable(StorageResourceKindApi.WORK_KIND)
                || AutoStorage.RESOURCE_KIND_REGISTRY.keySet().stream()
                .anyMatch(kindId -> !isBuiltInKindId(kindId));
    }

    static boolean isEnergyKindId(ResourceLocation kindId) {
        return kindId.equals(StorageResourceKindApi.ENERGY_KIND)
                || kindId.equals(StorageResourceKindApi.BOTANIA_MANA_KIND)
                || kindId.equals(BOTANIA_MANA_REGISTRY_ID)
                || kindId.equals(StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND)
                || kindId.equals(ARS_NOUVEAU_SOURCE_REGISTRY_ID);
    }

    static boolean isBuiltInKindId(ResourceLocation kindId) {
        return kindId.equals(StorageResourceKindApi.ITEM_KIND)
                || kindId.equals(StorageResourceKindApi.FLUID_KIND)
                || isEnergyKindId(kindId)
                || kindId.equals(StorageResourceKindApi.WORK_KIND)
                || isChemicalKindId(kindId);
    }

    static boolean accepts(StorageResourceKey key) {
        StorageResourceKind kind = AutoStorage.RESOURCE_KIND_REGISTRY.get(key.kindId());
        return kind != null && kind.accepts(key);
    }

    static boolean isRegistered(StorageResourceKey key) {
        return AutoStorage.RESOURCE_KIND_REGISTRY.get(key.kindId()) != null;
    }

    static ItemStack kindRepresentative(ResourceLocation kindId) {
        StorageResourceKind kind = AutoStorage.RESOURCE_KIND_REGISTRY.get(kindId);
        if (kind == null) throw new IllegalArgumentException("Unknown storage resource kind " + kindId);
        return kind.representative();
    }

    static ItemStack representative(StorageResourceKey key, net.minecraft.core.HolderLookup.Provider registries) {
        if (key.kindId().equals(StorageResourceKindApi.ITEM_KIND)) {
            var item = StorageResourceBridge.itemKey(key, registries);
            if (item.isPresent()) return item.get().toStack(1);
        }
        if (key.kindId().equals(StorageResourceKindApi.FLUID_KIND)) {
            var fluid = StorageResourceBridge.fluidStack(key, 1, registries);
            if (fluid.isPresent()) {
                ItemStack bucket = new ItemStack(fluid.get().getFluid().getBucket());
                if (!bucket.isEmpty()) return named(bucket, fluid.get().getHoverName());
            }
        }
        if (key.kindId().equals(StorageResourceKindApi.WORK_KIND)) {
            EnergyType energyType = StorageResourceBridge.energyType(key).orElse(null);
            if (energyType != null) {
                return named(
                        energyType.representativeStack(),
                        Component.translatable("gui.auto_storage.energy." + energyType.getId()));
            }
            ResourceLocation descriptorId = StorageResourceBridge.descriptorId(key)
                    .or(() -> StorageResourceBridge.stationWorkDescriptorId(key))
                    .orElse(null);
            MachineDescriptor descriptor = descriptorId == null
                    ? null : MachineEnergyTable.get(descriptorId);
            if (descriptor != null) {
                Component label = StorageResourceBridge.stationWorkDescriptorId(key).isPresent()
                        ? Component.translatable(
                                "gui.auto_storage.resource.station_work",
                                descriptor.stationLabel())
                        : descriptorId.equals(MachineEnergyTable.AXE_ID)
                        ? Component.translatable("gui.auto_storage.axe_energy")
                        : descriptor.representativeStack().getHoverName();
                return named(descriptor.representativeStack(), label);
            }
        }
        ItemStack representative = kindRepresentative(key.kindId());
        if (isChemicalKindId(key.kindId())) {
            representative.set(
                    DataComponents.CUSTOM_NAME,
                    Component.translatable(key.resourceId().toLanguageKey("chemical")));
        }
        return representative;
    }

    private static ItemStack named(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }
}
