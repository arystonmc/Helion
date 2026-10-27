#version 460
#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require

#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_hash.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D Radiance;
layout(set = 0, binding = 1) uniform accelerationStructureEXT Scene;
layout(set = 0, binding = 2) uniform sampler2D AlbedoSampler;
layout(set = 0, binding = 3) uniform sampler2D GeometryLightSampler;
layout(set = 0, binding = 4) uniform sampler2D LightmapSampler;
layout(set = 0, binding = 5) uniform sampler2D SceneDepthSampler;
layout(set = 0, binding = 6) uniform sampler2D GeometryNormalSampler;
layout(set = 0, binding = 7) uniform sampler2D AtlasSampler;
layout(set = 0, binding = 8, std430) readonly buffer HelionSurfaceTable {
    uvec4 Sections[];
};
layout(set = 0, binding = 9) uniform sampler2D SkyLightSampler;
layout(set = 0, binding = 10, std430) readonly buffer HelionLightGrid {
    uvec4 Lights[];
};

layout(push_constant) uniform HelionPathTrace {
    mat4 InverseViewProjection;
    vec4 CameraToAnchor;
    vec4 Sun;
    vec4 Moon;
    vec4 Frame;
};

#include <helion:helion_rt_surface.glsl>

struct HelionBlockLight {
    vec3 low;
    vec3 high;
    vec3 color;
    float range;
    float strength;
    bool flickers;
};

const float HELION_LIGHTMAP_LAST_TEXEL = 15.0;
const ivec2 HELION_SUN_TEXEL = ivec2(0, 0);
const ivec2 HELION_MOON_TEXEL = ivec2(1, 0);
const ivec2 HELION_DOME_TEXEL = ivec2(2, 0);
const vec4 HELION_NO_RADIANCE = vec4(0.0);
const float HELION_SET = 0.5;
const float HELION_SKY_DEPTH = 0.0;
const float HELION_SECTION_SIZE = 16.0;
const float HELION_SCENE_FADE = 8.0;
const float HELION_ORIGIN_OFFSET = 0.02;
const float HELION_ORIGIN_DISTANCE_SHARE = 0.002;
const float HELION_RAY_START = 1.0e-3;
const uint HELION_SHADOW_INSTANCES = 0x03u;
const float HELION_CELESTIAL_REACH = 96.0;
const float HELION_SUN_RADIUS = 0.0186;
const float HELION_MOON_RADIUS = 0.0186;
const float HELION_SKY_GAMMA = 2.2;
const float HELION_SKY_GATE_LEVELS = 15.0;
const float HELION_DOME_GROUND = 0.45;
const float HELION_MIN_OPEN_SKY = 1.0e-4;
const vec3 HELION_VANILLA_SHADE = vec3(0.6, 1.0, 0.8);
const float HELION_VANILLA_SHADE_DOWN = 0.5;
const int HELION_GRID_RADIUS = 4;
const int HELION_GRID_SIZE = 2 * HELION_GRID_RADIUS + 1;
const uint HELION_GRID_CELLS = uint(HELION_GRID_SIZE * HELION_GRID_SIZE * HELION_GRID_SIZE);
const uint HELION_RECORD_ENTRIES = 2u;
const uint HELION_NO_LIGHT = 0xFFFFFFFFu;
const float HELION_LIGHT_COLOR_RANGE = 2.0;
const uint HELION_FLICKER_BIT = 1u << 24u;
const float HELION_BLOCK_STRENGTH = 2.2;
const float HELION_BLOCK_NEAR = 1.0;
const float HELION_OWN_FACE = 0.05;
const float HELION_ENTRY_MARGIN = 0.02;
const float HELION_MIN_DIRECTION = 1.0e-6;
const float HELION_REST_SHARE = 0.35;
const float HELION_BLOCK_FILL = 0.3;
const float HELION_BLOCK_DAY_FADE = 0.6;
const float HELION_FLICKER_DEPTH = 0.15;
const float HELION_FLICKER_SLOW = 7.0;
const float HELION_FLICKER_FAST = 13.0;
const float HELION_FLICKER_SPREAD = 1.7;
const float HELION_TAU = 6.28318530718;

uint helionState;

float helionRandom() {
    helionState = helionPcg(helionState);
    return float(helionState) / HELION_HASH_RANGE;
}

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

vec3 helionUnshaded(vec3 albedo, vec3 normal) {
    if (helionIsCrossPlane(normal)) {
        return albedo;
    }
    vec3 share = normal * normal;
    float shade = share.x * HELION_VANILLA_SHADE.x + share.z * HELION_VANILLA_SHADE.z + share.y * (normal.y > 0.0 ? HELION_VANILLA_SHADE.y : HELION_VANILLA_SHADE_DOWN);
    return min(albedo / shade, vec3(1.0));
}

float helionVisible(vec3 origin, vec3 direction, float reach) {
    rayQueryEXT shadow;
    rayQueryInitializeEXT(shadow, Scene, gl_RayFlagsTerminateOnFirstHitEXT, HELION_SHADOW_INSTANCES, origin, HELION_RAY_START, direction, reach);
    helionTraceCutout(shadow);
    return rayQueryGetIntersectionTypeEXT(shadow, true) == gl_RayQueryCommittedIntersectionNoneEXT ? 1.0 : 0.0;
}

vec3 helionConeDirection(vec3 axis, float radius) {
    float cosTheta = 1.0 - helionRandom() * (1.0 - cos(radius));
    float sinTheta = sqrt(max(1.0 - cosTheta * cosTheta, 0.0));
    float phi = HELION_TAU * helionRandom();
    vec3 helper = abs(axis.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(helper, axis));
    vec3 bitangent = cross(axis, tangent);
    return normalize(axis * cosTheta + (tangent * cos(phi) + bitangent * sin(phi)) * sinTheta);
}

float helionCelestial(vec3 origin, vec3 normal, vec4 light, float radius) {
    if (light.w < HELION_SET) {
        return 0.0;
    }
    vec3 direction = helionConeDirection(light.xyz, radius);
    float facing = dot(normal, direction);
    return facing > 0.0 ? facing * helionVisible(origin, direction, HELION_CELESTIAL_REACH) : 0.0;
}

HelionBlockLight helionBlockLight(uint record) {
    uvec4 low = Lights[HELION_GRID_CELLS + record * HELION_RECORD_ENTRIES];
    uvec4 high = Lights[HELION_GRID_CELLS + record * HELION_RECORD_ENTRIES + 1u];
    vec2 level = unpackHalf2x16(high.w);
    return HelionBlockLight(
        uintBitsToFloat(low.xyz), uintBitsToFloat(high.xyz), unpackUnorm4x8(low.w).zyx * HELION_LIGHT_COLOR_RANGE,
        level.x, level.y, (low.w & HELION_FLICKER_BIT) != 0u
    );
}

vec3 helionLightAim(HelionBlockLight light, vec3 normal) {
    return (light.low + light.high) * 0.5 + normal * (light.high - light.low) * 0.5;
}

float helionFlicker(HelionBlockLight light) {
    if (!light.flickers) {
        return 1.0;
    }
    float phase = float(helionPcg(floatBitsToUint(light.low.x) ^ helionPcg(floatBitsToUint(light.low.y) ^ helionPcg(floatBitsToUint(light.low.z))))) / HELION_HASH_RANGE * HELION_TAU;
    float slow = 0.5 + 0.5 * sin(Frame.x * HELION_FLICKER_SLOW + phase);
    float fast = 0.5 + 0.5 * sin(Frame.x * HELION_FLICKER_FAST + phase * HELION_FLICKER_SPREAD);
    return 1.0 - HELION_FLICKER_DEPTH * slow * fast;
}

vec3 helionLightShare(HelionBlockLight light, vec3 position, vec3 normal) {
    float gap = length(clamp(position, light.low, light.high) - position);
    if (gap >= light.range) {
        return vec3(0.0);
    }
    float facing = gap < HELION_OWN_FACE ? 1.0 : clamp(dot(normal, normalize(helionLightAim(light, normal) - position)), 0.0, 1.0);
    float reach = gap / light.range;
    float window = 1.0 - reach * reach * reach * reach;
    float falloff = window * window / max(gap * gap, HELION_BLOCK_NEAR * HELION_BLOCK_NEAR);
    return light.color * (light.strength * HELION_BLOCK_STRENGTH * falloff * facing * helionFlicker(light));
}

vec3 helionSafeDirection(vec3 direction) {
    return mix(direction, sign(direction + HELION_MIN_DIRECTION) * HELION_MIN_DIRECTION, lessThan(abs(direction), vec3(HELION_MIN_DIRECTION)));
}

float helionLightVisible(HelionBlockLight light, vec3 origin, vec3 normal) {
    vec3 toward = helionLightAim(light, normal) - origin;
    float span = length(toward);
    if (span < HELION_OWN_FACE) {
        return 1.0;
    }
    vec3 direction = toward / span;
    vec3 inverted = 1.0 / helionSafeDirection(direction);
    vec3 near = (light.low - origin) * inverted;
    vec3 far = (light.high - origin) * inverted;
    vec3 entries = min(near, far);
    float entry = clamp(max(max(entries.x, entries.y), entries.z), 0.0, span) - HELION_ENTRY_MARGIN;
    return entry <= HELION_RAY_START ? 1.0 : helionVisible(origin, direction, entry);
}

vec3 helionBlockLighting(vec3 position, vec3 origin, vec3 normal) {
    ivec3 cell = ivec3(floor(position / HELION_SECTION_SIZE)) + HELION_GRID_RADIUS;
    if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, ivec3(HELION_GRID_SIZE)))) {
        return vec3(0.0);
    }
    uvec4 header = Lights[cell.x + HELION_GRID_SIZE * (cell.y + HELION_GRID_SIZE * cell.z)];
    uint brightest = HELION_NO_LIGHT;
    float brightestWeight = 0.0;
    vec3 brightestShare = vec3(0.0);
    uint chosen = HELION_NO_LIGHT;
    float chosenWeight = 0.0;
    vec3 chosenShare = vec3(0.0);
    float restWeight = 0.0;
    for (uint index = 0u; index < header.y; index++) {
        uint record = header.x + index;
        vec3 share = helionLightShare(helionBlockLight(record), position, normal);
        float weight = helionLuminance(share);
        if (weight <= 0.0) {
            continue;
        }
        uint candidate = record;
        float candidateWeight = weight;
        vec3 candidateShare = share;
        if (weight > brightestWeight) {
            candidate = brightest;
            candidateWeight = brightestWeight;
            candidateShare = brightestShare;
            brightest = record;
            brightestWeight = weight;
            brightestShare = share;
        }
        if (candidateWeight > 0.0) {
            restWeight += candidateWeight;
            if (helionRandom() * restWeight < candidateWeight) {
                chosen = candidate;
                chosenWeight = candidateWeight;
                chosenShare = candidateShare;
            }
        }
    }
    if (brightest == HELION_NO_LIGHT) {
        return vec3(0.0);
    }
    vec3 lit = brightestShare * helionLightVisible(helionBlockLight(brightest), origin, normal);
    if (chosen == HELION_NO_LIGHT) {
        return lit;
    }
    vec3 rest = chosenShare * (restWeight / chosenWeight) * helionLightVisible(helionBlockLight(chosen), origin, normal);
    return lit + rest * (HELION_REST_SHARE / (1.0 + helionLuminance(rest)));
}

float helionSceneShare(vec3 origin) {
    float reach = CameraToAnchor.w;
    vec3 edges = min(origin + reach, reach + HELION_SECTION_SIZE - origin);
    return clamp(min(min(edges.x, edges.y), edges.z) / HELION_SCENE_FADE, 0.0, 1.0);
}

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(Radiance);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    vec4 levels = texelFetch(GeometryLightSampler, pixel, 0);
    float depth = texelFetch(SceneDepthSampler, pixel, 0).r;
    if (levels.a < HELION_GEOMETRY_PRESENT_THRESHOLD || depth <= HELION_SKY_DEPTH) {
        imageStore(Radiance, pixel, HELION_NO_RADIANCE);
        return;
    }
    vec3 albedo = texelFetch(AlbedoSampler, pixel, 0).rgb;
    if (helionIsGeometryMaterial(levels.a, HELION_GEOMETRY_EMISSIVE) || helionIsGeometryMaterial(levels.a, HELION_GEOMETRY_LAVA)) {
        imageStore(Radiance, pixel, vec4(helionSrgbToLinear(albedo), 1.0));
        return;
    }
    vec3 vanilla = helionSrgbToLinear(albedo * helionVanillaLight(levels.rg));
    vec2 screen = (vec2(pixel) + 0.5) / vec2(size);
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 view = world.xyz / world.w;
    vec3 normal = normalize(texelFetch(GeometryNormalSampler, pixel, 0).rgb * 2.0 - 1.0);
    vec3 position = view + CameraToAnchor.xyz;
    vec3 origin = position + normal * (HELION_ORIGIN_OFFSET + length(view) * HELION_ORIGIN_DISTANCE_SHARE);
    float traced = helionSceneShare(origin);
    if (traced <= 0.0) {
        imageStore(Radiance, pixel, vec4(vanilla, 1.0));
        return;
    }
    helionState = helionPcg(uint(pixel.x) ^ helionPcg(uint(pixel.y) ^ helionPcg(floatBitsToUint(Frame.y))));
    vec3 sunLight = texelFetch(SkyLightSampler, HELION_SUN_TEXEL, 0).rgb;
    vec3 moonLight = texelFetch(SkyLightSampler, HELION_MOON_TEXEL, 0).rgb;
    vec3 dome = texelFetch(SkyLightSampler, HELION_DOME_TEXEL, 0).rgb;
    vec3 ambient = helionSrgbToLinear(helionLightmapTexel(ivec2(0)));
    vec3 fullSky = helionSrgbToLinear(helionLightmapTexel(ivec2(0, HELION_LIGHTMAP_LAST_TEXEL)));
    float openSky = helionLuminance(sunLight) * max(Sun.y, 0.0) + helionLuminance(moonLight) * max(Moon.y, 0.0) + helionLuminance(dome);
    float scale = max(helionLuminance(fullSky) - helionLuminance(ambient), 0.0) / max(openSky, HELION_MIN_OPEN_SKY);
    float gate = clamp(levels.g * HELION_SKY_GATE_LEVELS, 0.0, 1.0);
    vec3 sky = dome * pow(levels.g, HELION_SKY_GAMMA) * mix(HELION_DOME_GROUND, 1.0, normal.y * 0.5 + 0.5);
    if (gate > 0.0) {
        sky += (sunLight * helionCelestial(origin, normal, Sun, HELION_SUN_RADIUS) + moonLight * helionCelestial(origin, normal, Moon, HELION_MOON_RADIUS)) * gate;
    }
    sky *= scale;
    vec3 fill = max(helionSrgbToLinear(helionVanillaLight(vec2(levels.r, 0.0))) - ambient, vec3(0.0)) * HELION_BLOCK_FILL;
    vec3 block = (helionBlockLighting(position, origin, normal) + fill) * mix(1.0, 1.0 - clamp(helionLuminance(sky), 0.0, 1.0), HELION_BLOCK_DAY_FADE);
    vec3 lit = helionSrgbToLinear(helionUnshaded(albedo, normal)) * (ambient + sky + block);
    imageStore(Radiance, pixel, vec4(mix(vanilla, lit, traced), 1.0));
}
