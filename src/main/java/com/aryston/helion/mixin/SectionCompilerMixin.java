package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.RtxSectionCapture;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.List;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
    @Inject(
        method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
        at = @At("RETURN")
    )
    private void helion$captureRayTracingGeometry(
        SectionPos position,
        RenderSectionRegion region,
        VertexSorting sorting,
        SectionBufferBuilderPack builders,
        List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
        CallbackInfoReturnable<SectionCompiler.Results> callback
    ) {
        RtxSectionCapture.captured(position, region, callback.getReturnValue());
    }
}
