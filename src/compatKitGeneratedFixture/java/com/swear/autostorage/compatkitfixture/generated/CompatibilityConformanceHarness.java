package com.swear.autostorage.compatkitfixture.generated;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CompatibilityConformanceHarness {
    public enum Mode {
        HAPPY,
        ONE_SHORT,
        DESTINATION_FULL,
        CHECKED_OVERFLOW,
        STALE_HOLDER,
        CATALYST_TOOL_REMAINDER,
        MULTI_OUTPUT,
        MIXED_RESOURCE_ROLLBACK
    }

    public record Snapshot(Map<String, Long> amounts) {
        public Snapshot {
            amounts = Map.copyOf(amounts);
        }
    }

    public record Attempt(boolean success) {
    }

    public interface Scenario {
        void reset();

        void configure(Mode mode);

        Snapshot snapshot();

        Attempt attempt(long crafts);

        boolean coexistenceHealthy();
    }

    private CompatibilityConformanceHarness() {
    }

    public static void assertDelta(
            GameTestHelper helper,
            Snapshot before,
            Snapshot after,
            Map<String, Long> perCraft,
            long crafts
    ) {
        Map<String, Long> expected = new LinkedHashMap<>(before.amounts());
        for (Map.Entry<String, Long> entry : perCraft.entrySet()) {
            long delta = Math.multiplyExact(entry.getValue(), crafts);
            expected.merge(entry.getKey(), delta, Math::addExact);
        }
        expected.values().removeIf(value -> value == 0L);
        Map<String, Long> actual = new LinkedHashMap<>(after.amounts());
        actual.values().removeIf(value -> value == 0L);
        if (!expected.equals(actual)) {
            helper.fail("Conformance delta mismatch: expected " + expected
                    + " but was " + actual);
        }
    }

    public static void assertUnchanged(
            GameTestHelper helper,
            Snapshot before,
            Snapshot after
    ) {
        if (!before.equals(after)) {
            helper.fail("Atomic rollback mismatch: before " + before
                    + " after " + after);
        }
    }

    public static void requireSuccess(GameTestHelper helper, Attempt attempt) {
        if (!attempt.success()) helper.fail("Expected conformance craft success");
    }

    public static void requireFailure(GameTestHelper helper, Attempt attempt) {
        if (attempt.success()) helper.fail("Expected conformance craft rejection");
    }
}
