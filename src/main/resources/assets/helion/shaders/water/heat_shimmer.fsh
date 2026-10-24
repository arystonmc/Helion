#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_geometry.glsl>

uniform sampler2D HeatSourceSampler;
uniform sampler2D GeometryLightSampler;

layout(std140) uniform HelionHeat {
    vec4 HeatTime;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_HEAT_TAPS = 8;
const float HELION_HEAT_TAP_SHARE = 0.012;
const float HELION_HEAT_MIN = 0.01;
const float HELION_HEAT_SELF = 0.6;
const float HELION_HEAT_AMPLITUDE = 0.0016;
const vec2 HELION_HEAT_FREQUENCY = vec2(55.0, 140.0);
const vec2 HELION_HEAT_SPEED = vec2(5.0, 9.0);
const float HELION_HEAT_WARP = 2.0;
const float HELION_HEAT_CROSS_X = 0.37;
const float HELION_HEAT_CROSS_Y = 0.53;

bool helionIsLava(ivec2 pixel, ivec2 size) {
    vec4 light = texelFetch(GeometryLightSampler, clamp(pixel, ivec2(0), size - 1), 0);
    return light.a > HELION_GEOMETRY_PRESENT_THRESHOLD && helionIsGeometryMaterial(light.a, HELION_GEOMETRY_LAVA);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 size = textureSize(GeometryLightSampler, 0);
    int spacing = max(int(float(size.y) * HELION_HEAT_TAP_SHARE), 1);
    float heat = helionIsLava(pixel, size) ? HELION_HEAT_SELF : 0.0;
    for (int tap = 1; tap <= HELION_HEAT_TAPS; tap++) {
        if (helionIsLava(pixel + ivec2(0, tap * spacing), size)) {
            heat = max(heat, 1.0 - float(tap - 1) / float(HELION_HEAT_TAPS));
        }
    }
    if (heat < HELION_HEAT_MIN) {
        discard;
    }
    vec2 phase = texCoord.yx * HELION_HEAT_FREQUENCY + HeatTime.x * HELION_HEAT_SPEED;
    vec2 wobble = vec2(
        sin(phase.x + sin(phase.y * HELION_HEAT_CROSS_X) * HELION_HEAT_WARP),
        cos(phase.y + sin(phase.x * HELION_HEAT_CROSS_Y) * HELION_HEAT_WARP)
    ) * HELION_HEAT_AMPLITUDE * heat;
    fragColor = vec4(texture(HeatSourceSampler, texCoord + wobble).rgb, 1.0);
}
