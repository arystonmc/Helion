package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.ChunkSectionsToRenderAccessor;
import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.geometry.TerrainSource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.OptionalDouble;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.profiling.Profiler;

final class VanillaTerrainSource implements TerrainSource {
    private static final int NO_ANISOTROPY = 1;

    private final LevelRendererAccessor level;
    private final ChunkSectionsToRender sections;
    private final SceneFog fog;
    private final StageEvents events;

    VanillaTerrainSource(LevelFrameRequest request, ChunkSectionsToRender sections, SceneFog fog, StageEvents events) {
        this.level = request.level();
        this.sections = sections;
        this.fog = fog;
        this.events = events;
    }

    @Override
    public void prepareFrame() {
        RenderSystem.setShaderFog(fog.current());
        if (level.helion$levelRenderState().shouldResetChunkLayerSampler || level.helion$chunkLayerSampler() == null) {
            replaceSampler();
        }
    }

    @Override
    public boolean supportsGeometryBuffer() {
        return !level.helion$levelRenderState().renderWireframeTerrain && VanillaGeometryPipelines.compiled();
    }

    @Override
    public void renderOpaque(RenderPass pass) {
        Profiler.get().push("solidTerrain");
        renderGroup(ChunkSectionLayerGroup.OPAQUE, pass);
        Profiler.get().pop();
        afterOpaque(pass);
    }

    @Override
    public void renderOpaqueGeometry(RenderPass geometryPass) {
        Profiler.get().push("solidTerrainGeometry");
        ChunkSectionsToRenderAccessor accessor = (ChunkSectionsToRenderAccessor) sections;
        GpuTextureView lightmap = level.helion$gameRenderer().lightmap();
        for (ChunkSectionLayer layer : ChunkSectionLayerGroup.OPAQUE.layers()) {
            VanillaGeometryPipelines.LayerPipelines pipelines = VanillaGeometryPipelines.forLayer(layer);
            accessor.helion$renderLayers(
                new ChunkSectionLayer[] {layer},
                chunkSampler(),
                geometryPass,
                blockAtlas(),
                lightmap,
                pipelines.direct(),
                pipelines.multiDraw()
            );
        }
        Profiler.get().pop();
    }

    @Override
    public void afterOpaque(RenderPass pass) {
        events.afterOpaqueBlocks(pass);
    }

    @Override
    public void renderTranslucent(RenderPass pass) {
        Profiler.get().push("translucentTerrain");
        renderGroup(ChunkSectionLayerGroup.TRANSLUCENT, pass);
        Profiler.get().pop();
        events.afterTranslucentBlocks(pass);
    }

    private void renderGroup(ChunkSectionLayerGroup group, RenderPass pass) {
        LevelRenderState state = level.helion$levelRenderState();
        sections.renderGroup(group, pass, chunkSampler(), blockAtlas(), state.renderWireframeTerrain);
    }

    private GpuSampler chunkSampler() {
        return Objects.requireNonNull(level.helion$chunkLayerSampler());
    }

    private GpuTextureView blockAtlas() {
        return level.helion$atlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getTextureView();
    }

    private void replaceSampler() {
        GpuSampler previous = level.helion$chunkLayerSampler();
        if (previous != null) {
            previous.close();
        }
        OptionsRenderState options = level.helion$optionsRenderState();
        int maxAnisotropy = options.textureFiltering == TextureFilteringMethod.ANISOTROPIC ? options.maxAnisotropyValue : NO_ANISOTROPY;
        level.helion$setChunkLayerSampler(RenderSystem.getDevice().createSampler(
            AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, maxAnisotropy, OptionalDouble.empty()
        ));
    }
}
