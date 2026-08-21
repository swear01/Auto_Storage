package com.swear.autostorage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record TerminalRepositoryUpdatePacket(
        int containerId,
        long revision,
        boolean full,
        int chunkIndex,
        int chunkCount,
        List<TerminalRepositoryEntry> entries
) implements CustomPacketPayload {
    static final int MAX_ENTRIES_PER_PACKET = 512;

    public static final Type<TerminalRepositoryUpdatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "terminal_repository_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalRepositoryUpdatePacket>
            STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TerminalRepositoryUpdatePacket decode(RegistryFriendlyByteBuf buf) {
            int containerId = buf.readVarInt();
            long revision = buf.readVarLong();
            boolean full = buf.readBoolean();
            int chunkIndex = buf.readVarInt();
            int chunkCount = buf.readVarInt();
            if (chunkCount <= 0 || chunkIndex < 0 || chunkIndex >= chunkCount) {
                throw new IllegalArgumentException("Invalid terminal repository chunk");
            }
            int entryCount = buf.readVarInt();
            if (entryCount < 0) {
                throw new IllegalArgumentException("Negative terminal repository entry count");
            }
            if (entryCount > MAX_ENTRIES_PER_PACKET) {
                throw new IllegalArgumentException("Too many terminal repository entries");
            }
            List<TerminalRepositoryEntry> entries = new ArrayList<>(entryCount);
            for (int index = 0; index < entryCount; index++) {
                long serial = buf.readVarLong();
                StorageResourceKey key = null;
                if (buf.readBoolean()) {
                    ResourceLocation kind = buf.readResourceLocation();
                    ResourceLocation resource = buf.readResourceLocation();
                    CompoundTag variant = buf.readNbt();
                    if (variant == null) {
                        throw new IllegalArgumentException("Missing terminal resource variant");
                    }
                    key = StorageResourceKey.of(kind, resource, variant);
                }
                ItemStack displayStack = ItemStack.STREAM_CODEC.decode(buf);
                entries.add(new TerminalRepositoryEntry(serial, key, displayStack));
            }
            return new TerminalRepositoryUpdatePacket(
                    containerId, revision, full, chunkIndex, chunkCount, entries);
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buf,
                TerminalRepositoryUpdatePacket packet
        ) {
            packet.write(buf);
        }
    };

    public TerminalRepositoryUpdatePacket {
        if (containerId < 0 || revision < 0) {
            throw new IllegalArgumentException("Invalid terminal repository identity");
        }
        if (chunkCount <= 0 || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("Invalid terminal repository chunk");
        }
        if (entries == null) {
            throw new IllegalArgumentException("Missing terminal repository entries");
        }
        if (entries.size() > MAX_ENTRIES_PER_PACKET) {
            throw new IllegalArgumentException("Too many terminal repository entries");
        }
        entries = List.copyOf(entries);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarLong(revision);
        buf.writeBoolean(full);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(entries.size());
        for (TerminalRepositoryEntry entry : entries) {
            buf.writeVarLong(entry.serial());
            StorageResourceKey key = entry.key();
            buf.writeBoolean(key != null);
            if (key != null) {
                buf.writeResourceLocation(key.kindId());
                buf.writeResourceLocation(key.resourceId());
                buf.writeNbt(key.variantData());
            }
            ItemStack.STREAM_CODEC.encode(buf, entry.displayStack());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
