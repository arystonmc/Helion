#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_sky_color.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_HORIZON_SAMPLES = 32;

void main() {
    vec3 sum = vec3(0.0);
    for (int index = 0; index < HELION_HORIZON_SAMPLES; index++) {
        float azimuth = 2.0 * HELION_PI * (float(index) + 0.5) / float(HELION_HORIZON_SAMPLES);
        sum += helionSkyColor(vec3(cos(azimuth), 0.0, sin(azimuth)));
    }
    fragColor = vec4(sum / float(HELION_HORIZON_SAMPLES), 1.0);
}
