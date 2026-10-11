package com.aryston.helion.render.shadow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

final class ShadowCascadeCache {
    private static final int[] UPDATE_INTERVALS = {1, 1, 2, 4};
    private static final int[] UPDATE_PHASES = {0, 0, 1, 0};
    private static final float MAX_CENTER_SHIFT = 0.25F;
    private static final float MIN_LIGHT_ALIGNMENT = (float) Math.cos(Math.toRadians(0.5));

    private final @Nullable RenderedCascade[] rendered = new RenderedCascade[ShadowCascades.COUNT];
    private long frame;
    private int resolution;
    private boolean moon;

    List<Integer> update(List<ShadowCascade> cascades, Vector3dc camera, ShadowLight light, int cascadeResolution) {
        if (cascadeResolution != resolution || light.moon() != moon) {
            clear();
            resolution = cascadeResolution;
            moon = light.moon();
        }
        List<Integer> due = new ArrayList<>(ShadowCascades.COUNT);
        for (int index = 0; index < ShadowCascades.COUNT; index++) {
            ShadowCascade cascade = cascades.get(index);
            if (isDue(index, cascade, camera, light.direction())) {
                rendered[index] = new RenderedCascade(cascade, camera, light.direction());
                due.add(index);
            }
        }
        frame++;
        return List.copyOf(due);
    }

    Matrix4f maskMatrix(int index, Vector3dc camera) {
        RenderedCascade cascade = renderedCascade(index);
        Vector3d moved = new Vector3d(camera).sub(cascade.camera());
        return cascade.cascade().lightViewProjection().translate((float) moved.x, (float) moved.y, (float) moved.z);
    }

    float texelSize(int index) {
        return renderedCascade(index).cascade().texelSize();
    }

    void clear() {
        Arrays.fill(rendered, null);
        frame = 0;
    }

    private boolean isDue(int index, ShadowCascade cascade, Vector3dc camera, Vector3fc lightDirection) {
        RenderedCascade previous = rendered[index];
        if (previous == null || isScheduled(index)) {
            return true;
        }
        return previous.lightDirection().dot(lightDirection) < MIN_LIGHT_ALIGNMENT
            || previous.cascade().radius() != cascade.radius()
            || centerShift(previous, cascade, camera) > MAX_CENTER_SHIFT * cascade.radius();
    }

    private boolean isScheduled(int index) {
        return (frame - UPDATE_PHASES[index]) % UPDATE_INTERVALS[index] == 0;
    }

    private static float centerShift(RenderedCascade previous, ShadowCascade cascade, Vector3dc camera) {
        Vector3d moved = new Vector3d(camera).sub(previous.camera());
        Vector3f lightSpaceMove = previous.cascade().lightView()
            .transformDirection(new Vector3f((float) moved.x, (float) moved.y, (float) moved.z));
        Vector3f previousCenter = new Vector3f(previous.cascade().lightSpaceCenter()).sub(lightSpaceMove);
        float shiftX = previousCenter.x - cascade.lightSpaceCenter().x();
        float shiftY = previousCenter.y - cascade.lightSpaceCenter().y();
        return (float) Math.sqrt(shiftX * shiftX + shiftY * shiftY);
    }

    private RenderedCascade renderedCascade(int index) {
        return Objects.requireNonNull(rendered[index], "cascade was never rendered");
    }

    private record RenderedCascade(ShadowCascade cascade, Vector3dc camera, Vector3fc lightDirection) {
        private RenderedCascade {
            camera = new Vector3d(camera);
            lightDirection = new Vector3f(lightDirection);
        }
    }
}
