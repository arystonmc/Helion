#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_lighting.glsl>
#include <helion:helion_sky_light.glsl>
#include <helion:helion_deferred_light.glsl>

uniform sampler2D AlbedoSampler;
uniform sampler2D GeometryLightSampler;
#ifdef HELION_VOXEL_LIGHT
uniform sampler2D VoxelLightSampler;
uniform sampler2D VoxelConfidenceSampler;
uniform sampler2D VoxelBounceSampler;
#endif

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;
#ifdef HELION_SHADOWS
layout(location = 1) out vec4 indirectShare;
#endif

const vec3 HELION_WHITE_ALBEDO = vec3(1.0);
const float HELION_DISPLAY_LIGHT_LIMIT = 1.0;

#ifdef HELION_VOXEL_LIGHT
const float HELION_VOXEL_VANILLA_FLOOR = 0.3;
const float HELION_BOUNCE_OVER_DIRECT = 0.2;

struct HelionVoxelBlock {
    vec3 unshadowed;
    float transmission;
};

float helionDisplayedLight(vec3 light) {
    vec3 clamped = min(helionAdjustLight(light), vec3(HELION_DISPLAY_LIGHT_LIMIT));
    return helionLuminance(helionSrgbToLinear(helionVanillaBrightness(helionLinearToSrgb(clamped))));
}

vec3 helionCappedBounce(vec3 bounced, vec3 vanilla) {
    float bouncedLuminance = helionLuminance(bounced);
    float cap = helionLuminance(vanilla);
    return bouncedLuminance > cap ? bounced * (cap / bouncedLuminance) : bounced;
}

vec3 helionWithBounce(vec3 direct, vec3 bounced, vec3 vanilla, vec3 floorTerm) {
    float bouncedLuminance = helionLuminance(bounced);
    float deficit = max(helionLuminance(vanilla) - helionLuminance(direct), 0.0);
    vec3 fill = bouncedLuminance > HELION_MIN_SKY_LUMINANCE ? bounced * min(deficit / bouncedLuminance, 1.0) : vec3(0.0);
    return max(direct + fill + bounced * HELION_BOUNCE_OVER_DIRECT, floorTerm);
}

HelionVoxelBlock helionVoxelBlock(ivec2 pixel, vec4 levels) {
    vec3 vanilla = helionInterpolatedBlockTerm(levels.r);
    vec4 traced = texture(VoxelLightSampler, texCoord);
    float trust = texture(VoxelConfidenceSampler, texCoord).r;
    vec4 bounce = texture(VoxelBounceSampler, texCoord);
    vec3 floorTerm = vanilla * HELION_VOXEL_VANILLA_FLOOR * bounce.a;
    vec3 tracedLight = traced.rgb * BlockFactor;
    vec3 bounced = helionCappedBounce(bounce.rgb * BlockFactor, vanilla);
    vec3 unshadowed = mix(vanilla, helionWithBounce(tracedLight, bounced, vanilla, floorTerm), trust);
    vec3 shadowed = mix(vanilla, helionWithBounce(tracedLight * traced.a, bounced, vanilla, floorTerm), trust);
    vec3 base = helionDeferredBase(pixel, levels);
    float lit = helionDisplayedLight(base + unshadowed * BlockLightIntensity);
    float dimmed = helionDisplayedLight(base + shadowed * BlockLightIntensity);
    float transmission = lit > HELION_MIN_SKY_LUMINANCE ? dimmed / lit : 1.0;
    return HelionVoxelBlock(unshadowed, transmission);
}
#endif

#ifdef HELION_SHADOWS
float helionSkyShare(vec4 levels) {
    vec3 sky = helionInterpolatedSkyTerm(levels.g, SkyLightColor) * SkyLightIntensity;
    vec3 total = helionAmbientTerm() + sky + helionInterpolatedBlockTerm(levels.r) * BlockLightIntensity;
    return helionLuminance(sky) / max(helionLuminance(total), HELION_MIN_SKY_LUMINANCE);
}
#endif

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 levels = texelFetch(GeometryLightSampler, pixel, 0);
    if (levels.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        discard;
    }
    #ifdef HELION_VOXEL_LIGHT
    HelionVoxelBlock voxelBlock = helionVoxelBlock(pixel, levels);
    vec4 light = helionDeferredLightWithBlock(pixel, levels, voxelBlock.unshadowed);
    #else
    vec4 light = helionDeferredLight(pixel, levels);
    #endif
    vec4 albedo = texelFetch(AlbedoSampler, pixel, 0);
    float chunkVisibility = levels.b;
    #ifdef HELION_LIGHT_ONLY
    vec3 surface = HELION_WHITE_ALBEDO;
    #else
    vec3 surface = albedo.rgb;
    #endif
    vec3 vanillaColor = surface * helionVanillaBrightness(helionLinearToSrgb(light.rgb));
    vec3 surfaceLight = helionSrgbToLinear(vanillaColor);
    #ifdef HELION_VOXEL_LIGHT
    surfaceLight *= voxelBlock.transmission;
    #endif
    #ifdef HELION_SHADOWS
    float skyShare = helionSkyShare(levels);
    surfaceLight *= helionShadowTransmission(pixel, skyShare);
    indirectShare = vec4(helionIndirectShare(pixel, skyShare));
    #endif
    vec3 radiance = helionInverseNeutral(surfaceLight) * max(light.rgb / light.a, vec3(HELION_DISPLAY_LIGHT_LIMIT));
    vec3 color = helionLinearToSrgb(helionNeutral(radiance));
    color = mix(FogColor.rgb, color, chunkVisibility);
    color = mix(color, FogColor.rgb, albedo.a * FogColor.a);
    fragColor = vec4(color, 1.0);
}
