#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_depth_of_field.glsl>

uniform sampler2D HalfSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_DOF_TAPS = 32;
const float HELION_GOLDEN_ANGLE = 2.39996323;
const float HELION_HALF_RESOLUTION = 0.5;
const float HELION_EDGE_SOFTNESS = 1.0;

void main() {
    vec2 halfTexel = InverseScreenSize / HELION_HALF_RESOLUTION;
    vec4 center = texture(HalfSampler, texCoord);
    float radius = abs(center.a) * MaxBlurPixels * HELION_HALF_RESOLUTION;
    vec3 sum = center.rgb;
    float weight = 1.0;
    for (int tap = 1; tap <= HELION_DOF_TAPS; tap++) {
        float distance = sqrt(float(tap) / float(HELION_DOF_TAPS)) * max(radius, 1.0);
        float angle = float(tap) * HELION_GOLDEN_ANGLE;
        vec4 neighbor = texture(HalfSampler, texCoord + vec2(cos(angle), sin(angle)) * distance * halfTexel);
        float reach = abs(neighbor.a) * MaxBlurPixels * HELION_HALF_RESOLUTION;
        float coverage = clamp(reach - distance + HELION_EDGE_SOFTNESS, 0.0, 1.0);
        float behind = neighbor.a > center.a ? min(coverage, clamp(radius - distance + HELION_EDGE_SOFTNESS, 0.0, 1.0)) : coverage;
        sum += neighbor.rgb * behind;
        weight += behind;
    }
    fragColor = vec4(sum / weight, center.a);
}
