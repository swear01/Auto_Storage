package com.swear.autostorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerminalSettingsPacket(
        int containerId,
        int visibleRows,
        TerminalPreferences preferences
) implements CustomPacketPayload {

    public static final Type<TerminalSettingsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "terminal_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSettingsPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TerminalSettingsPacket decode(RegistryFriendlyByteBuf buf) {
                    return read(buf);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, TerminalSettingsPacket packet) {
                    packet.write(buf);
                }
            };

    void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(visibleRows);
        preferences.write(buf);
    }

    static TerminalSettingsPacket read(FriendlyByteBuf buf) {
        return new TerminalSettingsPacket(
                buf.readVarInt(),
                buf.readVarInt(),
                TerminalPreferences.read(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
