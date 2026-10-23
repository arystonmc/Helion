package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;
import org.lwjgl.vulkan.VkCommandBuffer;

final class RtxEntityScene {
    static final long NO_ADDRESS = 0L;

    private static final String LABEL = "Helion Ray Tracing Entities";
    private static final int GROWTH = 2;
    private static final int UPDATES_PER_BUILD = 30;

    private final RtxDevice device;
    private final GpuResources resources;
    private final Long2LongOpenHashMap addresses = new Long2LongOpenHashMap();
    private final RtxEntitySurfaces surfaces;
    private @Nullable RtxAccelerationStructure structure;
    private @Nullable TrackedResource tracked;
    private List<Shape> built = List.of();
    private int updates;

    RtxEntityScene(RtxDevice device, GpuResources resources) {
        this.device = device;
        this.resources = resources;
        this.surfaces = RtxEntitySurfaces.create(device, resources);
    }

    long record(VkCommandBuffer commandBuffer, List<RtxEntityDraws.RtxEntityDraw> draws) {
        addresses.clear();
        surfaces.write(draws, this::address);
        if (draws.isEmpty()) {
            return NO_ADDRESS;
        }
        List<RtxGeometry> geometries = draws.stream().map(this::geometry).toList();
        List<Shape> shapes = geometries.stream().map(Shape::of).toList();
        RtxAccelerationStructure current = structure;
        if (current == null || !current.fits(RtxAccelerationStructure.dynamicSizes(device, geometries))) {
            release();
            List<RtxGeometry> grown = geometries.stream().map(geometry -> geometry.withData(geometry.dataAddress(), geometry.primitiveCount() * GROWTH)).toList();
            RtxAccelerationStructure created = RtxAccelerationStructure.createDynamic(device, grown);
            tracked = resources.track(LABEL, created.bytes(), () -> created.destroy(device.vkDevice()));
            structure = created;
            current = created;
        }
        boolean update = shapes.equals(built) && updates < UPDATES_PER_BUILD;
        current.record(commandBuffer, geometries, update);
        updates = update ? updates + 1 : 0;
        built = shapes;
        return current.address();
    }

    RtxEntitySurfaces surfaces() {
        return surfaces;
    }

    void clearSurfaces() {
        surfaces.write(List.of(), this::address);
    }

    void close() {
        release();
        surfaces.release(resources);
    }

    void release() {
        if (tracked != null) {
            resources.release(tracked);
        }
        tracked = null;
        structure = null;
        built = List.of();
    }

    private RtxGeometry geometry(RtxEntityDraws.RtxEntityDraw draw) {
        return RtxGeometry.drawn(
            address(draw.vertexBuffer()) + draw.positionOffset(),
            draw.vertexStride(),
            draw.vertexCount(),
            address(draw.indexBuffer()),
            draw.indexType(),
            draw.indexOffset(),
            draw.baseVertex(),
            draw.triangles()
        );
    }

    private long address(long buffer) {
        if (addresses.containsKey(buffer)) {
            return addresses.get(buffer);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long address = VK12.vkGetBufferDeviceAddress(device.vkDevice(), VkBufferDeviceAddressInfo.calloc(stack).sType$Default().buffer(buffer));
            addresses.put(buffer, address);
            return address;
        }
    }

    private record Shape(int primitives, int vertices, int indexType, int stride) {
        static Shape of(RtxGeometry geometry) {
            return new Shape(geometry.primitiveCount(), geometry.vertexCount(), geometry.indexType(), geometry.vertexStride());
        }
    }
}
