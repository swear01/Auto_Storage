package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.UUID;

final class BusRecoveryDrops {
    private static final String TAG_RECOVERY_DROP_ID = "magicStorageRecoveryDropId";

    private BusRecoveryDrops() {
    }

    static void saveRecoveryDropId(net.minecraft.nbt.CompoundTag tag, UUID recoveryDropId) {
        tag.putUUID(TAG_RECOVERY_DROP_ID, recoveryDropId);
    }

    static void protectEscrowDrop(BlockDropsEvent event) {
        if (event.getLevel().isClientSide()) return;
        var iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemEntity entity = iterator.next();
            ItemStack stack = entity.getItem();
            if (!hasEscrowPayload(stack)) continue;
            spawnIfMissing(event.getLevel(), event.getPos(), stack);
            iterator.remove();
        }
    }

    static void spawnIfMissing(Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide() || stack.isEmpty()) return;
        if (hasMatchingCurrentTickDrop(level, pos, stack)) return;
        spawnDirectOrThrow(level, pos, stack);
    }

    static void spawnDirectOrThrow(Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide() || stack.isEmpty()) return;
        ItemEntity entity = new ItemEntity(
                level,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                stack.copy());
        entity.setDefaultPickUpDelay();
        if (!level.addFreshEntity(entity)) {
            throw new IllegalStateException("Failed to spawn lossless Bus recovery drop at " + pos);
        }
    }

    private static boolean hasMatchingCurrentTickDrop(
            Level level,
            BlockPos pos,
            ItemStack expected
    ) {
        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(pos))) {
            if (entity.tickCount > 1) continue;
            if (matchesRecoveryDrop(entity.getItem(), expected)) return true;
        }
        return false;
    }

    static boolean containsMatchingEscrowDrop(
            Iterable<ItemEntity> entities,
            ItemStack expected
    ) {
        for (ItemEntity entity : entities) {
            if (matchesRecoveryDrop(entity.getItem(), expected)) return true;
        }
        return false;
    }

    private static boolean matchesRecoveryDrop(ItemStack candidate, ItemStack expected) {
        if (!candidate.is(expected.getItem())) return false;
        UUID expectedId = recoveryDropId(expected);
        return expectedId != null && expectedId.equals(recoveryDropId(candidate));
    }

    private static boolean hasEscrowPayload(ItemStack stack) {
        if (!stack.is(MagicStorage.IMPORT_BUS_ITEM.get())
                && !stack.is(MagicStorage.EXPORT_BUS_ITEM.get())) {
            return false;
        }
        var data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        return data != null && data.copyTag().contains("pendingResources");
    }

    private static UUID recoveryDropId(ItemStack stack) {
        var candidateData = stack.get(
                net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (candidateData == null) return null;
        var tag = candidateData.copyTag();
        return tag.hasUUID(TAG_RECOVERY_DROP_ID) ? tag.getUUID(TAG_RECOVERY_DROP_ID) : null;
    }
}
