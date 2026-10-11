package com.aryston.helion.render.post;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public final class ColorGradingResources {
    private static final String LABEL = "Helion Color Grading";
    private static final GpuFormat FORMAT = GpuFormat.RGBA8_UNORM;
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
    private static final int SINGLE_LAYER = 1;
    private static final int SINGLE_MIP = 1;
    private static final int BASE_LEVEL = 0;
    private static final int FIRST_LAYER = 0;
    private static final int ORIGIN = 0;
    private static final int BYTES = ColorGradingLut.WIDTH * ColorGradingLut.HEIGHT * ColorGradingLut.BYTES_PER_TEXEL;

    private final GpuResources resources;
    private @Nullable GpuTexture texture;
    private @Nullable GpuTextureView view;
    private @Nullable TrackedResource tracked;
    private @Nullable ColorGrade written;

    public ColorGradingResources(GpuResources resources) {
        this.resources = resources;
    }

    GpuTextureView lookupTable(ColorGrade grade) {
        if (texture == null) {
            GpuTexture created = RenderSystem.getDevice()
                .createTexture(LABEL, USAGE, FORMAT, ColorGradingLut.WIDTH, ColorGradingLut.HEIGHT, SINGLE_LAYER, SINGLE_MIP);
            GpuTextureView createdView = RenderSystem.getDevice().createTextureView(created);
            texture = created;
            view = createdView;
            tracked = resources.track(LABEL, BYTES, () -> {
                createdView.close();
                created.close();
            });
            written = null;
        }
        if (grade != written) {
            upload(grade);
        }
        return Objects.requireNonNull(view);
    }

    public void close() {
        if (tracked != null) {
            resources.release(tracked);
        }
        tracked = null;
        texture = null;
        view = null;
        written = null;
    }

    private void upload(ColorGrade grade) {
        ByteBuffer data = MemoryUtil.memAlloc(BYTES);
        try {
            ColorGradingLut.build(grade, data);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                Objects.requireNonNull(texture), data, BASE_LEVEL, FIRST_LAYER, ORIGIN, ORIGIN, ColorGradingLut.WIDTH, ColorGradingLut.HEIGHT
            );
            written = grade;
        } finally {
            MemoryUtil.memFree(data);
        }
    }
}
