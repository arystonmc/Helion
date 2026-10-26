package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;

final class RtxImage {
    private static final GpuFormat FORMAT = GpuFormat.RGBA8_UNORM;
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING | RtxSpike.TEXTURE_USAGE_STORAGE;
    private static final int SINGLE_LAYER = 1;
    private static final int SINGLE_MIP = 1;

    private final int width;
    private final int height;
    private final GpuTextureView view;
    private final TrackedResource tracked;

    private RtxImage(int width, int height, GpuTextureView view, TrackedResource tracked) {
        this.width = width;
        this.height = height;
        this.view = view;
        this.tracked = tracked;
    }

    static RtxImage create(GpuResources resources, String label, int width, int height) {
        return create(resources, label, width, height, FORMAT);
    }

    static RtxImage create(GpuResources resources, String label, int width, int height, GpuFormat format) {
        GpuTexture texture = RenderSystem.getDevice().createTexture(label, USAGE, format, width, height, SINGLE_LAYER, SINGLE_MIP);
        GpuTextureView view = RenderSystem.getDevice().createTextureView(texture);
        TrackedResource tracked = resources.track(label, (long) width * height * format.blockSize(), () -> {
            view.close();
            texture.close();
        });
        return new RtxImage(width, height, view, tracked);
    }

    boolean fits(int requestedWidth, int requestedHeight) {
        return width == requestedWidth && height == requestedHeight;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    GpuTextureView view() {
        return view;
    }

    long vkImageView() {
        return ((VulkanGpuTextureView) view).vkImageView();
    }

    void close(GpuResources resources) {
        resources.release(tracked);
    }
}
