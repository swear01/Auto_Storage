package com.swearprom.magicstorage.fixture.recipe;

import com.swearprom.magicstorage.magic_storage.MachineEnergyTable;
import com.swearprom.magicstorage.magic_storage.MachineDescriptor;
import com.swearprom.magicstorage.magic_storage.MachineDescriptorApi;
import com.swearprom.magicstorage.magic_storage.MachineVariant;
import com.swearprom.magicstorage.magic_storage.MachineWorkRate;
import com.swearprom.magicstorage.magic_storage.RecipeFamily;
import com.swearprom.magicstorage.magic_storage.RecipeFamilyApi;
import com.swearprom.magicstorage.magic_storage.RecipeFamilyCost;
import com.swearprom.magicstorage.magic_storage.RecipeFamilyFactories;
import com.swearprom.magicstorage.magic_storage.RecipePresentationKind;
import com.swearprom.magicstorage.magic_storage.StorageResourceKind;
import com.swearprom.magicstorage.magic_storage.StorageResourceKindApi;
import com.swearprom.magicstorage.magic_storage.StorageResourceContainerApi;
import com.swearprom.magicstorage.magic_storage.StorageResourceContainerStrategy;
import com.swearprom.magicstorage.magic_storage.StorageResourceBlockApi;
import com.swearprom.magicstorage.magic_storage.StorageResourceBlockStrategy;
import com.swearprom.magicstorage.magic_storage.StorageResourceCapabilities;
import com.swearprom.magicstorage.magic_storage.StorageResourceKey;
import com.swearprom.magicstorage.magic_storage.TypedRecipeInput;
import com.swearprom.magicstorage.magic_storage.TypedRecipeOutput;
import com.swearprom.magicstorage.magic_storage.TypedRecipePlan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.world.level.block.Blocks;

@Mod(FixtureMod.MODID)
public final class FixtureMod {
    public static final String MODID = "magic_storage_recipe_fixture";
    static final ResourceLocation FRACTIONAL_PROCESSOR_ID = ResourceLocation.fromNamespaceAndPath(
            MODID, "fractional_processor");

    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    static final DeferredItem<Item> ENERGY_CELL = ITEMS.register(
            "energy_cell", () -> new Item(new Item.Properties().stacksTo(1)));
    static final DeferredItem<Item> MANA_CELL = ITEMS.register(
            "mana_cell", () -> new Item(new Item.Properties().stacksTo(1)));
    static final DeferredHolder<RecipeType<?>, RecipeType<FixtureGrindingRecipe>> GRINDING_TYPE =
            RECIPE_TYPES.register("grinding", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return MODID + ":grinding";
                }
            });
    static final DeferredHolder<RecipeType<?>, RecipeType<FixtureInfusionRecipe>> INFUSION_TYPE =
            RECIPE_TYPES.register("infusion", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return MODID + ":infusion";
                }
            });
    static final DeferredHolder<RecipeType<?>, RecipeType<FixtureProcessingRecipe>> PROCESSING_TYPE =
            RECIPE_TYPES.register("processing", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return MODID + ":processing";
                }
            });
    private static final DeferredRegister<RecipeFamily> RECIPE_FAMILIES =
            RecipeFamilyApi.createDeferredRegister(MODID);
    private static final DeferredRegister<MachineDescriptor> MACHINE_DESCRIPTORS =
            MachineDescriptorApi.createDeferredRegister(MODID);
    private static final DeferredRegister<StorageResourceKind> RESOURCE_KINDS =
            StorageResourceKindApi.createDeferredRegister(MODID);
    private static final DeferredRegister<StorageResourceContainerStrategy> CONTAINER_STRATEGIES =
            StorageResourceContainerApi.createDeferredRegister(MODID);
    private static final DeferredRegister<StorageResourceBlockStrategy> BLOCK_STRATEGIES =
            StorageResourceBlockApi.createDeferredRegister(MODID);

    static {
        MACHINE_DESCRIPTORS.register("fractional_processor", () ->
                MachineDescriptor.installableVariants(
                        FRACTIONAL_PROCESSOR_ID,
                        () -> java.util.List.of(
                                MachineVariant.of(
                                        new net.minecraft.world.item.ItemStack(
                                                net.minecraft.world.item.Items.COPPER_BLOCK),
                                        MachineWorkRate.of(10, 9)),
                                MachineVariant.of(
                                        new net.minecraft.world.item.ItemStack(
                                                net.minecraft.world.item.Items.IRON_BLOCK),
                                        MachineWorkRate.of(5, 4))),
                        MachineEnergyTable.Category.PROCESS,
                        64,
                        null));
        RECIPE_FAMILIES.register("grinding", () -> RecipeFamilyFactories.singleItemToItem(
                FixtureGrindingRecipe.class,
                GRINDING_TYPE,
                MachineEnergyTable.STONECUTTER_ID,
                recipe -> recipe.getIngredients().getFirst(),
                (recipe, registries) -> recipe.getResultItem(registries),
                recipe -> RecipeFamilyCost.free(),
                RecipePresentationKind.STONECUTTING));
        RECIPE_FAMILIES.register("infusion", () -> RecipeFamilyFactories.deterministicResources(
                FixtureInfusionRecipe.class,
                INFUSION_TYPE,
                MachineEnergyTable.STONECUTTER_ID,
                (recipe, registries) -> TypedRecipePlan.builder()
                        .input(TypedRecipeInput.consume(item("cobblestone", registries), 1))
                        .input(TypedRecipeInput.consume(item("cobblestone", registries), 1))
                        .input(TypedRecipeInput.consumeAnyWithRemainders(
                                java.util.List.of(mana("blue"), mana("green")),
                                1,
                                java.util.Map.of(
                                        mana("blue"), TypedRecipeOutput.remainder(mana("blue"), 25),
                                        mana("green"), TypedRecipeOutput.remainder(mana("yellow"), 25))))
                        .input(TypedRecipeInput.catalyst(mana("red"), 5))
                        .input(TypedRecipeInput.tool(item("iron_pickaxe", registries), 1))
                        .output(TypedRecipeOutput.primary(item("gravel", registries), 3))
                        .output(TypedRecipeOutput.primary(StorageResourceKey.fluid(
                                new FluidStack(Fluids.WATER, 1), registries), 250))
                        .presentationOutput(new net.minecraft.world.item.ItemStack(
                                net.minecraft.world.item.Items.GRAVEL, 3))
                        .layout(3, 2, true)
                        .build(),
                recipe -> RecipeFamilyCost.free(),
                RecipePresentationKind.STONECUTTING));
        RECIPE_FAMILIES.register("processing", () -> RecipeFamilyFactories.singleItemToItem(
                FixtureProcessingRecipe.class,
                PROCESSING_TYPE,
                FRACTIONAL_PROCESSOR_ID,
                recipe -> recipe.getIngredients().getFirst(),
                (recipe, registries) -> recipe.getResultItem(registries),
                recipe -> RecipeFamilyCost.stationWork(10),
                RecipePresentationKind.STONECUTTING));
        RESOURCE_KINDS.register("mana", () -> StorageResourceKind.variantAware(
                () -> new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.AMETHYST_SHARD)));
        CONTAINER_STRATEGIES.register("mana_cell", FixtureManaContainer::strategy);
        BLOCK_STRATEGIES.register("mana", FixtureManaBlockStrategy::new);
    }

    public FixtureMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        RECIPE_FAMILIES.register(modEventBus);
        MACHINE_DESCRIPTORS.register(modEventBus);
        RESOURCE_KINDS.register(modEventBus);
        CONTAINER_STRATEGIES.register(modEventBus);
        BLOCK_STRATEGIES.register(modEventBus);
        modEventBus.addListener(FixtureMod::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                StorageResourceCapabilities.BLOCK,
                (level, pos, state, blockEntity, side) -> side == null
                        ? null
                        : FixtureManaBlockStrategy.handler(level, pos),
                Blocks.BLUE_GLAZED_TERRACOTTA);
        event.registerItem(
                Capabilities.EnergyStorage.ITEM,
                (stack, context) -> new FixtureEnergyStorage(stack),
                ENERGY_CELL.get());
        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> side == null
                        ? null
                        : FixtureNativeBlockStorage.fluid(level, pos),
                Blocks.BLUE_GLAZED_TERRACOTTA);
        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> side == null
                        ? null
                        : FixtureNativeBlockStorage.energy(level, pos),
                Blocks.RED_GLAZED_TERRACOTTA);
    }

    private static StorageResourceKey item(
            String path,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.withDefaultNamespace(path));
        return StorageResourceKey.item(new net.minecraft.world.item.ItemStack(item), registries);
    }

    private static StorageResourceKey mana(String path) {
        return resource(ResourceLocation.fromNamespaceAndPath(MODID, "mana"), MODID, path);
    }

    private static StorageResourceKey resource(
            ResourceLocation kind,
            String namespace,
            String path
    ) {
        return StorageResourceKey.of(
                kind, ResourceLocation.fromNamespaceAndPath(namespace, path), new CompoundTag());
    }
}
