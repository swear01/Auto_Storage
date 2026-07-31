package com.swear.autostorage.compatkitfixture.generated;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Map;

public final class GeneratedCompatKitConformanceGameTests {
    private GeneratedCompatKitConformanceGameTests() {
    }

    private static void assertAtomic(
            GameTestHelper helper,
            CompatibilityConformanceHarness.Scenario scenario,
            CompatibilityConformanceHarness.Mode mode
    ) {
        scenario.reset();
        scenario.configure(mode);
        var before = scenario.snapshot();
        CompatibilityConformanceHarness.requireFailure(helper, scenario.attempt(1L));
        CompatibilityConformanceHarness.assertUnchanged(helper, before, scenario.snapshot());
        helper.succeed();
    }

    private static void assertExact(
            GameTestHelper helper,
            CompatibilityConformanceHarness.Scenario scenario,
            CompatibilityConformanceHarness.Mode mode,
            Map<String, Long> expectedDelta
    ) {
        scenario.reset();
        scenario.configure(mode);
        var before = scenario.snapshot();
        CompatibilityConformanceHarness.requireSuccess(helper, scenario.attempt(1L));
        CompatibilityConformanceHarness.assertDelta(
                helper, before, scenario.snapshot(), expectedDelta, 1L);
        helper.succeed();
    }

    private static CompatibilityConformanceHarness.Scenario scenario0(
            GameTestHelper helper
    ) {
        return com.swear.autostorage.compatkitfixture.CompatKitConformanceProvider.create(helper, ResourceLocation.parse("ae2:logic_processor"));
    }

    private static Map<String, Long> expectedDelta0Happy() {
        return Map.ofEntries(
                    Map.entry("item/ae2:logic_processor", 1L),
                    Map.entry("item/minecraft:redstone", -1L));
    }

    private static Map<String, Long> expectedDelta0CatalystToolRemainder() {
        return Map.ofEntries(
                    Map.entry("item/ae2:logic_processor", 1L),
                    Map.entry("item/minecraft:redstone", -1L));
    }

    private static Map<String, Long> expectedDelta0MultiOutput() {
        return Map.ofEntries(
                    Map.entry("item/ae2:logic_processor", 1L),
                    Map.entry("item/ae2:silicon", 1L),
                    Map.entry("item/minecraft:redstone", -1L));
    }

    @GameTest(template = "empty")
    public static void inscriber_recipe_happy_path_and_batching(GameTestHelper helper) {
        var scenario = scenario0(helper);
        scenario.reset();
        scenario.configure(CompatibilityConformanceHarness.Mode.HAPPY);
        var before = scenario.snapshot();
        CompatibilityConformanceHarness.requireSuccess(helper, scenario.attempt(1L));
        CompatibilityConformanceHarness.assertDelta(
                helper, before, scenario.snapshot(), expectedDelta0Happy(), 1L);
        scenario.reset();
        scenario.configure(CompatibilityConformanceHarness.Mode.HAPPY);
        before = scenario.snapshot();
        CompatibilityConformanceHarness.requireSuccess(helper, scenario.attempt(8L));
        CompatibilityConformanceHarness.assertDelta(
                helper, before, scenario.snapshot(), expectedDelta0Happy(), 8L);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inscriber_recipe_one_short_shortage_is_atomic(GameTestHelper helper) {
        assertAtomic(helper, scenario0(helper),
                CompatibilityConformanceHarness.Mode.ONE_SHORT);
    }

    @GameTest(template = "empty")
    public static void inscriber_recipe_destination_capacity_is_atomic(GameTestHelper helper) {
        assertAtomic(helper, scenario0(helper),
                CompatibilityConformanceHarness.Mode.DESTINATION_FULL);
    }

    @GameTest(template = "empty")
    public static void inscriber_recipe_checked_overflow_is_atomic(GameTestHelper helper) {
        assertAtomic(helper, scenario0(helper),
                CompatibilityConformanceHarness.Mode.CHECKED_OVERFLOW);
    }

    @GameTest(template = "empty")
    public static void inscriber_recipe_stale_holder_is_atomic(GameTestHelper helper) {
        assertAtomic(helper, scenario0(helper),
                CompatibilityConformanceHarness.Mode.STALE_HOLDER);
    }

    @GameTest(template = "empty")
    public static void inscriber_recipe_catalyst_tool_remainder_is_exact(GameTestHelper helper) {
        assertExact(helper, scenario0(helper),
                CompatibilityConformanceHarness.Mode.CATALYST_TOOL_REMAINDER,
                expectedDelta0CatalystToolRemainder());
    }

    @GameTest(template = "empty")
    public static void inscriber_recipe_multi_output_merge_is_exact(GameTestHelper helper) {
        assertExact(helper, scenario0(helper),
                CompatibilityConformanceHarness.Mode.MULTI_OUTPUT,
                expectedDelta0MultiOutput());
    }

    @GameTest(template = "empty")
    public static void inscriber_recipe_mixed_resource_rollback_is_atomic(GameTestHelper helper) {
        assertAtomic(helper, scenario0(helper),
                CompatibilityConformanceHarness.Mode.MIXED_RESOURCE_ROLLBACK);
    }

    @GameTest(template = "empty")
    public static void inscriber_recipe_dedicated_server_client_isolation(GameTestHelper helper) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) helper.fail("Conformance requires a dedicated server");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inscriber_recipe_all_mod_coexistence(GameTestHelper helper) {
        var scenario = scenario0(helper);
        if (!scenario.coexistenceHealthy()) helper.fail("Compatibility coexistence failed");
        helper.succeed();
    }

}
