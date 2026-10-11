#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_exposure.glsl>

uniform sampler2D MeterSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_NO_WEIGHT = 0.0;

void main() {
    vec2 sum = vec2(0.0);
    for (int y = 0; y < HELION_EXPOSURE_METER_HEIGHT; y++) {
        for (int x = 0; x < HELION_EXPOSURE_METER_WIDTH; x++) {
            sum += texelFetch(MeterSampler, ivec2(x, y), 0).rg;
        }
    }
    float average = sum.y > HELION_NO_WEIGHT ? sum.x / sum.y : 0.0;
    fragColor = vec4(average, sum.y, 0.0, 1.0);
}
