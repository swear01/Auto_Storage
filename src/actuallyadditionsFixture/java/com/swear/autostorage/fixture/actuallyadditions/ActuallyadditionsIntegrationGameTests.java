package com.swear.autostorage.fixture.actuallyadditions;

import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import de.ellpeck.actuallyadditions.mod.crafting.PressingRecipe;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityCanolaPress;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityCrusher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ActuallyadditionsFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ActuallyadditionsIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final ResourceLocation CRUSHING =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "actuallyadditions_crushing");
    private static final ResourceLocation PRESSING =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "actuallyadditions_pressing");
    private static final ResourceLocation FERMENTING =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "actuallyadditions_fermenting");
    private static final ResourceLocation EMPOWERING =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "actuallyadditions_empowering");
    private static final ResourceLocation LASER =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "actuallyadditions_laser");
    private static final ResourceLocation BLAZE_ROD = aaRecipe("crushing/blaze_rod");
    private static final ResourceLocation IRON_ORE = aaRecipe("crushing/iron_ore");
    private static final ResourceLocation GUARANTEED_SECONDARY = fixtureRecipe(
            "guaranteed_secondary");
    private static final ResourceLocation CANOLA_PRESS = aaRecipe("pressing/canola");
    private static final ResourceLocation REFINED_CANOLA = aaRecipe("fermenting/refined_canola");
    private static final ResourceLocation LASER_DIAMOND =
            aaRecipe("laser/crystalize_diamatine_crystal");
    private static final long CRUSHER_COST =
            Math.multiplyExact((long) TileEntityCrusher.ENERGY_USE, 100L);
    private static final long PRESSING_COST =
            Math.multiplyExact((long) TileEntityCanolaPress.ENERGY_USE, 30L);

    private ActuallyadditionsIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void registration_and_excluded_families(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                ActuallyadditionsIntegrationGameTests.class);
        MachineDescriptor crusher = MachineEnergyTable.get(CRUSHING);
        MachineDescriptor press = MachineEnergyTable.get(PRESSING);
        MachineDescriptor barrel = MachineEnergyTable.get(FERMENTING);
        ItemStack crusherItem = new ItemStack(aaItem("crusher"));
        ItemStack pressItem = new ItemStack(aaItem("canola_press"));
        ItemStack barrelItem = new ItemStack(aaItem("fermenting_barrel"));
        if (crusher == null
                || press == null
                || barrel == null
                || crusher.category() != MachineCategory.PROCESS
                || press.category() != MachineCategory.PROCESS
                || barrel.category() != MachineCategory.PROCESS
                || crusher.maxInstalledCount() != MachineDescriptorApi.MAX_INSTALLED_COUNT
                || !crusher.accepts(crusherItem)
                || !press.accepts(pressItem)
                || !barrel.accepts(barrelItem)
                || !crusher.rateFor(crusherItem).orElseThrow().equals(
                MachineWorkRate.of(TileEntityCrusher.ENERGY_USE, 1))
                || !press.rateFor(pressItem).orElseThrow().equals(
                MachineWorkRate.of(TileEntityCanolaPress.ENERGY_USE, 1))
                || !barrel.rateFor(barrelItem).orElseThrow().equals(MachineWorkRate.of(1, 1))
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(CRUSHING)
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(PRESSING)
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(FERMENTING)
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(EMPOWERING)
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(LASER)
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(EMPOWERING)
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(LASER)
                || !supports(helper, BLAZE_ROD)
                || supports(helper, IRON_ORE)
                || supports(helper, LASER_DIAMOND)
                || !supports(helper, CANOLA_PRESS)
                || !supports(helper, REFINED_CANOLA)) {
            helper.fail(
                    "Actually Additions registration or excluded family boundary was incorrect");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void crushing_blaze_rod_consumes_exact_costs(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.BLAZE_ROD, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), CRUSHER_COST);
            installStation(context, "crusher", CRUSHING);
            tick(context.core(), 100);
            if (!craft(context, BLAZE_ROD)
                    || itemCount(context.core(), Items.BLAZE_ROD) != 0
                    || itemCount(context.core(), Items.BLAZE_POWDER) != 3
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(CRUSHING) != 0) {
                helper.fail("Actually Additions crushing did not consume exact blaze-rod costs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void crushing_chance_secondary_stays_unsupported(GameTestHelper helper) {
        if (supports(helper, IRON_ORE)) {
            helper.fail("Chance Crushing recipe was accepted: crushing/iron_ore");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void bucketless_fluid_recipe_stays_unsupported(GameTestHelper helper) {
        PressingRecipe recipe = new PressingRecipe(
                Ingredient.of(Items.STONE),
                new FluidStack(ActuallyadditionsFixtureMod.BUCKETLESS_FLUID.get(), 80));
        if (CraftingTerminalMenu.supportsRecipeContract(recipe)) {
            helper.fail("Bucketless fluid recipe was accepted without a presentation item");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void crushing_guaranteed_secondary_emits_both_outputs(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.COBBLESTONE, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), CRUSHER_COST);
            installStation(context, "crusher", CRUSHING);
            tick(context.core(), 100);
            if (!craft(context, GUARANTEED_SECONDARY)
                    || itemCount(context.core(), Items.COBBLESTONE) != 0
                    || itemCount(context.core(), Items.DIAMOND) != 2
                    || itemCount(context.core(), Items.EMERALD) != 3
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(CRUSHING) != 0) {
                helper.fail("Guaranteed Crushing secondary did not emit both exact outputs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void crushing_guaranteed_secondary_capacity_is_atomic_noop(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.COBBLESTONE, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), CRUSHER_COST);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(Items.EMERALD),
                            context.level().registryAccess()),
                    Long.MAX_VALUE - 2);
            installStation(context, "crusher", CRUSHING);
            tick(context.core(), 100);
            if (craft(context, GUARANTEED_SECONDARY)
                    || itemCount(context.core(), Items.COBBLESTONE) != 1
                    || itemCount(context.core(), Items.DIAMOND) != 0
                    || itemCount(context.core(), Items.EMERALD) != Long.MAX_VALUE - 2
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != CRUSHER_COST
                    || context.core().getStationWork(CRUSHING) != CRUSHER_COST) {
                helper.fail("Guaranteed Crushing secondary capacity was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void pressing_canola_emits_exact_oil(GameTestHelper helper) {
        withCore(helper, context -> {
            FluidStack oil = fluid("canola_oil", 80);
            seedItem(context.core(), aaItem("canola"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), PRESSING_COST);
            installStation(context, "canola_press", PRESSING);
            tick(context.core(), 30);
            if (!craft(context, CANOLA_PRESS)
                    || itemCount(context.core(), aaItem("canola")) != 0
                    || context.core().getResourceAmount(fluidKey(oil, context)) != 80
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(PRESSING) != 0) {
                helper.fail("Actually Additions pressing did not emit exact canola oil");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void fermenting_refined_canola_consumes_exact_fluid(GameTestHelper helper) {
        withCore(helper, context -> {
            FluidStack input = fluid("canola_oil", 80);
            FluidStack output = fluid("refined_canola_oil", 80);
            seedResource(context.core(), fluidKey(input, context), 80);
            installStation(context, "fermenting_barrel", FERMENTING);
            tick(context.core(), 100);
            if (!craft(context, REFINED_CANOLA)
                    || context.core().getResourceAmount(fluidKey(input, context)) != 0
                    || context.core().getResourceAmount(fluidKey(output, context)) != 80
                    || context.core().getStationWork(FERMENTING) != 0) {
                helper.fail("Actually Additions fermenting did not convert exact canola oil");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_ingredient_is_atomic_noop(GameTestHelper helper) {
        withCore(helper, context -> {
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), CRUSHER_COST);
            installStation(context, "crusher", CRUSHING);
            tick(context.core(), 100);
            if (craft(context, BLAZE_ROD)
                    || itemCount(context.core(), Items.BLAZE_POWDER) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != CRUSHER_COST
                    || context.core().getStationWork(CRUSHING) != CRUSHER_COST) {
                helper.fail(
                        "Actually Additions missing-ingredient transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_fe_is_atomic_noop(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.BLAZE_ROD, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), CRUSHER_COST - 1);
            installStation(context, "crusher", CRUSHING);
            tick(context.core(), 100);
            if (craft(context, BLAZE_ROD)
                    || itemCount(context.core(), Items.BLAZE_ROD) != 1
                    || itemCount(context.core(), Items.BLAZE_POWDER) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != CRUSHER_COST - 1
                    || context.core().getStationWork(CRUSHING) != CRUSHER_COST) {
                helper.fail("Actually Additions insufficient-FE transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_work_is_atomic_noop(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.BLAZE_ROD, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), CRUSHER_COST);
            installStation(context, "crusher", CRUSHING);
            tick(context.core(), 99);
            if (craft(context, BLAZE_ROD)
                    || itemCount(context.core(), Items.BLAZE_ROD) != 1
                    || itemCount(context.core(), Items.BLAZE_POWDER) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != CRUSHER_COST
                    || context.core().getStationWork(CRUSHING) != CRUSHER_COST - TileEntityCrusher.ENERGY_USE) {
                helper.fail(
                        "Actually Additions insufficient-work transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void full_destination_is_atomic_noop(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.BLAZE_ROD, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), CRUSHER_COST);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(Items.BLAZE_POWDER),
                            context.level().registryAccess()),
                    Long.MAX_VALUE - 2);
            installStation(context, "crusher", CRUSHING);
            tick(context.core(), 100);
            if (craft(context, BLAZE_ROD)
                    || itemCount(context.core(), Items.BLAZE_ROD) != 1
                    || itemCount(context.core(), Items.BLAZE_POWDER) != Long.MAX_VALUE - 2
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != CRUSHER_COST
                    || context.core().getStationWork(CRUSHING) != CRUSHER_COST) {
                helper.fail(
                        "Actually Additions full destination transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void long_overflow_destination_is_atomic_noop(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.BLAZE_ROD, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), CRUSHER_COST);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(Items.BLAZE_POWDER),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            installStation(context, "crusher", CRUSHING);
            tick(context.core(), 100);
            if (craft(context, BLAZE_ROD)
                    || itemCount(context.core(), Items.BLAZE_ROD) != 1
                    || itemCount(context.core(), Items.BLAZE_POWDER) != Long.MAX_VALUE
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != CRUSHER_COST
                    || context.core().getStationWork(CRUSHING) != CRUSHER_COST) {
                helper.fail(
                        "Actually Additions Long.MAX_VALUE destination was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    private static boolean supports(GameTestHelper helper, ResourceLocation recipeId) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
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

    private static void installStation(
            FixtureContext context,
            String stationPath,
            ResourceLocation descriptorId
    ) {
        ItemStack station = new ItemStack(aaItem(stationPath));
        var menu = new CraftingTerminalMenu(
                930, context.player().getInventory(), context.core());
        menu.clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START
                     + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = menu.getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) continue;
            if (MachineEnergyTable.get(descriptorId) == null
                    || !MachineEnergyTable.get(descriptorId).accepts(station)) {
                continue;
            }
            slot.set(station.copy());
            slot.setChanged();
            menu.clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
            return;
        }
        throw new IllegalStateException("Could not install Actually Additions station " + stationPath);
    }

    private static boolean craft(FixtureContext context, ResourceLocation recipeId) {
        var menu = new CraftingTerminalMenu(
                931, context.player().getInventory(), context.core());
        if (!menu.handleRecipeRequest(
                context.level(), recipeId, 1,
                CraftingDestination.NONE, context.player())) {
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

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            core.tick();
        }
    }

    private static StorageResourceKey fluidKey(FluidStack stack, FixtureContext context) {
        return StorageResourceKey.fluid(
                stack.copyWithAmount(1),
                context.level().registryAccess());
    }

    private static FluidStack fluid(String path, int amount) {
        Fluid fluid = BuiltInRegistries.FLUID.get(aaId(path));
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
            throw new IllegalStateException("Missing Actually Additions fluid " + path);
        }
        return new FluidStack(fluid, amount);
    }

    private static Item aaItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(aaId(path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Actually Additions item " + path);
        }
        return item;
    }

    private static ResourceLocation aaRecipe(String path) {
        return aaId(path);
    }

    private static ResourceLocation fixtureRecipe(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                ActuallyadditionsFixtureMod.MODID, path);
    }

    private static ResourceLocation aaId(String path) {
        return ResourceLocation.fromNamespaceAndPath("actuallyadditions", path);
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
