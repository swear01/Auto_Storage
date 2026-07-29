package com.swearprom.magicstorage.magic_storage;

import dev.emi.emi.api.stack.EmiIngredient;
import mekanism.client.recipe_viewer.emi.ChemicalEmiStack;
import net.minecraft.client.gui.GuiGraphics;

final class MekanismChemicalClientCompat {
    private MekanismChemicalClientCompat() {
    }

    static boolean render(
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
