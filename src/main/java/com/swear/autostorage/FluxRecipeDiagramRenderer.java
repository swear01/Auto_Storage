package com.swear.autostorage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public final class FluxRecipeDiagramRenderer implements RecipeDiagramRenderer {
    public static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            AutoStorage.MODID, "flux_station/redstone_to_flux_dust");

    private static final ResourceLocation FLUX_BLOCK_ID = ResourceLocation.fromNamespaceAndPath(
            "fluxnetworks", "flux_block");
    private static final int CONTENT_WIDTH = 128;
    private static final int CONTENT_HEIGHT = 80;
    private static final int ANIMATION_TICKS = 80;

    private final ItemStack bedrockStack;
    private final ItemStack fluxBlockStack;

    public FluxRecipeDiagramRenderer() {
        bedrockStack = new ItemStack(Blocks.BEDROCK);
        Block fluxBlock = BuiltInRegistries.BLOCK.get(FLUX_BLOCK_ID);
        fluxBlockStack = fluxBlock == Blocks.AIR ? ItemStack.EMPTY : new ItemStack(fluxBlock);
    }

    @Override
    public boolean supports(RecipePresentation presentation, Geometry geometry) {
        return RECIPE_ID.equals(presentation.recipeId())
                && presentation.kind() == RecipePresentationKind.WORLD_STATION
                && !presentation.isEmpty()
                && matchesExpectedRecipe(presentation);
    }

    @Override
    public boolean usesSharedStationBadge() {
        return false;
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
        Rect diagram = geometry.diagram();
        RenderBounds bounds = renderBounds(diagram, left, top);
        float progress = animationProgress(partialTick);
        ItemStack input = presentation.inputs().getFirst();
        ItemStack output = presentation.output();

        graphics.enableScissor(
                left + diagram.x(),
                top + diagram.y(),
                left + diagram.right(),
                top + diagram.bottom());
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(bounds.x(), bounds.y(), 0.0F);
            graphics.pose().scale(bounds.scale(), bounds.scale(), 1.0F);
            graphics.fill(4, 16, CONTENT_WIDTH - 4, CONTENT_HEIGHT - 2, 0x18000000);
            graphics.fill(18, 42, 110, 44, 0xFF8B8B8B);
            graphics.fill(48, 40, 80, 42, 0xFF606060);

            renderStack(graphics, font, presentation.station(), 56, 2);
            renderStack(graphics, font, baseStack(progress), 56, 47);
            renderStack(graphics, font, input, 8, 30);
            renderStack(graphics, font, output, 104, 30);

            int movingX = 25 + Math.round(78.0F * progress);
            renderStack(graphics, font, progress < 0.5F ? input : output, movingX, 30);
            graphics.fill(46, 31, 50, 35, 0xFF606060);
            graphics.fill(78, 31, 82, 35, 0xFF606060);

            Component title = Component.translatable("gui.auto_storage.flux_recipe.title");
            graphics.drawString(
                    font,
                    title,
                    (CONTENT_WIDTH - font.width(title)) / 2,
                    67,
                    0xFF404040,
                    false);
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
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
        RenderBounds bounds = renderBounds(diagram, left, top);
        double logicalX = (mouseX - bounds.x()) / bounds.scale();
        double logicalY = (mouseY - bounds.y()) / bounds.scale();
        if (new Rect(6, 28, 24, 22).contains(logicalX, logicalY)) {
            graphics.renderTooltip(font, presentation.inputs().getFirst(), mouseX, mouseY);
            return true;
        }
        if (new Rect(100, 28, 24, 22).contains(logicalX, logicalY)) {
            graphics.renderTooltip(font, presentation.output(), mouseX, mouseY);
            return true;
        }
        if (new Rect(50, 0, 28, 24).contains(logicalX, logicalY)) {
            graphics.renderTooltip(font, presentation.station(), mouseX, mouseY);
            return true;
        }
        if (new Rect(48, 42, 32, 28).contains(logicalX, logicalY)) {
            Block fluxBlock = BuiltInRegistries.BLOCK.get(FLUX_BLOCK_ID);
            Component fluxName = fluxBlock == Blocks.AIR
                    ? Component.translatable("gui.auto_storage.flux_recipe.unavailable")
                    : fluxBlock.getName();
            graphics.renderComponentTooltip(font, List.of(Component.translatable(
                    "gui.auto_storage.flux_recipe.base_options",
                    new ItemStack(Blocks.BEDROCK).getHoverName(),
                    fluxName)), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private static boolean matchesExpectedRecipe(RecipePresentation presentation) {
        return FluxRecipePresentationContract.matches(presentation);
    }

    private static RenderBounds renderBounds(Rect diagram, int left, int top) {
        float scale = Math.min(
                1.0F,
                Math.min(
                        diagram.width() / (float) CONTENT_WIDTH,
                        diagram.height() / (float) CONTENT_HEIGHT));
        int scaledWidth = Math.round(CONTENT_WIDTH * scale);
        int scaledHeight = Math.round(CONTENT_HEIGHT * scale);
        return new RenderBounds(
                left + diagram.x() + RecipeDiagramGeometry.centeredOffset(diagram.width(), scaledWidth),
                top + diagram.y() + RecipeDiagramGeometry.centeredOffset(diagram.height(), scaledHeight),
                scale);
    }

    private static float animationProgress(float partialTick) {
        long gameTime = Minecraft.getInstance().level == null
                ? 0L : Minecraft.getInstance().level.getGameTime();
        return (Math.floorMod(gameTime, (long) ANIMATION_TICKS) + partialTick) / ANIMATION_TICKS;
    }

    private ItemStack baseStack(float progress) {
        return progress < 0.5F || fluxBlockStack.isEmpty()
                ? bedrockStack
                : fluxBlockStack;
    }

    private static void renderStack(
            GuiGraphics graphics,
            Font font,
            ItemStack stack,
            int x,
            int y
    ) {
        graphics.renderItem(stack, x, y);
        if (!stack.isEmpty()) graphics.renderItemDecorations(font, stack, x, y);
    }

    private record RenderBounds(int x, int y, float scale) {
    }
}
