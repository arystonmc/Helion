#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_image.glsl>

uniform sampler2D BloomSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 bloom = texture(BloomSampler, texCoord).rgb * BloomStrength * ExposureScale;
    fragColor = vec4(helionLinearToSrgb(helionNeutral(bloom)), 1.0);
}
