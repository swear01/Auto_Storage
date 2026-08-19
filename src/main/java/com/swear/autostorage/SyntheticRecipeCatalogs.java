package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SyntheticRecipeCatalogs {
    public interface Catalog {
        List<RecipeHolder<?>> recipes(Level level);

        RecipeHolder<?> byId(Level level, ResourceLocation id);
    }

    private static final List<Catalog> CATALOGS = new ArrayList<>();

    private SyntheticRecipeCatalogs() {
    }

    public static void register(Catalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        CATALOGS.add(catalog);
    }

    public static List<RecipeHolder<?>> recipes(
            Level level
    ) {
        if (CATALOGS.isEmpty()) return List.of();
        List<RecipeHolder<?>> holders = new ArrayList<>();
        for (Catalog catalog : CATALOGS) {
            holders.addAll(catalog.recipes(level));
        }
        return holders;
    }

    public static RecipeHolder<?> byId(
            Level level,
            ResourceLocation id
    ) {
        for (Catalog catalog : CATALOGS) {
            RecipeHolder<?> holder = catalog.byId(level, id);
            if (holder != null) return holder;
        }
        return null;
    }
}
