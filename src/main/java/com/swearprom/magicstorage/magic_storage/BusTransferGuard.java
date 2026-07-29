package com.swearprom.magicstorage.magic_storage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

final class BusTransferGuard {
    private static final ThreadLocal<Deque<BusActor>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);

    private BusTransferGuard() {
    }

    static <T> T run(BusActor actor, T rejected, Supplier<T> operation) {
        Deque<BusActor> active = ACTIVE.get();
        for (BusActor current : active) {
            boolean sameBus = current.dimension().equals(actor.dimension())
                    && current.busPos().equals(actor.busPos());
            if (current.networkId().equals(actor.networkId())
                    || sameBus && current.operationId().equals(actor.operationId())
                    || sameBus && current.direction() != actor.direction()) {
                return rejected;
            }
        }
        active.push(actor);
        try {
            return operation.get();
        } finally {
            if (active.pop() != actor) {
                throw new IllegalStateException("Bus transfer guard stack diverged");
            }
            if (active.isEmpty()) ACTIVE.remove();
        }
    }
}
