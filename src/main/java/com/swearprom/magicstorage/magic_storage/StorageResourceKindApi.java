package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StorageResourceKindApi {
    public static final int MAX_KINDS = 256;
    public static final ResourceLocation ITEM_KIND = id("item");
    public static final ResourceLocation FLUID_KIND = id("fluid");
    public static final ResourceLocation ENERGY_KIND = id("neoforge_energy");
    public static final ResourceLocation WORK_KIND = id("work");
    public static final ResourceLocation CHEMICAL_KIND =
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical");
    public static final ResourceLocation BOTANIA_MANA_KIND =
            ResourceLocation.fromNamespaceAndPath("botania", "mana");
    public static final ResourceLocation ARS_NOUVEAU_SOURCE_KIND =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source");
    public static final ResourceKey<Registry<StorageResourceKind>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(id("resource_kind"));

    private StorageResourceKindApi() {
    }

    public static DeferredRegister<StorageResourceKind> createDeferredRegister(String modId) {
        return DeferredRegister.create(REGISTRY_KEY, modId);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, path);
    }
}
