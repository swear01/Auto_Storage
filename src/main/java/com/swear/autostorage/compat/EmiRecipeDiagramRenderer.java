package com.swear.autostorage.compat;

import com.swear.autostorage.RecipeDiagramRenderer;
import com.swear.autostorage.RecipePresentation;
import com.swear.autostorage.RecipePresentationKind;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EmiRecipeDiagramRenderer implements RecipeDiagramRenderer {
    private ResourceLocation cachedId;
    private EmiRecipe cachedRecipe;
    private int cachedWidth;
    private int cachedHeight;
    private List<Widget> cachedWidgets = List.of();

    @Override
    public boolean supports(RecipePresentation presentation, Geometry geometry) {
        if (presentation.isEmpty()
                || presentation.kind() == RecipePresentationKind.AXE
                || presentation.kind() == RecipePresentationKind.WORLD_STATION) {
            return false;
        }
        EmiRecipe recipe = compatibleRecipe(presentation);
        return recipe != null
                && recipe.getDisplayWidth() > 0
                && recipe.getDisplayHeight() > 0;
    }

    @Override
    public void render(
            GuiGraphics graphics,
            Font font,
            RecipePresentation presentation,
            Geometry geometry,
            int left,
            int top,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        WidgetState state = widgetState(presentation, geometry);
        Rect diagram = geometry.diagram();
        int originX = left + state.x();
        int originY = top + state.y();
        int localMouseX = (int) ((mouseX - originX) / state.scale());
        int localMouseY = (int) ((mouseY - originY) / state.scale());

        graphics.enableScissor(
                left + diagram.x(),
                top + diagram.y(),
                left + diagram.right(),
                top + diagram.bottom());
        try {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(originX, originY, 0);
                graphics.pose().scale(state.scale(), state.scale(), 1.0F);
                for (Widget widget : state.widgets()) {
                    widget.render(graphics, localMouseX, localMouseY, partialTick);
                }
            } finally {
                graphics.pose().popPose();
            }
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(
            RecipePresentation presentation,
            Geometry geometry,
            int left,
            int top,
            double mouseX,
            double mouseY,
            int button
    ) {
        Rect diagram = geometry.diagram();
        if (!diagram.contains(mouseX - left, mouseY - top)) return false;
        WidgetState state = widgetState(presentation, geometry);
        int localMouseX = (int) ((mouseX - left - state.x()) / state.scale());
        int localMouseY = (int) ((mouseY - top - state.y()) / state.scale());
        for (Widget widget : state.widgets()) {
            if (widget.getBounds().contains(localMouseX, localMouseY)
                    && widget.mouseClicked(localMouseX, localMouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(
            RecipePresentation presentation,
            Geometry geometry,
            int left,
            int top,
            int mouseX,
            int mouseY,
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        Rect diagram = geometry.diagram();
        if (!diagram.contains(mouseX - left, mouseY - top)) return false;
        WidgetState state = widgetState(presentation, geometry);
        int localMouseX = (int) ((mouseX - left - state.x()) / state.scale());
        int localMouseY = (int) ((mouseY - top - state.y()) / state.scale());
        for (Widget widget : state.widgets()) {
            if (widget.getBounds().contains(localMouseX, localMouseY)
                    && widget.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean renderTooltip(
            GuiGraphics graphics,
            Font font,
            RecipePresentation presentation,
            Geometry geometry,
            int left,
            int top,
            int mouseX,
            int mouseY
    ) {
        Rect diagram = geometry.diagram();
        if (!diagram.contains(mouseX - left, mouseY - top)) return false;
        WidgetState state = widgetState(presentation, geometry);
        int localMouseX = (int) ((mouseX - left - state.x()) / state.scale());
        int localMouseY = (int) ((mouseY - top - state.y()) / state.scale());
        for (Widget widget : state.widgets()) {
            if (!widget.getBounds().contains(localMouseX, localMouseY)) continue;
            List<ClientTooltipComponent> tooltip = widget.getTooltip(localMouseX, localMouseY);
            if (tooltip.isEmpty()) continue;
            renderTooltipComponents(graphics, font, tooltip, mouseX, mouseY);
            return true;
        }
        return false;
    }

    private EmiRecipe compatibleRecipe(RecipePresentation presentation) {
        var manager = EmiApi.getRecipeManager();
        if (Objects.equals(cachedId, presentation.recipeId())
                && cachedRecipe != null
                && manager.getRecipe(cachedRecipe.getId()) == cachedRecipe) {
            return cachedRecipe;
        }
        EmiRecipe direct = manager.getRecipe(presentation.recipeId());
        if (direct != null && Objects.equals(direct.getId(), presentation.recipeId())) {
            RecipeHolder<?> backingRecipe = direct.getBackingRecipe();
            if (backingRecipe != null && backingRecipe.id().equals(presentation.recipeId())) {
                return direct;
            }
        }
        EmiRecipe match = null;
        for (EmiRecipe candidate : manager.getRecipesByOutput(EmiStack.of(presentation.output()))) {
            if (!matchesPublicRecipe(candidate, presentation)) continue;
            if (match != null && match != candidate) return null;
            match = candidate;
        }
        return match;
    }

    private static boolean matchesPublicRecipe(
            EmiRecipe recipe,
            RecipePresentation presentation
    ) {
        if (recipe == null || recipe.getId() == null
                || recipe.getDisplayWidth() <= 0 || recipe.getDisplayHeight() <= 0
                || !matchesOutput(recipe, presentation.output())) {
            return false;
        }
        List<RecipePresentation.Resource> resources = presentation.resources();
        if (resources.isEmpty()
                || resources.stream().anyMatch(resource ->
                resource.kind() != RecipePresentation.ResourceKind.ITEM)) {
            return false;
        }
        long[] matchedAmounts = new long[resources.size()];
        for (EmiIngredient ingredient : recipe.getInputs()) {
            if (ingredient.isEmpty()) continue;
            long amount = ingredient.getAmount();
            if (amount <= 0) return false;
            int matchedResource = -1;
            for (int index = 0; index < resources.size(); index++) {
                ItemStack expected = resources.get(index).stack();
                if (ingredient.getEmiStacks().stream().anyMatch(candidate ->
                        ItemStack.isSameItemSameComponents(candidate.getItemStack(), expected))) {
                    if (matchedResource >= 0) return false;
                    matchedResource = index;
                }
            }
            if (matchedResource < 0
                    || amount > resources.get(matchedResource).required()
                    - matchedAmounts[matchedResource]) {
                return false;
            }
            matchedAmounts[matchedResource] += amount;
        }
        for (int index = 0; index < resources.size(); index++) {
            if (matchedAmounts[index] != resources.get(index).required()) return false;
        }
        return true;
    }

    private static boolean matchesOutput(EmiRecipe recipe, ItemStack output) {
        return recipe.getOutputs().stream().anyMatch(candidate ->
                candidate.getAmount() == output.getCount()
                        && ItemStack.isSameItemSameComponents(candidate.getItemStack(), output));
    }

    private WidgetState widgetState(RecipePresentation presentation, Geometry geometry) {
        EmiRecipe recipe = compatibleRecipe(presentation);
        if (recipe == null || recipe.getDisplayWidth() <= 0 || recipe.getDisplayHeight() <= 0
                || geometry.diagram().width() <= 0 || geometry.diagram().height() <= 0) {
            throw new IllegalStateException(
                    "Selected recipe no longer has a compatible EMI public-widget representation");
        }
        if (cachedRecipe != recipe
                || !presentation.recipeId().equals(cachedId)
                || cachedWidth != recipe.getDisplayWidth()
                || cachedHeight != recipe.getDisplayHeight()) {
            PublicWidgetHolder holder = new PublicWidgetHolder(
                    recipe.getDisplayWidth(), recipe.getDisplayHeight());
            recipe.addWidgets(holder);
            cachedId = presentation.recipeId();
            cachedRecipe = recipe;
            cachedWidth = recipe.getDisplayWidth();
            cachedHeight = recipe.getDisplayHeight();
            cachedWidgets = holder.widgets();
        }
        Rect diagram = geometry.diagram();
        float scale = Math.min(1.0F, Math.min(
                diagram.width() / (float) cachedWidth,
                diagram.height() / (float) cachedHeight));
        int scaledWidth = Math.round(cachedWidth * scale);
        return new WidgetState(
                diagram.x() + (diagram.width() - scaledWidth) / 2,
                diagram.y(),
                scale,
                cachedWidgets);
    }

    private static void renderTooltipComponents(
            GuiGraphics graphics,
            Font font,
            List<ClientTooltipComponent> components,
            int mouseX,
            int mouseY
    ) {
        int width = components.stream().mapToInt(component -> component.getWidth(font)).max().orElse(0);
        int height = components.stream().mapToInt(ClientTooltipComponent::getHeight).sum();
        if (components.size() > 1) height += 2;
        int x = mouseX + 12;
        if (x + width + 8 > graphics.guiWidth()) x = mouseX - width - 12;
        x = Math.clamp(x, 4, Math.max(4, graphics.guiWidth() - width - 4));
        int y = Math.clamp(
                mouseY - 12,
                4,
                Math.max(4, graphics.guiHeight() - height - 4));

        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, 400);
            graphics.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0xF0100010);
            graphics.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0xFF505050);
            graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xF0100010);
            int lineY = y;
            for (int index = 0; index < components.size(); index++) {
                ClientTooltipComponent component = components.get(index);
                component.renderText(
                        font,
                        x,
                        lineY,
                        graphics.pose().last().pose(),
                        graphics.bufferSource());
                lineY += component.getHeight();
                if (index == 0 && components.size() > 1) lineY += 2;
            }
            graphics.flush();
            lineY = y;
            for (int index = 0; index < components.size(); index++) {
                ClientTooltipComponent component = components.get(index);
                component.renderImage(font, x, lineY, graphics);
                lineY += component.getHeight();
                if (index == 0 && components.size() > 1) lineY += 2;
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private record WidgetState(int x, int y, float scale, List<Widget> widgets) {
        private WidgetState {
            if (scale <= 0.0F) {
                throw new IllegalArgumentException("EMI widget scale must be positive");
            }
            widgets = List.copyOf(widgets);
        }
    }

    private static final class PublicWidgetHolder implements WidgetHolder {
        private final int width;
        private final int height;
        private final List<Widget> widgets = new ArrayList<>();

        private PublicWidgetHolder(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("EMI widget holder bounds must be positive");
            }
            this.width = width;
            this.height = height;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public <T extends Widget> T add(T widget) {
            Objects.requireNonNull(widget, "widget");
            Objects.requireNonNull(widget.getBounds(), "widget bounds");
            widgets.add(widget);
            return widget;
        }

        private List<Widget> widgets() {
            return List.copyOf(widgets);
        }
    }
}
