package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

final class OptionalModContainerStrategies {
    private static final String MEKANISM_MOD_ID = "mekanism";
    private static final String MEKANISM_COMPAT_CLASS =
            "com.swearprom.magicstorage.magic_storage.MekanismChemicalCompat";
    private static final String BOTANIA_MOD_ID = "botania";
    private static final String BOTANIA_COMPAT_CLASS =
            "com.swearprom.magicstorage.magic_storage.compat.botania.BotaniaCompat";
    private static Method depositMethod;
    private static Method withdrawMethod;
    private static Method botaniaDepositMethod;
    private static Method botaniaWithdrawMethod;

    private OptionalModContainerStrategies() {
    }

    static Optional<StorageResourceContainerStrategy.Transfer> planMekanismChemicalDeposit(
            ItemStack singleContainer,
            HolderLookup.Provider registries
    ) {
        if (!ModList.get().isLoaded(MEKANISM_MOD_ID)) return Optional.empty();
        return invoke(depositMethod(), singleContainer, registries);
    }

    static Optional<StorageResourceContainerStrategy.Transfer> planMekanismChemicalWithdraw(
            ItemStack singleContainer,
            StorageResourceKey key,
            long maxAmount,
            HolderLookup.Provider registries
    ) {
        if (!ModList.get().isLoaded(MEKANISM_MOD_ID)) return Optional.empty();
        return invoke(withdrawMethod(), singleContainer, key, maxAmount, registries);
    }

    static Optional<StorageResourceContainerStrategy.Transfer> planBotaniaManaDeposit(
            ItemStack singleContainer,
            HolderLookup.Provider registries
    ) {
        if (!ModList.get().isLoaded(BOTANIA_MOD_ID)) return Optional.empty();
        return invokeBotania(botaniaDepositMethod(), singleContainer, registries);
    }

    static Optional<StorageResourceContainerStrategy.Transfer> planBotaniaManaWithdraw(
            ItemStack singleContainer,
            StorageResourceKey key,
            long maxAmount,
            HolderLookup.Provider registries
    ) {
        if (!ModList.get().isLoaded(BOTANIA_MOD_ID)) return Optional.empty();
        return invokeBotania(
                botaniaWithdrawMethod(), singleContainer, key, maxAmount, registries);
    }

    private static synchronized Method depositMethod() {
        if (depositMethod == null) {
            depositMethod = method("planContainerDeposit", ItemStack.class, HolderLookup.Provider.class);
        }
        return depositMethod;
    }

    private static synchronized Method withdrawMethod() {
        if (withdrawMethod == null) {
            withdrawMethod = method(
                    "planContainerWithdraw",
                    ItemStack.class,
                    StorageResourceKey.class,
                    long.class,
                    HolderLookup.Provider.class);
        }
        return withdrawMethod;
    }

    private static synchronized Method botaniaDepositMethod() {
        if (botaniaDepositMethod == null) {
            botaniaDepositMethod = botaniaMethod(
                    "planContainerDeposit",
                    ItemStack.class,
                    HolderLookup.Provider.class);
        }
        return botaniaDepositMethod;
    }

    private static synchronized Method botaniaWithdrawMethod() {
        if (botaniaWithdrawMethod == null) {
            botaniaWithdrawMethod = botaniaMethod(
                    "planContainerWithdraw",
                    ItemStack.class,
                    StorageResourceKey.class,
                    long.class,
                    HolderLookup.Provider.class);
        }
        return botaniaWithdrawMethod;
    }

    private static Method method(String name, Class<?>... parameterTypes) {
        try {
            return Class.forName(MEKANISM_COMPAT_CLASS).getDeclaredMethod(name, parameterTypes);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            throw new IllegalStateException("Failed to load Mekanism container compatibility", exception);
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    "Mekanism container compatibility is binary-incompatible", error);
        }
    }

    private static Method botaniaMethod(String name, Class<?>... parameterTypes) {
        try {
            return Class.forName(BOTANIA_COMPAT_CLASS).getDeclaredMethod(name, parameterTypes);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            throw new IllegalStateException(
                    "Failed to load Botania container compatibility", exception);
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    "Botania container compatibility is binary-incompatible", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<StorageResourceContainerStrategy.Transfer> invoke(
            Method method,
            Object... arguments
    ) {
        try {
            return (Optional<StorageResourceContainerStrategy.Transfer>) method.invoke(null, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to access Mekanism container compatibility", exception);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof LinkageError error) {
                throw new IllegalStateException(
                        "Mekanism container compatibility is binary-incompatible", error);
            }
            throw new IllegalStateException(
                    "Mekanism container compatibility failed", exception.getCause());
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    "Mekanism container compatibility is binary-incompatible", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<StorageResourceContainerStrategy.Transfer> invokeBotania(
            Method method,
            Object... arguments
    ) {
        try {
            return (Optional<StorageResourceContainerStrategy.Transfer>) method.invoke(
                    null, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Failed to access Botania container compatibility", exception);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof LinkageError error) {
                throw new IllegalStateException(
                        "Botania container compatibility is binary-incompatible", error);
            }
            throw new IllegalStateException(
                    "Botania container compatibility failed", exception.getCause());
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    "Botania container compatibility is binary-incompatible", error);
        }
    }
}
