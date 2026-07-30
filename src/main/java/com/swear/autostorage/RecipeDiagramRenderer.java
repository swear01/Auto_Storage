package com.swear.autostorage;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public interface RecipeDiagramRenderer {
    boolean supports(RecipePresentation presentation, Geometry geometry);

    void render(
            GuiGraphics graphics,
            Font font,
            RecipePresentation presentation,
            Geometry geometry,
            int left,
            int top,
            int mouseX,
            int mouseY,
            float partialTick
    );

    default boolean mouseClicked(
            RecipePresentation presentation,
            Geometry geometry,
            int left,
            int top,
            double mouseX,
            double mouseY,
            int button
    ) {
        return false;
    }

    default boolean keyPressed(
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
        return false;
    }

    default boolean renderTooltip(
            GuiGraphics graphics,
            Font font,
            RecipePresentation presentation,
            Geometry geometry,
            int left,
            int top,
            int mouseX,
            int mouseY
    ) {
        return false;
    }

    record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Recipe diagram bounds cannot be negative");
            }
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right()
                    && pointY >= y && pointY < bottom();
        }
    }

    record Geometry(
            Rect diagram,
            List<Rect> inputSlots,
            Rect arrow,
            Rect output,
            Rect station,
            Rect shapelessMarker
    ) {
        public Geometry {
            inputSlots = List.copyOf(inputSlots);
            if (inputSlots.size() != RecipePresentation.MAX_INPUTS) {
                throw new IllegalArgumentException("Recipe diagram requires nine input slots");
            }
        }
    }
}
