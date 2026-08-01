package com.swear.autostorage.fixture.mysticalagriculture;

import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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

@GameTestHolder(MysticalagricultureFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class MysticalagricultureIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final long FE_COST = 4_000L;
    private static final long WORK_COST = 200L;
    private static final ResourceLocation REPROCESSOR = ResourceLocation.fromNamespaceAndPath(
            AutoStorage.MODID, "mysticalagriculture_reprocessor");
    private static final ResourceLocation REPROCESSOR_RECIPE = ma(
            "seed/reprocessor/inferium");
    private static final ResourceLocation INFUSION_RECIPE = ma(
            "augment/absorption_i");
    private static final ResourceLocation ENCHANTER_RECIPE = ma(
            "enchanter/aqua_affinity");
    private static final ResourceLocation SOUL_EXTRACTION_RECIPE = ma(
            "souls/armadillo_scute");
    private static final ResourceLocation SOULIUM_SPAWNER_RECIPE = ma(
            "soulium_spawner/bee");
    private static final ResourceLocation AWAKENING_RECIPE = ma(
            "awakened_supremium_block_awakening");

    private MysticalagricultureIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void reprocessor_registers_exact_rate_and_rejected_families_stay_out(
            GameTestHelper helper
    ) {
        if (!ModList.get().isLoaded("mysticalagriculture")) {
            helper.fail("Mystical Agriculture mod is not loaded");
            return;
        }
        MachineDescriptor descriptor = MachineEnergyTable.get(REPROCESSOR);
        ItemStack station = new ItemStack(maItem("seed_reprocessor"));
        if (descriptor == null
                || descriptor.category() != MachineCategory.PROCESS
                || descriptor.maxInstalledCount() != MachineDescriptorApi.MAX_INSTALLED_COUNT
                || descriptor.variants().size() != 1
                || !descriptor.accepts(station)
                || !descriptor.rateFor(station).orElseThrow().equals(
                MachineWorkRate.of(1, 1))
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(REPROCESSOR)
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorage.MODID, "mysticalagriculture_infusion"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorage.MODID, "mysticalagriculture_enchanter"))) {
            helper.fail(
                    "COMPAT_KIT_PRESENT_TARGET_LOAD_ONCE Mystical Agriculture Reprocessor registration was incorrect");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void reprocessor_consumes_seed_fe_and_work(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), maItem("inferium_seeds"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), FE_COST);
            installReprocessor(context);
            tick(context.core(), 200);
            if (!craft(context, REPROCESSOR_RECIPE)
                    || itemCount(context.core(), maItem("inferium_seeds")) != 0
                    || itemCount(context.core(), maItem("inferium_essence")) != 2
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(REPROCESSOR) != 0) {
                helper.fail("Mystical Agriculture Reprocessor did not consume exact costs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_seed_is_atomic_no_op(GameTestHelper helper) {
        withCore(helper, context -> {
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), FE_COST);
            installReprocessor(context);
            tick(context.core(), 200);
            if (craft(context, REPROCESSOR_RECIPE)
                    || itemCount(context.core(), maItem("inferium_essence")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != FE_COST
                    || context.core().getStationWork(REPROCESSOR) != WORK_COST) {
                helper.fail(
                        "COMPAT_KIT_INGREDIENT_SHORTAGE_ATOMIC Mystical Agriculture missing seed was not atomic");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_fe_is_atomic_no_op(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), maItem("inferium_seeds"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), FE_COST - 1);
            installReprocessor(context);
            tick(context.core(), 200);
            if (craft(context, REPROCESSOR_RECIPE)
                    || itemCount(context.core(), maItem("inferium_seeds")) != 1
                    || itemCount(context.core(), maItem("inferium_essence")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != FE_COST - 1
                    || context.core().getStationWork(REPROCESSOR) != WORK_COST) {
                helper.fail("Mystical Agriculture insufficient FE was not atomic");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void insufficient_work_is_atomic_no_op(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), maItem("inferium_seeds"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), FE_COST);
            installReprocessor(context);
            tick(context.core(), 199);
            if (craft(context, REPROCESSOR_RECIPE)
                    || itemCount(context.core(), maItem("inferium_seeds")) != 1
                    || itemCount(context.core(), maItem("inferium_essence")) != 0
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != FE_COST
                    || context.core().getStationWork(REPROCESSOR) != 199) {
                helper.fail("Mystical Agriculture insufficient work was not atomic");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void full_destination_rolls_back(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), maItem("inferium_seeds"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), FE_COST);
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(maItem("inferium_essence")),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            installReprocessor(context);
            tick(context.core(), 200);
            if (craft(context, REPROCESSOR_RECIPE)
                    || itemCount(context.core(), maItem("inferium_seeds")) != 1
                    || itemCount(context.core(), maItem("inferium_essence")) != Long.MAX_VALUE
                    || context.core().getResourceAmount(
                    StorageResourceKey.neoforgeEnergy()) != FE_COST
                    || context.core().getStationWork(REPROCESSOR) != WORK_COST) {
                helper.fail(
                        "COMPAT_KIT_DESTINATION_CAPACITY_ATOMIC Mystical Agriculture full destination was not atomic");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void rejected_machine_families_fail_closed(GameTestHelper helper) {
        for (ResourceLocation recipeId : List.of(
                INFUSION_RECIPE,
                ENCHANTER_RECIPE,
                SOUL_EXTRACTION_RECIPE,
                SOULIUM_SPAWNER_RECIPE,
                AWAKENING_RECIPE)) {
            var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail(
                        "COMPAT_KIT_REJECTED_FAMILY_FAIL_CLOSED Missing Mystical Agriculture recipe "
                                + recipeId);
                return;
            }
            if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail(
                        "COMPAT_KIT_REJECTED_FAMILY_FAIL_CLOSED Unsafe Mystical Agriculture recipe was accepted: "
                                + recipeId);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_recipe_in_each_rejected_machine_type_fails_closed(
            GameTestHelper helper
    ) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                INFUSION_RECIPE,
                ENCHANTER_RECIPE,
                SOUL_EXTRACTION_RECIPE,
                SOULIUM_SPAWNER_RECIPE,
                AWAKENING_RECIPE)) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail(
                        "COMPAT_KIT_CHECKED_OVERFLOW_ATOMIC Missing Mystical Agriculture recipe "
                                + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        if (types.size() != 5) {
            helper.fail(
                    "COMPAT_KIT_CHECKED_OVERFLOW_ATOMIC Expected 5 rejected Mystical Agriculture recipe types, found "
                            + types.size());
            return;
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail(
                        "COMPAT_KIT_CHECKED_OVERFLOW_ATOMIC Rejected Mystical Agriculture recipe type is empty: "
                                + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail(
                            "COMPAT_KIT_CHECKED_OVERFLOW_ATOMIC Unsafe Mystical Agriculture recipe type accepted "
                                    + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
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

    private static void installReprocessor(FixtureContext context) {
        ItemStack station = new ItemStack(maItem("seed_reprocessor"));
        var menu = new CraftingTerminalMenu(
                940, context.player().getInventory(), context.core());
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
        throw new IllegalStateException("Could not install Mystical Agriculture Reprocessor");
    }

    private static boolean craft(FixtureContext context, ResourceLocation recipeId) {
        var menu = new CraftingTerminalMenu(
                941, context.player().getInventory(), context.core());
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
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item maItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(ma(path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Mystical Agriculture item " + path);
        }
        return item;
    }

    private static ResourceLocation ma(String path) {
        return ResourceLocation.fromNamespaceAndPath("mysticalagriculture", path);
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
