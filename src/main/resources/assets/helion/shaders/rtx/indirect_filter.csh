#version 460

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D Filtered;
layout(set = 0, binding = 1) uniform sampler2D InputSampler;
layout(set = 0, binding = 2) uniform sampler2D GuideSampler;

layout(push_constant) uniform HelionIndirectFilter {
    ivec4 Step;
};

const int HELION_PT_RADIUS = 2;
const float HELION_PT_KERNEL[3] = float[](0.375, 0.25, 0.0625);
const float HELION_PT_NORMAL_POWER = 32.0;
const float HELION_PT_DEPTH_SHARPNESS = 0.03;
const float HELION_PT_MIN_WEIGHT = 1.0e-4;

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(Filtered);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    vec4 center = texelFetch(InputSampler, pixel, 0);
    vec4 guide = texelFetch(GuideSampler, pixel, 0);
    if (guide.w <= 0.0) {
        imageStore(Filtered, pixel, center);
        return;
    }
    vec3 sum = vec3(0.0);
    float weights = 0.0;
    for (int y = -HELION_PT_RADIUS; y <= HELION_PT_RADIUS; y++) {
        for (int x = -HELION_PT_RADIUS; x <= HELION_PT_RADIUS; x++) {
            ivec2 tap = clamp(pixel + ivec2(x, y) * Step.x, ivec2(0), size - 1);
            vec4 tapGuide = texelFetch(GuideSampler, tap, 0);
            if (tapGuide.w <= 0.0) {
                continue;
            }
            float kernel = HELION_PT_KERNEL[abs(x)] * HELION_PT_KERNEL[abs(y)];
            float normalWeight = pow(max(dot(tapGuide.xyz, guide.xyz), 0.0), HELION_PT_NORMAL_POWER);
            float depthWeight = exp(-abs(tapGuide.w - guide.w) / (HELION_PT_DEPTH_SHARPNESS * guide.w * float(Step.x) + HELION_PT_MIN_WEIGHT));
            float weight = kernel * normalWeight * depthWeight;
            sum += texelFetch(InputSampler, tap, 0).rgb * weight;
            weights += weight;
        }
    }
    imageStore(Filtered, pixel, vec4(weights > HELION_PT_MIN_WEIGHT ? sum / weights : center.rgb, center.a));
}
