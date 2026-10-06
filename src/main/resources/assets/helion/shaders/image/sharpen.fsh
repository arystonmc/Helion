#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_image.glsl>

uniform sampler2D SceneColorSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_SHARPEN_SOFT_PEAK = 8.0;
const float HELION_SHARPEN_HARD_PEAK = 5.0;
const float HELION_SHARPEN_EPSILON = 1.0e-5;
const float HELION_SHARPEN_NEIGHBORS = 4.0;

vec3 helionSceneTap(ivec2 pixel, ivec2 offset, ivec2 limit) {
    return texelFetch(SceneColorSampler, clamp(pixel + offset, ivec2(0), limit), 0).rgb;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 limit = textureSize(SceneColorSampler, 0) - ivec2(1);
    vec4 centerSample = texelFetch(SceneColorSampler, pixel, 0);
    vec3 center = centerSample.rgb;
    vec3 north = helionSceneTap(pixel, ivec2(0, 1), limit);
    vec3 south = helionSceneTap(pixel, ivec2(0, -1), limit);
    vec3 east = helionSceneTap(pixel, ivec2(1, 0), limit);
    vec3 west = helionSceneTap(pixel, ivec2(-1, 0), limit);
    vec3 darkest = min(center, min(min(north, south), min(east, west)));
    vec3 brightest = max(center, max(max(north, south), max(east, west)));
    vec3 headroom = min(darkest, vec3(1.0) - brightest);
    vec3 amplitude = sqrt(clamp(headroom / max(brightest, vec3(HELION_SHARPEN_EPSILON)), 0.0, 1.0));
    vec3 weight = -amplitude / mix(HELION_SHARPEN_SOFT_PEAK, HELION_SHARPEN_HARD_PEAK, SharpenStrength);
    vec3 neighbors = north + south + east + west;
    vec3 sharpened = (center + neighbors * weight) / (vec3(1.0) + HELION_SHARPEN_NEIGHBORS * weight);
    fragColor = vec4(clamp(sharpened, 0.0, 1.0), centerSample.a);
}
