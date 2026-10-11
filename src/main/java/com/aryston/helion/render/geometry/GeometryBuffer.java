package com.aryston.helion.render.geometry;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class GeometryBuffer {
    private @Nullable Targets targets;
    private @Nullable ResourceHandle<RenderTarget> terrainDepth;

    public Optional<Targets> targets() {
        return Optional.ofNullable(targets);
    }

    public Optional<ResourceHandle<RenderTarget>> terrainDepth() {
        return Optional.ofNullable(terrainDepth);
    }

    void publish(Targets written) {
        targets = written;
    }

    void publishTerrainDepth(ResourceHandle<RenderTarget> written) {
        terrainDepth = written;
    }

    public record Targets(
        ResourceHandle<RenderTarget> normal,
        ResourceHandle<RenderTarget> light,
        ResourceHandle<RenderTarget> albedo
    ) {
    }
}
