package com.swear.autostorage.fixture.draconicevolution;

import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.List;

@GameTestHolder(DraconicevolutionFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class DraconicevolutionIntegrationGameTests {
    private DraconicevolutionIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void present_mod_registers_no_unsafe_families(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), DraconicevolutionIntegrationGameTests.class);
        if (!ModList.get().isLoaded("draconicevolution")
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("draconicevolution")
                                || id.getPath().startsWith("draconicevolution_"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .filter(id -> id.getNamespace().equals("draconicevolution")
                                || id.getPath().startsWith("draconicevolution_"))
                        .anyMatch(id -> !id.equals(GENERATOR))) {
            helper.fail("Draconic Evolution unsafe recipe contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void vanilla_crafting_under_namespace_stays_supported(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = de("infused_obsidian");
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Draconic Evolution vanilla crafting must stay supported");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void fusion_awakened_draconium_block_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(de("awakened_draconium_block")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Draconic Evolution recipe awakened_draconium_block");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Draconic Evolution recipe was accepted: awakened_draconium_block");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void fusion_awakened_core_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(de("components/awakened_core")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Draconic Evolution recipe components/awakened_core");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Draconic Evolution recipe was accepted: components/awakened_core");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void fusion_crafting_injector_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(de("machines/wyvern_crafting_injector")).orElse(null);
        if (holder == null) {
            helper.fail(
                    "Missing representative Draconic Evolution recipe machines/wyvern_crafting_injector");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail(
                    "Unsafe Draconic Evolution recipe was accepted: machines/wyvern_crafting_injector");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_fusion_crafting_recipe_fails_closed(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                de("awakened_draconium_block"),
                de("components/awakened_core"),
                de("machines/wyvern_crafting_injector"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited Draconic Evolution recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        if (types.size() != 1) {
            helper.fail(
                    "Expected 1 unique audited Draconic Evolution fusion recipe type, but found "
                            + types.size());
            return;
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited Draconic Evolution recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe Draconic Evolution fusion recipe type accepted " + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static ResourceLocation de(String path) {
        return ResourceLocation.fromNamespaceAndPath("draconicevolution", path);
    }

    @GameTest(template = "craftingtests.platform")
    public static void generator_converts_fuel_to_fe_over_exact_work(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            var menu = transformMenu(core, player, new ItemStack(Items.COAL));
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(GENERATOR))
                    .findFirst()
                    .orElse(null);
            if (use == null) {
                helper.fail("Draconic generator transform use is missing");
                return;
            }
            selectTransform(menu, player, GENERATOR);
            if (menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("Draconic generator must reject without an installed generator");
                return;
            }
            installStation(core, player, deItem("generator"));
            tick(core, 1600);
            if (!menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 64_000L
                    || core.getStationWork(GENERATOR) != 0) {
                helper.fail("Draconic generator committed the wrong FE/work transaction");
                return;
            }
            helper.succeed();
        });
    }

    private static final int TRANSFORM_PAGE_BUTTON = 15;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final ResourceLocation GENERATOR =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "draconicevolution_generator");

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
                960, player.getInventory(), core);
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
                961, player.getInventory(), core);
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
        throw new IllegalStateException("Could not install Draconic Evolution station");
    }

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item deItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("draconicevolution", path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Draconic Evolution item " + path);
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
