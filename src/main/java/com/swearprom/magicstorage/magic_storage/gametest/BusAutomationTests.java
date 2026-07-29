package com.swearprom.magicstorage.magic_storage.gametest;

import com.swearprom.magicstorage.magic_storage.Action;
import com.swearprom.magicstorage.magic_storage.BusActor;
import com.swearprom.magicstorage.magic_storage.BusConfiguration;
import com.swearprom.magicstorage.magic_storage.BusConfigurationMenu;
import com.swearprom.magicstorage.magic_storage.BusFilterMode;
import com.swearprom.magicstorage.magic_storage.BusFilterPolicy;
import com.swearprom.magicstorage.magic_storage.BusFilterRule;
import com.swearprom.magicstorage.magic_storage.BusKind;
import com.swearprom.magicstorage.magic_storage.BusMode;
import com.swearprom.magicstorage.magic_storage.BusOperationDirection;
import com.swearprom.magicstorage.magic_storage.BusOperationId;
import com.swearprom.magicstorage.magic_storage.ExportBusBlock;
import com.swearprom.magicstorage.magic_storage.ExportBusBlockEntity;
import com.swearprom.magicstorage.magic_storage.ImportBusBlock;
import com.swearprom.magicstorage.magic_storage.ImportBusBlockEntity;
import com.swearprom.magicstorage.magic_storage.ItemKey;
import com.swearprom.magicstorage.magic_storage.MagicStorage;
import com.swearprom.magicstorage.magic_storage.StorageCoreBlockEntity;
import com.swearprom.magicstorage.magic_storage.StorageResourceCapabilities;
import com.swearprom.magicstorage.magic_storage.StorageResourceHandler;
import com.swearprom.magicstorage.magic_storage.StorageResourceKey;
import com.swearprom.magicstorage.magic_storage.StorageResourceKindApi;
import com.swearprom.magicstorage.magic_storage.StorageListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

@GameTestHolder(MagicStorage.MODID)
@PrefixGameTestTemplate(false)
public final class BusAutomationTests {

    private BusAutomationTests() {
    }

    @GameTest(template = "behavioraltests.platform")
    public static void schema_one_bus_config_migrates_to_typed_filter_schema(GameTestHelper helper) {
        CompoundTag root = new CompoundTag();
        CompoundTag schemaOne = currentConfigTag(BusFilterMode.DENY);
        schemaOne.put("filterRules", new ListTag());
        root.put(BusConfiguration.TAG_BUS_CONFIG, schemaOne);
        BusConfiguration migrated = BusConfiguration.load(
                root, BusKind.IMPORT, helper.getLevel().registryAccess());
        if (!migrated.supported()
                || migrated.schema() != 2
                || migrated.mode() != BusMode.DIRECTIONAL
                || migrated.sideMask() != BusConfiguration.ALL_SIDES_MASK
                || migrated.filterMode() != BusFilterMode.DENY
                || !migrated.filterRules().isEmpty()) {
            helper.fail("Schema-one Bus config did not migrate losslessly to typed filters");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void typed_resource_filter_round_trips_and_matches_exact_variant(
            GameTestHelper helper
    ) {
        FluidStack namedWater = new FluidStack(
                net.minecraft.world.level.material.Fluids.WATER, 1);
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Filtered Water"));
        StorageResourceKey namedKey = StorageResourceKey.fluid(
                namedWater, helper.getLevel().registryAccess());
        StorageResourceKey plainKey = StorageResourceKey.fluid(
                new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1),
                helper.getLevel().registryAccess());
        BusConfiguration original = BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                false,
                true,
                BusFilterMode.ALLOW,
                List.of(BusFilterRule.resource(namedKey)),
                Optional.empty(),
                4);
        CompoundTag root = new CompoundTag();
        original.save(root, helper.getLevel().registryAccess());
        BusConfiguration loaded = BusConfiguration.load(
                root, BusKind.EXPORT, helper.getLevel().registryAccess());
        BusFilterPolicy policy = BusFilterPolicy.compile(
                loaded, helper.getLevel().registryAccess());
        if (loaded.schema() != 2
                || loaded.filterRules().size() != 1
                || loaded.filterRules().getFirst().resourceKey().orElse(null) == null
                || !loaded.filterRules().getFirst().resourceKey().orElseThrow().equals(namedKey)
                || !policy.allows(namedKey)
                || policy.allows(plainKey)) {
            helper.fail("Typed resource filter lost schema, exact variant identity, or match policy");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void legacy_import_config_migrates_exact_defaults(GameTestHelper helper) {
        BusConfiguration config = BusConfiguration.load(
                new CompoundTag(), BusKind.IMPORT, helper.getLevel().registryAccess());
        if (!config.supported()
                || config.schema() != 2
                || config.mode() != BusMode.DIRECTIONAL
                || config.sideMask() != BusConfiguration.ALL_SIDES_MASK
                || !config.unsidedAccess()
                || !config.automationEnabled()
                || config.filterMode() != BusFilterMode.DENY
                || !config.filterRules().isEmpty()
                || config.owner().isPresent()
                || config.configRevision() != 0) {
            helper.fail("Legacy Import Bus defaults did not migrate exactly");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void legacy_export_filter_migrates_exact_components(GameTestHelper helper) {
        ItemStack namedDiamond = new ItemStack(Items.DIAMOND);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("exact-filter"));
        CompoundTag root = new CompoundTag();
        root.put("filter", namedDiamond.save(helper.getLevel().registryAccess()));

        BusConfiguration config = BusConfiguration.load(
                root, BusKind.EXPORT, helper.getLevel().registryAccess());
        if (!config.supported()
                || config.mode() != BusMode.DIRECTIONAL
                || config.sideMask() != BusConfiguration.ALL_SIDES_MASK
                || config.unsidedAccess()
                || !config.automationEnabled()
                || config.filterMode() != BusFilterMode.ALLOW
                || config.filterRules().size() != 1
                || config.filterRules().getFirst().type() != BusFilterRule.Type.EXACT_STACK
                || config.filterRules().getFirst().exactKey().isEmpty()
                || !config.filterRules().getFirst().exactKey().orElseThrow().equals(ItemKey.of(namedDiamond))) {
            helper.fail("Legacy Export Bus filter did not migrate as an exact component-sensitive rule");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void current_bus_config_round_trip_preserves_every_field(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        ItemStack exact = new ItemStack(Items.NETHERITE_PICKAXE);
        exact.set(DataComponents.CUSTOM_NAME, Component.literal("round-trip"));
        BusConfiguration source = BusConfiguration.current(
                BusMode.DIRECTIONLESS,
                0b100101,
                false,
                false,
                BusFilterMode.ALLOW,
                List.of(
                        BusFilterRule.exact(exact),
                        BusFilterRule.item(ResourceLocation.withDefaultNamespace("diamond")),
                        BusFilterRule.tag(ResourceLocation.withDefaultNamespace("planks")),
                        BusFilterRule.mod("minecraft")
                ),
                Optional.of(owner),
                27
        );
        CompoundTag root = new CompoundTag();
        source.save(root, helper.getLevel().registryAccess());
        BusConfiguration loaded = BusConfiguration.load(
                root, BusKind.IMPORT, helper.getLevel().registryAccess());
        if (!source.equals(loaded)) {
            helper.fail("Current bus configuration did not round-trip exactly");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void unknown_bus_schema_is_preserved_and_disabled(GameTestHelper helper) {
        CompoundTag raw = new CompoundTag();
        raw.putInt("schema", 99);
        raw.putString("futureMode", "quantum");
        CompoundTag root = new CompoundTag();
        root.put(BusConfiguration.TAG_BUS_CONFIG, raw.copy());

        BusConfiguration config = BusConfiguration.load(
                root, BusKind.IMPORT, helper.getLevel().registryAccess());
        CompoundTag written = new CompoundTag();
        config.save(written, helper.getLevel().registryAccess());
        if (config.supported()
                || config.automationEnabled()
                || !written.getCompound(BusConfiguration.TAG_BUS_CONFIG).equals(raw)) {
            helper.fail("Unknown bus schema was not preserved raw and disabled");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void malformed_current_bus_config_is_preserved_and_disabled(GameTestHelper helper) {
        CompoundTag raw = new CompoundTag();
        raw.putInt("schema", 1);
        raw.putString("mode", "sideways");
        raw.putInt("sideMask", BusConfiguration.ALL_SIDES_MASK);
        raw.putBoolean("unsidedAccess", true);
        raw.putBoolean("automationEnabled", true);
        raw.putString("filterMode", "deny");
        raw.putLong("configRevision", 0);
        CompoundTag root = new CompoundTag();
        root.put(BusConfiguration.TAG_BUS_CONFIG, raw.copy());

        BusConfiguration config = BusConfiguration.load(
                root, BusKind.IMPORT, helper.getLevel().registryAccess());
        CompoundTag written = new CompoundTag();
        config.save(written, helper.getLevel().registryAccess());
        if (config.supported()
                || config.automationEnabled()
                || !written.getCompound(BusConfiguration.TAG_BUS_CONFIG).equals(raw)) {
            helper.fail("Malformed current bus config did not fail closed with raw preservation");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void player_placement_captures_bus_owner_without_changing_defaults(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos importPos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos exportPos = helper.absolutePos(new BlockPos(3, 3, 1));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(exportPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        MagicStorage.IMPORT_BUS.get().setPlacedBy(
                level, importPos, level.getBlockState(importPos), player, new ItemStack(MagicStorage.IMPORT_BUS_ITEM.get()));
        MagicStorage.EXPORT_BUS.get().setPlacedBy(
                level, exportPos, level.getBlockState(exportPos), player, new ItemStack(MagicStorage.EXPORT_BUS_ITEM.get()));

        if (!(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity importBus)
                || !(level.getBlockEntity(exportPos) instanceof ExportBusBlockEntity exportBus)
                || !importBus.getBusConfiguration().owner().equals(Optional.of(player.getUUID()))
                || !exportBus.getBusConfiguration().owner().equals(Optional.of(player.getUUID()))
                || importBus.getBusConfiguration().filterMode() != BusFilterMode.DENY
                || exportBus.getBusConfiguration().filterMode() != BusFilterMode.ALLOW) {
            helper.fail("Bus placement did not capture owner while preserving kind defaults");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void structured_bus_actor_preserves_complete_operation_identity(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID network = UUID.randomUUID();
        BlockPos busPos = helper.absolutePos(new BlockPos(1, 3, 1));
        BusOperationId operationId = new BusOperationId(1234, 17);
        BusActor actor = BusActor.owned(
                owner,
                helper.getLevel().dimension(),
                busPos,
                network,
                BusOperationDirection.EXPORT,
                operationId
        );
        BusActor legacy = BusActor.legacyUnclaimed(
                helper.getLevel().dimension(),
                busPos,
                network,
                BusOperationDirection.IMPORT,
                new BusOperationId(1234, 18)
        );
        if (!actor.owner().equals(Optional.of(owner))
                || !actor.dimension().equals(helper.getLevel().dimension())
                || !actor.busPos().equals(busPos)
                || !actor.networkId().equals(network)
                || actor.direction() != BusOperationDirection.EXPORT
                || !actor.operationId().equals(operationId)
                || !legacy.legacyUnclaimed()
                || actor.legacyUnclaimed()
                || actor.name().equals(legacy.name())) {
            helper.fail("Structured bus actor did not preserve complete operation identity");
            return;
        }

        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos importPos = corePos.east();
        BlockPos exportPos = corePos.west();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(exportPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity importer)
                    || !(level.getBlockEntity(exportPos) instanceof ExportBusBlockEntity exporter)) {
                helper.fail("Structured escrow actor fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            UUID importOwner = UUID.randomUUID();
            UUID exportOwner = UUID.randomUUID();
            seedPendingResource(importer, StorageResourceKey.neoforgeEnergy(), 17, level.registryAccess());
            seedPendingResource(exporter, StorageResourceKey.neoforgeEnergy(), 23, level.registryAccess());
            importer.assignOwnerOnPlacement(importOwner);
            exporter.assignOwnerOnPlacement(exportOwner);
            List<com.swearprom.magicstorage.magic_storage.Actor> actors = new java.util.ArrayList<>();
            StorageListener listener = new StorageListener() {
                @Override
                public void onChanged(
                        ItemKey key,
                        long delta,
                        long newAmount,
                        com.swearprom.magicstorage.magic_storage.Actor actor
                ) {
                }

                @Override
                public void onResourceChanged(
                        StorageResourceKey key,
                        long delta,
                        long newAmount,
                        com.swearprom.magicstorage.magic_storage.Actor actor
                ) {
                    if (key.equals(StorageResourceKey.neoforgeEnergy())) actors.add(actor);
                }
            };
            core.addListener(listener);
            importer.tick();
            exporter.tick();
            core.removeListener(listener);

            UUID liveNetwork = core.getNetworkId();
            if (actors.size() != 2
                    || !(actors.get(0) instanceof BusActor importActor)
                    || !(actors.get(1) instanceof BusActor exportActor)
                    || !importActor.owner().equals(Optional.of(importOwner))
                    || !importActor.dimension().equals(level.dimension())
                    || !importActor.busPos().equals(importPos)
                    || !importActor.networkId().equals(liveNetwork)
                    || importActor.direction() != BusOperationDirection.IMPORT
                    || !exportActor.owner().equals(Optional.of(exportOwner))
                    || !exportActor.dimension().equals(level.dimension())
                    || !exportActor.busPos().equals(exportPos)
                    || !exportActor.networkId().equals(liveNetwork)
                    || exportActor.direction() != BusOperationDirection.EXPORT
                    || core.getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 40
                    || importer.getPendingResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || exporter.getPendingResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0) {
                helper.fail("Escrow drain did not attribute both Core mutations to structured Bus actors: "
                        + actors);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void config_revision_overflow_is_rejected_without_wrap(GameTestHelper helper) {
        BusConfiguration config = BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                true,
                true,
                BusFilterMode.DENY,
                List.of(),
                Optional.empty(),
                Long.MAX_VALUE
        );
        try {
            config.nextRevision();
            helper.fail("Bus config revision wrapped past Long.MAX_VALUE");
        } catch (IllegalStateException expected) {
            helper.succeed();
        }
    }

    @GameTest(template = "behavioraltests.platform")
    public static void allow_and_deny_empty_filters_have_opposite_semantics(GameTestHelper helper) {
        ItemKey stone = ItemKey.of(new ItemStack(Items.STONE));
        BusFilterPolicy allow = BusFilterPolicy.compile(
                filterConfig(BusFilterMode.ALLOW, List.of()), helper.getLevel().registryAccess());
        BusFilterPolicy deny = BusFilterPolicy.compile(
                filterConfig(BusFilterMode.DENY, List.of()), helper.getLevel().registryAccess());
        if (allow.allows(stone) || !deny.allows(stone)) {
            helper.fail("Empty Allow must match nothing and empty Deny must match everything");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void unsupported_filter_policy_never_allows_candidates(GameTestHelper helper) {
        CompoundTag raw = new CompoundTag();
        raw.putInt("schema", 99);
        CompoundTag root = new CompoundTag();
        root.put(BusConfiguration.TAG_BUS_CONFIG, raw);
        BusConfiguration config = BusConfiguration.load(
                root, BusKind.IMPORT, helper.getLevel().registryAccess());
        BusFilterPolicy policy = BusFilterPolicy.compile(config, helper.getLevel().registryAccess());
        ItemKey stone = ItemKey.of(new ItemStack(Items.STONE));
        if (policy.allows(stone) || !policy.orderedCandidates(List.of(stone)).isEmpty()) {
            helper.fail("Unsupported bus filter policy must fail closed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void every_filter_rule_type_uses_its_exact_identity_contract(GameTestHelper helper) {
        ItemStack namedDiamond = new ItemStack(Items.DIAMOND);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
        ItemKey namedKey = ItemKey.of(namedDiamond);
        ItemKey plainDiamond = ItemKey.of(new ItemStack(Items.DIAMOND));
        ItemKey oakPlanks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey dirt = ItemKey.of(new ItemStack(Items.DIRT));
        var registries = helper.getLevel().registryAccess();

        BusFilterPolicy exact = BusFilterPolicy.compile(filterConfig(
                BusFilterMode.ALLOW, List.of(BusFilterRule.exact(namedDiamond))), registries);
        BusFilterPolicy item = BusFilterPolicy.compile(filterConfig(
                BusFilterMode.ALLOW,
                List.of(BusFilterRule.item(ResourceLocation.withDefaultNamespace("diamond")))), registries);
        BusFilterPolicy tag = BusFilterPolicy.compile(filterConfig(
                BusFilterMode.ALLOW,
                List.of(BusFilterRule.tag(ResourceLocation.withDefaultNamespace("planks")))), registries);
        BusFilterPolicy mod = BusFilterPolicy.compile(filterConfig(
                BusFilterMode.ALLOW, List.of(BusFilterRule.mod("minecraft"))), registries);

        if (!exact.allows(namedKey) || exact.allows(plainDiamond)
                || !item.allows(namedKey) || !item.allows(plainDiamond) || item.allows(dirt)
                || !tag.allows(oakPlanks) || tag.allows(dirt)
                || !mod.allows(dirt)) {
            helper.fail("Exact/item/tag/mod filter semantics diverged");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void duplicate_and_unavailable_rules_stay_visible_but_never_match(GameTestHelper helper) {
        CompoundTag configTag = currentConfigTag(BusFilterMode.ALLOW);
        ListTag rules = new ListTag();
        rules.add(itemRule("minecraft:stone"));
        rules.add(itemRule("minecraft:stone"));
        rules.add(itemRule("missing_addon:missing_item"));
        configTag.put("filterRules", rules);
        CompoundTag root = new CompoundTag();
        root.put(BusConfiguration.TAG_BUS_CONFIG, configTag);

        BusConfiguration config = BusConfiguration.load(
                root, BusKind.EXPORT, helper.getLevel().registryAccess());
        BusFilterPolicy policy = BusFilterPolicy.compile(config, helper.getLevel().registryAccess());
        if (!config.supported()
                || policy.ruleSlots().size() != 3
                || policy.ruleSlots().get(0).state() != BusFilterPolicy.RuleState.ACTIVE
                || policy.ruleSlots().get(1).state() != BusFilterPolicy.RuleState.DUPLICATE
                || policy.ruleSlots().get(2).state() != BusFilterPolicy.RuleState.UNAVAILABLE
                || !policy.allows(ItemKey.of(new ItemStack(Items.STONE)))
                || policy.allows(ItemKey.of(new ItemStack(Items.DIRT)))) {
            helper.fail("Duplicate/unavailable filter rules were hidden or became matchable");
            return;
        }

        CompoundTag denyConfigTag = currentConfigTag(BusFilterMode.DENY);
        ListTag denyRules = new ListTag();
        denyRules.add(itemRule("minecraft:stone"));
        denyRules.add(itemRule("missing_addon:missing_item"));
        denyConfigTag.put("filterRules", denyRules);
        CompoundTag denyRoot = new CompoundTag();
        denyRoot.put(BusConfiguration.TAG_BUS_CONFIG, denyConfigTag);
        BusFilterPolicy deny = BusFilterPolicy.compile(
                BusConfiguration.load(denyRoot, BusKind.EXPORT, helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        ItemKey dirt = ItemKey.of(new ItemStack(Items.DIRT));
        StorageResourceKey energy = StorageResourceKey.neoforgeEnergy();
        if (deny.ruleSlots().stream().noneMatch(
                slot -> slot.state() == BusFilterPolicy.RuleState.UNAVAILABLE)
                || deny.allows(dirt)
                || deny.allows(energy)
                || !deny.orderedCandidates(List.of(dirt)).isEmpty()
                || !deny.orderedResourceCandidates(List.of(energy)).isEmpty()) {
            helper.fail("Deny policy with an unavailable rule did not fail closed for item and typed candidates");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void allow_candidates_follow_rule_order_then_canonical_item_identity(GameTestHelper helper) {
        ItemStack stoneB = new ItemStack(Items.STONE);
        stoneB.set(DataComponents.CUSTOM_NAME, Component.literal("B"));
        ItemStack stoneA = new ItemStack(Items.STONE);
        stoneA.set(DataComponents.CUSTOM_NAME, Component.literal("A"));
        ItemKey dirt = ItemKey.of(new ItemStack(Items.DIRT));
        ItemKey keyA = ItemKey.of(stoneA);
        ItemKey keyB = ItemKey.of(stoneB);
        BusFilterPolicy policy = BusFilterPolicy.compile(filterConfig(
                BusFilterMode.ALLOW,
                List.of(
                        BusFilterRule.item(ResourceLocation.withDefaultNamespace("dirt")),
                        BusFilterRule.item(ResourceLocation.withDefaultNamespace("stone"))
                )), helper.getLevel().registryAccess());

        List<ItemKey> ordered = policy.orderedCandidates(List.of(keyB, keyA, dirt));
        List<ItemKey> reversed = policy.orderedCandidates(List.of(dirt, keyA, keyB));
        if (!ordered.equals(List.of(dirt, keyA, keyB)) || !reversed.equals(ordered)) {
            helper.fail("Allow candidates ignored rule order or canonical component order: " + ordered);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void deny_candidates_use_global_deterministic_order(GameTestHelper helper) {
        ItemStack stoneB = new ItemStack(Items.STONE);
        stoneB.set(DataComponents.CUSTOM_NAME, Component.literal("B"));
        ItemStack stoneA = new ItemStack(Items.STONE);
        stoneA.set(DataComponents.CUSTOM_NAME, Component.literal("A"));
        ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));
        ItemKey dirt = ItemKey.of(new ItemStack(Items.DIRT));
        ItemKey keyA = ItemKey.of(stoneA);
        ItemKey keyB = ItemKey.of(stoneB);
        BusFilterPolicy policy = BusFilterPolicy.compile(filterConfig(
                BusFilterMode.DENY,
                List.of(BusFilterRule.item(ResourceLocation.withDefaultNamespace("dirt")))),
                helper.getLevel().registryAccess());

        List<ItemKey> ordered = policy.orderedCandidates(List.of(keyB, dirt, keyA, diamond));
        List<ItemKey> reversed = policy.orderedCandidates(List.of(diamond, keyA, dirt, keyB));
        if (!ordered.equals(List.of(diamond, keyA, keyB)) || !reversed.equals(ordered)) {
            helper.fail("Deny candidates were not globally deterministic: " + ordered);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void export_item_candidate_cache_tracks_core_and_config_revisions_for_active_and_passive(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos targetPos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(targetPos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), Block.UPDATE_ALL);
        TestItemHandler target = new TestItemHandler(1);
        installTestEndpoint(level, targetPos, target, new TestEnergyStorage(0, 0));

        helper.runAfterDelay(2, () -> {
            CountingStorageCoreBlockEntity core = replaceWithCountingCore(level, corePos);
            if (!(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
                helper.fail("Export candidate cache Bus is missing");
                return;
            }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 2));
            BusConfiguration passive = BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    1);
            bus.setBusConfiguration(passive);
            IItemHandler passiveItems = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, busPos, Direction.UP);
            if (passiveItems == null) {
                helper.fail("Passive Export item capability is missing");
                return;
            }

            long stableRevision = core.getCraftableRevision();
            ItemStack first = passiveItems.getStackInSlot(0);
            ItemStack repeated = passiveItems.getStackInSlot(0);
            if (!first.is(Items.STONE) || !repeated.is(Items.STONE)
                    || core.getCraftableRevision() != stableRevision
                    || core.displayStackBuilds() != 1) {
                helper.fail("Unchanged passive Export automation rebuilt the full display list: "
                        + core.displayStackBuilds());
                return;
            }

            MagicStorage.invalidateDatapackCaches();
            if (!passiveItems.getStackInSlot(0).is(Items.STONE)
                    || core.displayStackBuilds() != 2) {
                helper.fail("Datapack reload did not invalidate the Export candidate cache");
                return;
            }

            core.extractItem(ItemKey.of(new ItemStack(Items.STONE)), 64);
            core.insertItem(new ItemStack(Items.DIRT, 2));
            if (core.getCraftableRevision() == stableRevision
                    || !passiveItems.getStackInSlot(0).is(Items.DIRT)
                    || core.displayStackBuilds() != 3) {
                helper.fail("Passive Export reused a stale candidate after Core revision change");
                return;
            }

            bus.setBusConfiguration(BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.item(ResourceLocation.withDefaultNamespace("stone"))),
                    Optional.empty(),
                    2));
            if (!passiveItems.getStackInSlot(0).isEmpty()
                    || core.displayStackBuilds() != 4) {
                helper.fail("Passive Export reused a stale candidate after filter revision change");
                return;
            }
            bus.setBusConfiguration(BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.item(ResourceLocation.withDefaultNamespace("dirt"))),
                    Optional.empty(),
                    3));
            if (!passiveItems.getStackInSlot(0).is(Items.DIRT)
                    || core.displayStackBuilds() != 5) {
                helper.fail("Passive Export did not rebuild the candidate after filter revision change");
                return;
            }

            target.setStack(0, new ItemStack(Items.BARRIER, 64));
            bus.setBusConfiguration(BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    4));
            stableRevision = core.getCraftableRevision();
            bus.tick();
            runNextAutomationCycle(bus);
            if (core.getCraftableRevision() != stableRevision
                    || core.displayStackBuilds() != 6) {
                helper.fail("Unchanged active Export automation rebuilt the full display list: "
                        + core.displayStackBuilds());
                return;
            }

            core.extractItem(ItemKey.of(new ItemStack(Items.DIRT)), 64);
            core.insertItem(new ItemStack(Items.STONE, 2));
            target.setStack(0, ItemStack.EMPTY);
            runNextAutomationCycle(bus);
            if (!target.getStackInSlot(0).is(Items.STONE)
                    || core.displayStackBuilds() != 7) {
                helper.fail("Active Export reused a stale candidate after Core revision change");
                return;
            }

            core.insertItem(new ItemStack(Items.STONE, 2));
            core.insertItem(new ItemStack(Items.DIRT, 2));
            target.setStack(0, new ItemStack(Items.BARRIER, 64));
            bus.setBusConfiguration(BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.item(ResourceLocation.withDefaultNamespace("stone"))),
                    Optional.empty(),
                    5));
            bus.tick();
            long configStableRevision = core.getCraftableRevision();
            target.setStack(0, ItemStack.EMPTY);
            bus.setBusConfiguration(BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.item(ResourceLocation.withDefaultNamespace("dirt"))),
                    Optional.empty(),
                    6));
            bus.tick();
            if (core.getCraftableRevision() == configStableRevision
                    || !target.getStackInSlot(0).is(Items.DIRT)
                    || core.displayStackBuilds() != 9) {
                helper.fail("Active Export reused a stale candidate after filter revision change");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void directional_import_keeps_source_order_and_attempts_typed_transfer(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos sourcePos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(sourcePos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), Block.UPDATE_ALL);
        TestItemHandler source = new TestItemHandler(2);
        source.setStack(0, new ItemStack(Items.DIRT, 4));
        source.setStack(1, new ItemStack(Items.STONE, 4));
        TestEnergyStorage energy = new TestEnergyStorage(1_000, 400);
        installTestEndpoint(level, sourcePos, source, energy);
        BusConfiguration config = BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                true,
                true,
                BusFilterMode.ALLOW,
                List.of(
                        BusFilterRule.item(ResourceLocation.withDefaultNamespace("stone")),
                        BusFilterRule.resource(StorageResourceKey.neoforgeEnergy())),
                Optional.empty(),
                1);
        if (!applyConfiguration(level, busPos, MagicStorage.IMPORT_BUS_BE.get(),
                MagicStorage.IMPORT_BUS_ITEM.get(), config)) {
            helper.fail("Could not apply Import filter configuration");
            return;
        }

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)) {
                helper.fail("Directional Import fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            bus.setBusConfiguration(bus.getBusConfiguration());
            bus.tick();
            if (core.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 4
                    || source.getStackInSlot(0).getCount() != 4
                    || !source.getStackInSlot(1).isEmpty()
                    || core.getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 400
                    || energy.getEnergyStored() != 0) {
                helper.fail("Directional Import did not independently transfer one item stack and typed resource: "
                        + "coreDirt=" + core.getItemCount(ItemKey.of(new ItemStack(Items.DIRT)))
                        + ", coreStone=" + core.getItemCount(ItemKey.of(new ItemStack(Items.STONE)))
                        + ", sourceDirt=" + source.getStackInSlot(0)
                        + ", sourceStone=" + source.getStackInSlot(1)
                        + ", coreEnergy=" + core.getResourceAmount(StorageResourceKey.neoforgeEnergy())
                        + ", sourceEnergy=" + energy.getEnergyStored());
                return;
            }

            source.setStack(1, new ItemStack(Items.STONE, 3));
            energy.receiveEnergy(50, false);
            makeCoreStorageUnavailable(level, corePos);
            boolean activeCrashed = false;
            try {
                for (int tick = 0; tick <= 10; tick++) bus.tick();
            } catch (IllegalStateException exception) {
                if (!isUnavailableNetworkIdFailure(exception)) throw exception;
                activeCrashed = true;
            }
            seedPendingResource(
                    bus, StorageResourceKey.neoforgeEnergy(), 29, level.registryAccess());
            boolean recoveryCrashed = false;
            try {
                bus.tick();
            } catch (IllegalStateException exception) {
                if (!isUnavailableNetworkIdFailure(exception)) throw exception;
                recoveryCrashed = true;
            }
            bus.setRemoved();
            if (activeCrashed || recoveryCrashed
                    || source.getStackInSlot(1).getCount() != 3
                    || energy.getEnergyStored() != 50
                    || bus.getPendingResourceAmount(StorageResourceKey.neoforgeEnergy()) != 29) {
                helper.fail("Unavailable Core Import tick reached getNetworkId or mutated resources: activeCrash="
                        + activeCrashed + ", recoveryCrash=" + recoveryCrashed);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void passive_import_filter_matches_simulation_and_preserves_caller_stack(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Passive Import Core is missing");
                return;
            }
            core.rebuildNetwork(level);
            BusConfiguration config = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    true,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.item(ResourceLocation.withDefaultNamespace("stone"))),
                    Optional.empty(),
                    1);
            if (!applyConfiguration(level, busPos, MagicStorage.IMPORT_BUS_BE.get(),
                    MagicStorage.IMPORT_BUS_ITEM.get(), config)) {
                helper.fail("Could not apply passive Import filter configuration");
                return;
            }
            IItemHandler handler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, busPos, Direction.UP);
            if (handler == null) {
                helper.fail("Passive Import capability is missing");
                return;
            }
            ItemStack denied = new ItemStack(Items.DIRT, 5);
            ItemStack deniedRemainder = handler.insertItem(0, denied, false);
            ItemStack allowed = new ItemStack(Items.STONE, 5);
            ItemStack simulatedRemainder = handler.insertItem(0, allowed, true);
            if (denied.getCount() != 5 || allowed.getCount() != 5
                    || deniedRemainder.getCount() != 5 || !simulatedRemainder.isEmpty()
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0) {
                helper.fail("Passive Import simulation/filter mutated input or Core state");
                return;
            }
            ItemStack committedRemainder = handler.insertItem(0, allowed, false);
            if (!committedRemainder.isEmpty() || allowed.getCount() != 5
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 5) {
                helper.fail("Passive Import execution diverged from filtered simulation");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void directional_export_uses_rule_order_and_attempts_typed_transfer(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos targetPos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(targetPos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), Block.UPDATE_ALL);
        TestItemHandler target = new TestItemHandler(1);
        TestEnergyStorage energy = new TestEnergyStorage(1_000, 0);
        installTestEndpoint(level, targetPos, target, energy);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
                helper.fail("Directional Export fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 2));
            core.insertItem(new ItemStack(Items.DIRT, 2));
            core.insertResource(StorageResourceKey.neoforgeEnergy(), 400, Action.EXECUTE);
            BusConfiguration config = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(
                            BusFilterRule.item(ResourceLocation.withDefaultNamespace("dirt")),
                            BusFilterRule.item(ResourceLocation.withDefaultNamespace("stone")),
                            BusFilterRule.resource(StorageResourceKey.neoforgeEnergy())),
                    Optional.empty(),
                    1);
            if (!applyConfiguration(level, busPos, MagicStorage.EXPORT_BUS_BE.get(),
                    MagicStorage.EXPORT_BUS_ITEM.get(), config)) {
                helper.fail("Could not apply ordered Export filter configuration");
                return;
            }
            bus.tick();
            if (!target.getStackInSlot(0).is(Items.DIRT) || target.getStackInSlot(0).getCount() != 2
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 2
                    || core.getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || energy.getEnergyStored() != 400) {
                helper.fail("Directional Export did not independently transfer one ordered item stack and typed resource");
                return;
            }

            makeCoreStorageUnavailable(level, corePos);
            boolean activeCrashed = false;
            try {
                for (int tick = 0; tick <= 10; tick++) bus.tick();
            } catch (IllegalStateException exception) {
                if (!isUnavailableNetworkIdFailure(exception)) throw exception;
                activeCrashed = true;
            }
            seedPendingResource(
                    bus, StorageResourceKey.neoforgeEnergy(), 31, level.registryAccess());
            boolean recoveryCrashed = false;
            try {
                bus.tick();
            } catch (IllegalStateException exception) {
                if (!isUnavailableNetworkIdFailure(exception)) throw exception;
                recoveryCrashed = true;
            }
            bus.setRemoved();
            if (activeCrashed || recoveryCrashed
                    || target.getStackInSlot(0).getCount() != 2
                    || energy.getEnergyStored() != 400
                    || bus.getPendingResourceAmount(StorageResourceKey.neoforgeEnergy()) != 31) {
                helper.fail("Unavailable Core Export tick reached getNetworkId or mutated resources: activeCrash="
                        + activeCrashed + ", recoveryCrash=" + recoveryCrashed);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void directional_import_rejects_item_endpoint_replaced_during_simulation(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos sourcePos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(sourcePos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), Block.UPDATE_ALL);
        TestItemHandler original = new TestItemHandler(1);
        original.setStack(0, new ItemStack(Items.STONE, 4));
        TestItemHandler replacement = new TestItemHandler(1);
        replacement.setStack(0, new ItemStack(Items.DIRT, 7));
        original.setOnSimulatedExtract(() -> installTestEndpoint(
                level, sourcePos, replacement, null));
        installTestEndpoint(level, sourcePos, original, null);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)) {
                helper.fail("Import stale-item simulation fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            bus.tick();
            if (original.getStackInSlot(0).getCount() != 4
                    || replacement.getStackInSlot(0).getCount() != 7
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 0) {
                helper.fail("Import committed against an item endpoint replaced during simulation");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void directional_export_rejects_item_endpoint_replaced_during_simulation(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos targetPos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(targetPos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), Block.UPDATE_ALL);
        TestItemHandler original = new TestItemHandler(1);
        TestItemHandler replacement = new TestItemHandler(1);
        boolean[] callbackRan = {false};
        original.setOnSimulatedInsert(() -> {
            callbackRan[0] = true;
            installTestEndpoint(level, targetPos, replacement, null);
        });
        installTestEndpoint(level, targetPos, original, null);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
                helper.fail("Export stale-item simulation fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 4));
            bus.setBusConfiguration(filterConfig(
                    BusFilterMode.ALLOW, List.of(BusFilterRule.exact(new ItemStack(Items.STONE)))));
            bus.tick();
            if (!callbackRan[0]
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 4
                    || !original.getStackInSlot(0).isEmpty()
                    || !replacement.getStackInSlot(0).isEmpty()) {
                helper.fail("Export committed against an item endpoint replaced during simulation: core="
                        + core.getItemCount(ItemKey.of(new ItemStack(Items.STONE)))
                        + ", original=" + original.getStackInSlot(0)
                        + ", replacement=" + replacement.getStackInSlot(0)
                        + ", callback=" + callbackRan[0]
                        + ", current=" + level.getCapability(
                        Capabilities.ItemHandler.BLOCK, targetPos, Direction.WEST));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void directional_import_preserves_item_extracted_before_endpoint_replacement(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos sourcePos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(sourcePos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), Block.UPDATE_ALL);
        TestItemHandler original = new TestItemHandler(1);
        original.setStack(0, new ItemStack(Items.STONE, 4));
        TestItemHandler replacement = new TestItemHandler(1);
        original.setOnExecutedExtract(() -> installTestEndpoint(
                level, sourcePos, replacement, null));
        installTestEndpoint(level, sourcePos, original, null);

        helper.runAfterDelay(2, () -> {
            boolean previousDrops = level.getGameRules().getBoolean(
                    net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                    .set(false, level.getServer());
            try {
                if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                        || !(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)) {
                    helper.fail("Import stale-item execute fixture is incomplete");
                    return;
                }
                core.rebuildNetwork(level);
                bus.setBusConfiguration(bus.getBusConfiguration());
                bus.tick();
                long dropped = level.getEntitiesOfClass(
                                net.minecraft.world.entity.item.ItemEntity.class,
                                new net.minecraft.world.phys.AABB(busPos).inflate(2.0)).stream()
                        .filter(entity -> entity.getItem().is(Items.STONE))
                        .mapToLong(entity -> entity.getItem().getCount())
                        .sum();
                if (!original.getStackInSlot(0).isEmpty()
                        || !replacement.getStackInSlot(0).isEmpty()
                        || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0
                        || dropped != 4) {
                    helper.fail("Import lost or duplicated item extracted before endpoint replacement: drop="
                            + dropped);
                    return;
                }
                helper.succeed();
            } finally {
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                        .set(previousDrops, level.getServer());
            }
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void directional_export_reclaims_item_inserted_before_endpoint_replacement(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos targetPos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(targetPos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), Block.UPDATE_ALL);
        TestItemHandler original = new TestItemHandler(1);
        TestItemHandler replacement = new TestItemHandler(1);
        boolean[] callbackRan = {false};
        original.setOnExecutedInsert(() -> {
            callbackRan[0] = true;
            installTestEndpoint(level, targetPos, replacement, null);
        });
        installTestEndpoint(level, targetPos, original, null);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
                helper.fail("Export stale-item execute fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 4));
            bus.setBusConfiguration(filterConfig(
                    BusFilterMode.ALLOW, List.of(BusFilterRule.exact(new ItemStack(Items.STONE)))));
            bus.tick();
            if (!callbackRan[0]
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 4
                    || !original.getStackInSlot(0).isEmpty()
                    || !replacement.getStackInSlot(0).isEmpty()) {
                helper.fail("Export did not reclaim items accepted before endpoint replacement: core="
                        + core.getItemCount(ItemKey.of(new ItemStack(Items.STONE)))
                        + ", original=" + original.getStackInSlot(0)
                        + ", replacement=" + replacement.getStackInSlot(0)
                        + ", callback=" + callbackRan[0]
                        + ", current=" + level.getCapability(
                        Capabilities.ItemHandler.BLOCK, targetPos, Direction.WEST));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void directional_export_recovers_item_when_replaced_endpoint_refuses_reclaim(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos targetPos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(targetPos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), Block.UPDATE_ALL);
        TestItemHandler original = new TestItemHandler(1);
        TestItemHandler replacement = new TestItemHandler(1);
        boolean[] callbackRan = {false};
        original.setOnExecutedInsert(() -> {
            callbackRan[0] = true;
            original.setExtractionEnabled(false);
            installTestEndpoint(level, targetPos, replacement, null);
        });
        installTestEndpoint(level, targetPos, original, null);

        helper.runAfterDelay(2, () -> {
            boolean previousDrops = level.getGameRules().getBoolean(
                    net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                    .set(false, level.getServer());
            try {
                if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                        || !(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
                    helper.fail("Export unreclaimable stale-item fixture is incomplete");
                    return;
                }
                core.rebuildNetwork(level);
                core.insertItem(new ItemStack(Items.STONE, 4));
                bus.setBusConfiguration(filterConfig(
                        BusFilterMode.ALLOW,
                        List.of(BusFilterRule.exact(new ItemStack(Items.STONE)))));
                bus.tick();
                long dropped = level.getEntitiesOfClass(
                                net.minecraft.world.entity.item.ItemEntity.class,
                                new net.minecraft.world.phys.AABB(busPos).inflate(2.0)).stream()
                        .filter(entity -> entity.getItem().is(Items.STONE))
                        .mapToLong(entity -> entity.getItem().getCount())
                        .sum();
                if (!callbackRan[0]
                        || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0
                        || original.getStackInSlot(0).getCount() != 4
                        || !replacement.getStackInSlot(0).isEmpty()
                        || dropped != 4) {
                    helper.fail("Export did not recover an unreclaimable item from a replaced endpoint: core="
                            + core.getItemCount(ItemKey.of(new ItemStack(Items.STONE)))
                            + ", original=" + original.getStackInSlot(0)
                            + ", replacement=" + replacement.getStackInSlot(0)
                            + ", drop=" + dropped
                            + ", callback=" + callbackRan[0]);
                    return;
                }
                helper.succeed();
            } finally {
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                        .set(previousDrops, level.getServer());
            }
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void disabled_automation_blocks_active_and_passive_import(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos sourcePos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(sourcePos, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)
                    || !(level.getBlockEntity(sourcePos) instanceof BarrelBlockEntity source)) {
                helper.fail("Disabled automation fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            BusConfiguration config = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    true,
                    false,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    1);
            if (!applyConfiguration(level, busPos, MagicStorage.IMPORT_BUS_BE.get(),
                    MagicStorage.IMPORT_BUS_ITEM.get(), config)) {
                helper.fail("Could not disable Import automation");
                return;
            }
            source.setItem(0, new ItemStack(Items.STONE, 3));
            source.setChanged();
            bus.tick();
            IItemHandler handler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, busPos, Direction.UP);
            ItemStack passive = new ItemStack(Items.DIRT, 3);
            ItemStack passiveRemainder = handler == null
                    ? passive.copy()
                    : handler.insertItem(0, passive, false);
            if (source.getItem(0).getCount() != 3 || passiveRemainder.getCount() != 3
                    || core.getTypeCount() != 0) {
                helper.fail("Disabled automation still mutated active or passive Import state");
                return;
            }
            level.setBlock(sourcePos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(), Block.UPDATE_ALL);
            TestItemHandler changingSource = new TestItemHandler(1);
            changingSource.setStack(0, new ItemStack(Items.DIRT, 4));
            changingSource.setSimulatedExtract(new ItemStack(Items.STONE, 4));
            installTestEndpoint(level, sourcePos, changingSource, null);
            BusConfiguration enabled = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    true,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.item(ResourceLocation.withDefaultNamespace("stone"))),
                    Optional.empty(),
                    2);
            if (!applyConfiguration(level, busPos, MagicStorage.IMPORT_BUS_BE.get(),
                    MagicStorage.IMPORT_BUS_ITEM.get(), enabled)) {
                helper.fail("Could not enable identity-changing Import fixture");
                return;
            }
            bus.tick();
            if (!changingSource.getStackInSlot(0).is(Items.DIRT)
                    || changingSource.getStackInSlot(0).getCount() != 4
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 0) {
                helper.fail("Import inserted an execute result whose identity differed from simulation");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void bus_capabilities_follow_mode_side_unsided_and_automation_matrix(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos importPos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos exportPos = helper.absolutePos(new BlockPos(3, 3, 1));
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(exportPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        int upOnly = 1 << Direction.UP.ordinal();

        BusConfiguration importConfig = BusConfiguration.current(
                BusMode.DIRECTIONLESS,
                upOnly,
                false,
                true,
                BusFilterMode.DENY,
                List.of(),
                Optional.empty(),
                1);
        BusConfiguration directionalExport = BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                true,
                true,
                BusFilterMode.DENY,
                List.of(),
                Optional.empty(),
                1);
        if (!applyConfiguration(level, importPos, MagicStorage.IMPORT_BUS_BE.get(),
                MagicStorage.IMPORT_BUS_ITEM.get(), importConfig)
                || !applyConfiguration(level, exportPos, MagicStorage.EXPORT_BUS_BE.get(),
                MagicStorage.EXPORT_BUS_ITEM.get(), directionalExport)) {
            helper.fail("Could not apply Bus capability matrix configuration");
            return;
        }

        if (level.getCapability(StorageResourceCapabilities.BLOCK, importPos, Direction.UP) == null
                || level.getCapability(StorageResourceCapabilities.BLOCK, importPos, Direction.DOWN) != null
                || level.getCapability(StorageResourceCapabilities.BLOCK, importPos, null) != null
                || level.getCapability(StorageResourceCapabilities.BLOCK, exportPos, Direction.UP) != null
                || level.getCapability(StorageResourceCapabilities.BLOCK, exportPos, null) != null) {
            helper.fail("Directional/Import side capability matrix diverged");
            return;
        }

        BusConfiguration directionlessExport = BusConfiguration.current(
                BusMode.DIRECTIONLESS,
                upOnly,
                true,
                true,
                BusFilterMode.DENY,
                List.of(),
                Optional.empty(),
                2);
        BusConfiguration disabledImport = BusConfiguration.current(
                BusMode.DIRECTIONLESS,
                upOnly,
                true,
                false,
                BusFilterMode.DENY,
                List.of(),
                Optional.empty(),
                2);
        if (!applyConfiguration(level, exportPos, MagicStorage.EXPORT_BUS_BE.get(),
                MagicStorage.EXPORT_BUS_ITEM.get(), directionlessExport)
                || !applyConfiguration(level, importPos, MagicStorage.IMPORT_BUS_BE.get(),
                MagicStorage.IMPORT_BUS_ITEM.get(), disabledImport)) {
            helper.fail("Could not update Bus capability matrix configuration");
            return;
        }
        if (level.getCapability(StorageResourceCapabilities.BLOCK, exportPos, Direction.UP) == null
                || level.getCapability(StorageResourceCapabilities.BLOCK, exportPos, Direction.DOWN) != null
                || level.getCapability(StorageResourceCapabilities.BLOCK, exportPos, null) == null
                || level.getCapability(StorageResourceCapabilities.BLOCK, importPos, Direction.UP) != null
                || level.getCapability(StorageResourceCapabilities.BLOCK, importPos, null) != null) {
            helper.fail("Directionless/disabled Bus capability matrix diverged");
            return;
        }
        IItemHandler cachedExport = level.getCapability(
                Capabilities.ItemHandler.BLOCK, exportPos, Direction.UP);
        if (cachedExport == null || !cachedExport.getStackInSlot(0).isEmpty()) {
            helper.fail("Could not establish isolated Export missing-Core capability state");
            return;
        }
        BlockPos corePos = exportPos.south();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
            helper.fail("Late Export Core is missing");
            return;
        }
        core.rebuildNetwork(level);
        core.insertItem(new ItemStack(Items.STONE, 3));
        if (!cachedExport.getStackInSlot(0).isEmpty()) {
            helper.fail("Export capability reran missing-Core BFS before the ten-tick backoff expired");
            return;
        }
        helper.runAfterDelay(11, () -> {
            ItemStack visible = cachedExport.getStackInSlot(0);
            if (!visible.is(Items.STONE) || visible.getCount() != 3) {
                helper.fail("Export capability did not retry Core discovery after the ten-tick backoff");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void cached_import_capabilities_recheck_their_original_side(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Cached Import capability Core is missing");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceHandler cachedResource = level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP);
            IItemHandler cachedItems = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, busPos, Direction.UP);
            if (cachedResource == null || cachedItems == null) {
                helper.fail("Could not cache enabled Import capabilities");
                return;
            }
            BusConfiguration downOnly = BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    1 << Direction.DOWN.ordinal(),
                    false,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    1);
            if (!applyConfiguration(level, busPos, MagicStorage.IMPORT_BUS_BE.get(),
                    MagicStorage.IMPORT_BUS_ITEM.get(), downOnly)) {
                helper.fail("Could not remove the cached Import side");
                return;
            }
            StorageResourceKey water = StorageResourceKey.of(
                    StorageResourceKindApi.FLUID_KIND,
                    ResourceLocation.withDefaultNamespace("water"),
                    new CompoundTag());
            ItemStack dirt = new ItemStack(Items.DIRT, 3);
            ItemStack remainder = cachedItems.insertItem(0, dirt, false);
            if (cachedResource.insert(water, 1_000, false) != 0
                    || remainder.getCount() != dirt.getCount()
                    || core.getTypeCount() != 0
                    || level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP) != null) {
                helper.fail("Cached Import capability bypassed its disabled side");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void cached_export_capability_rechecks_directionless_mode(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Cached Export capability Core is missing");
                return;
            }
            core.rebuildNetwork(level);
            BusConfiguration directionless = BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    1);
            if (!applyConfiguration(level, busPos, MagicStorage.EXPORT_BUS_BE.get(),
                    MagicStorage.EXPORT_BUS_ITEM.get(), directionless)) {
                helper.fail("Could not enable passive Export capability");
                return;
            }
            StorageResourceKey water = StorageResourceKey.of(
                    StorageResourceKindApi.FLUID_KIND,
                    ResourceLocation.withDefaultNamespace("water"),
                    new CompoundTag());
            if (core.insertResource(water, 1_000,
                    com.swearprom.magicstorage.magic_storage.Action.EXECUTE) != 1_000) {
                helper.fail("Could not seed cached Export capability resource");
                return;
            }
            StorageResourceHandler cached = level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP);
            if (cached == null) {
                helper.fail("Could not cache enabled Export capability");
                return;
            }
            BusConfiguration directional = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    false,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    2);
            if (!applyConfiguration(level, busPos, MagicStorage.EXPORT_BUS_BE.get(),
                    MagicStorage.EXPORT_BUS_ITEM.get(), directional)) {
                helper.fail("Could not disable passive Export mode");
                return;
            }
            if (cached.extract(water, 500, false) != 0
                    || core.getResourceAmount(water) != 1_000
                    || level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP) != null) {
                helper.fail("Cached Export capability bypassed directional mode");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void cached_bus_capabilities_reject_replaced_block_entities(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos importPos = corePos.west();
        BlockPos exportPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(exportPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Replaced Bus capability Core is missing");
                return;
            }
            core.rebuildNetwork(level);
            BusConfiguration directionless = BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    BusConfiguration.ALL_SIDES_MASK,
                    true,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    1);
            if (!applyConfiguration(level, importPos, MagicStorage.IMPORT_BUS_BE.get(),
                    MagicStorage.IMPORT_BUS_ITEM.get(), directionless)
                    || !applyConfiguration(level, exportPos, MagicStorage.EXPORT_BUS_BE.get(),
                    MagicStorage.EXPORT_BUS_ITEM.get(), directionless)) {
                helper.fail("Could not enable replaced Bus capabilities");
                return;
            }
            StorageResourceKey water = StorageResourceKey.of(
                    StorageResourceKindApi.FLUID_KIND,
                    ResourceLocation.withDefaultNamespace("water"),
                    new CompoundTag());
            if (core.insertResource(water, 1_000, Action.EXECUTE) != 1_000) {
                helper.fail("Could not seed replaced Export Bus resource");
                return;
            }
            StorageResourceHandler cachedImport = level.getCapability(
                    StorageResourceCapabilities.BLOCK, importPos, Direction.UP);
            IItemHandler cachedImportItems = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, importPos, Direction.UP);
            StorageResourceHandler cachedExport = level.getCapability(
                    StorageResourceCapabilities.BLOCK, exportPos, Direction.UP);
            if (cachedImport == null || cachedImportItems == null || cachedExport == null) {
                helper.fail("Could not cache Bus capabilities before replacement");
                return;
            }

            level.setBlock(importPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(exportPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
            ItemStack dirt = new ItemStack(Items.DIRT, 3);
            ItemStack remainder = cachedImportItems.insertItem(0, dirt, false);
            if (cachedImport.insert(water, 250, false) != 0
                    || remainder.getCount() != dirt.getCount()
                    || cachedExport.extract(water, 250, false) != 0
                    || core.getResourceAmount(water) != 1_000
                    || core.getItemCount(ItemKey.of(dirt)) != 0
                    || level.getCapability(
                    StorageResourceCapabilities.BLOCK, importPos, Direction.UP) != null
                    || level.getCapability(
                    StorageResourceCapabilities.BLOCK, exportPos, Direction.UP) != null) {
                helper.fail("Cached Bus capability remained live after block entity replacement");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void passive_bus_capability_rejects_same_network_listener_reentry(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Reentrant passive Bus Core is missing");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceHandler handler = level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP);
            if (handler == null) {
                helper.fail("Reentrant passive Import capability is missing");
                return;
            }
            StorageResourceKey energy = StorageResourceKey.neoforgeEnergy();
            long[] nestedResult = {-1};
            boolean[] firstCallback = {true};
            StorageListener listener = new StorageListener() {
                @Override
                public void onChanged(
                        ItemKey key,
                        long delta,
                        long newAmount,
                        com.swearprom.magicstorage.magic_storage.Actor actor
                ) {
                }

                @Override
                public void onResourceChanged(
                        StorageResourceKey key,
                        long delta,
                        long newAmount,
                        com.swearprom.magicstorage.magic_storage.Actor actor
                ) {
                    if (!firstCallback[0]) return;
                    firstCallback[0] = false;
                    nestedResult[0] = handler.insert(energy, 1, false);
                }
            };
            core.addListener(listener);
            long inserted = handler.insert(energy, 10, false);
            core.removeListener(listener);
            if (inserted != 10
                    || nestedResult[0] != 0
                    || core.getResourceAmount(energy) != 10) {
                helper.fail("Passive Bus capability re-entered the same Core network");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void passive_import_accepts_typed_resources_through_generic_and_native_capabilities(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Typed Import Core is missing");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceHandler generic = level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP);
            IFluidHandler fluids = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, busPos, Direction.UP);
            var energy = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, busPos, Direction.UP);
            if (generic == null || fluids == null || energy == null) {
                helper.fail("Import Bus did not expose all typed insertion capabilities");
                return;
            }
            StorageResourceKey water = StorageResourceKey.of(
                    StorageResourceKindApi.FLUID_KIND,
                    ResourceLocation.withDefaultNamespace("water"),
                    new CompoundTag());
            StorageResourceKey dirtKey = StorageResourceKey.item(
                    new ItemStack(Items.DIRT), level.registryAccess());
            if (generic.insert(water, 1_000, true) != 1_000
                    || core.getResourceAmount(water) != 0
                    || generic.insert(water, 1_000, false) != 1_000
                    || core.getResourceAmount(water) != 1_000
                    || generic.insert(dirtKey, 100, false) != 0
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 0) {
                helper.fail("Generic Import capability violated simulation or exact insertion");
                return;
            }
            FluidStack namedWater = new FluidStack(
                    net.minecraft.world.level.material.Fluids.WATER, 250);
            namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Bus Variant"));
            if (fluids.fill(namedWater, IFluidHandler.FluidAction.EXECUTE) != 250
                    || energy.receiveEnergy(600, false) != 600
                    || core.getTypeCount() != 3
                    || !generic.getStoredResources().isEmpty()) {
                helper.fail("Native Import capabilities did not route into the universal ledger");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void passive_export_extracts_typed_resources_without_accepting_insertion(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Typed Export Core is missing");
                return;
            }
            core.rebuildNetwork(level);
            BusConfiguration config = BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    BusConfiguration.ALL_SIDES_MASK,
                    true,
                    true,
                    BusFilterMode.DENY,
                    List.of(),
                    Optional.empty(),
                    1);
            if (!applyConfiguration(level, busPos, MagicStorage.EXPORT_BUS_BE.get(),
                    MagicStorage.EXPORT_BUS_ITEM.get(), config)) {
                helper.fail("Could not configure typed Export capability");
                return;
            }
            StorageResourceKey water = StorageResourceKey.of(
                    StorageResourceKindApi.FLUID_KIND,
                    ResourceLocation.withDefaultNamespace("water"),
                    new CompoundTag());
            StorageResourceKey energyKey = StorageResourceKey.of(
                    StorageResourceKindApi.ENERGY_KIND,
                    ResourceLocation.fromNamespaceAndPath("neoforge", "energy"),
                    new CompoundTag());
            if (core.insertResource(water, 2_000, com.swearprom.magicstorage.magic_storage.Action.EXECUTE)
                    != 2_000
                    || core.insertResource(energyKey, 800,
                    com.swearprom.magicstorage.magic_storage.Action.EXECUTE) != 800
                    || core.insertItem(new ItemStack(Items.DIRT, 64)) != 64
                    || core.insertItem(new ItemStack(Items.DIRT, 36)) != 36) {
                helper.fail("Could not seed typed Export resources");
                return;
            }
            StorageResourceKey dirtKey = StorageResourceKey.item(
                    new ItemStack(Items.DIRT), level.registryAccess());
            StorageResourceHandler generic = level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP);
            IItemHandler items = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, busPos, Direction.UP);
            IFluidHandler fluids = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, busPos, Direction.UP);
            var energy = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, busPos, Direction.UP);
            if (generic == null || items == null || fluids == null || energy == null) {
                helper.fail("Export Bus did not expose all typed extraction capabilities");
                return;
            }
            if (generic.extract(water, 600, true) != 600
                    || core.getResourceAmount(water) != 2_000
                    || generic.extract(water, 600, false) != 600
                    || generic.insert(water, 1, false) != 0
                    || generic.getStoredResources().contains(dirtKey)
                    || generic.getAmount(dirtKey) != 0
                    || generic.extract(dirtKey, 100, false) != 0
                    || items.getStackInSlot(0).getCount() != 64
                    || items.extractItem(0, Integer.MAX_VALUE, true).getCount() != 64
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 100
                    || items.extractItem(0, Integer.MAX_VALUE, false).getCount() != 64
                    || core.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 36
                    || items.insertItem(0, new ItemStack(Items.STONE), false).getCount() != 1
                    || fluids.drain(400, IFluidHandler.FluidAction.EXECUTE).getAmount() != 400
                    || energy.extractEnergy(300, false) != 300
                    || core.getResourceAmount(water) != 1_000
                    || core.getResourceAmount(energyKey) != 500) {
                helper.fail("Typed Export capability violated extraction direction or exact amounts");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void passive_import_native_wrappers_obey_exact_typed_allow_filter(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Filtered typed Import Core is missing");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey water = StorageResourceKey.of(
                    StorageResourceKindApi.FLUID_KIND,
                    ResourceLocation.withDefaultNamespace("water"),
                    new CompoundTag());
            BusConfiguration config = BusConfiguration.current(
                    BusMode.DIRECTIONAL,
                    BusConfiguration.ALL_SIDES_MASK,
                    true,
                    true,
                    BusFilterMode.ALLOW,
                    List.of(BusFilterRule.resource(water)),
                    Optional.empty(),
                    1);
            if (!applyConfiguration(level, busPos, MagicStorage.IMPORT_BUS_BE.get(),
                    MagicStorage.IMPORT_BUS_ITEM.get(), config)) {
                helper.fail("Could not configure filtered typed Import capability");
                return;
            }
            StorageResourceHandler generic = level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP);
            IFluidHandler fluids = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, busPos, Direction.UP);
            var energy = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, busPos, Direction.UP);
            if (generic == null || fluids == null || energy == null) {
                helper.fail("Filtered typed Import capabilities are missing");
                return;
            }
            FluidStack lava = new FluidStack(
                    net.minecraft.world.level.material.Fluids.LAVA, 250);
            if (generic.insert(water, 500, false) != 500
                    || fluids.fill(lava, IFluidHandler.FluidAction.EXECUTE) != 0
                    || energy.receiveEnergy(300, false) != 0
                    || core.getResourceAmount(water) != 500
                    || core.getTypeCount() != 1) {
                helper.fail("Typed Import native wrapper bypassed the exact Allow filter");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void passive_export_native_wrappers_obey_exact_typed_deny_filter(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos busPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Filtered typed Export Core is missing");
                return;
            }
            core.rebuildNetwork(level);
            StorageResourceKey water = StorageResourceKey.of(
                    StorageResourceKindApi.FLUID_KIND,
                    ResourceLocation.withDefaultNamespace("water"),
                    new CompoundTag());
            StorageResourceKey energyKey = StorageResourceKey.neoforgeEnergy();
            if (core.insertResource(water, 500, Action.EXECUTE) != 500
                    || core.insertResource(energyKey, 300, Action.EXECUTE) != 300) {
                helper.fail("Could not seed filtered typed Export resources");
                return;
            }
            BusConfiguration config = BusConfiguration.current(
                    BusMode.DIRECTIONLESS,
                    BusConfiguration.ALL_SIDES_MASK,
                    true,
                    true,
                    BusFilterMode.DENY,
                    List.of(BusFilterRule.resource(water)),
                    Optional.empty(),
                    1);
            if (!applyConfiguration(level, busPos, MagicStorage.EXPORT_BUS_BE.get(),
                    MagicStorage.EXPORT_BUS_ITEM.get(), config)) {
                helper.fail("Could not configure filtered typed Export capability");
                return;
            }
            StorageResourceHandler generic = level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP);
            IFluidHandler fluids = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, busPos, Direction.UP);
            var energy = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, busPos, Direction.UP);
            if (generic == null || fluids == null || energy == null) {
                helper.fail("Filtered typed Export capabilities are missing");
                return;
            }
            if (!generic.getStoredResources().equals(List.of(energyKey))
                    || !fluids.drain(250, IFluidHandler.FluidAction.EXECUTE).isEmpty()
                    || energy.extractEnergy(300, false) != 300
                    || core.getResourceAmount(water) != 500
                    || core.getResourceAmount(energyKey) != 0) {
                helper.fail("Typed Export native wrapper bypassed the exact Deny filter");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void export_drop_preserves_non_exact_filter_configuration(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos busPos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
            helper.fail("Export Bus is missing for drop configuration test");
            return;
        }
        if (!escrowDropHandoffStateCycles(bus)) {
            helper.fail("Export Bus escrow-drop handoff state did not consume exactly once");
            return;
        }
        BusConfiguration config = BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                false,
                true,
                BusFilterMode.ALLOW,
                List.of(BusFilterRule.item(ResourceLocation.withDefaultNamespace("stone"))),
                Optional.of(UUID.randomUUID()),
                4);
        if (!applyConfiguration(level, busPos, MagicStorage.EXPORT_BUS_BE.get(),
                MagicStorage.EXPORT_BUS_ITEM.get(), config)) {
            helper.fail("Could not apply non-exact Export configuration");
            return;
        }
        ItemStack busDrop = Block.getDrops(level.getBlockState(busPos), level, busPos, bus).stream()
                .filter(stack -> stack.is(MagicStorage.EXPORT_BUS_ITEM.get()))
                .findFirst()
                .orElse(ItemStack.EMPTY);
        if (busDrop.isEmpty() || !busDrop.has(DataComponents.BLOCK_ENTITY_DATA)) {
            helper.fail("Export drop lost non-exact filter configuration");
            return;
        }
        BusConfiguration dropped = BusConfiguration.load(
                busDrop.get(DataComponents.BLOCK_ENTITY_DATA).copyTag(),
                BusKind.EXPORT,
                level.registryAccess());
        if (!dropped.supported() || dropped.owner().isPresent()
                || dropped.filterRules().size() != 1
                || dropped.filterRules().getFirst().type() != BusFilterRule.Type.ITEM
                || !dropped.filterRules().getFirst().id()
                .equals(Optional.of(ResourceLocation.withDefaultNamespace("stone")))) {
            helper.fail("Export drop did not preserve owner-stripped non-exact filter state");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void import_drop_preserves_typed_filter_configuration_without_owner(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos busPos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)) {
            helper.fail("Import Bus is missing for typed drop configuration test");
            return;
        }
        if (!escrowDropHandoffStateCycles(bus)) {
            helper.fail("Import Bus escrow-drop handoff state did not consume exactly once");
            return;
        }
        StorageResourceKey water = StorageResourceKey.fluid(
                new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1),
                level.registryAccess());
        BusConfiguration config = BusConfiguration.current(
                BusMode.DIRECTIONLESS,
                1 << Direction.UP.ordinal(),
                false,
                false,
                BusFilterMode.ALLOW,
                List.of(BusFilterRule.resource(water)),
                Optional.of(UUID.randomUUID()),
                7);
        if (!applyConfiguration(level, busPos, MagicStorage.IMPORT_BUS_BE.get(),
                MagicStorage.IMPORT_BUS_ITEM.get(), config)) {
            helper.fail("Could not apply typed Import drop configuration");
            return;
        }
        ItemStack busDrop = Block.getDrops(level.getBlockState(busPos), level, busPos, bus).stream()
                .filter(stack -> stack.is(MagicStorage.IMPORT_BUS_ITEM.get()))
                .findFirst()
                .orElse(ItemStack.EMPTY);
        if (busDrop.isEmpty() || !busDrop.has(DataComponents.BLOCK_ENTITY_DATA)) {
            helper.fail("Import drop lost typed filter configuration");
            return;
        }
        BusConfiguration dropped = BusConfiguration.load(
                busDrop.get(DataComponents.BLOCK_ENTITY_DATA).copyTag(),
                BusKind.IMPORT,
                level.registryAccess());
        if (!dropped.supported()
                || dropped.owner().isPresent()
                || dropped.mode() != BusMode.DIRECTIONLESS
                || dropped.sideMask() != 1 << Direction.UP.ordinal()
                || dropped.unsidedAccess()
                || dropped.automationEnabled()
                || dropped.filterRules().size() != 1
                || !dropped.filterRules().getFirst().resourceKey().equals(Optional.of(water))) {
            helper.fail("Import drop changed typed filter, side, mode, or owner state");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void bus_configuration_menu_keeps_client_server_slot_and_data_parity(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos busPos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)) {
            helper.fail("Import Bus is missing for menu parity");
            return;
        }
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var serverMenu = new BusConfigurationMenu(600, player.getInventory(), bus);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        BusConfigurationMenu.writeOpenData(buffer, bus);
        var clientMenu = new BusConfigurationMenu(601, player.getInventory(), buffer);
        int serverData = menuDataSlotCount(serverMenu);
        int clientData = menuDataSlotCount(clientMenu);
        if (serverMenu.slots.size() != BusConfigurationMenu.TOTAL_SLOTS
                || clientMenu.slots.size() != BusConfigurationMenu.TOTAL_SLOTS
                || serverData != BusConfigurationMenu.DATA_SLOT_COUNT
                || clientData != BusConfigurationMenu.DATA_SLOT_COUNT) {
            helper.fail("Bus menu parity diverged: serverSlots=" + serverMenu.slots.size()
                    + ", clientSlots=" + clientMenu.slots.size()
                    + ", serverData=" + serverData + ", clientData=" + clientData);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void bus_configuration_menu_rejects_non_owner_and_stale_session_edits(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos busPos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
            helper.fail("Export Bus is missing for menu access test");
            return;
        }
        var owner = helper.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
        BusConfiguration owned = BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                false,
                true,
                BusFilterMode.ALLOW,
                List.of(),
                Optional.of(owner.getUUID()),
                3);
        if (!applyConfiguration(level, busPos, MagicStorage.EXPORT_BUS_BE.get(),
                MagicStorage.EXPORT_BUS_ITEM.get(), owned)) {
            helper.fail("Could not seed owned Bus configuration");
            return;
        }
        var menu = new BusConfigurationMenu(602, owner.getInventory(), bus);
        var intruder = helper.makeMockPlayer(GameType.SURVIVAL);
        intruder.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
        if (menu.clickMenuButton(intruder, BusConfigurationMenu.TOGGLE_MODE_BUTTON)
                || bus.getBusConfiguration().configRevision() != 3) {
            helper.fail("Non-owner changed the Bus configuration");
            return;
        }
        BusConfiguration external = BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                false,
                false,
                BusFilterMode.ALLOW,
                List.of(),
                Optional.of(owner.getUUID()),
                4);
        if (!applyConfiguration(level, busPos, MagicStorage.EXPORT_BUS_BE.get(),
                MagicStorage.EXPORT_BUS_ITEM.get(), external)
                || menu.clickMenuButton(owner, BusConfigurationMenu.TOGGLE_MODE_BUTTON)
                || bus.getBusConfiguration().configRevision() != 4
                || bus.getBusConfiguration().mode() != BusMode.DIRECTIONAL) {
            helper.fail("Stale Bus menu edit was not rejected");
            return;
        }
        menu.broadcastChanges();
        if (menu.canConfigure()) {
            helper.fail("Stale Bus menu remained visually editable after rejecting the edit");
            return;
        }
        BusConfiguration exhausted = BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                false,
                true,
                BusFilterMode.ALLOW,
                List.of(),
                Optional.of(owner.getUUID()),
                Long.MAX_VALUE);
        if (!applyConfiguration(level, busPos, MagicStorage.EXPORT_BUS_BE.get(),
                MagicStorage.EXPORT_BUS_ITEM.get(), exhausted)) {
            helper.fail("Could not seed exhausted Bus configuration revision");
            return;
        }
        var exhaustedMenu = new BusConfigurationMenu(605, owner.getInventory(), bus);
        try {
            if (exhaustedMenu.clickMenuButton(
                    owner, BusConfigurationMenu.TOGGLE_AUTOMATION_BUTTON)) {
                helper.fail("Exhausted Bus revision accepted another menu edit");
                return;
            }
        } catch (IllegalStateException exception) {
            helper.fail("Exhausted Bus revision escaped the menu as an exception");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void bus_mode_menu_edit_updates_the_synced_directionless_blockstate(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos busPos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
            helper.fail("Export Bus is missing for mode blockstate sync");
            return;
        }
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
        bus.assignOwnerOnPlacement(player.getUUID());
        var menu = new BusConfigurationMenu(604, player.getInventory(), bus);
        if (!menu.clickMenuButton(player, BusConfigurationMenu.TOGGLE_MODE_BUTTON)
                || bus.getBusConfiguration().mode() != BusMode.DIRECTIONLESS
                || !level.getBlockState(busPos).getValue(ExportBusBlock.DIRECTIONLESS)) {
            helper.fail("Directionless Bus mode was not synchronized through blockstate");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "behavioraltests.platform")
    public static void bus_filter_ghost_slot_sets_typed_resource_without_consuming_container(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos busPos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
            helper.fail("Export Bus is missing for typed ghost filter");
            return;
        }
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
        bus.assignOwnerOnPlacement(player.getUUID());
        var menu = new BusConfigurationMenu(603, player.getInventory(), bus);
        menu.setCarried(new ItemStack(Items.WATER_BUCKET));
        menu.clicked(BusConfigurationMenu.FILTER_SLOT_START, 0, ClickType.PICKUP, player);
        StorageResourceKey water = StorageResourceKey.fluid(
                new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1),
                level.registryAccess());
        if (!menu.getCarried().is(Items.WATER_BUCKET)
                || menu.getCarried().getCount() != 1
                || bus.getBusConfiguration().filterRules().size() != 1
                || !bus.getBusConfiguration().filterRules().getFirst().resourceKey()
                .equals(Optional.of(water))
                || bus.getBusConfiguration().configRevision() != 1) {
            helper.fail("Typed ghost filter consumed the container or lost exact resource identity: "
                    + "carried=" + menu.getCarried()
                    + ", rules=" + bus.getBusConfiguration().filterRules().size()
                    + ", first=" + (bus.getBusConfiguration().filterRules().isEmpty()
                    ? "none" : bus.getBusConfiguration().filterRules().getFirst().type())
                    + ", key=" + (bus.getBusConfiguration().filterRules().isEmpty()
                    ? "none" : bus.getBusConfiguration().filterRules().getFirst().resourceKey())
                    + ", revision=" + bus.getBusConfiguration().configRevision()
                    + ", owner=" + bus.getBusConfiguration().owner()
                    + ", player=" + player.getUUID()
                    + ", serverPlayer=" + (player instanceof net.minecraft.server.level.ServerPlayer)
                    + ", spectator=" + player.isSpectator()
                    + ", mayBuild=" + player.mayBuild()
                    + ", sameLevel=" + (player.level() == level)
                    + ", sameBe=" + (level.getBlockEntity(busPos) == bus)
                    + ", distance=" + player.distanceToSqr(
                    busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5));
            return;
        }
        helper.succeed();
    }

    private static boolean applyConfiguration(
            net.minecraft.world.level.Level level,
            BlockPos pos,
            BlockEntityType<?> blockEntityType,
            Item blockItem,
            BusConfiguration configuration
    ) {
        CompoundTag tag = new CompoundTag();
        configuration.save(tag, level.registryAccess());
        ItemStack carrier = new ItemStack(blockItem);
        BlockItem.setBlockEntityData(carrier, blockEntityType, tag);
        return BlockItem.updateCustomBlockEntityTag(level, null, pos, carrier);
    }

    private static int menuDataSlotCount(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        try {
            var field = net.minecraft.world.inventory.AbstractContainerMenu.class
                    .getDeclaredField("dataSlots");
            field.setAccessible(true);
            return ((java.util.List<?>) field.get(menu)).size();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static BusConfiguration filterConfig(BusFilterMode mode, List<BusFilterRule> rules) {
        return BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                false,
                true,
                mode,
                rules,
                Optional.empty(),
                0
        );
    }

    private static CompoundTag currentConfigTag(BusFilterMode mode) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putString("mode", "directional");
        tag.putInt("sideMask", BusConfiguration.ALL_SIDES_MASK);
        tag.putBoolean("unsidedAccess", false);
        tag.putBoolean("automationEnabled", true);
        tag.putString("filterMode", mode.name().toLowerCase(java.util.Locale.ROOT));
        tag.putLong("configRevision", 0);
        return tag;
    }

    private static CountingStorageCoreBlockEntity replaceWithCountingCore(
            net.minecraft.server.level.ServerLevel level,
            BlockPos corePos
    ) {
        if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity original)) {
            throw new IllegalStateException("Core test fixture is missing");
        }
        CompoundTag stored = original.saveWithoutMetadata(level.registryAccess());
        level.removeBlockEntity(corePos);
        CountingStorageCoreBlockEntity replacement = new CountingStorageCoreBlockEntity(
                corePos, level.getBlockState(corePos));
        replacement.loadWithComponents(stored, level.registryAccess());
        level.setBlockEntity(replacement);
        replacement.onLoad();
        if (!replacement.isStorageAvailable()) {
            throw new IllegalStateException("Counting Core test fixture did not attach storage");
        }
        return replacement;
    }

    private static void runNextAutomationCycle(ExportBusBlockEntity bus) {
        for (int tick = 0; tick <= 10; tick++) bus.tick();
    }

    private static void makeCoreStorageUnavailable(
            net.minecraft.server.level.ServerLevel level,
            BlockPos corePos
    ) {
        level.removeBlock(corePos, false);
        level.setBlock(
                corePos,
                MagicStorage.STORAGE_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL);
        if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
            throw new IllegalStateException("Unavailable Core test fixture was not replaced");
        }
        CompoundTag reference = new CompoundTag();
        reference.putUUID("storageId", UUID.randomUUID());
        reference.putInt("storageSchema", 1);
        core.loadWithComponents(reference, level.registryAccess());
        if (core.isStorageAvailable()) {
            throw new IllegalStateException("Unavailable Core test fixture remained attached");
        }
    }

    private static void seedPendingResource(
            ImportBusBlockEntity bus,
            StorageResourceKey key,
            long amount,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        bus.loadWithComponents(pendingResourceTag(key, amount), registries);
    }

    private static void seedPendingResource(
            ExportBusBlockEntity bus,
            StorageResourceKey key,
            long amount,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        bus.loadWithComponents(pendingResourceTag(key, amount), registries);
    }

    private static CompoundTag pendingResourceTag(StorageResourceKey key, long amount) {
        CompoundTag entry = new CompoundTag();
        entry.putString("kind", key.kindId().toString());
        entry.putString("resource", key.resourceId().toString());
        entry.put("variant", key.variantData());
        entry.putLong("amount", amount);
        ListTag entries = new ListTag();
        entries.add(entry);
        CompoundTag ledger = new CompoundTag();
        ledger.putInt("schema", 1);
        ledger.put("entries", entries);
        CompoundTag root = new CompoundTag();
        root.put("pendingResources", ledger);
        return root;
    }

    private static boolean isUnavailableNetworkIdFailure(IllegalStateException exception) {
        return exception.getMessage() != null
                && exception.getMessage().contains("Core storage data is unavailable");
    }

    private static CompoundTag itemRule(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "item");
        tag.putString("id", id);
        return tag;
    }

    private static boolean escrowDropHandoffStateCycles(Object bus) {
        try {
            var mark = bus.getClass().getDeclaredMethod("markEscrowDropWillBePreserved");
            var consume = bus.getClass().getDeclaredMethod("consumeEscrowDropWillBePreserved");
            mark.setAccessible(true);
            consume.setAccessible(true);
            if ((boolean) consume.invoke(bus)) return false;
            mark.invoke(bus);
            return (boolean) consume.invoke(bus) && !(boolean) consume.invoke(bus);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static final Map<net.minecraft.world.level.Level, Map<BlockPos, TestEndpoint>>
            TEST_ENDPOINTS = new WeakHashMap<>();
    private static boolean testEndpointCapabilitiesRegistered;

    private static synchronized void installTestEndpoint(
            net.minecraft.world.level.Level level,
            BlockPos pos,
            IItemHandler items,
            IEnergyStorage energy
    ) {
        ensureTestEndpointCapabilities();
        TEST_ENDPOINTS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(pos.immutable(), new TestEndpoint(items, energy));
        level.invalidateCapabilities(pos);
    }

    private static synchronized TestEndpoint testEndpoint(
            net.minecraft.world.level.Level level,
            BlockPos pos
    ) {
        Map<BlockPos, TestEndpoint> endpoints = TEST_ENDPOINTS.get(level);
        return endpoints == null ? null : endpoints.get(pos);
    }

    private static synchronized void ensureTestEndpointCapabilities() {
        if (testEndpointCapabilitiesRegistered) return;
        try {
            Constructor<RegisterCapabilitiesEvent> constructor =
                    RegisterCapabilitiesEvent.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            RegisterCapabilitiesEvent event = constructor.newInstance();
            event.registerBlock(
                    Capabilities.ItemHandler.BLOCK,
                    (level, pos, state, blockEntity, side) -> {
                        TestEndpoint endpoint = testEndpoint(level, pos);
                        return endpoint == null ? null : endpoint.items();
                    },
                    Blocks.BLUE_GLAZED_TERRACOTTA);
            event.registerBlock(
                    Capabilities.EnergyStorage.BLOCK,
                    (level, pos, state, blockEntity, side) -> {
                        TestEndpoint endpoint = testEndpoint(level, pos);
                        return endpoint == null ? null : endpoint.energy();
                    },
                    Blocks.BLUE_GLAZED_TERRACOTTA);
            testEndpointCapabilitiesRegistered = true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record TestEndpoint(IItemHandler items, IEnergyStorage energy) {
    }

    private static final class CountingStorageCoreBlockEntity extends StorageCoreBlockEntity {
        private int displayStackBuilds;

        private CountingStorageCoreBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            super(pos, state);
        }

        @Override
        public List<ItemStack> getDisplayStacks() {
            displayStackBuilds++;
            return super.getDisplayStacks();
        }

        private int displayStackBuilds() {
            return displayStackBuilds;
        }
    }

    private static final class TestItemHandler implements IItemHandler {
        private final ItemStack[] stacks;
        private ItemStack simulatedExtract = ItemStack.EMPTY;
        private Runnable onSimulatedInsert;
        private Runnable onExecutedInsert;
        private Runnable onSimulatedExtract;
        private Runnable onExecutedExtract;
        private boolean extractionEnabled = true;

        private TestItemHandler(int slots) {
            stacks = new ItemStack[slots];
            java.util.Arrays.fill(stacks, ItemStack.EMPTY);
        }

        private void setStack(int slot, ItemStack stack) {
            stacks[slot] = stack.copy();
        }

        private void setSimulatedExtract(ItemStack stack) {
            simulatedExtract = stack.copy();
        }

        private void setOnSimulatedInsert(Runnable callback) {
            onSimulatedInsert = callback;
        }

        private void setOnExecutedInsert(Runnable callback) {
            onExecutedInsert = callback;
        }

        private void setOnSimulatedExtract(Runnable callback) {
            onSimulatedExtract = callback;
        }

        private void setOnExecutedExtract(Runnable callback) {
            onExecutedExtract = callback;
        }

        private void setExtractionEnabled(boolean enabled) {
            extractionEnabled = enabled;
        }

        @Override
        public int getSlots() {
            return stacks.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < stacks.length ? stacks[slot].copy() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= stacks.length || stack.isEmpty()) return stack.copy();
            ItemStack existing = stacks[slot];
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) {
                return stack.copy();
            }
            int existingCount = existing.isEmpty() ? 0 : existing.getCount();
            int accepted = Math.min(stack.getCount(), 64 - existingCount);
            if (!simulate && accepted > 0) {
                stacks[slot] = stack.copyWithCount(existingCount + accepted);
            }
            if (simulate) onSimulatedInsert = runOnce(onSimulatedInsert);
            else onExecutedInsert = runOnce(onExecutedInsert);
            return accepted == stack.getCount()
                    ? ItemStack.EMPTY
                    : stack.copyWithCount(stack.getCount() - accepted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!extractionEnabled || slot < 0 || slot >= stacks.length || amount <= 0) {
                return ItemStack.EMPTY;
            }
            if (simulate && !simulatedExtract.isEmpty()) {
                return simulatedExtract.copyWithCount(Math.min(amount, simulatedExtract.getCount()));
            }
            ItemStack existing = stacks[slot];
            if (existing.isEmpty()) return ItemStack.EMPTY;
            int extracted = Math.min(amount, existing.getCount());
            ItemStack result = existing.copyWithCount(extracted);
            if (!simulate) {
                existing.shrink(extracted);
                if (existing.isEmpty()) stacks[slot] = ItemStack.EMPTY;
            }
            if (simulate) onSimulatedExtract = runOnce(onSimulatedExtract);
            else onExecutedExtract = runOnce(onExecutedExtract);
            return result;
        }

        private static Runnable runOnce(Runnable callback) {
            if (callback != null) callback.run();
            return null;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < stacks.length ? 64 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < stacks.length && !stack.isEmpty();
        }
    }

    private static final class TestEnergyStorage implements IEnergyStorage {
        private final int capacity;
        private int energy;

        private TestEnergyStorage(int capacity, int energy) {
            this.capacity = capacity;
            this.energy = energy;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.min(maxReceive, capacity - energy);
            if (!simulate) energy += accepted;
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = Math.min(maxExtract, energy);
            if (!simulate) energy -= extracted;
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
