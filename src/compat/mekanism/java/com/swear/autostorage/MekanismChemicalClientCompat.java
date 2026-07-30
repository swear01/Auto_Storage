package com.swear.autostorage;

import dev.emi.emi.api.stack.EmiIngredient;
import mekanism.client.recipe_viewer.emi.ChemicalEmiStack;
import net.minecraft.client.gui.GuiGraphics;

public final class MekanismChemicalClientCompat {
    private MekanismChemicalClientCompat() {
    }

    public static void register() {
        TerminalResourceRendererApi.register(
                StorageResourceKindApi.CHEMICAL_KIND,
                GuiGraphics.class,
                MekanismChemicalClientCompat::render);
    }

    private static boolean render(
            GuiGraphics graphics,
            StorageResourceKey key,
            long amount,
            int x,
            int y,
            float partialTick
    ) {
        var chemical = MekanismChemicalCompat.stack(key, Math.max(1, amount));
        if (chemical.isEmpty()) return false;
        new ChemicalEmiStack(chemical).render(
                graphics, x, y, partialTick, EmiIngredient.RENDER_ICON);
        return true;
    }
}
