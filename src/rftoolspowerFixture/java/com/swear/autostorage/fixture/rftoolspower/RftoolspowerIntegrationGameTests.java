package com.swear.autostorage.fixture.rftoolspower;

import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import mcjty.rftoolspower.modules.generator.CoalGeneratorConfig;
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

@GameTestHolder(RftoolspowerFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class RftoolspowerIntegrationGameTests {
    private RftoolspowerIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void present_mod_registers_no_unsafe_families(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                RftoolspowerIntegrationGameTests.class);
        if (!ModList.get().isLoaded("rftoolspower")
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("rftoolspower")
                                || id.getPath().startsWith("rftoolspower_"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .filter(id -> id.getNamespace().equals("rftoolspower")
                                || id.getPath().startsWith("rftoolspower_"))
                        .anyMatch(id -> !id.equals(GENERATOR))) {
            helper.fail("RFTools Power unsafe machine contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void vanilla_crafting_under_namespace_stays_supported(
            GameTestHelper helper
    ) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(rf("coalgenerator")).orElse(null);
        if (holder == null
                || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("RFTools Power vanilla crafting must stay supported");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void transform_ingredient_shortage_is_atomic(GameTestHelper helper) {
        withCore(helper, (level, core, player) -> {
            var menu = transformMenu(core, player, new ItemStack(Items.COAL));
            selectTransform(menu, player, GENERATOR);
            installStation(core, player, rfItem("coalgenerator"));
            menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                    .set(ItemStack.EMPTY);
            tick(core, CoalGeneratorConfig.TICKSPERCOAL.get());
            long accrued = core.getStationWork(GENERATOR);
            boolean clicked = menu.clickMenuButton(player, 2);
            if (clicked
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0
                    || core.getStationWork(GENERATOR) != accrued
                    || accrued <= 0) {
                helper.fail(
                        "RFTools Power missing-ingredient transform was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void transform_destination_overflow_is_atomic(GameTestHelper helper) {
        withCore(helper, (level, core, player) -> {
            seedResource(core, StorageResourceKey.neoforgeEnergy(), Long.MAX_VALUE);
            var menu = transformMenu(core, player, new ItemStack(Items.COAL));
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(GENERATOR))
                    .findFirst()
                    .orElse(null);
            if (use == null) {
                helper.fail("RFTools Power generator transform use is missing");
                return;
            }
            selectTransform(menu, player, GENERATOR);
            installStation(core, player, rfItem("coalgenerator"));
            tick(core, CoalGeneratorConfig.TICKSPERCOAL.get());
            long accrued = core.getStationWork(GENERATOR);
            boolean clicked = menu.clickMenuButton(player, 2);
            if (clicked
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != Long.MAX_VALUE
                    || core.getStationWork(GENERATOR) != accrued
                    || accrued <= 0
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                            .getItem().is(Items.COAL)) {
                helper.fail(
                        "RFTools Power full destination transform was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void coal_generator_converts_fuel_to_fe_over_exact_work(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            var menu = transformMenu(core, player, new ItemStack(Items.COAL));
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(GENERATOR))
                    .findFirst()
                    .orElse(null);
            long work = CoalGeneratorConfig.TICKSPERCOAL.get();
            long expected = work * CoalGeneratorConfig.RFPERTICK.get();
            if (use == null
                    || use.amountPerItem() != expected
                    || use.stationWorkPerItem() != work) {
                helper.fail("RFTools Power generator transform use is missing");
                return;
            }
            selectTransform(menu, player, GENERATOR);
            if (menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("RFTools Power generator must reject without an installed generator");
                return;
            }
            installStation(core, player, rfItem("coalgenerator"));
            tick(core, Math.toIntExact(work));
            if (!menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != expected
                    || core.getStationWork(GENERATOR) != 0) {
                helper.fail(
                        "RFTools Power generator committed the wrong FE/work/input transaction");
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
                    AutoStorage.MODID, "rftoolspower_coal_generator");

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
                970, player.getInventory(), core);
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
                971, player.getInventory(), core);
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
        throw new IllegalStateException("Could not install RFTools Power station");
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

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static ResourceLocation rf(String path) {
        return ResourceLocation.fromNamespaceAndPath("rftoolspower", path);
    }

    private static Item rfItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(rf(path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing RFTools Power item " + path);
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
