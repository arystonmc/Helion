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
        for (int frame = 0; frame < JitterSequence.BASE_LENGTH; frame++) {
            Vector2f offset = JitterSequence.pixelOffset(frame, JitterSequence.BASE_LENGTH);
            assertTrue(Math.abs(offset.x) < HALF_PIXEL);
            assertTrue(Math.abs(offset.y) < HALF_PIXEL);
        }
    }

    @Test
    void sequenceRepeatsAndVaries() {
        assertEquals(JitterSequence.pixelOffset(0, JitterSequence.BASE_LENGTH), JitterSequence.pixelOffset(JitterSequence.BASE_LENGTH, JitterSequence.BASE_LENGTH));
        assertNotEquals(JitterSequence.pixelOffset(0, JitterSequence.BASE_LENGTH), JitterSequence.pixelOffset(1, JitterSequence.BASE_LENGTH));
    }

    @Test
    void offsetsAverageNearThePixelCenter() {
        Vector2f sum = new Vector2f();
        for (int frame = 0; frame < JitterSequence.BASE_LENGTH; frame++) {
            sum.add(JitterSequence.pixelOffset(frame, JitterSequence.BASE_LENGTH));
        }
        assertTrue(Math.abs(sum.x / JitterSequence.BASE_LENGTH) < MAX_MEAN_OFFSET);
        assertTrue(Math.abs(sum.y / JitterSequence.BASE_LENGTH) < MAX_MEAN_OFFSET);
    }

    @Test
    void upscalingUsesLongerSequencesSoEveryOutputPixelIsCovered() {
        assertEquals(JitterSequence.BASE_LENGTH, JitterSequence.length(1.0F));
        assertEquals(32, JitterSequence.length(2.0F));
        assertEquals(18, JitterSequence.length(1.5F));
        assertEquals(JitterSequence.MAX_LENGTH, JitterSequence.length(10.0F));
    }

    @Test
    void ndcOffsetIsTwoUnitsPerScreen() {
        Vector2f pixels = JitterSequence.pixelOffset(1, JitterSequence.BASE_LENGTH);
        Vector2f ndc = JitterSequence.ndcOffset(pixels, WIDTH, HEIGHT);
        assertEquals(pixels.x * 2.0F / WIDTH, ndc.x, TOLERANCE);
        assertEquals(pixels.y * 2.0F / HEIGHT, ndc.y, TOLERANCE);
    }
}
