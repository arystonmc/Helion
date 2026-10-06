#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_image.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float SKY_DEVICE_DEPTH = 0.0;
const vec2 DARK_EMISSIVE_RANGE = vec2(0.6, 0.85);
const vec2 DAYLIGHT_EMISSIVE_RANGE = vec2(0.75, 0.95);
const float DARK_SATURATION_FLOOR = 0.5;
const float DAYLIGHT_SATURATION_FLOOR = 0.25;
const float DARK_EMISSIVE_BOOST = 8.0;
const float SKY_EMISSIVE_START = 0.9;
const float SKY_EMISSIVE_END = 1.0;
const float SKY_SATURATION_START = 0.05;
const float SKY_SATURATION_END = 0.15;
const float FOG_DISTANCE_START = 0.08;
const float FOG_DISTANCE_END = 0.2;

float emissiveWeight(vec3 encoded, bool sky) {
    float peak = helionMaxComponent(encoded);
    float colorfulness = helionSaturation(encoded);
    float fogDistance = smoothstep(FOG_DISTANCE_START, FOG_DISTANCE_END, distance(encoded, FogColor.rgb));
    if (sky) {
        return fogDistance * smoothstep(SKY_EMISSIVE_START, SKY_EMISSIVE_END, peak)
            * (1.0 - smoothstep(SKY_SATURATION_START, SKY_SATURATION_END, colorfulness));
    }
    vec2 range = mix(DARK_EMISSIVE_RANGE, DAYLIGHT_EMISSIVE_RANGE, Daylight);
    float saturationFloor = mix(DARK_SATURATION_FLOOR, DAYLIGHT_SATURATION_FLOOR, Daylight);
    return fogDistance * smoothstep(range.x, range.y, peak) * mix(saturationFloor, 1.0, colorfulness);
}

vec3 helionBloomTap(vec2 uv) {
    vec3 encoded = texture(SceneColorSampler, uv).rgb;
    bool sky = texture(SceneDepthSampler, uv).r == SKY_DEVICE_DEPTH;
    float boost = mix(DARK_EMISSIVE_BOOST, 1.0, Daylight);
    vec3 expanded = helionInverseNeutral(helionSrgbToLinear(encoded));
    return max(helionBloomThreshold(expanded * boost * emissiveWeight(encoded, sky)), 0.0);
}

#include <helion:helion_bloom_downsample.glsl>

void main() {
    vec2 texel = 1.0 / vec2(textureSize(SceneColorSampler, 0));
    fragColor = vec4(helionDownsample13(texCoord, texel, true), 1.0);
}
