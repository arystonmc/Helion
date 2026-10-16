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
    static final int SETS = 2;
    private static final long[] REFRESH_NANOS = {100_000_000L, 100_000_000L, 200_000_000L, 400_000_000L};
    private static final int FIRST_FAR_CASCADE = 2;
    private static final int FAR_CASCADE_TURNS = 2;
    private static final float MAX_CENTER_SHIFT = 0.25F;
    private static final float MIN_LIGHT_ALIGNMENT = (float) Math.cos(Math.toRadians(0.5));
    private static final long MIN_FADE_NANOS = 100_000_000L;
    private static final long MAX_FADE_NANOS = 2_000_000_000L;
    private static final long NO_TIME = Long.MIN_VALUE;
    private static final int NEAR_CASCADE = 0;

    private final @Nullable RenderedCascade[][] rendered = new RenderedCascade[SETS][ShadowCascades.COUNT];
    private final Vector3f direction = new Vector3f();
    private boolean hasDirection;
    private int current;
    private boolean previousValid;
    private long fadeStart = NO_TIME;
    private long fadeNanos = MIN_FADE_NANOS;
    private long lastSwap = NO_TIME;
    private long frame;
    private int resolution;
    private boolean moon;

    List<Slot> update(List<ShadowCascade> cascades, Vector3dc camera, ShadowLight light, int cascadeResolution, long now) {
        if (cascadeResolution != resolution || light.moon() != moon
            || hasDirection && direction.dot(light.direction()) < MIN_LIGHT_ALIGNMENT) {
            clear();
            resolution = cascadeResolution;
            moon = light.moon();
        }
        if (!hasDirection) {
            direction.set(light.direction());
            hasDirection = true;
        } else if (!direction.equals(light.direction())) {
            step(now);
            direction.set(light.direction());
        }
        if (previousValid && centerShift(previousCascade(NEAR_CASCADE), cascades.get(NEAR_CASCADE), camera)
            > MAX_CENTER_SHIFT * cascades.get(NEAR_CASCADE).radius()) {
            previousValid = false;
        }
        List<Slot> due = new ArrayList<>(ShadowCascades.COUNT);
        for (int index = 0; index < ShadowCascades.COUNT; index++) {
            ShadowCascade cascade = cascades.get(index);
            if (isDue(index, cascade, camera, now)) {
                rendered[current][index] = new RenderedCascade(cascade, camera, direction, now);
                due.add(new Slot(current, index));
            }
        }
        if (fadeStart == NO_TIME && isComplete(current)) {
            fadeStart = now;
        }
        frame++;
        return List.copyOf(due);
    }

    int currentSet() {
        return current;
    }

    int previousSet() {
        return SETS - 1 - current;
    }

    float previousWeight(long now) {
        if (!previousValid) {
            return 0.0F;
        }
        if (fadeStart == NO_TIME) {
            return 1.0F;
        }
        return 1.0F - Math.clamp((float) (now - fadeStart) / fadeNanos, 0.0F, 1.0F);
    }

    Matrix4f maskMatrix(int set, int index, Vector3dc camera) {
        RenderedCascade cascade = renderedCascade(set, index);
        Vector3d moved = new Vector3d(camera).sub(cascade.camera());
        return cascade.cascade().lightViewProjection().translate((float) moved.x, (float) moved.y, (float) moved.z);
    }

    float texelSize(int set, int index) {
        return renderedCascade(set, index).cascade().texelSize();
    }

    Vector3fc lightDirection(int set) {
        return renderedCascade(set, NEAR_CASCADE).lightDirection();
    }

    void clear() {
        for (RenderedCascade[] set : rendered) {
            Arrays.fill(set, null);
        }
        hasDirection = false;
        current = 0;
        previousValid = false;
        fadeStart = NO_TIME;
        fadeNanos = MIN_FADE_NANOS;
        lastSwap = NO_TIME;
        frame = 0;
    }

    private void step(long now) {
        if (!isComplete(current)) {
            return;
        }
        if (lastSwap != NO_TIME) {
            fadeNanos = Math.clamp(now - lastSwap, MIN_FADE_NANOS, MAX_FADE_NANOS);
        }
        lastSwap = now;
        current = previousSet();
        previousValid = true;
        fadeStart = NO_TIME;
    }

    private boolean isComplete(int set) {
        for (RenderedCascade cascade : rendered[set]) {
            if (cascade == null || !cascade.lightDirection().equals(direction)) {
                return false;
            }
        }
        return true;
    }

    private boolean isDue(int index, ShadowCascade cascade, Vector3dc camera, long now) {
        RenderedCascade previous = rendered[current][index];
        if (previous == null
            || previous.lightDirection().dot(direction) < MIN_LIGHT_ALIGNMENT
            || previous.cascade().radius() != cascade.radius()
            || centerShift(previous, cascade, camera) > MAX_CENTER_SHIFT * cascade.radius()) {
            return true;
        }
        boolean stale = !previous.lightDirection().equals(direction) || now - previous.time() >= REFRESH_NANOS[index];
        return stale && hasTurn(index);
    }

    private boolean hasTurn(int index) {
        return index < FIRST_FAR_CASCADE || (frame + index) % FAR_CASCADE_TURNS == 0;
    }

    private RenderedCascade previousCascade(int index) {
        return renderedCascade(previousSet(), index);
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

    private RenderedCascade renderedCascade(int set, int index) {
        return Objects.requireNonNull(rendered[set][index], "cascade was never rendered");
    }

    record Slot(int set, int cascade) {
        int target() {
            return set * ShadowCascades.COUNT + cascade;
        }
    }

    private record RenderedCascade(ShadowCascade cascade, Vector3dc camera, Vector3fc lightDirection, long time) {
        private RenderedCascade {
            camera = new Vector3d(camera);
            lightDirection = new Vector3f(lightDirection);
        }
    }
}
