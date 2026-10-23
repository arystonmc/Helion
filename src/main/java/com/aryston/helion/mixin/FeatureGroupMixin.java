package com.aryston.helion.mixin;

import com.aryston.helion.integration.vanilla.EntityGeometryDraws;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.List;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.oit.OitStage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.feature.FeatureRenderDispatcher$PreparedGroup")
abstract class FeatureGroupMixin {
    @Shadow
    @Final
    private FeatureRendererType<?> featureType;

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void helion$skipOutsideGeometryBuffer(
        FeatureFrameContext context, @Nullable OitStage stage, RenderPass renderPass, FeatureRendererMap featureRenderers, List<SubmitNode> submits, CallbackInfo callback
    ) {
        if (EntityGeometryDraws.skipsGroup(featureRenderers.getOrThrow(featureType))) {
            callback.cancel();
        }
    }
}
