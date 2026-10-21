#ifndef HELION_CLOUD_NOISE_GLSL
#define HELION_CLOUD_NOISE_GLSL

// The cloud noise is a tiling 64 x 64 x 64 volume packed into a 2D atlas: 64 slices of 64 x 64 texels in an 8 x 8 grid,
// each slice with a one texel border copied from the opposite edge, so bilinear filtering wraps without seams.
const float HELION_CLOUD_NOISE_SIZE = 64.0;
const float HELION_CLOUD_NOISE_TILE = 66.0;
const float HELION_CLOUD_NOISE_GRID = 8.0;
const float HELION_CLOUD_NOISE_ATLAS = HELION_CLOUD_NOISE_TILE * HELION_CLOUD_NOISE_GRID;

vec2 helionCloudNoiseSliceOrigin(float slice) {
    return vec2(mod(slice, HELION_CLOUD_NOISE_GRID), floor(slice / HELION_CLOUD_NOISE_GRID)) * HELION_CLOUD_NOISE_TILE;
}

vec4 helionCloudNoiseSlice(sampler2D atlas, vec2 coordinates, float slice) {
    vec2 texel = helionCloudNoiseSliceOrigin(slice) + fract(coordinates) * HELION_CLOUD_NOISE_SIZE + 1.0;
    return textureLod(atlas, texel / HELION_CLOUD_NOISE_ATLAS, 0.0);
}

vec4 helionCloudNoise(sampler2D atlas, vec3 coordinates) {
    float depth = fract(coordinates.z) * HELION_CLOUD_NOISE_SIZE - 0.5;
    float lower = floor(depth);
    float blend = depth - lower;
    float first = mod(lower + HELION_CLOUD_NOISE_SIZE, HELION_CLOUD_NOISE_SIZE);
    float second = mod(first + 1.0, HELION_CLOUD_NOISE_SIZE);
    return mix(helionCloudNoiseSlice(atlas, coordinates.xy, first), helionCloudNoiseSlice(atlas, coordinates.xy, second), blend);
}

float helionCloudRemap(float value, float low, float high, float newLow, float newHigh) {
    return newLow + (value - low) / max(high - low, 1.0e-5) * (newHigh - newLow);
}

#endif
