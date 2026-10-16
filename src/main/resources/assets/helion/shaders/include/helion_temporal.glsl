#ifndef HELION_TEMPORAL_GLSL
#define HELION_TEMPORAL_GLSL

layout(std140) uniform HelionTemporal {
    mat4 Reprojection;
    vec2 InverseScreenSize;
    vec2 InputSize;
    vec2 JitterPixels;
    float CurrentWeight;
    float HistoryValid;
};

#endif
