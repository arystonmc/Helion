package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.EntityGeometryDraws;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PreparedRenderType.class)
abstract class PreparedRenderTypeMixin {
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void helion$skipOtherEntityPass(StagedVertexBuffer.ExecuteInfo info, RenderPass renderPass, RenderPipeline pipeline, CallbackInfo callback) {
        if (EntityGeometryDraws.skipsDraw(pipeline)) {
            callback.cancel();
        }
    }

    @ModifyArg(
        method = "draw",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getCompiledPipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;")
    )
    private RenderPipeline helion$writeGeometryBuffer(RenderPipeline pipeline) {
        return EntityGeometryDraws.substitute(pipeline);
    }
}
