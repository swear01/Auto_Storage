package com.swear.autostorage.compat.ae2;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Ae2CompatModule implements AutoStorageCompatModule {
    private static final DeferredRegister<MachineDescriptor> MACHINES =
            MachineDescriptorApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<RecipeFamily> RECIPES =
            RecipeFamilyApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<TransformProvider> TRANSFORMS =
            TransformProviderApi.createDeferredRegister(AutoStorageApi.MOD_ID);

    @Override
    public void register(AutoStorageCompatContext context) {
        Ae2Compat.register(MACHINES, RECIPES, TRANSFORMS);
        context.register(addon -> addon
                .machineDescriptors(MACHINES)
                .recipeFamilies(RECIPES)
                .transformProviders(TRANSFORMS));
    }
}
