package com.aryston.helion.render.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class RenderSettingsTest {
    @Test
    void foundationTurnsEveryEffectOff() {
        RenderSettings foundation = RenderSettings.foundation();

        assertFalse(foundation.ambientOcclusion().enabled());
        assertFalse(foundation.image().bloom().enabled());
        assertFalse(foundation.image().sharpening().enabled());
    }

    @Test
    void foundationKeepsThePlainPresentCopy() {
        assertFalse(RenderSettings.foundation().image().needsComposite());
    }
}
