package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BusActor(
        Optional<UUID> owner,
        ResourceKey<Level> dimension,
        BlockPos busPos,
        UUID networkId,
        BusOperationDirection direction,
        BusOperationId operationId
) implements Actor {

    public BusActor {
        owner = Objects.requireNonNull(owner, "owner");
        dimension = Objects.requireNonNull(dimension, "dimension");
        busPos = Objects.requireNonNull(busPos, "busPos").immutable();
        networkId = Objects.requireNonNull(networkId, "networkId");
        direction = Objects.requireNonNull(direction, "direction");
        operationId = Objects.requireNonNull(operationId, "operationId");
    }

    public static BusActor owned(
            UUID owner,
            ResourceKey<Level> dimension,
            BlockPos busPos,
            UUID networkId,
            BusOperationDirection direction,
            BusOperationId operationId
    ) {
        return new BusActor(Optional.of(owner), dimension, busPos, networkId, direction, operationId);
    }

    public static BusActor legacyUnclaimed(
            ResourceKey<Level> dimension,
            BlockPos busPos,
            UUID networkId,
            BusOperationDirection direction,
            BusOperationId operationId
    ) {
        return new BusActor(Optional.empty(), dimension, busPos, networkId, direction, operationId);
    }

    public boolean legacyUnclaimed() {
        return owner.isEmpty();
    }

    @Override
    public String name() {
        return "bus_" + direction.name().toLowerCase(java.util.Locale.ROOT)
                + "@" + dimension.location() + ":" + busPos.toShortString()
                + "/" + networkId
                + "/" + owner.map(UUID::toString).orElse("legacy-unclaimed")
                + "#" + operationId.gameTime() + ":" + operationId.sequence();
    }
}
