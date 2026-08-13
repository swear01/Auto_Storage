package com.swear.autostorage.fixture.powah;

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.Action;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.StorageTerminalMenu;
import com.swear.autostorage.TerminalDisplayStack;
import com.swear.autostorage.TerminalResourceDisplay;
import com.swear.autostorage.TerminalResourceView;
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
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
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import owmii.powah.Powah;
import owmii.powah.block.Tier;

import java.util.EnumMap;
import java.util.Map;

@GameTestHolder(PowahFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class PowahIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int TRANSFORM_PAGE_BUTTON = 15;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int NEXT_RESOURCE_VIEW_BUTTON = 26;
    private static final ResourceLocation ENERGIZING = stationId("energizing");
    private static final ResourceLocation FURNATOR = stationId("furnator");
    private static final ResourceLocation MAGMATOR =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "powah_magmator");
    private static final ResourceLocation FURNATOR_TRANSFORM =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "powah_furnator");
    private static final ResourceLocation ENERGIZED_STEEL =
            powahId("energizing/energized_steel");
    private static final ResourceLocation SIX_INPUT_BATCH = fixtureRecipe("six_input_batch");

    private PowahIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void energizing_registers_live_rod_rates_and_safe_recipes(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), PowahIntegrationGameTests.class);
        MachineDescriptor descriptor = MachineEnergyTable.get(ENERGIZING);
        if (descriptor == null
                || descriptor.category() != MachineCategory.PROCESS
                || descriptor.maxInstalledCount() != MachineDescriptorApi.MAX_INSTALLED_COUNT
                || descriptor.energyType() != null
                || descriptor.variants().size() != Tier.getNormalVariants().length
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(ENERGIZING)) {
            helper.fail("Missing Powah Energizing station/family");
            return;
        }
        Map<Tier, MachineWorkRate> expected = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.getNormalVariants()) {
            expected.put(tier, MachineWorkRate.of(
                    Powah.config().devices.energizing_rods.getTransfer(tier), 1));
        }
        for (Tier tier : Tier.getNormalVariants()) {
            ItemStack rod = new ItemStack(powahItem("energizing_rod_" + tier.getName()));
            if (!descriptor.accepts(rod)
                    || !descriptor.rateFor(rod).orElseThrow().equals(expected.get(tier))) {
                helper.fail("Powah rod rate did not match loaded config for " + tier.getName());
                return;
            }
        }
        if (!supports(helper, ENERGIZED_STEEL)
                || !supports(helper, SIX_INPUT_BATCH)
                || supports(helper, fixtureRecipe("seven_inputs"))
                || supports(helper, fixtureRecipe("zero_energy"))) {
            helper.fail("Powah unsupported input-count/energy behavior did not fail closed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void energy_cell_uses_standard_neoforge_energy_capability(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(
                pos,
                BuiltInRegistries.BLOCK.get(powahId("energy_cell_starter")).defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> assertEnergyCellCapability(helper, level, pos));
    }

    private static void assertEnergyCellCapability(
            GameTestHelper helper,
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos
    ) {
        IEnergyStorage energy = level.getCapability(
                Capabilities.EnergyStorage.BLOCK, pos, null);
        if (energy == null) {
            helper.fail("Powah Energy Cell did not expose NeoForge Energy");
            return;
        }
        int simulatedInsert = energy.receiveEnergy(200, true);
        int afterInsertSimulation = energy.getEnergyStored();
        int inserted = energy.receiveEnergy(200, false);
        int afterInsert = energy.getEnergyStored();
        int simulatedExtract = energy.extractEnergy(75, true);
        int afterExtractSimulation = energy.getEnergyStored();
        int extracted = energy.extractEnergy(75, false);
        int afterExtract = energy.getEnergyStored();
        if (simulatedInsert != 200
                || afterInsertSimulation != 0
                || inserted != 200
                || afterInsert != 200
                || simulatedExtract != 75
                || afterExtractSimulation != 200
                || extracted != 75
                || afterExtract != 125) {
            helper.fail("Powah Energy Cell capability values were "
                    + simulatedInsert + "/" + afterInsertSimulation + "/"
                    + inserted + "/" + afterInsert + "/"
                    + simulatedExtract + "/" + afterExtractSimulation + "/"
                    + extracted + "/" + afterExtract);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void terminal_displays_fe_and_core_reload_preserves_it(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            StorageResourceKey energy = StorageResourceKey.neoforgeEnergy();
            seedResource(context.core(), energy, 4_321);
            var menu = new StorageTerminalMenu(
                    911, context.player().getInventory(), context.core());
            selectEnergyView(menu, context);
            ItemStack display = menu.getSlot(0).getItem();
            if (TerminalResourceDisplay.key(display).filter(energy::equals).isEmpty()
                    || TerminalDisplayStack.amount(display) != 4_321) {
                helper.fail("Terminal did not display exact stored Powah FE");
                return;
            }
            CompoundTag saved = context.core().saveWithoutMetadata(
                    context.level().registryAccess());
            BlockPos pos = context.core().getBlockPos();
            context.level().removeBlockEntity(pos);
            var reloaded = new StorageCoreBlockEntity(
                    pos, AutoStorage.STORAGE_CORE.get().defaultBlockState());
            reloaded.loadWithComponents(saved, context.level().registryAccess());
            context.level().setBlockEntity(reloaded);
            helper.runAfterDelay(2, () -> {
                reloaded.rebuildNetwork(context.level());
                if (reloaded.getResourceAmount(energy) != 4_321) {
                    helper.fail("Powah FE did not survive the Core repository reload");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void official_energized_steel_consumes_items_fe_and_work(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedItem(context.core(), Items.GOLD_INGOT, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), 10_000);
            if (!installStarterRod(context)) {
                helper.fail("Could not install the Powah Energizing Rod");
                return;
            }
            tick(context.core(), 100);
            if (!craft(context, ENERGIZED_STEEL, 1)
                    || itemCount(context.core(), Items.IRON_INGOT) != 0
                    || itemCount(context.core(), Items.GOLD_INGOT) != 0
                    || itemCount(context.core(), powahItem("steel_energized")) != 2
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(ENERGIZING) != 0) {
                helper.fail("Official Energized Steel recipe committed wrong resources");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void six_input_recipe_batches_output_fe_and_work(GameTestHelper helper) {
        withCore(helper, context -> {
            for (Item item : batchInputs()) seedItem(context.core(), item, 2);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), 2_400);
            if (!installStarterRod(context)) {
                helper.fail("Could not install the Powah Energizing Rod");
                return;
            }
            tick(context.core(), 24);
            if (!craft(context, SIX_INPUT_BATCH, 2)
                    || batchInputs().stream().anyMatch(item -> itemCount(context.core(), item) != 0)
                    || itemCount(context.core(), Items.LAPIS_LAZULI) != 6
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(ENERGIZING) != 0) {
                helper.fail("Six-input Energizing batch committed wrong resources");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void fe_shortage_rejects_without_partial_mutation(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedItem(context.core(), Items.GOLD_INGOT, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), 9_999);
            if (!installStarterRod(context)) {
                helper.fail("Could not install the Powah Energizing Rod");
                return;
            }
            tick(context.core(), 100);
            if (craft(context, ENERGIZED_STEEL, 1)
                    || itemCount(context.core(), Items.IRON_INGOT) != 1
                    || itemCount(context.core(), Items.GOLD_INGOT) != 1
                    || itemCount(context.core(), powahItem("steel_energized")) != 0
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 9_999
                    || context.core().getStationWork(ENERGIZING) != 10_000) {
                helper.fail("Powah FE shortage partially mutated the transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void station_work_shortage_rejects_without_partial_mutation(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedItem(context.core(), Items.GOLD_INGOT, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), 10_000);
            if (!installStarterRod(context)) {
                helper.fail("Could not install the Powah Energizing Rod");
                return;
            }
            tick(context.core(), 99);
            if (craft(context, ENERGIZED_STEEL, 1)
                    || itemCount(context.core(), Items.IRON_INGOT) != 1
                    || itemCount(context.core(), Items.GOLD_INGOT) != 1
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 10_000
                    || context.core().getStationWork(ENERGIZING) != 9_900) {
                helper.fail("Powah station-work shortage partially mutated the transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void output_overflow_rolls_back_items_fe_and_work(GameTestHelper helper) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.IRON_INGOT, 1);
            seedItem(context.core(), Items.GOLD_INGOT, 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), 10_000);
            StorageResourceKey output = itemKey(
                    context, new ItemStack(powahItem("steel_energized")));
            seedResource(context.core(), output, Long.MAX_VALUE);
            if (!installStarterRod(context)) {
                helper.fail("Could not install the Powah Energizing Rod");
                return;
            }
            tick(context.core(), 100);
            if (craft(context, ENERGIZED_STEEL, 1)
                    || itemCount(context.core(), Items.IRON_INGOT) != 1
                    || itemCount(context.core(), Items.GOLD_INGOT) != 1
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 10_000
                    || context.core().getResourceAmount(output) != Long.MAX_VALUE
                    || context.core().getStationWork(ENERGIZING) != 10_000) {
                helper.fail("Rejected Powah output overflow partially mutated the transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void higher_tier_rod_uses_its_loaded_transfer_rate(GameTestHelper helper) {
        withCore(helper, context -> {
            ItemStack nitro = new ItemStack(powahItem("energizing_rod_nitro"));
            if (!installStation(context, nitro)) {
                helper.fail("Could not install the Nitro Energizing Rod");
                return;
            }
            long expected = Powah.config().devices.energizing_rods.getTransfer(Tier.NITRO);
            tick(context.core(), 1);
            if (context.core().getStationWork(ENERGIZING) != expected) {
                helper.fail("Nitro Energizing Rod did not use its loaded transfer rate");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void furnator_transform_requires_station_and_commits_fe_work_atomically(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            var menu = transformMenu(context, new ItemStack(Items.COAL));
            var furnatorUse = menu.getTransformUses().stream()
                    .filter(use -> use.id().equals(FURNATOR_TRANSFORM))
                    .findFirst()
                    .orElse(null);
            if (furnatorUse == null
                    || !furnatorUse.targetId().equals(StorageResourceKindApi.ENERGY_KIND)
                    || menu.getTransformTargets().stream().noneMatch(
                    target -> target.id().equals(StorageResourceKindApi.ENERGY_KIND))) {
                helper.fail("Furnator must be a distinct recipe card under the FE target");
                return;
            }
            selectTransform(menu, context, FURNATOR_TRANSFORM);
            if (menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().is(Items.COAL)) {
                helper.fail("Furnator transform must reject without an installed Furnator");
                return;
            }

            if (!installStation(context, new ItemStack(powahItem("furnator_starter")))) {
                helper.fail("Could not install the Starter Furnator");
                return;
            }
            long output = expectedFurnatorOutput(new ItemStack(Items.COAL));
            long rate = Powah.config().generators.furnators.getGeneration(Tier.STARTER);
            tick(context.core(), Math.toIntExact((output + rate - 1) / rate));
            if (!menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy()) != output
                    || context.core().getStationWork(FURNATOR) !=
                    Math.toIntExact((output + rate - 1) / rate) * rate - output
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().isEmpty()) {
                helper.fail("Furnator transform committed the wrong FE/work/input transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void furnator_transform_overflow_and_removed_station_fail_without_mutation(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            if (!installStation(context, new ItemStack(powahItem("furnator_nitro")))) {
                helper.fail("Could not install the Nitro Furnator");
                return;
            }
            long output = expectedFurnatorOutput(new ItemStack(Items.COAL));
            long rate = Powah.config().generators.furnators.getGeneration(Tier.NITRO);
            tick(context.core(), Math.toIntExact((output + rate - 1) / rate));
            long workBefore = context.core().getStationWork(FURNATOR);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), Long.MAX_VALUE);

            var menu = transformMenu(context, new ItemStack(Items.COAL));
            selectTransform(menu, context, FURNATOR_TRANSFORM);
            if (menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(StorageResourceKey.neoforgeEnergy())
                    != Long.MAX_VALUE
                    || context.core().getStationWork(FURNATOR) != workBefore
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().is(Items.COAL)) {
                helper.fail("Furnator output overflow partially mutated the transform");
                return;
            }

            removeStation(context, FURNATOR);
            context.core().extractResource(
                    StorageResourceKey.neoforgeEnergy(), Long.MAX_VALUE, Action.EXECUTE);
            if (menu.clickMenuButton(context.player(), 2)
                    || context.core().getStationWork(FURNATOR) != workBefore
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().is(Items.COAL)) {
                helper.fail("Removing a Furnator must disable its transform without deleting work");
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

    @GameTest(template = "craftingtests.platform")
    public static void magmator_lava_bucket_converts_to_fe_work_and_retained_bucket(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            var menu = transformMenu(
                    context, new ItemStack(Items.LAVA_BUCKET));
            var magmatorUse = menu.getTransformUses().stream()
                    .filter(use -> use.id().equals(MAGMATOR))
                    .findFirst()
                    .orElse(null);
            long energyPerBucket = Math.multiplyExact(
                    10L,
                    owmii.powah.api.PowahAPI.getMagmaticFluidEnergyProduced(
                            net.minecraft.world.level.material.Fluids.LAVA));
            if (magmatorUse == null
                    || magmatorUse.amountPerItem() != energyPerBucket
                    || magmatorUse.stationWorkPerItem() != 10
                    || magmatorUse.retainedItems().size() != 1
                    || !magmatorUse.retainedItems().getFirst().is(Items.BUCKET)) {
                helper.fail("Magmator use must convert one lava bucket to exact FE "
                        + "over 10 work retaining an empty bucket");
                return;
            }
            selectTransform(menu, context, MAGMATOR);
            if (menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("Magmator must reject without an installed magmator");
                return;
            }
            if (!installStation(context, new ItemStack(powahItem("magmator_starter")))) {
                helper.fail("Could not install the Magmator");
                return;
            }
            tick(context.core(), 10);
            if (!menu.clickMenuButton(context.player(), 2)
                    || context.core().getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != energyPerBucket
                    || context.core().getStationWork(MAGMATOR) != 0
                    || !menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT)
                    .getItem().isEmpty()
                    || context.player().getInventory().countItem(Items.BUCKET) != 1) {
                helper.fail("Magmator committed the wrong FE/work/input/bucket transaction");
                return;
            }
            helper.succeed();
        });
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

    private static boolean installStarterRod(FixtureContext context) {
        return installStation(
                context, new ItemStack(powahItem("energizing_rod_starter")));
    }

    private static boolean installStation(FixtureContext context, ItemStack station) {
        var menu = new CraftingTerminalMenu(
                912, context.player().getInventory(), context.core());
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

    private static CraftingTerminalMenu transformMenu(
            FixtureContext context,
            ItemStack input
    ) {
        var menu = new CraftingTerminalMenu(
                914, context.player().getInventory(), context.core());
        menu.clickMenuButton(context.player(), TRANSFORM_PAGE_BUTTON);
        menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).set(input);
        return menu;
    }

    private static void selectTransform(
            CraftingTerminalMenu menu,
            FixtureContext context,
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
                context.player(), CraftingTerminalMenu.transformUseButtonId(useIndex))) {
            throw new IllegalStateException("Could not select transform " + transformId);
        }
    }

    private static void removeStation(
            FixtureContext context,
            ResourceLocation descriptorId
    ) {
        int slot = MachineEnergyTable.findSlot(descriptorId);
        var menu = new CraftingTerminalMenu(
                915, context.player().getInventory(), context.core());
        menu.clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        menu.getSlot(CraftingTerminalMenu.MACHINE_SLOT_START + slot).set(ItemStack.EMPTY);
    }

    private static long expectedFurnatorOutput(ItemStack stack) {
        return Math.multiplyExact(
                stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING),
                Powah.config().general.energy_per_fuel_tick);
    }

    private static boolean craft(
            FixtureContext context,
            ResourceLocation recipeId,
            int crafts
    ) {
        var menu = new CraftingTerminalMenu(
                913, context.player().getInventory(), context.core());
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

    private static void selectEnergyView(
            StorageTerminalMenu menu,
            FixtureContext context
    ) {
        for (int attempt = 0;
             attempt < TerminalResourceView.values().length
                     && menu.getResourceView() != TerminalResourceView.ENERGY;
             attempt++) {
            menu.clickMenuButton(context.player(), NEXT_RESOURCE_VIEW_BUTTON);
        }
        if (menu.getResourceView() != TerminalResourceView.ENERGY) {
            throw new IllegalStateException("Energy resource view is unavailable");
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

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static java.util.List<Item> batchInputs() {
        return java.util.List.of(
                Items.IRON_INGOT,
                Items.GOLD_INGOT,
                Items.REDSTONE,
                Items.QUARTZ,
                Items.DIAMOND,
                Items.EMERALD);
    }

    private static Item powahItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(powahId(path));
        if (item == Items.AIR) throw new IllegalStateException("Missing Powah item " + path);
        return item;
    }

    private static ResourceLocation powahId(String path) {
        return ResourceLocation.fromNamespaceAndPath("powah", path);
    }

    private static ResourceLocation fixtureRecipe(String path) {
        return ResourceLocation.fromNamespaceAndPath(PowahFixtureMod.MODID, path);
    }

    private static ResourceLocation stationId(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                AutoStorage.MODID, "powah_" + path);
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
