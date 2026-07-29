package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

final class OptionalModBlockStrategies {
    private static final String MEKANISM_MOD_ID = "mekanism";
    private static final String MEKANISM_COMPAT_CLASS =
            "com.swearprom.magicstorage.magic_storage.MekanismChemicalCompat";
    private static final String ARS_NOUVEAU_MOD_ID = "ars_nouveau";
    private static final String ARS_NOUVEAU_COMPAT_CLASS =
            "com.swearprom.magicstorage.magic_storage.compat.arsnouveau.ArsNouveauCompat";
    private static Method findMekanismBlockHandlerMethod;
    private static Method findArsNouveauBlockHandlerMethod;

    private OptionalModBlockStrategies() {
    }

    static Optional<StorageResourceHandler> findMekanismChemical(
            Level level,
            BlockPos pos,
            Direction side
    ) {
        if (!ModList.get().isLoaded(MEKANISM_MOD_ID)) return Optional.empty();
        try {
            return invoke(findMekanismBlockHandlerMethod(), level, pos, side);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to access Mekanism block compatibility", exception);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof LinkageError error) {
                throw new IllegalStateException(
                        "Mekanism block compatibility is binary-incompatible", error);
            }
            throw new IllegalStateException(
                    "Mekanism block compatibility failed", exception.getCause());
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    "Mekanism block compatibility is binary-incompatible", error);
        }
    }

    static Optional<StorageResourceHandler> findArsNouveauSource(
            Level level,
            BlockPos pos,
            Direction side
    ) {
        if (!ModList.get().isLoaded(ARS_NOUVEAU_MOD_ID)) return Optional.empty();
        try {
            return invoke(findArsNouveauBlockHandlerMethod(), level, pos, side);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Failed to access Ars Nouveau block compatibility", exception);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof LinkageError error) {
                throw new IllegalStateException(
                        "Ars Nouveau block compatibility is binary-incompatible", error);
            }
            throw new IllegalStateException(
                    "Ars Nouveau block compatibility failed", exception.getCause());
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    "Ars Nouveau block compatibility is binary-incompatible", error);
        }
    }

    private static synchronized Method findMekanismBlockHandlerMethod() {
        if (findMekanismBlockHandlerMethod == null) {
            try {
                findMekanismBlockHandlerMethod = Class.forName(MEKANISM_COMPAT_CLASS).getDeclaredMethod(
                        "findBlockHandler", Level.class, BlockPos.class, Direction.class);
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                throw new IllegalStateException("Failed to load Mekanism block compatibility", exception);
            } catch (LinkageError error) {
                throw new IllegalStateException(
                        "Mekanism block compatibility is binary-incompatible", error);
            }
        }
        return findMekanismBlockHandlerMethod;
    }

    private static synchronized Method findArsNouveauBlockHandlerMethod() {
        if (findArsNouveauBlockHandlerMethod == null) {
            try {
                findArsNouveauBlockHandlerMethod = Class.forName(
                        ARS_NOUVEAU_COMPAT_CLASS).getDeclaredMethod(
                        "findSourceBlockHandler", Level.class, BlockPos.class, Direction.class);
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                throw new IllegalStateException(
                        "Failed to load Ars Nouveau block compatibility", exception);
            } catch (LinkageError error) {
                throw new IllegalStateException(
                        "Ars Nouveau block compatibility is binary-incompatible", error);
            }
        }
        return findArsNouveauBlockHandlerMethod;
    }

    @SuppressWarnings("unchecked")
    private static Optional<StorageResourceHandler> invoke(
            Method method,
            Object... arguments
    ) throws InvocationTargetException, IllegalAccessException {
        return (Optional<StorageResourceHandler>) method.invoke(null, arguments);
    }
}
