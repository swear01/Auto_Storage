package com.swear.autostorage.fixture.create;

import com.swear.autostorage.MachineCategory;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.swear.autostorage.Action;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(CreateFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class CreateIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final Map<String, String> STATIONS = Map.of(
            "milling", "millstone",
            "crushing", "crushing_wheel",
            "cutting", "mechanical_saw",
            "filling", "spout",
            "emptying", "item_drain");

    private CreateIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void registers_only_audited_families_and_stations(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), CreateIntegrationGameTests.class);
        for (var entry : STATIONS.entrySet()) {
            if (!validStation(stationId(entry.getKey()), entry.getValue())
                    || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                    stationId(entry.getKey()))) {
                helper.fail("Missing audited Create family or station " + entry.getKey());
                return;
            }
        }
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(stationId("pressing"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(stationId("mixing"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(stationId("compacting"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(stationId("deploying"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                stationId("sequenced_assembly"))
                || supports(helper, createRecipe("pressing/cardboard"))
                || supports(helper, createRecipe("mixing/lava_from_cobble"))
                || supports(helper, createRecipe("deploying/cogwheel"))
                || supports(helper, createRecipe("sequenced_assembly/precision_mechanism"))
                || supports(helper, createRecipe("milling/short_grass"))
                || supports(helper, fixtureRecipe("zero_time_cutting"))) {
            helper.fail("Create compatibility did not fail closed outside the audited slice");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void milling_returns_item_remainder_and_consumes_duration(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.LAVA_BUCKET, 1);
            installStation(context, "millstone");
            tick(context.core(), 10);
            if (!craft(context, fixtureRecipe("remainder_milling"))
                    || itemCount(context.core(), Items.LAVA_BUCKET) != 0
                    || itemCount(context.core(), Items.BUCKET) != 1
                    || itemCount(context.core(), Items.OBSIDIAN) != 1
                    || context.core().getStationWork(stationId("milling")) != 0) {
                helper.fail("Create Milling transaction or remainder was wrong");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void crushing_merges_deterministic_duplicate_outputs(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.STONE, 1);
            installStation(context, "crushing_wheel");
            tick(context.core(), 40);
            if (!craft(context, fixtureRecipe("duplicate_crushing"))
                    || itemCount(context.core(), Items.STONE) != 0
                    || itemCount(context.core(), Items.GRAVEL) != 5
                    || context.core().getStationWork(stationId("crushing")) != 0) {
                helper.fail("Create Crushing did not merge deterministic outputs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void crushing_preserves_each_distinct_deterministic_output(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.STONE, 1);
            installStation(context, "crushing_wheel");
            tick(context.core(), 40);
            if (!craft(context, fixtureRecipe("distinct_crushing"))
                    || itemCount(context.core(), Items.STONE) != 0
                    || itemCount(context.core(), Items.GRAVEL) != 2
                    || itemCount(context.core(), Items.FLINT) != 1
                    || context.core().getStationWork(stationId("crushing")) != 0) {
                helper.fail("Create Crushing lost a distinct deterministic output");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void cutting_uses_recipe_duration_and_output_count(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), createItem("andesite_alloy"), 1);
            installStation(context, "mechanical_saw");
            tick(context.core(), 200);
            if (!craft(context, createRecipe("cutting/andesite_alloy"))
                    || itemCount(context.core(), createItem("andesite_alloy")) != 0
                    || itemCount(context.core(), createItem("shaft")) != 6
                    || context.core().getStationWork(stationId("cutting")) != 0) {
                helper.fail("Create Cutting duration or output count was wrong");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void installed_station_and_work_survive_core_reload(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            ResourceLocation descriptorId = stationId("milling");
            installStation(context, "millstone");
            tick(context.core(), 17);
            CompoundTag saved = context.core().saveWithoutMetadata(
                    context.level().registryAccess());
            BlockPos pos = context.core().getBlockPos();
            context.level().removeBlockEntity(pos);
            var reloaded = new StorageCoreBlockEntity(
                    pos, AutoStorage.STORAGE_CORE.get().defaultBlockState());
            reloaded.loadWithComponents(saved, context.level().registryAccess());
            context.level().setBlockEntity(reloaded);
            helper.runAfterDelay(2, () -> {
                reloaded.rebuildNetwork(context.level());
                var menu = new CraftingTerminalMenu(
                        916, context.player().getInventory(), reloaded);
                menu.clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
                ItemStack installed = menu.getSlot(
                        CraftingTerminalMenu.MACHINE_SLOT_START
                                + MachineEnergyTable.findSlot(descriptorId)).getItem();
                long stationWork = reloaded.getStationWork(descriptorId);
                if (!installed.is(createItem("millstone"))
                        || installed.getCount() != 1 || stationWork < 17) {
                    helper.fail("Create station or work did not survive Core reload: station="
                            + installed + ", work=" + stationWork);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void filling_consumes_item_fluid_and_fixed_work(GameTestHelper helper) {
        withCore(helper, context -> {
            StorageResourceKey honey = fluidKey(context, createId("honey"));
            seedItem(context.core(), Items.GLASS_BOTTLE, 1);
            seedResource(context.core(), honey, 250);
            installStation(context, "spout");
            tick(context.core(), SpoutBlockEntity.FILLING_TIME);
            if (!craft(context, createRecipe("filling/honey_bottle"))
                    || itemCount(context.core(), Items.GLASS_BOTTLE) != 0
                    || itemCount(context.core(), Items.HONEY_BOTTLE) != 1
                    || context.core().getResourceAmount(honey) != 0
                    || context.core().getStationWork(stationId("filling")) != 0) {
                helper.fail("Create Filling transaction was wrong");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void emptying_outputs_item_fluid_and_consumes_fixed_work(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            StorageResourceKey honey = fluidKey(context, createId("honey"));
            seedItem(context.core(), Items.HONEY_BOTTLE, 1);
            installStation(context, "item_drain");
            tick(context.core(), ItemDrainBlockEntity.FILLING_TIME);
            if (!craft(context, createRecipe("emptying/honey_bottle"))
                    || itemCount(context.core(), Items.HONEY_BOTTLE) != 0
                    || itemCount(context.core(), Items.GLASS_BOTTLE) != 1
                    || context.core().getResourceAmount(honey) != 250
                    || context.core().getStationWork(stationId("emptying")) != 0) {
                helper.fail("Create Emptying transaction was wrong");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_work_rolls_back_crushing(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.STONE, 1);
            installStation(context, "crushing_wheel");
            tick(context.core(), 39);
            if (craft(context, fixtureRecipe("duplicate_crushing"))
                    || itemCount(context.core(), Items.STONE) != 1
                    || itemCount(context.core(), Items.GRAVEL) != 0
                    || context.core().getStationWork(stationId("crushing")) != 39) {
                helper.fail("Create Crushing work shortage partially mutated storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_fluid_rolls_back_filling(GameTestHelper helper) {
        withCore(helper, context -> {
            StorageResourceKey honey = fluidKey(context, createId("honey"));
            seedItem(context.core(), Items.GLASS_BOTTLE, 1);
            seedResource(context.core(), honey, 249);
            installStation(context, "spout");
            tick(context.core(), SpoutBlockEntity.FILLING_TIME);
            if (craft(context, createRecipe("filling/honey_bottle"))
                    || itemCount(context.core(), Items.GLASS_BOTTLE) != 1
                    || itemCount(context.core(), Items.HONEY_BOTTLE) != 0
                    || context.core().getResourceAmount(honey) != 249
                    || context.core().getStationWork(stationId("filling"))
                    != SpoutBlockEntity.FILLING_TIME) {
                helper.fail("Create Filling fluid shortage partially mutated storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void output_overflow_rolls_back_emptying(GameTestHelper helper) {
        withCore(helper, context -> {
            StorageResourceKey honey = fluidKey(context, createId("honey"));
            seedItem(context.core(), Items.HONEY_BOTTLE, 1);
            seedResource(context.core(), honey, Long.MAX_VALUE);
            installStation(context, "item_drain");
            tick(context.core(), ItemDrainBlockEntity.FILLING_TIME);
            if (craft(context, createRecipe("emptying/honey_bottle"))
                    || itemCount(context.core(), Items.HONEY_BOTTLE) != 1
                    || itemCount(context.core(), Items.GLASS_BOTTLE) != 0
                    || context.core().getResourceAmount(honey) != Long.MAX_VALUE
                    || context.core().getStationWork(stationId("emptying"))
                    != ItemDrainBlockEntity.FILLING_TIME) {
                helper.fail("Create Emptying overflow partially mutated storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void chance_output_rejects_without_mutation(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.SHORT_GRASS, 1);
            installStation(context, "millstone");
            tick(context.core(), 50);
            if (craft(context, createRecipe("milling/short_grass"))
                    || itemCount(context.core(), Items.SHORT_GRASS) != 1
                    || itemCount(context.core(), Items.WHEAT_SEEDS) != 0
                    || context.core().getStationWork(stationId("milling")) != 50) {
                helper.fail("Create chance output did not fail closed");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void exact_five_families_are_discoverable(GameTestHelper helper) {
        if (!supports(helper, fixtureRecipe("remainder_milling"))
                || !supports(helper, fixtureRecipe("duplicate_crushing"))
                || !supports(helper, createRecipe("cutting/andesite_alloy"))
                || !supports(helper, createRecipe("filling/honey_bottle"))
                || !supports(helper, createRecipe("emptying/honey_bottle"))) {
            helper.fail("An audited deterministic Create family was not discoverable");
            return;
        }
        helper.succeed();
    }

    private static boolean validStation(ResourceLocation stationId, String itemPath) {
        MachineDescriptor descriptor = MachineEnergyTable.get(stationId);
        ItemStack station = new ItemStack(createItem(itemPath));
        return descriptor != null
                && descriptor.category() == MachineCategory.PROCESS
                && descriptor.maxInstalledCount() == MachineDescriptorApi.MAX_INSTALLED_COUNT
                && descriptor.energyType() == null
                && descriptor.variants().size() == 1
                && descriptor.accepts(station)
                && descriptor.rateFor(station).orElseThrow().equals(MachineWorkRate.ONE);
    }

    private static boolean supports(GameTestHelper helper, ResourceLocation recipeId) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null) throw new IllegalStateException("Missing recipe " + recipeId);
        return CraftingTerminalMenu.supportsRecipeHolder(holder);
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
            player.setPos(
                    corePos.getX() + 0.5,
                    corePos.getY() + 0.5,
                    corePos.getZ() + 0.5);
            assertion.run(new FixtureContext(level, core, player));
        });
    }

    private static void installStation(FixtureContext context, String itemPath) {
        ItemStack station = new ItemStack(createItem(itemPath));
        var menu = new CraftingTerminalMenu(
                914, context.player().getInventory(), context.core());
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
            return;
        }
        throw new IllegalStateException("Could not install Create station " + itemPath);
    }

    private static boolean craft(FixtureContext context, ResourceLocation recipeId) {
        var menu = new CraftingTerminalMenu(
                915, context.player().getInventory(), context.core());
        if (!menu.handleRecipeRequest(
                context.level(), recipeId, 1, CraftingDestination.NONE, context.player())) {
            return false;
        }
        if (menu.computeCraftPreview(context.core(), context.player()).craftable() < 1) {
            return false;
        }
        return menu.handleRecipeRequest(
                context.level(), recipeId, 1,
                CraftingDestination.STORAGE, context.player());
    }

    private static void seedItem(StorageCoreBlockEntity core, Item item, int amount) {
        ItemStack stack = new ItemStack(item, amount);
        if (core.insertItem(stack) != amount) {
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

    private static StorageResourceKey fluidKey(
            FixtureContext context,
            ResourceLocation fluidId
    ) {
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid == null) throw new IllegalStateException("Missing fluid " + fluidId);
        return StorageResourceKey.fluid(
                new FluidStack(fluid, 1), context.level().registryAccess());
    }

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item createItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(createId(path));
        if (item == Items.AIR) throw new IllegalStateException("Missing Create item " + path);
        return item;
    }

    private static ResourceLocation createId(String path) {
        return ResourceLocation.fromNamespaceAndPath("create", path);
    }

    private static ResourceLocation createRecipe(String path) {
        return createId(path);
    }

    private static ResourceLocation fixtureRecipe(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateFixtureMod.MODID, path);
    }

    private static ResourceLocation stationId(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                AutoStorage.MODID, "create_" + path);
    }

    private record FixtureContext(
            net.minecraft.server.level.ServerLevel level,
            StorageCoreBlockEntity core,
            net.minecraft.world.entity.player.Player player
    ) {
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(FixtureContext context);
    }
}
