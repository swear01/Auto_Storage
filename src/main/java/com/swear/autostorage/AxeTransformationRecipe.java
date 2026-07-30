package com.swear.autostorage;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbility;

final class AxeTransformationRecipe implements Recipe<SingleRecipeInput> {
    static final RecipeType<AxeTransformationRecipe> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return AutoStorage.MODID + ":axe_transformation";
        }
    };
    private static final MapCodec<AxeTransformationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(AxeTransformationRecipe::input),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AxeTransformationRecipe::result),
            ItemAbility.CODEC.fieldOf("ability").forGetter(AxeTransformationRecipe::ability)
    ).apply(instance, AxeTransformationRecipe::new));
    private static final RecipeSerializer<AxeTransformationRecipe> SERIALIZER =
            new RecipeSerializer<>() {
                private final StreamCodec<RegistryFriendlyByteBuf, AxeTransformationRecipe> streamCodec =
                        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

                @Override
                public MapCodec<AxeTransformationRecipe> codec() {
                    return CODEC;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, AxeTransformationRecipe> streamCodec() {
                    return streamCodec;
                }
            };

    private final Ingredient input;
    private final ItemStack result;
    private final ItemAbility ability;

    AxeTransformationRecipe(Ingredient input, ItemStack result, ItemAbility ability) {
        this.input = input;
        this.result = result.copy();
        this.ability = ability;
    }

    Ingredient input() {
        return input;
    }

    ItemStack result() {
        return result.copy();
    }

    ItemAbility ability() {
        return ability;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, input);
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(Items.IRON_AXE);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return TYPE;
    }
}
