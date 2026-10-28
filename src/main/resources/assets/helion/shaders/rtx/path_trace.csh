#version 460
#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require

#include <helion:helion_path_tracing.glsl>
#include <helion:helion_hash.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D Irradiance;
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
layout(set = 0, binding = 11) uniform sampler2D MaterialSampler;

layout(push_constant) uniform HelionPathTrace {
    mat4 InverseViewProjection;
    vec4 Camera;
    vec4 Sun;
    vec4 Moon;
    ivec4 Frame;
};

struct HelionCacheEntry {
    uvec4 state;
    ivec4 cell;
    vec4 light;
    vec4 sampleAt;
};

layout(set = 0, binding = 12, std430) coherent buffer HelionLightCache {
    HelionCacheEntry Entries[];
};
layout(set = 0, binding = 13, std430) coherent buffer HelionCacheQueue {
    uint QueuedCount;
    uint Queued[];
};
layout(set = 0, binding = 14, std430) coherent buffer HelionCacheNextQueue {
    uint NextCount;
    uint Next[];
};

#include <helion:helion_rt_surface.glsl>

struct HelionSky {
    vec3 sun;
    vec3 moon;
    vec3 dome;
    vec3 ambient;
};

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
const int HELION_GRID_RADIUS = 4;
const int HELION_GRID_SIZE = 2 * HELION_GRID_RADIUS + 1;
const float HELION_SCENE_REACH = float(HELION_GRID_RADIUS) * HELION_SECTION_SIZE;
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
const float HELION_BLOCK_DAY_FADE = 0.6;
const float HELION_FLICKER_DEPTH = 0.15;
const float HELION_FLICKER_SLOW = 7.0;
const float HELION_FLICKER_FAST = 13.0;
const float HELION_FLICKER_SPREAD = 1.7;
const float HELION_TAU = 6.28318530718;
const uint HELION_SECTION_INSTANCES = 0x01u;
const int HELION_BOUNCES = 3;
const int HELION_ROULETTE_START = 2;
const float HELION_ROULETTE_MIN = 0.1;
const float HELION_BOUNCE_REACH = 128.0;
const float HELION_GROUND_SHARE = 0.3;
const float HELION_HIT_OFFSET = 0.01;
const float HELION_PLANT_START = 0.3;
const float HELION_INDIRECT_LIMIT = 4.0;

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
    float slow = 0.5 + 0.5 * sin(Camera.w * HELION_FLICKER_SLOW + phase);
    float fast = 0.5 + 0.5 * sin(Camera.w * HELION_FLICKER_FAST + phase * HELION_FLICKER_SPREAD);
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

uvec4 helionLightCell(vec3 position) {
    ivec3 cell = ivec3(floor(position / HELION_SECTION_SIZE)) + HELION_GRID_RADIUS;
    if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, ivec3(HELION_GRID_SIZE)))) {
        return uvec4(0u);
    }
    return Lights[cell.x + HELION_GRID_SIZE * (cell.y + HELION_GRID_SIZE * cell.z)];
}

vec3 helionBlockLighting(vec3 position, vec3 origin, vec3 normal) {
    uvec4 header = helionLightCell(position);
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

vec3 helionBounceBlockLight(vec3 position, vec3 origin, vec3 normal) {
    uvec4 header = helionLightCell(position);
    uint chosen = HELION_NO_LIGHT;
    float chosenWeight = 0.0;
    vec3 chosenShare = vec3(0.0);
    float totalWeight = 0.0;
    float brightestWeight = 0.0;
    for (uint index = 0u; index < header.y; index++) {
        uint record = header.x + index;
        vec3 share = helionLightShare(helionBlockLight(record), position, normal);
        float weight = helionLuminance(share);
        if (weight <= 0.0) {
            continue;
        }
        totalWeight += weight;
        brightestWeight = max(brightestWeight, weight);
        if (helionRandom() * totalWeight < weight) {
            chosen = record;
            chosenWeight = weight;
            chosenShare = share;
        }
    }
    if (chosen == HELION_NO_LIGHT) {
        return vec3(0.0);
    }
    float rest = totalWeight - brightestWeight;
    float combined = brightestWeight + rest * (HELION_REST_SHARE / (1.0 + rest));
    return chosenShare * (combined / chosenWeight) * helionLightVisible(helionBlockLight(chosen), origin, normal);
}

vec3 helionCelestialLight(vec3 origin, vec3 normal, HelionSky sky) {
    return sky.sun * helionCelestial(origin, normal, Sun, HELION_SUN_RADIUS) + sky.moon * helionCelestial(origin, normal, Moon, HELION_MOON_RADIUS);
}

vec3 helionCosineDirection(vec3 normal) {
    float first = helionRandom();
    float angle = HELION_TAU * helionRandom();
    float radius = sqrt(first);
    vec3 helper = abs(normal.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(helper, normal));
    vec3 bitangent = cross(normal, tangent);
    return normalize(tangent * (radius * cos(angle)) + bitangent * (radius * sin(angle)) + normal * sqrt(max(1.0 - first, 0.0)));
}

float helionRayStart(vec3 normal) {
    return helionIsCrossPlane(normal) ? HELION_PLANT_START : HELION_RAY_START;
}

vec3 helionSkyTail(vec3 normal, float skyLevel, HelionSky sky) {
    return sky.ambient + sky.dome * pow(skyLevel, HELION_SKY_GAMMA) * mix(HELION_DOME_GROUND, 1.0, normal.y * 0.5 + 0.5);
}

vec3 helionIndirect(vec3 origin, vec3 normal, float start, float gate, HelionSky sky) {
    vec3 radiance = vec3(0.0);
    vec3 throughput = vec3(1.0);
    for (int bounce = 0; bounce < HELION_BOUNCES; bounce++) {
        vec3 direction = helionCosineDirection(normal);
        rayQueryEXT query;
        rayQueryInitializeEXT(query, Scene, gl_RayFlagsNoneEXT, HELION_SECTION_INSTANCES, origin, start, direction, HELION_BOUNCE_REACH);
        helionTraceCutout(query);
        if (rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
            radiance += throughput * sky.dome * (gate * (direction.y > 0.0 ? 1.0 : HELION_GROUND_SHARE));
            break;
        }
        HelionSurfacePoint point = helionCommittedPoint(query);
        if (helionIsEmissiveCell(helionMaterialCell(textureLod(MaterialSampler, point.uv, 0.0).r))) {
            break;
        }
        vec3 hit = origin + direction * rayQueryGetIntersectionTEXT(query, true);
        normal = dot(point.normal, direction) < 0.0 ? point.normal : -point.normal;
        origin = hit + normal * HELION_HIT_OFFSET;
        start = helionRayStart(normal);
        float skyLevel = clamp(point.light.y / HELION_MAX_LIGHT_COORDINATE, 0.0, 1.0);
        gate = clamp(skyLevel * HELION_SKY_GATE_LEVELS, 0.0, 1.0);
        throughput *= helionSurfaceAlbedo(textureLod(AtlasSampler, point.uv, 0.0).rgb * point.tint.rgb, normal);
        vec3 light = helionBounceBlockLight(hit, origin, normal) + sky.ambient;
        if (gate > 0.0) {
            light += helionCelestialLight(origin, normal, sky) * gate;
        }
        bool last = bounce == HELION_BOUNCES - 1
            || (bounce + 1 >= HELION_ROULETTE_START && helionRandom() >= clamp(max(max(throughput.r, throughput.g), throughput.b), HELION_ROULETTE_MIN, 1.0));
        if (last) {
            radiance += throughput * (light + helionSkyTail(normal, skyLevel, sky));
            break;
        }
        radiance += throughput * light;
    }
    float luminance = helionLuminance(radiance);
    return luminance > HELION_INDIRECT_LIMIT ? radiance * (HELION_INDIRECT_LIMIT / luminance) : radiance;
}

float helionSceneShare(vec3 origin) {
    float reach = HELION_SCENE_REACH;
    vec3 edges = min(origin + reach, reach + HELION_SECTION_SIZE - origin);
    return clamp(min(min(edges.x, edges.y), edges.z) / HELION_SCENE_FADE, 0.0, 1.0);
}

HelionSky helionSky() {
    vec3 sunLight = texelFetch(SkyLightSampler, HELION_SUN_TEXEL, 0).rgb;
    vec3 moonLight = texelFetch(SkyLightSampler, HELION_MOON_TEXEL, 0).rgb;
    vec3 dome = texelFetch(SkyLightSampler, HELION_DOME_TEXEL, 0).rgb;
    vec3 ambient = helionSrgbToLinear(helionLightmapTexel(ivec2(0)));
    vec3 fullSky = helionSrgbToLinear(helionLightmapTexel(ivec2(0, HELION_LIGHTMAP_LAST_TEXEL)));
    float openSky = helionLuminance(sunLight) * max(Sun.y, 0.0) + helionLuminance(moonLight) * max(Moon.y, 0.0) + helionLuminance(dome);
    float scale = max(helionLuminance(fullSky) - helionLuminance(ambient), 0.0) / max(openSky, HELION_MIN_OPEN_SKY);
    return HelionSky(sunLight * scale, moonLight * scale, dome * scale, ambient);
}

const int HELION_CACHE_PROBES = 8;
const uint HELION_CACHE_EVICT_AGE = 600u;
const float HELION_CACHE_NEAR = 12.0;
const int HELION_CACHE_TOP_LEVEL = 6;
const int HELION_CACHE_FINE_LEVELS = 2;
const float HELION_CACHE_CELLS_PER_BLOCK = 4.0;
const int HELION_CACHE_SECTION_CELLS = 64;
const float HELION_CACHE_FINE_PLANE = 16.0;
const float HELION_CACHE_COARSE_PLANE = 1.0;
const float HELION_CACHE_AXIS = 0.99;
const uint HELION_CACHE_READY = 8u;
const uint HELION_CACHE_MAX_COUNT = 64u;
const int HELION_CACHE_YOUNG_PATHS = 4;
const uint HELION_CACHE_CHECK_SALT = 0x9E3779B9u;
const uint HELION_CACHE_LEVEL_SHIFT = 3u;
const uint HELION_CACHE_FACE_MASK = 7u;
const int HELION_NO_SLOT = -1;
const float HELION_CACHE_MIN_WEIGHT = 1.0e-6;

struct HelionCacheCell {
    uint face;
    int level;
    int axis;
    int column;
    int row;
    float plane;
    float cellsPerBlock;
};

HelionCacheCell helionCacheLayout(uint face, int level) {
    HelionCacheCell cell;
    cell.face = face;
    cell.level = level;
    cell.axis = int(face >> 1u);
    cell.column = (cell.axis + 1) % 3;
    cell.row = (cell.axis + 2) % 3;
    cell.plane = level < HELION_CACHE_FINE_LEVELS ? HELION_CACHE_FINE_PLANE : HELION_CACHE_COARSE_PLANE;
    cell.cellsPerBlock = HELION_CACHE_CELLS_PER_BLOCK / float(1 << level);
    return cell;
}

int helionCachePlaneOrigin(HelionCacheCell cell) {
    return Frame[cell.axis] * int(HELION_SECTION_SIZE * cell.plane);
}

int helionCacheOrigin(HelionCacheCell cell, int axis) {
    return Frame[axis] * (HELION_CACHE_SECTION_CELLS >> cell.level);
}

uint helionCacheHash(HelionCacheCell cell, int plane, ivec2 coordinates) {
    return helionPcg((cell.face | uint(cell.level) << HELION_CACHE_LEVEL_SHIFT) ^ helionPcg(uint(plane) ^ helionPcg(uint(coordinates.x) ^ helionPcg(uint(coordinates.y)))));
}

uint helionCacheCheck(uint hash) {
    return helionPcg(hash ^ HELION_CACHE_CHECK_SALT) | 1u;
}

int helionCacheFind(uint hash) {
    uint check = helionCacheCheck(hash);
    uint capacity = uint(Entries.length());
    for (int probe = 0; probe < HELION_CACHE_PROBES; probe++) {
        uint slot = (hash + uint(probe)) % capacity;
        uint found = Entries[slot].state.x;
        if (found == check) {
            return int(slot);
        }
        if (found == 0u) {
            return HELION_NO_SLOT;
        }
    }
    return HELION_NO_SLOT;
}

int helionCacheClaim(uint hash) {
    uint check = helionCacheCheck(hash);
    uint capacity = uint(Entries.length());
    for (int probe = 0; probe < HELION_CACHE_PROBES; probe++) {
        uint slot = (hash + uint(probe)) % capacity;
        uint seen = Entries[slot].state.x;
        if (seen == check) {
            return int(slot);
        }
        if (seen != 0u) {
            continue;
        }
        uint found = atomicCompSwap(Entries[slot].state.x, 0u, check);
        if (found == 0u || found == check) {
            return int(slot);
        }
    }
    uint frame = uint(Frame.w);
    for (int probe = 0; probe < HELION_CACHE_PROBES; probe++) {
        uint slot = (hash + uint(probe)) % capacity;
        uint old = Entries[slot].state.x;
        if (frame - Entries[slot].state.y > HELION_CACHE_EVICT_AGE && atomicCompSwap(Entries[slot].state.x, old, check) == old) {
            Entries[slot].state.z = 0u;
            return int(slot);
        }
    }
    return HELION_NO_SLOT;
}

void helionCacheRequest(int slot, HelionCacheCell cell, int plane, ivec2 coordinates, vec2 inside, float gate) {
    if (Entries[slot].state.y == uint(Frame.w) || atomicExchange(Entries[slot].state.y, uint(Frame.w)) == uint(Frame.w)) {
        return;
    }
    Entries[slot].state.w = cell.face | uint(cell.level) << HELION_CACHE_LEVEL_SHIFT;
    Entries[slot].cell = ivec4(plane, coordinates, 0);
    Entries[slot].sampleAt = vec4(inside, 0.0, 0.0);
    Entries[slot].light.w = gate;
    uint index = atomicAdd(QueuedCount, 1u);
    if (index < uint(Queued.length())) {
        Queued[index] = uint(slot);
    }
}

bool helionCachedIndirect(vec3 position, vec3 normal, float distance, float gate, out vec3 indirect) {
    indirect = vec3(0.0);
    vec3 magnitude = abs(normal);
    int axis = magnitude.x > magnitude.y ? (magnitude.x > magnitude.z ? 0 : 2) : (magnitude.y > magnitude.z ? 1 : 2);
    if (magnitude[axis] < HELION_CACHE_AXIS) {
        return false;
    }
    int level = clamp(int(floor(log2(max(distance / HELION_CACHE_NEAR, 1.0)))), 0, HELION_CACHE_TOP_LEVEL);
    HelionCacheCell cell = helionCacheLayout(uint(axis * 2) + (normal[axis] < 0.0 ? 1u : 0u), level);
    int plane = helionCachePlaneOrigin(cell) + int(round(position[axis] * cell.plane));
    vec2 local = vec2(position[cell.column], position[cell.row]) * cell.cellsPerBlock;
    ivec2 origin = ivec2(helionCacheOrigin(cell, cell.column), helionCacheOrigin(cell, cell.row));
    ivec2 own = ivec2(floor(local));
    int slot = helionCacheClaim(helionCacheHash(cell, plane, origin + own));
    if (slot == HELION_NO_SLOT) {
        return false;
    }
    helionCacheRequest(slot, cell, plane, origin + own, local - vec2(own), gate);
    if (Entries[slot].state.z < HELION_CACHE_READY) {
        return false;
    }
    vec2 corner = local - 0.5;
    ivec2 first = ivec2(floor(corner));
    vec2 blend = corner - vec2(first);
    float weights = 0.0;
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            int found = helionCacheFind(helionCacheHash(cell, plane, origin + first + ivec2(x, y)));
            if (found == HELION_NO_SLOT || Entries[found].state.z == 0u) {
                continue;
            }
            float weight = (x == 0 ? 1.0 - blend.x : blend.x) * (y == 0 ? 1.0 - blend.y : blend.y);
            indirect += Entries[found].light.rgb * weight;
            weights += weight;
        }
    }
    indirect /= max(weights, HELION_CACHE_MIN_WEIGHT);
    return weights > 0.0;
}

#ifdef HELION_LIGHT_CACHE_UPDATE
void main() {
    uint index = gl_GlobalInvocationID.y * gl_NumWorkGroups.x * gl_WorkGroupSize.x + gl_GlobalInvocationID.x;
    if (index == 0u) {
        NextCount = 0u;
    }
    if (index >= min(QueuedCount, uint(Queued.length()))) {
        return;
    }
    uint slot = Queued[index];
    HelionCacheEntry entry = Entries[slot];
    HelionCacheCell cell = helionCacheLayout(entry.state.w & HELION_CACHE_FACE_MASK, int(entry.state.w >> HELION_CACHE_LEVEL_SHIFT));
    vec3 normal = vec3(0.0);
    normal[cell.axis] = (cell.face & 1u) == 0u ? 1.0 : -1.0;
    vec3 position;
    position[cell.axis] = float(entry.cell.x - helionCachePlaneOrigin(cell)) / cell.plane;
    position[cell.column] = (float(entry.cell.y - helionCacheOrigin(cell, cell.column)) + entry.sampleAt.x) / cell.cellsPerBlock;
    position[cell.row] = (float(entry.cell.z - helionCacheOrigin(cell, cell.row)) + entry.sampleAt.y) / cell.cellsPerBlock;
    vec3 origin = position + normal * HELION_ORIGIN_OFFSET;
    if (helionSceneShare(origin) <= 0.0) {
        return;
    }
    helionState = helionPcg(slot ^ helionPcg(uint(Frame.w)));
    HelionSky sky = helionSky();
    int paths = entry.state.z < HELION_CACHE_READY ? HELION_CACHE_YOUNG_PATHS : 1;
    vec3 sum = vec3(0.0);
    for (int path = 0; path < paths; path++) {
        sum += helionIndirect(origin, normal, HELION_RAY_START, entry.light.w, sky);
    }
    uint count = min(entry.state.z + uint(paths), HELION_CACHE_MAX_COUNT);
    Entries[slot].light.rgb = mix(entry.light.rgb, sum / float(paths), float(paths) / float(count));
    Entries[slot].state.z = count;
}
#else
void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(Irradiance);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    vec4 levels = texelFetch(GeometryLightSampler, pixel, 0);
    float depth = texelFetch(SceneDepthSampler, pixel, 0).r;
    if (levels.a < HELION_GEOMETRY_PRESENT_THRESHOLD || depth <= HELION_SKY_DEPTH) {
        imageStore(Irradiance, pixel, vec4(HELION_PATH_NOTHING));
        return;
    }
    if (helionIsEmissiveGeometry(levels.a)) {
        imageStore(Irradiance, pixel, vec4(vec3(1.0), HELION_PATH_EMISSIVE));
        return;
    }
    vec3 albedo = texelFetch(AlbedoSampler, pixel, 0).rgb;
    vec2 screen = (vec2(pixel) + 0.5) / vec2(size);
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 view = world.xyz / world.w;
    vec3 normal = normalize(texelFetch(GeometryNormalSampler, pixel, 0).rgb * 2.0 - 1.0);
    vec3 surface = helionSurfaceAlbedo(albedo, normal);
    vec3 vanilla = min(helionSrgbToLinear(albedo * helionVanillaLight(levels.rg)) / max(surface, vec3(HELION_PATH_MIN_ALBEDO)), vec3(HELION_PATH_VANILLA_LIMIT));
    vec3 position = view + Camera.xyz;
    float span = length(view);
    vec3 origin = position + normal * (HELION_ORIGIN_OFFSET + span * HELION_ORIGIN_DISTANCE_SHARE);
    float traced = helionSceneShare(origin);
    if (traced <= 0.0) {
        imageStore(Irradiance, pixel, vec4(vanilla, HELION_PATH_LIT));
        return;
    }
    helionState = helionPcg(uint(pixel.x) ^ helionPcg(uint(pixel.y) ^ helionPcg(uint(Frame.w))));
    HelionSky sky = helionSky();
    float gate = clamp(levels.g * HELION_SKY_GATE_LEVELS, 0.0, 1.0);
    vec3 indirect;
    if (!helionCachedIndirect(position, normal, span, gate, indirect)) {
        indirect = helionIndirect(origin, normal, helionRayStart(normal), gate, sky);
    }
    vec3 skyLight = helionCelestialLight(origin, normal, sky) * gate + indirect;
    vec3 block = helionBlockLighting(position, origin, normal) * mix(1.0, 1.0 - clamp(helionLuminance(skyLight), 0.0, 1.0), HELION_BLOCK_DAY_FADE);
    imageStore(Irradiance, pixel, vec4(mix(vanilla, sky.ambient + skyLight + block, traced), HELION_PATH_LIT));
}
#endif
