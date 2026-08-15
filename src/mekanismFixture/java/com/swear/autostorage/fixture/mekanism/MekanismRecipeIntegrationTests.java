package com.swear.autostorage.fixture.mekanism;

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.Action;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TerminalDisplayStack;
import com.swear.autostorage.TerminalResourceDisplay;
import com.swear.autostorage.TerminalResourceView;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.chemical.attribute.ChemicalFuel;
import com.swear.autostorage.MekanismChemicalCompat;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.world.entity.player.Player;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.basic.BasicCentrifugingRecipe;
import mekanism.api.recipes.basic.BasicChemicalCrystallizerRecipe;
import mekanism.api.recipes.basic.BasicChemicalDissolutionRecipe;
import mekanism.api.recipes.basic.BasicChemicalInfuserRecipe;
import mekanism.api.recipes.basic.BasicChemicalOxidizerRecipe;
import mekanism.api.recipes.basic.BasicCombinerRecipe;
import mekanism.api.recipes.basic.BasicCompressingRecipe;
import mekanism.api.recipes.basic.BasicCrushingRecipe;
import mekanism.api.recipes.basic.BasicElectrolysisRecipe;
import mekanism.api.recipes.basic.BasicEnrichingRecipe;
import mekanism.api.recipes.basic.BasicInjectingRecipe;
import mekanism.api.recipes.basic.BasicMetallurgicInfuserRecipe;
import mekanism.api.recipes.basic.BasicNucleosynthesizingRecipe;
import mekanism.api.recipes.basic.BasicPaintingRecipe;
import mekanism.api.recipes.basic.BasicPigmentExtractingRecipe;
import mekanism.api.recipes.basic.BasicPigmentMixingRecipe;
import mekanism.api.recipes.basic.BasicPressurizedReactionRecipe;
import mekanism.api.recipes.basic.BasicPurifyingRecipe;
import mekanism.api.recipes.basic.BasicRotaryRecipe;
import mekanism.api.recipes.basic.BasicSawmillRecipe;
import mekanism.api.recipes.basic.BasicSmeltingRecipe;
import mekanism.api.recipes.basic.BasicWashingRecipe;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.tile.machine.TileEntityNutritionalLiquifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(MekanismFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismRecipeIntegrationTests {
    @GameTest(template = "craftingtests.platform")
    public static void gas_generator_converts_gas_tank_to_exact_fe_and_work(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            Item tankItem = registeredItem(
                    helper, mekanismId("basic_chemical_tank"));
            ItemStack tank = new ItemStack(tankItem);
            IChemicalHandler handler = tank.getCapability(
                    MekanismChemicalCompat.CHEMICAL_ITEM_CAPABILITY);
            if (handler == null) {
                helper.fail("Basic chemical tank has no chemical capability");
                return;
            }
            handler.setChemicalInTank(
                    0, MekanismChemicals.HYDROGEN.asStack(32_000));
            ChemicalStack contents = handler.getChemicalInTank(0);
            if (contents.isEmpty() || contents.getAmount() != 32_000
                    || !contents.is(MekanismChemicals.HYDROGEN)) {
                helper.fail("Could not fill basic chemical tank with hydrogen: "
                        + contents.getAmount());
                return;
            }
            ChemicalFuel fuel = handler.getChemicalInTank(0).getData(
                    IMekanismDataMapTypes.INSTANCE.chemicalFuel());
            if (fuel == null || fuel.energyDensity() <= 0) {
                helper.fail("Hydrogen has no gas-burning fuel datamap");
                return;
            }
            long expectedFe = 32_000L * fuel.energyDensity();
            long expectedWork = (32_000L + 255L) / 256L;
            var menu = transformMenu(core, player, tank);
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(GAS_GENERATOR))
                    .findFirst()
                    .orElse(null);
            if (use == null
                    || use.amountPerItem() != expectedFe
                    || use.stationWorkPerItem() != expectedWork
                    || use.retainedItems().size() != 1
                    || !use.retainedItems().getFirst().is(tankItem)) {
                helper.fail("Gas generator transform use is missing or wrong");
                return;
            }
            selectTransform(menu, player, GAS_GENERATOR);
            if (menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("Gas generator must reject without an installed generator");
                return;
            }
            installTransformStation(core, player,
                    ResourceLocation.fromNamespaceAndPath(
                            "mekanismgenerators", "gas_burning_generator"));
            addCoreTicks(core, Math.toIntExact(expectedWork));
            boolean committed = menu.clickMenuButton(player, 2);
            long fe = core.getResourceAmount(StorageResourceKey.neoforgeEnergy());
            long work = core.getStationWork(GAS_GENERATOR);
            int emptyTanks = player.getInventory().countItem(tankItem);
            if (!committed
                    || fe != expectedFe
                    || work != 0
                    || emptyTanks != 1) {
                helper.fail("Gas generator committed the wrong FE/work/input transaction: "
                        + "committed=" + committed + " fe=" + fe
                        + " expected=" + expectedFe + " work=" + work
                        + " emptyTanks=" + emptyTanks + " slot="
                        + menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void energy_conversion_converts_redstone_to_instant_fe(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            var menu = transformMenu(core, player, new ItemStack(Items.REDSTONE));
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(ENERGY_CONVERSION))
                    .findFirst()
                    .orElse(null);
            if (use == null
                    || use.amountPerItem() != 10_000L
                    || use.stationWorkPerItem() != 0
                    || use.stationId() != null) {
                helper.fail("Energy conversion transform use is missing or wrong");
                return;
            }
            selectTransform(menu, player, ENERGY_CONVERSION);
            if (!menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 10_000L
                    || core.getStationWork(GAS_GENERATOR) != 0
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                            .getItem().isEmpty()) {
                helper.fail("Energy conversion committed the wrong FE transaction");
                return;
            }
            var blockMenu = transformMenu(
                    core, player, new ItemStack(Items.REDSTONE_BLOCK));
            var blockUse = blockMenu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(ENERGY_CONVERSION))
                    .findFirst()
                    .orElse(null);
            if (blockUse == null || blockUse.amountPerItem() != 90_000L) {
                helper.fail("Energy conversion block use is missing or wrong");
                return;
            }
            selectTransform(blockMenu, player, ENERGY_CONVERSION);
            if (!blockMenu.clickMenuButton(player, 2)
                    || core.getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 100_000L) {
                helper.fail("Energy conversion block committed the wrong FE");
                return;
            }
            helper.succeed();
        });
    }

    private static final ResourceLocation ENERGY_CONVERSION =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "mekanism_energy_conversion");
    private static final ResourceLocation CHEMICAL_CONVERSION =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "mekanism_chemical_conversion");
    @GameTest(template = "craftingtests.platform")
    public static void chemical_conversion_converts_dusts_to_instant_chemicals(
            GameTestHelper helper
    ) {
        withCore(helper, (level, core, player) -> {
            var menu = transformMenu(core, player, new ItemStack(Items.REDSTONE));
            var use = menu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(CHEMICAL_CONVERSION))
                    .findFirst()
                    .orElse(null);
            if (use == null
                    || use.stationWorkPerItem() != 0
                    || use.stationId() != null
                    || !use.output().kindId().equals(
                            StorageResourceKindApi.CHEMICAL_KIND)) {
                helper.fail("Chemical conversion redstone use is missing or wrong");
                return;
            }
            long expectedAmount = conversionAmount(helper, Items.REDSTONE);
            if (use.amountPerItem() != expectedAmount) {
                helper.fail("Chemical conversion redstone amount is wrong: "
                        + use.amountPerItem() + " != " + expectedAmount);
                return;
            }
            selectTransform(menu, player, CHEMICAL_CONVERSION);
            if (!menu.clickMenuButton(player, 2)
                    || core.getResourceAmount(use.output()) != expectedAmount
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                            .getItem().isEmpty()) {
                helper.fail("Chemical conversion redstone committed wrong amount");
                return;
            }
            var osmiumMenu = transformMenu(
                    core, player,
                    new ItemStack(mekItem("ingot_osmium")));
            var osmiumUse = osmiumMenu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(CHEMICAL_CONVERSION))
                    .findFirst()
                    .orElse(null);
            long osmiumAmount = conversionAmount(helper, mekItem("ingot_osmium"));
            if (osmiumUse == null
                    || osmiumUse.amountPerItem() != osmiumAmount) {
                helper.fail("Chemical conversion osmium use is missing or wrong");
                return;
            }
            selectTransform(osmiumMenu, player, CHEMICAL_CONVERSION);
            boolean committed = osmiumMenu.clickMenuButton(player, 2);
            if (!committed
                    || core.getResourceAmount(osmiumUse.output())
                            != osmiumAmount
                    || core.getResourceAmount(use.output())
                            != expectedAmount) {
                helper.fail("Chemical conversion osmium committed wrong amount: "
                        + "clicked=" + committed + " osmium="
                        + core.getResourceAmount(osmiumUse.output())
                        + " redstone=" + core.getResourceAmount(use.output()));
                return;
            }
            ItemStack enrichedRedstone = new ItemStack(
                    mekItem("enriched_redstone"));
            long enrichedAmount = conversionAmount(
                    helper, mekItem("enriched_redstone"));
            if (enrichedAmount != 80L) {
                helper.fail("Enriched redstone conversion must be 80 mb, got "
                        + enrichedAmount);
                return;
            }
            var enrichedMenu = transformMenu(
                    core, player, enrichedRedstone);
            var enrichedUse = enrichedMenu.getTransformUses().stream()
                    .filter(candidate -> candidate.id().equals(CHEMICAL_CONVERSION))
                    .findFirst()
                    .orElse(null);
            if (enrichedUse == null
                    || enrichedUse.amountPerItem() != 80L) {
                helper.fail("Enriched redstone use is missing or wrong");
                return;
            }
            selectTransform(enrichedMenu, player, CHEMICAL_CONVERSION);
            if (!enrichedMenu.clickMenuButton(player, 2)
                    || core.getResourceAmount(enrichedUse.output())
                            != expectedAmount + 80L) {
                helper.fail("Enriched redstone committed wrong amount");
                return;
            }
            helper.succeed();
        });
    }

    private static int countEnergyHolders(GameTestHelper helper) {
        int count = 0;
        for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
            if (holder.value()
                    instanceof mekanism.api.recipes.ItemStackToEnergyRecipe recipe
                    && mekanism.common.recipe.MekanismRecipeType
                            .ENERGY_CONVERSION.is(recipe.getType())) {
                count++;
            }
        }
        return count;
    }

    private static long conversionAmount(GameTestHelper helper, Item item) {
        for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
            if (holder.value() instanceof mekanism.api.recipes.ItemStackToChemicalRecipe recipe
                    && mekanism.common.recipe.MekanismRecipeType.CHEMICAL_CONVERSION
                            .is(recipe.getType())) {
                for (ItemStack input : recipe.getInput().getRepresentations()) {
                    if (input.is(item)) {
                        return recipe.getOutputDefinition().getFirst().getAmount();
                    }
                }
            }
        }
        return -1;
    }

    private static Item mekItem(String path) {
        return BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("mekanism", path));
    }

    private static final int TRANSFORM_PAGE_BUTTON = 15;
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final ResourceLocation GAS_GENERATOR =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "mekanism_gas_generator");

    private static void withCore(
            GameTestHelper helper,
            TransformFixtureAssertion assertion
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

    private static CraftingTerminalMenu transformMenu(
            StorageCoreBlockEntity core,
            Player player,
            ItemStack input
    ) {
        var menu = new CraftingTerminalMenu(
                601, player.getInventory(), core);
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

    private static void installTransformStation(
            StorageCoreBlockEntity core,
            Player player,
            ResourceLocation stationItemId
    ) {
        Item stationItem = BuiltInRegistries.ITEM.get(stationItemId);
        if (stationItem == Items.AIR) {
            throw new IllegalStateException("Missing Mekanism Generators item "
                    + stationItemId);
        }
        ItemStack station = new ItemStack(stationItem);
        var menu = new CraftingTerminalMenu(
                602, player.getInventory(), core);
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
        throw new IllegalStateException("Could not install Mekanism gas generator");
    }

    private static void addCoreTicks(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    @FunctionalInterface
    private interface TransformFixtureAssertion {
        void run(
                net.minecraft.server.level.ServerLevel level,
                StorageCoreBlockEntity core,
                Player player
        );
    }


    private static final int CRAFTABLE_PAGE_BUTTON = 6;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int NEXT_RESOURCE_VIEW_BUTTON = 26;
    private static final int SIMPLE_WORK = 200;
    private static final int CHEMICAL_OXIDIZER_WORK = 100;
    private static final int CHEMICAL_DISSOLUTION_WORK = 100;
    private static final int CHEMICAL_CRYSTALLIZER_WORK = 200;
    private static final int PIGMENT_EXTRACTOR_WORK = 100;
    private static final int PAINTING_WORK = 200;
    private static final int NUCLEOSYNTHESIZER_WORK = 40;
    private static final int REACTION_DURATION = 40;
    private static final long REACTION_ENERGY = 1_000;
    private static final long REACTION_TOTAL_ENERGY = REACTION_ENERGY * REACTION_DURATION;
    private static final long REACTION_FLUID = 250;
    private static final long REACTION_CHEMICAL = 500;
    private static final long REACTION_CHEMICAL_OUTPUT = 125;

    private static final StationSpec CRUSHER = station("crusher");
    private static final StationSpec ENRICHMENT_CHAMBER = station("enrichment_chamber");
    private static final StationSpec ENERGIZED_SMELTER = station("energized_smelter");
    private static final StationSpec OSMIUM_COMPRESSOR = station("osmium_compressor");
    private static final StationSpec COMBINER = station("combiner");
    private static final StationSpec PURIFICATION_CHAMBER = station("purification_chamber");
    private static final StationSpec CHEMICAL_INJECTION_CHAMBER =
            station("chemical_injection_chamber");
    private static final StationSpec METALLURGIC_INFUSER = station("metallurgic_infuser");
    private static final StationSpec PRECISION_SAWMILL = station("precision_sawmill");
    private static final StationSpec PRESSURIZED_REACTION_CHAMBER =
            station("pressurized_reaction_chamber");
    private static final StationSpec ROTARY_CONDENSENTRATOR =
            station("rotary_condensentrator");
    private static final StationSpec CHEMICAL_OXIDIZER = station("chemical_oxidizer");
    private static final StationSpec CHEMICAL_INFUSER = station("chemical_infuser");
    private static final StationSpec ELECTROLYTIC_SEPARATOR =
            station("electrolytic_separator");
    private static final StationSpec CHEMICAL_DISSOLUTION_CHAMBER =
            station("chemical_dissolution_chamber");
    private static final StationSpec CHEMICAL_WASHER = station("chemical_washer");
    private static final StationSpec CHEMICAL_CRYSTALLIZER =
            station("chemical_crystallizer");
    private static final StationSpec ISOTOPIC_CENTRIFUGE = station("isotopic_centrifuge");
    private static final StationSpec NUTRITIONAL_LIQUIFIER =
            station("nutritional_liquifier");
    private static final StationSpec ANTIPROTONIC_NUCLEOSYNTHESIZER =
            station("antiprotonic_nucleosynthesizer");
    private static final StationSpec PIGMENT_EXTRACTOR = station("pigment_extractor");
    private static final StationSpec PIGMENT_MIXER = station("pigment_mixer");
    private static final StationSpec PAINTING_MACHINE = station("painting_machine");
    private static final List<FactoryFamilySpec> FACTORY_FAMILIES = List.of(
            factoryFamily(ENERGIZED_SMELTER, "smelting"),
            factoryFamily(ENRICHMENT_CHAMBER, "enriching"),
            factoryFamily(CRUSHER, "crushing"),
            factoryFamily(OSMIUM_COMPRESSOR, "compressing"),
            factoryFamily(COMBINER, "combining"),
            factoryFamily(PURIFICATION_CHAMBER, "purifying"),
            factoryFamily(CHEMICAL_INJECTION_CHAMBER, "injecting"),
            factoryFamily(METALLURGIC_INFUSER, "infusing"),
            factoryFamily(PRECISION_SAWMILL, "sawing"));
    private static final List<FactoryTierSpec> FACTORY_TIERS = List.of(
            new FactoryTierSpec("basic", 3),
            new FactoryTierSpec("advanced", 5),
            new FactoryTierSpec("elite", 7),
            new FactoryTierSpec("ultimate", 9));

    private MekanismRecipeIntegrationTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void item_resource_keys_round_trip_all_mekanism_default_components(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), MekanismRecipeIntegrationTests.class);
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || !itemId.getNamespace().equals("mekanism")) continue;
            ItemStack source = new ItemStack(item);
            StorageResourceKey key;
            try {
                key = StorageResourceKey.item(source, helper.getLevel().registryAccess());
            } catch (RuntimeException exception) {
                helper.fail("Could not encode default components for " + itemId + ": "
                        + exception.getMessage());
                return;
            }
            ItemStack restored = key.itemStack(helper.getLevel().registryAccess())
                    .orElse(ItemStack.EMPTY);
            if (!ItemStack.isSameItemSameComponents(source, restored)) {
                helper.fail("Mekanism default item components did not round-trip for " + itemId);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void all_factory_backed_families_accept_basic_and_factory_variants_at_exact_rates(
            GameTestHelper helper
    ) {
        for (FactoryFamilySpec family : FACTORY_FAMILIES) {
            MachineDescriptor descriptor = MachineEnergyTable.get(
                    family.station().descriptorId());
            if (descriptor == null) {
                helper.fail("Missing factory-backed descriptor: "
                        + family.station().descriptorId());
                return;
            }
            if (descriptor.category() != MachineCategory.PROCESS
                    || !descriptor.isPolymorphic()
                    || descriptor.variants().size() != FACTORY_TIERS.size() + 1) {
                helper.fail("Factory-backed descriptor did not expose exactly one basic machine "
                        + "and four factory tiers: " + family.station().descriptorId());
                return;
            }
            Component expectedLabel = Component.translatable(
                    "factory.mekanism." + family.factoryTypePath());
            if (!descriptor.stationLabel().equals(expectedLabel)) {
                helper.fail("Factory-backed descriptor does not use its shared recipe family label: "
                        + family.station().descriptorId() + ", expected="
                        + expectedLabel.getString() + ", actual="
                        + descriptor.stationLabel().getString());
                return;
            }
            if (!assertVariantRate(
                    helper, descriptor, family.station().itemId(), MachineWorkRate.ONE)) {
                return;
            }
            for (FactoryTierSpec tier : FACTORY_TIERS) {
                if (!assertVariantRate(
                        helper,
                        descriptor,
                        family.factoryItemId(tier),
                        MachineWorkRate.of(tier.processes(), 1))) {
                    return;
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void factory_backed_descriptors_fail_closed_for_unknown_and_missing_items(
            GameTestHelper helper
    ) {
        Item unknownMachine = registeredItem(helper, mekanismId("digital_miner"));
        ResourceLocation missingId = mekanismId("missing_auto_storage_factory");
        Item missingItem = BuiltInRegistries.ITEM.get(missingId);
        if (missingItem != Items.AIR) {
            helper.fail("Missing-item fixture unexpectedly resolved a registered item: " + missingId);
            return;
        }
        for (FactoryFamilySpec family : FACTORY_FAMILIES) {
            MachineDescriptor descriptor = MachineEnergyTable.get(
                    family.station().descriptorId());
            if (descriptor == null) {
                helper.fail("Missing factory-backed descriptor before fail-closed check: "
                        + family.station().descriptorId());
                return;
            }
            ItemStack unknownStack = new ItemStack(unknownMachine);
            ItemStack missingStack = new ItemStack(missingItem);
            if (descriptor.accepts(unknownStack)
                    || descriptor.rateFor(unknownStack).isPresent()
                    || descriptor.accepts(missingStack)
                    || descriptor.rateFor(missingStack).isPresent()) {
                helper.fail("Factory-backed descriptor accepted an unknown or missing item: "
                        + family.station().descriptorId());
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void crushing_preserves_sized_input_and_exact_output(GameTestHelper helper) {
        ResourceLocation recipeId = recipeId("crushing_sized");
        RecipeHolder<BasicCrushingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicCrushingRecipe(
                        IngredientCreatorAccess.item().from(Items.COBBLESTONE, 2),
                        new ItemStack(Items.GRAVEL, 3)));
        withFixture(helper, holder, CRUSHER, context -> assertSimpleRecipe(
                helper, context, recipeId, Items.COBBLESTONE, 2, Items.GRAVEL, 3));
    }

    @GameTest(template = "craftingtests.platform")
    public static void enriching_preserves_sized_input_and_exact_output(GameTestHelper helper) {
        ResourceLocation recipeId = recipeId("enriching_sized");
        RecipeHolder<BasicEnrichingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicEnrichingRecipe(
                        IngredientCreatorAccess.item().from(Items.REDSTONE, 3),
                        new ItemStack(Items.GLOWSTONE_DUST, 4)));
        withFixture(helper, holder, ENRICHMENT_CHAMBER, context -> assertSimpleRecipe(
                helper, context, recipeId, Items.REDSTONE, 3, Items.GLOWSTONE_DUST, 4));
    }

    @GameTest(template = "craftingtests.platform")
    public static void smelting_preserves_sized_input_and_exact_output(GameTestHelper helper) {
        ResourceLocation recipeId = recipeId("smelting_sized");
        RecipeHolder<BasicSmeltingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicSmeltingRecipe(
                        IngredientCreatorAccess.item().from(Items.RAW_IRON, 2),
                        new ItemStack(Items.IRON_INGOT, 2)));
        withFixture(helper, holder, ENERGIZED_SMELTER, context -> assertSimpleRecipe(
                helper, context, recipeId, Items.RAW_IRON, 2, Items.IRON_INGOT, 2));
    }

    @GameTest(template = "craftingtests.platform")
    public static void combining_consumes_both_sized_item_inputs(GameTestHelper helper) {
        ResourceLocation recipeId = recipeId("combining_sized");
        RecipeHolder<BasicCombinerRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicCombinerRecipe(
                        IngredientCreatorAccess.item().from(Items.COBBLESTONE, 2),
                        IngredientCreatorAccess.item().from(Items.FLINT, 3),
                        new ItemStack(Items.DEEPSLATE, 4)));
        withFixture(helper, holder, COMBINER, context -> {
            seedItem(context.core(), Items.COBBLESTONE, 2);
            seedItem(context.core(), Items.FLINT, 3);
            addWork(context, SIMPLE_WORK);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craft(context, recipeId)) {
                helper.fail("Combining did not discover and commit both sized item inputs");
                return;
            }
            if (itemCount(context.core(), Items.COBBLESTONE) != 0
                    || itemCount(context.core(), Items.FLINT) != 0
                    || inventoryCount(context, Items.DEEPSLATE) != 4
                    || stationWork(context) != 0) {
                helper.fail("Combining changed the wrong item counts or station work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void compressing_basic_machine_generates_one_work_per_tick(GameTestHelper helper) {
        assertCompressingRate(helper, OSMIUM_COMPRESSOR.itemId(), 1);
    }

    @GameTest(template = "craftingtests.platform")
    public static void compressing_basic_factory_generates_three_work_per_tick(
            GameTestHelper helper
    ) {
        assertCompressingRate(helper, factoryItemId("basic", "compressing"), 3);
    }

    @GameTest(template = "craftingtests.platform")
    public static void compressing_advanced_factory_generates_five_work_per_tick(
            GameTestHelper helper
    ) {
        assertCompressingRate(helper, factoryItemId("advanced", "compressing"), 5);
    }

    @GameTest(template = "craftingtests.platform")
    public static void compressing_elite_factory_generates_seven_work_per_tick(
            GameTestHelper helper
    ) {
        assertCompressingRate(helper, factoryItemId("elite", "compressing"), 7);
    }

    @GameTest(template = "craftingtests.platform")
    public static void compressing_ultimate_factory_generates_nine_work_per_tick(
            GameTestHelper helper
    ) {
        assertCompressingRate(helper, factoryItemId("ultimate", "compressing"), 9);
    }

    @GameTest(template = "craftingtests.platform")
    public static void purifying_consumes_sized_item_and_chemical_inputs(GameTestHelper helper) {
        ResourceLocation recipeId = recipeId("purifying_sized");
        Holder<Chemical> oxygen = chemical("oxygen");
        RecipeHolder<BasicPurifyingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicPurifyingRecipe(
                        IngredientCreatorAccess.item().from(Items.RAW_GOLD, 2),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(oxygen, 75)),
                        new ItemStack(Items.GOLD_INGOT, 3),
                        false));
        withFixture(helper, holder, PURIFICATION_CHAMBER, context -> assertChemicalRecipe(
                helper, context, recipeId, Items.RAW_GOLD, 2, oxygen, 75,
                Items.GOLD_INGOT, 3, SIMPLE_WORK, 0));
    }

    @GameTest(template = "craftingtests.platform")
    public static void injecting_consumes_sized_item_and_chemical_inputs(GameTestHelper helper) {
        ResourceLocation recipeId = recipeId("injecting_sized");
        Holder<Chemical> hydrogenChloride = chemical("hydrogen_chloride");
        RecipeHolder<BasicInjectingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicInjectingRecipe(
                        IngredientCreatorAccess.item().from(Items.RAW_COPPER, 3),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(hydrogenChloride, 125)),
                        new ItemStack(Items.COPPER_INGOT, 4),
                        false));
        withFixture(helper, holder, CHEMICAL_INJECTION_CHAMBER, context -> assertChemicalRecipe(
                helper, context, recipeId, Items.RAW_COPPER, 3, hydrogenChloride, 125,
                Items.COPPER_INGOT, 4, SIMPLE_WORK, 0));
    }

    @GameTest(template = "craftingtests.platform")
    public static void infusing_consumes_sized_item_and_infusion_inputs(GameTestHelper helper) {
        ResourceLocation recipeId = recipeId("infusing_sized");
        Holder<Chemical> redstone = chemical("redstone");
        RecipeHolder<BasicMetallurgicInfuserRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicMetallurgicInfuserRecipe(
                        IngredientCreatorAccess.item().from(Items.IRON_INGOT, 2),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(redstone, 40)),
                        new ItemStack(Items.REDSTONE_TORCH, 2),
                        false));
        withFixture(helper, holder, METALLURGIC_INFUSER, context -> assertChemicalRecipe(
                helper, context, recipeId, Items.IRON_INGOT, 2, redstone, 40,
                Items.REDSTONE_TORCH, 2, SIMPLE_WORK, 0));
    }

    @GameTest(template = "craftingtests.platform")
    public static void sawing_preserves_deterministic_main_output(GameTestHelper helper) {
        ResourceLocation recipeId = recipeId("sawing_deterministic");
        RecipeHolder<BasicSawmillRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicSawmillRecipe(
                        IngredientCreatorAccess.item().from(Items.OAK_LOG, 2),
                        new ItemStack(Items.OAK_PLANKS, 8),
                        ItemStack.EMPTY,
                        0));
        withFixture(helper, holder, PRECISION_SAWMILL, context -> assertSimpleRecipe(
                helper, context, recipeId, Items.OAK_LOG, 2, Items.OAK_PLANKS, 8));
    }

    @GameTest(template = "craftingtests.platform")
    public static void probabilistic_sawing_fails_closed_without_mutation(GameTestHelper helper) {
        ResourceLocation recipeId = recipeId("sawing_probabilistic");
        RecipeHolder<BasicSawmillRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicSawmillRecipe(
                        IngredientCreatorAccess.item().from(Items.OAK_LOG, 2),
                        new ItemStack(Items.OAK_PLANKS, 8),
                        new ItemStack(Items.STICK, 2),
                        0.5));
        withFixture(helper, holder, PRECISION_SAWMILL, context -> {
            seedItem(context.core(), Items.OAK_LOG, 2);
            addWork(context, SIMPLE_WORK);
            if (select(context, recipeId) || craft(context, recipeId)) {
                helper.fail("Probabilistic sawing recipe became selectable or craftable");
                return;
            }
            if (itemCount(context.core(), Items.OAK_LOG) != 2
                    || inventoryCount(context, Items.OAK_PLANKS) != 0
                    || inventoryCount(context, Items.STICK) != 0
                    || stationWork(context) != SIMPLE_WORK) {
                helper.fail("Rejected probabilistic sawing recipe mutated storage or station work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void reaction_commits_item_fluid_chemical_energy_work_and_cooutput(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("reaction_all_resources");
        withFixture(helper, itemOutputReaction(recipeId), PRESSURIZED_REACTION_CHAMBER, context -> {
            ReactionKeys keys = seedReactionInputs(context, 1);
            addWork(context, REACTION_DURATION);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craft(context, recipeId)) {
                helper.fail("Pressurized Reaction did not commit every typed requirement");
                return;
            }
            if (itemCount(context.core(), Items.CLAY_BALL) != 0
                    || context.core().getResourceAmount(keys.water()) != 0
                    || context.core().getResourceAmount(keys.hydrogen()) != 0
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getResourceAmount(keys.oxygen()) != REACTION_CHEMICAL_OUTPUT
                    || inventoryCount(context, Items.DIAMOND) != 2
                    || stationWork(context) != 0) {
                helper.fail("Pressurized Reaction lost or miscounted a typed input, output, or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void reaction_missing_item_is_atomic_noop(GameTestHelper helper) {
        assertMissingReactionResource(helper, MissingResource.ITEM);
    }

    @GameTest(template = "craftingtests.platform")
    public static void reaction_missing_fluid_is_atomic_noop(GameTestHelper helper) {
        assertMissingReactionResource(helper, MissingResource.FLUID);
    }

    @GameTest(template = "craftingtests.platform")
    public static void reaction_missing_chemical_is_atomic_noop(GameTestHelper helper) {
        assertMissingReactionResource(helper, MissingResource.CHEMICAL);
    }

    @GameTest(template = "craftingtests.platform")
    public static void reaction_missing_energy_is_atomic_noop(GameTestHelper helper) {
        assertMissingReactionResource(helper, MissingResource.ENERGY);
    }

    @GameTest(template = "craftingtests.platform")
    public static void reaction_missing_station_work_is_atomic_noop(GameTestHelper helper) {
        assertMissingReactionResource(helper, MissingResource.STATION_WORK);
    }

    @GameTest(template = "craftingtests.platform")
    public static void reaction_full_destination_rolls_back_all_resources_and_work(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("reaction_full_destination");
        withFixture(helper, itemOutputReaction(recipeId), PRESSURIZED_REACTION_CHAMBER, context -> {
            ReactionKeys keys = seedReactionInputs(context, 2);
            for (Item filler : List.of(
                    Items.STONE,
                    Items.DIRT,
                    Items.SAND,
                    Items.OAK_LOG,
                    Items.IRON_INGOT,
                    Items.GOLD_INGOT)) {
                seedItem(context.core(), filler, 1);
            }
            if (context.core().getTypeCount() != context.core().getTotalTypeSlots()) {
                helper.fail("Reaction rollback fixture did not fill storage type capacity");
                return;
            }
            for (int slot = 0; slot < context.player().getInventory().getContainerSize(); slot++) {
                context.player().getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            addWork(context, REACTION_DURATION);
            if (!select(context, recipeId)) {
                helper.fail("Could not select Pressurized Reaction before rollback check");
                return;
            }
            if (craft(context, recipeId)) {
                helper.fail("Full destination unexpectedly accepted Pressurized Reaction outputs");
                return;
            }
            if (itemCount(context.core(), Items.CLAY_BALL) != 4
                    || context.core().getResourceAmount(keys.water()) != REACTION_FLUID * 2
                    || context.core().getResourceAmount(keys.hydrogen()) != REACTION_CHEMICAL * 2
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy())
                    != REACTION_TOTAL_ENERGY * 2
                    || context.core().getResourceAmount(keys.oxygen()) != 0
                    || inventoryCount(context, Items.DIAMOND) != 0
                    || stationWork(context) != REACTION_DURATION) {
                helper.fail("Failed Pressurized Reaction delivery changed resources or station work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void chemical_only_reaction_lists_selects_and_commits_to_core(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("reaction_chemical_only");
        withFixture(helper, chemicalOnlyReaction(recipeId), PRESSURIZED_REACTION_CHAMBER, context -> {
            ReactionKeys keys = seedReactionInputs(context, 1);
            addWork(context, REACTION_DURATION);
            context.menu().clickMenuButton(context.player(), CRAFTABLE_PAGE_BUTTON);
            for (int view = 0; view < 3; view++) {
                context.menu().clickMenuButton(context.player(), NEXT_RESOURCE_VIEW_BUTTON);
            }
            context.menu().refreshDisplayItems(context.core());
            if (context.menu().getResourceView() != TerminalResourceView.GAS) {
                helper.fail("Craftable did not accept the Gas resource view");
                return;
            }
            int outputSlot = findResourceSlot(context.menu(), keys.oxygen());
            if (outputSlot < 0) {
                helper.fail("Chemical-only output was absent from the Gas Craftable view");
                return;
            }
            context.menu().clicked(outputSlot, 0, ClickType.PICKUP, context.player());
            if (!TerminalResourceDisplay.key(context.menu().getSelectedStack())
                    .filter(keys.oxygen()::equals).isPresent()
                    || !TerminalResourceDisplay.key(
                    context.menu().getRecipePresentation().output())
                    .filter(keys.oxygen()::equals).isPresent()
                    || context.menu().getRecipePresentation().inputs().stream()
                    .filter(TerminalResourceDisplay::isTyped)
                    .noneMatch(input -> TerminalResourceDisplay.key(input)
                            .filter(keys.hydrogen()::equals).isPresent()
                            && TerminalDisplayStack.amount(input) == REACTION_CHEMICAL)
                    || context.menu().getIngredientPreview().stream()
                    .noneMatch(input -> TerminalResourceDisplay.key(input.stack())
                            .filter(keys.hydrogen()::equals).isPresent()
                            && input.required() == REACTION_CHEMICAL)
                    || TerminalDisplayStack.amount(
                    context.menu().getRecipePresentation().output())
                    != REACTION_CHEMICAL_OUTPUT
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1) {
                helper.fail("Chemical-only output did not preserve its exact preview key and amount");
                return;
            }
            if (context.menu().handleRecipeRequest(
                    context.level(), recipeId, 1,
                    CraftingDestination.INVENTORY, context.player())
                    || context.menu().handleRecipeRequest(
                    context.level(), recipeId, 1,
                    CraftingDestination.CURSOR, context.player())) {
                helper.fail("Chemical-only output accepted an item-only destination");
                return;
            }
            if (itemCount(context.core(), Items.CLAY_BALL) != 2
                    || context.core().getResourceAmount(keys.water()) != REACTION_FLUID
                    || context.core().getResourceAmount(keys.hydrogen()) != REACTION_CHEMICAL
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy())
                    != REACTION_TOTAL_ENERGY
                    || context.core().getResourceAmount(keys.oxygen()) != 0
                    || stationWork(context) != REACTION_DURATION) {
                helper.fail("Rejected chemical-only destination partially mutated the transaction");
                return;
            }
            if (!context.menu().handleRecipeRequest(
                    context.level(), recipeId, 1,
                    CraftingDestination.STORAGE, context.player())) {
                helper.fail("Chemical-only output did not commit to explicit Storage destination");
                return;
            }
            if (itemCount(context.core(), Items.CLAY_BALL) != 0
                    || context.core().getResourceAmount(keys.water()) != 0
                    || context.core().getResourceAmount(keys.hydrogen()) != 0
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getResourceAmount(keys.oxygen()) != REACTION_CHEMICAL_OUTPUT
                    || stationWork(context) != 0
                    || inventoryCount(context, Items.BREWING_STAND) != 0) {
                helper.fail("Chemical-only Reaction changed the wrong resource or item counts");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void chemical_only_reaction_full_core_is_atomic_noop(GameTestHelper helper) {
        ResourceLocation recipeId = recipeId("reaction_chemical_only_full_core");
        withFixture(helper, chemicalOnlyReaction(recipeId), PRESSURIZED_REACTION_CHAMBER, context -> {
            ReactionKeys keys = seedReactionInputs(context, 2);
            for (Item filler : List.of(
                    Items.STONE,
                    Items.DIRT,
                    Items.SAND,
                    Items.OAK_LOG,
                    Items.IRON_INGOT,
                    Items.GOLD_INGOT)) {
                seedItem(context.core(), filler, 1);
            }
            if (context.core().getTypeCount() != context.core().getTotalTypeSlots()) {
                helper.fail("Chemical-only capacity fixture did not fill storage type capacity");
                return;
            }
            addWork(context, REACTION_DURATION);
            long workBefore = stationWork(context);
            if (!select(context, recipeId)) {
                helper.fail("Chemical-only output could not be selected before capacity check");
                return;
            }
            if (context.menu().handleRecipeRequest(
                    context.level(), recipeId, 1,
                    CraftingDestination.STORAGE, context.player())) {
                helper.fail("Chemical-only output bypassed full Core type capacity");
                return;
            }
            if (itemCount(context.core(), Items.CLAY_BALL) != 4
                    || context.core().getResourceAmount(keys.water()) != REACTION_FLUID * 2
                    || context.core().getResourceAmount(keys.hydrogen()) != REACTION_CHEMICAL * 2
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy())
                    != REACTION_TOTAL_ENERGY * 2
                    || context.core().getResourceAmount(keys.oxygen()) != 0
                    || stationWork(context) != workBefore) {
                helper.fail("Rejected chemical-only output partially mutated the transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void chemical_only_reaction_output_overflow_is_atomic_noop(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("reaction_chemical_only_overflow");
        withFixture(helper, chemicalOnlyReaction(recipeId), PRESSURIZED_REACTION_CHAMBER, context -> {
            ReactionKeys keys = seedReactionInputs(context, 1);
            long oxygenBefore = Long.MAX_VALUE - REACTION_CHEMICAL_OUTPUT + 1;
            seedResource(context.core(), keys.oxygen(), oxygenBefore);
            addWork(context, REACTION_DURATION);
            if (!select(context, recipeId)) {
                helper.fail("Chemical-only overflow recipe could not be selected");
                return;
            }
            if (context.menu().handleRecipeRequest(
                    context.level(), recipeId, 1,
                    CraftingDestination.STORAGE, context.player())) {
                helper.fail("Chemical-only output overflow unexpectedly committed");
                return;
            }
            if (itemCount(context.core(), Items.CLAY_BALL) != 2
                    || context.core().getResourceAmount(keys.water()) != REACTION_FLUID
                    || context.core().getResourceAmount(keys.hydrogen()) != REACTION_CHEMICAL
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy())
                    != REACTION_TOTAL_ENERGY
                    || context.core().getResourceAmount(keys.oxygen()) != oxygenBefore
                    || stationWork(context) != REACTION_DURATION) {
                helper.fail("Chemical-only output overflow partially mutated the transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void rotary_fluid_to_chemical_commits_exact_direction(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("rotary_fluid_to_chemical");
        Holder<Chemical> oxygen = chemical("oxygen");
        RecipeHolder<BasicRotaryRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicRotaryRecipe(
                        IngredientCreatorAccess.fluid().from(new FluidStack(Fluids.WATER, 250)),
                        new ChemicalStack(oxygen, 125)));
        withFixture(helper, holder, ROTARY_CONDENSENTRATOR, context -> {
            StorageResourceKey water = fluidKey(context, new FluidStack(Fluids.WATER, 1));
            StorageResourceKey oxygenKey = chemicalKey(oxygen);
            seedResource(context.core(), water, 250);
            addWork(context, 1);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craftToStorage(context, recipeId)) {
                helper.fail("Rotary fluid-to-chemical recipe was not discovered and committed");
                return;
            }
            if (context.core().getResourceAmount(water) != 0
                    || context.core().getResourceAmount(oxygenKey) != 125
                    || stationWork(context) != 0) {
                helper.fail("Rotary fluid-to-chemical recipe changed the wrong resources or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void rotary_chemical_to_fluid_commits_exact_direction(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("rotary_chemical_to_fluid");
        Holder<Chemical> hydrogen = chemical("hydrogen");
        RecipeHolder<BasicRotaryRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicRotaryRecipe(
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(hydrogen, 100)),
                        new FluidStack(Fluids.WATER, 200)));
        withFixture(helper, holder, ROTARY_CONDENSENTRATOR, context -> {
            StorageResourceKey hydrogenKey = chemicalKey(hydrogen);
            StorageResourceKey water = fluidKey(context, new FluidStack(Fluids.WATER, 1));
            seedResource(context.core(), hydrogenKey, 100);
            addWork(context, 1);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craftToStorage(context, recipeId)) {
                helper.fail("Rotary chemical-to-fluid recipe was not discovered and committed");
                return;
            }
            if (context.core().getResourceAmount(hydrogenKey) != 0
                    || context.core().getResourceAmount(water) != 200
                    || stationWork(context) != 0) {
                helper.fail("Rotary chemical-to-fluid recipe changed the wrong resources or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void rotary_bidirectional_recipe_fails_closed_without_conditional_variants(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("rotary_bidirectional");
        Holder<Chemical> hydrogen = chemical("hydrogen");
        RecipeHolder<BasicRotaryRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicRotaryRecipe(
                        IngredientCreatorAccess.fluid().from(new FluidStack(Fluids.WATER, 1)),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(hydrogen, 1)),
                        new ChemicalStack(hydrogen, 1),
                        new FluidStack(Fluids.WATER, 1)));
        withFixture(helper, holder, ROTARY_CONDENSENTRATOR, context -> {
            StorageResourceKey hydrogenKey = chemicalKey(hydrogen);
            StorageResourceKey water = fluidKey(context, new FluidStack(Fluids.WATER, 1));
            seedResource(context.core(), water, 1);
            seedResource(context.core(), hydrogenKey, 1);
            addWork(context, 1);
            if (select(context, recipeId) || craftToStorage(context, recipeId)) {
                helper.fail("Bidirectional Rotary recipe was exposed without conditional plans");
                return;
            }
            if (context.core().getResourceAmount(water) != 1
                    || context.core().getResourceAmount(hydrogenKey) != 1
                    || stationWork(context) != 1) {
                helper.fail("Rejected bidirectional Rotary recipe mutated resources or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void oxidizing_commits_item_to_chemical_with_exact_duration(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("oxidizing_item_to_chemical");
        Holder<Chemical> oxygen = chemical("oxygen");
        RecipeHolder<BasicChemicalOxidizerRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicChemicalOxidizerRecipe(
                        IngredientCreatorAccess.item().from(Items.REDSTONE, 2),
                        new ChemicalStack(oxygen, 75)));
        withFixture(helper, holder, CHEMICAL_OXIDIZER, context -> {
            StorageResourceKey oxygenKey = chemicalKey(oxygen);
            seedItem(context.core(), Items.REDSTONE, 2);
            addWork(context, CHEMICAL_OXIDIZER_WORK);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craftToStorage(context, recipeId)) {
                helper.fail("Chemical Oxidizer recipe was not discovered and committed");
                return;
            }
            if (itemCount(context.core(), Items.REDSTONE) != 0
                    || context.core().getResourceAmount(oxygenKey) != 75
                    || stationWork(context) != 0) {
                helper.fail("Chemical Oxidizer changed the wrong resources or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void chemical_infusing_commits_two_chemical_inputs(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("chemical_infusing_two_inputs");
        Holder<Chemical> hydrogen = chemical("hydrogen");
        Holder<Chemical> chlorine = chemical("chlorine");
        Holder<Chemical> hydrogenChloride = chemical("hydrogen_chloride");
        RecipeHolder<BasicChemicalInfuserRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicChemicalInfuserRecipe(
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(hydrogen, 20)),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(chlorine, 30)),
                        new ChemicalStack(hydrogenChloride, 40)));
        withFixture(helper, holder, CHEMICAL_INFUSER, context -> {
            StorageResourceKey hydrogenKey = chemicalKey(hydrogen);
            StorageResourceKey chlorineKey = chemicalKey(chlorine);
            StorageResourceKey outputKey = chemicalKey(hydrogenChloride);
            seedResource(context.core(), hydrogenKey, 20);
            seedResource(context.core(), chlorineKey, 30);
            addWork(context, 1);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craftToStorage(context, recipeId)) {
                helper.fail("Chemical Infuser recipe was not discovered and committed");
                return;
            }
            if (context.core().getResourceAmount(hydrogenKey) != 0
                    || context.core().getResourceAmount(chlorineKey) != 0
                    || context.core().getResourceAmount(outputKey) != 40
                    || stationWork(context) != 0) {
                helper.fail("Chemical Infuser changed the wrong resources or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void separating_commits_both_chemical_outputs_and_energy_multiplier_work(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("separating_two_outputs");
        Holder<Chemical> hydrogen = chemical("hydrogen");
        Holder<Chemical> oxygen = chemical("oxygen");
        RecipeHolder<BasicElectrolysisRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicElectrolysisRecipe(
                        IngredientCreatorAccess.fluid().from(
                                new FluidStack(Fluids.WATER, 100)),
                        3,
                        new ChemicalStack(hydrogen, 200),
                        new ChemicalStack(oxygen, 100)));
        withFixture(helper, holder, ELECTROLYTIC_SEPARATOR, context -> {
            StorageResourceKey water = fluidKey(context, new FluidStack(Fluids.WATER, 1));
            StorageResourceKey hydrogenKey = chemicalKey(hydrogen);
            StorageResourceKey oxygenKey = chemicalKey(oxygen);
            seedResource(context.core(), water, 100);
            addWork(context, 3);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craftToStorage(context, recipeId)) {
                helper.fail("Electrolytic Separator recipe was not discovered and committed");
                return;
            }
            if (context.core().getResourceAmount(water) != 0
                    || context.core().getResourceAmount(hydrogenKey) != 200
                    || context.core().getResourceAmount(oxygenKey) != 100
                    || stationWork(context) != 0) {
                helper.fail("Electrolytic Separator lost an output or ignored its multiplier");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void dissolution_multiplies_per_tick_chemical_by_exact_duration(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("dissolution_per_tick");
        Holder<Chemical> sulfuricAcid = chemical("sulfuric_acid");
        Holder<Chemical> nuclearWaste = chemical("nuclear_waste");
        RecipeHolder<BasicChemicalDissolutionRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicChemicalDissolutionRecipe(
                        IngredientCreatorAccess.item().from(Items.RAW_IRON, 2),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(sulfuricAcid, 2)),
                        new ChemicalStack(nuclearWaste, 25),
                        true));
        withFixture(helper, holder, CHEMICAL_DISSOLUTION_CHAMBER, context -> {
            StorageResourceKey acidKey = chemicalKey(sulfuricAcid);
            StorageResourceKey wasteKey = chemicalKey(nuclearWaste);
            seedItem(context.core(), Items.RAW_IRON, 2);
            seedResource(
                    context.core(), acidKey, 2L * CHEMICAL_DISSOLUTION_WORK);
            addWork(context, CHEMICAL_DISSOLUTION_WORK);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craftToStorage(context, recipeId)) {
                helper.fail("Chemical Dissolution recipe was not discovered and committed");
                return;
            }
            if (itemCount(context.core(), Items.RAW_IRON) != 0
                    || context.core().getResourceAmount(acidKey) != 0
                    || context.core().getResourceAmount(wasteKey) != 25
                    || stationWork(context) != 0) {
                helper.fail("Chemical Dissolution changed the wrong resources or duration");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void washing_commits_fluid_and_chemical_inputs(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("washing_fluid_chemical");
        Holder<Chemical> sulfurDioxide = chemical("sulfur_dioxide");
        Holder<Chemical> sulfurTrioxide = chemical("sulfur_trioxide");
        RecipeHolder<BasicWashingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicWashingRecipe(
                        IngredientCreatorAccess.fluid().from(
                                new FluidStack(Fluids.WATER, 150)),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(sulfurDioxide, 50)),
                        new ChemicalStack(sulfurTrioxide, 75)));
        withFixture(helper, holder, CHEMICAL_WASHER, context -> {
            StorageResourceKey water = fluidKey(context, new FluidStack(Fluids.WATER, 1));
            StorageResourceKey inputKey = chemicalKey(sulfurDioxide);
            StorageResourceKey outputKey = chemicalKey(sulfurTrioxide);
            seedResource(context.core(), water, 150);
            seedResource(context.core(), inputKey, 50);
            addWork(context, 1);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craftToStorage(context, recipeId)) {
                helper.fail("Chemical Washer recipe was not discovered and committed");
                return;
            }
            if (context.core().getResourceAmount(water) != 0
                    || context.core().getResourceAmount(inputKey) != 0
                    || context.core().getResourceAmount(outputKey) != 75
                    || stationWork(context) != 0) {
                helper.fail("Chemical Washer changed the wrong resources or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void crystallizing_commits_chemical_to_item_with_exact_duration(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("crystallizing_chemical_to_item");
        Holder<Chemical> nuclearWaste = chemical("nuclear_waste");
        RecipeHolder<BasicChemicalCrystallizerRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicChemicalCrystallizerRecipe(
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(nuclearWaste, 125)),
                        new ItemStack(Items.DIAMOND, 2)));
        withFixture(helper, holder, CHEMICAL_CRYSTALLIZER, context -> {
            StorageResourceKey wasteKey = chemicalKey(nuclearWaste);
            seedResource(context.core(), wasteKey, 125);
            addWork(context, CHEMICAL_CRYSTALLIZER_WORK);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craft(context, recipeId)) {
                helper.fail("Chemical Crystallizer recipe was not discovered and committed");
                return;
            }
            if (context.core().getResourceAmount(wasteKey) != 0
                    || inventoryCount(context, Items.DIAMOND) != 2
                    || stationWork(context) != 0) {
                helper.fail("Chemical Crystallizer changed the wrong resources or duration");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void centrifuging_commits_chemical_to_chemical(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("centrifuging_chemical");
        Holder<Chemical> nuclearWaste = chemical("nuclear_waste");
        Holder<Chemical> spentWaste = chemical("spent_nuclear_waste");
        RecipeHolder<BasicCentrifugingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicCentrifugingRecipe(
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(nuclearWaste, 100)),
                        new ChemicalStack(spentWaste, 80)));
        withFixture(helper, holder, ISOTOPIC_CENTRIFUGE, context -> {
            StorageResourceKey inputKey = chemicalKey(nuclearWaste);
            StorageResourceKey outputKey = chemicalKey(spentWaste);
            seedResource(context.core(), inputKey, 100);
            addWork(context, 1);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craftToStorage(context, recipeId)) {
                helper.fail("Isotopic Centrifuge recipe was not discovered and committed");
                return;
            }
            if (context.core().getResourceAmount(inputKey) != 0
                    || context.core().getResourceAmount(outputKey) != 80
                    || stationWork(context) != 0) {
                helper.fail("Isotopic Centrifuge changed the wrong resources or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void nutritional_liquifier_remains_unregistered_without_a_recipe_type(
            GameTestHelper helper
    ) {
        ItemStack mushroomStew = new ItemStack(Items.MUSHROOM_STEW);
        var recipe = TileEntityNutritionalLiquifier.getRecipe(mushroomStew);
        if (recipe == null || recipe.getType() != null) {
            helper.fail("Mekanism 10.7.19 no longer exposes the audited null-type food recipe");
            return;
        }
        var output = recipe.getOutput(mushroomStew);
        if (output.fluid().isEmpty()
                || !output.optionalItem().is(Items.BOWL)
                || output.optionalItem().getCount() != 1) {
            helper.fail("Nutritional synthetic recipe lost its exact optional container output");
            return;
        }
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                NUTRITIONAL_LIQUIFIER.descriptorId())
                || MachineEnergyTable.get(NUTRITIONAL_LIQUIFIER.descriptorId()) != null) {
            helper.fail("Nutritional Liquifier was registered without discoverable server recipes");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void nucleosynthesizing_uses_recipe_duration_and_per_tick_chemical(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("nucleosynthesizing_duration");
        Holder<Chemical> antimatter = chemical("antimatter");
        RecipeHolder<BasicNucleosynthesizingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicNucleosynthesizingRecipe(
                        IngredientCreatorAccess.item().from(Items.COBBLESTONE, 2),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(antimatter, 2)),
                        new ItemStack(Items.NETHERITE_SCRAP, 2),
                        NUCLEOSYNTHESIZER_WORK,
                        true));
        withFixture(helper, holder, ANTIPROTONIC_NUCLEOSYNTHESIZER, context -> {
            StorageResourceKey antimatterKey = chemicalKey(antimatter);
            seedItem(context.core(), Items.COBBLESTONE, 2);
            seedResource(
                    context.core(), antimatterKey, 2L * NUCLEOSYNTHESIZER_WORK);
            addWork(context, NUCLEOSYNTHESIZER_WORK);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craft(context, recipeId)) {
                helper.fail("Nucleosynthesizer recipe was not discovered and committed");
                return;
            }
            if (itemCount(context.core(), Items.COBBLESTONE) != 0
                    || context.core().getResourceAmount(antimatterKey) != 0
                    || inventoryCount(context, Items.NETHERITE_SCRAP) != 2
                    || stationWork(context) != 0) {
                helper.fail("Nucleosynthesizer changed the wrong resources or duration");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void pigment_extracting_commits_item_to_chemical(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("pigment_extracting");
        Holder<Chemical> redPigment = chemical("red");
        RecipeHolder<BasicPigmentExtractingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicPigmentExtractingRecipe(
                        IngredientCreatorAccess.item().from(Items.RED_DYE, 2),
                        new ChemicalStack(redPigment, 150)));
        withFixture(helper, holder, PIGMENT_EXTRACTOR, context -> {
            StorageResourceKey outputKey = chemicalKey(redPigment);
            seedItem(context.core(), Items.RED_DYE, 2);
            addWork(context, PIGMENT_EXTRACTOR_WORK);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craftToStorage(context, recipeId)) {
                helper.fail("Pigment Extractor recipe was not discovered and committed");
                return;
            }
            if (itemCount(context.core(), Items.RED_DYE) != 0
                    || context.core().getResourceAmount(outputKey) != 150
                    || stationWork(context) != 0) {
                helper.fail("Pigment Extractor changed the wrong resources or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void pigment_mixing_commits_two_chemical_inputs(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("pigment_mixing");
        Holder<Chemical> redPigment = chemical("red");
        Holder<Chemical> bluePigment = chemical("blue");
        Holder<Chemical> purplePigment = chemical("purple");
        RecipeHolder<BasicPigmentMixingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicPigmentMixingRecipe(
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(redPigment, 20)),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(bluePigment, 30)),
                        new ChemicalStack(purplePigment, 40)));
        withFixture(helper, holder, PIGMENT_MIXER, context -> {
            StorageResourceKey redKey = chemicalKey(redPigment);
            StorageResourceKey blueKey = chemicalKey(bluePigment);
            StorageResourceKey purpleKey = chemicalKey(purplePigment);
            seedResource(context.core(), redKey, 20);
            seedResource(context.core(), blueKey, 30);
            addWork(context, 1);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craftToStorage(context, recipeId)) {
                helper.fail("Pigment Mixer recipe was not discovered and committed");
                return;
            }
            if (context.core().getResourceAmount(redKey) != 0
                    || context.core().getResourceAmount(blueKey) != 0
                    || context.core().getResourceAmount(purpleKey) != 40
                    || stationWork(context) != 0) {
                helper.fail("Pigment Mixer changed the wrong resources or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void painting_multiplies_per_tick_pigment_by_exact_duration(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = recipeId("painting_per_tick");
        Holder<Chemical> redPigment = chemical("red");
        RecipeHolder<BasicPaintingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicPaintingRecipe(
                        IngredientCreatorAccess.item().from(Items.WHITE_WOOL, 2),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(redPigment, 3)),
                        new ItemStack(Items.RED_WOOL, 2),
                        true));
        withFixture(helper, holder, PAINTING_MACHINE, context -> {
            StorageResourceKey pigmentKey = chemicalKey(redPigment);
            seedItem(context.core(), Items.WHITE_WOOL, 2);
            seedResource(context.core(), pigmentKey, 3L * PAINTING_WORK);
            addWork(context, PAINTING_WORK);
            if (!select(context, recipeId)
                    || context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 1
                    || !craft(context, recipeId)) {
                helper.fail("Painting Machine recipe was not discovered and committed");
                return;
            }
            if (itemCount(context.core(), Items.WHITE_WOOL) != 0
                    || context.core().getResourceAmount(pigmentKey) != 0
                    || inventoryCount(context, Items.RED_WOOL) != 2
                    || stationWork(context) != 0) {
                helper.fail("Painting Machine changed the wrong resources or duration");
                return;
            }
            helper.succeed();
        });
    }

    private static boolean assertVariantRate(
            GameTestHelper helper,
            MachineDescriptor descriptor,
            ResourceLocation itemId,
            MachineWorkRate expectedRate
    ) {
        Item item = registeredItem(helper, itemId);
        ItemStack stack = new ItemStack(item);
        MachineWorkRate actualRate = descriptor.rateFor(stack).orElse(null);
        if (!descriptor.accepts(stack) || !expectedRate.equals(actualRate)) {
            helper.fail("Factory-backed descriptor rate mismatch for " + itemId
                    + ": expected " + expectedRate + ", got " + actualRate);
            return false;
        }
        return true;
    }

    private static void assertCompressingRate(
            GameTestHelper helper,
            ResourceLocation stationItemId,
            int workPerTick
    ) {
        ResourceLocation recipeId = recipeId("compressing_rate_" + workPerTick);
        Holder<Chemical> osmium = chemical("osmium");
        RecipeHolder<BasicCompressingRecipe> holder = new RecipeHolder<>(recipeId,
                new BasicCompressingRecipe(
                        IngredientCreatorAccess.item().from(Items.RAW_IRON, 2),
                        IngredientCreatorAccess.chemicalStack().from(
                                new ChemicalStack(osmium, 50)),
                        new ItemStack(Items.IRON_BLOCK),
                        false));
        int ticks = Math.floorDiv(SIMPLE_WORK + workPerTick - 1, workPerTick);
        long generatedWork = Math.multiplyExact((long) ticks, workPerTick);
        withFixture(
                helper,
                holder,
                OSMIUM_COMPRESSOR,
                stationItemId,
                context -> {
                    seedItem(context.core(), Items.RAW_IRON, 2);
                    seedResource(context.core(), chemicalKey(osmium), 50);
                    addWork(context, ticks);
                    if (stationWork(context) != generatedWork) {
                        helper.fail("Installed " + stationItemId + " generated "
                                + stationWork(context) + " work after " + ticks
                                + " ticks; expected " + generatedWork);
                        return;
                    }
                    if (!select(context, recipeId)
                            || context.menu().computeCraftPreview(
                            context.core(), context.player()).craftable() != 1
                            || !craft(context, recipeId)) {
                        helper.fail("Compressing rate fixture did not preview and craft: "
                                + stationItemId);
                        return;
                    }
                    if (itemCount(context.core(), Items.RAW_IRON) != 0
                            || context.core().getResourceAmount(chemicalKey(osmium)) != 0
                            || inventoryCount(context, Items.IRON_BLOCK) != 1
                            || stationWork(context) != generatedWork - SIMPLE_WORK) {
                        helper.fail("Compressing rate fixture changed the wrong resources or work: "
                                + stationItemId);
                        return;
                    }
                    helper.succeed();
                });
    }

    private static void assertChemicalRecipe(
            GameTestHelper helper,
            FixtureContext context,
            ResourceLocation recipeId,
            Item input,
            int inputCount,
            Holder<Chemical> chemical,
            long chemicalAmount,
            Item output,
            int outputCount,
            int ticks,
            long expectedRemainingWork
    ) {
        StorageResourceKey chemicalKey = chemicalKey(chemical);
        seedItem(context.core(), input, inputCount);
        seedResource(context.core(), chemicalKey, chemicalAmount);
        addWork(context, ticks);
        if (!select(context, recipeId)
                || context.menu().computeCraftPreview(
                context.core(), context.player()).craftable() != 1
                || !craft(context, recipeId)) {
            helper.fail("Mekanism item-chemical recipe was not discovered and committed: "
                    + recipeId);
            return;
        }
        if (itemCount(context.core(), input) != 0
                || context.core().getResourceAmount(chemicalKey) != 0
                || inventoryCount(context, output) != outputCount
                || stationWork(context) != expectedRemainingWork) {
            helper.fail("Mekanism item-chemical recipe changed the wrong resources or work: "
                    + recipeId);
            return;
        }
        helper.succeed();
    }

    private static void assertSimpleRecipe(
            GameTestHelper helper,
            FixtureContext context,
            ResourceLocation recipeId,
            Item input,
            int inputCount,
            Item output,
            int outputCount
    ) {
        seedItem(context.core(), input, inputCount);
        addWork(context, SIMPLE_WORK);
        if (!select(context, recipeId)
                || context.menu().computeCraftPreview(
                context.core(), context.player()).craftable() != 1
                || !craft(context, recipeId)) {
            helper.fail("Mekanism item recipe was not discovered, previewed, and committed: " + recipeId);
            return;
        }
        if (itemCount(context.core(), input) != 0
                || inventoryCount(context, output) != outputCount
                || stationWork(context) != 0) {
            helper.fail("Mekanism item recipe changed the wrong counts or station work: " + recipeId);
            return;
        }
        helper.succeed();
    }

    private static void assertMissingReactionResource(
            GameTestHelper helper,
            MissingResource missing
    ) {
        ResourceLocation recipeId = recipeId("reaction_missing_" + missing.id());
        withFixture(helper, itemOutputReaction(recipeId), PRESSURIZED_REACTION_CHAMBER, context -> {
            ReactionKeys keys = reactionKeys(context);
            if (missing != MissingResource.ITEM) seedItem(context.core(), Items.CLAY_BALL, 2);
            if (missing != MissingResource.FLUID) {
                seedResource(context.core(), keys.water(), REACTION_FLUID);
            }
            if (missing != MissingResource.CHEMICAL) {
                seedResource(context.core(), keys.hydrogen(), REACTION_CHEMICAL);
            }
            if (missing != MissingResource.ENERGY) {
                seedResource(
                        context.core(), StorageResourceKey.neoforgeEnergy(), REACTION_TOTAL_ENERGY);
            }
            if (missing != MissingResource.STATION_WORK) addWork(context, REACTION_DURATION);

            long itemBefore = itemCount(context.core(), Items.CLAY_BALL);
            long waterBefore = context.core().getResourceAmount(keys.water());
            long chemicalBefore = context.core().getResourceAmount(keys.hydrogen());
            long energyBefore = context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy());
            long workBefore = stationWork(context);
            if (!select(context, recipeId)) {
                helper.fail("Missing-resource reaction could not be selected for preview: " + missing);
                return;
            }
            if (context.menu().computeCraftPreview(
                    context.core(), context.player()).craftable() != 0) {
                helper.fail("Missing-resource reaction previewed as craftable: " + missing);
                return;
            }
            if (craft(context, recipeId)) {
                helper.fail("Missing-resource reaction unexpectedly committed: " + missing);
                return;
            }
            if (itemCount(context.core(), Items.CLAY_BALL) != itemBefore
                    || context.core().getResourceAmount(keys.water()) != waterBefore
                    || context.core().getResourceAmount(keys.hydrogen()) != chemicalBefore
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy())
                    != energyBefore
                    || context.core().getResourceAmount(keys.oxygen()) != 0
                    || inventoryCount(context, Items.DIAMOND) != 0
                    || stationWork(context) != workBefore) {
                helper.fail("Missing-resource reaction partially mutated state: " + missing);
                return;
            }
            helper.succeed();
        });
    }

    private static RecipeHolder<BasicPressurizedReactionRecipe> itemOutputReaction(
            ResourceLocation recipeId
    ) {
        Holder<Chemical> hydrogen = chemical("hydrogen");
        Holder<Chemical> oxygen = chemical("oxygen");
        return new RecipeHolder<>(recipeId, new BasicPressurizedReactionRecipe(
                IngredientCreatorAccess.item().from(Items.CLAY_BALL, 2),
                IngredientCreatorAccess.fluid().from(
                        new FluidStack(Fluids.WATER, (int) REACTION_FLUID)),
                IngredientCreatorAccess.chemicalStack().from(
                        new ChemicalStack(hydrogen, REACTION_CHEMICAL)),
                REACTION_ENERGY,
                REACTION_DURATION,
                new ItemStack(Items.DIAMOND, 2),
                new ChemicalStack(oxygen, REACTION_CHEMICAL_OUTPUT)));
    }

    private static RecipeHolder<BasicPressurizedReactionRecipe> chemicalOnlyReaction(
            ResourceLocation recipeId
    ) {
        Holder<Chemical> hydrogen = chemical("hydrogen");
        Holder<Chemical> oxygen = chemical("oxygen");
        return new RecipeHolder<>(recipeId, new BasicPressurizedReactionRecipe(
                IngredientCreatorAccess.item().from(Items.CLAY_BALL, 2),
                IngredientCreatorAccess.fluid().from(
                        new FluidStack(Fluids.WATER, (int) REACTION_FLUID)),
                IngredientCreatorAccess.chemicalStack().from(
                        new ChemicalStack(hydrogen, REACTION_CHEMICAL)),
                REACTION_ENERGY,
                REACTION_DURATION,
                ItemStack.EMPTY,
                new ChemicalStack(oxygen, REACTION_CHEMICAL_OUTPUT)));
    }

    private static int findResourceSlot(CraftingTerminalMenu menu, StorageResourceKey key) {
        for (int slot = 0; slot < CraftingTerminalMenu.DISPLAY_SLOTS; slot++) {
            if (TerminalResourceDisplay.key(menu.getSlot(slot).getItem())
                    .filter(key::equals).isPresent()) return slot;
        }
        return -1;
    }

    private static ReactionKeys seedReactionInputs(FixtureContext context, int crafts) {
        ReactionKeys keys = reactionKeys(context);
        seedItem(context.core(), Items.CLAY_BALL, Math.multiplyExact(2, crafts));
        seedResource(context.core(), keys.water(), Math.multiplyExact(REACTION_FLUID, crafts));
        seedResource(
                context.core(), keys.hydrogen(), Math.multiplyExact(REACTION_CHEMICAL, crafts));
        seedResource(context.core(), StorageResourceKey.neoforgeEnergy(),
                Math.multiplyExact(REACTION_TOTAL_ENERGY, crafts));
        return keys;
    }

    private static ReactionKeys reactionKeys(FixtureContext context) {
        return new ReactionKeys(
                StorageResourceKey.fluid(
                        new FluidStack(Fluids.WATER, 1), context.level().registryAccess()),
                chemicalKey(chemical("hydrogen")),
                chemicalKey(chemical("oxygen")));
    }

    private static void withFixture(
            GameTestHelper helper,
            RecipeHolder<?> holder,
            StationSpec station,
            FixtureAssertion assertion
    ) {
        withFixture(helper, holder, station, station.itemId(), assertion);
    }

    private static void withFixture(
            GameTestHelper helper,
            RecipeHolder<?> holder,
            StationSpec station,
            ResourceLocation stationItemId,
            FixtureAssertion assertion
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(
                corePos.south(),
                AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            var manager = level.getRecipeManager();
            List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
            List<RecipeHolder<?>> registered = new ArrayList<>(original);
            registered.removeIf(recipe -> recipe.id().equals(holder.id()));
            registered.add(holder);
            manager.replaceRecipes(registered);
            try {
                core.rebuildNetwork(level);
                var player = helper.makeMockPlayer(GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(600, player.getInventory(), core);
                var context = new FixtureContext(level, core, player, menu, station);
                if (!installStation(context, stationItemId)) {
                    helper.fail("Could not install Mekanism station descriptor: " + stationItemId);
                    return;
                }
                if (!AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(station.descriptorId())) {
                    helper.fail("Mekanism recipe family was not registered: "
                            + station.descriptorId());
                    return;
                }
                assertion.run(context);
            } finally {
                manager.replaceRecipes(original);
            }
        });
    }

    private static boolean installStation(
            FixtureContext context,
            ResourceLocation stationItemId
    ) {
        Item stationItem = BuiltInRegistries.ITEM.get(stationItemId);
        ItemStack stationStack = new ItemStack(stationItem);
        if (stationStack.isEmpty()) return false;
        context.menu().clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = context.menu().getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(stationStack)) continue;
            slot.set(stationStack.copy());
            slot.setChanged();
            context.menu().clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
            return true;
        }
        context.menu().clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
        return false;
    }

    private static void addWork(FixtureContext context, int ticks) {
        for (int tick = 0; tick < ticks; tick++) context.core().tick();
    }

    private static long stationWork(FixtureContext context) {
        return context.core().getStationWork(context.station().descriptorId());
    }

    private static boolean select(FixtureContext context, ResourceLocation recipeId) {
        return context.menu().handleRecipeRequest(
                context.level(), recipeId, 1, CraftingDestination.NONE, context.player());
    }

    private static boolean craft(FixtureContext context, ResourceLocation recipeId) {
        return context.menu().handleRecipeRequest(
                context.level(), recipeId, 1, CraftingDestination.INVENTORY, context.player());
    }

    private static boolean craftToStorage(
            FixtureContext context,
            ResourceLocation recipeId
    ) {
        return context.menu().handleRecipeRequest(
                context.level(), recipeId, 1, CraftingDestination.STORAGE, context.player());
    }

    private static void seedItem(StorageCoreBlockEntity core, Item item, int count) {
        if (core.insertItem(new ItemStack(item, count)) != count) {
            throw new IllegalStateException("Could not seed " + item + " x" + count);
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

    private static int inventoryCount(FixtureContext context, Item item) {
        int count = 0;
        for (int slot = 0; slot < context.player().getInventory().getContainerSize(); slot++) {
            ItemStack stack = context.player().getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static Holder<Chemical> chemical(String path) {
        return MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(
                MekanismAPI.CHEMICAL_REGISTRY_NAME,
                ResourceLocation.fromNamespaceAndPath("mekanism", path))).orElseThrow();
    }

    private static StorageResourceKey chemicalKey(Holder<Chemical> chemical) {
        return StorageResourceKey.of(
                StorageResourceKindApi.CHEMICAL_KIND,
                chemical.unwrapKey().orElseThrow().location(),
                new CompoundTag());
    }

    private static StorageResourceKey fluidKey(FixtureContext context, FluidStack fluid) {
        return StorageResourceKey.fluid(fluid, context.level().registryAccess());
    }

    private static Item registeredItem(GameTestHelper helper, ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) helper.fail("Expected registered Mekanism item: " + id);
        return item;
    }

    private static StationSpec station(String path) {
        return new StationSpec(
                mekanismId(path),
                ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "mekanism_" + path));
    }

    private static FactoryFamilySpec factoryFamily(
            StationSpec station,
            String factoryTypePath
    ) {
        return new FactoryFamilySpec(station, factoryTypePath);
    }

    private static ResourceLocation factoryItemId(String tier, String factoryTypePath) {
        return mekanismId(tier + "_" + factoryTypePath + "_factory");
    }

    private static ResourceLocation mekanismId(String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", path);
    }

    private static ResourceLocation recipeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MekanismFixtureMod.MODID, path);
    }

    private enum MissingResource {
        ITEM("item"),
        FLUID("fluid"),
        CHEMICAL("chemical"),
        ENERGY("energy"),
        STATION_WORK("station_work");

        private final String id;

        MissingResource(String id) {
            this.id = id;
        }

        private String id() {
            return id;
        }
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(FixtureContext context);
    }

    private record StationSpec(ResourceLocation itemId, ResourceLocation descriptorId) {
    }

    private record FactoryFamilySpec(StationSpec station, String factoryTypePath) {
        private ResourceLocation factoryItemId(FactoryTierSpec tier) {
            return MekanismRecipeIntegrationTests.factoryItemId(
                    tier.registryPrefix(), factoryTypePath);
        }
    }

    private record FactoryTierSpec(String registryPrefix, int processes) {
    }

    private record ReactionKeys(
            StorageResourceKey water,
            StorageResourceKey hydrogen,
            StorageResourceKey oxygen
    ) {
    }

    private record FixtureContext(
            net.minecraft.server.level.ServerLevel level,
            StorageCoreBlockEntity core,
            net.minecraft.world.entity.player.Player player,
            CraftingTerminalMenu menu,
            StationSpec station
    ) {
    }
}
