package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

final class BusTypedResourceTransfer {
    static final long TRANSFER_LIMIT = 1_000;

    private BusTypedResourceTransfer() {
    }

    static boolean importOne(
            Level level,
            BlockPos busPos,
            BlockPos sourcePos,
            Direction sourceSide,
            StorageCoreBlockEntity core,
            BusConfiguration configuration,
            BusResourceEscrow escrow,
            Actor actor
    ) {
        for (StorageResourceBlockStrategies.Endpoint endpoint
                : StorageResourceBlockStrategies.find(level, sourcePos, sourceSide)) {
            TransferPlan plan = TransferPlan.capture(
                    level, busPos, sourcePos, sourceSide, core, configuration,
                    endpoint, actor, BusOperationDirection.IMPORT);
            if (plan == null) return false;
            StorageResourceHandler source = endpoint.handler();
            List<StorageResourceKey> candidates = source.getStoredResources().stream()
                    .filter(endpoint::supports)
                    .filter(key -> !key.kindId().equals(StorageResourceKindApi.ITEM_KIND))
                    .filter(key -> BusFilterPolicy.compile(
                            configuration, level.registryAccess()).allows(key))
                    .distinct()
                    .sorted()
                    .toList();
            for (StorageResourceKey key : candidates) {
                long simulatedExtract = bounded(source.extract(key, TRANSFER_LIMIT, true));
                if (simulatedExtract <= 0) continue;
                long simulatedInsert = bounded(core.insertResource(
                        key, simulatedExtract, Action.SIMULATE, actor));
                long planned = Math.min(simulatedExtract, simulatedInsert);
                if (planned <= 0) continue;
                if (!plan.stillValid()) return false;

                long extracted = nonNegative(source.extract(key, planned, false));
                if (extracted <= 0) continue;
                long executable = Math.min(extracted, planned);
                if (!plan.stillValid()) {
                    preserve(plan, escrow, key, extracted, "Import stale execute rollback");
                    return false;
                }

                long unrecovered = restoreToHandler(source, key, extracted - executable);
                long inserted = nonNegative(core.insertResource(
                        key, executable, Action.EXECUTE, actor));
                if (inserted > executable) inserted = executable;
                unrecovered = Math.addExact(
                        unrecovered, restoreToHandler(source, key, executable - inserted));
                preserve(plan, escrow, key, unrecovered, "Import rollback");
                return inserted > 0;
            }
        }
        return false;
    }

    static boolean exportOne(
            Level level,
            BlockPos busPos,
            BlockPos targetPos,
            Direction targetSide,
            StorageCoreBlockEntity core,
            BusConfiguration configuration,
            BusResourceEscrow escrow,
            Actor actor
    ) {
        List<StorageResourceBlockStrategies.Endpoint> endpoints =
                StorageResourceBlockStrategies.find(level, targetPos, targetSide);
        if (endpoints.isEmpty()) return false;
        BusFilterPolicy filter = BusFilterPolicy.compile(
                configuration, level.registryAccess());
        List<StorageResourceKey> candidates = filter.orderedResourceCandidates(
                core.getResourceKeys().stream()
                .filter(key -> !key.kindId().equals(StorageResourceKindApi.ITEM_KIND))
                .toList());
        for (StorageResourceKey key : candidates) {
            long simulatedExtract = bounded(core.extractResource(
                    key, TRANSFER_LIMIT, Action.SIMULATE, actor));
            if (simulatedExtract <= 0) continue;
            for (StorageResourceBlockStrategies.Endpoint endpoint : endpoints) {
                if (!endpoint.supports(key)) continue;
                TransferPlan plan = TransferPlan.capture(
                        level, busPos, targetPos, targetSide, core, configuration,
                        endpoint, actor, BusOperationDirection.EXPORT);
                if (plan == null) return false;
                StorageResourceHandler target = endpoint.handler();
                long simulatedInsert = bounded(target.insert(key, simulatedExtract, true));
                long planned = Math.min(simulatedExtract, simulatedInsert);
                if (planned <= 0) continue;
                if (!plan.stillValid()) return false;

                long extracted = nonNegative(core.extractResource(
                        key, planned, Action.EXECUTE, actor));
                if (extracted <= 0) return false;
                if (extracted > planned) extracted = planned;
                if (!plan.stillValid()) {
                    long unrestored = restoreToCore(plan, key, extracted, actor);
                    preserve(plan, escrow, key, unrestored, "Export stale execute rollback");
                    return false;
                }

                long inserted = nonNegative(target.insert(key, extracted, false));
                if (inserted > extracted) inserted = extracted;
                if (!plan.stillValid()) {
                    long reversed = Math.min(inserted, nonNegative(target.extract(
                            key, inserted, false)));
                    long unrestored = restoreToCore(
                            plan, key, Math.addExact(extracted - inserted, reversed), actor);
                    long unreclaimed = plan.sameEndpointIdentity() ? 0 : inserted - reversed;
                    preserve(plan, escrow, key, Math.addExact(unreclaimed, unrestored),
                            "Export stale target rollback");
                    return false;
                }
                long unrestored = restoreToCore(plan, key, extracted - inserted, actor);
                preserve(plan, escrow, key, unrestored, "Export rollback");
                return inserted > 0;
            }
        }
        return false;
    }

    private static long restoreToHandler(
            StorageResourceHandler handler,
            StorageResourceKey key,
            long amount
    ) {
        if (amount <= 0) return 0;
        long restored = Math.min(amount, nonNegative(handler.insert(key, amount, false)));
        return amount - restored;
    }

    private static long restoreToCore(
            TransferPlan plan,
            StorageResourceKey key,
            long amount,
            Actor actor
    ) {
        if (amount <= 0) return 0;
        if (!plan.sameCoreIdentity()) return amount;
        long restored = Math.min(amount, nonNegative(plan.core().insertResource(
                key, amount, Action.EXECUTE, actor)));
        return amount - restored;
    }

    private static void preserve(
            TransferPlan plan,
            BusResourceEscrow escrow,
            StorageResourceKey key,
            long amount,
            String phase
    ) {
        if (amount <= 0) return;
        if (plan.sameBusIdentity()) {
            escrow.add(key, amount);
            logEscrowed(plan.busPos(), key, amount, phase);
            return;
        }

        BusResourceEscrow recovery = BusResourceEscrow.empty();
        recovery.add(key, amount);
        CompoundTag tag = new CompoundTag();
        plan.configuration().withoutOwner().save(tag, plan.level().registryAccess());
        recovery.save(tag);
        ItemStack drop;
        if (plan.direction() == BusOperationDirection.IMPORT) {
            drop = new ItemStack(AutoStorage.IMPORT_BUS_ITEM.get());
            BlockItem.setBlockEntityData(drop, AutoStorage.IMPORT_BUS_BE.get(), tag);
        } else {
            drop = new ItemStack(AutoStorage.EXPORT_BUS_ITEM.get());
            BlockItem.setBlockEntityData(drop, AutoStorage.EXPORT_BUS_BE.get(), tag);
        }
        BusRecoveryDrops.spawnIfMissing(plan.level(), plan.busPos(), drop);
        logEscrowed(plan.busPos(), key, amount, phase + " recovery drop");
    }

    private static long bounded(long amount) {
        return Math.min(TRANSFER_LIMIT, nonNegative(amount));
    }

    private static long nonNegative(long amount) {
        return Math.max(0, amount);
    }

    private static void logEscrowed(
            BlockPos busPos,
            StorageResourceKey key,
            long amount,
            String phase
    ) {
        if (amount <= 0) return;
        AutoStorage.LOGGER.warn(
                "{} preserved typed resource in Bus escrow at {}: key={}, amount={}",
                phase, busPos, key, amount);
    }

    private record TransferPlan(
            Level level,
            BlockPos busPos,
            BlockEntity bus,
            BlockState busState,
            BusConfiguration configuration,
            StorageCoreBlockEntity core,
            BlockPos corePos,
            UUID networkId,
            long topologyRevision,
            List<BlockPos> path,
            BlockPos endpointPos,
            BlockState endpointState,
            Direction endpointSide,
            StorageResourceBlockStrategies.Endpoint endpoint,
            BusOperationDirection direction
    ) {
        private static TransferPlan capture(
                Level level,
                BlockPos busPos,
                BlockPos endpointPos,
                Direction endpointSide,
                StorageCoreBlockEntity core,
                BusConfiguration configuration,
                StorageResourceBlockStrategies.Endpoint endpoint,
                Actor actor,
                BusOperationDirection direction
        ) {
            BlockEntity bus = level.getBlockEntity(busPos);
            if (!(bus instanceof BusConfigurationHost host)
                    || !(actor instanceof BusActor busActor)
                    || host.busKind() != (direction == BusOperationDirection.IMPORT
                    ? BusKind.IMPORT : BusKind.EXPORT)
                    || !configuration.supported()
                    || !configuration.automationEnabled()
                    || configuration.mode() != BusMode.DIRECTIONAL
                    || !configuration.equals(host.getBusConfiguration())
                    || core.isRemoved()
                    || core.isConflicted()) {
                return null;
            }
            UUID networkId = core.getNetworkId();
            if (!busActor.dimension().equals(level.dimension())
                    || !busActor.busPos().equals(busPos)
                    || !busActor.networkId().equals(networkId)
                    || busActor.direction() != direction) {
                return null;
            }
            List<BlockPos> path = AutoStorage.findLoadedNetworkPath(
                    level, busPos, core.getBlockPos());
            if (path.isEmpty()) return null;
            return new TransferPlan(
                    level,
                    busPos.immutable(),
                    bus,
                    level.getBlockState(busPos),
                    configuration,
                    core,
                    core.getBlockPos().immutable(),
                    networkId,
                    core.getTopologyRevision(),
                    path,
                    endpointPos.immutable(),
                    level.getBlockState(endpointPos),
                    endpointSide,
                    endpoint,
                    direction);
        }

        private boolean stillValid() {
            if (!sameBusIdentity()
                    || !level.getBlockState(busPos).equals(busState)
                    || !(bus instanceof BusConfigurationHost host)
                    || !configuration.equals(host.getBusConfiguration())
                    || !sameCoreIdentity()
                    || core.getTopologyRevision() != topologyRevision
                    || !core.getConnectedBlocks().contains(busPos)
                    || !AutoStorage.hasLoadedNetworkPath(level, path, busPos, corePos)
                    || !sameEndpointIdentity()) {
                return false;
            }
            return true;
        }

        private boolean sameBusIdentity() {
            return !bus.isRemoved()
                    && level.hasChunkAt(busPos)
                    && level.getBlockEntity(busPos) == bus;
        }

        private boolean sameCoreIdentity() {
            return !core.isRemoved()
                    && level.hasChunkAt(corePos)
                    && level.getBlockEntity(corePos) == core
                    && !core.isConflicted()
                    && networkId.equals(core.getNetworkId());
        }

        private boolean sameEndpointIdentity() {
            return level.hasChunkAt(endpointPos)
                    && level.getBlockState(endpointPos).equals(endpointState)
                    && StorageResourceBlockStrategies.find(
                            level, endpointPos, endpointSide).stream()
                            .anyMatch(endpoint::sameIdentity);
        }
    }
}
