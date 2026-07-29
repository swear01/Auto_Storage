package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BusConfiguration {

    public static final String TAG_BUS_CONFIG = "busConfig";
    public static final int ALL_SIDES_MASK = 0b111111;
    public static final int MAX_FILTER_RULES = 9;
    private static final int CURRENT_SCHEMA = 2;
    private static final int ITEM_FILTER_SCHEMA = 1;
    private static final String TAG_SCHEMA = "schema";
    private static final String TAG_MODE = "mode";
    private static final String TAG_SIDE_MASK = "sideMask";
    private static final String TAG_UNSIDED_ACCESS = "unsidedAccess";
    private static final String TAG_AUTOMATION_ENABLED = "automationEnabled";
    private static final String TAG_FILTER_MODE = "filterMode";
    private static final String TAG_FILTER_RULES = "filterRules";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_CONFIG_REVISION = "configRevision";

    private final int schema;
    private final BusMode mode;
    private final int sideMask;
    private final boolean unsidedAccess;
    private final boolean automationEnabled;
    private final BusFilterMode filterMode;
    private final List<BusFilterRule> filterRules;
    private final Optional<UUID> owner;
    private final long configRevision;
    private final Tag unsupportedRaw;

    private BusConfiguration(
            int schema,
            BusMode mode,
            int sideMask,
            boolean unsidedAccess,
            boolean automationEnabled,
            BusFilterMode filterMode,
            List<BusFilterRule> filterRules,
            Optional<UUID> owner,
            long configRevision,
            Tag unsupportedRaw
    ) {
        this.schema = schema;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.sideMask = sideMask;
        this.unsidedAccess = unsidedAccess;
        this.automationEnabled = automationEnabled;
        this.filterMode = Objects.requireNonNull(filterMode, "filterMode");
        this.filterRules = List.copyOf(filterRules);
        this.owner = Objects.requireNonNull(owner, "owner");
        this.configRevision = configRevision;
        this.unsupportedRaw = unsupportedRaw == null ? null : unsupportedRaw.copy();
    }

    public static BusConfiguration current(
            BusMode mode,
            int sideMask,
            boolean unsidedAccess,
            boolean automationEnabled,
            BusFilterMode filterMode,
            List<BusFilterRule> filterRules,
            Optional<UUID> owner,
            long configRevision
    ) {
        Objects.requireNonNull(filterRules, "filterRules");
        if ((sideMask & ~ALL_SIDES_MASK) != 0 || sideMask < 0) {
            throw new IllegalArgumentException("Bus side mask must contain only six direction bits");
        }
        if (filterRules.size() > MAX_FILTER_RULES || filterRules.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Bus filter rule count must be at most " + MAX_FILTER_RULES);
        }
        if (configRevision < 0) {
            throw new IllegalArgumentException("Bus config revision must be non-negative");
        }
        return new BusConfiguration(
                CURRENT_SCHEMA,
                mode,
                sideMask,
                unsidedAccess,
                automationEnabled,
                filterMode,
                filterRules,
                owner,
                configRevision,
                null
        );
    }

    public static BusConfiguration defaults(BusKind kind) {
        return current(
                BusMode.DIRECTIONAL,
                ALL_SIDES_MASK,
                kind == BusKind.IMPORT,
                true,
                kind == BusKind.IMPORT ? BusFilterMode.DENY : BusFilterMode.ALLOW,
                List.of(),
                Optional.empty(),
                0
        );
    }

    public static BusConfiguration load(
            CompoundTag root,
            BusKind kind,
            HolderLookup.Provider registries
    ) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(registries, "registries");
        Tag raw = root.get(TAG_BUS_CONFIG);
        if (raw == null) return migrateLegacy(root, kind, registries);
        if (!(raw instanceof CompoundTag tag)) return unsupported(raw);
        if (!tag.contains(TAG_SCHEMA, Tag.TAG_INT)
                || tag.getInt(TAG_SCHEMA) != CURRENT_SCHEMA
                && tag.getInt(TAG_SCHEMA) != ITEM_FILTER_SCHEMA) {
            return unsupported(tag);
        }
        if (!hasCurrentShape(tag)) return unsupported(tag);
        Optional<BusMode> mode = BusMode.parse(tag.getString(TAG_MODE));
        Optional<BusFilterMode> filterMode = BusFilterMode.parse(tag.getString(TAG_FILTER_MODE));
        int sideMask = tag.getInt(TAG_SIDE_MASK);
        long configRevision = tag.getLong(TAG_CONFIG_REVISION);
        if (mode.isEmpty() || filterMode.isEmpty()
                || sideMask < 0 || (sideMask & ~ALL_SIDES_MASK) != 0
                || configRevision < 0
                || tag.contains(TAG_OWNER) && !tag.hasUUID(TAG_OWNER)) {
            return unsupported(tag);
        }
        if (!(tag.get(TAG_FILTER_RULES) instanceof ListTag ruleTags)
                || !ruleTags.isEmpty() && ruleTags.getElementType() != Tag.TAG_COMPOUND
                || ruleTags.size() > MAX_FILTER_RULES) {
            return unsupported(tag);
        }
        List<BusFilterRule> rules = ruleTags.stream()
                .map(value -> BusFilterRule.load((CompoundTag) value, registries))
                .toList();
        Optional<UUID> owner = tag.hasUUID(TAG_OWNER)
                ? Optional.of(tag.getUUID(TAG_OWNER))
                : Optional.empty();
        return current(
                mode.orElseThrow(),
                sideMask,
                tag.getBoolean(TAG_UNSIDED_ACCESS),
                tag.getBoolean(TAG_AUTOMATION_ENABLED),
                filterMode.orElseThrow(),
                rules,
                owner,
                configRevision
        );
    }

    private static boolean hasCurrentShape(CompoundTag tag) {
        return tag.contains(TAG_MODE, Tag.TAG_STRING)
                && tag.contains(TAG_SIDE_MASK, Tag.TAG_INT)
                && tag.contains(TAG_UNSIDED_ACCESS, Tag.TAG_BYTE)
                && tag.contains(TAG_AUTOMATION_ENABLED, Tag.TAG_BYTE)
                && tag.contains(TAG_FILTER_MODE, Tag.TAG_STRING)
                && tag.contains(TAG_FILTER_RULES, Tag.TAG_LIST)
                && tag.contains(TAG_CONFIG_REVISION, Tag.TAG_LONG);
    }

    private static BusConfiguration migrateLegacy(
            CompoundTag root,
            BusKind kind,
            HolderLookup.Provider registries
    ) {
        BusConfiguration defaults = defaults(kind);
        if (kind != BusKind.EXPORT || !(root.get("filter") instanceof CompoundTag legacyFilter)) {
            return defaults;
        }
        BusFilterRule rule = BusFilterRule.loadLegacyExact(legacyFilter, registries);
        return current(
                defaults.mode,
                defaults.sideMask,
                defaults.unsidedAccess,
                defaults.automationEnabled,
                defaults.filterMode,
                List.of(rule),
                defaults.owner,
                defaults.configRevision
        );
    }

    private static BusConfiguration unsupported(Tag raw) {
        int schema = raw instanceof CompoundTag tag && tag.contains(TAG_SCHEMA, Tag.TAG_INT)
                ? tag.getInt(TAG_SCHEMA)
                : -1;
        return new BusConfiguration(
                schema,
                BusMode.DIRECTIONAL,
                0,
                false,
                false,
                BusFilterMode.DENY,
                List.of(),
                Optional.empty(),
                0,
                raw
        );
    }

    public void save(CompoundTag root, HolderLookup.Provider registries) {
        if (unsupportedRaw != null) {
            root.put(TAG_BUS_CONFIG, unsupportedRaw.copy());
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_SCHEMA, schema);
        tag.putString(TAG_MODE, mode.serializedName());
        tag.putInt(TAG_SIDE_MASK, sideMask);
        tag.putBoolean(TAG_UNSIDED_ACCESS, unsidedAccess);
        tag.putBoolean(TAG_AUTOMATION_ENABLED, automationEnabled);
        tag.putString(TAG_FILTER_MODE, filterMode.serializedName());
        ListTag rules = new ListTag();
        for (BusFilterRule rule : filterRules) rules.add(rule.save(registries));
        tag.put(TAG_FILTER_RULES, rules);
        owner.ifPresent(value -> tag.putUUID(TAG_OWNER, value));
        tag.putLong(TAG_CONFIG_REVISION, configRevision);
        root.put(TAG_BUS_CONFIG, tag);
    }

    public BusConfiguration assignInitialOwner(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        if (!supported() || this.owner.isPresent()) return this;
        return current(
                mode,
                sideMask,
                unsidedAccess,
                automationEnabled,
                filterMode,
                filterRules,
                Optional.of(owner),
                configRevision
        );
    }

    public BusConfiguration withoutOwner() {
        if (unsupportedRaw instanceof CompoundTag raw) {
            CompoundTag sanitized = raw.copy();
            sanitized.remove(TAG_OWNER);
            return unsupported(sanitized);
        }
        if (owner.isEmpty()) return this;
        return current(
                mode,
                sideMask,
                unsidedAccess,
                automationEnabled,
                filterMode,
                filterRules,
                Optional.empty(),
                configRevision
        );
    }

    public BusConfiguration withSingleExactFilter(ItemStack stack) {
        if (!supported()) return this;
        List<BusFilterRule> rules = stack.isEmpty()
                ? List.of()
                : List.of(BusFilterRule.exact(stack));
        return current(
                mode,
                sideMask,
                unsidedAccess,
                automationEnabled,
                filterMode,
                rules,
                owner,
                nextRevision()
        );
    }

    public long nextRevision() {
        if (configRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("Bus config revision exhausted");
        }
        return configRevision + 1;
    }

    public int schema() {
        return schema;
    }

    public BusMode mode() {
        return mode;
    }

    public int sideMask() {
        return sideMask;
    }

    public boolean unsidedAccess() {
        return unsidedAccess;
    }

    public boolean automationEnabled() {
        return automationEnabled;
    }

    public boolean allowsCapability(Direction side) {
        if (!supported() || !automationEnabled) return false;
        return side == null
                ? unsidedAccess
                : (sideMask & 1 << side.ordinal()) != 0;
    }

    public BusFilterMode filterMode() {
        return filterMode;
    }

    public List<BusFilterRule> filterRules() {
        return filterRules;
    }

    public Optional<UUID> owner() {
        return owner;
    }

    public long configRevision() {
        return configRevision;
    }

    public boolean supported() {
        return unsupportedRaw == null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BusConfiguration config)) return false;
        return schema == config.schema
                && sideMask == config.sideMask
                && unsidedAccess == config.unsidedAccess
                && automationEnabled == config.automationEnabled
                && configRevision == config.configRevision
                && mode == config.mode
                && filterMode == config.filterMode
                && filterRules.equals(config.filterRules)
                && owner.equals(config.owner)
                && Objects.equals(unsupportedRaw, config.unsupportedRaw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                schema,
                mode,
                sideMask,
                unsidedAccess,
                automationEnabled,
                filterMode,
                filterRules,
                owner,
                configRevision,
                unsupportedRaw
        );
    }
}
