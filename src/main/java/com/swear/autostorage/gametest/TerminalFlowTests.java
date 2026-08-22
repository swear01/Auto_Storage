package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

@GameTestHolder(AutoStorage.MODID)
public class TerminalFlowTests {

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void generated_work_only_invalidates_craftable_cache_at_thresholds(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(
                corePos,
                AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(
                corePos.east(),
                AutoStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(3, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not found");
                return;
            }
            core.rebuildNetwork(level);
            long initial = core.getCraftableRevision();
            if (!core.applyResourceTransaction(
                    java.util.Map.of(
                            StorageResourceBridge.energyKey(EnergyType.FURNACE_FUEL),
                            1L),
                    Action.EXECUTE,
                    Actor.EMPTY)) {
                helper.fail("Generated work transaction was rejected");
                return;
            }
            if (core.getCraftableRevision() != initial) {
                helper.fail("Positive generated work invalidated the shared Craftable cache");
                return;
            }
            if (core.insertItem(new ItemStack(Items.STONE)) != 1) {
                helper.fail("Item transaction was rejected");
                return;
            }
            if (core.getCraftableRevision() == initial) {
                helper.fail("Item mutation did not invalidate the shared Craftable cache");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void gui_runtime_fixture_seeds_every_item_and_installable_station(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(
                corePos,
                AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(
                corePos.east(),
                AutoStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(),
                Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not found");
                return;
            }
            core.rebuildNetwork(level);
            GuiRuntimeFixture.SeedSummary summary =
                    GuiRuntimeFixture.seedCore(core, 7);
            long expectedTypes = BuiltInRegistries.ITEM.entrySet().stream()
                    .map(entry -> entry.getValue().getDefaultInstance())
                    .filter(stack -> !stack.isEmpty())
                    .count();
            if (summary.itemTypes() != expectedTypes) {
                helper.fail("Runtime item fixture missed registered items");
                return;
            }
            for (var entry : BuiltInRegistries.ITEM.entrySet()) {
                ItemStack stack = entry.getValue().getDefaultInstance();
                if (stack.isEmpty()) continue;
                if (core.getItemCount(ItemKey.of(stack)) < 7) {
                    helper.fail("Runtime fixture item count is low for "
                            + entry.getKey().location());
                    return;
                }
            }
            for (int slot = 0; slot < MachineEnergyTable.entries().size(); slot++) {
                MachineDescriptor descriptor = MachineEnergyTable.get(slot);
                if (descriptor.category() == MachineCategory.TRANSFORM) continue;
                ItemStack installed = core.getMachineContainer().getItem(slot);
                int expectedCount = descriptor.category() == MachineCategory.PROCESS
                        ? Math.min(130, descriptor.maxInstalledCount()) : 1;
                if (!descriptor.accepts(installed)
                        || installed.getCount() != expectedCount) {
                    helper.fail("Runtime fixture station mismatch for " + descriptor.id());
                    return;
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_insert_via_quick_move(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitPos = corePos.east();

        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);

            var testStack = new ItemStack(Items.STONE, 64);
            long directInserted = core.insertItem(testStack.copy(), false);
            if (directInserted == 0) helper.fail("Direct insert returned 0 (slots=" + core.getTotalTypeSlots() + " types=" + core.getTypeCount() + ")");
            if (directInserted != 64) helper.fail("Direct insert should insert 64, got " + directInserted);
            core.extractItem(ItemKey.of(new ItemStack(Items.STONE)), directInserted);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var stone = new ItemStack(Items.STONE, 64);
            player.getInventory().add(stone.copy());

            var menu = new StorageTerminalMenu(0, player.getInventory(), core);
            menu.refreshDisplayItems(core);

            int stoneSlot = -1;
            for (int s = StorageTerminalMenu.DISPLAY_SLOTS; s < menu.slots.size(); s++) {
                if (menu.getSlot(s).hasItem() && menu.getSlot(s).getItem().is(Items.STONE)) {
                    stoneSlot = s; break;
                }
            }
            if (stoneSlot < 0) { helper.fail("Could not find stone slot in menu"); return; }

            var result = menu.quickMoveStack(player, stoneSlot);
            if (!result.isEmpty()) helper.fail("quickMove returned: " + result);

            var playerStillHasStone = player.getInventory().getItem(0).getCount() > 0;
            if (playerStillHasStone && core.getTypeCount() == 1) {
                helper.fail("Insertion partially worked (core has items but player still has stone)");
            } else if (!playerStillHasStone && core.getTypeCount() == 0) {
                helper.fail("Player stone removed but core is empty");
            } else if (playerStillHasStone && core.getTypeCount() == 0) {
                helper.fail("Insert completely failed - stone still in player inv, core empty");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_extract_via_quick_move(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 64));

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(1, player.getInventory(), core);
            menu.refreshDisplayItems(core);

            var result = menu.quickMoveStack(player, 0);
            if (!result.isEmpty()) helper.fail("quickMove from network should return empty");
            boolean foundStone = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(Items.STONE)) { foundStone = true; break; }
            }
            if (!foundStone) helper.fail("Stone should be in player inventory after extract");
            if (core.getTypeCount() != 0) helper.fail("Core should be empty after full extract");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_empty_display_when_no_core(GameTestHelper helper) {
        var level = helper.getLevel();
        var termPos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(termPos, AutoStorage.STORAGE_TERMINAL.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var core = AutoStorage.bfsFindCore(level, termPos);
            if (core != null) helper.fail("Should not find core for isolated terminal");
            helper.succeed();
        });
    }

    @GameTest(template = "platform", batch = "network_guard")
    public static void terminal_finds_core_across_legal_large_network(GameTestHelper helper) {
        var level = helper.getLevel();
        var terminalPos = helper.absolutePos(new BlockPos(1, 3, 1));
        var corePos = terminalPos.offset(20, 20, 20);
        var networkPositions = new java.util.ArrayList<BlockPos>();
        var unitState = AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState();

        for (int x = 0; x <= 20; x++) {
            for (int y = 0; y <= 20; y++) {
                for (int z = 0; z <= 20; z++) {
                    if (x + y + z > 35
                            && !(y == 0 && z == 0)
                            && !(x == 20 && z == 0)
                            && !(x == 20 && y == 20)) continue;
                    BlockPos pos = terminalPos.offset(x, y, z);
                    networkPositions.add(pos);
                    level.setBlock(
                            pos,
                            pos.equals(terminalPos)
                                    ? AutoStorage.STORAGE_TERMINAL.get().defaultBlockState()
                                    : pos.equals(corePos)
                                    ? AutoStorage.STORAGE_CORE.get().defaultBlockState()
                                    : unitState,
                            Block.UPDATE_ALL);
                }
            }
        }

        String failure = null;
        if (networkPositions.size() >= AutoStorage.MAX_NETWORK_BLOCKS) {
            failure = "Test network must remain below the configured member limit";
        } else {
            var found = AutoStorage.bfsFindCore(level, terminalPos);
            if (found == null || !found.getBlockPos().equals(corePos)) {
                failure = "BFS did not find the Core across a legal "
                        + networkPositions.size() + "-block network";
            } else if (AutoStorage.findLoadedNetworkPath(level, terminalPos, corePos).isEmpty()) {
                failure = "Loaded path search did not reach the Core across the legal large network";
            } else {
                try {
                    var method = AutoStorage.class.getDeclaredMethod(
                            "bfsFindCorePositions",
                            net.minecraft.world.level.Level.class,
                            BlockPos.class);
                    method.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    var cores = (java.util.Set<BlockPos>) method.invoke(null, level, terminalPos);
                    if (!cores.contains(corePos)) {
                        failure = "Core-position search did not reach the Core across the legal large network";
                    }
                } catch (ReflectiveOperationException exception) {
                    failure = "Could not invoke Core-position traversal: " + exception;
                }
            }
        }

        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (BlockPos pos : networkPositions) {
            level.setBlock(pos, air, Block.UPDATE_ALL);
        }
        if (failure != null) {
            helper.fail(failure);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void search_filter_finds_items(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            var stone = new ItemStack(Items.STONE, 64);
            var dirt = new ItemStack(Items.DIRT, 64);
            core.insertItem(stone);
            core.insertItem(dirt);
            var stoneFiltered = core.getDisplayStacks("stone");
            if (stoneFiltered.size() != 1) helper.fail("'stone' filter should return 1 item");
            if (!stoneFiltered.get(0).is(Items.STONE)) helper.fail("Filtered item should be stone");
            var emptyFiltered = core.getDisplayStacks("");
            if (emptyFiltered.size() != 2) helper.fail("Empty filter should return all items");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void large_item_count_display(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            var stack = new ItemStack(Items.STONE, 999);
            core.insertItem(stack);
            var actualCount = core.getItemCount(ItemKey.of(new ItemStack(Items.STONE)));
            if (actualCount != 999) helper.fail("Should store actual count 999, got " + actualCount);
            var display = core.getDisplayStacks();
            if (display.isEmpty()) helper.fail("Display should not be empty");
            if (display.get(0).getCount() != 1 || TerminalDisplayStack.amount(display.get(0)) != 999) {
                helper.fail("Display stack should stay visible with exact logical amount 999, got count="
                        + display.get(0).getCount() + " amount=" + TerminalDisplayStack.amount(display.get(0)));
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(7, player.getInventory(), core);
            menu.refreshDisplayItems(core);
            if (menu.getSlot(0).getItem().getCount() != 1
                    || TerminalDisplayStack.amount(menu.getSlot(0).getItem()) != 999) {
                helper.fail("Menu slot should carry exact logical amount 999 without abusing stack count");
            }
            var extracted = core.extractItem(ItemKey.of(new ItemStack(Items.STONE)), 500);
            if (extracted.getCount() != 500) helper.fail("Extracted wrong amount: " + extracted.getCount());
            var remaining = core.getItemCount(ItemKey.of(new ItemStack(Items.STONE)));
            if (remaining != 499) helper.fail("Remaining should be 499, got " + remaining);
            helper.succeed();
        });
    }

    // ===== New tests for sort/search/visible rows =====

    @GameTest(template = "platform")
    public static void sort_items_by_name_ascending(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.DIAMOND, 1));
            core.insertItem(new ItemStack(Items.STONE, 1));
            core.insertItem(new ItemStack(Items.APPLE, 1));
            var sorted = core.getDisplayStacks("", SortMode.NAME, SortOrder.ASCENDING);
            if (sorted.size() != 3) helper.fail("Should have 3 items, got " + sorted.size());
            if (!sorted.get(0).is(Items.APPLE)) helper.fail("First should be Apple");
            if (!sorted.get(1).is(Items.DIAMOND)) helper.fail("Second should be Diamond");
            if (!sorted.get(2).is(Items.STONE)) helper.fail("Third should be Stone");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void sort_items_by_name_descending(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.APPLE, 1));
            core.insertItem(new ItemStack(Items.DIAMOND, 1));
            core.insertItem(new ItemStack(Items.STONE, 1));
            var sorted = core.getDisplayStacks("", SortMode.NAME, SortOrder.DESCENDING);
            if (sorted.size() != 3) helper.fail("Should have 3 items, got " + sorted.size());
            if (!sorted.get(0).is(Items.STONE)) helper.fail("First desc should be Stone");
            if (!sorted.get(1).is(Items.DIAMOND)) helper.fail("Second desc should be Diamond");
            if (!sorted.get(2).is(Items.APPLE)) helper.fail("Third desc should be Apple");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void sort_items_by_quantity(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 10));
            core.insertItem(new ItemStack(Items.DIRT, 50));
            core.insertItem(new ItemStack(Items.DIAMOND, 3));
            var sorted = core.getDisplayStacks("", SortMode.QUANTITY, SortOrder.ASCENDING);
            if (sorted.size() != 3) helper.fail("Should have 3 items, got " + sorted.size());
            if (!sorted.get(0).is(Items.DIAMOND)) helper.fail("First (fewest) should be Diamond");
            if (!sorted.get(1).is(Items.STONE)) helper.fail("Second should be Stone");
            if (!sorted.get(2).is(Items.DIRT)) helper.fail("Third (most) should be Dirt");
            var descSorted = core.getDisplayStacks("", SortMode.QUANTITY, SortOrder.DESCENDING);
            if (!descSorted.get(0).is(Items.DIRT)) helper.fail("First desc should be Dirt (most)");
            if (!descSorted.get(2).is(Items.DIAMOND)) helper.fail("Last desc should be Diamond (fewest)");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_page_is_bounded_and_tracks_exact_mutations(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not found");
                return;
            }
            core.rebuildNetwork(level);
            ItemStack last = ItemStack.EMPTY;
            for (int index = 0; index < 20; index++) {
                ItemStack stack = new ItemStack(Items.STONE);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                        String.format(java.util.Locale.ROOT, "Scale %02d", index)));
                ItemStack identity = stack.copy();
                if (core.insertItem(stack) != 1) {
                    helper.fail("Could not insert exact variant " + index);
                    return;
                }
                last = identity;
            }
            TerminalDisplayPage page = core.getTerminalDisplayPage(
                    "scale", SortMode.NAME, SortOrder.ASCENDING,
                    TerminalResourceView.ITEM, 9, 9);
            if (page.totalTypes() != 20 || page.offset() != 9 || page.stacks().size() != 9) {
                helper.fail("Bounded page metadata was " + page);
                return;
            }
            if (!page.stacks().getFirst().getHoverName().getString().equals("Scale 09")) {
                helper.fail("Far page did not preserve exact Name ordering");
                return;
            }
            ItemKey lastKey = ItemKey.of(last);
            if (core.insertItemCount(lastKey, 99, Action.EXECUTE, Actor.EMPTY) != 99) {
                helper.fail("Could not update exact variant amount");
                return;
            }
            TerminalDisplayPage quantity = core.getTerminalDisplayPage(
                    "", SortMode.QUANTITY, SortOrder.DESCENDING,
                    TerminalResourceView.ITEM, 0, 9);
            if (quantity.totalTypes() != 20 || quantity.stacks().size() != 9
                    || !ItemStack.isSameItemSameComponents(
                    TerminalDisplayStack.strip(quantity.stacks().getFirst()), last)
                    || TerminalDisplayStack.amount(quantity.stacks().getFirst()) != 100) {
                helper.fail("Quantity page did not update the exact mutated variant");
                return;
            }
            if (core.extractItemCount(lastKey, 100, Action.SIMULATE, Actor.EMPTY) != 100
                    || core.getTerminalDisplayPage(
                    "", SortMode.NAME, SortOrder.ASCENDING,
                    TerminalResourceView.ITEM, 0, 9).totalTypes() != 20) {
                helper.fail("Simulated extraction changed the shared terminal index");
                return;
            }
            if (core.extractItemCount(lastKey, 100, Action.EXECUTE, Actor.EMPTY) != 100
                    || core.getTerminalDisplayPage(
                    "", SortMode.NAME, SortOrder.ASCENDING,
                    TerminalResourceView.ITEM, 0, 9).totalTypes() != 19) {
                helper.fail("Exact removal did not update the shared terminal index");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void sort_items_by_id(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));
            core.insertItem(new ItemStack(Items.DIRT, 1));
            var sorted = core.getDisplayStacks("", SortMode.ID, SortOrder.ASCENDING);
            if (sorted.size() != 2) helper.fail("Should have 2 items, got " + sorted.size());
            var firstId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(sorted.get(0).getItem()).toString();
            var secondId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(sorted.get(1).getItem()).toString();
            if (firstId.compareTo(secondId) >= 0) helper.fail("ID sort asc: " + firstId + " before " + secondId);
            var descSorted = core.getDisplayStacks("", SortMode.ID, SortOrder.DESCENDING);
            var descFirst = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(descSorted.get(0).getItem()).toString();
            var descSecond = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(descSorted.get(1).getItem()).toString();
            if (descFirst.compareTo(descSecond) <= 0) helper.fail("ID sort desc: " + descFirst + " after " + descSecond);
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void sort_items_by_mod_then_name(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not found");
                return;
            }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE));
            core.insertItem(AutoStorage.STORAGE_TERMINAL_ITEM.get().getDefaultInstance());
            core.insertItem(AutoStorage.CRAFTING_TERMINAL_ITEM.get().getDefaultInstance());
            var sorted = core.getDisplayStacks("", SortMode.MOD, SortOrder.ASCENDING);
            if (sorted.size() != 3) {
                helper.fail("Should have 3 mod-sorted items, got " + sorted.size());
                return;
            }
            if (!sorted.get(0).is(AutoStorage.CRAFTING_TERMINAL_ITEM.get())
                    || !sorted.get(1).is(AutoStorage.STORAGE_TERMINAL_ITEM.get())
                    || !sorted.get(2).is(Items.STONE)) {
                helper.fail("Mod sort must use namespace, then display name, then registry path");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void search_filter_by_mod(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));
            core.insertItem(new ItemStack(Items.DIAMOND, 1));
            var modFiltered = core.getDisplayStacks("@minecraft");
            if (modFiltered.size() != 2) helper.fail("@minecraft should match 2 vanilla items, got " + modFiltered.size());
            var noMatch = core.getDisplayStacks("@nonexistentmod");
            if (!noMatch.isEmpty()) helper.fail("@nonexistentmod should match nothing, got " + noMatch.size());
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void search_filter_by_tag(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));
            core.insertItem(new ItemStack(Items.DIAMOND, 1));
            core.insertItem(new ItemStack(Items.OAK_LOG, 1));
            var tagFiltered = core.getDisplayStacks("#minecraft:logs");
            if (tagFiltered.isEmpty()) helper.fail("#minecraft:logs should match at least OAK_LOG");
            for (var s : tagFiltered) {
                if (s.is(Items.STONE) || s.is(Items.DIAMOND)) helper.fail("Stone/Diamond should not match logs tag");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void menu_apply_settings_changes_sort(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(2, player.getInventory(), core);
            if (menu.getVisibleRows() != StorageTerminalMenu.INITIAL_DISPLAY_ROWS) {
                helper.fail("A fresh terminal must use the bounded pre-layout row count");
                return;
            }
            var packet = new TerminalSettingsPacket(0, 9, TerminalPreferences.defaults());
            menu.applySettings(packet, player);
            if (menu.getVisibleRows() != 9) helper.fail("visibleRows should be 9, got " + menu.getVisibleRows());
            var tallerPacket = new TerminalSettingsPacket(0, 12, TerminalPreferences.defaults());
            menu.applySettings(tallerPacket, player);
            if (menu.getVisibleRows() != 12) {
                helper.fail("A taller client should receive 12 complete display rows, got "
                        + menu.getVisibleRows());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void visible_rows_clamped_minimum(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(3, player.getInventory(), core);
            var packet = new TerminalSettingsPacket(0, 1, TerminalPreferences.defaults());
            menu.applySettings(packet, player);
            if (menu.getVisibleRows() != 3) helper.fail("visibleRows under 3 clamp to 3, got " + menu.getVisibleRows());
            var packetMax = new TerminalSettingsPacket(0, 20, TerminalPreferences.defaults());
            menu.applySettings(packetMax, player);
            if (menu.getVisibleRows() < 3) helper.fail("visibleRows should stay >= 3, got " + menu.getVisibleRows());
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void display_respects_visible_rows(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T6.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            int inserted = 0;
            for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                if (core.insertItem(new ItemStack(item)) == 1) inserted++;
                if (inserted == 30) break;
            }
            if (inserted != 30) {
                helper.fail("Could not create 30 display types");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(4, player.getInventory(), core);
            var packet = new TerminalSettingsPacket(0, 1, TerminalPreferences.defaults());
            menu.applySettings(packet, player);
            menu.refreshDisplayItems(core);
            if (!menu.getSlot(0).hasItem()) helper.fail("Slot 0 should have item with visibleRows=1");
            if (menu.getSlot(9).hasItem()) helper.fail("Slot 9 should be empty beyond row 0 with visibleRows=1");
            var packet2 = new TerminalSettingsPacket(0, 2, TerminalPreferences.defaults());
            menu.applySettings(packet2, player);
            menu.refreshDisplayItems(core);
            if (!menu.getSlot(17).hasItem() || menu.getSlot(18).hasItem()) {
                helper.fail("Crafting terminal must keep client/server slot visibility aligned at two rows");
                return;
            }
            var packet3 = new TerminalSettingsPacket(0, 3, TerminalPreferences.defaults());
            menu.applySettings(packet3, player);
            menu.refreshDisplayItems(core);
            int filled = 0;
            for (int i = 0; i < 27; i++) {
                if (menu.getSlot(i).hasItem()) filled++;
            }
            if (filled != 27) helper.fail("visibleRows=3 should show exactly 27 slots, got " + filled);
            if (menu.getSlot(27).hasItem()) helper.fail("Slot 27 should be empty beyond visibleRows=3");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void sort_mode_cycles_through_menu_button(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.DIAMOND, 5));
            core.insertItem(new ItemStack(Items.STONE, 63));
            core.insertItem(new ItemStack(Items.APPLE, 1));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(5, player.getInventory(), core);
            menu.refreshDisplayItems(core);
            if (menu.getSlot(0).getItem().isEmpty()) helper.fail("Slot 0 should have items");
            menu.clickMenuButton(player, 12);
            menu.refreshDisplayItems(core);
            if (menu.getSlot(0).getItem().isEmpty()) helper.fail("Slot 0 should still have items after sort cycle");
            menu.clickMenuButton(player, 11);
            menu.refreshDisplayItems(core);
            if (!menu.getSlot(0).hasItem()) helper.fail("Slot 0 empty after sort order toggle");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void apply_settings_preserves_sort_state(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));
            core.insertItem(new ItemStack(Items.APPLE, 64));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(6, player.getInventory(), core);
            menu.refreshDisplayItems(core);
            if (!menu.getSlot(0).getItem().is(Items.APPLE))
                helper.fail("Default NAME sort: slot 0 should be Apple, got " + menu.getSlot(0).getItem());
            menu.clickMenuButton(player, 12); // NAME -> QUANTITY (ascending: fewest first)
            menu.refreshDisplayItems(core);
            if (!menu.getSlot(0).getItem().is(Items.STONE))
                helper.fail("QUANTITY asc: slot 0 should be Stone (fewest), got " + menu.getSlot(0).getItem());
            var persisted = menu.getTerminalPreferences();
            menu.applySettings(new TerminalSettingsPacket(0, 9, persisted), player);
            menu.refreshDisplayItems(core);
            if (!menu.getSlot(0).getItem().is(Items.STONE))
                helper.fail("applySettings must preserve QUANTITY sort: slot 0 should still be Stone, got " + menu.getSlot(0).getItem());
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void rs2_style_preferences_restore_across_storage_terminals(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var preferences = new TerminalPreferences(
                    SortMode.MOD,
                    SortOrder.DESCENDING,
                    SearchMode.EMI,
                    TerminalResourceView.FLUID,
                    CraftingTerminalPage.STORAGE,
                    false,
                    TerminalOutputDestination.PLAYER,
                    null);
            var packet = new TerminalSettingsPacket(41, 8, preferences);
            var first = new StorageTerminalMenu(41, player.getInventory(), core);
            if (!first.applySettings(packet, player)) {
                helper.fail("First terminal should accept persisted preferences");
                return;
            }
            var second = new StorageTerminalMenu(42, player.getInventory(), core);
            if (!second.applySettings(new TerminalSettingsPacket(42, 8, preferences), player)) {
                helper.fail("Second terminal should accept the same client-global preferences");
                return;
            }
            if (!first.getTerminalPreferences().matchesCommon(preferences)
                    || !second.getTerminalPreferences().matchesCommon(preferences)
                    || second.getVisibleRows() != 8) {
                helper.fail("Storage terminals did not restore the shared preferences");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void crafting_terminal_applies_persisted_crafting_preferences(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(43, player.getInventory(), core);
            var preferences = new TerminalPreferences(
                    SortMode.QUANTITY,
                    SortOrder.DESCENDING,
                    SearchMode.EMI_TWO_WAY,
                    TerminalResourceView.FLUID,
                    CraftingTerminalPage.CRAFTABLE,
                    true,
                    TerminalOutputDestination.STORAGE,
                    EnergyType.BLAZE_FUEL);
            if (!menu.applySettings(new TerminalSettingsPacket(43, 9, preferences), player)) {
                helper.fail("Crafting terminal should accept persisted preferences");
                return;
            }
            if (!menu.getTerminalPreferences().equals(preferences)
                    || menu.getVisibleRows() != 9
                    || menu.getPage() != CraftingTerminalPage.CRAFTABLE
                    || !menu.isUsePlayerInventory()
                    || menu.getOutputDestination() != TerminalOutputDestination.STORAGE
                    || menu.getSelectedFuelTarget() != EnergyType.BLAZE_FUEL) {
                helper.fail("Crafting-only preferences were not restored atomically");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void unavailable_resource_view_is_normalized_without_losing_other_preferences(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(44, player.getInventory(), core);
            var unavailable = new TerminalPreferences(
                    SortMode.MOD,
                    SortOrder.DESCENDING,
                    SearchMode.EMI,
                    TerminalResourceView.GAS,
                    CraftingTerminalPage.STORAGE,
                    false,
                    TerminalOutputDestination.PLAYER,
                    null);
            menu.applySettings(new TerminalSettingsPacket(44, 8, unavailable), player);
            if (menu.getResourceView() != TerminalResourceView.ITEM
                    || menu.getSortMode() != SortMode.MOD
                    || menu.getSortOrder() != SortOrder.DESCENDING
                    || menu.getSearchMode() != SearchMode.EMI
                    || menu.getVisibleRows() != 8) {
                helper.fail("Unavailable Gas preference was not normalized independently");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void view_settings_synced_to_data_slots(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(7, player.getInventory(), core);
            if (menu.getSortMode() != SortMode.NAME) helper.fail("default sortMode should be NAME, got " + menu.getSortMode());
            if (menu.getSortOrder() != SortOrder.ASCENDING) helper.fail("default sortOrder should be ASCENDING, got " + menu.getSortOrder());
            if (menu.getSearchMode() != SearchMode.OFF) helper.fail("default searchMode should be OFF, got " + menu.getSearchMode());
            if (menu.getResourceView() != TerminalResourceView.ITEM) helper.fail("default resource view should be ITEM, got " + menu.getResourceView());
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_SORT_MODE_BUTTON);
            if (menu.getSortMode() != SortMode.QUANTITY)
                helper.fail("clickMenuButton(12) should sync sortMode=QUANTITY, got " + menu.getSortMode());
            menu.clickMenuButton(player, StorageTerminalMenu.PREVIOUS_SORT_MODE_BUTTON);
            if (menu.getSortMode() != SortMode.NAME)
                helper.fail("previous sort button should sync sortMode=NAME, got " + menu.getSortMode());
            menu.clickMenuButton(player, StorageTerminalMenu.PREVIOUS_SORT_MODE_BUTTON);
            if (menu.getSortMode() != SortMode.ID)
                helper.fail("previous sort button should wrap NAME to ID, got " + menu.getSortMode());
            menu.clickMenuButton(player, StorageTerminalMenu.SORT_ORDER_BUTTON);
            if (menu.getSortOrder() != SortOrder.DESCENDING)
                helper.fail("clickMenuButton(11) should sync sortOrder=DESCENDING, got " + menu.getSortOrder());
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_SEARCH_MODE_BUTTON);
            if (menu.getSearchMode() != SearchMode.EMI)
                helper.fail("clickMenuButton(13) should sync searchMode=EMI, got " + menu.getSearchMode());
            menu.clickMenuButton(player, StorageTerminalMenu.PREVIOUS_SEARCH_MODE_BUTTON);
            if (menu.getSearchMode() != SearchMode.OFF)
                helper.fail("previous search button should sync searchMode=OFF, got " + menu.getSearchMode());
            menu.clickMenuButton(player, StorageTerminalMenu.PREVIOUS_SEARCH_MODE_BUTTON);
            if (menu.getSearchMode() != SearchMode.EMI_TWO_WAY)
                helper.fail("previous search button should wrap OFF to EMI_TWO_WAY, got " + menu.getSearchMode());
            menu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            if (menu.getResourceView() != TerminalResourceView.FLUID)
                helper.fail("next resource button should sync FLUID, got " + menu.getResourceView());
            menu.clickMenuButton(player, StorageTerminalMenu.PREVIOUS_RESOURCE_VIEW_BUTTON);
            if (menu.getResourceView() != TerminalResourceView.ITEM)
                helper.fail("previous resource button should sync ITEM, got " + menu.getResourceView());
            menu.clickMenuButton(player, StorageTerminalMenu.PREVIOUS_RESOURCE_VIEW_BUTTON);
            if (menu.getResourceView() != TerminalResourceView.ALL)
                helper.fail("previous resource button should wrap ITEM to the All aggregate, got "
                        + menu.getResourceView());
            menu.clickMenuButton(player, StorageTerminalMenu.RESET_SORT_ORDER_BUTTON);
            menu.clickMenuButton(player, StorageTerminalMenu.RESET_SORT_MODE_BUTTON);
            menu.clickMenuButton(player, StorageTerminalMenu.RESET_SEARCH_MODE_BUTTON);
            menu.clickMenuButton(player, StorageTerminalMenu.RESET_RESOURCE_VIEW_BUTTON);
            if (menu.getSortOrder() != SortOrder.ASCENDING
                    || menu.getSortMode() != SortMode.NAME
                    || menu.getSearchMode() != SearchMode.OFF
                    || menu.getResourceView() != TerminalResourceView.ITEM) {
                helper.fail("Middle-reset actions must restore Ascending, Name, Off, and Item defaults");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void data_slot_parity_server_vs_buf_ctor(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var serverMenu = new StorageTerminalMenu(8, player.getInventory(), core);

            var byteBuf = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            byteBuf.writeBlockPos(corePos);
            byteBuf.writeBlockPos(corePos);
            byteBuf.writeBoolean(false);
            MachineEnergyTable.writeSnapshot(byteBuf, MachineEnergyTable.entries());
            StorageTerminalMenu bufMenu;
            try {
                var ctor = StorageTerminalMenu.class.getDeclaredConstructor(
                        net.minecraft.world.inventory.MenuType.class, int.class,
                        net.minecraft.world.entity.player.Inventory.class,
                        net.minecraft.network.RegistryFriendlyByteBuf.class);
                ctor.setAccessible(true);
                bufMenu = ctor.newInstance(
                        AutoStorage.STORAGE_TERMINAL_MENU.get(), 9, player.getInventory(), byteBuf);
            } catch (ReflectiveOperationException e) {
                helper.fail("Could not build buf-ctor menu: " + e);
                return;
            }

            int serverCount = dataSlotCount(serverMenu);
            int bufCount = dataSlotCount(bufMenu);
            if (serverCount != bufCount)
                helper.fail("data-slot count mismatch: server=" + serverCount + " buf=" + bufCount);
            if (serverCount != 13)
                helper.fail("expected exactly 13 synced data slots (wide counts + view settings + wide scroll metadata + unlimited capacity + resource view), got " + serverCount);
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_scroll_metadata_syncs_to_buf_menu(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T6.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            int insertedTypes = 0;
            for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                if (core.insertItem(new ItemStack(item, 1)) == 1) insertedTypes++;
                if (insertedTypes == 72) break;
            }
            if (insertedTypes != 72) { helper.fail("Could not create 72 display types, got " + insertedTypes); return; }

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var serverMenu = new StorageTerminalMenu(80, player.getInventory(), core);
            serverMenu.applySettings(
                    new TerminalSettingsPacket(
                            serverMenu.containerId, 3, TerminalPreferences.defaults()),
                    player);
            serverMenu.scrollBy(StorageTerminalMenu.DISPLAY_COLS);
            if (serverMenu.getTotalItemTypes() != 72 || serverMenu.getScrollOffset() != StorageTerminalMenu.DISPLAY_COLS) {
                helper.fail("Server scroll setup failed: types=" + serverMenu.getTotalItemTypes()
                        + " offset=" + serverMenu.getScrollOffset());
                return;
            }

            var byteBuf = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            byteBuf.writeBlockPos(corePos);
            byteBuf.writeBlockPos(corePos);
            byteBuf.writeBoolean(false);
            MachineEnergyTable.writeSnapshot(byteBuf, MachineEnergyTable.entries());
            StorageTerminalMenu clientMenu;
            try {
                var ctor = StorageTerminalMenu.class.getDeclaredConstructor(
                        net.minecraft.world.inventory.MenuType.class, int.class,
                        net.minecraft.world.entity.player.Inventory.class,
                        net.minecraft.network.RegistryFriendlyByteBuf.class);
                ctor.setAccessible(true);
                clientMenu = ctor.newInstance(
                        AutoStorage.STORAGE_TERMINAL_MENU.get(), 81, player.getInventory(), byteBuf);
            } catch (ReflectiveOperationException e) {
                helper.fail("Could not build buf-ctor menu: " + e);
                return;
            }

            var serverData = dataSlots(serverMenu);
            var clientData = dataSlots(clientMenu);
            if (serverData.size() != clientData.size()) {
                helper.fail("data-slot count mismatch: server=" + serverData.size() + " client=" + clientData.size());
                return;
            }
            if (serverData.size() != 13) {
                helper.fail("storage menu should sync exactly 13 data slots, got " + serverData.size());
                return;
            }
            for (int i = 0; i < serverData.size(); i++) {
                var wire = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                var packet = new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(
                        serverMenu.containerId, i, serverData.get(i).get());
                net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.encode(wire, packet);
                var decoded = net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.decode(wire);
                clientData.get(decoded.getId()).set(decoded.getValue());
            }
            if (clientMenu.getTotalItemTypes() != 72) {
                helper.fail("Client did not receive total item types, got " + clientMenu.getTotalItemTypes());
                return;
            }
            if (clientMenu.getScrollOffset() != StorageTerminalMenu.DISPLAY_COLS) {
                helper.fail("Client did not receive scroll offset, got " + clientMenu.getScrollOffset());
                return;
            }
            serverMenu.clickMenuButton(player, StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON);
            clientData.get(12).set(serverData.get(12).get());
            if (clientMenu.getResourceView() != TerminalResourceView.FLUID) {
                helper.fail("Client did not receive the server-owned resource view");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_wide_metadata_survives_signed_short_wire(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var serverMenu = new StorageTerminalMenu(84, player.getInventory(), core);
            serverMenu.displayTypeCount = 0x1_2345;
            serverMenu.displayMaxTypes = 0x2_8000;
            serverMenu.totalItemTypes = 0x3_4567;
            serverMenu.scrollOffset = 0x8000;

            var byteBuf = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            byteBuf.writeBlockPos(corePos);
            byteBuf.writeBlockPos(corePos);
            byteBuf.writeBoolean(false);
            MachineEnergyTable.writeSnapshot(byteBuf, MachineEnergyTable.entries());
            StorageTerminalMenu clientMenu;
            try {
                var ctor = StorageTerminalMenu.class.getDeclaredConstructor(
                        net.minecraft.world.inventory.MenuType.class, int.class,
                        net.minecraft.world.entity.player.Inventory.class,
                        net.minecraft.network.RegistryFriendlyByteBuf.class);
                ctor.setAccessible(true);
                clientMenu = ctor.newInstance(
                        AutoStorage.STORAGE_TERMINAL_MENU.get(), 85, player.getInventory(), byteBuf);
            } catch (ReflectiveOperationException e) {
                helper.fail("Could not build buf-ctor menu: " + e);
                return;
            }

            var serverData = dataSlots(serverMenu);
            var clientData = dataSlots(clientMenu);
            if (serverData.size() != 13 || clientData.size() != 13) {
                helper.fail("Wide storage metadata plus unlimited capacity and resource view requires exact 13-slot parity, server="
                        + serverData.size() + " client=" + clientData.size());
                return;
            }
            for (int i = 0; i < serverData.size(); i++) {
                var wire = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                var packet = new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(
                        serverMenu.containerId, i, serverData.get(i).get());
                net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.encode(wire, packet);
                var decoded = net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.decode(wire);
                clientData.get(decoded.getId()).set(decoded.getValue());
            }
            if (clientMenu.getTypeCount() != 0x1_2345
                    || clientMenu.getMaxTypes() != 0x2_8000
                    || clientMenu.getTotalItemTypes() != 0x3_4567
                    || clientMenu.getScrollOffset() != 0x8000) {
                helper.fail("Signed-short wire truncated wide metadata: types=" + clientMenu.getTypeCount()
                        + " max=" + clientMenu.getMaxTypes() + " total=" + clientMenu.getTotalItemTypes()
                        + " scroll=" + clientMenu.getScrollOffset());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_server_scroll_state_clamps_to_complete_rows(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T6.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            int inserted = 0;
            for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                if (core.insertItem(new ItemStack(item)) == 1) inserted++;
                if (inserted == 55) break;
            }
            if (inserted != 55) {
                helper.fail("Could not create 55 display types");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(86, player.getInventory(), core);
            menu.applySettings(
                    new TerminalSettingsPacket(
                            menu.containerId, 6, TerminalPreferences.defaults()),
                    player);
            menu.scrollTo(1);
            if (menu.getScrollOffset() != 0) {
                helper.fail("Absolute scroll must snap to the nearest complete row");
                return;
            }
            menu.scrollTo(Integer.MAX_VALUE);
            if (menu.getScrollOffset() != StorageTerminalMenu.DISPLAY_COLS) {
                helper.fail("Absolute scroll must expose the final partial row without shifting columns");
                return;
            }
            menu.scrollTo(-1);
            if (menu.getScrollOffset() != 0) {
                helper.fail("Absolute scroll must clamp negative offsets to zero");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_rejects_unknown_buttons_without_refresh(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CountingStorageTerminalMenu(87, player.getInventory(), core);
            menu.refreshCount = 0;

            if (menu.clickMenuButton(player, 999)) {
                helper.fail("Unknown terminal button ids must return false");
                return;
            }
            if (menu.refreshCount != 0) {
                helper.fail("Unknown terminal buttons must not refresh storage, got " + menu.refreshCount);
                return;
            }
            if (!menu.clickMenuButton(player, 11) || menu.refreshCount != 1) {
                helper.fail("A valid sort button must be handled with exactly one refresh");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_deduplicates_filter_and_layout_requests(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CountingStorageTerminalMenu(88, player.getInventory(), core);
            menu.refreshCount = 0;
            if (menu.applyFilter(core, "") || menu.refreshCount != 0) {
                helper.fail("Repeating the current empty filter must be a no-op");
                return;
            }
            boolean changedFilter = menu.applyFilter(core, "stone");
            boolean repeatedFilter = menu.applyFilter(core, "stone");
            if (!changedFilter || repeatedFilter || menu.refreshCount != 1) {
                helper.fail("A changed filter must refresh once and an identical repeat must not refresh");
                return;
            }

            boolean unchangedRows = menu.applySettings(
                    new TerminalSettingsPacket(menu.containerId, menu.getVisibleRows(),
                            menu.getTerminalPreferences()), player);
            boolean changedRows = menu.applySettings(new TerminalSettingsPacket(
                    menu.containerId, 3, menu.getTerminalPreferences()), player);
            if (unchangedRows || !changedRows) {
                helper.fail("Layout requests must report whether visible rows actually changed");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void open_terminal_refreshes_after_core_storage_event(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(82, player.getInventory(), core);
            if (!menu.getSlot(0).getItem().isEmpty()) { helper.fail("Terminal should start empty"); return; }

            core.insertItem(new ItemStack(Items.STONE, 1), Action.EXECUTE, Actor.bus(corePos.east()));
            menu.broadcastChanges();
            if (!menu.getSlot(0).getItem().is(Items.STONE)) {
                helper.fail("Open terminal did not refresh after a storage change event");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void local_terminal_invalidates_when_network_disconnects(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var bridgePos = corePos.east();
        var terminalPos = bridgePos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(bridgePos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(terminalPos, AutoStorage.STORAGE_TERMINAL.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.setPos(terminalPos.getX() + 0.5, terminalPos.getY() + 0.5, terminalPos.getZ() + 0.5);
            var menu = new StorageTerminalMenu(83, player.getInventory(), core, terminalPos, false);
            if (!menu.stillValid(player)) { helper.fail("Connected terminal should start valid"); return; }

            level.removeBlock(bridgePos, false);
            core.rebuildNetwork(level);
            if (menu.stillValid(player)) {
                helper.fail("Local terminal must invalidate when it is disconnected from its core");
                return;
            }
            if (menu.getCore(level) != null) {
                helper.fail("Disconnected local terminal must not retain storage access");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void shift_extract_splits_unstackable_items_into_legal_stacks(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var damagedSwords = new ItemStack(Items.DIAMOND_SWORD, 36);
            damagedSwords.setDamageValue(1);
            ItemKey key = ItemKey.of(damagedSwords);
            if (core.insertItem(damagedSwords) != 36) { helper.fail("Could not store test swords"); return; }

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(84, player.getInventory(), core);
            menu.quickMoveStack(player, 0);
            int total = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                if (stack.getCount() > stack.getMaxStackSize()) {
                    helper.fail("Shift extract created an illegal stack of " + stack.getCount() + " " + stack);
                    return;
                }
                if (ItemStack.isSameItemSameComponents(stack, key.toStack(1))) total += stack.getCount();
            }
            if (total != 36) { helper.fail("Expected 36 swords in player inventory, got " + total); return; }
            if (core.getItemCount(key) != 0) { helper.fail("All swords should leave core"); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void shift_extract_drops_remainder_after_partial_inventory_insert(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 10));

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            for (int i = 0; i < 36; i++) {
                player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
            }
            player.getInventory().setItem(0, new ItemStack(Items.STONE, 62));
            var menu = new StorageTerminalMenu(85, player.getInventory(), core);
            menu.quickMoveStack(player, 0);

            int inventoryGain = player.getInventory().getItem(0).getCount() - 62;
            int dropped = level.getEntitiesOfClass(
                    ItemEntity.class,
                    player.getBoundingBox().inflate(16),
                    entity -> entity.getItem().is(Items.STONE)
            ).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            if (inventoryGain + dropped != 10) {
                helper.fail("Shift extraction must preserve all 10 stones across inventory and drops, inventory="
                        + inventoryGain + " dropped=" + dropped);
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0) {
                helper.fail("Successful shift extraction should remove all 10 stones from storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void crafting_terminal_pages_are_explicit_mutually_exclusive_and_idempotent(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(86, player.getInventory(), core);
            if (menu.getPage() != CraftingTerminalPage.STORAGE || menu.getSelectedFuelTarget() != null) {
                helper.fail("New crafting menu should open on Storage with Auto target");
                return;
            }
            if (!menu.getSlot(0).isActive() || menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).isActive()) {
                helper.fail("Storage page should activate display slots and deactivate fuel input");
                return;
            }
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            if (menu.getPage() != CraftingTerminalPage.CRAFTABLE
                    || !menu.getSlot(0).isActive()
                    || menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).isActive()) {
                helper.fail("Craftable page button must be idempotent and keep only item slots active");
                return;
            }
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            if (menu.getPage() != CraftingTerminalPage.TRANSFORM) {
                helper.fail("Transform page button should update server-owned page state");
                return;
            }
            if (menu.getSlot(0).isActive() || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).isActive()) {
                helper.fail("Transform page should deactivate display slots and activate its input");
                return;
            }
            menu.clickMenuButton(player, CraftingTerminalMenu.STORAGE_PAGE_BUTTON);
            if (menu.getPage() != CraftingTerminalPage.STORAGE) {
                helper.fail("Storage page button should restore the storage catalog");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void lava_bucket_fuel_page_shift_move_returns_empty_bucket_to_player_slot(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().setItem(0, new ItemStack(Items.LAVA_BUCKET));
            var menu = new CraftingTerminalMenu(92, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            int menuSlot = findPlayerMenuSlot(menu, Items.LAVA_BUCKET);
            if (menuSlot < 0) { helper.fail("Lava bucket player slot not found"); return; }

            menu.quickMoveStack(player, menuSlot);
            selectFirstTransformUse(menu, player);
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 20000) {
                helper.fail("Lava bucket should convert to 20000 furnace fuel");
                return;
            }
            ItemStack remainder = menu.getSlot(menuSlot).getItem();
            if (!remainder.is(Items.BUCKET) || remainder.getCount() != 1) {
                helper.fail("Lava bucket fuel conversion must return one empty bucket to the same player slot");
                return;
            }
            if (core.getTypeCount() != 0) {
                helper.fail("Fuel conversion remainder must not enter ordinary network storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void stacked_container_fuel_page_returns_every_remainder(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            ItemStack lavaBuckets = new ItemStack(Items.LAVA_BUCKET);
            lavaBuckets.set(DataComponents.MAX_STACK_SIZE, 3);
            lavaBuckets.setCount(3);
            player.getInventory().setItem(0, lavaBuckets);
            var menu = new CraftingTerminalMenu(93, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            int menuSlot = findPlayerMenuSlot(menu, Items.LAVA_BUCKET);
            if (menuSlot < 0) { helper.fail("Stacked lava bucket player slot not found"); return; }

            menu.quickMoveStack(player, menuSlot);
            selectFirstTransformUse(menu, player);
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 60000) {
                helper.fail("Three lava buckets should convert to 60000 furnace fuel");
                return;
            }
            ItemStack remainder = menu.getSlot(menuSlot).getItem();
            if (!remainder.is(Items.BUCKET) || remainder.getCount() != 3) {
                helper.fail("Three stacked lava buckets must return three empty buckets, got " + remainder);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void multi_pool_fuel_page_auto_prefers_scarce_pool_and_explicit_target_overrides(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().setItem(0, new ItemStack(Items.BLAZE_ROD));
            player.getInventory().setItem(1, new ItemStack(Items.BLAZE_ROD));
            var menu = new CraftingTerminalMenu(87, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);

            int autoSlot = findPlayerMenuSlot(menu, Items.BLAZE_ROD);
            menu.quickMoveStack(player, autoSlot);
            if (!menu.getVisibleTransformUses().stream().anyMatch(
                    use -> use.targetId().equals(
                            TransformProviderApi.energyTargetId(EnergyType.BLAZE_FUEL)))) {
                helper.fail("Auto Show Uses did not expose the Blaze transformation");
                return;
            }
            int blazeUse = -1;
            List<TransformProviderApi.Use> uses = menu.getVisibleTransformUses();
            for (int index = 0; index < uses.size(); index++) {
                if (uses.get(index).targetId().equals(
                        TransformProviderApi.energyTargetId(EnergyType.BLAZE_FUEL))) {
                    blazeUse = index;
                    break;
                }
            }
            if (blazeUse < 0) {
                helper.fail("Auto Show Uses did not retain the Blaze transformation identity");
                return;
            }
            menu.clickMenuButton(player, CraftingTerminalMenu.transformUseButtonId(blazeUse));
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getEnergy(EnergyType.BLAZE_FUEL) != 1200
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != 0) {
                helper.fail("Auto should route Blaze Rod to scarce Blaze Fuel only");
                return;
            }

            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            if (menu.getSelectedFuelTarget() != EnergyType.FURNACE_FUEL) {
                helper.fail("Explicit Furnace target should be selected before insertion");
                return;
            }
            int furnaceSlot = findPlayerMenuSlot(menu, Items.BLAZE_ROD);
            menu.quickMoveStack(player, furnaceSlot);
            selectFirstTransformUse(menu, player);
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 2400) {
                helper.fail("Furnace selection should add exactly 2400 furnace fuel");
                return;
            }
            if (core.getEnergy(EnergyType.BLAZE_FUEL) != 1200) {
                helper.fail("Blaze selection should add exactly 1200 blaze fuel");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void invalid_or_incompatible_fuel_page_targets_fail_closed(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().setItem(0, new ItemStack(Items.COAL));
            var menu = new CraftingTerminalMenu(88, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.fuelTargetButtonId(EnergyType.BLAZE_FUEL));
            int coalSlot = findPlayerMenuSlot(menu, Items.COAL);

            menu.quickMoveStack(player, coalSlot);
            if (!menu.getSlot(coalSlot).getItem().isEmpty()
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                    .getItem().is(Items.COAL)
                    || menu.clickMenuButton(player, 2)) {
                helper.fail("Incompatible explicit target must preserve the Transform input");
                return;
            }
            menu.clickMenuButton(player, 999);
            if (menu.getSelectedFuelTarget() != EnergyType.BLAZE_FUEL) {
                helper.fail("Forged machine-energy target must not replace the valid fuel target");
                return;
            }

            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 0 || core.getEnergy(EnergyType.BLAZE_FUEL) != 0) {
                helper.fail("Invalid fuel requests must not mutate any energy pool");
                return;
            }
            if (!menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                    .mayPlace(new ItemStack(Items.COAL))) {
                helper.fail("Transform input must accept coal while Blaze is selected");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void fuel_page_conversion_is_shared_by_crafting_and_remote_terminals(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var localPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            localPlayer.getInventory().setItem(0, new ItemStack(Items.COAL));
            var craftingMenu = new CraftingTerminalMenu(89, localPlayer.getInventory(), core);
            craftingMenu.clickMenuButton(localPlayer, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            int localSlot = findPlayerMenuSlot(craftingMenu, Items.COAL);
            craftingMenu.quickMoveStack(localPlayer, localSlot);
            selectFirstTransformUse(craftingMenu, localPlayer);
            craftingMenu.clickMenuButton(localPlayer, CraftingTerminalMenu.MAX_CRAFT_BUTTON);

            var remotePlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            remotePlayer.getInventory().setItem(0, new ItemStack(Items.COAL));
            var remoteMenu = new CraftingTerminalMenu(90, remotePlayer.getInventory(), core, corePos, true);
            remoteMenu.clickMenuButton(remotePlayer, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            int remoteSlot = findPlayerMenuSlot(remoteMenu, Items.COAL);
            remoteMenu.quickMoveStack(remotePlayer, remoteSlot);
            selectFirstTransformUse(remoteMenu, remotePlayer);
            remoteMenu.clickMenuButton(remotePlayer, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 3200) {
                helper.fail("Crafting and remote terminals should both convert one coal, got "
                        + core.getEnergy(EnergyType.FURNACE_FUEL));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void ordinary_fuel_quick_move_on_items_page_stores_it_as_an_item(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().setItem(0, new ItemStack(Items.COAL, 2));
            var menu = new StorageTerminalMenu(91, player.getInventory(), core);
            int menuSlot = findPlayerMenuSlot(menu, Items.COAL);

            menu.quickMoveStack(player, menuSlot);
            if (!menu.getSlot(menuSlot).getItem().isEmpty()) {
                helper.fail("Items-page fuel quick-move should consume the player stack after storing it");
                return;
            }
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 0) {
                helper.fail("Items-page fuel quick-move must not convert stored coal");
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.COAL))) != 2) {
                helper.fail("Items-page fuel quick-move should store both coal items in the network");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void incremental_add_matches_full_rebuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var u1 = corePos.east();
        var u2 = u1.east();
        var u3 = u2.east();
        var u4 = u3.east();

        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(u1, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(u2, AutoStorage.STORAGE_UNIT_T2.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(u3, AutoStorage.STORAGE_UNIT_T3.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            long baseline = core.getTotalTypeSlots();
            int baseMembers = core.getConnectedBlocks().size();
            if (baseline != 10 + 25 + 50) helper.fail("baseline capacity wrong, got " + baseline);

            level.setBlock(u4, AutoStorage.STORAGE_UNIT_T4.get().defaultBlockState(), Block.UPDATE_ALL);
            boolean grew = core.tryIncrementalAdd(level, u4);
            if (!grew) helper.fail("tryIncrementalAdd should grow for a neighbour of a connected member");
            long afterIncremental = core.getTotalTypeSlots();
            int afterMembers = core.getConnectedBlocks().size();
            if (afterMembers != baseMembers + 1) helper.fail("incremental should add exactly 1 member, got " + afterMembers);

            core.rebuildNetwork(level);
            long authoritative = core.getTotalTypeSlots();
            if (afterIncremental != authoritative)
                helper.fail("incremental capacity " + afterIncremental + " != full-rebuild " + authoritative);
            if (authoritative != 10 + 25 + 50 + 100) helper.fail("full-rebuild capacity wrong, got " + authoritative);
            var connectedView = core.getConnectedBlocks();
            try {
                connectedView.clear();
            } catch (UnsupportedOperationException ignored) {
            }
            if (core.getConnectedBlocks().size() != afterMembers) {
                helper.fail("Mutating the connected-block view changed the Core's authoritative topology");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "range_platform")
    public static void piston_moved_bridge_rebuilds_old_and_new_network_positions(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var anchorPos = corePos.east();
        var bridgePos = anchorPos.east();
        var pistonPos = bridgePos.below();
        var movedPos = bridgePos.above();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(anchorPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(bridgePos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(movedPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pistonPos, net.minecraft.world.level.block.Blocks.PISTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.UP), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            if (core.getTotalTypeSlots() != 20) { helper.fail("Test setup should have 20 type slots"); return; }
            level.setBlock(pistonPos.below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            level.updateNeighborsAt(pistonPos.below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);

            helper.runAfterDelay(8, () -> {
                if (!level.getBlockState(movedPos).is(AutoStorage.STORAGE_UNIT_T1.get())) {
                    helper.fail("Piston did not move the bridge unit: bridge=" + level.getBlockState(bridgePos)
                            + ", moved=" + level.getBlockState(movedPos)
                            + ", piston=" + level.getBlockState(pistonPos));
                    return;
                }
                if (core.getTotalTypeSlots() != 10) {
                    helper.fail("Piston-moved bridge left stale capacity: " + core.getTotalTypeSlots());
                    return;
                }
                if (core.getConnectedBlocks().contains(bridgePos)
                        || core.getConnectedBlocks().contains(movedPos)) {
                    helper.fail("Detached old/new piston positions must not remain connected to the core");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void programmatic_network_updates_and_core_neighbor_rebuilds_are_coalesced(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var firstUnit = corePos.east();
        var secondUnit = corePos.east(2);
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(firstUnit, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            if (core.getTotalTypeSlots() != 10) { helper.fail("Test setup should have 10 type slots"); return; }
            level.setBlock(secondUnit, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

            helper.runAfterDelay(2, () -> {
                if (core.getTotalTypeSlots() != 20 || !core.getConnectedBlocks().contains(secondUnit)) {
                    helper.fail("Programmatic placement did not update the remote core network");
                    return;
                }
                var countingCore = new CountingStorageCoreBlockEntity(
                        corePos, level.getBlockState(corePos));
                level.removeBlockEntity(corePos);
                level.setBlockEntity(countingCore);
                helper.runAfterDelay(2, () -> {
                    int baseline = countingCore.rebuildCount;
                    var coreBlock = (StorageCoreBlock) AutoStorage.STORAGE_CORE.get();
                    for (int i = 0; i < 3; i++) {
                        coreBlock.neighborChanged(
                                level.getBlockState(corePos),
                                level,
                                corePos,
                                net.minecraft.world.level.block.Blocks.STONE,
                                corePos.north(),
                                false);
                    }
                    if (countingCore.rebuildCount != baseline) {
                        helper.fail("Non-network neighbors must not rebuild the Core");
                        return;
                    }
                    for (int i = 0; i < 3; i++) {
                        coreBlock.neighborChanged(
                                level.getBlockState(corePos),
                                level,
                                corePos,
                                AutoStorage.STORAGE_UNIT_T1.get(),
                                firstUnit,
                                false);
                    }
                    if (countingCore.rebuildCount != baseline) {
                        helper.fail("Relevant neighbor changes must defer their rebuild");
                        return;
                    }
                    helper.runAfterDelay(2, () -> {
                        if (countingCore.rebuildCount != baseline + 1) {
                            helper.fail("Relevant neighbor changes must coalesce to one rebuild, got "
                                    + (countingCore.rebuildCount - baseline));
                            return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "range_platform")
    public static void incremental_add_respects_full_rebuild_depth_boundary(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        for (int distance = 1; distance <= AutoStorage.NETWORK_SCAN_DEPTH; distance++) {
            level.setBlock(corePos.above(distance), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        }

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var boundaryPos = corePos.above(AutoStorage.NETWORK_SCAN_DEPTH);
            int authoritativeCapacity = (AutoStorage.NETWORK_SCAN_DEPTH - 1) * 10;
            if (core.getTotalTypeSlots() != authoritativeCapacity || core.getConnectedBlocks().contains(boundaryPos)) {
                helper.fail("Test setup did not stop at the authoritative depth boundary");
                return;
            }
            if (!networkCapped(core)) {
                helper.fail("Depth-truncated topology must expose its capped state");
                return;
            }
            Component[] warning = {null};
            boolean[] actionBar = {false};
            var player = new net.neoforged.neoforge.common.util.FakePlayer(
                    level,
                    new com.mojang.authlib.GameProfile(
                            java.util.UUID.randomUUID(), "network_cap_test")) {
                @Override
                public void displayClientMessage(Component message, boolean overlay) {
                    warning[0] = message;
                    actionBar[0] = overlay;
                }
            };
            level.getBlockState(corePos).useWithoutItem(
                    level,
                    player,
                    new BlockHitResult(
                            Vec3.atCenterOf(corePos),
                            Direction.UP,
                            corePos,
                            false));
            if (!actionBar[0]
                    || warning[0] == null
                    || !(warning[0].getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents contents)
                    || !"msg.auto_storage.network_capped".equals(contents.getKey())) {
                helper.fail("A capped Core must show its concise player-facing warning");
                return;
            }
            if (core.tryIncrementalAdd(level, boundaryPos)) {
                helper.fail("Incremental add must reject a block excluded by full rebuild depth");
                return;
            }
            if (core.getTotalTypeSlots() != authoritativeCapacity) {
                helper.fail("Rejected boundary growth must not mutate cached capacity");
                return;
            }
            level.removeBlock(boundaryPos, false);
            core.rebuildNetwork(level);
            if (networkCapped(core)) {
                helper.fail("Capped state must clear after the truncated member is removed");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void incremental_add_rejects_non_adjacent(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var u1 = corePos.east();
        var detached = corePos.south().south();

        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(u1, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);

            level.setBlock(detached, AutoStorage.STORAGE_UNIT_T2.get().defaultBlockState(), Block.UPDATE_ALL);
            boolean grew = core.tryIncrementalAdd(level, detached);
            if (grew) helper.fail("tryIncrementalAdd must reject a block not touching the connected set");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void break_middle_unit_full_rebuild_capacity(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var u1 = corePos.east();
        var u2 = u1.east();
        var u3 = u2.east();

        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(u1, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(u2, AutoStorage.STORAGE_UNIT_T2.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(u3, AutoStorage.STORAGE_UNIT_T3.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            if (core.getTotalTypeSlots() != 10 + 25 + 50) helper.fail("pre-break capacity wrong, got " + core.getTotalTypeSlots());

            level.removeBlock(u2, false);
            core.rebuildNetwork(level);
            if (core.getTotalTypeSlots() != 10) helper.fail("after breaking middle unit, only u1 stays connected: expected 10, got " + core.getTotalTypeSlots());
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void multi_core_network_is_disabled(GameTestHelper helper) {
        var level = helper.getLevel();
        var coreA = helper.absolutePos(new BlockPos(1, 3, 1));
        var unit = coreA.east();
        var coreB = unit.east();
        level.setBlock(coreA, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unit, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(coreB, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(coreA);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core A not found"); return; }
            core.rebuildNetwork(level);
            if (!core.isConflicted()) { helper.fail("multi-core network should be conflicted"); return; }
            long inserted = core.insertItem(new ItemStack(Items.STONE, 64));
            if (inserted != 0) { helper.fail("conflicted network must not accept items, got " + inserted); return; }
            ItemStack extracted = core.extractItem(ItemKey.of(new ItemStack(Items.STONE)), 1);
            if (!extracted.isEmpty()) { helper.fail("conflicted network must not extract items, got " + extracted); return; }
            ItemStack coal = new ItemStack(Items.COAL);
            if (core.addFuel(coal, EnergyType.FURNACE_FUEL)) {
                helper.fail("conflicted network must not accept fuel energy");
                return;
            }
            if (coal.getCount() != 1 || core.getEnergy(EnergyType.FURNACE_FUEL) != 0) {
                helper.fail("rejected conflicted fuel must preserve source and pool");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform", batch = "network_guard")
    public static void rebuild_network_respects_max_block_guard(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        int maxNetworkBlocks = AutoStorage.MAX_NETWORK_BLOCKS;
        int cubeSize = 21;
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        var unitState = AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState();

        for (int x = 0; x < cubeSize; x++) {
            for (int y = 0; y < cubeSize; y++) {
                for (int z = 0; z < cubeSize; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    level.setBlock(new BlockPos(corePos.getX() + x, corePos.getY() + y, corePos.getZ() + z), unitState, Block.UPDATE_ALL);
                }
            }
        }

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            int connected = core.getConnectedBlocks().size();
            String failure = null;
            if (connected > maxNetworkBlocks) {
                failure = "rebuildNetwork should cap connected blocks at " + maxNetworkBlocks + ", got " + connected;
            }
            if (failure == null && !networkCapped(core)) {
                failure = "Member-truncated topology must expose its capped state";
            }
            int expectedCapacity = (connected - 1) * 10;
            if (failure == null && core.getTotalTypeSlots() != expectedCapacity) {
                failure = "bounded rebuild capacity should match scanned T1 units: expected " + expectedCapacity + ", got " + core.getTotalTypeSlots();
            }
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            for (int x = 0; x < cubeSize; x++) {
                for (int y = 0; y < cubeSize; y++) {
                    for (int z = 0; z < cubeSize; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        level.setBlock(new BlockPos(corePos.getX() + x, corePos.getY() + y, corePos.getZ() + z), air, Block.UPDATE_ALL);
                    }
                }
            }
            if (failure != null) {
                helper.fail(failure);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void import_bus_item_handler_is_available_on_enabled_sides_and_simulation_is_insert_only(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var busPos = corePos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, AutoStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            IItemHandler handler = null;
            for (Direction side : Direction.values()) {
                IItemHandler sided = level.getCapability(Capabilities.ItemHandler.BLOCK, busPos, side);
                if (sided == null) {
                    helper.fail("Import Bus must expose ItemHandler capability on side " + side);
                    return;
                }
                if (side == Direction.UP) handler = sided;
            }
            IItemHandler unsided = level.getCapability(Capabilities.ItemHandler.BLOCK, busPos, null);
            if (unsided == null || handler == null
                    || handler != level.getCapability(
                    Capabilities.ItemHandler.BLOCK, busPos, Direction.UP)) {
                helper.fail("Import Bus capability must be stable per enabled side");
                return;
            }
            if (handler.getSlots() != 1 || handler.getSlotLimit(0) < 64
                    || !handler.isItemValid(0, new ItemStack(Items.STONE))) {
                helper.fail("Import Bus ItemHandler must expose one full-stack insertion slot");
                return;
            }
            ItemStack input = new ItemStack(Items.STONE, 17);
            ItemStack remainder = handler.insertItem(0, input, true);
            if (!remainder.isEmpty() || input.getCount() != 17
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0) {
                helper.fail("Simulated Import Bus insertion must predict full acceptance without mutation: remainder="
                        + remainder + " input=" + input.getCount());
                return;
            }
            if (!handler.getStackInSlot(0).isEmpty()
                    || !handler.extractItem(0, 64, true).isEmpty()
                    || !handler.extractItem(0, 64, false).isEmpty()) {
                helper.fail("Import Bus ItemHandler must remain insert-only");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void import_bus_item_handler_executes_exact_full_and_partial_remainders(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var busPos = corePos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, AutoStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, busPos, Direction.WEST);
            if (handler == null) {
                helper.fail("Import Bus ItemHandler capability missing");
                return;
            }
            ItemStack fullInput = new ItemStack(Items.STONE, 13);
            ItemStack fullRemainder = handler.insertItem(0, fullInput, false);
            if (!fullRemainder.isEmpty() || fullInput.getCount() != 13
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 13) {
                helper.fail("Import Bus execute must insert the exact accepted stack: remainder="
                        + fullRemainder + " stored=" + core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))));
                return;
            }

            ItemKey stoneKey = ItemKey.of(new ItemStack(Items.STONE));
            core.extractItemCount(stoneKey, Long.MAX_VALUE, Action.EXECUTE, Actor.EMPTY);
            core.insertItemCount(stoneKey, Long.MAX_VALUE - 3, Action.EXECUTE, Actor.EMPTY);
            ItemStack partialInput = new ItemStack(Items.STONE, 8);
            ItemStack partialRemainder = handler.insertItem(0, partialInput, false);
            if (!partialRemainder.is(Items.STONE) || partialRemainder.getCount() != 5
                    || partialInput.getCount() != 8
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != Long.MAX_VALUE) {
                helper.fail("Import Bus execute must return the exact five-item partial remainder: remainder="
                        + partialRemainder + " stored=" + core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void import_bus_item_handler_rejects_without_core_and_during_conflict(GameTestHelper helper) {
        var level = helper.getLevel();
        var isolatedBusPos = helper.absolutePos(new BlockPos(1, 3, 1));
        var firstCorePos = helper.absolutePos(new BlockPos(4, 3, 1));
        var conflictedBusPos = firstCorePos.east();
        var secondCorePos = conflictedBusPos.east();
        level.setBlock(isolatedBusPos, AutoStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(firstCorePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(firstCorePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(conflictedBusPos, AutoStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(secondCorePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            IItemHandler isolated = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, isolatedBusPos, Direction.NORTH);
            if (isolated == null) {
                helper.fail("Isolated Import Bus must still expose ItemHandler capability");
                return;
            }
            ItemStack noCoreInput = new ItemStack(Items.STONE, 9);
            ItemStack noCoreRemainder = isolated.insertItem(0, noCoreInput, false);
            if (!noCoreRemainder.is(Items.STONE) || noCoreRemainder.getCount() != 9
                    || noCoreInput.getCount() != 9) {
                helper.fail("Import Bus without a Core must return the full input stack");
                return;
            }
            if (!(level.getBlockEntity(firstCorePos) instanceof StorageCoreBlockEntity first)
                    || !(level.getBlockEntity(secondCorePos) instanceof StorageCoreBlockEntity second)) {
                helper.fail("Conflicted Core fixture missing");
                return;
            }
            first.rebuildNetwork(level);
            second.rebuildNetwork(level);
            if (!first.isConflicted() || !second.isConflicted()) {
                helper.fail("Import Bus conflict fixture did not conflict both Cores");
                return;
            }
            IItemHandler conflicted = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, conflictedBusPos, Direction.SOUTH);
            if (conflicted == null) {
                helper.fail("Conflicted Import Bus must still expose ItemHandler capability");
                return;
            }
            ItemStack conflictInput = new ItemStack(Items.STONE, 11);
            ItemStack conflictRemainder = conflicted.insertItem(0, conflictInput, false);
            if (!conflictRemainder.is(Items.STONE) || conflictRemainder.getCount() != 11
                    || conflictInput.getCount() != 11
                    || first.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0
                    || second.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0) {
                helper.fail("Conflicted Import Bus must return the full input without mutating either Core");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void import_bus_passive_missing_core_lookup_is_throttled(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var busPos = corePos.east();
        level.setBlock(busPos, AutoStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            IItemHandler handler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, busPos, Direction.WEST);
            if (handler == null) {
                helper.fail("Import Bus ItemHandler capability missing");
                return;
            }
            ItemStack first = new ItemStack(Items.STONE, 3);
            ItemStack firstRemainder = handler.insertItem(0, first, false);
            if (!firstRemainder.is(Items.STONE) || firstRemainder.getCount() != 3) {
                helper.fail("Initial passive insertion without a Core must return the full stack");
                return;
            }

            level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found after placement");
                return;
            }
            core.rebuildNetwork(level);
            ItemStack immediate = new ItemStack(Items.STONE, 4);
            ItemStack immediateRemainder = handler.insertItem(0, immediate, false);
            if (!immediateRemainder.is(Items.STONE) || immediateRemainder.getCount() != 4
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0) {
                helper.fail("Passive missing-Core lookup must remain negatively cached for its cooldown");
            }
        });

        helper.runAfterDelay(13, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core missing after passive lookup cooldown");
                return;
            }
            IItemHandler handler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, busPos, Direction.WEST);
            if (handler == null) {
                helper.fail("Import Bus ItemHandler capability missing after cooldown");
                return;
            }
            ItemStack afterCooldown = new ItemStack(Items.STONE, 5);
            ItemStack remainder = handler.insertItem(0, afterCooldown, false);
            if (!remainder.isEmpty()
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 5) {
                helper.fail("Passive insertion must discover and use the Core after the lookup cooldown");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void import_bus_stops_after_network_disconnect(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitPos = corePos.east();
        var busPos = unitPos.east();
        var chestPos = busPos.east();

        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos,
                AutoStorage.IMPORT_BUS.get().defaultBlockState().setValue(ImportBusBlock.FACING, net.minecraft.core.Direction.EAST),
                Block.UPDATE_ALL);
        level.setBlock(chestPos, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var coreBe = level.getBlockEntity(corePos);
            if (!(coreBe instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var chestBe = level.getBlockEntity(chestPos);
            if (!(chestBe instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity chest)) {
                helper.fail("Barrel not found"); return;
            }
            chest.setItem(0, new ItemStack(Items.STONE, 1));
            chest.setChanged();
            var busBe = level.getBlockEntity(busPos);
            if (!(busBe instanceof ImportBusBlockEntity bus)) { helper.fail("Import bus not found"); return; }
            for (int i = 0; i < 11; i++) bus.tick();
            var key = ItemKey.of(new ItemStack(Items.STONE));
            if (core.getItemCount(key) != 1) { helper.fail("initial import should put 1 stone in core, got " + core.getItemCount(key)); return; }

            level.removeBlock(unitPos, false);
            core.rebuildNetwork(level);
            chest.setItem(0, new ItemStack(Items.STONE, 1));
            chest.setChanged();
            for (int i = 0; i < 11; i++) bus.tick();
            if (core.getItemCount(key) != 1) { helper.fail("disconnected import bus must not keep importing via cached core, got " + core.getItemCount(key)); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void programmatic_bridge_destroy_rebuilds_network_and_invalidates_bus(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var anchorPos = corePos.east();
        var bridgePos = anchorPos.east();
        var busPos = bridgePos.east();
        var chestPos = busPos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(anchorPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(bridgePos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos,
                AutoStorage.IMPORT_BUS.get().defaultBlockState().setValue(ImportBusBlock.FACING, net.minecraft.core.Direction.EAST),
                Block.UPDATE_ALL);
        level.setBlock(chestPos, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var coreBe = level.getBlockEntity(corePos);
            if (!(coreBe instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var chestBe = level.getBlockEntity(chestPos);
            if (!(chestBe instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity chest)) {
                helper.fail("Barrel not found"); return;
            }
            var busBe = level.getBlockEntity(busPos);
            if (!(busBe instanceof ImportBusBlockEntity bus)) { helper.fail("Import bus not found"); return; }
            chest.setItem(0, new ItemStack(Items.STONE, 1));
            chest.setChanged();
            for (int i = 0; i < 11; i++) bus.tick();
            ItemKey key = ItemKey.of(new ItemStack(Items.STONE));
            if (core.getItemCount(key) != 1) { helper.fail("Initial import should cache the connected core"); return; }

            chest.setItem(0, new ItemStack(Items.STONE, 1));
            chest.setChanged();
            level.destroyBlock(bridgePos, false);
            helper.runAfterDelay(2, () -> {
                for (int i = 0; i < 11; i++) bus.tick();
                if (core.getConnectedBlocks().contains(busPos)) {
                    helper.fail("Programmatic bridge destruction left bus in the core network cache");
                    return;
                }
                if (core.getItemCount(key) != 1) {
                    helper.fail("Disconnected cached bus imported after programmatic bridge destruction");
                    return;
                }
                if (chest.getItem(0).getCount() != 1) {
                    helper.fail("Disconnected bus consumed the source item");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void import_bus_does_not_extract_when_type_slots_full(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var busPos = corePos.east();
        var chestPos = busPos.east();

        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos,
                AutoStorage.IMPORT_BUS.get().defaultBlockState().setValue(ImportBusBlock.FACING, net.minecraft.core.Direction.EAST),
                Block.UPDATE_ALL);
        level.setBlock(chestPos, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var coreBe = level.getBlockEntity(corePos);
            if (!(coreBe instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            ItemStack[] tenTypes = {
                    new ItemStack(Items.STONE), new ItemStack(Items.DIRT), new ItemStack(Items.COBBLESTONE),
                    new ItemStack(Items.GRANITE), new ItemStack(Items.DIORITE), new ItemStack(Items.ANDESITE),
                    new ItemStack(Items.OAK_LOG), new ItemStack(Items.SPRUCE_LOG), new ItemStack(Items.BIRCH_LOG),
                    new ItemStack(Items.SAND)
            };
            for (ItemStack stack : tenTypes) {
                long inserted = core.insertItem(stack.copy());
                if (inserted != 1) { helper.fail("setup should fill each T1 type slot, got " + inserted + " for " + stack); return; }
            }
            if (core.getTypeCount() != core.getTotalTypeSlots()) {
                helper.fail("setup should fill all type slots: types=" + core.getTypeCount() + " max=" + core.getTotalTypeSlots());
                return;
            }
            var chestBe = level.getBlockEntity(chestPos);
            if (!(chestBe instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel)) {
                helper.fail("Barrel not found"); return;
            }
            barrel.setItem(0, new ItemStack(Items.GOLD_INGOT, 16));
            barrel.setChanged();
            var busBe = level.getBlockEntity(busPos);
            if (!(busBe instanceof ImportBusBlockEntity bus)) { helper.fail("Import bus not found"); return; }
            for (int i = 0; i < 11; i++) bus.tick();
            if (barrel.getItem(0).getCount() != 16) {
                helper.fail("full type storage must not make import bus extract and lose items, got " + barrel.getItem(0));
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.GOLD_INGOT))) != 0) {
                helper.fail("new type should not be inserted when all type slots are full");
                return;
            }
            if (core.getTypeCount() != core.getTotalTypeSlots()) {
                helper.fail("type count should remain full without overflowing");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void search_filter_invalid_tag_tokens_return_empty(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));
            try {
                if (core.getDisplayStacks("#").isEmpty()) { helper.fail("bare # tag token should behave like an empty query"); return; }
                if (!core.getDisplayStacks("#:").isEmpty()) { helper.fail("invalid #: tag token should match nothing"); return; }
                if (!core.getDisplayStacks("#minecraft:BAD!").isEmpty()) { helper.fail("invalid tag token should match nothing"); return; }
            } catch (RuntimeException e) {
                helper.fail("invalid tag tokens must not throw: " + e);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void search_filter_packet_rejects_oversized_payload(GameTestHelper helper) {
        var level = helper.getLevel();
        int maxFilterLength = SearchFilterPacket.MAX_FILTER_LENGTH;
        var validBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        String valid = "a".repeat(maxFilterLength);
        SearchFilterPacket.STREAM_CODEC.encode(validBuffer, new SearchFilterPacket(4, valid));
        var decoded = SearchFilterPacket.STREAM_CODEC.decode(validBuffer);
        if (!decoded.filter().equals(valid)) { helper.fail("Maximum valid search filter did not round-trip"); return; }

        var oversizedBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        boolean rejected = false;
        try {
            SearchFilterPacket.STREAM_CODEC.encode(oversizedBuffer,
                    new SearchFilterPacket(4, "a".repeat(maxFilterLength + 1)));
        } catch (RuntimeException expected) {
            rejected = true;
        }
        if (!rejected) { helper.fail("Oversized search filter should be rejected by the packet codec"); return; }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void incremental_growth_rebuilds_when_placed_block_bridges_detached_segment(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var connected = corePos.east();
        var bridge = corePos.east(2);
        var detached = corePos.east(3);
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(connected, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(detached, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            if (core.getConnectedBlocks().contains(detached)) { helper.fail("test setup invalid: detached segment already connected"); return; }
            level.setBlock(bridge, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
            AutoStorage.findCoresAndGrow(level, bridge);
            if (!core.getConnectedBlocks().contains(bridge)) { helper.fail("placed bridge should connect to core"); return; }
            if (!core.getConnectedBlocks().contains(detached)) { helper.fail("bridge placement must rebuild and include detached segment"); return; }
            if (core.getTotalTypeSlots() != 30) { helper.fail("all three T1 units should contribute 30 slots, got " + core.getTotalTypeSlots()); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void incremental_bridge_between_two_cores_conflicts_both_networks(GameTestHelper helper) {
        var level = helper.getLevel();
        var coreA = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitA = coreA.east();
        var bridge = coreA.east(2);
        var unitB = coreA.east(3);
        var coreB = coreA.east(4);
        level.setBlock(coreA, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitA, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitB, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(coreB, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var beA = level.getBlockEntity(coreA);
            var beB = level.getBlockEntity(coreB);
            if (!(beA instanceof StorageCoreBlockEntity a)) { helper.fail("Core A not found"); return; }
            if (!(beB instanceof StorageCoreBlockEntity b)) { helper.fail("Core B not found"); return; }
            a.rebuildNetwork(level);
            b.rebuildNetwork(level);
            if (a.isConflicted() || b.isConflicted()) { helper.fail("test setup invalid: cores conflicted before bridge"); return; }
            level.setBlock(bridge, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
            AutoStorage.findCoresAndGrow(level, bridge);
            if (!a.isConflicted()) { helper.fail("core A should conflict when bridge joins two cores"); return; }
            if (!b.isConflicted()) { helper.fail("core B should conflict when bridge joins two cores"); return; }
            if (a.insertItem(new ItemStack(Items.STONE, 1)) != 0) { helper.fail("conflicted core A must not accept items"); return; }
            if (b.insertItem(new ItemStack(Items.STONE, 1)) != 0) { helper.fail("conflicted core B must not accept items"); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "range_platform")
    public static void terminal_validates_against_access_block_not_core_distance(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var terminalPos = corePos.east(10);
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 1; i < 10; i++) {
            level.setBlock(corePos.east(i), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(terminalPos, AutoStorage.STORAGE_TERMINAL.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.moveTo(terminalPos.getX() + 0.5, terminalPos.getY() + 0.5, terminalPos.getZ() + 0.5, 0, 0);
            var menu = new StorageTerminalMenu(0, player.getInventory(), core, terminalPos, false);
            if (!menu.stillValid(player)) helper.fail("terminal menu should remain valid near terminal even when core is far");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void remote_terminal_valid_away_from_bound_core(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.moveTo(corePos.getX() + 100.5, corePos.getY() + 0.5, corePos.getZ() + 100.5, 0, 0);
            var menu = new CraftingTerminalMenu(0, player.getInventory(), core, corePos, true);
            if (!menu.stillValid(player)) helper.fail("remote terminal should remain valid away from bound core");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void open_remote_menu_invalidates_when_core_is_replaced_at_same_position(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity original)) { helper.fail("Core not found"); return; }
            original.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(92, player.getInventory(), original, corePos, true);
            if (!menu.stillValid(player)) { helper.fail("Remote menu should start valid"); return; }
            var originalNetworkId = original.getNetworkId();

            level.destroyBlock(corePos, false);
            level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity replacement)) {
                helper.fail("Replacement core not found");
                return;
            }
            if (replacement.getNetworkId().equals(originalNetworkId)) {
                helper.fail("Replacement core must have a distinct identity");
                return;
            }
            if (menu.stillValid(player) || menu.getCore(level) != null) {
                helper.fail("Open remote menu must not retarget a replacement core at the same coordinates");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void bound_remote_terminal_rejects_replacement_core_at_same_position(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity)) { helper.fail("Core not found"); return; }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var stack = new ItemStack(AutoStorage.REMOTE_TERMINAL.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.setShiftKeyDown(true);
            var remote = (RemoteTerminalItem) AutoStorage.REMOTE_TERMINAL.get();
            remote.useOn(new UseOnContext(
                    level,
                    player,
                    InteractionHand.MAIN_HAND,
                    stack,
                    new BlockHitResult(Vec3.atCenterOf(corePos), Direction.UP, corePos, false)
            ));
            player.setShiftKeyDown(false);

            level.destroyBlock(corePos, false);
            level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            var result = remote.use(level, player, InteractionHand.MAIN_HAND);
            if (result.getResult() != net.minecraft.world.InteractionResult.FAIL) {
                helper.fail("A terminal bound to the old core must reject a same-position replacement");
                return;
            }
            if (player.containerMenu != player.inventoryMenu) {
                helper.fail("Stale remote binding must not open the replacement core menu");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void rebinding_remote_terminal_preserves_unrelated_custom_data(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity)) { helper.fail("Core not found"); return; }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var stack = new ItemStack(AutoStorage.REMOTE_TERMINAL.get());
            var originalData = new CompoundTag();
            originalData.putString("otherModData", "preserve-me");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(originalData));
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.setShiftKeyDown(true);
            var remote = (RemoteTerminalItem) AutoStorage.REMOTE_TERMINAL.get();
            remote.useOn(new UseOnContext(
                    level,
                    player,
                    InteractionHand.MAIN_HAND,
                    stack,
                    new BlockHitResult(Vec3.atCenterOf(corePos), Direction.UP, corePos, false)
            ));
            player.setShiftKeyDown(false);

            var reboundData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!"preserve-me".equals(reboundData.getString("otherModData"))) {
                helper.fail("Remote rebinding must preserve unrelated custom data");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void legacy_coordinate_only_remote_binding_fails_closed(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var stack = new ItemStack(AutoStorage.REMOTE_TERMINAL.get());
            var tag = new CompoundTag();
            tag.putInt("coreX", corePos.getX());
            tag.putInt("coreY", corePos.getY());
            tag.putInt("coreZ", corePos.getZ());
            tag.putString("dimension", level.dimension().location().toString());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var remote = (RemoteTerminalItem) AutoStorage.REMOTE_TERMINAL.get();
            var result = remote.use(level, player, InteractionHand.MAIN_HAND);
            if (result.getResult() != net.minecraft.world.InteractionResult.FAIL) {
                helper.fail("Legacy coordinate-only bindings must require explicit rebinding");
                return;
            }
            if (player.containerMenu != player.inventoryMenu) {
                helper.fail("Legacy coordinate-only binding must not open any core menu");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_pickup_click_amounts_match_vanilla_like_stacks(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var key = ItemKey.of(new ItemStack(Items.STONE));
            core.insertItem(new ItemStack(Items.STONE, 100));
            var leftPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var leftMenu = new StorageTerminalMenu(30, leftPlayer.getInventory(), core);
            leftMenu.refreshDisplayItems(core);
            leftMenu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, leftPlayer);
            if (!leftMenu.getCarried().is(Items.STONE) || leftMenu.getCarried().getCount() != 64) {
                helper.fail("left click should carry one stack of 64, got " + leftMenu.getCarried());
                return;
            }
            if (core.getItemCount(key) != 36) { helper.fail("left click should leave 36, got " + core.getItemCount(key)); return; }

            core.extractItem(key, 36);
            core.insertItem(new ItemStack(Items.STONE, 100));
            var rightPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var rightMenu = new StorageTerminalMenu(31, rightPlayer.getInventory(), core);
            rightMenu.refreshDisplayItems(core);
            rightMenu.clicked(0, 1, net.minecraft.world.inventory.ClickType.PICKUP, rightPlayer);
            if (!rightMenu.getCarried().is(Items.STONE) || rightMenu.getCarried().getCount() != 32) {
                helper.fail("right click should carry half stack of 32, got " + rightMenu.getCarried());
                return;
            }
            if (core.getItemCount(key) != 68) { helper.fail("right click should leave 68, got " + core.getItemCount(key)); return; }

            core.extractItem(key, 68);
            core.insertItem(new ItemStack(Items.STONE, 63));
            var oddPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var oddMenu = new StorageTerminalMenu(36, oddPlayer.getInventory(), core);
            oddMenu.refreshDisplayItems(core);
            oddMenu.clicked(0, 1, net.minecraft.world.inventory.ClickType.PICKUP, oddPlayer);
            if (!oddMenu.getCarried().is(Items.STONE) || oddMenu.getCarried().getCount() != 32) {
                helper.fail("right click should round half of 63 up to 32, got " + oddMenu.getCarried());
                return;
            }
            if (core.getItemCount(key) != 31) {
                helper.fail("right click of 63 should leave 31, got " + core.getItemCount(key));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void terminal_display_rejects_forged_click_types_and_buttons(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            ItemKey key = ItemKey.of(new ItemStack(Items.STONE));
            core.insertItem(new ItemStack(Items.STONE, 64));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(37, player.getInventory(), core);
            menu.refreshDisplayItems(core);

            for (var type : new net.minecraft.world.inventory.ClickType[] {
                    net.minecraft.world.inventory.ClickType.SWAP,
                    net.minecraft.world.inventory.ClickType.CLONE,
                    net.minecraft.world.inventory.ClickType.THROW,
                    net.minecraft.world.inventory.ClickType.QUICK_CRAFT,
                    net.minecraft.world.inventory.ClickType.PICKUP_ALL
            }) {
                menu.clicked(0, 0, type, player);
                if (!menu.getCarried().isEmpty() || core.getItemCount(key) != 64) {
                    helper.fail("Forged display click must be ignored: " + type);
                    return;
                }
            }

            menu.clicked(0, 2, net.minecraft.world.inventory.ClickType.PICKUP, player);
            if (!menu.getCarried().isEmpty() || core.getItemCount(key) != 64) {
                helper.fail("Display PICKUP must reject buttons other than left/right");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void crafting_and_remote_display_quick_move_extracts_to_player(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            ItemKey key = ItemKey.of(new ItemStack(Items.STONE));
            core.insertItem(new ItemStack(Items.STONE, 64));

            var localPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var localMenu = new CraftingTerminalMenu(38, localPlayer.getInventory(), core);
            localMenu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, localPlayer);
            if (countPlayerItem(localPlayer, Items.STONE) != 64 || core.getItemCount(key) != 0) {
                helper.fail("Local crafting terminal shift-click must extract the display stack");
                return;
            }

            core.insertItem(new ItemStack(Items.STONE, 64));
            var remotePlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var remoteMenu = new CraftingTerminalMenu(39, remotePlayer.getInventory(), core, corePos, true);
            remoteMenu.clicked(0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, remotePlayer);
            if (countPlayerItem(remotePlayer, Items.STONE) != 64 || core.getItemCount(key) != 0) {
                helper.fail("Remote crafting terminal shift-click must extract the display stack");
                return;
            }
            helper.succeed();
        });
    }

    private static int countPlayerItem(net.minecraft.world.entity.player.Player player,
                                       net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    @GameTest(template = "platform")
    public static void terminal_invalidates_when_access_block_is_removed(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var terminalPos = corePos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(terminalPos, AutoStorage.STORAGE_TERMINAL.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.moveTo(terminalPos.getX() + 0.5, terminalPos.getY() + 0.5, terminalPos.getZ() + 0.5, 0, 0);
            var menu = new StorageTerminalMenu(32, player.getInventory(), core, terminalPos, false);
            if (!menu.stillValid(player)) { helper.fail("menu should start valid while access terminal exists"); return; }
            level.removeBlock(terminalPos, false);
            if (menu.stillValid(player)) { helper.fail("menu must invalidate when the access terminal block is removed"); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void remote_terminal_invalidates_when_bound_core_is_removed(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.moveTo(corePos.getX() + 100.5, corePos.getY() + 0.5, corePos.getZ() + 100.5, 0, 0);
            var menu = new CraftingTerminalMenu(33, player.getInventory(), core, corePos, true);
            if (!menu.stillValid(player)) { helper.fail("remote menu should start valid while core exists"); return; }
            level.removeBlock(corePos, false);
            if (menu.stillValid(player)) { helper.fail("remote menu must invalidate when the bound core block is removed"); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void export_bus_only_extracts_what_target_can_accept(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var busPos = corePos.east();
        var chestPos = busPos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos,
                AutoStorage.EXPORT_BUS.get().defaultBlockState().setValue(ExportBusBlock.FACING, net.minecraft.core.Direction.EAST),
                Block.UPDATE_ALL);
        level.setBlock(chestPos, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var coreBe = level.getBlockEntity(corePos);
            if (!(coreBe instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 10));
            var chestBe = level.getBlockEntity(chestPos);
            if (!(chestBe instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel)) { helper.fail("Barrel not found"); return; }
            barrel.setItem(0, new ItemStack(Items.STONE, 63));
            for (int i = 1; i < barrel.getContainerSize(); i++) {
                barrel.setItem(i, new ItemStack(Items.DIRT, 64));
            }
            barrel.setChanged();
            var busBe = level.getBlockEntity(busPos);
            if (!(busBe instanceof ExportBusBlockEntity bus)) { helper.fail("Export bus not found"); return; }
            bus.setFilter(new ItemStack(Items.STONE));
            for (int i = 0; i < 11; i++) bus.tick();
            if (barrel.getItem(0).getCount() != 64) { helper.fail("barrel should receive exactly one stone, got " + barrel.getItem(0)); return; }
            long remaining = core.getItemCount(ItemKey.of(new ItemStack(Items.STONE)));
            if (remaining != 9) { helper.fail("core should only lose one stone, got remaining=" + remaining); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void export_bus_stops_after_network_disconnect(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitPos = corePos.east();
        var busPos = unitPos.east();
        var chestPos = busPos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos,
                AutoStorage.EXPORT_BUS.get().defaultBlockState().setValue(ExportBusBlock.FACING, net.minecraft.core.Direction.EAST),
                Block.UPDATE_ALL);
        level.setBlock(chestPos, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var coreBe = level.getBlockEntity(corePos);
            if (!(coreBe instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));
            var chestBe = level.getBlockEntity(chestPos);
            if (!(chestBe instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel)) { helper.fail("Barrel not found"); return; }
            var busBe = level.getBlockEntity(busPos);
            if (!(busBe instanceof ExportBusBlockEntity bus)) { helper.fail("Export bus not found"); return; }
            bus.setFilter(new ItemStack(Items.STONE));
            for (int i = 0; i < 11; i++) bus.tick();
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0) { helper.fail("initial export should remove one stone"); return; }
            if (barrel.getItem(0).getCount() != 1) { helper.fail("initial export should place one stone in barrel, got " + barrel.getItem(0)); return; }
            level.removeBlock(unitPos, false);
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));
            for (int i = 0; i < 11; i++) bus.tick();
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 1) { helper.fail("disconnected export bus must not keep exporting via cached core"); return; }
            if (barrel.getItem(0).getCount() != 1) { helper.fail("disconnected export bus should not add another stone, got " + barrel.getItem(0)); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void import_bus_returns_exact_unaccepted_remainder_after_reentrant_capacity_change(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var source = new ReentrantItemHandler(new ItemStack(Items.STONE, 64), 64, 64);
            source.beforeActualExtract = () -> core.insertItemCount(
                    ItemKey.of(new ItemStack(Items.STONE)),
                    Long.MAX_VALUE - 32,
                    Action.EXECUTE,
                    Actor.EMPTY);
            var overflow = new java.util.ArrayList<ItemStack>();

            if (!ImportBusBlockEntity.transferOneStack(core, source, Actor.bus(corePos),
                    stack -> overflow.add(stack.copy()))) {
                helper.fail("Import transfer should extract the simulated stack");
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != Long.MAX_VALUE) {
                helper.fail("Import should fill only the remaining 32-count core capacity");
                return;
            }
            if (!source.getStackInSlot(0).is(Items.STONE) || source.getStackInSlot(0).getCount() != 32) {
                helper.fail("Import must return the exact 32-item remainder to its source slot");
                return;
            }
            if (!overflow.isEmpty()) {
                helper.fail("Source accepted the rollback, so import must not emit overflow");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void export_bus_emits_unrestorable_remainder_instead_of_losing_it(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitPos = corePos.east();
        var secondCorePos = unitPos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 64));
            var target = new ReentrantItemHandler(ItemStack.EMPTY, 64, 32);
            target.beforeActualInsert = () -> {
                level.setBlock(secondCorePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
                core.rebuildNetwork(level);
            };
            var overflow = new java.util.ArrayList<ItemStack>();

            if (!ExportBusBlockEntity.transferOneStack(core, ItemKey.of(new ItemStack(Items.STONE)), target,
                    Actor.bus(corePos), stack -> overflow.add(stack.copy()))) {
                helper.fail("Export transfer should begin after target simulation accepts the stack");
                return;
            }
            if (!target.getStackInSlot(0).is(Items.STONE) || target.getStackInSlot(0).getCount() != 32) {
                helper.fail("Reentrant target should accept exactly 32 stones");
                return;
            }
            if (overflow.size() != 1 || !overflow.get(0).is(Items.STONE) || overflow.get(0).getCount() != 32) {
                helper.fail("Export must emit the exact 32-item remainder when conflicted core rejects rollback");
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0) {
                helper.fail("Conflicted core should not silently accept the export rollback");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void crafting_menu_data_slot_parity_server_vs_buf_ctor(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            ItemStack syncedAxe = new ItemStack(Items.IRON_AXE);
            syncedAxe.setDamageValue(syncedAxe.getMaxDamage() - 37);
            if (!core.addAxeEnergy(syncedAxe)) {
                helper.fail("Could not seed finite Axe Energy for menu parity test");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var serverMenu = new CraftingTerminalMenu(34, player.getInventory(), core);
            var byteBuf = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            byteBuf.writeBlockPos(corePos);
            byteBuf.writeBlockPos(corePos);
            byteBuf.writeBoolean(false);
            MachineEnergyTable.writeSnapshot(byteBuf, MachineEnergyTable.entries());
            var bufMenu = new CraftingTerminalMenu(35, player.getInventory(), byteBuf);
            if (!serverMenu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON)
                    || serverMenu.getOutputDestination() != TerminalOutputDestination.STORAGE) {
                helper.fail("Server menu could not select Storage output before wire sync");
                return;
            }
            var serverData = dataSlots(serverMenu);
            var clientData = dataSlots(bufMenu);
            int serverCount = serverData.size();
            int bufCount = clientData.size();
            if (serverCount != bufCount) { helper.fail("crafting data-slot count mismatch: server=" + serverCount + " buf=" + bufCount); return; }
            if (serverCount != 392) { helper.fail("crafting menu should sync base 13 + crafting/fuel/resource/output/Axe Energy/Transform-card/craftable-variant 379 data slots, got " + serverCount); return; }
            for (int i = 0; i < serverData.size(); i++) {
                var wire = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                var packet = new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(
                        bufMenu.containerId, i, serverData.get(i).get());
                net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.encode(wire, packet);
                var decoded = net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC.decode(wire);
                clientData.get(decoded.getId()).set(decoded.getValue());
            }
            if (bufMenu.getOutputDestination() != TerminalOutputDestination.STORAGE) {
                helper.fail("Client menu did not receive the server-owned Storage output destination");
                return;
            }
            if (bufMenu.getAxeEnergyAmount() != 37 || bufMenu.hasInfiniteAxeEnergy()) {
                helper.fail("Client menu did not receive exact finite Axe Energy state");
                return;
            }
            int expectedSlots = StorageTerminalMenu.DISPLAY_SLOTS + 50
                    + MachineDescriptorApi.MAX_DESCRIPTORS
                    + RecipePresentation.MAX_ITEM_RESOURCES;
            if (serverMenu.slots.size() != expectedSlots || bufMenu.slots.size() != expectedSlots) {
                helper.fail("crafting menu requires fixed descriptor-bank slot parity, server="
                        + serverMenu.slots.size() + " buf=" + bufMenu.slots.size());
                return;
            }
            if (CraftingTerminalMenu.FUEL_INPUT_SLOT != StorageTerminalMenu.DISPLAY_SLOTS + 36) {
                helper.fail("Fuel input slot index must remain immediately after the 36 player slots");
                return;
            }
            var fuelInput = serverMenu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT);
            if (fuelInput.isActive()) { helper.fail("Fuel input must be inactive on the default Storage page"); return; }
            if (fuelInput.mayPlace(new ItemStack(Items.COAL))) {
                helper.fail("Inactive Storage-page fuel input must reject placement");
                return;
            }
            serverMenu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            if (!fuelInput.isActive() || !fuelInput.mayPlace(new ItemStack(Items.COAL))) {
                helper.fail("Active Auto transform input should accept coal on the Transform page");
                return;
            }
            serverMenu.clickMenuButton(player, CraftingTerminalMenu.STATIONS_PAGE_BUTTON);
            if (fuelInput.isActive()) {
                helper.fail("Transform input must be inactive on the Stations page");
                return;
            }
            int machineStart = CraftingTerminalMenu.FUEL_INPUT_SLOT + 1;
            ItemStack[] machines = {
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
            for (int i = 0; i < machines.length; i++) {
                var slot = serverMenu.getSlot(machineStart + i);
                boolean installableStation = i < MachineEnergyTable.AXE_SLOT;
                if (slot.isActive() != installableStation
                        || slot.mayPlace(machines[i]) != installableStation) {
                    helper.fail("Stations slot " + i + " active/acceptance mismatch");
                    return;
                }
                if (installableStation && slot.mayPlace(machines[(i + 1) % machines.length])) {
                    helper.fail("Stations slot " + i + " accepted the wrong machine");
                    return;
                }
            }
            int metadataStart = machineStart + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
            int metadataSlots = serverMenu.slots.size() - metadataStart;
            if (metadataSlots != RecipePresentation.MAX_ITEM_RESOURCES + 13) { helper.fail("crafting presentation metadata should include every resource plus 13 fixed hidden slots after machine equipment, got " + metadataSlots); return; }
            for (int i = metadataStart; i < serverMenu.slots.size(); i++) {
                var slot = serverMenu.getSlot(i);
                if (slot.isActive()) { helper.fail("metadata slot " + i + " must be inactive"); return; }
                if (slot.mayPlace(new ItemStack(Items.STONE))) { helper.fail("metadata slot " + i + " must reject manual placement"); return; }
                if (slot.mayPickup(player)) { helper.fail("metadata slot " + i + " must reject pickup"); return; }
                if (slot.x != -9999 || slot.y != -9999) { helper.fail("metadata slot " + i + " should live offscreen, got " + slot.x + "," + slot.y); return; }
            }
            helper.succeed();
        });
    }

    private static int dataSlotCount(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        return dataSlots(menu).size();
    }

    private static final class ReentrantItemHandler implements net.neoforged.neoforge.items.IItemHandler {
        private ItemStack stored;
        private final int simulatedInsertLimit;
        private final int actualInsertLimit;
        private Runnable beforeActualExtract;
        private Runnable beforeActualInsert;

        private ReentrantItemHandler(ItemStack stored, int simulatedInsertLimit, int actualInsertLimit) {
            this.stored = stored.copy();
            this.simulatedInsertLimit = simulatedInsertLimit;
            this.actualInsertLimit = actualInsertLimit;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stored;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            if (!simulate && beforeActualInsert != null) {
                Runnable callback = beforeActualInsert;
                beforeActualInsert = null;
                callback.run();
            }
            int limit = simulate ? simulatedInsertLimit : actualInsertLimit;
            int existing = stored.isEmpty() ? 0 : stored.getCount();
            int accepted = Math.min(stack.getCount(), Math.max(0, limit - existing));
            if (!simulate && accepted > 0) {
                if (stored.isEmpty()) stored = stack.copyWithCount(accepted);
                else stored.grow(accepted);
            }
            return accepted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (stored.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int extracted = Math.min(amount, stored.getCount());
            ItemStack result = stored.copyWithCount(extracted);
            if (!simulate) {
                if (beforeActualExtract != null) {
                    Runnable callback = beforeActualExtract;
                    beforeActualExtract = null;
                    callback.run();
                }
                stored.shrink(extracted);
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }

    private static int findPlayerMenuSlot(StorageTerminalMenu menu, net.minecraft.world.item.Item item) {
        int end = StorageTerminalMenu.DISPLAY_SLOTS + 36;
        for (int i = StorageTerminalMenu.DISPLAY_SLOTS; i < end; i++) {
            if (menu.getSlot(i).getItem().is(item)) return i;
        }
        return -1;
    }

    private static void selectFirstTransformUse(
            CraftingTerminalMenu menu,
            net.minecraft.world.entity.player.Player player
    ) {
        if (menu.getVisibleTransformUses().isEmpty()
                || !menu.clickMenuButton(
                player, CraftingTerminalMenu.transformUseButtonId(0))) {
            throw new IllegalStateException("No compatible Transform card");
        }
    }

    private static final class CountingStorageTerminalMenu extends StorageTerminalMenu {
        private int refreshCount;

        private CountingStorageTerminalMenu(
                int containerId,
                net.minecraft.world.entity.player.Inventory inventory,
                StorageCoreBlockEntity core
        ) {
            super(containerId, inventory, core);
        }

        @Override
        public void refreshDisplayItemsFiltered(StorageCoreBlockEntity core, String filter) {
            refreshCount++;
            super.refreshDisplayItemsFiltered(core, filter);
        }
    }

    private static final class CountingStorageCoreBlockEntity extends StorageCoreBlockEntity {
        private int rebuildCount;

        private CountingStorageCoreBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            super(pos, state);
        }

        @Override
        public void rebuildNetwork(net.minecraft.world.level.Level level) {
            rebuildCount++;
            super.rebuildNetwork(level);
        }
    }

    private static boolean networkCapped(StorageCoreBlockEntity core) {
        return core.isNetworkCapped();
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<net.minecraft.world.inventory.DataSlot> dataSlots(
            net.minecraft.world.inventory.AbstractContainerMenu menu) {
        try {
            var f = net.minecraft.world.inventory.AbstractContainerMenu.class.getDeclaredField("dataSlots");
            f.setAccessible(true);
            return (java.util.List<net.minecraft.world.inventory.DataSlot>) f.get(menu);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
