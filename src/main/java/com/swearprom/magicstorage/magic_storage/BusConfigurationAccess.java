package com.swearprom.magicstorage.magic_storage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

final class BusConfigurationAccess {
    private static final double MAX_DISTANCE_SQUARED = 64.0;

    private BusConfigurationAccess() {
    }

    static boolean canConfigure(BusConfigurationHost host, Player player) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(player, "player");
        if (player.isSpectator()
                || !player.mayBuild()
                || host.getLevel() == null
                || host.getLevel().isClientSide()
                || player.level() != host.getLevel()
                || !host.getLevel().hasChunkAt(host.getBlockPos())
                || host.getLevel().getBlockEntity(host.getBlockPos()) != host
                || player.distanceToSqr(
                host.getBlockPos().getX() + 0.5,
                host.getBlockPos().getY() + 0.5,
                host.getBlockPos().getZ() + 0.5) > MAX_DISTANCE_SQUARED) {
            return false;
        }
        Optional<java.util.UUID> owner = host.getBusConfiguration().owner();
        return owner.isEmpty()
                || owner.get().equals(player.getUUID())
                || player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2);
    }

    static boolean update(
            BusConfigurationHost host,
            Player player,
            long expectedRevision,
            UnaryOperator<BusConfiguration> editor
    ) {
        Objects.requireNonNull(editor, "editor");
        if (!canConfigure(host, player)) return false;
        BusConfiguration current = host.getBusConfiguration();
        if (!current.supported()
                || current.configRevision() != expectedRevision
                || current.configRevision() == Long.MAX_VALUE) return false;
        boolean operator = player instanceof ServerPlayer serverPlayer
                && serverPlayer.hasPermissions(2);
        BusConfiguration owned = current.owner().isEmpty() && !operator
                ? current.assignInitialOwner(player.getUUID())
                : current;
        BusConfiguration edited = Objects.requireNonNull(editor.apply(owned), "edited configuration");
        if (!edited.supported()
                || !edited.owner().equals(owned.owner())
                || edited.configRevision() != current.nextRevision()) {
            return false;
        }
        host.setBusConfiguration(edited);
        return true;
    }

    static boolean authorizeMutation(BusConfigurationHost host, Player player) {
        if (!canConfigure(host, player)) return false;
        BusConfiguration current = host.getBusConfiguration();
        if (!current.supported()) return false;
        boolean operator = player instanceof ServerPlayer serverPlayer
                && serverPlayer.hasPermissions(2);
        if (current.owner().isPresent() || operator) return true;
        if (current.configRevision() == Long.MAX_VALUE) return false;
        host.setBusConfiguration(BusConfiguration.current(
                current.mode(),
                current.sideMask(),
                current.unsidedAccess(),
                current.automationEnabled(),
                current.filterMode(),
                current.filterRules(),
                Optional.of(player.getUUID()),
                current.nextRevision()));
        return true;
    }
}
