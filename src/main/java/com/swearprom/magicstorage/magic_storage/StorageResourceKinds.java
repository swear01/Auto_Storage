package com.swearprom.magicstorage.magic_storage;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

final class StorageResourceKinds {
    private static final ResourceLocation CHEMICAL_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "chemical");
    private static final ResourceLocation BOTANIA_MANA_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "mana");
    private static final ResourceLocation BOTANIA_MANA_TABLET_ID =
            ResourceLocation.fromNamespaceAndPath("botania", "mana_tablet");
    private static final ResourceLocation ARS_NOUVEAU_SOURCE_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "source");
    private static final ResourceLocation ARS_NOUVEAU_SOURCE_JAR_ID =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_jar");

    private StorageResourceKinds() {
    }

    static void registerBuiltIns(DeferredRegister<StorageResourceKind> kinds) {
        kinds.register(StorageResourceKindApi.ITEM_KIND.getPath(), () ->
                StorageResourceKind.variantAware(() -> new ItemStack(Items.CHEST)));
        kinds.register(StorageResourceKindApi.FLUID_KIND.getPath(), () ->
                StorageResourceKind.variantAware(() -> new ItemStack(Items.BUCKET)));
        kinds.register(StorageResourceKindApi.ENERGY_KIND.getPath(), () ->
                StorageResourceKind.variantless(() -> new ItemStack(Items.REDSTONE)));
        kinds.register(StorageResourceKindApi.WORK_KIND.getPath(), () ->
                StorageResourceKind.variantAware(() -> new ItemStack(Items.CLOCK)));
    }

    static void registerChemical(DeferredRegister<StorageResourceKind> kinds) {
        kinds.register(StorageResourceKindApi.CHEMICAL_KIND.getPath(), () ->
                StorageResourceKind.variantless(() -> new ItemStack(Items.BREWING_STAND)));
        kinds.addAlias(StorageResourceKindApi.CHEMICAL_KIND, CHEMICAL_REGISTRY_ID);
    }

    static void registerBotaniaMana(DeferredRegister<StorageResourceKind> kinds) {
        kinds.register(StorageResourceKindApi.BOTANIA_MANA_KIND.getPath(), () ->
                StorageResourceKind.variantless(() ->
                        new ItemStack(BuiltInRegistries.ITEM.get(BOTANIA_MANA_TABLET_ID))));
        kinds.addAlias(StorageResourceKindApi.BOTANIA_MANA_KIND, BOTANIA_MANA_REGISTRY_ID);
    }

    static void registerArsNouveauSource(DeferredRegister<StorageResourceKind> kinds) {
        kinds.register(StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND.getPath(), () ->
                StorageResourceKind.variantless(() ->
                        new ItemStack(BuiltInRegistries.ITEM.get(ARS_NOUVEAU_SOURCE_JAR_ID))));
        kinds.addAlias(
                StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND,
                ARS_NOUVEAU_SOURCE_REGISTRY_ID);
    }

    static boolean isKindAvailable(ResourceLocation kindId) {
        return MagicStorage.RESOURCE_KIND_REGISTRY.get(kindId) != null;
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
        return MagicStorage.RESOURCE_KIND_REGISTRY.keySet().stream()
                .anyMatch(kindId -> !isBuiltInKindId(kindId));
    }

    static boolean isBuiltInKindId(ResourceLocation kindId) {
        return kindId.equals(StorageResourceKindApi.ITEM_KIND)
                || kindId.equals(StorageResourceKindApi.FLUID_KIND)
                || kindId.equals(StorageResourceKindApi.ENERGY_KIND)
                || kindId.equals(StorageResourceKindApi.WORK_KIND)
                || isChemicalKindId(kindId);
    }

    static boolean accepts(StorageResourceKey key) {
        StorageResourceKind kind = MagicStorage.RESOURCE_KIND_REGISTRY.get(key.kindId());
        return kind != null && kind.accepts(key);
    }

    static boolean isRegistered(StorageResourceKey key) {
        return MagicStorage.RESOURCE_KIND_REGISTRY.get(key.kindId()) != null;
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
                if (!bucket.isEmpty()) return bucket;
            }
        }
        if (key.kindId().equals(StorageResourceKindApi.WORK_KIND)) {
            EnergyType energyType = StorageResourceBridge.energyType(key).orElse(null);
            if (energyType != null) return energyType.representativeStack();
            ResourceLocation descriptorId = StorageResourceBridge.descriptorId(key)
                    .or(() -> StorageResourceBridge.stationWorkDescriptorId(key))
                    .orElse(null);
            MachineDescriptor descriptor = descriptorId == null
                    ? null : MachineEnergyTable.get(descriptorId);
            if (descriptor != null) return descriptor.representativeStack();
        }
        StorageResourceKind kind = MagicStorage.RESOURCE_KIND_REGISTRY.get(key.kindId());
        if (kind == null) throw new IllegalArgumentException("Unknown storage resource kind " + key.kindId());
        return kind.representative();
    }
}
