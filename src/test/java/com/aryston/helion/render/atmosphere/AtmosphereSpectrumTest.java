package com.aryston.helion.render.atmosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AtmosphereSpectrumTest {
    private static final double RED_WAVELENGTH = 680.0;
    private static final double BLUE_WAVELENGTH = 440.0;
    private static final double GREEN_WAVELENGTH = 550.0;
    private static final double PUBLISHED_RAYLEIGH_RED = 5.802e-3;
    private static final double PUBLISHED_RAYLEIGH_BLUE = 33.1e-3;
    private static final double PUBLISHED_OZONE_GREEN = 1.881e-3;
    private static final double RELATIVE_TOLERANCE = 0.01;
    private static final double TOLERANCE = 1.0e-9;
    private static final double PEAK_LUMINANCE_WAVELENGTH = 560.0;
    private static final double PEAK_BAND_LUMINANCE = 19.9;
    private static final double BAND_TOLERANCE = 0.2;

    @Test
    void wavelengthsCoverTheVisibleRange() {
        assertEquals(400.0, AtmosphereSpectrum.wavelength(0), TOLERANCE);
        assertEquals(700.0, AtmosphereSpectrum.wavelength(AtmosphereSpectrum.WAVELENGTH_COUNT - 1), TOLERANCE);
    }

    @Test
    void rayleighMatchesThePublishedRgbCoefficients() {
        assertRelative(PUBLISHED_RAYLEIGH_RED, AtmosphereSpectrum.rayleighScattering(RED_WAVELENGTH));
        assertRelative(PUBLISHED_RAYLEIGH_BLUE, AtmosphereSpectrum.rayleighScattering(BLUE_WAVELENGTH));
    }

    @Test
    void ozoneMatchesThePublishedGreenCoefficient() {
        assertRelative(PUBLISHED_OZONE_GREEN, AtmosphereSpectrum.ozoneAbsorption(GREEN_WAVELENGTH));
    }

    @Test
    void sunlightIsWhite() {
        double[][] weights = AtmosphereSpectrum.toLinearSrgb();
        for (double[] channel : weights) {
            double sum = 0.0;
            for (double weight : channel) {
                sum += weight;
            }
            assertEquals(1.0, sum, TOLERANCE);
        }
    }

    @Test
    void shortWavelengthsLeanBlueAndLongOnesRed() {
        double[][] weights = AtmosphereSpectrum.toLinearSrgb();
        int first = 0;
        int last = AtmosphereSpectrum.WAVELENGTH_COUNT - 1;
        assertTrue(weights[2][first] > weights[0][first]);
        assertTrue(weights[0][last] > weights[2][last]);
    }

    @Test
    void colorMatchingBandIntegratesTheTable() {
        assertEquals(PEAK_BAND_LUMINANCE, AtmosphereSpectrum.colorMatchingBand(PEAK_LUMINANCE_WAVELENGTH)[1], BAND_TOLERANCE);
    }

    private static void assertRelative(double expected, double actual) {
        assertEquals(expected, actual, expected * RELATIVE_TOLERANCE);
    }
}
