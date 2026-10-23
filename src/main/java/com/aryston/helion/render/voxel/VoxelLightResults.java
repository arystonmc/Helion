package com.aryston.helion.render.voxel;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class VoxelLightResults {
    private @Nullable Targets targets;
    private List<ResourceHandle<RenderTarget>> probes = List.of();
    private @Nullable ResourceHandle<RenderTarget> bounce;

    public Optional<Targets> targets() {
        return Optional.ofNullable(targets);
    }

    List<ResourceHandle<RenderTarget>> probes() {
        return probes;
    }

    void publishProbes(List<ResourceHandle<RenderTarget>> written) {
        probes = written;
    }

    Optional<ResourceHandle<RenderTarget>> bounce() {
        return Optional.ofNullable(bounce);
    }

    void publishBounce(ResourceHandle<RenderTarget> written) {
        bounce = written;
    }

    void publish(Targets written) {
        targets = written;
    }

    public record Targets(ResourceHandle<RenderTarget> light, ResourceHandle<RenderTarget> confidence, ResourceHandle<RenderTarget> bounce) {
    }
}
