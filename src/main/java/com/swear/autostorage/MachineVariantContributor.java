package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Registry-owned contribution of exact variants to an existing logical station
 * descriptor.
 */
public final class MachineVariantContributor {
    private final ResourceLocation descriptorId;
    private final Supplier<List<MachineVariant>> variants;

    private MachineVariantContributor(
            ResourceLocation descriptorId,
            Supplier<List<MachineVariant>> variants
    ) {
        this.descriptorId = Objects.requireNonNull(descriptorId, "descriptorId");
        this.variants = Objects.requireNonNull(variants, "variants");
    }

    /**
     * Creates a variant contribution.
     *
     * @param descriptorId existing logical station descriptor ID
     * @param variants deferred exact variant supplier
     * @return validated contribution
     */
    public static MachineVariantContributor of(
            ResourceLocation descriptorId,
            Supplier<List<MachineVariant>> variants
    ) {
        return new MachineVariantContributor(descriptorId, variants);
    }

    /**
     * @return target logical descriptor ID
     */
    public ResourceLocation descriptorId() {
        return descriptorId;
    }

    /**
     * Resolves and defensively copies the contributed variants.
     *
     * @return contributed exact variants
     */
    public List<MachineVariant> variants() {
        return List.copyOf(Objects.requireNonNull(
                variants.get(), "machine variant contributor result"));
    }
}
