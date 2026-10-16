#ifndef HELION_DEPTH_OF_FIELD_GLSL
#define HELION_DEPTH_OF_FIELD_GLSL

#include <helion:helion_view.glsl>

layout(std140) uniform HelionDepthOfField {
    vec2 InverseScreenSize;
    float MaxBlurPixels;
    float FocusBlend;
    float Aperture;
};

const float HELION_DOF_SHARP_BAND = 0.25;
const float HELION_DOF_BAND_PER_LOG_DISTANCE = 0.08;
const float HELION_DOF_NEAR_LIMIT = 0.35;
const float HELION_DOF_FAR_LIMIT = 0.4;
const float HELION_DOF_MIN_DEPTH = 0.05;
const float HELION_DOF_KNEE = 0.4;
const float HELION_DOF_CLOUD_DISTANCE = 48.0;

#ifdef HELION_DOF_TERRAIN_DEPTH
uniform sampler2D TerrainDepthSampler;
#endif

float helionDofViewDepth(float deviceDepth) {
    return deviceDepth <= HELION_SKY_DEVICE_DEPTH ? HELION_SKY_VIEW_DEPTH : helionLinearDepth(deviceDepth);
}

float helionCircleOfConfusion(float viewDepth, float focusDepth) {
    float ratio = log(max(viewDepth, HELION_DOF_MIN_DEPTH) / max(focusDepth, HELION_DOF_MIN_DEPTH));
    float band = HELION_DOF_SHARP_BAND + HELION_DOF_BAND_PER_LOG_DISTANCE * log(1.0 + focusDepth);
    float excess = max(abs(ratio) - band, 0.0);
    float eased = excess < HELION_DOF_KNEE ? excess * excess / (2.0 * HELION_DOF_KNEE) : excess - 0.5 * HELION_DOF_KNEE;
    float blur = eased * Aperture;
    return ratio > 0.0 ? min(blur, HELION_DOF_FAR_LIMIT) : -min(blur, HELION_DOF_NEAR_LIMIT);
}

float helionDofCircle(float deviceDepth, vec2 uv, float focusDepth) {
    float viewDepth = helionDofViewDepth(deviceDepth);
    if (viewDepth >= HELION_SKY_VIEW_DEPTH) {
        return 0.0;
    }
    #ifdef HELION_DOF_TERRAIN_DEPTH
    if (viewDepth > HELION_DOF_CLOUD_DISTANCE && texture(TerrainDepthSampler, uv).r <= HELION_SKY_DEVICE_DEPTH) {
        return 0.0;
    }
    #endif
    return helionCircleOfConfusion(viewDepth, focusDepth);
}

#endif
