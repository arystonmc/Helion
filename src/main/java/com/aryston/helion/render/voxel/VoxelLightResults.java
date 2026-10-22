package com.aryston.helion.render.voxel;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class VoxelLightResults {
    private @Nullable Targets targets;

    public Optional<Targets> targets() {
        return Optional.ofNullable(targets);
    }

    void publish(Targets written) {
        targets = written;
    }

    public record Targets(ResourceHandle<RenderTarget> light, ResourceHandle<RenderTarget> confidence) {
    }
}
