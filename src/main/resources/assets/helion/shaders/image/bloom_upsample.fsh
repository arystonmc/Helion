#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D SourceSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float TENT_CENTER_WEIGHT = 4.0;
const float TENT_EDGE_WEIGHT = 2.0;
const float TENT_TOTAL_WEIGHT = 16.0;

vec3 tap(vec2 texel, float x, float y) {
    return texture(SourceSampler, texCoord + texel * vec2(x, y)).rgb;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(SourceSampler, 0));
    vec3 center = tap(texel, 0.0, 0.0);
    vec3 edges = tap(texel, -1.0, 0.0) + tap(texel, 1.0, 0.0) + tap(texel, 0.0, -1.0) + tap(texel, 0.0, 1.0);
    vec3 corners = tap(texel, -1.0, -1.0) + tap(texel, 1.0, -1.0) + tap(texel, -1.0, 1.0) + tap(texel, 1.0, 1.0);
    vec3 sum = center * TENT_CENTER_WEIGHT + edges * TENT_EDGE_WEIGHT + corners;
    fragColor = vec4(sum / TENT_TOTAL_WEIGHT, 1.0);
}
