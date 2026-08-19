package com.swear.autostorage.compat.fluxnetworks;

import com.swear.autostorage.SyntheticRecipeCatalogs;
import com.swear.autostorage.WorldStationConversionRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import sonar.fluxnetworks.FluxConfig;

import java.util.List;

final class FluxConversionCatalog implements SyntheticRecipeCatalogs.Catalog {
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            "auto_storage", "flux_station/redstone_to_flux_dust");

    private final ResourceLocation stationDescriptorId;
    private final ResourceLocation outputId;
    private volatile RecipeHolder<?> cachedHolder;

    FluxConversionCatalog(ResourceLocation stationDescriptorId, ResourceLocation outputId) {
        this.stationDescriptorId = stationDescriptorId;
        this.outputId = outputId;
    }

    @Override
    public List<RecipeHolder<?>> recipes(Level level) {
        if (!FluxConfig.enableFluxRecipe) return List.of();
        RecipeHolder<?> holder = holder();
        return holder == null ? List.of() : List.of(holder);
    }

    @Override
    public RecipeHolder<?> byId(Level level, ResourceLocation id) {
        return FluxConfig.enableFluxRecipe && RECIPE_ID.equals(id) ? holder() : null;
    }

    private RecipeHolder<?> holder() {
        RecipeHolder<?> current = cachedHolder;
        if (current != null) return current;
        synchronized (this) {
            current = cachedHolder;
            if (current != null) return current;
            Item fluxDust = BuiltInRegistries.ITEM.get(outputId);
            if (fluxDust == Items.AIR) return null;
            current = new RecipeHolder<>(
                    RECIPE_ID,
                    new WorldStationConversionRecipe(
                            Ingredient.of(Items.REDSTONE),
                            new ItemStack(fluxDust),
                            stationDescriptorId));
            cachedHolder = current;
            return current;
        }
    }
}
