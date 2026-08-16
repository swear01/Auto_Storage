package com.swear.autostorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MachineEnergyTable {
    public static final int FURNACE_SLOT = 0;
    public static final int BLAST_FURNACE_SLOT = 1;
    public static final int SMOKER_SLOT = 2;
    public static final int CAMPFIRE_SLOT = 3;
    public static final int BREWING_STAND_SLOT = 4;
    public static final int CRAFTING_TABLE_SLOT = 5;
    public static final int STONECUTTER_SLOT = 6;
    public static final int SMITHING_TABLE_SLOT = 7;
    public static final int AXE_SLOT = 8;

    public static final ResourceLocation FURNACE_ID = id("furnace");
    public static final ResourceLocation BLAST_FURNACE_ID = id("blast_furnace");
    public static final ResourceLocation SMOKER_ID = id("smoker");
    public static final ResourceLocation CAMPFIRE_ID = id("campfire");
    public static final ResourceLocation BREWING_STAND_ID = id("brewing_stand");
    public static final ResourceLocation CRAFTING_TABLE_ID = id("crafting_table");
    public static final ResourceLocation STONECUTTER_ID = id("stonecutter");
    public static final ResourceLocation SMITHING_TABLE_ID = id("smithing_table");
    public static final ResourceLocation AXE_ID = id("axe");
    public static final ResourceLocation HAMMER_ID = id("engineers_hammer");
    public static final ResourceLocation ELECTRODE_ID = id("graphite_electrode");

    private static final List<ResourceLocation> BUILT_IN_ORDER = List.of(
            FURNACE_ID,
            BLAST_FURNACE_ID,
            SMOKER_ID,
            CAMPFIRE_ID,
            BREWING_STAND_ID,
            CRAFTING_TABLE_ID,
            STONECUTTER_ID,
            SMITHING_TABLE_ID,
            AXE_ID);
    private static volatile List<MachineDescriptor> cachedEntries;

    private MachineEnergyTable() {
    }

    static void registerBuiltIns(DeferredRegister<MachineDescriptor> descriptors) {
        descriptors.register(FURNACE_ID.getPath(), () -> MachineDescriptor.installableVariants(
                FURNACE_ID,
                Items.FURNACE.getDescription(),
                () -> List.of(MachineVariant.of(
                        new ItemStack(Items.FURNACE), MachineWorkRate.ONE)),
                MachineCategory.PROCESS,
                MachineDescriptorApi.MAX_INSTALLED_COUNT,
                EnergyType.SMELTING_ENERGY));
        descriptors.register(BLAST_FURNACE_ID.getPath(), () -> installable(
                BLAST_FURNACE_ID, new ItemStack(Items.BLAST_FURNACE), EnergyType.BLASTING_ENERGY, 1,
                MachineCategory.PROCESS, MachineDescriptorApi.MAX_INSTALLED_COUNT));
        descriptors.register(SMOKER_ID.getPath(), () -> installable(
                SMOKER_ID, new ItemStack(Items.SMOKER), EnergyType.SMOKING_ENERGY, 1,
                MachineCategory.PROCESS, MachineDescriptorApi.MAX_INSTALLED_COUNT));
        descriptors.register(CAMPFIRE_ID.getPath(), () -> installable(
                CAMPFIRE_ID, new ItemStack(Items.CAMPFIRE), EnergyType.CAMPFIRE_ENERGY, 1,
                MachineCategory.PROCESS, MachineDescriptorApi.MAX_INSTALLED_COUNT));
        descriptors.register(BREWING_STAND_ID.getPath(), () -> installable(
                BREWING_STAND_ID, new ItemStack(Items.BREWING_STAND), EnergyType.BREW_ENERGY, 1,
                MachineCategory.PROCESS, MachineDescriptorApi.MAX_INSTALLED_COUNT));
        descriptors.register(CRAFTING_TABLE_ID.getPath(), () -> installable(
                CRAFTING_TABLE_ID, new ItemStack(Items.CRAFTING_TABLE), null, 0, MachineCategory.INSTANT, 1));
        descriptors.register(STONECUTTER_ID.getPath(), () -> installable(
                STONECUTTER_ID, new ItemStack(Items.STONECUTTER), null, 0, MachineCategory.INSTANT, 1));
        descriptors.register(SMITHING_TABLE_ID.getPath(), () -> installable(
                SMITHING_TABLE_ID, new ItemStack(Items.SMITHING_TABLE), null, 0, MachineCategory.INSTANT, 1));
        descriptors.register(AXE_ID.getPath(), () -> MachineDescriptor.transform(
                AXE_ID,
                new ItemStack(Items.IRON_AXE),
                Ingredient.of(
                        Items.WOODEN_AXE,
                        Items.STONE_AXE,
                        Items.IRON_AXE,
                        Items.GOLDEN_AXE,
                        Items.DIAMOND_AXE,
                        Items.NETHERITE_AXE),
                stack -> AxeEnergy.isInfinite(stack)
                        ? new MachineDescriptor.TransformAmount(0, true)
                        : new MachineDescriptor.TransformAmount(AxeEnergy.finiteValue(stack), false)));
        descriptors.register(HAMMER_ID.getPath(), () -> MachineDescriptor.transform(
                HAMMER_ID,
                new ItemStack(net.minecraft.world.item.Items.STONE_AXE),
                Ingredient.of(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.ResourceLocation
                                        .fromNamespaceAndPath(
                                                "immersiveengineering", "hammer"))),
                stack -> ToolDurability.finiteValue(stack) > 0
                        ? new MachineDescriptor.TransformAmount(
                                ToolDurability.finiteValue(stack), false)
                        : new MachineDescriptor.TransformAmount(0, false)));
        descriptors.register(ELECTRODE_ID.getPath(), () -> MachineDescriptor.transform(
                ELECTRODE_ID,
                new ItemStack(net.minecraft.world.item.Items.STICK),
                Ingredient.of(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.ResourceLocation
                                        .fromNamespaceAndPath(
                                                "immersiveengineering",
                                                "graphite_electrode"))),
                stack -> ToolDurability.finiteValue(stack) > 0
                        ? new MachineDescriptor.TransformAmount(
                                ToolDurability.finiteValue(stack), false)
                        : new MachineDescriptor.TransformAmount(0, false)));
    }

    public static int size() {
        return entries().size();
    }

    public static List<MachineDescriptor> entries() {
        List<MachineDescriptor> cached = cachedEntries;
        if (cached != null) return cached;
        synchronized (MachineEnergyTable.class) {
            cached = cachedEntries;
            if (cached == null) {
                cached = buildEntries();
                cachedEntries = cached;
            }
        }
        return cached;
    }

    private static List<MachineDescriptor> buildEntries() {
        MachineVariantContributors.validateDescriptorTargets(
                AutoStorage.MACHINE_VARIANT_CONTRIBUTOR_REGISTRY.entrySet().stream()
                        .map(entry -> Map.entry(
                                entry.getKey().location(), entry.getValue()))
                        .toList(),
                id -> AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.get(id) != null);
        List<MachineDescriptor> ordered = new ArrayList<>();
        for (ResourceLocation id : BUILT_IN_ORDER) {
            MachineDescriptor descriptor = AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.get(id);
            if (descriptor == null) {
                throw new IllegalStateException("Missing built-in machine descriptor: " + id);
            }
            descriptor = descriptor.withContributedVariants();
            validateRegistryId(id, descriptor);
            ordered.add(descriptor);
        }
        AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.entrySet().stream()
                .filter(entry -> !BUILT_IN_ORDER.contains(entry.getKey().location()))
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    MachineDescriptor descriptor = entry.getValue().withContributedVariants();
                    validateRegistryId(entry.getKey().location(), descriptor);
                    ordered.add(descriptor);
                });
        if (ordered.size() > MachineDescriptorApi.MAX_DESCRIPTORS) {
            throw new IllegalStateException("Registered " + ordered.size()
                    + " machine descriptors; maximum is " + MachineDescriptorApi.MAX_DESCRIPTORS);
        }
        return List.copyOf(ordered);
    }

    public static MachineDescriptor get(int slot) {
        List<MachineDescriptor> entries = entries();
        return slot >= 0 && slot < entries.size() ? entries.get(slot) : null;
    }

    public static MachineDescriptor get(ResourceLocation id) {
        return entries().stream()
                .filter(descriptor -> descriptor.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public static int findSlot(ItemStack stack) {
        List<MachineDescriptor> entries = entries();
        for (int slot = 0; slot < entries.size(); slot++) {
            if (entries.get(slot).accepts(stack)) return slot;
        }
        return -1;
    }

    public static int findSlot(ResourceLocation descriptorId) {
        List<MachineDescriptor> entries = entries();
        for (int slot = 0; slot < entries.size(); slot++) {
            if (entries.get(slot).id().equals(descriptorId)) return slot;
        }
        return -1;
    }

    public static boolean isInstalled(StorageCoreBlockEntity core, int slot) {
        MachineDescriptor descriptor = get(slot);
        return core != null && descriptor != null && descriptor.maxInstalledCount() > 0
                && descriptor.accepts(core.getMachineContainer().getItem(slot));
    }

    public static void writeSnapshot(RegistryFriendlyByteBuf buf, List<MachineDescriptor> descriptors) {
        if (descriptors.size() > MachineDescriptorApi.MAX_DESCRIPTORS) {
            throw new IllegalArgumentException("Too many machine descriptors: " + descriptors.size());
        }
        buf.writeVarInt(descriptors.size());
        Set<ResourceLocation> ids = new HashSet<>();
        for (MachineDescriptor descriptor : descriptors) {
            if (!ids.add(descriptor.id())) {
                throw new IllegalArgumentException("Duplicate machine descriptor: " + descriptor.id());
            }
            buf.writeResourceLocation(descriptor.id());
            ItemStack.STREAM_CODEC.encode(buf, descriptor.representativeStack());
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, descriptor.acceptedItems());
            buf.writeEnum(descriptor.category());
            if (descriptor.category() != MachineCategory.TRANSFORM) {
                ComponentSerialization.STREAM_CODEC.encode(buf, descriptor.stationLabel());
            }
            buf.writeVarInt(descriptor.maxInstalledCount());
            buf.writeBoolean(descriptor.energyType() != null);
            if (descriptor.energyType() != null) buf.writeEnum(descriptor.energyType());
            buf.writeVarInt(descriptor.energyPerTick());
            buf.writeBoolean(descriptor.isPolymorphic());
            List<MachineVariant> variants = descriptor.isPolymorphic()
                    ? descriptor.variants() : List.of();
            buf.writeVarInt(variants.size());
            for (MachineVariant variant : variants) {
                ItemStack.STREAM_CODEC.encode(buf, variant.stack());
                buf.writeVarLong(variant.rate().numerator());
                buf.writeVarLong(variant.rate().denominator());
            }
        }
    }

    public static List<MachineDescriptor> readSnapshot(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MachineDescriptorApi.MAX_DESCRIPTORS) {
            throw new IllegalArgumentException("Invalid machine descriptor count: " + count);
        }
        List<MachineDescriptor> descriptors = new ArrayList<>(count);
        Set<ResourceLocation> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate machine descriptor: " + id);
            ItemStack representative = ItemStack.STREAM_CODEC.decode(buf);
            Ingredient acceptedItems = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
            MachineCategory category = buf.readEnum(MachineCategory.class);
            var stationLabel = category == MachineCategory.TRANSFORM
                    ? null : ComponentSerialization.STREAM_CODEC.decode(buf);
            int maxInstalledCount = buf.readVarInt();
            EnergyType energyType = buf.readBoolean() ? buf.readEnum(EnergyType.class) : null;
            int energyPerTick = buf.readVarInt();
            boolean polymorphic = buf.readBoolean();
            int variantCount = buf.readVarInt();
            if (variantCount < 0 || variantCount > 64) {
                throw new IllegalArgumentException("Invalid machine variant count: " + variantCount);
            }
            List<MachineVariant> variants = new ArrayList<>(variantCount);
            for (int variant = 0; variant < variantCount; variant++) {
                variants.add(MachineVariant.of(
                        ItemStack.STREAM_CODEC.decode(buf),
                        MachineWorkRate.of(buf.readVarLong(), buf.readVarLong())));
            }
            descriptors.add(!polymorphic
                    ? MachineDescriptor.clientSynced(
                            id, stationLabel, representative, acceptedItems, category,
                            maxInstalledCount, energyType, energyPerTick)
                    : MachineDescriptor.clientSyncedVariants(
                            id, stationLabel, variants, category, maxInstalledCount, energyType));
        }
        return List.copyOf(descriptors);
    }

    private static MachineDescriptor installable(
            ResourceLocation id,
            ItemStack representative,
            EnergyType energyType,
            int energyPerTick,
            MachineCategory category,
            int maxInstalledCount
    ) {
        return MachineDescriptor.installable(
                id,
                representative,
                Ingredient.of(representative.getItem()),
                category,
                maxInstalledCount,
                energyType,
                energyPerTick);
    }

    private static void validateRegistryId(ResourceLocation registryId, MachineDescriptor descriptor) {
        if (!registryId.equals(descriptor.id())) {
            throw new IllegalStateException("Machine descriptor registry id " + registryId
                    + " does not match declared id " + descriptor.id());
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path);
    }
}
