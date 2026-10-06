#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <helion:helion_sky_color.glsl>

uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_SKY_DEPTH = 0.0;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    if (depth <= HELION_SKY_DEPTH) {
        discard;
    }
    vec3 position = helionViewPosition(texCoord, depth);
    float amount = total_fog_value(
        fog_spherical_distance(position),
        fog_cylindrical_distance(position),
        AerialEnvironmentalStart,
        AerialEnvironmentalEnd,
        AerialRenderDistanceStart,
        AerialRenderDistanceEnd
    );
    if (amount <= 0.0) {
        discard;
    }
    fragColor = vec4(helionSkyColor(helionViewRay(texCoord)), amount);
}
