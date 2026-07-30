package example.autostorage;

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineVariant;
import com.swear.autostorage.MachineVariantContributor;
import com.swear.autostorage.MachineVariantContributorApi;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.RecipeFamilyCost;
import com.swear.autostorage.RecipeFamilyFactories;
import com.swear.autostorage.RecipePresentationKind;
import com.swear.autostorage.StorageResourceBlockApi;
import com.swear.autostorage.StorageResourceBlockStrategy;
import com.swear.autostorage.StorageResourceContainerApi;
import com.swear.autostorage.StorageResourceContainerStrategy;
import com.swear.autostorage.StorageResourceHandler;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKind;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.api.AutoStorageAddon;
import com.swear.autostorage.api.AutoStorageApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

@Mod(ExampleAddon.MOD_ID)
public final class ExampleAddon {
    public static final String MOD_ID = "example_auto_storage_addon";
    private static final ResourceLocation STATION_ID = id("crystal_cutter");
    private static final ResourceLocation STARLIGHT_KIND_ID = id("starlight");
    private static final ResourceLocation STARLIGHT_ID = id("stored_starlight");

    private static final DeferredRegister<MachineDescriptor> MACHINES =
            MachineDescriptorApi.createDeferredRegister(MOD_ID);
    private static final DeferredRegister<RecipeFamily> RECIPES =
            RecipeFamilyApi.createDeferredRegister(MOD_ID);
    private static final DeferredRegister<StorageResourceKind> KINDS =
            StorageResourceKindApi.createDeferredRegister(MOD_ID);
    private static final DeferredRegister<StorageResourceContainerStrategy> CONTAINERS =
            StorageResourceContainerApi.createDeferredRegister(MOD_ID);
    private static final DeferredRegister<StorageResourceBlockStrategy> BLOCKS =
            StorageResourceBlockApi.createDeferredRegister(MOD_ID);
    private static final DeferredRegister<TransformProvider> TRANSFORMS =
            TransformProviderApi.createDeferredRegister(MOD_ID);
    private static final DeferredRegister<MachineVariantContributor> VARIANTS =
            MachineVariantContributorApi.createDeferredRegister(MOD_ID);

    static {
        MACHINES.register("crystal_cutter", () -> MachineDescriptor.installable(
                STATION_ID,
                new ItemStack(Items.STONECUTTER),
                Ingredient.of(Items.STONECUTTER),
                MachineCategory.INSTANT,
                1,
                null,
                0));
        RECIPES.register("crystal_cutting", () ->
                RecipeFamilyFactories.singleItemToItem(
                        StonecutterRecipe.class,
                        () -> RecipeType.STONECUTTING,
                        STATION_ID,
                        recipe -> recipe.getIngredients().getFirst(),
                        (recipe, registries) -> recipe.getResultItem(registries),
                        recipe -> RecipeFamilyCost.free(),
                        RecipePresentationKind.STONECUTTING));
        KINDS.register("starlight", () -> StorageResourceKind.variantless(
                () -> new ItemStack(Items.AMETHYST_SHARD)));
        CONTAINERS.register("starlight_container", EmptyContainerStrategy::new);
        BLOCKS.register("starlight_block", EmptyBlockStrategy::new);
        TRANSFORMS.register("glowstone_to_starlight", () -> TransformProvider.of(
                STARLIGHT_KIND_ID,
                new ItemStack(Items.AMETHYST_SHARD),
                Component.literal("Starlight"),
                Component.literal("Glowstone"),
                input -> input.is(Items.GLOWSTONE_DUST)
                        ? new TransformProviderApi.Result(
                        starlight(), 10, null, 0)
                        : null));
        VARIANTS.register("crafter_as_crafting_table", () ->
                MachineVariantContributor.of(
                        AutoStorageApi.id("crafting_table"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(Items.CRAFTER),
                                MachineWorkRate.ZERO))));
    }

    public ExampleAddon(IEventBus modBus) {
        AutoStorageAddon.register(MOD_ID, modBus, addon -> addon
                .machineDescriptors(MACHINES)
                .recipeFamilies(RECIPES)
                .resourceKinds(KINDS)
                .containerStrategies(CONTAINERS)
                .blockStrategies(BLOCKS)
                .transformProviders(TRANSFORMS)
                .machineVariantContributors(VARIANTS));
    }

    private static StorageResourceKey starlight() {
        return StorageResourceKey.of(
                STARLIGHT_KIND_ID, STARLIGHT_ID, new CompoundTag());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static final class EmptyContainerStrategy
            implements StorageResourceContainerStrategy {
        @Override
        public ResourceLocation kindId() {
            return STARLIGHT_KIND_ID;
        }

        @Override
        public Optional<Transfer> planDeposit(
                ItemStack singleContainer,
                HolderLookup.Provider registries
        ) {
            return Optional.empty();
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
    }

    private static final class EmptyBlockStrategy
            implements StorageResourceBlockStrategy {
        @Override
        public ResourceLocation kindId() {
            return STARLIGHT_KIND_ID;
        }

        @Override
        public Optional<StorageResourceHandler> find(
                Level level,
                BlockPos pos,
                Direction side
        ) {
            return Optional.empty();
        }
    }
}
