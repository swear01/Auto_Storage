package com.swear.autostorage;

import com.swear.autostorage.api.AutoStorageApi;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.chemical.attribute.ChemicalFuel;
import mekanism.api.recipes.ItemStackToChemicalRecipe;
import mekanism.api.recipes.ItemStackToEnergyRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MekanismTransformCompat {
    private MekanismTransformCompat() {
    }

    private static final long MAX_GAS_BURN_RATE = 256;
    private static final long REDSTONE_DUST_FE = 10_000;
    private static final long REDSTONE_BLOCK_FE = 90_000;
    private static final TagKey<Item> REDSTONE_DUST_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "dusts/redstone"));
    private static final TagKey<Item> REDSTONE_BLOCK_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "storage_blocks/redstone"));

    static {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                MekanismTransformCompat::onServerStarted);
    }

    private static void onServerStarted(
            net.neoforged.neoforge.event.server.ServerStartedEvent event
    ) {
        ConversionShapePattern.rebuild(event.getServer());
    }

    private static final ConversionShapePattern SHAPE_PATTERN =
            new ConversionShapePattern();
    private static final GasFuelPattern GAS_PATTERN = new GasFuelPattern();
    private static final ResourceLocation gasProviderId =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorageApi.MOD_ID, "mekanism_gas_generator");

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<TransformProvider> transforms
    ) {
        ConversionScanner.register(SHAPE_PATTERN);
        transforms.register("mekanism_chemical_conversion", () ->
                TransformProvider.of(
                        StorageResourceKindApi.CHEMICAL_KIND,
                        new ItemStack(Items.GLOWSTONE_DUST),
                        Component.translatable(
                                "gui.auto_storage.resource_view.chemical"),
                        Component.translatable(
                                "gui.auto_storage.source.mekanism_chemical_conversion"),
                        SHAPE_PATTERN::resolveChemical));
        transforms.register("mekanism_energy_conversion", () ->
                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.source.mekanism_energy_conversion"),
                        SHAPE_PATTERN::resolveEnergy));
        ConversionScanner.register(GAS_PATTERN);
        transforms.register(gasProviderId.getPath(), () ->
                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.station.mekanism_gas_generator"),
                        GAS_PATTERN::resolve));
        ResourceLocation generatorId = ResourceLocation.fromNamespaceAndPath(
                AutoStorageApi.MOD_ID, "mekanism_gas_generator");
        machines.register(generatorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        generatorId,
                        Component.translatable(
                                "gui.auto_storage.station.mekanism_gas_generator"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "mekanismgenerators",
                                                "gas_burning_generator"))),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
    }

    private static final class ConversionShapePattern
            implements com.swear.autostorage.ConversionPattern {
        private static final Map<Item, ChemicalStack> CHEMICAL_OUTPUTS =
                new HashMap<>();
        private static final Map<Item, Long> ENERGY_OUTPUTS = new HashMap<>();
        private static String revision = "";

        @Override
        public ResourceLocation patternId() {
            return ResourceLocation.fromNamespaceAndPath(
                    "mekanism", "conversion_shapes");
        }

        @Override
        public TransformProviderApi.Result resolve(ItemStack input) {
            TransformProviderApi.Result result = resolveChemical(input);
            if (result != null) return result;
            return resolveEnergy(input);
        }

        TransformProviderApi.Result resolveChemical(ItemStack input) {
            if (input == null || input.isEmpty() || input.getCount() != 1) {
                return null;
            }
            ChemicalStack output = CHEMICAL_OUTPUTS.get(input.getItem());
            if (output == null || output.isEmpty() || output.getAmount() <= 0) {
                return null;
            }
            return new TransformProviderApi.Result(
                    MekanismChemicalCompat.key(output),
                    output.getAmount(),
                    null,
                    0);
        }

        TransformProviderApi.Result resolveEnergy(ItemStack input) {
            if (input == null || input.isEmpty() || input.getCount() != 1) {
                return null;
            }
            Long energy = ENERGY_OUTPUTS.get(input.getItem());
            if (energy == null || energy <= 0) {
                return null;
            }
            return new TransformProviderApi.Result(
                    StorageResourceKey.neoforgeEnergy(),
                    energy,
                    null,
                    0);
        }

        @Override
        public String revisionKey() {
            return revision;
        }

        static void rebuild(net.minecraft.server.MinecraftServer server) {
            Map<Item, ChemicalStack> chemicals = new HashMap<>();
            Map<Item, Long> energies = new HashMap<>();
            StringBuilder digest = new StringBuilder();
            for (RecipeHolder<?> holder :
                    server.getRecipeManager().getRecipes()) {
                if (holder.value()
                        instanceof ItemStackToChemicalRecipe recipe) {
                    List<ItemStack> inputs =
                            recipe.getInput().getRepresentations();
                    List<ChemicalStack> outputs =
                            recipe.getOutputDefinition();
                    if (inputs.size() != 1 || outputs.size() != 1) {
                        continue;
                    }
                    ItemStack input = inputs.getFirst();
                    ChemicalStack output = outputs.getFirst();
                    if (input.isEmpty() || input.getCount() != 1
                            || output.isEmpty() || output.getAmount() <= 0) {
                        continue;
                    }
                    chemicals.put(input.getItem(), output);
                    digest.append(holder.id()).append('=')
                            .append(recipe.getType()).append('/')
                            .append(output.getAmount()).append(';');
                } else if (holder.value()
                        instanceof ItemStackToEnergyRecipe recipe) {
                    List<ItemStack> inputs =
                            recipe.getInput().getRepresentations();
                    long[] outputs = recipe.getOutputDefinition();
                    if (inputs.size() != 1 || outputs.length != 1) {
                        continue;
                    }
                    ItemStack input = inputs.getFirst();
                    long output = outputs[0];
                    if (input.isEmpty() || input.getCount() != 1
                            || output <= 0) {
                        continue;
                    }
                    energies.put(input.getItem(), output);
                    digest.append(holder.id()).append('=')
                            .append(output).append(';');
                }
            }
            CHEMICAL_OUTPUTS.clear();
            CHEMICAL_OUTPUTS.putAll(chemicals);
            ENERGY_OUTPUTS.clear();
            ENERGY_OUTPUTS.putAll(energies);
            revision = digest.toString();
        }
    }

    private static final class GasFuelPattern
            implements com.swear.autostorage.ConversionPattern {
        private static String revision = "";

        @Override
        public ResourceLocation patternId() {
            return ResourceLocation.fromNamespaceAndPath(
                    "mekanism", "chemical_fuel");
        }

        @Override
        public TransformProviderApi.Result resolve(ItemStack input) {
            if (input == null || input.isEmpty()) return null;
            IChemicalHandler handler = input.getCapability(
                    MekanismChemicalCompat.CHEMICAL_ITEM_CAPABILITY);
            if (handler == null || handler.getChemicalTanks() <= 0) return null;
            ChemicalStack contents = handler.getChemicalInTank(0);
            if (contents == null || contents.isEmpty()
                    || contents.getAmount() <= 0) {
                return null;
            }
            ChemicalFuel fuel = contents.getData(
                    IMekanismDataMapTypes.INSTANCE.chemicalFuel());
            if (fuel == null || fuel.burnTicks() <= 0
                    || fuel.energyPerTick() <= 0) {
                return null;
            }
            long amount = contents.getAmount();
            long work = (amount + MAX_GAS_BURN_RATE - 1) / MAX_GAS_BURN_RATE;
            long fe;
            try {
                fe = Math.multiplyExact(amount, fuel.energyDensity());
            } catch (ArithmeticException exception) {
                return null;
            }
            return new TransformProviderApi.Result(
                    StorageResourceKey.neoforgeEnergy(),
                    fe,
                    gasProviderId,
                    work,
                    List.of(new ItemStack(input.getItem())));
        }

        @Override
        public String revisionKey() {
            return revision;
        }

        static void rebuild(net.minecraft.server.MinecraftServer server) {
            StringBuilder digest = new StringBuilder();
            for (Item item : BuiltInRegistries.ITEM) {
                net.minecraft.world.item.ItemStack stack =
                        new net.minecraft.world.item.ItemStack(item);
                IChemicalHandler handler = stack.getCapability(
                        MekanismChemicalCompat.CHEMICAL_ITEM_CAPABILITY);
                if (handler == null || handler.getChemicalTanks() <= 0) {
                    continue;
                }
                ChemicalStack contents = handler.getChemicalInTank(0);
                if (contents == null || contents.isEmpty()) continue;
                ChemicalFuel fuel = contents.getData(
                        IMekanismDataMapTypes.INSTANCE.chemicalFuel());
                if (fuel != null) {
                    digest.append(item).append('=')
                            .append(fuel.burnTicks()).append('/')
                            .append(fuel.energyPerTick()).append(';');
                }
            }
            revision = digest.toString();
        }
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException(
                    "Loaded Mekanism Generators did not register " + id);
        }
        return item;
    }
}
