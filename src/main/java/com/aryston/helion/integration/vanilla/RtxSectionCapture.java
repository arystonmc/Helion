package com.aryston.helion.integration.vanilla;

import static com.aryston.helion.Helion.LOGGER;

import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.rtx.RayTracing;
import com.aryston.helion.render.rtx.RtxSectionInbox;
import com.aryston.helion.render.rtx.RtxSectionPos;
import com.aryston.helion.render.rtx.RtxSectionUpdate;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;

public final class RtxSectionCapture {
    private static final List<ChunkSectionLayer> SOLID_LAYERS = List.of(ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT);
    private static final String POSITION = "Position";
    private static final String COMPACT_POSITION = "PositionLight";
    private static final int VERTICES_PER_QUAD = 4;
    private static final int FLOATS_PER_VERTEX = 3;
    private static final int COMPACT_MASK = 0xFFFF;
    private static final float COMPACT_STEP = 1.0F / 2048.0F;
    private static final float COMPACT_ORIGIN = 8.0F;
    private static final int SECOND = 1;
    private static final int THIRD = 2;
    private static final int UNKNOWN_FORMAT = -1;
    private static final int NO_QUADS = 0;
    private static volatile boolean unknownFormatReported;

    private RtxSectionCapture() {
    }

    public static void captured(SectionPos position, SectionCompiler.Results results) {
        if (!RayTracing.ENABLED) {
            return;
        }
        RtxSectionInbox inbox = HelionRenderCore.get().rayTracing().inbox();
        if (!inbox.wants(position.x(), position.y(), position.z())) {
            return;
        }
        int quads = 0;
        for (ChunkSectionLayer layer : SOLID_LAYERS) {
            MeshData mesh = results.renderedLayers.get(layer);
            quads += mesh == null ? NO_QUADS : mesh.drawState().vertexCount() / VERTICES_PER_QUAD;
        }
        float[] positions = new float[quads * VERTICES_PER_QUAD * FLOATS_PER_VERTEX];
        int written = 0;
        for (ChunkSectionLayer layer : SOLID_LAYERS) {
            MeshData mesh = results.renderedLayers.get(layer);
            if (mesh != null) {
                int copied = decode(mesh, positions, written);
                if (copied == UNKNOWN_FORMAT) {
                    return;
                }
                written += copied;
            }
        }
        inbox.offer(RtxSectionUpdate.mesh(position.x(), position.y(), position.z(), positions, quads));
    }

    public static void removed(long sectionNode) {
        if (RayTracing.ENABLED) {
            HelionRenderCore.get().rayTracing().inbox()
                .offer(RtxSectionUpdate.removed(SectionPos.x(sectionNode), SectionPos.y(sectionNode), SectionPos.z(sectionNode)));
        }
    }

    static void requestRecompiles() {
        if (!RayTracing.ENABLED) {
            return;
        }
        for (RtxSectionPos request : HelionRenderCore.get().rayTracing().inbox().takeRecompileRequests()) {
            Minecraft.getInstance().levelExtractor.setSectionDirty(request.sectionX(), request.sectionY(), request.sectionZ());
        }
    }

    private static int decode(MeshData mesh, float[] positions, int offset) {
        VertexFormat format = mesh.drawState().format();
        int vertices = mesh.drawState().vertexCount() - mesh.drawState().vertexCount() % VERTICES_PER_QUAD;
        int stride = format.getVertexSize();
        ByteBuffer data = mesh.vertexBuffer();
        VertexFormatElement plain = format.getElement(POSITION);
        VertexFormatElement compact = format.getElement(COMPACT_POSITION);
        if (plain == null && compact == null) {
            reportUnknown(format);
            return UNKNOWN_FORMAT;
        }
        int index = offset;
        for (int vertex = 0; vertex < vertices; vertex++) {
            int base = vertex * stride;
            if (plain != null) {
                int at = base + plain.offset();
                positions[index++] = data.getFloat(at);
                positions[index++] = data.getFloat(at + SECOND * Float.BYTES);
                positions[index++] = data.getFloat(at + THIRD * Float.BYTES);
            } else {
                int at = base + compact.offset();
                positions[index++] = compact(data.getShort(at));
                positions[index++] = compact(data.getShort(at + SECOND * Short.BYTES));
                positions[index++] = compact(data.getShort(at + THIRD * Short.BYTES));
            }
        }
        return index - offset;
    }

    private static float compact(short value) {
        return (value & COMPACT_MASK) * COMPACT_STEP - COMPACT_ORIGIN;
    }

    private static void reportUnknown(VertexFormat format) {
        if (!unknownFormatReported) {
            unknownFormatReported = true;
            LOGGER.warn("Helion ray tracing skips terrain in the unknown vertex format {}", format);
        }
    }
}
