package com.swear.autostorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerminalScrollPacket(int containerId, int offset) implements CustomPacketPayload {

    public static final Type<TerminalScrollPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "terminal_scroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalScrollPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TerminalScrollPacket::containerId,
                    ByteBufCodecs.VAR_INT, TerminalScrollPacket::offset,
                    TerminalScrollPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
