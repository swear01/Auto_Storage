package com.swear.autostorage.fixture.botania;

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.Action;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKind;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.StorageTerminalMenu;
import com.swear.autostorage.TerminalContainerTransferDirection;
import com.swear.autostorage.TerminalHeldContainerTransferPacket;
import com.swear.autostorage.TerminalResourceView;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.common.crafting.ManaInfusionRecipe;
import vazkii.botania.common.crafting.StateIngredients;
import vazkii.botania.common.component.BotaniaDataComponents;
import vazkii.botania.common.item.ManaTabletItem;

import java.util.List;

@GameTestHolder(BotaniaFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class BotaniaIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int NEXT_RESOURCE_VIEW_BUTTON = 26;

    private static final ResourceLocation MANA_POOL =
            stationId("mana_pool");
    private static final ResourceLocation RUNIC_ALTAR =
            stationId("runic_altar");
    private static final ResourceLocation TERRESTRIAL_AGGLOMERATION =
            stationId("terrestrial_agglomeration_plate");
    private static final ResourceLocation PETAL_APOTHECARY =
            stationId("petal_apothecary");
    private static final ResourceLocation ELVEN_GATEWAY =
            stationId("elven_gateway");

    private BotaniaIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void mana_infusion_with_an_empty_output_fails_closed(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), BotaniaIntegrationGameTests.class);
        var recipe = new ManaInfusionRecipe(
                ItemStack.EMPTY,
                Ingredient.of(Items.DIAMOND),
                500,
                "",
                StateIngredients.NONE);
        if (CraftingTerminalMenu.supportsRecipeContract(recipe)) {
            helper.fail("Mana Infusion accepted an empty output");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void mana_kind_terminal_group_and_five_instant_stations_are_conditional(
            GameTestHelper helper
    ) {
        StorageResourceKind manaKind = AutoStorage.RESOURCE_KIND_REGISTRY.get(
                StorageResourceKindApi.BOTANIA_MANA_KIND);
        if (manaKind == null
                || !manaKind.representative().is(botaniaItem("mana_powder"))
                || !Component.translatable("gui.auto_storage.resource.mana")
                .equals(manaKind.representative().get(DataComponents.CUSTOM_NAME))
                || !TerminalResourceView.ENERGY.isAvailable()
                || !TerminalResourceView.ENERGY.matches(manaKey())
                || TerminalResourceView.OTHER.matches(manaKey())) {
            helper.fail("Botania Mana was not exposed in the Energy resource group");
            return;
        }

        List<StationSpec> stations = List.of(
                new StationSpec(MANA_POOL, "mana_pool"),
                new StationSpec(RUNIC_ALTAR, "runic_altar"),
                new StationSpec(TERRESTRIAL_AGGLOMERATION, "terrestrial_agglomeration_plate"),
                new StationSpec(PETAL_APOTHECARY, "petal_apothecary"),
                new StationSpec(ELVEN_GATEWAY, "elven_gateway_core"));
        for (StationSpec station : stations) {
            MachineDescriptor descriptor = MachineEnergyTable.get(station.descriptorId());
            Item stationItem = botaniaItem(station.itemPath());
            if (descriptor == null
                    || descriptor.category() != MachineCategory.INSTANT
                    || descriptor.maxInstalledCount() != 1
                    || !descriptor.accepts(new ItemStack(stationItem))
                    || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(station.descriptorId())) {
                helper.fail("Missing exact Botania instant station/family: " + station);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void terminal_moves_exact_mana_through_a_private_tablet_copy(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            ItemStack tablet = new ItemStack(botaniaItem("mana_tablet"));
            ManaItem tabletMana = requireManaItem(tablet);
            tabletMana.addMana(5_000);
            if (tabletMana.getMana() != 5_000) {
                helper.fail("Fixture could not charge a local Mana Tablet");
                return;
            }

            StorageTerminalMenu menu = new StorageTerminalMenu(
                    701, context.player().getInventory(), context.core());
            selectEnergyView(menu, context);
            menu.setCarried(tablet);
            boolean deposited = transferHeld(
                    menu, context, TerminalContainerTransferDirection.DEPOSIT);
            if (!deposited
                    || context.core().getResourceAmount(manaKey()) != 5_000
                    || requireManaItem(menu.getCarried()).getMana() != 0) {
                helper.fail("Mana Tablet deposit did not commit one exact private-copy delta");
                return;
            }

            boolean withdrawn = transferHeld(
                    menu, context, TerminalContainerTransferDirection.WITHDRAW);
            if (!withdrawn
                    || context.core().getResourceAmount(manaKey()) != 0
                    || requireManaItem(menu.getCarried()).getMana() != 5_000) {
                helper.fail("Mana Tablet withdrawal did not commit one exact private-copy delta");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void terminal_rejects_creative_tablets_and_bound_mana_mirrors(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            StorageTerminalMenu menu = new StorageTerminalMenu(
                    702, context.player().getInventory(), context.core());
            selectEnergyView(menu, context);

            ItemStack creative = new ItemStack(botaniaItem("mana_tablet"));
            ManaTabletItem.setStackCreative(creative);
            menu.setCarried(creative.copy());
            if (transferHeld(menu, context, TerminalContainerTransferDirection.DEPOSIT)
                    || context.core().getResourceAmount(manaKey()) != 0
                    || !ItemStack.isSameItemSameComponents(menu.getCarried(), creative)) {
                helper.fail("Creative Mana Tablet was treated as finite local Mana");
                return;
            }

            ItemStack mirror = new ItemStack(botaniaItem("mana_mirror"));
            mirror.set(BotaniaDataComponents.MAX_MANA, 10_000);
            mirror.set(BotaniaDataComponents.MANA, 5_000);
            mirror.set(BotaniaDataComponents.MANA_BACKLOG, 0);
            mirror.set(
                    BotaniaDataComponents.MANA_POOL_POS,
                    GlobalPos.of(context.level().dimension(), context.core().getBlockPos()));
            menu.setCarried(mirror.copy());
            if (transferHeld(menu, context, TerminalContainerTransferDirection.DEPOSIT)
                    || context.core().getResourceAmount(manaKey()) != 0
                    || !ItemStack.isSameItemSameComponents(menu.getCarried(), mirror)) {
                helper.fail("Bound Mana Mirror was treated as a local atomic Mana container");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void mana_infusion_consumes_mana_and_retains_state_catalyst_for_batch(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            Item catalyst = botaniaItem("alchemy_catalyst");
            seedItem(context.core(), Items.DIAMOND, 2);
            seedItem(context.core(), catalyst, 1);
            seedResource(context.core(), manaKey(), 1_000);
            if (!installStation(context, "mana_pool")
                    || !craft(context, recipeId("mana_infusion"), 2)) {
                helper.fail("Mana Infusion batch was not discovered or committed");
                return;
            }
            if (itemCount(context.core(), Items.DIAMOND) != 0
                    || itemCount(context.core(), catalyst) != 1
                    || itemCount(context.core(), Items.EMERALD) != 4
                    || context.core().getResourceAmount(manaKey()) != 0) {
                helper.fail("Mana Infusion did not retain its StateIngredient catalyst exactly");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void mana_infusion_output_overflow_rolls_back_mana_items_and_catalyst(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            Item catalyst = botaniaItem("alchemy_catalyst");
            seedItem(context.core(), Items.DIAMOND, 1);
            seedItem(context.core(), catalyst, 1);
            seedResource(context.core(), manaKey(), 500);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(Items.EMERALD), context.level().registryAccess()),
                    Long.MAX_VALUE);
            if (!installStation(context, "mana_pool")) {
                helper.fail("Could not install Mana Pool for overflow fixture");
                return;
            }
            if (craft(context, recipeId("mana_infusion"), 1)
                    || itemCount(context.core(), Items.DIAMOND) != 1
                    || itemCount(context.core(), catalyst) != 1
                    || itemCount(context.core(), Items.EMERALD) != Long.MAX_VALUE
                    || context.core().getResourceAmount(manaKey()) != 500) {
                helper.fail("Rejected Mana Infusion overflow partially mutated one typed transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void runic_altar_retains_catalyst_and_returns_container_remainders(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.WATER_BUCKET, 2);
            seedItem(context.core(), Items.QUARTZ, 1);
            seedItem(context.core(), Items.CLAY_BALL, 2);
            seedResource(context.core(), manaKey(), 400);
            if (!installStation(context, "runic_altar")
                    || !craft(context, recipeId("runic_altar"), 2)) {
                helper.fail("Runic Altar batch was not discovered or committed");
                return;
            }
            if (itemCount(context.core(), Items.WATER_BUCKET) != 0
                    || itemCount(context.core(), Items.QUARTZ) != 1
                    || itemCount(context.core(), Items.CLAY_BALL) != 0
                    || itemCount(context.core(), Items.GOLD_INGOT) != 4
                    || itemCount(context.core(), Items.BUCKET) != 2
                    || context.core().getResourceAmount(manaKey()) != 0) {
                helper.fail("Runic Altar catalyst, reagent, remainder, output, or Mana delta was wrong");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void terrestrial_agglomeration_commits_items_mana_and_output_together(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.IRON_INGOT, 2);
            seedItem(context.core(), Items.REDSTONE, 2);
            seedResource(context.core(), manaKey(), 2_000);
            if (!installStation(context, "terrestrial_agglomeration_plate")
                    || !craft(context, recipeId("terrestrial_agglomeration"), 2)) {
                helper.fail("Terrestrial Agglomeration batch was not discovered or committed");
                return;
            }
            if (itemCount(context.core(), Items.IRON_INGOT) != 0
                    || itemCount(context.core(), Items.REDSTONE) != 0
                    || itemCount(context.core(), Items.NETHERITE_SCRAP) != 2
                    || context.core().getResourceAmount(manaKey()) != 0) {
                helper.fail("Terrestrial Agglomeration did not commit one exact typed transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void petal_apothecary_consumes_one_bucket_of_stored_water(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.POPPY, 1);
            seedItem(context.core(), Items.DANDELION, 1);
            seedItem(context.core(), Items.WHEAT_SEEDS, 1);
            StorageResourceKey water = StorageResourceKey.fluid(
                    new FluidStack(Fluids.WATER, 1), context.level().registryAccess());
            seedResource(context.core(), water, 1_000);
            if (!installStation(context, "petal_apothecary")
                    || !craft(context, recipeId("petal_apothecary"), 1)) {
                helper.fail("Petal Apothecary recipe was not discovered or committed");
                return;
            }
            if (itemCount(context.core(), Items.POPPY) != 0
                    || itemCount(context.core(), Items.DANDELION) != 0
                    || itemCount(context.core(), Items.WHEAT_SEEDS) != 0
                    || itemCount(context.core(), Items.HONEYCOMB) != 1
                    || context.core().getResourceAmount(water) != 0) {
                helper.fail("Petal Apothecary did not consume exact water/items in one transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void elven_trade_commits_every_fixed_output_exactly(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.COAL, 2);
            seedItem(context.core(), Items.FLINT, 2);
            seedResource(context.core(), manaKey(), 1_000);
            if (!installStation(context, "elven_gateway_core")
                    || !craft(context, recipeId("elven_trade"), 2)) {
                helper.fail("Elven Trade batch was not discovered or committed");
                return;
            }
            if (itemCount(context.core(), Items.COAL) != 0
                    || itemCount(context.core(), Items.FLINT) != 0
                    || itemCount(context.core(), Items.DIAMOND) != 4
                    || itemCount(context.core(), Items.EMERALD) != 6
                    || context.core().getResourceAmount(manaKey()) != 0) {
                helper.fail("Elven Trade did not preserve outputs or consume 500 Mana per trade");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void elven_trade_rejects_one_mana_short_without_partial_mutation(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.COAL, 1);
            seedItem(context.core(), Items.FLINT, 1);
            seedResource(context.core(), manaKey(), 499);
            if (!installStation(context, "elven_gateway_core")) {
                helper.fail("Could not install Elven Gateway for insufficient-Mana fixture");
                return;
            }
            if (craft(context, recipeId("elven_trade"), 1)
                    || itemCount(context.core(), Items.COAL) != 1
                    || itemCount(context.core(), Items.FLINT) != 1
                    || itemCount(context.core(), Items.DIAMOND) != 0
                    || itemCount(context.core(), Items.EMERALD) != 0
                    || context.core().getResourceAmount(manaKey()) != 499) {
                helper.fail("Insufficient Elven Trade Mana partially changed one transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void botania_recipe_requires_its_matching_installed_station(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            Item catalyst = botaniaItem("alchemy_catalyst");
            seedItem(context.core(), Items.DIAMOND, 1);
            seedItem(context.core(), catalyst, 1);
            seedResource(context.core(), manaKey(), 500);
            if (select(context, recipeId("mana_infusion"))
                    || craft(context, recipeId("mana_infusion"), 1)
                    || itemCount(context.core(), Items.DIAMOND) != 1
                    || itemCount(context.core(), catalyst) != 1
                    || context.core().getResourceAmount(manaKey()) != 500) {
                helper.fail("Mana Infusion ignored its matching installed Mana Pool");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void recipes_beyond_the_nine_input_transaction_contract_fail_closed(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            for (Item item : List.of(
                    Items.POPPY,
                    Items.DANDELION,
                    Items.BLUE_ORCHID,
                    Items.ALLIUM,
                    Items.AZURE_BLUET,
                    Items.RED_TULIP,
                    Items.ORANGE_TULIP,
                    Items.WHITE_TULIP,
                    Items.WHEAT_SEEDS)) {
                seedItem(context.core(), item, 1);
            }
            StorageResourceKey water = StorageResourceKey.fluid(
                    new FluidStack(Fluids.WATER, 1), context.level().registryAccess());
            seedResource(context.core(), water, 1_000);
            if (!installStation(context, "petal_apothecary")) {
                helper.fail("Could not install Petal Apothecary for fail-closed fixture");
                return;
            }
            if (select(context, recipeId("petal_apothecary_too_many_inputs"))
                    || craft(context, recipeId("petal_apothecary_too_many_inputs"), 1)
                    || itemCount(context.core(), Items.AMETHYST_SHARD) != 0
                    || context.core().getResourceAmount(water) != 1_000) {
                helper.fail("A ten-input Botania recipe escaped the bounded typed-plan contract");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void world_random_brewery_and_passive_receiver_families_stay_excluded(
            GameTestHelper helper
    ) {
        for (String path : List.of(
                "botanical_brewery",
                "pure_daisy",
                "orechid",
                "mana_receiver")) {
            if (AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(stationId(path))) {
                helper.fail("Unsafe Botania family was registered: " + path);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void thermalily_lava_bucket_converts_to_mana_work_and_retained_bucket(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            var menu = transformMenu(
                    context, new ItemStack(Items.LAVA_BUCKET));
            var thermalilyUse = menu.getTransformUses().stream()
                    .filter(use -> use.id().equals(stationId("thermalily")))
                    .findFirst()
                    .orElse(null);
            if (thermalilyUse == null
                    || thermalilyUse.amountPerItem() != 27_000
                    || thermalilyUse.stationWorkPerItem() != 6_600
                    || thermalilyUse.retainedItems().size() != 1
                    || !thermalilyUse.retainedItems().getFirst().is(Items.BUCKET)) {
                helper.fail("Thermalily use must be a 27,000-Mana / 6,600-work lava conversion "
                        + "retaining one empty bucket");
                return;
            }
            selectTransform(menu, context, stationId("thermalily"));
            if (menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(manaKey()) != 0
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                    .getItem().is(Items.LAVA_BUCKET)) {
                helper.fail("Thermalily must reject without an installed flower");
                return;
            }

            if (!installStation(context, "thermalily")) {
                helper.fail("Could not install the Thermalily");
                return;
            }
            tick(context.core(), 6_600);
            if (!menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(manaKey()) != 27_000
                    || context.core().getStationWork(stationId("thermalily")) != 0
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().isEmpty()
                    || context.player().getInventory().countItem(Items.BUCKET) != 1) {
                helper.fail("Thermalily committed the wrong mana/work/input/bucket transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void endoflame_fuel_converts_to_exact_mana_with_burn_plus_cooldown_work(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            if (!installStation(context, "endoflame")) {
                helper.fail("Could not install the Endoflame");
                return;
            }
            var menu = transformMenu(context, new ItemStack(Items.COAL));
            var endoflameUse = menu.getTransformUses().stream()
                    .filter(use -> use.id().equals(stationId("endoflame")))
                    .findFirst()
                    .orElse(null);
            if (endoflameUse == null
                    || endoflameUse.amountPerItem() != 4_800
                    || endoflameUse.stationWorkPerItem() != 1_640
                    || !endoflameUse.retainedItems().isEmpty()) {
                helper.fail("Endoflame use must convert one coal to 4,800 Mana over "
                        + "1,640 work with no retained item");
                return;
            }
            if (transformMenu(context, new ItemStack(Items.STONE))
                    .getTransformUses().stream()
                    .anyMatch(use -> use.id().equals(stationId("endoflame")))) {
                helper.fail("Endoflame must reject non-fuel inputs");
                return;
            }
            selectTransform(menu, context, stationId("endoflame"));
            tick(context.core(), 1_640);
            if (!menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(manaKey()) != 4_800
                    || context.core().getStationWork(stationId("endoflame")) != 0
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().isEmpty()) {
                helper.fail("Endoflame committed the wrong mana/work/input transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void thermalily_mana_overflow_rolls_back_work_input_and_retained_bucket(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            if (!installStation(context, "thermalily")) {
                helper.fail("Could not install the Thermalily");
                return;
            }
            seedResource(context.core(), manaKey(), Long.MAX_VALUE);
            tick(context.core(), 6_600);
            long workBefore = context.core().getStationWork(stationId("thermalily"));
            var menu = transformMenu(
                    context, new ItemStack(Items.LAVA_BUCKET));
            selectTransform(menu, context, stationId("thermalily"));
            if (menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(manaKey()) != Long.MAX_VALUE
                    || context.core().getStationWork(stationId("thermalily")) != workBefore
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                    .getItem().is(Items.LAVA_BUCKET)
                    || context.player().getInventory().countItem(Items.BUCKET) != 0) {
                helper.fail("Thermalily mana overflow partially mutated the transform");
                return;
            }
            helper.succeed();
        });
    }

    private static void withCore(GameTestHelper helper, FixtureAssertion assertion) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(
                corePos,
                AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(
                corePos.south(),
                AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            assertion.run(new FixtureContext(level, core, player));
        });
    }

    private static boolean installStation(
            FixtureContext context,
            String itemPath
    ) {
        var menu = new CraftingTerminalMenu(
                703, context.player().getInventory(), context.core());
        ItemStack station = new ItemStack(botaniaItem(itemPath));
        menu.clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START
                     + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = menu.getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) continue;
            slot.set(station.copy());
            slot.setChanged();
            menu.clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
            return slot.getItem().getCount() == 1
                    && ItemStack.isSameItemSameComponents(slot.getItem(), station);
        }
        menu.clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
        return false;
    }

    private static boolean select(FixtureContext context, ResourceLocation recipeId) {
        var menu = new CraftingTerminalMenu(
                704, context.player().getInventory(), context.core());
        return menu.handleRecipeRequest(
                context.level(), recipeId, 1, CraftingDestination.NONE, context.player());
    }

    private static boolean craft(
            FixtureContext context,
            ResourceLocation recipeId,
            int crafts
    ) {
        var menu = new CraftingTerminalMenu(
                705, context.player().getInventory(), context.core());
        if (!menu.handleRecipeRequest(
                context.level(), recipeId, 1, CraftingDestination.NONE, context.player())) {
            return false;
        }
        if (menu.computeCraftPreview(context.core(), context.player()).craftable() < crafts) {
            return false;
        }
        return menu.handleRecipeRequest(
                context.level(), recipeId, crafts, CraftingDestination.STORAGE, context.player());
    }

    private static void selectEnergyView(
            StorageTerminalMenu menu,
            FixtureContext context
    ) {
        for (int attempt = 0;
             attempt < TerminalResourceView.values().length
                     && menu.getResourceView() != TerminalResourceView.ENERGY;
             attempt++) {
            menu.clickMenuButton(
                    context.player(), NEXT_RESOURCE_VIEW_BUTTON);
        }
        if (menu.getResourceView() != TerminalResourceView.ENERGY) {
            throw new IllegalStateException("Botania Mana was not selectable in Energy");
        }
        menu.refreshDisplayItems(context.core());
    }

    private static boolean transferHeld(
            StorageTerminalMenu menu,
            FixtureContext context,
            TerminalContainerTransferDirection direction
    ) {
        menu.refreshDisplayItems(context.core());
        return menu.handleHeldContainerTransfer(
                new TerminalHeldContainerTransferPacket(
                        menu.containerId,
                        menu.getStateId(),
                        0,
                        TerminalResourceView.ENERGY,
                        direction),
                context.player());
    }

    private static void seedItem(StorageCoreBlockEntity core, Item item, int amount) {
        if (core.insertItem(new ItemStack(item, amount)) != amount) {
            throw new IllegalStateException("Could not seed " + item + " x" + amount);
        }
    }

    private static void seedResource(
            StorageCoreBlockEntity core,
            StorageResourceKey key,
            long amount
    ) {
        if (core.insertResource(key, amount, Action.EXECUTE) != amount) {
            throw new IllegalStateException("Could not seed " + key + " x" + amount);
        }
    }

    private static long itemCount(StorageCoreBlockEntity core, Item item) {
        return core.getItemCount(ItemKey.of(new ItemStack(item)));
    }

    private static ManaItem requireManaItem(ItemStack stack) {
        ManaItem mana = ManaItem.LOOKUP.find(stack);
        if (mana == null) throw new IllegalStateException("Stack has no Botania ManaItem");
        return mana;
    }

    private static StorageResourceKey manaKey() {
        return StorageResourceKey.of(
                StorageResourceKindApi.BOTANIA_MANA_KIND,
                StorageResourceKindApi.BOTANIA_MANA_KIND,
                new CompoundTag());
    }

    private static Item botaniaItem(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("botania", path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalStateException("Missing Botania item " + id);
        return item;
    }

    private static ResourceLocation stationId(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                AutoStorage.MODID, "botania_" + path);
    }

    private static ResourceLocation recipeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(BotaniaFixtureMod.MODID, path);
    }

    private static final int TRANSFORM_PAGE_BUTTON = 15;

    private static CraftingTerminalMenu transformMenu(
            FixtureContext context,
            ItemStack input
    ) {
        var menu = new CraftingTerminalMenu(
                706, context.player().getInventory(), context.core());
        menu.clickMenuButton(context.player(), TRANSFORM_PAGE_BUTTON);
        menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).set(input);
        return menu;
    }

    private static void selectTransform(
            CraftingTerminalMenu menu,
            FixtureContext context,
            ResourceLocation transformId
    ) {
        int useIndex = -1;
        var uses = menu.getVisibleTransformUses();
        for (int index = 0; index < uses.size(); index++) {
            if (uses.get(index).id().equals(transformId)) {
                useIndex = index;
                break;
            }
        }
        if (useIndex < 0 || !menu.clickMenuButton(
                context.player(), CraftingTerminalMenu.transformUseButtonId(useIndex))) {
            throw new IllegalStateException("Could not select transform " + transformId);
        }
    }

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(FixtureContext context);
    }

    private record FixtureContext(
            net.minecraft.server.level.ServerLevel level,
            StorageCoreBlockEntity core,
            net.minecraft.world.entity.player.Player player
    ) {
    }

    private record StationSpec(ResourceLocation descriptorId, String itemPath) {
    }
}
