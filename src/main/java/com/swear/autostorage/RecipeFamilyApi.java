package com.swear.autostorage;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RecipeFamilyApi {
    public static final int MAX_FAMILIES = 256;
    public static final ResourceKey<Registry<RecipeFamily>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "recipe_family"));

    private RecipeFamilyApi() {
    }

    public static DeferredRegister<RecipeFamily> createDeferredRegister(String modId) {
        return DeferredRegister.create(REGISTRY_KEY, modId);
    }
}
