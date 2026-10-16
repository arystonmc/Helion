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
    private static final long[] REFRESH_NANOS = {50_000_000L, 50_000_000L, 100_000_000L, 200_000_000L};
    private static final int FIRST_FAR_CASCADE = 2;
    private static final int FAR_CASCADE_TURNS = 2;
    private static final float MAX_CENTER_SHIFT = 0.25F;
    private static final float MIN_LIGHT_ALIGNMENT = (float) Math.cos(Math.toRadians(0.5));

    private final @Nullable RenderedCascade[] rendered = new RenderedCascade[ShadowCascades.COUNT];
    private long frame;
    private int resolution;
    private boolean moon;

    List<Integer> update(List<ShadowCascade> cascades, Vector3dc camera, ShadowLight light, int cascadeResolution, long now) {
        if (cascadeResolution != resolution || light.moon() != moon) {
            clear();
            resolution = cascadeResolution;
            moon = light.moon();
        }
        List<Integer> due = new ArrayList<>(ShadowCascades.COUNT);
        for (int index = 0; index < ShadowCascades.COUNT; index++) {
            ShadowCascade cascade = cascades.get(index);
            if (isDue(index, cascade, camera, light.direction(), now)) {
                rendered[index] = new RenderedCascade(cascade, camera, light.direction(), now);
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

    private boolean isDue(int index, ShadowCascade cascade, Vector3dc camera, Vector3fc lightDirection, long now) {
        RenderedCascade previous = rendered[index];
        if (previous == null
            || previous.lightDirection().dot(lightDirection) < MIN_LIGHT_ALIGNMENT
            || previous.cascade().radius() != cascade.radius()
            || centerShift(previous, cascade, camera) > MAX_CENTER_SHIFT * cascade.radius()) {
            return true;
        }
        boolean stale = !previous.lightDirection().equals(lightDirection) || now - previous.time() >= REFRESH_NANOS[index];
        return stale && hasTurn(index);
    }

    private boolean hasTurn(int index) {
        return index < FIRST_FAR_CASCADE || (frame + index) % FAR_CASCADE_TURNS == 0;
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

    private record RenderedCascade(ShadowCascade cascade, Vector3dc camera, Vector3fc lightDirection, long time) {
        private RenderedCascade {
            camera = new Vector3d(camera);
            lightDirection = new Vector3f(lightDirection);
        }
    }
}
