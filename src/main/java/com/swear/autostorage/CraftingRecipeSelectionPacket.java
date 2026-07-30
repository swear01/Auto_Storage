package com.swear.autostorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CraftingRecipeSelectionPacket(
        int containerId,
        ResourceLocation recipeId,
        int amount,
        CraftingDestination destination
) implements CustomPacketPayload {

    public static final Type<CraftingRecipeSelectionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "crafting_recipe_selection"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, CraftingDestination> DESTINATION_CODEC =
            ByteBufCodecs.idMapper(CraftingDestination::byId, CraftingDestination::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingRecipeSelectionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CraftingRecipeSelectionPacket::containerId,
                    ResourceLocation.STREAM_CODEC, CraftingRecipeSelectionPacket::recipeId,
                    ByteBufCodecs.VAR_INT, CraftingRecipeSelectionPacket::amount,
                    DESTINATION_CODEC, CraftingRecipeSelectionPacket::destination,
                    CraftingRecipeSelectionPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
