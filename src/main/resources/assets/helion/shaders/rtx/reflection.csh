#version 460
#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require

#include <helion:helion_water.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer HelionSurface {
    uint values[];
};

layout(set = 0, binding = 0, rgba8) uniform writeonly image2D Reflection;
layout(set = 0, binding = 1) uniform accelerationStructureEXT Scene;
layout(set = 0, binding = 2) uniform sampler2D WaterDepthSampler;
layout(set = 0, binding = 3) uniform sampler2D WaterSurfaceSampler;
layout(set = 0, binding = 4) uniform sampler2D AtlasSampler;
layout(set = 0, binding = 5) uniform sampler2D LightmapSampler;
layout(set = 0, binding = 6, std430) readonly buffer HelionSurfaceTable {
    uvec2 Sections[];
};

layout(push_constant) uniform HelionRayReflection {
    mat4 InverseViewProjection;
    vec4 CameraToAnchor;
    vec4 Trace;
};

const float HELION_RT_SKY_DEPTH = 0.0;
const float HELION_RT_NORMAL_OFFSET = 0.02;
const float HELION_RT_RAY_START = 1.0e-3;
const float HELION_RT_SECTION_SIZE = 16.0;
const uint HELION_RT_ALL_INSTANCES = 0xFFu;
const uint HELION_RT_INTS_PER_VERTEX = 3u;
const uint HELION_RT_VERTICES_PER_QUAD = 4u;
const uint HELION_RT_HALF_MASK = 0xFFFFu;
const uint HELION_RT_HALF_SHIFT = 16u;
const uint HELION_RT_BYTE_MASK = 0xFFu;
const uint HELION_RT_BYTE_SHIFT = 8u;
const float HELION_RT_UV_SCALE = 1.0 / 65536.0;
const float HELION_RT_LIGHTMAP_SCALE = 1.0 / 256.0;
const float HELION_RT_LIGHTMAP_TEXEL = 0.5 / 16.0;
const vec4 HELION_RT_MISS = vec4(0.0);

vec2 helionUv(uint packed) {
    return vec2(packed & HELION_RT_HALF_MASK, packed >> HELION_RT_HALF_SHIFT) * HELION_RT_UV_SCALE;
}

vec2 helionLight(uint packed) {
    return vec2(packed & HELION_RT_BYTE_MASK, (packed >> HELION_RT_BYTE_SHIFT) & HELION_RT_BYTE_MASK);
}

vec4 helionHitColor(rayQueryEXT query) {
    HelionSurface surface = HelionSurface(Sections[rayQueryGetIntersectionInstanceCustomIndexEXT(query, true)]);
    uint primitive = uint(rayQueryGetIntersectionPrimitiveIndexEXT(query, true));
    vec2 barycentrics = rayQueryGetIntersectionBarycentricsEXT(query, true);
    vec3 weights = vec3(1.0 - barycentrics.x - barycentrics.y, barycentrics.x, barycentrics.y);
    uvec3 corners = (primitive & 1u) == 0u ? uvec3(0u, 1u, 2u) : uvec3(2u, 3u, 0u);
    uvec3 vertices = (primitive >> 1u) * HELION_RT_VERTICES_PER_QUAD + corners;
    vec2 uv = vec2(0.0);
    vec4 tint = vec4(0.0);
    vec2 light = vec2(0.0);
    for (int corner = 0; corner < 3; corner++) {
        uint base = vertices[corner] * HELION_RT_INTS_PER_VERTEX;
        uv += helionUv(surface.values[base]) * weights[corner];
        tint += unpackUnorm4x8(surface.values[base + 1u]) * weights[corner];
        light += helionLight(surface.values[base + 2u]) * weights[corner];
    }
    vec3 albedo = textureLod(AtlasSampler, uv, 0.0).rgb * tint.rgb;
    vec2 lightmapUv = clamp(light * HELION_RT_LIGHTMAP_SCALE + HELION_RT_LIGHTMAP_TEXEL, vec2(HELION_RT_LIGHTMAP_TEXEL), vec2(1.0 - HELION_RT_LIGHTMAP_TEXEL));
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
    rayQueryInitializeEXT(query, Scene, gl_RayFlagsOpaqueEXT, HELION_RT_ALL_INSTANCES, origin, HELION_RT_RAY_START, direction, Trace.x);
    while (rayQueryProceedEXT(query)) {
    }
    if (rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
        imageStore(Reflection, pixel, HELION_RT_MISS);
        return;
    }
    imageStore(Reflection, pixel, helionHitColor(query));
}
