package com.swear.autostorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerminalRepositoryResyncPacket(int containerId) implements CustomPacketPayload {
    public static final Type<TerminalRepositoryResyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "terminal_repository_resync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalRepositoryResyncPacket>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    TerminalRepositoryResyncPacket::containerId,
                    TerminalRepositoryResyncPacket::new);

    public TerminalRepositoryResyncPacket {
        if (containerId < 0) throw new IllegalArgumentException("Invalid terminal container ID");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
