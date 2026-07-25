package com.swearprom.magicstorage.fixture.evilcraft;

import com.swearprom.magicstorage.magic_storage.Action;
import com.swearprom.magicstorage.magic_storage.CraftingDestination;
import com.swearprom.magicstorage.magic_storage.CraftingTerminalMenu;
import com.swearprom.magicstorage.magic_storage.ItemKey;
import com.swearprom.magicstorage.magic_storage.MachineDescriptor;
import com.swearprom.magicstorage.magic_storage.MachineEnergyTable;
import com.swearprom.magicstorage.magic_storage.MachineWorkRate;
import com.swearprom.magicstorage.magic_storage.MagicStorage;
import com.swearprom.magicstorage.magic_storage.StorageCoreBlockEntity;
import com.swearprom.magicstorage.magic_storage.StorageResourceKey;
import com.swearprom.magicstorage.magic_storage.StorageTerminalMenu;
import com.swearprom.magicstorage.magic_storage.TerminalDisplayStack;
import com.swearprom.magicstorage.magic_storage.TerminalResourceDisplay;
import com.swearprom.magicstorage.magic_storage.TerminalResourceView;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(EvilCraftFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class EvilCraftIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int NEXT_RESOURCE_VIEW_BUTTON = 26;
    private static final ResourceLocation BLOOD_INFUSER = stationId("blood_infuser");
    private static final ResourceLocation TIERED_BATCH = fixtureRecipe("tiered_blood_batch");
    private static final ResourceLocation FLUID_ONLY = fixtureRecipe("fluid_only");
    private static final ResourceLocation PROMISE_INPUT_OVERLAP =
            fixtureRecipe("promise_input_overlap");

    private EvilCraftIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void blood_infuser_registers_only_exact_supported_recipes(
            GameTestHelper helper
    ) {
        MachineDescriptor descriptor = MachineEnergyTable.get(BLOOD_INFUSER);
        ItemStack station = new ItemStack(evilItem("blood_infuser"));
        if (descriptor == null
                || descriptor.category() != MachineEnergyTable.Category.PROCESS
                || !descriptor.accepts(station)
                || !descriptor.rateFor(station).orElseThrow().equals(MachineWorkRate.ONE)
                || !MagicStorage.RECIPE_FAMILY_REGISTRY.containsKey(BLOOD_INFUSER)) {
            helper.fail("Missing exact EvilCraft Blood Infuser station/family");
            return;
        }
        if (!supports(helper, evilRecipe("blood_infuser/base/bloody_cobblestone"))
                || supports(helper, evilRecipe("blood_infuser/base/dark_power_gem"))
                || supports(helper, fixtureRecipe("positive_xp"))
                || supports(helper, fixtureRecipe("tag_output"))
                || supports(helper, fixtureRecipe("over_tier"))
                || MagicStorage.RECIPE_FAMILY_REGISTRY.containsKey(stationId("purifier"))) {
            helper.fail("EvilCraft unsupported XP/tag/tier/Purifier behavior did not fail closed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void promise_input_and_catalyst_overlap_fails_closed(
            GameTestHelper helper
    ) {
        var overlap = helper.getLevel().getRecipeManager()
                .byKey(PROMISE_INPUT_OVERLAP).orElse(null);
        if (overlap == null) {
            helper.fail("Promise overlap fixture was not loaded");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(overlap)) {
            helper.fail("Blood Infuser accepted one Promise as input and retained catalyst");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void dark_tank_blood_uses_standard_fluid_capability(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(
                pos,
                BuiltInRegistries.BLOCK.get(evilId("dark_tank")).defaultBlockState(),
                Block.UPDATE_ALL);
        IFluidHandler tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        FluidStack blood = new FluidStack(BuiltInRegistries.FLUID.get(evilId("blood")), 1_000);
        if (tank == null
                || tank.fill(blood, IFluidHandler.FluidAction.SIMULATE) != 1_000
                || tank.getFluidInTank(0).getAmount() != 0
                || tank.fill(blood, IFluidHandler.FluidAction.EXECUTE) != 1_000
                || tank.getFluidInTank(0).getAmount() != 1_000
                || tank.drain(250, IFluidHandler.FluidAction.SIMULATE).getAmount() != 250
                || tank.getFluidInTank(0).getAmount() != 1_000
                || tank.drain(250, IFluidHandler.FluidAction.EXECUTE).getAmount() != 250
                || tank.getFluidInTank(0).getAmount() != 750) {
            helper.fail("EvilCraft Dark Tank violated standard fluid simulate/commit semantics");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void terminal_displays_blood_and_core_reload_preserves_it(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            StorageResourceKey blood = bloodKey(context);
            seedResource(context.core(), blood, 4_321);
            var menu = new StorageTerminalMenu(
                    901, context.player().getInventory(), context.core());
            selectFluidView(menu, context);
            ItemStack display = menu.getSlot(0).getItem();
            if (!TerminalResourceDisplay.key(display).filter(blood::equals).isPresent()
                    || TerminalDisplayStack.amount(display) != 4_321) {
                helper.fail("Terminal did not display exact stored EvilCraft Blood");
                return;
            }
            CompoundTag saved = context.core().saveWithoutMetadata(
                    context.level().registryAccess());
            BlockPos pos = context.core().getBlockPos();
            context.level().removeBlockEntity(pos);
            var reloaded = new StorageCoreBlockEntity(
                    pos, MagicStorage.STORAGE_CORE.get().defaultBlockState());
            reloaded.loadWithComponents(saved, context.level().registryAccess());
            context.level().setBlockEntity(reloaded);
            helper.runAfterDelay(2, () -> {
                reloaded.rebuildNetwork(context.level());
                if (reloaded.getResourceAmount(blood) != 4_321) {
                    helper.fail("EvilCraft Blood did not survive the Core repository reload");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void tiered_recipe_batches_blood_and_retains_higher_promise(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.CLAY_BALL, 2);
            seedItem(context.core(), evilItem("promise_tier_3"), 1);
            seedResource(context.core(), bloodKey(context), 500);
            if (!installStation(context)) {
                helper.fail("Could not install the Blood Infuser");
                return;
            }
            tick(context.core(), 80);
            if (!craft(context, TIERED_BATCH, 2)
                    || itemCount(context.core(), Items.CLAY_BALL) != 0
                    || itemCount(context.core(), Items.BRICK) != 6
                    || itemCount(context.core(), evilItem("promise_tier_3")) != 1
                    || context.core().getResourceAmount(bloodKey(context)) != 0
                    || context.core().getStationWork(BLOOD_INFUSER) != 0) {
                helper.fail("Tiered Blood Infuser recipe did not commit exact batch resources");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_promise_rejects_without_partial_mutation(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.CLAY_BALL, 1);
            seedResource(context.core(), bloodKey(context), 250);
            if (!installStation(context)) {
                helper.fail("Could not install the Blood Infuser");
                return;
            }
            tick(context.core(), 40);
            if (craft(context, TIERED_BATCH, 1)
                    || itemCount(context.core(), Items.CLAY_BALL) != 1
                    || itemCount(context.core(), Items.BRICK) != 0
                    || context.core().getResourceAmount(bloodKey(context)) != 250
                    || context.core().getStationWork(BLOOD_INFUSER) != 40) {
                helper.fail("Missing Promise partially mutated Blood Infuser resources");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void fluid_only_recipe_batches_exact_amounts(GameTestHelper helper) {
        withCore(helper, context -> {
            seedResource(context.core(), bloodKey(context), 250);
            if (!installStation(context)) {
                helper.fail("Could not install the Blood Infuser");
                return;
            }
            tick(context.core(), 40);
            if (!craft(context, FLUID_ONLY, 2)
                    || itemCount(context.core(), Items.REDSTONE) != 4
                    || context.core().getResourceAmount(bloodKey(context)) != 0
                    || context.core().getStationWork(BLOOD_INFUSER) != 0) {
                helper.fail("Fluid-only Blood Infuser recipe committed wrong amounts");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void official_bloody_cobblestone_recipe_commits_exactly(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.MOSSY_COBBLESTONE, 1);
            seedResource(context.core(), bloodKey(context), 500);
            if (!installStation(context)) {
                helper.fail("Could not install the Blood Infuser");
                return;
            }
            tick(context.core(), 250);
            if (!craft(context, evilRecipe("blood_infuser/base/bloody_cobblestone"), 1)
                    || itemCount(context.core(), Items.MOSSY_COBBLESTONE) != 0
                    || itemCount(context.core(), evilItem("bloody_cobblestone")) != 1
                    || context.core().getResourceAmount(bloodKey(context)) != 0
                    || context.core().getStationWork(BLOOD_INFUSER) != 0) {
                helper.fail("Official Bloody Cobblestone recipe committed wrong resources");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void blood_shortage_rejects_without_partial_mutation(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.MOSSY_COBBLESTONE, 1);
            seedResource(context.core(), bloodKey(context), 499);
            if (!installStation(context)) {
                helper.fail("Could not install the Blood Infuser");
                return;
            }
            tick(context.core(), 250);
            if (craft(context, evilRecipe("blood_infuser/base/bloody_cobblestone"), 1)
                    || itemCount(context.core(), Items.MOSSY_COBBLESTONE) != 1
                    || itemCount(context.core(), evilItem("bloody_cobblestone")) != 0
                    || context.core().getResourceAmount(bloodKey(context)) != 499
                    || context.core().getStationWork(BLOOD_INFUSER) != 250) {
                helper.fail("Blood shortage partially mutated item, fluid, or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void output_overflow_rolls_back_item_blood_and_work(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.MOSSY_COBBLESTONE, 1);
            seedResource(context.core(), bloodKey(context), 500);
            StorageResourceKey output = itemKey(
                    context, new ItemStack(evilItem("bloody_cobblestone")));
            seedResource(context.core(), output, Long.MAX_VALUE);
            if (!installStation(context)) {
                helper.fail("Could not install the Blood Infuser");
                return;
            }
            tick(context.core(), 250);
            if (craft(context, evilRecipe("blood_infuser/base/bloody_cobblestone"), 1)
                    || itemCount(context.core(), Items.MOSSY_COBBLESTONE) != 1
                    || context.core().getResourceAmount(bloodKey(context)) != 500
                    || context.core().getResourceAmount(output) != Long.MAX_VALUE
                    || context.core().getStationWork(BLOOD_INFUSER) != 250) {
                helper.fail("Rejected Blood Infuser overflow partially mutated the transaction");
                return;
            }
            helper.succeed();
        });
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
                MagicStorage.STORAGE_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(
                corePos.south(),
                MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
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

    private static boolean installStation(FixtureContext context) {
        var menu = new CraftingTerminalMenu(
                902, context.player().getInventory(), context.core());
        ItemStack station = new ItemStack(evilItem("blood_infuser"));
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
            return ItemStack.isSameItemSameComponents(slot.getItem(), station);
        }
        menu.clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
        return false;
    }

    private static boolean craft(
            FixtureContext context,
            ResourceLocation recipeId,
            int crafts
    ) {
        var menu = new CraftingTerminalMenu(
                903, context.player().getInventory(), context.core());
        if (!menu.handleRecipeRequest(
                context.level(), recipeId, 1, CraftingDestination.NONE, context.player())) {
            return false;
        }
        if (menu.computeCraftPreview(context.core(), context.player()).craftable() < crafts) {
            return false;
        }
        return menu.handleRecipeRequest(
                context.level(),
                recipeId,
                crafts,
                CraftingDestination.STORAGE,
                context.player());
    }

    private static void selectFluidView(
            StorageTerminalMenu menu,
            FixtureContext context
    ) {
        for (int attempt = 0;
             attempt < TerminalResourceView.values().length
                     && menu.getResourceView() != TerminalResourceView.FLUID;
             attempt++) {
            menu.clickMenuButton(context.player(), NEXT_RESOURCE_VIEW_BUTTON);
        }
        if (menu.getResourceView() != TerminalResourceView.FLUID) {
            throw new IllegalStateException("Fluid resource view is unavailable");
        }
        menu.refreshDisplayItems(context.core());
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

    private static StorageResourceKey itemKey(FixtureContext context, ItemStack stack) {
        return StorageResourceKey.item(stack, context.level().registryAccess());
    }

    private static StorageResourceKey bloodKey(FixtureContext context) {
        return StorageResourceKey.fluid(
                new FluidStack(BuiltInRegistries.FLUID.get(evilId("blood")), 1),
                context.level().registryAccess());
    }

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item evilItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(evilId(path));
        if (item == Items.AIR) throw new IllegalStateException("Missing EvilCraft item " + path);
        return item;
    }

    private static ResourceLocation evilId(String path) {
        return ResourceLocation.fromNamespaceAndPath("evilcraft", path);
    }

    private static ResourceLocation evilRecipe(String path) {
        return evilId(path);
    }

    private static ResourceLocation fixtureRecipe(String path) {
        return ResourceLocation.fromNamespaceAndPath(EvilCraftFixtureMod.MODID, path);
    }

    private static ResourceLocation stationId(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                MagicStorage.MODID, "evilcraft_" + path);
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
