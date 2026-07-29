package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TypedRecipePlan {
    public static final int MAX_INPUTS = 81;

    private final List<TypedRecipeInput> inputs;
    private final List<TypedRecipeOutput> outputs;
    private final TypedRecipeOutput selectionOutput;
    private final ItemStack presentationOutput;
    private final int width;
    private final int height;
    private final boolean shapeless;

    private TypedRecipePlan(Builder builder) {
        inputs = List.copyOf(builder.inputs);
        outputs = List.copyOf(builder.outputs);
        List<TypedRecipeOutput> primaryOutputs = outputs.stream()
                .filter(output -> output.role() == TypedRecipeOutput.Role.PRIMARY)
                .toList();
        List<TypedRecipeOutput> primaryItems = primaryOutputs.stream()
                .filter(output -> output.key().kindId().equals(StorageResourceKindApi.ITEM_KIND))
                .toList();
        selectionOutput = primaryItems.size() == 1
                ? primaryItems.getFirst()
                : primaryItems.isEmpty() && primaryOutputs.size() == 1
                        ? primaryOutputs.getFirst()
                        : null;
        presentationOutput = builder.presentationOutput.copy();
        width = builder.width;
        height = builder.height;
        shapeless = builder.shapeless;
        if (inputs.isEmpty() || inputs.size() > MAX_INPUTS) {
            throw new IllegalArgumentException("Typed recipe plan requires one to 81 inputs");
        }
        if (primaryOutputs.isEmpty()) {
            throw new IllegalArgumentException("Typed recipe plan requires a primary output");
        }
        if (selectionOutput == null) {
            throw new IllegalArgumentException(
                    "Typed recipe plan requires one unambiguous terminal selection output");
        }
        if (presentationOutput.isEmpty()) {
            throw new IllegalArgumentException("Typed recipe plan requires a presentation output");
        }
        if (selectionOutput.key().kindId().equals(StorageResourceKindApi.ITEM_KIND)
                && !selectionOutput.key().resourceId().equals(
                BuiltInRegistries.ITEM.getKey(presentationOutput.getItem()))) {
            throw new IllegalArgumentException(
                    "Typed item selection output must match its presentation item");
        }
        if (width < 1 || width > 3 || height < 1 || height > 3
                || (inputs.size() <= RecipePresentation.MAX_INPUTS
                && width * height < inputs.size())
                || (inputs.size() > RecipePresentation.MAX_INPUTS
                && (!shapeless || width != 3 || height != 3))) {
            throw new IllegalArgumentException(
                    "Typed recipe layout must fit its inputs or use a 3x3 shapeless summary");
        }
        requireCompatibleInputKeys(inputs);
        requireUniqueKeys(outputs.stream().map(TypedRecipeOutput::key).toList(), "output");
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<TypedRecipeInput> inputs() {
        return inputs;
    }

    public List<TypedRecipeOutput> outputs() {
        return outputs;
    }

    public StorageResourceKey selectionOutputKey() {
        return selectionOutput.key();
    }

    public TypedRecipeOutput selectionOutput() {
        return selectionOutput;
    }

    public ItemStack presentationOutput() {
        return presentationOutput.copy();
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

    private static void requireUniqueKeys(List<StorageResourceKey> keys, String name) {
        Set<StorageResourceKey> unique = new HashSet<>();
        for (StorageResourceKey key : keys) {
            if (!unique.add(key)) {
                throw new IllegalArgumentException("Typed recipe " + name + " keys must be unique");
            }
        }
    }

    private static void requireCompatibleInputKeys(List<TypedRecipeInput> inputs) {
        java.util.Map<StorageResourceKey, TypedRecipeInput.Role> roles = new java.util.HashMap<>();
        for (TypedRecipeInput input : inputs) {
            for (StorageResourceKey key : input.alternatives()) {
                TypedRecipeInput.Role previous = roles.putIfAbsent(key, input.role());
                if (previous != null
                        && (previous != TypedRecipeInput.Role.CONSUME
                        || input.role() != TypedRecipeInput.Role.CONSUME)) {
                    throw new IllegalArgumentException(
                            "Retained typed recipe input keys cannot overlap");
                }
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TypedRecipePlan plan
                && inputs.equals(plan.inputs)
                && outputs.equals(plan.outputs)
                && ItemStack.isSameItemSameComponents(presentationOutput, plan.presentationOutput)
                && presentationOutput.getCount() == plan.presentationOutput.getCount()
                && width == plan.width
                && height == plan.height
                && shapeless == plan.shapeless;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                inputs,
                outputs,
                ItemStack.hashItemAndComponents(presentationOutput),
                presentationOutput.getCount(),
                width,
                height,
                shapeless);
    }

    public static final class Builder {
        private final List<TypedRecipeInput> inputs = new ArrayList<>();
        private final List<TypedRecipeOutput> outputs = new ArrayList<>();
        private ItemStack presentationOutput = ItemStack.EMPTY;
        private int width;
        private int height;
        private boolean shapeless;

        private Builder() {
        }

        public Builder input(TypedRecipeInput input) {
            inputs.add(Objects.requireNonNull(input, "input"));
            return this;
        }

        public Builder output(TypedRecipeOutput output) {
            outputs.add(Objects.requireNonNull(output, "output"));
            return this;
        }

        public Builder presentationOutput(ItemStack output) {
            presentationOutput = Objects.requireNonNull(output, "output").copy();
            return this;
        }

        public Builder layout(int width, int height, boolean shapeless) {
            this.width = width;
            this.height = height;
            this.shapeless = shapeless;
            return this;
        }

        public TypedRecipePlan build() {
            return new TypedRecipePlan(this);
        }
    }
}
