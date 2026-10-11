#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_depth_of_field.glsl>

uniform sampler2D DepthSampler;
uniform sampler2D PreviousFocusSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_FOCUS_SPREAD = 0.04;
const float HELION_MAX_FOCUS_DEPTH = 256.0;
const vec2 HELION_SCREEN_CENTER = vec2(0.5);
const int HELION_FOCUS_RADIUS = 1;

void main() {
    ivec2 size = textureSize(DepthSampler, 0);
    float focus = HELION_MAX_FOCUS_DEPTH;
    for (int y = -HELION_FOCUS_RADIUS; y <= HELION_FOCUS_RADIUS; y++) {
        for (int x = -HELION_FOCUS_RADIUS; x <= HELION_FOCUS_RADIUS; x++) {
            vec2 uv = HELION_SCREEN_CENTER + vec2(x, y) * HELION_FOCUS_SPREAD;
            float depth = texelFetch(DepthSampler, ivec2(uv * vec2(size)), 0).r;
            focus = min(focus, helionDofViewDepth(depth));
        }
    }
    float previous = texelFetch(PreviousFocusSampler, ivec2(0), 0).r;
    fragColor = vec4(mix(previous, focus, FocusBlend), 0.0, 0.0, 1.0);
}
