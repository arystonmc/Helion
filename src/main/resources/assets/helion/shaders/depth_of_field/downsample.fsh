#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_depth_of_field.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D DepthSampler;
uniform sampler2D FocusSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_QUAD_AVERAGE = 0.25;

void main() {
    ivec2 source = ivec2(gl_FragCoord.xy) * 2;
    ivec2 lastPixel = textureSize(SceneColorSampler, 0) - 1;
    vec2 inverseSize = 1.0 / vec2(textureSize(DepthSampler, 0));
    float focus = texelFetch(FocusSampler, ivec2(0), 0).r;
    vec3 color = vec3(0.0);
    float circle = 0.0;
    for (int y = 0; y <= 1; y++) {
        for (int x = 0; x <= 1; x++) {
            ivec2 pixel = min(source + ivec2(x, y), lastPixel);
            color += texelFetch(SceneColorSampler, pixel, 0).rgb;
            float sampleCircle = helionDofCircle(texelFetch(DepthSampler, pixel, 0).r, (vec2(pixel) + 0.5) * inverseSize, focus);
            circle = abs(sampleCircle) > abs(circle) ? sampleCircle : circle;
        }
    }
    fragColor = vec4(color * HELION_QUAD_AVERAGE, circle);
}
