package com.swear.autostorage.compat;

import com.swear.autostorage.RecipeDiagramRenderer;
import com.swear.autostorage.RecipePresentation;
import com.swear.autostorage.RecipePresentationKind;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Objects;
import java.util.Optional;

public final class JeiRecipeDiagramRenderer implements RecipeDiagramRenderer {
    private final IJeiRuntime runtime;
    private ResourceLocation cachedId;
    private IRecipeLayoutDrawable<?> cachedLayout;
    private int cachedWidth;
    private int cachedHeight;

    JeiRecipeDiagramRenderer(IJeiRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public boolean supports(RecipePresentation presentation, Geometry geometry) {
        if (presentation.isEmpty() || presentation.kind() == RecipePresentationKind.AXE) {
            return false;
        }
        IRecipeLayoutDrawable<?> layout = compatibleLayout(presentation);
        return layout != null
                && layout.getRect().getWidth() > 0
                && layout.getRect().getHeight() > 0;
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
        LayoutState state = layoutState(presentation, geometry, left, top);
        Rect diagram = geometry.diagram();
        int localMouseX = (int) ((mouseX - state.originX()) / state.scale());
        int localMouseY = (int) ((mouseY - state.originY()) / state.scale());

        state.layout().tick();
        graphics.enableScissor(
                left + diagram.x(),
                top + diagram.y(),
                left + diagram.right(),
                top + diagram.bottom());
        try {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(state.originX(), state.originY(), 0);
                graphics.pose().scale(state.scale(), state.scale(), 1.0F);
                state.layout().setPosition(0, 0);
                state.layout().drawRecipe(graphics, localMouseX, localMouseY);
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
        LayoutState state = layoutState(presentation, geometry, left, top);
        double localMouseX = (mouseX - state.originX()) / state.scale();
        double localMouseY = (mouseY - state.originY()) / state.scale();
        IJeiInputHandler inputHandler = state.layout().getInputHandler();
        if (!inputHandler.handleInput(localMouseX, localMouseY, JeiUserInput.mouse(button, true))) {
            return false;
        }
        return inputHandler.handleInput(localMouseX, localMouseY, JeiUserInput.mouse(button, false));
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
        LayoutState state = layoutState(presentation, geometry, left, top);
        double localMouseX = (mouseX - state.originX()) / (double) state.scale();
        double localMouseY = (mouseY - state.originY()) / (double) state.scale();
        return state.layout().getInputHandler().handleInput(
                localMouseX,
                localMouseY,
                JeiUserInput.key(keyCode, scanCode, modifiers));
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
        LayoutState state = layoutState(presentation, geometry, left, top);
        int localMouseX = (int) ((mouseX - state.originX()) / state.scale());
        int localMouseY = (int) ((mouseY - state.originY()) / state.scale());
        state.layout().setPosition(state.originX(), state.originY());
        state.layout().drawOverlays(graphics, mouseX, mouseY);
        return state.layout().isMouseOver(localMouseX, localMouseY)
                || state.layout().getSlotUnderMouse(localMouseX, localMouseY).isPresent();
    }

    private IRecipeLayoutDrawable<?> compatibleLayout(RecipePresentation presentation) {
        if (Objects.equals(cachedId, presentation.recipeId())) {
            return cachedLayout;
        }
        Optional<IRecipeLayoutDrawable<?>> created = createLayout(presentation.recipeId());
        if (created.isEmpty()) {
            cachedId = presentation.recipeId();
            cachedLayout = null;
            cachedWidth = 0;
            cachedHeight = 0;
            return null;
        }
        IRecipeLayoutDrawable<?> layout = created.get();
        Rect2i rect = layout.getRect();
        cachedId = presentation.recipeId();
        cachedLayout = layout;
        cachedWidth = rect.getWidth();
        cachedHeight = rect.getHeight();
        return layout;
    }

    private LayoutState layoutState(RecipePresentation presentation, Geometry geometry, int left, int top) {
        IRecipeLayoutDrawable<?> layout = compatibleLayout(presentation);
        if (layout == null || cachedWidth <= 0 || cachedHeight <= 0
                || geometry.diagram().width() <= 0 || geometry.diagram().height() <= 0) {
            throw new IllegalStateException(
                    "Selected recipe no longer has a compatible JEI public layout representation");
        }
        Rect diagram = geometry.diagram();
        float scale = Math.min(1.0F, Math.min(
                diagram.width() / (float) cachedWidth,
                diagram.height() / (float) cachedHeight));
        int scaledWidth = Math.round(cachedWidth * scale);
        return new LayoutState(
                left + diagram.x() + (diagram.width() - scaledWidth) / 2,
                top + diagram.y(),
                scale,
                layout);
    }

    private Optional<IRecipeLayoutDrawable<?>> createLayout(ResourceLocation recipeId) {
        IRecipeManager manager = runtime.getRecipeManager();
        IFocusGroup focuses = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        Optional<IRecipeLayoutDrawable<?>> fromHolder = createLayoutFromHolder(manager, focuses, recipeId);
        if (fromHolder.isPresent()) {
            return fromHolder;
        }
        return createLayoutByScan(manager, focuses, recipeId);
    }

    private Optional<IRecipeLayoutDrawable<?>> createLayoutFromHolder(
            IRecipeManager manager,
            IFocusGroup focuses,
            ResourceLocation recipeId
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Optional.empty();
        }
        RecipeHolder<?> holder = minecraft.level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null) {
            return Optional.empty();
        }
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        if (typeId == null) {
            return Optional.empty();
        }
        Optional<RecipeType<?>> recipeType = manager.getRecipeType(typeId);
        if (recipeType.isEmpty()) {
            return Optional.empty();
        }
        IRecipeCategory<?> category = manager.getRecipeCategory(recipeType.get());
        Optional<IRecipeLayoutDrawable<?>> direct = createDrawable(manager, category, holder, focuses);
        if (direct.isPresent()) {
            return direct;
        }
        return manager.createRecipeLookup(recipeType.get())
                .get()
                .filter(recipe -> recipeId.equals(category.getRegistryName(cast(recipe))))
                .findFirst()
                .flatMap(recipe -> createDrawable(manager, category, recipe, focuses));
    }

    private Optional<IRecipeLayoutDrawable<?>> createLayoutByScan(
            IRecipeManager manager,
            IFocusGroup focuses,
            ResourceLocation recipeId
    ) {
        return manager.createRecipeCategoryLookup()
                .get()
                .map(category -> manager.createRecipeLookup(category.getRecipeType())
                        .get()
                        .filter(recipe -> recipeId.equals(category.getRegistryName(cast(recipe))))
                        .findFirst()
                        .flatMap(recipe -> createDrawable(manager, category, recipe, focuses)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional<IRecipeLayoutDrawable<?>> createDrawable(
            IRecipeManager manager,
            IRecipeCategory category,
            Object recipe,
            IFocusGroup focuses
    ) {
        Optional created = manager.createRecipeLayoutDrawable(category, recipe, focuses);
        if (created.isEmpty()) {
            return Optional.empty();
        }
        IRecipeLayoutDrawable<?> layout = (IRecipeLayoutDrawable<?>) created.get();
        Rect2i rect = layout.getRect();
        if (rect.getWidth() <= 0 || rect.getHeight() <= 0) {
            return Optional.empty();
        }
        return Optional.of(layout);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    private record LayoutState(int originX, int originY, float scale, IRecipeLayoutDrawable<?> layout) {
        private LayoutState {
            if (scale <= 0.0F) {
                throw new IllegalArgumentException("JEI layout scale must be positive");
            }
            Objects.requireNonNull(layout, "layout");
        }
    }
}
