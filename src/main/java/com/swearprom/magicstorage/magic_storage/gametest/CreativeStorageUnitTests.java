package com.swearprom.magicstorage.magic_storage;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MagicStorage.MODID)
@PrefixGameTestTemplate(false)
public final class CreativeStorageUnitTests {

    @GameTest(template = "registrationtests.empty")
    public static void unlimited_capacity_is_explicit_and_not_a_finite_sentinel(GameTestHelper helper) {
        StorageTypeCapacity capacity = StorageTypeCapacity.unlimitedCapacity();

        if (!capacity.unlimited() || capacity.finiteTypeSlots() != 0) {
            helper.fail("Unlimited capacity must use an explicit state with no finite sentinel");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "registrationtests.empty")
    public static void capacity_composition_handles_zero_finite_one_multiple_and_mixed_units(GameTestHelper helper) {
        StorageTypeCapacity zero = StorageTypeCapacity.zero();
        StorageTypeCapacity finite = zero
                .plus(StorageTypeCapacity.finite(10))
                .plus(StorageTypeCapacity.finite(25));
        StorageTypeCapacity oneCreative = finite.plus(StorageTypeCapacity.unlimitedCapacity());
        StorageTypeCapacity multipleCreative = oneCreative.plus(StorageTypeCapacity.unlimitedCapacity());

        if (zero.finiteTypeSlots() != 0 || zero.unlimited()) {
            helper.fail("Zero capacity must be finite zero");
            return;
        }
        if (finite.finiteTypeSlots() != 35 || finite.unlimited()) {
            helper.fail("Finite capacities must add exactly");
            return;
        }
        if (!oneCreative.unlimited() || oneCreative.finiteTypeSlots() != 35
                || !multipleCreative.equals(oneCreative)) {
            helper.fail("One or multiple Creative Storage Units must produce the same unlimited result");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void one_connected_creative_unit_makes_the_loaded_network_unlimited(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos creativeUnitPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(creativeUnitPos,
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            StorageTypeCapacity capacity = core.getTypeCapacity();
            if (!capacity.unlimited() || capacity.finiteTypeSlots() != 0
                    || !core.getConnectedBlocks().contains(creativeUnitPos)) {
                helper.fail("A connected Creative Storage Unit must provide explicit unlimited capacity");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void unlimited_network_accepts_more_component_types_than_all_finite_tiers(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(),
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            int finiteTierTotal = 10 + 25 + 50 + 100 + 200 + 400;
            for (int damage = 0; damage <= finiteTierTotal; damage++) {
                ItemStack variant = new ItemStack(Items.NETHERITE_PICKAXE);
                variant.setDamageValue(damage);
                if (core.insertItem(variant) != 1) {
                    helper.fail("Unlimited capacity rejected component variant " + damage);
                    return;
                }
            }
            if (core.getTypeCount() != finiteTierTotal + 1) {
                helper.fail("Expected " + (finiteTierTotal + 1)
                        + " distinct component types, got " + core.getTypeCount());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void creative_capacity_is_shared_by_items_fluids_and_power(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(),
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            IFluidHandler fluids = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, corePos, null);
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, corePos, null);
            if (fluids == null || energy == null) {
                helper.fail("Creative network did not expose typed resource capabilities");
                return;
            }
            for (int variant = 0; variant < 16; variant++) {
                ItemStack item = new ItemStack(Items.DIAMOND);
                item.set(DataComponents.CUSTOM_NAME, Component.literal("Creative Item " + variant));
                FluidStack fluid = new FluidStack(Fluids.WATER, 250);
                fluid.set(DataComponents.CUSTOM_NAME, Component.literal("Creative Fluid " + variant));
                if (core.insertItem(item) != 1
                        || fluids.fill(fluid, IFluidHandler.FluidAction.EXECUTE) != 250) {
                    helper.fail("Creative capacity rejected typed variant " + variant);
                    return;
                }
            }
            if (energy.receiveEnergy(4_000, false) != 4_000
                    || core.getTypeCount() != 33
                    || !core.getTypeCapacity().unlimited()) {
                helper.fail("Creative capacity was not shared by item, fluid, and power types");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void removing_and_reconnecting_creative_capacity_preserves_over_capacity_storage(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos finiteUnitPos = corePos.east();
        BlockPos creativeUnitPos = corePos.west();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(finiteUnitPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(creativeUnitPos,
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            for (int damage = 0; damage < 11; damage++) {
                ItemStack variant = new ItemStack(Items.DIAMOND_PICKAXE);
                variant.setDamageValue(damage);
                if (core.insertItem(variant) != 1) {
                    helper.fail("Could not seed over-capacity component variant " + damage);
                    return;
                }
            }

            level.removeBlock(creativeUnitPos, false);
            core.rebuildNetwork(level);
            if (core.getTypeCapacity().unlimited() || core.getTotalTypeSlots() != 10
                    || core.getTypeCount() != 11) {
                helper.fail("Removing creative capacity must keep 11 stored types over finite capacity 10");
                return;
            }
            ItemStack existing = new ItemStack(Items.DIAMOND_PICKAXE);
            existing.setDamageValue(10);
            if (core.extractItem(ItemKey.of(existing), 1).getCount() != 1) {
                helper.fail("Existing over-capacity items must remain extractable");
                return;
            }
            if (core.insertItem(new ItemStack(Items.DIRT)) != 0) {
                helper.fail("A new type must be rejected while the finite network remains full");
                return;
            }

            level.setBlock(creativeUnitPos,
                    MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
            core.rebuildNetwork(level);
            if (!core.getTypeCapacity().unlimited()
                    || core.insertItem(new ItemStack(Items.DIRT)) != 1) {
                helper.fail("Reconnecting creative capacity must immediately accept new types");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void creative_capacity_accepts_a_new_crafting_output_type(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(),
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            core.getMachineContainer().setItem(
                    MachineEnergyTable.CRAFTING_TABLE_SLOT, new ItemStack(Items.CRAFTING_TABLE));
            if (core.insertItem(new ItemStack(Items.OAK_PLANKS, 2)) != 2) {
                helper.fail("Could not seed crafting ingredients into creative-capacity storage");
                return;
            }
            var stickRecipe = level.getRecipeManager().byKey(
                    ResourceLocation.withDefaultNamespace("stick")
            ).orElse(null);
            if (stickRecipe == null) {
                helper.fail("Vanilla stick recipe not found");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new CraftingTerminalMenu(208, player.getInventory(), core);
            menu.getCurrentRecipes().add(stickRecipe);
            menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
            menu.craftItem(1, player);

            if (core.getItemCount(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STICK))) != 4) {
                helper.fail("Unlimited capacity must accept a new crafting output type in storage");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void creative_capacity_incremental_add_matches_full_rebuild(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos finiteUnitPos = corePos.east();
        BlockPos firstCreativePos = finiteUnitPos.east();
        BlockPos secondCreativePos = firstCreativePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(finiteUnitPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            level.setBlock(firstCreativePos,
                    MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
            if (!core.tryIncrementalAdd(level, firstCreativePos)) {
                helper.fail("Incremental topology must accept a connected Creative Storage Unit");
                return;
            }
            StorageTypeCapacity oneCreativeIncremental = core.getTypeCapacity();
            core.rebuildNetwork(level);
            if (!oneCreativeIncremental.equals(core.getTypeCapacity())) {
                helper.fail("One-Creative incremental capacity must match full rebuild");
                return;
            }

            level.setBlock(secondCreativePos,
                    MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
            if (!core.tryIncrementalAdd(level, secondCreativePos)) {
                helper.fail("Incremental topology must accept a second Creative Storage Unit");
                return;
            }
            StorageTypeCapacity multipleCreativeIncremental = core.getTypeCapacity();
            core.rebuildNetwork(level);
            if (!multipleCreativeIncremental.equals(core.getTypeCapacity())
                    || !multipleCreativeIncremental.equals(oneCreativeIncremental)
                    || !multipleCreativeIncremental.unlimited()
                    || multipleCreativeIncremental.finiteTypeSlots() != 10) {
                helper.fail("Mixed and multiple-Creative topology must have rebuild parity");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void creative_capacity_does_not_bypass_multi_core_conflict(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos firstCorePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos creativeUnitPos = firstCorePos.east();
        BlockPos secondCorePos = creativeUnitPos.east();
        level.setBlock(firstCorePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(creativeUnitPos,
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(firstCorePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("First Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            if (core.insertItem(new ItemStack(Items.STONE)) != 1) {
                helper.fail("Could not seed the valid one-Core network");
                return;
            }

            level.setBlock(secondCorePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            core.rebuildNetwork(level);
            if (!core.isConflicted() || !core.getTypeCapacity().unlimited()
                    || core.insertItem(new ItemStack(Items.DIRT)) != 0
                    || !core.extractItem(ItemKey.of(new ItemStack(Items.STONE)), 1).isEmpty()) {
                helper.fail("Creative capacity must not bypass multi-Core conflict behavior");
                return;
            }

            level.removeBlock(secondCorePos, false);
            core.rebuildNetwork(level);
            if (core.isConflicted()
                    || core.extractItem(ItemKey.of(new ItemStack(Items.STONE)), 1).getCount() != 1) {
                helper.fail("Resolving the Core conflict must restore access without deleting items");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void unlimited_capacity_survives_save_load_and_both_menu_sync_paths(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(),
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            if (core.insertItem(new ItemStack(Items.STONE, 7)) != 7) {
                helper.fail("Could not seed storage before save/load");
                return;
            }
            var saved = new net.minecraft.nbt.CompoundTag();
            core.saveAdditional(saved, level.registryAccess());
            level.removeBlockEntity(corePos);
            var reloaded = new StorageCoreBlockEntity(
                    corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState());
            reloaded.loadAdditional(saved, level.registryAccess());
            level.setBlockEntity(reloaded);
        });

        helper.runAfterDelay(4, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Reloaded Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            if (!core.getTypeCapacity().unlimited()
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 7) {
                helper.fail("Save/load must preserve items and topology-derived unlimited capacity");
                return;
            }

            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var storageServer = new StorageTerminalMenu(209, player.getInventory(), core);
            var storageBuffer = menuBuffer(level, corePos, false);
            var storageClient = new StorageTerminalMenu(
                    MagicStorage.STORAGE_TERMINAL_MENU.get(), 210, player.getInventory(), storageBuffer);
            if (!syncMenuData(storageServer, storageClient, 13)) {
                helper.fail("Storage menu data-slot parity must include explicit unlimited capacity");
                return;
            }

            var craftingServer = new CraftingTerminalMenu(211, player.getInventory(), core);
            var craftingBuffer = menuBuffer(level, corePos, true);
            var craftingClient = new CraftingTerminalMenu(212, player.getInventory(), craftingBuffer);
            if (!syncMenuData(craftingServer, craftingClient, 392)) {
                helper.fail("Crafting menu data-slot parity must include explicit unlimited capacity");
                return;
            }
            if (!storageServer.hasUnlimitedTypeCapacity()
                    || !storageClient.hasUnlimitedTypeCapacity()
                    || !craftingServer.hasUnlimitedTypeCapacity()
                    || !craftingClient.hasUnlimitedTypeCapacity()
                    || storageClient.getMaxTypes() != 0
                    || craftingClient.getMaxTypes() != 0) {
                helper.fail("Both terminal clients must receive explicit unlimited capacity after save/load");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void open_storage_menu_tracks_creative_disconnect_and_reconnect(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos creativeUnitPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(creativeUnitPos,
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var menu = new StorageTerminalMenu(213, player.getInventory(), core);
            if (!menu.hasUnlimitedTypeCapacity()) {
                helper.fail("Open Storage menu did not start with creative capacity");
                return;
            }

            level.removeBlock(creativeUnitPos, false);
            core.rebuildNetwork(level);
            menu.broadcastChanges();
            if (menu.hasUnlimitedTypeCapacity()) {
                helper.fail("Open Storage menu kept stale unlimited capacity after disconnect");
                return;
            }

            level.setBlock(creativeUnitPos,
                    MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
            core.rebuildNetwork(level);
            menu.broadcastChanges();
            if (!menu.hasUnlimitedTypeCapacity()) {
                helper.fail("Open Storage menu did not restore unlimited capacity after reconnect");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "registrationtests.empty")
    public static void creative_storage_unit_block_and_item_are_registered_without_a_recipe(GameTestHelper helper) {
        var level = helper.getLevel();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                MagicStorage.MODID, "creative_storage_unit");
        var blocks = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BLOCK);
        var items = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ITEM);
        if (blocks.get(id) != MagicStorage.CREATIVE_STORAGE_UNIT.get()
                || items.get(id) != MagicStorage.CREATIVE_STORAGE_UNIT_ITEM.get()
                || MagicStorage.CREATIVE_STORAGE_UNIT.get().asItem()
                        != MagicStorage.CREATIVE_STORAGE_UNIT_ITEM.get()) {
            helper.fail("Creative Storage Unit needs matching block/item command registration");
            return;
        }
        boolean hasRecipe = level.getRecipeManager().getRecipes().stream()
                .map(net.minecraft.world.item.crafting.RecipeHolder::value)
                .map(recipe -> recipe.getResultItem(level.registryAccess()))
                .anyMatch(stack -> stack.is(MagicStorage.CREATIVE_STORAGE_UNIT_ITEM.get()));
        if (hasRecipe) {
            helper.fail("Creative Storage Unit must not have a survival recipe");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void creative_storage_unit_has_no_inventory_or_item_generation(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos unitPos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(unitPos,
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(20, () -> {
            if (MagicStorage.CREATIVE_STORAGE_UNIT.get()
                    instanceof net.minecraft.world.level.block.EntityBlock
                    || level.getBlockEntity(unitPos) != null
                    || level.getBlockState(unitPos).isRandomlyTicking()) {
                helper.fail("Creative Storage Unit must not own inventory or ticking state");
                return;
            }
            if (!level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(unitPos)).isEmpty()) {
                helper.fail("Creative Storage Unit must never generate items");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void disconnecting_and_reconnecting_creative_unit_preserves_core_contents(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos finiteUnitPos = corePos.west();
        BlockPos bridgePos = corePos.east();
        BlockPos creativeUnitPos = bridgePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(finiteUnitPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(bridgePos, MagicStorage.STORAGE_TERMINAL.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(creativeUnitPos,
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            for (int damage = 0; damage < 11; damage++) {
                ItemStack variant = new ItemStack(Items.WOODEN_PICKAXE);
                variant.setDamageValue(damage);
                if (core.insertItem(variant) != 1) {
                    helper.fail("Could not seed disconnect over-capacity type " + damage);
                    return;
                }
            }

            level.removeBlock(bridgePos, false);
            core.rebuildNetwork(level);
            if (level.getBlockState(creativeUnitPos).isAir()
                    || core.getTypeCapacity().unlimited()
                    || core.getTotalTypeSlots() != 10
                    || core.getTypeCount() != 11
                    || core.insertItem(new ItemStack(Items.DIRT)) != 0) {
                helper.fail("Disconnected creative capacity must preserve finite over-capacity behavior");
                return;
            }

            level.setBlock(bridgePos,
                    MagicStorage.STORAGE_TERMINAL.get().defaultBlockState(), Block.UPDATE_ALL);
            core.rebuildNetwork(level);
            if (!core.getTypeCapacity().unlimited()
                    || core.insertItem(new ItemStack(Items.DIRT)) != 1) {
                helper.fail("Reconnecting the unchanged Creative Storage Unit must restore capacity");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void wrenching_creative_capacity_preserves_core_contents(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos finiteUnitPos = corePos.east();
        BlockPos creativeUnitPos = corePos.west();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(finiteUnitPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(creativeUnitPos,
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            for (int damage = 0; damage < 11; damage++) {
                ItemStack variant = new ItemStack(Items.IRON_PICKAXE);
                variant.setDamageValue(damage);
                if (core.insertItem(variant) != 1) {
                    helper.fail("Could not seed wrench over-capacity type " + damage);
                    return;
                }
            }

            var player = FakePlayerFactory.get(
                    level, new GameProfile(UUID.randomUUID(), "creative-unit-wrench-test"));
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
            player.setShiftKeyDown(true);
            var hit = new BlockHitResult(
                    Vec3.atCenterOf(creativeUnitPos), Direction.UP, creativeUnitPos, false);
            if (!WrenchActions.tryUse(level, player, InteractionHand.MAIN_HAND, hit).consumesAction()
                    || !level.getBlockState(creativeUnitPos).isAir()) {
                helper.fail("Sneak-wrench did not dismantle the Creative Storage Unit");
                return;
            }
            core.rebuildNetwork(level);
            boolean receivedUnit = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (player.getInventory().getItem(slot).is(MagicStorage.CREATIVE_STORAGE_UNIT_ITEM.get())) {
                    receivedUnit = true;
                    break;
                }
            }
            if (!receivedUnit || core.getTypeCapacity().unlimited()
                    || core.getTotalTypeSlots() != 10 || core.getTypeCount() != 11) {
                helper.fail("Wrench dismantle must return the unit and preserve over-capacity Core contents");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform", batch = "creative_explosion")
    public static void exploding_creative_capacity_preserves_core_contents(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos finiteUnitPos = corePos.east();
        BlockPos creativeUnitPos = corePos.west();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(finiteUnitPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(creativeUnitPos,
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            for (int damage = 0; damage < 11; damage++) {
                ItemStack variant = new ItemStack(Items.GOLDEN_PICKAXE);
                variant.setDamageValue(damage);
                if (core.insertItem(variant) != 1) {
                    helper.fail("Could not seed explosion over-capacity type " + damage);
                    return;
                }
            }

            var calculator = new net.minecraft.world.level.ExplosionDamageCalculator() {
                @Override
                public java.util.Optional<Float> getBlockExplosionResistance(
                        net.minecraft.world.level.Explosion explosion,
                        net.minecraft.world.level.BlockGetter reader,
                        BlockPos pos,
                        net.minecraft.world.level.block.state.BlockState state,
                        net.minecraft.world.level.material.FluidState fluid
                ) {
                    if (pos.equals(creativeUnitPos)) return java.util.Optional.of(0.0F);
                    if (state.isAir() && fluid.isEmpty()) return java.util.Optional.empty();
                    return java.util.Optional.of(10000.0F);
                }

                @Override
                public boolean shouldBlockExplode(
                        net.minecraft.world.level.Explosion explosion,
                        net.minecraft.world.level.BlockGetter reader,
                        BlockPos pos,
                        net.minecraft.world.level.block.state.BlockState state,
                        float power
                ) {
                    return pos.equals(creativeUnitPos);
                }
            };
            level.explode(
                    null,
                    null,
                    calculator,
                    Vec3.atCenterOf(creativeUnitPos),
                    2.0F,
                    false,
                    net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
            core.rebuildNetwork(level);
            if (!level.getBlockState(creativeUnitPos).isAir()
                    || core.getTypeCapacity().unlimited()
                    || core.getTotalTypeSlots() != 10
                    || core.getTypeCount() != 11) {
                helper.fail("Explosion must remove creative capacity without deleting Core contents");
                return;
            }
            level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(creativeUnitPos).inflate(8.0D)
            ).forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }

    @GameTest(
            template = "behavioraltests.platform",
            batch = "creative_chunk_unload",
            timeoutTicks = 400)
    public static void unloading_and_reloading_creative_capacity_preserves_core_contents(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 3, 1));
        ChunkPos anchorChunk = new ChunkPos(anchor);
        BlockPos corePos = null;
        BlockPos finiteUnitPos = null;
        BlockPos creativeUnitPos = null;
        for (Direction direction : new Direction[]{
                Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
            BlockPos remoteProbe = switch (direction) {
                case EAST -> new BlockPos(
                        anchorChunk.getMaxBlockX() - 1, anchor.getY(), anchorChunk.getMiddleBlockZ())
                        .relative(direction, 34);
                case WEST -> new BlockPos(
                        anchorChunk.getMinBlockX() + 1, anchor.getY(), anchorChunk.getMiddleBlockZ())
                        .relative(direction, 34);
                case SOUTH -> new BlockPos(
                        anchorChunk.getMiddleBlockX(), anchor.getY(), anchorChunk.getMaxBlockZ() - 1)
                        .relative(direction, 34);
                default -> new BlockPos(
                        anchorChunk.getMiddleBlockX(), anchor.getY(), anchorChunk.getMinBlockZ() + 1)
                        .relative(direction, 34);
            };
            ChunkPos candidateChunk = new ChunkPos(remoteProbe);
            boolean insideForcedFullRadius = false;
            for (long forcedChunkValue : level.getForcedChunks()) {
                ChunkPos forcedChunk = new ChunkPos(
                        ChunkPos.getX(forcedChunkValue),
                        ChunkPos.getZ(forcedChunkValue));
                if (Math.max(
                        Math.abs(candidateChunk.x - forcedChunk.x),
                        Math.abs(candidateChunk.z - forcedChunk.z)) <= 2) {
                    insideForcedFullRadius = true;
                    break;
                }
            }
            if (!insideForcedFullRadius) {
                int y = level.getMaxBuildHeight() - 2;
                creativeUnitPos = switch (direction) {
                    case EAST -> new BlockPos(candidateChunk.getMinBlockX(), y, candidateChunk.getMiddleBlockZ());
                    case WEST -> new BlockPos(candidateChunk.getMaxBlockX(), y, candidateChunk.getMiddleBlockZ());
                    case SOUTH -> new BlockPos(candidateChunk.getMiddleBlockX(), y, candidateChunk.getMinBlockZ());
                    default -> new BlockPos(candidateChunk.getMiddleBlockX(), y, candidateChunk.getMaxBlockZ());
                };
                corePos = creativeUnitPos.relative(direction.getOpposite());
                finiteUnitPos = corePos.relative(direction.getOpposite());
                break;
            }
        }
        if (corePos == null) {
            helper.fail("No adjacent unforced chunk available for unload coverage");
            return;
        }

        BlockPos finalCorePos = corePos;
        BlockPos finalFiniteUnitPos = finiteUnitPos;
        BlockPos finalCreativeUnitPos = creativeUnitPos;
        ChunkPos creativeChunk = new ChunkPos(creativeUnitPos);
        ChunkPos loadedCoreChunk = new ChunkPos(corePos);
        level.getChunkAt(corePos);
        var creativeLevelChunk = level.getChunkAt(creativeUnitPos);
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> irrelevantBoundary =
                fillChunkBoundaryPrefix(
                        creativeLevelChunk,
                        MagicStorage.MAX_NETWORK_BLOCKS,
                        MagicStorage.STORAGE_TERMINAL.get().defaultBlockState(),
                        creativeUnitPos);
        if (irrelevantBoundary.size() != MagicStorage.MAX_NETWORK_BLOCKS) {
            helper.fail("Could not saturate the irrelevant chunk boundary");
            return;
        }
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(finiteUnitPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(creativeUnitPos,
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);

        if (!(level.getBlockEntity(finalCorePos) instanceof StorageCoreBlockEntity core)) {
            helper.fail("Storage Core block entity missing");
            return;
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        core.getTypeCapacity().unlimited()
                                && core.getConnectedBlocks().contains(finalCreativeUnitPos),
                        "Waiting for initial remote Creative Storage Unit capacity"))
                .thenExecute(() -> {
                    for (int damage = 0; damage < 11; damage++) {
                        ItemStack variant = new ItemStack(Items.STONE_PICKAXE);
                        variant.setDamageValue(damage);
                        if (core.insertItem(variant) != 1) {
                            helper.fail("Could not seed unload over-capacity type " + damage);
                            return;
                        }
                    }
                    level.getChunkSource().removeRegionTicket(
                            net.minecraft.server.level.TicketType.UNKNOWN,
                            creativeChunk,
                            0,
                            creativeChunk);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(level.hasChunkAt(finalCorePos),
                            "Storage Core boundary chunk must remain loaded");
                    helper.assertFalse(level.hasChunkAt(finalCreativeUnitPos),
                            "Waiting for Creative Storage Unit chunk to unload: "
                                    + level.getChunkSource().getChunkDebugData(creativeChunk));
                    helper.assertTrue(!core.getTypeCapacity().unlimited()
                                    && core.getTotalTypeSlots() == 10
                                    && core.getTypeCount() == 11,
                            "Unloaded creative capacity must leave Core contents over finite capacity: capacity="
                                    + core.getTypeCapacity() + ", types=" + core.getTypeCount());
                })
                .thenExecute(() -> level.getChunkAt(finalCreativeUnitPos))
                .thenWaitUntil(() -> helper.assertTrue(
                        level.hasChunkAt(finalCreativeUnitPos)
                                && core.getTypeCapacity().unlimited()
                                && core.getTypeCount() == 11
                                && core.getConnectedBlocks().contains(finalCreativeUnitPos),
                        "Reloading the Creative Storage Unit chunk must restore unlimited capacity: capacity="
                                + core.getTypeCapacity() + ", connected="
                                + core.getConnectedBlocks().contains(finalCreativeUnitPos)))
                .thenExecute(() -> {
                    var reloadedChunk = level.getChunkAt(finalCreativeUnitPos);
                    for (var entry : irrelevantBoundary.entrySet()) {
                        setChunkBlockState(reloadedChunk, entry.getKey(), entry.getValue());
                    }
                    level.removeBlock(finalCreativeUnitPos, false);
                    level.removeBlock(finalFiniteUnitPos, false);
                    level.removeBlock(finalCorePos, false);
                    level.getChunkSource().removeRegionTicket(
                            net.minecraft.server.level.TicketType.UNKNOWN,
                            creativeChunk,
                            0,
                            creativeChunk);
                    level.getChunkSource().removeRegionTicket(
                            net.minecraft.server.level.TicketType.UNKNOWN,
                            loadedCoreChunk,
                            0,
                            loadedCoreChunk);
                })
                .thenSucceed();
    }

    private static java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> fillChunkBoundaryPrefix(
            net.minecraft.world.level.chunk.LevelChunk chunk,
            int count,
            net.minecraft.world.level.block.state.BlockState state,
            BlockPos excluded
    ) {
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> positions =
                new java.util.HashMap<>();
        ChunkPos chunkPos = chunk.getPos();
        for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight() && positions.size() < count; y++) {
            for (int offset = 0; offset < 16 && positions.size() < count; offset++) {
                BlockPos[] candidates = {
                        new BlockPos(chunkPos.getMinBlockX(), y, chunkPos.getMinBlockZ() + offset),
                        new BlockPos(chunkPos.getMaxBlockX(), y, chunkPos.getMinBlockZ() + offset),
                        new BlockPos(chunkPos.getMinBlockX() + offset, y, chunkPos.getMinBlockZ()),
                        new BlockPos(chunkPos.getMinBlockX() + offset, y, chunkPos.getMaxBlockZ())
                };
                for (BlockPos pos : candidates) {
                    if (!pos.equals(excluded) && !positions.containsKey(pos)) {
                        positions.put(pos.immutable(), chunk.getBlockState(pos));
                        setChunkBlockState(chunk, pos, state);
                        if (positions.size() == count) break;
                    }
                }
            }
        }
        return positions;
    }

    private static void setChunkBlockState(
            net.minecraft.world.level.chunk.LevelChunk chunk,
            BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state
    ) {
        chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(
                pos.getX() & 15,
                pos.getY() & 15,
                pos.getZ() & 15,
                state);
    }

    private static net.minecraft.network.RegistryFriendlyByteBuf menuBuffer(
            net.minecraft.world.level.Level level,
            BlockPos corePos,
            boolean includeMachineSnapshot
    ) {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        buffer.writeBlockPos(corePos);
        buffer.writeBlockPos(corePos);
        buffer.writeBoolean(false);
        if (includeMachineSnapshot) {
            MachineEnergyTable.writeSnapshot(buffer, MachineEnergyTable.entries());
        }
        return buffer;
    }

    private static boolean syncMenuData(
            net.minecraft.world.inventory.AbstractContainerMenu server,
            net.minecraft.world.inventory.AbstractContainerMenu client,
            int expectedSlots
    ) {
        java.util.List<net.minecraft.world.inventory.DataSlot> serverData = dataSlots(server);
        java.util.List<net.minecraft.world.inventory.DataSlot> clientData = dataSlots(client);
        if (serverData.size() != expectedSlots || clientData.size() != expectedSlots) return false;
        for (int i = 0; i < serverData.size(); i++) {
            clientData.get(i).set(serverData.get(i).get());
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<net.minecraft.world.inventory.DataSlot> dataSlots(
            net.minecraft.world.inventory.AbstractContainerMenu menu
    ) {
        try {
            var field = net.minecraft.world.inventory.AbstractContainerMenu.class.getDeclaredField("dataSlots");
            field.setAccessible(true);
            return (java.util.List<net.minecraft.world.inventory.DataSlot>) field.get(menu);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
