package com.swear.autostorage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

public final class TerminalResourceDisplay {
    private static final String RESOURCE_KEY = "auto_storage:terminal_resource";
    private static final String KIND_KEY = "kind";
    private static final String ID_KEY = "id";
    private static final String VARIANT_KEY = "variant";

    private TerminalResourceDisplay() {
    }

    static ItemStack create(ItemStack representative, StorageResourceKey key, long amount) {
        ItemStack display = TerminalDisplayStack.create(representative, amount);
        CustomData.update(DataComponents.CUSTOM_DATA, display, root -> {
            CompoundTag resource = new CompoundTag();
            resource.putString(KIND_KEY, key.kindId().toString());
            resource.putString(ID_KEY, key.resourceId().toString());
            resource.put(VARIANT_KEY, key.variantData());
            root.put(RESOURCE_KEY, resource);
        });
        return display;
    }

    public static boolean isTyped(ItemStack stack) {
        return key(stack).isPresent();
    }

    public static Optional<StorageResourceKey> key(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        Tag raw = data.copyTag().get(RESOURCE_KEY);
        if (!(raw instanceof CompoundTag resource)
                || !resource.contains(KIND_KEY, Tag.TAG_STRING)
                || !resource.contains(ID_KEY, Tag.TAG_STRING)
                || !resource.contains(VARIANT_KEY, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        ResourceLocation kind = ResourceLocation.tryParse(resource.getString(KIND_KEY));
        ResourceLocation id = ResourceLocation.tryParse(resource.getString(ID_KEY));
        if (kind == null || id == null) return Optional.empty();
        return Optional.of(StorageResourceKey.of(kind, id, resource.getCompound(VARIANT_KEY)));
    }
}
