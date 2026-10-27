#version 460

#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D Radiance;
layout(set = 0, binding = 1) uniform sampler2D AlbedoSampler;
layout(set = 0, binding = 2) uniform sampler2D GeometryLightSampler;
layout(set = 0, binding = 3) uniform sampler2D LightmapSampler;

const float HELION_LIGHTMAP_LAST_TEXEL = 15.0;
const vec4 HELION_NO_RADIANCE = vec4(0.0);

vec3 helionLightmapTexel(ivec2 texel) {
    return texelFetch(LightmapSampler, clamp(texel, ivec2(0), ivec2(HELION_LIGHTMAP_LAST_TEXEL)), 0).rgb;
}

vec3 helionVanillaLight(vec2 levels) {
    vec2 position = clamp(levels * HELION_LIGHTMAP_LAST_TEXEL, vec2(0.0), vec2(HELION_LIGHTMAP_LAST_TEXEL));
    ivec2 low = ivec2(floor(position));
    vec2 blend = position - vec2(low);
    vec3 bottom = mix(helionLightmapTexel(low), helionLightmapTexel(low + ivec2(1, 0)), blend.x);
    vec3 top = mix(helionLightmapTexel(low + ivec2(0, 1)), helionLightmapTexel(low + ivec2(1, 1)), blend.x);
    return mix(bottom, top, blend.y);
}

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(pixel, imageSize(Radiance)))) {
        return;
    }
    vec4 levels = texelFetch(GeometryLightSampler, pixel, 0);
    if (levels.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        imageStore(Radiance, pixel, HELION_NO_RADIANCE);
        return;
    }
    vec3 albedo = texelFetch(AlbedoSampler, pixel, 0).rgb;
    vec3 lit = helionSrgbToLinear(albedo * helionVanillaLight(levels.rg));
    imageStore(Radiance, pixel, vec4(helionInverseNeutral(lit), 1.0));
}
