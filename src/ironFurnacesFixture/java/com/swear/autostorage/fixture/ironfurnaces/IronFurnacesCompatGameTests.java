package com.swear.autostorage.fixture.ironfurnaces;

import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineVariant;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.compat.ironfurnaces.IronFurnacesCompat;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import ironfurnaces.blocks.furnaces.BlockIronFurnaceBase;
import ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(IronFurnacesFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class IronFurnacesCompatGameTests {
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final ResourceLocation COPPER = ironFurnace("copper_furnace");
    private static final ResourceLocation IRON = ironFurnace("iron_furnace");
    private static final ResourceLocation NETHERITE = ironFurnace("netherite_furnace");

    private IronFurnacesCompatGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void variants_are_complete_ordered_and_exclude_multi_output_furnaces(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), IronFurnacesCompatGameTests.class);
        List<ResourceLocation> actual = IronFurnacesCompat.furnaceVariants().stream()
                .map(MachineVariant::stack)
                .map(ItemStack::getItem)
                .map(BuiltInRegistries.ITEM::getKey)
                .toList();
        List<ResourceLocation> expected = List.of(
                COPPER,
                IRON,
                ironFurnace("silver_furnace"),
                ironFurnace("gold_furnace"),
                ironFurnace("diamond_furnace"),
                ironFurnace("emerald_furnace"),
                ironFurnace("crystal_furnace"),
                ironFurnace("obsidian_furnace"),
                NETHERITE,
                ironFurnace("million_furnace"));
        if (!actual.equals(expected)) {
            helper.fail("Iron Furnaces variants were not in the supported tier order: " + actual);
            return;
        }
        if (actual.contains(ironFurnace("allthemodium_furnace"))
                || actual.contains(ironFurnace("vibranium_furnace"))
                || actual.contains(ironFurnace("unobtainium_furnace"))) {
            helper.fail("Multi-output Iron Furnaces variants must remain excluded");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void rates_follow_live_cook_time_config(GameTestHelper helper) {
        List<MachineVariant> variants = IronFurnacesCompat.furnaceVariants();
        ModConfigSpec.IntValue copperConfig = cookTimeConfig(COPPER);
        ModConfigSpec.IntValue ironConfig = cookTimeConfig(IRON);
        ModConfigSpec.IntValue netheriteConfig = cookTimeConfig(NETHERITE);
        int originalCopper = copperConfig.get();
        int originalIron = ironConfig.get();
        int originalNetherite = netheriteConfig.get();
        try {
            copperConfig.set(180);
            ironConfig.set(160);
            netheriteConfig.set(5);
            if (!variants.get(0).rate().equals(MachineWorkRate.of(10, 9))
                    || !variants.get(1).rate().equals(MachineWorkRate.of(5, 4))
                    || !variants.get(8).rate().equals(MachineWorkRate.of(40, 1))) {
                helper.fail("Iron Furnaces rates did not derive 200/configuredTicks");
                return;
            }
            ironConfig.set(100);
            if (!variants.get(1).rate().equals(MachineWorkRate.of(2, 1))) {
                helper.fail("Existing Iron Furnaces variant did not observe a live config change");
                return;
            }
            helper.succeed();
        } finally {
            copperConfig.set(originalCopper);
            ironConfig.set(originalIron);
            netheriteConfig.set(originalNetherite);
        }
    }

    @GameTest(template = "craftingtests.platform")
    public static void copper_variant_generates_exact_fractional_core_work(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            ModConfigSpec.IntValue copperConfig = cookTimeConfig(COPPER);
            int originalCopper = copperConfig.get();
            try {
                copperConfig.set(180);
                core.rebuildNetwork(level);
                var player = helper.makeMockPlayer(GameType.SURVIVAL);
                player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
                var menu = new CraftingTerminalMenu(601, player.getInventory(), core);
                menu.clickMenuButton(player, STATIONS_PAGE_BUTTON);
                ItemStack copperFurnace = new ItemStack(
                        BuiltInRegistries.ITEM.get(COPPER));
                var slot = findMachineSlot(menu, copperFurnace);
                if (slot == null) {
                    helper.fail("Iron Furnaces fixture descriptor had no installable slot");
                    return;
                }
                slot.set(copperFurnace);
                slot.setChanged();
                for (int tick = 0; tick < 9; tick++) core.tick();
                if (core.getEnergy(com.swear.autostorage.EnergyType.SMELTING_ENERGY)
                        != 10
                        || !MachineEnergyTable.get(MachineEnergyTable.FURNACE_ID)
                        .accepts(copperFurnace)) {
                    helper.fail("Copper Furnace did not join the built-in Furnace slot at 10/9 work");
                    return;
                }
                helper.succeed();
            } finally {
                copperConfig.set(originalCopper);
            }
        });
    }

    private static net.minecraft.world.inventory.Slot findMachineSlot(
            CraftingTerminalMenu menu,
            ItemStack station
    ) {
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = menu.getSlot(index);
            if (slot.isActive() && slot.mayPlace(station)) return slot;
        }
        return null;
    }

    private static ModConfigSpec.IntValue cookTimeConfig(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (!(block instanceof BlockIronFurnaceBase furnace)) {
            throw new IllegalStateException("Not an Iron Furnaces block: " + id);
        }
        BlockEntity blockEntity = furnace.newBlockEntity(BlockPos.ZERO, furnace.defaultBlockState());
        if (!(blockEntity instanceof BlockIronFurnaceTileBase furnaceTile)) {
            throw new IllegalStateException("Iron Furnaces block did not create a furnace tile: " + id);
        }
        return furnaceTile.getCookTimeConfig();
    }

    private static ResourceLocation ironFurnace(String path) {
        return ResourceLocation.fromNamespaceAndPath("ironfurnaces", path);
    }
}
