#ifndef HELION_BLOOM_DOWNSAMPLE_GLSL
#define HELION_BLOOM_DOWNSAMPLE_GLSL

#include <helion:helion_color.glsl>

const float HELION_BLOOM_CENTER_WEIGHT = 0.5;
const float HELION_BLOOM_CORNER_WEIGHT = 0.125;
const float HELION_BLOOM_BOX_AVERAGE = 0.25;

float helionBloomBoxWeight(vec3 box, float weight, bool karisAverage) {
    return karisAverage ? weight / (1.0 + helionLuminance(box)) : weight;
}

vec3 helionDownsample13(vec2 uv, vec2 texel, bool karisAverage) {
    vec3 a = helionBloomTap(uv + texel * vec2(-2.0, -2.0));
    vec3 b = helionBloomTap(uv + texel * vec2(0.0, -2.0));
    vec3 c = helionBloomTap(uv + texel * vec2(2.0, -2.0));
    vec3 d = helionBloomTap(uv + texel * vec2(-1.0, -1.0));
    vec3 e = helionBloomTap(uv + texel * vec2(1.0, -1.0));
    vec3 f = helionBloomTap(uv + texel * vec2(-2.0, 0.0));
    vec3 g = helionBloomTap(uv);
    vec3 h = helionBloomTap(uv + texel * vec2(2.0, 0.0));
    vec3 i = helionBloomTap(uv + texel * vec2(-1.0, 1.0));
    vec3 j = helionBloomTap(uv + texel * vec2(1.0, 1.0));
    vec3 k = helionBloomTap(uv + texel * vec2(-2.0, 2.0));
    vec3 l = helionBloomTap(uv + texel * vec2(0.0, 2.0));
    vec3 m = helionBloomTap(uv + texel * vec2(2.0, 2.0));

    vec3 center = (d + e + i + j) * HELION_BLOOM_BOX_AVERAGE;
    vec3 topLeft = (a + b + f + g) * HELION_BLOOM_BOX_AVERAGE;
    vec3 topRight = (b + c + g + h) * HELION_BLOOM_BOX_AVERAGE;
    vec3 bottomLeft = (f + g + k + l) * HELION_BLOOM_BOX_AVERAGE;
    vec3 bottomRight = (g + h + l + m) * HELION_BLOOM_BOX_AVERAGE;

    float centerWeight = helionBloomBoxWeight(center, HELION_BLOOM_CENTER_WEIGHT, karisAverage);
    float topLeftWeight = helionBloomBoxWeight(topLeft, HELION_BLOOM_CORNER_WEIGHT, karisAverage);
    float topRightWeight = helionBloomBoxWeight(topRight, HELION_BLOOM_CORNER_WEIGHT, karisAverage);
    float bottomLeftWeight = helionBloomBoxWeight(bottomLeft, HELION_BLOOM_CORNER_WEIGHT, karisAverage);
    float bottomRightWeight = helionBloomBoxWeight(bottomRight, HELION_BLOOM_CORNER_WEIGHT, karisAverage);

    vec3 sum = center * centerWeight + topLeft * topLeftWeight + topRight * topRightWeight
        + bottomLeft * bottomLeftWeight + bottomRight * bottomRightWeight;
    return sum / (centerWeight + topLeftWeight + topRightWeight + bottomLeftWeight + bottomRightWeight);
}

#endif
