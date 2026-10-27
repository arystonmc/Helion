package com.aryston.helion.render.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryston.helion.render.atmosphere.PhysicalSkySettings;
import com.aryston.helion.render.clouds.CloudQuality;
import com.aryston.helion.render.clouds.CloudSettings;
import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.geometry.GeometryBufferView;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.rtx.RayTracingSettings;
import com.aryston.helion.render.temporal.RenderScale;
import com.aryston.helion.render.temporal.TemporalSettings;
import com.aryston.helion.render.water.ReflectionSettings;
import org.junit.jupiter.api.Test;

class RenderSettingsTest {
    private static final RenderSettings GEOMETRY_WITH_VIEW = new RenderSettings(
        ImageSettings.FOUNDATION,
        new GeometryBufferSettings(true, GeometryBufferView.NORMALS),
        new TemporalSettings(true, RenderScale.NATIVE),
        PhysicalSkySettings.enabledWithDefaults(),
        new CloudSettings(true, CloudQuality.HIGH),
        new RayTracingSettings(true, true, true),
        new ReflectionSettings(true, true, true)
    );

    @Test
    void foundationTurnsEveryEffectOff() {
        RenderSettings foundation = GEOMETRY_WITH_VIEW.foundation();

        assertFalse(foundation.image().bloom().enabled());
        assertFalse(foundation.image().sharpening().enabled());
        assertFalse(foundation.temporal().enabled());
        assertFalse(foundation.sky().enabled());
        assertFalse(foundation.clouds().enabled());
        assertFalse(foundation.rayTracing().enabled());
        assertFalse(foundation.reflections().screenSpace());
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
        assertFalse(RenderSettings.OFF.temporal().enabled());
        assertFalse(RenderSettings.OFF.sky().enabled());
        assertFalse(RenderSettings.OFF.clouds().enabled());
    }
}
