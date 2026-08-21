package com.swear.autostorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerminalRepositoryContainerTransferPacket(
        int containerId,
        int stateId,
        long serial,
        TerminalResourceView expectedView,
        TerminalContainerTransferDirection direction
) implements CustomPacketPayload {
    public static final Type<TerminalRepositoryContainerTransferPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "terminal_repository_container_transfer"));

    public static final StreamCodec<RegistryFriendlyByteBuf,
            TerminalRepositoryContainerTransferPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TerminalRepositoryContainerTransferPacket decode(RegistryFriendlyByteBuf buf) {
            int containerId = buf.readVarInt();
            int stateId = buf.readVarInt();
            long serial = buf.readVarLong();
            TerminalResourceView view = TerminalResourceView.requireWireId(buf.readVarInt());
            TerminalContainerTransferDirection direction =
                    TerminalContainerTransferDirection.byWireId(buf.readVarInt());
            if (direction == null) {
                throw new IllegalArgumentException("Unknown terminal container transfer direction");
            }
            return new TerminalRepositoryContainerTransferPacket(
                    containerId, stateId, serial, view, direction);
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buf,
                TerminalRepositoryContainerTransferPacket packet
        ) {
            buf.writeVarInt(packet.containerId());
            buf.writeVarInt(packet.stateId());
            buf.writeVarLong(packet.serial());
            buf.writeVarInt(packet.expectedView().wireId());
            buf.writeVarInt(packet.direction().wireId());
        }
    };

    public TerminalRepositoryContainerTransferPacket {
        if (containerId < 0 || stateId < 0 || serial <= 0) {
            throw new IllegalArgumentException("Invalid terminal repository transfer");
        }
        java.util.Objects.requireNonNull(expectedView, "expectedView");
        java.util.Objects.requireNonNull(direction, "direction");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
