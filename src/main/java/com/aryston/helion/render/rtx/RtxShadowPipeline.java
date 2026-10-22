package com.aryston.helion.render.rtx;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;
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

final class RtxShadowPipeline {
    static final int PUSH_CONSTANT_BYTES = 128;
    static final int WORKGROUP_SIZE = 8;

    private static final String SOURCE = "/assets/helion/shaders/rtx/shadow_mask.csh";
    private static final String NAME = "shadow_mask.csh";
    private static final int MASK_BINDING = 0;
    private static final int SCENE_BINDING = 1;
    private static final int AGREEMENT_BINDING = 2;
    private static final int BOXES_BINDING = 3;
    private static final int FIRST_SAMPLER_BINDING = 4;
    private static final int SAMPLED_TEXTURES = 7;
    private static final int BINDINGS = FIRST_SAMPLER_BINDING + SAMPLED_TEXTURES;
    private static final int SINGLE = 1;
    private static final int NO_OFFSET = 0;

    private final VkDevice device;
    private final long setLayout;
    private final long pipelineLayout;
    private final long pipeline;
    private final long sampler;

    private RtxShadowPipeline(VkDevice device, long setLayout, long pipelineLayout, long pipeline, long sampler) {
        this.device = device;
        this.setLayout = setLayout;
        this.pipelineLayout = pipelineLayout;
        this.pipeline = pipeline;
        this.sampler = sampler;
    }

    static RtxShadowPipeline create(RtxDevice rtxDevice) {
        VkDevice device = rtxDevice.vkDevice();
        ByteBuffer spirv = RtxShaderSource.compileCompute(SOURCE, NAME);
        try {
            long setLayout = createSetLayout(device);
            long pipelineLayout = createPipelineLayout(device, setLayout);
            long module = createModule(device, spirv);
            try {
                return new RtxShadowPipeline(device, setLayout, pipelineLayout, createPipeline(device, pipelineLayout, module), createSampler(device));
            } finally {
                VK10.vkDestroyShaderModule(device, module, null);
            }
        } finally {
            MemoryUtil.memFree(spirv);
        }
    }

    void record(VkCommandBuffer commandBuffer, RtxTraceInputs inputs, ByteBuffer constants, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(BINDINGS, stack);
            writes.get(MASK_BINDING)
                .sType$Default()
                .dstBinding(MASK_BINDING)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(SINGLE)
                .pImageInfo(VkDescriptorImageInfo.calloc(SINGLE, stack).imageView(inputs.maskView()).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL));
            VkWriteDescriptorSetAccelerationStructureKHR scene = VkWriteDescriptorSetAccelerationStructureKHR.calloc(stack)
                .sType$Default()
                .accelerationStructureCount(SINGLE)
                .pAccelerationStructures(stack.longs(inputs.scene()));
            writes.get(SCENE_BINDING)
                .sType$Default()
                .pNext(scene)
                .dstBinding(SCENE_BINDING)
                .descriptorType(KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR)
                .descriptorCount(SINGLE);
            writeBuffer(writes.get(AGREEMENT_BINDING), AGREEMENT_BINDING, inputs.agreement(), inputs.agreementOffset(), inputs.agreementRange(), stack);
            writeBuffer(writes.get(BOXES_BINDING), BOXES_BINDING, inputs.boxes(), NO_OFFSET, inputs.boxesRange(), stack);
            for (int index = 0; index < SAMPLED_TEXTURES; index++) {
                writes.get(FIRST_SAMPLER_BINDING + index)
                    .sType$Default()
                    .dstBinding(FIRST_SAMPLER_BINDING + index)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(SINGLE)
                    .pImageInfo(VkDescriptorImageInfo.calloc(SINGLE, stack)
                        .sampler(sampler)
                        .imageView(inputs.sampledViews()[index])
                        .imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL));
            }
            KHRPushDescriptor.vkCmdPushDescriptorSetKHR(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0, writes);
            VK10.vkCmdPushConstants(commandBuffer, pipelineLayout, VK10.VK_SHADER_STAGE_COMPUTE_BIT, NO_OFFSET, constants);
            VK10.vkCmdDispatch(commandBuffer, Math.ceilDiv(width, WORKGROUP_SIZE), Math.ceilDiv(height, WORKGROUP_SIZE), SINGLE);
        }
    }

    static int sampledTextures() {
        return SAMPLED_TEXTURES;
    }

    void destroy() {
        VK10.vkDestroyPipeline(device, pipeline, null);
        VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
        VK10.vkDestroyDescriptorSetLayout(device, setLayout, null);
        VK10.vkDestroySampler(device, sampler, null);
    }

    private static void writeBuffer(VkWriteDescriptorSet write, int binding, long buffer, long offset, long range, MemoryStack stack) {
        write.sType$Default()
            .dstBinding(binding)
            .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
            .descriptorCount(SINGLE)
            .pBufferInfo(VkDescriptorBufferInfo.calloc(SINGLE, stack).buffer(buffer).offset(offset).range(range));
    }

    private static int descriptorType(int binding) {
        return switch (binding) {
            case MASK_BINDING -> VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
            case SCENE_BINDING -> KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
            case AGREEMENT_BINDING, BOXES_BINDING -> VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            default -> VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        };
    }

    private static long createSetLayout(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(BINDINGS, stack);
            for (int binding = 0; binding < BINDINGS; binding++) {
                bindings.get(binding)
                    .binding(binding)
                    .descriptorType(descriptorType(binding))
                    .descriptorCount(SINGLE)
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

    private static long createPipelineLayout(VkDevice device, long setLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer constants = VkPushConstantRange.calloc(SINGLE, stack)
                .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT)
                .offset(NO_OFFSET)
                .size(PUSH_CONSTANT_BYTES);
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
