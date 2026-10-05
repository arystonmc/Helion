package com.aryston.helion.render.backend;

import com.mojang.renderpearl.api.device.DeviceInfo;

public record GpuDeviceSummary(String name, String vendor, String backend, String driver, boolean zeroToOneDepth, float timestampPeriod) {
    private static final String VULKAN_BACKEND = "Vulkan";

    public static GpuDeviceSummary of(DeviceInfo info) {
        return new GpuDeviceSummary(
            info.name(),
            info.vendorName(),
            info.backendName(),
            info.driverInfo(),
            info.isZZeroToOne(),
            info.timestampPeriod()
        );
    }

    public boolean isVulkan() {
        return VULKAN_BACKEND.equals(backend);
    }

    public boolean supportsTimestamps() {
        return timestampPeriod > 0.0F;
    }
}
