package com.aryston.helion.render.rtx;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import com.mojang.renderpearl.backend.vulkan.init.VulkanPNextStruct;
import java.util.Set;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.KHRDeferredHostOperations;
import org.lwjgl.vulkan.KHRRayQuery;
import org.lwjgl.vulkan.VkPhysicalDeviceAccelerationStructureFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceRayQueryFeaturesKHR;

public final class RtxFeature {
    private static final VulkanPNextStruct ACCELERATION_STRUCTURE_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceAccelerationStructureFeaturesKHR.class);
    private static final VulkanPNextStruct RAY_QUERY_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceRayQueryFeaturesKHR.class);

    public static final FeatureSet FEATURE_SET = new FeatureSet(
        "Helion ray query spike",
        Set.of(
            KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
            KHRRayQuery.VK_KHR_RAY_QUERY_EXTENSION_NAME,
            KHRDeferredHostOperations.VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME
        ),
        Set.of(
            new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, "bufferDeviceAddress"),
            new VulkanFeature(ACCELERATION_STRUCTURE_FEATURES, "accelerationStructure"),
            new VulkanFeature(RAY_QUERY_FEATURES, "rayQuery")
        )
    );

    private RtxFeature() {
    }
}
