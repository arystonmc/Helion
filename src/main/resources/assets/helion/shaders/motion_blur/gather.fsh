#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_view.glsl>
#include <helion:helion_motion_blur.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D DepthSampler;
uniform sampler2D NeighborMaxSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_GATHER_TAPS = 12;
const float HELION_MIN_BLUR_PIXELS = 0.5;
const float HELION_SOFT_DEPTH_EXTENT = 0.5;
const float HELION_CYLINDER_EDGE = 0.05;
const vec3 HELION_NOISE_WEIGHTS = vec3(0.06711056, 0.00583715, 52.9829189);
const float HELION_CYLINDER_WEIGHT = 2.0;
const float HELION_HALF_SPAN = 0.5;

float helionSoftDepthCompare(float first, float second) {
    return clamp(1.0 - (first - second) / HELION_SOFT_DEPTH_EXTENT, 0.0, 1.0);
}

float helionCone(float distance, float velocityLength) {
    return clamp(1.0 - distance / max(velocityLength, HELION_MIN_BLUR_PIXELS), 0.0, 1.0);
}

float helionCylinder(float distance, float velocityLength) {
    float edge = velocityLength * HELION_CYLINDER_EDGE;
    return 1.0 - smoothstep(velocityLength - edge, velocityLength + edge, distance);
}

float helionBlurViewDepth(float deviceDepth) {
    return deviceDepth <= HELION_SKY_DEVICE_DEPTH ? HELION_SKY_VIEW_DEPTH : helionLinearDepth(deviceDepth);
}

float helionInterleavedNoise(vec2 pixel) {
    return fract(HELION_NOISE_WEIGHTS.z * fract(dot(pixel, HELION_NOISE_WEIGHTS.xy)));
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec3 center = texelFetch(SceneColorSampler, pixel, 0).rgb;
    vec2 neighborMax = texelFetch(NeighborMaxSampler, pixel / HELION_MOTION_TILE_SIZE, 0).rg;
    if (length(neighborMax) < HELION_MIN_BLUR_PIXELS) {
        fragColor = vec4(center, 1.0);
        return;
    }
    ivec2 lastPixel = textureSize(SceneColorSampler, 0) - 1;
    float centerDepth = texelFetch(DepthSampler, pixel, 0).r;
    float centerViewDepth = helionBlurViewDepth(centerDepth);
    float centerSpeed = length(helionBlurVelocity(vec2(pixel) + 0.5, centerDepth));
    float weight = 1.0 / max(centerSpeed, HELION_MIN_BLUR_PIXELS);
    vec3 sum = center * weight;
    float jitter = helionInterleavedNoise(gl_FragCoord.xy) - HELION_HALF_SPAN;
    for (int tap = 0; tap < HELION_GATHER_TAPS; tap++) {
        float offset = mix(-HELION_HALF_SPAN, HELION_HALF_SPAN, (float(tap) + jitter + 1.0) / float(HELION_GATHER_TAPS + 1));
        ivec2 samplePixel = clamp(ivec2(gl_FragCoord.xy + neighborMax * offset), ivec2(0), lastPixel);
        float sampleDepth = texelFetch(DepthSampler, samplePixel, 0).r;
        float sampleViewDepth = helionBlurViewDepth(sampleDepth);
        float sampleSpeed = length(helionBlurVelocity(vec2(samplePixel) + 0.5, sampleDepth));
        float distance = length(vec2(samplePixel - pixel));
        float foreground = helionSoftDepthCompare(centerViewDepth, sampleViewDepth);
        float background = helionSoftDepthCompare(sampleViewDepth, centerViewDepth);
        float sampleWeight = foreground * helionCone(distance, sampleSpeed)
            + background * helionCone(distance, centerSpeed)
            + helionCylinder(distance, sampleSpeed) * helionCylinder(distance, centerSpeed) * HELION_CYLINDER_WEIGHT;
        sum += texelFetch(SceneColorSampler, samplePixel, 0).rgb * sampleWeight;
        weight += sampleWeight;
    }
    fragColor = vec4(sum / weight, 1.0);
}
