#version 460
#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require

#include <helion:helion_water.glsl>

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

layout(push_constant) uniform HelionRayReflection {
    mat4 InverseViewProjection;
    vec4 CameraToAnchor;
    vec4 Trace;
};

#include <helion:helion_rt_surface.glsl>

const float HELION_RT_SKY_DEPTH = 0.0;
const float HELION_RT_NORMAL_OFFSET = 0.02;
const float HELION_RT_RAY_START = 1.0e-3;
const float HELION_RT_SECTION_SIZE = 16.0;
const uint HELION_RT_ALL_INSTANCES = 0xFFu;
const float HELION_RT_LIGHTMAP_SCALE = 1.0 / 256.0;
const float HELION_RT_LIGHTMAP_TEXEL = 0.5 / 16.0;
const vec4 HELION_RT_MISS = vec4(0.0);

vec4 helionHitColor(rayQueryEXT query) {
    HelionSurfacePoint point = helionCommittedPoint(query);
    vec3 albedo = textureLod(AtlasSampler, point.uv, 0.0).rgb * point.tint.rgb;
    vec2 lightmapUv = clamp(point.light * HELION_RT_LIGHTMAP_SCALE + HELION_RT_LIGHTMAP_TEXEL, vec2(HELION_RT_LIGHTMAP_TEXEL), vec2(1.0 - HELION_RT_LIGHTMAP_TEXEL));
    return vec4(albedo * textureLod(LightmapSampler, lightmapUv, 0.0).rgb, 1.0);
}

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(Reflection);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    float depth = texelFetch(WaterDepthSampler, pixel, 0).r;
    vec4 water = texelFetch(WaterSurfaceSampler, pixel, 0);
    if (depth <= HELION_RT_SKY_DEPTH || !helionIsWater(water)) {
        imageStore(Reflection, pixel, HELION_RT_MISS);
        return;
    }
    vec3 normal = helionDecodeOctahedral(water.xy);
    vec2 screen = (vec2(pixel) + 0.5) / vec2(size);
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
    rayQueryInitializeEXT(query, Scene, gl_RayFlagsNoneEXT, HELION_RT_ALL_INSTANCES, origin, HELION_RT_RAY_START, direction, Trace.x);
    helionTraceCutout(query);
    if (rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
        imageStore(Reflection, pixel, HELION_RT_MISS);
        return;
    }
    imageStore(Reflection, pixel, helionHitColor(query));
}
