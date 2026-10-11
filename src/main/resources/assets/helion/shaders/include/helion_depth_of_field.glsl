#ifndef HELION_DEPTH_OF_FIELD_GLSL
#define HELION_DEPTH_OF_FIELD_GLSL

#include <helion:helion_view.glsl>

layout(std140) uniform HelionDepthOfField {
    vec2 InverseScreenSize;
    float MaxBlurPixels;
    float FocusBlend;
    float Aperture;
};

float helionDofViewDepth(float deviceDepth) {
    return deviceDepth <= HELION_SKY_DEVICE_DEPTH ? HELION_SKY_VIEW_DEPTH : helionLinearDepth(deviceDepth);
}

float helionCircleOfConfusion(float viewDepth, float focusDepth) {
    return clamp((1.0 - focusDepth / viewDepth) * Aperture, -1.0, 1.0);
}

#endif
