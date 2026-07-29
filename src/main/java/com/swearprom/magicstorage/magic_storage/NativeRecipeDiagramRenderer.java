package com.swearprom.magicstorage.magic_storage;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class NativeRecipeDiagramRenderer implements RecipeDiagramRenderer {
    @Override
    public boolean supports(RecipePresentation presentation, Geometry geometry) {
        if (presentation.isEmpty()) return false;
        return true;
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
        renderInputs(graphics, font, presentation, geometry, left, top, partialTick);
        drawArrow(
                graphics, geometry.arrow(), left, top,
                0xFF606060);
        if (presentation.shapeless()) {
            renderShapelessMarker(graphics, geometry.shapelessMarker(), left, top);
        }

        Rect outputBounds = geometry.output();
        drawLargeSlotFrame(graphics, outputBounds, left, top);
        ItemStack output = presentation.output();
        int outputX = left + outputBounds.x() + (outputBounds.width() - 16) / 2;
        int outputY = top + outputBounds.y() + (outputBounds.height() - 16) / 2;
        StorageTerminalScreen.renderTerminalIcon(
                graphics, output, outputX, outputY, partialTick);
        if (TerminalResourceDisplay.isTyped(output)) {
            String amount = TerminalAmountFormatter.formatCompact(
                    TerminalDisplayStack.amount(output));
            graphics.drawString(
                    font,
                    amount,
                    outputX + 17 - font.width(amount),
                    outputY + 9,
                    0xFFFFFFFF,
                    false);
        } else {
            graphics.renderItemDecorations(font, output, outputX, outputY);
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
        double localX = mouseX - left;
        double localY = mouseY - top;
        if (geometry.output().contains(localX, localY)) {
            ItemStack output = presentation.output();
            if (TerminalResourceDisplay.isTyped(output)) {
                graphics.renderComponentTooltip(font, List.of(
                        output.getHoverName(),
                        Component.translatable(
                                "gui.magic_storage.output_amount",
                                TerminalDisplayStack.amount(output))
                ), mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, output, mouseX, mouseY);
            }
            return true;
        }
        ItemStack input = inputAt(presentation, geometry, localX, localY);
        if (!input.isEmpty()) {
            graphics.renderTooltip(font, input, mouseX, mouseY);
            return true;
        }
        return false;
    }

    private static void renderInputs(
            GuiGraphics graphics,
            Font font,
            RecipePresentation presentation,
            Geometry geometry,
            int left,
            int top,
            float partialTick
    ) {
        List<ItemStack> inputs = presentation.inputs();
        if (presentation.kind() == RecipePresentationKind.CRAFTING) {
            for (Rect slot : geometry.inputSlots()) {
                drawInputSlot(graphics, font, slot, ItemStack.EMPTY, left, top, partialTick);
            }
        }
        int positions = presentation.width() * presentation.height();
        for (int input = 0; input < positions; input++) {
            drawInputSlot(
                    graphics,
                    font,
                    inputSlot(presentation, geometry, input),
                    inputs.get(input),
                    left,
                    top,
                    partialTick);
        }
    }

    private static Rect inputSlot(
            RecipePresentation presentation,
            Geometry geometry,
            int input
    ) {
        int column = input % presentation.width();
        int row = input / presentation.width();
        int columnOffset = (3 - presentation.width()) / 2;
        int rowOffset = (3 - presentation.height()) / 2;
        return geometry.inputSlots().get((row + rowOffset) * 3 + column + columnOffset);
    }

    private static ItemStack inputAt(
            RecipePresentation presentation,
            Geometry geometry,
            double mouseX,
            double mouseY
    ) {
        List<ItemStack> inputs = presentation.inputs();
        int positions = presentation.width() * presentation.height();
        for (int input = 0; input < positions; input++) {
            if (inputSlot(presentation, geometry, input).contains(mouseX, mouseY)) {
                return inputs.get(input);
            }
        }
        return ItemStack.EMPTY;
    }

    private static void drawInputSlot(
            GuiGraphics graphics,
            Font font,
            Rect slot,
            ItemStack stack,
            int left,
            int top,
            float partialTick
    ) {
        int itemX = left + slot.x() + 1;
        int itemY = top + slot.y() + 1;
        drawSlotFrame(graphics, itemX, itemY);
        if (stack.isEmpty()) return;
        StorageTerminalScreen.renderTypedResourceBackground(
                graphics, stack, itemX, itemY);
        StorageTerminalScreen.renderTerminalIcon(
                graphics, stack, itemX, itemY, partialTick);
        if (TerminalDisplayStack.isDisplay(stack)) {
            String amount = TerminalAmountFormatter.formatCompact(
                    TerminalDisplayStack.amount(stack));
            graphics.drawString(
                    font,
                    amount,
                    itemX + 17 - font.width(amount),
                    itemY + 9,
                    0xFFFFFFFF,
                    false);
        } else {
            graphics.renderItemDecorations(font, stack, itemX, itemY);
        }
    }

    private static void drawSlotFrame(GuiGraphics graphics, int x, int y) {
        StorageTerminalScreen.drawVanillaSlot(graphics, x, y);
    }

    private static void drawLargeSlotFrame(
            GuiGraphics graphics,
            Rect bounds,
            int left,
            int top
    ) {
        StorageTerminalScreen.drawRaisedPanel(
                graphics,
                left,
                top,
                new TerminalLayout.Rect(
                        bounds.x(), bounds.y(), bounds.width(), bounds.height()));
    }

    private static void renderShapelessMarker(
            GuiGraphics graphics,
            Rect bounds,
            int left,
            int top
    ) {
        int x = left + bounds.x();
        int y = top + bounds.y();
        int color = 0xFF606060;
        graphics.fill(x, y + 2, x + 7, y + 3, color);
        graphics.fill(x + 6, y + 1, x + 8, y + 4, color);
        graphics.fill(x + 2, y + 7, x + 9, y + 8, color);
        graphics.fill(x + 1, y + 6, x + 3, y + 9, color);
    }

    private static void drawArrow(
            GuiGraphics graphics,
            Rect bounds,
            int left,
            int top,
            int color
    ) {
        int iconX = left + bounds.x() + (bounds.width() - TerminalLayout.ICON_CANVAS_SIZE) / 2;
        int iconY = top + bounds.y() + (bounds.height() - TerminalLayout.ICON_CANVAS_SIZE) / 2;
        StorageTerminalScreen.blitControlIcon(
                graphics, iconX, iconY, StorageTerminalScreen.TerminalControlIcon.NEXT, color);
    }
}
