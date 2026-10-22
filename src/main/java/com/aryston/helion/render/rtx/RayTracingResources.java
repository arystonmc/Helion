package com.aryston.helion.render.rtx;

import static com.aryston.helion.Helion.LOGGER;

import com.aryston.helion.render.resource.GpuResources;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VK10;

public final class RayTracingResources {
    static final int SUN_SHADOW_CONSTANT_BYTES = 96;

    private static final String SUN_SHADOW_SOURCE = "/assets/helion/shaders/rtx/sun_shadow.csh";
    private static final String SUN_SHADOW_NAME = "sun_shadow.csh";
    private static final String PIPELINE_LABEL = "Helion Ray Tracing Sun Shadow Pipeline";
    private static final String MASK_LABEL = "Helion Ray Tracing Sun Shadow Mask";
    private static final long PIPELINE_BYTES = 0L;
    private static final long LOG_INTERVAL_FRAMES = 600L;
    private static final long BYTES_PER_MIB = 1L << 20;
    private static final int[] SUN_SHADOW_DESCRIPTORS = {
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
        KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
    };

    private final GpuResources resources;
    private final RtxSectionInbox inbox = new RtxSectionInbox();
    private @Nullable RtxDevice device;
    private @Nullable RtxSectionScene scene;
    private @Nullable RtxComputePipeline sunShadow;
    private @Nullable RtxShadowMask mask;
    private @Nullable ByteBuffer constants;
    private boolean checked;
    private boolean reportedComplete;
    private long frame;
    private long lastLogFrame;

    public RayTracingResources(GpuResources resources) {
        this.resources = resources;
    }

    public RtxSectionInbox inbox() {
        return inbox;
    }

    boolean available() {
        if (!checked) {
            checked = true;
            open();
        }
        return scene != null;
    }

    RtxDevice device() {
        return Objects.requireNonNull(device);
    }

    RtxSectionScene scene() {
        return Objects.requireNonNull(scene);
    }

    RtxComputePipeline sunShadow() {
        return Objects.requireNonNull(sunShadow);
    }

    ByteBuffer constants() {
        if (constants == null) {
            constants = MemoryUtil.memAlloc(SUN_SHADOW_CONSTANT_BYTES);
        }
        return constants;
    }

    RtxShadowMask mask(int width, int height) {
        RtxShadowMask current = mask;
        if (current != null && current.fits(width, height)) {
            return current;
        }
        if (current != null) {
            current.close(resources);
        }
        RtxShadowMask created = RtxShadowMask.create(resources, MASK_LABEL, width, height);
        mask = created;
        return created;
    }

    Optional<RtxShadowMask> currentMask() {
        return Optional.ofNullable(mask);
    }

    void logIfDue(RtxScenePlan plan, double prepareMillis, Map<String, Double> gpuMillis) {
        frame++;
        boolean firstComplete = !reportedComplete && plan.pendingSections() == 0 && plan.builtSections() > 0;
        if (!firstComplete && frame - lastLogFrame < LOG_INTERVAL_FRAMES) {
            return;
        }
        reportedComplete |= firstComplete;
        lastLogFrame = frame;
        LOGGER.info(
            "Helion ray tracing: {} sections built ({} MiB), {} pending, {} in the top level; CPU {} ms; GPU bottom levels {} ms, top level {} ms, sun shadow {} ms",
            plan.builtSections(), plan.builtBytes() / BYTES_PER_MIB, plan.pendingSections(), plan.visibleSections(),
            String.format("%.3f", prepareMillis), millis(gpuMillis, RtxSceneStage.BOTTOM_STAGE), millis(gpuMillis, RtxSceneStage.TOP_STAGE),
            millis(gpuMillis, RtxSunShadowStage.NAME)
        );
    }

    public void close() {
        if (scene != null) {
            scene.close();
        }
        if (mask != null) {
            mask.close(resources);
        }
        if (constants != null) {
            MemoryUtil.memFree(constants);
        }
        scene = null;
        mask = null;
        constants = null;
    }

    private void open() {
        Optional<RtxDevice> found = RtxDevice.find();
        if (found.isEmpty()) {
            LOGGER.warn("Helion ray tracing is off: the device did not enable ray queries");
            return;
        }
        RtxDevice rtx = found.get();
        RtxComputePipeline pipeline;
        try {
            pipeline = RtxComputePipeline.create(rtx, SUN_SHADOW_SOURCE, SUN_SHADOW_NAME, SUN_SHADOW_DESCRIPTORS, SUN_SHADOW_CONSTANT_BYTES);
        } catch (IllegalStateException exception) {
            LOGGER.error("Helion ray tracing is off: {}", exception.getMessage());
            return;
        }
        resources.track(PIPELINE_LABEL, PIPELINE_BYTES, pipeline::destroy);
        device = rtx;
        sunShadow = pipeline;
        scene = new RtxSectionScene(rtx, resources);
        LOGGER.info("Helion ray tracing is on: section scene within {} sections of the camera", RtxSectionInbox.RADIUS);
    }

    private static String millis(Map<String, Double> gpuMillis, String stage) {
        Double value = gpuMillis.get(stage);
        return value == null ? "-" : String.format("%.3f", value);
    }
}
