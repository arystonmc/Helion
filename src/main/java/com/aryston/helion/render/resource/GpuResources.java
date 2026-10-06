package com.aryston.helion.render.resource;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;

public final class GpuResources {
    private final List<TrackedResource> live = new ArrayList<>();

    public TrackedResource track(String label, long bytes, Runnable releaseAction) {
        TrackedResource resource = new TrackedResource(label, bytes, releaseAction);
        live.add(resource);
        return resource;
    }

    public void release(TrackedResource resource) {
        if (live.remove(resource)) {
            RenderSystem.queueFencedTask(resource.releaseAction());
        }
    }

    public void forget(TrackedResource resource) {
        live.remove(resource);
    }

    public int releaseAll() {
        int released = live.size();
        live.forEach(resource -> resource.releaseAction().run());
        live.clear();
        return released;
    }

    public List<TrackedResource> live() {
        return List.copyOf(live);
    }

    public int count() {
        return live.size();
    }

    public long totalBytes() {
        return live.stream().mapToLong(TrackedResource::bytes).sum();
    }
}
