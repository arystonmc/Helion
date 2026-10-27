package com.aryston.helion.render.rtx;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkWriteDescriptorSetAccelerationStructureKHR;

final class RtxComputePipeline {
    static final int WORKGROUP_SIZE = 8;

    private static final int SINGLE = 1;
    private static final int NO_OFFSET = 0;
    private static final int FIRST_SET = 0;

    private final VkDevice device;
    private final long setLayout;
    private final long pipelineLayout;
    private final long pipeline;
    private final long sampler;

    private RtxComputePipeline(VkDevice device, long setLayout, long pipelineLayout, long pipeline, long sampler) {
        this.device = device;
        this.setLayout = setLayout;
        this.pipelineLayout = pipelineLayout;
        this.pipeline = pipeline;
        this.sampler = sampler;
    }

    static RtxComputePipeline create(RtxDevice rtxDevice, String source, String name, int[] descriptorTypes, int pushConstantBytes) {
        int[] singles = new int[descriptorTypes.length];
        Arrays.fill(singles, SINGLE);
        return create(rtxDevice, source, name, Set.of(), descriptorTypes, singles, pushConstantBytes);
    }

    static RtxComputePipeline create(
        RtxDevice rtxDevice, String source, String name, Set<String> defines, int[] descriptorTypes, int[] descriptorCounts, int pushConstantBytes
    ) {
        VkDevice device = rtxDevice.vkDevice();
        ByteBuffer spirv = RtxShaderSource.compileCompute(source, name, defines);
        try {
            long setLayout = createSetLayout(device, descriptorTypes, descriptorCounts);
            long pipelineLayout = createPipelineLayout(device, setLayout, pushConstantBytes);
            long module = createModule(device, spirv);
            try {
                return new RtxComputePipeline(device, setLayout, pipelineLayout, createPipeline(device, pipelineLayout, module), createSampler(device));
            } finally {
                VK10.vkDestroyShaderModule(device, module, null);
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    void dispatch(VkCommandBuffer commandBuffer, List<RtxBinding> bindings, ByteBuffer constants, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(bindings.size(), stack);
            for (int binding = 0; binding < bindings.size(); binding++) {
                describe(writes.get(binding), binding, bindings.get(binding), stack);
            }
            KHRPushDescriptor.vkCmdPushDescriptorSetKHR(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, FIRST_SET, writes);
            VK10.vkCmdPushConstants(commandBuffer, pipelineLayout, VK10.VK_SHADER_STAGE_COMPUTE_BIT, NO_OFFSET, constants);
            VK10.vkCmdDispatch(commandBuffer, Math.ceilDiv(width, WORKGROUP_SIZE), Math.ceilDiv(height, WORKGROUP_SIZE), SINGLE);
        }
    }

    void destroy() {
        VK10.vkDestroyPipeline(device, pipeline, null);
        VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
        VK10.vkDestroyDescriptorSetLayout(device, setLayout, null);
        VK10.vkDestroySampler(device, sampler, null);
    }

    private void describe(VkWriteDescriptorSet write, int binding, RtxBinding bound, MemoryStack stack) {
        write.sType$Default().dstBinding(binding).descriptorType(bound.descriptorType());
        switch (bound) {
            case RtxBinding.StorageImage image -> write.pImageInfo(
                VkDescriptorImageInfo.calloc(SINGLE, stack).imageView(image.imageView()).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL)
            );
            case RtxBinding.Scene scene -> write.pNext(VkWriteDescriptorSetAccelerationStructureKHR.calloc(stack)
                .sType$Default()
                .accelerationStructureCount(SINGLE)
                .pAccelerationStructures(stack.longs(scene.accelerationStructure())));
            case RtxBinding.StorageBuffer buffer -> write.pBufferInfo(
                VkDescriptorBufferInfo.calloc(SINGLE, stack).buffer(buffer.buffer()).offset(buffer.offset()).range(buffer.range())
            );
            case RtxBinding.Sampled sampled -> write.pImageInfo(
                VkDescriptorImageInfo.calloc(SINGLE, stack).sampler(sampler).imageView(sampled.imageView()).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL)
            );
            case RtxBinding.SampledArray array -> write.pImageInfo(sampledArray(array.imageViews(), stack));
        }
        write.descriptorCount(bound.count());
    }

    private VkDescriptorImageInfo.Buffer sampledArray(long[] imageViews, MemoryStack stack) {
        VkDescriptorImageInfo.Buffer infos = VkDescriptorImageInfo.calloc(imageViews.length, stack);
        for (int element = 0; element < imageViews.length; element++) {
            infos.get(element).sampler(sampler).imageView(imageViews[element]).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
        }
        return infos;
    }

    private static long createSetLayout(VkDevice device, int[] descriptorTypes, int[] descriptorCounts) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(descriptorTypes.length, stack);
            for (int binding = 0; binding < descriptorTypes.length; binding++) {
                bindings.get(binding)
                    .binding(binding)
                    .descriptorType(descriptorTypes[binding])
                    .descriptorCount(descriptorCounts[binding])
                    .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            }
            VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .flags(KHRPushDescriptor.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR)
                .pBindings(bindings);
            LongBuffer handle = stack.mallocLong(1);
            RtxBuffer.check(VK10.vkCreateDescriptorSetLayout(device, info, null, handle), "descriptor set layout");
            return handle.get(0);
        }
    }

    private static long createPipelineLayout(VkDevice device, long setLayout, int pushConstantBytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer constants = VkPushConstantRange.calloc(SINGLE, stack)
                .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
                .offset(NO_OFFSET)
                .size(pushConstantBytes);
            VkPipelineLayoutCreateInfo info = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pSetLayouts(stack.longs(setLayout))
                .pPushConstantRanges(constants);
            LongBuffer handle = stack.mallocLong(1);
            RtxBuffer.check(VK10.vkCreatePipelineLayout(device, info, null, handle), "pipeline layout");
            return handle.get(0);
        }
    }

    private static long createModule(VkDevice device, ByteBuffer spirv) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(spirv);
            LongBuffer handle = stack.mallocLong(1);
            RtxBuffer.check(VK10.vkCreateShaderModule(device, info, null, handle), "shader module");
            return handle.get(0);
        }
    }

    private static long createPipeline(VkDevice device, long pipelineLayout, long module) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkComputePipelineCreateInfo.Buffer info = VkComputePipelineCreateInfo.calloc(SINGLE, stack)
                .sType$Default()
                .layout(pipelineLayout);
            info.stage()
                .sType$Default()
                .stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
                .module(module)
                .pName(stack.UTF8(RtxShaderSource.entry()));
            LongBuffer handle = stack.mallocLong(1);
            RtxBuffer.check(VK10.vkCreateComputePipelines(device, VK10.VK_NULL_HANDLE, info, null, handle), "compute pipeline");
            return handle.get(0);
        }
    }

    private static long createSampler(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack)
                .sType$Default()
                .magFilter(VK10.VK_FILTER_NEAREST)
                .minFilter(VK10.VK_FILTER_NEAREST)
                .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
            LongBuffer handle = stack.mallocLong(1);
            RtxBuffer.check(VK10.vkCreateSampler(device, info, null, handle), "sampler");
            return handle.get(0);
        }
    }
}
