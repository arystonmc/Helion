#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D TileMaxSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_NEIGHBOR_RADIUS = 1;

void main() {
    ivec2 tile = ivec2(gl_FragCoord.xy);
    ivec2 lastTile = textureSize(TileMaxSampler, 0) - 1;
    vec2 longest = vec2(0.0);
    for (int y = -HELION_NEIGHBOR_RADIUS; y <= HELION_NEIGHBOR_RADIUS; y++) {
        for (int x = -HELION_NEIGHBOR_RADIUS; x <= HELION_NEIGHBOR_RADIUS; x++) {
            vec2 velocity = texelFetch(TileMaxSampler, clamp(tile + ivec2(x, y), ivec2(0), lastTile), 0).rg;
            longest = dot(velocity, velocity) > dot(longest, longest) ? velocity : longest;
        }
    }
    fragColor = vec4(longest, 0.0, 1.0);
}
