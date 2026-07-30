package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TransformProviderApi {
    public static final int TARGET_BUTTON_BASE = 1_000;
    static final int LEGACY_FUEL_BUTTON_BASE = 19;
    private static final List<Provider> PROVIDERS = new ArrayList<>();

    private TransformProviderApi() {
    }

    public static synchronized void register(
            ResourceLocation id,
            ResourceLocation targetId,
            ItemStack representative,
            Component targetLabel,
            Component sourceLabel,
            Resolver resolver
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(representative, "representative");
        Objects.requireNonNull(targetLabel, "targetLabel");
        Objects.requireNonNull(sourceLabel, "sourceLabel");
        Objects.requireNonNull(resolver, "resolver");
        if (representative.isEmpty()) {
            throw new IllegalArgumentException("Transform representative cannot be empty: " + id);
        }
        if (PROVIDERS.stream().anyMatch(provider -> provider.id().equals(id))) {
            throw new IllegalArgumentException("Duplicate transform provider: " + id);
        }
        PROVIDERS.add(new Provider(
                id, targetId, representative.copyWithCount(1),
                targetLabel, sourceLabel, resolver));
    }

    public static synchronized Optional<Component> sourceLabel(ResourceLocation providerId) {
        return PROVIDERS.stream()
                .filter(provider -> provider.id().equals(providerId))
                .map(Provider::sourceLabel)
                .findFirst();
    }

    public static ResourceLocation energyTargetId(EnergyType type) {
        Objects.requireNonNull(type, "type");
        if (type.isMachineGenerated()) {
            throw new IllegalArgumentException("Machine-generated work is not a transform target");
        }
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, type.getId());
    }

    public static Optional<EnergyType> energyType(ResourceLocation targetId) {
        if (targetId == null) return Optional.empty();
        for (EnergyType type : EnergyType.values()) {
            if (!type.isMachineGenerated() && energyTargetId(type).equals(targetId)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static int targetButtonId(
            ResourceLocation targetId,
            List<MachineDescriptor> descriptors
    ) {
        int index = targetIds(descriptors).indexOf(targetId);
        if (index < 0) throw new IllegalArgumentException("Unknown transform target: " + targetId);
        return index < 2 ? LEGACY_FUEL_BUTTON_BASE + index : TARGET_BUTTON_BASE + index;
    }

    static List<ResourceLocation> targetIds(List<MachineDescriptor> descriptors) {
        List<ResourceLocation> result = new ArrayList<>();
        for (EnergyType type : EnergyType.values()) {
            if (!type.isMachineGenerated()) result.add(energyTargetId(type));
        }
        descriptors.stream()
                .filter(descriptor -> descriptor.category() == MachineEnergyTable.Category.TRANSFORM)
                .map(MachineDescriptor::id)
                .forEach(result::add);
        synchronized (TransformProviderApi.class) {
            PROVIDERS.stream().map(Provider::targetId).forEach(result::add);
        }
        return result.stream().distinct().toList();
    }

    static List<ResourceLocation> useIds(List<MachineDescriptor> descriptors) {
        List<ResourceLocation> result = new ArrayList<>();
        for (EnergyType type : EnergyType.values()) {
            if (!type.isMachineGenerated()) result.add(energyTargetId(type));
        }
        descriptors.stream()
                .filter(descriptor -> descriptor.category() == MachineEnergyTable.Category.TRANSFORM)
                .map(MachineDescriptor::id)
                .forEach(result::add);
        synchronized (TransformProviderApi.class) {
            PROVIDERS.stream().map(Provider::id).forEach(result::add);
        }
        if (result.stream().distinct().count() != result.size()) {
            throw new IllegalStateException("Duplicate transform use id");
        }
        return List.copyOf(result);
    }

    static List<Target> targets(List<MachineDescriptor> descriptors) {
        Map<ResourceLocation, Target> result = new LinkedHashMap<>();
        for (EnergyType type : EnergyType.values()) {
            if (!type.isMachineGenerated()) {
                Target target = new Target(
                        energyTargetId(type),
                        type.representativeStack(),
                        Component.translatable("gui.auto_storage.energy." + type.getId()));
                putTarget(result, target);
            }
        }
        descriptors.stream()
                .filter(descriptor -> descriptor.category() == MachineEnergyTable.Category.TRANSFORM)
                .map(descriptor -> new Target(
                        descriptor.id(),
                        descriptor.representativeStack(),
                        descriptor.representativeStack().getHoverName()))
                .forEach(target -> putTarget(result, target));
        synchronized (TransformProviderApi.class) {
            PROVIDERS.stream()
                    .map(provider -> new Target(
                            provider.targetId(),
                            provider.representative(),
                            provider.targetLabel()))
                    .forEach(target -> putTarget(result, target));
        }
        return List.copyOf(result.values());
    }

    private static void putTarget(
            Map<ResourceLocation, Target> targets,
            Target target
    ) {
        Target existing = targets.putIfAbsent(target.id(), target);
        if (existing != null && !existing.label().equals(target.label())) {
            throw new IllegalStateException(
                    "Conflicting labels for transform target " + target.id());
        }
    }

    static List<Use> uses(ItemStack input, List<MachineDescriptor> descriptors) {
        if (input.isEmpty()) return List.of();
        ItemStack one = input.copyWithCount(1);
        List<Use> result = new ArrayList<>();
        for (FuelValue value : FuelTable.getFuelValues(one)) {
            result.add(new Use(
                    energyTargetId(value.pool()),
                    energyTargetId(value.pool()),
                    value.pool().representativeStack(),
                    StorageResourceBridge.energyKey(value.pool()),
                    value.valuePerItem(),
                    false,
                    null,
                    0));
        }
        for (MachineDescriptor descriptor : descriptors) {
            if (descriptor.category() != MachineEnergyTable.Category.TRANSFORM
                    || !descriptor.accepts(one)) continue;
            MachineDescriptor.TransformAmount value = descriptor.valueOf(one);
            if (value.infinite() || value.amount() > 0) {
                result.add(new Use(
                        descriptor.id(),
                        descriptor.id(),
                        descriptor.representativeStack(),
                        StorageResourceBridge.descriptorKey(descriptor.id()),
                        value.amount(),
                        value.infinite(),
                        null,
                        0));
            }
        }
        synchronized (TransformProviderApi.class) {
            for (Provider provider : PROVIDERS) {
                Result resolved = provider.resolver().resolve(one.copy());
                if (resolved == null) continue;
                result.add(new Use(
                        provider.id(),
                        provider.targetId(),
                        provider.representative(),
                        resolved.output(),
                        resolved.amountPerItem(),
                        false,
                        resolved.stationId(),
                        resolved.stationWorkPerItem()));
            }
        }
        return List.copyOf(result);
    }

    static ItemStack sortStack(Use use) {
        return TerminalDisplayStack.create(
                use.representative(),
                use.infinite() ? Long.MAX_VALUE : use.amountPerItem());
    }

    public record Result(
            StorageResourceKey output,
            long amountPerItem,
            @Nullable ResourceLocation stationId,
            long stationWorkPerItem
    ) {
        public Result {
            Objects.requireNonNull(output, "output");
            if (amountPerItem <= 0 || stationWorkPerItem < 0
                    || (stationId == null) != (stationWorkPerItem == 0)) {
                throw new IllegalArgumentException("Invalid transform result");
            }
        }
    }

    public record Target(
            ResourceLocation id,
            ItemStack representative,
            Component label
    ) {
        public Target {
            Objects.requireNonNull(id, "id");
            representative = Objects.requireNonNull(representative, "representative")
                    .copyWithCount(1);
            Objects.requireNonNull(label, "label");
        }
    }

    public record Use(
            ResourceLocation id,
            ResourceLocation targetId,
            ItemStack representative,
            StorageResourceKey output,
            long amountPerItem,
            boolean infinite,
            @Nullable ResourceLocation stationId,
            long stationWorkPerItem
    ) {
        public Use {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(targetId, "targetId");
            representative = Objects.requireNonNull(representative, "representative")
                    .copyWithCount(1);
            Objects.requireNonNull(output, "output");
            if (infinite ? amountPerItem != 0 : amountPerItem <= 0) {
                throw new IllegalArgumentException("Invalid transform output amount");
            }
            if (stationWorkPerItem < 0 || (stationId == null) != (stationWorkPerItem == 0)) {
                throw new IllegalArgumentException("Invalid transform station cost");
            }
        }
    }

    @FunctionalInterface
    public interface Resolver {
        @Nullable
        Result resolve(ItemStack input);
    }

    private record Provider(
            ResourceLocation id,
            ResourceLocation targetId,
            ItemStack representative,
            Component targetLabel,
            Component sourceLabel,
            Resolver resolver
    ) {
        private Provider {
            representative = representative.copyWithCount(1);
            Objects.requireNonNull(targetLabel, "targetLabel");
            Objects.requireNonNull(sourceLabel, "sourceLabel");
        }
    }
}
