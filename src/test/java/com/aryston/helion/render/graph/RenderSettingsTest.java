package com.aryston.helion.render.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.geometry.GeometryBufferView;
import com.aryston.helion.render.lighting.AmbientOcclusionSettings;
import com.aryston.helion.render.post.ImageSettings;
import org.junit.jupiter.api.Test;

class RenderSettingsTest {
    private static final RenderSettings GEOMETRY_WITH_VIEW = new RenderSettings(
        AmbientOcclusionSettings.DISABLED,
        ImageSettings.FOUNDATION,
        new GeometryBufferSettings(true, GeometryBufferView.NORMALS)
    );

    @Test
    void foundationTurnsEveryEffectOff() {
        RenderSettings foundation = GEOMETRY_WITH_VIEW.foundation();

        assertFalse(foundation.ambientOcclusion().enabled());
        assertFalse(foundation.image().bloom().enabled());
        assertFalse(foundation.image().sharpening().enabled());
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
    }
}
