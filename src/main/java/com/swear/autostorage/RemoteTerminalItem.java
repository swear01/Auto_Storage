package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RemoteTerminalItem extends Item {
    private static final String TAG_CORE_X = "coreX";
    private static final String TAG_CORE_Y = "coreY";
    private static final String TAG_CORE_Z = "coreZ";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_CORE_ID = "coreId";

    public RemoteTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (!isBound(stack)) {
                player.displayClientMessage(Component.translatable("msg.auto_storage.remote_unbound"), true);
                return InteractionResultHolder.fail(stack);
            }
            BlockPos corePos = getBoundCorePos(stack);
            Optional<ResourceKey<Level>> boundDimension = getBoundDimension(stack);
            if (boundDimension.isEmpty()) {
                player.displayClientMessage(Component.translatable("msg.auto_storage.remote_unbound"), true);
                return InteractionResultHolder.fail(stack);
            }
            ResourceKey<Level> dimension = boundDimension.get();

            if (!player.level().dimension().equals(dimension)) {
                player.displayClientMessage(Component.translatable("msg.auto_storage.remote_wrong_dimension"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!level.hasChunkAt(corePos)) {
                player.displayClientMessage(Component.translatable("msg.auto_storage.remote_core_unloaded"), true);
                return InteractionResultHolder.fail(stack);
            }

            StorageCoreBlockEntity core = level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity c ? c : null;
            if (core == null) {
                player.displayClientMessage(Component.translatable("msg.auto_storage.remote_stale"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!core.isStorageAvailable()) {
                player.displayClientMessage(
                        Component.translatable("msg.auto_storage.core_storage_unavailable"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!core.getNetworkId().equals(getBoundCoreId(stack))) {
                player.displayClientMessage(Component.translatable("msg.auto_storage.remote_stale"), true);
                return InteractionResultHolder.fail(stack);
            }

            player.openMenu(new SimpleMenuProvider(
                    (containerId, inv, p) -> new CraftingTerminalMenu(containerId, inv, core, core.getBlockPos(), true),
                    Component.translatable("container.auto_storage.remote_terminal")
            ), buf -> {
                buf.writeBlockPos(core.getBlockPos());
                buf.writeBlockPos(core.getBlockPos());
                buf.writeBoolean(true);
                MachineEnergyTable.writeSnapshot(buf, MachineEnergyTable.entries());
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (player != null && player.isShiftKeyDown()) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof IStorageNetworkBlock netBlock && netBlock.isStorageCore()) {
                if (!level.isClientSide()) {
                    if (!(level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core)) {
                        return InteractionResult.FAIL;
                    }
                    if (!core.isStorageAvailable()) {
                        player.displayClientMessage(
                                Component.translatable("msg.auto_storage.core_storage_unavailable"), true);
                        return InteractionResult.FAIL;
                    }
                    bindToCore(stack, pos, level.dimension(), core.getNetworkId());
                    player.displayClientMessage(Component.translatable("msg.auto_storage.remote_bound"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return InteractionResult.PASS;
    }

    private static boolean isBound(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        CompoundTag tag = customData.copyTag();
        return tag.contains(TAG_CORE_X) && tag.contains(TAG_CORE_Y) && tag.contains(TAG_CORE_Z)
                && tag.contains(TAG_DIMENSION) && tag.hasUUID(TAG_CORE_ID)
                && ResourceLocation.tryParse(tag.getString(TAG_DIMENSION)) != null;
    }

    private static BlockPos getBoundCorePos(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return new BlockPos(tag.getInt(TAG_CORE_X), tag.getInt(TAG_CORE_Y), tag.getInt(TAG_CORE_Z));
    }

    private static Optional<ResourceKey<Level>> getBoundDimension(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        return id == null ? Optional.empty() : Optional.of(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static UUID getBoundCoreId(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getUUID(TAG_CORE_ID);
    }

    private static void bindToCore(ItemStack stack, BlockPos pos, ResourceKey<Level> dimension, UUID coreId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(TAG_CORE_X, pos.getX());
            tag.putInt(TAG_CORE_Y, pos.getY());
            tag.putInt(TAG_CORE_Z, pos.getZ());
            tag.putString(TAG_DIMENSION, dimension.location().toString());
            tag.putUUID(TAG_CORE_ID, coreId);
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isBound(stack)) {
            tooltip.add(Component.translatable("tooltip.auto_storage.remote_bound",
                    getBoundCorePos(stack).toShortString()));
        } else {
            tooltip.add(Component.translatable("tooltip.auto_storage.remote_unbound"));
        }
    }
}
