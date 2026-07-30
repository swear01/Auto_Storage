package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

@GameTestHolder(AutoStorage.MODID)
public class CraftingTests {

    @GameTest(template = "platform")
    public static void iron_ingot_has_smelting_and_blasting_recipes(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(0, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.IRON_INGOT));
            if (menu.getRecipeCount() < 2) helper.fail("Iron ingot should have >=2 recipes (smelt+blast)");
            boolean foundSmelting = false, foundBlasting = false;
            for (int r = 0; r < menu.getRecipeCount(); r++) {
                var type = menu.getCurrentRecipes().get(r).value().getType();
                if (type == RecipeType.SMELTING) foundSmelting = true;
                if (type == RecipeType.BLASTING) foundBlasting = true;
            }
            if (!foundSmelting) helper.fail("Smelting recipe not found for iron ingot");
            if (!foundBlasting) helper.fail("Blasting recipe not found for iron ingot");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void recipe_navigation_next_and_prev(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(1, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.IRON_INGOT));
            if (menu.getRecipeCount() < 2) { helper.fail("Need >=2 recipes for nav test"); return; }
            int start = menu.getCurrentRecipeIndex();
            menu.nextRecipe();
            int afterNext = menu.getCurrentRecipeIndex();
            if (afterNext == start) helper.fail("nextRecipe should change index");
            menu.prevRecipe();
            if (menu.getCurrentRecipeIndex() != start) helper.fail("prevRecipe should return to start");
            // Wrap around should go to first recipe
            for (int i = 0; i < menu.getRecipeCount(); i++) menu.nextRecipe();
            if (menu.getCurrentRecipeIndex() != 0) helper.fail("Wrapping should return to recipe 0");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void exact_recipe_selection_accepts_supported_backing_recipe_and_rejects_stale_id(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.IRON_INGOT));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(43, player.getInventory(), core);
            menu.refreshDisplayItems(core);
            int ironSlot = findDisplaySlot(menu, Items.IRON_INGOT);
            if (ironSlot < 0) { helper.fail("Iron ingot display slot not found"); return; }

            var blasting = level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING).stream()
                    .filter(holder -> holder.value().getResultItem(level.registryAccess()).is(Items.IRON_INGOT))
                    .findFirst().orElse(null);
            if (blasting == null) { helper.fail("Iron ingot blasting recipe not found"); return; }
            if (!CraftingTerminalMenu.supportsRecipeContract(blasting.value())) {
                helper.fail("Generic blasting recipe should be supported by the terminal contract");
                return;
            }
            if (!menu.selectRecipe(level, ironSlot, blasting.id(), player)) {
                helper.fail("Exact supported recipe selection should succeed");
                return;
            }
            if (!menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex()).id().equals(blasting.id())) {
                helper.fail("Exact recipe selection chose a different same-output recipe");
                return;
            }

            int selectedIndex = menu.getCurrentRecipeIndex();
            if (menu.selectRecipe(level, ironSlot,
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "missing_recipe"), player)) {
                helper.fail("Unknown recipe id must fail closed");
                return;
            }
            if (menu.getCurrentRecipeIndex() != selectedIndex) {
                helper.fail("Rejected recipe selection must preserve the current recipe");
                return;
            }

            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            if (menu.selectRecipe(level, ironSlot, blasting.id(), player)) {
                helper.fail("Fuel page must reject server-side recipe selection");
                return;
            }
            menu.clickMenuButton(player, CraftingTerminalMenu.STORAGE_PAGE_BUTTON);

            var smithing = level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING).stream()
                    .filter(holder -> holder.value() instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe)
                    .findFirst().orElse(null);
            if (smithing == null) { helper.fail("Smithing recipe not found"); return; }
            if (!CraftingTerminalMenu.supportsRecipeContract(smithing.value())) {
                helper.fail("Deterministic smithing-transform recipes must use the component-preserving contract");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void toggle_show_only_craftable(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(2, player.getInventory(), core);
            if (menu.getPage() != CraftingTerminalPage.STORAGE) helper.fail("Storage should be the default page");
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            if (menu.getPage() != CraftingTerminalPage.CRAFTABLE) helper.fail("Craftable button should select its page");
            menu.clickMenuButton(player, CraftingTerminalMenu.STORAGE_PAGE_BUTTON);
            if (menu.getPage() != CraftingTerminalPage.STORAGE) helper.fail("Storage button should select its page");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void exact_quantity_is_atomic_and_max_exhausts_the_fresh_preview(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 10));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(120, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            if (!menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                    1, CraftingDestination.NONE, player)) {
                helper.fail("Exact stick recipe should be selectable");
                return;
            }
            if (menu.computeCraftPreview(core, player).craftable() != 5) {
                helper.fail("Ten planks should preview exactly five stick recipe executions");
                return;
            }

            menu.clickMenuButton(player, 3);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 10
                    || countInInventory(player, Items.STICK) != 0) {
                helper.fail("Exact ×8 must be a complete no-op when only five crafts are possible");
                return;
            }

            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0
                    || countInInventory(player, Items.STICK) != 20) {
                helper.fail("Max must consume all ten planks and produce five stick recipe results");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void max_craft_handles_counts_above_single_item_stack_limit(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);

            ItemStack first = new ItemStack(Items.OAK_PLANKS);
            first.setCount(Integer.MAX_VALUE);
            ItemStack second = first.copy();
            if (core.insertItem(first) != Integer.MAX_VALUE
                    || core.insertItem(second) != Integer.MAX_VALUE) {
                helper.fail("Test setup could not store two Integer.MAX_VALUE plank chunks");
                return;
            }
            if (core.insertItem(new ItemStack(Items.OAK_PLANKS, 2)) != 2) {
                helper.fail("Test setup could not cross the Integer.MAX_VALUE craft boundary");
                return;
            }
            var stickRecipe = level.getRecipeManager().byKey(
                    ResourceLocation.withDefaultNamespace("stick")
            ).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            var menu = new CraftingTerminalMenu(138, player.getInventory(), core);
            menu.getCurrentRecipes().add(stickRecipe);
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            if (menu.getOutputDestination() != TerminalOutputDestination.STORAGE) {
                helper.fail("Long-count Max setup must select Storage output");
                return;
            }

            var plankDeltas = new java.util.ArrayList<Long>();
            var stickDeltas = new java.util.ArrayList<Long>();
            core.addListener((key, delta, amount, actor) -> {
                if (key.equals(ItemKey.of(new ItemStack(Items.OAK_PLANKS)))) plankDeltas.add(delta);
                if (key.equals(ItemKey.of(new ItemStack(Items.STICK)))) stickDeltas.add(delta);
            });

            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);

            long consumedPlanks = 2L * Integer.MAX_VALUE + 2L;
            long expectedSticks = 4L * (Integer.MAX_VALUE + 1L);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0) {
                helper.fail("Max must consume the complete long-count plank reservation");
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != expectedSticks) {
                helper.fail("Max must route all " + expectedSticks + " sticks into Core without int overflow");
                return;
            }
            if (countInInventory(player, Items.STICK) != 0) {
                helper.fail("Full player inventory must keep the long-count output in Core");
                return;
            }
            if (!plankDeltas.equals(java.util.List.of(-consumedPlanks))
                    || !stickDeltas.equals(java.util.List.of(expectedSticks))) {
                helper.fail("Long-count Max must commit one bulk extraction and one bulk insertion: planks="
                        + plankDeltas + " sticks=" + stickDeltas);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void max_uses_largest_deliverable_amount_instead_of_rejecting_all(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 5));
            Item[] fillerTypes = {
                    Items.STONE, Items.DIRT, Items.GRANITE, Items.DIORITE, Items.ANDESITE,
                    Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.SAND
            };
            for (Item item : fillerTypes) core.insertItem(new ItemStack(item));
            if (core.getTypeCount() != core.getTotalTypeSlots()) {
                helper.fail("Core setup must fill every type slot");
                return;
            }

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            player.getInventory().setItem(0, new ItemStack(Items.STICK, 60));
            var stickRecipe = level.getRecipeManager().byKey(
                    ResourceLocation.withDefaultNamespace("stick")
            ).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var menu = new CraftingTerminalMenu(139, player.getInventory(), core);
            menu.getCurrentRecipes().add(stickRecipe);

            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);

            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 3
                    || player.getInventory().getItem(0).getCount() != 64
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != 0) {
                helper.fail("Max must fall back from two blocked crafts to one deliverable craft");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void stale_max_request_recomputes_server_state_and_consumes_nothing(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            ItemKey planks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 4));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(121, player.getInventory(), core);
            if (!menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                    1, CraftingDestination.NONE, player)) {
                helper.fail("Exact stick recipe should be selectable");
                return;
            }
            if (menu.computeCraftPreview(core, player).craftable() != 2) {
                helper.fail("Test setup must preview two stick crafts");
                return;
            }
            core.extractItem(planks, 4);
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getItemCount(planks) != 0 || countInInventory(player, Items.STICK) != 0) {
                helper.fail("Max must not trust stale client preview state");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform", batch = "crafting_max_above_preview_cap")
    public static void max_exhausts_more_than_the_synced_preview_cap_into_inventory_and_core(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            int crafts = 10_005;
            ItemKey dirt = ItemKey.of(new ItemStack(Items.DIRT));
            core.insertItem(new ItemStack(Items.DIRT, crafts));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "max_above_preview_cap"),
                    new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.STONE),
                            NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT)))
            );
            var menu = new CraftingTerminalMenu(122, player.getInventory(), core);
            withTemporaryRegisteredRecipe(level, holder, () -> {
                if (!menu.handleRecipeRequest(level, holder.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Test recipe should be selectable");
                    return;
                }
                if (menu.computeCraftPreview(core, player).craftable() != 9_999) {
                    helper.fail("Normal synced preview must remain safely capped at 9,999");
                    return;
                }
                menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
                int inventory = countInInventory(player, Items.STONE);
                long stored = core.getItemCount(ItemKey.of(new ItemStack(Items.STONE)));
                int dropped = level.getEntitiesOfClass(
                        ItemEntity.class,
                        player.getBoundingBox().inflate(16),
                        entity -> entity.getItem().is(Items.STONE)
                ).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
                if (core.getItemCount(dirt) != 0 || inventory + stored != crafts || dropped != 0) {
                    helper.fail("Max must exhaust all crafts above the preview cap into inventory and Core, remaining="
                            + core.getItemCount(dirt) + " inventory=" + inventory
                            + " stored=" + stored + " dropped=" + dropped);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void craftable_only_filters_grid_to_craftable_outputs(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 64));
            core.insertItem(new ItemStack(Items.STICK, 1));
            core.insertItem(new ItemStack(Items.DIAMOND, 1));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(20, player.getInventory(), core);

            menu.refreshDisplayItems(core);
            if (menu.getTotalItemTypes() != 3) { helper.fail("unfiltered grid should show 3 item types, got " + menu.getTotalItemTypes()); return; }

            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            menu.refreshDisplayItems(core);
            int stickSlot = findDisplaySlot(menu, Items.STICK);
            if (stickSlot < 0) {
                helper.fail("Craftable-only grid should include sticks from available planks");
                return;
            }
            ItemStack displayedStick = menu.getSlot(stickSlot).getItem();
            if (displayedStick.getCount() != 1 || TerminalDisplayStack.amount(displayedStick) != 1) {
                helper.fail("Craftable Stick must display the exact one currently stored, not potential craft yield");
                return;
            }
            if (findDisplaySlot(menu, Items.DIAMOND) >= 0) {
                helper.fail("Stored but currently uncraftable diamond must not appear in Craftable mode");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void craftable_only_lists_zero_storage_charcoal_from_logs(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.OAK_LOG));
            var charcoalRecipe = findCookingRecipe(level, RecipeType.SMELTING, Items.OAK_LOG, Items.CHARCOAL);
            if (charcoalRecipe == null) {
                helper.fail("Vanilla Oak Log to Charcoal smelting recipe not found");
                return;
            }
            int cookingTime = ((AbstractCookingRecipe) charcoalRecipe.value()).getCookingTime();
            for (int i = 0; i < cookingTime; i++) core.tick();
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.CHARCOAL))) != 0) {
                helper.fail("Test requires zero stored Charcoal");
                return;
            }

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(61, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            menu.refreshDisplayItems(core);
            int charcoalSlot = findDisplaySlot(menu, Items.CHARCOAL);
            if (charcoalSlot < 0) {
                helper.fail("Craftable mode must synthesize zero-storage Charcoal from available Oak Logs");
                return;
            }
            ItemStack displayedCharcoal = menu.getSlot(charcoalSlot).getItem();
            if (displayedCharcoal.getCount() != 1 || TerminalDisplayStack.amount(displayedCharcoal) != 0) {
                helper.fail("Zero-storage Charcoal must stay visible without displaying a fake quantity");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void zero_storage_craftable_output_selects_and_crafts_without_becoming_extractable(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.OAK_LOG));
            var charcoalRecipe = findCookingRecipe(level, RecipeType.SMELTING, Items.OAK_LOG, Items.CHARCOAL);
            if (charcoalRecipe == null) {
                helper.fail("Vanilla Oak Log to Charcoal smelting recipe not found");
                return;
            }
            int cookingTime = ((AbstractCookingRecipe) charcoalRecipe.value()).getCookingTime();
            for (int i = 0; i < cookingTime; i++) core.tick();
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);
            long processBefore = core.getEnergy(EnergyType.SMELTING_ENERGY);
            long fuelBefore = core.getEnergy(EnergyType.FURNACE_FUEL);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(62, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            menu.refreshDisplayItems(core);
            int charcoalSlot = findDisplaySlot(menu, Items.CHARCOAL);
            if (charcoalSlot < 0) {
                helper.fail("Zero-storage Charcoal craftable entry not found");
                return;
            }

            ItemStack shifted = menu.quickMoveStack(player, charcoalSlot);
            if (!shifted.isEmpty() || countInInventory(player, Items.CHARCOAL) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.CHARCOAL))) != 0) {
                helper.fail("Synthetic Craftable output must never extract as stored inventory");
                return;
            }

            menu.clicked(charcoalSlot, 0, ClickType.PICKUP, player);
            if (!menu.getSelectedStack().is(Items.CHARCOAL)) {
                helper.fail("Synthetic Charcoal entry should select its recipe output");
                return;
            }
            menu.craftItem(1, player);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_LOG))) != 0
                    || countInInventory(player, Items.CHARCOAL) != 1) {
                helper.fail("Selected synthetic Charcoal recipe should consume one log and produce one Charcoal");
                return;
            }
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != processBefore - cookingTime
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != fuelBefore - cookingTime) {
                helper.fail("Synthetic Charcoal craft must consume the exact recipe cooking time");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void exact_recipe_selection_accepts_zero_storage_craftable_output(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.OAK_LOG));
            var charcoalRecipe = findCookingRecipe(level, RecipeType.SMELTING, Items.OAK_LOG, Items.CHARCOAL);
            if (charcoalRecipe == null) {
                helper.fail("Vanilla Oak Log to Charcoal smelting recipe not found");
                return;
            }
            int cookingTime = ((AbstractCookingRecipe) charcoalRecipe.value()).getCookingTime();
            for (int i = 0; i < cookingTime; i++) core.tick();
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(66, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            menu.refreshDisplayItems(core);
            int charcoalSlot = findDisplaySlot(menu, Items.CHARCOAL);
            if (charcoalSlot < 0) {
                helper.fail("Zero-storage Charcoal craftable entry not found");
                return;
            }
            if (!menu.selectRecipe(level, charcoalSlot, charcoalRecipe.id(), player)) {
                helper.fail("Exact recipe selection must not require its output to exist in Core storage");
                return;
            }
            if (!menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex()).id().equals(charcoalRecipe.id())) {
                helper.fail("Zero-storage selection must retain the requested exact recipe identity");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void crafting_recipe_request_packet_round_trips_amount_and_destination(GameTestHelper helper) {
        var recipeId = ResourceLocation.withDefaultNamespace("stick");
        var wire = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        var packet = new CraftingRecipeSelectionPacket(
                67, recipeId, 8, CraftingDestination.INVENTORY);
        CraftingRecipeSelectionPacket.STREAM_CODEC.encode(wire, packet);
        var decoded = CraftingRecipeSelectionPacket.STREAM_CODEC.decode(wire);
        if (decoded.containerId() != 67 || !decoded.recipeId().equals(recipeId)
                || decoded.amount() != 8 || decoded.destination() != CraftingDestination.INVENTORY) {
            helper.fail("Crafting recipe request packet must preserve exact recipe, amount, and destination");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void recipe_request_none_selects_exact_recipe_without_consuming(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 2));
            var stickRecipe = level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("stick")).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(68, player.getInventory(), core);
            if (!menu.handleRecipeRequest(level, stickRecipe.id(), 1, CraftingDestination.NONE, player)) {
                helper.fail("NONE request should select an exact supported recipe without visible/stored output");
                return;
            }
            menu.refreshDisplayItems(core);
            if (!menu.getSelectedStack().is(Items.STICK)
                    || !menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex()).id().equals(stickRecipe.id())) {
                helper.fail("NONE request must retain exact selected recipe identity after a grid refresh");
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 2
                    || countInInventory(player, Items.STICK) != 0) {
                helper.fail("NONE request must consume and produce nothing");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void inventory_recipe_request_crafts_up_to_requested_amount(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 4));
            var stickRecipe = level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("stick")).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(69, player.getInventory(), core);
            if (!menu.handleRecipeRequest(level, stickRecipe.id(), 8, CraftingDestination.INVENTORY, player)) {
                helper.fail("Inventory request should execute the exact one-level recipe");
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0
                    || countInInventory(player, Items.STICK) != 8) {
                helper.fail("Eight requested crafts are an upper bound: four planks should produce eight sticks");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void cursor_recipe_request_respects_capacity_and_incompatible_stack(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 4));
            var stickRecipe = level.getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("stick")).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(70, player.getInventory(), core);
            menu.setCarried(new ItemStack(Items.DIAMOND));
            if (menu.handleRecipeRequest(level, stickRecipe.id(), 64, CraftingDestination.CURSOR, player)) {
                helper.fail("Incompatible cursor must reject immediate crafting");
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 4
                    || !menu.getCarried().is(Items.DIAMOND)) {
                helper.fail("Rejected cursor request must preserve ingredients and cursor");
                return;
            }

            menu.setCarried(new ItemStack(Items.STICK, 56));
            if (!menu.handleRecipeRequest(level, stickRecipe.id(), 64, CraftingDestination.CURSOR, player)) {
                helper.fail("Compatible cursor should accept the largest legal one-level craft amount");
                return;
            }
            if (!menu.getCarried().is(Items.STICK) || menu.getCarried().getCount() != 64
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0) {
                helper.fail("Cursor capacity eight should consume four planks and add exactly eight sticks");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void recipe_request_rejects_invalid_amount_stale_id_and_fuel_page(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 2));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(71, player.getInventory(), core);
            var stickId = ResourceLocation.withDefaultNamespace("stick");
            if (menu.handleRecipeRequest(level, stickId, 0, CraftingDestination.INVENTORY, player)
                    || menu.handleRecipeRequest(level, stickId, 65, CraftingDestination.INVENTORY, player)
                    || menu.handleRecipeRequest(level,
                            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "missing_emi_recipe"),
                            1, CraftingDestination.INVENTORY, player)) {
                helper.fail("Invalid amount or stale recipe ID must fail closed");
                return;
            }
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            if (menu.handleRecipeRequest(level, stickId, 1, CraftingDestination.INVENTORY, player)) {
                helper.fail("Fuel page must reject EMI recipe requests");
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 2
                    || countInInventory(player, Items.STICK) != 0) {
                helper.fail("Rejected recipe requests must preserve all state");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void inventory_recipe_request_uses_slot_freed_by_player_ingredients(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
            var menu = new CraftingTerminalMenu(72, player.getInventory(), core);
            menu.toggleUsePlayerInventory();
            if (!menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                    64, CraftingDestination.INVENTORY, player)) {
                helper.fail("Player ingredients should free exact capacity for their requested output");
                return;
            }
            if (!player.getInventory().getItem(0).is(Items.STICK)
                    || player.getInventory().getItem(0).getCount() != 4
                    || countInInventory(player, Items.OAK_PLANKS) != 0) {
                helper.fail("Consumed plank slot should receive exactly four crafted sticks");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void inventory_recipe_request_returns_full_destination_overflow_to_core(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 2));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            var menu = new CraftingTerminalMenu(73, player.getInventory(), core);
            if (!menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                    1, CraftingDestination.INVENTORY, player)) {
                helper.fail("A full inventory should return immediate-craft overflow to Core");
                return;
            }
            int dropped = level.getEntitiesOfClass(
                    ItemEntity.class,
                    player.getBoundingBox().inflate(16),
                    entity -> entity.getItem().is(Items.STICK)
            ).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != 4
                    || countInInventory(player, Items.STICK) != 0 || dropped != 0) {
                helper.fail("Immediate crafting must preserve overflow in Core without item entities");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void direct_craft_returns_inventory_overflow_to_core_without_dropping(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            ItemKey planks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
            ItemKey sticks = ItemKey.of(new ItemStack(Items.STICK));
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 2));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            var menu = new CraftingTerminalMenu(130, player.getInventory(), core);
            if (!menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                    1, CraftingDestination.NONE, player)) {
                helper.fail("Exact stick recipe could not be selected");
                return;
            }

            menu.craftItem(1, player);

            int dropped = level.getEntitiesOfClass(
                    ItemEntity.class,
                    player.getBoundingBox().inflate(16),
                    entity -> entity.getItem().is(Items.STICK)
            ).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            if (core.getItemCount(planks) != 0 || core.getItemCount(sticks) != 4
                    || countInInventory(player, Items.STICK) != 0 || dropped != 0) {
                helper.fail("Full player inventory must route all crafted output to Core without item entities: "
                        + "planks=" + core.getItemCount(planks)
                        + " coreSticks=" + core.getItemCount(sticks)
                        + " inventorySticks=" + countInInventory(player, Items.STICK)
                        + " dropped=" + dropped);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void direct_craft_rejects_atomically_when_inventory_and_core_are_full(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 3));
            Item[] fillerTypes = {
                    Items.STONE, Items.DIRT, Items.GRANITE, Items.DIORITE, Items.ANDESITE,
                    Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.SAND
            };
            for (Item item : fillerTypes) {
                if (core.insertItem(new ItemStack(item)) != 1) {
                    helper.fail("Could not fill Core type slots with " + item);
                    return;
                }
            }
            if (core.getTypeCount() != core.getTotalTypeSlots()) {
                helper.fail("Core setup must fill all type slots");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            var menu = new CraftingTerminalMenu(131, player.getInventory(), core);
            if (!menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                    1, CraftingDestination.NONE, player)) {
                helper.fail("Exact stick recipe could not be selected");
                return;
            }

            menu.craftItem(1, player);

            int dropped = level.getEntitiesOfClass(
                    ItemEntity.class,
                    player.getBoundingBox().inflate(16),
                    entity -> entity.getItem().is(Items.STICK)
            ).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 3
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != 0
                    || countInInventory(player, Items.STICK) != 0 || dropped != 0) {
                helper.fail("No output capacity must reject the whole craft without consumption or drops");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void direct_storage_destination_routes_primary_and_container_remainders_to_core(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.MILK_BUCKET));
            var recipe = new ShapelessRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    new ItemStack(Items.DIAMOND),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.MILK_BUCKET))
            );
            var holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "storage_output_remainder_test"),
                    recipe
            );
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(167, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                menu.craftItem(1, player);
                if (menu.getOutputDestination() != TerminalOutputDestination.STORAGE) {
                    helper.fail("Direct craft destination changed during execution");
                    return;
                }
                if (core.getItemCount(ItemKey.of(new ItemStack(Items.MILK_BUCKET))) != 0
                        || core.getItemCount(ItemKey.of(new ItemStack(Items.DIAMOND))) != 1
                        || core.getItemCount(ItemKey.of(new ItemStack(Items.BUCKET))) != 1) {
                    helper.fail("Storage output must keep both primary output and container remainder in Core");
                    return;
                }
                if (countInInventory(player, Items.DIAMOND) != 0
                        || countInInventory(player, Items.BUCKET) != 0) {
                    helper.fail("Storage output must not place primary output or remainder in player inventory");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void direct_storage_destination_accepts_exact_long_capacity(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            loadCoreItems(
                    core,
                    level.registryAccess(),
                    new CoreItemCount(new ItemStack(Items.OAK_PLANKS), 2),
                    new CoreItemCount(new ItemStack(Items.STICK), Long.MAX_VALUE - 4));
            installAllRecipeStations(core);
            var stickRecipe = level.getRecipeManager().byKey(
                    ResourceLocation.withDefaultNamespace("stick")
            ).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(168, player.getInventory(), core);
            menu.getCurrentRecipes().add(stickRecipe);
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            menu.craftItem(1, player);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != Long.MAX_VALUE
                    || countInInventory(player, Items.STICK) != 0) {
                helper.fail("Exact remaining Core count capacity must accept the complete Storage batch");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void direct_storage_destination_rejects_one_item_short_without_mutation(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            long initialSticks = Long.MAX_VALUE - 3;
            loadCoreItems(
                    core,
                    level.registryAccess(),
                    new CoreItemCount(new ItemStack(Items.OAK_PLANKS), 2),
                    new CoreItemCount(new ItemStack(Items.STICK), initialSticks));
            installAllRecipeStations(core);
            var stickRecipe = level.getRecipeManager().byKey(
                    ResourceLocation.withDefaultNamespace("stick")
            ).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(169, player.getInventory(), core);
            menu.getCurrentRecipes().add(stickRecipe);
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            menu.craftItem(1, player);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 2
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != initialSticks
                    || countInInventory(player, Items.STICK) != 0) {
                helper.fail("One-item-short Storage capacity must reject the whole batch before mutation");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void every_direct_amount_button_honors_storage_destination(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 156));
            var stickRecipe = level.getRecipeManager().byKey(
                    ResourceLocation.withDefaultNamespace("stick")
            ).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(170, player.getInventory(), core);
            menu.getCurrentRecipes().add(stickRecipe);
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            menu.clickMenuButton(player, 2);
            menu.clickMenuButton(player, 3);
            menu.clickMenuButton(player, 4);
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != 312
                    || countInInventory(player, Items.STICK) != 0) {
                helper.fail("×1, ×8, ×64, and Max must all route direct output through Storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void emi_cursor_and_inventory_destinations_override_terminal_storage_toggle(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 4));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(171, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            ResourceLocation stickId = ResourceLocation.withDefaultNamespace("stick");
            if (!menu.handleRecipeRequest(level, stickId, 1, CraftingDestination.CURSOR, player)
                    || !menu.getCarried().is(Items.STICK) || menu.getCarried().getCount() != 4) {
                helper.fail("EMI Cursor destination must remain authoritative over terminal Storage output");
                return;
            }
            menu.setCarried(ItemStack.EMPTY);
            if (!menu.handleRecipeRequest(level, stickId, 1, CraftingDestination.INVENTORY, player)
                    || countInInventory(player, Items.STICK) != 4) {
                helper.fail("EMI Inventory destination must remain authoritative over terminal Storage output");
                return;
            }
            if (menu.getOutputDestination() != TerminalOutputDestination.STORAGE
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != 0) {
                helper.fail("EMI requests must not mutate or obey the terminal session destination");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void ingredient_source_and_output_destination_are_independent(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var stickRecipe = level.getRecipeManager().byKey(
                    ResourceLocation.withDefaultNamespace("stick")
            ).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
            var menu = new CraftingTerminalMenu(172, player.getInventory(), core);
            menu.getCurrentRecipes().add(stickRecipe);
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            menu.craftItem(1, player);
            if (player.getInventory().getItem(0).getCount() != 2
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != 0) {
                helper.fail("Storage destination must not implicitly enable player ingredient sources");
                return;
            }
            menu.toggleUsePlayerInventory();
            menu.craftItem(1, player);
            if (!player.getInventory().getItem(0).isEmpty()
                    || countInInventory(player, Items.STICK) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != 4
                    || menu.getOutputDestination() != TerminalOutputDestination.STORAGE) {
                helper.fail("Enabled player source must feed a craft whose output still goes only to Storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void storage_output_commit_failure_rolls_back_core_ingredients(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity originalCore)) {
            helper.fail("Original Core not found");
            return;
        }
        var reference = new net.minecraft.nbt.CompoundTag();
        originalCore.saveAdditional(reference, level.registryAccess());
        level.removeBlockEntity(corePos);
        var rejectingCore = new RejectingCraftOutputCore(
                corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState());
        rejectingCore.loadAdditional(reference, level.registryAccess());
        level.setBlockEntity(rejectingCore);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof RejectingCraftOutputCore core)) {
                helper.fail("Rejecting Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 2));
            var stickRecipe = level.getRecipeManager().byKey(
                    ResourceLocation.withDefaultNamespace("stick")
            ).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(173, player.getInventory(), core);
            menu.getCurrentRecipes().add(stickRecipe);
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            core.rejectCraftOutput = true;
            menu.craftItem(1, player);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 2
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != 0
                    || countInInventory(player, Items.STICK) != 0) {
                helper.fail("Rejected Storage output insertion must roll back every extracted ingredient");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void inventory_recipe_request_uses_inventory_then_core_fallback(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 4));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            player.getInventory().setItem(0, new ItemStack(Items.STICK, 60));
            var menu = new CraftingTerminalMenu(74, player.getInventory(), core);
            if (!menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                    64, CraftingDestination.INVENTORY, player)) {
                helper.fail("Capacity for one whole result should execute one recipe");
                return;
            }
            if (player.getInventory().getItem(0).getCount() != 64
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != 4
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0) {
                helper.fail("Request must fill the inventory first and return remaining output to Core");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void recipe_request_amount_is_a_craft_upper_bound(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 4));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(75, player.getInventory(), core);
            if (!menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                    1, CraftingDestination.INVENTORY, player)) {
                helper.fail("One requested craft should execute when legal");
                return;
            }
            if (countInInventory(player, Items.STICK) != 4
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 2) {
                helper.fail("Amount one must produce one recipe result, not exhaust available materials");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void cooking_recipe_request_consumes_exact_recipe_time(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.OAK_LOG));
            var recipe = findCookingRecipe(level, RecipeType.SMELTING, Items.OAK_LOG, Items.CHARCOAL);
            if (recipe == null) {
                helper.fail("Vanilla Oak Log to Charcoal smelting recipe not found");
                return;
            }
            int cookingTime = ((AbstractCookingRecipe) recipe.value()).getCookingTime();
            for (int tick = 0; tick < cookingTime; tick++) core.tick();
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);
            long processBefore = core.getEnergy(EnergyType.SMELTING_ENERGY);
            long fuelBefore = core.getEnergy(EnergyType.FURNACE_FUEL);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(76, player.getInventory(), core);
            if (!menu.handleRecipeRequest(level, recipe.id(), 64,
                    CraftingDestination.INVENTORY, player)) {
                helper.fail("Exact cooking recipe request should execute when fully funded");
                return;
            }
            if (countInInventory(player, Items.CHARCOAL) != 1
                    || core.getEnergy(EnergyType.SMELTING_ENERGY) != processBefore - cookingTime
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != fuelBefore - cookingTime) {
                helper.fail("Cooking request must spend the concrete recipe time from both pools");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void craftable_catalog_respects_search_and_quantity_sort(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.OAK_LOG));
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 2));
            var charcoalRecipe = findCookingRecipe(level, RecipeType.SMELTING, Items.OAK_LOG, Items.CHARCOAL);
            if (charcoalRecipe == null) {
                helper.fail("Vanilla Oak Log to Charcoal smelting recipe not found");
                return;
            }
            int cookingTime = ((AbstractCookingRecipe) charcoalRecipe.value()).getCookingTime();
            for (int i = 0; i < cookingTime; i++) core.tick();
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(63, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            menu.clickMenuButton(player, 12);
            menu.clickMenuButton(player, 11);
            menu.refreshDisplayItems(core);
            if (!menu.getSlot(0).getItem().is(Items.OAK_PLANKS)
                    || menu.getSlot(0).getItem().getCount() != 1
                    || TerminalDisplayStack.amount(menu.getSlot(0).getItem()) != 2) {
                helper.fail("Descending quantity sort must use the two Oak Planks currently stored");
                return;
            }
            int charcoalSlot = findDisplaySlot(menu, Items.CHARCOAL);
            if (charcoalSlot < 0 || TerminalDisplayStack.amount(menu.getSlot(charcoalSlot).getItem()) != 0) {
                helper.fail("Quantity sorting must retain zero-storage Charcoal");
                return;
            }

            menu.applyFilter(core, "charcoal");
            if (menu.getTotalItemTypes() != 1 || !menu.getSlot(0).getItem().is(Items.CHARCOAL)) {
                helper.fail("Craftable search should filter recipe outputs, not stored ingredients");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void craftable_catalog_rebuilds_when_recipe_manager_contents_change(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.DIRT));
            var holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "craftable_catalog_reload"),
                    new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.EMERALD),
                            NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT)))
            );
            var manager = level.getRecipeManager();
            var originalRecipes = java.util.List.copyOf(manager.getRecipes());
            var withTestRecipe = new java.util.ArrayList<>(originalRecipes);
            withTestRecipe.add(holder);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(64, player.getInventory(), core);
            try {
                manager.replaceRecipes(withTestRecipe);
                menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
                menu.refreshDisplayItems(core);
                if (findDisplaySlot(menu, Items.EMERALD) < 0) {
                    helper.fail("New current RecipeManager contents must enter the Craftable catalog");
                    return;
                }

                manager.replaceRecipes(originalRecipes);
                menu.refreshDisplayItems(core);
                if (findDisplaySlot(menu, Items.EMERALD) >= 0) {
                    helper.fail("Removed recipe must leave the Craftable catalog without reopening the menu");
                    return;
                }
            } finally {
                manager.replaceRecipes(originalRecipes);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void craftable_grid_refreshes_when_machine_energy_crosses_recipe_threshold(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.OAK_LOG));
            var charcoalRecipe = findCookingRecipe(level, RecipeType.SMELTING, Items.OAK_LOG, Items.CHARCOAL);
            if (charcoalRecipe == null) {
                helper.fail("Vanilla Oak Log to Charcoal smelting recipe not found");
                return;
            }
            int cookingTime = ((AbstractCookingRecipe) charcoalRecipe.value()).getCookingTime();
            for (int i = 1; i < cookingTime; i++) core.tick();
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(65, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            menu.refreshDisplayItems(core);
            if (findDisplaySlot(menu, Items.CHARCOAL) >= 0) {
                helper.fail("Charcoal must not appear one process tick early");
                return;
            }

            core.tick();
            menu.broadcastChanges();
            if (findDisplaySlot(menu, Items.CHARCOAL) < 0) {
                helper.fail("Open Craftable grid must refresh when machine energy reaches the recipe time");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void craftable_grid_does_not_rescan_before_machine_energy_threshold(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_LOG));
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CountingRefreshMenu(167, player.getInventory(), core);
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            int refreshesBeforeTick = menu.refreshes;

            core.tick();
            menu.broadcastChanges();
            if (menu.refreshes != refreshesBeforeTick) {
                helper.fail("A sub-threshold machine tick rebuilt the full Craftable grid");
                return;
            }
            menu.observeStationWorkChange(core);
            if (menu.descriptorStateSends != 1) {
                helper.fail("Station work changed without synchronizing the descriptor amount");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void warm_craftable_switch_reuses_the_visible_snapshot(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_LOG));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CountingRefreshMenu(168, player.getInventory(), core);

            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.STORAGE_PAGE_BUTTON);
            int refreshesBeforeWarmSwitch = menu.refreshes;
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);

            if (menu.refreshes != refreshesBeforeWarmSwitch
                    || findDisplaySlot(menu, Items.OAK_PLANKS) < 0) {
                helper.fail("An unchanged warm Craftable switch rebuilt the full catalog");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void craftable_prefetch_tracks_player_inventory_setting(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG));
            var menu = new CraftingTerminalMenu(169, player.getInventory(), core);

            menu.broadcastChanges();
            menu.clickMenuButton(player, 7);
            menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);

            if (findDisplaySlot(menu, Items.OAK_PLANKS) < 0) {
                helper.fail("Craftable cache ignored the enabled player inventory source");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void constructor_preserves_component_variants_without_compact_grid(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.DIAMOND_SWORD));
            ItemStack damagedSword = new ItemStack(Items.DIAMOND_SWORD);
            damagedSword.setDamageValue(1);
            core.insertItem(damagedSword);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(21, player.getInventory(), core);

            if (menu.getTotalItemTypes() != 2) {
                helper.fail("Crafting terminal must preserve both sword component variants, got " + menu.getTotalItemTypes());
                return;
            }
            int total = 0;
            for (int slot = 0; slot < menu.getTotalItemTypes(); slot++) {
                if (menu.getSlot(slot).getItem().is(Items.DIAMOND_SWORD)) {
                    total += menu.getSlot(slot).getItem().getCount();
                }
            }
            if (total != 2) {
                helper.fail("Separate sword variants should retain their exact total count, got " + total);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void full_identity_grid_keeps_same_name_items_separate(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.CREEPER_BANNER_PATTERN));
            core.insertItem(new ItemStack(Items.FLOW_BANNER_PATTERN));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(52, player.getInventory(), core);

            if (menu.getTotalItemTypes() != 2) {
                helper.fail("Full-identity grid must not merge distinct items sharing the name Banner Pattern");
                return;
            }
            boolean foundCreeper = false;
            boolean foundFlow = false;
            for (int slot = 0; slot < menu.getTotalItemTypes(); slot++) {
                foundCreeper |= menu.getSlot(slot).getItem().is(Items.CREEPER_BANNER_PATTERN);
                foundFlow |= menu.getSlot(slot).getItem().is(Items.FLOW_BANNER_PATTERN);
            }
            if (!foundCreeper || !foundFlow) {
                helper.fail("Grid must preserve both same-name item identities");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void toggle_use_player_inventory(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(3, player.getInventory(), core);
            if (menu.isUsePlayerInventory()) helper.fail("should default false");
            menu.toggleUsePlayerInventory();
            if (!menu.isUsePlayerInventory()) helper.fail("toggle should enable");
            menu.toggleUsePlayerInventory();
            if (menu.isUsePlayerInventory()) helper.fail("toggle again should disable");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void output_destination_toggle_is_independent_and_does_not_mutate_grid(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.DIAMOND_SWORD));
            ItemStack damagedSword = new ItemStack(Items.DIAMOND_SWORD);
            damagedSword.setDamageValue(1);
            core.insertItem(damagedSword);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(4, player.getInventory(), core);
            int before = menu.getTotalItemTypes();
            if (menu.getOutputDestination() != TerminalOutputDestination.PLAYER) {
                helper.fail("Direct craft output must default to Player");
                return;
            }
            menu.toggleUsePlayerInventory();
            if (!menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON)
                    || menu.getOutputDestination() != TerminalOutputDestination.STORAGE) {
                helper.fail("Output destination button must select Storage");
                return;
            }
            if (!menu.isUsePlayerInventory()) {
                helper.fail("Output destination must not change the independent ingredient source");
                return;
            }
            if (menu.getTotalItemTypes() != before || before != 2) {
                helper.fail("Output destination must not mutate the full-identity grid");
                return;
            }
            if (!menu.clickMenuButton(player, CraftingTerminalMenu.RESET_OUTPUT_DESTINATION_BUTTON)
                    || !menu.clickMenuButton(player, CraftingTerminalMenu.RESET_PLAYER_INVENTORY_BUTTON)
                    || menu.getOutputDestination() != TerminalOutputDestination.PLAYER
                    || menu.isUsePlayerInventory()) {
                helper.fail("Middle-reset actions must restore Player output and Core-only ingredients");
                return;
            }
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            if (menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON)
                    || menu.clickMenuButton(player, CraftingTerminalMenu.RESET_OUTPUT_DESTINATION_BUTTON)
                    || menu.getOutputDestination() != TerminalOutputDestination.PLAYER) {
                helper.fail("Fuel page must reject forged output-destination changes");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void stonecutting_recipe_lookup(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.getMachineContainer().setItem(MachineEnergyTable.STONECUTTER_SLOT, ItemStack.EMPTY);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(5, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.STONE_BRICKS));
            if (menu.getCurrentRecipes().stream()
                    .anyMatch(holder -> holder.value().getType() == RecipeType.STONECUTTING)) {
                helper.fail("Stonecutting recipes must remain hidden until a Stonecutter is installed");
                return;
            }

            core.getMachineContainer().setItem(
                    MachineEnergyTable.STONECUTTER_SLOT, new ItemStack(Items.STONECUTTER));
            menu.lookUpRecipes(level, new ItemStack(Items.STONE_BRICKS));
            boolean foundStonecutting = false;
            for (int r = 0; r < menu.getRecipeCount(); r++) {
                if (menu.getCurrentRecipes().get(r).value().getType() == RecipeType.STONECUTTING)
                    foundStonecutting = true;
            }
            if (!foundStonecutting) helper.fail("Installed Stonecutter should unlock stonecutting recipes");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void crafting_recipes_require_installed_crafting_table_and_refresh_live(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.getMachineContainer().setItem(MachineEnergyTable.CRAFTING_TABLE_SLOT, ItemStack.EMPTY);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 2));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(132, player.getInventory(), core);

            menu.lookUpRecipes(level, new ItemStack(Items.STICK));
            if (menu.getRecipeCount() != 0
                    || menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                            1, CraftingDestination.INVENTORY, player)) {
                helper.fail("Crafting-table recipes must be hidden and rejected without the installed station");
                return;
            }

            core.getMachineContainer().setItem(
                    MachineEnergyTable.CRAFTING_TABLE_SLOT, new ItemStack(Items.CRAFTING_TABLE));
            menu.broadcastChanges();
            menu.lookUpRecipes(level, new ItemStack(Items.STICK));
            if (menu.getRecipeCount() < 1
                    || !menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                            1, CraftingDestination.INVENTORY, player)) {
                helper.fail("Installing a Crafting Table must unlock the vanilla stick recipe immediately");
                return;
            }
            if (countInInventory(player, Items.STICK) != 4) {
                helper.fail("Unlocked crafting recipe did not execute exactly once");
                return;
            }

            core.getMachineContainer().setItem(MachineEnergyTable.CRAFTING_TABLE_SLOT, ItemStack.EMPTY);
            menu.broadcastChanges();
            if (!menu.getCurrentRecipes().isEmpty()
                    || menu.handleRecipeRequest(level, ResourceLocation.withDefaultNamespace("stick"),
                            1, CraftingDestination.INVENTORY, player)) {
                helper.fail("Removing the Crafting Table must invalidate the open recipe immediately");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void smithing_transform_requires_table_and_preserves_base_components(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.getMachineContainer().setItem(MachineEnergyTable.SMITHING_TABLE_SLOT, ItemStack.EMPTY);
            ItemStack base = new ItemStack(Items.DIAMOND_SWORD);
            base.setDamageValue(37);
            base.set(DataComponents.CUSTOM_NAME, Component.literal("Kept Name"));
            core.insertItem(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            core.insertItem(base.copy());
            core.insertItem(new ItemStack(Items.NETHERITE_INGOT));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(133, player.getInventory(), core);

            menu.lookUpRecipes(level, new ItemStack(Items.NETHERITE_SWORD));
            if (menu.getCurrentRecipes().stream()
                    .anyMatch(holder -> holder.value().getType() == RecipeType.SMITHING)) {
                helper.fail("Smithing transforms must be hidden without an installed Smithing Table");
                return;
            }

            core.getMachineContainer().setItem(
                    MachineEnergyTable.SMITHING_TABLE_SLOT, new ItemStack(Items.SMITHING_TABLE));
            menu.lookUpRecipes(level, new ItemStack(Items.NETHERITE_SWORD));
            var smithing = menu.getCurrentRecipes().stream()
                    .filter(holder -> holder.value().getType() == RecipeType.SMITHING)
                    .findFirst().orElse(null);
            if (smithing == null || !menu.handleRecipeRequest(
                    level, smithing.id(), 1, CraftingDestination.INVENTORY, player)) {
                helper.fail("Installed Smithing Table should execute the exact netherite sword transform");
                return;
            }
            ItemStack output = ItemStack.EMPTY;
            for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                if (player.getInventory().getItem(slot).is(Items.NETHERITE_SWORD)) {
                    output = player.getInventory().getItem(slot);
                    break;
                }
            }
            if (output.isEmpty() || output.getDamageValue() != 37
                    || !Component.literal("Kept Name").equals(output.get(DataComponents.CUSTOM_NAME))) {
                helper.fail("Smithing output must preserve base damage and custom name, got " + output);
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE))) != 0
                    || core.getItemCount(ItemKey.of(base)) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.NETHERITE_INGOT))) != 0) {
                helper.fail("Smithing transform must consume template, exact base variant, and addition once");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void smithing_transform_matches_component_sensitive_base_exactly(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);

            ItemStack matchingBase = new ItemStack(Items.DIAMOND_SWORD);
            matchingBase.setDamageValue(23);
            matchingBase.set(DataComponents.CUSTOM_NAME, Component.literal("Required Components"));
            ItemStack rejectedBase = new ItemStack(Items.DIAMOND_SWORD);
            rejectedBase.setDamageValue(41);
            rejectedBase.set(DataComponents.CUSTOM_NAME, Component.literal("Wrong Components"));
            ItemKey matchingKey = ItemKey.of(matchingBase);
            ItemKey rejectedKey = ItemKey.of(rejectedBase);
            core.insertItem(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            core.insertItem(rejectedBase.copy());
            core.insertItem(matchingBase.copy());
            core.insertItem(new ItemStack(Items.NETHERITE_INGOT));

            var recipe = new SmithingTransformRecipe(
                    Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                    DataComponentIngredient.of(true, matchingBase),
                    Ingredient.of(Items.NETHERITE_INGOT),
                    new ItemStack(Items.NETHERITE_SWORD)
            );
            var holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "component_exact_smithing_test"),
                    recipe
            );
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(137, player.getInventory(), core);

            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                if (menu.computeCraftPreview(core, player).craftable() != 1) {
                    helper.fail("Component-sensitive Smithing base must preview from the exact stored variant");
                    return;
                }
                menu.craftItem(1, player);

                ItemStack output = ItemStack.EMPTY;
                for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                    if (player.getInventory().getItem(slot).is(Items.NETHERITE_SWORD)) {
                        output = player.getInventory().getItem(slot);
                        break;
                    }
                }
                if (output.isEmpty() || output.getDamageValue() != 23
                        || !Component.literal("Required Components").equals(
                                output.get(DataComponents.CUSTOM_NAME))) {
                    helper.fail("Smithing output must inherit the exact matching base components, got " + output);
                    return;
                }
                if (core.getItemCount(matchingKey) != 0 || core.getItemCount(rejectedKey) != 1) {
                    helper.fail("Smithing must consume only the exact matching base variant");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void axe_transformation_requires_stored_energy_and_spends_exact_units(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_LOG, 2));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(134, player.getInventory(), core);

            menu.lookUpRecipes(level, new ItemStack(Items.STRIPPED_OAK_LOG));
            if (!menu.getCurrentRecipes().isEmpty()) {
                helper.fail("Axe transformations must stay hidden until Axe Energy is stored");
                return;
            }

            ItemStack axe = new ItemStack(Items.IRON_AXE);
            axe.setDamageValue(axe.getMaxDamage() - 1);
            if (!core.addAxeEnergy(axe) || !axe.isEmpty()) {
                helper.fail("Test setup could not convert the one-use axe into Axe Energy");
                return;
            }
            menu.lookUpRecipes(level, new ItemStack(Items.STRIPPED_OAK_LOG));
            if (menu.getCurrentRecipes().isEmpty()) {
                helper.fail("Stored Axe Energy must expose the oak-log stripping transformation");
                return;
            }
            CraftingTerminalMenu.CraftPreview preview = menu.computeCraftPreview(core, player);
            RecipePresentation presentation = menu.getRecipePresentation();
            RecipePresentation.Resource axeResource = presentation.resources().stream()
                    .filter(row -> row.kind() == RecipePresentation.ResourceKind.TOOL)
                    .findFirst().orElse(null);
            if (preview.craftable() != 1 || axeResource == null
                    || axeResource.available() != 1 || axeResource.required() != 1) {
                helper.fail("Preview must expose one stored Axe Energy as one tool resource: "
                        + preview + " / " + presentation);
                return;
            }

            menu.craftItem(1, player);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_LOG))) != 1
                    || countInInventory(player, Items.STRIPPED_OAK_LOG) != 1
                    || core.getAxeEnergy() != 0 || core.hasInfiniteAxeEnergy()
                    || !core.getMachineContainer().getItem(MachineEnergyTable.AXE_SLOT).isEmpty()) {
                helper.fail("One strip must consume one log and exactly one finite Axe Energy");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void axe_catalog_supports_scraping_and_wax_removal(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
            if (!core.addAxeEnergy(axe)) {
                helper.fail("Test setup could not store Axe Energy");
                return;
            }
            long energyBefore = core.getAxeEnergy();
            core.insertItem(new ItemStack(Items.OXIDIZED_COPPER));
            core.insertItem(new ItemStack(Items.WAXED_COPPER_BLOCK));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(135, player.getInventory(), core);

            menu.lookUpRecipes(level, new ItemStack(Items.WEATHERED_COPPER));
            if (menu.getCurrentRecipes().isEmpty()) {
                helper.fail("Stored Axe Energy must expose oxidized-copper scraping");
                return;
            }
            menu.craftItem(1, player);
            menu.lookUpRecipes(level, new ItemStack(Items.COPPER_BLOCK));
            int waxOffRecipe = -1;
            for (int index = 0; index < menu.getCurrentRecipes().size(); index++) {
                while (menu.getCurrentRecipeIndex() != index) menu.nextRecipe();
                if (menu.getCurrentRecipes().get(index).value() instanceof AxeTransformationRecipe
                        && menu.computeCraftPreview(core, player).craftable() > 0) {
                    waxOffRecipe = index;
                    break;
                }
            }
            if (waxOffRecipe < 0) {
                helper.fail("Stored Axe Energy must expose wax removal");
                return;
            }
            while (menu.getCurrentRecipeIndex() != waxOffRecipe) menu.nextRecipe();
            menu.craftItem(1, player);

            int weathered = countInInventory(player, Items.WEATHERED_COPPER);
            int unwaxed = countInInventory(player, Items.COPPER_BLOCK);
            if (weathered != 1
                    || unwaxed != 1
                    || core.getAxeEnergy() != energyBefore - 2) {
                helper.fail("Scraping and wax removal must each spend one Axe Energy: weathered="
                        + weathered + " unwaxed=" + unwaxed + " energy=" + core.getAxeEnergy());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void blocked_axe_output_preserves_material_and_tool_atomically(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            ItemStack axe = new ItemStack(Items.IRON_AXE);
            axe.setDamageValue(17);
            if (!core.addAxeEnergy(axe)) {
                helper.fail("Test setup could not store finite Axe Energy");
                return;
            }
            long energyBefore = core.getAxeEnergy();
            core.insertItem(new ItemStack(Items.OAK_LOG, 2));
            Item[] fillerTypes = {
                    Items.STONE, Items.DIRT, Items.GRANITE, Items.DIORITE, Items.ANDESITE,
                    Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.SAND, Items.GRAVEL
            };
            for (Item item : fillerTypes) core.insertItem(new ItemStack(item));
            if (core.getTypeCount() != core.getTotalTypeSlots()) {
                helper.fail("Core setup must fill every type slot");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            var menu = new CraftingTerminalMenu(136, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.STRIPPED_OAK_LOG));
            if (menu.getCurrentRecipes().isEmpty()) {
                helper.fail("Axe strip transformation not found");
                return;
            }

            menu.craftItem(1, player);
            int dropped = level.getEntitiesOfClass(
                    ItemEntity.class,
                    player.getBoundingBox().inflate(16),
                    entity -> entity.getItem().is(Items.STRIPPED_OAK_LOG)
            ).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_LOG))) != 2
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STRIPPED_OAK_LOG))) != 0
                    || countInInventory(player, Items.STRIPPED_OAK_LOG) != 0
                    || core.getAxeEnergy() != energyBefore || dropped != 0) {
                helper.fail("No output capacity must preserve material and Axe Energy without drops");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void finite_and_infinite_axe_energy_bound_max_crafting(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            ItemStack finite = new ItemStack(Items.IRON_AXE);
            finite.setDamageValue(finite.getMaxDamage() - 3);
            if (!core.addAxeEnergy(finite)) {
                helper.fail("Could not store three finite Axe Energy");
                return;
            }
            core.insertItem(new ItemStack(Items.OAK_LOG, 10));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(137, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.STRIPPED_OAK_LOG));
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (countInInventory(player, Items.STRIPPED_OAK_LOG) != 3
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_LOG))) != 7
                    || core.getAxeEnergy() != 0) {
                helper.fail("Max must stop at the exact finite Axe Energy capacity");
                return;
            }

            ItemStack infinite = new ItemStack(Items.DIAMOND_AXE);
            infinite.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
            if (!core.addAxeEnergy(infinite) || !core.hasInfiniteAxeEnergy()) {
                helper.fail("Could not enable infinite Axe Energy");
                return;
            }
            core.insertItem(new ItemStack(Items.OAK_LOG, 64));
            menu.lookUpRecipes(level, new ItemStack(Items.STRIPPED_OAK_LOG));
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_LOG))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STRIPPED_OAK_LOG))) != 71
                    || !core.hasInfiniteAxeEnergy()) {
                helper.fail("Infinite Axe Energy Max must consume every remaining log without decrementing infinity");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void infinite_axe_max_handles_long_max_inventory_as_one_bulk_commit(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.insertItemCount(ItemKey.of(new ItemStack(Items.OAK_LOG)),
                    Long.MAX_VALUE, Action.EXECUTE, Actor.EMPTY);
            core.storageRecordForTesting().infiniteDescriptors().add(MachineEnergyTable.AXE_ID);
            core.rebuildNetwork(level);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(174, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.STRIPPED_OAK_LOG));
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            var deltas = new java.util.ArrayList<Long>();
            core.addListener((key, delta, amount, actor) -> {
                if (key.equals(ItemKey.of(new ItemStack(Items.OAK_LOG)))
                        || key.equals(ItemKey.of(new ItemStack(Items.STRIPPED_OAK_LOG)))) {
                    deltas.add(delta);
                }
            });

            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_LOG))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STRIPPED_OAK_LOG)))
                    != Long.MAX_VALUE || !core.hasInfiniteAxeEnergy()) {
                helper.fail("Long.MAX_VALUE Axe Max must fully commit without decrementing infinity");
                return;
            }
            if (!deltas.equals(java.util.List.of(-Long.MAX_VALUE, Long.MAX_VALUE))) {
                helper.fail("Long.MAX_VALUE Axe Max must emit one extraction and one insertion: " + deltas);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void campfire_recipe_lookup(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(6, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.COOKED_BEEF));
            if (menu.getRecipeCount() < 1) helper.fail("Cooked beef should have smelting recipe");
            boolean foundCampfire = false;
            for (int r = 0; r < menu.getRecipeCount(); r++) {
                if (menu.getCurrentRecipes().get(r).value().getType() == RecipeType.CAMPFIRE_COOKING)
                    foundCampfire = true;
            }
            if (!foundCampfire) helper.fail("Cooked beef should have campfire recipe");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void recipe_energy_cost_per_type(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(7, player.getInventory(), core);

            var smelting = new SmeltingRecipe("", CookingBookCategory.MISC,
                    Ingredient.of(Items.DIRT), new ItemStack(Items.STONE), 0, 37);
            var smeltCost = RecipeEnergyTable.getCost(smelting);
            if (smeltCost == null) helper.fail("Smelting should have cost");
            if (smeltCost.processType() != EnergyType.SMELTING_ENERGY) helper.fail("Smelting uses smelting_energy");
            if (smeltCost.fuelType() != EnergyType.FURNACE_FUEL) helper.fail("Smelting uses furnace_fuel");
            if (smeltCost.processAmount() != 37 || smeltCost.fuelAmount() != 37) {
                helper.fail("Smelting cost should use the concrete 37-tick recipe time");
            }

            var blasting = new net.minecraft.world.item.crafting.BlastingRecipe("", CookingBookCategory.MISC,
                    Ingredient.of(Items.DIRT), new ItemStack(Items.STONE), 0, 23);
            var blastCost = RecipeEnergyTable.getCost(blasting);
            if (blastCost == null) helper.fail("Blasting should have cost");
            if (blastCost.processType() != EnergyType.BLASTING_ENERGY) helper.fail("Blasting uses blasting_energy");
            if (blastCost.processAmount() != 23 || blastCost.fuelAmount() != 23) {
                helper.fail("Blasting cost should use the concrete 23-tick recipe time");
            }

            var crafting = new ShapelessRecipe("", CraftingBookCategory.MISC,
                    new ItemStack(Items.DIAMOND), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT)));
            if (RecipeEnergyTable.getCost(crafting) != null) helper.fail("Crafting should be free");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void cooking_recipe_preview_uses_concrete_recipe_time(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.DIRT));
            for (int i = 0; i < 37; i++) core.tick();
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);

            var holder = testSmeltingRecipe("preview_concrete_cooking_time", 37);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(58, player.getInventory(), core);
            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                int craftable = menu.computeCraftPreview(core, player).craftable();
                if (craftable != 1) {
                    helper.fail("A 37-tick recipe must preview with exactly 37 process/Fuel, got " + craftable);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void cooking_recipe_execution_consumes_concrete_recipe_time(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.DIRT));
            for (int i = 0; i < 200; i++) core.tick();
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);
            long processBefore = core.getEnergy(EnergyType.SMELTING_ENERGY);
            long fuelBefore = core.getEnergy(EnergyType.FURNACE_FUEL);

            var holder = testSmeltingRecipe("execute_concrete_cooking_time", 37);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(59, player.getInventory(), core);
            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                menu.craftItem(1, player);
                if (countInInventory(player, Items.DIAMOND) != 1) {
                    helper.fail("The registered 37-tick recipe should produce one diamond");
                    return;
                }
                if (core.getEnergy(EnergyType.SMELTING_ENERGY) != processBefore - 37) {
                    helper.fail("Execution must consume the concrete 37-tick process cost");
                    return;
                }
                if (core.getEnergy(EnergyType.FURNACE_FUEL) != fuelBefore - 37) {
                    helper.fail("Execution must consume the concrete 37-tick Fuel cost");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void non_positive_cooking_recipe_is_not_supported(GameTestHelper helper) {
        var holder = testSmeltingRecipe("zero_cooking_time_contract", 0);
        if (CraftingTerminalMenu.supportsRecipeContract(holder.value())) {
            helper.fail("A non-positive cooking-time recipe must fail the terminal contract");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void non_positive_cooking_recipe_cannot_preview_or_execute(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.DIRT));

            var holder = testSmeltingRecipe("zero_cooking_time_execute", 0);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(60, player.getInventory(), core);
            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                int craftable = menu.computeCraftPreview(core, player).craftable();
                menu.craftItem(1, player);
                if (craftable != 0) {
                    helper.fail("A non-positive cooking-time recipe must preview zero crafts");
                    return;
                }
                if (core.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 1
                        || countInInventory(player, Items.DIAMOND) != 0) {
                    helper.fail("A non-positive cooking-time recipe must consume and produce nothing");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void multi_ingredient_recipe_lookup(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(8, player.getInventory(), core);

            // Pickaxe needs stick + material (multiple ingredients)
            menu.lookUpRecipes(level, new ItemStack(Items.IRON_PICKAXE));
            if (menu.getRecipeCount() < 1) { helper.fail("Iron pickaxe should have crafting recipe"); return; }
            var recipe = menu.getCurrentRecipes().get(0).value();
            if (recipe.getType() != RecipeType.CRAFTING) helper.fail("Pickaxe is crafting type");
            var ingredients = recipe.getIngredients();
            if (ingredients.size() < 2) helper.fail("Pickaxe should have multiple ingredients");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void selection_tracks_item_identity_after_grid_reorder(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.APPLE, 1));
            core.insertItem(new ItemStack(Items.IRON_INGOT, 1));
            core.insertItem(new ItemStack(Items.STONE, 1));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(0, player.getInventory(), core);
            menu.refreshDisplayItems(core);
            // Full-identity grid sorts by name: Apple(0), Iron Ingot(1), Stone(2).
            int ironSlot = -1;
            for (int s = 0; s < StorageTerminalMenu.DISPLAY_SLOTS; s++) {
                if (menu.getSlot(s).getItem().is(Items.IRON_INGOT)) { ironSlot = s; break; }
            }
            if (ironSlot < 0) { helper.fail("Iron ingot not in grid"); return; }
            menu.clicked(ironSlot, 0, ClickType.PICKUP, player);
            if (!menu.getSelectedStack().is(Items.IRON_INGOT)) helper.fail("Selected should be iron ingot");
            // Reorder grid: remove Apple (sorts before iron), so iron's slot index shifts down.
            core.extractItem(ItemKey.of(new ItemStack(Items.APPLE)), 1);
            menu.refreshDisplayItems(core);
            ItemStack nowAtIronSlot = menu.getSlot(ironSlot).getItem();
            if (nowAtIronSlot.is(Items.IRON_INGOT))
                helper.fail("Grid did not reorder; test setup invalid");
            // Selection must still be iron ingot, recipes still iron ingot's.
            if (!menu.getSelectedStack().is(Items.IRON_INGOT))
                helper.fail("Selection should still be iron ingot after reorder, was " + menu.getSelectedStack());
            boolean foundSmelting = false;
            for (int r = 0; r < menu.getRecipeCount(); r++) {
                if (menu.getCurrentRecipes().get(r).value().getType() == RecipeType.SMELTING) foundSmelting = true;
            }
            if (!foundSmelting) helper.fail("Recipes should still resolve iron ingot (smelting) after reorder");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void selecting_item_without_supported_recipe_keeps_identity_and_empty_presentation(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            ItemStack unsupported = new ItemStack(Items.BARRIER);
            unsupported.set(DataComponents.CUSTOM_NAME, Component.literal("No Recipe Identity"));
            if (core.insertItem(unsupported.copy()) != 1) {
                helper.fail("Could not seed unsupported selected item");
                return;
            }

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(167, player.getInventory(), core);
            menu.refreshDisplayItems(core);
            int displaySlot = -1;
            for (int slot = 0; slot < StorageTerminalMenu.DISPLAY_SLOTS; slot++) {
                ItemStack displayed = TerminalDisplayStack.strip(menu.getSlot(slot).getItem());
                if (ItemStack.isSameItemSameComponents(displayed, unsupported)) {
                    displaySlot = slot;
                    break;
                }
            }
            if (displaySlot < 0) {
                helper.fail("Unsupported item identity not found in terminal display");
                return;
            }

            menu.clicked(displaySlot, 0, ClickType.PICKUP, player);
            ItemStack selected = menu.getSelectedStack();
            RecipePresentation presentation = menu.getRecipePresentation();
            if (!ItemStack.isSameItemSameComponents(selected, unsupported)
                    || selected.getCount() != 1
                    || menu.getRecipeCount() != 0
                    || !presentation.isEmpty()) {
                helper.fail("Unsupported item selection must retain exact identity with an empty presentation: selected="
                        + selected + " recipes=" + menu.getRecipeCount() + " presentation=" + presentation.kind());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void craft_preview_reports_craftable_and_missing(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(0, player.getInventory(), core);
            // Iron block = 9 iron ingots, crafting (no energy).
            menu.lookUpRecipes(level, new ItemStack(Items.IRON_BLOCK));
            if (menu.getRecipeCount() < 1) { helper.fail("Iron block should have crafting recipe"); return; }
            // No materials -> craftable 0, missing non-empty.
            var empty = menu.computeCraftPreview(core, player);
            if (empty.craftable() != 0) helper.fail("With no materials craftable should be 0, got " + empty.craftable());
            if (empty.missing().isEmpty()) helper.fail("With no materials missing should be non-empty");
            // Enough materials -> craftable >= 1, missing empty.
            core.insertItem(new ItemStack(Items.IRON_INGOT, 64));
            var ok = menu.computeCraftPreview(core, player);
            if (ok.craftable() < 1) helper.fail("With 64 ingots craftable should be >=1, got " + ok.craftable());
            if (!ok.missing().isEmpty()) helper.fail("With enough materials missing should be empty");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void craft_preview_saturates_core_and_player_counts_without_wrapping(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.insertItemCount(ItemKey.of(new ItemStack(Items.STONE)),
                    Long.MAX_VALUE, Action.EXECUTE, Actor.EMPTY);
            installAllRecipeStations(core);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().setItem(0, new ItemStack(Items.STONE));
            var menu = new CraftingTerminalMenu(44, player.getInventory(), core);
            menu.toggleUsePlayerInventory();
            menu.lookUpRecipes(level, new ItemStack(Items.STONE_BRICKS));
            if (menu.getRecipeCount() < 1) { helper.fail("Stone bricks recipe not found"); return; }
            var preview = menu.computeCraftPreview(core, player);
            if (preview.craftable() != 9999) {
                helper.fail("Preview should saturate at its 9999 cap, got " + preview.craftable());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void crafting_recipe_consumes_network_materials_and_outputs_to_player(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 5));

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(30, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.CRAFTING_TABLE));
            if (menu.getRecipeCount() < 1) { helper.fail("Crafting table should have a crafting recipe"); return; }
            if (menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex()).value().getType() != RecipeType.CRAFTING) {
                helper.fail("Crafting table recipe should be a crafting recipe");
                return;
            }

            menu.craftItem(1, player);
            long remainingPlanks = core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS)));
            if (remainingPlanks != 1) {
                helper.fail("Crafting one table should consume 4 network planks, left " + remainingPlanks);
                return;
            }
            int tables = countInInventory(player, Items.CRAFTING_TABLE);
            if (tables != 1) {
                helper.fail("Crafting one table should output 1 crafting table to player, got " + tables);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void smelting_recipe_consumes_network_ingredient_fuel_energy_and_outputs(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.COBBLESTONE, 2));
            for (int i = 0; i < 200; i++) core.tick();
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);
            long energyBefore = core.getEnergy(EnergyType.SMELTING_ENERGY);
            long fuelBefore = core.getEnergy(EnergyType.FURNACE_FUEL);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(31, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.STONE));
            if (menu.getRecipeCount() < 1) { helper.fail("Stone should have a smelting recipe"); return; }
            if (menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex()).value().getType() != RecipeType.SMELTING) {
                helper.fail("Stone recipe should be smelting");
                return;
            }

            menu.craftItem(1, player);
            long remainingCobble = core.getItemCount(ItemKey.of(new ItemStack(Items.COBBLESTONE)));
            if (remainingCobble != 1) {
                helper.fail("Smelting one stone should consume 1 cobblestone, left " + remainingCobble);
                return;
            }
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != energyBefore - 200) {
                helper.fail("Smelting should consume 200 smelting energy, before " + energyBefore + " after " + core.getEnergy(EnergyType.SMELTING_ENERGY));
                return;
            }
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != fuelBefore - 200) {
                helper.fail("Smelting should consume 200 furnace fuel, before " + fuelBefore + " after " + core.getEnergy(EnergyType.FURNACE_FUEL));
                return;
            }
            if (countInInventory(player, Items.STONE) != 1) {
                helper.fail("Smelting should output 1 stone to player");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void smelting_recipe_without_energy_or_fuel_leaves_state_unchanged(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.COBBLESTONE, 1));
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(32, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.STONE));
            if (menu.getRecipeCount() < 1) { helper.fail("Stone should have a smelting recipe"); return; }

            menu.craftItem(1, player);
            long fuelAfterNoEnergyAttempt = core.getEnergy(EnergyType.FURNACE_FUEL);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.COBBLESTONE))) != 1) {
                helper.fail("No-energy smelting must not consume cobblestone");
                return;
            }
            if (fuelAfterNoEnergyAttempt != 1600) {
                helper.fail("No-energy smelting must not consume fuel, got " + fuelAfterNoEnergyAttempt);
                return;
            }
            if (countInInventory(player, Items.STONE) != 0) {
                helper.fail("No-energy smelting must not output stone");
                return;
            }

            core.consumeEnergy(new EnergyCost(EnergyType.SMELTING_ENERGY, 0, EnergyType.FURNACE_FUEL, fuelAfterNoEnergyAttempt), 1);
            for (int i = 0; i < 200; i++) core.tick();
            long energyBeforeNoFuelAttempt = core.getEnergy(EnergyType.SMELTING_ENERGY);
            menu.craftItem(1, player);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.COBBLESTONE))) != 1) {
                helper.fail("No-fuel smelting must not consume cobblestone");
                return;
            }
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != energyBeforeNoFuelAttempt) {
                helper.fail("No-fuel smelting must not consume process energy");
                return;
            }
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 0) {
                helper.fail("No-fuel smelting should still have 0 fuel, got " + core.getEnergy(EnergyType.FURNACE_FUEL));
                return;
            }
            if (countInInventory(player, Items.STONE) != 0) {
                helper.fail("No-fuel smelting must not output stone");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void player_inventory_toggle_only_consumes_when_enabled(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 4));
            var menu = new CraftingTerminalMenu(33, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.CRAFTING_TABLE));
            if (menu.getRecipeCount() < 1) { helper.fail("Crafting table should have a crafting recipe"); return; }

            menu.craftItem(1, player);
            if (countInInventory(player, Items.OAK_PLANKS) != 4) {
                helper.fail("Disabled player-inventory crafting must not consume player planks");
                return;
            }
            if (countInInventory(player, Items.CRAFTING_TABLE) != 0) {
                helper.fail("Disabled player-inventory crafting must not output crafting tables");
                return;
            }

            menu.clickMenuButton(player, 7);
            if (!menu.isUsePlayerInventory()) {
                helper.fail("Button 7 should enable player inventory crafting");
                return;
            }
            menu.clickMenuButton(player, 2);
            if (countInInventory(player, Items.OAK_PLANKS) != 0) {
                helper.fail("Enabled player-inventory crafting should consume player planks");
                return;
            }
            if (countInInventory(player, Items.CRAFTING_TABLE) != 1) {
                helper.fail("Enabled player-inventory crafting should output 1 crafting table");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void selected_item_removed_from_network_clears_preview(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.IRON_INGOT, 1));

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(34, player.getInventory(), core);
            menu.refreshDisplayItems(core);
            int ironSlot = findDisplaySlot(menu, Items.IRON_INGOT);
            if (ironSlot < 0) { helper.fail("Iron ingot not in display grid"); return; }
            menu.clicked(ironSlot, 0, ClickType.PICKUP, player);
            if (!menu.getSelectedStack().is(Items.IRON_INGOT)) {
                helper.fail("Display click should select iron ingot");
                return;
            }
            if (menu.getRecipeCount() == 0 || menu.getMissingPreview().isEmpty()) {
                helper.fail("Selected iron ingot should have recipe metadata and missing preview before removal");
                return;
            }

            core.extractItem(ItemKey.of(new ItemStack(Items.IRON_INGOT)), 1);
            menu.refreshDisplayItems(core);
            if (!menu.getSelectedStack().isEmpty()) {
                helper.fail("Removed selected item should clear selected stack, got " + menu.getSelectedStack());
                return;
            }
            if (menu.getRecipeCount() != 0) {
                helper.fail("Removed selected item should clear recipe count, got " + menu.getRecipeCount());
                return;
            }
            if (menu.getCraftableCount() != 0) {
                helper.fail("Removed selected item should clear craftable count, got " + menu.getCraftableCount());
                return;
            }
            if (!menu.getMissingPreview().isEmpty()) {
                helper.fail("Removed selected item should clear missing preview");
                return;
            }
            if (!"No recipe".equals(menu.getCurrentRecipeTypeLabel())) {
                helper.fail("Removed selected item should clear recipe type label, got " + menu.getCurrentRecipeTypeLabel());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void display_click_syncs_selected_recipe_metadata_and_missing_preview(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.IRON_INGOT, 1));

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(35, player.getInventory(), core);
            menu.refreshDisplayItems(core);
            int ironSlot = findDisplaySlot(menu, Items.IRON_INGOT);
            if (ironSlot < 0) { helper.fail("Iron ingot not in display grid"); return; }

            menu.clicked(ironSlot, 0, ClickType.PICKUP, player);
            if (!menu.getSelectedStack().is(Items.IRON_INGOT)) {
                helper.fail("Display click should sync selected iron ingot");
                return;
            }
            if (menu.getRecipeCount() < 2) {
                helper.fail("Iron ingot display click should sync smelting/blasting recipe count, got " + menu.getRecipeCount());
                return;
            }
            String expectedLabel = labelFor(menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex()).value().getType());
            if (!expectedLabel.equals(menu.getCurrentRecipeTypeLabel())) {
                helper.fail("Iron ingot recipe type label should match current recipe type " + expectedLabel + ", got " + menu.getCurrentRecipeTypeLabel());
                return;
            }
            if (menu.getCraftableCount() != 0) {
                helper.fail("Iron ingot should not be craftable without ingredients/energy/fuel, got " + menu.getCraftableCount());
                return;
            }
            if (menu.getMissingPreview().isEmpty()) {
                helper.fail("Iron ingot should sync missing ingredient preview after display click");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void recipe_type_navigation_updates_synced_metadata(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(36, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.IRON_INGOT));
            if (menu.getRecipeCount() < 2) { helper.fail("Iron ingot should have multiple recipe types"); return; }
            int recipeCount = menu.getRecipeCount();
            int firstSmeltingIndex = -1;
            int firstBlastingIndex = -1;
            for (int r = 0; r < menu.getCurrentRecipes().size(); r++) {
                RecipeType<?> type = menu.getCurrentRecipes().get(r).value().getType();
                if (type == RecipeType.SMELTING && firstSmeltingIndex < 0) {
                    firstSmeltingIndex = r;
                }
                if (type == RecipeType.BLASTING && firstBlastingIndex < 0) {
                    firstBlastingIndex = r;
                }
            }
            if (firstSmeltingIndex < 0 || firstBlastingIndex <= firstSmeltingIndex) {
                helper.fail("Iron ingot should have smelting before blasting recipes, smelting=" + firstSmeltingIndex + " blasting=" + firstBlastingIndex);
                return;
            }

            for (int i = 0; i < firstSmeltingIndex; i++) menu.clickMenuButton(player, 9);
            if (menu.getCurrentRecipeIndex() != firstSmeltingIndex) {
                helper.fail("Next recipe button should sync current recipe index " + firstSmeltingIndex + ", got " + menu.getCurrentRecipeIndex());
                return;
            }
            if (!"Smelting".equals(menu.getCurrentRecipeTypeLabel())) {
                helper.fail("Recipe navigation should sync type label to Smelting, got " + menu.getCurrentRecipeTypeLabel());
                return;
            }

            for (int i = firstSmeltingIndex; i < firstBlastingIndex; i++) menu.clickMenuButton(player, 9);
            if (menu.getCurrentRecipeIndex() != firstBlastingIndex) {
                helper.fail("Next recipe button should sync current recipe index " + firstBlastingIndex + ", got " + menu.getCurrentRecipeIndex());
                return;
            }
            if (menu.getRecipeCount() != recipeCount) {
                helper.fail("Recipe navigation should keep recipe count synced, got " + menu.getRecipeCount());
                return;
            }
            if (!"Blasting".equals(menu.getCurrentRecipeTypeLabel())) {
                helper.fail("Recipe navigation should sync type label to Blasting, got " + menu.getCurrentRecipeTypeLabel());
                return;
            }

            menu.clickMenuButton(player, 8);
            if (menu.getCurrentRecipeIndex() != firstBlastingIndex - 1) {
                helper.fail("Prev recipe button should decrement current recipe index");
                return;
            }
            if (!"Smelting".equals(menu.getCurrentRecipeTypeLabel())) {
                helper.fail("Prev recipe button should sync type label back to Smelting, got " + menu.getCurrentRecipeTypeLabel());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void duplicate_ingredients_are_aggregated_before_crafting(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.IRON_INGOT, 8));

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(0, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.IRON_BLOCK));
            if (menu.getRecipeCount() < 1) { helper.fail("Iron block should have a crafting recipe"); return; }

            var preview = menu.computeCraftPreview(core, player);
            if (preview.craftable() != 0) {
                helper.fail("Eight ingots must not preview as craftable iron blocks, got " + preview.craftable());
                return;
            }
            if (preview.ingredients().size() != 1) {
                helper.fail("Nine equal iron-ingot predicates should render as one requirement row, got "
                        + preview.ingredients().size());
                return;
            }
            if (!preview.energies().isEmpty()) {
                helper.fail("Crafting recipes must not expose fake energy rows");
                return;
            }
            var iron = preview.ingredients().getFirst();
            if (!iron.stack().is(Items.IRON_INGOT) || iron.available() != 8 || iron.required() != 9) {
                helper.fail("Iron block resource row should show 8 available / 9 required, got " + iron);
                return;
            }

            menu.craftItem(1, player);
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.IRON_INGOT))) != 8) {
                helper.fail("Failed craft must leave the eight ingots in storage");
                return;
            }
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(Items.IRON_BLOCK)) {
                    helper.fail("Failed craft must not create an iron block");
                    return;
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void conflicted_network_crafting_preview_and_execute_are_atomic(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitPos = corePos.east();
        var secondCorePos = unitPos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            if (core.insertItem(new ItemStack(Items.OAK_PLANKS, 4)) != 4) {
                helper.fail("Test setup could not insert crafting materials");
                return;
            }

            level.setBlock(secondCorePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            if (!core.isConflicted()) { helper.fail("Test setup did not create a conflicted network"); return; }
            if (core.countMatching(Ingredient.of(Items.OAK_PLANKS)) != 4) {
                helper.fail("Test setup must reproduce countMatching visibility on a conflicted core");
                return;
            }

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(40, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.CRAFTING_TABLE));
            if (menu.getRecipeCount() < 1) { helper.fail("Crafting table recipe not found"); return; }

            var preview = menu.computeCraftPreview(core, player);
            menu.craftItem(1, player);
            if (preview.craftable() != 0) {
                helper.fail("Conflicted storage must preview zero craftable items, got " + preview.craftable());
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 4) {
                helper.fail("Conflicted craft must consume no network materials");
                return;
            }
            if (countInInventory(player, Items.CRAFTING_TABLE) != 0) {
                helper.fail("Conflicted craft must produce no output");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void special_recipes_without_static_ingredients_are_not_selectable(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(41, player.getInventory(), core);

            var unsafeFireworks = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(holder -> holder.value().getResultItem(level.registryAccess()).is(Items.FIREWORK_ROCKET))
                    .filter(holder -> !hasCompleteGenericIngredients(holder.value()))
                    .toList();
            if (unsafeFireworks.isEmpty()) { helper.fail("Unsafe firework recipe not found"); return; }
            menu.lookUpRecipes(level, new ItemStack(Items.FIREWORK_ROCKET));
            if (menu.getCurrentRecipes().stream().anyMatch(unsafeFireworks::contains)) {
                helper.fail("Special firework recipes without generic ingredients must not be selectable");
                return;
            }

            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void special_recipes_without_static_ingredients_cannot_preview_or_execute(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(42, player.getInventory(), core);

            var fireworkRecipe = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(holder -> holder.value().getResultItem(level.registryAccess()).is(Items.FIREWORK_ROCKET))
                    .filter(holder -> !hasCompleteGenericIngredients(holder.value()))
                    .findFirst().orElse(null);
            if (fireworkRecipe == null) { helper.fail("Firework recipe not found"); return; }
            menu.getCurrentRecipes().add(fireworkRecipe);
            if (menu.computeCraftPreview(core, player).craftable() != 0) {
                helper.fail("Unsafe firework recipe must preview zero craftable items");
                return;
            }
            menu.craftItem(1, player);
            if (countInInventory(player, Items.FIREWORK_ROCKET) != 0) {
                helper.fail("Unsafe firework recipe must not execute as free output");
                return;
            }

            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void overlapping_ingredient_predicates_reserve_distinct_items_atomically(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS));

            var ingredients = NonNullList.of(
                    Ingredient.EMPTY,
                    Ingredient.of(ItemTags.PLANKS),
                    Ingredient.of(Items.OAK_PLANKS)
            );
            var recipe = new ShapelessRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    new ItemStack(Items.DIRT),
                    ingredients
            );
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(43, player.getInventory(), core);
            var holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "overlapping_ingredients_test"),
                    recipe
            );
            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                var previewResult = menu.computeCraftPreview(core, player);
                int preview = previewResult.craftable();
                if (previewResult.ingredients().size() != 2) {
                    helper.fail("Overlapping but distinct predicates must remain two resource rows, got "
                            + previewResult.ingredients().size());
                    return;
                }
                for (var ingredient : previewResult.ingredients()) {
                    if (ingredient.available() != 1 || ingredient.required() != 1) {
                        helper.fail("Each overlapping row should independently show 1 available / 1 required, got "
                                + ingredient);
                        return;
                    }
                }
                menu.craftItem(1, player);
                if (preview != 0) {
                    helper.fail("One oak plank cannot satisfy both a planks-tag ingredient and an oak-only ingredient, preview=" + preview);
                    return;
                }
                if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 1) {
                    helper.fail("Failed overlapping-ingredient craft must leave the reserved plank untouched");
                    return;
                }
                if (countInInventory(player, Items.DIRT) != 0) {
                    helper.fail("Failed overlapping-ingredient craft must not produce output");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void overlapping_ingredient_reservation_reroutes_across_storage_and_player_inventory(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS));

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().setItem(0, new ItemStack(Items.BIRCH_PLANKS));
            var menu = new CraftingTerminalMenu(46, player.getInventory(), core);
            menu.toggleUsePlayerInventory();
            var recipe = new ShapelessRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    new ItemStack(Items.DIRT),
                    NonNullList.of(
                            Ingredient.EMPTY,
                            Ingredient.of(ItemTags.PLANKS),
                            Ingredient.of(Items.OAK_PLANKS)
                    )
            );
            var holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "overlapping_reroute_test"),
                    recipe
            );
            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                if (menu.computeCraftPreview(core, player).craftable() != 1) {
                    helper.fail("Oak must be reserved for the specific ingredient while birch satisfies the broad tag");
                    return;
                }
                menu.craftItem(1, player);
                if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0) {
                    helper.fail("Successful rerouted craft should consume the stored oak plank");
                    return;
                }
                if (countInInventory(player, Items.BIRCH_PLANKS) != 0 || countInInventory(player, Items.DIRT) != 1) {
                    helper.fail("Successful rerouted craft should consume birch and produce one dirt");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void partial_player_inventory_capacity_returns_crafting_output_remainder_to_core(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 2));

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            for (int i = 0; i < 36; i++) {
                player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
            }
            player.getInventory().setItem(0, new ItemStack(Items.STICK, 62));
            int sticksBefore = countInInventory(player, Items.STICK);

            var menu = new CraftingTerminalMenu(44, player.getInventory(), core);
            if (menu.getOutputDestination() != TerminalOutputDestination.PLAYER) {
                helper.fail("Player must be the default direct-craft output destination");
                return;
            }
            var recipe = new ShapelessRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    new ItemStack(Items.STICK, 4),
                    NonNullList.of(
                            Ingredient.EMPTY,
                            Ingredient.of(Items.OAK_PLANKS),
                            Ingredient.of(Items.OAK_PLANKS)
                    )
            );
            var holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "partial_output_test"),
                    recipe
            );
            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                menu.craftItem(1, player);

                int inventoryGain = countInInventory(player, Items.STICK) - sticksBefore;
                long stored = core.getItemCount(ItemKey.of(new ItemStack(Items.STICK)));
                int dropped = level.getEntitiesOfClass(
                        ItemEntity.class,
                        player.getBoundingBox().inflate(16),
                        entity -> entity.getItem().is(Items.STICK)
                ).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
                if (inventoryGain != 2 || stored != 2 || dropped != 0) {
                    helper.fail("Crafting output must fill the inventory then return the remainder to Core, inventory="
                            + inventoryGain + " stored=" + stored + " dropped=" + dropped);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void crafting_returns_item_level_container_remainders(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.MILK_BUCKET));

            var recipe = new ShapelessRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    new ItemStack(Items.DIAMOND),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.MILK_BUCKET))
            );
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(45, player.getInventory(), core);
            var holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "container_remainder_test"),
                    recipe
            );
            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                menu.craftItem(1, player);
                if (countInInventory(player, Items.DIAMOND) != 1) {
                    helper.fail("Container-remainder recipe should produce its output");
                    return;
                }
                if (countInInventory(player, Items.BUCKET) != 1) {
                    helper.fail("Consuming one milk bucket must return one empty bucket");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void vanilla_cake_recipe_returns_all_three_milk_buckets(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.MILK_BUCKET, 3));
            core.insertItem(new ItemStack(Items.SUGAR, 2));
            core.insertItem(new ItemStack(Items.EGG));
            core.insertItem(new ItemStack(Items.WHEAT, 3));

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(184, player.getInventory(), core);
            ResourceLocation cakeRecipe = ResourceLocation.withDefaultNamespace("cake");
            if (!menu.handleRecipeRequest(
                    level, cakeRecipe, 1, CraftingDestination.INVENTORY, player)) {
                helper.fail("Vanilla Cake recipe was not selected and crafted");
                return;
            }
            if (countInInventory(player, Items.CAKE) != 1
                    || countInInventory(player, Items.BUCKET) != 3
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.MILK_BUCKET))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.SUGAR))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.EGG))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.WHEAT))) != 0) {
                helper.fail("Vanilla Cake must consume its exact ingredients and return three buckets: "
                        + "cake=" + countInInventory(player, Items.CAKE)
                        + ", buckets=" + countInInventory(player, Items.BUCKET));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void fuel_conversion_refreshes_open_crafting_preview(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.COBBLESTONE));
            core.insertItem(new ItemStack(Items.STONE));
            for (int i = 0; i < 200; i++) core.tick();

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().setItem(0, new ItemStack(Items.COAL));
            var menu = new CraftingTerminalMenu(47, player.getInventory(), core);
            int stoneSlot = findDisplaySlot(menu, Items.STONE);
            if (stoneSlot < 0) { helper.fail("Stone output not found in terminal grid"); return; }
            menu.clicked(stoneSlot, 0, ClickType.PICKUP, player);
            if (menu.getCraftableCount() != 0) {
                helper.fail("Stone should not be craftable before furnace fuel is added");
                return;
            }

            int coalSlot = -1;
            for (int i = StorageTerminalMenu.DISPLAY_SLOTS;
                 i < StorageTerminalMenu.DISPLAY_SLOTS + StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; i++) {
                if (menu.getSlot(i).getItem().is(Items.COAL)) { coalSlot = i; break; }
            }
            if (coalSlot < 0) { helper.fail("Coal player slot not found"); return; }
            menu.clickMenuButton(player, CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON);
            menu.clickMenuButton(player,
                    CraftingTerminalMenu.fuelTargetButtonId(EnergyType.FURNACE_FUEL));
            menu.quickMoveStack(player, coalSlot);
            menu.clickMenuButton(player, CraftingTerminalMenu.transformUseButtonId(0));
            menu.clickMenuButton(player, CraftingTerminalMenu.MAX_CRAFT_BUTTON);
            menu.clickMenuButton(player, CraftingTerminalMenu.STORAGE_PAGE_BUTTON);
            if (menu.getCraftableCount() != 1) {
                helper.fail("Fuel conversion should refresh preview to one craftable stone, got "
                        + menu.getCraftableCount());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void dynamic_recipe_subclasses_fail_closed(GameTestHelper helper) {
        var recipe = new ShapelessRecipe(
                "",
                CraftingBookCategory.MISC,
                new ItemStack(Items.DIRT),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE))
        ) {
            @Override
            public ItemStack assemble(net.minecraft.world.item.crafting.CraftingInput input,
                                      net.minecraft.core.HolderLookup.Provider registries) {
                return new ItemStack(Items.DIAMOND);
            }
        };
        if (CraftingTerminalMenu.supportsRecipeContract(recipe)) {
            helper.fail("Recipe subclasses with dynamic assemble behavior must fail closed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void distinct_component_ingredient_predicates_are_not_merged(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            ItemStack required = new ItemStack(Items.STONE);
            required.set(DataComponents.CUSTOM_NAME, Component.literal("required"));
            Ingredient partial = DataComponentIngredient.of(false, required);
            Ingredient strict = DataComponentIngredient.of(true, required);
            ItemStack extra = required.copyWithCount(2);
            extra.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            ItemKey extraKey = ItemKey.of(extra);
            core.insertItem(extra.copy());

            var recipe = new ShapelessRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    new ItemStack(Items.DIRT),
                    NonNullList.of(Ingredient.EMPTY, partial, strict)
            );
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(48, player.getInventory(), core);
            var holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "component_predicate_test"),
                    recipe
            );
            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                int preview = menu.computeCraftPreview(core, player).craftable();
                menu.craftItem(1, player);
                if (preview != 0) {
                    helper.fail("Partial and strict component predicates must remain distinct, preview=" + preview);
                    return;
                }
                if (core.getItemCount(extraKey) != 2) {
                    helper.fail("Rejected component-predicate craft must preserve both source items");
                    return;
                }
                if (countInInventory(player, Items.DIRT) != 0) {
                    helper.fail("Rejected component-predicate craft must not produce output");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void player_inventory_crafting_excludes_offhand_and_armor_slots(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var stickRecipe = level.getRecipeManager().byKey(
                    ResourceLocation.fromNamespaceAndPath("minecraft", "stick")
            ).orElse(null);
            if (stickRecipe == null) { helper.fail("Vanilla stick recipe not found"); return; }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().setItem(40, new ItemStack(Items.OAK_PLANKS, 2));
            var menu = new CraftingTerminalMenu(49, player.getInventory(), core);
            menu.toggleUsePlayerInventory();
            menu.getCurrentRecipes().add(stickRecipe);

            int preview = menu.computeCraftPreview(core, player).craftable();
            menu.craftItem(1, player);
            if (preview != 0) {
                helper.fail("Offhand ingredients must not count as visible player inventory, preview=" + preview);
                return;
            }
            if (!player.getInventory().getItem(40).is(Items.OAK_PLANKS)
                    || player.getInventory().getItem(40).getCount() != 2) {
                helper.fail("Crafting must not consume the offhand slot");
                return;
            }
            if (countInInventory(player, Items.STICK) != 0) {
                helper.fail("Offhand-only ingredients must not produce output");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void installed_machine_energy_refreshes_open_crafting_preview(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            installMachine(core, helper, 0, new ItemStack(Items.FURNACE));
            core.insertItem(new ItemStack(Items.COBBLESTONE));
            core.insertItem(new ItemStack(Items.STONE));
            for (int i = 0; i < 199; i++) core.tick();
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != 199) {
                helper.fail("One installed Furnace must generate exactly 199 energy in 199 manual ticks");
                return;
            }
            core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL);

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(50, player.getInventory(), core);
            menu.refreshDisplayItems(core);
            int stoneSlot = findDisplaySlot(menu, Items.STONE);
            if (stoneSlot < 0) { helper.fail("Stone output not found"); return; }
            var smelting = level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).stream()
                    .filter(holder -> holder.value().getResultItem(level.registryAccess()).is(Items.STONE))
                    .findFirst().orElse(null);
            if (smelting == null || !menu.selectRecipe(level, stoneSlot, smelting.id(), player)) {
                helper.fail("Stone smelting recipe could not be selected");
                return;
            }
            if (menu.getCraftableCount() != 0) {
                helper.fail("Stone must start one process-energy point short");
                return;
            }
            var initialEnergyRows = menu.getEnergyPreview();
            if (initialEnergyRows.size() != 2
                    || initialEnergyRows.get(0).type() != EnergyType.SMELTING_ENERGY
                    || initialEnergyRows.get(0).available() != 199
                    || initialEnergyRows.get(0).required() != 200
                    || initialEnergyRows.get(1).type() != EnergyType.FURNACE_FUEL
                    || initialEnergyRows.get(1).available() != 1600
                    || initialEnergyRows.get(1).required() != 200) {
                helper.fail("Cooking preview must sync process and Fuel available/required-for-one rows, got "
                        + initialEnergyRows);
                return;
            }

            core.tick();
            menu.broadcastChanges();
            if (menu.getCraftableCount() != 1) {
                helper.fail("Installed-machine energy should refresh preview to one craftable stone, got "
                        + menu.getCraftableCount());
                return;
            }
            if (menu.getEnergyPreview().getFirst().available() != 200) {
                helper.fail("Open process-energy resource row must refresh from 199 to 200");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void player_inventory_changes_refresh_open_crafting_preview(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.CRAFTING_TABLE));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(53, player.getInventory(), core);
            int outputSlot = findDisplaySlot(menu, Items.CRAFTING_TABLE);
            if (outputSlot < 0) {
                helper.fail("Crafting table output not found");
                return;
            }
            menu.clicked(outputSlot, 0, ClickType.PICKUP, player);
            menu.clickMenuButton(player, 7);
            if (menu.getCraftableCount() != 0) {
                helper.fail("Preview must start at zero without player planks");
                return;
            }
            var initialIngredients = menu.getIngredientPreview();
            if (initialIngredients.size() != 1
                    || initialIngredients.getFirst().available() != 0
                    || initialIngredients.getFirst().required() != 4) {
                helper.fail("Open crafting-table preview should show 0 available / 4 required, got "
                        + initialIngredients);
                return;
            }

            player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 4));
            menu.broadcastChanges();
            if (menu.getCraftableCount() != 1) {
                helper.fail("Adding four player planks must refresh the open preview to one craft");
                return;
            }
            if (menu.getIngredientPreview().getFirst().available() != 4) {
                helper.fail("Player inventory resource row must refresh to 4 available");
                return;
            }

            for (int slot = 0; slot < 36; slot++) {
                if (player.getInventory().getItem(slot).is(Items.OAK_PLANKS)) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
            menu.broadcastChanges();
            if (menu.getCraftableCount() != 0) {
                helper.fail("Removing player planks must refresh the open preview back to zero");
                return;
            }
            if (menu.getIngredientPreview().getFirst().available() != 0) {
                helper.fail("Player inventory resource row must refresh back to 0 available");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void topology_conflict_and_recovery_refresh_open_crafting_preview(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitPos = corePos.east();
        var secondCorePos = unitPos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.CRAFTING_TABLE));
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 4));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(54, player.getInventory(), core);
            int outputSlot = findDisplaySlot(menu, Items.CRAFTING_TABLE);
            if (outputSlot < 0) {
                helper.fail("Crafting table output not found");
                return;
            }
            menu.clicked(outputSlot, 0, ClickType.PICKUP, player);
            if (menu.getCraftableCount() != 1) {
                helper.fail("Preview must start craftable before conflict");
                return;
            }

            level.setBlock(secondCorePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            menu.broadcastChanges();
            if (menu.getCraftableCount() != 0) {
                helper.fail("Topology conflict must refresh the open preview to zero");
                return;
            }

            level.removeBlock(secondCorePos, false);
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            menu.broadcastChanges();
            if (menu.getCraftableCount() != 1) {
                helper.fail("Resolving topology conflict must restore the open preview");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void crafting_listener_reentrancy_cannot_interrupt_atomic_commit(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitPos = corePos.east();
        var secondCorePos = unitPos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            ItemKey plankKey = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
            ItemKey cobbleKey = ItemKey.of(new ItemStack(Items.COBBLESTONE));
            core.insertItem(new ItemStack(Items.OAK_PLANKS));
            core.insertItem(new ItemStack(Items.COBBLESTONE));

            var recipe = new ShapelessRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    new ItemStack(Items.DIRT),
                    NonNullList.of(
                            Ingredient.EMPTY,
                            Ingredient.of(Items.OAK_PLANKS),
                            Ingredient.of(Items.COBBLESTONE)
                    )
            );
            var holder = new RecipeHolder<>(
                    ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "listener_reentrancy_test"),
                    recipe
            );
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(55, player.getInventory(), core);
            var fired = new java.util.concurrent.atomic.AtomicBoolean();
            core.addListener((key, delta, amount, actor) -> {
                if (delta < 0 && fired.compareAndSet(false, true)) {
                    level.setBlock(secondCorePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
                    core.rebuildNetwork(level);
            installAllRecipeStations(core);
                }
            });

            withTemporaryRegisteredRecipe(level, holder, () -> {
                menu.getCurrentRecipes().add(holder);
                try {
                    menu.craftItem(1, player);
                } catch (RuntimeException e) {
                    helper.fail("Listener reentrancy must not escape the crafting transaction: " + e);
                    return;
                }

                long planks = core.getItemCount(plankKey);
                long cobble = core.getItemCount(cobbleKey);
                int dirt = countInInventory(player, Items.DIRT);
                boolean committed = planks == 0 && cobble == 0 && dirt == 1;
                boolean rolledBack = planks == 1 && cobble == 1 && dirt == 0;
                if (!committed && !rolledBack) {
                    helper.fail("Reentrant craft must fully commit or fully roll back: planks=" + planks
                            + " cobble=" + cobble + " dirt=" + dirt);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void craft_preview_snapshots_storage_sources_once(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity actualCore)) {
                helper.fail("Core not found");
                return;
            }
            actualCore.getMachineContainer().setItem(
                    MachineEnergyTable.CRAFTING_TABLE_SLOT, new ItemStack(Items.CRAFTING_TABLE));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(56, player.getInventory(), actualCore);
            menu.lookUpRecipes(level, new ItemStack(Items.CRAFTING_TABLE));
            if (menu.getRecipeCount() < 1) {
                helper.fail("Crafting table recipe not found");
                return;
            }

            var countingCore = new CountingPreviewCore(corePos,
                    AutoStorage.STORAGE_CORE.get().defaultBlockState());
            countingCore.setLevel(level);
            countingCore.getMachineContainer().setItem(
                    MachineEnergyTable.CRAFTING_TABLE_SLOT, new ItemStack(Items.CRAFTING_TABLE));
            var preview = menu.computeCraftPreview(countingCore, player);
            if (preview.craftable() != 16) {
                helper.fail("64 planks should preview exactly 16 crafting tables, got " + preview.craftable());
                return;
            }
            if (countingCore.displaySnapshots != 1) {
                helper.fail("Preview must snapshot storage sources once, got " + countingCore.displaySnapshots);
                return;
            }
            if (preview.ingredients().size() != 1
                    || preview.ingredients().getFirst().available() != 64
                    || preview.ingredients().getFirst().required() != 4) {
                helper.fail("Snapshot preview must expose 64 available / 4 required, got "
                        + preview.ingredients());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void removed_recipe_cannot_execute_from_open_terminal_cache(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            ItemKey planksKey = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 2));

            var manager = level.getRecipeManager();
            var stickRecipe = manager.byKey(ResourceLocation.withDefaultNamespace("stick")).orElse(null);
            if (stickRecipe == null) { helper.fail("Vanilla stick recipe not found"); return; }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(51, player.getInventory(), core);
            menu.getCurrentRecipes().add(stickRecipe);

            var originalRecipes = java.util.List.copyOf(manager.getRecipes());
            int preview;
            try {
                manager.replaceRecipes(originalRecipes.stream()
                        .filter(holder -> !holder.id().equals(stickRecipe.id()))
                        .toList());
                preview = menu.computeCraftPreview(core, player).craftable();
                menu.craftItem(1, player);
            } finally {
                manager.replaceRecipes(originalRecipes);
            }

            if (preview != 0) {
                helper.fail("A recipe removed by datapack reload must preview zero craftable items");
                return;
            }
            if (core.getItemCount(planksKey) != 2) {
                helper.fail("A recipe removed by datapack reload must not consume cached ingredients");
                return;
            }
            if (countInInventory(player, Items.STICK) != 0) {
                helper.fail("A recipe removed by datapack reload must not produce cached output");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void shaped_recipe_presentation_preserves_positions_identity_output_and_ledger(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.OAK_PLANKS, 10));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(160, player.getInventory(), core);
            ResourceLocation recipeId = ResourceLocation.withDefaultNamespace("stick");
            if (!menu.handleRecipeRequest(
                    level, recipeId, 1, CraftingDestination.NONE, player)) {
                helper.fail("Could not select vanilla stick recipe");
                return;
            }

            RecipePresentation presentation = menu.getRecipePresentation();
            if (!presentation.recipeId().equals(recipeId)
                    || presentation.kind() != RecipePresentationKind.CRAFTING
                    || presentation.width() != 1
                    || presentation.height() != 2
                    || presentation.shapeless()) {
                helper.fail("Shaped metadata mismatch: " + presentation);
                return;
            }
            if (presentation.inputs().size() != 9
                    || !presentation.inputs().get(0).is(Items.OAK_PLANKS)
                    || !presentation.inputs().get(1).is(Items.OAK_PLANKS)
                    || presentation.inputs().subList(2, 9).stream().anyMatch(stack -> !stack.isEmpty())) {
                helper.fail("Stick pattern must retain its exact 1x2 positions: " + presentation.inputs());
                return;
            }
            if (!presentation.output().is(Items.STICK)
                    || presentation.output().getCount() != 4
                    || !presentation.station().is(Items.CRAFTING_TABLE)) {
                helper.fail("Exact output/station mismatch: " + presentation);
                return;
            }
            RecipePresentation.Resource resource = presentation.resources().stream()
                    .filter(row -> row.kind() == RecipePresentation.ResourceKind.ITEM)
                    .filter(row -> row.stack().is(Items.OAK_PLANKS))
                    .findFirst().orElse(null);
            if (resource == null || resource.available() != 10 || resource.required() != 2) {
                helper.fail("Shaped ledger must aggregate 10 available / 2 required planks: "
                        + presentation.resources());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void shapeless_recipe_presentation_marks_layout_and_exact_output_count(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.DIRT, 5));
            core.insertItem(new ItemStack(Items.STONE, 7));
            var recipeId = ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "presentation_shapeless");
            var holder = new RecipeHolder<>(recipeId, new ShapelessRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    new ItemStack(Items.EMERALD, 2),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT), Ingredient.of(Items.STONE))));

            withTemporaryRegisteredRecipe(level, holder, () -> {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(161, player.getInventory(), core);
                if (!menu.handleRecipeRequest(
                        level, recipeId, 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not select test shapeless recipe");
                    return;
                }
                RecipePresentation presentation = menu.getRecipePresentation();
                if (presentation.kind() != RecipePresentationKind.CRAFTING
                        || !presentation.shapeless()
                        || presentation.width() != 3
                        || presentation.height() != 3
                        || presentation.inputs().size() != 9
                        || !presentation.inputs().get(0).is(Items.DIRT)
                        || !presentation.inputs().get(1).is(Items.STONE)
                        || !presentation.output().is(Items.EMERALD)
                        || presentation.output().getCount() != 2) {
                    helper.fail("Shapeless presentation mismatch: " + presentation);
                    return;
                }
                long itemRows = presentation.resources().stream()
                        .filter(row -> row.kind() == RecipePresentation.ResourceKind.ITEM)
                        .count();
                if (itemRows != 2) {
                    helper.fail("Shapeless ledger must retain two item resources: "
                            + presentation.resources());
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void cooking_and_stonecutting_present_real_roles_station_and_costs(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.DIRT, 9));
            core.insertItem(new ItemStack(Items.STONE, 4));
            var cookingId = ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "presentation_cooking");
            var cooking = new RecipeHolder<>(cookingId, new SmeltingRecipe(
                    "", CookingBookCategory.MISC, Ingredient.of(Items.DIRT),
                    new ItemStack(Items.DIAMOND, 3), 0, 37));
            var stonecuttingId = ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "presentation_stonecutting");
            var stonecutting = new RecipeHolder<>(stonecuttingId, new StonecutterRecipe(
                    "", Ingredient.of(Items.STONE), new ItemStack(Items.STONE_BRICKS, 2)));

            var manager = level.getRecipeManager();
            var original = java.util.List.copyOf(manager.getRecipes());
            var registered = new java.util.ArrayList<>(original);
            registered.add(cooking);
            registered.add(stonecutting);
            manager.replaceRecipes(registered);
            try {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(162, player.getInventory(), core);
                if (!menu.handleRecipeRequest(
                        level, cookingId, 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not select test cooking recipe");
                    return;
                }
                RecipePresentation cookingPresentation = menu.getRecipePresentation();
                long energyRows = cookingPresentation.resources().stream()
                        .filter(row -> row.kind() == RecipePresentation.ResourceKind.ENERGY)
                        .filter(row -> row.required() == 37)
                        .count();
                if (cookingPresentation.kind() != RecipePresentationKind.COOKING
                        || cookingPresentation.inputs().stream().noneMatch(stack -> stack.is(Items.DIRT))
                        || !cookingPresentation.output().is(Items.DIAMOND)
                        || cookingPresentation.output().getCount() != 3
                        || !cookingPresentation.station().is(Items.FURNACE)
                        || energyRows != 2) {
                    helper.fail("Cooking presentation mismatch: " + cookingPresentation);
                    return;
                }

                if (!menu.handleRecipeRequest(
                        level, stonecuttingId, 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not select test stonecutting recipe");
                    return;
                }
                RecipePresentation stonecuttingPresentation = menu.getRecipePresentation();
                if (stonecuttingPresentation.kind() != RecipePresentationKind.STONECUTTING
                        || stonecuttingPresentation.inputs().stream()
                        .filter(stack -> !stack.isEmpty()).count() != 1
                        || stonecuttingPresentation.inputs().stream().noneMatch(stack -> stack.is(Items.STONE))
                        || !stonecuttingPresentation.output().is(Items.STONE_BRICKS)
                        || stonecuttingPresentation.output().getCount() != 2
                        || !stonecuttingPresentation.station().is(Items.STONECUTTER)) {
                    helper.fail("Stonecutting presentation mismatch: " + stonecuttingPresentation);
                    return;
                }
                helper.succeed();
            } finally {
                manager.replaceRecipes(original);
            }
        });
    }

    @GameTest(template = "platform")
    public static void smithing_and_axe_present_role_order_and_distinct_tool_resource(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            core.insertItem(new ItemStack(Items.DIAMOND_SWORD));
            core.insertItem(new ItemStack(Items.NETHERITE_INGOT));
            core.insertItem(new ItemStack(Items.OAK_LOG, 2));
            var smithingId = ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "presentation_smithing");
            var smithing = new RecipeHolder<>(smithingId, new SmithingTransformRecipe(
                    Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                    Ingredient.of(Items.DIAMOND_SWORD),
                    Ingredient.of(Items.NETHERITE_INGOT),
                    new ItemStack(Items.NETHERITE_SWORD)));

            withTemporaryRegisteredRecipe(level, smithing, () -> {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(163, player.getInventory(), core);
                if (!menu.handleRecipeRequest(
                        level, smithingId, 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not select test smithing recipe");
                    return;
                }
                RecipePresentation smithingPresentation = menu.getRecipePresentation();
                if (smithingPresentation.kind() != RecipePresentationKind.SMITHING
                        || smithingPresentation.width() != 3
                        || smithingPresentation.height() != 1
                        || !smithingPresentation.inputs().get(0)
                        .is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
                        || !smithingPresentation.inputs().get(1).is(Items.DIAMOND_SWORD)
                        || !smithingPresentation.inputs().get(2).is(Items.NETHERITE_INGOT)
                        || !smithingPresentation.station().is(Items.SMITHING_TABLE)) {
                    helper.fail("Smithing role order mismatch: " + smithingPresentation);
                    return;
                }

                ItemStack axe = new ItemStack(Items.IRON_AXE);
                axe.setDamageValue(axe.getMaxDamage() - 5);
                if (!core.addAxeEnergy(axe)) {
                    helper.fail("Could not store Axe Energy for presentation test");
                    return;
                }
                menu.lookUpRecipes(level, new ItemStack(Items.STRIPPED_OAK_LOG));
                RecipeHolder<?> axeRecipe = menu.getCurrentRecipes().stream()
                        .filter(holder -> holder.value() instanceof AxeTransformationRecipe)
                        .findFirst().orElse(null);
                if (axeRecipe == null || !menu.handleRecipeRequest(
                        level, axeRecipe.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not select synthetic axe recipe");
                    return;
                }
                RecipePresentation axePresentation = menu.getRecipePresentation();
                RecipePresentation.Resource tool = axePresentation.resources().stream()
                        .filter(row -> row.kind() == RecipePresentation.ResourceKind.TOOL)
                        .findFirst().orElse(null);
                if (axePresentation.kind() != RecipePresentationKind.AXE
                        || !axePresentation.recipeId().equals(axeRecipe.id())
                        || axePresentation.inputs().stream().noneMatch(stack -> stack.is(Items.OAK_LOG))
                        || !axePresentation.output().is(Items.STRIPPED_OAK_LOG)
                        || tool == null
                        || !tool.stack().is(Items.IRON_AXE)
                        || tool.available() != 5
                        || tool.required() != 1
                        || tool.infinite()) {
                    helper.fail("Axe tool-resource presentation mismatch: " + axePresentation);
                    return;
                }
                if (RecipePresentation.Resource.tool(
                        new ItemStack(Items.IRON_AXE), Long.MAX_VALUE, 1).infinite()) {
                    helper.fail("Finite Long.MAX_VALUE tool availability must remain distinct from infinity");
                    return;
                }

                ItemStack unbreakable = new ItemStack(Items.DIAMOND_AXE);
                unbreakable.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
                if (!core.addAxeEnergy(unbreakable) || !menu.handleRecipeRequest(
                        level, axeRecipe.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not refresh the synthetic axe recipe with infinite Axe Energy");
                    return;
                }
                RecipePresentation.Resource infiniteTool = menu.getRecipePresentation().resources().stream()
                        .filter(row -> row.kind() == RecipePresentation.ResourceKind.TOOL)
                        .findFirst().orElse(null);
                if (infiniteTool == null || !infiniteTool.infinite()
                        || infiniteTool.available() != Long.MAX_VALUE || infiniteTool.required() != 1) {
                    helper.fail("Infinite Axe Energy must remain explicit in recipe presentation: "
                            + infiniteTool);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void recipe_navigation_updates_exact_presentation_identity(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(164, player.getInventory(), core);
            menu.lookUpRecipes(level, new ItemStack(Items.IRON_INGOT));
            if (menu.getRecipeCount() < 2) {
                helper.fail("Iron ingot fixture requires multiple recipes");
                return;
            }
            ResourceLocation first = menu.getCurrentRecipes().getFirst().id();
            if (!menu.getRecipePresentation().recipeId().equals(first)) {
                helper.fail("Initial presentation must use the active holder id");
                return;
            }
            menu.clickMenuButton(player, 9);
            ResourceLocation second = menu.getCurrentRecipes()
                    .get(menu.getCurrentRecipeIndex()).id();
            if (second.equals(first)
                    || !menu.getRecipePresentation().recipeId().equals(second)) {
                helper.fail("Next recipe must update exact presentation id");
                return;
            }
            menu.clickMenuButton(player, 8);
            if (!menu.getRecipePresentation().recipeId().equals(first)) {
                helper.fail("Previous recipe must restore exact presentation id");
                return;
            }
            menu.lookUpRecipes(level, new ItemStack(Items.BARRIER));
            if (!menu.getRecipePresentation().isEmpty()) {
                helper.fail("Empty recipe lookup must clear the presentation");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void oversized_shaped_recipe_is_rejected_before_presentation(GameTestHelper helper) {
        NonNullList<Ingredient> ingredients = NonNullList.withSize(4, Ingredient.of(Items.DIRT));
        var recipe = new ShapedRecipe(
                "",
                CraftingBookCategory.MISC,
                new ShapedRecipePattern(4, 1, ingredients, java.util.Optional.empty()),
                new ItemStack(Items.BARRIER));
        if (CraftingTerminalMenu.supportsRecipeContract(recipe)) {
            helper.fail("Recipes wider than the bounded 3x3 presentation must fail closed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void same_type_recipe_navigation_is_sorted_by_exact_id(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "presentation_order_a");
            ResourceLocation secondId = ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "presentation_order_b");
            var first = new RecipeHolder<>(firstId, new ShapelessRecipe(
                    "", CraftingBookCategory.MISC, new ItemStack(Items.BARRIER),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT))));
            var second = new RecipeHolder<>(secondId, new ShapelessRecipe(
                    "", CraftingBookCategory.MISC, new ItemStack(Items.BARRIER),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE))));
            var manager = level.getRecipeManager();
            var original = java.util.List.copyOf(manager.getRecipes());
            var registered = new java.util.ArrayList<>(original);
            registered.add(second);
            registered.add(first);
            manager.replaceRecipes(registered);
            try {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(165, player.getInventory(), core);
                menu.lookUpRecipes(level, new ItemStack(Items.BARRIER));
                if (menu.getCurrentRecipes().size() != 2
                        || !menu.getCurrentRecipes().get(0).id().equals(firstId)
                        || !menu.getCurrentRecipes().get(1).id().equals(secondId)) {
                    helper.fail("Same-type recipes must use exact ID as their deterministic tie-breaker: "
                            + menu.getCurrentRecipes().stream().map(RecipeHolder::id).toList());
                    return;
                }
                helper.succeed();
            } finally {
                manager.replaceRecipes(original);
            }
        });
    }

    @GameTest(template = "platform")
    public static void recipe_navigation_prioritizes_craftable_variants_and_preserves_selection(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.BARRIER));
            core.insertItem(new ItemStack(Items.DIRT));
            ResourceLocation blockedId = ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "craftable_order_a_blocked");
            ResourceLocation craftableId = ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "craftable_order_z_available");
            var blocked = new RecipeHolder<>(blockedId, new ShapelessRecipe(
                    "", CraftingBookCategory.MISC, new ItemStack(Items.BARRIER),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE))));
            var craftable = new RecipeHolder<>(craftableId, new ShapelessRecipe(
                    "", CraftingBookCategory.MISC, new ItemStack(Items.BARRIER),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT))));
            var manager = level.getRecipeManager();
            var original = java.util.List.copyOf(manager.getRecipes());
            var registered = new java.util.ArrayList<>(original);
            registered.add(blocked);
            registered.add(craftable);
            manager.replaceRecipes(registered);
            try {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(167, player.getInventory(), core);
                int barrierSlot = findDisplaySlot(menu, Items.BARRIER);
                if (barrierSlot < 0) {
                    helper.fail("Barrier display slot not found");
                    return;
                }
                menu.clicked(barrierSlot, 0, ClickType.PICKUP, player);
                if (menu.getRecipeCount() != 2
                        || menu.getCraftableRecipeCount() != 1
                        || !menu.getCurrentRecipes().getFirst().id().equals(craftableId)) {
                    helper.fail("Craftable recipe must sort first with a separate craftable-variant count: "
                            + menu.getCurrentRecipes().stream().map(RecipeHolder::id).toList());
                    return;
                }
                menu.nextRecipe();
                if (!menu.getRecipePresentation().recipeId().equals(blockedId)) {
                    helper.fail("Fixture could not select the blocked exact recipe");
                    return;
                }

                core.insertItem(new ItemStack(Items.STONE));
                menu.broadcastChanges();
                if (menu.getCraftableRecipeCount() != 2
                        || !menu.getRecipePresentation().recipeId().equals(blockedId)
                        || !menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex())
                        .id().equals(blockedId)) {
                    helper.fail("Material changes must rerank variants without changing exact selection");
                    return;
                }
                core.extractItem(ItemKey.of(new ItemStack(Items.DIRT)), 1);
                core.extractItem(ItemKey.of(new ItemStack(Items.STONE)), 1);
                menu.broadcastChanges();
                if (menu.getCraftableRecipeCount() != 0
                        || menu.getRecipeCount() != 2
                        || !menu.getRecipePresentation().recipeId().equals(blockedId)) {
                    helper.fail("Zero-craftable state must retain both variants and exact selection");
                    return;
                }
                helper.succeed();
            } finally {
                manager.replaceRecipes(original);
            }
        });
    }

    @GameTest(template = "platform")
    public static void recipe_navigation_rebinds_exact_identity_before_reload_validation(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installAllRecipeStations(core);
            core.insertItem(new ItemStack(Items.DIRT, 2));
            core.insertItem(new ItemStack(Items.STONE, 2));
            ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "presentation_reload_a");
            ResourceLocation secondId = ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "presentation_reload_b");
            var first = new RecipeHolder<>(firstId, new ShapelessRecipe(
                    "", CraftingBookCategory.MISC, new ItemStack(Items.COMMAND_BLOCK),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT))));
            var second = new RecipeHolder<>(secondId, new ShapelessRecipe(
                    "", CraftingBookCategory.MISC, new ItemStack(Items.COMMAND_BLOCK),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE))));
            var manager = level.getRecipeManager();
            var original = java.util.List.copyOf(manager.getRecipes());
            var registered = new java.util.ArrayList<>(original);
            registered.add(first);
            registered.add(second);
            manager.replaceRecipes(registered);
            try {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(166, player.getInventory(), core);
                if (!menu.handleRecipeRequest(
                        level, firstId, 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not select first exact reload recipe");
                    return;
                }
                menu.clickMenuButton(player, 9);
                if (!menu.getRecipePresentation().recipeId().equals(secondId)) {
                    helper.fail("Navigation did not select the second exact recipe");
                    return;
                }
                var reloaded = new java.util.ArrayList<>(original);
                reloaded.add(second);
                manager.replaceRecipes(reloaded);
                menu.refreshDisplayItems(core);
                RecipePresentation presentation = menu.getRecipePresentation();
                if (presentation.isEmpty() || !presentation.recipeId().equals(secondId)) {
                    helper.fail("Reload validation used stale pre-navigation recipe identity");
                    return;
                }
                helper.succeed();
            } finally {
                manager.replaceRecipes(original);
            }
        });
    }

    private static int countInInventory(Player player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void loadCoreItems(
            StorageCoreBlockEntity core,
            net.minecraft.core.HolderLookup.Provider registries,
            CoreItemCount... entries
    ) {
        for (CoreItemCount stored : entries) {
            core.insertItemCount(
                    ItemKey.of(stored.stack()), stored.count(), Action.EXECUTE, Actor.EMPTY);
        }
    }

    private record CoreItemCount(ItemStack stack, long count) {
        private CoreItemCount {
            stack = stack.copyWithCount(1);
            if (count <= 0) throw new IllegalArgumentException("Core item count must be positive");
        }
    }

    private static final class RejectingCraftOutputCore extends StorageCoreBlockEntity {
        private boolean rejectCraftOutput;

        private RejectingCraftOutputCore(
                BlockPos pos,
                net.minecraft.world.level.block.state.BlockState state
        ) {
            super(pos, state);
        }

        @Override
        public long insertItem(ItemStack stack, Action action, Actor actor) {
            if (rejectCraftOutput && action == Action.EXECUTE
                    && stack.is(Items.STICK) && actor.name().equals("auto_storage_crafting")) {
                return 0;
            }
            return super.insertItem(stack, action, actor);
        }

        @Override
        public long insertItemCount(ItemKey key, long amount, Action action, Actor actor) {
            if (rejectCraftOutput && action == Action.EXECUTE
                    && key.item() == Items.STICK && actor.name().equals("auto_storage_crafting")) {
                return 0;
            }
            return super.insertItemCount(key, amount, action, actor);
        }

        @Override
        public boolean applyResourceTransaction(
                StorageResourceTransaction transaction,
                Action action,
                Actor actor
        ) {
            if (rejectCraftOutput && action == Action.EXECUTE
                    && actor.name().equals("auto_storage_crafting")
                    && transaction.deltas().entrySet().stream()
                    .anyMatch(entry -> entry.getValue() > 0
                            && entry.getKey().resourceId().equals(
                            ResourceLocation.withDefaultNamespace("stick")))) return false;
            return super.applyResourceTransaction(transaction, action, actor);
        }
    }

    private static final class CountingPreviewCore extends StorageCoreBlockEntity {
        private int displaySnapshots;

        private CountingPreviewCore(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            super(pos, state);
        }

        @Override
        public boolean isStorageAvailable() {
            return true;
        }

        @Override
        public java.util.List<ItemStack> getDisplayStacks() {
            displaySnapshots++;
            return java.util.List.of(new ItemStack(Items.OAK_PLANKS, 64));
        }

        @Override
        java.util.List<IngredientSource> storedItemSources() {
            displaySnapshots++;
            ItemStack stack = new ItemStack(Items.OAK_PLANKS);
            return java.util.List.of(new IngredientSource(ItemKey.of(stack), -1, stack, 64L));
        }

        @Override
        public long getItemCount(ItemKey key) {
            return key.item() == Items.OAK_PLANKS ? 64 : 0;
        }

        @Override
        public long countMatching(java.util.function.Predicate<ItemStack> predicate) {
            return predicate.test(new ItemStack(Items.OAK_PLANKS)) ? 64 : 0;
        }
    }

    private static final class CountingRefreshMenu extends CraftingTerminalMenu {
        private int refreshes;
        private int descriptorStateSends;

        private CountingRefreshMenu(
                int containerId,
                net.minecraft.world.entity.player.Inventory inventory,
                StorageCoreBlockEntity core
        ) {
            super(containerId, inventory, core);
            refreshes = 0;
        }

        @Override
        public void refreshDisplayItems(StorageCoreBlockEntity core) {
            refreshes++;
            super.refreshDisplayItems(core);
        }

        private void observeStationWorkChange(StorageCoreBlockEntity core) {
            descriptorStateSends = 0;
            onObservedStationWorkChanged(
                    core, java.util.Map.of(MachineEnergyTable.FURNACE_ID, 1L), false);
        }

        @Override
        protected void sendDescriptorStates() {
            descriptorStateSends++;
        }
    }

    private static void installMachine(
            StorageCoreBlockEntity core,
            GameTestHelper helper,
            int slot,
            ItemStack stack
    ) {
        core.getMachineContainer().setItem(slot, stack.copy());
    }

    private static void installAllRecipeStations(StorageCoreBlockEntity core) {
        core.getMachineContainer().setItem(
                MachineEnergyTable.FURNACE_SLOT, new ItemStack(Items.FURNACE));
        core.getMachineContainer().setItem(
                MachineEnergyTable.BLAST_FURNACE_SLOT, new ItemStack(Items.BLAST_FURNACE));
        core.getMachineContainer().setItem(
                MachineEnergyTable.SMOKER_SLOT, new ItemStack(Items.SMOKER));
        core.getMachineContainer().setItem(
                MachineEnergyTable.CAMPFIRE_SLOT, new ItemStack(Items.CAMPFIRE));
        core.getMachineContainer().setItem(
                MachineEnergyTable.BREWING_STAND_SLOT, new ItemStack(Items.BREWING_STAND));
        core.getMachineContainer().setItem(
                MachineEnergyTable.CRAFTING_TABLE_SLOT, new ItemStack(Items.CRAFTING_TABLE));
        core.getMachineContainer().setItem(
                MachineEnergyTable.STONECUTTER_SLOT, new ItemStack(Items.STONECUTTER));
        core.getMachineContainer().setItem(
                MachineEnergyTable.SMITHING_TABLE_SLOT, new ItemStack(Items.SMITHING_TABLE));
    }

    private static void withTemporaryRegisteredRecipe(
            net.minecraft.world.level.Level level,
            RecipeHolder<?> holder,
            Runnable action
    ) {
        var manager = level.getRecipeManager();
        var originalRecipes = java.util.List.copyOf(manager.getRecipes());
        var registeredRecipes = new java.util.ArrayList<>(originalRecipes);
        registeredRecipes.removeIf(recipe -> recipe.id().equals(holder.id()));
        registeredRecipes.add(holder);
        manager.replaceRecipes(registeredRecipes);
        try {
            action.run();
        } finally {
            manager.replaceRecipes(originalRecipes);
        }
    }

    private static RecipeHolder<SmeltingRecipe> testSmeltingRecipe(String path, int cookingTime) {
        return new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path),
                new SmeltingRecipe(
                        "",
                        CookingBookCategory.MISC,
                        Ingredient.of(Items.DIRT),
                        new ItemStack(Items.DIAMOND),
                        0,
                        cookingTime
                )
        );
    }

    private static RecipeHolder<?> findCookingRecipe(
            net.minecraft.world.level.Level level,
            RecipeType<?> type,
            Item input,
            Item output
    ) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        var recipes = (java.util.Collection<RecipeHolder<?>>) (java.util.Collection)
                level.getRecipeManager().getAllRecipesFor((RecipeType) type);
        return recipes.stream()
                .filter(holder -> holder.value() instanceof AbstractCookingRecipe)
                .filter(holder -> holder.value().getResultItem(level.registryAccess()).is(output))
                .filter(holder -> holder.value().getIngredients().stream()
                        .anyMatch(ingredient -> ingredient.test(new ItemStack(input))))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasCompleteGenericIngredients(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if (recipe.isSpecial() || recipe.isIncomplete()) return false;
        return recipe.getIngredients().stream().anyMatch(ingredient -> !ingredient.isEmpty());
    }

    private static int findDisplaySlot(CraftingTerminalMenu menu, Item item) {
        for (int s = 0; s < StorageTerminalMenu.DISPLAY_SLOTS; s++) {
            if (menu.getSlot(s).getItem().is(item)) return s;
        }
        return -1;
    }

    private static String labelFor(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) return "Crafting";
        if (type == RecipeType.SMELTING) return "Smelting";
        if (type == RecipeType.BLASTING) return "Blasting";
        if (type == RecipeType.SMOKING) return "Smoking";
        if (type == RecipeType.CAMPFIRE_COOKING) return "Campfire";
        if (type == RecipeType.STONECUTTING) return "Stonecutting";
        if (type == RecipeType.SMITHING) return "Smithing";
        return "No recipe";
    }
}
