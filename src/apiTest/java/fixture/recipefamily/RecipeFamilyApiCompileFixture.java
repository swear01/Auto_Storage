package fixture.recipefamily;

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.AutoStorageCapabilityApi;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineVariant;
import com.swear.autostorage.MachineVariantContributor;
import com.swear.autostorage.MachineVariantContributorApi;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.EnergyCost;
import com.swear.autostorage.EnergyType;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.RecipeFamilyCost;
import com.swear.autostorage.RecipeFamilyFactories;
import com.swear.autostorage.RecipePresentationKind;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKind;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.StorageResourceCapabilities;
import com.swear.autostorage.StorageResourceBlockApi;
import com.swear.autostorage.StorageResourceBlockStrategy;
import com.swear.autostorage.BusFilterRule;
import com.swear.autostorage.StorageResourceContainerApi;
import com.swear.autostorage.StorageResourceContainerStrategy;
import com.swear.autostorage.StorageResourceHandler;
import com.swear.autostorage.StorageResourceTransaction;
import com.swear.autostorage.TerminalResourceRendererApi;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.TypedRecipeInput;
import com.swear.autostorage.TypedRecipeOutput;
import com.swear.autostorage.TypedRecipePlan;
import com.swear.autostorage.api.AutoStorageAddon;
import com.swear.autostorage.api.AutoStorageApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.util.List;
import java.util.Optional;

public final class RecipeFamilyApiCompileFixture {
    private static final BlockCapability<StorageResourceHandler, Direction>
            RESOURCE_CAPABILITY = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("fixture_mod", "resource"),
            StorageResourceHandler.class);
    private RecipeFamilyApiCompileFixture() {
    }

    public static RecipeFamily create() {
        return RecipeFamilyFactories.singleItemToItem(
                StonecutterRecipe.class,
                () -> RecipeType.STONECUTTING,
                AutoStorageApi.id("stonecutter"),
                recipe -> recipe.getIngredients().getFirst(),
                (recipe, registries) -> recipe.getResultItem(registries),
                recipe -> RecipeFamilyCost.free(),
                RecipePresentationKind.STONECUTTING);
    }

    public static RecipeFamily createTyped() {
        return RecipeFamilyFactories.deterministicResources(
                StonecutterRecipe.class,
                () -> RecipeType.STONECUTTING,
                AutoStorageApi.id("stonecutter"),
                (recipe, registries) -> TypedRecipePlan.builder()
                        .input(TypedRecipeInput.consume(resource("mana", "blue"), 100))
                        .input(TypedRecipeInput.consumeAnyWithRemainders(
                                List.of(
                                        resource("mana", "red"),
                                        resource("mana", "green")),
                                1,
                                java.util.Map.of(
                                        resource("mana", "red"),
                                        TypedRecipeOutput.remainder(resource("mana", "blue"), 1))))
                        .input(TypedRecipeInput.catalyst(resource("item", "diamond"), 1))
                        .input(TypedRecipeInput.tool(resource("item", "iron_pickaxe"), 1))
                        .output(TypedRecipeOutput.primary(resource("item", "redstone"), 2))
                        .output(TypedRecipeOutput.remainder(resource("mana", "blue"), 25))
                        .presentationOutput(new ItemStack(Items.REDSTONE, 2))
                        .layout(2, 2, false)
                        .build(),
                recipe -> RecipeFamilyCost.free(),
                RecipePresentationKind.STONECUTTING);
    }

    public static RecipeFamily createConditionalTyped() {
        return RecipeFamilyFactories.deterministicResources(
                StonecutterRecipe.class,
                () -> RecipeType.STONECUTTING,
                AutoStorageApi.id("stonecutter"),
                recipe -> !recipe.getGroup().isEmpty(),
                (recipe, registries) -> TypedRecipePlan.builder()
                        .input(TypedRecipeInput.consume(resource("item", "stone"), 1))
                        .output(TypedRecipeOutput.primary(resource("item", "stone_bricks"), 1))
                        .presentationOutput(new ItemStack(Items.STONE_BRICKS))
                        .layout(1, 1, false)
                        .build(),
                recipe -> RecipeFamilyCost.free(),
                RecipePresentationKind.STONECUTTING);
    }

    public static RecipeFamily createDynamicTyped() {
        return RecipeFamilyFactories.dynamicDeterministicResources(
                StonecutterRecipe.class,
                () -> RecipeType.STONECUTTING,
                AutoStorageApi.id("stonecutter"),
                recipe -> !recipe.getGroup().isEmpty(),
                (recipe, registries) -> TypedRecipePlan.builder()
                        .input(TypedRecipeInput.consume(resource("mana", "blue"), 100))
                        .output(TypedRecipeOutput.primary(resource("item", "redstone"), 2))
                        .presentationOutput(new ItemStack(Items.REDSTONE, 2))
                        .layout(1, 1, false)
                        .build(),
                recipe -> RecipeFamilyCost.free(),
                () -> 1L,
                RecipePresentationKind.STONECUTTING);
    }

    public static DeferredRegister<RecipeFamily> register() {
        DeferredRegister<RecipeFamily> families = RecipeFamilyApi.createDeferredRegister("fixture_mod");
        families.register("stonecutting", RecipeFamilyApiCompileFixture::create);
        return families;
    }

    public static void wireAddon(IEventBus modBus) {
        DeferredRegister<MachineDescriptor> machines =
                MachineDescriptorApi.createDeferredRegister("fixture_mod");
        DeferredRegister<RecipeFamily> recipes =
                RecipeFamilyApi.createDeferredRegister("fixture_mod");
        DeferredRegister<StorageResourceKind> kinds =
                StorageResourceKindApi.createDeferredRegister("fixture_mod");
        DeferredRegister<StorageResourceContainerStrategy> containers =
                StorageResourceContainerApi.createDeferredRegister("fixture_mod");
        DeferredRegister<StorageResourceBlockStrategy> blocks =
                StorageResourceBlockApi.createDeferredRegister("fixture_mod");
        DeferredRegister<TransformProvider> transforms =
                TransformProviderApi.createDeferredRegister("fixture_mod");
        DeferredRegister<MachineVariantContributor> variants =
                MachineVariantContributorApi.createDeferredRegister("fixture_mod");

        AutoStorageAddon.register("fixture_mod", modBus, addon -> addon
                .machineDescriptors(machines)
                .recipeFamilies(recipes)
                .resourceKinds(kinds)
                .containerStrategies(containers)
                .blockStrategies(blocks)
                .transformProviders(transforms)
                .machineVariantContributors(variants)
                .capabilities(event -> AutoStorageCapabilityApi.registerSidedResourceCapability(
                        event,
                        RESOURCE_CAPABILITY,
                        (resources, side) -> resources))
                .recipeReload(
                        ResourceLocation.fromNamespaceAndPath("fixture_mod", "recipes"),
                        () -> {
                        }));
        TerminalResourceRendererApi.register(
                ResourceLocation.fromNamespaceAndPath("fixture_mod", "mana"),
                Object.class,
                (context, key, amount, x, y, partialTick) -> false);
    }

    public static MachineDescriptor polymorphicStation() {
        return MachineDescriptor.installableVariants(
                ResourceLocation.fromNamespaceAndPath("fixture_mod", "polymorphic_station"),
                net.minecraft.network.chat.Component.literal("Polymorphic Station"),
                () -> List.of(
                        MachineVariant.of(new ItemStack(Items.COPPER_BLOCK), MachineWorkRate.of(10, 9)),
                        MachineVariant.of(new ItemStack(Items.IRON_BLOCK), MachineWorkRate.of(5, 4))),
                MachineCategory.PROCESS,
                64,
                null);
    }

    public static RecipeFamilyCost stationWorkCost() {
        return RecipeFamilyCost.stationWorkAndEnergy(
                200,
                new EnergyCost(
                        EnergyType.SMELTING_ENERGY,
                        0,
                        EnergyType.FURNACE_FUEL,
                        200));
    }

    public static DeferredRegister<StorageResourceKind> registerResourceKinds() {
        DeferredRegister<StorageResourceKind> kinds =
                StorageResourceKindApi.createDeferredRegister("fixture_mod");
        kinds.register("mana", () -> StorageResourceKind.variantAware(
                () -> new ItemStack(Items.AMETHYST_SHARD)));
        return kinds;
    }

    public static DeferredRegister<StorageResourceContainerStrategy> registerContainerStrategies() {
        DeferredRegister<StorageResourceContainerStrategy> strategies =
                StorageResourceContainerApi.createDeferredRegister("fixture_mod");
        strategies.register("mana_cell", RecipeFamilyApiCompileFixture::createContainerStrategy);
        return strategies;
    }

    public static StorageResourceContainerStrategy createContainerStrategy() {
        return new StorageResourceContainerStrategy() {
            @Override
            public ResourceLocation kindId() {
                return ResourceLocation.fromNamespaceAndPath("fixture_mod", "mana");
            }

            @Override
            public Optional<Transfer> planDeposit(
                    ItemStack singleContainer,
                    HolderLookup.Provider registries
            ) {
                return Optional.of(new Transfer(
                        resource("mana", "blue"),
                        100,
                        new ItemStack(Items.GLASS_BOTTLE)));
            }

            @Override
            public Optional<Transfer> planWithdraw(
                    ItemStack singleContainer,
                    StorageResourceKey key,
                    long maxAmount,
                    HolderLookup.Provider registries
            ) {
                return Optional.empty();
            }
        };
    }

    public static DeferredRegister<StorageResourceBlockStrategy> registerBlockStrategies() {
        DeferredRegister<StorageResourceBlockStrategy> strategies =
                StorageResourceBlockApi.createDeferredRegister("fixture_mod");
        strategies.register("mana", RecipeFamilyApiCompileFixture::createBlockStrategy);
        return strategies;
    }

    public static StorageResourceBlockStrategy createBlockStrategy() {
        return new StorageResourceBlockStrategy() {
            @Override
            public ResourceLocation kindId() {
                return ResourceLocation.fromNamespaceAndPath("fixture_mod", "mana");
            }

            @Override
            public Optional<StorageResourceHandler> find(
                    Level level,
                    BlockPos pos,
                    Direction side
            ) {
                return Optional.of(resourceHandler());
            }
        };
    }

    public static BusFilterRule typedBusFilterRule() {
        return BusFilterRule.resource(resource("mana", "blue"));
    }

    public static StorageResourceTransaction typedTransaction() {
        StorageResourceKey mana = StorageResourceKey.of(
                ResourceLocation.fromNamespaceAndPath("fixture_mod", "mana"),
                ResourceLocation.fromNamespaceAndPath("fixture_mod", "blue"),
                new CompoundTag());
        StorageResourceKey dust = StorageResourceKey.of(
                StorageResourceKindApi.ITEM_KIND,
                ResourceLocation.fromNamespaceAndPath("minecraft", "redstone"),
                new CompoundTag());
        return StorageResourceTransaction.builder()
                .add(mana, -100)
                .add(dust, 1)
                .build();
    }

    public static StorageResourceHandler resourceHandler() {
        return new StorageResourceHandler() {
            @Override
            public List<StorageResourceKey> getStoredResources() {
                return List.of();
            }

            @Override
            public long getAmount(StorageResourceKey key) {
                return 0;
            }

            @Override
            public long insert(StorageResourceKey key, long amount, boolean simulate) {
                return 0;
            }

            @Override
            public long extract(StorageResourceKey key, long amount, boolean simulate) {
                return 0;
            }
        };
    }

    public static Object resourceBlockCapability() {
        return StorageResourceCapabilities.BLOCK;
    }

    public static List<StorageResourceKey> canonicalBuiltInKeys(HolderLookup.Provider registries) {
        StorageResourceKey item = StorageResourceKey.item(
                new ItemStack(Items.DIAMOND), registries);
        StorageResourceKey fluid = StorageResourceKey.fluid(
                new FluidStack(Fluids.WATER, 1), registries);
        item.itemStack(registries).orElseThrow();
        fluid.fluidStack(1, registries).orElseThrow();
        return List.of(item, fluid, StorageResourceKey.neoforgeEnergy());
    }

    private static StorageResourceKey resource(String kind, String path) {
        ResourceLocation kindId = kind.equals("item")
                ? StorageResourceKindApi.ITEM_KIND
                : ResourceLocation.fromNamespaceAndPath("fixture_mod", kind);
        String namespace = kind.equals("item") ? "minecraft" : "fixture_mod";
        return StorageResourceKey.of(
                kindId,
                ResourceLocation.fromNamespaceAndPath(namespace, path),
                new CompoundTag());
    }
}
