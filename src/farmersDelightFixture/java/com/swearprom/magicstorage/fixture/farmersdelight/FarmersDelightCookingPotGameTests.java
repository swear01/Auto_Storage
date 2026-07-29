package com.swearprom.magicstorage.fixture.farmersdelight;

import com.swearprom.magicstorage.magic_storage.CraftingDestination;
import com.swearprom.magicstorage.magic_storage.CraftingTerminalMenu;
import com.swearprom.magicstorage.magic_storage.EnergyType;
import com.swearprom.magicstorage.magic_storage.ItemKey;
import com.swearprom.magicstorage.magic_storage.MagicStorage;
import com.swearprom.magicstorage.magic_storage.StorageCoreBlockEntity;
import com.swearprom.magicstorage.magic_storage.compat.farmersdelight.FarmersDelightCookingPotCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModItems;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(FarmersDelightFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class FarmersDelightCookingPotGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int CRAFTABLE_PAGE_BUTTON = 6;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int PLAYER_INVENTORY_BUTTON = 7;
    private static final int COOK_TIME = 40;
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            FarmersDelightFixtureMod.MODID, "cooking_pot_fixture");

    private FarmersDelightCookingPotGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void cooking_pot_requires_every_ingredient_and_serving_container(
            GameTestHelper helper
    ) {
        withFixture(helper, context -> {
            seed(context.core(), Items.CARROT, 1);
            seed(context.core(), Items.WATER_BUCKET, 1);
            addFuel(context);
            addWork(context, COOK_TIME);

            if (!select(context)) {
                helper.fail("Cooking Pot recipe family was not discovered");
                return;
            }
            if (context.menu().computeCraftPreview(context.core(), context.player()).craftable() != 0
                    || context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Cooking Pot recipe crafted without its serving container");
                return;
            }
            assertState(helper, context, 1, 1, 0, 0, 0, COOK_TIME, 1600);
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void cooking_pot_requires_furnace_fuel(GameTestHelper helper) {
        withFixture(helper, context -> {
            seedInputs(context, 1);
            addWork(context, COOK_TIME);

            if (!select(context)) {
                helper.fail("Cooking Pot recipe family was not discovered");
                return;
            }
            if (context.menu().computeCraftPreview(context.core(), context.player()).craftable() != 0
                    || context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Cooking Pot recipe crafted without Furnace Fuel");
                return;
            }
            assertState(helper, context, 1, 1, 1, 0, 0, COOK_TIME, 0);
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void cooking_pot_charges_exact_recipe_cook_time(GameTestHelper helper) {
        withFixture(helper, context -> {
            seedInputs(context, 1);
            addFuel(context);
            addWork(context, COOK_TIME - 1);

            if (!select(context)) {
                helper.fail("Cooking Pot recipe family was not discovered");
                return;
            }
            if (context.menu().computeCraftPreview(context.core(), context.player()).craftable() != 0
                    || context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Cooking Pot recipe ignored its exact cookTime station-work cost");
                return;
            }
            assertState(helper, context, 1, 1, 1, 0, 0, COOK_TIME - 1, 1600);
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void cooking_pot_commits_multi_input_container_output_and_remainder(
            GameTestHelper helper
    ) {
        withFixture(helper, context -> {
            seedInputs(context, 1);
            addFuel(context);
            addWork(context, COOK_TIME);

            if (!select(context)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Cooking Pot recipe did not commit after every exact requirement was met");
                return;
            }
            assertState(helper, context, 0, 0, 0, 2, 1, 0, 1560);
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void cooking_pot_full_destination_rolls_back_all_resources_and_work(
            GameTestHelper helper
    ) {
        withFixture(helper, context -> {
            seedInputs(context, 2);
            for (var item : List.of(
                    Items.STONE,
                    Items.DIRT,
                    Items.SAND,
                    Items.OAK_LOG,
                    Items.IRON_INGOT,
                    Items.GOLD_INGOT,
                    Items.DIAMOND)) {
                seed(context.core(), item, 1);
            }
            if (context.core().getTypeCount() != 10) {
                helper.fail("Cooking Pot rollback fixture did not fill all ten Core type slots");
                return;
            }
            for (int slot = 0; slot < context.player().getInventory().getContainerSize(); slot++) {
                context.player().getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            addFuel(context);
            addWork(context, COOK_TIME);

            if (!select(context)) {
                helper.fail("Cooking Pot recipe family was not discovered");
                return;
            }
            if (context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Full destination unexpectedly accepted Cooking Pot output");
                return;
            }
            assertState(helper, context, 2, 2, 2, 0, 0, COOK_TIME, 1600);
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void cooking_pot_lists_and_consumes_typed_inputs_from_player_inventory(
            GameTestHelper helper
    ) {
        withFixture(helper, context -> {
            context.player().getInventory().setItem(0, new ItemStack(Items.CARROT));
            context.player().getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
            context.player().getInventory().setItem(2, new ItemStack(Items.BOWL));
            addFuel(context);
            addWork(context, COOK_TIME);

            context.menu().clickMenuButton(
                    context.player(), CRAFTABLE_PAGE_BUTTON);
            context.menu().refreshDisplayItems(context.core());
            if (displayContains(context, Items.GOLDEN_CARROT)) {
                helper.fail("Cooking Pot typed recipe used player inputs while that source was disabled");
                return;
            }
            context.menu().clickMenuButton(context.player(), PLAYER_INVENTORY_BUTTON);
            context.menu().refreshDisplayItems(context.core());
            if (!displayContains(context, Items.GOLDEN_CARROT)
                    || !select(context)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Cooking Pot typed recipe ignored enabled player-inventory inputs");
                return;
            }
            if (inventoryCount(context, Items.CARROT) != 0
                    || inventoryCount(context, Items.WATER_BUCKET) != 0
                    || inventoryCount(context, Items.BOWL) != 0) {
                helper.fail("Cooking Pot did not consume its player-inventory typed inputs");
                return;
            }
            assertState(helper, context, 0, 0, 0, 2, 1, 0, 1560);
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void cooking_pot_player_inventory_inputs_roll_back_with_full_destination(
            GameTestHelper helper
    ) {
        withFixture(helper, context -> {
            for (var item : List.of(
                    Items.STONE,
                    Items.DIRT,
                    Items.SAND,
                    Items.OAK_LOG,
                    Items.IRON_INGOT,
                    Items.GOLD_INGOT,
                    Items.DIAMOND,
                    Items.EMERALD,
                    Items.COAL,
                    Items.REDSTONE)) {
                seed(context.core(), item, 1);
            }
            for (int slot = 0; slot < context.player().getInventory().getContainerSize(); slot++) {
                context.player().getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            context.player().getInventory().setItem(0, new ItemStack(Items.CARROT, 2));
            context.player().getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET, 2));
            context.player().getInventory().setItem(2, new ItemStack(Items.BOWL, 2));
            context.menu().clickMenuButton(context.player(), PLAYER_INVENTORY_BUTTON);
            addFuel(context);
            addWork(context, COOK_TIME);

            if (!select(context)) {
                helper.fail("Cooking Pot player-inventory recipe was not discovered");
                return;
            }
            if (context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())) {
                helper.fail("Full destination unexpectedly consumed player-inventory typed inputs");
                return;
            }
            if (inventoryCount(context, Items.CARROT) != 2
                    || inventoryCount(context, Items.WATER_BUCKET) != 2
                    || inventoryCount(context, Items.BOWL) != 2
                    || inventoryCount(context, Items.GOLDEN_CARROT) != 0
                    || inventoryCount(context, Items.BUCKET) != 0
                    || context.core().getStationWork(
                    FarmersDelightCookingPotCompat.descriptorId("magic_storage")) != COOK_TIME
                    || context.core().getEnergy(EnergyType.FURNACE_FUEL) != 1600) {
                helper.fail("Failed Cooking Pot delivery did not preserve player inputs and costs");
                return;
            }
            helper.succeed();
        });
    }

    private static void withFixture(GameTestHelper helper, FixtureAssertion assertion) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(
                corePos.south(),
                MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.add(Ingredient.of(Items.CARROT));
            ingredients.add(Ingredient.of(Items.WATER_BUCKET));
            RecipeHolder<CookingPotRecipe> holder = new RecipeHolder<>(
                    RECIPE_ID,
                    new CookingPotRecipe(
                            "",
                            null,
                            ingredients,
                            new ItemStack(Items.GOLDEN_CARROT, 2),
                            new ItemStack(Items.BOWL),
                            0,
                            COOK_TIME));
            var manager = level.getRecipeManager();
            List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
            List<RecipeHolder<?>> registered = new ArrayList<>(original);
            registered.removeIf(recipe -> recipe.id().equals(RECIPE_ID));
            registered.add(holder);
            manager.replaceRecipes(registered);
            try {
                core.rebuildNetwork(level);
                var player = helper.makeMockPlayer(GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(500, player.getInventory(), core);
                var context = new FixtureContext(level, core, player, menu);
                if (!installCookingPot(context)) {
                    helper.fail("Could not install Farmer's Delight Cooking Pot descriptor");
                    return;
                }
                assertion.run(context);
            } finally {
                manager.replaceRecipes(original);
            }
        });
    }

    private static boolean installCookingPot(FixtureContext context) {
        context.menu().clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        ItemStack station = new ItemStack(ModItems.COOKING_POT.get());
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = context.menu().getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) continue;
            slot.set(station.copy());
            slot.setChanged();
            context.menu().clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
            return true;
        }
        context.menu().clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
        return false;
    }

    private static void addWork(FixtureContext context, int ticks) {
        for (int tick = 0; tick < ticks; tick++) context.core().tick();
    }

    private static void addFuel(FixtureContext context) {
        ItemStack coal = new ItemStack(Items.COAL);
        if (!context.core().addFuel(coal, EnergyType.FURNACE_FUEL) || !coal.isEmpty()) {
            throw new IllegalStateException("Could not seed Cooking Pot Furnace Fuel");
        }
    }

    private static boolean select(FixtureContext context) {
        return context.menu().handleRecipeRequest(
                context.level(), RECIPE_ID, 1, CraftingDestination.NONE, context.player());
    }

    private static boolean displayContains(FixtureContext context, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < com.swearprom.magicstorage.magic_storage.StorageTerminalMenu.DISPLAY_SLOTS;
             slot++) {
            if (context.menu().getSlot(slot).getItem().is(item)) return true;
        }
        return false;
    }

    private static void seedInputs(FixtureContext context, int count) {
        seed(context.core(), Items.CARROT, count);
        seed(context.core(), Items.WATER_BUCKET, count);
        seed(context.core(), Items.BOWL, count);
    }

    private static void seed(StorageCoreBlockEntity core, net.minecraft.world.item.Item item, int count) {
        if (core.insertItem(new ItemStack(item, count)) != count) {
            throw new IllegalStateException("Could not seed " + item + " x" + count);
        }
    }

    private static void assertState(
            GameTestHelper helper,
            FixtureContext context,
            long carrots,
            long waterBuckets,
            long bowls,
            int output,
            int buckets,
            long work,
            long fuel
    ) {
        long actualCarrots = itemCount(context.core(), Items.CARROT);
        long actualWaterBuckets = itemCount(context.core(), Items.WATER_BUCKET);
        long actualBowls = itemCount(context.core(), Items.BOWL);
        int actualOutput = inventoryCount(context, Items.GOLDEN_CARROT);
        int actualBuckets = inventoryCount(context, Items.BUCKET);
        long actualWork = context.core().getStationWork(
                FarmersDelightCookingPotCompat.descriptorId(MagicStorage.MODID));
        long actualFuel = context.core().getEnergy(EnergyType.FURNACE_FUEL);
        if (actualCarrots != carrots
                || actualWaterBuckets != waterBuckets
                || actualBowls != bowls
                || actualOutput != output
                || actualBuckets != buckets
                || actualWork != work
                || actualFuel != fuel) {
            helper.fail("Unexpected Cooking Pot state: carrots=" + actualCarrots
                    + ", waterBuckets=" + actualWaterBuckets
                    + ", bowls=" + actualBowls
                    + ", output=" + actualOutput
                    + ", buckets=" + actualBuckets
                    + ", work=" + actualWork
                    + ", fuel=" + actualFuel);
            return;
        }
        helper.succeed();
    }

    private static long itemCount(StorageCoreBlockEntity core, net.minecraft.world.item.Item item) {
        return core.getItemCount(ItemKey.of(new ItemStack(item)));
    }

    private static int inventoryCount(FixtureContext context, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < context.player().getInventory().getContainerSize(); slot++) {
            ItemStack stack = context.player().getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(FixtureContext context);
    }

    private record FixtureContext(
            net.minecraft.server.level.ServerLevel level,
            StorageCoreBlockEntity core,
            net.minecraft.world.entity.player.Player player,
            CraftingTerminalMenu menu
    ) {
    }
}
