package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Objects;

public final class RecipePresentation {
    public static final int MAX_INPUTS = 9;
    public static final int MAX_ITEM_RESOURCES = TypedRecipePlan.MAX_INPUTS;

    private static final String ID_KEY = "magic_storage:recipe_presentation_id";
    private static final String KIND_KEY = "magic_storage:recipe_presentation_kind";
    private static final String STATION_ID_KEY = "magic_storage:recipe_presentation_station_id";
    private static final String WIDTH_KEY = "magic_storage:recipe_presentation_width";
    private static final String HEIGHT_KEY = "magic_storage:recipe_presentation_height";
    private static final String SHAPELESS_KEY = "magic_storage:recipe_presentation_shapeless";
    private static final String ITEM_RESOURCE_COUNT_KEY =
            "magic_storage:recipe_presentation_item_resource_count";
    private static final String TOOL_AVAILABLE_KEY =
            "magic_storage:recipe_presentation_tool_available";
    private static final String TOOL_REQUIRED_KEY =
            "magic_storage:recipe_presentation_tool_required";
    private static final String TOOL_INFINITE_KEY =
            "magic_storage:recipe_presentation_tool_infinite";
    private static final String STATION_WORK_AVAILABLE_KEY =
            "magic_storage:recipe_presentation_station_work_available";
    private static final String STATION_WORK_REQUIRED_KEY =
            "magic_storage:recipe_presentation_station_work_required";

    public enum ResourceKind {
        ITEM,
        ENERGY,
        STATION_WORK,
        TOOL
    }

    public record Resource(
            ResourceKind kind,
            ItemStack stack,
            EnergyType energyType,
            long available,
            long required,
            boolean infinite
    ) {
        public Resource {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(stack, "stack");
            stack = stack.copyWithCount(stack.isEmpty() ? 0 : 1);
            if (available < 0 || required <= 0) {
                throw new IllegalArgumentException("Recipe resource amounts must be non-negative/positive");
            }
            if (kind == ResourceKind.ENERGY) {
                if (energyType == null || !stack.isEmpty()) {
                    throw new IllegalArgumentException("Energy resources require only an energy type");
                }
            } else if (energyType != null || stack.isEmpty()) {
                throw new IllegalArgumentException("Item/tool resources require only an item stack");
            }
            if (infinite && kind != ResourceKind.TOOL) {
                throw new IllegalArgumentException("Only tool resources can be infinite");
            }
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }

        public static Resource item(ItemStack stack, long available, long required) {
            return new Resource(ResourceKind.ITEM, stack, null, available, required, false);
        }

        public static Resource energy(EnergyType type, long available, long required) {
            return new Resource(ResourceKind.ENERGY, ItemStack.EMPTY, type, available, required, false);
        }

        public static Resource stationWork(ItemStack stack, long available, long required) {
            return new Resource(ResourceKind.STATION_WORK, stack, null, available, required, false);
        }

        public static Resource tool(ItemStack stack, long available, long required) {
            return tool(stack, available, required, false);
        }

        public static Resource tool(ItemStack stack, long available, long required, boolean infinite) {
            return new Resource(ResourceKind.TOOL, stack, null, available, required, infinite);
        }
    }

    record Metadata(
            ResourceLocation recipeId,
            ResourceLocation stationDescriptorId,
            RecipePresentationKind kind,
            int width,
            int height,
            boolean shapeless,
            int itemResourceCount,
            long toolAvailable,
            long toolRequired,
            boolean toolInfinite,
            long stationWorkAvailable,
            long stationWorkRequired
    ) {
        Metadata {
            Objects.requireNonNull(recipeId, "recipeId");
            Objects.requireNonNull(stationDescriptorId, "stationDescriptorId");
            Objects.requireNonNull(kind, "kind");
            if (kind == RecipePresentationKind.NONE) {
                throw new IllegalArgumentException("Non-empty presentation cannot use NONE kind");
            }
            if (width < 1 || width > 3 || height < 1 || height > 3) {
                throw new IllegalArgumentException("Recipe presentation dimensions must be within 1..3");
            }
            if (itemResourceCount < 0 || itemResourceCount > MAX_ITEM_RESOURCES) {
                throw new IllegalArgumentException("Recipe presentation item resource count is out of bounds");
            }
            if (toolAvailable < 0 || toolRequired < 0) {
                throw new IllegalArgumentException("Recipe presentation tool amounts cannot be negative");
            }
            if (stationWorkAvailable < 0 || stationWorkRequired < 0
                    || stationWorkRequired == 0 && stationWorkAvailable != 0) {
                throw new IllegalArgumentException("Recipe presentation station work is invalid");
            }
            if ((toolRequired == 0) != (toolAvailable == 0)) {
                throw new IllegalArgumentException("Tool availability and requirement must be present together");
            }
            if (toolInfinite && (toolRequired == 0 || toolAvailable != Long.MAX_VALUE)) {
                throw new IllegalArgumentException("Infinite tool metadata requires an explicit available tool row");
            }
        }
    }

    private static final RecipePresentation EMPTY = new RecipePresentation();

    private final ResourceLocation recipeId;
    private final RecipePresentationKind kind;
    private final int width;
    private final int height;
    private final boolean shapeless;
    private final List<ItemStack> inputs;
    private final ItemStack output;
    private final ItemStack station;
    private final List<ItemStack> stationVariants;
    private final List<Resource> resources;

    private RecipePresentation() {
        recipeId = null;
        kind = RecipePresentationKind.NONE;
        width = 0;
        height = 0;
        shapeless = false;
        inputs = List.of();
        output = ItemStack.EMPTY;
        station = ItemStack.EMPTY;
        stationVariants = List.of();
        resources = List.of();
    }

    RecipePresentation(
            Metadata metadata,
            List<ItemStack> inputs,
            ItemStack output,
            ItemStack station,
            List<ItemStack> stationVariants,
            List<Resource> resources
    ) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(stationVariants, "stationVariants");
        Objects.requireNonNull(resources, "resources");
        if (inputs.size() != MAX_INPUTS) {
            throw new IllegalArgumentException("Recipe presentation requires exactly nine input positions");
        }
        if (output.isEmpty() || station.isEmpty()) {
            throw new IllegalArgumentException("Recipe presentation requires output and station stacks");
        }
        if (stationVariants.isEmpty() || stationVariants.stream().anyMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("Recipe presentation requires station variants");
        }
        long itemRows = resources.stream().filter(row -> row.kind() == ResourceKind.ITEM).count();
        long toolRows = resources.stream().filter(row -> row.kind() == ResourceKind.TOOL).count();
        long stationRows = resources.stream()
                .filter(row -> row.kind() == ResourceKind.STATION_WORK).count();
        if (itemRows != metadata.itemResourceCount() || toolRows > 1
                || stationRows != (metadata.stationWorkRequired() > 0 ? 1 : 0)) {
            throw new IllegalArgumentException("Recipe resource rows do not match presentation metadata");
        }
        this.recipeId = metadata.recipeId();
        this.kind = metadata.kind();
        this.width = metadata.width();
        this.height = metadata.height();
        this.shapeless = metadata.shapeless();
        this.inputs = inputs.stream().map(ItemStack::copy).toList();
        this.output = output.copy();
        this.station = station.copyWithCount(1);
        this.stationVariants = stationVariants.stream()
                .map(stack -> stack.copyWithCount(1))
                .toList();
        this.resources = List.copyOf(resources);
    }

    public static RecipePresentation empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return kind == RecipePresentationKind.NONE;
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public RecipePresentationKind kind() {
        return kind;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean shapeless() {
        return shapeless;
    }

    public List<ItemStack> inputs() {
        return inputs.stream().map(ItemStack::copy).toList();
    }

    public ItemStack output() {
        return output.copy();
    }

    public ItemStack station() {
        return station.copy();
    }

    public List<ItemStack> stationVariants() {
        return stationVariants.stream().map(ItemStack::copy).toList();
    }

    public ItemStack stationForCycle(long cycle) {
        if (stationVariants.isEmpty()) return station();
        int index = Math.floorMod(cycle, stationVariants.size());
        return stationVariants.get(index).copy();
    }

    public List<Resource> resources() {
        return resources;
    }

    static ItemStack metadataCarrier(Metadata metadata) {
        ItemStack carrier = new ItemStack(Items.PAPER);
        CustomData.update(DataComponents.CUSTOM_DATA, carrier, tag -> {
            tag.putString(ID_KEY, metadata.recipeId().toString());
            tag.putString(STATION_ID_KEY, metadata.stationDescriptorId().toString());
            tag.putInt(KIND_KEY, metadata.kind().ordinal());
            tag.putInt(WIDTH_KEY, metadata.width());
            tag.putInt(HEIGHT_KEY, metadata.height());
            tag.putBoolean(SHAPELESS_KEY, metadata.shapeless());
            tag.putInt(ITEM_RESOURCE_COUNT_KEY, metadata.itemResourceCount());
            tag.putLong(TOOL_AVAILABLE_KEY, metadata.toolAvailable());
            tag.putLong(TOOL_REQUIRED_KEY, metadata.toolRequired());
            tag.putBoolean(TOOL_INFINITE_KEY, metadata.toolInfinite());
            tag.putLong(STATION_WORK_AVAILABLE_KEY, metadata.stationWorkAvailable());
            tag.putLong(STATION_WORK_REQUIRED_KEY, metadata.stationWorkRequired());
        });
        return carrier;
    }

    static Metadata metadataFromCarrier(ItemStack carrier) {
        if (carrier.isEmpty()) return null;
        if (!carrier.is(Items.PAPER)) {
            throw new IllegalStateException("Recipe presentation metadata carrier is not paper");
        }
        CustomData data = carrier.get(DataComponents.CUSTOM_DATA);
        if (data == null) throw new IllegalStateException("Recipe presentation metadata is missing");
        CompoundTag tag = data.copyTag();
        requireType(tag, ID_KEY, Tag.TAG_STRING);
        requireType(tag, STATION_ID_KEY, Tag.TAG_STRING);
        requireType(tag, KIND_KEY, Tag.TAG_INT);
        requireType(tag, WIDTH_KEY, Tag.TAG_INT);
        requireType(tag, HEIGHT_KEY, Tag.TAG_INT);
        requireType(tag, SHAPELESS_KEY, Tag.TAG_BYTE);
        requireType(tag, ITEM_RESOURCE_COUNT_KEY, Tag.TAG_INT);
        requireType(tag, TOOL_AVAILABLE_KEY, Tag.TAG_LONG);
        requireType(tag, TOOL_REQUIRED_KEY, Tag.TAG_LONG);
        requireType(tag, TOOL_INFINITE_KEY, Tag.TAG_BYTE);
        requireType(tag, STATION_WORK_AVAILABLE_KEY, Tag.TAG_LONG);
        requireType(tag, STATION_WORK_REQUIRED_KEY, Tag.TAG_LONG);
        return new Metadata(
                ResourceLocation.parse(tag.getString(ID_KEY)),
                ResourceLocation.parse(tag.getString(STATION_ID_KEY)),
                RecipePresentationKind.fromOrdinal(tag.getInt(KIND_KEY)),
                tag.getInt(WIDTH_KEY),
                tag.getInt(HEIGHT_KEY),
                tag.getBoolean(SHAPELESS_KEY),
                tag.getInt(ITEM_RESOURCE_COUNT_KEY),
                tag.getLong(TOOL_AVAILABLE_KEY),
                tag.getLong(TOOL_REQUIRED_KEY),
                tag.getBoolean(TOOL_INFINITE_KEY),
                tag.getLong(STATION_WORK_AVAILABLE_KEY),
                tag.getLong(STATION_WORK_REQUIRED_KEY));
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalStateException("Recipe presentation metadata has invalid field: " + key);
        }
    }
}
