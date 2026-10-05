package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.LevelFrameRequest;
import com.aryston.helion.integration.vanilla.LevelRenderHook;
import com.aryston.helion.render.HelionRenderCore;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @WrapMethod(method = "render")
    private void helion$render(
        GraphicsResourceAllocator resourceAllocator,
        boolean renderOutline,
        CameraRenderState cameraState,
        GpuBufferSlice terrainFog,
        Vector4f fogColor,
        boolean shouldRenderSky,
        boolean consistentDepthRequired,
        Operation<Void> original
    ) {
        LevelFrameRequest request = new LevelFrameRequest(
            (LevelRenderer) (Object) this,
            resourceAllocator,
            renderOutline,
            cameraState,
            terrainFog,
            fogColor,
            shouldRenderSky,
            consistentDepthRequired
        );
        LevelRenderHook.render(request, () -> original.call(
            resourceAllocator, renderOutline, cameraState, terrainFog, fogColor, shouldRenderSky, consistentDepthRequired
        ));
    }

    @WrapOperation(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"
        )
    )
    private RenderTarget helion$redirectMainTarget(GameRenderer gameRenderer, Operation<RenderTarget> original) {
        return HelionRenderCore.get().resolveMainTarget(original.call(gameRenderer));
    }
}
