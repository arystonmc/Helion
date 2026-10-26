package com.aryston.helion.mixin;

import com.aryston.helion.integration.RayTracingStartup;
import com.aryston.helion.render.rtx.RayTracing;
import com.aryston.helion.render.rtx.RtxFeature;
import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VulkanFeatureSets.class)
abstract class VulkanFeatureSetsMixin {
    @Inject(method = "optionalFeatureSets", at = @At("RETURN"))
    private static void helion$requestRayQuery(CallbackInfoReturnable<Set<FeatureSet>> callback) {
        RayTracingStartup.ensureRequested();
        if (RayTracing.requested()) {
            callback.getReturnValue().add(RtxFeature.FEATURE_SET);
            callback.getReturnValue().add(RtxFeature.ENTITY_TEXTURES);
        }
    }
}
