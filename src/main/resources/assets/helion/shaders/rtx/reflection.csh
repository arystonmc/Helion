#version 460
#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require
#ifdef HELION_RT_ENTITIES
#extension GL_EXT_nonuniform_qualifier : require
#endif

#include <helion:helion_water.glsl>
#include <helion:helion_wetness.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba8) uniform writeonly image2D Reflection;
layout(set = 0, binding = 1) uniform accelerationStructureEXT Scene;
layout(set = 0, binding = 2) uniform sampler2D WaterDepthSampler;
layout(set = 0, binding = 3) uniform sampler2D WaterSurfaceSampler;
layout(set = 0, binding = 4) uniform sampler2D AtlasSampler;
layout(set = 0, binding = 5) uniform sampler2D LightmapSampler;
layout(set = 0, binding = 6, std430) readonly buffer HelionSurfaceTable {
    uvec4 Sections[];
};
layout(set = 0, binding = 7) uniform sampler2D SceneDepthSampler;
layout(set = 0, binding = 8) uniform sampler2D GeometryNormalSampler;
layout(set = 0, binding = 9) uniform sampler2D GeometryLightSampler;
layout(set = 0, binding = 10) uniform sampler2D GlassDepthSampler;
layout(set = 0, binding = 11) uniform sampler2D GlassSurfaceSampler;
#ifdef HELION_RT_ENTITIES
const int HELION_RT_ENTITY_TEXTURES = 16;
layout(set = 0, binding = 12) uniform sampler2D EntityTextures[HELION_RT_ENTITY_TEXTURES];
layout(set = 0, binding = 13, std430) readonly buffer HelionEntityTable {
    uvec4 Entities[];
};
#endif

layout(push_constant) uniform HelionRayReflection {
    mat4 InverseViewProjection;
    vec4 CameraToAnchor;
    vec4 Trace;
    vec4 LightDirection;
};

#include <helion:helion_rt_surface.glsl>
#ifdef HELION_RT_ENTITIES
#include <helion:helion_rt_entity.glsl>
#endif

const float HELION_RT_SKY_DEPTH = 0.0;
const float HELION_RT_NORMAL_OFFSET = 0.02;
const float HELION_RT_RAY_START = 1.0e-3;
const float HELION_RT_SECTION_SIZE = 16.0;
const uint HELION_RT_SECTION_INSTANCES = 0x01u;
const uint HELION_RT_ENTITY_INSTANCES = 0x04u;
const uint HELION_RT_SHADOW_INSTANCES = 0x03u;
const float HELION_RT_SHADOW_REACH = 64.0;
const float HELION_RT_SHADOW_LIFT = 0.02;
const float HELION_RT_SHADOWED_SKY = 0.55;
const float HELION_RT_SET = 0.5;
const float HELION_RT_LIGHTMAP_SCALE = 1.0 / 256.0;
const float HELION_RT_LIGHTMAP_TEXEL = 0.5 / 16.0;
const vec4 HELION_RT_MISS = vec4(0.0);
const float HELION_RT_PUDDLE_TRACED = 0.05;
const vec3 HELION_RT_UP = vec3(0.0, 1.0, 0.0);
const vec4 HELION_RT_PUDDLE_WETNESS = vec4(1.0, 0.0, 0.0, 0.0);

vec4 helionLitColor(vec3 albedo, vec2 light) {
    vec2 lightmapUv = clamp(light * HELION_RT_LIGHTMAP_SCALE + HELION_RT_LIGHTMAP_TEXEL, vec2(HELION_RT_LIGHTMAP_TEXEL), vec2(1.0 - HELION_RT_LIGHTMAP_TEXEL));
    return vec4(albedo * textureLod(LightmapSampler, lightmapUv, 0.0).rgb, 1.0);
}

float helionSkyShadow(vec3 hit, vec3 direction) {
    if (LightDirection.w < HELION_RT_SET) {
        return 1.0;
    }
    rayQueryEXT shadow;
    vec3 origin = hit - direction * HELION_RT_SHADOW_LIFT + LightDirection.xyz * HELION_RT_SHADOW_LIFT;
    rayQueryInitializeEXT(shadow, Scene, gl_RayFlagsTerminateOnFirstHitEXT, HELION_RT_SHADOW_INSTANCES, origin, HELION_RT_RAY_START, LightDirection.xyz, HELION_RT_SHADOW_REACH);
    helionTraceCutout(shadow);
    return rayQueryGetIntersectionTypeEXT(shadow, true) == gl_RayQueryCommittedIntersectionNoneEXT ? 1.0 : HELION_RT_SHADOWED_SKY;
}

vec4 helionHitColor(rayQueryEXT query, float skyShadow) {
#ifdef HELION_RT_ENTITIES
    if (helionIsEntity(rayQueryGetIntersectionInstanceCustomIndexEXT(query, true))) {
        HelionEntityPoint entity = helionEntityCommittedPoint(query);
        return helionLitColor(entity.color.rgb, entity.light * vec2(1.0, skyShadow));
    }
#endif
    HelionSurfacePoint point = helionCommittedPoint(query);
    return helionLitColor(textureLod(AtlasSampler, point.uv, 0.0).rgb * point.tint.rgb, point.light * vec2(1.0, skyShadow));
}

bool helionCandidateHit(rayQueryEXT query) {
#ifdef HELION_RT_ENTITIES
    if (helionIsEntity(rayQueryGetIntersectionInstanceCustomIndexEXT(query, false))) {
        return helionEntityCandidateHit(query);
    }
#endif
    return textureLod(AtlasSampler, helionCandidatePoint(query).uv, 0.0).a >= HELION_RT_CUTOUT_ALPHA;
}

void helionTraceReflection(rayQueryEXT query) {
    while (rayQueryProceedEXT(query)) {
        if (rayQueryGetIntersectionTypeEXT(query, false) == gl_RayQueryCandidateIntersectionTriangleEXT && helionCandidateHit(query)) {
            rayQueryConfirmIntersectionEXT(query);
        }
    }
}

uint helionReflectedInstances() {
#ifdef HELION_RT_ENTITIES
    return HELION_RT_SECTION_INSTANCES | HELION_RT_ENTITY_INSTANCES;
#else
    return HELION_RT_SECTION_INSTANCES;
#endif
}

bool helionIsTracedPuddle(ivec2 pixel, vec2 screen) {
    float sceneDepth = texelFetch(SceneDepthSampler, pixel, 0).r;
    vec4 light = texelFetch(GeometryLightSampler, pixel, 0);
    if (Trace.w <= 0.0 || sceneDepth <= HELION_RT_SKY_DEPTH || light.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        return false;
    }
    vec3 surfaceNormal = normalize(texelFetch(GeometryNormalSampler, pixel, 0).rgb * 2.0 - 1.0);
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, sceneDepth, 1.0);
    vec3 position = world.xyz / world.w + vec3(Trace.y, 0.0, Trace.z);
    vec4 wetness = vec4(HELION_RT_PUDDLE_WETNESS.x, Trace.w, HELION_RT_PUDDLE_WETNESS.zw);
    return helionWetSurface(light, surfaceNormal, position, wetness, vec2(0.0), false).puddle > HELION_RT_PUDDLE_TRACED;
}

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(Reflection);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    float depth = texelFetch(WaterDepthSampler, pixel, 0).r;
    vec4 water = texelFetch(WaterSurfaceSampler, pixel, 0);
    if (!helionIsWater(water)) {
        depth = texelFetch(GlassDepthSampler, pixel, 0).r;
        water = texelFetch(GlassSurfaceSampler, pixel, 0);
    }
    vec2 screen = (vec2(pixel) + 0.5) / vec2(size);
    vec3 normal;
    if (depth > HELION_RT_SKY_DEPTH && (helionIsWater(water) || helionIsGlossy(water))) {
        normal = helionDecodeOctahedral(water.xy);
    } else if (helionIsTracedPuddle(pixel, screen)) {
        depth = texelFetch(SceneDepthSampler, pixel, 0).r;
        normal = HELION_RT_UP;
    } else {
        imageStore(Reflection, pixel, HELION_RT_MISS);
        return;
    }
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 position = world.xyz / world.w;
    vec3 direction = reflect(normalize(position), normal);
    vec3 origin = position + CameraToAnchor.xyz + normal * HELION_RT_NORMAL_OFFSET;
    float reach = CameraToAnchor.w;
    if (any(lessThan(origin, vec3(-reach))) || any(greaterThanEqual(origin, vec3(reach + HELION_RT_SECTION_SIZE)))) {
        imageStore(Reflection, pixel, HELION_RT_MISS);
        return;
    }
    rayQueryEXT query;
    rayQueryInitializeEXT(query, Scene, gl_RayFlagsNoneEXT, helionReflectedInstances(), origin, HELION_RT_RAY_START, direction, Trace.x);
    helionTraceReflection(query);
    if (rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
        imageStore(Reflection, pixel, HELION_RT_MISS);
        return;
    }
    vec3 hit = origin + direction * rayQueryGetIntersectionTEXT(query, true);
    imageStore(Reflection, pixel, helionHitColor(query, helionSkyShadow(hit, direction)));
}
