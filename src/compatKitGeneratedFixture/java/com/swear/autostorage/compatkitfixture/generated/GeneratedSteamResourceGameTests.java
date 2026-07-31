package com.swear.autostorage.compatkitfixture.generated;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class GeneratedSteamResourceGameTests {
    public record Snapshot(Map<String, Long> amounts) {
        public Snapshot {
            amounts = Map.copyOf(amounts);
        }
    }

    public interface ResourceScenario {
        void reset();

        void seed();

        Snapshot snapshot();

        byte[] save();

        void clear();

        void load(byte[] saved);

        boolean deposit();

        boolean withdraw();

        boolean attemptMixedRollback();
    }

    private GeneratedSteamResourceGameTests() {
    }

    private static void assertDelta(
            GameTestHelper helper,
            Snapshot before,
            Snapshot after,
            String key,
            long delta
    ) {
        Map<String, Long> expected = new LinkedHashMap<>(before.amounts());
        expected.merge(key, delta, Math::addExact);
        expected.values().removeIf(value -> value == 0L);
        Map<String, Long> actual = new LinkedHashMap<>(after.amounts());
        actual.values().removeIf(value -> value == 0L);
        if (!expected.equals(actual)) {
            helper.fail("Resource delta mismatch: expected " + expected + " but was " + actual);
        }
    }

    private static void assertUnchanged(
            GameTestHelper helper,
            Snapshot before,
            Snapshot after
    ) {
        if (!before.equals(after)) {
            helper.fail("Resource atomicity mismatch: before " + before + " after " + after);
        }
    }

    private static ResourceScenario scenario0(GameTestHelper helper) {
        return com.swear.autostorage.compatkitfixture.CompatKitResourceProvider.create(helper);
    }

    @GameTest(template = "empty")
    public static void compat_kit_fixture_steam_persistence_round_trip(GameTestHelper helper) {
        var scenario = scenario0(helper);
        scenario.reset();
        scenario.seed();
        var before = scenario.snapshot();
        byte[] saved = Objects.requireNonNull(scenario.save(), "saved resource state");
        scenario.clear();
        if (before.equals(scenario.snapshot())) helper.fail("Resource clear did not change state");
        scenario.load(saved);
        assertUnchanged(helper, before, scenario.snapshot());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compat_kit_fixture_steam_container_deposit_and_withdraw(GameTestHelper helper) {
        var scenario = scenario0(helper);
        scenario.reset();
        var before = scenario.snapshot();
        if (!scenario.deposit()) helper.fail("Resource container deposit failed");
        assertDelta(helper, before, scenario.snapshot(), "resource/compat_kit_fixture:steam", 1000L);
        if (!scenario.withdraw()) helper.fail("Resource container withdrawal failed");
        assertUnchanged(helper, before, scenario.snapshot());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compat_kit_fixture_steam_mixed_resource_rollback_is_atomic(GameTestHelper helper) {
        var scenario = scenario0(helper);
        scenario.reset();
        scenario.seed();
        var before = scenario.snapshot();
        if (scenario.attemptMixedRollback()) helper.fail("Mixed resource rollback unexpectedly committed");
        if (!before.equals(scenario.snapshot())) helper.fail("Mixed resource rollback mutated state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compat_kit_fixture_steam_dedicated_server_client_isolation(GameTestHelper helper) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) helper.fail("Resource conformance requires a dedicated server");
        helper.succeed();
    }

}
