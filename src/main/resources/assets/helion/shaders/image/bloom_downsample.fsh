#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D SourceSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

vec3 helionBloomTap(vec2 uv) {
    return texture(SourceSampler, uv).rgb;
}

#include <helion:helion_bloom_downsample.glsl>

void main() {
    vec2 texel = 1.0 / vec2(textureSize(SourceSampler, 0));
    fragColor = vec4(helionDownsample13(texCoord, texel, false), 1.0);
}
