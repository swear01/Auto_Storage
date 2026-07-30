package com.swear.autostorage;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class OptionalModRecipeCompatibility {
    private static final String IRON_FURNACES_MOD_ID = "ironfurnaces";
    private static final String IRON_FURNACES_CLASS =
            "com.swear.autostorage.compat.ironfurnaces.IronFurnacesCompat";
    private static final String FARMERS_DELIGHT_MOD_ID = "farmersdelight";
    private static final String FARMERS_DELIGHT_CLASS =
            "com.swear.autostorage.compat.farmersdelight.FarmersDelightCookingPotCompat";
    private static final String MEKANISM_MOD_ID = "mekanism";
    private static final String MEKANISM_CLASS =
            "com.swear.autostorage.MekanismRecipeCompat";
    private static final String BOTANIA_MOD_ID = "botania";
    private static final String BOTANIA_COMPAT_CLASS =
            "com.swear.autostorage.compat.botania.BotaniaCompat";
    private static final String MODERN_INDUSTRIALIZATION_MOD_ID = "modern_industrialization";
    private static final String MODERN_INDUSTRIALIZATION_COMPAT_CLASS =
            "com.swear.autostorage.compat.modernindustrialization.ModernIndustrializationCompat";
    private static final String ARS_NOUVEAU_MOD_ID = "ars_nouveau";
    private static final String ARS_NOUVEAU_COMPAT_CLASS =
            "com.swear.autostorage.compat.arsnouveau.ArsNouveauCompat";
    private static final String EVILCRAFT_MOD_ID = "evilcraft";
    private static final String EVILCRAFT_COMPAT_CLASS =
            "com.swear.autostorage.compat.evilcraft.EvilCraftCompat";
    private static final String POWAH_MOD_ID = "powah";
    private static final String POWAH_COMPAT_CLASS =
            "com.swear.autostorage.compat.powah.PowahCompat";
    private static final String INDUSTRIAL_FOREGOING_MOD_ID = "industrialforegoing";
    private static final String INDUSTRIAL_FOREGOING_COMPAT_CLASS =
            "com.swear.autostorage.compat.industrialforegoing.IndustrialForegoingCompat";
    private static final String CREATE_MOD_ID = "create";
    private static final String CREATE_COMPAT_CLASS =
            "com.swear.autostorage.compat.create.CreateCompat";
    private static final String EXTENDED_CRAFTING_MOD_ID = "extendedcrafting";
    private static final String EXTENDED_CRAFTING_COMPAT_CLASS =
            "com.swear.autostorage.compat.extendedcrafting.ExtendedCraftingCompat";

    private OptionalModRecipeCompatibility() {
    }

    static void register() {
        if (ModList.get().isLoaded(IRON_FURNACES_MOD_ID)) {
            MachineVariantContributors.register(
                    MachineEnergyTable.FURNACE_ID,
                    IRON_FURNACES_MOD_ID,
                    () -> invokeVariants(
                            IRON_FURNACES_MOD_ID,
                            "Iron Furnaces station compatibility",
                            IRON_FURNACES_CLASS,
                            "furnaceVariants"));
        }
        if (ModList.get().isLoaded(FARMERS_DELIGHT_MOD_ID)) {
            invokeRegistrar(
                    FARMERS_DELIGHT_MOD_ID,
                    "Farmer's Delight Cooking Pot compatibility",
                    FARMERS_DELIGHT_CLASS,
                    "register");
        }
        if (ModList.get().isLoaded(MEKANISM_MOD_ID)) {
            StorageResourceKinds.registerChemical(AutoStorage.RESOURCE_KINDS);
            invokeRegistrar(
                    MEKANISM_MOD_ID,
                    "Mekanism recipe compatibility",
                    MEKANISM_CLASS,
                    "register");
        }
        if (ModList.get().isLoaded(BOTANIA_MOD_ID)) {
            StorageResourceKinds.registerBotaniaMana(AutoStorage.RESOURCE_KINDS);
            invokeRegistrar(
                    BOTANIA_MOD_ID,
                    "Botania Mana and recipe compatibility",
                    BOTANIA_COMPAT_CLASS,
                    "register");
        }
        if (ModList.get().isLoaded(MODERN_INDUSTRIALIZATION_MOD_ID)) {
            invokeRegistrar(
                    MODERN_INDUSTRIALIZATION_MOD_ID,
                    "Modern Industrialization recipe compatibility",
                    MODERN_INDUSTRIALIZATION_COMPAT_CLASS,
                    "register");
        }
        if (ModList.get().isLoaded(ARS_NOUVEAU_MOD_ID)) {
            StorageResourceKinds.registerArsNouveauSource(AutoStorage.RESOURCE_KINDS);
            invokeRegistrar(
                    ARS_NOUVEAU_MOD_ID,
                    "Ars Nouveau Source and recipe compatibility",
                    ARS_NOUVEAU_COMPAT_CLASS,
                    "register");
        }
        if (ModList.get().isLoaded(EVILCRAFT_MOD_ID)) {
            invokeRegistrar(
                    EVILCRAFT_MOD_ID,
                    "EvilCraft Blood Infuser compatibility",
                    EVILCRAFT_COMPAT_CLASS,
                    "register");
        }
        if (ModList.get().isLoaded(POWAH_MOD_ID)) {
            invokeRegistrar(
                    POWAH_MOD_ID,
                    "Powah Energizing compatibility",
                    POWAH_COMPAT_CLASS,
                    "register");
        }
        if (ModList.get().isLoaded(INDUSTRIAL_FOREGOING_MOD_ID)) {
            invokeRegistrar(
                    INDUSTRIAL_FOREGOING_MOD_ID,
                    "Industrial Foregoing recipe compatibility",
                    INDUSTRIAL_FOREGOING_COMPAT_CLASS,
                    "register");
        }
        if (ModList.get().isLoaded(CREATE_MOD_ID)) {
            invokeRegistrar(
                    CREATE_MOD_ID,
                    "Create recipe compatibility",
                    CREATE_COMPAT_CLASS,
                    "register");
        }
        if (ModList.get().isLoaded(EXTENDED_CRAFTING_MOD_ID)) {
            invokeRegistrar(
                    EXTENDED_CRAFTING_MOD_ID,
                    "Extended Crafting table compatibility",
                    EXTENDED_CRAFTING_COMPAT_CLASS,
                    "register");
        }
    }

    static void refreshRuntimeData() {
        if (ModList.get().isLoaded(EXTENDED_CRAFTING_MOD_ID)) {
            invokeNoArg(
                    EXTENDED_CRAFTING_MOD_ID,
                    "Extended Crafting runtime data",
                    EXTENDED_CRAFTING_COMPAT_CLASS,
                    "refreshRuntimeData");
        }
    }

    private static void invokeRegistrar(
            String modId,
            String module,
            String className,
            String methodName
    ) {
        try {
            Class.forName(className)
                    .getDeclaredMethod(methodName, DeferredRegister.class, DeferredRegister.class)
                    .invoke(null, AutoStorage.MACHINE_DESCRIPTORS, AutoStorage.RECIPE_FAMILIES);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Failed to load " + module + " for loaded mod " + modId, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof LinkageError error) {
                throw new IllegalStateException(
                        module + " is binary-incompatible with loaded mod " + modId, error);
            }
            throw new IllegalStateException(
                    module + " failed for loaded mod " + modId, cause);
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    module + " is binary-incompatible with loaded mod " + modId, error);
        }
    }

    private static List<MachineVariant> invokeVariants(
            String modId,
            String module,
            String className,
            String methodName
    ) {
        try {
            Method method = Class.forName(className).getDeclaredMethod(methodName);
            Object result = method.invoke(null);
            if (!(result instanceof List<?> raw)) {
                throw new IllegalStateException(module + " returned a non-list variant result");
            }
            List<MachineVariant> variants = new ArrayList<>(raw.size());
            for (Object entry : raw) {
                if (!(entry instanceof MachineVariant variant)) {
                    throw new IllegalStateException(
                            module + " returned a non-variant entry for " + modId);
                }
                variants.add(variant);
            }
            return List.copyOf(variants);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Failed to load " + module + " for loaded mod " + modId, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof LinkageError error) {
                throw new IllegalStateException(
                        module + " is binary-incompatible with loaded mod " + modId, error);
            }
            throw new IllegalStateException(
                    module + " failed for loaded mod " + modId, cause);
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    module + " is binary-incompatible with loaded mod " + modId, error);
        }
    }

    private static void invokeNoArg(
            String modId,
            String module,
            String className,
            String methodName
    ) {
        try {
            Class.forName(className).getDeclaredMethod(methodName).invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Failed to load " + module + " for loaded mod " + modId, exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(
                    module + " failed for loaded mod " + modId, exception.getCause());
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    module + " is binary-incompatible with loaded mod " + modId, error);
        }
    }
}
