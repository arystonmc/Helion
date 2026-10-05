#ifndef HELION_AMBIENT_OCCLUSION_DENOISE_GLSL
#define HELION_AMBIENT_OCCLUSION_DENOISE_GLSL

#include <helion:helion_ambient_occlusion.glsl>

uniform sampler2D OcclusionSampler;
uniform sampler2D EdgesSampler;

const float HELION_DIAGONAL_WEIGHT = 0.425;
const float HELION_LEAK_THRESHOLD = 2.5;
const float HELION_LEAK_STRENGTH = 0.5;
const float HELION_INTERMEDIATE_BLUR_DIVISOR = 5.0;

ivec2 helionClampPixel(ivec2 pixel) {
    return clamp(pixel, ivec2(0), textureSize(OcclusionSampler, 0) - 1);
}

float helionOcclusionAt(ivec2 pixel) {
    return texelFetch(OcclusionSampler, helionClampPixel(pixel), 0).r;
}

vec4 helionEdgesAt(ivec2 pixel) {
    return helionUnpackEdges(texelFetch(EdgesSampler, helionClampPixel(pixel), 0).g);
}

float helionDenoise(ivec2 pixel, bool finalPass) {
    vec4 edgesCenter = helionEdgesAt(pixel);
    vec4 edgesLeft = helionEdgesAt(pixel + ivec2(-1, 0));
    vec4 edgesRight = helionEdgesAt(pixel + ivec2(1, 0));
    vec4 edgesTop = helionEdgesAt(pixel + ivec2(0, -1));
    vec4 edgesBottom = helionEdgesAt(pixel + ivec2(0, 1));

    edgesCenter *= vec4(edgesLeft.y, edgesRight.x, edgesTop.w, edgesBottom.z);
    float edginess = clamp(4.0 - HELION_LEAK_THRESHOLD - dot(edgesCenter, vec4(1.0)), 0.0, 1.0)
        / (4.0 - HELION_LEAK_THRESHOLD) * HELION_LEAK_STRENGTH;
    edgesCenter = clamp(edgesCenter + edginess, 0.0, 1.0);

    float weightTopLeft = HELION_DIAGONAL_WEIGHT * (edgesCenter.x * edgesLeft.z + edgesCenter.z * edgesTop.x);
    float weightTopRight = HELION_DIAGONAL_WEIGHT * (edgesCenter.z * edgesTop.y + edgesCenter.y * edgesRight.z);
    float weightBottomLeft = HELION_DIAGONAL_WEIGHT * (edgesCenter.w * edgesBottom.x + edgesCenter.x * edgesLeft.w);
    float weightBottomRight = HELION_DIAGONAL_WEIGHT * (edgesCenter.y * edgesRight.w + edgesCenter.w * edgesBottom.y);

    float centerWeight = finalPass ? DenoiseBlurBeta : DenoiseBlurBeta / HELION_INTERMEDIATE_BLUR_DIVISOR;
    float sum = helionOcclusionAt(pixel) * centerWeight;
    float sumWeight = centerWeight;

    sum += helionOcclusionAt(pixel + ivec2(-1, 0)) * edgesCenter.x;
    sum += helionOcclusionAt(pixel + ivec2(1, 0)) * edgesCenter.y;
    sum += helionOcclusionAt(pixel + ivec2(0, -1)) * edgesCenter.z;
    sum += helionOcclusionAt(pixel + ivec2(0, 1)) * edgesCenter.w;
    sum += helionOcclusionAt(pixel + ivec2(-1, -1)) * weightTopLeft;
    sum += helionOcclusionAt(pixel + ivec2(1, -1)) * weightTopRight;
    sum += helionOcclusionAt(pixel + ivec2(-1, 1)) * weightBottomLeft;
    sum += helionOcclusionAt(pixel + ivec2(1, 1)) * weightBottomRight;
    sumWeight += dot(edgesCenter, vec4(1.0)) + weightTopLeft + weightTopRight + weightBottomLeft + weightBottomRight;

    return sum / sumWeight;
}

#endif
