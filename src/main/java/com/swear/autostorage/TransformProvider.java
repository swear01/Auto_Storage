package com.swear.autostorage;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Registry-owned deterministic conversion from one exact item input to a typed
 * resource output.
 */
public final class TransformProvider {
    private final ResourceLocation targetId;
    private final ItemStack representative;
    private final Component targetLabel;
    private final Component sourceLabel;
    private final TransformProviderApi.Resolver resolver;

    private TransformProvider(
            ResourceLocation targetId,
            ItemStack representative,
            Component targetLabel,
            Component sourceLabel,
            TransformProviderApi.Resolver resolver
    ) {
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.representative = Objects.requireNonNull(representative, "representative")
                .copyWithCount(1);
        this.targetLabel = Objects.requireNonNull(targetLabel, "targetLabel");
        this.sourceLabel = Objects.requireNonNull(sourceLabel, "sourceLabel");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        if (this.representative.isEmpty()) {
            throw new IllegalArgumentException("Transform representative cannot be empty");
        }
    }

    /**
     * Creates a Transform provider.
     *
     * @param targetId stable produced-target group ID
     * @param representative item used to identify the target
     * @param targetLabel localized produced-resource label
     * @param sourceLabel localized conversion-family label
     * @param resolver side-effect-free exact-input resolver
     * @return validated provider
     */
    public static TransformProvider of(
            ResourceLocation targetId,
            ItemStack representative,
            Component targetLabel,
            Component sourceLabel,
            TransformProviderApi.Resolver resolver
    ) {
        return new TransformProvider(
                targetId, representative, targetLabel, sourceLabel, resolver);
    }

    /**
     * @return stable produced-target group ID
     */
    public ResourceLocation targetId() {
        return targetId;
    }

    /**
     * @return a defensive one-count representative copy
     */
    public ItemStack representative() {
        return representative.copy();
    }

    /**
     * @return localized produced-resource label
     */
    public Component targetLabel() {
        return targetLabel;
    }

    /**
     * @return localized conversion-family label
     */
    public Component sourceLabel() {
        return sourceLabel;
    }

    /**
     * @return side-effect-free exact-input resolver
     */
    public TransformProviderApi.Resolver resolver() {
        return resolver;
    }
}
