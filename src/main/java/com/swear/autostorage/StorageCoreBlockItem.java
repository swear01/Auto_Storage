package com.swear.autostorage;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class StorageCoreBlockItem extends BlockItem {
    static final String RECOVERY_ID = "autoStorageCoreRecoveryId";
    private static final String STORED_TYPE_COUNT = "autoStorageStoredTypeCount";
    private static final String STORED_ITEM_COUNT = "autoStorageStoredItemCount";

    public StorageCoreBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    static ItemStack createRecoveryStack(CoreStorageRepository.RecoverySummary summary) {
        ItemStack stack = new ItemStack(AutoStorage.STORAGE_CORE_ITEM.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID(RECOVERY_ID, summary.id());
            tag.putInt(STORED_TYPE_COUNT, summary.typeCount());
            tag.putLong(STORED_ITEM_COUNT, summary.itemCount());
        });
        return stack;
    }

    public static Optional<UUID> getRecoveryId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(RECOVERY_ID) ? Optional.of(tag.getUUID(RECOVERY_ID)) : Optional.empty();
    }

    public static int getStoredTypeCount(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return Math.max(0, data.copyTag().getInt(STORED_TYPE_COUNT));
    }

    public static long getStoredItemCount(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return Math.max(0, data.copyTag().getLong(STORED_ITEM_COUNT));
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Optional<UUID> recoveryId = getRecoveryId(context.getItemInHand());
        if (recoveryId.isPresent() && context.getLevel() instanceof ServerLevel serverLevel
                && !CoreStorageRepository.get(serverLevel).canClaim(recoveryId.get())) {
            Player player = context.getPlayer();
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("msg.auto_storage.core_recovery_consumed"), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (getRecoveryId(stack).isEmpty()) return;
        tooltip.add(Component.translatable(
                "tooltip.auto_storage.core_packed",
                getStoredTypeCount(stack),
                getStoredItemCount(stack)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                "tooltip.auto_storage.core_recovery_token").withStyle(ChatFormatting.GRAY));
    }
}
