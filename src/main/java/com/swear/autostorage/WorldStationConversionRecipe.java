package com.swear.autostorage;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public final class WorldStationConversionRecipe implements Recipe<SingleRecipeInput> {
    public static final RecipeType<WorldStationConversionRecipe> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return AutoStorage.MODID + ":world_station_conversion";
        }
    };
    private static final MapCodec<WorldStationConversionRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("input")
                            .forGetter(WorldStationConversionRecipe::input),
                    ItemStack.STRICT_CODEC.fieldOf("result")
                            .forGetter(WorldStationConversionRecipe::result),
                    ResourceLocation.CODEC.fieldOf("station")
                            .forGetter(WorldStationConversionRecipe::stationDescriptorId)
            ).apply(instance, WorldStationConversionRecipe::new));
    private static final RecipeSerializer<WorldStationConversionRecipe> SERIALIZER =
            new RecipeSerializer<>() {
                private final StreamCodec<RegistryFriendlyByteBuf, WorldStationConversionRecipe> streamCodec =
                        ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

                @Override
                public MapCodec<WorldStationConversionRecipe> codec() {
                    return CODEC;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, WorldStationConversionRecipe> streamCodec() {
                    return streamCodec;
                }
            };

    private final Ingredient input;
    private final ItemStack result;
    private final ResourceLocation stationDescriptorId;

    public WorldStationConversionRecipe(
            Ingredient input,
            ItemStack result,
            ResourceLocation stationDescriptorId
    ) {
        this.input = input;
        this.result = result.copy();
        this.stationDescriptorId = stationDescriptorId;
    }

    public Ingredient input() {
        return input;
    }

    public ItemStack result() {
        return result.copy();
    }

    public ResourceLocation stationDescriptorId() {
        return stationDescriptorId;
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
        return new ItemStack(Items.REDSTONE);
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
