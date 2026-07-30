package com.swear.autostorage;

import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class MekanismChemicalCompat {
    private static final ResourceLocation CHEMICAL_KIND = StorageResourceKindApi.CHEMICAL_KIND;
    private static final BlockCapability<IChemicalHandler, Direction> CHEMICAL_CAPABILITY =
            BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class);
    private static final ItemCapability<IChemicalHandler, Void> CHEMICAL_ITEM_CAPABILITY =
            ItemCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class);
    private static final Map<Object, WeakReference<IChemicalHandler>> HANDLERS =
            new WeakHashMap<>();

    private MekanismChemicalCompat() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        AutoStorageCapabilityApi.registerSidedResourceCapability(
                event,
                CHEMICAL_CAPABILITY,
                (resources, side) -> handler(resources));
    }

    public static Optional<StorageResourceContainerStrategy.Transfer> planContainerDeposit(
            net.minecraft.world.item.ItemStack singleContainer,
            HolderLookup.Provider registries
    ) {
        if (singleContainer.isEmpty() || singleContainer.getCount() != 1) return Optional.empty();
        net.minecraft.world.item.ItemStack copy = singleContainer.copy();
        IChemicalHandler handler = copy.getCapability(CHEMICAL_ITEM_CAPABILITY);
        if (handler == null) return Optional.empty();
        ChemicalStack simulated = handler.extractChemical(Long.MAX_VALUE, Action.SIMULATE);
        if (simulated.isEmpty()) return Optional.empty();
        long before = amountOf(handler, simulated);
        ChemicalStack executed = handler.extractChemical(simulated, Action.EXECUTE);
        if (!ChemicalStack.isSameChemical(executed, simulated)
                || executed.getAmount() != simulated.getAmount()
                || amountOf(handler, simulated) != before - executed.getAmount()) {
            return Optional.empty();
        }
        return Optional.of(new StorageResourceContainerStrategy.Transfer(
                key(executed), executed.getAmount(), copy));
    }

    public static Optional<StorageResourceHandler> findBlockHandler(
            Level level,
            BlockPos pos,
            Direction side
    ) {
        IChemicalHandler handler = level.getCapability(CHEMICAL_CAPABILITY, pos, side);
        return handler == null
                ? Optional.empty()
                : Optional.of(new BlockChemicalHandler(handler));
    }

    public static Optional<StorageResourceContainerStrategy.Transfer> planContainerWithdraw(
            net.minecraft.world.item.ItemStack singleContainer,
            StorageResourceKey key,
            long maxAmount,
            HolderLookup.Provider registries
    ) {
        if (singleContainer.isEmpty() || singleContainer.getCount() != 1
                || !key.kindId().equals(CHEMICAL_KIND) || maxAmount <= 0) {
            return Optional.empty();
        }
        ChemicalStack requested = stack(key, maxAmount);
        if (requested.isEmpty()) return Optional.empty();
        net.minecraft.world.item.ItemStack copy = singleContainer.copy();
        IChemicalHandler handler = copy.getCapability(CHEMICAL_ITEM_CAPABILITY);
        if (handler == null) return Optional.empty();
        ChemicalStack simulatedRemainder = handler.insertChemical(requested, Action.SIMULATE);
        long accepted = requested.getAmount() - simulatedRemainder.getAmount();
        if (accepted <= 0) return Optional.empty();
        long before = amountOf(handler, requested);
        ChemicalStack exact = requested.copyWithAmount(accepted);
        ChemicalStack executedRemainder = handler.insertChemical(exact, Action.EXECUTE);
        if (!executedRemainder.isEmpty()
                || amountOf(handler, requested) != before + accepted) {
            return Optional.empty();
        }
        return Optional.of(new StorageResourceContainerStrategy.Transfer(key, accepted, copy));
    }

    private static synchronized IChemicalHandler handler(StorageResourceHandler resources) {
        WeakReference<IChemicalHandler> reference = HANDLERS.get(resources);
        IChemicalHandler existing = reference == null ? null : reference.get();
        if (existing != null) return existing;
        IChemicalHandler created = new ResourceChemicalHandler(resources);
        HANDLERS.put(resources, new WeakReference<>(created));
        return created;
    }

    static StorageResourceKey key(ChemicalStack stack) {
        ResourceLocation chemicalId = stack.getChemicalHolder().unwrapKey()
                .orElseThrow(() -> new IllegalArgumentException("Unregistered chemical cannot be stored"))
                .location();
        return StorageResourceKey.of(CHEMICAL_KIND, chemicalId, new CompoundTag());
    }

    static ChemicalStack stack(StorageResourceKey key, long amount) {
        if (!key.kindId().equals(CHEMICAL_KIND) || amount <= 0) return ChemicalStack.EMPTY;
        return MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(
                        MekanismAPI.CHEMICAL_REGISTRY_NAME, key.resourceId()))
                .map(holder -> new ChemicalStack(holder, amount))
                .orElse(ChemicalStack.EMPTY);
    }

    private static long amountOf(IChemicalHandler handler, ChemicalStack chemical) {
        long result = 0;
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            ChemicalStack stored = handler.getChemicalInTank(tank);
            if (ChemicalStack.isSameChemical(stored, chemical)) {
                result = Math.addExact(result, stored.getAmount());
            }
        }
        return result;
    }

    private record BlockChemicalHandler(IChemicalHandler chemicals)
            implements StorageResourceHandler {
        @Override
        public List<StorageResourceKey> getStoredResources() {
            java.util.TreeSet<StorageResourceKey> result = new java.util.TreeSet<>();
            for (int tank = 0; tank < chemicals.getChemicalTanks(); tank++) {
                ChemicalStack stored = chemicals.getChemicalInTank(tank);
                if (!stored.isEmpty()) result.add(key(stored));
            }
            return List.copyOf(result);
        }

        @Override
        public long getAmount(StorageResourceKey key) {
            if (!key.kindId().equals(CHEMICAL_KIND)) return 0;
            long amount = 0;
            for (int tank = 0; tank < chemicals.getChemicalTanks(); tank++) {
                ChemicalStack stored = chemicals.getChemicalInTank(tank);
                if (!stored.isEmpty() && key(stored).equals(key)) {
                    amount = Math.addExact(amount, stored.getAmount());
                }
            }
            return amount;
        }

        @Override
        public long insert(StorageResourceKey key, long amount, boolean simulate) {
            ChemicalStack requested = stack(key, amount);
            if (requested.isEmpty()) return 0;
            ChemicalStack remainder = chemicals.insertChemical(
                    requested, simulate ? Action.SIMULATE : Action.EXECUTE);
            if (!remainder.isEmpty() && !ChemicalStack.isSameChemical(remainder, requested)) return 0;
            return requested.getAmount() - remainder.getAmount();
        }

        @Override
        public long extract(StorageResourceKey key, long amount, boolean simulate) {
            ChemicalStack requested = stack(key, amount);
            if (requested.isEmpty()) return 0;
            ChemicalStack extracted = chemicals.extractChemical(
                    requested, simulate ? Action.SIMULATE : Action.EXECUTE);
            return !extracted.isEmpty() && ChemicalStack.isSameChemical(extracted, requested)
                    ? extracted.getAmount()
                    : 0;
        }
    }

    private static final class ResourceChemicalHandler implements IChemicalHandler {
        private final StorageResourceHandler resources;

        private ResourceChemicalHandler(StorageResourceHandler resources) {
            this.resources = resources;
        }

        @Override
        public int getChemicalTanks() {
            return Math.max(1, availableKeys().size());
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank) {
            List<StorageResourceKey> keys = availableKeys();
            if (tank < 0 || tank >= keys.size()) return ChemicalStack.EMPTY;
            StorageResourceKey key = keys.get(tank);
            return stack(key, resources.getAmount(key));
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack chemicalStack) {
            throw new UnsupportedOperationException("Auto Storage chemical tanks are transactional");
        }

        @Override
        public long getChemicalTankCapacity(int tank) {
            return tank >= 0 && tank < getChemicalTanks() ? Long.MAX_VALUE : 0;
        }

        @Override
        public boolean isValid(int tank, ChemicalStack chemicalStack) {
            return tank >= 0 && tank < getChemicalTanks() && !chemicalStack.isEmpty();
        }

        @Override
        public ChemicalStack insertChemical(
                int tank,
                ChemicalStack chemicalStack,
                Action action
        ) {
            if (!isValid(tank, chemicalStack)) return chemicalStack.copy();
            ChemicalStack stored = getChemicalInTank(tank);
            if (!stored.isEmpty() && !ChemicalStack.isSameChemical(stored, chemicalStack)) {
                return chemicalStack.copy();
            }
            return insertChemical(chemicalStack, action);
        }

        @Override
        public ChemicalStack insertChemical(ChemicalStack chemicalStack, Action action) {
            if (chemicalStack.isEmpty()) return ChemicalStack.EMPTY;
            long inserted = resources.insert(
                    key(chemicalStack),
                    chemicalStack.getAmount(),
                    action.simulate());
            long remaining = chemicalStack.getAmount() - inserted;
            return remaining == 0 ? ChemicalStack.EMPTY : chemicalStack.copyWithAmount(remaining);
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            List<StorageResourceKey> keys = availableKeys();
            if (amount <= 0 || tank < 0 || tank >= keys.size()) return ChemicalStack.EMPTY;
            StorageResourceKey key = keys.get(tank);
            long extracted = resources.extract(
                    key,
                    amount,
                    action.simulate());
            return stack(key, extracted);
        }

        private List<StorageResourceKey> availableKeys() {
            return resources.getStoredResources().stream()
                    .filter(key -> key.kindId().equals(CHEMICAL_KIND))
                    .filter(key -> !stack(key, 1).isEmpty())
                    .sorted()
                    .toList();
        }
    }
}
