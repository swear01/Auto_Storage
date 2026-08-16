package com.swear.autostorage.fixture.productivetrees;

import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(ProductivetreesFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ProductivetreesIntegrationGameTests {
    private ProductivetreesIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void sawmill_family_registered_with_expected_machines(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                ProductivetreesIntegrationGameTests.class);
        if (!ModList.get().isLoaded("productivetrees")) {
            helper.fail("Productive Trees mod is not loaded");
            return;
        }
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .filter(id -> id.getNamespace().equals("productivetrees")
                                || id.getPath().startsWith("productivetrees_"))
                        .anyMatch(id -> !SUPPORTED_MACHINES.contains(id))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .filter(id -> id.getNamespace().equals("productivetrees")
                                || id.getPath().startsWith("productivetrees_"))
                        .anyMatch(id -> !SUPPORTED_MACHINES.contains(id))) {
            helper.fail("Productive Trees unsafe machine contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void sawmill_recipe_is_supported(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pt("sawmill/dark_oak_planks_from_log")).orElse(null);
        if (holder == null
                || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Sawmill recipe must be supported: sawmill/dark_oak_planks_from_log");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void sawmill_converts_log_to_planks_with_sawdust(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            var manager = level.getRecipeManager();
            var holder = manager.byKey(pt("sawmill/dark_oak_planks_from_log")).orElse(null);
            if (holder == null
                    || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Sawmill recipe must be supported: sawmill/dark_oak_planks_from_log");
                return;
            }
            seedItem(core, Items.DARK_OAK_LOG, 1);
            installStation(core, player, ptItem("sawmill"));
            addCoreTicks(core, 10_000);
            var menu = new CraftingTerminalMenu(
                    610, player.getInventory(), core);
            boolean requested = menu.handleRecipeRequest(
                    level, pt("sawmill/dark_oak_planks_from_log"), 1,
                    CraftingDestination.NONE, player);
            boolean committed = requested
                    && menu.computeCraftPreview(core, player).craftable() >= 1
                    && menu.handleRecipeRequest(
                            level, pt("sawmill/dark_oak_planks_from_log"), 1,
                            CraftingDestination.STORAGE, player);
            long planks = core.getItemCount(ItemKey.of(new ItemStack(Items.DARK_OAK_PLANKS)));
            long sawdust = core.getItemCount(ItemKey.of(new ItemStack(ptItem("sawdust"))));
            long logs = core.getItemCount(ItemKey.of(new ItemStack(Items.DARK_OAK_LOG)));
            if (!committed || planks != 6) {
                helper.fail("Sawmill craft did not commit: planks=" + planks
                        + " logs=" + logs + " sawdust=" + sawdust
                        + " craftable=" + menu.getCraftableCount()
                        + " committed=" + committed);
                return;
            }
            if (sawdust != 2) {
                helper.fail("Sawmill craft produced the wrong sawdust count: "
                        + sawdust + " (planks=" + planks + " logs=" + logs + ")");
                return;
            }
            if (logs != 0) {
                helper.fail("Sawmill craft did not consume the input log: logs=" + logs);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void sawmill_shortage_rolls_back_atomically(GameTestHelper helper) {
        withCore(helper, (level, core, player) -> {
            var holder = level.getRecipeManager()
                    .byKey(pt("sawmill/dark_oak_planks_from_log")).orElse(null);
            if (holder == null) {
                helper.fail("Missing representative Productive Trees recipe");
                return;
            }
            installStation(core, player, ptItem("sawmill"));
            addCoreTicks(core, 10_000);
            var menu = new CraftingTerminalMenu(
                    611, player.getInventory(), core);
            boolean committed = menu.handleRecipeRequest(
                    level, pt("sawmill/dark_oak_planks_from_log"), 1,
                    CraftingDestination.STORAGE, player);
            if (committed) {
                helper.fail("Sawmill craft committed without an input log");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void sawmill_requires_installed_station(GameTestHelper helper) {
        withCore(helper, (level, core, player) -> {
            var holder = level.getRecipeManager()
                    .byKey(pt("sawmill/dark_oak_planks_from_log")).orElse(null);
            if (holder == null) {
                helper.fail("Missing representative Productive Trees recipe");
                return;
            }
            seedItem(core, Items.DARK_OAK_LOG, 1);
            addCoreTicks(core, 10_000);
            var menu = new CraftingTerminalMenu(
                    612, player.getInventory(), core);
            boolean committed = menu.handleRecipeRequest(
                    level, pt("sawmill/dark_oak_planks_from_log"), 1,
                    CraftingDestination.STORAGE, player);
            if (committed) {
                helper.fail("Sawmill craft committed without the sawmill station");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void sawmill_destination_capacity_rolls_back_atomically(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            seedItem(core, Items.DARK_OAK_LOG, 1);
            for (Item filler : List.of(
                    Items.STONE, Items.DIRT, Items.SAND, Items.OAK_LOG,
                    Items.IRON_INGOT, Items.GOLD_INGOT, Items.COBBLESTONE,
                    Items.GRAVEL, Items.REDSTONE, Items.LAPIS_LAZULI)) {
                seedItem(core, filler, 1);
                if (core.getTypeCount() >= core.getTotalTypeSlots()) break;
            }
            if (core.getTypeCount() != core.getTotalTypeSlots()) {
                helper.fail("Sawmill destination fixture did not fill storage type capacity");
                return;
            }
            installStation(core, player, ptItem("sawmill"));
            addCoreTicks(core, 10_000);
            var menu = new CraftingTerminalMenu(
                    613, player.getInventory(), core);
            boolean committed = menu.handleRecipeRequest(
                    level, pt("sawmill/dark_oak_planks_from_log"), 1,
                    CraftingDestination.STORAGE, player);
            if (committed) {
                helper.fail("Sawmill craft committed into a full destination");
                return;
            }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.DARK_OAK_LOG))) != 1) {
                helper.fail("Sawmill rollback changed the input log");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void log_stripping_and_world_recipes_fail_closed(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                pt("pollination/balsam_fir"),
                pt("fruiting/alder"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) continue;
            types.add(holder.value().getType());
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe Productive Trees recipe type accepted "
                            + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void every_sawmill_recipe_is_supported(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var holders = manager.getAllRecipesFor(
                (net.minecraft.world.item.crafting.RecipeType)
                        cy.jdkdigital.productivetrees.registry
                                .TreeRegistrator.SAW_MILLLING_TYPE.get());
        if (holders.isEmpty()) {
            helper.fail("Audited Productive Trees sawmill recipe type is empty");
            return;
        }
        for (Object raw : holders) {
            var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
            if (!CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Sawmill recipe not supported: " + holder.id());
                return;
            }
        }
        helper.succeed();
    }

    private static final Set<ResourceLocation> SUPPORTED_MACHINES = Set.of(
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "productivetrees_sawmill"));

    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int STORAGE_PAGE_BUTTON = 14;

    private static void withCore(
            GameTestHelper helper,
            FixtureAssertion assertion
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
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

    private static void seedItem(StorageCoreBlockEntity core, Item item, int count) {
        if (core.insertResource(
                StorageResourceKey.item(new ItemStack(item), core.getLevel().registryAccess()),
                count, Action.EXECUTE) != count) {
            throw new IllegalStateException("Could not seed " + item);
        }
    }

    private static void installStation(
            StorageCoreBlockEntity core,
            Player player,
            Item stationItem
    ) {
        ItemStack station = new ItemStack(stationItem);
        var menu = new CraftingTerminalMenu(
                611, player.getInventory(), core);
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
        throw new IllegalStateException("Could not install Productive Trees sawmill station");
    }

    private static void addCoreTicks(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item ptItem(String path) {
        return BuiltInRegistries.ITEM.get(pt(path));
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(
                net.minecraft.server.level.ServerLevel level,
                StorageCoreBlockEntity core,
                Player player
        );
    }

    private static ResourceLocation pt(String path) {
        return ResourceLocation.fromNamespaceAndPath("productivetrees", path);
    }
}
