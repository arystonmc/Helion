package com.aryston.helion.render.rtx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jspecify.annotations.Nullable;

public final class RtxSectionInbox {
    static final int RADIUS = 4;
    private static final int CAPTURE_MARGIN = 1;

    private final ConcurrentLinkedQueue<RtxSectionUpdate> updates = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<RtxSectionPos> recompileRequests = new ConcurrentLinkedQueue<>();
    private volatile @Nullable RtxSectionPos center;

    public boolean wants(int sectionX, int sectionY, int sectionZ) {
        RtxSectionPos current = center;
        return current != null && current.within(sectionX, sectionY, sectionZ, RADIUS + CAPTURE_MARGIN);
    }

    public void offer(RtxSectionUpdate update) {
        updates.add(update);
    }

    public List<RtxSectionPos> takeRecompileRequests() {
        List<RtxSectionPos> taken = new ArrayList<>();
        for (RtxSectionPos request = recompileRequests.poll(); request != null; request = recompileRequests.poll()) {
            taken.add(request);
        }
        return taken;
    }

    void clear() {
        center = null;
        updates.clear();
        recompileRequests.clear();
    }

    void moveTo(RtxSectionPos moved) {
        center = moved;
    }

    @Nullable RtxSectionUpdate poll() {
        return updates.poll();
    }

    void requestRecompile(int sectionX, int sectionY, int sectionZ) {
        recompileRequests.add(new RtxSectionPos(sectionX, sectionY, sectionZ));
    }
}
