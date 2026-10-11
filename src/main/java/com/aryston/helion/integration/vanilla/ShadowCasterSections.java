package com.aryston.helion.integration.vanilla;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jspecify.annotations.Nullable;

final class ShadowCasterSections {
    private static final int REFRESH_INTERVAL_FRAMES = 20;

    private final List<SectionRenderDispatcher.RenderSection> sections = new ArrayList<>();
    private @Nullable ViewArea area;
    private long cameraSection = Long.MIN_VALUE;
    private int framesSinceRefresh;

    List<SectionRenderDispatcher.RenderSection> withGeometry(ViewArea viewArea, SectionPos camera, List<ChunkSectionLayer> layers) {
        framesSinceRefresh++;
        if (viewArea != area || camera.asLong() != cameraSection || framesSinceRefresh >= REFRESH_INTERVAL_FRAMES) {
            refresh(viewArea, camera, layers);
        }
        return sections;
    }

    private void refresh(ViewArea viewArea, SectionPos camera, List<ChunkSectionLayer> layers) {
        area = viewArea;
        cameraSection = camera.asLong();
        framesSinceRefresh = 0;
        sections.clear();
        int radius = viewArea.getViewDistance();
        BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();
        for (int x = camera.x() - radius; x <= camera.x() + radius; x++) {
            for (int z = camera.z() - radius; z <= camera.z() + radius; z++) {
                for (int y = viewArea.minSectionY(); y <= viewArea.maxSectionY(); y++) {
                    origin.set(SectionPos.sectionToBlockCoord(x), SectionPos.sectionToBlockCoord(y), SectionPos.sectionToBlockCoord(z));
                    SectionRenderDispatcher.RenderSection section = viewArea.getRenderSectionAt(origin);
                    if (section != null && hasGeometry(section.getSectionMesh(), layers)) {
                        sections.add(section);
                    }
                }
            }
        }
    }

    private static boolean hasGeometry(SectionMesh mesh, List<ChunkSectionLayer> layers) {
        for (ChunkSectionLayer layer : layers) {
            if (!mesh.isEmpty(layer)) {
                return true;
            }
        }
        return false;
    }
}
