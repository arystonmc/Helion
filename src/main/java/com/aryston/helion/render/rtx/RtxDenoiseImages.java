package com.aryston.helion.render.rtx;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.resource.GpuResources;
import com.mojang.renderpearl.api.GpuFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

final class RtxDenoiseImages {
    private static final String WORK_LABEL = "Helion Path Traced Light #";
    private static final String HISTORY_LABEL = "Helion Path Traced History";
    private static final String MOMENTS_LABEL = "Helion Path Traced Moments #";
    private static final String GUIDE_LABEL = "Helion Path Traced Guide #";
    private static final String SPECULAR_LABEL = "Helion Path Traced Specular";
    private static final String SPECULAR_HISTORY_LABEL = "Helion Path Traced Specular History #";
    private static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final int PAIR = 2;

    private final int width;
    private final int height;
    private final RtxImage[] work = new RtxImage[PAIR];
    private final RtxImage history;
    private final RtxImage[] moments = new RtxImage[PAIR];
    private final RtxImage[] guide = new RtxImage[PAIR];
    private final RtxImage specular;
    private final RtxImage[] specularHistory = new RtxImage[PAIR];
    private int current;
    private @Nullable Matrix4f previousViewProjection;
    private @Nullable Vector3d previousPosition;

    private RtxDenoiseImages(GpuResources resources, int width, int height) {
        this.width = width;
        this.height = height;
        history = RtxImage.create(resources, HISTORY_LABEL, width, height, FORMAT);
        specular = RtxImage.create(resources, SPECULAR_LABEL, width, height, FORMAT);
        for (int slot = 0; slot < PAIR; slot++) {
            work[slot] = RtxImage.create(resources, WORK_LABEL + slot, width, height, FORMAT);
            moments[slot] = RtxImage.create(resources, MOMENTS_LABEL + slot, width, height, FORMAT);
            guide[slot] = RtxImage.create(resources, GUIDE_LABEL + slot, width, height, FORMAT);
            specularHistory[slot] = RtxImage.create(resources, SPECULAR_HISTORY_LABEL + slot, width, height, FORMAT);
        }
    }

    static RtxDenoiseImages create(GpuResources resources, int width, int height) {
        return new RtxDenoiseImages(resources, width, height);
    }

    boolean fits(int requestedWidth, int requestedHeight) {
        return width == requestedWidth && height == requestedHeight;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    RtxImage work(int slot) {
        return work[slot];
    }

    RtxImage history() {
        return history;
    }

    RtxImage moments() {
        return moments[current];
    }

    RtxImage previousMoments() {
        return moments[1 - current];
    }

    RtxImage guide() {
        return guide[current];
    }

    RtxImage previousGuide() {
        return guide[1 - current];
    }

    RtxImage specular() {
        return specular;
    }

    RtxImage specularHistory() {
        return specularHistory[current];
    }

    RtxImage previousSpecularHistory() {
        return specularHistory[1 - current];
    }

    Matrix4f reprojection(HelionCamera camera) {
        Matrix4f last = previousViewProjection;
        Vector3d lastPosition = previousPosition;
        if (last == null || lastPosition == null) {
            return new Matrix4f().zero();
        }
        Vector3dc position = camera.position();
        return new Matrix4f(last).translate(
            (float) (position.x() - lastPosition.x()), (float) (position.y() - lastPosition.y()), (float) (position.z() - lastPosition.z())
        );
    }

    void advance(Matrix4fc viewProjection, Vector3dc position) {
        current = 1 - current;
        previousViewProjection = new Matrix4f(viewProjection);
        previousPosition = new Vector3d(position);
    }

    void close(GpuResources resources) {
        history.close(resources);
        specular.close(resources);
        for (int slot = 0; slot < PAIR; slot++) {
            work[slot].close(resources);
            moments[slot].close(resources);
            guide[slot].close(resources);
            specularHistory[slot].close(resources);
        }
    }
}
