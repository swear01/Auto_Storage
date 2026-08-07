package com.swear.autostorage.compat;

import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingRecipeSelectionPacket;
import com.swear.autostorage.CraftingTerminalMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

@JeiPlugin
public final class AutoStorageJeiPlugin implements IModPlugin {
    private static final int MAX_CRAFT_AMOUNT = 64;
    private static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiRecipeDiagramBootstrap.markRuntimeAvailable(jeiRuntime);
        JeiDiagramPerformanceProbe.scheduleOnce(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiRecipeDiagramBootstrap.markRuntimeUnavailable();
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(
                new CraftingTerminalTransferHandler(registration.getTransferHelper()));
    }

    private static final class CraftingTerminalTransferHandler
            implements IUniversalRecipeTransferHandler<CraftingTerminalMenu> {
        private final IRecipeTransferHandlerHelper transferHelper;

        private CraftingTerminalTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
            this.transferHelper = Objects.requireNonNull(transferHelper, "transferHelper");
        }

        @Override
        public Class<? extends CraftingTerminalMenu> getContainerClass() {
            return CraftingTerminalMenu.class;
        }

        @Override
        public Optional<MenuType<CraftingTerminalMenu>> getMenuType() {
            return Optional.of(AutoStorage.CRAFTING_TERMINAL_MENU.get());
        }

        @Override
        @Nullable
        public IRecipeTransferError transferRecipe(
                CraftingTerminalMenu container,
                Object recipe,
                IRecipeSlotsView recipeSlots,
                Player player,
                boolean maxTransfer,
                boolean doTransfer
        ) {
            if (!container.getPage().isItemPage()) {
                return transferHelper.createUserErrorWithTooltip(
                        Component.translatable("gui.auto_storage.jei.transfer.wrong_page"));
            }
            RecipeHolder<?> holder = resolveHolder(recipe);
            if (holder == null || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                return transferHelper.createUserErrorWithTooltip(
                        Component.translatable("gui.auto_storage.jei.transfer.unsupported"));
            }
            if (!doTransfer) {
                return null;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.getConnection() == null) {
                return transferHelper.createInternalError();
            }
            int amount = maxTransfer ? MAX_CRAFT_AMOUNT : 1;
            minecraft.getConnection().send(new CraftingRecipeSelectionPacket(
                    container.containerId,
                    holder.id(),
                    amount,
                    CraftingDestination.INVENTORY));
            return null;
        }

        @Nullable
        private static RecipeHolder<?> resolveHolder(Object recipe) {
            if (!(recipe instanceof RecipeHolder<?> holder)) {
                return null;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return null;
            }
            return minecraft.level.getRecipeManager().byKey(holder.id()).orElse(null);
        }
    }
}
