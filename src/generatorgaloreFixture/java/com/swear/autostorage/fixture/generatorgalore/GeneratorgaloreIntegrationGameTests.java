package com.swear.autostorage.fixture.generatorgalore;

import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import com.mojang.datafixers.util.Pair;
import cy.jdkdigital.generatorgalore.GeneratorGalore;
import cy.jdkdigital.generatorgalore.common.datamap.SolidFuelMap;
import cy.jdkdigital.generatorgalore.registry.GeneratorRegistry;
import cy.jdkdigital.generatorgalore.util.GeneratorObject;
import cy.jdkdigital.generatorgalore.util.GeneratorUtil;
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
import java.util.List;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GeneratorgaloreFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class GeneratorgaloreIntegrationGameTests {
    private GeneratorgaloreIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void present_mod_registers_no_unsafe_families(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                GeneratorgaloreIntegrationGameTests.class);
        if (!ModList.get().isLoaded("generatorgalore")
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("generatorgalore")
                                || id.getPath().startsWith("generatorgalore_"))) {
            helper.fail("Generator Galore unsafe machine contract was registered");
            return;
        }
        for (String generator : GENERATORS) {
            ResourceLocation descriptorId = descriptor(generator);
            if (!AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(descriptorId)) {
                helper.fail("Generator Galore descriptor missing " + descriptorId);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void transform_ingredient_shortage_is_atomic(GameTestHelper helper) {
        withCore(helper, (level, core, player) -> {
            var menu = transformMenu(core, player, new ItemStack(Items.COAL));
            selectTransform(menu, player, descriptor("copper"));
            installStation(core, player, ggItem("copper_generator"));
            menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                    .set(ItemStack.EMPTY);
            addCoreTicks(core, 100);
            long accrued = core.getStationWork(descriptor("copper"));
            boolean clicked = menu.clickMenuButton(player, 2);
            if (clicked
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0
                    || core.getStationWork(descriptor("copper")) != accrued
                    || accrued <= 0) {
                helper.fail(
                        "Generator Galore missing-ingredient transform was not an atomic no-op");
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
                    .filter(candidate -> candidate.id().equals(descriptor("copper")))
                    .findFirst()
                    .orElse(null);
            if (use == null) {
                helper.fail("Generator Galore copper transform use is missing");
                return;
            }
            selectTransform(menu, player, descriptor("copper"));
            installStation(core, player, ggItem("copper_generator"));
            addCoreTicks(core, 100);
            long accrued = core.getStationWork(descriptor("copper"));
            boolean clicked = menu.clickMenuButton(player, 2);
            if (clicked
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != Long.MAX_VALUE
                    || core.getStationWork(descriptor("copper")) != accrued
                    || accrued <= 0
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                            .getItem().is(Items.COAL)) {
                helper.fail(
                        "Generator Galore full destination transform was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void copper_generator_converts_coal_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "copper", new ItemStack(Items.COAL));
    }

    @GameTest(template = "craftingtests.platform")
    public static void gold_generator_converts_coal_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "gold", new ItemStack(Items.COAL));
    }

    @GameTest(template = "craftingtests.platform")
    public static void iron_generator_converts_coal_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "iron", new ItemStack(Items.COAL));
    }

    @GameTest(template = "craftingtests.platform")
    public static void diamond_generator_converts_coal_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "diamond", new ItemStack(Items.COAL));
    }

    @GameTest(template = "craftingtests.platform")
    public static void emerald_generator_converts_coal_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "emerald", new ItemStack(Items.COAL));
    }

    @GameTest(template = "craftingtests.platform")
    public static void netherite_generator_converts_coal_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "netherite", new ItemStack(Items.COAL));
    }

    @GameTest(template = "craftingtests.platform")
    public static void obsidian_generator_converts_coal_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "obsidian", new ItemStack(Items.COAL));
    }

    @GameTest(template = "craftingtests.platform")
    public static void netherstar_generator_converts_coal_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "netherstar", new ItemStack(Items.COAL));
    }

    @GameTest(template = "craftingtests.platform")
    public static void halitosis_generator_converts_dragon_breath_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "halitosis",
                new ItemStack(Items.DRAGON_BREATH));
    }

    @GameTest(template = "craftingtests.platform")
    public static void culinary_generator_converts_food_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "culinary", new ItemStack(Items.COOKED_BEEF));
    }

    @GameTest(template = "craftingtests.platform")
    public static void enchantment_generator_converts_enchanted_book_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
        enchantedBook.enchant(
                helper.getLevel().holderLookup(
                                net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(
                                net.minecraft.world.item.enchantment.Enchantments.SHARPNESS),
                1);
        withCore(helper, (level, core, player) -> {
            long[] expected = expectedFeAndWork("enchantment", enchantedBook);
            if (expected == null) {
                helper.fail("Generator Galore enchantment fuel has no rate for enchanted books");
                return;
            }
            var menu = transformMenu(core, player, enchantedBook);
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(descriptor("enchantment")))
                    .findFirst()
                    .orElse(null);
            if (use == null
                    || use.amountPerItem() != expected[0]
                    || use.stationWorkPerItem() != expected[1]
                    || use.retainedItems().size() != 1
                    || !use.retainedItems().getFirst().is(Items.BOOK)) {
                helper.fail("Generator Galore enchantment transform use is missing or wrong");
                return;
            }
            selectTransform(menu, player, descriptor("enchantment"));
            if (menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("Generator Galore generator must reject without an installed machine");
                return;
            }
            installStation(core, player, ggItem("enchantment_generator"));
            addCoreTicks(core, Math.toIntExact(expected[1]));
            if (!menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != expected[0]
                    || core.getStationWork(descriptor("enchantment")) != 0
                    || player.getInventory().countItem(Items.BOOK) != 1) {
                helper.fail(
                        "Generator Galore generator committed the wrong FE/work/input transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void ender_generator_converts_ender_pearl_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        assertGeneratorFuel(helper, "ender", new ItemStack(Items.ENDER_PEARL));
    }

    private static void assertGeneratorFuel(
            GameTestHelper helper,
            String generator,
            ItemStack fuel
    ) {
        withCore(helper, (level, core, player) -> {
            long[] expected = expectedFeAndWork(generator, fuel);
            if (expected == null) {
                helper.fail("Generator Galore fuel has no rate for "
                        + generator + ": " + fuel.getItem());
                return;
            }
            var menu = transformMenu(core, player, fuel);
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(descriptor(generator)))
                    .findFirst()
                    .orElse(null);
            if (use == null
                    || use.amountPerItem() != expected[0]
                    || use.stationWorkPerItem() != expected[1]) {
                helper.fail("Generator Galore " + generator
                        + " transform use is missing or wrong");
                return;
            }
            selectTransform(menu, player, descriptor(generator));
            if (menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("Generator Galore generator must reject without an installed machine");
                return;
            }
            installStation(core, player, ggItem(generator + "_generator"));
            addCoreTicks(core, Math.toIntExact(expected[1]));
            if (!menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != expected[0]
                    || core.getStationWork(descriptor(generator)) != 0) {
                helper.fail(
                        "Generator Galore generator committed the wrong FE/work/input transaction");
                return;
            }
            helper.succeed();
        });
    }

    private static long[] expectedFeAndWork(String generator, ItemStack fuel) {
        GeneratorObject gen = GeneratorRegistry.generators.get(
                ResourceLocation.fromNamespaceAndPath("generatorgalore", generator));
        if (gen == null) return null;
        long rate;
        long work;
        SolidFuelMap map = gen.getBlockSupplier().get().builtInRegistryHolder()
                .getData(GeneratorGalore.SOLID_FUEL_MAP);
        SolidFuelMap.SolidFuel matched = null;
        if (map != null) {
            for (SolidFuelMap.SolidFuel candidate : map.fuels()) {
                if (candidate.item().test(fuel)) {
                    matched = candidate;
                    break;
                }
            }
        }
        if (matched != null) {
            rate = matched.generationRate();
            work = (long) matched.burnTime() * (long) matched.consumptionRate();
        } else if (gen.getFuelType() == GeneratorUtil.FuelType.ENCHANTMENT) {
            Pair<Float, Integer> pair =
                    GeneratorUtil.calculateEnchantmentGenerationRate(gen, fuel);
            if (pair == null) return null;
            rate = Math.round(pair.getFirst());
            work = pair.getSecond();
        } else if (gen.getFuelType() == GeneratorUtil.FuelType.FOOD) {
            Pair<Float, Integer> pair =
                    GeneratorUtil.calculateFoodGenerationRate(gen, fuel);
            if (pair == null) return null;
            rate = Math.round(pair.getFirst());
            work = pair.getSecond();
        } else {
            int burnTime = fuel.getBurnTime(RecipeType.SMELTING);
            if (burnTime <= 0) return null;
            rate = Math.round(gen.getGenerationRate());
            work = Math.round(burnTime * gen.getConsumptionRate());
        }
        if (rate <= 0 || work <= 0) return null;
        return new long[]{Math.multiplyExact(rate, work), work};
    }

    private static final List<String> GENERATORS = List.of(
            "copper", "gold", "iron", "diamond", "emerald", "netherite",
            "obsidian", "netherstar", "halitosis", "culinary",
            "enchantment", "ender");
    private static final int TRANSFORM_PAGE_BUTTON = 15;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int STORAGE_PAGE_BUTTON = 14;

    private static ResourceLocation descriptor(String generator) {
        return ResourceLocation.fromNamespaceAndPath(
                AutoStorage.MODID,
                "generatorgalore_" + generator + "_generator");
    }

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
                990, player.getInventory(), core);
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
                991, player.getInventory(), core);
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
        throw new IllegalStateException("Could not install Generator Galore station");
    }

    private static void addCoreTicks(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
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

    private static Item ggItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("generatorgalore", path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Generator Galore item " + path);
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
