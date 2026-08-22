package com.swear.autostorage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class FluxRecipePresentationContract {
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            AutoStorage.MODID, "flux_station/redstone_to_flux_dust");
    private static final ResourceLocation STATION_ID = ResourceLocation.fromNamespaceAndPath(
            AutoStorage.MODID, "flux_station");
    private static final ResourceLocation REDSTONE_ID = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "redstone");
    private static final ResourceLocation FLUX_DUST_ID = ResourceLocation.fromNamespaceAndPath(
            "fluxnetworks", "flux_dust");

    private FluxRecipePresentationContract() {
    }

    public static boolean matches(RecipePresentation presentation) {
        if (presentation == null || presentation.isEmpty()
                || !RECIPE_ID.equals(presentation.recipeId())
                || presentation.kind() != RecipePresentationKind.WORLD_STATION
                || !STATION_ID.equals(presentation.stationDescriptorId())
                || presentation.width() != 1
                || presentation.height() != 1
                || presentation.shapeless()) {
            return false;
        }
        List<ItemStack> inputs = presentation.inputs();
        if (inputs.size() != RecipePresentation.MAX_INPUTS
                || !exactItem(inputs.getFirst(), REDSTONE_ID)
                || !exactItem(presentation.output(), FLUX_DUST_ID)
                || !exactItem(presentation.station(), STATION_ID)
                || presentation.stationVariants().size() != 1
                || !exactItem(presentation.stationVariants().getFirst(), STATION_ID)) {
            return false;
        }
        for (int index = 1; index < inputs.size(); index++) {
            if (!inputs.get(index).isEmpty()) return false;
        }
        List<RecipePresentation.Resource> resources = presentation.resources();
        return resources.size() == 1
                && resources.getFirst().kind() == RecipePresentation.ResourceKind.ITEM
                && resources.getFirst().required() == 1
                && exactItem(resources.getFirst().stack(), REDSTONE_ID);
    }

    private static boolean exactItem(ItemStack stack, ResourceLocation id) {
        Item expected = BuiltInRegistries.ITEM.get(id);
        return expected != Items.AIR
                && stack.getCount() == 1
                && ItemStack.isSameItemSameComponents(stack, new ItemStack(expected));
    }
}
