package com.aryston.helion.render.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryston.helion.render.atmosphere.PhysicalSkySettings;
import com.aryston.helion.render.clouds.CloudQuality;
import com.aryston.helion.render.clouds.CloudSettings;
import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.geometry.GeometryBufferView;
import com.aryston.helion.render.lighting.AmbientOcclusionSettings;
import com.aryston.helion.render.lighting.DeferredLightingSettings;
import com.aryston.helion.render.lighting.DynamicLightSettings;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.shadow.ShadowQuality;
import com.aryston.helion.render.shadow.ShadowSettings;
import com.aryston.helion.render.temporal.RenderScale;
import com.aryston.helion.render.temporal.TemporalSettings;
import com.aryston.helion.render.voxel.VoxelLightingMode;
import com.aryston.helion.render.voxel.VoxelLightingSettings;
import org.junit.jupiter.api.Test;

class RenderSettingsTest {
    private static final float STRONG_BLOCK_LIGHT = 2.0F;
    private static final RenderSettings GEOMETRY_WITH_VIEW = new RenderSettings(
        AmbientOcclusionSettings.DISABLED,
        ImageSettings.FOUNDATION,
        new GeometryBufferSettings(true, GeometryBufferView.NORMALS),
        new DeferredLightingSettings(true, STRONG_BLOCK_LIGHT, DeferredLightingSettings.DEFAULT_SKY_LIGHT_INTENSITY, true),
        new TemporalSettings(true, RenderScale.NATIVE),
        PhysicalSkySettings.enabledWithDefaults(),
        new ShadowSettings(true, ShadowQuality.HIGH),
        new DynamicLightSettings(true),
        new CloudSettings(true, CloudQuality.HIGH),
        new VoxelLightingSettings(VoxelLightingMode.SHADER)
    );

    @Test
    void foundationTurnsEveryEffectOff() {
        RenderSettings foundation = GEOMETRY_WITH_VIEW.foundation();

        assertFalse(foundation.ambientOcclusion().enabled());
        assertFalse(foundation.image().bloom().enabled());
        assertFalse(foundation.image().sharpening().enabled());
        assertFalse(foundation.lighting().enabled());
        assertFalse(foundation.temporal().enabled());
        assertFalse(foundation.sky().enabled());
        assertFalse(foundation.shadows().enabled());
        assertFalse(foundation.dynamicLights().enabled());
        assertFalse(foundation.clouds().enabled());
        assertFalse(foundation.voxels().enabled());
    }

    @Test
    void foundationKeepsThePlainPresentCopy() {
        assertFalse(GEOMETRY_WITH_VIEW.foundation().image().needsComposite());
    }

    @Test
    void foundationKeepsTheGeometryBufferButHidesItsView() {
        GeometryBufferSettings geometry = GEOMETRY_WITH_VIEW.foundation().geometry();

        assertTrue(geometry.enabled());
        assertEquals(GeometryBufferView.NONE, geometry.view());
        assertFalse(geometry.showsView());
    }

    @Test
    void offRendersNothingExtra() {
        assertFalse(RenderSettings.OFF.geometry().enabled());
        assertFalse(RenderSettings.OFF.image().needsComposite());
        assertFalse(RenderSettings.OFF.ambientOcclusion().enabled());
        assertFalse(RenderSettings.OFF.lighting().enabled());
        assertFalse(RenderSettings.OFF.temporal().enabled());
        assertFalse(RenderSettings.OFF.sky().enabled());
        assertFalse(RenderSettings.OFF.shadows().enabled());
        assertFalse(RenderSettings.OFF.dynamicLights().enabled());
        assertFalse(RenderSettings.OFF.clouds().enabled());
    }
}
