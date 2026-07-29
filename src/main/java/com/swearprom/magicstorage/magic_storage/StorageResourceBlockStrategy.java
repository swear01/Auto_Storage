package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Optional;

public interface StorageResourceBlockStrategy {
    ResourceLocation kindId();

    Optional<StorageResourceHandler> find(Level level, BlockPos pos, Direction side);
}
