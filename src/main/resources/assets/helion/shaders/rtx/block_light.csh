#version 460
#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require

#include <helion:helion_geometry.glsl>
#include <helion:helion_light_level.glsl>
#include <helion:helion_hash.glsl>
#include <helion:helion_voxel.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D BlockLight;
layout(set = 0, binding = 1) uniform accelerationStructureEXT Scene;
layout(set = 0, binding = 2) uniform sampler2D DepthSampler;
layout(set = 0, binding = 3) uniform sampler2D GeometryNormalSampler;
layout(set = 0, binding = 4) uniform sampler2D AtlasSampler;
layout(set = 0, binding = 5, std430) readonly buffer HelionSurfaceTable {
    uvec4 Sections[];
};
layout(set = 0, binding = 6) uniform sampler2D MaterialSampler;
layout(set = 0, binding = 7) uniform sampler2D VoxelLightListSampler;
layout(set = 0, binding = 8) uniform sampler2D VoxelLightGridSampler;
layout(set = 0, binding = 9, std430) readonly buffer HelionTracedDynamicLights {
    ivec4 DynamicCounts;
    vec4 DynamicLights[];
};
layout(set = 0, binding = 10) uniform sampler2D VoxelSampler;
layout(set = 0, binding = 11) uniform sampler2D VoxelCoarseSampler;
layout(set = 0, binding = 12) uniform sampler2D VoxelColorSampler;

layout(push_constant) uniform HelionRayBlockLight {
    mat4 InverseViewProjection;
    vec4 CameraToAnchor;
    vec4 CameraToVolume;
    ivec4 VolumeMin;
    vec4 Settings;
};

#include <helion:helion_rt_surface.glsl>

const float HELION_RT_SKY_DEPTH = 0.0;
const float HELION_RT_NORMAL_OFFSET = 0.03;
const float HELION_RT_DISTANCE_OFFSET = 0.002;
const float HELION_RT_RAY_START = 1.0e-3;
const float HELION_RT_PLANT_START = 0.3;
const float HELION_RT_SECTION_SIZE = 16.0;
const float HELION_RT_LIGHT_END = 0.45;
const float HELION_RT_LIGHT_SPREAD = 0.3;
const float HELION_RT_EMITTER_STEP = 1.0e-3;
const int HELION_RT_EMITTER_SKIPS = 3;
const uint HELION_RT_SECTION_INSTANCES = 0x01u;
const uint HELION_RT_ENTITY_INSTANCES = 0x02u;
const int HELION_RT_LIGHTS_PER_CELL = 16;
const int HELION_RT_CELL_SHIFT = 3;
const int HELION_RT_CELLS_PER_AXIS = 16;
const float HELION_RT_GRID_SCALE = 65535.0;
const int HELION_RT_DYNAMIC_LIGHTS = 16;
const int HELION_RT_CANDIDATES = HELION_RT_LIGHTS_PER_CELL + HELION_RT_DYNAMIC_LIGHTS;
const int HELION_RT_MAX_TRACED = 8;
const float HELION_RT_TRACE_SHARE = 0.35;
const float HELION_RT_LIGHT_SPILL = 0.15;
const float HELION_RT_UNLIT = 1.0e-5;
const float HELION_RT_WRAP = 0.5;
const float HELION_RT_TOUCHING = 0.05;
const float HELION_RT_BOX_MARGIN = 0.02;
const float HELION_RT_POINT_END = 0.3;
const float HELION_RT_FLICKERING = 0.5;
const float HELION_RT_FLICKER_STRENGTH = 0.07;
const float HELION_RT_TAU = 6.28318531;
const vec3 HELION_RT_FLICKER_RATES = vec3(5.3, 9.1, 15.7);
const vec3 HELION_RT_FLICKER_WEIGHTS = vec3(0.5, 0.3, 0.2);
const vec3 HELION_RT_FLICKER_PHASE_SCALE = vec3(1.0, 1.9, 3.1);
const uint HELION_RT_DYNAMIC_SEED = 7919u;
const uint HELION_RT_FRAME_SEED = 1013u;
const vec3 HELION_RT_LUMA = vec3(0.2126, 0.7152, 0.0722);
const vec4 HELION_RT_UNTRACED = vec4(0.0, 0.0, 0.0, 1.0);
const int HELION_RT_TINT_STEPS = 32;
const int HELION_RT_TINTS_LIGHT = 4;
const float HELION_RT_MIN_DIRECTION = 1.0e-6;

struct HelionLightSum {
    vec3 hue;
    float total;
    float peak;
};

struct HelionCandidate {
    vec3 position;
    vec3 term;
};

float helionLuminance(vec3 color) {
    return dot(color, HELION_RT_LUMA);
}

float helionUnitHash(uint seed) {
    return float(helionPcg(seed)) / HELION_HASH_RANGE;
}

float helionFlicker(float flickers, uint seed) {
    if (flickers < HELION_RT_FLICKERING) {
        return 1.0;
    }
    float phase = helionUnitHash(seed) * HELION_RT_TAU;
    vec3 waves = sin(Settings.y * HELION_RT_FLICKER_RATES + phase * HELION_RT_FLICKER_PHASE_SCALE);
    return 1.0 + HELION_RT_FLICKER_STRENGTH * dot(waves, HELION_RT_FLICKER_WEIGHTS);
}

uint helionBlockSeed(vec3 volumePosition) {
    ivec3 world = ivec3(floor(volumePosition)) + VolumeMin.xyz;
    return helionPcg(helionPcg(helionPcg(uint(world.x)) + uint(world.y)) + uint(world.z));
}

int helionGridLight(ivec3 cell, int slot) {
    int index = cell.x + HELION_RT_CELLS_PER_AXIS * (cell.y + HELION_RT_CELLS_PER_AXIS * cell.z);
    ivec2 texel = ivec2(index % HELION_RT_CELLS_PER_AXIS * HELION_RT_LIGHTS_PER_CELL + slot, index / HELION_RT_CELLS_PER_AXIS);
    return int(texelFetch(VoxelLightGridSampler, texel, 0).r * HELION_RT_GRID_SCALE + 0.5) - 1;
}

HelionCandidate helionCandidate(ivec3 cell, vec3 surface, vec3 normal, int candidate) {
    vec4 source;
    vec4 color;
    uint seed;
    if (candidate < HELION_RT_LIGHTS_PER_CELL) {
        int light = helionGridLight(cell, candidate);
        source = texelFetch(VoxelLightListSampler, ivec2(light, 0), 0);
        color = texelFetch(VoxelLightListSampler, ivec2(light, 1), 0);
        seed = helionBlockSeed(source.xyz);
    } else {
        int index = candidate - HELION_RT_LIGHTS_PER_CELL;
        source = DynamicLights[index * 2];
        color = DynamicLights[index * 2 + 1];
        seed = helionPcg(uint(index) + HELION_RT_DYNAMIC_SEED);
    }
    float level = source.w - distance(surface, source.xyz) * HELION_VOXEL_LIGHT_FALLOFF;
    if (level <= 0.0) {
        return HelionCandidate(source.xyz, vec3(0.0));
    }
    bool ownFace = false;
    if (candidate < HELION_RT_LIGHTS_PER_CELL) {
        vec3 low = floor(source.xyz);
        ownFace = distance(clamp(surface, low, low + 1.0), surface) < HELION_RT_TOUCHING && dot(normal, surface - source.xyz) > 0.0;
    }
    vec3 toLight = source.xyz - surface;
    float distanceToLight = length(toLight);
    float facing = ownFace || distanceToLight <= HELION_RT_TOUCHING ? 1.0 : dot(normal, toLight / distanceToLight);
    float lambert = clamp((facing + HELION_RT_WRAP) / (1.0 + HELION_RT_WRAP), 0.0, 1.0);
    vec3 term = helionBalancedLightColor(color.rgb) * helionLevelBrightness(level / HELION_LIGHT_LEVELS) * helionFlicker(color.a, seed) * lambert;
    return HelionCandidate(source.xyz, term);
}

void helionAddLight(inout HelionLightSum sum, vec3 term) {
    float luminance = helionLuminance(term);
    sum.hue += term;
    sum.total += luminance;
    sum.peak = max(sum.peak, luminance);
}

vec3 helionLightTotal(HelionLightSum sum) {
    float headroom = max(1.0 - sum.peak, 0.0);
    float luminance = sum.peak + headroom * (1.0 - exp(-(sum.total - sum.peak) * HELION_RT_LIGHT_SPILL));
    return sum.total > HELION_RT_UNLIT ? sum.hue * (luminance / sum.total) : vec3(0.0);
}

vec3 helionGlassTint(vec3 from, vec3 to) {
    vec3 direction = to - from;
    ivec3 cell = ivec3(floor(from));
    ivec3 target = ivec3(floor(to));
    ivec3 stepping = ivec3(sign(direction));
    vec3 delta = 1.0 / max(abs(direction), vec3(HELION_RT_MIN_DIRECTION));
    vec3 next = mix(from - vec3(cell), vec3(cell) + 1.0 - from, step(0.0, direction)) * delta;
    vec3 tint = vec3(1.0);
    for (int walk = 0; walk < HELION_RT_TINT_STEPS; walk++) {
        if (min(next.x, min(next.y, next.z)) > 1.0) {
            break;
        }
        if (next.x <= next.y && next.x <= next.z) {
            cell.x += stepping.x;
            next.x += delta.x;
        } else if (next.y <= next.z) {
            cell.y += stepping.y;
            next.y += delta.y;
        } else {
            cell.z += stepping.z;
            next.z += delta.z;
        }
        if (cell == target) {
            break;
        }
        ivec3 block = VolumeMin.xyz + cell;
        if (!helionVoxelCoarseBlocked(VoxelCoarseSampler, block)) {
            continue;
        }
        if ((helionVoxelFetch(VoxelSampler, block) & HELION_RT_TINTS_LIGHT) != 0) {
            tint *= texelFetch(VoxelColorSampler, helionVoxelTexel(block), 0).rgb;
        }
    }
    return tint;
}

float helionBoxEntry(vec3 origin, vec3 direction, vec3 low, vec3 high) {
    vec3 safe = mix(direction, vec3(HELION_RT_MIN_DIRECTION), lessThan(abs(direction), vec3(HELION_RT_MIN_DIRECTION)));
    vec3 first = (low - origin) / safe;
    vec3 second = (high - origin) / safe;
    vec3 near = min(first, second);
    return max(max(near.x, near.y), near.z);
}

bool helionHitsEmitter(rayQueryEXT query) {
    return helionIsEmissiveCell(helionMaterialCell(textureLod(MaterialSampler, helionCommittedPoint(query).uv, 0.0).r));
}

bool helionBlocked(vec3 origin, vec3 direction, float reach, float start) {
    if (reach <= start) {
        return false;
    }
    rayQueryEXT entities;
    rayQueryInitializeEXT(entities, Scene, gl_RayFlagsTerminateOnFirstHitEXT, HELION_RT_ENTITY_INSTANCES, origin, start, direction, reach);
    while (rayQueryProceedEXT(entities)) {
    }
    if (rayQueryGetIntersectionTypeEXT(entities, true) != gl_RayQueryCommittedIntersectionNoneEXT) {
        return true;
    }
    rayQueryEXT first;
    rayQueryInitializeEXT(first, Scene, gl_RayFlagsTerminateOnFirstHitEXT, HELION_RT_SECTION_INSTANCES, origin, start, direction, reach);
    helionTraceCutout(first);
    if (rayQueryGetIntersectionTypeEXT(first, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
        return false;
    }
    if (!helionHitsEmitter(first)) {
        return true;
    }
    float from = start;
    for (int skip = 0; skip < HELION_RT_EMITTER_SKIPS; skip++) {
        rayQueryEXT nearest;
        rayQueryInitializeEXT(nearest, Scene, gl_RayFlagsNoneEXT, HELION_RT_SECTION_INSTANCES, origin, from, direction, reach);
        helionTraceCutout(nearest);
        if (rayQueryGetIntersectionTypeEXT(nearest, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
            return false;
        }
        if (!helionHitsEmitter(nearest)) {
            return true;
        }
        from = rayQueryGetIntersectionTEXT(nearest, true) + HELION_RT_EMITTER_STEP;
    }
    return true;
}

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(BlockLight);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    vec2 screen = (vec2(pixel) + 0.5) / vec2(size);
    ivec2 fullSize = textureSize(DepthSampler, 0);
    ivec2 fullPixel = min(ivec2(screen * vec2(fullSize)), fullSize - 1);
    float depth = texelFetch(DepthSampler, fullPixel, 0).r;
    vec4 encodedNormal = texelFetch(GeometryNormalSampler, fullPixel, 0);
    if (depth <= HELION_RT_SKY_DEPTH || encodedNormal.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        imageStore(BlockLight, pixel, HELION_RT_UNTRACED);
        return;
    }
    vec3 normal = normalize(encodedNormal.rgb * 2.0 - 1.0);
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 relative = world.xyz / world.w;
    vec3 surface = relative + CameraToVolume.xyz;
    vec3 evaluated = surface + normal * HELION_RT_LIGHT_END;
    ivec3 cell = clamp(ivec3(floor(evaluated)) >> HELION_RT_CELL_SHIFT, ivec3(0), ivec3(HELION_RT_CELLS_PER_AXIS - 1));
    float weights[HELION_RT_CANDIDATES];
    for (int candidate = 0; candidate < HELION_RT_CANDIDATES; candidate++) {
        weights[candidate] = 0.0;
    }
    float strongest = 0.0;
    for (int slot = 0; slot < HELION_RT_LIGHTS_PER_CELL; slot++) {
        if (helionGridLight(cell, slot) < 0) {
            break;
        }
        weights[slot] = helionLuminance(helionCandidate(cell, surface, normal, slot).term);
        strongest = max(strongest, weights[slot]);
    }
    for (int index = 0; index < HELION_RT_DYNAMIC_LIGHTS; index++) {
        if (index >= DynamicCounts.x) {
            break;
        }
        int candidate = HELION_RT_LIGHTS_PER_CELL + index;
        weights[candidate] = helionLuminance(helionCandidate(cell, surface, normal, candidate).term);
        strongest = max(strongest, weights[candidate]);
    }
    if (strongest <= HELION_RT_UNLIT) {
        imageStore(BlockLight, pixel, HELION_RT_UNTRACED);
        return;
    }
    float viewDistance = length(relative);
    vec3 origin = relative + CameraToAnchor.xyz + normal * (HELION_RT_NORMAL_OFFSET + viewDistance * HELION_RT_DISTANCE_OFFSET);
    float start = helionIsCrossPlane(normal) ? HELION_RT_PLANT_START : HELION_RT_RAY_START;
    uint frameSeed = helionPcg(uint(pixel.x) + helionPcg(uint(pixel.y) + uint(Settings.x) * HELION_RT_FRAME_SEED));
    HelionLightSum unshadowed = HelionLightSum(vec3(0.0), 0.0, 0.0);
    HelionLightSum visible = HelionLightSum(vec3(0.0), 0.0, 0.0);
    float tracedWeight = 0.0;
    float visibleWeight = 0.0;
    int budget = min(int(Settings.z), HELION_RT_MAX_TRACED);
    for (int attempt = 0; attempt < HELION_RT_MAX_TRACED; attempt++) {
        if (attempt >= budget) {
            break;
        }
        int pick = -1;
        for (int candidate = 0; candidate < HELION_RT_CANDIDATES; candidate++) {
            if (weights[candidate] > 0.0 && (pick < 0 || weights[candidate] > weights[pick])) {
                pick = candidate;
            }
        }
        if (pick < 0 || (attempt > 0 && weights[pick] < strongest * HELION_RT_TRACE_SHARE)) {
            break;
        }
        HelionCandidate light = helionCandidate(cell, surface, normal, pick);
        uint seed = helionPcg(frameSeed + uint(pick));
        vec3 jitter = (vec3(helionUnitHash(seed), helionUnitHash(seed + 1u), helionUnitHash(seed + 2u)) - 0.5) * 2.0 * HELION_RT_LIGHT_SPREAD;
        vec3 target = light.position + jitter - CameraToVolume.xyz + CameraToAnchor.xyz;
        vec3 toTarget = target - origin;
        vec3 direction = toTarget / max(length(toTarget), HELION_RT_UNLIT);
        float reach = length(toTarget) - HELION_RT_POINT_END;
        if (pick < HELION_RT_LIGHTS_PER_CELL) {
            vec3 low = floor(light.position) - CameraToVolume.xyz + CameraToAnchor.xyz;
            reach = helionBoxEntry(origin, direction, low, low + 1.0) - HELION_RT_BOX_MARGIN;
        }
        bool blocked = helionBlocked(origin, direction, reach, start);
        helionAddLight(unshadowed, light.term);
        if (!blocked) {
            helionAddLight(visible, light.term * helionGlassTint(surface + normal * HELION_RT_LIGHT_END, light.position));
            visibleWeight += weights[pick];
        }
        tracedWeight += weights[pick];
        weights[pick] = 0.0;
    }
    float share = tracedWeight > HELION_RT_UNLIT ? visibleWeight / tracedWeight : 1.0;
    for (int candidate = 0; candidate < HELION_RT_CANDIDATES; candidate++) {
        if (weights[candidate] > 0.0) {
            vec3 term = helionCandidate(cell, surface, normal, candidate).term;
            helionAddLight(unshadowed, term);
            helionAddLight(visible, term * share);
        }
    }
    vec3 total = helionLightTotal(unshadowed);
    vec3 lit = helionLightTotal(visible);
    float totalLuminance = helionLuminance(total);
    float litLuminance = helionLuminance(lit);
    float visibility = totalLuminance > HELION_RT_UNLIT ? litLuminance / totalLuminance : 1.0;
    vec3 litHue = litLuminance > HELION_RT_UNLIT ? lit * (totalLuminance / litLuminance) : total;
    imageStore(BlockLight, pixel, vec4(litHue, visibility));
}
