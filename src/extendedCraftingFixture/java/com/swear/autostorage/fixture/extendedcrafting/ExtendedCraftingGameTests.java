package com.swear.autostorage.fixture.extendedcrafting;

import com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe;
import com.blakebr0.extendedcrafting.init.ModBlocks;
import com.blakebr0.extendedcrafting.init.ModItems;
import com.blakebr0.extendedcrafting.singularity.SingularityRegistry;
import com.blakebr0.extendedcrafting.singularity.SingularityUtils;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ExtendedCraftingFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ExtendedCraftingGameTests {
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            ExtendedCraftingFixtureMod.MODID, "ultimate_grid");
    private static final ResourceLocation ULTIMATE_SINGULARITY_RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath("extendedcrafting", "ultimate_singularity");
    private static final java.util.List<net.minecraft.world.item.Item> INGREDIENTS =
            java.util.List.of(
                    Items.REDSTONE,
                    Items.GLOWSTONE_DUST,
                    Items.COAL,
                    Items.CHARCOAL,
                    Items.IRON_INGOT,
                    Items.GOLD_INGOT,
                    Items.COPPER_INGOT,
                    Items.LAPIS_LAZULI,
                    Items.QUARTZ);

    private ExtendedCraftingGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void full_9x9_recipe_requires_station_and_consumes_all_81_inputs(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), ExtendedCraftingGameTests.class);
        withFixture(helper, context -> {
            seedIngredients(context.core(), 10);
            if (context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1, CraftingDestination.NONE, context.player())) {
                helper.fail("Extended Crafting recipe was selectable without an Ultimate Crafting Table");
                return;
            }
            installUltimateTable(context);
            if (!select(context)) {
                helper.fail("Extended Crafting 9x9 recipe was not discovered after station install");
                return;
            }
            var holder = context.level().getRecipeManager().byKey(RECIPE_ID).orElseThrow();
            if (!(holder.value() instanceof ShapedTableRecipe recipe)
                    || recipe.getWidth() != 9 || recipe.getHeight() != 9
                    || context.menu().getIngredientPreview().size() != 9
                    || context.menu().getIngredientPreview().stream()
                    .anyMatch(preview -> preview.required() != 9)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1) {
                helper.fail("Extended Crafting fixture did not preserve its 9x9/81-input contract");
                return;
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1, CraftingDestination.STORAGE, context.player())
                    || INGREDIENTS.stream().anyMatch(item -> itemCount(context.core(), item) != 1)
                    || itemCount(context.core(), Items.DIAMOND) != 1) {
                helper.fail("Extended Crafting 9x9 recipe did not commit exactly 81 inputs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void full_9x9_recipe_is_atomic_when_one_input_is_missing(GameTestHelper helper) {
        withFixture(helper, context -> {
            seedIngredients(context.core(), 9);
            if (context.core().extractItem(
                    ItemKey.of(new ItemStack(Items.QUARTZ)),
                    1).getCount() != 1) {
                helper.fail("Could not remove the one-short Extended Crafting ingredient");
                return;
            }
            installUltimateTable(context);
            if (!select(context)) {
                helper.fail("Extended Crafting 9x9 recipe was not discovered");
                return;
            }
            if (context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 0
                    || context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1,
                    CraftingDestination.STORAGE, context.player())
                    || itemCount(context.core(), Items.QUARTZ) != 8
                    || INGREDIENTS.stream()
                    .filter(item -> item != Items.QUARTZ)
                    .anyMatch(item -> itemCount(context.core(), item) != 9)
                    || itemCount(context.core(), Items.DIAMOND) != 0) {
                helper.fail("Extended Crafting one-short request was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void full_9x9_recipe_rolls_back_when_player_output_is_full(GameTestHelper helper) {
        withFixture(helper, context -> {
            seedIngredients(context.core(), 10);
            if (context.core().insertItem(new ItemStack(Items.EMERALD)) != 1) {
                helper.fail("Could not fill the final Core type slot");
                return;
            }
            installUltimateTable(context);
            if (!select(context)) {
                helper.fail("Extended Crafting 9x9 recipe was not discovered");
                return;
            }
            for (int slot = 0; slot < context.player().getInventory().getContainerSize(); slot++) {
                context.player().getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            if (context.menu().handleRecipeRequest(
                    context.level(), RECIPE_ID, 1,
                    CraftingDestination.INVENTORY, context.player())
                    || INGREDIENTS.stream().anyMatch(item -> itemCount(context.core(), item) != 10)
                    || itemCount(context.core(), Items.DIAMOND) != 0) {
                helper.fail("Extended Crafting full-destination request did not roll back");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void ultimate_singularity_preserves_every_component_input(GameTestHelper helper) {
        withFixture(helper, AutoStorage.STORAGE_UNIT_T4.get(), context -> {
            var singularities = SingularityRegistry.getInstance().getSingularities().stream()
                    .filter(singularity -> singularity.isInUltimateSingularity()
                            && !singularity.getIngredient().isEmpty())
                    .map(SingularityUtils::getItemForSingularity)
                    .toList();
            if (singularities.isEmpty()) {
                helper.fail("Ultimate Singularity fixture did not expose any inputs");
                return;
            }
            long itemTypes = singularities.stream().map(ItemStack::getItem).distinct().count();
            long componentTypes = singularities.stream()
                    .map(ItemStack::hashItemAndComponents).distinct().count();
            if (singularities.size() <= 9
                    || itemTypes != 1
                    || componentTypes != singularities.size()) {
                helper.fail("Ultimate Singularity fixture inputs=" + singularities.size()
                        + ", itemTypes=" + itemTypes + ", componentTypes=" + componentTypes);
                return;
            }
            for (ItemStack singularity : singularities) {
                ItemStack input = singularity.copyWithCount(2);
                if (context.core().insertItem(input) != 2) {
                    helper.fail("Could not seed Ultimate Singularity component input");
                    return;
                }
            }
            installUltimateTable(context);
            if (!select(context, ULTIMATE_SINGULARITY_RECIPE_ID)) {
                helper.fail("Ultimate Singularity recipe was not discovered");
                return;
            }
            if (context.menu().getIngredientPreview().size() != singularities.size()
                    || context.menu().getIngredientPreview().stream()
                    .anyMatch(preview -> preview.available() != 2 || preview.required() != 1)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 2) {
                helper.fail("Ultimate Singularity preview did not preserve every component input");
                return;
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), ULTIMATE_SINGULARITY_RECIPE_ID, 1,
                    CraftingDestination.STORAGE, context.player())
                    || singularities.stream().anyMatch(
                    singularity -> itemCount(context.core(), singularity) != 1)
                    || itemCount(context.core(), new ItemStack(
                    ModItems.ULTIMATE_SINGULARITY.get())) != 1) {
                helper.fail("Ultimate Singularity did not commit every exact component input");
                return;
            }
            helper.succeed();
        });
    }

    private static void withFixture(GameTestHelper helper, FixtureAssertion assertion) {
        withFixture(helper, AutoStorage.STORAGE_UNIT_T1.get(), assertion);
    }

    private static void withFixture(
            GameTestHelper helper,
            Block storageUnit,
            FixtureAssertion assertion
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(
                corePos.south(),
                storageUnit.defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(900, player.getInventory(), core);
            assertion.run(new FixtureContext(level, core, player, menu));
        });
    }

    private static void installUltimateTable(FixtureContext context) {
        context.menu().clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        ItemStack station = new ItemStack(ModBlocks.ULTIMATE_TABLE.get());
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = context.menu().getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) continue;
            slot.set(station);
            slot.setChanged();
            context.menu().clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
            return;
        }
        throw new IllegalStateException("Ultimate Crafting Table descriptor has no installable slot");
    }

    private static boolean select(FixtureContext context) {
        return select(context, RECIPE_ID);
    }

    private static boolean select(FixtureContext context, ResourceLocation recipeId) {
        return context.menu().handleRecipeRequest(
                context.level(), recipeId, 1, CraftingDestination.NONE, context.player());
    }

    private static void seedIngredients(StorageCoreBlockEntity core, int amount) {
        for (var item : INGREDIENTS) {
            if (core.insertItem(new ItemStack(item, amount)) != amount) {
                throw new IllegalStateException("Could not seed Extended Crafting ingredient " + item);
            }
        }
    }

    private static long itemCount(StorageCoreBlockEntity core, net.minecraft.world.item.Item item) {
        return core.getItemCount(ItemKey.of(new ItemStack(item)));
    }

    private static long itemCount(StorageCoreBlockEntity core, ItemStack stack) {
        return core.getItemCount(ItemKey.of(stack));
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
