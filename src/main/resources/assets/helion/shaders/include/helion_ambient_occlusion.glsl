#ifndef HELION_AMBIENT_OCCLUSION_GLSL
#define HELION_AMBIENT_OCCLUSION_GLSL

layout(std140) uniform HelionAmbientOcclusion {
    vec2 ViewportPixelSize;
    float EffectRadius;
    float EffectFalloffRange;
    float SampleDistributionPower;
    float ThinOccluderCompensation;
    float FinalValuePower;
    float DenoiseBlurBeta;
    float Strength;
    float MaxScreenRadius;
    int SliceCount;
    int StepsPerSlice;
};

const float HELION_OCCLUSION_TERM_SCALE = 1.5;
const float HELION_EDGE_LEVELS = 2.9;
const float HELION_EDGE_STEPS = 3.0;

float helionPackEdges(vec4 edgesLRTB) {
    edgesLRTB = round(clamp(edgesLRTB, 0.0, 1.0) * HELION_EDGE_LEVELS);
    return dot(edgesLRTB, vec4(64.0 / 255.0, 16.0 / 255.0, 4.0 / 255.0, 1.0 / 255.0));
}

vec4 helionUnpackEdges(float packedEdges) {
    int packedValue = int(packedEdges * 255.5);
    return vec4(
        float((packedValue >> 6) & 3),
        float((packedValue >> 4) & 3),
        float((packedValue >> 2) & 3),
        float(packedValue & 3)
    ) / HELION_EDGE_STEPS;
}

#endif
