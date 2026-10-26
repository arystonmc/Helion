#version 460
#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require

#include <helion:helion_geometry.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba8) uniform writeonly image2D ShadowMask;
layout(set = 0, binding = 1) uniform accelerationStructureEXT Scene;
layout(set = 0, binding = 2) uniform sampler2D DepthSampler;
layout(set = 0, binding = 3) uniform sampler2D GeometryNormalSampler;
layout(set = 0, binding = 4) uniform sampler2D AtlasSampler;
layout(set = 0, binding = 5, std430) readonly buffer HelionSurfaceTable {
    uvec4 Sections[];
};
layout(set = 0, binding = 6) uniform sampler2D MaterialSampler;

layout(push_constant) uniform HelionRaySunShadow {
    mat4 InverseViewProjection;
    vec4 CameraToAnchor;
    vec4 LightDirection;
    vec4 Softness;
};

#include <helion:helion_rt_surface.glsl>

const float HELION_RT_SKY_DEPTH = 0.0;
const float HELION_RT_NORMAL_OFFSET = 0.02;
const float HELION_RT_DISTANCE_OFFSET = 0.002;
const float HELION_RT_RAY_START = 1.0e-3;
const float HELION_RT_SECTION_SIZE = 16.0;
const float HELION_RT_EDGE_FADE = 8.0;
const float HELION_RT_TAU = 6.28318531;
const float HELION_RT_GOLDEN_ANGLE = 2.39996323;
const float HELION_RT_UPRIGHT = 0.99;
const float HELION_RT_FAR = 1.0e9;
const float HELION_RT_NOISE_SCALE = 52.9829189;
const vec2 HELION_RT_NOISE_STEP = vec2(0.06711056, 0.00583715);
const int HELION_RT_SHADOW_RAYS = 4;
const float HELION_RT_HALF = 0.5;
const float HELION_RT_WITH_ENTITIES = 1.0;
const float HELION_RT_WITHOUT_ENTITIES = 0.5;
const int HELION_RT_VERTICAL_AXIS = 1;
const uint HELION_RT_SECTION_INSTANCES = 0x01u;
const uint HELION_RT_ENTITY_INSTANCES = 0x02u;
const int HELION_RT_EMITTER_SKIPS = 4;
const float HELION_RT_EMITTER_STEP = 1.0e-3;
const vec4 HELION_RT_UNTRACED = vec4(1.0, 0.0, 0.0, 1.0);
const vec3 HELION_RT_UP = vec3(0.0, 1.0, 0.0);
const vec3 HELION_RT_EAST = vec3(1.0, 0.0, 0.0);

float helionInterleavedNoise(ivec2 pixel) {
    return fract(HELION_RT_NOISE_SCALE * fract(dot(vec2(pixel), HELION_RT_NOISE_STEP)));
}

bool helionHitsEmitter(rayQueryEXT query) {
    return helionIsEmissiveCell(helionMaterialCell(textureLod(MaterialSampler, helionCommittedPoint(query).uv, 0.0).r));
}

bool helionTerrainBlocks(vec3 origin, vec3 direction, float reachable) {
    rayQueryEXT query;
    rayQueryInitializeEXT(query, Scene, gl_RayFlagsTerminateOnFirstHitEXT, HELION_RT_SECTION_INSTANCES, origin, HELION_RT_RAY_START, direction, reachable);
    helionTraceCutout(query);
    if (rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
        return false;
    }
    if (!helionHitsEmitter(query)) {
        return true;
    }
    float start = HELION_RT_RAY_START;
    for (int skip = 0; skip < HELION_RT_EMITTER_SKIPS; skip++) {
        rayQueryEXT nearest;
        rayQueryInitializeEXT(nearest, Scene, gl_RayFlagsNoneEXT, HELION_RT_SECTION_INSTANCES, origin, start, direction, reachable);
        helionTraceCutout(nearest);
        if (rayQueryGetIntersectionTypeEXT(nearest, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
            return false;
        }
        if (!helionHitsEmitter(nearest)) {
            return true;
        }
        start = rayQueryGetIntersectionTEXT(nearest, true) + HELION_RT_EMITTER_STEP;
    }
    return true;
}

bool helionEntityBlocks(vec3 origin, vec3 direction, float reachable) {
    rayQueryEXT query;
    rayQueryInitializeEXT(query, Scene, gl_RayFlagsTerminateOnFirstHitEXT, HELION_RT_ENTITY_INSTANCES, origin, HELION_RT_RAY_START, direction, reachable);
    while (rayQueryProceedEXT(query)) {
    }
    return rayQueryGetIntersectionTypeEXT(query, true) != gl_RayQueryCommittedIntersectionNoneEXT;
}

vec2 helionBoxExit(vec3 origin, vec3 direction, float reach) {
    vec3 low = vec3(-reach);
    vec3 high = vec3(reach + HELION_RT_SECTION_SIZE);
    float exit = HELION_RT_FAR;
    float axis = 0.0;
    for (int index = 0; index < 3; index++) {
        if (direction[index] == 0.0) {
            continue;
        }
        float bound = direction[index] > 0.0 ? high[index] : low[index];
        float distance = (bound - origin[index]) / direction[index];
        if (distance < exit) {
            exit = distance;
            axis = float(index);
        }
    }
    return vec2(exit, axis);
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
    vec3 light = normalize(LightDirection.xyz);
    vec3 normal = normalize(encodedNormal.rgb * 2.0 - 1.0);
    if (depth <= HELION_RT_SKY_DEPTH || encodedNormal.a < HELION_GEOMETRY_PRESENT_THRESHOLD || dot(normal, light) <= 0.0) {
        imageStore(ShadowMask, pixel, HELION_RT_UNTRACED);
        return;
    }
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 surface = world.xyz / world.w;
    vec3 origin = surface + CameraToAnchor.xyz + normal * (HELION_RT_NORMAL_OFFSET + length(surface) * HELION_RT_DISTANCE_OFFSET);
    float reach = CameraToAnchor.w;
    vec3 inside = min(origin + reach, vec3(reach + HELION_RT_SECTION_SIZE) - origin);
    float edge = min(inside.x, min(inside.y, inside.z));
    if (edge <= 0.0) {
        imageStore(ShadowMask, pixel, HELION_RT_UNTRACED);
        return;
    }
    vec3 tangent = normalize(cross(light, abs(light.y) < HELION_RT_UPRIGHT ? HELION_RT_UP : HELION_RT_EAST));
    vec3 bitangent = cross(light, tangent);
    float rotation = helionInterleavedNoise(pixel) * HELION_RT_TAU;
    float lit = 0.0;
    float decided = 0.0;
    for (int ray = 0; ray < HELION_RT_SHADOW_RAYS; ray++) {
        float radius = sqrt((float(ray) + 0.5) / float(HELION_RT_SHADOW_RAYS)) * Softness.x;
        float angle = float(ray) * HELION_RT_GOLDEN_ANGLE + rotation;
        vec3 direction = normalize(light + (tangent * cos(angle) + bitangent * sin(angle)) * radius);
        vec2 exit = helionBoxExit(origin, direction, reach);
        float reachable = min(exit.x, LightDirection.w);
        if (helionTerrainBlocks(origin, direction, reachable) || helionEntityBlocks(origin, direction, reachable)) {
            decided += 1.0;
        } else if (int(exit.y) == HELION_RT_VERTICAL_AXIS || exit.x >= LightDirection.w) {
            decided += 1.0;
            lit += 1.0;
        }
    }
    float visibility = decided > 0.0 ? lit / decided : 1.0;
    float weight = decided / float(HELION_RT_SHADOW_RAYS) * smoothstep(0.0, HELION_RT_EDGE_FADE, edge);
    imageStore(ShadowMask, pixel, vec4(visibility, weight, 1.0, Softness.y > HELION_RT_HALF ? HELION_RT_WITH_ENTITIES : HELION_RT_WITHOUT_ENTITIES));
}
