package com.swearprom.magicstorage.magic_storage;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

final class MachineVariantContributors {
    private static final Map<ResourceLocation, Map<String, Supplier<List<MachineVariant>>>>
            CONTRIBUTORS = new LinkedHashMap<>();

    private MachineVariantContributors() {
    }

    static synchronized void register(
            ResourceLocation descriptorId,
            String module,
            Supplier<List<MachineVariant>> variants
    ) {
        Objects.requireNonNull(descriptorId, "descriptorId");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(variants, "variants");
        Supplier<List<MachineVariant>> previous = CONTRIBUTORS
                .computeIfAbsent(descriptorId, ignored -> new LinkedHashMap<>())
                .putIfAbsent(module, variants);
        if (previous != null) {
            throw new IllegalStateException(
                    "Machine variant contributor registered twice: " + module);
        }
    }

    static synchronized List<MachineVariant> combine(
            ResourceLocation descriptorId,
            List<MachineVariant> builtIns
    ) {
        List<MachineVariant> combined = new ArrayList<>(List.copyOf(builtIns));
        for (Supplier<List<MachineVariant>> supplier : CONTRIBUTORS
                .getOrDefault(descriptorId, Map.of()).values()) {
            combined.addAll(List.copyOf(Objects.requireNonNull(
                    supplier.get(), "machine variant contributor result")));
        }
        return List.copyOf(combined);
    }
}
