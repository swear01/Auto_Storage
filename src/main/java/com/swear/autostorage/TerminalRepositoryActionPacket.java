package com.swear.autostorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerminalRepositoryActionPacket(
        int containerId,
        long serial,
        int button,
        boolean quickMove
) implements CustomPacketPayload {
    public static final Type<TerminalRepositoryActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "terminal_repository_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalRepositoryActionPacket>
            STREAM_CODEC = StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    TerminalRepositoryActionPacket::containerId,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_LONG,
                    TerminalRepositoryActionPacket::serial,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    TerminalRepositoryActionPacket::button,
                    net.minecraft.network.codec.ByteBufCodecs.BOOL,
                    TerminalRepositoryActionPacket::quickMove,
                    TerminalRepositoryActionPacket::new);

    public TerminalRepositoryActionPacket {
        if (containerId < 0 || serial <= 0 || button < 0 || button > 1) {
            throw new IllegalArgumentException("Invalid terminal repository action");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
