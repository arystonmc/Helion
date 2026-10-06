#ifndef HELION_IMAGE_GLSL
#define HELION_IMAGE_GLSL

#include <helion:helion_color.glsl>

layout(std140) uniform HelionImage {
    vec4 FogColor;
    float ExposureScale;
    float BloomStrength;
    float BloomThreshold;
    float BloomKnee;
    float Daylight;
    int ToneMapperId;
    int DitherEnabled;
    float SharpenStrength;
};

const int HELION_TONE_MAPPER_FILMIC = 1;
const int HELION_TONE_MAPPER_NONE = 2;
const int HELION_DITHER_OFF = 0;
const float HELION_DISPLAY_LEVELS = 255.0;
const float HELION_BLOOM_EPSILON = 1.0e-4;
const float HELION_FILM_BLEACH = 0.25;
const float HELION_FILM_BLEACH_HALF = 2.0;

vec3 helionExpandScene(vec3 linearColor) {
    return ToneMapperId == HELION_TONE_MAPPER_NONE ? linearColor : helionInverseNeutral(linearColor);
}

vec3 helionToneMap(vec3 color) {
    return ToneMapperId == HELION_TONE_MAPPER_NONE ? clamp(color, 0.0, 1.0) : helionNeutral(color);
}

vec3 helionDisplay(vec3 base, vec3 lit) {
    vec3 mapped = helionToneMap(lit);
    if (ToneMapperId != HELION_TONE_MAPPER_FILMIC) {
        return helionLinearToSrgb(mapped);
    }
    float overshoot = max(helionMaxComponent(lit) - helionMaxComponent(base), 0.0);
    float bleach = HELION_FILM_BLEACH * overshoot / (overshoot + HELION_FILM_BLEACH_HALF);
    mapped = mix(mapped, vec3(helionMaxComponent(mapped)), bleach);
    return helionFilmGrade(helionLinearToSrgb(mapped));
}

vec3 helionBloomThreshold(vec3 color) {
    float brightness = helionMaxComponent(color);
    float knee = clamp(brightness - BloomThreshold + BloomKnee, 0.0, 2.0 * BloomKnee);
    knee = knee * knee / (4.0 * BloomKnee + HELION_BLOOM_EPSILON);
    float contribution = max(knee, brightness - BloomThreshold) / max(brightness, HELION_BLOOM_EPSILON);
    return color * contribution;
}

vec3 helionDither(vec3 encoded, vec2 pixel) {
    if (DitherEnabled == HELION_DITHER_OFF) {
        return encoded;
    }
    return encoded + helionTriangularNoise(pixel) / HELION_DISPLAY_LEVELS;
}

#endif
