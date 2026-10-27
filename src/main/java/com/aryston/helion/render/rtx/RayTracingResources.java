package com.aryston.helion.render.rtx;

import static com.aryston.helion.Helion.LOGGER;

import com.aryston.helion.render.resource.GpuResources;
import com.mojang.renderpearl.api.GpuFormat;
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
    static final int REFLECTION_CONSTANT_BYTES = 112;
    static final int PATH_TRACE_CONSTANT_BYTES = 128;
    private static final String REFLECTION_SOURCE = "/assets/helion/shaders/rtx/reflection.csh";
    private static final String REFLECTION_NAME = "reflection.csh";
    private static final String REFLECTION_PIPELINE_LABEL = "Helion Ray Tracing Reflection Pipeline";
    private static final String REFLECTION_LABEL = "Helion Ray Tracing Reflections";
    private static final String PATH_TRACE_SOURCE = "/assets/helion/shaders/rtx/path_trace.csh";
    private static final String PATH_TRACE_NAME = "path_trace.csh";
    private static final String PATH_TRACE_PIPELINE_LABEL = "Helion Path Tracing Pipeline";
    private static final String RADIANCE_LABEL = "Helion Path Traced Radiance";
    private static final GpuFormat RADIANCE_FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final long PIPELINE_BYTES = 0L;
    private static final long LOG_INTERVAL_FRAMES = 600L;
    private static final long BYTES_PER_MIB = 1L << 20;
    private static final int[] REFLECTION_DESCRIPTORS = {
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
        KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
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
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
    };
    private static final int[] ENTITY_REFLECTION_COUNTS = {
        RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE,
        RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxBinding.SINGLE, RtxEntitySurfaces.TEXTURE_SLOTS, RtxBinding.SINGLE
    };
    private static final String ENTITY_REFLECTION_DEFINE = "HELION_RT_ENTITIES";
    private static final int[] PATH_TRACE_DESCRIPTORS = {
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
        KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
    };

    private final GpuResources resources;
    private final RtxSectionInbox inbox = new RtxSectionInbox();
    private final RtxEntityDraws entityDraws = new RtxEntityDraws();
    private @Nullable RtxDevice device;
    private @Nullable RtxSectionScene scene;
    private @Nullable RtxComputePipeline reflection;
    private @Nullable RtxImage reflectionImage;
    private @Nullable ByteBuffer reflectionConstants;
    private @Nullable RtxComputePipeline pathTrace;
    private @Nullable ByteBuffer pathTraceConstants;
    private @Nullable RtxImage radianceImage;
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
        if (radianceImage != null) {
            radianceImage.close(resources);
            radianceImage = null;
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

    boolean pathTracingAvailable() {
        return pathTrace != null;
    }

    RtxComputePipeline pathTracePipeline() {
        return Objects.requireNonNull(pathTrace);
    }

    ByteBuffer pathTraceConstants() {
        if (pathTraceConstants == null) {
            pathTraceConstants = MemoryUtil.memAlloc(PATH_TRACE_CONSTANT_BYTES);
        }
        return pathTraceConstants;
    }

    RtxImage radiance(int width, int height) {
        RtxImage current = radianceImage;
        if (current != null && current.fits(width, height)) {
            return current;
        }
        if (current != null) {
            current.close(resources);
        }
        RtxImage created = RtxImage.create(resources, RADIANCE_LABEL, width, height, RADIANCE_FORMAT);
        radianceImage = created;
        return created;
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
            "Helion ray tracing: {} sections built ({} MiB), {} pending, {} in the top level; CPU {} ms; GPU bottom levels {} ms, top level {} ms, path tracing {} ms, reflections {} ms",
            plan.builtSections(), plan.builtBytes() / BYTES_PER_MIB, plan.pendingSections(), plan.visibleSections(),
            String.format("%.3f", prepareMillis), millis(gpuMillis, RtxSceneStage.BOTTOM_STAGE), millis(gpuMillis, RtxSceneStage.TOP_STAGE),
            millis(gpuMillis, RtxPathTracingStage.TRACE_PASS), millis(gpuMillis, RtxReflectionStage.NAME)
        );
    }

    public void close() {
        if (scene != null) {
            scene.close();
        }
        if (reflectionImage != null) {
            reflectionImage.close(resources);
        }
        if (radianceImage != null) {
            radianceImage.close(resources);
        }
        if (reflectionConstants != null) {
            MemoryUtil.memFree(reflectionConstants);
        }
        if (pathTraceConstants != null) {
            MemoryUtil.memFree(pathTraceConstants);
        }
        scene = null;
        reflectionImage = null;
        reflectionConstants = null;
        pathTraceConstants = null;
        radianceImage = null;
    }

    private void open() {
        Optional<RtxDevice> found = RtxDevice.find();
        if (found.isEmpty()) {
            LOGGER.warn("Helion ray tracing is off: the device did not enable ray queries");
            return;
        }
        RtxDevice rtx = found.get();
        device = rtx;
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
        try {
            RtxComputePipeline traced = RtxComputePipeline.create(rtx, PATH_TRACE_SOURCE, PATH_TRACE_NAME, PATH_TRACE_DESCRIPTORS, PATH_TRACE_CONSTANT_BYTES);
            resources.track(PATH_TRACE_PIPELINE_LABEL, PIPELINE_BYTES, traced::destroy);
            pathTrace = traced;
        } catch (IllegalStateException exception) {
            LOGGER.error("Helion path traced light is off: {}", exception.getMessage());
        }
        LOGGER.info("Helion ray tracing is on: section scene within {} sections of the camera", RtxSectionInbox.RADIUS);
    }

    private static String millis(Map<String, Double> gpuMillis, String stage) {
        Double value = gpuMillis.get(stage);
        return value == null ? "-" : String.format("%.3f", value);
    }
}
