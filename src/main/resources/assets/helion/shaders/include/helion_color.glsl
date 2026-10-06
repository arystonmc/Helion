#ifndef HELION_COLOR_GLSL
#define HELION_COLOR_GLSL

const vec3 HELION_LUMINANCE_WEIGHTS = vec3(0.2126, 0.7152, 0.0722);
const float HELION_SRGB_LINEAR_LIMIT = 0.0031308;
const float HELION_SRGB_ENCODED_LIMIT = 0.04045;
const float HELION_SRGB_LINEAR_SLOPE = 12.92;
const float HELION_SRGB_SCALE = 1.055;
const float HELION_SRGB_OFFSET = 0.055;
const float HELION_SRGB_GAMMA = 2.4;

const float HELION_NEUTRAL_START_COMPRESSION = 0.76;
const float HELION_NEUTRAL_MAX_PEAK = 16.0;

const mat3 HELION_LINEAR_SRGB_TO_REC2020 = mat3(
    vec3(0.6274, 0.0691, 0.0164),
    vec3(0.3293, 0.9195, 0.0880),
    vec3(0.0433, 0.0113, 0.8956)
);
const mat3 HELION_LINEAR_REC2020_TO_SRGB = mat3(
    vec3(1.6605, -0.1246, -0.0182),
    vec3(-0.5876, 1.1329, -0.1006),
    vec3(-0.0728, -0.0083, 1.1187)
);
const mat3 HELION_AGX_INSET = mat3(
    vec3(0.856627153315983, 0.137318972929847, 0.11189821299995),
    vec3(0.0951212405381588, 0.761241990602591, 0.0767994186031903),
    vec3(0.0482516061458583, 0.101439036467562, 0.811302368396859)
);
const mat3 HELION_AGX_OUTSET = mat3(
    vec3(1.1271005818144368, -0.1413297634984383, -0.14132976349843826),
    vec3(-0.11060664309660323, 1.157823702216272, -0.11060664309660294),
    vec3(-0.016493938717834573, -0.016493938717834257, 1.2519364065950405)
);
const float HELION_AGX_MIN_EV = -12.47393;
const float HELION_AGX_MAX_EV = 4.026069;
const float HELION_AGX_LOG_FLOOR = 1.0e-10;
const float HELION_AGX_DISPLAY_GAMMA = 2.2;

const vec2 HELION_NOISE_WEIGHTS = vec2(0.06711056, 0.00583715);
const float HELION_NOISE_SCALE = 52.9829189;
const vec2 HELION_NOISE_SECOND_OFFSET = vec2(5.588238);

float helionMaxComponent(vec3 color) {
    return max(color.r, max(color.g, color.b));
}

float helionMinComponent(vec3 color) {
    return min(color.r, min(color.g, color.b));
}

float helionLuminance(vec3 color) {
    return dot(color, HELION_LUMINANCE_WEIGHTS);
}

vec3 helionSrgbToLinear(vec3 encoded) {
    vec3 low = encoded / HELION_SRGB_LINEAR_SLOPE;
    vec3 high = pow((encoded + HELION_SRGB_OFFSET) / HELION_SRGB_SCALE, vec3(HELION_SRGB_GAMMA));
    return mix(high, low, lessThanEqual(encoded, vec3(HELION_SRGB_ENCODED_LIMIT)));
}

vec3 helionLinearToSrgb(vec3 linearColor) {
    linearColor = clamp(linearColor, 0.0, 1.0);
    vec3 low = linearColor * HELION_SRGB_LINEAR_SLOPE;
    vec3 high = HELION_SRGB_SCALE * pow(linearColor, vec3(1.0 / HELION_SRGB_GAMMA)) - HELION_SRGB_OFFSET;
    return mix(high, low, lessThanEqual(linearColor, vec3(HELION_SRGB_LINEAR_LIMIT)));
}

float helionNeutralShoulder(float peak) {
    if (peak <= HELION_NEUTRAL_START_COMPRESSION) {
        return peak;
    }
    float range = 1.0 - HELION_NEUTRAL_START_COMPRESSION;
    return 1.0 - range * range / (peak + range - HELION_NEUTRAL_START_COMPRESSION);
}

float helionInverseNeutralShoulder(float compressedPeak) {
    if (compressedPeak <= HELION_NEUTRAL_START_COMPRESSION) {
        return compressedPeak;
    }
    float range = 1.0 - HELION_NEUTRAL_START_COMPRESSION;
    return range * range / (1.0 - compressedPeak) - range + HELION_NEUTRAL_START_COMPRESSION;
}

vec3 helionNeutral(vec3 color) {
    color = max(color, 0.0);
    float peak = helionMaxComponent(color);
    return peak > HELION_NEUTRAL_START_COMPRESSION ? color * (helionNeutralShoulder(peak) / peak) : color;
}

vec3 helionInverseNeutral(vec3 color) {
    color = clamp(color, 0.0, helionNeutralShoulder(HELION_NEUTRAL_MAX_PEAK));
    float peak = helionMaxComponent(color);
    return peak > HELION_NEUTRAL_START_COMPRESSION ? color * (helionInverseNeutralShoulder(peak) / peak) : color;
}

vec3 helionAgxContrast(vec3 x) {
    vec3 x2 = x * x;
    vec3 x4 = x2 * x2;
    return 15.5 * x4 * x2 - 40.14 * x4 * x + 31.96 * x4 - 6.868 * x2 * x + 0.4298 * x2 + 0.1191 * x - 0.00232;
}

vec3 helionAgx(vec3 color) {
    color = HELION_AGX_INSET * (HELION_LINEAR_SRGB_TO_REC2020 * color);
    color = log2(max(color, HELION_AGX_LOG_FLOOR));
    color = clamp((color - HELION_AGX_MIN_EV) / (HELION_AGX_MAX_EV - HELION_AGX_MIN_EV), 0.0, 1.0);
    color = HELION_AGX_OUTSET * helionAgxContrast(color);
    color = pow(max(color, 0.0), vec3(HELION_AGX_DISPLAY_GAMMA));
    return clamp(HELION_LINEAR_REC2020_TO_SRGB * color, 0.0, 1.0);
}

float helionInterleavedGradientNoise(vec2 pixel) {
    return fract(HELION_NOISE_SCALE * fract(dot(pixel, HELION_NOISE_WEIGHTS)));
}

float helionTriangularNoise(vec2 pixel) {
    return helionInterleavedGradientNoise(pixel) + helionInterleavedGradientNoise(pixel + HELION_NOISE_SECOND_OFFSET) - 1.0;
}

#endif
