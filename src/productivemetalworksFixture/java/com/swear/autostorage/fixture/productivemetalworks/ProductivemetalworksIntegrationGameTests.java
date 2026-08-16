package com.swear.autostorage.fixture.productivemetalworks;

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

@GameTestHolder(ProductivemetalworksFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ProductivemetalworksIntegrationGameTests {
    private ProductivemetalworksIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void foundry_and_casting_contracts_registered(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                ProductivemetalworksIntegrationGameTests.class);
        if (!ModList.get().isLoaded("productivemetalworks")) {
            helper.fail("Productive Metalworks mod is not loaded");
            return;
        }
        if (AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .filter(id -> id.getNamespace().equals("productivemetalworks")
                                || id.getPath().startsWith("productivemetalworks_"))
                        .anyMatch(id -> !SUPPORTED_MACHINES.contains(id))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .filter(id -> id.getNamespace().equals("productivemetalworks")
                                || id.getPath().startsWith("productivemetalworks_"))
                        .anyMatch(id -> !SUPPORTED_MACHINES.contains(id))) {
            helper.fail("Productive Metalworks unsafe machine contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void item_melting_recipes_are_supported(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pmw("melting/ancient_debris")).orElse(null);
        if (holder == null
                || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Melting recipe must be supported: melting/ancient_debris");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void item_melting_converts_clock_to_molten_fluids(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            var holder = level.getRecipeManager()
                    .byKey(pmw("melting/clock")).orElse(null);
            if (holder == null
                    || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Melting recipe must be supported: melting/clock");
                return;
            }
            seedItem(core, Items.CLOCK, 1);
            installStation(core, player, pmwItem("gray_foundry_controller"));
            addCoreTicks(core, 10_000);
            var menu = new CraftingTerminalMenu(
                    610, player.getInventory(), core);
            boolean requested = menu.handleRecipeRequest(
                    level, pmw("melting/clock"), 1,
                    CraftingDestination.NONE, player);
            boolean committed = requested
                    && menu.computeCraftPreview(core, player).craftable() >= 1
                    && menu.handleRecipeRequest(
                            level, pmw("melting/clock"), 1,
                            CraftingDestination.STORAGE, player);
            long gold = core.getResourceAmount(fluid(core, "molten_gold"));
            long redstone = core.getResourceAmount(fluid(core, "molten_redstone"));
            long clocks = core.getItemCount(ItemKey.of(new ItemStack(Items.CLOCK)));
            if (!committed || gold != 360) {
                helper.fail("Melting craft did not commit: gold=" + gold
                        + " redstone=" + redstone + " clocks=" + clocks
                        + " craftable=" + menu.getCraftableCount()
                        + " committed=" + committed);
                return;
            }
            if (redstone != 100) {
                helper.fail("Melting craft produced the wrong secondary fluid: "
                        + redstone + " (gold=" + gold + " clocks=" + clocks + ")");
                return;
            }
            if (clocks != 0) {
                helper.fail("Melting craft did not consume the clock: clocks=" + clocks);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void basin_casting_recipes_are_supported(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pmw("casting/blue_foundry_capacitor")).orElse(null);
        if (holder == null
                || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Basin casting recipe must be supported: casting/blue_foundry_capacitor");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void basin_casting_converts_fluid_and_cast_to_item(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            var holder = level.getRecipeManager()
                    .byKey(pmw("casting/blue_foundry_capacitor")).orElse(null);
            if (holder == null
                    || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Basin casting recipe must be supported: casting/blue_foundry_capacitor");
                return;
            }
            seedItem(core, pmwItem("blue_fire_bricks"), 1);
            seedResource(core, fluid(core, "molten_redstone"), 1000);
            installStation(core, player, pmwItem("casting_basin"));
            addCoreTicks(core, 10_000);
            var menu = new CraftingTerminalMenu(
                    611, player.getInventory(), core);
            boolean requested = menu.handleRecipeRequest(
                    level, pmw("casting/blue_foundry_capacitor"), 1,
                    CraftingDestination.NONE, player);
            boolean committed = requested
                    && menu.computeCraftPreview(core, player).craftable() >= 1
                    && menu.handleRecipeRequest(
                            level, pmw("casting/blue_foundry_capacitor"), 1,
                            CraftingDestination.STORAGE, player);
            long capacitor = core.getItemCount(ItemKey.of(
                    new ItemStack(pmwItem("blue_foundry_capacitor"))));
            long bricks = core.getItemCount(ItemKey.of(
                    new ItemStack(pmwItem("blue_fire_bricks"))));
            long fluidLeft = core.getResourceAmount(fluid(core, "molten_redstone"));
            if (!committed || capacitor != 1) {
                helper.fail("Basin casting craft did not commit: capacitor="
                        + capacitor + " bricks=" + bricks
                        + " fluidLeft=" + fluidLeft
                        + " craftable=" + menu.getCraftableCount()
                        + " committed=" + committed);
                return;
            }
            if (bricks != 0 || fluidLeft != 0) {
                helper.fail("Basin casting did not consume cast and fluid: bricks="
                        + bricks + " fluidLeft=" + fluidLeft);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void mold_casting_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pmw("casting/blaze_rod")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Metalworks recipe casting/blaze_rod");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Metalworks mold recipe was accepted: casting/blaze_rod");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void fluid_alloying_and_entity_melting_fail_closed(
            GameTestHelper helper
    ) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                pmw("alloying/molten_obsidian"))) {
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
                    helper.fail("Unsafe Productive Metalworks recipe type accepted "
                            + holder.id());
                    return;
                }
            }
        }
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getPath().contains("entity_melting"))) {
            helper.fail("Productive Metalworks entity melting boundary changed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_melting_and_consuming_casting_recipe_is_supported(
            GameTestHelper helper
    ) {
        var manager = helper.getLevel().getRecipeManager();
        int melting = 0;
        int casting = 0;
        int mold = 0;
        var meltingType = (net.minecraft.world.item.crafting.RecipeType)
                cy.jdkdigital.productivemetalworks.registry
                        .MetalworksRegistrator.ITEM_MELTING_TYPE.get();
        var itemCastingType = (net.minecraft.world.item.crafting.RecipeType)
                cy.jdkdigital.productivemetalworks.registry
                        .MetalworksRegistrator.ITEM_CASTING_TYPE.get();
        var blockCastingType = (net.minecraft.world.item.crafting.RecipeType)
                cy.jdkdigital.productivemetalworks.registry
                        .MetalworksRegistrator.BLOCK_CASTING_TYPE.get();
        for (var holder : manager.getRecipes()) {
            if (!holder.id().getNamespace().equals("productivemetalworks")) continue;
            var type = holder.value().getType();
            if (type.equals(meltingType)) {
                melting++;
                if (!CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Melting recipe not supported: " + holder.id());
                    return;
                }
            } else if (type.equals(itemCastingType)
                    || type.equals(blockCastingType)) {
                var recipe = (cy.jdkdigital.productivemetalworks.recipe
                        .ItemCastingRecipe) holder.value();
                if (recipe.consumeCast) {
                    casting++;
                    if (!CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                        helper.fail("Casting recipe not supported: " + holder.id());
                        return;
                    }
                } else {
                    mold++;
                    if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                        helper.fail("Mold casting recipe was accepted: " + holder.id());
                        return;
                    }
                }
            }
        }
        if (melting < 40 || casting < 15 || mold < 5) {
            helper.fail("Productive Metalworks recipe counts drifted: melting="
                    + melting + " casting=" + casting + " mold=" + mold);
            return;
        }
        helper.succeed();
    }

    private static final Set<ResourceLocation> SUPPORTED_MACHINES = Set.of(
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "productivemetalworks_foundry"),
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "productivemetalworks_casting_table"),
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID,
                    "productivemetalworks_casting_table_basin"));

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

    private static void seedResource(
            StorageCoreBlockEntity core,
            StorageResourceKey key,
            long amount
    ) {
        if (core.insertResource(key, amount, Action.EXECUTE) != amount) {
            throw new IllegalStateException("Could not seed " + key);
        }
    }

    private static StorageResourceKey fluid(
            StorageCoreBlockEntity core,
            String path
    ) {
        net.minecraft.world.level.material.Fluid fluid =
                BuiltInRegistries.FLUID.get(
                        ResourceLocation.fromNamespaceAndPath(
                                "productivemetalworks", path));
        return StorageResourceKey.fluid(
                new net.neoforged.neoforge.fluids.FluidStack(fluid, 1),
                core.getLevel().registryAccess());
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
        throw new IllegalStateException("Could not install Productive Metalworks station");
    }

    private static void addCoreTicks(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item pmwItem(String path) {
        return BuiltInRegistries.ITEM.get(pmw(path));
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(
                net.minecraft.server.level.ServerLevel level,
                StorageCoreBlockEntity core,
                Player player
        );
    }

    private static ResourceLocation pmw(String path) {
        return ResourceLocation.fromNamespaceAndPath("productivemetalworks", path);
    }
}
