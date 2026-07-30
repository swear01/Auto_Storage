package com.swear.autostorage.fixture.mekanism;

import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.BusConfiguration;
import com.swear.autostorage.BusFilterMode;
import com.swear.autostorage.BusMode;
import com.swear.autostorage.ExportBusBlock;
import com.swear.autostorage.ExportBusBlockEntity;
import com.swear.autostorage.ImportBusBlock;
import com.swear.autostorage.ImportBusBlockEntity;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageTerminalMenu;
import com.swear.autostorage.TerminalDisplayStack;
import com.swear.autostorage.TerminalContainerTransferDirection;
import com.swear.autostorage.TerminalHeldContainerTransferPacket;
import com.swear.autostorage.TerminalResourceDisplay;
import com.swear.autostorage.TerminalResourceView;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

@GameTestHolder(MekanismFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismChemicalIntegrationTests {
    private static final BlockCapability<IChemicalHandler, net.minecraft.core.Direction>
            CHEMICAL_CAPABILITY = BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class);
    private static final ItemCapability<IChemicalHandler, Void> CHEMICAL_ITEM_CAPABILITY =
            ItemCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class);

    private MekanismChemicalIntegrationTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void core_chemical_capability_preserves_long_amounts_and_simulation(
            GameTestHelper helper
    ) {
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
            IChemicalHandler handler = level.getCapability(CHEMICAL_CAPABILITY, corePos, null);
            if (handler == null) {
                helper.fail("Storage Core did not expose the Mekanism chemical capability");
                return;
            }
            var oxygenHolder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(
                    MekanismAPI.CHEMICAL_REGISTRY_NAME,
                    ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"))).orElseThrow();
            ChemicalStack oxygen = new ChemicalStack(oxygenHolder, 5_000_000_000L);

            ChemicalStack simulatedRemainder = handler.insertChemical(oxygen, Action.SIMULATE);
            if (!simulatedRemainder.isEmpty()
                    || core.getTypeCount() != 0
                    || oxygen.getAmount() != 5_000_000_000L) {
                helper.fail("Chemical insertion simulation mutated Core or caller stack");
                return;
            }
            ChemicalStack remainder = handler.insertChemical(oxygen, Action.EXECUTE);
            if (!remainder.isEmpty() || core.getTypeCount() != 1
                    || oxygen.getAmount() != 5_000_000_000L) {
                helper.fail("Chemical insertion did not store the exact long amount");
                return;
            }
            ResourceLocation stableChemicalKind = ResourceLocation.fromNamespaceAndPath(
                    "mekanism", "chemical");
            if (core.getResourceKeys().stream()
                    .noneMatch(key -> key.kindId().equals(stableChemicalKind))) {
                helper.fail("Chemical storage changed its persisted resource-kind identity");
                return;
            }
            if (handler.getChemicalTanks() != 1
                    || handler.getChemicalInTank(0).getAmount() != 5_000_000_000L
                    || !handler.getChemicalInTank(0).is(oxygenHolder)) {
                helper.fail("Chemical tank view lost identity or long amount");
                return;
            }
            ChemicalStack simulatedExtract = handler.extractChemical(
                    oxygen.copyWithAmount(2_000_000_000L), Action.SIMULATE);
            if (simulatedExtract.getAmount() != 2_000_000_000L
                    || handler.getChemicalInTank(0).getAmount() != 5_000_000_000L) {
                helper.fail("Chemical extraction simulation mutated Core");
                return;
            }
            ChemicalStack extracted = handler.extractChemical(
                    oxygen.copyWithAmount(2_000_000_000L), Action.EXECUTE);
            if (extracted.getAmount() != 2_000_000_000L
                    || handler.getChemicalInTank(0).getAmount() != 3_000_000_000L) {
                helper.fail("Chemical extraction execute returned the wrong amount");
                return;
            }
            var hydrogenHolder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(
                    MekanismAPI.CHEMICAL_REGISTRY_NAME,
                    ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen"))).orElseThrow();
            ChemicalStack hydrogen = new ChemicalStack(hydrogenHolder, 4_000L);
            if (!handler.isValid(0, hydrogen)) {
                helper.fail("Tank type validity incorrectly depended on the stored chemical");
                return;
            }
            ChemicalStack explicitTankRemainder = handler.insertChemical(
                    0, hydrogen, Action.EXECUTE);
            if (explicitTankRemainder.getAmount() != 4_000L
                    || !explicitTankRemainder.is(hydrogenHolder)
                    || hydrogen.getAmount() != 4_000L
                    || core.getTypeCount() != 1
                    || handler.getChemicalTanks() != 1
                    || handler.getChemicalInTank(0).getAmount() != 3_000_000_000L
                    || !handler.getChemicalInTank(0).is(oxygenHolder)) {
                helper.fail("Explicit tank accepted a different chemical type");
                return;
            }
            ChemicalStack hydrogenRemainder = handler.insertChemical(hydrogen, Action.EXECUTE);
            if (!hydrogenRemainder.isEmpty()
                    || hydrogen.getAmount() != 4_000L
                    || core.getTypeCount() != 2
                    || handler.getChemicalTanks() != 2) {
                helper.fail("Generic insertion did not create a second chemical type");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void buses_expose_configured_chemical_capabilities(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var importPos = corePos.east();
        var exportPos = corePos.west();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(importPos, AutoStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(exportPos, AutoStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Chemical Bus Core not found");
                return;
            }
            core.rebuildNetwork(level);
            BusConfiguration exportConfig = BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    BusConfiguration.ALL_SIDES_MASK,
                    true,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    1);
            CompoundTag configTag = new CompoundTag();
            exportConfig.save(configTag, level.registryAccess());
            ItemStack carrier = new ItemStack(AutoStorage.EXPORT_BUS_ITEM.get());
            BlockItem.setBlockEntityData(carrier, AutoStorage.EXPORT_BUS_BE.get(), configTag);
            if (!BlockItem.updateCustomBlockEntityTag(level, null, exportPos, carrier)) {
                helper.fail("Could not configure chemical Export Bus");
                return;
            }
            IChemicalHandler importer = level.getCapability(CHEMICAL_CAPABILITY, importPos, null);
            IChemicalHandler exporter = level.getCapability(CHEMICAL_CAPABILITY, exportPos, null);
            if (importer == null || exporter == null) {
                helper.fail("Chemical capabilities were missing from buses");
                return;
            }
            var oxygenHolder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(
                    MekanismAPI.CHEMICAL_REGISTRY_NAME,
                    ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"))).orElseThrow();
            ChemicalStack oxygen = new ChemicalStack(oxygenHolder, 1_000);
            if (!importer.insertChemical(oxygen, Action.SIMULATE).isEmpty()
                    || core.getTypeCount() != 0
                    || !importer.insertChemical(oxygen, Action.EXECUTE).isEmpty()
                    || !importer.extractChemical(oxygen.copyWithAmount(1), Action.EXECUTE).isEmpty()
                    || exporter.insertChemical(oxygen, Action.EXECUTE).getAmount() != 1_000
                    || exporter.extractChemical(
                    oxygen.copyWithAmount(400), Action.SIMULATE).getAmount() != 400
                    || exporter.extractChemical(
                    oxygen.copyWithAmount(400), Action.EXECUTE).getAmount() != 400
                    || exporter.getChemicalInTank(0).getAmount() != 600) {
                helper.fail("Chemical Bus direction, simulation, or exact amount diverged");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void chemical_bus_capability_cache_keeps_each_side_isolated(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var importPos = corePos.east();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(importPos, AutoStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity importer)) {
                helper.fail("Sided chemical Import fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            importer.setBusConfiguration(BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    1 << Direction.UP.ordinal(),
                    false,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    1));
            IChemicalHandler cachedUp = level.getCapability(
                    CHEMICAL_CAPABILITY, importPos, Direction.UP);
            if (cachedUp == null) {
                helper.fail("Could not cache the enabled upper chemical Bus side");
                return;
            }
            importer.setBusConfiguration(BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    1 << Direction.DOWN.ordinal(),
                    false,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    2));
            IChemicalHandler freshDown = level.getCapability(
                    CHEMICAL_CAPABILITY, importPos, Direction.DOWN);
            var oxygenHolder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(
                    MekanismAPI.CHEMICAL_REGISTRY_NAME,
                    ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"))).orElseThrow();
            ChemicalStack oxygen = new ChemicalStack(oxygenHolder, 250);
            if (freshDown == null
                    || cachedUp.insertChemical(oxygen, Action.EXECUTE).getAmount() != 250
                    || !freshDown.insertChemical(oxygen, Action.EXECUTE).isEmpty()
                    || level.getCapability(CHEMICAL_CAPABILITY, importPos, Direction.UP) != null
                    || core.getResourceKeys().stream().map(core::getResourceAmount)
                    .noneMatch(amount -> amount == 250)) {
                helper.fail("Chemical Bus capability cache reused the disabled side wrapper");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void directional_buses_transfer_one_bounded_sided_chemical_batch(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(2, 3, 2));
        var importPos = corePos.east();
        var sourcePos = importPos.east();
        var exportPos = corePos.west();
        var targetPos = exportPos.west();
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(importPos, AutoStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(sourcePos, Blocks.WHITE_GLAZED_TERRACOTTA.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), Block.UPDATE_ALL);
        level.setBlock(exportPos, AutoStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
        level.setBlock(targetPos, Blocks.WHITE_GLAZED_TERRACOTTA.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity importer)
                    || !(level.getBlockEntity(exportPos) instanceof ExportBusBlockEntity exporter)) {
                helper.fail("Directional chemical Bus fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            BusConfiguration configuration = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    1);
            CompoundTag importTag = new CompoundTag();
            configuration.save(importTag, level.registryAccess());
            ItemStack importCarrier = new ItemStack(AutoStorage.IMPORT_BUS_ITEM.get());
            BlockItem.setBlockEntityData(importCarrier, AutoStorage.IMPORT_BUS_BE.get(), importTag);
            CompoundTag exportTag = new CompoundTag();
            configuration.save(exportTag, level.registryAccess());
            ItemStack exportCarrier = new ItemStack(AutoStorage.EXPORT_BUS_ITEM.get());
            BlockItem.setBlockEntityData(exportCarrier, AutoStorage.EXPORT_BUS_BE.get(), exportTag);
            if (!BlockItem.updateCustomBlockEntityTag(level, null, importPos, importCarrier)
                    || !BlockItem.updateCustomBlockEntityTag(level, null, exportPos, exportCarrier)) {
                helper.fail("Could not configure directional chemical buses with deny-empty filters");
                return;
            }

            IChemicalHandler sourceFront = level.getCapability(
                    CHEMICAL_CAPABILITY, sourcePos, Direction.WEST);
            IChemicalHandler sourceWrongSide = level.getCapability(
                    CHEMICAL_CAPABILITY, sourcePos, Direction.EAST);
            IChemicalHandler targetFront = level.getCapability(
                    CHEMICAL_CAPABILITY, targetPos, Direction.EAST);
            if (sourceFront == null || targetFront == null) {
                StringBuilder availableSides = new StringBuilder();
                for (Direction side : Direction.values()) {
                    if (level.getCapability(CHEMICAL_CAPABILITY, sourcePos, side) != null) {
                        if (!availableSides.isEmpty()) availableSides.append(',');
                        availableSides.append(side.getName());
                    }
                }
                helper.fail("Mekanism fixture chemical front capability is missing; source sides="
                        + availableSides + ", source=" + level.getBlockState(sourcePos)
                        + ", target=" + level.getBlockState(targetPos));
                return;
            }
            var oxygenHolder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(
                    MekanismAPI.CHEMICAL_REGISTRY_NAME,
                    ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"))).orElseThrow();
            long batch = 1_000L;
            long initialSourceAmount = 2_500L;
            ChemicalStack sourceRemainder = sourceFront.insertChemical(
                    new ChemicalStack(oxygenHolder, initialSourceAmount), Action.EXECUTE);
            if (!sourceRemainder.isEmpty()) {
                helper.fail("Could not seed the Mekanism API source through its front");
                return;
            }
            if (sourceWrongSide != null
                    && !sourceWrongSide.extractChemical(batch, Action.SIMULATE).isEmpty()) {
                helper.fail("Mekanism fixture unexpectedly exposed chemical output away from its front");
                return;
            }
            ChemicalStack simulatedImport = sourceFront.extractChemical(batch, Action.SIMULATE);
            if (simulatedImport.getAmount() != batch
                    || sourceFront.getChemicalInTank(0).getAmount() != initialSourceAmount) {
                helper.fail("Source chemical simulation did not preserve one exact bounded batch");
                return;
            }

            StorageResourceKey oxygenKey = StorageResourceKey.of(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical"),
                    ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"),
                    new CompoundTag());
            importer.tick();
            if (core.getResourceAmount(oxygenKey) != batch
                    || sourceFront.getChemicalInTank(0).getAmount() != initialSourceAmount - batch) {
                helper.fail("Directional chemical Import did not execute one exact bounded batch from tank front");
                return;
            }

            ChemicalStack simulatedExportRemainder = targetFront.insertChemical(
                    new ChemicalStack(oxygenHolder, batch), Action.SIMULATE);
            if (!simulatedExportRemainder.isEmpty()
                    || !targetFront.getChemicalInTank(0).isEmpty()
                    || core.getResourceAmount(oxygenKey) != batch) {
                helper.fail("Target chemical simulation mutated state or rejected the exact batch");
                return;
            }
            exporter.tick();
            if (core.getResourceAmount(oxygenKey) != 0
                    || targetFront.getChemicalInTank(0).getAmount() != batch
                    || !targetFront.getChemicalInTank(0).is(oxygenHolder)) {
                helper.fail("Directional chemical Export did not execute one exact bounded batch into tank front");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void terminal_chemical_container_preserves_exact_amount_and_identity(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Chemical Terminal Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var oxygenHolder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(
                    MekanismAPI.CHEMICAL_REGISTRY_NAME,
                    ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"))).orElseThrow();
            ItemStack tank = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "ultimate_chemical_tank")));
            IChemicalHandler tankHandler = tank.getCapability(CHEMICAL_ITEM_CAPABILITY);
            if (tankHandler == null || !tankHandler.insertChemical(
                    new ChemicalStack(oxygenHolder, 500_000L), Action.EXECUTE).isEmpty()) {
                helper.fail("Could not prepare a real Mekanism chemical tank item");
                return;
            }
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            var menu = new StorageTerminalMenu(501, player.getInventory(), core);
            for (int step = 0; step < 3; step++) {
                menu.clickMenuButton(player, 26);
            }
            menu.setCarried(tank);
            StorageResourceKey oxygenKey = StorageResourceKey.of(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical"),
                    ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"),
                    new CompoundTag());

            if (!menu.handleHeldContainerTransfer(new TerminalHeldContainerTransferPacket(
                    menu.containerId,
                    menu.getStateId(),
                    0,
                    TerminalResourceView.GAS,
                    TerminalContainerTransferDirection.DEPOSIT), player)
                    || core.getResourceAmount(oxygenKey) != 500_000L) {
                helper.fail("Mekanism tank did not deposit its exact chemical amount");
                return;
            }
            List<ItemStack> gasEntries = core.getTerminalDisplayStacks(
                    "",
                    com.swear.autostorage.SortMode.NAME,
                    com.swear.autostorage.SortOrder.ASCENDING,
                    TerminalResourceView.GAS);
            ItemStack gasEntry = gasEntries.size() == 1 ? gasEntries.getFirst() : ItemStack.EMPTY;
            if (!TerminalResourceDisplay.key(gasEntry).filter(oxygenKey::equals).isPresent()
                    || TerminalDisplayStack.amount(gasEntry) != 500_000L
                    || !oxygenHolder.value().getTextComponent().equals(
                    gasEntry.get(DataComponents.CUSTOM_NAME))) {
                helper.fail("Gas entry did not preserve its typed chemical identity, amount, and name");
                return;
            }
            IChemicalHandler emptyHandler = menu.getCarried().getCapability(CHEMICAL_ITEM_CAPABILITY);
            if (emptyHandler == null || !emptyHandler.extractChemical(
                    Long.MAX_VALUE, Action.SIMULATE).isEmpty()) {
                helper.fail("Chemical deposit did not leave the tank empty");
                return;
            }
            if (!menu.handleHeldContainerTransfer(new TerminalHeldContainerTransferPacket(
                    menu.containerId,
                    menu.getStateId(),
                    0,
                    TerminalResourceView.GAS,
                    TerminalContainerTransferDirection.WITHDRAW), player)) {
                helper.fail("Mekanism tank chemical withdrawal was rejected");
                return;
            }
            IChemicalHandler filledHandler = menu.getCarried().getCapability(CHEMICAL_ITEM_CAPABILITY);
            ChemicalStack withdrawn = filledHandler == null
                    ? ChemicalStack.EMPTY
                    : filledHandler.extractChemical(Long.MAX_VALUE, Action.SIMULATE);
            if (withdrawn.getAmount() != 500_000L
                    || !withdrawn.is(oxygenHolder)
                    || core.getResourceAmount(oxygenKey) != 0) {
                helper.fail("Mekanism tank withdrawal lost chemical amount or identity");
                return;
            }
            helper.succeed();
        });
    }
}
