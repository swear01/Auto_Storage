package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class MachineVariantContributors {
    private MachineVariantContributors() {
    }

    static void validateDescriptorTargets(
            List<Map.Entry<ResourceLocation, MachineVariantContributor>> contributors,
            Predicate<ResourceLocation> descriptorExists
    ) {
        for (Map.Entry<ResourceLocation, MachineVariantContributor> entry : contributors) {
            ResourceLocation descriptorId = entry.getValue().descriptorId();
            if (!descriptorExists.test(descriptorId)) {
                throw new IllegalStateException(
                        "Machine variant contributor " + entry.getKey()
                                + " targets missing descriptor " + descriptorId);
            }
        }
    }

    static boolean has(ResourceLocation descriptorId) {
        return AutoStorage.MACHINE_VARIANT_CONTRIBUTOR_REGISTRY.entrySet().stream()
                .anyMatch(entry -> entry.getValue().descriptorId().equals(descriptorId));
    }

    static List<MachineVariant> combine(
            ResourceLocation descriptorId,
            List<MachineVariant> builtIns
    ) {
        List<MachineVariant> combined = new ArrayList<>(List.copyOf(builtIns));
        AutoStorage.MACHINE_VARIANT_CONTRIBUTOR_REGISTRY.entrySet().stream()
                .filter(entry -> entry.getValue().descriptorId().equals(descriptorId))
                .sorted(java.util.Comparator.comparing(
                        entry -> entry.getKey().location().toString()))
                .forEach(entry -> combined.addAll(entry.getValue().variants()));
        return List.copyOf(combined);
    }
}
