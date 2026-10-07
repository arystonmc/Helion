package com.aryston.helion.render.shadow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class ShadowCascadesTest {
    private static final float FIELD_OF_VIEW = (float) Math.toRadians(70.0);
    private static final float ASPECT = 16.0F / 9.0F;
    private static final float NEAR_PLANE = 0.05F;
    private static final float FAR_PLANE = 1024.0F;
    private static final int RESOLUTION = 1536;
    private static final float TOLERANCE = 1.0e-3F;
    private static final float TEXEL_TOLERANCE = 0.02F;
    private static final Vector3f MORNING_SUN = new Vector3f(0.6F, 0.8F, 0.0F);
    private static final Matrix4f PROJECTION = new Matrix4f().setPerspective(FIELD_OF_VIEW, ASPECT, NEAR_PLANE, FAR_PLANE, true);
    private static final Matrix4f VIEW_ROTATION = new Matrix4f().rotateX(0.3F).rotateY(1.1F);

    @Test
    void everyCascadeHoldsItsSliceOfTheView() {
        List<ShadowCascade> cascades = compute(new Vector3d(10.0, 70.0, -20.0));
        Matrix4f viewToWorld = new Matrix4f(VIEW_ROTATION).invert();
        float tanX = 1.0F / PROJECTION.m00();
        float tanY = 1.0F / PROJECTION.m11();
        float near = 0.0F;
        for (int index = 0; index < ShadowCascades.COUNT; index++) {
            float far = ShadowCascades.splitDistance(index, ShadowCascades.MAX_DISTANCE);
            for (float depth : new float[] {Math.max(near, NEAR_PLANE), far}) {
                for (int corner = 0; corner < 4; corner++) {
                    float x = (corner % 2 == 0 ? -1.0F : 1.0F) * depth * tanX;
                    float y = (corner < 2 ? -1.0F : 1.0F) * depth * tanY;
                    Vector3f world = viewToWorld.transformPosition(new Vector3f(x, y, -depth));
                    assertInside(cascades.get(index), world);
                }
            }
            near = far;
        }
    }

    @Test
    void movingTheCameraShiftsTheMapByWholeTexels() {
        Vector3d first = new Vector3d(100.3, 64.0, 50.7);
        Vector3d second = new Vector3d(100.75, 64.2, 51.1);
        List<ShadowCascade> before = compute(first);
        List<ShadowCascade> after = compute(second);
        Vector3d worldPoint = new Vector3d(103.0, 62.0, 55.0);
        for (int index = 0; index < ShadowCascades.COUNT; index++) {
            float texelsBefore = texelX(before.get(index), worldPoint, first);
            float texelsAfter = texelX(after.get(index), worldPoint, second);
            float shift = texelsAfter - texelsBefore;
            assertEquals(Math.round(shift), shift, TEXEL_TOLERANCE);
        }
    }

    @Test
    void castersTowardTheLightStayInsideTheDepthRange() {
        ShadowCascade cascade = compute(new Vector3d(0.0, 64.0, 0.0)).getFirst();
        Vector3f light = new Vector3f(MORNING_SUN).normalize();
        Vector3f nearCaster = new Vector3f(light).mul(ShadowCascades.CASTER_REACH * 0.5F);
        Vector3f beyondReach = new Vector3f(light).mul(ShadowCascades.CASTER_REACH * 2.0F);

        assertInside(cascade, nearCaster);
        assertTrue(depth(cascade, beyondReach) < 0.0F);
    }

    @Test
    void containsSelectsSectionsNearTheCascade() {
        ShadowCascade cascade = compute(new Vector3d(0.0, 64.0, 0.0)).getFirst();
        float sectionRadius = 14.0F;

        assertTrue(cascade.containsLightSpace(cascade.lightView().transformPosition(new Vector3f(0.0F, 0.0F, 0.0F)), sectionRadius));
        assertFalse(cascade.containsLightSpace(cascade.lightView().transformPosition(new Vector3f(400.0F, 0.0F, 400.0F)), sectionRadius));
    }

    @Test
    void shadowDistanceFollowsTheRenderDistance() {
        assertEquals(96.0F, ShadowCascades.distance(96), TOLERANCE);
        assertEquals(ShadowCascades.MAX_DISTANCE, ShadowCascades.distance(512), TOLERANCE);
    }

    @Test
    void theSunCastsShadowsByDayAndTheMoonByNight() {
        Vector3f below = new Vector3f(0.0F, -1.0F, 0.0F);
        ShadowLight day = ShadowLight.choose(new Vector3f(0.0F, 1.0F, 0.0F), below).orElseThrow();
        ShadowLight night = ShadowLight.choose(below, new Vector3f(0.3F, 0.9F, 0.0F)).orElseThrow();

        assertFalse(day.moon());
        assertEquals(1.0F, day.sunStrength(), TOLERANCE);
        assertEquals(0.0F, day.moonStrength(), TOLERANCE);
        assertTrue(night.moon());
        assertEquals(0.0F, night.sunStrength(), TOLERANCE);
        assertTrue(ShadowLight.choose(below, below).isEmpty());
    }

    @Test
    void shadowsFadeNearTheHorizon() {
        Vector3f below = new Vector3f(0.0F, -1.0F, 0.0F);
        float low = ShadowLight.choose(new Vector3f(1.0F, 0.05F, 0.0F), below).orElseThrow().strength();
        float high = ShadowLight.choose(new Vector3f(1.0F, 0.5F, 0.0F), below).orElseThrow().strength();

        assertTrue(low > 0.0F && low < 1.0F);
        assertEquals(1.0F, high, TOLERANCE);
    }

    private static List<ShadowCascade> compute(Vector3d camera) {
        return ShadowCascades.compute(camera, VIEW_ROTATION, PROJECTION, MORNING_SUN, ShadowCascades.MAX_DISTANCE, RESOLUTION, true);
    }

    private static void assertInside(ShadowCascade cascade, Vector3f cameraRelative) {
        Vector4f clip = cascade.lightViewProjection().transform(new Vector4f(cameraRelative, 1.0F));
        assertTrue(Math.abs(clip.x) <= 1.0F + TOLERANCE, "x " + clip.x);
        assertTrue(Math.abs(clip.y) <= 1.0F + TOLERANCE, "y " + clip.y);
        assertTrue(clip.z >= -TOLERANCE && clip.z <= 1.0F + TOLERANCE, "depth " + clip.z);
    }

    private static float depth(ShadowCascade cascade, Vector3f cameraRelative) {
        return cascade.lightViewProjection().transform(new Vector4f(cameraRelative, 1.0F)).z;
    }

    private static float texelX(ShadowCascade cascade, Vector3d world, Vector3d camera) {
        Vector3f relative = new Vector3f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z));
        Vector4f clip = cascade.lightViewProjection().transform(new Vector4f(relative, 1.0F));
        return (clip.x * 0.5F + 0.5F) * RESOLUTION;
    }
}
