package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.UUID;

final class BusItemTransferPlan {
    private final Level level;
    private final BlockPos busPos;
    private final BlockEntity bus;
    private final BlockState busState;
    private final BusConfiguration configuration;
    private final StorageCoreBlockEntity core;
    private final BlockPos corePos;
    private final UUID networkId;
    private final long topologyRevision;
    private final List<BlockPos> path;
    private final BlockPos endpointPos;
    private final BlockState endpointState;
    private final Direction endpointSide;
    private final IItemHandler endpoint;

    private BusItemTransferPlan(
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
            IItemHandler endpoint
    ) {
        this.level = level;
        this.busPos = busPos;
        this.bus = bus;
        this.busState = busState;
        this.configuration = configuration;
        this.core = core;
        this.corePos = corePos;
        this.networkId = networkId;
        this.topologyRevision = topologyRevision;
        this.path = path;
        this.endpointPos = endpointPos;
        this.endpointState = endpointState;
        this.endpointSide = endpointSide;
        this.endpoint = endpoint;
    }

    static BusItemTransferPlan capture(
            Level level,
            BlockPos busPos,
            BlockPos endpointPos,
            Direction endpointSide,
            StorageCoreBlockEntity core,
            BusConfiguration configuration,
            IItemHandler endpoint,
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
        List<BlockPos> path = MagicStorage.findLoadedNetworkPath(
                level, busPos, core.getBlockPos());
        if (path.isEmpty()) return null;
        return new BusItemTransferPlan(
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
                endpoint);
    }

    boolean stillValid() {
        if (!sameBusIdentity()
                || !level.getBlockState(busPos).equals(busState)
                || !(bus instanceof BusConfigurationHost host)
                || !configuration.equals(host.getBusConfiguration())
                || !sameCoreIdentity()
                || core.getTopologyRevision() != topologyRevision
                || !core.getConnectedBlocks().contains(busPos)
                || !MagicStorage.hasLoadedNetworkPath(level, path, busPos, corePos)
                || !sameEndpointIdentity()) {
            return false;
        }
        return true;
    }

    boolean sameCoreIdentity() {
        return !core.isRemoved()
                && level.hasChunkAt(corePos)
                && level.getBlockEntity(corePos) == core
                && !core.isConflicted()
                && networkId.equals(core.getNetworkId());
    }

    boolean sameEndpointIdentity() {
        if (!level.hasChunkAt(endpointPos)
                || !level.getBlockState(endpointPos).equals(endpointState)) {
            return false;
        }
        IItemHandler current = level.getCapability(
                Capabilities.ItemHandler.BLOCK, endpointPos, endpointSide);
        return current != null && (current == endpoint || current.equals(endpoint));
    }

    private boolean sameBusIdentity() {
        return !bus.isRemoved()
                && level.hasChunkAt(busPos)
                && level.getBlockEntity(busPos) == bus;
    }
}
