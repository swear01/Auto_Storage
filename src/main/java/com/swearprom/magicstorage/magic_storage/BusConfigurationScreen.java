package com.swearprom.magicstorage.magic_storage;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BusConfigurationScreen extends AbstractContainerScreen<BusConfigurationMenu> {
    private final Button[] sideButtons = new Button[Direction.values().length];
    private Button modeButton;
    private Button unsidedButton;
    private Button automationButton;
    private Button filterModeButton;

    public BusConfigurationScreen(
            BusConfigurationMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
        imageWidth = 220;
        imageHeight = 172;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 29;
        inventoryLabelY = 80;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addControl(10, 20, 58, BusConfigurationMenu.TOGGLE_MODE_BUTTON);
        unsidedButton = addControl(10, 36, 58, BusConfigurationMenu.TOGGLE_UNSIDED_BUTTON);
        automationButton = addControl(10, 52, 58, BusConfigurationMenu.TOGGLE_AUTOMATION_BUTTON);
        filterModeButton = addControl(10, 68, 58, BusConfigurationMenu.TOGGLE_FILTER_MODE_BUTTON);
        for (Direction direction : Direction.values()) {
            int index = direction.ordinal();
            int x = 150 + index % 2 * 31;
            int y = 24 + index / 2 * 18;
            Button button = addControl(
                    x, y, 29, BusConfigurationMenu.TOGGLE_SIDE_BUTTON_START + index);
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.magic_storage.bus.side." + direction.getName())));
            sideButtons[index] = button;
        }
        modeButton.setTooltip(Tooltip.create(Component.translatable("gui.magic_storage.bus.mode")));
        unsidedButton.setTooltip(Tooltip.create(Component.translatable("gui.magic_storage.bus.unsided")));
        automationButton.setTooltip(Tooltip.create(Component.translatable("gui.magic_storage.bus.automation")));
        filterModeButton.setTooltip(Tooltip.create(Component.translatable("gui.magic_storage.bus.filters")));
        updateControls();
    }

    private Button addControl(int x, int y, int width, int id) {
        return addRenderableWidget(Button.builder(
                        Component.empty(), button -> sendButton(id))
                .bounds(leftPos + x, topPos + y, width, 14)
                .build());
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null && menu.canConfigure()) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateControls();
    }

    private void updateControls() {
        if (modeButton == null) return;
        boolean enabled = menu.canConfigure() && menu.isSupported();
        modeButton.active = enabled;
        unsidedButton.active = enabled;
        automationButton.active = enabled;
        filterModeButton.active = enabled;
        modeButton.setMessage(Component.translatable(menu.getMode() == BusMode.DIRECTIONAL
                ? "gui.magic_storage.bus.directional"
                : "gui.magic_storage.bus.directionless"));
        unsidedButton.setMessage(onOff(menu.hasUnsidedAccess()));
        automationButton.setMessage(onOff(menu.isAutomationEnabled()));
        filterModeButton.setMessage(Component.translatable(
                menu.getFilterMode() == BusFilterMode.ALLOW
                        ? "gui.magic_storage.bus.allow"
                        : "gui.magic_storage.bus.deny"));
        for (Direction direction : Direction.values()) {
            Button button = sideButtons[direction.ordinal()];
            if (button == null) continue;
            button.active = enabled;
            boolean selected = (menu.getSideMask() & 1 << direction.ordinal()) != 0;
            button.setMessage(Component.translatable("gui.magic_storage.bus.side.short." + direction.getName())
                    .withColor(selected ? 0x55FF55 : 0x777777));
        }
    }

    private static Component onOff(boolean value) {
        return value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        StorageTerminalScreen.drawRaisedPanel(
                graphics, leftPos, topPos, new TerminalLayout.Rect(0, 0, imageWidth, imageHeight));
        StorageTerminalScreen.drawInsetPanel(
                graphics, leftPos, topPos, new TerminalLayout.Rect(6, 17, 66, 66));
        StorageTerminalScreen.drawInsetPanel(
                graphics, leftPos, topPos, new TerminalLayout.Rect(76, 17, 62, 62));
        StorageTerminalScreen.drawInsetPanel(
                graphics, leftPos, topPos, new TerminalLayout.Rect(146, 17, 68, 62));
        StorageTerminalScreen.drawInsetPanel(
                graphics, leftPos, topPos, new TerminalLayout.Rect(28, 87, 164, 76));
        for (int index = 0; index < BusConfigurationMenu.FILTER_SLOT_COUNT; index++) {
            int x = leftPos + 82 + index % 3 * 18;
            int y = topPos + 22 + index / 3 * 18;
            StorageTerminalScreen.drawVanillaSlot(graphics, x, y);
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                StorageTerminalScreen.drawVanillaSlot(
                        graphics, leftPos + 29 + column * 18, topPos + 90 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            StorageTerminalScreen.drawVanillaSlot(
                    graphics, leftPos + 29 + column * 18, topPos + 148);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        graphics.drawString(font,
                Component.translatable("gui.magic_storage.bus.filters"),
                82, 8, 0x404040, false);
        graphics.drawCenteredString(font,
                Component.translatable(menu.getMode() == BusMode.DIRECTIONAL
                        ? "gui.magic_storage.bus.front_transfer"
                        : "gui.magic_storage.bus.external_sides"),
                180, 80, 0x404040);
        if (!menu.canConfigure()) {
            graphics.drawString(font,
                    Component.translatable("gui.magic_storage.bus.read_only"),
                    8, 80, 0x555555, false);
        }
    }
}
