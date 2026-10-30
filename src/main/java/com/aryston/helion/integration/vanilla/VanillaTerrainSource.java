package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.ChunkSectionsToRenderAccessor;
import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.geometry.GeometryBufferPipelines;
import com.aryston.helion.render.geometry.GlossyCells;
import com.aryston.helion.render.geometry.TerrainSource;
import com.aryston.helion.render.geometry.TerrainTextures;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import org.joml.Vector4f;

final class VanillaTerrainSource implements TerrainSource {
    private static final int NO_ANISOTROPY = 1;
    private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
    private static final Identifier WATER_FLOW = Identifier.withDefaultNamespace("block/water_flow");
    private static final Identifier WATER_OVERLAY = Identifier.withDefaultNamespace("block/water_overlay");

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
    public void renderOpaqueGeometry(RenderPass geometryPass, boolean seeThroughGlass) {
        Profiler.get().push("solidTerrainGeometry");
        for (ChunkSectionLayer layer : ChunkSectionLayerGroup.OPAQUE.layers()) {
            renderLayer(layer, geometryPass, VanillaGeometryPipelines.forLayer(layer, seeThroughGlass));
        }
        Profiler.get().pop();
    }

    @Override
    public void afterOpaque(RenderPass pass) {
        events.afterOpaqueBlocks(pass);
    }

    @Override
    public void renderTranslucent(RenderPass pass, Optional<GpuTextureView> seeThroughGlass) {
        Profiler.get().push("translucentTerrain");
        Optional<VanillaGeometryPipelines.LayerPipelines> seeThrough = seeThroughGlass.flatMap(ignored -> VanillaGeometryPipelines.seeThroughTranslucent());
        if (seeThrough.isPresent()) {
            pass.setUniform(GeometryBufferPipelines.GLOSSY_SAMPLER, seeThroughGlass.get(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            renderLayer(ChunkSectionLayer.TRANSLUCENT, pass, seeThrough.get());
        } else {
            renderGroup(ChunkSectionLayerGroup.TRANSLUCENT, pass);
        }
        Profiler.get().pop();
        events.afterTranslucentBlocks(pass);
    }

    @Override
    public boolean supportsWaterSurface() {
        return !level.helion$levelRenderState().renderWireframeTerrain && VanillaWaterPipelines.surface().isPresent();
    }

    @Override
    public void renderWaterSurface(RenderPass waterPass) {
        Profiler.get().push("waterSurface");
        VanillaWaterPipelines.surface().ifPresent(pipelines -> renderLayer(ChunkSectionLayer.TRANSLUCENT, waterPass, pipelines));
        Profiler.get().pop();
    }

    @Override
    public void renderGlassSurface(RenderPass glassPass) {
        Profiler.get().push("glassSurface");
        VanillaWaterPipelines.surface().ifPresent(pipelines -> renderLayer(ChunkSectionLayer.TRANSLUCENT, glassPass, pipelines));
        VanillaWaterPipelines.glass().ifPresent(pipelines -> renderLayer(ChunkSectionLayer.CUTOUT, glassPass, pipelines));
        Profiler.get().pop();
    }

    @Override
    public TerrainTextures textures() {
        TextureAtlas atlas = level.helion$atlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        return new TerrainTextures(atlas.getTextureView(), level.helion$gameRenderer().lightmap(), bounds(atlas, WATER_STILL), bounds(atlas, WATER_FLOW), bounds(atlas, WATER_OVERLAY));
    }

    @Override
    public GlossyCells glossyCells() {
        return VanillaGlossyCells.of(level.helion$atlasManager().getAtlasOrThrow(AtlasIds.BLOCKS));
    }

    private static Vector4f bounds(TextureAtlas atlas, Identifier sprite) {
        TextureAtlasSprite found = atlas.getSprite(sprite);
        return new Vector4f(found.getU0(), found.getV0(), found.getU1(), found.getV1());
    }

    private void renderGroup(ChunkSectionLayerGroup group, RenderPass pass) {
        LevelRenderState state = level.helion$levelRenderState();
        sections.renderGroup(group, pass, chunkSampler(), blockAtlas(), state.renderWireframeTerrain);
    }

    private void renderLayer(ChunkSectionLayer layer, RenderPass pass, VanillaGeometryPipelines.LayerPipelines pipelines) {
        ((ChunkSectionsToRenderAccessor) sections).helion$renderLayers(
            new ChunkSectionLayer[] {layer},
            chunkSampler(),
            pass,
            blockAtlas(),
            level.helion$gameRenderer().lightmap(),
            pipelines.direct(),
            pipelines.multiDraw()
        );
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
