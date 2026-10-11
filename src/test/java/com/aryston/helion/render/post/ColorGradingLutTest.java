package com.aryston.helion.render.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ColorGradingLutTest {
    private static final float ONE_STEP = 1.0F / 255.0F;
    private static final Vector3f GREY = new Vector3f(0.5F, 0.5F, 0.5F);
    private static final Vector3f GRASS = new Vector3f(0.45F, 0.6F, 0.25F);

    @Test
    void theNeutralGradeKeepsEveryColor() {
        for (int index = 0; index < ColorGradingLut.SIZE; index++) {
            float value = ColorGradingLut.coordinate(index);
            Vector3f graded = ColorGradingLut.grade(new Vector3f(value, value * 0.5F, 1.0F - value), ColorGrade.NEUTRAL);
            assertEquals(value, graded.x, ONE_STEP);
            assertEquals(value * 0.5F, graded.y, ONE_STEP);
            assertEquals(1.0F - value, graded.z, ONE_STEP);
        }
    }

    @Test
    void warmAndCoolShiftTheWhiteBalance() {
        Vector3f warm = ColorGradingLut.grade(GREY, ColorGrade.WARM);
        Vector3f cool = ColorGradingLut.grade(GREY, ColorGrade.COOL);

        assertTrue(warm.x > warm.z);
        assertTrue(cool.z > cool.x);
    }

    @Test
    void vividIsMoreSaturatedThanMuted() {
        assertTrue(saturation(ColorGradingLut.grade(GRASS, ColorGrade.VIVID)) > saturation(ColorGradingLut.grade(GRASS, ColorGrade.MUTED)));
    }

    @Test
    void theTableHoldsEveryLatticePointInOrder() {
        ByteBuffer table = ColorGradingLut.build(
            ColorGrade.NEUTRAL, ByteBuffer.allocate(ColorGradingLut.WIDTH * ColorGradingLut.HEIGHT * ColorGradingLut.BYTES_PER_TEXEL)
        );
        int lastRedOfFirstRow = (ColorGradingLut.SIZE - 1) * ColorGradingLut.BYTES_PER_TEXEL;

        assertEquals(ColorGradingLut.WIDTH * ColorGradingLut.HEIGHT * ColorGradingLut.BYTES_PER_TEXEL, table.remaining());
        assertEquals(0, Byte.toUnsignedInt(table.get(0)));
        assertEquals(255, Byte.toUnsignedInt(table.get(lastRedOfFirstRow)));
    }

    private static float saturation(Vector3f color) {
        return Math.max(color.x, Math.max(color.y, color.z)) - Math.min(color.x, Math.min(color.y, color.z));
    }
}
