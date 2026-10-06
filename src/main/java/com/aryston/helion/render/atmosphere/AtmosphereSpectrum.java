package com.aryston.helion.render.atmosphere;

public final class AtmosphereSpectrum {
    public static final int WAVELENGTH_COUNT = 16;
    public static final int CHANNELS_PER_GROUP = 4;
    public static final int GROUP_COUNT = WAVELENGTH_COUNT / CHANNELS_PER_GROUP;
    public static final int RGB_CHANNELS = 3;
    static final double FIRST_WAVELENGTH = 400.0;
    static final double WAVELENGTH_STEP = 20.0;
    private static final double TABLE_FIRST_WAVELENGTH = 360.0;
    private static final double TABLE_STEP = 10.0;
    private static final double CIE_FIRST_WAVELENGTH = 390.0;
    private static final double CIE_STEP = 5.0;
    private static final int CIE_X = 1;
    private static final int CIE_Y = 2;
    private static final int CIE_Z = 3;
    private static final double HALF = 0.5;
    private static final double RAYLEIGH_COEFFICIENT = 1.24062e-6;
    private static final double RAYLEIGH_WAVELENGTH_POWER = -4.0;
    private static final double MICROMETERS_PER_NANOMETER = 1.0e-3;
    private static final double METERS_PER_KILOMETER = 1000.0;
    private static final double DOBSON_UNIT = 2.687e20;
    private static final double OZONE_COLUMN_DOBSON_UNITS = 300.0;
    private static final double OZONE_LAYER_THICKNESS_METERS = 15000.0;
    private static final double MAX_OZONE_NUMBER_DENSITY = OZONE_COLUMN_DOBSON_UNITS * DOBSON_UNIT / OZONE_LAYER_THICKNESS_METERS;
    private static final double[][] XYZ_TO_LINEAR_SRGB = {
        {3.2406, -1.5372, -0.4986},
        {-0.9689, 1.8758, 0.0415},
        {0.0557, -0.2040, 1.0570}
    };
    private static final double[] SOLAR_IRRADIANCE = {
        1.11776, 1.14259, 1.01249, 1.14716, 1.72765, 1.73054, 1.6887, 1.61253,
        1.91198, 2.03474, 2.02042, 2.02212, 1.93377, 1.95809, 1.91686, 1.8298,
        1.8685, 1.8931, 1.85149, 1.8504, 1.8341, 1.8345, 1.8147, 1.78158, 1.7533,
        1.6965, 1.68194, 1.64654, 1.6048, 1.52143, 1.55622, 1.5113, 1.474, 1.4482,
        1.41018, 1.36775, 1.34188, 1.31429, 1.28303, 1.26758, 1.2367, 1.2082,
        1.18737, 1.14683, 1.12362, 1.1058, 1.07124, 1.04992
    };
    private static final double[] OZONE_CROSS_SECTION = {
        1.18e-27, 2.182e-28, 2.818e-28, 6.636e-28, 1.527e-27, 2.763e-27, 5.52e-27,
        8.451e-27, 1.582e-26, 2.316e-26, 3.669e-26, 4.924e-26, 7.752e-26, 9.016e-26,
        1.48e-25, 1.602e-25, 2.139e-25, 2.755e-25, 3.091e-25, 3.5e-25, 4.266e-25,
        4.672e-25, 4.398e-25, 4.701e-25, 5.019e-25, 4.305e-25, 3.74e-25, 3.215e-25,
        2.662e-25, 2.238e-25, 1.852e-25, 1.473e-25, 1.209e-25, 9.423e-26, 7.455e-26,
        6.566e-26, 5.105e-26, 4.15e-26, 4.228e-26, 3.237e-26, 2.451e-26, 2.801e-26,
        2.534e-26, 1.624e-26, 1.465e-26, 2.078e-26, 1.383e-26, 7.105e-27
    };
    private static final double[][] CIE_COLOR_MATCHING = {
        {390, 0.004243000000, 0.000120000000, 0.020050010000},
        {395, 0.007650000000, 0.000217000000, 0.036210000000},
        {400, 0.014310000000, 0.000396000000, 0.067850010000},
        {405, 0.023190000000, 0.000640000000, 0.110200000000},
        {410, 0.043510000000, 0.001210000000, 0.207400000000},
        {415, 0.077630000000, 0.002180000000, 0.371300000000},
        {420, 0.134380000000, 0.004000000000, 0.645600000000},
        {425, 0.214770000000, 0.007300000000, 1.039050100000},
        {430, 0.283900000000, 0.011600000000, 1.385600000000},
        {435, 0.328500000000, 0.016840000000, 1.622960000000},
        {440, 0.348280000000, 0.023000000000, 1.747060000000},
        {445, 0.348060000000, 0.029800000000, 1.782600000000},
        {450, 0.336200000000, 0.038000000000, 1.772110000000},
        {455, 0.318700000000, 0.048000000000, 1.744100000000},
        {460, 0.290800000000, 0.060000000000, 1.669200000000},
        {465, 0.251100000000, 0.073900000000, 1.528100000000},
        {470, 0.195360000000, 0.090980000000, 1.287640000000},
        {475, 0.142100000000, 0.112600000000, 1.041900000000},
        {480, 0.095640000000, 0.139020000000, 0.812950100000},
        {485, 0.057950010000, 0.169300000000, 0.616200000000},
        {490, 0.032010000000, 0.208020000000, 0.465180000000},
        {495, 0.014700000000, 0.258600000000, 0.353300000000},
        {500, 0.004900000000, 0.323000000000, 0.272000000000},
        {505, 0.002400000000, 0.407300000000, 0.212300000000},
        {510, 0.009300000000, 0.503000000000, 0.158200000000},
        {515, 0.029100000000, 0.608200000000, 0.111700000000},
        {520, 0.063270000000, 0.710000000000, 0.078249990000},
        {525, 0.109600000000, 0.793200000000, 0.057250010000},
        {530, 0.165500000000, 0.862000000000, 0.042160000000},
        {535, 0.225749900000, 0.914850100000, 0.029840000000},
        {540, 0.290400000000, 0.954000000000, 0.020300000000},
        {545, 0.359700000000, 0.980300000000, 0.013400000000},
        {550, 0.433449900000, 0.994950100000, 0.008749999000},
        {555, 0.512050100000, 1.000000000000, 0.005749999000},
        {560, 0.594500000000, 0.995000000000, 0.003900000000},
        {565, 0.678400000000, 0.978600000000, 0.002749999000},
        {570, 0.762100000000, 0.952000000000, 0.002100000000},
        {575, 0.842500000000, 0.915400000000, 0.001800000000},
        {580, 0.916300000000, 0.870000000000, 0.001650001000},
        {585, 0.978600000000, 0.816300000000, 0.001400000000},
        {590, 1.026300000000, 0.757000000000, 0.001100000000},
        {595, 1.056700000000, 0.694900000000, 0.001000000000},
        {600, 1.062200000000, 0.631000000000, 0.000800000000},
        {605, 1.045600000000, 0.566800000000, 0.000600000000},
        {610, 1.002600000000, 0.503000000000, 0.000340000000},
        {615, 0.938400000000, 0.441200000000, 0.000240000000},
        {620, 0.854449900000, 0.381000000000, 0.000190000000},
        {625, 0.751400000000, 0.321000000000, 0.000100000000},
        {630, 0.642400000000, 0.265000000000, 0.000049999990},
        {635, 0.541900000000, 0.217000000000, 0.000030000000},
        {640, 0.447900000000, 0.175000000000, 0.000020000000},
        {645, 0.360800000000, 0.138200000000, 0.000010000000},
        {650, 0.283500000000, 0.107000000000, 0.000000000000},
        {655, 0.218700000000, 0.081600000000, 0.000000000000},
        {660, 0.164900000000, 0.061000000000, 0.000000000000},
        {665, 0.121200000000, 0.044580000000, 0.000000000000},
        {670, 0.087400000000, 0.032000000000, 0.000000000000},
        {675, 0.063600000000, 0.023200000000, 0.000000000000},
        {680, 0.046770000000, 0.017000000000, 0.000000000000},
        {685, 0.032900000000, 0.011920000000, 0.000000000000},
        {690, 0.022700000000, 0.008210000000, 0.000000000000},
        {695, 0.015840000000, 0.005723000000, 0.000000000000},
        {700, 0.011359160000, 0.004102000000, 0.000000000000},
        {705, 0.008110916000, 0.002929000000, 0.000000000000},
        {710, 0.005790346000, 0.002091000000, 0.000000000000}
    };

    private AtmosphereSpectrum() {
    }

    public static double wavelength(int index) {
        return FIRST_WAVELENGTH + index * WAVELENGTH_STEP;
    }

    public static double rayleighScattering(double wavelength) {
        return RAYLEIGH_COEFFICIENT * Math.pow(wavelength * MICROMETERS_PER_NANOMETER, RAYLEIGH_WAVELENGTH_POWER) * METERS_PER_KILOMETER;
    }

    public static double ozoneAbsorption(double wavelength) {
        return MAX_OZONE_NUMBER_DENSITY * table(OZONE_CROSS_SECTION, wavelength) * METERS_PER_KILOMETER;
    }

    public static double solarIrradiance(double wavelength) {
        return table(SOLAR_IRRADIANCE, wavelength);
    }

    public static double[][] toLinearSrgb() {
        double[][] weights = new double[RGB_CHANNELS][WAVELENGTH_COUNT];
        double[] white = new double[RGB_CHANNELS];
        for (int index = 0; index < WAVELENGTH_COUNT; index++) {
            double[] xyz = colorMatchingBand(wavelength(index));
            double solar = solarIrradiance(wavelength(index));
            for (int channel = 0; channel < RGB_CHANNELS; channel++) {
                double[] row = XYZ_TO_LINEAR_SRGB[channel];
                weights[channel][index] = (row[0] * xyz[0] + row[1] * xyz[1] + row[2] * xyz[2]) * solar;
                white[channel] += weights[channel][index];
            }
        }
        for (int channel = 0; channel < RGB_CHANNELS; channel++) {
            for (int index = 0; index < WAVELENGTH_COUNT; index++) {
                weights[channel][index] /= white[channel];
            }
        }
        return weights;
    }

    static double[] colorMatchingBand(double center) {
        double[] xyz = new double[RGB_CHANNELS];
        double start = center - WAVELENGTH_STEP * HALF;
        int samples = (int) Math.round(WAVELENGTH_STEP / CIE_STEP);
        for (int sample = 0; sample <= samples; sample++) {
            double[] row = CIE_COLOR_MATCHING[(int) Math.round((start + sample * CIE_STEP - CIE_FIRST_WAVELENGTH) / CIE_STEP)];
            double weight = sample == 0 || sample == samples ? HALF * CIE_STEP : CIE_STEP;
            xyz[0] += row[CIE_X] * weight;
            xyz[1] += row[CIE_Y] * weight;
            xyz[2] += row[CIE_Z] * weight;
        }
        return xyz;
    }

    private static double table(double[] values, double wavelength) {
        double position = (wavelength - TABLE_FIRST_WAVELENGTH) / TABLE_STEP;
        int lower = (int) Math.floor(position);
        double fraction = position - lower;
        return values[lower] * (1.0 - fraction) + values[lower + 1] * fraction;
    }
}
