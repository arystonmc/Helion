#version 460

#include <helion:helion_color.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D Filtered;
layout(set = 0, binding = 1) uniform sampler2D InputSampler;
layout(set = 0, binding = 2) uniform sampler2D GuideSampler;

layout(push_constant) uniform HelionDenoiseFilter {
    ivec4 Step;
};

const int HELION_RADIUS = 2;
const float HELION_KERNEL[3] = float[](0.375, 0.25, 0.0625);
const float HELION_NORMAL_POWER = 128.0;
const float HELION_DEPTH_SHARPNESS = 0.03;
const float HELION_LUMINANCE_SIGMA = 4.0;
const float HELION_MIN_WEIGHT = 1.0e-4;
const float HELION_MIN_SIGMA = 1.0e-3;
const float HELION_NO_SURFACE = 0.0;

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(Filtered);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    vec4 guide = texelFetch(GuideSampler, pixel, 0);
    if (guide.w == HELION_NO_SURFACE) {
        return;
    }
    vec4 center = texelFetch(InputSampler, pixel, 0);
    if (guide.w < HELION_NO_SURFACE) {
        imageStore(Filtered, pixel, center);
        return;
    }
    float centerLuminance = helionLuminance(center.rgb);
    float sigma = HELION_LUMINANCE_SIGMA * sqrt(max(center.a, 0.0)) + HELION_MIN_SIGMA;
    float depthScale = HELION_DEPTH_SHARPNESS * guide.w * float(Step.x) + HELION_MIN_WEIGHT;
    vec3 sum = vec3(0.0);
    float variance = 0.0;
    float weights = 0.0;
    for (int y = -HELION_RADIUS; y <= HELION_RADIUS; y++) {
        for (int x = -HELION_RADIUS; x <= HELION_RADIUS; x++) {
            ivec2 tap = clamp(pixel + ivec2(x, y) * Step.x, ivec2(0), size - 1);
            vec4 tapGuide = texelFetch(GuideSampler, tap, 0);
            if (tapGuide.w <= 0.0) {
                continue;
            }
            vec4 value = texelFetch(InputSampler, tap, 0);
            float normalWeight = pow(max(dot(tapGuide.xyz, guide.xyz), 0.0), HELION_NORMAL_POWER);
            float depthWeight = exp(-abs(tapGuide.w - guide.w) / depthScale);
            float luminanceWeight = exp(-abs(helionLuminance(value.rgb) - centerLuminance) / sigma);
            float weight = HELION_KERNEL[abs(x)] * HELION_KERNEL[abs(y)] * normalWeight * depthWeight * luminanceWeight;
            sum += value.rgb * weight;
            variance += value.a * weight * weight;
            weights += weight;
        }
    }
    imageStore(Filtered, pixel, weights > HELION_MIN_WEIGHT ? vec4(sum / weights, variance / (weights * weights)) : center);
}
