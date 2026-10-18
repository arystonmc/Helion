package com.aryston.helion.integration.vanilla;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.jspecify.annotations.Nullable;

public final class ShadowCasterChanges {
    private static final int CAPACITY = 1 << 16;
    private static final ConcurrentLinkedQueue<SectionRenderDispatcher.RenderSection> CHANGED = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger PENDING = new AtomicInteger();

    private static volatile boolean overflowed;

    private ShadowCasterChanges() {
    }

    public static void meshChanged(SectionRenderDispatcher.RenderSection section) {
        if (overflowed) {
            return;
        }
        if (PENDING.incrementAndGet() > CAPACITY) {
            overflowed = true;
            CHANGED.clear();
            return;
        }
        CHANGED.add(section);
    }

    static boolean overflowed() {
        return overflowed;
    }

    static SectionRenderDispatcher.@Nullable RenderSection next() {
        SectionRenderDispatcher.RenderSection section = CHANGED.poll();
        if (section != null) {
            PENDING.decrementAndGet();
        }
        return section;
    }

    static void clear() {
        CHANGED.clear();
        PENDING.set(0);
        overflowed = false;
    }
}
