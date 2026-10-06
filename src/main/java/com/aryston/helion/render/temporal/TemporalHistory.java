package com.aryston.helion.render.temporal;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

final class TemporalHistory {
    private static final String LABEL = "Helion Temporal History";
    private static final int TARGET_COUNT = 2;

    private final GpuResources resources;
    private List<TextureTarget> targets = List.of();
    private @Nullable TrackedResource tracked;
    private int written;

    TemporalHistory(GpuResources resources) {
        this.resources = resources;
    }

    boolean ensureSize(int width, int height) {
        if (!targets.isEmpty() && targets.getFirst().width == width && targets.getFirst().height == height) {
            return false;
        }
        close();
        List<TextureTarget> created = IntStream.range(0, TARGET_COUNT)
            .mapToObj(index -> new TextureTarget(LABEL + " #" + index, width, height, TemporalPipelines.HISTORY_FORMAT, null))
            .toList();
        long bytes = (long) width * height * TemporalPipelines.HISTORY_FORMAT.blockSize() * TARGET_COUNT;
        targets = created;
        tracked = resources.track(LABEL, bytes, () -> created.forEach(TextureTarget::destroyBuffers));
        return true;
    }

    RenderTarget previous() {
        return targets.get(written);
    }

    RenderTarget next() {
        return targets.get((written + 1) % TARGET_COUNT);
    }

    void swap() {
        written = (written + 1) % TARGET_COUNT;
    }

    void close() {
        if (tracked != null) {
            resources.release(tracked);
        }
        tracked = null;
        targets = List.of();
    }
}
