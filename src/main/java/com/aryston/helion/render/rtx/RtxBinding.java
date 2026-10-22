package com.aryston.helion.render.rtx;

import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VK10;

sealed interface RtxBinding {
    int descriptorType();

    record StorageImage(long imageView) implements RtxBinding {
        @Override
        public int descriptorType() {
            return VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        }
    }

    record Scene(long accelerationStructure) implements RtxBinding {
        @Override
        public int descriptorType() {
            return KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
        }
    }

    record StorageBuffer(long buffer, long offset, long range) implements RtxBinding {
        @Override
        public int descriptorType() {
            return VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        }
    }

    record Sampled(long imageView) implements RtxBinding {
        @Override
        public int descriptorType() {
            return VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        }
    }
}
