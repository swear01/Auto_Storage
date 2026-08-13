package com.swear.autostorage.fixture.productivebees;

import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.List;

@GameTestHolder(ProductivebeesFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ProductivebeesIntegrationGameTests {
    private ProductivebeesIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void unsafe_machine_contracts_are_not_registered(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), ProductivebeesIntegrationGameTests.class);
        if (!ModList.get().isLoaded("productivebees")) {
            helper.fail("Productive Bees mod is not loaded");
            return;
        }
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("productivebees")
                                || id.getPath().startsWith("productivebees_"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .filter(id -> id.getNamespace().equals("productivebees")
                                || id.getPath().startsWith("productivebees_"))
                        .anyMatch(id -> !id.equals(HONEY_GENERATOR))) {
            helper.fail("Productive Bees unsafe machine contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void centrifuge_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("centrifuge/honeycomb_breeze")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe centrifuge/honeycomb_breeze");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: centrifuge/honeycomb_breeze");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void bottler_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("bottler/honey_bottle")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe bottler/honey_bottle");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: bottler/honey_bottle");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void advanced_beehive_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("bee_produce/coal_bee")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe bee_produce/coal_bee");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: bee_produce/coal_bee");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void bee_breeding_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("bee_breeding/quarry_bee")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe bee_breeding/quarry_bee");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: bee_breeding/quarry_bee");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void bee_conversion_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("bee_conversion/hoarder_bee")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe bee_conversion/hoarder_bee");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: bee_conversion/hoarder_bee");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void block_conversion_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("block_conversion/anvil_repair")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe block_conversion/anvil_repair");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: block_conversion/anvil_repair");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_recipe_in_each_audited_machine_type_fails_closed(
            GameTestHelper helper
    ) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                pb("centrifuge/honeycomb_breeze"),
                pb("bottler/honey_bottle"),
                pb("bee_produce/coal_bee"),
                pb("bee_breeding/quarry_bee"),
                pb("bee_conversion/hoarder_bee"),
                pb("block_conversion/anvil_repair"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited Productive Bees recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        if (types.size() != 6) {
            helper.fail("Expected 6 unique audited Productive Bees recipe types, but found " + types.size());
            return;
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited Productive Bees recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe Productive Bees recipe type accepted " + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static ResourceLocation pb(String path) {
        return ResourceLocation.fromNamespaceAndPath("productivebees", path);
    }

    @GameTest(template = "craftingtests.platform")
    public static void honey_generator_converts_honey_to_fe_and_retains_bottles(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            var menu = transformMenu(core, player, new ItemStack(Items.HONEY_BOTTLE));
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(HONEY_GENERATOR))
                    .findFirst()
                    .orElse(null);
            int honeyUse = cy.jdkdigital.productivebees.ProductiveBeesConfig
                    .GENERAL.generatorHoneyUse.get();
            int powerGen = cy.jdkdigital.productivebees.ProductiveBeesConfig
                    .GENERAL.generatorPowerGen.get();
            long work = 250L / honeyUse;
            long expected = work * powerGen;
            if (use == null
                    || use.amountPerItem() != expected
                    || use.stationWorkPerItem() != work
                    || use.retainedItems().size() != 1
                    || !use.retainedItems().getFirst().is(Items.GLASS_BOTTLE)) {
                helper.fail("Honey generator use must convert one bottle to exact "
                        + "FE/work retaining a glass bottle");
                return;
            }
            selectTransform(menu, player, HONEY_GENERATOR);
            if (menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("Honey generator must reject without an installed machine");
                return;
            }
            installStation(core, player, pbItem("honey_generator"));
            tick(core, Math.toIntExact(work));
            if (!menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != expected
                    || core.getStationWork(HONEY_GENERATOR) != 0
                    || player.getInventory().countItem(Items.GLASS_BOTTLE) != 1) {
                helper.fail("Honey generator committed the wrong FE/work/input transaction");
                return;
            }
            helper.succeed();
        });
    }

    private static final int TRANSFORM_PAGE_BUTTON = 15;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final ResourceLocation HONEY_GENERATOR =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "productivebees_honey_generator");

    private static void withCore(
            GameTestHelper helper,
            FixtureAssertion assertion
    ) {
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
            player.setPos(
                    corePos.getX() + 0.5,
                    corePos.getY() + 0.5,
                    corePos.getZ() + 0.5);
            assertion.run(level, core, player);
        });
    }

    private static CraftingTerminalMenu transformMenu(
            StorageCoreBlockEntity core,
            Player player,
            ItemStack input
    ) {
        var menu = new CraftingTerminalMenu(
                965, player.getInventory(), core);
        menu.clickMenuButton(player, TRANSFORM_PAGE_BUTTON);
        menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).set(input);
        return menu;
    }

    private static void selectTransform(
            CraftingTerminalMenu menu,
            Player player,
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
                player, CraftingTerminalMenu.transformUseButtonId(useIndex))) {
            throw new IllegalStateException("Could not select transform " + transformId);
        }
    }

    private static void installStation(
            StorageCoreBlockEntity core,
            Player player,
            Item stationItem
    ) {
        ItemStack station = new ItemStack(stationItem);
        var menu = new CraftingTerminalMenu(
                966, player.getInventory(), core);
        menu.clickMenuButton(player, STATIONS_PAGE_BUTTON);
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START
                     + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = menu.getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) continue;
            slot.set(station.copy());
            slot.setChanged();
            menu.clickMenuButton(player, STORAGE_PAGE_BUTTON);
            return;
        }
        menu.clickMenuButton(player, STORAGE_PAGE_BUTTON);
        throw new IllegalStateException("Could not install Productive Bees station");
    }

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item pbItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("productivebees", path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Productive Bees item " + path);
        }
        return item;
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(
                net.minecraft.server.level.ServerLevel level,
                StorageCoreBlockEntity core,
                Player player
        );
    }
}
