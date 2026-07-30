package com.swear.autostorage.compat;

import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.CraftingTerminalPage;
import com.swear.autostorage.CraftingRecipeSelectionPacket;
import com.swear.autostorage.CraftingTerminalScreen;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.StorageTerminalMenu;
import com.swear.autostorage.TerminalDisplayStack;
import com.swear.autostorage.TerminalResourceDisplay;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

@EmiEntrypoint
public class AutoStorageEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        EmiRecipeDiagramBootstrap.markRegistryReady();
        registry.addRecipeHandler(AutoStorage.CRAFTING_TERMINAL_MENU.get(), new CraftingTerminalHandler());
        registry.addExclusionArea(CraftingTerminalScreen.class, (screen, consumer) -> {
            for (var area : screen.getEmiExclusionAreas()) {
                consumer.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()));
            }
        });
    }

    static class CraftingTerminalHandler implements StandardRecipeHandler<CraftingTerminalMenu> {
        private static final int MAX_CRAFT_AMOUNT = 64;
        private static final int PLAYER_INVENTORY_SLOTS = 36;

        @Override
        public List<Slot> getInputSources(CraftingTerminalMenu handler) {
            if (!handler.getPage().isItemPage()) return List.of();
            int firstSlot = handler.getPage() == CraftingTerminalPage.STORAGE
                    ? 0 : StorageTerminalMenu.DISPLAY_SLOTS;
            int lastSlot = handler.isUsePlayerInventory()
                    ? StorageTerminalMenu.DISPLAY_SLOTS + PLAYER_INVENTORY_SLOTS
                    : StorageTerminalMenu.DISPLAY_SLOTS;
            return handler.slots.subList(firstSlot, Math.min(handler.slots.size(), lastSlot)).stream()
                    .filter(slot -> !TerminalResourceDisplay.isTyped(slot.getItem()))
                    .toList();
        }

        @Override
        public EmiPlayerInventory getInventory(AbstractContainerScreen<CraftingTerminalMenu> screen) {
            return new EmiPlayerInventory(getInputSources(screen.getMenu()).stream()
                    .map(Slot::getItem)
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> EmiStack.of(
                            TerminalDisplayStack.strip(stack), TerminalDisplayStack.amount(stack)))
                    .toList());
        }

        @Override
        public List<Slot> getCraftingSlots(CraftingTerminalMenu handler) {
            if (handler.getPage() != CraftingTerminalPage.STORAGE) return List.of();
            return handler.slots.subList(0, StorageTerminalMenu.DISPLAY_SLOTS);
        }

        @Override
        public List<Slot> getCraftingSlots(EmiRecipe recipe, CraftingTerminalMenu handler) {
            if (!handler.getPage().isItemPage()) return List.of();
            return getInputSources(handler);
        }

        @Override
        public boolean supportsRecipe(EmiRecipe recipe) {
            RecipeHolder<?> backingRecipe = recipe.getBackingRecipe();
            RecipeHolder<?> currentHolder = backingRecipe == null || Minecraft.getInstance().level == null
                    ? null
                    : Minecraft.getInstance().level.getRecipeManager()
                    .byKey(backingRecipe.id()).orElse(null);
            return backingRecipe != null
                    && currentHolder == backingRecipe
                    && CraftingTerminalMenu.supportsRecipeHolder(currentHolder);
        }

        @Override
        public boolean canCraft(EmiRecipe recipe, EmiCraftContext<CraftingTerminalMenu> context) {
            CraftingTerminalMenu handler = context.getScreenHandler();
            return handler.getPage().isItemPage()
                    && supportsRecipe(recipe);
        }

        @Override
        public boolean craft(EmiRecipe recipe, EmiCraftContext<CraftingTerminalMenu> context) {
            CraftingTerminalMenu menu = context.getScreenHandler();
            if (!menu.getPage().isItemPage() || !supportsRecipe(recipe)) return false;
            RecipeHolder<?> backingRecipe = recipe.getBackingRecipe();
            Minecraft minecraft = Minecraft.getInstance();
            if (backingRecipe == null || minecraft.player == null || minecraft.getConnection() == null) return false;
            int amount = Math.max(1, Math.min(context.getAmount(), MAX_CRAFT_AMOUNT));
            CraftingDestination destination = switch (context.getDestination()) {
                case NONE -> CraftingDestination.NONE;
                case CURSOR -> CraftingDestination.CURSOR;
                case INVENTORY -> CraftingDestination.INVENTORY;
            };
            minecraft.getConnection().send(new CraftingRecipeSelectionPacket(
                    menu.containerId, backingRecipe.id(), amount, destination));
            return true;
        }
    }
}
