#version 460
#extension GL_EXT_ray_query : require

#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_light_level.glsl>
#include <helion:helion_voxel.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba8) uniform image2D ShadowMask;
layout(set = 0, binding = 1) uniform accelerationStructureEXT Scene;
layout(set = 0, binding = 2, std430) buffer Agreement {
    uint counts[];
};
layout(set = 0, binding = 3, std430) readonly buffer Boxes {
    float boxes[];
};
layout(set = 0, binding = 4) uniform sampler2D DepthSampler;
layout(set = 0, binding = 5) uniform sampler2D GeometryNormalSampler;
layout(set = 0, binding = 6) uniform sampler2D VoxelSampler;
layout(set = 0, binding = 7) uniform sampler2D VoxelCoarseSampler;
layout(set = 0, binding = 8) uniform sampler2D VoxelReadySampler;
layout(set = 0, binding = 9) uniform sampler2D VoxelLightListSampler;
layout(set = 0, binding = 10) uniform sampler2D VoxelLightGridSampler;

layout(push_constant) uniform HelionRtxTrace {
    mat4 InverseViewProjection;
    vec4 CameraToVolume;
    ivec4 VolumeMin;
    ivec4 AreaMin;
    ivec4 AreaSize;
};

#include <helion:helion_voxel_trace.glsl>

const int HELION_RTX_RAY_QUERY = 0;
const int HELION_RTX_GRID_WALK = 1;
const int HELION_RTX_COMPARE = 2;
const uint HELION_RTX_COUNT_TRACED = 0u;
const uint HELION_RTX_COUNT_RAY_QUERY_BLOCKED = 1u;
const uint HELION_RTX_COUNT_GRID_WALK_BLOCKED = 2u;
const uint HELION_RTX_COUNT_DISAGREE = 3u;
const int HELION_RTX_BOX_FLOATS = 6;
const uint HELION_RTX_ALL_INSTANCES = 0xFFu;
const float HELION_RTX_RAY_START = 1.0e-3;
const float HELION_RTX_LIT = 0.5;
const vec4 HELION_RTX_UNTRACED = vec4(1.0, 1.0, 0.0, 1.0);

bool helionInArea(vec3 position) {
    vec3 local = position - vec3(AreaMin.xyz);
    return all(greaterThanEqual(local, vec3(0.0))) && all(lessThan(local, vec3(AreaSize.xyz)));
}

float helionBoxEntry(vec3 origin, vec3 direction, int box) {
    int base = box * HELION_RTX_BOX_FLOATS;
    vec3 low = vec3(boxes[base], boxes[base + 1], boxes[base + 2]);
    vec3 high = vec3(boxes[base + 3], boxes[base + 4], boxes[base + 5]);
    vec3 safe = mix(direction, vec3(HELION_MIN_DIRECTION), lessThan(abs(direction), vec3(HELION_MIN_DIRECTION)));
    vec3 first = (low - origin) / safe;
    vec3 second = (high - origin) / safe;
    vec3 near = min(first, second);
    vec3 far = max(first, second);
    float enter = max(near.x, max(near.y, near.z));
    float leave = min(far.x, min(far.y, far.z));
    return enter <= leave ? enter : -1.0;
}

bool helionRayQueryVisible(vec3 from, vec3 to) {
    vec3 origin = from - vec3(AreaMin.xyz);
    vec3 offset = to - from;
    float distance = length(offset);
    vec3 direction = offset / distance;
    rayQueryEXT query;
    rayQueryInitializeEXT(query, Scene, gl_RayFlagsTerminateOnFirstHitEXT, HELION_RTX_ALL_INSTANCES, origin, HELION_RTX_RAY_START, direction, distance);
    while (rayQueryProceedEXT(query)) {
        if (rayQueryGetIntersectionTypeEXT(query, false) == gl_RayQueryCandidateIntersectionAABBEXT) {
            float hit = helionBoxEntry(origin, direction, rayQueryGetIntersectionPrimitiveIndexEXT(query, false));
            if (hit >= HELION_RTX_RAY_START && hit <= distance) {
                rayQueryGenerateIntersectionEXT(query, hit);
            }
        }
    }
    return rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT;
}

int helionBrightestLight(vec3 surface, ivec3 cell) {
    int brightest = HELION_NO_LIGHT;
    float strongest = 0.0;
    for (int slot = 0; slot < HELION_LIGHTS_PER_CELL; slot++) {
        int light = helionGridLight(cell, slot);
        if (light == HELION_NO_LIGHT) {
            break;
        }
        vec4 source = texelFetch(VoxelLightListSampler, ivec2(light, 0), 0);
        vec3 offset = abs(surface - source.xyz);
        float level = source.w - (offset.x + offset.y + offset.z);
        if (level <= 0.0) {
            continue;
        }
        vec3 term = texelFetch(VoxelLightListSampler, ivec2(light, 1), 0).rgb * helionLevelBrightness(level / HELION_LIGHT_LEVELS);
        float weight = helionLuminance(term);
        if (weight > strongest) {
            strongest = weight;
            brightest = light;
        }
    }
    return brightest;
}

void helionCount(uint counter) {
    atomicAdd(counts[counter], 1u);
}

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(ShadowMask);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    vec2 screen = (vec2(pixel) + 0.5) / vec2(size);
    ivec2 fullSize = textureSize(DepthSampler, 0);
    ivec2 fullPixel = min(ivec2(screen * vec2(fullSize)), fullSize - 1);
    float depth = texelFetch(DepthSampler, fullPixel, 0).r;
    vec4 encodedNormal = texelFetch(GeometryNormalSampler, fullPixel, 0);
    if (depth <= HELION_SKY_DEPTH || encodedNormal.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        imageStore(ShadowMask, pixel, HELION_RTX_UNTRACED);
        return;
    }
    vec3 surface = helionVoxelSurface(screen, depth, normalize(encodedNormal.rgb * 2.0 - 1.0));
    ivec3 block = ivec3(floor(surface));
    if (helionEdgeDistance(surface, CameraToVolume.w) <= 0.0 || !helionInArea(surface)
        || !helionVoxelSlotReady(VoxelReadySampler, VolumeMin.xyz + block)) {
        imageStore(ShadowMask, pixel, HELION_RTX_UNTRACED);
        return;
    }
    int light = helionBrightestLight(surface, helionLightCell(block));
    if (light == HELION_NO_LIGHT) {
        imageStore(ShadowMask, pixel, HELION_RTX_UNTRACED);
        return;
    }
    bool enclosed;
    vec3 exit = helionLightExit(texelFetch(VoxelLightListSampler, ivec2(light, 0), 0).xyz, surface, enclosed);
    if (!helionInArea(exit)) {
        imageStore(ShadowMask, pixel, HELION_RTX_UNTRACED);
        return;
    }
    int mode = VolumeMin.w;
    vec4 previous = imageLoad(ShadowMask, pixel);
    bool rayLit = previous.r > HELION_RTX_LIT;
    bool walkLit = previous.g > HELION_RTX_LIT;
    if (mode != HELION_RTX_GRID_WALK) {
        rayLit = !enclosed && helionRayQueryVisible(surface, exit);
    }
    if (mode != HELION_RTX_RAY_QUERY) {
        bool unknown = false;
        walkLit = !enclosed && helionTraceVisibility(surface, exit, unknown) > 0.0;
    }
    if (mode == HELION_RTX_COMPARE) {
        helionCount(HELION_RTX_COUNT_TRACED);
        if (!rayLit) {
            helionCount(HELION_RTX_COUNT_RAY_QUERY_BLOCKED);
        }
        if (!walkLit) {
            helionCount(HELION_RTX_COUNT_GRID_WALK_BLOCKED);
        }
        if (rayLit != walkLit) {
            helionCount(HELION_RTX_COUNT_DISAGREE);
        }
    }
    imageStore(ShadowMask, pixel, vec4(rayLit ? 1.0 : 0.0, walkLit ? 1.0 : 0.0, 1.0, 1.0));
}
