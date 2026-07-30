package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

record RecipeAdapterSnapshot(
        RecipeAdapterRegistry registry,
        List<RecipeType<?>> discoveryTypes
) {
    private static final int EXTERNAL_PRIORITY = 1000;

    RecipeAdapterSnapshot {
        Objects.requireNonNull(registry, "registry");
        discoveryTypes = List.copyOf(discoveryTypes);
    }

    static RecipeAdapterSnapshot create(
            List<? extends RecipeAdapter> builtInAdapters,
            List<? extends RecipeType<?>> builtInDiscoveryTypes,
            Map<ResourceLocation, RecipeFamily> registeredFamilies,
            Predicate<ResourceLocation> stationExists
    ) {
        Objects.requireNonNull(builtInAdapters, "builtInAdapters");
        Objects.requireNonNull(builtInDiscoveryTypes, "builtInDiscoveryTypes");
        Objects.requireNonNull(registeredFamilies, "registeredFamilies");
        Objects.requireNonNull(stationExists, "stationExists");

        List<RecipeAdapter> adapters = new ArrayList<>(builtInAdapters);
        Set<RecipeFamilyKey> familyKeys = new HashSet<>();
        for (RecipeAdapter adapter : builtInAdapters) {
            adapter.exactFamilyKey().ifPresent(key -> {
                if (!familyKeys.add(key)) {
                    throw new IllegalArgumentException("Duplicate built-in recipe family: " + key);
                }
            });
        }

        LinkedHashSet<RecipeType<?>> discoveryTypes = new LinkedHashSet<>(builtInDiscoveryTypes);
        registeredFamilies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    ResourceLocation id = Objects.requireNonNull(entry.getKey(), "recipe family ID");
                    RecipeFamily family = Objects.requireNonNull(entry.getValue(), "recipe family");
                    if (!stationExists.test(family.stationDescriptorId())) {
                        throw new IllegalArgumentException(
                                "Recipe family " + id + " references missing station descriptor "
                                        + family.stationDescriptorId());
                    }
                    RecipeFamilyKey key = family.key();
                    if (!familyKeys.add(key)) {
                        throw new IllegalArgumentException(
                                "Duplicate exact recipe class and type for family " + id);
                    }
                    adapters.add(family.adapter(id, EXTERNAL_PRIORITY));
                    discoveryTypes.add(key.recipeType());
                });

        return new RecipeAdapterSnapshot(
                new RecipeAdapterRegistry(adapters), List.copyOf(discoveryTypes));
    }
}
