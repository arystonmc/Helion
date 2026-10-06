package com.aryston.helion.render.post;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.graph.RenderStage;
import com.aryston.helion.render.shader.FullscreenPass;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class BloomStage implements RenderStage {
    private static final String NAME = "bloom";
    private static final String PREFILTER_PASS = "bloom_prefilter";
    private static final String DOWNSAMPLE_PASS = "bloom_down_";
    private static final String UPSAMPLE_PASS = "bloom_up_";
    private static final String MIP_TARGET = "helion:bloom_mip_";
    private static final int MIN_SIZE = 1;
    private static final int BASE_LEVEL = 0;

    private final ImageResources resources;
    private @Nullable ImagePrograms programs;

    public BloomStage(ImageResources resources) {
        this.resources = resources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isActive(FrameContext frame) {
        if (!frame.settings().image().bloom().enabled()) {
            return false;
        }
        Optional<ImagePrograms> compiled = resources.programs(frame);
        programs = compiled.orElse(null);
        return compiled.isPresent();
    }

    @Override
    public void addTo(FrameContext frame) {
        ImagePrograms compiled = Objects.requireNonNull(programs);
        List<ResourceHandle<RenderTarget>> mips = new ArrayList<>();
        mips.add(addPrefilterPass(frame, compiled));
        for (int level = 1; level < ImageResources.BLOOM_MIP_COUNT; level++) {
            mips.add(addDownsamplePass(frame, compiled, level, mips.get(level - 1)));
        }
        for (int level = ImageResources.BLOOM_MIP_COUNT - 2; level >= BASE_LEVEL; level--) {
            mips.set(level, addUpsamplePass(frame, compiled, level, mips.get(level + 1), mips.get(level)));
        }
        frame.post().publishBloom(mips.getFirst());
    }

    private ResourceHandle<RenderTarget> addPrefilterPass(FrameContext frame, ImagePrograms compiled) {
        FramePass pass = frame.graph().addPass(PREFILTER_PASS);
        ResourceHandle<RenderTarget> scene = frame.targets().scene();
        pass.reads(scene);
        ResourceHandle<RenderTarget> mip = pass.createsInternal(MIP_TARGET + BASE_LEVEL, mipTarget(frame, BASE_LEVEL));
        pass.executes(frame.graph().timed(PREFILTER_PASS, () -> {
            GpuBuffer uniforms = resources.frameUniforms(frame);
            FullscreenPass.draw("Helion Bloom Prefilter", mip.get(), compiled.bloomPrefilter(), renderPass -> {
                renderPass.setUniform(ImagePipelines.SETTINGS, uniforms);
                renderPass.setUniform(ImagePipelines.SCENE_COLOR_SAMPLER, PostSampling.colorView(scene), PostSampling.linear());
                renderPass.setUniform(ImagePipelines.SCENE_DEPTH_SAMPLER, PostSampling.depthView(scene), PostSampling.nearest());
            });
        }));
        return mip;
    }

    private ResourceHandle<RenderTarget> addDownsamplePass(
        FrameContext frame,
        ImagePrograms compiled,
        int level,
        ResourceHandle<RenderTarget> source
    ) {
        String name = DOWNSAMPLE_PASS + level;
        FramePass pass = frame.graph().addPass(name);
        pass.reads(source);
        ResourceHandle<RenderTarget> mip = pass.createsInternal(MIP_TARGET + level, mipTarget(frame, level));
        pass.executes(frame.graph().timed(name, () -> FullscreenPass.draw(
            "Helion Bloom Downsample", mip.get(), compiled.bloomDownsample(), renderPass ->
                renderPass.setUniform(ImagePipelines.SOURCE_SAMPLER, PostSampling.colorView(source), PostSampling.linear()))));
        return mip;
    }

    private ResourceHandle<RenderTarget> addUpsamplePass(
        FrameContext frame,
        ImagePrograms compiled,
        int level,
        ResourceHandle<RenderTarget> source,
        ResourceHandle<RenderTarget> destination
    ) {
        String name = UPSAMPLE_PASS + level;
        FramePass pass = frame.graph().addPass(name);
        pass.reads(source);
        ResourceHandle<RenderTarget> mip = pass.readsAndWrites(destination);
        pass.executes(frame.graph().timed(name, () -> FullscreenPass.draw(
            "Helion Bloom Upsample", mip.get(), compiled.bloomUpsample(), renderPass ->
                renderPass.setUniform(ImagePipelines.SOURCE_SAMPLER, PostSampling.colorView(source), PostSampling.linear()))));
        return mip;
    }

    private static RenderTargetDescriptor mipTarget(FrameContext frame, int level) {
        int shift = level + 1;
        return new RenderTargetDescriptor(
            Math.max(MIN_SIZE, frame.scene().width() >> shift),
            Math.max(MIN_SIZE, frame.scene().height() >> shift),
            new RenderTargetDescriptor.TextureProperties(null, ImagePipelines.BLOOM_FORMAT),
            null
        );
    }
}
