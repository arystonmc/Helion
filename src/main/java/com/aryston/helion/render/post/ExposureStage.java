package com.aryston.helion.render.post;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class ExposureStage implements RenderStage {
    private static final String NAME = "exposure";
    private static final String METER_PASS = "exposure_meter";
    private static final String AVERAGE_PASS = "exposure_average";
    private static final String METER_TARGET = "helion:exposure_meter";
    private static final String AVERAGE_TARGET = "helion:exposure_average";
    private static final int AVERAGE_SIZE = 1;

    private final ImageResources resources;
    private @Nullable ExposurePrograms programs;

    public ExposureStage(ImageResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().image().autoExposure()) {
            resources.adaptation().reset();
            return false;
        }
        if (frame.scene().colorFormat() != ImagePipelines.OUTPUT_FORMAT) {
            return false;
        }
        Optional<ExposurePrograms> compiled = ExposurePrograms.compile();
        if (compiled.isEmpty()) {
            resources.reportMissingExposurePipelines();
        }
        programs = compiled.orElse(null);
        return compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        ExposurePrograms compiled = Objects.requireNonNull(programs);
        ResourceHandle<RenderTarget> meter = addMeterPass(frame, compiled);
        FramePass pass = frame.graph().addPass(AVERAGE_PASS);
        pass.disableCulling();
        pass.reads(meter);
        ResourceHandle<RenderTarget> average = pass.createsInternal(AVERAGE_TARGET, target(AVERAGE_SIZE, AVERAGE_SIZE));
        pass.executes(frame.graph().timed(AVERAGE_PASS, () -> {
            FullscreenPass.draw("Helion Exposure Average", average.get(), compiled.average(), renderPass ->
                renderPass.setUniform(ImagePipelines.METER_SAMPLER, PostSampling.colorView(meter), PostSampling.nearest()));
            resources.requestExposureReadback(Objects.requireNonNull(average.get().getColorTexture()));
        }));
    }

    private ResourceHandle<RenderTarget> addMeterPass(FrameContext frame, ExposurePrograms compiled) {
        FramePass pass = frame.graph().addPass(METER_PASS);
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(scene);
        ResourceHandle<RenderTarget> meter = pass.createsInternal(
            METER_TARGET, target(ImagePipelines.EXPOSURE_METER_WIDTH, ImagePipelines.EXPOSURE_METER_HEIGHT)
        );
        pass.executes(frame.graph().timed(METER_PASS, () -> {
            GpuBuffer uniforms = resources.frameUniforms(frame);
            FullscreenPass.draw("Helion Exposure Meter", meter.get(), compiled.meter(), renderPass -> {
                renderPass.setUniform(ImagePipelines.SETTINGS, uniforms);
                renderPass.setUniform(ImagePipelines.SCENE_COLOR_SAMPLER, PostSampling.colorView(scene), PostSampling.nearest());
                renderPass.setUniform(ImagePipelines.SCENE_DEPTH_SAMPLER, PostSampling.depthView(scene), PostSampling.nearest());
            });
        }));
        return meter;
    }

    private static RenderTargetDescriptor target(int width, int height) {
        return new RenderTargetDescriptor(width, height, new RenderTargetDescriptor.TextureProperties(null, ImagePipelines.EXPOSURE_FORMAT), null);
    }
}
