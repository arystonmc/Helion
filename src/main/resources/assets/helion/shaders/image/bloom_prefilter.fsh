#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_image.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float SKY_DEVICE_DEPTH = 0.0;
const float EMISSIVE_START = 0.75;
const float EMISSIVE_END = 0.95;
const float SATURATION_FLOOR = 0.25;
const float SKY_EMISSIVE_START = 0.9;
const float SKY_EMISSIVE_END = 1.0;
const float SKY_SATURATION_START = 0.05;
const float SKY_SATURATION_END = 0.15;
const float FOG_DISTANCE_START = 0.08;
const float FOG_DISTANCE_END = 0.2;

float saturation(vec3 encoded) {
    float peak = helionMaxComponent(encoded);
    return peak > 0.0 ? (peak - helionMinComponent(encoded)) / peak : 0.0;
}

float emissiveWeight(vec3 encoded, bool sky) {
    float peak = helionMaxComponent(encoded);
    float colorfulness = saturation(encoded);
    float fogDistance = smoothstep(FOG_DISTANCE_START, FOG_DISTANCE_END, distance(encoded, FogColor.rgb));
    if (sky) {
        return fogDistance * smoothstep(SKY_EMISSIVE_START, SKY_EMISSIVE_END, peak)
            * (1.0 - smoothstep(SKY_SATURATION_START, SKY_SATURATION_END, colorfulness));
    }
    return fogDistance * smoothstep(EMISSIVE_START, EMISSIVE_END, peak) * mix(SATURATION_FLOOR, 1.0, colorfulness);
}

vec3 helionBloomTap(vec2 uv) {
    vec3 encoded = texture(SceneColorSampler, uv).rgb;
    bool sky = texture(SceneDepthSampler, uv).r == SKY_DEVICE_DEPTH;
    vec3 expanded = helionInverseNeutral(helionSrgbToLinear(encoded));
    return max(helionBloomThreshold(expanded * emissiveWeight(encoded, sky)), 0.0);
}

#include <helion:helion_bloom_downsample.glsl>

void main() {
    vec2 texel = 1.0 / vec2(textureSize(SceneColorSampler, 0));
    fragColor = vec4(helionDownsample13(texCoord, texel, true), 1.0);
}
