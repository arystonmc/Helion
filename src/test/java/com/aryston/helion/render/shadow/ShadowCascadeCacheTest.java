package com.aryston.helion.render.shadow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aryston.helion.render.shadow.ShadowCascadeCache.Slot;
import java.util.List;
import java.util.stream.IntStream;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class ShadowCascadeCacheTest {
    private static final Matrix4f PROJECTION = new Matrix4f().setPerspective((float) Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1024.0F, true);
    private static final Matrix4f VIEW_ROTATION = new Matrix4f().rotateX(0.3F).rotateY(1.1F);
    private static final Vector3f MORNING_SUN = new Vector3f(0.6F, 0.8F, 0.0F).normalize();
    private static final Vector3f BELOW = new Vector3f(0.0F, -1.0F, 0.0F);
    private static final int RESOLUTION = 1536;
    private static final Vector3d CAMERA = new Vector3d(100.3, 64.0, 50.7);
    private static final float TOLERANCE = 1.0e-3F;
    private static final long MILLIS = 1_000_000L;

    @Test
    void cascadesWaitWhileNothingChangesAndRefreshAfterTheirTime() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        ShadowLight sun = light(MORNING_SUN);

        assertEquals(every(0), update(cache, CAMERA, sun, 0));
        assertEquals(List.of(), update(cache, CAMERA, sun, 10 * MILLIS));
        assertEquals(slots(0, 0, 1), update(cache, CAMERA, sun, 120 * MILLIS));
        assertEquals(slots(0, 0, 1), update(cache, CAMERA, sun, 230 * MILLIS));
        assertEquals(slots(0, 2), update(cache, CAMERA, sun, 240 * MILLIS));
        assertEquals(slots(0, 0, 1, 3), update(cache, CAMERA, sun, 410 * MILLIS));
    }

    @Test
    void aLightStepDrawsTheOtherSetAndFadesOutThePreviousOne() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        update(cache, CAMERA, light(MORNING_SUN), 0);

        assertEquals(every(1), update(cache, CAMERA, stepped(2), MILLIS));
        assertEquals(1.0F, cache.previousWeight(MILLIS), TOLERANCE);
        assertEquals(0.5F, cache.previousWeight(51 * MILLIS), TOLERANCE);
        assertEquals(0.0F, cache.previousWeight(101 * MILLIS), TOLERANCE);

        assertEquals(slots(0, 0, 1, 2), update(cache, CAMERA, stepped(4), 300 * MILLIS));
        assertEquals(1.0F, cache.previousWeight(300 * MILLIS), TOLERANCE);
        assertEquals(slots(0, 3), update(cache, CAMERA, stepped(4), 301 * MILLIS));
        assertEquals(0.5F, cache.previousWeight(301 * MILLIS + 299 * MILLIS / 2), TOLERANCE);
        assertEquals(1, cache.previousSet());
    }

    @Test
    void aWaitingCascadeStillShadowsTheSamePlaceInTheWorld() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        ShadowLight sun = light(MORNING_SUN);
        List<ShadowCascade> rendered = compute(CAMERA, sun);
        cache.update(rendered, CAMERA, sun, RESOLUTION, 0);
        Vector3d moved = new Vector3d(CAMERA).add(0.6, 0.1, -0.4);
        List<Slot> due = update(cache, moved, sun, 120 * MILLIS);
        Vector3d world = new Vector3d(130.0, 62.0, 80.0);
        int farCascade = ShadowCascades.COUNT - 1;

        assertEquals(slots(0, 0, 1), due);
        Vector4f expected = rendered.get(farCascade).lightViewProjection().transform(relative(world, CAMERA));
        Vector4f actual = cache.maskMatrix(0, farCascade, moved).transform(relative(world, moved));
        assertEquals(expected.x, actual.x, TOLERANCE);
        assertEquals(expected.y, actual.y, TOLERANCE);
        assertEquals(expected.z, actual.z, TOLERANCE);
    }

    @Test
    void aCameraJumpRedrawsEveryCascadeAndDropsThePreviousSet() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        update(cache, CAMERA, light(MORNING_SUN), 0);
        update(cache, CAMERA, stepped(2), MILLIS);

        assertEquals(every(1), update(cache, new Vector3d(CAMERA).add(600.0, 0.0, 600.0), stepped(2), 2 * MILLIS));
        assertEquals(0.0F, cache.previousWeight(2 * MILLIS), TOLERANCE);
    }

    @Test
    void aLargeLightTurnStartsOverWithoutFading() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        update(cache, CAMERA, light(MORNING_SUN), 0);

        assertEquals(every(0), update(cache, CAMERA, light(new Vector3f(0.5F, 0.85F, 0.1F)), MILLIS));
        assertEquals(0.0F, cache.previousWeight(MILLIS), TOLERANCE);
    }

    @Test
    void switchingFromTheSunToTheMoonStartsOver() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        update(cache, CAMERA, light(MORNING_SUN), 0);
        ShadowLight moon = ShadowLight.choose(BELOW, MORNING_SUN).orElseThrow();

        assertEquals(every(0), update(cache, CAMERA, moon, MILLIS));
        assertEquals(0.0F, cache.previousWeight(MILLIS), TOLERANCE);
    }

    private static List<Slot> update(ShadowCascadeCache cache, Vector3d camera, ShadowLight light, long now) {
        return cache.update(compute(camera, light), camera, light, RESOLUTION, now);
    }

    private static ShadowLight light(Vector3f direction) {
        return ShadowLight.choose(direction, BELOW).orElseThrow();
    }

    private static ShadowLight stepped(int steps) {
        return light(new Vector3f(MORNING_SUN).rotateZ(ShadowLight.DIRECTION_STEP * steps));
    }

    private static List<Slot> every(int set) {
        return slots(set, 0, 1, 2, 3);
    }

    private static List<Slot> slots(int set, int... cascades) {
        return IntStream.of(cascades).mapToObj(cascade -> new Slot(set, cascade)).toList();
    }

    private static List<ShadowCascade> compute(Vector3d camera, ShadowLight light) {
        return ShadowCascades.compute(camera, VIEW_ROTATION, PROJECTION, light.direction(), ShadowCascades.MAX_DISTANCE, RESOLUTION, true);
    }

    private static Vector4f relative(Vector3d world, Vector3d camera) {
        return new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1.0F);
    }
}
