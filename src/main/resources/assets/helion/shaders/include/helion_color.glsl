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

const float HELION_FILM_CONTRAST = 0.25;
const float HELION_FILM_VIBRANCE = 0.2;

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

float helionSaturation(vec3 color) {
    float peak = helionMaxComponent(color);
    return peak > 0.0 ? (peak - helionMinComponent(color)) / peak : 0.0;
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

vec3 helionFilmGrade(vec3 encoded) {
    vec3 contrasted = mix(encoded, smoothstep(0.0, 1.0, encoded), HELION_FILM_CONTRAST);
    float luminance = helionLuminance(contrasted);
    float vibrance = 1.0 + HELION_FILM_VIBRANCE * (1.0 - helionSaturation(contrasted));
    return clamp(luminance + (contrasted - luminance) * vibrance, 0.0, 1.0);
}

float helionInterleavedGradientNoise(vec2 pixel) {
    return fract(HELION_NOISE_SCALE * fract(dot(pixel, HELION_NOISE_WEIGHTS)));
}

float helionTriangularNoise(vec2 pixel) {
    return helionInterleavedGradientNoise(pixel) + helionInterleavedGradientNoise(pixel + HELION_NOISE_SECOND_OFFSET) - 1.0;
}

#endif
