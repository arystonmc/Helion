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
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;

public final class RtxSectionCapture {
    private static final List<ChunkSectionLayer> LAYERS = List.of(ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT, ChunkSectionLayer.TRANSLUCENT);
    private static final String POSITION = "Position";
    private static final String COMPACT_POSITION = "PositionLight";
    private static final String COLOR = "Color";
    private static final String UV = "UV0";
    private static final String COMPACT_UV = "PackedUV0";
    private static final String LIGHT = "UV2";
    private static final int VERTICES_PER_QUAD = 4;
    private static final int FLOATS_PER_VERTEX = 3;
    private static final int COMPACT_MASK = 0xFFFF;
    private static final float COMPACT_STEP = 1.0F / 2048.0F;
    private static final float COMPACT_ORIGIN = 8.0F;
    private static final float UV_SCALE = 65536.0F;
    private static final int UV_MAX = 0xFFFF;
    private static final int BYTE_MASK = 0xFF;
    private static final int SECOND_SHIFT = 8;
    private static final int THIRD_SHIFT = 16;
    private static final int FOURTH_SHIFT = 24;
    private static final int SECOND = 1;
    private static final int THIRD = 2;
    private static final int FOURTH = 3;
    private static final int NORMAL_INT = 3;
    private static final float NORMAL_SCALE = 32767.0F;
    private static final float MIN_AREA = 1.0e-12F;
    private static final int UNKNOWN_FORMAT = -1;
    private static final int NO_QUADS = 0;
    private static volatile boolean unknownFormatReported;

    private RtxSectionCapture() {
    }

    public static void captured(SectionPos position, RenderSectionRegion region, SectionCompiler.Results results) {
        if (!RayTracing.requested()) {
            return;
        }
        RtxSectionInbox inbox = HelionRenderCore.get().rayTracing().inbox();
        if (!inbox.wants(position.x(), position.y(), position.z())) {
            return;
        }
        int quads = 0;
        int solidQuads = 0;
        int translucentFrom = 0;
        for (ChunkSectionLayer layer : LAYERS) {
            MeshData mesh = results.renderedLayers.get(layer);
            quads += mesh == null ? NO_QUADS : mesh.drawState().vertexCount() / VERTICES_PER_QUAD;
            if (layer == ChunkSectionLayer.SOLID) {
                solidQuads = quads;
            } else if (layer == ChunkSectionLayer.CUTOUT) {
                translucentFrom = quads;
            }
        }
        int vertices = quads * VERTICES_PER_QUAD;
        float[] positions = new float[vertices * FLOATS_PER_VERTEX];
        int[] surface = new int[vertices * RtxSectionUpdate.SURFACE_INTS_PER_VERTEX];
        int written = 0;
        for (ChunkSectionLayer layer : LAYERS) {
            MeshData mesh = results.renderedLayers.get(layer);
            if (mesh != null) {
                int copied = decode(mesh, positions, surface, written);
                if (copied == UNKNOWN_FORMAT) {
                    return;
                }
                written += copied;
            }
        }
        inbox.offer(RtxSectionUpdate.mesh(position.x(), position.y(), position.z(), positions, surface, quads, solidQuads, translucentFrom, RtxSectionLights.scan(position, region)));
    }

    public static void removed(long sectionNode) {
        if (RayTracing.requested()) {
            HelionRenderCore.get().rayTracing().inbox()
                .offer(RtxSectionUpdate.removed(SectionPos.x(sectionNode), SectionPos.y(sectionNode), SectionPos.z(sectionNode)));
        }
    }

    static void requestRecompiles() {
        if (!RayTracing.requested()) {
            return;
        }
        for (RtxSectionPos request : HelionRenderCore.get().rayTracing().inbox().takeRecompileRequests()) {
            Minecraft.getInstance().levelExtractor.setSectionDirty(request.sectionX(), request.sectionY(), request.sectionZ());
        }
    }

    private static int decode(MeshData mesh, float[] positions, int[] surface, int firstVertex) {
        VertexFormat format = mesh.drawState().format();
        int vertices = mesh.drawState().vertexCount() - mesh.drawState().vertexCount() % VERTICES_PER_QUAD;
        int stride = format.getVertexSize();
        ByteBuffer data = mesh.vertexBuffer();
        VertexFormatElement plain = format.getElement(POSITION);
        VertexFormatElement compact = format.getElement(COMPACT_POSITION);
        VertexFormatElement color = format.getElement(COLOR);
        VertexFormatElement uv = format.getElement(plain != null ? UV : COMPACT_UV);
        VertexFormatElement light = plain != null ? format.getElement(LIGHT) : compact;
        if ((plain == null && compact == null) || color == null || uv == null || light == null) {
            reportUnknown(format);
            return UNKNOWN_FORMAT;
        }
        int index = firstVertex * FLOATS_PER_VERTEX;
        int attribute = firstVertex * RtxSectionUpdate.SURFACE_INTS_PER_VERTEX;
        for (int vertex = 0; vertex < vertices; vertex++) {
            int base = vertex * stride;
            if (plain != null) {
                int at = base + plain.offset();
                positions[index++] = data.getFloat(at);
                positions[index++] = data.getFloat(at + SECOND * Float.BYTES);
                positions[index++] = data.getFloat(at + THIRD * Float.BYTES);
                surface[attribute++] = packUv(data.getFloat(base + uv.offset()), data.getFloat(base + uv.offset() + Float.BYTES));
            } else {
                int at = base + compact.offset();
                positions[index++] = compact(data.getShort(at));
                positions[index++] = compact(data.getShort(at + SECOND * Short.BYTES));
                positions[index++] = compact(data.getShort(at + THIRD * Short.BYTES));
                surface[attribute++] = (data.getShort(base + uv.offset()) & COMPACT_MASK)
                    | (data.getShort(base + uv.offset() + Short.BYTES) & COMPACT_MASK) << THIRD_SHIFT;
            }
            surface[attribute++] = packBytes(data, base + color.offset());
            surface[attribute++] = plain != null
                ? (data.getShort(base + light.offset()) & BYTE_MASK) | (data.getShort(base + light.offset() + Short.BYTES) & BYTE_MASK) << SECOND_SHIFT
                : data.getShort(base + light.offset() + FOURTH * Short.BYTES) & COMPACT_MASK;
            attribute++;
        }
        writeNormals(positions, surface, firstVertex, vertices);
        return vertices;
    }

    private static void writeNormals(float[] positions, int[] surface, int firstVertex, int vertices) {
        for (int quad = firstVertex; quad < firstVertex + vertices; quad += VERTICES_PER_QUAD) {
            int packed = packNormal(positions, quad);
            for (int corner = 0; corner < VERTICES_PER_QUAD; corner++) {
                surface[(quad + corner) * RtxSectionUpdate.SURFACE_INTS_PER_VERTEX + NORMAL_INT] = packed;
            }
        }
    }

    private static int packNormal(float[] positions, int quad) {
        int a = quad * FLOATS_PER_VERTEX;
        int b = a + FLOATS_PER_VERTEX;
        int c = b + FLOATS_PER_VERTEX;
        int d = c + FLOATS_PER_VERTEX;
        float ux = positions[c] - positions[a];
        float uy = positions[c + SECOND] - positions[a + SECOND];
        float uz = positions[c + THIRD] - positions[a + THIRD];
        float vx = positions[d] - positions[b];
        float vy = positions[d + SECOND] - positions[b + SECOND];
        float vz = positions[d + THIRD] - positions[b + THIRD];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float sum = Math.abs(nx) + Math.abs(ny) + Math.abs(nz);
        if (sum < MIN_AREA) {
            return 0;
        }
        float x = nx / sum;
        float y = ny / sum;
        if (nz < 0.0F) {
            float foldedX = (1.0F - Math.abs(y)) * sign(x);
            y = (1.0F - Math.abs(x)) * sign(y);
            x = foldedX;
        }
        return (Math.round(x * NORMAL_SCALE) & COMPACT_MASK) | (Math.round(y * NORMAL_SCALE) & COMPACT_MASK) << THIRD_SHIFT;
    }

    private static float sign(float value) {
        return value >= 0.0F ? 1.0F : -1.0F;
    }

    private static int packUv(float u, float v) {
        return Math.clamp((int) (u * UV_SCALE), 0, UV_MAX) | Math.clamp((int) (v * UV_SCALE), 0, UV_MAX) << THIRD_SHIFT;
    }

    private static int packBytes(ByteBuffer data, int at) {
        return (data.get(at) & BYTE_MASK)
            | (data.get(at + SECOND) & BYTE_MASK) << SECOND_SHIFT
            | (data.get(at + THIRD) & BYTE_MASK) << THIRD_SHIFT
            | (data.get(at + FOURTH) & BYTE_MASK) << FOURTH_SHIFT;
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
