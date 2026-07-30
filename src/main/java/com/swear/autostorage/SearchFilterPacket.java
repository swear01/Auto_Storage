package com.swear.autostorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.ByteBuf;

public record SearchFilterPacket(int containerId, String filter) implements CustomPacketPayload {

    public static final int MAX_FILTER_LENGTH = 64;

    public static final Type<SearchFilterPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "search_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SearchFilterPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SearchFilterPacket::containerId,
                    ByteBufCodecs.stringUtf8(MAX_FILTER_LENGTH), SearchFilterPacket::filter,
                    SearchFilterPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
