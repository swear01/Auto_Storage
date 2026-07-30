package com.swear.autostorage;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

record RecipeCandidateIndex(Coverage coverage, List<ItemStack> representatives) {
    enum Coverage {
        EXHAUSTIVE,
        NON_EXHAUSTIVE
    }

    RecipeCandidateIndex {
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(representatives, "representatives");
        representatives = representatives.stream()
                .map(stack -> Objects.requireNonNull(stack, "representative"))
                .map(stack -> stack.copyWithCount(stack.isEmpty() ? 0 : 1))
                .toList();
    }

    static RecipeCandidateIndex exhaustive(List<ItemStack> representatives) {
        return new RecipeCandidateIndex(Coverage.EXHAUSTIVE, representatives);
    }

    static RecipeCandidateIndex nonExhaustive(List<ItemStack> representatives) {
        return new RecipeCandidateIndex(Coverage.NON_EXHAUSTIVE, representatives);
    }

    boolean isExhaustive() {
        return coverage == Coverage.EXHAUSTIVE;
    }

    @Override
    public List<ItemStack> representatives() {
        return representatives.stream().map(ItemStack::copy).toList();
    }
}
