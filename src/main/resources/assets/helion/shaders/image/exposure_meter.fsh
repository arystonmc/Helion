#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_image.glsl>
#include <helion:helion_exposure.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_CELL_SAMPLES = 4;
const float HELION_SKY_DEPTH = 0.0;
const float HELION_MIN_METERED_LUMINANCE = 1.0e-4;
const float HELION_CENTER_WEIGHT = 2.0;

void main() {
    vec2 meterSize = vec2(HELION_EXPOSURE_METER_WIDTH, HELION_EXPOSURE_METER_HEIGHT);
    vec2 cellSize = vec2(textureSize(SceneColorSampler, 0)) / meterSize;
    vec2 cellOrigin = floor(gl_FragCoord.xy) * cellSize;
    float logSum = 0.0;
    float count = 0.0;
    for (int y = 0; y < HELION_CELL_SAMPLES; y++) {
        for (int x = 0; x < HELION_CELL_SAMPLES; x++) {
            ivec2 pixel = ivec2(cellOrigin + (vec2(x, y) + 0.5) / float(HELION_CELL_SAMPLES) * cellSize);
            if (texelFetch(SceneDepthSampler, pixel, 0).r <= HELION_SKY_DEPTH) {
                continue;
            }
            vec3 color = helionExpandScene(helionSrgbToLinear(texelFetch(SceneColorSampler, pixel, 0).rgb));
            logSum += log2(max(helionLuminance(color), HELION_MIN_METERED_LUMINANCE));
            count += 1.0;
        }
    }
    float centerWeight = 1.0 + HELION_CENTER_WEIGHT * (1.0 - min(length(gl_FragCoord.xy / meterSize - 0.5) * 2.0, 1.0));
    fragColor = vec4(logSum * centerWeight, count * centerWeight, 0.0, 1.0);
}
