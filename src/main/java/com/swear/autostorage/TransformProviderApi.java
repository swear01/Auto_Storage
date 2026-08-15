package com.swear.autostorage;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;
import com.swear.autostorage.api.AutoStorageApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Public registry and value contracts for deterministic Transform providers.
 */
public final class TransformProviderApi {
    /**
     * Maximum number of provider entries.
     */
    public static final int MAX_PROVIDERS = 256;
    /**
     * Registry key for Transform providers.
     */
    public static final ResourceKey<Registry<TransformProvider>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(
                    AutoStorageApi.MOD_ID, "transform_provider"));
    public static final int TARGET_BUTTON_BASE = 1_000;
    static final int LEGACY_FUEL_BUTTON_BASE = 19;

    private TransformProviderApi() {
    }

    /**
     * Creates an addon-owned deferred register.
     *
     * @param modId addon namespace
     * @return deferred register targeting the provider registry
     */
    public static DeferredRegister<TransformProvider> createDeferredRegister(
            String modId
    ) {
        return DeferredRegister.create(REGISTRY_KEY, modId);
    }

    public static Optional<Component> sourceLabel(ResourceLocation providerId) {
        TransformProvider provider = AutoStorage.TRANSFORM_PROVIDER_REGISTRY.get(providerId);
        return provider == null ? Optional.empty() : Optional.of(provider.sourceLabel());
    }

    public static ResourceLocation energyTargetId(EnergyType type) {
        Objects.requireNonNull(type, "type");
        if (type.isMachineGenerated()) {
            throw new IllegalArgumentException("Machine-generated work is not a transform target");
        }
        return ResourceLocation.fromNamespaceAndPath(AutoStorageApi.MOD_ID, type.getId());
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
                .filter(descriptor -> descriptor.category() == MachineCategory.TRANSFORM)
                .map(MachineDescriptor::id)
                .forEach(result::add);
        providers().stream()
                .map(entry -> entry.getValue().targetId())
                .forEach(result::add);
        return result.stream().distinct().toList();
    }

    static List<ResourceLocation> useIds(List<MachineDescriptor> descriptors) {
        List<ResourceLocation> result = new ArrayList<>();
        for (EnergyType type : EnergyType.values()) {
            if (!type.isMachineGenerated()) result.add(energyTargetId(type));
        }
        descriptors.stream()
                .filter(descriptor -> descriptor.category() == MachineCategory.TRANSFORM)
                .map(MachineDescriptor::id)
                .forEach(result::add);
        providers().stream().map(Map.Entry::getKey).forEach(result::add);
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
                .filter(descriptor -> descriptor.category() == MachineCategory.TRANSFORM)
                .map(descriptor -> new Target(
                        descriptor.id(),
                        descriptor.representativeStack(),
                        descriptor.representativeStack().getHoverName()))
                .forEach(target -> putTarget(result, target));
        providers().stream()
                .map(Map.Entry::getValue)
                .map(provider -> new Target(
                        provider.targetId(),
                        provider.representative(),
                        provider.targetLabel()))
                .forEach(target -> putTarget(result, target));
        List<Target> ordered = new ArrayList<>(result.values());
        ordered.sort(Comparator
                .comparingInt((Target target) ->
                        CORE_TARGET_ORDER.contains(target.id()) ? 0 : 1)
                .thenComparing(target -> target.label().getString()));
        return List.copyOf(ordered);
    }

    /**
     * Core universal resources pinned at the top of the Transform target
     * sidebar in a fixed, stable order; module-produced kinds follow sorted
     * by label.
     */
    private static final List<ResourceLocation> CORE_TARGET_ORDER = List.of(
            StorageResourceKindApi.ENERGY_KIND,
            energyTargetId(EnergyType.FURNACE_FUEL),
            energyTargetId(EnergyType.BLAZE_FUEL),
            StorageResourceKindApi.BOTANIA_MANA_KIND);



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
            if (descriptor.category() != MachineCategory.TRANSFORM
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
        for (Map.Entry<ResourceLocation, TransformProvider> entry : providers()) {
            TransformProvider provider = entry.getValue();
            Result resolved = provider.resolver().resolve(one.copy());
            if (resolved == null) continue;
            result.add(new Use(
                    entry.getKey(),
                    provider.targetId(),
                    provider.representative(),
                    resolved.output(),
                    resolved.amountPerItem(),
                    false,
                    resolved.stationId(),
                    resolved.stationWorkPerItem(),
                    resolved.retainedItems()));
        }
        return List.copyOf(result);
    }

    private static List<Map.Entry<ResourceLocation, TransformProvider>> providers() {
        return AutoStorage.TRANSFORM_PROVIDER_REGISTRY.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey().location(), entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .toList();
    }

    static ItemStack sortStack(Use use) {
        return TerminalDisplayStack.create(
                use.representative(),
                use.infinite() ? Long.MAX_VALUE : use.amountPerItem());
    }

    /**
     * One resolved typed output and its optional station-work cost.
     *
     * @param output exact typed output key
     * @param amountPerItem positive output amount for one input item
     * @param stationId required station descriptor, or {@code null}
     * @param stationWorkPerItem positive work cost when a station is present
     * @param retainedItems exact item stacks returned per consumed input item
     */
    public record Result(
            StorageResourceKey output,
            long amountPerItem,
            @Nullable ResourceLocation stationId,
            long stationWorkPerItem,
            List<ItemStack> retainedItems
    ) {
        public Result {
            Objects.requireNonNull(output, "output");
            if (amountPerItem <= 0 || stationWorkPerItem < 0
                    || (stationId == null) != (stationWorkPerItem == 0)) {
                throw new IllegalArgumentException("Invalid transform result");
            }
            retainedItems = checkedRetainedItems(retainedItems);
        }

        public Result(
                StorageResourceKey output,
                long amountPerItem,
                @Nullable ResourceLocation stationId,
                long stationWorkPerItem
        ) {
            this(output, amountPerItem, stationId, stationWorkPerItem, List.of());
        }
    }

    private static List<ItemStack> checkedRetainedItems(List<ItemStack> retainedItems) {
        Objects.requireNonNull(retainedItems, "retainedItems");
        List<ItemStack> retained = new ArrayList<>(retainedItems.size());
        for (ItemStack stack : retainedItems) {
            Objects.requireNonNull(stack, "retained item");
            ItemStack copy = stack.copy();
            if (copy.isEmpty() || copy.getCount() <= 0
                    || copy.getCount() > copy.getMaxStackSize()) {
                throw new IllegalArgumentException(
                        "Retained item must have a positive stackable count");
            }
            if (retained.stream().anyMatch(existing ->
                    ItemStack.isSameItemSameComponents(existing, copy))) {
                throw new IllegalArgumentException(
                        "Duplicate retained item " + copy.getItem());
            }
            retained.add(copy);
        }
        return List.copyOf(retained);
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
            long stationWorkPerItem,
            List<ItemStack> retainedItems
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
            retainedItems = checkedRetainedItems(retainedItems);
        }

        public Use(
                ResourceLocation id,
                ResourceLocation targetId,
                ItemStack representative,
                StorageResourceKey output,
                long amountPerItem,
                boolean infinite,
                @Nullable ResourceLocation stationId,
                long stationWorkPerItem
        ) {
            this(id, targetId, representative, output, amountPerItem, infinite,
                    stationId, stationWorkPerItem, List.of());
        }
    }

    /**
     * Side-effect-free exact-input resolver. Returning {@code null} means the
     * provider does not accept the supplied item.
     */
    @FunctionalInterface
    public interface Resolver {
        @Nullable
        Result resolve(ItemStack input);
    }

}
