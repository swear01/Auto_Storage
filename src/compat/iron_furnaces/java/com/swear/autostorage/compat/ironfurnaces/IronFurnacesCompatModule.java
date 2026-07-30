package com.swear.autostorage.compat.ironfurnaces;

import com.swear.autostorage.MachineVariantContributor;
import com.swear.autostorage.MachineVariantContributorApi;
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class IronFurnacesCompatModule implements AutoStorageCompatModule {
    private static final DeferredRegister<MachineVariantContributor> VARIANTS =
            MachineVariantContributorApi.createDeferredRegister(AutoStorageApi.MOD_ID);

    static {
        VARIANTS.register("iron_furnaces", () -> MachineVariantContributor.of(
                AutoStorageApi.id("furnace"),
                IronFurnacesCompat::furnaceVariants));
    }

    @Override
    public void register(AutoStorageCompatContext context) {
        context.register(addon -> addon.machineVariantContributors(VARIANTS));
    }
}
