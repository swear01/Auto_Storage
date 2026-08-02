package com.swear.autostorage.compat.xycraftmachines;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Objects;

public final class XycraftMachinesCompat {
    private XycraftMachinesCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes
    ) {
        Objects.requireNonNull(machines, "machines");
        Objects.requireNonNull(recipes, "recipes");
        if (!machines.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "XyCraft Machines descriptor register targets the wrong registry");
        }
        if (!recipes.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "XyCraft Machines family register targets the wrong registry");
        }
        if (!machines.getNamespace().equals(recipes.getNamespace())) {
            throw new IllegalArgumentException(
                    "XyCraft Machines descriptors and families must share one namespace");
        }
    }
}
