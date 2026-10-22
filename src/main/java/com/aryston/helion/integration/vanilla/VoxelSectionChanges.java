package com.aryston.helion.integration.vanilla;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

public final class VoxelSectionChanges {
    private static final int CAPACITY = 1 << 14;
    private static final ConcurrentLinkedQueue<Long> CHANGED = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger PENDING = new AtomicInteger();

    private static volatile boolean collecting;
    private static volatile boolean overflowed;

    private VoxelSectionChanges() {
    }

    public static void meshChanged(long sectionNode) {
        if (!collecting || overflowed) {
            return;
        }
        if (PENDING.incrementAndGet() > CAPACITY) {
            overflowed = true;
            CHANGED.clear();
            return;
        }
        CHANGED.add(sectionNode);
    }

    static void collect(boolean enabled) {
        if (collecting != enabled) {
            collecting = enabled;
            clear();
        }
    }

    static boolean takeOverflow() {
        boolean result = overflowed;
        if (result) {
            clear();
        }
        return result;
    }

    static void drain(LongConsumer changed) {
        for (Long node = CHANGED.poll(); node != null; node = CHANGED.poll()) {
            PENDING.decrementAndGet();
            changed.accept(node);
        }
    }

    private static void clear() {
        CHANGED.clear();
        PENDING.set(0);
        overflowed = false;
    }
}
