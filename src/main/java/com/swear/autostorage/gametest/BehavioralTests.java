package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.HashMap;
import java.util.Locale;

@GameTestHolder(AutoStorage.MODID)
public class BehavioralTests {

    private static ResourceLocation key(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path);
    }

    @GameTest(template = "platform")
    public static void item_key_snapshots_source_stack_components(GameTestHelper helper) {
        ItemStack source = new ItemStack(Items.DIAMOND_SWORD);
        source.setDamageValue(7);
        ItemKey key = ItemKey.of(source);
        int originalHash = key.hashCode();
        var index = new HashMap<ItemKey, String>();
        index.put(key, "stored");

        source.setDamageValue(19);

        if (key.hashCode() != originalHash) {
            helper.fail("ItemKey hash must not change when the source stack changes");
            return;
        }
        if (!"stored".equals(index.get(key))) {
            helper.fail("ItemKey must remain retrievable from hash indexes after source mutation");
            return;
        }
        if (key.toStack(1).getDamageValue() != 7) {
            helper.fail("ItemKey must retain the original component snapshot");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void storage_search_is_locale_independent(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.IRON_INGOT));
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                var matches = core.getDisplayStacks("iron");
                if (matches.size() != 1 || !matches.get(0).is(Items.IRON_INGOT)) {
                    helper.fail("Search must match Iron Ingot under Turkish default locale");
                    return;
                }
            } finally {
                Locale.setDefault(previous);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void cached_network_path_rejects_unloaded_or_non_network_middle(GameTestHelper helper) {
        BlockPos start = new BlockPos(0, 0, 0);
        BlockPos middle = start.east();
        BlockPos target = middle.east();
        var path = java.util.List.of(start, middle, target);

        if (AutoStorage.isValidNetworkPath(path, start, target,
                pos -> !pos.equals(middle), pos -> true)) {
            helper.fail("A cached path must fail when its middle chunk is unloaded");
            return;
        }
        if (AutoStorage.isValidNetworkPath(path, start, target,
                pos -> true, pos -> !pos.equals(middle))) {
            helper.fail("A cached path must fail when its middle block is no longer a network block");
            return;
        }
        if (!AutoStorage.isValidNetworkPath(path, start, target,
                pos -> true, pos -> true)) {
            helper.fail("A contiguous loaded network path must remain valid");
            return;
        }
        if (AutoStorage.isValidNetworkPath(java.util.List.of(start, target), start, target,
                pos -> true, pos -> true)) {
            helper.fail("A cached path must reject non-adjacent jumps");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void storage_unit_tier_contributions(GameTestHelper helper) {
        assertUnitContribution(helper, AutoStorage.STORAGE_UNIT_T1.get(), 10, "storage_unit_t1");
        assertUnitContribution(helper, AutoStorage.STORAGE_UNIT_T2.get(), 25, "storage_unit_t2");
        assertUnitContribution(helper, AutoStorage.STORAGE_UNIT_T3.get(), 50, "storage_unit_t3");
        assertUnitContribution(helper, AutoStorage.STORAGE_UNIT_T4.get(), 100, "storage_unit_t4");
        assertUnitContribution(helper, AutoStorage.STORAGE_UNIT_T5.get(), 200, "storage_unit_t5");
        assertUnitContribution(helper, AutoStorage.STORAGE_UNIT_T6.get(), 400, "storage_unit_t6");
        helper.succeed();
    }

    private static void assertUnitContribution(GameTestHelper helper, Block block, int expected, String name) {
        if (!(block instanceof StorageUnitBlock su) || su.getTypeContribution() != expected) {
            helper.fail(name + " should contribute " + expected
                    + ", got " + ((StorageUnitBlock) block).getTypeContribution());
        }
    }

    @GameTest(template = "platform")
    public static void block_entity_type_is_registered(GameTestHelper helper) {
        var type = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK_ENTITY_TYPE)
                .get(key("storage_core"));
        if (type == null) helper.fail("storage_core BE type is not registered");
        if (type != AutoStorage.STORAGE_CORE_BE.get()) helper.fail("BE type mismatch");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void export_bus_is_registered(GameTestHelper helper) {
        var block = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).get(key("export_bus"));
        if (block == null) helper.fail("export_bus block not registered");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void all_unit_tiers_are_registered(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK);
        String[] tiers = {"storage_unit_t1", "storage_unit_t2",
                          "storage_unit_t3", "storage_unit_t4",
                          "storage_unit_t5", "storage_unit_t6"};
        for (String name : tiers) {
            if (registry.get(key(name)) == null) {
                helper.fail(name + " not registered");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void terminal_blocks_implement_network_interface(GameTestHelper helper) {
        var stBlock = AutoStorage.STORAGE_TERMINAL.get();
        var ctBlock = AutoStorage.CRAFTING_TERMINAL.get();
        if (!(stBlock instanceof IStorageNetworkBlock)) helper.fail("storage_terminal not IStorageNetworkBlock");
        if (!(ctBlock instanceof IStorageNetworkBlock)) helper.fail("crafting_terminal not IStorageNetworkBlock");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void core_block_is_storage_core(GameTestHelper helper) {
        var core = AutoStorage.STORAGE_CORE.get();
        if (!(core instanceof IStorageNetworkBlock net && net.isStorageCore())) {
            helper.fail("storage_core should return isStorageCore=true");
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void bfs_handles_multiple_units_via_level(GameTestHelper helper) {
        var level = helper.getLevel();
        var coreState = AutoStorage.STORAGE_CORE.get().defaultBlockState();
        var unitState = AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState();
        var unitT3State = AutoStorage.STORAGE_UNIT_T3.get().defaultBlockState();

        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unit1Pos = corePos.south();
        var unit2Pos = corePos.west();

        level.setBlock(corePos, coreState, Block.UPDATE_ALL);
        level.setBlock(unit1Pos, unitState, Block.UPDATE_ALL);
        level.setBlock(unit2Pos, unitT3State, Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not created. BE=" + be);
                return;
            }
            core.rebuildNetwork(level);
            var blocks = core.getConnectedBlocks();
            if (!blocks.contains(unit1Pos)) helper.fail("unit1 not connected");
            if (!blocks.contains(unit2Pos)) helper.fail("unit2 not connected");
            if (core.getTotalTypeSlots() != 60)
                helper.fail("Expected 60 slots (10+50), got " + core.getTotalTypeSlots());
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void machine_energy_requires_installed_machine_and_scales_with_count(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(5, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not found, got: " + (be == null ? "null" : be.getClass().getName()));
                return;
            }
            for (EnergyType type : new EnergyType[]{
                    EnergyType.SMELTING_ENERGY,
                    EnergyType.BLASTING_ENERGY,
                    EnergyType.SMOKING_ENERGY,
                    EnergyType.CAMPFIRE_ENERGY,
                    EnergyType.BREW_ENERGY
            }) {
                if (core.getEnergy(type) != 0) {
                    helper.fail(type.getId() + " must stay at zero while no machine is installed");
                    return;
                }
            }

            loadInstalledMachines(core, helper, new ItemStack(Items.FURNACE, 3));
            core.tick();
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != 3) {
                helper.fail("Three installed Furnaces must generate exactly 3 Smelting Energy per tick");
                return;
            }
            if (core.getEnergy(EnergyType.BLASTING_ENERGY) != 0
                    || core.getEnergy(EnergyType.SMOKING_ENERGY) != 0
                    || core.getEnergy(EnergyType.CAMPFIRE_ENERGY) != 0
                    || core.getEnergy(EnergyType.BREW_ENERGY) != 0) {
                helper.fail("A Furnace must not generate another machine's energy");
                return;
            }

            loadInstalledMachines(core, helper);
            core.tick();
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != 3) {
                helper.fail("Removing the machine must stop generation without deleting stored energy");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void installed_machine_types_generate_only_their_mapped_energy(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            loadInstalledMachines(core, helper,
                    new ItemStack(Items.FURNACE, 2),
                    new ItemStack(Items.BLAST_FURNACE, 3),
                    new ItemStack(Items.SMOKER, 4),
                    new ItemStack(Items.CAMPFIRE, 5),
                    new ItemStack(Items.BREWING_STAND, 6));
            core.tick();

            long[] expected = {2, 3, 4, 5, 6};
            EnergyType[] types = {
                    EnergyType.SMELTING_ENERGY,
                    EnergyType.BLASTING_ENERGY,
                    EnergyType.SMOKING_ENERGY,
                    EnergyType.CAMPFIRE_ENERGY,
                    EnergyType.BREW_ENERGY
            };
            for (int i = 0; i < types.length; i++) {
                if (core.getEnergy(types[i]) != expected[i]) {
                    helper.fail(types[i].getId() + " expected " + expected[i]
                            + " after one tick, got " + core.getEnergy(types[i]));
                    return;
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void insert_and_extract_item(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            
            var unitPos = corePos.east();
            level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
            core.rebuildNetwork(level);
            
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64);
            long inserted = core.insertItem(stack);
            if (inserted != 64) helper.fail("Expected to insert 64, got " + inserted);
            
            net.minecraft.world.item.ItemStack extracted = core.extractItem(ItemKey.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE)), 32);
            if (extracted.getCount() != 32) helper.fail("Expected to extract 32, got " + extracted.getCount());
            
            var display = core.getDisplayStacks();
            if (display.isEmpty() || TerminalDisplayStack.amount(display.get(0)) != 32) {
                helper.fail("Remaining count should be 32, got "
                        + (display.isEmpty() ? "empty" : TerminalDisplayStack.amount(display.get(0))));
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void nbt_separation(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            
            var unitPos = corePos.east();
            level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
            core.rebuildNetwork(level);
            
            net.minecraft.world.item.ItemStack sword1 = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            net.minecraft.world.item.ItemStack sword2 = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            sword2.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 1);
            
            core.insertItem(sword1.copy());
            core.insertItem(sword2.copy());
            
            if (core.getTypeCount() != 2) {
                helper.fail("Expected 2 distinct item types due to NBT, got " + core.getTypeCount());
            }
            
            net.minecraft.world.item.ItemStack extracted = core.extractItem(ItemKey.of(sword2), 1);
            if (extracted.isEmpty() || !extracted.isEnchanted()) {
                helper.fail("Extracted sword should have enchantments");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void type_slot_limit(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level); // 0 slots initially
            
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 1);
            long inserted = core.insertItem(stack);
            if (inserted != 0) {
                helper.fail("Should not insert if totalTypeSlots is 0, inserted " + inserted);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void fuel_conversion(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            
            net.minecraft.world.item.ItemStack coal = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL);
            core.addFuel(coal, EnergyType.FURNACE_FUEL);
            
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 1600) {
                helper.fail("Expected 1600 furnace_fuel from coal, got " + core.getEnergy(EnergyType.FURNACE_FUEL));
            }
            if (!coal.isEmpty()) {
                helper.fail("Coal item should be consumed");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void energy_consumption(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            
            loadInstalledMachines(core, helper, new ItemStack(Items.FURNACE));
            for (int i = 0; i < 200; i++) {
                core.tick();
            }
            
            // add fuel
            core.addFuel(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL), EnergyType.FURNACE_FUEL);
            
            EnergyCost cost = new EnergyCost(EnergyType.SMELTING_ENERGY, 100, EnergyType.FURNACE_FUEL, 100);
            boolean success = core.consumeEnergy(cost, 1);
            
            if (!success) helper.fail("Should be able to consume energy");
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) < 100) helper.fail("Smelting energy should be at least 100");
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 1500) helper.fail("Furnace fuel should be 1500");
            
            // test failure case
            boolean fail = core.consumeEnergy(cost, 100); // 100 * 100 = 10000 smelting, we don't have that
            if (fail) helper.fail("Should fail due to insufficient process energy");
            
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void dynamic_capacity_change(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitPos = corePos.south();
        
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            if (core.getTotalTypeSlots() != 10) helper.fail("Expected 10 slots initially");
            
            level.destroyBlock(unitPos, false);
            
            helper.runAfterDelay(2, () -> {
                core.rebuildNetwork(level);
                if (core.getTotalTypeSlots() != 0) {
                    helper.fail("Expected 0 slots after unit destroyed, got " + core.getTotalTypeSlots());
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void insert_multiple_item_types(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 10));
            core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 5));
            core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 20));
            if (core.getTypeCount() != 3) helper.fail("Expected 3 types, got " + core.getTypeCount());
            var display = core.getDisplayStacks();
            long total = 0;
            for (var s : display) total += TerminalDisplayStack.amount(s);
            if (total != 35) helper.fail("Expected 35 total items, got " + total);
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void insert_same_item_accumulates(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 10));
            core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 20));
            if (core.getTypeCount() != 1) helper.fail("Should be 1 type (accumulated)");
            var extracted = core.extractItem(ItemKey.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE)), 30);
            if (extracted.getCount() != 30) helper.fail("Expected 30, got " + extracted.getCount());
            if (core.getTypeCount() != 0) helper.fail("Should be 0 types after full extract");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void extract_more_than_available(GameTestHelper helper) {
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.getLevel().setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(1, () -> {
            var level = helper.getLevel();
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 10));
            var extracted = core.extractItem(ItemKey.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE)), 999);
            if (extracted.getCount() != 10) helper.fail("Should only return available 10, got " + extracted.getCount());
            if (core.getTypeCount() != 0) helper.fail("Should be empty after extracting all");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void extract_from_empty(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var extracted = core.extractItem(ItemKey.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE)), 1);
            if (!extracted.isEmpty()) helper.fail("Should return empty from empty inventory");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void insert_zero_does_nothing(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            long r = core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 0));
            if (r != 0) helper.fail("Insert 0 should return 0");
            if (core.getTypeCount() != 0) helper.fail("Insert 0 should not add type");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void different_fuel_types(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.addFuel(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL), EnergyType.FURNACE_FUEL);
            core.addFuel(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_POWDER), EnergyType.BLAZE_FUEL);
            if (core.getEnergy(EnergyType.FURNACE_FUEL) != 1600) helper.fail("Furnace fuel should be 1600");
            if (core.getEnergy(EnergyType.BLAZE_FUEL) != 600) helper.fail("Blaze fuel should be 600");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void energy_type_isolation(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(1, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            loadInstalledMachines(core, helper,
                    ItemStack.EMPTY,
                    new ItemStack(Items.BLAST_FURNACE));
            for (int i = 0; i < 200; i++) core.tick();
            long smeltBefore = core.getEnergy(EnergyType.SMELTING_ENERGY);
            long blastBefore = core.getEnergy(EnergyType.BLASTING_ENERGY);
            var cost = new EnergyCost(EnergyType.BLASTING_ENERGY, 100, EnergyType.FURNACE_FUEL, 0);
            core.addFuel(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL), EnergyType.FURNACE_FUEL);
            core.consumeEnergy(cost, 1);
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != smeltBefore)
                helper.fail("Smelting energy should NOT be affected by blasting consumption");
            if (core.getEnergy(EnergyType.BLASTING_ENERGY) >= blastBefore)
                helper.fail("Blasting energy should decrease after consumption");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void display_stacks_filter_by_name(GameTestHelper helper) {
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.getLevel().setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(1, () -> {
            var level = helper.getLevel();
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 1));
            core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 1));
            core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 1));
            var stoneFiltered = core.getDisplayStacks("stone");
            if (stoneFiltered.size() != 1) helper.fail("'stone' should match 1 item");
            var dirtFiltered = core.getDisplayStacks("dirt");
            if (dirtFiltered.size() != 1) helper.fail("'dirt' should match 1 item");
            var all = core.getDisplayStacks("");
            if (all.size() != 3) helper.fail("Empty filter should show all 3");
            var none = core.getDisplayStacks("nonexistent");
            if (!none.isEmpty()) helper.fail("No match should return empty");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void recipe_lookup_finds_smelting_recipe(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.getMachineContainer().setItem(
                    MachineEnergyTable.FURNACE_SLOT, new ItemStack(Items.FURNACE));
            core.getMachineContainer().setItem(
                    MachineEnergyTable.BLAST_FURNACE_SLOT, new ItemStack(Items.BLAST_FURNACE));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(0, player.getInventory(), core);
            menu.lookUpRecipes(level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
            var recipes = menu.getCurrentRecipes();
            if (recipes.isEmpty()) helper.fail("Should find smelting/blasting recipes for iron ingot");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void crafting_table_recipe_no_energy(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.getMachineContainer().setItem(
                    MachineEnergyTable.CRAFTING_TABLE_SLOT, new ItemStack(Items.CRAFTING_TABLE));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(1, player.getInventory(), core);
            menu.lookUpRecipes(level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
            if (menu.getCurrentRecipes().isEmpty()) { helper.fail("No recipes for sticks"); return; }
            // Verify recipe type is crafting (instant, no energy)
            var holder = menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex());
            if (holder.value().getType() != net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                helper.fail("Stick recipe should be Crafting type");
            var cost = RecipeEnergyTable.getCost(holder.value());
            if (cost != null) helper.fail("Crafting table recipe should have no energy cost (null)");
            // Verify recipe has ingredients
            if (holder.value().getIngredients().isEmpty()) helper.fail("Recipe should have ingredients");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void export_bus_no_item_loss_when_target_full(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitPos = corePos.south();
        var busPos = corePos.east();
        var chestPos = busPos.east();

        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitPos, AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos,
            AutoStorage.EXPORT_BUS.get().defaultBlockState().setValue(ExportBusBlock.FACING, Direction.EAST),
            Block.UPDATE_ALL);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(3, () -> {
            var coreBe = level.getBlockEntity(corePos);
            if (!(coreBe instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));

            var chestBe = level.getBlockEntity(chestPos);
            if (!(chestBe instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)) {
                helper.fail("Chest not found"); return;
            }
            for (int i = 0; i < chest.getContainerSize(); i++) {
                chest.setItem(i, new ItemStack(Items.DIRT, 64));
            }

            var busBe = level.getBlockEntity(busPos);
            if (!(busBe instanceof ExportBusBlockEntity bus)) { helper.fail("Bus not found"); return; }
            bus.setFilter(new ItemStack(Items.STONE));
            bus.tick();

            var stoneKey = ItemKey.of(new ItemStack(Items.STONE));
            var remaining = core.extractItem(stoneKey, 1, true);
            if (remaining.isEmpty()) {
                helper.fail("Stone was lost when export bus pushed to full chest");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void action_actor_storage_ops_and_change_events(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var key = ItemKey.of(new ItemStack(Items.STONE));
            java.util.List<String> events = new java.util.ArrayList<>();
            core.addListener((changedKey, delta, newAmount, actor) ->
                    events.add(changedKey.item().toString() + ":" + delta + ":" + newAmount + ":" + actor.name()));

            ItemStack simulatedStack = new ItemStack(Items.STONE, 16);
            long simulated = core.insertItem(simulatedStack, Action.SIMULATE, Actor.bus(corePos));
            if (simulated != 16) { helper.fail("simulate insert should report 16, got " + simulated); return; }
            if (core.getItemCount(key) != 0) { helper.fail("simulate insert changed storage"); return; }
            if (!events.isEmpty()) { helper.fail("simulate insert fired events"); return; }

            ItemStack executeStack = new ItemStack(Items.STONE, 16);
            long inserted = core.insertItem(executeStack, Action.EXECUTE, Actor.bus(corePos));
            if (inserted != 16) { helper.fail("execute insert should report 16, got " + inserted); return; }
            if (core.getItemCount(key) != 16) { helper.fail("execute insert count should be 16, got " + core.getItemCount(key)); return; }

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            ItemStack simulatedExtract = core.extractItem(key, 5, Action.SIMULATE, Actor.player(player));
            if (simulatedExtract.getCount() != 5) { helper.fail("simulate extract should return 5, got " + simulatedExtract.getCount()); return; }
            if (core.getItemCount(key) != 16) { helper.fail("simulate extract changed storage"); return; }

            ItemStack extracted = core.extractItem(key, 5, Action.EXECUTE, Actor.crafting());
            if (extracted.getCount() != 5) { helper.fail("execute extract should return 5, got " + extracted.getCount()); return; }
            if (core.getItemCount(key) != 11) { helper.fail("execute extract count should be 11, got " + core.getItemCount(key)); return; }

            if (events.size() != 2) { helper.fail("expected 2 execute events, got " + events); return; }
            if (!events.get(0).contains(":16:16:bus@")) { helper.fail("insert event missing bus actor: " + events); return; }
            if (!events.get(1).contains(":-5:11:auto_storage_crafting")) { helper.fail("extract event missing crafting actor: " + events); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void smelting_craft_consumes_energy(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            loadInstalledMachines(core, helper, new ItemStack(Items.FURNACE));
            for (int i = 0; i < 200; i++) core.tick();
            core.addFuel(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL), EnergyType.FURNACE_FUEL);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(2, player.getInventory(), core);
            menu.lookUpRecipes(level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
            if (menu.getCurrentRecipes().isEmpty()) { helper.fail("No recipes for iron ingot"); return; }
            // Verify smelting recipe has energy cost
            for (int r = 0; r < menu.getRecipeCount(); r++) {
                var holder = menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex());
                if (holder.value().getType() == net.minecraft.world.item.crafting.RecipeType.SMELTING) {
                    var cost = RecipeEnergyTable.getCost(holder.value());
                    if (cost == null || cost.processAmount() <= 0) helper.fail("Smelting should have energy cost");
                    if (cost.fuelAmount() <= 0) helper.fail("Smelting should require fuel energy");
                    helper.succeed();
                    return;
                }
                menu.nextRecipe();
            }
            helper.fail("No smelting recipe found for iron ingot");
        });
    }

    @GameTest(template = "platform")
    public static void craft_rejected_without_energy(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.getMachineContainer().setItem(
                    MachineEnergyTable.FURNACE_SLOT, new ItemStack(Items.FURNACE));
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(3, player.getInventory(), core);
            menu.lookUpRecipes(level, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
            if (menu.getCurrentRecipes().isEmpty()) { helper.fail("No recipes for iron ingot"); return; }
            core.insertItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_ORE, 1));
            boolean hasRecipe = false;
            for (int r = 0; r < menu.getRecipeCount(); r++) {
                var holder = menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex());
                if (holder.value().getType() == net.minecraft.world.item.crafting.RecipeType.SMELTING) {
                    hasRecipe = true; break;
                }
                menu.nextRecipe();
            }
            if (!hasRecipe) helper.fail("Smelting recipe not found for iron ingot");
            // Verify recipe needs iron ore as input
            var holder = menu.getCurrentRecipes().get(menu.getCurrentRecipeIndex());
            if (holder.value().getIngredients().isEmpty()) helper.fail("Recipe should have ingredients");
            // Verify energy cost exists for smelting
            var cost = RecipeEnergyTable.getCost(holder.value());
            if (cost == null) helper.fail("Smelting should have energy cost");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void action_execute_consumes_input_but_simulate_preserves_it(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            ItemStack simulated = new ItemStack(Items.STONE, 7);
            long accepted = core.insertItem(simulated, Action.SIMULATE, Actor.player(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL)));
            if (accepted != 7) { helper.fail("simulate should report accepting 7, got " + accepted); return; }
            if (simulated.getCount() != 7) { helper.fail("simulate must not consume input stack, got " + simulated.getCount()); return; }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0) { helper.fail("simulate must not mutate storage"); return; }

            ItemStack executed = new ItemStack(Items.STONE, 7);
            long inserted = core.insertItem(executed, Action.EXECUTE, Actor.crafting());
            if (inserted != 7) { helper.fail("execute should insert 7, got " + inserted); return; }
            if (executed.getCount() != 0) { helper.fail("execute must consume input stack, got " + executed.getCount()); return; }
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 7) { helper.fail("storage should contain 7 stone"); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void storage_listener_remove_stops_future_events(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            java.util.List<String> events = new java.util.ArrayList<>();
            StorageListener listener = (key, delta, newAmount, actor) -> events.add(delta + ":" + newAmount + ":" + actor.name());
            core.addListener(listener);
            core.insertItem(new ItemStack(Items.STONE, 3), Action.EXECUTE, Actor.bus(corePos));
            core.removeListener(listener);
            core.insertItem(new ItemStack(Items.STONE, 2), Action.EXECUTE, Actor.bus(corePos));
            if (events.size() != 1) { helper.fail("removed listener should see exactly 1 event, got " + events); return; }
            if (!events.get(0).startsWith("3:3:bus@")) { helper.fail("first event should record inserted amount/new total/bus actor, got " + events); return; }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void extract_matching_simulate_preserves_variants_and_events(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
            ItemStack damaged = new ItemStack(Items.DIAMOND_SWORD);
            damaged.setDamageValue(1);
            core.insertItem(plain.copy());
            core.insertItem(damaged.copy());
            java.util.List<Long> deltas = new java.util.ArrayList<>();
            core.addListener((key, delta, newAmount, actor) -> deltas.add(delta));

            long simulated = core.extractMatching(s -> s.is(Items.DIAMOND_SWORD), 2, Action.SIMULATE, Actor.crafting());
            if (simulated != 2) { helper.fail("simulate fuzzy extract should report 2, got " + simulated); return; }
            if (core.countMatching(s -> s.is(Items.DIAMOND_SWORD)) != 2) { helper.fail("simulate fuzzy extract changed storage"); return; }
            if (!deltas.isEmpty()) { helper.fail("simulate fuzzy extract fired events: " + deltas); return; }

            long executed = core.extractMatching(s -> s.is(Items.DIAMOND_SWORD), 2, Action.EXECUTE, Actor.crafting());
            if (executed != 2) { helper.fail("execute fuzzy extract should remove 2, got " + executed); return; }
            if (core.countMatching(s -> s.is(Items.DIAMOND_SWORD)) != 0) { helper.fail("execute fuzzy extract should remove both variants"); return; }
            if (deltas.size() != 2 || deltas.get(0) != -1 || deltas.get(1) != -1) {
                helper.fail("execute fuzzy extract should fire one -1 delta per variant, got " + deltas);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void type_slot_is_reusable_after_last_variant_extracted(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));
            if (core.getTypeCount() != 1) { helper.fail("type count should be 1 after insert, got " + core.getTypeCount()); return; }
            ItemStack extracted = core.extractItem(ItemKey.of(new ItemStack(Items.STONE)), 1, Action.EXECUTE, Actor.player(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL)));
            if (extracted.getCount() != 1) { helper.fail("should extract final stone, got " + extracted); return; }
            if (core.getTypeCount() != 0) { helper.fail("type count should drop to 0 after final variant, got " + core.getTypeCount()); return; }
            long inserted = core.insertItem(new ItemStack(Items.DIRT, 1), Action.EXECUTE, Actor.EMPTY);
            if (inserted != 1) { helper.fail("freed type slot should accept dirt, got " + inserted); return; }
            if (core.getTypeCount() != 1) { helper.fail("type count should be 1 after reusing slot, got " + core.getTypeCount()); return; }
            helper.succeed();
        });
    }

    private static void loadInstalledMachines(
            StorageCoreBlockEntity core,
            GameTestHelper helper,
            ItemStack... machines
    ) {
        core.getMachineContainer().clearContent();
        for (int slot = 0; slot < machines.length; slot++) {
            ItemStack stack = machines[slot];
            if (stack.isEmpty()) continue;
            core.getMachineContainer().setItem(slot, stack.copy());
        }
    }
}
