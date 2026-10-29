#ifndef HELION_RT_LIGHT_GLSL
#define HELION_RT_LIGHT_GLSL

#include <helion:helion_color.glsl>

struct HelionSky {
    vec3 sun;
    vec3 moon;
    vec3 dome;
    vec3 ambient;
    float vanillaShare;
};

struct HelionBlockLight {
    vec3 low;
    vec3 high;
    vec3 color;
    float range;
    float strength;
    bool flickers;
    bool dynamic;
};

const float HELION_LIGHTMAP_LAST_TEXEL = 15.0;
const ivec2 HELION_SUN_TEXEL = ivec2(0, 0);
const ivec2 HELION_MOON_TEXEL = ivec2(1, 0);
const ivec2 HELION_DOME_TEXEL = ivec2(2, 0);
const float HELION_DAYLIGHT_OPEN_SKY = 3.0;
const float HELION_DAY_FULL_SKY = 1.0;
const float HELION_MIN_VANILLA_SKY = 1.0e-3;
const float HELION_LIGHT_CELL_SIZE = 16.0;
const int HELION_GRID_RADIUS = 4;
const int HELION_GRID_SIZE = 2 * HELION_GRID_RADIUS + 1;
const uint HELION_GRID_CELLS = uint(HELION_GRID_SIZE * HELION_GRID_SIZE * HELION_GRID_SIZE);
const uint HELION_RECORD_ENTRIES = 2u;
const uint HELION_NO_LIGHT = 0xFFFFFFFFu;
const float HELION_LIGHT_COLOR_RANGE = 4.0;
const uint HELION_FLICKER_BIT = 1u << 24u;
const uint HELION_DYNAMIC_BIT = 1u << 25u;
const uint HELION_GLOBAL_ENTRIES = 1u;
const float HELION_BLOCK_STRENGTH = 3.5;
const float HELION_BLOCK_NEAR = 1.0;
const float HELION_BLOCK_WRAP = 0.35;
const float HELION_OWN_FACE = 0.05;
const float HELION_REST_SHARE = 0.35;

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

uvec4 helionLightGlobals() {
    return Lights[HELION_GRID_CELLS];
}

uint helionLightRecord(uvec4 header, uvec4 globals, uint index) {
    return index < header.y ? header.x + index : globals.x + index - header.y;
}

HelionBlockLight helionBlockLight(uint record) {
    uint at = HELION_GRID_CELLS + HELION_GLOBAL_ENTRIES + record * HELION_RECORD_ENTRIES;
    uvec4 low = Lights[at];
    uvec4 high = Lights[at + 1u];
    vec2 level = unpackHalf2x16(high.w);
    return HelionBlockLight(
        uintBitsToFloat(low.xyz), uintBitsToFloat(high.xyz), unpackUnorm4x8(low.w).zyx * HELION_LIGHT_COLOR_RANGE,
        level.x, level.y, (low.w & HELION_FLICKER_BIT) != 0u, (low.w & HELION_DYNAMIC_BIT) != 0u
    );
}

vec3 helionLightAim(HelionBlockLight light, vec3 normal) {
    return (light.low + light.high) * 0.5 + normal * (light.high - light.low) * 0.5;
}

vec3 helionLightReach(HelionBlockLight light, vec3 position, vec3 normal) {
    vec3 extent = (light.high - light.low) * 0.5;
    float gap = max(length(position - (light.low + light.high) * 0.5) - min(extent.x, min(extent.y, extent.z)), 0.0);
    if (gap >= light.range) {
        return vec3(0.0);
    }
    float facing = gap < HELION_OWN_FACE ? 1.0 : clamp((dot(normal, normalize(helionLightAim(light, normal) - position)) + HELION_BLOCK_WRAP) / (1.0 + HELION_BLOCK_WRAP), 0.0, 1.0);
    float reach = gap / light.range;
    float window = 1.0 - reach * reach * reach * reach;
    float falloff = window * window / (gap * gap + HELION_BLOCK_NEAR * HELION_BLOCK_NEAR);
    return light.color * (light.strength * HELION_BLOCK_STRENGTH * falloff * facing);
}

uvec4 helionLightCell(vec3 position) {
    ivec3 cell = ivec3(floor(position / HELION_LIGHT_CELL_SIZE)) + HELION_GRID_RADIUS;
    if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, ivec3(HELION_GRID_SIZE)))) {
        return uvec4(0u);
    }
    return Lights[cell.x + HELION_GRID_SIZE * (cell.y + HELION_GRID_SIZE * cell.z)];
}

HelionSky helionSky() {
    vec3 sunLight = texelFetch(SkyLightSampler, HELION_SUN_TEXEL, 0).rgb;
    vec3 moonLight = texelFetch(SkyLightSampler, HELION_MOON_TEXEL, 0).rgb;
    vec3 dome = texelFetch(SkyLightSampler, HELION_DOME_TEXEL, 0).rgb;
    vec3 ambient = helionSrgbToLinear(helionLightmapTexel(ivec2(0))) * uintBitsToFloat(helionLightGlobals().z);
    float openSky = helionLuminance(sunLight) * max(Sun.y, 0.0) + helionLuminance(moonLight) * max(Moon.y, 0.0) + helionLuminance(dome);
    float scale = max(HELION_DAY_FULL_SKY - helionLuminance(ambient), 0.0) / max(openSky, HELION_DAYLIGHT_OPEN_SKY);
    float vanillaSky = helionLuminance(helionSrgbToLinear(helionLightmapTexel(ivec2(0, HELION_LIGHTMAP_LAST_TEXEL))));
    float vanillaShare = clamp((helionLuminance(ambient) + openSky * scale) / max(vanillaSky, HELION_MIN_VANILLA_SKY), 0.0, 1.0);
    return HelionSky(sunLight * scale, moonLight * scale, dome * scale, ambient, vanillaShare);
}

#endif
