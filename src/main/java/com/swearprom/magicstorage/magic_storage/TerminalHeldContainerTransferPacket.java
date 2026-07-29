package com.swearprom.magicstorage.magic_storage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerminalHeldContainerTransferPacket(
        int containerId,
        int stateId,
        int slotIndex,
        TerminalResourceView expectedView,
        TerminalContainerTransferDirection direction
) implements CustomPacketPayload {
    public static final Type<TerminalHeldContainerTransferPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    MagicStorage.MODID, "terminal_held_container_transfer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalHeldContainerTransferPacket>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public TerminalHeldContainerTransferPacket decode(RegistryFriendlyByteBuf buf) {
                    int containerId = buf.readVarInt();
                    int stateId = buf.readVarInt();
                    int slotIndex = buf.readVarInt();
                    TerminalResourceView view = TerminalResourceView.requireById(buf.readVarInt());
                    TerminalContainerTransferDirection direction =
                            TerminalContainerTransferDirection.byId(buf.readVarInt());
                    if (direction == null) {
                        throw new IllegalArgumentException("Unknown terminal container transfer direction");
                    }
                    return new TerminalHeldContainerTransferPacket(
                            containerId, stateId, slotIndex, view, direction);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buf,
                        TerminalHeldContainerTransferPacket packet
                ) {
                    buf.writeVarInt(packet.containerId());
                    buf.writeVarInt(packet.stateId());
                    buf.writeVarInt(packet.slotIndex());
                    buf.writeVarInt(packet.expectedView().ordinal());
                    buf.writeVarInt(packet.direction().ordinal());
                }
            };

    public TerminalHeldContainerTransferPacket {
        if (containerId < 0 || stateId < 0) {
            throw new IllegalArgumentException("Terminal container and state IDs must be non-negative");
        }
        java.util.Objects.requireNonNull(expectedView, "expectedView");
        java.util.Objects.requireNonNull(direction, "direction");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
