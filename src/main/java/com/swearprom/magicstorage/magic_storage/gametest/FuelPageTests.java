package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MagicStorage.MODID)
@PrefixGameTestTemplate(false)
public class FuelPageTests {

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void transform_discovers_compatible_uses_without_consuming_input(
            GameTestHelper helper
    ) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(170, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            Slot input = menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT);
            input.set(new ItemStack(Items.BLAZE_ROD));

            var uses = menu.getTransformUses();
            if (uses.size() != 2
                    || !uses.get(0).id().equals(
                    TransformProviderApi.energyTargetId(EnergyType.FURNACE_FUEL))
                    || !uses.get(1).id().equals(
                    TransformProviderApi.energyTargetId(EnergyType.BLAZE_FUEL))
                    || input.getItem().getCount() != 1
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != 0
                    || core.getEnergy(EnergyType.BLAZE_FUEL) != 0) {
                helper.fail("Transform discovery must be stable and non-consuming: " + uses);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void transform_accepts_items_with_no_use_and_rejects_actions_neutrally(
            GameTestHelper helper
    ) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(171, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            Slot input = menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT);
            ItemStack cobblestone = new ItemStack(Items.COBBLESTONE);
            if (!input.mayPlace(cobblestone)) {
                helper.fail("Transform input must accept an item so its uses can be inspected");
                return;
            }
            input.set(cobblestone);
            if (!menu.getTransformUses().isEmpty()
                    || menu.clickMenuButton(player, 2)
                    || !input.getItem().is(Items.COBBLESTONE)
                    || input.getItem().getCount() != 1) {
                helper.fail("An item with no transformations must remain untouched");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void transform_input_waits_for_explicit_amount_action(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(168, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            Slot input = menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT);
            input.set(new ItemStack(Items.COAL, 2));
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 0
                    || !input.getItem().is(Items.COAL)
                    || input.getItem().getCount() != 2) {
                helper.fail("Transform preview consumed its input before an explicit action");
                return;
            }
            menu.clickMenuButton(player, 2);
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 1_600
                    || !input.getItem().is(Items.COAL)
                    || input.getItem().getCount() != 1) {
                helper.fail("Transform x1 did not consume exactly one input");
                return;
            }
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 3_200
                    || !input.getItem().isEmpty()) {
                helper.fail("Transform Max did not consume the remaining compatible input");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void stations_page_exposes_only_installable_machine_slots(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(169, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            if (!menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).isActive()
                    || menu.getSlot(CraftingTerminalMenu.MACHINE_SLOT_START).isActive()) {
                helper.fail("Transform page must expose only its transient input");
                return;
            }
            menu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            if (menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).isActive()
                    || !menu.getSlot(CraftingTerminalMenu.MACHINE_SLOT_START).isActive()
                    || menu.getPage() != CraftingTerminalPage.STATIONS) {
                helper.fail("Stations page must expose installable machines without Transform input");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void fullscreen_stations_workspace_uses_two_top_aligned_groups(GameTestHelper helper) {
        var counts = currentFuelDescriptorCounts();
        var geometry = TerminalLayout.forProfile(
                TerminalProfile.CRAFTING,
                423, 291, counts);

        if (!geometry.wide()
                || geometry.timedStationsGrid().pageCount() != 1
                || geometry.instantStationsGrid().pageCount() != 1
                || geometry.timedStationsGrid().cells().size() != counts.timedStationCount()
                || geometry.instantStationsGrid().cells().size() != counts.instantStationCount()
                || geometry.timedStationsPanel().y() != geometry.instantStationsPanel().y()
                || geometry.timedStationsPanel().overlaps(geometry.instantStationsPanel())
                || geometry.timedStationsGrid().columns() != 2
                || geometry.instantStationsGrid().columns() != 2) {
            helper.fail("Stations must show top-aligned Processing and Instant groups with two columns each: "
                    + geometry);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void fullscreen_fuel_category_panels_fill_width_without_inventory_overlap(GameTestHelper helper) {
        var geometry = TerminalLayout.forProfile(
                TerminalProfile.CRAFTING,
                423, 291, currentFuelDescriptorCounts());
        TerminalLayout.Rect playerInventory = geometry.playerInventory();
        var stations = java.util.List.of(
                geometry.timedStationsPanel(),
                geometry.instantStationsPanel());
        if (geometry.consumablesPanel().x() != 8
                || geometry.consumablesPanel().width() != geometry.imageWidth() - 16
                || geometry.consumablesPanel().overlaps(playerInventory)
                || stations.stream().anyMatch(panel -> panel.overlaps(playerInventory))
                || stations.get(0).y() != stations.get(1).y()
                || stations.get(0).right() >= stations.get(1).x()
                || stations.get(1).bottom() > playerInventory.y()) {
            helper.fail("Transform must fill the workspace and Stations must split it left/right: "
                    + stations + ", inventory=" + playerInventory);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void fullscreen_fuel_rows_use_horizontal_headers_and_full_width_content(
            GameTestHelper helper
    ) {
        var geometry = TerminalLayout.forProfile(
                TerminalProfile.CRAFTING,
                423, 291, new TerminalLayout.FuelDescriptorCounts(64, 64, 64, 3));
        var panels = java.util.List.of(
                geometry.consumablesPanel(),
                geometry.timedStationsPanel(),
                geometry.instantStationsPanel());
        var grids = java.util.List.of(
                geometry.consumablesGrid(),
                geometry.timedStationsGrid(),
                geometry.instantStationsGrid());
        var controls = java.util.List.of(
                geometry.consumablesPageControls(),
                geometry.timedStationsPageControls(),
                geometry.instantStationsPageControls());

        for (int category = 0; category < controls.size(); category++) {
            TerminalLayout.Rect label = TerminalLayout.fuelCategoryLabel(
                    panels.get(category), grids.get(category));
            TerminalLayout.Rect previous = controls.get(category).previous();
            TerminalLayout.Rect next = controls.get(category).next();
            if (label.x() != panels.get(category).x() + 2
                    || label.width() != panels.get(category).width() - 4
                    || label.y() != panels.get(category).y() + 2
                    || label.bottom() > grids.get(category).bounds().y()
                    || grids.get(category).bounds().x() != panels.get(category).x() + 2
                    || grids.get(category).bounds().right()
                    != panels.get(category).right() - 2
                    || !contains(label, previous)
                    || !contains(label, next)
                    || previous.overlaps(next)
                    || previous.overlaps(grids.get(category).bounds())
                    || next.overlaps(grids.get(category).bounds())
                    || previous.width() != TerminalLayout.CONTROL_SIZE
                    || previous.height() != TerminalLayout.CONTROL_SIZE
                    || next.width() != TerminalLayout.CONTROL_SIZE
                    || next.height() != TerminalLayout.CONTROL_SIZE) {
                helper.fail("Fuel row " + category
                        + " must use one horizontal header above a full-width content row: "
                        + controls.get(category) + ", label=" + label
                        + ", grid=" + grids.get(category).bounds());
                return;
            }
        }
        if (geometry.consumablesPageControls().next().right()
                > geometry.fuelTargetSelector().x() - 2) {
            helper.fail("Consumables page controls must end before the Fuel Target selector");
            return;
        }
        helper.succeed();
    }

    private static TerminalLayout.FuelDescriptorCounts currentFuelDescriptorCounts() {
        int timed = (int) MachineEnergyTable.entries().stream()
                .filter(entry -> entry.category() == MachineEnergyTable.Category.PROCESS)
                .count();
        int instant = (int) MachineEnergyTable.entries().stream()
                .filter(entry -> entry.category() == MachineEnergyTable.Category.INSTANT)
                .count();
        int consumable = CraftingTerminalMenu.fuelTargets().size()
                + (int) MachineEnergyTable.entries().stream()
                .filter(entry -> entry.category() == MachineEnergyTable.Category.TRANSFORM)
                .count()
                + 1;
        return new TerminalLayout.FuelDescriptorCounts(
                consumable, timed, instant, CraftingTerminalMenu.fuelTargets().size() + 1);
    }

    private static boolean contains(TerminalLayout.Rect outer, TerminalLayout.Rect inner) {
        return outer.contains(inner.x(), inner.y())
                && outer.contains(inner.right() - 1, inner.bottom() - 1);
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void crafting_items_page_stores_fuel_without_conversion(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            player.getInventory().setItem(0, new ItemStack(Items.BLAZE_ROD, 2));
            var menu = new CraftingTerminalMenu(101, player.getInventory(), core);
            int slot = findPlayerMenuSlot(menu, Items.BLAZE_ROD);
            menu.quickMoveStack(player, slot);

            if (!menu.getSlot(slot).getItem().isEmpty()) {
                helper.fail("Items-page quick move should store the complete fuel stack");
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.BLAZE_ROD))) != 2) {
                helper.fail("Crafting Items page should store Blaze Rods as ordinary items");
                return;
            }
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 0 || core.getEnergy(EnergyType.BLAZE_FUEL) != 0) {
                helper.fail("Items-page storage must not convert any fuel");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void retired_bottle_inputs_are_not_accepted_as_fuel(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(124, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            var fuelInput = menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT);
            ItemStack[] retiredInputs = {
                    new ItemStack(Items.GLASS_BOTTLE),
                    new ItemStack(Items.POTION)
            };
            boolean recognizedAsFuel = false;
            boolean acceptedBySlot = false;
            boolean convertedByCore = false;
            for (ItemStack input : retiredInputs) {
                recognizedAsFuel |= core.isFuel(input);
                acceptedBySlot |= fuelInput.mayPlace(input);
                for (EnergyType type : EnergyType.values()) {
                    ItemStack probe = input.copy();
                    long before = core.getEnergy(type);
                    boolean converted = core.addFuel(probe, type);
                    convertedByCore |= converted || probe.getCount() != input.getCount()
                            || core.getEnergy(type) != before;
                }
            }
            if (recognizedAsFuel || acceptedBySlot || convertedByCore) {
                helper.fail("Retired bottle inputs must be rejected by fuel lookup, slot placement, and Core conversion: lookup="
                        + recognizedAsFuel + " slot=" + acceptedBySlot + " conversion=" + convertedByCore);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void items_page_rejects_forged_hidden_fuel_slot_click(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(109, player.getInventory(), core);
            menu.setCarried(new ItemStack(Items.COAL));
            menu.clicked(CraftingTerminalMenu.FUEL_INPUT_SLOT, 0, ClickType.PICKUP, player);

            if (!menu.getCarried().is(Items.COAL) || menu.getCarried().getCount() != 1) {
                helper.fail("Items page must preserve carried fuel after a forged hidden-slot click");
                return;
            }
            if (!menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().isEmpty()) {
                helper.fail("Items page must not accept fuel into its hidden dedicated slot");
                return;
            }
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 0 || core.getTypeCount() != 0) {
                helper.fail("Forged hidden-slot click must not mutate energy or storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void legacy_popup_button_ids_are_noops(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            player.getInventory().setItem(0, new ItemStack(Items.COAL));
            var storageMenu = new StorageTerminalMenu(110, player.getInventory(), core);
            int storageSlot = findPlayerMenuSlot(storageMenu, Items.COAL);
            int oldStorageButton = 1000 + storageSlot * EnergyType.values().length
                    + EnergyType.FURNACE_FUEL.ordinal();
            storageMenu.clickMenuButton(player, oldStorageButton);

            if (!storageMenu.getSlot(storageSlot).getItem().is(Items.COAL)
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != 0) {
                helper.fail("Storage terminal must ignore retired popup fuel button ids");
                return;
            }

            var craftingMenu = new CraftingTerminalMenu(111, player.getInventory(), core);
            int craftingSlot = findPlayerMenuSlot(craftingMenu, Items.COAL);
            int oldCraftingButton = 1000 + craftingSlot * EnergyType.values().length
                    + EnergyType.FURNACE_FUEL.ordinal();
            craftingMenu.clickMenuButton(player, oldCraftingButton);
            if (!craftingMenu.getSlot(craftingSlot).getItem().is(Items.COAL)
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != 0) {
                helper.fail("Crafting terminal must ignore retired popup fuel button ids");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void retired_previous_next_target_button_ids_are_noops(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(123, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.BLAZE_FUEL));

            if (menu.clickMenuButton(player, 17)
                    || menu.getSelectedFuelTarget() != EnergyType.BLAZE_FUEL) {
                helper.fail("Retired previous-target button id must not mutate the explicit target");
                return;
            }
            if (menu.clickMenuButton(player, 18)
                    || menu.getSelectedFuelTarget() != EnergyType.BLAZE_FUEL) {
                helper.fail("Retired next-target button id must not mutate the explicit target");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void fuel_target_ids_keep_fuel_and_brew_while_retired_bottle_is_rejected(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            int fuelTargetId = CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL);
            int brewTargetId = CraftingTerminalMenu.fuelTargetButtonId(EnergyType.BLAZE_FUEL);
            if (fuelTargetId != 19 || brewTargetId != 20) {
                helper.fail("Fuel and Brew target IDs must remain 19 and 20, got "
                        + fuelTargetId + " and " + brewTargetId);
                return;
            }

            var menu = new CraftingTerminalMenu(125, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            if (!menu.clickMenuButton(player, fuelTargetId)
                    || menu.getSelectedFuelTarget() != EnergyType.FURNACE_FUEL) {
                helper.fail("Fuel target ID 19 must select Fuel");
                return;
            }
            if (!menu.clickMenuButton(player, brewTargetId)
                    || menu.getSelectedFuelTarget() != EnergyType.BLAZE_FUEL) {
                helper.fail("Brew target ID 20 must select Brew");
                return;
            }
            boolean retiredAccepted = menu.clickMenuButton(player, 21);
            if (retiredAccepted || menu.getSelectedFuelTarget() != EnergyType.BLAZE_FUEL) {
                helper.fail("Retired Bottle target ID 21 must be rejected without changing Brew: accepted="
                        + retiredAccepted + " selected=" + menu.getSelectedFuelTarget());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void crafting_items_page_delegates_shared_view_controls(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(113, player.getInventory(), core);
            menu.clickMenuButton(player, 12);
            menu.clickMenuButton(player, 11);
            menu.clickMenuButton(player, 13);

            if (menu.getSortMode() != SortMode.QUANTITY
                    || menu.getSortOrder() != SortOrder.DESCENDING
                    || menu.getSearchMode() != SearchMode.EMI) {
                helper.fail("Crafting Items page must delegate sort, order, and search controls");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void auto_overflow_does_not_fallback_to_second_pool(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            core.storageRecordForTesting().setEnergyAmount(EnergyType.BLAZE_FUEL, Long.MAX_VALUE);
            core.insertItem(new ItemStack(Items.STONE));
            core.insertItem(new ItemStack(Items.DIRT));

            player.getInventory().setItem(0, new ItemStack(Items.BLAZE_ROD));
            var menu = new CraftingTerminalMenu(112, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            int rodSlot = findPlayerMenuSlot(menu, Items.BLAZE_ROD);
            menu.quickMoveStack(player, rodSlot);
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);

            if (!menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().is(Items.BLAZE_ROD)) {
                helper.fail("Auto overflow must preserve the complete source stack");
                return;
            }
            if (core.getEnergy(EnergyType.BLAZE_FUEL) != Long.MAX_VALUE
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != 0) {
                helper.fail("Auto overflow must not fallback from scarce Blaze to Furnace");
                return;
            }

            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (!menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().isEmpty()
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != 2400) {
                helper.fail("Explicit Furnace target should convert the preserved Blaze Rod");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void fuel_page_slot_converts_oak_logs_using_runtime_burn_time_per_item(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            ItemStack oakLogs = new ItemStack(Items.OAK_LOG, 3);
            int burnTime = oakLogs.getBurnTime(null);
            if (burnTime <= 0) {
                helper.fail("Test setup requires Oak Logs to have a positive runtime burn time");
                return;
            }

            var menu = new CraftingTerminalMenu(120, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            var input = menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT);
            if (!input.mayPlace(oakLogs)) {
                helper.fail("Fuel-page input must accept Oak Logs from runtime burn time; hardcoded whitelist rejected them");
                return;
            }

            input.set(oakLogs.copy());
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            long expected = Math.multiplyExact((long) burnTime, oakLogs.getCount());
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != expected) {
                helper.fail("Three Oak Logs must add runtime burn time per item: expected "
                        + expected + " got " + core.getEnergy(EnergyType.FURNACE_FUEL));
                return;
            }
            if (!input.getItem().isEmpty()) {
                helper.fail("Successful Fuel-page conversion must consume the complete Oak Log stack");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void transform_preview_and_max_stop_at_exact_output_capacity(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            ItemStack logs = new ItemStack(Items.OAK_LOG, 3);
            int burnTime = logs.getBurnTime(null);
            if (burnTime <= 0) {
                helper.fail("Test setup requires Oak Logs to have a positive runtime burn time");
                return;
            }
            core.storageRecordForTesting().setEnergyAmount(
                    EnergyType.FURNACE_FUEL,
                    Long.MAX_VALUE - Math.multiplyExact((long) burnTime, 2L));

            var menu = new CraftingTerminalMenu(128, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(
                    player,
                    CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            Slot input = menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT);
            input.set(logs.copy());

            if (menu.getCraftableCount() != 2) {
                helper.fail("Transform preview must expose the exact two-item output capacity, got "
                        + menu.getCraftableCount());
                return;
            }
            if (!menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON)
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != Long.MAX_VALUE
                    || !input.getItem().is(Items.OAK_LOG)
                    || input.getItem().getCount() != 1
                    || menu.getCraftableCount() != 0) {
                helper.fail("Transform Max must fill the output exactly and preserve the rejected remainder");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void fuel_page_shift_click_converts_oak_logs_using_runtime_burn_time(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            ItemStack oakLogs = new ItemStack(Items.OAK_LOG, 2);
            int burnTime = oakLogs.getBurnTime(null);
            if (burnTime <= 0) {
                helper.fail("Test setup requires Oak Logs to have a positive runtime burn time");
                return;
            }

            player.getInventory().setItem(0, oakLogs.copy());
            var menu = new CraftingTerminalMenu(121, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            int playerSlot = findPlayerMenuSlot(menu, Items.OAK_LOG);
            ItemStack moved = menu.quickMoveStack(player, playerSlot);
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);

            if (moved.isEmpty()) {
                helper.fail("Fuel-page Shift-click must convert Oak Logs; hardcoded whitelist rejected them");
                return;
            }
            if (!menu.getSlot(playerSlot).getItem().isEmpty()) {
                helper.fail("Fuel-page Shift-click must consume the complete Oak Log source stack");
                return;
            }
            long expected = Math.multiplyExact((long) burnTime, oakLogs.getCount());
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != expected) {
                helper.fail("Shift-clicked Oak Logs must add runtime burn time per item: expected "
                        + expected + " got " + core.getEnergy(EnergyType.FURNACE_FUEL));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void runtime_fuel_accepts_multiple_vanilla_fuel_shapes_at_exact_burn_time(
            GameTestHelper helper
    ) {
        withCore(helper, (core, player) -> {
            ItemStack[] fuels = {
                    new ItemStack(Items.STICK, 4),
                    new ItemStack(Items.CHARCOAL, 2),
                    new ItemStack(Items.COAL_BLOCK),
                    new ItemStack(Items.DRIED_KELP_BLOCK)
            };
            long expected = 0;
            for (ItemStack fuel : fuels) {
                int burnTime = fuel.getBurnTime(null);
                if (burnTime <= 0) {
                    helper.fail("Representative runtime fuel has no burn time: " + fuel);
                    return;
                }
                expected = Math.addExact(
                        expected,
                        Math.multiplyExact((long) burnTime, fuel.getCount()));
                ItemStack input = fuel.copy();
                if (!core.addFuel(input, EnergyType.FURNACE_FUEL) || !input.isEmpty()) {
                    helper.fail("Runtime Fuel rejected or partially consumed " + fuel);
                    return;
                }
                if (core.getEnergy(EnergyType.FURNACE_FUEL) != expected) {
                    helper.fail("Runtime Fuel total drifted after " + fuel
                            + ": expected " + expected
                            + " got " + core.getEnergy(EnergyType.FURNACE_FUEL));
                    return;
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void transform_keeps_zero_runtime_burn_time_item_unconverted(
            GameTestHelper helper
    ) {
        withCore(helper, (core, player) -> {
            ItemStack stone = new ItemStack(Items.STONE, 2);
            if (stone.getBurnTime(null) != 0) {
                helper.fail("Test setup requires Stone to have zero runtime burn time");
                return;
            }

            var menu = new CraftingTerminalMenu(122, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            var input = menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT);
            if (!input.mayPlace(stone)) {
                helper.fail("Transform input must accept an item before discovering its uses");
                return;
            }

            menu.setCarried(stone.copy());
            menu.clicked(CraftingTerminalMenu.FUEL_INPUT_SLOT, 0, ClickType.PICKUP, player);
            if (!menu.getCarried().isEmpty()
                    || !input.getItem().is(Items.STONE)
                    || input.getItem().getCount() != 2
                    || !menu.getTransformUses().isEmpty()
                    || menu.clickMenuButton(player, 2)) {
                helper.fail("Zero-use Transform input must stay visible and unconverted");
                return;
            }
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 0) {
                helper.fail("Rejected non-fuel must not mutate Fuel energy");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void core_runtime_fuel_overflow_preserves_stack_and_energy(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            ItemStack oakLog = new ItemStack(Items.OAK_LOG);
            int burnTime = oakLog.getBurnTime(null);
            if (burnTime <= 0) {
                helper.fail("Test setup requires Oak Logs to have a positive runtime burn time");
                return;
            }
            if (!core.addFuel(oakLog, EnergyType.FURNACE_FUEL)) {
                helper.fail("Core runtime Fuel commit must accept Oak Logs before overflow validation; hardcoded whitelist rejected them");
                return;
            }
            if (!oakLog.isEmpty() || core.getEnergy(EnergyType.FURNACE_FUEL) != burnTime) {
                helper.fail("Core runtime Fuel precondition did not add exactly one Oak Log burn time");
                return;
            }

            ItemStack overflowStack = new ItemStack(Items.OAK_LOG, 2);
            long stackValue = Math.multiplyExact((long) burnTime, overflowStack.getCount());
            long originalEnergy = Long.MAX_VALUE - stackValue + 1;
            core.storageRecordForTesting().setEnergyAmount(EnergyType.FURNACE_FUEL, originalEnergy);

            if (core.addFuel(overflowStack, EnergyType.FURNACE_FUEL)) {
                helper.fail("Runtime Fuel addition must fail closed when the exact stack value overflows the pool");
                return;
            }
            if (!overflowStack.is(Items.OAK_LOG) || overflowStack.getCount() != 2) {
                helper.fail("Overflow rejection must preserve the complete runtime Fuel stack");
                return;
            }
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != originalEnergy) {
                helper.fail("Overflow rejection must preserve the original Fuel energy");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void dedicated_input_converts_and_keeps_stacked_container_remainders(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(102, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));

            ItemStack lavaBuckets = new ItemStack(Items.LAVA_BUCKET);
            lavaBuckets.set(DataComponents.MAX_STACK_SIZE, 3);
            lavaBuckets.setCount(3);
            menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).set(lavaBuckets);
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);

            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 60000) {
                helper.fail("Dedicated input should convert all three Lava Buckets");
                return;
            }
            int buckets = player.getInventory().countItem(Items.BUCKET);
            if (buckets != 3) {
                helper.fail("Dedicated input should return three empty bucket remainders, got " + buckets);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void transform_input_preserves_no_use_incompatible_and_overflow_inputs(
            GameTestHelper helper
    ) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(103, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            var input = menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT);
            if (!input.mayPlace(new ItemStack(Items.STONE))) {
                helper.fail("Transform input must accept non-matching items for use inspection");
                return;
            }

            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.BLAZE_FUEL));
            input.set(new ItemStack(Items.COAL));
            if (!input.mayPlace(new ItemStack(Items.COAL))
                    || menu.clickMenuButton(player, 2)
                    || !input.getItem().is(Items.COAL)) {
                helper.fail("An incompatible explicit transform must preserve its input");
                return;
            }

            core.storageRecordForTesting().setEnergyAmount(EnergyType.FURNACE_FUEL, Long.MAX_VALUE);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            input.set(new ItemStack(Items.COAL));
            if (menu.clickMenuButton(player, 2)) {
                helper.fail("Overflowing transform must fail");
                return;
            }
            if (!input.getItem().is(Items.COAL) || input.getItem().getCount() != 1) {
                helper.fail("Overflow failure must preserve fuel in the dedicated input");
                return;
            }
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != Long.MAX_VALUE) {
                helper.fail("Overflow failure must not wrap or mutate the full pool");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void menu_close_returns_leftover_fuel_input_to_player(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            core.storageRecordForTesting().setEnergyAmount(EnergyType.FURNACE_FUEL, Long.MAX_VALUE);

            var menu = new CraftingTerminalMenu(104, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).set(new ItemStack(Items.COAL, 2));
            menu.removed(player);

            if (!menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().isEmpty()) {
                helper.fail("Closing menu must clear the dedicated fuel input");
                return;
            }
            if (player.getInventory().countItem(Items.COAL) != 2) {
                helper.fail("Closing menu must return rejected fuel to the player");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void leaving_fuel_page_returns_unconverted_input_before_hiding_slot(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            core.storageRecordForTesting().setEnergyAmount(EnergyType.FURNACE_FUEL, Long.MAX_VALUE);

            var menu = new CraftingTerminalMenu(108, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).set(new ItemStack(Items.COAL, 2));
            menu.clickMenuButton(player, CraftingTerminalMenu.STORAGE_PAGE_BUTTON);

            if (menu.getPage() != CraftingTerminalPage.STORAGE) {
                helper.fail("Storage page button should complete after returning fuel input");
                return;
            }
            if (!menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().isEmpty()) {
                helper.fail("Leaving Fuel page must not hide occupied input");
                return;
            }
            if (player.getInventory().countItem(Items.COAL) != 2) {
                helper.fail("Leaving Fuel page must return rejected fuel to player");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void page_target_and_all_long_energy_values_sync_to_buf_menu(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            long[] expectedAmounts = {
                    0L,
                    0xFFFFL,
                    0x1_0000L,
                    0xFFFF_FFFFL,
                    0x1_0000_0000L,
                    0x1234_8000_FFFE_FFFFL,
                    Long.MAX_VALUE - 1,
                    Long.MAX_VALUE
            };
            for (EnergyType type : EnergyType.values()) {
                core.storageRecordForTesting().setEnergyAmount(type, expectedAmounts[type.ordinal()]);
            }
            core.storageRecordForTesting().setDescriptorAmount(
                    MachineEnergyTable.AXE_ID, Long.MAX_VALUE - 2);
            long stoneInserted = core.insertItem(new ItemStack(Items.STONE));
            long dirtInserted = core.insertItem(new ItemStack(Items.DIRT));
            if (stoneInserted != 1 || dirtInserted != 1 || core.getTypeCount() != 2) {
                helper.fail("Fuel type-capacity fixture could not store two item types");
                return;
            }

            var serverMenu = new CraftingTerminalMenu(105, player.getInventory(), core);
            serverMenu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            serverMenu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.BLAZE_FUEL));

            var byteBuf = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
            byteBuf.writeBlockPos(core.getBlockPos());
            byteBuf.writeBlockPos(core.getBlockPos());
            byteBuf.writeBoolean(false);
            MachineEnergyTable.writeSnapshot(byteBuf, MachineEnergyTable.entries());
            var clientMenu = new CraftingTerminalMenu(106, player.getInventory(), byteBuf);

            var serverData = dataSlots(serverMenu);
            var clientData = dataSlots(clientMenu);
            if (serverData.size() != 102 || clientData.size() != 102) {
                helper.fail("Crafting fuel/resource/output/Axe Energy/unlimited-capacity/resource-view sync requires exact 102-slot compatibility parity, server="
                        + serverData.size() + " client=" + clientData.size());
                return;
            }
            for (int i = 0; i < serverData.size(); i++) {
                var wire = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                var packet = new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(
                        serverMenu.containerId, i, serverData.get(i).get());
                net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.encode(wire, packet);
                var decoded = net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.decode(wire);
                if (decoded.getContainerId() != serverMenu.containerId || decoded.getId() != i) {
                    helper.fail("Container data packet identity changed at slot " + i);
                    return;
                }
                clientData.get(decoded.getId()).set(decoded.getValue());
            }
            if (clientMenu.getPage() != CraftingTerminalPage.TRANSFORM
                    || clientMenu.getSelectedFuelTarget() != EnergyType.BLAZE_FUEL) {
                helper.fail("Client menu did not receive page and selected target");
                return;
            }
            if (clientMenu.getTypeCount() != 2
                    || clientMenu.getMaxTypes() != core.getTotalTypeSlots()) {
                helper.fail("Fuel page did not retain base storage type capacity: types="
                        + clientMenu.getTypeCount() + " max=" + clientMenu.getMaxTypes());
                return;
            }
            for (EnergyType type : EnergyType.values()) {
                long expected = expectedAmounts[type.ordinal()];
                if (clientMenu.getEnergyAmount(type) != expected) {
                    helper.fail(type.getId() + " long sync mismatch: expected " + expected
                            + " got " + clientMenu.getEnergyAmount(type));
                    return;
                }
            }
            if (clientMenu.getAxeEnergyAmount() != Long.MAX_VALUE - 2
                    || clientMenu.hasInfiniteAxeEnergy()) {
                helper.fail("Finite Axe Energy long sync mismatch");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void recipe_resource_longs_survive_signed_short_wire(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            long available = 0x1234_8000_FFFE_FFFFL;
            core.insertItemCount(ItemKey.of(new ItemStack(Items.COBBLESTONE)),
                    available, Action.EXECUTE, Actor.EMPTY);
            core.insertItemCount(ItemKey.of(new ItemStack(Items.STONE)),
                    1, Action.EXECUTE, Actor.EMPTY);
            core.storageRecordForTesting().setEnergyAmount(EnergyType.SMELTING_ENERGY, available);
            core.storageRecordForTesting().setEnergyAmount(EnergyType.FURNACE_FUEL, available);
            core.getMachineContainer().setItem(
                    MachineEnergyTable.FURNACE_SLOT, new ItemStack(Items.FURNACE));

            var serverMenu = new CraftingTerminalMenu(120, player.getInventory(), core);
            int stoneSlot = -1;
            for (int slot = 0; slot < StorageTerminalMenu.DISPLAY_SLOTS; slot++) {
                if (serverMenu.getSlot(slot).getItem().is(Items.STONE)) {
                    stoneSlot = slot;
                    break;
                }
            }
            var smelting = helper.getLevel().getRecipeManager()
                    .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING).stream()
                    .filter(holder -> holder.value().getResultItem(helper.getLevel().registryAccess()).is(Items.STONE))
                    .filter(holder -> holder.value().getIngredients().stream()
                            .anyMatch(ingredient -> ingredient.test(new ItemStack(Items.COBBLESTONE))))
                    .findFirst().orElse(null);
            if (stoneSlot < 0 || smelting == null
                    || !serverMenu.selectRecipe(helper.getLevel(), stoneSlot, smelting.id(), player)) {
                helper.fail("Could not select the Stone smelting recipe for resource wire test");
                return;
            }

            var byteBuf = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
            byteBuf.writeBlockPos(core.getBlockPos());
            byteBuf.writeBlockPos(core.getBlockPos());
            byteBuf.writeBoolean(false);
            MachineEnergyTable.writeSnapshot(byteBuf, MachineEnergyTable.entries());
            var clientMenu = new CraftingTerminalMenu(121, player.getInventory(), byteBuf);
            var serverData = dataSlots(serverMenu);
            var clientData = dataSlots(clientMenu);
            for (int i = 0; i < serverData.size(); i++) {
                var wire = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                var packet = new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(
                        serverMenu.containerId, i, serverData.get(i).get());
                net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.encode(wire, packet);
                var decoded = net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.decode(wire);
                clientData.get(decoded.getId()).set(decoded.getValue());
            }
            int metadataStart = CraftingTerminalMenu.MACHINE_SLOT_START + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
            for (int slot = metadataStart; slot < serverMenu.slots.size(); slot++) {
                clientMenu.getSlot(slot).set(serverMenu.getSlot(slot).getItem().copy());
            }

            var ingredients = clientMenu.getIngredientPreview();
            var energies = clientMenu.getEnergyPreview();
            if (ingredients.size() != 1
                    || !ingredients.getFirst().stack().is(Items.COBBLESTONE)
                    || ingredients.getFirst().available() != available
                    || ingredients.getFirst().required() != 1) {
                helper.fail("Ingredient long wire mismatch: " + ingredients);
                return;
            }
            if (energies.size() != 2
                    || energies.get(0).type() != EnergyType.SMELTING_ENERGY
                    || energies.get(0).available() != available
                    || energies.get(0).required() != 200
                    || energies.get(1).type() != EnergyType.FURNACE_FUEL
                    || energies.get(1).available() != available
                    || energies.get(1).required() != 200) {
                helper.fail("Energy resource wire mismatch: " + energies);
                return;
            }
            var presentation = clientMenu.getRecipePresentation();
            if (!presentation.recipeId().equals(smelting.id())
                    || presentation.kind() != RecipePresentationKind.COOKING
                    || presentation.inputs().stream()
                    .noneMatch(stack -> stack.is(Items.COBBLESTONE))
                    || !presentation.output().is(Items.STONE)
                    || !presentation.station().is(Items.FURNACE)
                    || presentation.resources().size() != 3) {
                helper.fail("Exact recipe presentation did not survive menu wire sync: " + presentation);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void open_fuel_page_tracks_live_energy_listener_updates(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(107, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            ItemStack coal = new ItemStack(Items.COAL, 2);
            if (!core.addFuel(coal, EnergyType.FURNACE_FUEL)) {
                helper.fail("Test setup could not add coal fuel");
                return;
            }
            menu.broadcastChanges();
            if (menu.getEnergyAmount(EnergyType.FURNACE_FUEL) != 3200) {
                helper.fail("Open Fuel page should track listener-driven pool updates");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void open_fuel_page_tracks_live_type_count_and_capacity(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(120, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            int initialCapacity = core.getTotalTypeSlots();

            if (core.insertItem(new ItemStack(Items.STONE)) != 1) {
                helper.fail("Test setup could not insert a stored type");
                return;
            }
            menu.broadcastChanges();
            if (menu.getTypeCount() != 1) {
                helper.fail("Open Fuel page should track live stored type count");
                return;
            }

            helper.getLevel().setBlock(core.getBlockPos().west(),
                    MagicStorage.STORAGE_UNIT_T2.get().defaultBlockState(), Block.UPDATE_ALL);
            core.rebuildNetwork(helper.getLevel());
            menu.broadcastChanges();
            if (core.getTotalTypeSlots() <= initialCapacity
                    || menu.getMaxTypes() != core.getTotalTypeSlots()) {
                helper.fail("Open Fuel page should track live total type capacity: menu="
                        + menu.getMaxTypes() + " core=" + core.getTotalTypeSlots());
                return;
            }

            core.extractItem(ItemKey.of(new ItemStack(Items.STONE)), 1, Action.EXECUTE, Actor.EMPTY);
            menu.broadcastChanges();
            if (menu.getTypeCount() != 0) {
                helper.fail("Open Fuel page should track removal of the final stored type");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void fuel_page_shift_move_installs_and_returns_machine_stack(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            player.getInventory().setItem(0, new ItemStack(Items.FURNACE, 3));
            var menu = new CraftingTerminalMenu(114, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            int playerSlot = findPlayerMenuSlot(menu, Items.FURNACE);
            int furnaceMachineSlot = CraftingTerminalMenu.FUEL_INPUT_SLOT + 1;
            menu.quickMoveStack(player, playerSlot);

            ItemStack installed = menu.getSlot(furnaceMachineSlot).getItem();
            if (!installed.is(Items.FURNACE) || installed.getCount() != 3) {
                helper.fail("Fuel-page shift move must install all three Furnaces, got " + installed);
                return;
            }
            if (!menu.getSlot(playerSlot).getItem().isEmpty()) {
                helper.fail("Installing machines must consume the source player stack");
                return;
            }

            long before = core.getEnergy(EnergyType.SMELTING_ENERGY);
            core.tick();
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) - before != 3) {
                helper.fail("Three installed Furnaces must generate exactly +3 in one tick");
                return;
            }

            menu.quickMoveStack(player, furnaceMachineSlot);
            if (!menu.getSlot(furnaceMachineSlot).getItem().isEmpty()
                    || player.getInventory().countItem(Items.FURNACE) != 3) {
                helper.fail("Shift-moving an installed machine must return the complete stack to the player");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void fuel_page_shift_move_routes_later_entries_in_all_three_categories_by_descriptor(
            GameTestHelper helper
    ) {
        withCore(helper, (core, player) -> {
            ItemStack axe = new ItemStack(Items.IRON_AXE);
            axe.setDamageValue(axe.getMaxDamage() - 7);
            player.getInventory().setItem(0, new ItemStack(Items.SMOKER, 3));
            player.getInventory().setItem(1, new ItemStack(Items.SMITHING_TABLE));
            player.getInventory().setItem(2, axe);

            var menu = new CraftingTerminalMenu(129, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            int smokerSource = findPlayerMenuSlot(menu, Items.SMOKER);
            int smithingSource = findPlayerMenuSlot(menu, Items.SMITHING_TABLE);
            int axeSource = findPlayerMenuSlot(menu, Items.IRON_AXE);

            menu.quickMoveStack(player, smokerSource);
            menu.quickMoveStack(player, smithingSource);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.quickMoveStack(player, axeSource);
            menu.clickMenuButton(player, 2);

            ItemStack installedSmokers = menu.getSlot(
                    CraftingTerminalMenu.MACHINE_SLOT_START + MachineEnergyTable.SMOKER_SLOT).getItem();
            ItemStack installedSmithingTable = menu.getSlot(
                    CraftingTerminalMenu.MACHINE_SLOT_START + MachineEnergyTable.SMITHING_TABLE_SLOT).getItem();
            if (!installedSmokers.is(Items.SMOKER) || installedSmokers.getCount() != 3
                    || !installedSmithingTable.is(Items.SMITHING_TABLE)
                    || installedSmithingTable.getCount() != 1
                    || core.getAxeEnergy() != 7) {
                helper.fail("Stations and Transform must route machines and consumables to their own domains: "
                        + installedSmokers + ", " + installedSmithingTable
                        + ", axe=" + core.getAxeEnergy());
                return;
            }
            if (!menu.getSlot(smokerSource).getItem().isEmpty()
                    || !menu.getSlot(smithingSource).getItem().isEmpty()
                    || !menu.getSlot(axeSource).getItem().isEmpty()) {
                helper.fail("Descriptor routing must consume each accepted player source exactly once");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void timed_station_shift_move_accumulates_past_one_stack_without_loss(
            GameTestHelper helper
    ) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(130, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            int machineSlot = CraftingTerminalMenu.MACHINE_SLOT_START
                    + MachineEnergyTable.FURNACE_SLOT;
            int[] batches = {64, 64, 2};

            for (int batch : batches) {
                player.getInventory().setItem(0, new ItemStack(Items.FURNACE, batch));
                int source = findPlayerMenuSlot(menu, Items.FURNACE);
                menu.quickMoveStack(player, source);
                if (!menu.getSlot(source).getItem().isEmpty()) {
                    helper.fail("Timed station must accept repeated Shift-click batches past 64; "
                            + "rejected source " + menu.getSlot(source).getItem()
                            + " after installed=" + menu.getSlot(machineSlot).getItem().getCount());
                    return;
                }
            }

            ItemStack installed = menu.getSlot(machineSlot).getItem();
            if (!installed.is(Items.FURNACE) || installed.getCount() != 130) {
                helper.fail("Three lossless batches of 64 + 64 + 2 must install exactly 130 Furnaces: "
                        + installed);
                return;
            }
            long before = core.getEnergy(EnergyType.SMELTING_ENERGY);
            core.tick();
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) - before != 130) {
                helper.fail("130 installed Furnaces must contribute exactly +130 per tick");
                return;
            }

            menu.quickMoveStack(player, machineSlot);
            if (!menu.getSlot(machineSlot).getItem().isEmpty()
                    || player.getInventory().countItem(Items.FURNACE) != 130) {
                helper.fail("Removing an over-64 timed station stack must return all 130 machines");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void timed_station_shift_move_stops_at_integer_max_without_overflow_or_loss(
            GameTestHelper helper
    ) {
        withCore(helper, (core, player) -> {
            int machineSlot = CraftingTerminalMenu.MACHINE_SLOT_START
                    + MachineEnergyTable.FURNACE_SLOT;
            core.getMachineContainer().setItem(
                    MachineEnergyTable.FURNACE_SLOT,
                    new ItemStack(Items.FURNACE, Integer.MAX_VALUE - 32));
            if (core.getMachineContainer().getItem(
                    MachineEnergyTable.FURNACE_SLOT).getCount() != Integer.MAX_VALUE - 32) {
                helper.fail("Test setup could not seed the near-cap workstation aggregate");
                return;
            }

            player.getInventory().setItem(0, new ItemStack(Items.FURNACE, 64));
            var menu = new CraftingTerminalMenu(183, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            int source = findPlayerMenuSlot(menu, Items.FURNACE);
            ItemStack moved = menu.quickMoveStack(player, source);

            ItemStack installed = menu.getSlot(machineSlot).getItem();
            ItemStack remainder = menu.getSlot(source).getItem();
            if (moved.isEmpty()
                    || !installed.is(Items.FURNACE)
                    || installed.getCount() != Integer.MAX_VALUE
                    || !remainder.is(Items.FURNACE)
                    || remainder.getCount() != 32) {
                helper.fail("Near-cap Shift-click must install exactly the available 32 slots "
                        + "and preserve the other 32 machines: installed=" + installed
                        + ", remainder=" + remainder + ", moved=" + moved);
                return;
            }

            ItemStack returned = menu.quickMoveStack(player, machineSlot);
            ItemStack stillInstalled = menu.getSlot(machineSlot).getItem();
            int inventoryCount = player.getInventory().countItem(Items.FURNACE);
            int expectedInventoryCount = StorageTerminalMenu.PLAYER_INVENTORY_SLOTS * 64;
            int expectedInstalledCount = Integer.MAX_VALUE - (expectedInventoryCount - 32);
            long conserved = (long) inventoryCount
                    + (stillInstalled.is(Items.FURNACE) ? stillInstalled.getCount() : 0);
            if (returned.isEmpty()
                    || inventoryCount != expectedInventoryCount
                    || !stillInstalled.is(Items.FURNACE)
                    || stillInstalled.getCount() != expectedInstalledCount
                    || conserved != (long) Integer.MAX_VALUE + 32) {
                helper.fail("Near-cap removal must fill player slots without signed overflow or loss: "
                        + "installed=" + stillInstalled + ", inventory=" + inventoryCount
                        + ", returned=" + returned + ", conserved=" + conserved);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void instant_station_accepts_one_and_rejects_second_without_consuming(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            player.getInventory().setItem(0, new ItemStack(Items.CRAFTING_TABLE, 2));
            var menu = new CraftingTerminalMenu(122, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            int playerSlot = findPlayerMenuSlot(menu, Items.CRAFTING_TABLE);
            int stationSlot = CraftingTerminalMenu.MACHINE_SLOT_START
                    + MachineEnergyTable.CRAFTING_TABLE_SLOT;

            menu.quickMoveStack(player, playerSlot);
            ItemStack installed = menu.getSlot(stationSlot).getItem();
            ItemStack rejected = menu.getSlot(playerSlot).getItem();
            if (!installed.is(Items.CRAFTING_TABLE) || installed.getCount() != 1) {
                helper.fail("Instant station must install exactly one Crafting Table: " + installed);
                return;
            }
            if (!rejected.is(Items.CRAFTING_TABLE) || rejected.getCount() != 1) {
                helper.fail("Second instant station must remain in the source slot: " + rejected);
                return;
            }

            menu.quickMoveStack(player, playerSlot);
            if (menu.getSlot(stationSlot).getItem().getCount() != 1
                    || menu.getSlot(playerSlot).getItem().getCount() != 1) {
                helper.fail("A full instant-station slot must reject a second insertion without consuming it");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void axes_convert_to_exact_finite_energy_and_never_remain_installed(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            ItemStack axe = new ItemStack(Items.IRON_AXE);
            axe.setDamageValue(axe.getMaxDamage() - 5);
            axe.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.UNBREAKING), 2);
            player.getInventory().setItem(0, axe);
            var menu = new CraftingTerminalMenu(123, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            int playerSlot = findPlayerMenuSlot(menu, Items.IRON_AXE);
            int axeSlot = CraftingTerminalMenu.MACHINE_SLOT_START + MachineEnergyTable.AXE_SLOT;

            menu.quickMoveStack(player, playerSlot);
            menu.clickMenuButton(player, 2);
            if (core.getAxeEnergy() != 15 || core.hasInfiniteAxeEnergy()) {
                helper.fail("Five remaining durability with Unbreaking II must become exactly 15 Axe Energy");
                return;
            }
            if (!menu.getSlot(playerSlot).getItem().isEmpty()
                    || !menu.getSlot(axeSlot).getItem().isEmpty()
                    || !core.getMachineContainer().getItem(MachineEnergyTable.AXE_SLOT).isEmpty()) {
                helper.fail("An accepted axe must be consumed instead of becoming installed equipment");
                return;
            }

            ItemStack mendingOnly = new ItemStack(Items.IRON_AXE);
            mendingOnly.setDamageValue(mendingOnly.getMaxDamage() - 4);
            mendingOnly.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.MENDING), 1);
            menu.setCarried(mendingOnly);
            menu.clicked(CraftingTerminalMenu.FUEL_INPUT_SLOT, 0, ClickType.PICKUP, player);
            menu.clickMenuButton(player, 2);
            if (core.getAxeEnergy() != 19 || !menu.getCarried().isEmpty()
                    || !menu.getSlot(axeSlot).getItem().isEmpty()) {
                helper.fail("Mending and unrelated enchantments must not multiply Axe Energy: "
                        + core.getAxeEnergy());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void finite_axe_energy_accumulates_and_checked_overflow_preserves_input(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            ItemStack first = new ItemStack(Items.WOODEN_AXE);
            first.setDamageValue(first.getMaxDamage() - 3);
            ItemStack second = new ItemStack(Items.STONE_AXE);
            second.setDamageValue(second.getMaxDamage() - 7);
            if (!core.addAxeEnergy(first) || !core.addAxeEnergy(second)
                    || core.getAxeEnergy() != 10 || !first.isEmpty() || !second.isEmpty()) {
                helper.fail("Multiple finite axes must accumulate atomically to ten Axe Energy");
                return;
            }

            core.storageRecordForTesting().setDescriptorAmount(
                    MachineEnergyTable.AXE_ID, Long.MAX_VALUE - 1);
            ItemStack rejected = new ItemStack(Items.IRON_AXE);
            rejected.setDamageValue(rejected.getMaxDamage() - 2);
            if (core.addAxeEnergy(rejected)) {
                helper.fail("Checked Axe Energy overflow must reject the complete input");
                return;
            }
            if (core.getAxeEnergy() != Long.MAX_VALUE - 1 || rejected.getCount() != 1) {
                helper.fail("Overflow rejection must preserve both stored energy and input axe");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void unbreakable_axe_sets_infinite_and_rejects_further_axes(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            ItemStack unbreakable = new ItemStack(Items.DIAMOND_AXE);
            unbreakable.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            if (!core.addAxeEnergy(unbreakable) || !unbreakable.isEmpty()
                    || !core.hasInfiniteAxeEnergy()) {
                helper.fail("Unbreakable axe must be consumed into explicit infinite Axe Energy");
                return;
            }

            ItemStack rejected = new ItemStack(Items.IRON_AXE);
            if (core.addAxeEnergy(rejected) || rejected.getCount() != 1) {
                helper.fail("Further axes must be rejected without consumption after Axe Energy is infinite");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void descriptor_registry_snapshot_is_frozen_and_reused(GameTestHelper helper) {
        var first = MachineEnergyTable.entries();
        var second = MachineEnergyTable.entries();
        if (first != second) {
            helper.fail("Frozen descriptor registry snapshot must be reused instead of rebuilt per Core tick");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void machine_slots_enforce_page_exact_mapping_and_forged_click_guard(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            var menu = new CraftingTerminalMenu(115, player.getInventory(), core);
            int start = CraftingTerminalMenu.FUEL_INPUT_SLOT + 1;
            ItemStack[] expected = {
                    new ItemStack(Items.FURNACE),
                    new ItemStack(Items.BLAST_FURNACE),
                    new ItemStack(Items.SMOKER),
                    new ItemStack(Items.CAMPFIRE),
                    new ItemStack(Items.BREWING_STAND),
                    new ItemStack(Items.CRAFTING_TABLE),
                    new ItemStack(Items.STONECUTTER),
                    new ItemStack(Items.SMITHING_TABLE),
                    new ItemStack(Items.IRON_AXE)
            };

            if (CraftingTerminalMenu.MACHINE_SLOT_COUNT != MachineDescriptorApi.MAX_DESCRIPTORS
                    || menu.getMachineDescriptors().size() != expected.length) {
                helper.fail("Menu must keep a fixed descriptor bank while exposing all registered descriptors");
                return;
            }

            for (int i = 0; i < expected.length; i++) {
                if (menu.getSlot(start + i).isActive() || menu.getSlot(start + i).mayPlace(expected[i])) {
                    helper.fail("Machine slot " + i + " must be hidden and reject placement on Items page");
                    return;
                }
            }
            menu.setCarried(new ItemStack(Items.FURNACE));
            menu.clicked(start, 0, ClickType.PICKUP, player);
            if (!menu.getCarried().is(Items.FURNACE) || !menu.getSlot(start).getItem().isEmpty()) {
                helper.fail("Forged Items-page machine-slot click must be a no-op");
                return;
            }
            menu.setCarried(ItemStack.EMPTY);

            menu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            for (int i = 0; i < MachineEnergyTable.AXE_SLOT; i++) {
                var slot = menu.getSlot(start + i);
                if (!slot.isActive() || !slot.mayPlace(expected[i])) {
                    helper.fail("Station slot " + i + " must accept its exact machine");
                    return;
                }
                ItemStack wrong = expected[(i + 1) % expected.length];
                if (slot.mayPlace(wrong) || slot.mayPlace(new ItemStack(Items.STONE))) {
                    helper.fail("Fuel machine slot " + i + " accepted a wrong machine or ordinary item");
                    return;
                }
            }
            if (menu.getSlot(start + MachineEnergyTable.AXE_SLOT).isActive()
                    || menu.getSlot(start + MachineEnergyTable.AXE_SLOT)
                    .mayPlace(new ItemStack(Items.DIAMOND_AXE))) {
                helper.fail("Consumable descriptors must not appear as installable stations");
                return;
            }
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            if (!menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                    .mayPlace(new ItemStack(Items.DIAMOND_AXE))
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                    .mayPlace(new ItemStack(Items.DIAMOND_PICKAXE))) {
                helper.fail("Transform input must accept every item before use discovery");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void installed_machine_survives_page_exit_close_and_remote_reopen(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            player.getInventory().setItem(0, new ItemStack(Items.BREWING_STAND, 2));
            var localMenu = new CraftingTerminalMenu(116, player.getInventory(), core);
            localMenu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            int playerSlot = findPlayerMenuSlot(localMenu, Items.BREWING_STAND);
            int brewingMachineSlot = CraftingTerminalMenu.FUEL_INPUT_SLOT + 5;
            localMenu.quickMoveStack(player, playerSlot);
            localMenu.clickMenuButton(player, CraftingTerminalMenu.STORAGE_PAGE_BUTTON);
            if (!localMenu.getSlot(brewingMachineSlot).getItem().is(Items.BREWING_STAND)
                    || localMenu.getSlot(brewingMachineSlot).getItem().getCount() != 2) {
                helper.fail("Leaving Fuel page must keep installed Brewing Stands in the Core");
                return;
            }
            localMenu.removed(player);

            var remotePlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var remoteMenu = new CraftingTerminalMenu(117, remotePlayer.getInventory(), core, core.getBlockPos(), true);
            remoteMenu.clickMenuButton(remotePlayer, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            ItemStack reopened = remoteMenu.getSlot(brewingMachineSlot).getItem();
            if (!reopened.is(Items.BREWING_STAND) || reopened.getCount() != 2) {
                helper.fail("Remote reopen must see the same Core-owned installed machines, got " + reopened);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void items_page_stores_machine_as_ordinary_network_item(GameTestHelper helper) {
        withCore(helper, (core, player) -> {
            player.getInventory().setItem(0, new ItemStack(Items.SMOKER, 2));
            var menu = new CraftingTerminalMenu(118, player.getInventory(), core);
            int playerSlot = findPlayerMenuSlot(menu, Items.SMOKER);
            menu.quickMoveStack(player, playerSlot);

            if (core.getItemCount(ItemKey.of(new ItemStack(Items.SMOKER))) != 2) {
                helper.fail("Items page must store Smoker blocks as ordinary network items");
                return;
            }
            core.tick();
            if (core.getEnergy(EnergyType.SMOKING_ENERGY) != 0) {
                helper.fail("Machines stored in the ordinary network must not generate machine energy");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "terminalflowtests.platform", batch = "fuel_page")
    public static void conflicted_core_pauses_generation_rejects_install_and_allows_retrieval(GameTestHelper helper) {
        var level = helper.getLevel();
        var firstPos = helper.absolutePos(new BlockPos(1, 3, 1));
        var secondPos = firstPos.east();
        level.setBlock(firstPos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(secondPos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(firstPos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("First Core not found");
                return;
            }
            core.rebuildNetwork(level);
            core.getMachineContainer().setItem(0, new ItemStack(Items.FURNACE, 2));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(119, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            var machineSlot = menu.getSlot(CraftingTerminalMenu.MACHINE_SLOT_START);
            if (machineSlot.mayPlace(new ItemStack(Items.FURNACE))) {
                helper.fail("Conflicted Core must reject new machine installation");
                return;
            }
            long before = core.getEnergy(EnergyType.SMELTING_ENERGY);
            core.tick();
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != before) {
                helper.fail("Conflicted Core must pause installed-machine generation");
                return;
            }

            menu.quickMoveStack(player, CraftingTerminalMenu.MACHINE_SLOT_START);
            if (!machineSlot.getItem().isEmpty() || player.getInventory().countItem(Items.FURNACE) != 2) {
                helper.fail("Conflict must not trap already-installed machines");
                return;
            }
            helper.succeed();
        });
    }

    private static void withCore(GameTestHelper helper, FuelPageBody body) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            body.run(core, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
        });
    }

    private static int findPlayerMenuSlot(StorageTerminalMenu menu, net.minecraft.world.item.Item item) {
        int end = StorageTerminalMenu.DISPLAY_SLOTS + StorageTerminalMenu.PLAYER_INVENTORY_SLOTS;
        for (int i = StorageTerminalMenu.DISPLAY_SLOTS; i < end; i++) {
            if (menu.getSlot(i).getItem().is(item)) return i;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<net.minecraft.world.inventory.DataSlot> dataSlots(
            net.minecraft.world.inventory.AbstractContainerMenu menu) {
        try {
            var field = net.minecraft.world.inventory.AbstractContainerMenu.class.getDeclaredField("dataSlots");
            field.setAccessible(true);
            return (java.util.List<net.minecraft.world.inventory.DataSlot>) field.get(menu);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface FuelPageBody {
        void run(StorageCoreBlockEntity core, net.minecraft.world.entity.player.Player player);
    }
}
