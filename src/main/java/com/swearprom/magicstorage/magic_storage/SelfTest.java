package com.swearprom.magicstorage.magic_storage;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SelfTest {
    private static int passed = 0;
    private static int failed = 0;

    static void runAll() {
        testItemKey();
        testStorageResourceLedger();
        testBusResourceEscrow();
        testResourceContainerTransferContract();
        testAxeSyntheticDiscovery();
        testAxeEnergyContract();
        testFuelTable();
        testFuelAutoPriority();
        testRecipeEnergyTable();
        testRecipeAdapterFoundation();
        testRecipeAdapterRegistryContract();
        testPublicRecipeFamilyFactory();
        testRecipeAdapterSnapshot();
        testBuiltInRecipeAdapterFamilies();
        testSmithingTrimAdapterFoundation();
        testRecipeAdapterCandidateCoverage();
        testRecipeAdapterReloadIdentity();
        testLargeTypedRecipePlan();
        testEnergyType();
        testMachineVariantContract();
        testMachineWorkAccumulator();
        testMachineRateFormatter();
        testEnergyCost();
        testFuelValue();
        testTransformProviderApi();
        testEnergyTypeUniqueness();
        testSortMode();
        testSortOrder();
        testSearchMode();
        testTerminalSearchQuery();
        testFuelSearchModel();
        testRecipeStationCycle();
        testTerminalPreferences();
        testTerminalSettingsPacketCodec();
        testTerminalResourceView();
        testTerminalProfilesAndCycleDirection();
        testTerminalAmountFormatter();
        testRecipeResourceAmountFormatter();
        testTerminalDisplayStack();
        testTerminalEntryComparator();
        testTerminalScrollbar();
        testAdaptiveTerminalLayout();

        MagicStorage.LOGGER.info("SelfTest: {} passed, {} failed, {} total",
                passed, failed, passed + failed);
        if (failed > 0) {
            MagicStorage.LOGGER.error("SelfTest: {} TESTS FAILED!", failed);
        }
    }

    private static void testMachineRateFormatter() {
        assertTrue("machine rate tooltip uses a fixed decimal for zero",
                MachineRateFormatter.format(MachineWorkRate.ZERO, 1).equals("0.00"));
        assertTrue("machine rate tooltip renders fractional rates as decimals",
                MachineRateFormatter.format(MachineWorkRate.of(5, 4), 1).equals("1.25"));
        assertTrue("machine rate tooltip keeps two decimal places after stack multiplication",
                MachineRateFormatter.format(MachineWorkRate.of(5, 4), 2).equals("2.50"));
        assertTrue("machine rate tooltip rounds repeating decimals consistently",
                MachineRateFormatter.format(MachineWorkRate.of(10, 9), 1).equals("1.11"));
        assertTrue("positive machine rates never render as zero",
                MachineRateFormatter.format(MachineWorkRate.of(1, 1_000), 1).equals("<0.01"));
        assertTrue("an empty machine slot still renders zero",
                MachineRateFormatter.format(MachineWorkRate.of(5, 4), 0).equals("0.00"));
        assertTrue("near-limit Processing aggregates keep an exact non-overflowing rate",
                MachineRateFormatter.format(
                        MachineWorkRate.of(1, 200), Integer.MAX_VALUE)
                        .equals("10737418.24"));
    }

    private static void testRecipeResourceAmountFormatter() {
        assertTrue("recipe resource amount preserves the full compact available value",
                RecipeResourceAmountFormatter.format(121_000, 200, false)
                        .available().equals("121K"));
        assertTrue("recipe resource amount preserves every required digit on its own line",
                RecipeResourceAmountFormatter.format(121_000, 200, false)
                        .required().equals("/200"));
        assertTrue("recipe resource amount keeps infinite availability explicit",
                RecipeResourceAmountFormatter.format(Long.MAX_VALUE, 1, true)
                        .available().equals("∞"));
        assertTrue("short recipe resource amounts stay on one line",
                RecipeResourceAmountFormatter.format(1, 1, false)
                        .inline().equals("1/1"));
        assertTrue("recipe resource amount bounds the largest exact long without truncation",
                RecipeResourceAmountFormatter.format(Long.MAX_VALUE, Long.MAX_VALUE, false)
                        .required().equals("/9.2E"));
    }

    private static void testLargeTypedRecipePlan() {
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        for (int input = 0; input < 19; input++) {
            builder.input(TypedRecipeInput.consume(StorageResourceKey.of(
                    ResourceLocation.fromNamespaceAndPath("test", "kind"),
                    ResourceLocation.fromNamespaceAndPath("test", "input_" + input),
                    new CompoundTag()), 1));
        }
        builder.output(TypedRecipeOutput.primary(StorageResourceKey.of(
                        StorageResourceKindApi.ITEM_KIND,
                        ResourceLocation.withDefaultNamespace("diamond"),
                        new CompoundTag()), 1))
                .presentationOutput(new ItemStack(Items.DIAMOND))
                .layout(3, 3, true);
        try {
            TypedRecipePlan plan = builder.build();
            assertTrue("typed recipe plans preserve more than nine exact inputs",
                    plan.inputs().size() == 19);
        } catch (IllegalArgumentException exception) {
            assertTrue("typed recipe plans preserve more than nine exact inputs", false);
        }
    }

    private static void testTransformProviderApi() {
        var fuel = TransformProviderApi.targets(MachineEnergyTable.entries()).stream()
                .filter(target -> target.id().equals(
                        TransformProviderApi.energyTargetId(EnergyType.FURNACE_FUEL)))
                .findFirst()
                .orElseThrow();
        assertTrue("Transform targets carry a localized produced-resource label",
                fuel.label().equals(
                        Component.translatable("gui.magic_storage.energy.furnace_fuel")));
    }

    private static void testItemKey() {
        var stack1 = new ItemStack(Items.DIAMOND_SWORD);
        var stack2 = new ItemStack(Items.DIAMOND_SWORD);
        var stack3 = new ItemStack(Items.IRON_SWORD);

        var key1 = ItemKey.of(stack1);
        var key2 = ItemKey.of(stack2);
        var key3 = ItemKey.of(stack3);
        var key1Again = ItemKey.of(stack1);

        assertTrue("same item = same key", key1.equals(key2));
        assertTrue("same item = same hashCode", key1.hashCode() == key2.hashCode());
        assertTrue("different item = different key", !key1.equals(key3));
        assertTrue("key is stable (same stack = same key)", key1.equals(key1Again));
        assertTrue("key = itself", key1.equals(key1));
        assertTrue("key != null", !key1.equals(null));
        assertTrue("primaryKey returns item", key1.item() == Items.DIAMOND_SWORD);
    }

    private static void testStorageResourceLedger() {
        ResourceLocation itemKind = ResourceLocation.fromNamespaceAndPath("magic_storage", "item");
        ResourceLocation fluidKind = ResourceLocation.fromNamespaceAndPath("magic_storage", "fluid");
        ResourceLocation powerKind = ResourceLocation.fromNamespaceAndPath("magic_storage", "neoforge_energy");
        ResourceLocation chemicalKind = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical");

        CompoundTag itemVariant = new CompoundTag();
        itemVariant.putString("component", "exact");
        StorageResourceKey item = StorageResourceKey.of(itemKind,
                ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"), itemVariant);
        itemVariant.putString("component", "mutated");
        CompoundTag exposedVariant = item.variantData();
        exposedVariant.putString("component", "also_mutated");
        assertTrue("typed resource keys defensively copy exact variant data",
                item.variantData().getString("component").equals("exact"));

        StorageResourceKey fluid = StorageResourceKey.of(fluidKind,
                ResourceLocation.fromNamespaceAndPath("minecraft", "water"), new CompoundTag());
        StorageResourceKey power = StorageResourceKey.of(powerKind,
                ResourceLocation.fromNamespaceAndPath("neoforge", "energy"), new CompoundTag());
        StorageResourceKey chemical = StorageResourceKey.of(chemicalKind,
                ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"), new CompoundTag());
        StorageResourceLedger ledger = new StorageResourceLedger();

        assertTrue("typed resource exact insertion accepts the requested amount",
                ledger.insert(item, 3, StorageTypeCapacity.finite(4), Action.EXECUTE) == 3
                        && ledger.amount(item) == 3 && ledger.typeCount() == 1);

        Map<StorageResourceKey, Long> mixed = new LinkedHashMap<>();
        mixed.put(item, -2L);
        mixed.put(fluid, 1_000L);
        mixed.put(power, 500L);
        mixed.put(chemical, 250L);
        assertTrue("mixed item fluid power and chemical transaction simulates without mutation",
                ledger.applyExact(mixed, StorageTypeCapacity.finite(4), Action.SIMULATE)
                        && ledger.amount(item) == 3
                        && ledger.amount(fluid) == 0
                        && ledger.typeCount() == 1);
        assertTrue("mixed item fluid power and chemical transaction commits atomically",
                ledger.applyExact(mixed, StorageTypeCapacity.finite(4), Action.EXECUTE)
                        && ledger.amount(item) == 1
                        && ledger.amount(fluid) == 1_000
                        && ledger.amount(power) == 500
                        && ledger.amount(chemical) == 250
                        && ledger.typeCount() == 4);

        Map<StorageResourceKey, Long> underflow = new LinkedHashMap<>();
        underflow.put(item, -2L);
        underflow.put(fluid, 1L);
        assertTrue("underflow rejects the complete mixed transaction without partial mutation",
                !ledger.applyExact(underflow, StorageTypeCapacity.finite(4), Action.EXECUTE)
                        && ledger.amount(item) == 1 && ledger.amount(fluid) == 1_000);

        StorageResourceKey extra = StorageResourceKey.of(
                ResourceLocation.fromNamespaceAndPath("example", "mana"),
                ResourceLocation.fromNamespaceAndPath("example", "arcane"),
                new CompoundTag());
        assertTrue("type capacity rejects a new resource kind without changing existing amounts",
                !ledger.applyExact(Map.of(extra, 1L, fluid, 1L),
                        StorageTypeCapacity.finite(4), Action.EXECUTE)
                        && ledger.amount(item) == 1
                        && ledger.amount(fluid) == 1_000
                        && ledger.amount(extra) == 0);

        assertTrue("bounded exact insertion reports Long.MAX_VALUE headroom",
                ledger.insert(power, Long.MAX_VALUE, StorageTypeCapacity.finite(4), Action.EXECUTE)
                        == Long.MAX_VALUE - 500
                        && ledger.amount(power) == Long.MAX_VALUE);
        assertTrue("overflow rejects a complete mixed transaction without consuming another key",
                !ledger.applyExact(Map.of(power, 1L, item, -1L),
                        StorageTypeCapacity.finite(4), Action.EXECUTE)
                        && ledger.amount(power) == Long.MAX_VALUE && ledger.amount(item) == 1);

        CompoundTag saved = ledger.save();
        StorageResourceLedger loaded = StorageResourceLedger.load(saved);
        assertTrue("typed resource ledger preserves all known and optional-provider entries",
                loaded.snapshot().equals(ledger.snapshot())
                        && loaded.amount(chemical) == 250
                        && loaded.typeCount() == 4);

        CompoundTag malformed = saved.copy();
        malformed.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .add(malformed.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND)
                        .getCompound(0).copy());
        boolean duplicateRejected = false;
        try {
            StorageResourceLedger.load(malformed);
        } catch (IllegalArgumentException expected) {
            duplicateRejected = true;
        }
        assertTrue("duplicate persisted resource keys fail closed", duplicateRejected);
    }

    private static void testResourceContainerTransferContract() {
        boolean rejectedMultipleResults = false;
        try {
            new StorageResourceContainerStrategy.Transfer(
                    StorageResourceKey.neoforgeEnergy(),
                    1,
                    new ItemStack(Items.BUCKET, 2));
        } catch (IllegalArgumentException expected) {
            rejectedMultipleResults = true;
        }
        assertTrue("one consumed container cannot produce multiple result containers",
                rejectedMultipleResults);
    }

    private static void testBusResourceEscrow() {
        StorageResourceKey key = StorageResourceKey.neoforgeEnergy();
        BusResourceEscrow escrow = BusResourceEscrow.empty();
        escrow.add(key, Long.MAX_VALUE - 5);
        boolean overflowRejected = false;
        try {
            escrow.add(key, 10);
        } catch (IllegalStateException expected) {
            overflowRejected = true;
        }
        assertTrue("typed Bus escrow rejects overflow without partial mutation",
                overflowRejected && escrow.amount(key) == Long.MAX_VALUE - 5);
    }

    private static void testAxeSyntheticDiscovery() {
        ResourceLocation vanillaBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "oak_log");
        ResourceLocation arbitraryModBlock = ResourceLocation.fromNamespaceAndPath("audit_mod", "hooked_log");

        assertTrue("axe discovery allows minecraft namespace blocks",
                AxeTransformationCatalog.isSyntheticDiscoveryAllowed(vanillaBlock));
        assertTrue("axe discovery rejects arbitrary mod namespace blocks",
                !AxeTransformationCatalog.isSyntheticDiscoveryAllowed(arbitraryModBlock));
    }

    private static void testAxeEnergyContract() {
        ItemStack pristine = new ItemStack(Items.IRON_AXE);
        ItemStack damaged = pristine.copy();
        damaged.setDamageValue(17);
        ItemStack broken = pristine.copy();
        broken.setDamageValue(broken.getMaxDamage());

        assertTrue("Axe Energy keeps exact remaining durability",
                AxeEnergy.remainingDurability(pristine) == pristine.getMaxDamage()
                        && AxeEnergy.remainingDurability(damaged) == damaged.getMaxDamage() - 17
                        && AxeEnergy.remainingDurability(broken) == 0);
        assertTrue("Axe Energy accepts axe actions and rejects unrelated tools",
                AxeEnergy.accepts(pristine) && !AxeEnergy.accepts(new ItemStack(Items.IRON_PICKAXE)));
        assertTrue("Unbreaking multiplier is exact level plus one",
                AxeEnergy.scaledFiniteValue(5, 2, 1) == 15);
        ItemStack unbreakable = pristine.copy();
        unbreakable.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        assertTrue("explicit Unbreakable becomes infinite Axe Energy", AxeEnergy.isInfinite(unbreakable));
    }

    private static void testEnergyCost() {
        var cost = new EnergyCost(EnergyType.SMELTING_ENERGY, 200, EnergyType.FURNACE_FUEL, 200);
        assertTrue("energyCost processType", cost.processType() == EnergyType.SMELTING_ENERGY);
        assertTrue("energyCost processAmount", cost.processAmount() == 200);
        assertTrue("energyCost fuelType", cost.fuelType() == EnergyType.FURNACE_FUEL);
        assertTrue("energyCost fuelAmount", cost.fuelAmount() == 200);

        var cost2 = new EnergyCost(EnergyType.SMELTING_ENERGY, 200, EnergyType.FURNACE_FUEL, 200);
        assertTrue("energyCost equals", cost.equals(cost2));
        assertTrue("energyCost hashCode", cost.hashCode() == cost2.hashCode());
    }

    private static void testFuelValue() {
        var fv = new FuelValue(EnergyType.FURNACE_FUEL, 1600);
        assertTrue("fuelValue pool", fv.pool() == EnergyType.FURNACE_FUEL);
        assertTrue("fuelValue amount", fv.valuePerItem() == 1600);
    }

    private static void testEnergyTypeUniqueness() {
        Set<String> ids = new HashSet<>();
        for (EnergyType type : EnergyType.values()) {
            assertTrue("unique id: " + type.getId(), ids.add(type.getId()));
        }
    }

    private static void testFuelTable() {
        assertTrue("glass bottle is not fuel", !FuelTable.isFuel(new ItemStack(Items.GLASS_BOTTLE)));
        assertTrue("glass bottle has no fuel values",
                FuelTable.getFuelValues(new ItemStack(Items.GLASS_BOTTLE)).isEmpty());
        assertTrue("potion is not fuel", !FuelTable.isFuel(new ItemStack(Items.POTION)));
        assertTrue("potion has no fuel values",
                FuelTable.getFuelValues(new ItemStack(Items.POTION)).isEmpty());
        assertTrue("blaze rod has explicit brew overlay", FuelTable.isFuel(new ItemStack(Items.BLAZE_ROD)));
        assertTrue("blaze rod brew overlay is 1200", FuelTable.getFuelValues(new ItemStack(Items.BLAZE_ROD))
                .stream().anyMatch(fv -> fv.pool() == EnergyType.BLAZE_FUEL && fv.valuePerItem() == 1200));
        assertTrue("blaze powder has explicit brew overlay", FuelTable.isFuel(new ItemStack(Items.BLAZE_POWDER)));
        assertTrue("blaze powder brew overlay is 600", FuelTable.getFuelValues(new ItemStack(Items.BLAZE_POWDER))
                .stream().anyMatch(fv -> fv.pool() == EnergyType.BLAZE_FUEL && fv.valuePerItem() == 600));
        assertTrue("stone is not fuel", !FuelTable.isFuel(new ItemStack(Items.STONE)));
        assertTrue("blaze rod has one explicit brew overlay", FuelTable.getFuelValues(new ItemStack(Items.BLAZE_ROD))
                .stream().filter(fv -> fv.pool() == EnergyType.BLAZE_FUEL).count() == 1);
    }

    private static void testFuelAutoPriority() {
        assertTrue("retired bottle inputs have no automatic fuel target",
                FuelTable.getAutoFuelValue(new ItemStack(Items.GLASS_BOTTLE), type -> 0L) == null
                        && FuelTable.getAutoFuelValue(new ItemStack(Items.POTION), type -> 0L) == null);
        assertTrue("blaze fuel has two distinct suppliers",
                FuelTable.getSupplierCount(EnergyType.BLAZE_FUEL) == 2);

        FuelValue blazeRodAuto = FuelTable.getAutoFuelValue(new ItemStack(Items.BLAZE_ROD), type -> 0L);
        assertTrue("auto blaze rod prefers the pool with fewer fuel choices",
                blazeRodAuto != null && blazeRodAuto.pool() == EnergyType.BLAZE_FUEL);
        List<FuelValue> equalScarcity = List.of(
                new FuelValue(EnergyType.SMELTING_ENERGY, 1),
                new FuelValue(EnergyType.BLASTING_ENERGY, 1));
        FuelValue lowerAmount = FuelTable.selectAutoFuelValue(equalScarcity,
                type -> type == EnergyType.SMELTING_ENERGY ? 20L : 10L);
        assertTrue("equal scarcity prefers the lower accumulated pool",
                lowerAmount != null && lowerAmount.pool() == EnergyType.BLASTING_ENERGY);
        FuelValue enumTie = FuelTable.selectAutoFuelValue(equalScarcity, type -> 10L);
        assertTrue("equal scarcity and amount use stable enum order",
                enumTie != null && enumTie.pool() == EnergyType.SMELTING_ENERGY);
        assertTrue("non-fuel has no auto target",
                FuelTable.getAutoFuelValue(new ItemStack(Items.STONE), type -> 0L) == null);
    }

    private static void testRecipeEnergyTable() {
        var smeltCost = RecipeEnergyTable.getCost(new SmeltingRecipe("", CookingBookCategory.MISC,
                Ingredient.of(Items.DIRT), new ItemStack(Items.STONE), 0, 37));
        assertTrue("smelting needs smelting_energy", smeltCost.processType() == EnergyType.SMELTING_ENERGY);
        assertTrue("smelting process uses concrete cooking time", smeltCost.processAmount() == 37);
        assertTrue("smelting needs furnace_fuel", smeltCost.fuelType() == EnergyType.FURNACE_FUEL);
        assertTrue("smelting fuel uses concrete cooking time", smeltCost.fuelAmount() == 37);

        var blastCost = RecipeEnergyTable.getCost(new BlastingRecipe("", CookingBookCategory.MISC,
                Ingredient.of(Items.DIRT), new ItemStack(Items.STONE), 0, 23));
        assertTrue("blasting uses concrete cooking time", blastCost.processAmount() == 23);
        assertTrue("blasting uses blast energy", blastCost.processType() == EnergyType.BLASTING_ENERGY);

        var smokeCost = RecipeEnergyTable.getCost(new SmokingRecipe("", CookingBookCategory.MISC,
                Ingredient.of(Items.BEEF), new ItemStack(Items.COOKED_BEEF), 0, 19));
        assertTrue("smoking uses smoking_energy", smokeCost.processType() == EnergyType.SMOKING_ENERGY);
        assertTrue("smoking uses concrete cooking time", smokeCost.processAmount() == 19);

        var campCost = RecipeEnergyTable.getCost(new CampfireCookingRecipe("", CookingBookCategory.MISC,
                Ingredient.of(Items.BEEF), new ItemStack(Items.COOKED_BEEF), 0, 71));
        assertTrue("campfire uses campfire energy", campCost.processType() == EnergyType.CAMPFIRE_ENERGY);
        assertTrue("campfire uses concrete cooking time", campCost.processAmount() == 71);

        var crafting = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT)));
        assertTrue("non-cooking recipe cost is null", RecipeEnergyTable.getCost(crafting) == null);
    }

    private static void testRecipeAdapterFoundation() {
        var holder = new net.minecraft.world.item.crafting.RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("test_mod", "shapeless_adapter"),
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
                        NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT))));
        var match = BuiltInRecipeAdapters.registry().classify(holder).orElse(null);

        assertTrue("exact shapeless holders select the stable built-in adapter ID",
                match != null && match.adapterId().equals(BuiltInRecipeAdapters.SHAPELESS_ID));
        assertTrue("adapter matches require ordered inputs and explicit multiplicity",
                match != null && match.orderedInputs().size() == 1
                        && match.orderedInputs().getFirst().multiplicity() == 1);
        assertTrue("adapter matches require a stable station descriptor ID",
                match != null
                        && match.stationDescriptorId().equals(MachineEnergyTable.CRAFTING_TABLE_ID));
        assertTrue("free adapters still provide explicit process Fuel and tool cost semantics",
                match != null && match.cost().energyCost().isEmpty()
                        && match.cost().toolCost().isEmpty());
        assertTrue("adapter matches require presentation semantics",
                match != null && match.presentation().kind() == RecipePresentationKind.CRAFTING
                        && match.presentation().shapeless());
        assertTrue("adapter matches require simulation and commit validation",
                match != null && match.validatesSimulation(holder)
                        && match.validatesCommit(holder));
    }

    private static void testRecipeAdapterRegistryContract() {
        RecipeAdapter later = testRecipeAdapter("later", 20);
        RecipeAdapter samePriorityZ = testRecipeAdapter("same_z", 10);
        RecipeAdapter samePriorityA = testRecipeAdapter("same_a", 10);
        RecipeAdapterRegistry registry = new RecipeAdapterRegistry(
                List.of(later, samePriorityZ, samePriorityA));
        List<ResourceLocation> orderedIds = registry.adapters().stream()
                .map(RecipeAdapter::id)
                .toList();
        var holder = new net.minecraft.world.item.crafting.RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("test_mod", "priority"),
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
                        NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT))));

        assertTrue("adapter order is priority then stable ID",
                orderedIds.equals(List.of(samePriorityA.id(), samePriorityZ.id(), later.id())));
        assertTrue("adapters are keyed by stable ResourceLocation ID",
                registry.get(samePriorityA.id()).orElse(null) == samePriorityA
                        && registry.get(testRecipeId("missing_adapter")).isEmpty());
        RecipeAdapterMatch selectedMatch = registry.classify(holder).orElseThrow();
        assertTrue("classification uses deterministic first-match priority",
                selectedMatch.adapterId().equals(samePriorityA.id()));
        var checkedOutput = selectedMatch.checkedOutput(
                List.of(java.util.Map.of(ItemKey.of(new ItemStack(Items.DIRT)), 1L)),
                1,
                null
        ).orElse(null);
        assertTrue("adapter matches require checked output and remainder assembly",
                checkedOutput != null
                        && checkedOutput.primaryOutputs().getOrDefault(
                        ItemKey.of(new ItemStack(Items.DIAMOND)), 0L) == 1
                        && checkedOutput.remainders().isEmpty());

        boolean duplicateRejected = false;
        try {
            new RecipeAdapterRegistry(List.of(
                    testRecipeAdapter("duplicate", 0), testRecipeAdapter("duplicate", 1)));
        } catch (IllegalArgumentException expected) {
            duplicateRejected = true;
        }
        assertTrue("duplicate adapter IDs are rejected", duplicateRejected);
    }

    private static RecipeAdapter testRecipeAdapter(String path, int priority) {
        return new RecipeAdapter() {
            private final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test_mod", path);

            @Override
            public ResourceLocation id() {
                return id;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public boolean supports(net.minecraft.world.item.crafting.RecipeHolder<?> holder) {
                return true;
            }

            @Override
            public RecipeCandidateIndex candidateIndex(
                    net.minecraft.world.item.crafting.RecipeHolder<?> holder
            ) {
                return RecipeCandidateIndex.exhaustive(List.of(new ItemStack(Items.DIRT)));
            }

            @Override
            public RecipeAdapterMatch.Contract contract(RecipeHolder<?> holder) {
                Ingredient ingredient = Ingredient.of(Items.DIRT);
                RecipeAdapterMatch.Input input = RecipeAdapterMatch.Input.of(
                        ingredient,
                        ingredient,
                        List.of(new ItemStack(Items.DIRT)),
                        1
                );
                return new RecipeAdapterMatch.Contract(
                        List.of(input),
                        MachineEnergyTable.CRAFTING_TABLE_ID,
                        RecipeAdapterMatch.Cost.free(),
                        (allocations, crafts, level) -> java.util.Optional.of(
                                new RecipeAdapterMatch.CheckedOutput(
                                        java.util.Map.of(
                                                ItemKey.of(new ItemStack(Items.DIAMOND)), crafts),
                                        java.util.Map.of()
                                )),
                        new RecipeAdapterMatch.Presentation(
                                RecipePresentationKind.CRAFTING,
                                3,
                                3,
                                true,
                                (inputs, level) -> new ItemStack(Items.DIAMOND)
                        ),
                        current -> true,
                        current -> true
                );
            }

            @Override
            public List<RecipeAdapterMatch.Contract> resolveVariants(
                    RecipeHolder<?> holder,
                    RecipeAdapterMatch.Contract baseContract,
                    List<ItemStack> availableStacks,
                    net.minecraft.world.level.Level level
            ) {
                return List.of(baseContract);
            }

            @Override
            public boolean matchesLookupOutput(
                    RecipeHolder<?> holder,
                    RecipeAdapterMatch.Contract variantContract,
                    ItemStack requestedOutput,
                    net.minecraft.world.level.Level level
            ) {
                return false;
            }
        };
    }

    private static void testPublicRecipeFamilyFactory() {
        final class FixtureRecipe extends StonecutterRecipe {
            private FixtureRecipe() {
                super("", Ingredient.of(Items.DIRT), new ItemStack(Items.DIAMOND, 2));
            }
        }

        ResourceLocation familyId = testRecipeId("public_family");
        RecipeFamily family = RecipeFamilyFactories.singleItemToItem(
                FixtureRecipe.class,
                () -> net.minecraft.world.item.crafting.RecipeType.STONECUTTING,
                MachineEnergyTable.STONECUTTER_ID,
                recipe -> recipe.getIngredients().getFirst(),
                (recipe, registries) -> recipe.getResultItem(registries),
                recipe -> RecipeFamilyCost.free(),
                RecipePresentationKind.STONECUTTING);
        RecipeAdapterRegistry registry = new RecipeAdapterRegistry(List.of(family.adapter(familyId, 1000)));
        RecipeHolder<FixtureRecipe> holder = new RecipeHolder<>(
                testRecipeId("public_family_recipe"), new FixtureRecipe());
        RecipeAdapterMatch match = registry.classify(holder).orElse(null);

        assertTrue("public exact family receives its stable registry ID",
                match != null && match.adapterId().equals(familyId));
        assertTrue("public exact family exposes deterministic input candidates",
                match != null && match.candidateIndex().isExhaustive()
                        && match.candidateIndex().representatives().stream()
                        .anyMatch(stack -> stack.is(Items.DIRT)));
        assertTrue("public exact family maps station cost and presentation",
                match != null
                        && match.stationDescriptorId().equals(MachineEnergyTable.STONECUTTER_ID)
                        && match.cost().equals(RecipeAdapterMatch.Cost.free())
                        && match.presentation().kind() == RecipePresentationKind.STONECUTTING);
        ResourceLocation processStation = testRecipeId("process_station");
        assertTrue("public recipe families expose descriptor-scoped station work",
                RecipeFamilyCost.stationWork(37).toInternal(processStation).equals(
                        RecipeAdapterMatch.Cost.stationWork(
                                new RecipeAdapterMatch.StationWorkCost(processStation, 37))));
        EnergyCost processFuel = new EnergyCost(
                EnergyType.SMELTING_ENERGY, 0, EnergyType.FURNACE_FUEL, 37);
        assertTrue("public station work can share one atomic legacy Fuel cost",
                RecipeFamilyCost.stationWorkAndEnergy(37, processFuel)
                        .toInternal(processStation)
                        .equals(new RecipeAdapterMatch.Cost(
                                java.util.Optional.of(processFuel),
                                java.util.Optional.empty(),
                                java.util.Optional.of(new RecipeAdapterMatch.StationWorkCost(
                                        processStation, 37)))));
        assertTrue("public exact family does not claim the recipe superclass",
                registry.classify(new RecipeHolder<>(
                        testRecipeId("vanilla_stonecutting"),
                        new StonecutterRecipe("", Ingredient.of(Items.DIRT),
                                new ItemStack(Items.DIAMOND)))).isEmpty());

        boolean nonePresentationRejected = false;
        try {
            RecipeFamilyFactories.singleItemToItem(
                    FixtureRecipe.class,
                    () -> net.minecraft.world.item.crafting.RecipeType.STONECUTTING,
                    MachineEnergyTable.STONECUTTER_ID,
                    recipe -> recipe.getIngredients().getFirst(),
                    (recipe, registries) -> recipe.getResultItem(registries),
                    recipe -> RecipeFamilyCost.free(),
                    RecipePresentationKind.NONE);
        } catch (IllegalArgumentException expected) {
            nonePresentationRejected = true;
        }
        assertTrue("public recipe families reject NONE presentation", nonePresentationRejected);

        boolean negativeEnergyRejected = false;
        try {
            RecipeFamilyCost.energy(new EnergyCost(
                    EnergyType.SMELTING_ENERGY,
                    -1,
                    EnergyType.FURNACE_FUEL,
                    1));
        } catch (IllegalArgumentException expected) {
            negativeEnergyRejected = true;
        }
        assertTrue("public recipe families reject negative energy costs", negativeEnergyRejected);

        boolean emptyEnergyRejected = false;
        try {
            RecipeFamilyCost.energy(new EnergyCost(
                    EnergyType.SMELTING_ENERGY,
                    0,
                    EnergyType.FURNACE_FUEL,
                    0));
        } catch (IllegalArgumentException expected) {
            emptyEnergyRejected = true;
        }
        assertTrue("public recipe families require free() for zero energy cost", emptyEnergyRejected);

        boolean overlappingEnergyRejected = false;
        try {
            RecipeFamilyCost.energy(new EnergyCost(
                    EnergyType.SMELTING_ENERGY,
                    1,
                    EnergyType.SMELTING_ENERGY,
                    1));
        } catch (IllegalArgumentException expected) {
            overlappingEnergyRejected = true;
        }
        assertTrue("public recipe families reject overlapping energy pools",
                overlappingEnergyRejected);

        StorageResourceKey blue = StorageResourceKey.of(
                testRecipeId("mana"), testRecipeId("blue"), new net.minecraft.nbt.CompoundTag());
        StorageResourceKey red = StorageResourceKey.of(
                testRecipeId("mana"), testRecipeId("red"), new net.minecraft.nbt.CompoundTag());
        TypedRecipeInput alternatives = TypedRecipeInput.consumeAny(List.of(blue, red), 3);
        assertTrue("typed recipe inputs preserve deterministic resource alternatives",
                alternatives.alternatives().equals(List.of(blue, red))
                        && alternatives.key().equals(blue)
                        && alternatives.amount() == 3);
        TypedRecipeInput alternativeRemainders = TypedRecipeInput.consumeAnyWithRemainders(
                List.of(blue, red),
                2,
                java.util.Map.of(
                        blue, TypedRecipeOutput.remainder(red, 1),
                        red, TypedRecipeOutput.remainder(blue, 2)));
        assertTrue("typed recipe inputs preserve alternative-specific remainders",
                alternativeRemainders.remainderFor(blue).orElseThrow().key().equals(red)
                        && alternativeRemainders.remainderFor(red).orElseThrow().amount() == 2);
        boolean unrelatedRemainderRejected = false;
        try {
            TypedRecipeInput.consumeAnyWithRemainders(
                    List.of(blue),
                    1,
                    java.util.Map.of(red, TypedRecipeOutput.remainder(blue, 1)));
        } catch (IllegalArgumentException expected) {
            unrelatedRemainderRejected = true;
        }
        assertTrue("typed recipe inputs reject remainders for unrelated alternatives",
                unrelatedRemainderRejected);
        boolean duplicateAlternativeRejected = false;
        try {
            TypedRecipeInput.consumeAny(List.of(blue, blue), 1);
        } catch (IllegalArgumentException expected) {
            duplicateAlternativeRejected = true;
        }
        assertTrue("typed recipe inputs reject duplicate alternatives",
                duplicateAlternativeRejected);
    }

    private static void testRecipeAdapterSnapshot() {
        RecipeFamily family = RecipeFamilyFactories.singleItemToItem(
                StonecutterRecipe.class,
                () -> net.minecraft.world.item.crafting.RecipeType.STONECUTTING,
                MachineEnergyTable.STONECUTTER_ID,
                recipe -> recipe.getIngredients().getFirst(),
                (recipe, registries) -> recipe.getResultItem(registries),
                recipe -> RecipeFamilyCost.free(),
                RecipePresentationKind.STONECUTTING);
        RecipeAdapterSnapshot snapshot = RecipeAdapterSnapshot.create(
                List.of(testRecipeAdapter("built_in", 10)),
                List.of(net.minecraft.world.item.crafting.RecipeType.CRAFTING),
                java.util.Map.of(testRecipeId("registered_family"), family),
                id -> id.equals(MachineEnergyTable.STONECUTTER_ID));

        assertTrue("registered families join the deterministic adapter registry",
                snapshot.registry().adapters().stream().map(RecipeAdapter::id).toList().equals(List.of(
                        testRecipeId("built_in"), testRecipeId("registered_family"))));
        assertTrue("registered family recipe types join discovery exactly once",
                snapshot.discoveryTypes().equals(List.of(
                        net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                        net.minecraft.world.item.crafting.RecipeType.STONECUTTING)));

        RecipeAdapterSnapshot ordered = RecipeAdapterSnapshot.create(
                List.of(testRecipeAdapter("built_in", 10)),
                List.of(),
                java.util.Map.of(
                        testRecipeId("z_family"), family,
                        testRecipeId("a_family"), RecipeFamilyFactories.singleItemToItem(
                                StonecutterRecipe.class,
                                () -> net.minecraft.world.item.crafting.RecipeType.SMELTING,
                                MachineEnergyTable.STONECUTTER_ID,
                                recipe -> recipe.getIngredients().getFirst(),
                                (recipe, registries) -> recipe.getResultItem(registries),
                                recipe -> RecipeFamilyCost.free(),
                                RecipePresentationKind.STONECUTTING)),
                id -> true);
        assertTrue("registered families are ordered by full ID after built-ins",
                ordered.registry().adapters().stream().map(RecipeAdapter::id).toList().equals(List.of(
                        testRecipeId("built_in"), testRecipeId("a_family"), testRecipeId("z_family"))));

        boolean duplicateFamilyRejected = false;
        try {
            RecipeAdapterSnapshot.create(
                    List.of(),
                    List.of(),
                    java.util.Map.of(
                            testRecipeId("duplicate_a"), family,
                            testRecipeId("duplicate_b"), family),
                    id -> true);
        } catch (IllegalArgumentException expected) {
            duplicateFamilyRejected = true;
        }
        assertTrue("duplicate exact recipe class and type registrations are rejected",
                duplicateFamilyRejected);

        boolean builtInShadowRejected = false;
        try {
            RecipeAdapterSnapshot.create(
                    List.of(family.adapter(testRecipeId("built_in_family"), 0)),
                    List.of(),
                    java.util.Map.of(testRecipeId("external_shadow"), family),
                    id -> true);
        } catch (IllegalArgumentException expected) {
            builtInShadowRejected = true;
        }
        assertTrue("registered families cannot shadow built-in exact class and type ownership",
                builtInShadowRejected);

        boolean missingStationRejected = false;
        try {
            RecipeAdapterSnapshot.create(
                    List.of(),
                    List.of(),
                    java.util.Map.of(testRecipeId("missing_station"), family),
                    id -> false);
        } catch (IllegalArgumentException expected) {
            missingStationRejected = true;
        }
        assertTrue("recipe families with missing station descriptors fail closed",
                missingStationRejected);
    }

    private static void testBuiltInRecipeAdapterFamilies() {
        List<RecipeHolder<?>> holders = List.of(
                new RecipeHolder<>(testRecipeId("shaped"), new ShapedRecipe(
                        "", CraftingBookCategory.MISC,
                        new ShapedRecipePattern(1, 1,
                                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT)),
                                java.util.Optional.empty()),
                        new ItemStack(Items.STONE))),
                new RecipeHolder<>(testRecipeId("shapeless"), new ShapelessRecipe(
                        "", CraftingBookCategory.MISC, new ItemStack(Items.STONE),
                        NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT)))),
                new RecipeHolder<>(testRecipeId("smelting"), new SmeltingRecipe(
                        "", CookingBookCategory.MISC, Ingredient.of(Items.DIRT),
                        new ItemStack(Items.STONE), 0, 200)),
                new RecipeHolder<>(testRecipeId("blasting"), new BlastingRecipe(
                        "", CookingBookCategory.MISC, Ingredient.of(Items.DIRT),
                        new ItemStack(Items.STONE), 0, 100)),
                new RecipeHolder<>(testRecipeId("smoking"), new SmokingRecipe(
                        "", CookingBookCategory.MISC, Ingredient.of(Items.BEEF),
                        new ItemStack(Items.COOKED_BEEF), 0, 100)),
                new RecipeHolder<>(testRecipeId("campfire"), new CampfireCookingRecipe(
                        "", CookingBookCategory.MISC, Ingredient.of(Items.BEEF),
                        new ItemStack(Items.COOKED_BEEF), 0, 600)),
                new RecipeHolder<>(testRecipeId("stonecutting"), new StonecutterRecipe(
                        "", Ingredient.of(Items.STONE), new ItemStack(Items.STONE_BRICKS))),
                new RecipeHolder<>(testRecipeId("smithing"), new SmithingTransformRecipe(
                        Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        Ingredient.of(Items.DIAMOND_SWORD),
                        Ingredient.of(Items.NETHERITE_INGOT),
                        new ItemStack(Items.NETHERITE_SWORD))),
                new RecipeHolder<>(testRecipeId("smithing_trim"), new SmithingTrimRecipe(
                        Ingredient.of(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                        Ingredient.of(Items.IRON_CHESTPLATE),
                        Ingredient.of(Items.REDSTONE))),
                new RecipeHolder<>(testRecipeId("axe"), new AxeTransformationRecipe(
                        Ingredient.of(Items.OAK_LOG), new ItemStack(Items.STRIPPED_OAK_LOG),
                        ItemAbilities.AXE_STRIP))
        );
        List<ResourceLocation> expectedAdapterIds = List.of(
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "shaped_crafting"),
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "shapeless_crafting"),
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "smelting"),
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "blasting"),
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "smoking"),
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "campfire_cooking"),
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "stonecutting"),
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "smithing_transform"),
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "smithing_trim"),
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "axe_transformation")
        );

        for (int index = 0; index < holders.size(); index++) {
            RecipeAdapterMatch match = BuiltInRecipeAdapters.registry()
                    .classify(holders.get(index)).orElse(null);
            assertTrue("exact built-in family selects adapter " + expectedAdapterIds.get(index),
                    match != null && match.adapterId().equals(expectedAdapterIds.get(index)));
        }
        List<ResourceLocation> actualAdapterIds = BuiltInRecipeAdapters.registry().adapters().stream()
                .map(RecipeAdapter::id)
                .filter(id -> id.getNamespace().equals(MagicStorage.MODID))
                .toList();
        assertTrue("built-in adapter IDs have a stable deterministic order",
                actualAdapterIds.size() >= expectedAdapterIds.size()
                        && actualAdapterIds.subList(0, expectedAdapterIds.size())
                        .equals(expectedAdapterIds));

        ShapelessRecipe customFamily = new ShapelessRecipe(
                "", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT))) {
        };
        assertTrue("custom recipe classes remain unsupported without a registered exact adapter",
                BuiltInRecipeAdapters.registry().classify(
                        new RecipeHolder<>(testRecipeId("custom_family"), customFamily)).isEmpty());
    }

    private static ResourceLocation testRecipeId(String path) {
        return ResourceLocation.fromNamespaceAndPath("test_mod", path);
    }

    private static void testSmithingTrimAdapterFoundation() {
        ResourceLocation recipeId = testRecipeId("smithing_trim");
        SmithingTrimRecipe recipe = new SmithingTrimRecipe(
                Ingredient.of(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                Ingredient.of(Items.IRON_CHESTPLATE),
                Ingredient.of(Items.REDSTONE));
        RecipeHolder<SmithingTrimRecipe> holder = new RecipeHolder<>(recipeId, recipe);
        RecipeAdapterMatch match = BuiltInRecipeAdapters.registry().classify(holder).orElse(null);

        assertTrue("exact Smithing Trim recipes select a dedicated stable adapter",
                match != null && match.adapterId().equals(ResourceLocation.fromNamespaceAndPath(
                        MagicStorage.MODID, "smithing_trim")));
        assertTrue("Smithing Trim delegates exact template base and addition predicates",
                match != null && match.orderedInputs().size() == 3
                        && match.orderedInputs().get(0).test(
                        new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE))
                        && !match.orderedInputs().get(0).test(new ItemStack(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE))
                        && match.orderedInputs().get(1).test(new ItemStack(Items.IRON_CHESTPLATE))
                        && !match.orderedInputs().get(1).test(new ItemStack(Items.IRON_SWORD))
                        && match.orderedInputs().get(2).test(new ItemStack(Items.REDSTONE))
                        && !match.orderedInputs().get(2).test(new ItemStack(Items.GOLD_INGOT)));
        assertTrue("Smithing Trim requires the Smithing Table station",
                match != null
                        && match.stationDescriptorId().equals(MachineEnergyTable.SMITHING_TABLE_ID));

        RecipeHolder<SmithingTrimRecipe> replacement = new RecipeHolder<>(recipeId,
                new SmithingTrimRecipe(
                        Ingredient.of(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                        Ingredient.of(Items.IRON_CHESTPLATE),
                        Ingredient.of(Items.REDSTONE)));
        assertTrue("Smithing Trim matches reject a stale same-ID holder",
                match != null && !match.validatesSimulation(replacement)
                        && !match.validatesCommit(replacement));

        SmithingTrimRecipe dynamicSubclass = new SmithingTrimRecipe(
                Ingredient.of(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                Ingredient.of(Items.IRON_CHESTPLATE),
                Ingredient.of(Items.REDSTONE)) {
        };
        assertTrue("dynamic Smithing Trim subclasses remain unsupported",
                BuiltInRecipeAdapters.registry().classify(
                        new RecipeHolder<>(testRecipeId("dynamic_smithing_trim"), dynamicSubclass)).isEmpty());
        assertTrue("incomplete Smithing Trim recipes remain unsupported",
                BuiltInRecipeAdapters.registry().classify(new RecipeHolder<>(
                        testRecipeId("incomplete_smithing_trim"),
                        new SmithingTrimRecipe(
                                Ingredient.of(),
                                Ingredient.of(Items.IRON_CHESTPLATE),
                                Ingredient.of(Items.REDSTONE)))).isEmpty());
    }

    private static void testRecipeAdapterCandidateCoverage() {
        RecipeHolder<ShapelessRecipe> simple = new RecipeHolder<>(
                testRecipeId("simple_candidates"),
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
                        NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT))));
        ItemStack exactStone = new ItemStack(Items.STONE);
        exactStone.set(DataComponents.CUSTOM_NAME, Component.literal("exact"));
        Ingredient nonSimple = DataComponentIngredient.of(true, exactStone);
        RecipeHolder<ShapelessRecipe> componentExact = new RecipeHolder<>(
                testRecipeId("non_exhaustive_candidates"),
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.EMERALD),
                        NonNullList.of(Ingredient.EMPTY, nonSimple)));

        RecipeCandidateIndex simpleIndex = BuiltInRecipeAdapters.registry()
                .classify(simple).orElseThrow().candidateIndex();
        RecipeCandidateIndex componentIndex = BuiltInRecipeAdapters.registry()
                .classify(componentExact).orElseThrow().candidateIndex();

        assertTrue("simple ingredient representatives are explicitly exhaustive",
                simpleIndex.coverage() == RecipeCandidateIndex.Coverage.EXHAUSTIVE
                        && simpleIndex.representatives().stream().anyMatch(stack -> stack.is(Items.DIRT)));
        assertTrue("non-simple ingredient representatives are explicitly non-exhaustive",
                componentIndex.coverage() == RecipeCandidateIndex.Coverage.NON_EXHAUSTIVE
                        && componentIndex.representatives().stream()
                        .anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, exactStone)));
    }

    private static void testRecipeAdapterReloadIdentity() {
        ResourceLocation recipeId = testRecipeId("reload_identity");
        RecipeHolder<ShapelessRecipe> original = new RecipeHolder<>(
                recipeId,
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
                        NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT))));
        RecipeAdapterMatch originalMatch = BuiltInRecipeAdapters.registry()
                .classify(original).orElseThrow();
        RecipeHolder<ShapelessRecipe> replacement = new RecipeHolder<>(
                recipeId,
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
                        NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE))));
        RecipeAdapterMatch replacementMatch = BuiltInRecipeAdapters.registry()
                .classify(replacement).orElseThrow();

        assertTrue("adapter matches are bound to the exact holder identity",
                originalMatch.isCurrentHolder(original)
                        && !originalMatch.isCurrentHolder(replacement)
                        && replacementMatch.isCurrentHolder(replacement));
        assertTrue("same-ID reloads are classified from the replacement recipe instance",
                replacementMatch.candidateIndex().representatives().stream()
                        .anyMatch(stack -> stack.is(Items.STONE))
                        && replacementMatch.candidateIndex().representatives().stream()
                        .noneMatch(stack -> stack.is(Items.DIRT)));

        ShapelessRecipe replacementCustomFamily = new ShapelessRecipe(
                "", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE))) {
        };
        assertTrue("same-ID reloads do not inherit stale adapter support",
                BuiltInRecipeAdapters.registry().classify(
                        new RecipeHolder<>(recipeId, replacementCustomFamily)).isEmpty());
    }

    private static void testEnergyType() {
        assertTrue("smelting is machine-generated", EnergyType.SMELTING_ENERGY.isMachineGenerated());
        assertTrue("blasting is machine-generated", EnergyType.BLASTING_ENERGY.isMachineGenerated());
        assertTrue("furnace_fuel is not machine-generated", !EnergyType.FURNACE_FUEL.isMachineGenerated());
        assertTrue("blaze_fuel is not machine-generated", !EnergyType.BLAZE_FUEL.isMachineGenerated());
        assertTrue("bottle fuel energy type is retired", java.util.Arrays.stream(EnergyType.values())
                .noneMatch(type -> type.name().equals("BOTTLE_FUEL") || type.getId().equals("bottle_fuel")));
        assertTrue("7 types total", EnergyType.values().length == 7);
        assertTrue("smelting id", EnergyType.SMELTING_ENERGY.getId().equals("smelting_energy"));
        assertTrue("every energy type has a representative item",
                java.util.Arrays.stream(EnergyType.values())
                        .allMatch(type -> !type.representativeStack().isEmpty()));
        assertTrue("every representative item is a single display icon",
                java.util.Arrays.stream(EnergyType.values())
                        .allMatch(type -> type.representativeStack().getCount() == 1));
        assertTrue("Smelting Energy uses Furnace as its representative item",
                EnergyType.SMELTING_ENERGY.representativeStack().is(Items.FURNACE));
        assertTrue("Blasting Energy uses Blast Furnace as its representative item",
                EnergyType.BLASTING_ENERGY.representativeStack().is(Items.BLAST_FURNACE));
        assertTrue("Smoking Energy uses Smoker as its representative item",
                EnergyType.SMOKING_ENERGY.representativeStack().is(Items.SMOKER));
        assertTrue("Campfire Energy uses Campfire as its representative item",
                EnergyType.CAMPFIRE_ENERGY.representativeStack().is(Items.CAMPFIRE));
        assertTrue("Brew Energy uses Brewing Stand as its representative item",
                EnergyType.BREW_ENERGY.representativeStack().is(Items.BREWING_STAND));
        assertTrue("Fuel uses coal as its representative item",
                EnergyType.FURNACE_FUEL.representativeStack().is(Items.COAL));
        assertTrue("Brew Energy uses blaze rod as its representative item",
                EnergyType.BLAZE_FUEL.representativeStack().is(Items.BLAZE_ROD));
        assertTrue("all built-in station and consumable mappings are present",
                MachineEnergyTable.size() >= 9);
        assertTrue("Furnace maps to smelting", MachineEnergyTable.get(0).representativeStack().is(Items.FURNACE)
                && MachineEnergyTable.get(0).energyType() == EnergyType.SMELTING_ENERGY
                && MachineEnergyTable.get(0).accepts(new ItemStack(Items.FURNACE)));
        assertTrue("Blast Furnace maps to blasting", MachineEnergyTable.get(1).representativeStack().is(Items.BLAST_FURNACE)
                && MachineEnergyTable.get(1).energyType() == EnergyType.BLASTING_ENERGY);
        assertTrue("Smoker maps to smoking", MachineEnergyTable.get(2).representativeStack().is(Items.SMOKER)
                && MachineEnergyTable.get(2).energyType() == EnergyType.SMOKING_ENERGY);
        assertTrue("Campfire maps to campfire", MachineEnergyTable.get(3).representativeStack().is(Items.CAMPFIRE)
                && MachineEnergyTable.get(3).energyType() == EnergyType.CAMPFIRE_ENERGY);
        assertTrue("Brewing Stand maps to brew", MachineEnergyTable.get(4).representativeStack().is(Items.BREWING_STAND)
                && MachineEnergyTable.get(4).energyType() == EnergyType.BREW_ENERGY);
        assertTrue("Crafting Table maps to crafting station slot",
                MachineEnergyTable.get(MachineEnergyTable.CRAFTING_TABLE_SLOT).representativeStack().is(Items.CRAFTING_TABLE));
        assertTrue("Stonecutter maps to stonecutting station slot",
                MachineEnergyTable.get(MachineEnergyTable.STONECUTTER_SLOT).representativeStack().is(Items.STONECUTTER));
        assertTrue("Smithing Table maps to smithing station slot",
                MachineEnergyTable.get(MachineEnergyTable.SMITHING_TABLE_SLOT).representativeStack().is(Items.SMITHING_TABLE));
        assertTrue("Axe slot accepts axes and rejects pickaxes",
                MachineEnergyTable.get(MachineEnergyTable.AXE_SLOT).accepts(new ItemStack(Items.DIAMOND_AXE))
                        && !MachineEnergyTable.get(MachineEnergyTable.AXE_SLOT)
                                .accepts(new ItemStack(Items.DIAMOND_PICKAXE)));
        assertTrue("process machines stack",
                MachineEnergyTable.get(MachineEnergyTable.FURNACE_SLOT).category()
                        == MachineEnergyTable.Category.PROCESS
                        && MachineEnergyTable.get(MachineEnergyTable.FURNACE_SLOT)
                        .maxInstalledCount() >= 130);
        assertTrue("instant stations install exactly one",
                MachineEnergyTable.get(MachineEnergyTable.CRAFTING_TABLE_SLOT).category()
                        == MachineEnergyTable.Category.INSTANT
                        && MachineEnergyTable.get(MachineEnergyTable.CRAFTING_TABLE_SLOT).maxInstalledCount() == 1);
        assertTrue("axes are consumable energy input, not installed stations",
                MachineEnergyTable.get(MachineEnergyTable.AXE_SLOT).category()
                        == MachineEnergyTable.Category.TRANSFORM
                        && MachineEnergyTable.get(MachineEnergyTable.AXE_SLOT).maxInstalledCount() == 0);
        assertTrue("machine rate is one per installed block",
                MachineEnergyTable.get(0).rateFor(new ItemStack(Items.FURNACE))
                        .orElseThrow().equals(MachineWorkRate.ONE));
    }

    private static void testMachineVariantContract() {
        MachineWorkRate reduced = MachineWorkRate.of(200, 160);
        assertTrue("machine work rates normalize exact fractions",
                reduced.numerator() == 5 && reduced.denominator() == 4);
        assertTrue("machine work rates retain slow fractional generation",
                MachineWorkRate.of(200, 720).equals(MachineWorkRate.of(5, 18)));

        MachineVariant copper = MachineVariant.of(
                new ItemStack(Items.COPPER_BLOCK), MachineWorkRate.of(10, 9));
        MachineVariant iron = MachineVariant.of(
                new ItemStack(Items.IRON_BLOCK), MachineWorkRate.of(5, 4));
        ResourceLocation id = testRecipeId("variant_station");
        Component stationLabel = Component.literal("Variant Processor");
        MachineDescriptor descriptor = MachineDescriptor.installableVariants(
                id,
                stationLabel,
                () -> List.of(copper, iron),
                MachineEnergyTable.Category.PROCESS,
                64,
                EnergyType.SMELTING_ENERGY);

        assertTrue("one logical descriptor exposes ordered concrete variants",
                descriptor.variants().size() == 2
                        && descriptor.variants().get(0).stack().is(Items.COPPER_BLOCK)
                        && descriptor.variants().get(1).stack().is(Items.IRON_BLOCK));
        assertTrue("one logical descriptor exposes its synchronized family label",
                descriptor.stationLabel().equals(stationLabel));
        assertTrue("variant descriptor is marked polymorphic", descriptor.isPolymorphic());
        assertTrue("legacy fixed descriptor is not marked polymorphic",
                !MachineEnergyTable.get(MachineEnergyTable.BLAST_FURNACE_SLOT).isPolymorphic());
        assertTrue("variant lookup retains each concrete rate",
                descriptor.rateFor(new ItemStack(Items.COPPER_BLOCK)).orElseThrow()
                        .equals(MachineWorkRate.of(10, 9))
                        && descriptor.rateFor(new ItemStack(Items.IRON_BLOCK)).orElseThrow()
                        .equals(MachineWorkRate.of(5, 4)));
        assertTrue("variant lookup rejects unrelated station items",
                descriptor.rateFor(new ItemStack(Items.GOLD_BLOCK)).isEmpty()
                        && !descriptor.accepts(new ItemStack(Items.GOLD_BLOCK)));

        boolean duplicateRejected = false;
        try {
            MachineDescriptor.installableVariants(
                    testRecipeId("duplicate_variant_station"),
                    Component.literal("Duplicate Variant Station"),
                    () -> List.of(
                            MachineVariant.of(new ItemStack(Items.FURNACE), MachineWorkRate.ONE),
                            MachineVariant.of(new ItemStack(Items.FURNACE), MachineWorkRate.of(2, 1))),
                    MachineEnergyTable.Category.PROCESS,
                    64,
                    EnergyType.SMELTING_ENERGY).variants();
        } catch (IllegalArgumentException exception) {
            duplicateRejected = true;
        }
        assertTrue("one logical descriptor rejects duplicate concrete items", duplicateRejected);

        boolean instantRateRejected = false;
        try {
            MachineDescriptor.installableVariants(
                    testRecipeId("invalid_instant_variant_station"),
                    Component.literal("Invalid Instant Station"),
                    () -> List.of(MachineVariant.of(
                            new ItemStack(Items.CRAFTING_TABLE), MachineWorkRate.ONE)),
                    MachineEnergyTable.Category.INSTANT,
                    1,
                    null).variants();
        } catch (IllegalArgumentException exception) {
            instantRateRejected = true;
        }
        assertTrue("instant variants reject process work rates", instantRateRejected);
    }

    private static void testMachineWorkAccumulator() {
        ResourceLocation furnace = ResourceLocation.withDefaultNamespace("furnace");
        MachineWorkRate slow = MachineWorkRate.of(5, 18);
        MachineWorkAccumulator.Advance advance = null;
        MachineWorkAccumulator.Remainder remainder = null;
        long generated = 0;
        for (int tick = 0; tick < 18; tick++) {
            advance = MachineWorkAccumulator.advance(remainder, furnace, slow, 1);
            generated += advance.wholeWork();
            remainder = advance.remainder();
        }
        assertTrue("fractional station work is exact across ticks",
                generated == 5 && remainder.remainder() == 0);

        MachineWorkAccumulator.Advance switched = MachineWorkAccumulator.advance(
                MachineWorkAccumulator.advance(null, furnace, slow, 1).remainder(),
                ResourceLocation.withDefaultNamespace("blast_furnace"),
                MachineWorkRate.of(5, 4),
                1);
        assertTrue("changing station variants resets incompatible fractional state",
                switched.wholeWork() == 1 && switched.remainder().remainder() == 1);

        MachineWorkAccumulator.Advance saturated = MachineWorkAccumulator.advance(
                null,
                furnace,
                MachineWorkRate.of(Long.MAX_VALUE, 2),
                64);
        assertTrue("station work overflow saturates instead of wrapping",
                saturated.wholeWork() == Long.MAX_VALUE && saturated.remainder().remainder() == 0);
    }

    private static void testTerminalAmountFormatter() {
        assertTrue("slot amount zero stays exact", TerminalAmountFormatter.formatCompact(0).equals("0"));
        assertTrue("slot amount 999 stays exact", TerminalAmountFormatter.formatCompact(999).equals("999"));
        assertTrue("slot amount 1000 uses K", TerminalAmountFormatter.formatCompact(1_000).equals("1K"));
        assertTrue("slot amount 1500 keeps one decimal", TerminalAmountFormatter.formatCompact(1_500).equals("1.5K"));
        assertTrue("slot amount floors instead of overstating",
                TerminalAmountFormatter.formatCompact(8_192).equals("8.1K"));
        assertTrue("slot amount removes decimal at 100K",
                TerminalAmountFormatter.formatCompact(100_000).equals("100K"));
        assertTrue("slot amount 1M uses M", TerminalAmountFormatter.formatCompact(1_000_000).equals("1M"));
        assertTrue("slot amount 1.5M keeps one decimal",
                TerminalAmountFormatter.formatCompact(1_500_000).equals("1.5M"));
        assertTrue("slot amount supports exa scale",
                TerminalAmountFormatter.formatCompact(Long.MAX_VALUE).equals("9.2E"));
        float fixedScale = TerminalAmountFormatter.scaleForSlot(String::length, 4);
        assertTrue("one slot scale is derived from the widest permitted compact shape",
                Math.abs(fixedScale - 0.8F) < 0.0001F);
    }

    private static void testTerminalDisplayStack() {
        ItemStack original = new ItemStack(Items.DIAMOND_SWORD, 7);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Original"));
        CompoundTag originalData = new CompoundTag();
        originalData.putString("owner", "test");
        original.set(DataComponents.CUSTOM_DATA, CustomData.of(originalData));

        ItemStack zero = TerminalDisplayStack.create(original, 0);
        ItemStack stackSized = TerminalDisplayStack.create(original, 64);
        ItemStack aboveInt = TerminalDisplayStack.create(original, (long) Integer.MAX_VALUE + 1);
        ItemStack huge = TerminalDisplayStack.create(original, Long.MAX_VALUE);
        assertTrue("zero display amount remains a visible one-count stack",
                !zero.isEmpty() && zero.getCount() == 1 && TerminalDisplayStack.amount(zero) == 0);
        assertTrue("display amount preserves exact long",
                stackSized.getCount() == 1 && TerminalDisplayStack.amount(stackSized) == 64
                        && aboveInt.getCount() == 1
                        && TerminalDisplayStack.amount(aboveInt) == (long) Integer.MAX_VALUE + 1
                        && huge.getCount() == 1 && TerminalDisplayStack.amount(huge) == Long.MAX_VALUE);
        assertTrue("display metadata is detected", TerminalDisplayStack.isDisplay(huge));
        assertTrue("plain stacks use their real count",
                TerminalDisplayStack.amount(original) == 7 && !TerminalDisplayStack.isDisplay(original));

        ItemStack stripped = TerminalDisplayStack.strip(huge);
        assertTrue("stripping display metadata preserves item components",
                ItemStack.isSameItemSameComponents(original, stripped));
        assertTrue("stripping display metadata removes only the display marker",
                !TerminalDisplayStack.isDisplay(stripped)
                        && stripped.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag().getString("owner").equals("test"));
        assertTrue("ItemKey ignores display metadata",
                ItemKey.of(huge).equals(ItemKey.of(original)));
        assertTrue("ItemKey never recreates display metadata",
                !TerminalDisplayStack.isDisplay(ItemKey.of(huge).toStack(1)));

        ItemStack noCustomData = new ItemStack(Items.STONE);
        ItemStack strippedNoCustomData = TerminalDisplayStack.strip(
                TerminalDisplayStack.create(noCustomData, 64));
        assertTrue("stripping the only marker removes empty custom data",
                !strippedNoCustomData.has(DataComponents.CUSTOM_DATA));
    }

    private static void testTerminalEntryComparator() {
        ItemStack stone = TerminalDisplayStack.create(new ItemStack(Items.STONE), 5);
        ItemStack apple = TerminalDisplayStack.create(new ItemStack(Items.APPLE), 20);
        ItemStack diamond = TerminalDisplayStack.create(new ItemStack(Items.DIAMOND), 10);

        List<ItemStack> quantity = new ArrayList<>(List.of(apple, diamond, stone));
        quantity.sort(TerminalEntryComparator.forMode(SortMode.QUANTITY, SortOrder.ASCENDING));
        assertTrue("quantity sorting uses exact display amounts",
                quantity.get(0).is(Items.STONE)
                        && quantity.get(1).is(Items.DIAMOND)
                        && quantity.get(2).is(Items.APPLE));

        List<ItemStack> descendingId = new ArrayList<>(List.of(stone, apple, diamond));
        descendingId.sort(TerminalEntryComparator.forMode(SortMode.ID, SortOrder.DESCENDING));
        assertTrue("ID sorting compares the complete identifier and reverses deterministically",
                descendingId.get(0).is(Items.STONE)
                        && descendingId.get(1).is(Items.DIAMOND)
                        && descendingId.get(2).is(Items.APPLE));

        ItemStack alpha = TerminalDisplayStack.create(new ItemStack(Items.STONE), 5);
        alpha.set(DataComponents.CUSTOM_NAME, Component.literal("Alpha"));
        ItemStack beta = TerminalDisplayStack.create(new ItemStack(Items.STONE), 5);
        beta.set(DataComponents.CUSTOM_NAME, Component.literal("Beta"));
        List<ItemStack> variantOrder = new ArrayList<>(List.of(beta, alpha));
        List<ItemStack> reversedInput = new ArrayList<>(List.of(alpha, beta));
        variantOrder.sort(TerminalEntryComparator.forMode(SortMode.ID, SortOrder.ASCENDING));
        reversedInput.sort(TerminalEntryComparator.forMode(SortMode.ID, SortOrder.ASCENDING));
        assertTrue("component variants have a deterministic final tie-breaker",
                ItemKey.of(variantOrder.get(0)).equals(ItemKey.of(reversedInput.get(0)))
                        && ItemKey.of(variantOrder.get(1)).equals(ItemKey.of(reversedInput.get(1))));

        record UtilityEntry(ItemStack stack, long amount) {
        }
        List<UtilityEntry> utilityEntries = new ArrayList<>(List.of(
                new UtilityEntry(new ItemStack(Items.APPLE), 20),
                new UtilityEntry(new ItemStack(Items.STONE), 5)));
        utilityEntries.sort(TerminalEntryComparator.forMode(
                SortMode.QUANTITY,
                SortOrder.ASCENDING,
                entry -> TerminalDisplayStack.create(entry.stack(), entry.amount())));
        assertTrue("non-grid terminal pages reuse the shared entry comparator",
                utilityEntries.getFirst().stack().is(Items.STONE));
    }

    private static void testSortMode() {
        assertTrue("SortMode has 4 values", SortMode.values().length == 4);
        assertTrue("SortMode NAME ordinal 0", SortMode.NAME.ordinal() == 0);
        assertTrue("SortMode QUANTITY ordinal 1", SortMode.QUANTITY.ordinal() == 1);
        assertTrue("SortMode MOD ordinal 2", SortMode.values()[2].name().equals("MOD"));
        assertTrue("SortMode ID ordinal 3", SortMode.ID.ordinal() == 3);
        assertTrue("NAME.next() -> QUANTITY", SortMode.NAME.next() == SortMode.QUANTITY);
        assertTrue("QUANTITY.next() -> MOD", SortMode.QUANTITY.next().name().equals("MOD"));
        assertTrue("MOD.next() -> ID", SortMode.values()[2].next() == SortMode.ID);
        assertTrue("ID.next() -> NAME", SortMode.ID.next() == SortMode.NAME);
        assertTrue("NAME.previous() -> ID", SortMode.NAME.previous() == SortMode.ID);
        assertTrue("QUANTITY.previous() -> NAME", SortMode.QUANTITY.previous() == SortMode.NAME);
        assertTrue("MOD.previous() -> QUANTITY", SortMode.MOD.previous() == SortMode.QUANTITY);
        assertTrue("ID.previous() -> MOD", SortMode.ID.previous() == SortMode.MOD);
    }

    private static void testSortOrder() {
        assertTrue("SortOrder has 2 values", SortOrder.values().length == 2);
        assertTrue("SortOrder ASCENDING ordinal 0", SortOrder.ASCENDING.ordinal() == 0);
        assertTrue("SortOrder DESCENDING ordinal 1", SortOrder.DESCENDING.ordinal() == 1);
        assertTrue("ASCENDING toggle -> DESCENDING",
                SortOrder.toggle(SortOrder.ASCENDING) == SortOrder.DESCENDING);
        assertTrue("DESCENDING toggle -> ASCENDING",
                SortOrder.toggle(SortOrder.DESCENDING) == SortOrder.ASCENDING);
    }

    private static void testSearchMode() {
        assertTrue("SearchMode has 3 values", SearchMode.values().length == 3);
        assertTrue("SearchMode OFF ordinal 0", SearchMode.OFF.ordinal() == 0);
        assertTrue("SearchMode EMI ordinal 1", SearchMode.EMI.ordinal() == 1);
        assertTrue("SearchMode EMI_TWO_WAY ordinal 2", SearchMode.EMI_TWO_WAY.ordinal() == 2);
        assertTrue("OFF.next() -> EMI", SearchMode.OFF.next() == SearchMode.EMI);
        assertTrue("EMI.next() -> EMI_TWO_WAY",
                SearchMode.EMI.next() == SearchMode.EMI_TWO_WAY);
        assertTrue("EMI_TWO_WAY.next() -> OFF",
                SearchMode.EMI_TWO_WAY.next() == SearchMode.OFF);
        assertTrue("OFF.previous() -> EMI_TWO_WAY",
                SearchMode.OFF.previous() == SearchMode.EMI_TWO_WAY);
        assertTrue("EMI.previous() -> OFF", SearchMode.EMI.previous() == SearchMode.OFF);
        assertTrue("EMI_TWO_WAY.previous() -> EMI",
                SearchMode.EMI_TWO_WAY.previous() == SearchMode.EMI);
        assertTrue("OFF does not synchronize with EMI",
                !SearchMode.OFF.synchronizesToEmi()
                        && !SearchMode.OFF.synchronizesFromEmi());
        assertTrue("EMI is terminal-to-EMI synchronization",
                SearchMode.EMI.synchronizesToEmi()
                        && !SearchMode.EMI.synchronizesFromEmi());
        assertTrue("EMI two-way synchronizes in both directions",
                SearchMode.EMI_TWO_WAY.synchronizesToEmi()
                        && SearchMode.EMI_TWO_WAY.synchronizesFromEmi());
        assertTrue("legacy search modes explicitly migrate to OFF synchronization",
                SearchMode.fromConfigValue("AUTO") == SearchMode.OFF
                        && SearchMode.fromConfigValue("NORMAL") == SearchMode.OFF
                        && SearchMode.fromConfigValue("TAG") == SearchMode.OFF
                        && SearchMode.fromConfigValue("MOD") == SearchMode.OFF);
        assertTrue("canonical search synchronization config values round-trip",
                SearchMode.fromConfigValue("OFF") == SearchMode.OFF
                        && SearchMode.fromConfigValue("EMI") == SearchMode.EMI
                        && SearchMode.fromConfigValue("EMI_TWO_WAY") == SearchMode.EMI_TWO_WAY);
        boolean rejectedUnknown = false;
        try {
            SearchMode.fromConfigValue("unknown");
        } catch (IllegalArgumentException expected) {
            rejectedUnknown = true;
        }
        assertTrue("unknown search synchronization config values fail explicitly",
                rejectedUnknown);
    }

    private static void testTerminalSearchQuery() {
        var iron = TerminalSearchEntry.create(ItemKey.of(new ItemStack(Items.IRON_INGOT)));
        assertTrue("compiled search query combines name and mod terms",
                TerminalSearchQuery.compile("iron @minecraft").matches(iron));
        assertTrue("a bare operator behaves like an empty query",
                TerminalSearchQuery.compile("@").isEmpty()
                        && TerminalSearchQuery.compile("#").isEmpty()
                        && TerminalSearchQuery.compile("$").isEmpty()
                        && TerminalSearchQuery.compile("@ # $").isEmpty()
                        && TerminalSearchQuery.compile("@").matches(iron));
        assertTrue("typing can continue after an unfinished mod prefix",
                TerminalSearchQuery.compile("@minecraft").matches(iron));
        assertTrue("mod-prefix matching accepts a partial namespace",
                TerminalSearchQuery.compile("@mine").matches(iron));
        assertTrue("compiled search query keeps dollar-prefixed name matching",
                TerminalSearchQuery.compile("$ingot").matches(iron));
        assertTrue("compiled search query rejects an invalid tag without throwing",
                !TerminalSearchQuery.compile("#minecraft:BAD!").matches(iron));
        assertTrue("compiled search query is locale-independent",
                TerminalSearchQuery.compile("IRON").matches(iron));
        var scoped = TerminalSearchEntry.create(
                new ItemStack(Items.REDSTONE),
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "energy"),
                Component.literal("Energy"));
        StorageResourceKey processing = StorageResourceBridge.stationWorkKey(
                ResourceLocation.fromNamespaceAndPath(
                        MagicStorage.MODID, "minecraft_furnace"));
        assertTrue("non-grid scopes reuse name and mod-prefix search semantics",
                TerminalSearchQuery.compile("energy @magic_storage").matches(scoped)
                        && TerminalSearchQuery.compile("@magic_stor").matches(scoped)
                        && TerminalSearchQuery.compile("@minecraft")
                        .matches(processing, new ItemStack(Items.FURNACE)));
    }

    private static void testFuelSearchModel() {
        MachineDescriptor variants = MachineDescriptor.installableVariants(
                testRecipeId("fuel_search_variants"),
                Component.literal("Fuel Search Variants"),
                () -> List.of(
                        MachineVariant.of(new ItemStack(Items.FURNACE), MachineWorkRate.ONE),
                        MachineVariant.of(new ItemStack(Items.IRON_BLOCK), MachineWorkRate.of(2, 1))),
                MachineEnergyTable.Category.PROCESS,
                64,
                EnergyType.SMELTING_ENERGY);
        MachineDescriptor consumable = MachineEnergyTable.get(MachineEnergyTable.AXE_SLOT);
        MachineDescriptor instant = MachineEnergyTable.get(MachineEnergyTable.CRAFTING_TABLE_SLOT);
        List<MachineDescriptor> descriptors = List.of(consumable, variants, instant);

        List<FuelSearchModel.Entry> all = FuelSearchModel.search(
                "", List.of(EnergyType.FURNACE_FUEL), descriptors);
        assertTrue("Station search includes only timed and instant stations",
                all.size() == 2
                        && all.get(0).machineSlot() == 1
                        && all.get(0).category() == MachineEnergyTable.Category.PROCESS
                        && all.get(1).machineSlot() == 2
                        && all.get(1).category() == MachineEnergyTable.Category.INSTANT);
        List<FuelSearchModel.Entry> variantMatch = FuelSearchModel.search(
                "iron block", List.of(EnergyType.FURNACE_FUEL), descriptors);
        assertTrue("Fuel search matches every concrete polymorphic station variant",
                variantMatch.size() == 1
                        && variantMatch.getFirst().machineSlot() == 1);
        List<FuelSearchModel.Entry> reserveMatch = FuelSearchModel.search(
                "coal", List.of(EnergyType.FURNACE_FUEL), descriptors);
        assertTrue("Station search excludes transform reserves",
                reserveMatch.isEmpty());
        List<FuelSearchModel.Entry> instantMatch = FuelSearchModel.search(
                "@minecraft crafting", List.of(EnergyType.FURNACE_FUEL), descriptors);
        assertTrue("Fuel search reuses terminal name and mod-prefix semantics",
                instantMatch.size() == 1
                        && instantMatch.getFirst().category()
                        == MachineEnergyTable.Category.INSTANT);
    }

    private static void testRecipeStationCycle() {
        assertTrue("station variants keep the first icon for the first 1000ms",
                RecipeStationCycle.cycle(0) == 0
                        && RecipeStationCycle.cycle(999) == 0);
        assertTrue("station variants advance once per second",
                RecipeStationCycle.cycle(1_000) == 1
                        && RecipeStationCycle.cycle(1_999) == 1
                        && RecipeStationCycle.cycle(2_000) == 2);
        assertTrue("a backwards clock is clamped to the first station",
                RecipeStationCycle.cycle(-1) == 0);
    }

    private static void testTerminalProfilesAndCycleDirection() {
        assertTrue("Storage profile is the reduced terminal",
                !TerminalProfile.STORAGE.supports(TerminalProfile.Capability.PAGES)
                        && !TerminalProfile.STORAGE.supports(TerminalProfile.Capability.RECIPE_WORKSPACE)
                        && TerminalProfile.STORAGE.itemRailGroups().equals(List.of(5)));
        assertTrue("Crafting profile composes four pages, recipe, source, and output capabilities",
                TerminalProfile.CRAFTING.supports(TerminalProfile.Capability.PAGES)
                        && TerminalProfile.CRAFTING.supports(TerminalProfile.Capability.RECIPE_WORKSPACE)
                        && TerminalProfile.CRAFTING.supports(TerminalProfile.Capability.FUEL)
                        && TerminalProfile.CRAFTING.supports(TerminalProfile.Capability.PLAYER_INVENTORY_SOURCE)
                        && TerminalProfile.CRAFTING.supports(TerminalProfile.Capability.OUTPUT_DESTINATION)
                        && TerminalProfile.CRAFTING.playerInventorySourceIndex() == 9
                        && TerminalProfile.CRAFTING.outputDestinationIndex() == 10
                        && TerminalProfile.CRAFTING.itemRailGroups().equals(List.of(4, 5, 2))
                        && TerminalProfile.CRAFTING.fuelRailGroups().equals(List.of(4, 3)));
        assertTrue("terminal controls use an 18px hit box and 16px icon canvas",
                TerminalLayout.CONTROL_SIZE == 18 && TerminalLayout.ICON_CANVAS_SIZE == 16);
        assertTrue("left click selects next",
                TerminalCycleDirection.fromMouseButton(0) == TerminalCycleDirection.NEXT);
        assertTrue("right click selects previous",
                TerminalCycleDirection.fromMouseButton(1) == TerminalCycleDirection.PREVIOUS);
        assertTrue("wheel down selects next",
                TerminalCycleDirection.fromScroll(-1.0) == TerminalCycleDirection.NEXT);
        assertTrue("wheel up selects previous",
                TerminalCycleDirection.fromScroll(1.0) == TerminalCycleDirection.PREVIOUS);
    }

    private static void testTerminalResourceView() {
        assertTrue("TerminalResourceView has six explicit groups plus the All aggregate",
                TerminalResourceView.values().length == 7);
        assertTrue("resource view cycle keeps stable groups and appends the All aggregate",
                TerminalResourceView.ITEM.next() == TerminalResourceView.FLUID
                        && TerminalResourceView.FLUID.next() == TerminalResourceView.ENERGY
                        && TerminalResourceView.ENERGY.next() == TerminalResourceView.GAS
                        && TerminalResourceView.GAS.next() == TerminalResourceView.STATION_WORK
                        && TerminalResourceView.STATION_WORK.next() == TerminalResourceView.OTHER
                        && TerminalResourceView.OTHER.next() == TerminalResourceView.ALL
                        && TerminalResourceView.ALL.next() == TerminalResourceView.ITEM);
        assertTrue("resource view previous wraps from item to the All aggregate",
                TerminalResourceView.ITEM.previous() == TerminalResourceView.ALL);
        StorageResourceKey item = StorageResourceKey.of(
                StorageResourceKindApi.ITEM_KIND,
                ResourceLocation.fromNamespaceAndPath("minecraft", "stone"),
                new net.minecraft.nbt.CompoundTag());
        StorageResourceKey fluid = StorageResourceKey.of(
                StorageResourceKindApi.FLUID_KIND,
                ResourceLocation.fromNamespaceAndPath("minecraft", "water"),
                new net.minecraft.nbt.CompoundTag());
        StorageResourceKey energy = StorageResourceKey.neoforgeEnergy();
        StorageResourceKey fuelEnergy =
                StorageResourceBridge.energyKey(EnergyType.FURNACE_FUEL);
        StorageResourceKey mana = StorageResourceKey.of(
                StorageResourceKindApi.BOTANIA_MANA_KIND,
                StorageResourceKindApi.BOTANIA_MANA_KIND,
                new net.minecraft.nbt.CompoundTag());
        StorageResourceKey source = StorageResourceKey.of(
                StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND,
                StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND,
                new net.minecraft.nbt.CompoundTag());
        StorageResourceKey axeUses =
                StorageResourceBridge.descriptorKey(MachineEnergyTable.AXE_ID);
        StorageResourceKey stationWork = StorageResourceBridge.stationWorkKey(
                ResourceLocation.fromNamespaceAndPath("magic_storage", "test_station"));
        StorageResourceKey gas = StorageResourceKey.of(
                StorageResourceKindApi.CHEMICAL_KIND,
                ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"),
                new net.minecraft.nbt.CompoundTag());
        StorageResourceKey canonicalGas = StorageResourceKey.of(
                ResourceLocation.fromNamespaceAndPath("magic_storage", "chemical"),
                ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"),
                new net.minecraft.nbt.CompoundTag());
        StorageResourceKey addon = StorageResourceKey.of(
                ResourceLocation.fromNamespaceAndPath("example", "mana"),
                ResourceLocation.fromNamespaceAndPath("example", "blue"),
                new net.minecraft.nbt.CompoundTag());
        assertTrue("built-in resource kinds have one exact terminal view",
                TerminalResourceView.ITEM.matches(item)
                        && TerminalResourceView.FLUID.matches(fluid)
                        && TerminalResourceView.ENERGY.matches(energy)
                        && TerminalResourceView.ENERGY.matches(fuelEnergy)
                        && TerminalResourceView.ENERGY.matches(mana)
                        && TerminalResourceView.ENERGY.matches(source)
                        && TerminalResourceView.GAS.matches(gas)
                        && TerminalResourceView.GAS.matches(canonicalGas)
                        && TerminalResourceView.STATION_WORK.matches(stationWork)
                        && !TerminalResourceView.ENERGY.matches(stationWork));
        assertTrue("other view contains unclassified values and addon kinds",
                TerminalResourceView.OTHER.matches(axeUses)
                        && TerminalResourceView.OTHER.matches(addon)
                        && !TerminalResourceView.OTHER.matches(item)
                        && !TerminalResourceView.OTHER.matches(fluid)
                        && !TerminalResourceView.OTHER.matches(energy)
                        && !TerminalResourceView.OTHER.matches(fuelEnergy)
                        && !TerminalResourceView.OTHER.matches(mana)
                        && !TerminalResourceView.OTHER.matches(source)
                        && !TerminalResourceView.OTHER.matches(stationWork)
                        && !TerminalResourceView.OTHER.matches(gas)
                        && !TerminalResourceView.OTHER.matches(canonicalGas));
        assertTrue("All aggregate accepts every registered resource identity",
                TerminalResourceView.ALL.matches(item)
                        && TerminalResourceView.ALL.matches(fluid)
                        && TerminalResourceView.ALL.matches(energy)
                        && TerminalResourceView.ALL.matches(mana)
                        && TerminalResourceView.ALL.matches(source)
                        && TerminalResourceView.ALL.matches(axeUses)
                        && TerminalResourceView.ALL.matches(stationWork)
                        && TerminalResourceView.ALL.matches(gas)
                        && TerminalResourceView.ALL.matches(canonicalGas)
                        && TerminalResourceView.ALL.matches(addon)
                        && TerminalResourceView.ALL.isAvailable());
        assertTrue("invalid resource view wire id fails to item default",
                TerminalResourceView.byId(-1) == TerminalResourceView.ITEM
                        && TerminalResourceView.byId(99) == TerminalResourceView.ITEM);
        boolean rejectedInvalidPacketView = false;
        try {
            TerminalResourceView.requireById(99);
        } catch (IllegalArgumentException expected) {
            rejectedInvalidPacketView = true;
        }
        assertTrue("invalid resource view packet id is rejected",
                rejectedInvalidPacketView);
    }

    private static void testTerminalSettingsPacketCodec() {
        var preferences = new TerminalPreferences(
                SortMode.MOD,
                SortOrder.DESCENDING,
                SearchMode.EMI,
                TerminalResourceView.FLUID,
                CraftingTerminalPage.CRAFTABLE,
                true,
                TerminalOutputDestination.STORAGE,
                EnergyType.BLAZE_FUEL);
        var original = new TerminalSettingsPacket(123, 6, preferences);
        assertTrue("packet containerId 123", original.containerId() == 123);
        assertTrue("packet visibleRows 6", original.visibleRows() == 6);
        assertTrue("packet carries terminal preferences", original.preferences().equals(preferences));

        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        original.write(buf);
        var decoded = TerminalSettingsPacket.read(buf);
        assertTrue("terminal settings wire round trip", decoded.equals(original));

        var extreme = new TerminalSettingsPacket(42, 9, TerminalPreferences.defaults());
        var buf2 = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        extreme.write(buf2);
        assertTrue("default terminal settings wire round trip",
                TerminalSettingsPacket.read(buf2).equals(extreme));
    }

    private static void testTerminalPreferences() {
        var emptyConfig = CommentedConfig.inMemory();
        TerminalClientPreferences.SPEC.correct(emptyConfig);
        assertTrue("terminal client config corrects an empty first-run file",
                "auto".equals(emptyConfig.get("terminal.fuelTarget"))
                        && "OFF".equals(emptyConfig.get("terminal.searchMode"))
                        && Boolean.TRUE.equals(
                        emptyConfig.get("terminal.searchBoxAutoSelected")));
        var legacySearchConfig = CommentedConfig.inMemory();
        legacySearchConfig.set("terminal.searchMode", "TAG");
        TerminalClientPreferences.SPEC.correct(legacySearchConfig);
        assertTrue("legacy search config remains available for explicit migration",
                "TAG".equals(legacySearchConfig.get("terminal.searchMode"))
                        && SearchMode.fromConfigValue(
                        legacySearchConfig.get("terminal.searchMode")) == SearchMode.OFF);

        var defaults = TerminalPreferences.defaults();
        assertTrue("terminal preference defaults match first-open controls",
                defaults.sortMode() == SortMode.NAME
                        && defaults.sortOrder() == SortOrder.ASCENDING
                        && defaults.searchMode() == SearchMode.OFF
                        && defaults.resourceView() == TerminalResourceView.ITEM
                        && defaults.page() == CraftingTerminalPage.STORAGE
                        && !defaults.usePlayerInventory()
                        && defaults.outputDestination() == TerminalOutputDestination.PLAYER
                        && defaults.fuelTarget() == null);

        var crafting = new TerminalPreferences(
                SortMode.QUANTITY,
                SortOrder.DESCENDING,
                SearchMode.EMI_TWO_WAY,
                TerminalResourceView.GAS,
                CraftingTerminalPage.FUEL,
                true,
                TerminalOutputDestination.STORAGE,
                EnergyType.BLAZE_FUEL);
        var storageChange = new TerminalPreferences(
                SortMode.ID,
                SortOrder.ASCENDING,
                SearchMode.EMI,
                TerminalResourceView.OTHER,
                CraftingTerminalPage.STORAGE,
                false,
                TerminalOutputDestination.PLAYER,
                null);
        var merged = crafting.mergeCommon(storageChange);
        assertTrue("storage terminals update only shared RS2-style preferences",
                merged.sortMode() == SortMode.ID
                        && merged.sortOrder() == SortOrder.ASCENDING
                        && merged.searchMode() == SearchMode.EMI
                        && merged.resourceView() == TerminalResourceView.OTHER
                        && merged.page() == CraftingTerminalPage.TRANSFORM
                        && merged.usePlayerInventory()
                        && merged.outputDestination() == TerminalOutputDestination.STORAGE
                        && merged.fuelTarget() == EnergyType.BLAZE_FUEL);
        assertTrue("common preference comparison ignores crafting-only controls",
                merged.matchesCommon(storageChange));

        var session = new TerminalPreferenceSession(
                crafting, TerminalPreferenceSession.Scope.CRAFTING);
        assertTrue("preference session presents persisted crafting settings before acknowledgement",
                session.presentation(defaults).equals(crafting));
        assertTrue("preference session does not save server defaults before initial acknowledgement",
                session.observe(defaults).isEmpty());
        assertTrue("unacknowledged server defaults do not replace presented crafting settings",
                session.presentation(defaults).equals(crafting));
        assertTrue("initial acknowledgement does not rewrite the config",
                session.observe(crafting).isEmpty());
        var changed = new TerminalPreferences(
                SortMode.NAME,
                SortOrder.DESCENDING,
                SearchMode.EMI_TWO_WAY,
                TerminalResourceView.GAS,
                CraftingTerminalPage.FUEL,
                true,
                TerminalOutputDestination.STORAGE,
                EnergyType.BLAZE_FUEL);
        assertTrue("server-confirmed control changes are offered for persistence",
                session.observe(changed).orElseThrow().equals(changed));
        assertTrue("acknowledged sessions present current server settings",
                session.presentation(changed).equals(changed));
        assertTrue("unchanged server state is not saved twice", session.observe(changed).isEmpty());

        var storageSession = new TerminalPreferenceSession(
                crafting, TerminalPreferenceSession.Scope.STORAGE);
        var commonAcknowledgement = storageChange.mergeCommon(crafting);
        assertTrue("storage terminals present persisted common settings before acknowledgement",
                storageSession.presentation(defaults).matchesCommon(crafting));
        assertTrue("storage acknowledgement accepts matching common settings",
                storageSession.observe(commonAcknowledgement).isEmpty());
        assertTrue("acknowledged storage sessions present current server settings",
                storageSession.presentation(storageChange).equals(storageChange));

        boolean rejected = false;
        try {
            new TerminalPreferences(
                    SortMode.NAME,
                    SortOrder.ASCENDING,
                    SearchMode.OFF,
                    TerminalResourceView.ITEM,
                    CraftingTerminalPage.STORAGE,
                    false,
                    TerminalOutputDestination.PLAYER,
                    EnergyType.SMELTING_ENERGY);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue("non-fuel energy cannot become a persisted fuel target", rejected);
    }

    private static void testAdaptiveTerminalLayout() {
        var counts = new TerminalLayout.FuelDescriptorCounts(4, 5, 3, 3);
        var narrow = TerminalLayout.forProfile(TerminalProfile.CRAFTING, 320, 240, counts);
        assertTrue("crafting geometry retains its terminal profile",
                narrow.profile() == TerminalProfile.CRAFTING);
        assertTrue("320x240 crafting uses narrow fallback", !narrow.wide());
        assertTrue("narrow fallback fits the complete viewport", narrow.imageHeight() <= 240);
        assertTrue("narrow grid follows nine-column slot rhythm",
                narrow.itemGrid().width() == 9 * TerminalLayout.SLOT_SIZE
                        && narrow.itemGrid().height() == narrow.visibleRows() * TerminalLayout.SLOT_SIZE);
        assertTrue("narrow recipe workspace follows item grid",
                narrow.workspace().y() >= narrow.itemGrid().bottom()
                        && narrow.workspace().bottom() <= narrow.playerInventory().y());
        assertTrue("narrow player inventory fits inside frame",
                narrow.playerInventory().bottom() <= narrow.imageHeight());
        assertFuelCategoryGeometry("narrow", narrow, counts);
        assertRecipeGeometry("narrow", narrow);

        var sideBySide = TerminalLayout.forProfile(TerminalProfile.CRAFTING, 423, 291, counts);
        assertTrue("guiScale-4 fullscreen width uses side-by-side layout", sideBySide.wide());
        assertTrue("side-by-side frame shrinks within its supported range",
                sideBySide.imageWidth() == 367);
        assertTrue("side-by-side recipe ledger keeps three full-size amount columns",
                sideBySide.recipeLedger().width() >= 3 * 56);
        assertTrue("side-by-side layout reserves vertical breathing room",
                sideBySide.imageHeight() <= 291);
        assertTrue("side-by-side workspace sits right of item grid",
                sideBySide.workspace().x() >= sideBySide.scrollbar().right());
        assertTrue("side-by-side boundary is based on complete usable width",
                !TerminalLayout.forProfile(TerminalProfile.CRAFTING, 422, 291, counts).wide()
                        && TerminalLayout.forProfile(TerminalProfile.CRAFTING, 423, 291, counts).wide());
        assertFuelCategoryGeometry("side-by-side", sideBySide, counts);
        assertRecipeGeometry("side-by-side", sideBySide);
        assertTrue("guiScale-4 fullscreen keeps full-height recipe amount rows",
                sideBySide.recipeLedgerCells(9).stream()
                        .allMatch(cell -> cell.height() == TerminalLayout.SLOT_SIZE));
        var screenshotGeometry = TerminalLayout.forProfile(
                TerminalProfile.CRAFTING, 432, 319, counts);
        assertTrue("fullscreen recipe body keeps the compact top inset",
                screenshotGeometry.recipeContent().y()
                        == screenshotGeometry.workspace().y() + 4);
        float ultimateRecipeScale = Math.min(
                screenshotGeometry.recipeDiagram().width() / 162.0F,
                screenshotGeometry.recipeDiagram().height() / 195.0F);
        assertTrue("fullscreen keeps the Extended Crafting EMI widget readable when scaled",
                ultimateRecipeScale >= 0.75F);
        assertTrue("page tabs are visually separated from item controls",
                sideBySide.railButtons().get(4).y() - sideBySide.railButtons().get(3).bottom() >= 6);
        assertTrue("utility rail frames page tabs and the two visible sort controls",
                sideBySide.fuelRailButtons().size() == 7
                        && sideBySide.fuelRailPanel().contains(
                                sideBySide.fuelRailButtons().getLast().x(),
                                sideBySide.fuelRailButtons().getLast().y()));
        assertTrue("Transform sidebar includes Auto plus every server-approved target",
                sideBySide.transformTargetList().itemCount() == counts.fuelTargetCount());
        assertTrue("Transform target rows are descriptor-driven and bounded",
                sideBySide.transformTargetList().rows(0).size() == counts.fuelTargetCount()
                        && rectanglesDoNotOverlap(sideBySide.transformTargetList().rows(0)));
        assertTrue("Transform target sidebar never overlaps the left control rail",
                !sideBySide.transformTargetList().bounds().overlaps(sideBySide.railPanel()));

        TerminalLayout.Rect sampleFlowCell = new TerminalLayout.Rect(20, 30, 72, 28);
        assertTrue("station hover geometry is the left-centered 18px slot",
                TerminalLayout.fuelSlot(sampleFlowCell).equals(
                        new TerminalLayout.Rect(21, 35,
                                TerminalLayout.SLOT_SIZE, TerminalLayout.SLOT_SIZE)));
        assertTrue("reserve hover geometry is the left-centered 16px icon",
                TerminalLayout.fuelIcon(sampleFlowCell).equals(
                        new TerminalLayout.Rect(22, 36,
                                TerminalLayout.ICON_CANVAS_SIZE, TerminalLayout.ICON_CANVAS_SIZE)));
        assertTrue("station amount geometry sits horizontally beside its icon",
                TerminalLayout.fuelAmountBounds(sampleFlowCell).equals(
                        new TerminalLayout.Rect(42, 39, 49, 9)));
        assertTrue("Fuel slot and amount never overlap",
                !TerminalLayout.fuelSlot(sampleFlowCell).overlaps(
                        TerminalLayout.fuelAmountBounds(sampleFlowCell)));

        var overflowCounts = new TerminalLayout.FuelDescriptorCounts(64, 64, 64, 61);
        var overflow = TerminalLayout.forProfile(TerminalProfile.CRAFTING, 423, 291, overflowCounts);
        assertTrue("large Transform target lists expose a paged persistent viewport",
                overflow.transformTargetList().capacity() > 0
                        && overflow.transformTargetList().pageCount() > 1
                        && overflow.transformTargetList().itemCount() == 61);
        assertLastPageLayout("transform cards", overflow.transformCards());
        assertLastPageLayout("timed stations", overflow.timedStationsGrid());
        assertLastPageLayout("instant stations", overflow.instantStationsGrid());
        assertTrue("descriptor growth preserves the inventory-side type-capacity panel",
                overflow.fuelStatus().equals(sideBySide.fuelStatus())
                        && overflow.instantStationsGrid().bounds().equals(
                        sideBySide.instantStationsGrid().bounds())
                        && overflow.transformCards().pageCount() > 1
                        && overflow.timedStationsGrid().pageCount() > 1
                        && overflow.instantStationsGrid().pageCount() > 1);
        for (int page = 0; page < overflow.instantStationsGrid().pageCount(); page++) {
            for (TerminalLayout.Rect cell : overflow.instantStationsGrid().cells(page)) {
                assertTrue("paged Instant Stations never enter the inventory-side type-capacity panel",
                        !cell.overlaps(overflow.fuelStatus()));
            }
        }

        var fullscreen = TerminalLayout.forProfile(
                TerminalProfile.CRAFTING, 640, 416, overflowCounts);
        assertTrue("fullscreen Processing Stations receive the larger workspace share",
                fullscreen.timedStationsPanel().width()
                        > fullscreen.instantStationsPanel().width());
        assertTrue("fullscreen Processing Stations use three readable work-value columns",
                fullscreen.timedStationsGrid().columns() == 3);
        var sparseProcessing = TerminalLayout.forProfile(
                TerminalProfile.CRAFTING,
                640,
                416,
                new TerminalLayout.FuelDescriptorCounts(4, 5, 3, 3))
                .timedStationsGrid()
                .cells();
        assertTrue("sparse Processing rows preserve the same left-aligned three-column rhythm",
                sparseProcessing.size() == 5
                        && sparseProcessing.get(0).x() == sparseProcessing.get(3).x()
                        && sparseProcessing.get(1).x() == sparseProcessing.get(4).x()
                        && sparseProcessing.stream()
                        .map(TerminalLayout.Rect::width)
                        .distinct()
                        .count() == 1);
        assertTrue("fullscreen Instant Stations use a denser icon grid",
                fullscreen.instantStationsGrid().columns()
                        > fullscreen.timedStationsGrid().columns());
        for (TerminalLayout.FlowGrid grid : List.of(
                fullscreen.timedStationsGrid(),
                fullscreen.instantStationsGrid())) {
            assertTrue("fullscreen Stations derive at least seven readable rows from available height",
                    grid.rows() >= 7);
            assertTrue("adaptive Station rows preserve an 18px item slot",
                    grid.cells().stream().allMatch(cell -> cell.height() >= TerminalLayout.SLOT_SIZE));
        }
        assertTrue("Station search uses the same readable adaptive row sizing",
                fullscreen.fuelSearchGrid().cells().stream()
                        .allMatch(cell -> cell.height() >= TerminalLayout.SLOT_SIZE));
        var currentFullscreen = TerminalLayout.forProfile(
                TerminalProfile.CRAFTING, 735, 478, overflowCounts);
        assertTrue("current fullscreen uses every complete item row above the inventory",
                currentFullscreen.visibleRows() > 9
                        && currentFullscreen.visibleRows()
                        == currentFullscreen.itemGrid().height() / TerminalLayout.SLOT_SIZE);
        assertTrue("current fullscreen display rows remain inside the fixed menu slot bank",
                currentFullscreen.visibleRows() <= StorageTerminalMenu.MAX_DISPLAY_ROWS);
        var transformCards = TerminalLayout.forProfile(
                TerminalProfile.CRAFTING,
                735,
                462,
                new TerminalLayout.FuelDescriptorCounts(2, 5, 3, 5));
        int transformActionBottom = Math.max(
                transformCards.transformInput().bottom(),
                transformCards.transformAmountButtons().getFirst().bottom());
        List<TerminalLayout.Rect> visibleTransformCards =
                transformCards.transformCards().cells();
        assertTrue("Transform cards begin immediately below the input action row",
                transformCards.transformCards().bounds().y()
                        == transformActionBottom + 2);
        assertTrue("Transform cards keep their compact fixed-height rhythm",
                visibleTransformCards.size() == 2
                        && visibleTransformCards.stream().allMatch(cell -> cell.height() == 32)
                        && visibleTransformCards.get(1).y()
                        == visibleTransformCards.get(0).bottom());
        assertTrue("Transform paging is reserved below cards instead of leaving a hidden top row",
                transformCards.transformCardPageControls().previous().y()
                        > transformCards.transformCards().bounds().bottom());
        var tallFullscreen = TerminalLayout.forProfile(
                TerminalProfile.CRAFTING, 640, 600, overflowCounts);
        assertTrue("taller viewports expose more Station rows instead of keeping a fixed cap",
                tallFullscreen.timedStationsGrid().rows()
                        > fullscreen.timedStationsGrid().rows()
                        && tallFullscreen.instantStationsGrid().rows()
                        > fullscreen.instantStationsGrid().rows()
                        && tallFullscreen.timedStationsGrid().rows() >= 8);

        for (int height = 240; height <= 600; height++) {
            for (int width : new int[]{320, 415, 416, 423, 480, 854}) {
                var geometry = TerminalLayout.forProfile(
                        TerminalProfile.CRAFTING, width, height, counts);
                int left = geometry.centeredFrameLeft(width);
                int top = (height - geometry.imageHeight()) / 2;
                assertTrue("crafting frame stays onscreen at " + width + "x" + height,
                        left >= 0 && top >= 0 && left + geometry.imageWidth() <= width
                                && top + geometry.imageHeight() <= height);
                assertTrue("crafting rail stays onscreen at " + width + "x" + height,
                        left + geometry.railPanel().x() >= 0
                                && top + geometry.railPanel().y() >= 0
                                && top + geometry.railPanel().bottom() <= height);
                int groupLeft = left + geometry.railPanel().x();
                int groupRight = left + geometry.imageWidth();
                assertTrue("crafting rail+frame group is centered at " + width + "x" + height,
                        Math.abs(groupLeft - (width - groupRight)) <= 1);
                if (geometry.wide()) {
                    assertTrue("side-by-side group keeps horizontal breathing room at "
                                    + width + "x" + height,
                            groupLeft >= 16 && width - groupRight >= 16);
                }
                assertTrue("crafting row count stays supported at " + width + "x" + height,
                        geometry.visibleRows() >= 1
                                && geometry.visibleRows() <= StorageTerminalMenu.MAX_DISPLAY_ROWS);
                assertTrue("crafting exclusion covers frame and rail at " + width + "x" + height,
                        geometry.exclusionRects().size() == 2
                                && geometry.exclusionRects().get(1).equals(geometry.railPanel()));
                assertTrue("crafting rail buttons do not overlap at " + width + "x" + height,
                        rectanglesDoNotOverlap(geometry.railButtons()));
                assertFuelCategoryGeometry(width + "x" + height, geometry, counts);
                assertRecipeGeometry(width + "x" + height, geometry);
            }
        }

        for (int height = 240; height <= 600; height++) {
            var geometry = TerminalLayout.forProfile(
                    TerminalProfile.STORAGE, 320, height,
                    TerminalLayout.FuelDescriptorCounts.none());
            assertTrue("storage geometry retains its terminal profile",
                    geometry.profile() == TerminalProfile.STORAGE);
            assertTrue("storage frame stays onscreen at height " + height,
                    geometry.imageHeight() <= height);
            assertTrue("storage rows stay inside the adaptive slot bank at height " + height,
                    geometry.visibleRows() >= 3
                            && geometry.visibleRows() <= StorageTerminalMenu.MAX_DISPLAY_ROWS);
            assertTrue("storage search ends before scrollbar at height " + height,
                    geometry.searchBackground().right() <= geometry.scrollbar().x());
            int left = geometry.centeredFrameLeft(320);
            int groupLeft = left + geometry.railPanel().x();
            int groupRight = left + geometry.imageWidth();
            assertTrue("storage rail+frame group is centered at height " + height,
                    Math.abs(groupLeft - (320 - groupRight)) <= 1);
        }
    }

    private static void testTerminalScrollbar() {
        assertTrue("disabled scrollbar thumb stays at the track top",
                TerminalScrollbar.thumbTop(10, 100, 0, 0) == 10);
        assertTrue("scrollbar starts at the track top",
                TerminalScrollbar.thumbTop(10, 100, 0, 90) == 10);
        assertTrue("scrollbar reaches the track bottom",
                TerminalScrollbar.thumbTop(10, 100, 90, 90) == 95);
        TerminalScrollbar scrollbar = new TerminalScrollbar();
        int pressed = scrollbar.press(57, 10, 100, 45, 90, 18);
        assertTrue("thumb press preserves the current position", pressed == 45);
        assertTrue("thumb press enters grabbed state", scrollbar.isDragging());
        assertTrue("thumb drag preserves the exact five-pixel grab offset",
                scrollbar.drag(67, 10, 100, 90) == 55);
        scrollbar.release();

        int paged = scrollbar.press(90, 10, 100, 45, 90, 18);
        assertTrue("track click pages once toward the pointer", paged == 63);
        assertTrue("track click is held but does not grab the thumb",
                scrollbar.isPressed() && !scrollbar.isDragging());
        assertTrue("track hold waits before repeating",
                scrollbar.tick(90, 18).isEmpty()
                        && scrollbar.tick(90, 18).isEmpty()
                        && scrollbar.tick(90, 18).isEmpty()
                        && scrollbar.tick(90, 18).isEmpty());
        assertTrue("track hold repeats after its initial delay",
                scrollbar.tick(90, 18).orElse(-1) == 81);
        scrollbar.release();

        assertTrue("wheel step is immediate and row aligned",
                scrollbar.step(45, 9, 90) == 54);
        assertTrue("disabled scrollbar exposes a disabled visual state",
                scrollbar.visualState(0) == TerminalScrollbar.VisualState.DISABLED);
        assertTrue("released enabled scrollbar exposes its normal visual state",
                scrollbar.visualState(90) == TerminalScrollbar.VisualState.ENABLED);
    }

    private static void assertFuelCategoryGeometry(
            String label,
            TerminalLayout.Geometry geometry,
            TerminalLayout.FuelDescriptorCounts counts
    ) {
        TerminalLayout.Rect transform = geometry.transformPanel();
        List<TerminalLayout.Rect> stations = List.of(
                geometry.timedStationsPanel(),
                geometry.instantStationsPanel());
        List<TerminalLayout.Rect> panels = stations;
        assertTrue(label + " Transform fills the complete upper workspace",
                transform.x() == 8
                        && transform.y() == TerminalLayout.TOP_HEIGHT
                        && transform.width() == geometry.imageWidth() - 16
                        && transform.bottom() == geometry.playerInventory().y() - 13);
        assertTrue(label + " all four page viewports share the inventory upper edge",
                !geometry.wide()
                        || geometry.itemGrid().bottom() == transform.bottom()
                        && geometry.scrollbar().bottom() == transform.bottom()
                        && geometry.visibleRows()
                        == Math.min(
                        StorageTerminalMenu.MAX_DISPLAY_ROWS,
                        geometry.itemGrid().height() / TerminalLayout.SLOT_SIZE));
        assertTrue(label + " Stations use two non-overlapping top-aligned groups",
                rectanglesDoNotOverlap(stations)
                        && stations.getFirst().x() == transform.x()
                        && stations.getFirst().y() == transform.y()
                        && stations.getLast().y() == transform.y()
                        && stations.getFirst().bottom() == transform.bottom()
                        && stations.getLast().bottom() == transform.bottom()
                        && stations.getLast().right() == transform.right());
        assertTrue(label + " Stations preserve a visible group gap",
                stations.getLast().x() > stations.getFirst().right());
        assertPagedFlowGrid(label + " transform cards", geometry.transformCards(), counts.consumableCount());
        assertPagedFlowGrid(label + " timed stations", geometry.timedStationsGrid(), counts.timedStationCount());
        assertPagedFlowGrid(label + " instant stations", geometry.instantStationsGrid(), counts.instantStationCount());
        List<TerminalLayout.FlowGrid> grids = List.of(
                geometry.timedStationsGrid(),
                geometry.instantStationsGrid());
        boolean horizontalHeaders = grids.get(0).bounds().x() == panels.get(0).x() + 2
                && grids.get(1).bounds().x() == panels.get(1).x() + 2;
        for (int index = 0; index < grids.size(); index++) {
            TerminalLayout.Rect panel = panels.get(index);
            TerminalLayout.FlowGrid grid = grids.get(index);
            TerminalLayout.Rect header = TerminalLayout.fuelCategoryLabel(panel, grid);
            if (horizontalHeaders) {
                assertTrue(label + " tall Fuel rows use a horizontal header above full-width content",
                        header.x() == panel.x() + 2
                                && header.width() == panel.width() - 4
                                && header.y() == panel.y() + 2
                                && header.bottom() <= grid.bounds().y()
                                && grid.bounds().right() == panel.right() - 2);
            } else {
                assertTrue(label + " short Fuel rows retain a bounded compact label",
                        grid.bounds().x() >= panel.x() + 68
                                && header.right() <= grid.bounds().x());
            }
        }
        assertTrue(label + " Transform input remains a standalone slot outside recipe cards",
                geometry.transformPanel().contains(
                        geometry.transformInput().x(), geometry.transformInput().y())
                        && geometry.transformCards().cells().stream()
                        .noneMatch(cell -> cell.overlaps(geometry.transformInput())));
        assertTrue(label + " Transform and Stations searches reuse the common top search position",
                geometry.transformTargetSearch().equals(geometry.searchBox())
                        && geometry.fuelSearchBox().equals(geometry.searchBox()));
        assertTrue(label + " Transform target list begins at the top of the left column",
                geometry.transformTargetList().bounds().x()
                        == geometry.transformPanel().x() + 2
                        && geometry.transformTargetList().bounds().y()
                        == geometry.transformPanel().y() + 2
                        && geometry.transformTargetList().bounds().right()
                        < geometry.transformCards().bounds().x());
        assertTrue(label + " Transform amount actions stay in the upper input band",
                geometry.transformAmountButtons().stream().allMatch(button ->
                        button.y() >= geometry.transformInput().y()
                                && button.bottom() <= geometry.transformCards().bounds().y()));
        assertTrue(label + " Transform paging follows the compact card area",
                geometry.transformCards().bounds().bottom()
                        < geometry.transformCardPageControls().previous().y());
        assertTrue(label + " type capacity is an independent panel right of player inventory",
                geometry.fuelStatus().x() == geometry.playerInventory().right() + 2
                        && geometry.fuelStatus().right() == geometry.imageWidth() - 8
                        && geometry.fuelStatus().y() == geometry.playerInventory().y()
                        && geometry.fuelStatus().bottom() == geometry.playerInventory().bottom()
                        && !geometry.fuelStatus().overlaps(geometry.playerInventory())
                        && !geometry.fuelStatus().overlaps(geometry.instantStationsPanel()));
        assertTrue(label + " type capacity keeps icon and amount horizontal",
                TerminalLayout.fuelIcon(geometry.fuelStatus()).right()
                        <= TerminalLayout.fuelAmountBounds(geometry.fuelStatus()).x());
        assertTrue(label + " unified Fuel search replaces the complete upper workspace",
                geometry.fuelSearchPanel().equals(transform)
                        && geometry.fuelSearchPanel().right() == stations.getLast().right()
                        && geometry.fuelSearchPanel().bottom() == stations.getLast().bottom()
                        && !geometry.fuelSearchPanel().overlaps(geometry.playerInventory()));
        int searchableCount = counts.timedStationCount() + counts.instantStationCount();
        assertPagedFlowGrid(
                label + " unified Fuel search",
                geometry.fuelSearchGrid(),
                searchableCount);
        assertTrue(label + " filtered Fuel search cells use only the requested result count",
                geometry.fuelSearchGrid().cells(0, Math.min(2, searchableCount)).size()
                        == Math.min(2, searchableCount));
        assertTrue(label + " Fuel search paging controls stay inside the unified header",
                geometry.fuelSearchPanel().contains(
                        geometry.fuelSearchPageControls().previous().x(),
                        geometry.fuelSearchPageControls().previous().y())
                        && geometry.fuelSearchPanel().contains(
                        geometry.fuelSearchPageControls().next().x(),
                        geometry.fuelSearchPageControls().next().y())
                        && !geometry.fuelSearchPageControls().previous().overlaps(
                        geometry.fuelSearchPageControls().next()));
        for (TerminalLayout.FlowGrid grid : List.of(
                geometry.timedStationsGrid(),
                geometry.instantStationsGrid())) {
            for (TerminalLayout.Rect cell : grid.cells()) {
                TerminalLayout.Rect slot = TerminalLayout.fuelSlot(cell);
                TerminalLayout.Rect icon = TerminalLayout.fuelIcon(cell);
                assertTrue(label + " Fuel item content stays inside its cell",
                        slot.x() >= cell.x() && slot.y() >= cell.y()
                                && slot.right() <= cell.right() && slot.bottom() <= cell.bottom()
                                && icon.x() >= cell.x() && icon.y() >= cell.y()
                                && icon.right() <= cell.right() && icon.bottom() <= cell.bottom());
                if (!grid.compact()) {
                    TerminalLayout.Rect amount = TerminalLayout.fuelAmountBounds(cell);
                    assertTrue(label + " Fuel slot and icon stay left of amount",
                            slot.right() <= amount.x() && icon.right() <= amount.x());
                    assertTrue(label + " Processing work stays inside its cell",
                            amount.x() >= cell.x() && amount.y() >= cell.y()
                                    && amount.right() <= cell.right()
                                    && amount.bottom() <= cell.bottom());
                }
            }
        }
    }

    private static void assertPagedFlowGrid(
            String label,
            TerminalLayout.FlowGrid grid,
            int expectedCount
    ) {
        assertTrue(label + " preserves descriptor count", grid.itemCount() == expectedCount);
        assertTrue(label + " has bounded positive page dimensions",
                grid.columns() >= 1 && grid.rows() >= 1
                        && grid.capacity() == grid.columns() * grid.rows());
        assertTrue(label + " visible cells do not overlap", rectanglesDoNotOverlap(grid.cells()));
        if (!grid.compact() && !grid.cells().isEmpty()) {
            assertTrue(label + " starts at the fixed grid origin",
                    grid.cells().getFirst().x() == grid.bounds().x());
        }
        for (TerminalLayout.Rect cell : grid.cells()) {
            assertTrue(label + " cell stays inside row bounds",
                    cell.x() >= grid.bounds().x()
                            && cell.y() >= grid.bounds().y()
                            && cell.right() <= grid.bounds().right()
                            && cell.bottom() <= grid.bounds().bottom());
        }
    }

    private static void assertLastPageLayout(String label, TerminalLayout.FlowGrid grid) {
        List<TerminalLayout.Rect> last = grid.cells(grid.pageCount() - 1);
        assertTrue(label + " overflow creates at least two pages", grid.pageCount() >= 2);
        assertTrue(label + " last page starts at the grid origin",
                !last.isEmpty() && last.getFirst().x() == grid.bounds().x());
        if (!grid.compact()) {
            assertTrue(label + " last page preserves the first-page cell width",
                    last.getFirst().width() == grid.cells().getFirst().width());
        }
    }

    private static void assertRecipeGeometry(String label, TerminalLayout.Geometry geometry) {
        assertTrue(label + " recipe regions remain ordered",
                geometry.recipeDiagram().bottom() <= geometry.recipeLedger().y()
                        && geometry.recipeLedger().bottom() <= geometry.recipeFooter().y());
        assertTrue(label + " recipe empty state unifies diagram and ledger",
                geometry.recipeContent().equals(new TerminalLayout.Rect(
                        geometry.recipeDiagram().x(),
                        geometry.recipeDiagram().y(),
                        geometry.recipeDiagram().width(),
                        geometry.recipeLedger().bottom() - geometry.recipeDiagram().y()))
                        && geometry.recipeContent().bottom() <= geometry.recipeFooter().y()
                        && !geometry.recipeContent().overlaps(geometry.recipeFooter()));
        int contentTopLimit = geometry.workspace().y() + 4;
        int contentBottomLimit = geometry.recipeFooter().y() - 4;
        int bottomBreathingRoom = contentBottomLimit - geometry.recipeContent().bottom();
        assertTrue(label + " recipe body keeps a small symmetric top and side inset",
                geometry.recipeContent().y() == contentTopLimit
                        && geometry.recipeContent().x() - geometry.workspace().x() == 4
                        && geometry.recipeLedger().height() <= 54
                        && bottomBreathingRoom == 0);
        assertTrue(label + " recipe diagram exposes nine bounded inputs",
                geometry.recipeInputSlots().size() == RecipePresentation.MAX_INPUTS
                        && rectanglesDoNotOverlap(geometry.recipeInputSlots()));
        int ledgerCapacity = geometry.recipeLedgerCapacity();
        assertTrue(label + " ledger exposes a bounded readable viewport",
                ledgerCapacity >= 2 && ledgerCapacity <= 12);
        for (int resourceCount = 0; resourceCount <= ledgerCapacity; resourceCount++) {
            List<TerminalLayout.Rect> cells = geometry.recipeLedgerCells(resourceCount);
            assertTrue(label + " ledger row count follows resources",
                    cells.size() == resourceCount
                            && rectanglesDoNotOverlap(cells)
                            && cells.stream().allMatch(cell -> cell.height() >= 16));
        }
        List<TerminalLayout.Rect> oneResource = geometry.recipeLedgerCells(1);
        assertTrue(label + " resource rows align to the ledger top",
                oneResource.size() == 1
                        && oneResource.getFirst().height() <= TerminalLayout.SLOT_SIZE
                        && oneResource.getFirst().y() == geometry.recipeLedger().y());
        if (ledgerCapacity >= 9) {
            List<TerminalLayout.Rect> nineResources = geometry.recipeLedgerCells(9);
            assertTrue(label + " nine resources keep three readable rows and at most four columns",
                    nineResources.stream().map(TerminalLayout.Rect::y).distinct().count() == 3
                            && nineResources.stream().map(TerminalLayout.Rect::x)
                            .distinct().count() <= 4
                            && nineResources.stream().allMatch(cell -> cell.width() >= 56));
        }
        List<TerminalLayout.Rect> craftButtons = geometry.recipeCraftButtons();
        assertTrue(label + " amount strip contains four equal segments", craftButtons.size() == 4
                && craftButtons.stream().map(TerminalLayout.Rect::width).distinct().count() == 1);
        for (int index = 1; index < craftButtons.size(); index++) {
            assertTrue(label + " amount strip segments are contiguous",
                    craftButtons.get(index - 1).right() == craftButtons.get(index).x());
        }
        List<TerminalLayout.Rect> allFooterControls = new ArrayList<>(geometry.recipeNavigationButtons());
        allFooterControls.addAll(craftButtons);
        assertTrue(label + " navigation and amount strip do not overlap",
                rectanglesDoNotOverlap(allFooterControls));
    }

    private static boolean rectanglesDoNotOverlap(List<TerminalLayout.Rect> rectangles) {
        for (int left = 0; left < rectangles.size(); left++) {
            for (int right = left + 1; right < rectangles.size(); right++) {
                if (rectangles.get(left).overlaps(rectangles.get(right))) return false;
            }
        }
        return true;
    }

    private static void assertTrue(String message, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            MagicStorage.LOGGER.error("SelfTest FAILED: {}", message);
        }
    }
}
