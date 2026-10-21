#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_depth_of_field.glsl>

uniform sampler2D DepthSampler;
uniform sampler2D PreviousFocusSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_FOCUS_SAMPLES = 25;
const float HELION_FOCUS_RADIUS = 0.03;
const float HELION_GOLDEN_ANGLE = 2.39996323;
const float HELION_MAX_FOCUS_DEPTH = 16384.0;
const float HELION_FOCUS_HYSTERESIS = 0.04;
const vec2 HELION_SCREEN_CENTER = vec2(0.5);
const int HELION_FOCUS_QUANTILE = 4;

float helionSampleDepth(ivec2 size, int index) {
    float distance = sqrt(float(index) / float(HELION_FOCUS_SAMPLES)) * HELION_FOCUS_RADIUS;
    float angle = float(index) * HELION_GOLDEN_ANGLE;
    vec2 offset = vec2(cos(angle) * float(size.y) / float(size.x), sin(angle)) * distance;
    ivec2 pixel = clamp(ivec2((HELION_SCREEN_CENTER + offset) * vec2(size)), ivec2(0), size - 1);
    return helionDofViewDepth(texelFetch(DepthSampler, pixel, 0).r);
}

float helionMedianFocus(ivec2 size) {
    float depths[HELION_FOCUS_SAMPLES];
    int count = 0;
    for (int index = 0; index < HELION_FOCUS_SAMPLES; index++) {
        float depth = helionSampleDepth(size, index);
        if (depth < HELION_SKY_VIEW_DEPTH) {
            int slot = count;
            while (slot > 0 && depths[slot - 1] > depth) {
                depths[slot] = depths[slot - 1];
                slot--;
            }
            depths[slot] = depth;
            count++;
        }
    }
    return count == 0 ? HELION_MAX_FOCUS_DEPTH : min(depths[count / HELION_FOCUS_QUANTILE], HELION_MAX_FOCUS_DEPTH);
}

void main() {
    float target = helionMedianFocus(textureSize(DepthSampler, 0));
    float previous = max(texelFetch(PreviousFocusSampler, ivec2(0), 0).r, HELION_DOF_MIN_DEPTH);
    float change = log(target / previous);
    if (abs(change) < HELION_FOCUS_HYSTERESIS) {
        change = 0.0;
    }
    float rest = 1.0 - FocusBlend;
    float blend = change < 0.0 ? 1.0 - rest * rest : FocusBlend;
    fragColor = vec4(previous * exp(change * blend), 0.0, 0.0, 1.0);
}
