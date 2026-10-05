package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.LevelRendererAccessor;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;

public record LevelFrameRequest(
    LevelRenderer levelRenderer,
    GraphicsResourceAllocator resourceAllocator,
    boolean renderOutline,
    CameraRenderState cameraState,
    GpuBufferSlice terrainFog,
    Vector4f fogColor,
    boolean shouldRenderSky,
    boolean consistentDepthRequired
) {
    public LevelRendererAccessor level() {
        return (LevelRendererAccessor) levelRenderer;
    }
}
