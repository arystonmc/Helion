package com.aryston.helion.render.water;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

final class GlassRainMaps {
    private static final String BEAD_LABEL = "Helion Glass Rain Beads";
    private static final String STREAK_LABEL = "Helion Glass Rain Streaks";
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
    private static final GpuFormat FORMAT = GpuFormat.RGBA8_UNORM;
    private static final int SINGLE_LAYER = 1;
    private static final int FIRST_LAYER = 0;
    private static final int ORIGIN = 0;

    private final GpuResources resources;
    private final List<TrackedResource> tracked = new ArrayList<>();
    private @Nullable GpuTextureView beads;
    private @Nullable GpuTextureView streaks;

    GlassRainMaps(GpuResources resources) {
        this.resources = resources;
    }

    GpuTextureView beads() {
        if (beads == null) {
            beads = upload(BEAD_LABEL, GlassRainPattern::beads);
        }
        return beads;
    }

    GpuTextureView streaks() {
        if (streaks == null) {
            streaks = upload(STREAK_LABEL, GlassRainPattern::streaks);
        }
        return streaks;
    }

    void close() {
        tracked.forEach(resources::release);
        tracked.clear();
        beads = null;
        streaks = null;
    }

    private GpuTextureView upload(String label, Supplier<GlassRainImage> pattern) {
        GlassRainImage image = pattern.get();
        int levels = image.levels().size();
        GpuTexture texture = RenderSystem.getDevice().createTexture(label, USAGE, FORMAT, image.width(), image.height(), SINGLE_LAYER, levels);
        GpuTextureView view = RenderSystem.getDevice().createTextureView(texture);
        long bytes = 0L;
        for (int level = 0; level < levels; level++) {
            byte[] texels = image.levels().get(level);
            ByteBuffer data = MemoryUtil.memAlloc(texels.length);
            try {
                data.put(texels).flip();
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, data, level, FIRST_LAYER, ORIGIN, ORIGIN, image.levelWidth(level), image.levelHeight(level));
            } finally {
                MemoryUtil.memFree(data);
            }
            bytes += texels.length;
        }
        tracked.add(resources.track(label, bytes, () -> {
            view.close();
            texture.close();
        }));
        return view;
    }
}
