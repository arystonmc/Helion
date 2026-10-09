package com.aryston.helion.render.shadow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class ShadowCascadeCacheTest {
    private static final Matrix4f PROJECTION = new Matrix4f().setPerspective((float) Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1024.0F, true);
    private static final Matrix4f VIEW_ROTATION = new Matrix4f().rotateX(0.3F).rotateY(1.1F);
    private static final Vector3f MORNING_SUN = new Vector3f(0.6F, 0.8F, 0.0F);
    private static final Vector3f BELOW = new Vector3f(0.0F, -1.0F, 0.0F);
    private static final int RESOLUTION = 1536;
    private static final Vector3d CAMERA = new Vector3d(100.3, 64.0, 50.7);
    private static final float TOLERANCE = 1.0e-3F;
    private static final List<Integer> EVERY_CASCADE = List.of(0, 1, 2, 3);

    @Test
    void nearCascadesUpdateEveryFrameAndFarCascadesTakeTurns() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        ShadowLight sun = light(MORNING_SUN);

        assertEquals(EVERY_CASCADE, update(cache, CAMERA, sun));
        assertEquals(List.of(0, 1, 2), update(cache, CAMERA, sun));
        assertEquals(List.of(0, 1), update(cache, CAMERA, sun));
        assertEquals(List.of(0, 1, 2), update(cache, CAMERA, sun));
        assertEquals(List.of(0, 1, 3), update(cache, CAMERA, sun));
    }

    @Test
    void aWaitingCascadeStillShadowsTheSamePlaceInTheWorld() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        ShadowLight sun = light(MORNING_SUN);
        List<ShadowCascade> rendered = compute(CAMERA);
        cache.update(rendered, CAMERA, sun, RESOLUTION);
        Vector3d moved = new Vector3d(CAMERA).add(0.6, 0.1, -0.4);
        List<Integer> due = update(cache, moved, sun);
        Vector3d world = new Vector3d(130.0, 62.0, 80.0);
        int farCascade = ShadowCascades.COUNT - 1;

        assertEquals(List.of(0, 1, 2), due);
        Vector4f expected = rendered.get(farCascade).lightViewProjection().transform(relative(world, CAMERA));
        Vector4f actual = cache.maskMatrix(farCascade, moved).transform(relative(world, moved));
        assertEquals(expected.x, actual.x, TOLERANCE);
        assertEquals(expected.y, actual.y, TOLERANCE);
        assertEquals(expected.z, actual.z, TOLERANCE);
    }

    @Test
    void aCameraJumpUpdatesEveryCascade() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        ShadowLight sun = light(MORNING_SUN);
        update(cache, CAMERA, sun);

        assertEquals(EVERY_CASCADE, update(cache, new Vector3d(CAMERA).add(600.0, 0.0, 600.0), sun));
    }

    @Test
    void aMovedLightUpdatesEveryCascade() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        update(cache, CAMERA, light(MORNING_SUN));

        assertEquals(EVERY_CASCADE, update(cache, CAMERA, light(new Vector3f(0.5F, 0.85F, 0.1F))));
    }

    @Test
    void switchingFromTheSunToTheMoonUpdatesEveryCascade() {
        ShadowCascadeCache cache = new ShadowCascadeCache();
        update(cache, CAMERA, light(MORNING_SUN));
        ShadowLight moon = ShadowLight.choose(BELOW, MORNING_SUN).orElseThrow();

        assertEquals(EVERY_CASCADE, update(cache, CAMERA, moon));
    }

    private static List<Integer> update(ShadowCascadeCache cache, Vector3d camera, ShadowLight light) {
        return cache.update(compute(camera, light), camera, light, RESOLUTION);
    }

    private static ShadowLight light(Vector3f direction) {
        return ShadowLight.choose(direction, BELOW).orElseThrow();
    }

    private static List<ShadowCascade> compute(Vector3d camera) {
        return compute(camera, light(MORNING_SUN));
    }

    private static List<ShadowCascade> compute(Vector3d camera, ShadowLight light) {
        return ShadowCascades.compute(camera, VIEW_ROTATION, PROJECTION, light.direction(), ShadowCascades.MAX_DISTANCE, RESOLUTION, true);
    }

    private static Vector4f relative(Vector3d world, Vector3d camera) {
        return new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1.0F);
    }
}
