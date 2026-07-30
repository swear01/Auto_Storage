package com.swear.autostorage.compat.evilcraft;

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineVariant;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.RecipeFamilyCost;
import com.swear.autostorage.RecipeFamilyFactories;
import com.swear.autostorage.RecipePresentationKind;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.TypedRecipeInput;
import com.swear.autostorage.TypedRecipeOutput;
import com.swear.autostorage.TypedRecipePlan;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.cyclops.evilcraft.RegistryEntries;
import org.cyclops.evilcraft.core.recipe.type.RecipeBloodInfuser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class EvilCraftCompat {
    private static final String MOD_ID = "evilcraft";

    private EvilCraftCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("EvilCraft descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("EvilCraft family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "EvilCraft descriptors and families must share one namespace");
        }

        ResourceLocation descriptorId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "evilcraft_blood_infuser");
        machineDescriptors.register(descriptorId.getPath(), () -> {
            Item item = requiredItem("blood_infuser");
            return MachineDescriptor.installableVariants(
                    descriptorId,
                    new ItemStack(item).getHoverName(),
                    () -> List.of(MachineVariant.of(new ItemStack(item), MachineWorkRate.ONE)),
                    MachineCategory.PROCESS,
                    MachineDescriptorApi.MAX_INSTALLED_COUNT,
                    null);
        });
        recipeFamilies.register(descriptorId.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        RecipeBloodInfuser.class,
                        RegistryEntries.RECIPETYPE_BLOOD_INFUSER::get,
                        descriptorId,
                        EvilCraftCompat::supports,
                        EvilCraftCompat::plan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.getDuration()),
                        RecipePresentationKind.CRAFTING));
    }

    private static boolean supports(RecipeBloodInfuser recipe) {
        int tier = recipe.getInputTier().orElse(0);
        ItemStack output = recipe.getOutputItem().left().orElse(ItemStack.EMPTY);
        return recipe.getDuration() > 0
                && Float.compare(recipe.getXp().orElse(0F), 0F) == 0
                && tier >= 0
                && tier <= 3
                && (recipe.getInputIngredient().isPresent()
                || recipe.getInputFluid().isPresent())
                && recipe.getInputIngredient().map(EvilCraftCompat::exact).orElse(true)
                && recipe.getInputFluid().map(EvilCraftCompat::exact).orElse(true)
                && !output.isEmpty()
                && inputCount(recipe) <= 3
                && promiseDoesNotOverlapInput(recipe);
    }

    private static TypedRecipePlan plan(
            RecipeBloodInfuser recipe,
            HolderLookup.Provider registries
    ) {
        List<TypedRecipeInput> inputs = new ArrayList<>();
        recipe.getInputIngredient().ifPresent(ingredient -> inputs.add(
                TypedRecipeInput.consumeAny(
                        representatives(ingredient).stream()
                                .map(stack -> StorageResourceKey.item(stack, registries))
                                .distinct()
                                .toList(),
                        1)));
        recipe.getInputFluid().ifPresent(fluid -> inputs.add(
                TypedRecipeInput.consume(
                        StorageResourceKey.fluid(fluid.copyWithAmount(1), registries),
                        fluid.getAmount())));
        int tier = recipe.getInputTier().orElse(0);
        if (tier > 0) {
            inputs.add(TypedRecipeInput.catalystAny(
                    java.util.stream.IntStream.rangeClosed(tier, 3)
                            .mapToObj(value -> StorageResourceKey.item(
                                    new ItemStack(requiredItem("promise_tier_" + value)),
                                    registries))
                            .toList(),
                    1));
        }

        ItemStack output = recipe.getOutputItem().left().orElseThrow().copy();
        StorageResourceKey outputKey = StorageResourceKey.item(
                output.copyWithCount(1), registries);
        int width = Math.min(3, inputs.size());
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        inputs.forEach(builder::input);
        return builder
                .presentationOutput(output)
                .layout(width, (inputs.size() + width - 1) / width, true)
                .output(TypedRecipeOutput.primary(outputKey, output.getCount()))
                .build();
    }

    private static int inputCount(RecipeBloodInfuser recipe) {
        return (recipe.getInputIngredient().isPresent() ? 1 : 0)
                + (recipe.getInputFluid().isPresent() ? 1 : 0)
                + (recipe.getInputTier().orElse(0) > 0 ? 1 : 0);
    }

    private static boolean promiseDoesNotOverlapInput(RecipeBloodInfuser recipe) {
        int tier = recipe.getInputTier().orElse(0);
        if (tier <= 0 || recipe.getInputIngredient().isEmpty()) return true;
        List<ItemStack> inputs = representatives(recipe.getInputIngredient().orElseThrow());
        return java.util.stream.IntStream.rangeClosed(tier, 3)
                .mapToObj(value -> requiredItem("promise_tier_" + value))
                .noneMatch(promise -> inputs.stream().anyMatch(stack -> stack.is(promise)));
    }

    private static boolean exact(Ingredient ingredient) {
        return !ingredient.isEmpty()
                && ingredient.isSimple()
                && !representatives(ingredient).isEmpty();
    }

    private static boolean exact(FluidStack fluid) {
        return !fluid.isEmpty() && fluid.getAmount() > 0;
    }

    private static List<ItemStack> representatives(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .distinct()
                .toList();
    }

    private static Item requiredItem(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing EvilCraft item " + id);
        }
        return item;
    }
}
