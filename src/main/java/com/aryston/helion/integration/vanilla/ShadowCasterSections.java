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
    private final List<SectionRenderDispatcher.RenderSection> sections = new ArrayList<>();
    private @Nullable ViewArea area;

    List<SectionRenderDispatcher.RenderSection> withGeometry(ViewArea viewArea, List<ChunkSectionLayer> layers) {
        if (viewArea != area || ShadowCasterChanges.overflowed()) {
            rebuild(viewArea, layers);
        } else {
            applyChanges(layers);
        }
        return sections;
    }

    private void rebuild(ViewArea viewArea, List<ChunkSectionLayer> layers) {
        ShadowCasterChanges.clear();
        sections.forEach(section -> member(section).helion$setCasterIndex(ShadowCasterMember.NOT_A_CASTER));
        sections.clear();
        area = viewArea;
        SectionPos center = viewArea.getCameraSectionPos();
        int radius = viewArea.getViewDistance();
        BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                for (int y = viewArea.minSectionY(); y <= viewArea.maxSectionY(); y++) {
                    origin.set(SectionPos.sectionToBlockCoord(x), SectionPos.sectionToBlockCoord(y), SectionPos.sectionToBlockCoord(z));
                    SectionRenderDispatcher.RenderSection section = viewArea.getRenderSectionAt(origin);
                    if (section != null && hasGeometry(section.getSectionMesh(), layers)) {
                        add(section);
                    }
                }
            }
        }
    }

    private void applyChanges(List<ChunkSectionLayer> layers) {
        SectionRenderDispatcher.RenderSection section;
        while ((section = ShadowCasterChanges.next()) != null) {
            int index = member(section).helion$casterIndex();
            boolean caster = hasGeometry(section.getSectionMesh(), layers);
            if (caster && index == ShadowCasterMember.NOT_A_CASTER) {
                add(section);
            } else if (!caster && index != ShadowCasterMember.NOT_A_CASTER) {
                remove(index);
            }
        }
    }

    private void add(SectionRenderDispatcher.RenderSection section) {
        member(section).helion$setCasterIndex(sections.size());
        sections.add(section);
    }

    private void remove(int index) {
        SectionRenderDispatcher.RenderSection removed = sections.get(index);
        SectionRenderDispatcher.RenderSection last = sections.removeLast();
        if (last != removed) {
            sections.set(index, last);
            member(last).helion$setCasterIndex(index);
        }
        member(removed).helion$setCasterIndex(ShadowCasterMember.NOT_A_CASTER);
    }

    private static ShadowCasterMember member(SectionRenderDispatcher.RenderSection section) {
        return (ShadowCasterMember) section;
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
