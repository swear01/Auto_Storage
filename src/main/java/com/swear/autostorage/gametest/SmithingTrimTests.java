package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder(AutoStorage.MODID)
@PrefixGameTestTemplate(false)
public final class SmithingTrimTests {
    private SmithingTrimTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void exact_adapter_assembles_trim_and_preserves_base_components(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        ItemStack template = new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
        ItemStack base = new ItemStack(Items.IRON_CHESTPLATE);
        base.setDamageValue(37);
        base.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved Trim Base"));
        ItemStack addition = new ItemStack(Items.REDSTONE);
        SmithingTrimRecipe recipe = new SmithingTrimRecipe(
                Ingredient.of(template), Ingredient.of(Items.IRON_CHESTPLATE), Ingredient.of(addition));
        RecipeHolder<SmithingTrimRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("trim_test", "exact_contract"), recipe);
        RecipeAdapterMatch match = BuiltInRecipeAdapters.registry().classify(holder).orElse(null);
        if (match == null) {
            helper.fail("Exact Smithing Trim recipe was not classified");
            return;
        }
        if (!match.stationDescriptorId().equals(MachineEnergyTable.SMITHING_TABLE_ID)
                || match.orderedInputs().size() != 3
                || match.candidateIndex().isExhaustive()) {
            helper.fail("Smithing Trim adapter contract is incomplete: " + match);
            return;
        }

        RecipeAdapterMatch.CheckedOutput checked = match.checkedOutput(List.of(
                Map.of(ItemKey.of(template), 2L),
                Map.of(ItemKey.of(base), 2L),
                Map.of(ItemKey.of(addition), 2L)
        ), 2, level).orElse(null);
        ItemStack expected = recipe.assemble(
                new SmithingRecipeInput(template, base, addition), level.registryAccess());
        if (checked == null || expected.isEmpty()
                || checked.primaryOutputs().size() != 1
                || checked.primaryOutputs().getOrDefault(ItemKey.of(expected), 0L) != 2
                || !checked.remainders().isEmpty()) {
            helper.fail("Smithing Trim checked output lost exact identity/count: " + checked);
            return;
        }
        ArmorTrim trim = expected.get(DataComponents.TRIM);
        var material = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL)
                .getOrThrow(TrimMaterials.REDSTONE);
        var pattern = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN)
                .getOrThrow(TrimPatterns.SENTRY);
        if (expected.getDamageValue() != 37
                || !Component.literal("Preserved Trim Base").equals(expected.get(DataComponents.CUSTOM_NAME))
                || trim == null || !trim.hasPatternAndMaterial(pattern, material)) {
            helper.fail("Smithing Trim output did not preserve base components and exact trim: " + expected);
            return;
        }
        List<RecipeAdapterMatch> variants = match.resolveVariants(
                List.of(template, base, addition), level);
        if (variants.size() != 1
                || !ItemStack.isSameItemSameComponents(
                variants.getFirst().presentationOutput(List.of(), level), expected)
                || !match.resolveVariants(List.of(), level).isEmpty()) {
            helper.fail("Smithing Trim did not resolve exactly one source-backed output variant: " + variants);
            return;
        }
        ItemStack otherTemplate = template.copy();
        otherTemplate.set(DataComponents.CUSTOM_NAME, Component.literal("Other Template"));
        ItemStack otherBase = base.copy();
        otherBase.set(DataComponents.CUSTOM_NAME, Component.literal("Other Base"));
        ItemStack otherAddition = addition.copy();
        otherAddition.set(DataComponents.CUSTOM_NAME, Component.literal("Other Addition"));
        List<RecipeAdapterMatch.Input> exactInputs = variants.getFirst().orderedInputs();
        if (exactInputs.get(0).test(otherTemplate)
                || exactInputs.get(1).test(otherBase)
                || exactInputs.get(2).test(otherAddition)) {
            helper.fail("Smithing Trim bound variant accepted a different component identity");
            return;
        }
        ItemStack presentation = match.presentationOutput(List.of(template, base, addition), level);
        if (!ItemStack.isSameItemSameComponents(presentation, expected)
                || presentation.getCount() != expected.getCount()) {
            helper.fail("Smithing Trim presentation output is not exact: " + presentation);
            return;
        }

        RecipeHolder<SmithingTrimRecipe> replacement = new RecipeHolder<>(holder.id(),
                new SmithingTrimRecipe(
                        Ingredient.of(template),
                        Ingredient.of(Items.IRON_CHESTPLATE),
                        Ingredient.of(addition)));
        if (match.validatesSimulation(replacement) || match.validatesCommit(replacement)) {
            helper.fail("Smithing Trim accepted a stale same-ID holder");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void smithing_transform_resolves_source_bound_component_variants(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        ItemStack template = new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        ItemStack firstBase = namedSword("Transform A", 19);
        ItemStack secondBase = namedSword("Transform B", 83);
        ItemStack addition = new ItemStack(Items.NETHERITE_INGOT);
        SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                Ingredient.of(template),
                Ingredient.of(Items.DIAMOND_SWORD),
                Ingredient.of(addition),
                new ItemStack(Items.NETHERITE_SWORD));
        RecipeHolder<SmithingTransformRecipe> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath("trim_test", "transform_variants"), recipe);
        RecipeAdapterMatch match = BuiltInRecipeAdapters.registry().classify(holder).orElse(null);
        if (match == null) {
            helper.fail("Smithing Transform recipe was not classified");
            return;
        }

        List<ItemStack> forward = List.of(template, firstBase, secondBase, addition);
        List<ItemStack> reverse = List.of(addition, secondBase, firstBase, template);
        List<RecipeAdapterMatch> forwardVariants = match.resolveVariants(forward, level);
        List<RecipeAdapterMatch> reverseVariants = match.resolveVariants(reverse, level);
        List<ItemKey> expected = List.of(
                ItemKey.of(recipe.assemble(
                        new SmithingRecipeInput(template, firstBase, addition), level.registryAccess())),
                ItemKey.of(recipe.assemble(
                        new SmithingRecipeInput(template, secondBase, addition), level.registryAccess())));
        List<ItemKey> forwardOutputs = variantOutputs(forwardVariants, level);
        List<ItemKey> reverseOutputs = variantOutputs(reverseVariants, level);
        if (forwardVariants.size() != 2
                || !forwardOutputs.equals(expected)
                || !forwardOutputs.equals(reverseOutputs)) {
            helper.fail("Smithing Transform did not preserve two deterministic exact base variants");
            return;
        }
        for (RecipeAdapterMatch variant : forwardVariants) {
            ItemStack output = variant.presentationOutput(List.of(), level);
            if (!output.is(Items.NETHERITE_SWORD)
                    || output.get(DataComponents.CUSTOM_NAME) == null
                    || output.getDamageValue() <= 0) {
                helper.fail("Smithing Transform variant lost source base components: " + output);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void trim_variant_order_is_canonical_across_source_iteration(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        ItemStack sentry = new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
        ItemStack dune = new ItemStack(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE);
        ItemStack firstBase = namedChestplate("Canonical A", 3);
        ItemStack secondBase = namedChestplate("Canonical B", 41);
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        SmithingTrimRecipe recipe = trimRecipe(
                Ingredient.of(sentry, dune),
                Ingredient.of(Items.IRON_CHESTPLATE),
                Ingredient.of(redstone, gold));
        RecipeAdapterMatch match = BuiltInRecipeAdapters.registry().classify(
                trimHolder("canonical_order", recipe)).orElse(null);
        if (match == null) {
            helper.fail("Canonical Smithing Trim fixture was not classified");
            return;
        }

        List<ItemStack> forward = List.of(sentry, dune, firstBase, secondBase, redstone, gold);
        List<ItemStack> reverse = List.of(gold, redstone, secondBase, firstBase, dune, sentry);
        List<ItemKey> forwardOutputs = variantOutputs(match.resolveVariants(forward, level), level);
        List<ItemKey> reverseOutputs = variantOutputs(match.resolveVariants(reverse, level), level);
        List<ItemKey> expected = new ArrayList<>();
        for (ItemStack template : List.of(dune, sentry)) {
            for (ItemStack base : List.of(firstBase, secondBase)) {
                for (ItemStack addition : List.of(gold, redstone)) {
                    expected.add(ItemKey.of(trimOutput(recipe, template, base, addition, level)));
                }
            }
        }
        if (!forwardOutputs.equals(expected) || !forwardOutputs.equals(reverseOutputs)) {
            helper.fail("Smithing Trim variant order depended on source iteration");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void trim_variant_combination_cap_fails_closed(GameTestHelper helper) {
        var level = helper.getLevel();
        SmithingTrimRecipe recipe = trimRecipe(
                Ingredient.of(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                Ingredient.of(Items.IRON_CHESTPLATE),
                Ingredient.of(Items.REDSTONE));
        RecipeAdapterMatch match = BuiltInRecipeAdapters.registry().classify(
                trimHolder("bounded_variants", recipe)).orElse(null);
        if (match == null) {
            helper.fail("Bounded Smithing Trim fixture was not classified");
            return;
        }
        List<ItemStack> sources = new ArrayList<>();
        for (int index = 0; index < 41; index++) {
            sources.add(namedStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, "Template " + index));
            sources.add(namedChestplate("Base " + index, index));
        }
        for (int index = 0; index < 40; index++) {
            sources.add(namedStack(Items.REDSTONE, "Addition " + index));
        }
        if (!match.resolveVariants(sources, level).isEmpty()) {
            helper.fail("Smithing Trim returned a partial result above the variant combination cap");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void menu_requires_smithing_table_and_executes_exact_trim_variant(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            ItemStack template = new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
            ItemStack base = namedChestplate("Exact Menu Base", 29);
            ItemStack addition = new ItemStack(Items.REDSTONE);
            SmithingTrimRecipe recipe = trimRecipe(
                    Ingredient.of(template), Ingredient.of(Items.IRON_CHESTPLATE), Ingredient.of(addition));
            RecipeHolder<SmithingTrimRecipe> holder = trimHolder("menu_exact", recipe);
            ItemStack expected = trimOutput(recipe, template, base, addition, level);
            require(helper, !expected.isEmpty(), "Could not assemble expected trim output");
            require(helper, core.insertItem(template.copy()) == 1, "Could not seed trim template");
            require(helper, core.insertItem(base.copy()) == 1, "Could not seed trim base");
            require(helper, core.insertItem(addition.copy()) == 1, "Could not seed trim addition");

            withTemporaryRecipes(level, List.of(holder), () -> {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(190, player.getInventory(), core);
                menu.lookUpRecipes(level, expected);
                if (!menu.getCurrentRecipes().isEmpty()
                        || menu.handleRecipeRequest(
                        level, holder.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Smithing Trim was available without a Smithing Table");
                    return;
                }
                assertStoredInputs(helper, core, template, base, addition, 1);

                installSmithingTable(core);
                menu.lookUpRecipes(level, expected);
                if (menu.getCurrentRecipes().stream().noneMatch(
                        candidate -> candidate.id().equals(holder.id()))
                        || !menu.handleRecipeRequest(
                        level, holder.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Installed Smithing Table did not expose the exact trim variant");
                    return;
                }
                RecipePresentation presentation = menu.getRecipePresentation();
                if (!ItemStack.isSameItemSameComponents(presentation.output(), expected)
                        || presentation.output().getCount() != 1
                        || presentation.kind() != RecipePresentationKind.SMITHING
                        || !presentation.station().is(Items.SMITHING_TABLE)) {
                    helper.fail("Smithing Trim presentation was not exact: " + presentation);
                    return;
                }
                if (!menu.handleRecipeRequest(
                        level, holder.id(), 1, CraftingDestination.INVENTORY, player)) {
                    helper.fail("Exact Smithing Trim request did not execute");
                    return;
                }
                if (countExactInInventory(player, expected) != 1) {
                    helper.fail("Exact trimmed output was not delivered to the player");
                    return;
                }
                assertStoredInputs(helper, core, template, base, addition, 0);
            });
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void craftable_catalog_keeps_every_exact_trim_output_variant(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T6.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installSmithingTable(core);
            ItemStack sentry = new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
            ItemStack dune = new ItemStack(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE);
            ItemStack firstBase = namedChestplate("Variant A", 7);
            ItemStack secondBase = namedChestplate("Variant B", 31);
            ItemStack redstone = new ItemStack(Items.REDSTONE);
            ItemStack gold = new ItemStack(Items.GOLD_INGOT);
            SmithingTrimRecipe recipe = trimRecipe(
                    Ingredient.of(sentry, dune),
                    Ingredient.of(Items.IRON_CHESTPLATE),
                    Ingredient.of(redstone, gold));
            RecipeHolder<SmithingTrimRecipe> holder = trimHolder("catalog_variants", recipe);
            for (ItemStack input : List.of(sentry, dune, firstBase, secondBase, redstone, gold)) {
                require(helper, core.insertItem(input.copy()) == 1, "Could not seed variant input " + input);
            }

            Set<ItemKey> expectedOutputs = new LinkedHashSet<>();
            for (ItemStack template : List.of(sentry, dune)) {
                for (ItemStack base : List.of(firstBase, secondBase)) {
                    for (ItemStack addition : List.of(redstone, gold)) {
                        ItemStack output = trimOutput(recipe, template, base, addition, level);
                        require(helper, !output.isEmpty(), "Could not assemble expected catalog variant");
                        expectedOutputs.add(ItemKey.of(output));
                    }
                }
            }
            require(helper, expectedOutputs.size() == 8,
                    "Fixture did not produce eight exact trim output variants");
            ItemStack selectedOutput = trimOutput(recipe, dune, secondBase, gold, level);

            withTemporaryRecipes(level, List.of(holder), () -> {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(191, player.getInventory(), core);
                menu.clickMenuButton(player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON);
                menu.refreshDisplayItems(core);
                Set<ItemKey> displayedOutputs = new LinkedHashSet<>();
                int selectedSlot = -1;
                for (int slot = 0; slot < StorageTerminalMenu.DISPLAY_SLOTS; slot++) {
                    ItemStack displayed = TerminalDisplayStack.strip(menu.getSlot(slot).getItem());
                    if (displayed.isEmpty() || !displayed.is(Items.IRON_CHESTPLATE)
                            || !displayed.has(DataComponents.TRIM)) continue;
                    ItemKey key = ItemKey.of(displayed);
                    displayedOutputs.add(key);
                    if (key.equals(ItemKey.of(selectedOutput))) selectedSlot = slot;
                }
                if (!displayedOutputs.equals(expectedOutputs)) {
                    helper.fail("Craftable catalog merged or omitted exact trim variants: expected="
                            + expectedOutputs.size() + " actual=" + displayedOutputs.size());
                    return;
                }
                if (menu.handleRecipeRequest(
                        level, holder.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Same-ID Trim request guessed a variant without an exact selected output");
                    return;
                }
                if (selectedSlot < 0
                        || !menu.selectRecipe(level, selectedSlot, holder.id(), player)) {
                    helper.fail("Could not select the exact component-sensitive trim variant");
                    return;
                }
                menu.refreshDisplayItems(core);
                if (!ItemStack.isSameItemSameComponents(
                        menu.getRecipePresentation().output(), selectedOutput)) {
                    helper.fail("Current-recipe refresh changed the selected exact trim variant");
                    return;
                }
                menu.craftItem(1, player);
                if (countExactInInventory(player, selectedOutput) != 1
                        || core.getItemCount(ItemKey.of(dune)) != 0
                        || core.getItemCount(ItemKey.of(secondBase)) != 0
                        || core.getItemCount(ItemKey.of(gold)) != 0
                        || core.getItemCount(ItemKey.of(sentry)) != 1
                        || core.getItemCount(ItemKey.of(firstBase)) != 1
                        || core.getItemCount(ItemKey.of(redstone)) != 1) {
                    helper.fail("Selected trim variant consumed or produced the wrong exact identities");
                    return;
                }
            });
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void full_destination_rejects_trim_before_any_mutation(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installSmithingTable(core);
            ItemStack template = new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
            ItemStack base = namedChestplate("Capacity Base", 11);
            ItemStack addition = new ItemStack(Items.REDSTONE, 2);
            SmithingTrimRecipe recipe = trimRecipe(
                    Ingredient.of(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                    Ingredient.of(Items.IRON_CHESTPLATE),
                    Ingredient.of(Items.REDSTONE));
            RecipeHolder<SmithingTrimRecipe> holder = trimHolder("full_destination", recipe);
            ItemStack expected = trimOutput(recipe, template, base, addition, level);
            require(helper, core.insertItem(template.copy()) == 2, "Could not seed two templates");
            require(helper, core.insertItemCount(ItemKey.of(base), 2, Action.EXECUTE, Actor.EMPTY) == 2,
                    "Could not seed two exact bases");
            require(helper, core.insertItem(addition.copy()) == 2, "Could not seed two additions");
            fillCoreTypes(core, helper);

            withTemporaryRecipes(level, List.of(holder), () -> {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                for (int slot = 0; slot < StorageTerminalMenu.PLAYER_INVENTORY_SLOTS; slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Items.NETHERRACK, 64));
                }
                var menu = new CraftingTerminalMenu(192, player.getInventory(), core);
                if (!menu.handleRecipeRequest(
                        level, holder.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not select full-destination trim fixture");
                    return;
                }
                menu.craftItem(1, player);
                if (core.getItemCount(ItemKey.of(template)) != 2
                        || core.getItemCount(ItemKey.of(base)) != 2
                        || core.getItemCount(ItemKey.of(addition)) != 2
                        || core.getItemCount(ItemKey.of(expected)) != 0
                        || countExactInInventory(player, expected) != 0) {
                    helper.fail("Full destination mutated Smithing Trim inputs or output");
                    return;
                }
            });
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void rejected_trim_output_rolls_back_every_input(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity originalCore)) {
                helper.fail("Core not found");
                return;
            }
            var reference = new net.minecraft.nbt.CompoundTag();
            originalCore.saveAdditional(reference, level.registryAccess());
            level.removeBlockEntity(corePos);
            var rejectingCore = new RejectingTrimOutputCore(
                    corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState());
            rejectingCore.loadAdditional(reference, level.registryAccess());
            level.setBlockEntity(rejectingCore);
        });
        helper.runAfterDelay(4, () -> {
            if (!(level.getBlockEntity(corePos) instanceof RejectingTrimOutputCore core)) {
                helper.fail("Rejecting Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installSmithingTable(core);
            ItemStack template = new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
            ItemStack base = namedChestplate("Rollback Base", 17);
            ItemStack addition = new ItemStack(Items.REDSTONE);
            SmithingTrimRecipe recipe = trimRecipe(
                    Ingredient.of(template), Ingredient.of(Items.IRON_CHESTPLATE), Ingredient.of(addition));
            RecipeHolder<SmithingTrimRecipe> holder = trimHolder("rollback", recipe);
            ItemStack expected = trimOutput(recipe, template, base, addition, level);
            require(helper, core.insertItem(template.copy()) == 1, "Could not seed rollback template");
            require(helper, core.insertItem(base.copy()) == 1, "Could not seed rollback base");
            require(helper, core.insertItem(addition.copy()) == 1, "Could not seed rollback addition");

            withTemporaryRecipes(level, List.of(holder), () -> {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(193, player.getInventory(), core);
                if (!menu.handleRecipeRequest(
                        level, holder.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not select rollback trim fixture");
                    return;
                }
                menu.clickMenuButton(player, CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON);
                core.rejectTrimOutput = true;
                menu.craftItem(1, player);
                assertStoredInputs(helper, core, template, base, addition, 1);
                if (core.getItemCount(ItemKey.of(expected)) != 0) {
                    helper.fail("Rejected trim output remained in Core after rollback");
                    return;
                }
            });
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void reload_and_context_incomplete_trim_variants_fail_closed(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            installSmithingTable(core);
            ItemStack template = new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
            ItemStack base = namedChestplate("Reload Base", 5);
            ItemStack addition = new ItemStack(Items.REDSTONE);
            SmithingTrimRecipe recipe = trimRecipe(
                    Ingredient.of(template), Ingredient.of(Items.IRON_CHESTPLATE), Ingredient.of(addition));
            RecipeHolder<SmithingTrimRecipe> holder = trimHolder("reload", recipe);
            ItemStack expected = trimOutput(recipe, template, base, addition, level);
            require(helper, core.insertItem(template.copy()) == 1, "Could not seed reload template");
            require(helper, core.insertItem(base.copy()) == 1, "Could not seed reload base");
            require(helper, core.insertItem(addition.copy()) == 1, "Could not seed reload addition");

            var manager = level.getRecipeManager();
            List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
            List<RecipeHolder<?>> registered = new ArrayList<>(original);
            registered.removeIf(candidate -> candidate.id().equals(holder.id()));
            registered.add(holder);
            manager.replaceRecipes(registered);
            try {
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var menu = new CraftingTerminalMenu(194, player.getInventory(), core);
                if (!menu.handleRecipeRequest(
                        level, holder.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not select reload trim fixture");
                    return;
                }
                ItemStack alternateTemplate = new ItemStack(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE);
                ItemStack alternateAddition = new ItemStack(Items.GOLD_INGOT);
                SmithingTrimRecipe supportedReplacement = trimRecipe(
                        Ingredient.of(alternateTemplate),
                        Ingredient.of(Items.IRON_CHESTPLATE),
                        Ingredient.of(alternateAddition));
                ItemStack alternateOutput = trimOutput(
                        supportedReplacement, alternateTemplate, base, alternateAddition, level);
                require(helper, core.insertItem(alternateTemplate.copy()) == 1,
                        "Could not seed alternate reload template");
                require(helper, core.insertItem(alternateAddition.copy()) == 1,
                        "Could not seed alternate reload addition");
                List<RecipeHolder<?>> reloaded = new ArrayList<>(original);
                reloaded.add(new RecipeHolder<>(holder.id(), supportedReplacement));
                manager.replaceRecipes(reloaded);
                menu.craftItem(1, player);
                menu.refreshDisplayItems(core);
                assertStoredInputs(helper, core, template, base, addition, 1);
                if (core.getItemCount(ItemKey.of(alternateTemplate)) != 1
                        || core.getItemCount(ItemKey.of(alternateAddition)) != 1
                        || countExactInInventory(player, expected) != 0
                        || countExactInInventory(player, alternateOutput) != 0
                        || !menu.getRecipePresentation().isEmpty()) {
                    helper.fail("Same-ID supported reload selected a different exact trim variant");
                    return;
                }

                registered = new ArrayList<>(original);
                registered.add(holder);
                manager.replaceRecipes(registered);
                if (!menu.handleRecipeRequest(
                        level, holder.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Could not reselect trim fixture before unsupported reload");
                    return;
                }
                SmithingTrimRecipe unsupportedReplacement = new SmithingTrimRecipe(
                        Ingredient.of(template),
                        Ingredient.of(Items.IRON_CHESTPLATE),
                        Ingredient.of(addition)) {
                };
                reloaded = new ArrayList<>(original);
                reloaded.add(new RecipeHolder<>(holder.id(), unsupportedReplacement));
                manager.replaceRecipes(reloaded);
                menu.craftItem(1, player);
                menu.refreshDisplayItems(core);
                assertStoredInputs(helper, core, template, base, addition, 1);
                if (countExactInInventory(player, expected) != 0
                        || !menu.getRecipePresentation().isEmpty()) {
                    helper.fail("Reloaded unsupported Smithing Trim holder did not fail closed");
                    return;
                }

                SmithingTrimRecipe incompleteContext = trimRecipe(
                        Ingredient.of(Items.DIRT),
                        Ingredient.of(Items.IRON_CHESTPLATE),
                        Ingredient.of(Items.STICK));
                RecipeHolder<SmithingTrimRecipe> incompleteHolder = trimHolder(
                        "missing_registry_context", incompleteContext);
                reloaded = new ArrayList<>(original);
                reloaded.add(incompleteHolder);
                manager.replaceRecipes(reloaded);
                core.insertItem(new ItemStack(Items.DIRT));
                core.insertItem(new ItemStack(Items.STICK));
                if (menu.handleRecipeRequest(
                        level, incompleteHolder.id(), 1, CraftingDestination.NONE, player)) {
                    helper.fail("Context-incomplete trim mapping produced an approximate output");
                    return;
                }
            } finally {
                manager.replaceRecipes(original);
            }
            helper.succeed();
        });
    }

    private static SmithingTrimRecipe trimRecipe(
            Ingredient template,
            Ingredient base,
            Ingredient addition
    ) {
        return new SmithingTrimRecipe(template, base, addition);
    }

    private static RecipeHolder<SmithingTrimRecipe> trimHolder(
            String path,
            SmithingTrimRecipe recipe
    ) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("trim_test", path), recipe);
    }

    private static ItemStack namedChestplate(String name, int damage) {
        ItemStack stack = new ItemStack(Items.IRON_CHESTPLATE);
        stack.setDamageValue(damage);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack namedSword(String name, int damage) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.setDamageValue(damage);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack namedStack(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static List<ItemKey> variantOutputs(
            List<RecipeAdapterMatch> variants,
            net.minecraft.world.level.Level level
    ) {
        return variants.stream()
                .map(variant -> variant.presentationOutput(List.of(), level))
                .map(ItemKey::of)
                .toList();
    }

    private static ItemStack trimOutput(
            SmithingTrimRecipe recipe,
            ItemStack template,
            ItemStack base,
            ItemStack addition,
            net.minecraft.world.level.Level level
    ) {
        return recipe.assemble(
                new SmithingRecipeInput(template, base, addition), level.registryAccess());
    }

    private static void installSmithingTable(StorageCoreBlockEntity core) {
        core.getMachineContainer().setItem(
                MachineEnergyTable.SMITHING_TABLE_SLOT, new ItemStack(Items.SMITHING_TABLE));
    }

    private static void assertStoredInputs(
            GameTestHelper helper,
            StorageCoreBlockEntity core,
            ItemStack template,
            ItemStack base,
            ItemStack addition,
            long expected
    ) {
        if (core.getItemCount(ItemKey.of(template)) != expected
                || core.getItemCount(ItemKey.of(base)) != expected
                || core.getItemCount(ItemKey.of(addition)) != expected) {
            helper.fail("Smithing Trim input counts changed unexpectedly");
        }
    }

    private static int countExactInInventory(net.minecraft.world.entity.player.Player player, ItemStack expected) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, expected)) count += stack.getCount();
        }
        return count;
    }

    private static void fillCoreTypes(StorageCoreBlockEntity core, GameTestHelper helper) {
        List<Item> fillers = List.of(
                Items.DIRT, Items.STONE, Items.COBBLESTONE, Items.OAK_PLANKS,
                Items.STICK, Items.COAL, Items.DIAMOND, Items.EMERALD,
                Items.LAPIS_LAZULI, Items.AMETHYST_SHARD, Items.QUARTZ,
                Items.COPPER_INGOT, Items.BRICK, Items.FLINT, Items.CLAY_BALL);
        int index = 0;
        while (core.getTypeCount() < core.getTotalTypeSlots() && index < fillers.size()) {
            ItemStack filler = new ItemStack(fillers.get(index++));
            if (core.getItemCount(ItemKey.of(filler)) == 0 && core.insertItem(filler) != 1) {
                helper.fail("Could not fill Core type capacity");
                return;
            }
        }
        if (core.getTypeCount() != core.getTotalTypeSlots()) {
            helper.fail("Fixture did not fill Core type capacity");
        }
    }

    private static void withTemporaryRecipes(
            net.minecraft.world.level.Level level,
            List<? extends RecipeHolder<?>> holders,
            Runnable action
    ) {
        var manager = level.getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        List<RecipeHolder<?>> registered = new ArrayList<>(original);
        Set<ResourceLocation> ids = holders.stream().map(RecipeHolder::id)
                .collect(java.util.stream.Collectors.toSet());
        registered.removeIf(holder -> ids.contains(holder.id()));
        registered.addAll(holders);
        manager.replaceRecipes(registered);
        try {
            action.run();
        } finally {
            manager.replaceRecipes(original);
        }
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private static final class RejectingTrimOutputCore extends StorageCoreBlockEntity {
        private boolean rejectTrimOutput;

        private RejectingTrimOutputCore(
                BlockPos pos,
                net.minecraft.world.level.block.state.BlockState state
        ) {
            super(pos, state);
        }

        @Override
        public long insertItemCount(ItemKey key, long amount, Action action, Actor actor) {
            if (rejectTrimOutput && action == Action.EXECUTE
                    && key.toStack(1).has(DataComponents.TRIM)
                    && actor.name().equals("auto_storage_crafting")) return 0;
            return super.insertItemCount(key, amount, action, actor);
        }

        @Override
        public boolean applyResourceTransaction(
                StorageResourceTransaction transaction,
                Action action,
                Actor actor
        ) {
            if (rejectTrimOutput && action == Action.EXECUTE
                    && actor.name().equals("auto_storage_crafting")
                    && getLevel() != null
                    && transaction.deltas().entrySet().stream()
                    .anyMatch(entry -> entry.getValue() > 0
                            && entry.getKey().itemStack(getLevel().registryAccess())
                            .map(stack -> stack.has(DataComponents.TRIM))
                            .orElse(false))) return false;
            return super.applyResourceTransaction(transaction, action, actor);
        }
    }
}
