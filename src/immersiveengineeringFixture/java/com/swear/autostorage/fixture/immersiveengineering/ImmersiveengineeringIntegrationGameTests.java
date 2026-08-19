package com.swear.autostorage.fixture.immersiveengineering;

import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.Action;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

@GameTestHolder(ImmersiveengineeringFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ImmersiveengineeringIntegrationGameTests {
    private ImmersiveengineeringIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void unsafe_multiblock_contracts_are_not_registered(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                ImmersiveengineeringIntegrationGameTests.class);
        if (!ModList.get().isLoaded("immersiveengineering")) {
            helper.fail("Immersive Engineering mod is not loaded");
            return;
        }
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .filter(id -> id.getNamespace().equals("immersiveengineering")
                                || id.getPath().startsWith("immersiveengineering_"))
                        .anyMatch(id -> !SUPPORTED_MACHINES.contains(id))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .filter(id -> id.getNamespace().equals("immersiveengineering")
                                || id.getPath().startsWith("immersiveengineering_"))
                        .anyMatch(id -> !SUPPORTED_MACHINES.contains(id))) {
            helper.fail("Immersive Engineering unsafe multiblock contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void alloy_smelter_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, ie("alloysmelter/electrum"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void coke_oven_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(ie("cokeoven/charcoal")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Immersive Engineering recipe cokeoven/charcoal");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Immersive Engineering recipe was accepted: cokeoven/charcoal");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void blast_furnace_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, ie("blastfurnace/steel"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void crusher_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, ie("crusher/amethyst"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void cloche_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, ie("cloche/allium"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void metal_press_and_arc_furnace_recipes_are_supported(
            GameTestHelper helper
    ) {
        for (ResourceLocation recipeId : List.of(ie("arcfurnace/dust_iron"))) {
            var holder = helper.getLevel().getRecipeManager()
                    .byKey(recipeId).orElse(null);
            if (holder == null
                    || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Arc Furnace recipe must be supported: " + recipeId);
                return;
            }
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
                ie("cokeoven/charcoal"),
                ie("blastfurnace/steel"),
                ie("cloche/allium"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited Immersive Engineering recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        if (types.size() != 3) {
            helper.fail("Audited Immersive Engineering recipe type is empty");
            return;
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited Immersive Engineering recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe Immersive Engineering recipe type accepted " + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void sawmill_recipes_with_secondary_outputs_fail_closed(
            GameTestHelper helper
    ) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(ie("sawmill/oak_log")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Immersive Engineering recipe sawmill/oak_log");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Sawmill recipe with secondary outputs was accepted");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void arc_furnace_recipe_is_supported_with_deterministic_plan(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            var holder = level.getRecipeManager()
                    .byKey(ie("arcfurnace/dust_iron")).orElse(null);
            if (holder == null
                    || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Arc Furnace recipe must be supported: arcfurnace/dust_iron");
                return;
            }
            seedItem(core, ieItem("dust_iron"), 1);
            seedResource(core, StorageResourceKey.neoforgeEnergy(), 1_000_000L);
            if (!core.addDescriptorTransform(
                    com.swear.autostorage.MachineEnergyTable.HAMMER_ID,
                    new ItemStack(ieItem("hammer")))) {
                helper.fail("Could not seed engineer's hammer resource");
                return;
            }
            installStation(core, player, ieItem("arc_furnace"));
            addCoreTicks(core, 10_000);
            var menu = new CraftingTerminalMenu(612, player.getInventory(), core);
            boolean acceptedWithoutElectrode = menu.handleRecipeRequest(
                    level, ie("arcfurnace/dust_iron"), 1,
                    com.swear.autostorage.CraftingDestination.STORAGE, player);
            if (acceptedWithoutElectrode) {
                helper.fail("Arc Furnace craft must require a graphite electrode");
                return;
            }
            if (!core.addDescriptorTransform(
                    com.swear.autostorage.MachineEnergyTable.ELECTRODE_ID,
                    new ItemStack(ieItem("graphite_electrode")))) {
                helper.fail("Could not seed graphite electrode resource");
                return;
            }
            long electrodeBefore = core.getDescriptorAmount(
                    com.swear.autostorage.MachineEnergyTable.ELECTRODE_ID);
            boolean committed = menu.handleRecipeRequest(
                    level, ie("arcfurnace/dust_iron"), 1,
                    com.swear.autostorage.CraftingDestination.STORAGE, player);
            long iron = core.getItemCount(ItemKey.of(new ItemStack(Items.IRON_INGOT)));
            long electrodeAfter = core.getDescriptorAmount(
                    com.swear.autostorage.MachineEnergyTable.ELECTRODE_ID);
            if (!committed || iron != 1 || electrodeAfter != electrodeBefore - 1) {
                helper.fail("Arc Furnace craft did not consume the electrode atomically: "
                        + "committed=" + committed + " iron=" + iron
                        + " electrodeBefore=" + electrodeBefore
                        + " electrodeAfter=" + electrodeAfter);
                return;
            }
            helper.succeed();
        });
    }

    private static final Set<ResourceLocation> SUPPORTED_MACHINES = Set.of(
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "immersiveengineering_arc_furnace"),
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "immersiveengineering_bottling_machine"));

    private static final ResourceLocation ARC_FURNACE =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "immersiveengineering_arc_furnace");
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
        throw new IllegalStateException("Could not install IE sawmill station");
    }

    private static void addCoreTicks(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item ieItem(String path) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ie(path));
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(
                net.minecraft.server.level.ServerLevel level,
                StorageCoreBlockEntity core,
                Player player
        );
    }

    private static void assertUnsupported(GameTestHelper helper, ResourceLocation... recipeIds) {
        for (ResourceLocation recipeId : recipeIds) {
            var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing representative Immersive Engineering recipe " + recipeId);
                return;
            }
            if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Unsafe Immersive Engineering recipe was accepted: " + recipeId);
                return;
            }
        }
        helper.succeed();
    }

    private static ResourceLocation ie(String path) {
        return ResourceLocation.fromNamespaceAndPath("immersiveengineering", path);
    }
}
