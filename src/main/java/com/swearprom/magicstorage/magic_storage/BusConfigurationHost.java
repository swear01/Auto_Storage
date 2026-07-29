package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.function.UnaryOperator;

interface BusConfigurationHost {
    BusKind busKind();

    BusConfiguration getBusConfiguration();

    BlockPos getBlockPos();

    Level getLevel();

    void setBusConfiguration(BusConfiguration configuration);

    default boolean updateBusConfiguration(
            Player player,
            long expectedRevision,
            UnaryOperator<BusConfiguration> editor
    ) {
        return BusConfigurationAccess.update(this, player, expectedRevision, editor);
    }
}
