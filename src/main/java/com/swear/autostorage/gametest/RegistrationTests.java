package com.swear.autostorage;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(AutoStorage.MODID)
public class RegistrationTests {

    private static ResourceLocation key(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path);
    }

    @GameTest(template = "empty")
    public static void storage_core_is_registered(GameTestHelper helper) {
        var block = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).get(key("storage_core"));
        if (block == null) helper.fail("storage_core block is not registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void storage_unit_t1_is_registered(GameTestHelper helper) {
        var block = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).get(key("storage_unit_t1"));
        if (block == null) helper.fail("storage_unit_t1 block is not registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void storage_unit_t6_is_registered(GameTestHelper helper) {
        var block = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).get(key("storage_unit_t6"));
        if (block == null) helper.fail("storage_unit_t6 block is not registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void storage_terminal_is_registered(GameTestHelper helper) {
        var block = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).get(key("storage_terminal"));
        if (block == null) helper.fail("storage_terminal block is not registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crafting_terminal_is_registered(GameTestHelper helper) {
        var block = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).get(key("crafting_terminal"));
        if (block == null) helper.fail("crafting_terminal block is not registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remote_terminal_item_is_registered(GameTestHelper helper) {
        var item = helper.getLevel().registryAccess().registryOrThrow(Registries.ITEM).get(key("remote_terminal"));
        if (item == null) helper.fail("remote_terminal item is not registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void import_bus_is_registered(GameTestHelper helper) {
        var block = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).get(key("import_bus"));
        if (block == null) helper.fail("import_bus block is not registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void patchouli_guide_content_is_packaged_as_assets(GameTestHelper helper) {
        ClassLoader loader = RegistrationTests.class.getClassLoader();
        String base = "assets/auto_storage/patchouli_books/guide/en_us";
        if (loader.getResource(base + "/categories/getting_started.json") == null)
            helper.fail("Patchouli category content must be under assets/, not data/");
        if (loader.getResource(base + "/entries/first_network.json") == null)
            helper.fail("Patchouli entry content must be under assets/, not data/");
        helper.succeed();
    }
}
