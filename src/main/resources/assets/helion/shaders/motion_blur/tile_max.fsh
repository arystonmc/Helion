#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_motion_blur.glsl>

uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_TILE_SAMPLES = 4;

void main() {
    ivec2 lastPixel = textureSize(DepthSampler, 0) - 1;
    vec2 tileOrigin = floor(gl_FragCoord.xy) * float(HELION_MOTION_TILE_SIZE);
    float step = float(HELION_MOTION_TILE_SIZE) / float(HELION_TILE_SAMPLES);
    vec2 longest = vec2(0.0);
    for (int y = 0; y < HELION_TILE_SAMPLES; y++) {
        for (int x = 0; x < HELION_TILE_SAMPLES; x++) {
            vec2 center = tileOrigin + (vec2(x, y) + 0.5) * step;
            ivec2 pixel = clamp(ivec2(center), ivec2(0), lastPixel);
            longest = helionLonger(longest, helionBlurVelocity(vec2(pixel) + 0.5, texelFetch(DepthSampler, pixel, 0).r));
        }
    }
    fragColor = vec4(longest, 0.0, 1.0);
}
