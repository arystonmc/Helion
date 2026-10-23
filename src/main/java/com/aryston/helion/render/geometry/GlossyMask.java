package com.aryston.helion.render.geometry;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public final class GlossyMask {
    private static final String LABEL = "Helion Glossy Mask";
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
    private static final GpuFormat FORMAT = GpuFormat.R8_UNORM;
    private static final int SINGLE_LAYER = 1;
    private static final int SINGLE_MIP = 1;
    private static final int BASE_LEVEL = 0;
    private static final int FIRST_LAYER = 0;
    private static final int ORIGIN = 0;

    private final GpuResources resources;
    private @Nullable GpuTextureView view;
    private @Nullable TrackedResource tracked;
    private @Nullable GpuTextureView atlas;
    private @Nullable ByteBuffer upload;

    public GlossyMask(GpuResources resources) {
        this.resources = resources;
    }

    public GpuTextureView update(GlossyCells cells) {
        if (view != null && cells.atlas() == atlas) {
            return view;
        }
        close();
        GpuTexture texture = RenderSystem.getDevice().createTexture(LABEL, USAGE, FORMAT, cells.width(), cells.height(), SINGLE_LAYER, SINGLE_MIP);
        GpuTextureView created = RenderSystem.getDevice().createTextureView(texture);
        tracked = resources.track(LABEL, (long) cells.width() * cells.height() * FORMAT.blockSize(), () -> {
            created.close();
            texture.close();
        });
        ByteBuffer data = MemoryUtil.memAlloc(cells.cells().length);
        data.put(cells.cells()).flip();
        upload = data;
        RenderSystem.getDevice().createCommandEncoder()
            .writeToTexture(texture, data, BASE_LEVEL, FIRST_LAYER, ORIGIN, ORIGIN, cells.width(), cells.height());
        view = created;
        atlas = cells.atlas();
        return created;
    }

    public void close() {
        if (tracked != null) {
            resources.release(tracked);
        }
        if (upload != null) {
            MemoryUtil.memFree(upload);
        }
        tracked = null;
        view = null;
        atlas = null;
        upload = null;
    }
}