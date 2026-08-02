package com.swear.autostorage.fixture.arsnouveau;

import com.swear.autostorage.MachineCategory;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.swear.autostorage.Action;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceBlockStrategy;
import com.swear.autostorage.StorageResourceHandler;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKind;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.StorageTerminalMenu;
import com.swear.autostorage.TerminalDisplayStack;
import com.swear.autostorage.TerminalResourceView;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(ArsNouveauFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ArsNouveauIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int NEXT_RESOURCE_VIEW_BUTTON = 26;
    private static final ResourceLocation IMBUEMENT =
            stationId("imbuement_chamber");
    private static final ResourceLocation APPARATUS =
            stationId("enchanting_apparatus");
    private static final ResourceLocation SOURCE_STRATEGY =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "ars_nouveau_source");
    private static final ResourceLocation KEEP_COMPONENTS =
            fixtureRecipe("apparatus_keep_components");
    private static final ResourceLocation TOO_MANY_INPUTS =
            fixtureRecipe("apparatus_too_many_inputs");
    private static final ResourceLocation RETAINED_INPUT_OVERLAP =
            fixtureRecipe("imbuement_retained_input_overlap");

    private ArsNouveauIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void source_kind_and_timed_recipe_families_are_conditional(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), ArsNouveauIntegrationGameTests.class);
        StorageResourceKind sourceKind = AutoStorage.RESOURCE_KIND_REGISTRY.get(
                StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND);
        if (sourceKind == null
                || !sourceKind.representative().is(arsItem("source_gem"))
                || !Component.translatable("gui.auto_storage.resource.source")
                .equals(sourceKind.representative().get(DataComponents.CUSTOM_NAME))
                || !TerminalResourceView.ENERGY.isAvailable()
                || !TerminalResourceView.ENERGY.matches(sourceKey())
                || TerminalResourceView.OTHER.matches(sourceKey())) {
            helper.fail("Ars Nouveau Source was not exposed in the Energy resource group");
            return;
        }
        for (var station : List.of(
                new StationSpec(IMBUEMENT, "imbuement_chamber"),
                new StationSpec(APPARATUS, "enchanting_apparatus"))) {
            MachineDescriptor descriptor = MachineEnergyTable.get(station.descriptorId());
            ItemStack stack = new ItemStack(arsItem(station.itemPath()));
            if (descriptor == null
                    || descriptor.category() != MachineCategory.PROCESS
                    || !descriptor.accepts(stack)
                    || !descriptor.rateFor(stack).orElseThrow().equals(MachineWorkRate.of(1, 1))
                    || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(station.descriptorId())) {
                helper.fail("Missing exact Ars Nouveau timed station/family: " + station);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void source_jar_strategy_simulates_then_commits_exact_amounts(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(
                pos,
                BuiltInRegistries.BLOCK.get(arsId("source_jar")).defaultBlockState(),
                Block.UPDATE_ALL);
        ISourceCap source = level.getCapability(
                CapabilityRegistry.SOURCE_CAPABILITY, pos, null);
        StorageResourceBlockStrategy strategy =
                AutoStorage.RESOURCE_BLOCK_STRATEGY_REGISTRY.get(SOURCE_STRATEGY);
        if (source == null || source.receiveSource(4_000, false) != 4_000 || strategy == null) {
            helper.fail("Could not create the Source Jar block strategy fixture");
            return;
        }
        StorageResourceHandler handler = strategy.find(level, pos, null).orElse(null);
        if (handler == null
                || !handler.getStoredResources().equals(List.of(sourceKey()))
                || handler.getAmount(sourceKey()) != 4_000
                || handler.extract(sourceKey(), 2_500, true) != 2_500
                || source.getSource() != 4_000
                || handler.extract(sourceKey(), 2_500, false) != 2_500
                || source.getSource() != 1_500
                || handler.insert(sourceKey(), 1_000, true) != 1_000
                || source.getSource() != 1_500
                || handler.insert(sourceKey(), 1_000, false) != 1_000
                || source.getSource() != 2_500) {
            helper.fail("Source Jar strategy violated exact simulate/commit semantics");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void terminal_displays_source_and_core_reload_preserves_it(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedResource(context.core(), sourceKey(), 4_321);
            var menu = new StorageTerminalMenu(
                    801, context.player().getInventory(), context.core());
            selectEnergyView(menu, context);
            if (!menu.getSlot(0).getItem().is(arsItem("source_gem"))
                    || TerminalDisplayStack.amount(menu.getSlot(0).getItem()) != 4_321
                    || !Component.translatable("gui.auto_storage.resource.source")
                    .equals(menu.getSlot(0).getItem().get(DataComponents.CUSTOM_NAME))) {
                helper.fail("Terminal did not display exact stored Source");
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
                if (reloaded.getResourceAmount(sourceKey()) != 4_321) {
                    helper.fail("Source did not survive the Core repository reload");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void imbuement_batches_source_and_retains_all_pedestal_catalysts(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), arsItem("source_gem"), 2);
            seedItem(context.core(), Items.FERMENTED_SPIDER_EYE, 1);
            seedItem(context.core(), Items.SUGAR, 1);
            seedItem(context.core(), Items.MILK_BUCKET, 1);
            seedResource(context.core(), sourceKey(), 4_000);
            if (!installStation(context, "imbuement_chamber")) {
                helper.fail("Could not install the Imbuement Chamber");
                return;
            }
            tick(context.core(), 200);
            if (!craft(context, arsRecipe("imbuement_abjuration_essence"), 2)
                    || itemCount(context.core(), arsItem("source_gem")) != 0
                    || itemCount(context.core(), Items.FERMENTED_SPIDER_EYE) != 1
                    || itemCount(context.core(), Items.SUGAR) != 1
                    || itemCount(context.core(), Items.MILK_BUCKET) != 1
                    || itemCount(context.core(), arsItem("abjuration_essence")) != 2
                    || context.core().getResourceAmount(sourceKey()) != 0
                    || context.core().getStationWork(IMBUEMENT) != 0) {
                helper.fail("Imbuement did not retain catalysts or consume exact Source/work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void apparatus_returns_container_remainders(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.GLASS_BOTTLE, 1);
            seedItem(context.core(), Items.LAVA_BUCKET, 1);
            seedItem(context.core(), Items.BUCKET, 1);
            seedItem(context.core(), arsItem("allow_scroll"), 1);
            seedItem(context.core(), Items.ENDER_PEARL, 1);
            if (!installStation(context, "enchanting_apparatus")) {
                helper.fail("Could not install the Enchanting Apparatus");
                return;
            }
            tick(context.core(), 210);
            if (!craft(context, arsRecipe("void_jar"), 1)
                    || itemCount(context.core(), arsItem("void_jar")) != 1
                    || itemCount(context.core(), Items.BUCKET) != 1
                    || itemCount(context.core(), Items.LAVA_BUCKET) != 0
                    || itemCount(context.core(), Items.GLASS_BOTTLE) != 0
                    || context.core().getStationWork(APPARATUS) != 0) {
                helper.fail("Apparatus did not preserve exact pedestal container remainders");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void apparatus_output_copies_reagent_components_and_resets_damage(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            ItemStack reagent = new ItemStack(Items.IRON_SWORD);
            reagent.set(DataComponents.CUSTOM_NAME, Component.literal("Bound Test Blade"));
            reagent.setDamageValue(117);
            ItemKey damaged = ItemKey.of(reagent);
            ItemStack expected = reagent.copy();
            expected.setDamageValue(0);
            ItemKey repaired = ItemKey.of(expected);
            seedItem(context.core(), reagent, 1);
            seedItem(context.core(), Items.DIAMOND, 1);
            seedResource(context.core(), sourceKey(), 100);
            if (!installStation(context, "enchanting_apparatus")) {
                helper.fail("Could not install the Enchanting Apparatus");
                return;
            }
            tick(context.core(), 210);
            if (!craft(context, KEEP_COMPONENTS, 1)
                    || context.core().getItemCount(damaged) != 0
                    || context.core().getItemCount(repaired) != 1
                    || itemCount(context.core(), Items.DIAMOND) != 0
                    || context.core().getResourceAmount(sourceKey()) != 0) {
                helper.fail("Apparatus keepNbt output did not use the exact reagent components");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void installed_station_source_and_work_survive_reload_before_craft(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.AMETHYST_SHARD, 1);
            seedResource(context.core(), sourceKey(), 500);
            if (!installStation(context, "imbuement_chamber")) {
                helper.fail("Could not install the Imbuement Chamber");
                return;
            }
            tick(context.core(), 50);
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
                long restoredWork = reloaded.getStationWork(IMBUEMENT);
                if (restoredWork < 50 || restoredWork > 100) {
                    helper.fail("Reloaded Imbuement work was not preserved: " + restoredWork);
                    return;
                }
                tick(reloaded, Math.toIntExact(100 - restoredWork));
                FixtureContext restored = new FixtureContext(
                        context.level(), reloaded, context.player());
                if (!craft(restored, arsRecipe("imbuement_amethyst"), 1)
                        || itemCount(reloaded, Items.AMETHYST_SHARD) != 0
                        || itemCount(reloaded, arsItem("source_gem")) != 1
                        || reloaded.getResourceAmount(sourceKey()) != 0
                        || reloaded.getStationWork(IMBUEMENT) != 0) {
                    helper.fail("Reloaded Imbuement state did not commit exactly: item="
                            + itemCount(reloaded, Items.AMETHYST_SHARD)
                            + ", output=" + itemCount(reloaded, arsItem("source_gem"))
                            + ", source=" + reloaded.getResourceAmount(sourceKey())
                            + ", work=" + reloaded.getStationWork(IMBUEMENT));
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void output_overflow_rolls_back_source_item_and_work(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.AMETHYST_SHARD, 1);
            seedResource(context.core(), sourceKey(), 500);
            StorageResourceKey output = itemKey(
                    context, new ItemStack(arsItem("source_gem")));
            seedResource(context.core(), output, Long.MAX_VALUE);
            if (!installStation(context, "imbuement_chamber")) {
                helper.fail("Could not install the Imbuement Chamber");
                return;
            }
            tick(context.core(), 100);
            if (craft(context, arsRecipe("imbuement_amethyst"), 1)
                    || itemCount(context.core(), Items.AMETHYST_SHARD) != 1
                    || context.core().getResourceAmount(sourceKey()) != 500
                    || context.core().getResourceAmount(output) != Long.MAX_VALUE
                    || context.core().getStationWork(IMBUEMENT) != 100) {
                helper.fail("Rejected Imbuement overflow partially mutated the transaction");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void source_shortage_rejects_recipe_without_partial_mutation(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            seedItem(context.core(), Items.AMETHYST_SHARD, 1);
            seedResource(context.core(), sourceKey(), 499);
            if (!installStation(context, "imbuement_chamber")) {
                helper.fail("Could not install the Imbuement Chamber");
                return;
            }
            tick(context.core(), 100);
            if (craft(context, arsRecipe("imbuement_amethyst"), 1)
                    || itemCount(context.core(), Items.AMETHYST_SHARD) != 1
                    || itemCount(context.core(), arsItem("source_gem")) != 0
                    || context.core().getResourceAmount(sourceKey()) != 499
                    || context.core().getStationWork(IMBUEMENT) != 100) {
                helper.fail("Source-short Imbuement partially mutated item, Source, or work");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void dynamic_world_and_over_input_families_fail_closed(
            GameTestHelper helper
    ) {
        var overInput = helper.getLevel().getRecipeManager().byKey(TOO_MANY_INPUTS).orElse(null);
        if (overInput == null || CraftingTerminalMenu.supportsRecipeHolder(overInput)) {
            helper.fail("Over-input Apparatus recipe escaped the bounded transaction contract");
            return;
        }
        for (String path : List.of(
                "enchantment",
                "reactive",
                "spell_write",
                "armor_upgrade",
                "prestidigitation",
                "crush",
                "ritual")) {
            if (AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(stationId(path))) {
                helper.fail("Unsafe Ars Nouveau family was registered: " + path);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void retained_imbuement_input_overlap_fails_closed(
            GameTestHelper helper
    ) {
        var overlap = helper.getLevel().getRecipeManager()
                .byKey(RETAINED_INPUT_OVERLAP).orElse(null);
        if (overlap == null) {
            helper.fail("Retained Imbuement overlap fixture was not loaded");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(overlap)) {
            helper.fail("Imbuement accepted one item as consumed input and retained catalyst");
            return;
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

    private static boolean installStation(FixtureContext context, String itemPath) {
        var menu = new CraftingTerminalMenu(
                802, context.player().getInventory(), context.core());
        ItemStack station = new ItemStack(arsItem(itemPath));
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
                803, context.player().getInventory(), context.core());
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
            throw new IllegalStateException("Ars Nouveau Source was not selectable in Energy");
        }
        menu.refreshDisplayItems(context.core());
    }

    private static void seedItem(StorageCoreBlockEntity core, Item item, int amount) {
        seedItem(core, new ItemStack(item, amount), amount);
    }

    private static void seedItem(StorageCoreBlockEntity core, ItemStack stack, int amount) {
        ItemStack copy = stack.copyWithCount(amount);
        if (core.insertItem(copy) != amount) {
            throw new IllegalStateException("Could not seed " + stack + " x" + amount);
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

    private static Item arsItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(arsId(path));
        if (item == Items.AIR) throw new IllegalStateException("Missing Ars Nouveau item " + path);
        return item;
    }

    private static ResourceLocation arsId(String path) {
        return ResourceLocation.fromNamespaceAndPath("ars_nouveau", path);
    }

    private static ResourceLocation arsRecipe(String path) {
        return arsId(path);
    }

    private static ResourceLocation fixtureRecipe(String path) {
        return ResourceLocation.fromNamespaceAndPath(ArsNouveauFixtureMod.MODID, path);
    }

    private static ResourceLocation stationId(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                AutoStorage.MODID, "ars_nouveau_" + path);
    }

    private static StorageResourceKey sourceKey() {
        return StorageResourceKey.of(
                StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND,
                StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND,
                new CompoundTag());
    }

    private record StationSpec(ResourceLocation descriptorId, String itemPath) {
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
