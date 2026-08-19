package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class MachineDescriptor {
    private final ResourceLocation id;
    @Nullable
    private final Component stationLabel;
    private final ItemStack representative;
    private final Ingredient acceptedItems;
    private final MachineCategory category;
    private final int maxInstalledCount;
    @Nullable
    private final EnergyType energyType;
    private final int energyPerTick;
    @Nullable
    private final Supplier<List<MachineVariant>> variantSource;
    @Nullable
    private final TransformValue transformValue;
    @Nullable
    private final ResourceLocation worldStationBlockId;

    private MachineDescriptor(
            ResourceLocation id,
            @Nullable Component stationLabel,
            ItemStack representative,
            Ingredient acceptedItems,
            MachineCategory category,
            int maxInstalledCount,
            @Nullable EnergyType energyType,
            int energyPerTick,
            @Nullable Supplier<List<MachineVariant>> variantSource,
            @Nullable TransformValue transformValue,
            @Nullable ResourceLocation worldStationBlockId
    ) {
        this.id = Objects.requireNonNull(id);
        this.stationLabel = stationLabel;
        this.representative = Objects.requireNonNull(representative).copyWithCount(1);
        this.acceptedItems = Objects.requireNonNull(acceptedItems);
        this.category = Objects.requireNonNull(category);
        this.maxInstalledCount = maxInstalledCount;
        this.energyType = energyType;
        this.energyPerTick = energyPerTick;
        this.variantSource = variantSource;
        this.transformValue = transformValue;
        this.worldStationBlockId = worldStationBlockId;
        validate();
    }

    public static MachineDescriptor installable(
            ResourceLocation id,
            ItemStack representative,
            Ingredient acceptedItems,
            MachineCategory category,
            int maxInstalledCount,
            @Nullable EnergyType energyType,
            int energyPerTick
    ) {
        return new MachineDescriptor(
                id,
                representative.getHoverName(),
                representative,
                acceptedItems,
                category,
                maxInstalledCount,
                energyType,
                energyPerTick,
                null,
                null,
                null);
    }

    public static MachineDescriptor installableVariants(
            ResourceLocation id,
            Component stationLabel,
            Supplier<List<MachineVariant>> variants,
            MachineCategory category,
            int maxInstalledCount,
            @Nullable EnergyType energyType
    ) {
        Objects.requireNonNull(variants, "variants");
        List<MachineVariant> initial = checkedVariantStacks(id, variants.get());
        return new MachineDescriptor(
                id,
                Objects.requireNonNull(stationLabel, "stationLabel"),
                initial.getFirst().stack(),
                Ingredient.of(initial.stream().map(MachineVariant::stack)),
                category,
                maxInstalledCount,
                energyType,
                0,
                variants,
                null,
                null);
    }

    public static MachineDescriptor worldStation(
            ResourceLocation id,
            Component stationLabel,
            ItemStack representative,
            ResourceLocation worldStationBlockId
    ) {
        Objects.requireNonNull(worldStationBlockId, "worldStationBlockId");
        return new MachineDescriptor(
                id,
                Objects.requireNonNull(stationLabel, "stationLabel"),
                Objects.requireNonNull(representative),
                Ingredient.of(representative),
                MachineCategory.INSTANT,
                1,
                null,
                0,
                null,
                null,
                worldStationBlockId);
    }

    public static MachineDescriptor transform(
            ResourceLocation id,
            ItemStack representative,
            Ingredient acceptedItems,
            TransformValue transformValue
    ) {
        return new MachineDescriptor(
                id,
                null,
                representative,
                acceptedItems,
                MachineCategory.TRANSFORM,
                0,
                null,
                0,
                null,
                Objects.requireNonNull(transformValue),
                null);
    }

    static MachineDescriptor clientSynced(
            ResourceLocation id,
            @Nullable Component stationLabel,
            ItemStack representative,
            Ingredient acceptedItems,
            MachineCategory category,
            int maxInstalledCount,
            @Nullable EnergyType energyType,
            int energyPerTick
    ) {
        TransformValue clientOnly = category == MachineCategory.TRANSFORM
                ? id.equals(MachineEnergyTable.AXE_ID)
                ? stack -> AxeEnergy.isInfinite(stack)
                ? new TransformAmount(0, true)
                : new TransformAmount(AxeEnergy.finiteValue(stack), false)
                : stack -> TransformAmount.EMPTY
                : null;
        return new MachineDescriptor(
                id,
                stationLabel,
                representative,
                acceptedItems,
                category,
                maxInstalledCount,
                energyType,
                energyPerTick,
                null,
                clientOnly,
                null);
    }

    static MachineDescriptor clientSyncedVariants(
            ResourceLocation id,
            Component stationLabel,
            List<MachineVariant> variants,
            MachineCategory category,
            int maxInstalledCount,
            @Nullable EnergyType energyType
    ) {
        List<MachineVariant> snapshot = List.copyOf(variants);
        return installableVariants(
                id, stationLabel, () -> snapshot, category, maxInstalledCount, energyType);
    }

    private void validate() {
        if (representative.isEmpty()) {
            throw new IllegalArgumentException("Descriptor representative cannot be empty: " + id);
        }
        if (acceptedItems.isEmpty()) {
            throw new IllegalArgumentException("Descriptor ingredient cannot be explicitly empty: " + id);
        }
        if (category == MachineCategory.TRANSFORM) {
            if (stationLabel != null || maxInstalledCount != 0 || energyType != null || energyPerTick != 0
                    || transformValue == null || worldStationBlockId != null) {
                throw new IllegalArgumentException("Invalid transform descriptor: " + id);
            }
            return;
        }
        if (worldStationBlockId != null) {
            if (variantSource != null || transformValue != null || stationLabel == null
                    || stationLabel.getString().isBlank()) {
                throw new IllegalArgumentException("Invalid world-station descriptor: " + id);
            }
            return;
        }
        if (stationLabel == null || stationLabel.getString().isBlank()
                || maxInstalledCount < 1
                || maxInstalledCount > MachineDescriptorApi.MAX_INSTALLED_COUNT
                || transformValue != null) {
            throw new IllegalArgumentException("Invalid installable descriptor count: " + id);
        }
        if (variantSource == null && ((energyType == null) != (energyPerTick == 0) || energyPerTick < 0)) {
            throw new IllegalArgumentException("Descriptor energy type/rate must be declared together: " + id);
        }
    }

    public ResourceLocation id() {
        return id;
    }

    public Component stationLabel() {
        if (stationLabel == null) {
            throw new IllegalStateException("Transform descriptor has no station label: " + id);
        }
        return stationLabel;
    }

    public ItemStack representativeStack() {
        return variantSource == null ? representative.copy() : variants().getFirst().stack();
    }

    public Ingredient acceptedItems() {
        if (variantSource == null) return acceptedItems;
        return Ingredient.of(variants().stream().map(MachineVariant::stack));
    }

    public MachineCategory category() {
        return category;
    }

    @Nullable
    public ResourceLocation worldStationBlockId() {
        return worldStationBlockId;
    }

    public int maxInstalledCount() {
        return maxInstalledCount;
    }

    @Nullable
    public EnergyType energyType() {
        return energyType;
    }

    public int energyPerTick() {
        return energyPerTick;
    }

    public boolean isPolymorphic() {
        return variantSource != null;
    }

    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return variantSource == null
                ? acceptedItems.test(stack)
                : variants().stream().anyMatch(variant -> variant.matches(stack));
    }

    public boolean generatesEnergy() {
        return category == MachineCategory.PROCESS
                && variants().stream().anyMatch(variant -> !variant.rate().isZero());
    }

    public List<MachineVariant> variants() {
        if (category == MachineCategory.TRANSFORM) return List.of();
        if (variantSource != null) return checkedVariants(id, category, variantSource.get());
        MachineWorkRate rate = category == MachineCategory.PROCESS
                ? MachineWorkRate.of(energyPerTick, 1) : MachineWorkRate.ZERO;
        List<MachineVariant> variants = new ArrayList<>();
        for (ItemStack stack : acceptedItems.getItems()) {
            if (!stack.isEmpty() && variants.stream().noneMatch(variant -> variant.matches(stack))) {
                variants.add(MachineVariant.of(stack, rate));
            }
        }
        if (variants.isEmpty()) variants.add(MachineVariant.of(representative, rate));
        return List.copyOf(variants);
    }

    MachineDescriptor withContributedVariants() {
        if (category == MachineCategory.TRANSFORM
                || !MachineVariantContributors.has(id)) return this;
        return installableVariants(
                id,
                stationLabel(),
                () -> MachineVariantContributors.combine(id, variants()),
                category,
                maxInstalledCount,
                energyType);
    }

    public Optional<MachineWorkRate> rateFor(ItemStack stack) {
        if (category == MachineCategory.TRANSFORM || !accepts(stack)) {
            return Optional.empty();
        }
        if (variantSource == null) {
            return Optional.of(category == MachineCategory.PROCESS
                    ? MachineWorkRate.of(energyPerTick, 1) : MachineWorkRate.ZERO);
        }
        return variants().stream()
                .filter(variant -> variant.matches(stack))
                .map(MachineVariant::rate)
                .findFirst();
    }

    private static List<MachineVariant> checkedVariants(
            ResourceLocation id,
            MachineCategory category,
            List<MachineVariant> variants
    ) {
        List<MachineVariant> snapshot = checkedVariantStacks(id, variants);
        for (MachineVariant variant : snapshot) {
            MachineWorkRate rate = variant.rate();
            if (category == MachineCategory.PROCESS && rate.isZero()) {
                throw new IllegalArgumentException("Process machine variants require positive work rates: " + id);
            }
            if (category == MachineCategory.INSTANT && !rate.isZero()) {
                throw new IllegalArgumentException("Instant machine variants cannot generate work: " + id);
            }
            if (category == MachineCategory.TRANSFORM) {
                throw new IllegalArgumentException("Transform descriptors cannot expose installable variants: " + id);
            }
        }
        return snapshot;
    }

    private static List<MachineVariant> checkedVariantStacks(
            ResourceLocation id,
            List<MachineVariant> variants
    ) {
        Objects.requireNonNull(variants, "variants");
        if (variants.isEmpty() || variants.size() > 64) {
            throw new IllegalArgumentException("Machine descriptor requires one to 64 variants: " + id);
        }
        List<MachineVariant> snapshot = List.copyOf(variants);
        Set<net.minecraft.world.item.Item> items = new HashSet<>();
        for (MachineVariant variant : snapshot) {
            Objects.requireNonNull(variant, "variant");
            if (!items.add(variant.stack().getItem())) {
                throw new IllegalArgumentException("Duplicate machine variant item for " + id);
            }
        }
        return snapshot;
    }

    public TransformAmount valueOf(ItemStack stack) {
        if (category != MachineCategory.TRANSFORM || transformValue == null) {
            throw new IllegalStateException("Descriptor is not a transform: " + id);
        }
        if (!accepts(stack)) return TransformAmount.EMPTY;
        TransformAmount value = Objects.requireNonNull(transformValue.value(stack.copy()));
        if (value.amount() < 0 || value.infinite() && value.amount() != 0) {
            throw new IllegalStateException("Invalid transform value from descriptor: " + id);
        }
        return value;
    }

    @FunctionalInterface
    public interface TransformValue {
        TransformAmount value(ItemStack stack);
    }

    public record TransformAmount(long amount, boolean infinite) {
        public static final TransformAmount EMPTY = new TransformAmount(0, false);

        public TransformAmount {
            if (amount < 0 || infinite && amount != 0) {
                throw new IllegalArgumentException("Invalid transform amount");
            }
        }
    }
}
