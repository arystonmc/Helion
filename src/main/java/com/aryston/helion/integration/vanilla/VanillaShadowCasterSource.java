package com.aryston.helion.integration.vanilla;

import com.aryston.helion.mixin.ChunkSectionsToRenderAccessor;
import com.aryston.helion.mixin.LevelRendererAccessor;
import com.aryston.helion.render.shadow.ShadowCascade;
import com.aryston.helion.render.shadow.ShadowCasterSource;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

final class VanillaShadowCasterSource implements ShadowCasterSource {
    private static final float SECTION_BOUNDING_RADIUS = (float) (SectionPos.SECTION_HALF_SIZE * Math.sqrt(3.0));
    private static final List<ChunkSectionLayer> CASTER_LAYERS = List.of(ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT);
    private static final boolean IGNORE_TRANSLUCENT_ORDER = false;

    private final LevelFrameRequest request;
    private final LevelRendererAccessor level;
    private final List<ChunkSectionsToRender> prepared = new ArrayList<>();

    VanillaShadowCasterSource(LevelFrameRequest request) {
        this.request = request;
        this.level = request.level();
    }

    @Override
    public boolean supportsShadows() {
        return !level.helion$levelRenderState().renderWireframeTerrain
            && level.helion$viewArea() != null
            && VanillaShadowPipelines.compiled();
    }

    @Override
    public int renderDistanceBlocks() {
        return Objects.requireNonNull(level.helion$viewArea()).getViewDistance() * SectionPos.SECTION_SIZE;
    }

    @Override
    public void prepare(List<ShadowCascade> cascades) {
        Profiler.get().push("shadowCasters");
        prepared.clear();
        List<List<SectionRenderDispatcher.RenderSection>> sections = collectSections(cascades);
        ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = level.helion$visibleSections();
        List<SectionRenderDispatcher.RenderSection> cameraSections = new ArrayList<>(visible);
        try {
            for (int index = 0; index < cascades.size(); index++) {
                visible.clear();
                visible.addAll(sections.get(index));
                prepared.add(prepareCascade(cascades.get(index).lightView()));
            }
        } finally {
            visible.clear();
            visible.addAll(cameraSections);
            Profiler.get().pop();
        }
    }

    @Override
    public void renderCascade(int cascade, RenderPass pass) {
        ChunkSectionsToRenderAccessor sections = (ChunkSectionsToRenderAccessor) prepared.get(cascade);
        GpuTextureView atlas = level.helion$atlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getTextureView();
        GpuTextureView lightmap = level.helion$gameRenderer().lightmap();
        GpuSampler sampler = Objects.requireNonNull(level.helion$chunkLayerSampler());
        for (ChunkSectionLayer layer : CASTER_LAYERS) {
            VanillaGeometryPipelines.LayerPipelines pipelines = VanillaShadowPipelines.forLayer(layer);
            sections.helion$renderLayers(
                new ChunkSectionLayer[] {layer}, sampler, pass, atlas, lightmap, pipelines.direct(), pipelines.multiDraw()
            );
        }
    }

    private ChunkSectionsToRender prepareCascade(Matrix4fc lightView) {
        return level.helion$usingMultiDrawIndirectForTerrain()
            ? request.levelRenderer().prepareChunkRendersIndirect(lightView, IGNORE_TRANSLUCENT_ORDER)
            : request.levelRenderer().prepareChunkRenders(lightView, IGNORE_TRANSLUCENT_ORDER);
    }

    private List<List<SectionRenderDispatcher.RenderSection>> collectSections(List<ShadowCascade> cascades) {
        List<List<SectionRenderDispatcher.RenderSection>> sections = new ArrayList<>();
        cascades.forEach(cascade -> sections.add(new ArrayList<>()));
        ViewArea area = Objects.requireNonNull(level.helion$viewArea());
        Vec3 camera = request.cameraState().pos;
        SectionPos center = SectionPos.of(camera);
        int radius = area.getViewDistance();
        Matrix4fc lightView = cascades.getFirst().lightView();
        BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();
        Vector3f lightSpace = new Vector3f();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                for (int y = area.minSectionY(); y <= area.maxSectionY(); y++) {
                    origin.set(SectionPos.sectionToBlockCoord(x), SectionPos.sectionToBlockCoord(y), SectionPos.sectionToBlockCoord(z));
                    SectionRenderDispatcher.RenderSection section = area.getRenderSectionAt(origin);
                    if (section == null) {
                        continue;
                    }
                    lightSpace.set(
                        (float) (origin.getX() + SectionPos.SECTION_HALF_SIZE - camera.x),
                        (float) (origin.getY() + SectionPos.SECTION_HALF_SIZE - camera.y),
                        (float) (origin.getZ() + SectionPos.SECTION_HALF_SIZE - camera.z)
                    );
                    lightView.transformPosition(lightSpace);
                    for (int index = 0; index < cascades.size(); index++) {
                        if (cascades.get(index).containsLightSpace(lightSpace, SECTION_BOUNDING_RADIUS)) {
                            sections.get(index).add(section);
                        }
                    }
                }
            }
        }
        return sections;
    }
}
