package com.swear.autostorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record MachineDescriptorStatePacket(
        int containerId,
        List<State> states
) implements CustomPacketPayload {
    public static final Type<MachineDescriptorStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "machine_descriptor_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineDescriptorStatePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MachineDescriptorStatePacket decode(RegistryFriendlyByteBuf buf) {
                    int containerId = buf.readVarInt();
                    int count = buf.readVarInt();
                    if (count < 0 || count > MachineDescriptorApi.MAX_DESCRIPTORS) {
                        throw new IllegalArgumentException("Invalid descriptor state count: " + count);
                    }
                    List<State> states = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        states.add(new State(
                                buf.readResourceLocation(),
                                buf.readVarLong(),
                                buf.readBoolean(),
                                buf.readBoolean()));
                    }
                    return new MachineDescriptorStatePacket(containerId, List.copyOf(states));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, MachineDescriptorStatePacket packet) {
                    if (packet.states().size() > MachineDescriptorApi.MAX_DESCRIPTORS) {
                        throw new IllegalArgumentException("Too many descriptor states: " + packet.states().size());
                    }
                    buf.writeVarInt(packet.containerId());
                    buf.writeVarInt(packet.states().size());
                    for (State state : packet.states()) {
                        buf.writeResourceLocation(state.descriptorId());
                        buf.writeVarLong(state.amount());
                        buf.writeBoolean(state.infinite());
                        buf.writeBoolean(state.worldAvailable());
                    }
                }
            };

    public MachineDescriptorStatePacket {
        states = List.copyOf(states);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record State(
            ResourceLocation descriptorId,
            long amount,
            boolean infinite,
            boolean worldAvailable
    ) {
        public State {
            if (amount < 0 || infinite && amount != 0) {
                throw new IllegalArgumentException("Invalid descriptor state for " + descriptorId);
            }
        }
    }
}
