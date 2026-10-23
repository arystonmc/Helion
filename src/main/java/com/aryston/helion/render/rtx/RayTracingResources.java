package com.aryston.helion.render.rtx;

import static com.aryston.helion.Helion.LOGGER;

import com.aryston.helion.render.resource.GpuResources;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VK10;

public final class RayTracingResources {
    static final int SUN_SHADOW_CONSTANT_BYTES = 112;
    static final int REFLECTION_CONSTANT_BYTES = 96;

    private static final String SUN_SHADOW_SOURCE = "/assets/helion/shaders/rtx/sun_shadow.csh";
    private static final String SUN_SHADOW_NAME = "sun_shadow.csh";
    private static final String REFLECTION_SOURCE = "/assets/helion/shaders/rtx/reflection.csh";
    private static final String REFLECTION_NAME = "reflection.csh";
    private static final String REFLECTION_PIPELINE_LABEL = "Helion Ray Tracing Reflection Pipeline";
    private static final String REFLECTION_LABEL = "Helion Ray Tracing Reflections";
    private static final String PIPELINE_LABEL = "Helion Ray Tracing Sun Shadow Pipeline";
    private static final String MASK_LABEL = "Helion Ray Tracing Sun Shadow Mask";
    private static final long PIPELINE_BYTES = 0L;
    private static final long LOG_INTERVAL_FRAMES = 600L;
    private static final long BYTES_PER_MIB = 1L << 20;
    private static final int[] SUN_SHADOW_DESCRIPTORS = {
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
        KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
    };
    private static final int[] REFLECTION_DESCRIPTORS = {
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
        KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
    };
    private static final int[] ENTITY_REFLECTION_DESCRIPTORS = {
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
        KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
    };
    private static final int[] ENTITY_REFLECTION_COUNTS = {
        RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE,
        RtxEntitySurfaces.TEXTURE_SLOTS, RtxBinding.SINGLE
    };
    private static final String ENTITY_REFLECTION_DEFINE = "HELION_RT_ENTITIES";

    private final GpuResources resources;
    private final RtxSectionInbox inbox = new RtxSectionInbox();
    private final RtxEntityDraws entityDraws = new RtxEntityDraws();
    private @Nullable RtxDevice device;
    private @Nullable RtxSectionScene scene;
    private @Nullable RtxComputePipeline sunShadow;
    private @Nullable RtxImage mask;
    private @Nullable ByteBuffer constants;
    private @Nullable RtxComputePipeline reflection;
    private @Nullable RtxImage reflectionImage;
    private @Nullable ByteBuffer reflectionConstants;
    private boolean checked;
    private boolean reportedComplete;
    private long frame;
    private long lastLogFrame;

    public RayTracingResources(GpuResources resources) {
        this.resources = resources;
    }

    public RtxEntityDraws entityDraws() {
        return entityDraws;
    }

    public RtxSectionInbox inbox() {
        return inbox;
    }

    boolean available() {
        if (!checked) {
            checked = true;
            open();
        }
        if (scene == null && device != null) {
            scene = new RtxSectionScene(device, resources);
        }
        return scene != null;
    }

    void suspend() {
        if (scene == null) {
            return;
        }
        scene.close();
        scene = null;
        inbox.clear();
        entityDraws.capture(false);
        if (mask != null) {
            mask.close(resources);
            mask = null;
        }
        if (reflectionImage != null) {
            reflectionImage.close(resources);
            reflectionImage = null;
        }
        LOGGER.info("Helion ray tracing is paused: its scene was released");
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

    boolean reflectionAvailable() {
        return reflection != null;
    }

    RtxComputePipeline reflectionPipeline() {
        return Objects.requireNonNull(reflection);
    }

    ByteBuffer reflectionConstants() {
        if (reflectionConstants == null) {
            reflectionConstants = MemoryUtil.memAlloc(REFLECTION_CONSTANT_BYTES);
        }
        return reflectionConstants;
    }

    RtxImage reflection(int width, int height) {
        RtxImage current = reflectionImage;
        if (current != null && current.fits(width, height)) {
            return current;
        }
        if (current != null) {
            current.close(resources);
        }
        RtxImage created = RtxImage.create(resources, REFLECTION_LABEL, width, height);
        reflectionImage = created;
        return created;
    }

    ByteBuffer constants() {
        if (constants == null) {
            constants = MemoryUtil.memAlloc(SUN_SHADOW_CONSTANT_BYTES);
        }
        return constants;
    }

    RtxImage mask(int width, int height) {
        RtxImage current = mask;
        if (current != null && current.fits(width, height)) {
            return current;
        }
        if (current != null) {
            current.close(resources);
        }
        RtxImage created = RtxImage.create(resources, MASK_LABEL, width, height);
        mask = created;
        return created;
    }

    Optional<RtxImage> currentMask() {
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
            "Helion ray tracing: {} sections built ({} MiB), {} pending, {} in the top level; CPU {} ms; GPU bottom levels {} ms, top level {} ms, sun shadow {} ms, reflections {} ms",
            plan.builtSections(), plan.builtBytes() / BYTES_PER_MIB, plan.pendingSections(), plan.visibleSections(),
            String.format("%.3f", prepareMillis), millis(gpuMillis, RtxSceneStage.BOTTOM_STAGE), millis(gpuMillis, RtxSceneStage.TOP_STAGE),
            millis(gpuMillis, RtxShadowTracer.NAME), millis(gpuMillis, RtxReflectionStage.NAME)
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
        if (reflectionImage != null) {
            reflectionImage.close(resources);
        }
        if (reflectionConstants != null) {
            MemoryUtil.memFree(reflectionConstants);
        }
        scene = null;
        mask = null;
        constants = null;
        reflectionImage = null;
        reflectionConstants = null;
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
        try {
            RtxComputePipeline traced = rtx.entityTextures()
                ? RtxComputePipeline.create(
                    rtx, REFLECTION_SOURCE, REFLECTION_NAME, Set.of(ENTITY_REFLECTION_DEFINE), ENTITY_REFLECTION_DESCRIPTORS, ENTITY_REFLECTION_COUNTS,
                    REFLECTION_CONSTANT_BYTES
                )
                : RtxComputePipeline.create(rtx, REFLECTION_SOURCE, REFLECTION_NAME, REFLECTION_DESCRIPTORS, REFLECTION_CONSTANT_BYTES);
            resources.track(REFLECTION_PIPELINE_LABEL, PIPELINE_BYTES, traced::destroy);
            reflection = traced;
        } catch (IllegalStateException exception) {
            LOGGER.error("Helion ray traced reflections are off: {}", exception.getMessage());
        }
        LOGGER.info("Helion ray tracing is on: section scene within {} sections of the camera", RtxSectionInbox.RADIUS);
    }

    private static String millis(Map<String, Double> gpuMillis, String stage) {
        Double value = gpuMillis.get(stage);
        return value == null ? "-" : String.format("%.3f", value);
    }
}
