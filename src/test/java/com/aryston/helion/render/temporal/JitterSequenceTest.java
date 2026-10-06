package com.aryston.helion.render.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

class JitterSequenceTest {
    private static final float TOLERANCE = 1.0e-6F;
    private static final float HALF_PIXEL = 0.5F;
    private static final float MAX_MEAN_OFFSET = 0.07F;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    @Test
    void haltonMatchesKnownValues() {
        assertEquals(0.5F, JitterSequence.halton(1, 2), TOLERANCE);
        assertEquals(0.25F, JitterSequence.halton(2, 2), TOLERANCE);
        assertEquals(1.0F / 3.0F, JitterSequence.halton(1, 3), TOLERANCE);
        assertEquals(4.0F / 9.0F, JitterSequence.halton(4, 3), TOLERANCE);
    }

    @Test
    void offsetsStayInsideThePixel() {
        for (int frame = 0; frame < JitterSequence.LENGTH; frame++) {
            Vector2f offset = JitterSequence.pixelOffset(frame);
            assertTrue(Math.abs(offset.x) < HALF_PIXEL);
            assertTrue(Math.abs(offset.y) < HALF_PIXEL);
        }
    }

    @Test
    void sequenceRepeatsAndVaries() {
        assertEquals(JitterSequence.pixelOffset(0), JitterSequence.pixelOffset(JitterSequence.LENGTH));
        assertNotEquals(JitterSequence.pixelOffset(0), JitterSequence.pixelOffset(1));
    }

    @Test
    void offsetsAverageNearThePixelCenter() {
        Vector2f sum = new Vector2f();
        for (int frame = 0; frame < JitterSequence.LENGTH; frame++) {
            sum.add(JitterSequence.pixelOffset(frame));
        }
        assertTrue(Math.abs(sum.x / JitterSequence.LENGTH) < MAX_MEAN_OFFSET);
        assertTrue(Math.abs(sum.y / JitterSequence.LENGTH) < MAX_MEAN_OFFSET);
    }

    @Test
    void ndcOffsetIsTwoUnitsPerScreen() {
        Vector2f pixels = JitterSequence.pixelOffset(1);
        Vector2f ndc = JitterSequence.ndcOffset(1, WIDTH, HEIGHT);
        assertEquals(pixels.x * 2.0F / WIDTH, ndc.x, TOLERANCE);
        assertEquals(pixels.y * 2.0F / HEIGHT, ndc.y, TOLERANCE);
    }
}
