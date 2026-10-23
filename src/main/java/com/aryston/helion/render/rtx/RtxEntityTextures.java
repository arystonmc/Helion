package com.aryston.helion.render.rtx;

import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

final class RtxEntityTextures {
    static final int CAPACITY = 64;
    static final String DEFINE = "HELION_RT_ENTITY_TEXTURES " + CAPACITY;

    private static final int FRAMES_IN_FLIGHT = 4;
    private static final int SINGLE = 1;
    private static final int FIRST_BINDING = 0;
    private static final int FIRST_ELEMENT = 0;

    private final VkDevice device;
    private final long setLayout;
    private final long pool;
    private final long sampler;
    private final long[] sets;
    private int frame;

    private RtxEntityTextures(VkDevice device, long setLayout, long pool, long sampler, long[] sets) {
        this.device = device;
        this.setLayout = setLayout;
        this.pool = pool;
        this.sampler = sampler;
        this.sets = sets;
    }

    static RtxEntityTextures create(RtxDevice rtxDevice) {
        VkDevice device = rtxDevice.vkDevice();
        long setLayout = createSetLayout(device);
        long pool = createPool(device);
        return new RtxEntityTextures(device, setLayout, pool, RtxComputePipeline.createSampler(device), allocateSets(device, pool, setLayout));
    }

    long setLayout() {
        return setLayout;
    }

    long prepare(long[] views, int count, long fallbackView) {
        frame = (frame + 1) % FRAMES_IN_FLIGHT;
        long set = sets[frame];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer images = VkDescriptorImageInfo.calloc(CAPACITY, stack);
            for (int slot = 0; slot < CAPACITY; slot++) {
                images.get(slot).sampler(sampler).imageView(slot < count ? views[slot] : fallbackView).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
            }
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(SINGLE, stack);
            write.get(0)
                .sType$Default()
                .dstSet(set)
                .dstBinding(FIRST_BINDING)
                .dstArrayElement(FIRST_ELEMENT)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .pImageInfo(images)
                .descriptorCount(CAPACITY);
            VK10.vkUpdateDescriptorSets(device, write, null);
        }
        return set;
    }

    void destroy() {
        VK10.vkDestroyDescriptorPool(device, pool, null);
        VK10.vkDestroyDescriptorSetLayout(device, setLayout, null);
        VK10.vkDestroySampler(device, sampler, null);
    }

    private static long createSetLayout(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(SINGLE, stack);
            binding.get(0)
                .binding(FIRST_BINDING)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(CAPACITY)
                .stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
            VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(binding);
            LongBuffer handle = stack.mallocLong(1);
            RtxBuffer.check(VK10.vkCreateDescriptorSetLayout(device, info, null, handle), "entity texture set layout");
            return handle.get(0);
        }
    }

    private static long createPool(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer size = VkDescriptorPoolSize.calloc(SINGLE, stack);
            size.get(0).type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(CAPACITY * FRAMES_IN_FLIGHT);
            VkDescriptorPoolCreateInfo info = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default().maxSets(FRAMES_IN_FLIGHT).pPoolSizes(size);
            LongBuffer handle = stack.mallocLong(1);
            RtxBuffer.check(VK10.vkCreateDescriptorPool(device, info, null, handle), "entity texture pool");
            return handle.get(0);
        }
    }

    private static long[] allocateSets(VkDevice device, long pool, long setLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer layouts = stack.mallocLong(FRAMES_IN_FLIGHT);
            for (int frame = 0; frame < FRAMES_IN_FLIGHT; frame++) {
                layouts.put(frame, setLayout);
            }
            VkDescriptorSetAllocateInfo info = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default().descriptorPool(pool).pSetLayouts(layouts);
            LongBuffer handles = stack.mallocLong(FRAMES_IN_FLIGHT);
            RtxBuffer.check(VK10.vkAllocateDescriptorSets(device, info, handles), "entity texture sets");
            long[] sets = new long[FRAMES_IN_FLIGHT];
            handles.get(sets);
            return sets;
        }
    }
}
