package com.aryston.helion.render.resource;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.GpuFormat;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class SceneTargets {
    private static final String LABEL = "Helion Scene";

    private final GpuResources resources;
    private @Nullable TextureTarget scene;
    private @Nullable TrackedResource tracked;

    public SceneTargets(GpuResources resources) {
        this.resources = resources;
    }

    public RenderTarget matching(RenderTarget main) {
        GpuFormat colorFormat = Objects.requireNonNull(main.getColorTexture()).getFormat();
        GpuFormat depthFormat = Objects.requireNonNull(main.getDepthTexture()).getFormat();
        if (scene == null || !hasFormats(scene, colorFormat, depthFormat)) {
            replace(new TextureTarget(LABEL, main.width, main.height, colorFormat, depthFormat), colorFormat, depthFormat);
        } else if (scene.width != main.width || scene.height != main.height) {
            scene.resize(main.width, main.height);
            retrack(colorFormat, depthFormat);
        }
        return scene;
    }

    public void close() {
        if (tracked != null) {
            resources.release(tracked);
        }
        scene = null;
        tracked = null;
    }

    private void replace(TextureTarget replacement, GpuFormat colorFormat, GpuFormat depthFormat) {
        close();
        scene = replacement;
        retrack(colorFormat, depthFormat);
    }

    private void retrack(GpuFormat colorFormat, GpuFormat depthFormat) {
        TextureTarget target = Objects.requireNonNull(scene);
        if (tracked != null) {
            resources.forget(tracked);
        }
        long bytes = (long) target.width * target.height * (colorFormat.blockSize() + depthFormat.blockSize());
        tracked = resources.track(LABEL, bytes, target::destroyBuffers);
    }

    private static boolean hasFormats(RenderTarget target, GpuFormat colorFormat, GpuFormat depthFormat) {
        return Objects.requireNonNull(target.getColorTexture()).getFormat() == colorFormat
            && Objects.requireNonNull(target.getDepthTexture()).getFormat() == depthFormat;
    }
}
