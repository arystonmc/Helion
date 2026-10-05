#ifndef HELION_VIEW_GLSL
#define HELION_VIEW_GLSL

#include <minecraft:projection.glsl>

const float HELION_SKY_DEVICE_DEPTH = 0.0;
const float HELION_SKY_VIEW_DEPTH = 1.0e6;

float helionLinearDepth(float deviceDepth) {
    #ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    deviceDepth = (deviceDepth - 0.5) * 2.0;
    #endif
    return ProjMat[3][2] / (deviceDepth + ProjMat[2][2]);
}

vec2 helionTanHalfFov() {
    return vec2(1.0 / abs(ProjMat[0][0]), 1.0 / abs(ProjMat[1][1]));
}

vec3 helionViewPosition(vec2 screenUv, float viewDepth) {
    vec2 tanHalfFov = helionTanHalfFov();
    vec2 ndcToViewMul = tanHalfFov * vec2(2.0, -2.0);
    vec2 ndcToViewAdd = tanHalfFov * vec2(-1.0, 1.0);
    return vec3((ndcToViewMul * screenUv + ndcToViewAdd) * viewDepth, viewDepth);
}

bool helionIsSky(float viewDepth) {
    return viewDepth >= HELION_SKY_VIEW_DEPTH;
}

#endif
