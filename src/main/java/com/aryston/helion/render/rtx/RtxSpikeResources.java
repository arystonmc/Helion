package com.aryston.helion.render.rtx;

import static com.aryston.helion.Helion.LOGGER;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.aryston.helion.render.voxel.VoxelBrickSink;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;
import org.lwjgl.vulkan.VK10;

public final class RtxSpikeResources {
    private static final String PIPELINE_LABEL = "Helion RTX Spike Pipeline";
    private static final String AGREEMENT_LABEL = "Helion RTX Spike Agreement";
    private static final String SCENE_LABEL = "Helion RTX Spike Scene";
    private static final String MASK_LABEL = "Helion RTX Spike Shadow Mask";
    private static final long REBUILD_INTERVAL_FRAMES = 10L;
    private static final long LOG_INTERVAL_FRAMES = 600L;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final long NO_REVISION = -1L;
    private static final long BYTES_PER_KIB = 1024L;
    private static final long PIPELINE_BYTES = 0L;
    private static final String SOURCE = "/assets/helion/shaders/rtx/shadow_mask.csh";
    private static final String SHADER_NAME = "shadow_mask.csh";
    private static final int PUSH_CONSTANT_BYTES = 128;
    private static final int[] DESCRIPTORS = {
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
        KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR,
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
        VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
        VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
    };

    private final GpuResources resources;
    private @Nullable RtxBlocks blocks;
    private @Nullable RtxDevice device;
    private @Nullable RtxComputePipeline pipeline;
    private @Nullable RtxAgreement agreement;
    private @Nullable RtxShadowMask mask;
    private @Nullable RtxScene scene;
    private @Nullable TrackedResource trackedScene;
    private @Nullable ByteBuffer constants;
    private int @Nullable [] exposed;
    private boolean checked;
    private long sceneRevision = NO_REVISION;
    private long lastBuildFrame;
    private long lastLogFrame;
    private long frame;
    private String lastBuild = "none";

    public RtxSpikeResources(GpuResources resources) {
        this.resources = resources;
    }

    public VoxelBrickSink blocks() {
        return rtxBlocks();
    }

    private RtxBlocks rtxBlocks() {
        if (blocks == null) {
            blocks = new RtxBlocks();
        }
        return blocks;
    }

    boolean available() {
        if (!checked) {
            checked = true;
            open();
        }
        return pipeline != null;
    }

    RtxDevice device() {
        return Objects.requireNonNull(device);
    }

    RtxComputePipeline pipeline() {
        return Objects.requireNonNull(pipeline);
    }

    RtxAgreement agreement() {
        return Objects.requireNonNull(agreement);
    }

    RtxTraceMode nextMode() {
        frame++;
        return RtxTraceMode.forFrame(frame);
    }

    ByteBuffer constants() {
        if (constants == null) {
            constants = MemoryUtil.memAlloc(PUSH_CONSTANT_BYTES);
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

    RtxScene scene(RtxArea area) {
        RtxBlocks source = rtxBlocks();
        long revision = source.revision(area);
        RtxScene current = scene;
        boolean stale = current == null || !current.area().equals(area) || revision != sceneRevision;
        if (current != null && (!stale || frame - lastBuildFrame < REBUILD_INTERVAL_FRAMES)) {
            return current;
        }
        return rebuild(source, area, revision);
    }

    void logIfDue(Map<String, Double> gpuMillis) {
        if (frame - lastLogFrame < LOG_INTERVAL_FRAMES) {
            return;
        }
        lastLogFrame = frame;
        LOGGER.info(
            "Helion RTX spike: GPU ray query {} ms, grid walk {} ms, compare {} ms, BLAS {} ms, TLAS {} ms, voxel_light {} ms; last build {}; {}",
            millis(gpuMillis, RtxTraceMode.RAY_QUERY.stage()), millis(gpuMillis, RtxTraceMode.GRID_WALK.stage()),
            millis(gpuMillis, RtxTraceMode.COMPARE.stage()), millis(gpuMillis, RtxShadowStage.BOTTOM_STAGE),
            millis(gpuMillis, RtxShadowStage.TOP_STAGE), millis(gpuMillis, RtxShadowStage.VOXEL_LIGHT_STAGE),
            lastBuild, agreement().takeSummary()
        );
    }

    public void close() {
        if (trackedScene != null) {
            resources.release(trackedScene);
        }
        if (mask != null) {
            mask.close(resources);
        }
        if (constants != null) {
            MemoryUtil.memFree(constants);
        }
        trackedScene = null;
        scene = null;
        mask = null;
        constants = null;
    }

    private RtxScene rebuild(RtxBlocks source, RtxArea area, long revision) {
        RtxDevice rtx = device();
        int[] indices = exposed;
        if (indices == null) {
            indices = new int[RtxBlocks.areaBlocks()];
            exposed = indices;
        }
        long start = System.nanoTime();
        int count = source.collectExposed(area, indices);
        long gathered = System.nanoTime();
        RtxScene created = RtxScene.create(rtx, area, indices, count);
        long allocated = System.nanoTime();
        if (trackedScene != null) {
            resources.release(trackedScene);
        }
        trackedScene = resources.track(SCENE_LABEL, created.bytes(), () -> created.destroy(rtx.vkDevice()));
        scene = created;
        sceneRevision = revision;
        lastBuildFrame = frame;
        lastBuild = String.format(
            "%d boxes, gather %.2f ms, allocate %.2f ms, %d KiB",
            count, (gathered - start) / NANOS_PER_MILLI, (allocated - gathered) / NANOS_PER_MILLI, created.bytes() / BYTES_PER_KIB
        );
        LOGGER.info("Helion RTX spike scene: {}", lastBuild);
        return created;
    }

    private void open() {
        Optional<RtxDevice> found = RtxDevice.find();
        if (found.isEmpty()) {
            LOGGER.warn("Helion RTX spike is off: the device did not enable ray queries");
            return;
        }
        RtxDevice rtx = found.get();
        RtxComputePipeline created;
        try {
            created = RtxComputePipeline.create(rtx, SOURCE, SHADER_NAME, DESCRIPTORS, PUSH_CONSTANT_BYTES);
        } catch (IllegalStateException exception) {
            LOGGER.error("Helion RTX spike is off: {}", exception.getMessage());
            return;
        }
        RtxAgreement counters = RtxAgreement.create(rtx);
        resources.track(PIPELINE_LABEL, PIPELINE_BYTES, created::destroy);
        resources.track(AGREEMENT_LABEL, counters.bytes(), counters::destroy);
        device = rtx;
        pipeline = created;
        agreement = counters;
        LOGGER.info("Helion RTX spike is on: ray query pipeline compiled");
    }

    private static String millis(Map<String, Double> gpuMillis, String stage) {
        Double value = gpuMillis.get(stage);
        return value == null ? "-" : String.format("%.3f", value);
    }
}
