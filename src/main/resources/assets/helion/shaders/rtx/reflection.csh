#version 460
#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require
#ifdef HELION_RT_ENTITY_TEXTURES
#extension GL_EXT_nonuniform_qualifier : require
#endif

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
#ifdef HELION_RT_ENTITY_TEXTURES
layout(set = 0, binding = 7, std430) readonly buffer HelionEntityTable {
    uvec4 Entities[];
};
layout(set = 1, binding = 0) uniform sampler2D EntityTextures[HELION_RT_ENTITY_TEXTURES];
#endif

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
const uint HELION_RT_SECTION_INSTANCES = 0x01u;
const float HELION_RT_LIGHTMAP_SCALE = 1.0 / 256.0;
const float HELION_RT_LIGHTMAP_TEXEL = 0.5 / 16.0;
const vec4 HELION_RT_MISS = vec4(0.0);

vec3 helionLightmap(vec2 light) {
    vec2 lightmapUv = clamp(light * HELION_RT_LIGHTMAP_SCALE + HELION_RT_LIGHTMAP_TEXEL, vec2(HELION_RT_LIGHTMAP_TEXEL), vec2(1.0 - HELION_RT_LIGHTMAP_TEXEL));
    return textureLod(LightmapSampler, lightmapUv, 0.0).rgb;
}

vec4 helionHitColor(rayQueryEXT query) {
    HelionSurfacePoint point = helionCommittedPoint(query);
    vec3 albedo = textureLod(AtlasSampler, point.uv, 0.0).rgb * point.tint.rgb;
    return vec4(albedo * helionLightmap(point.light), 1.0);
}

#ifdef HELION_RT_ENTITY_TEXTURES
const uint HELION_RT_ENTITY_INSTANCES = 0x02u;
const uint HELION_RT_TRACED_INSTANCES = HELION_RT_SECTION_INSTANCES | HELION_RT_ENTITY_INSTANCES;
const int HELION_RT_HIT_ATTEMPTS = 4;
const int HELION_RT_ENTITY_INSTANCE = 0xFFFFFF;
const uint HELION_RT_ENTITY_RECORD = 2u;
const uint HELION_RT_NO_TEXTURE = 0xFFFFu;
const uint HELION_RT_SHORT_INDICES = 0x10000u;
const uint HELION_RT_CUTOUT_SHIFT = 24u;
const uint HELION_RT_UV_SHIFT = 16u;
const uint HELION_RT_LIGHT_SHIFT = 24u;
const uint HELION_RT_WORD_SHIFT = 2u;
const uint HELION_RT_HIGH_HALF = 2u;
const uint HELION_RT_SHORT_BYTES = 2u;
const uint HELION_RT_WORD_BYTES = 4u;
const uint HELION_RT_TRIANGLE_CORNERS = 3u;
const float HELION_RT_BYTE_SCALE = 1.0 / 255.0;

uint helionEntityWord(uvec2 address, uint byteOffset) {
    return HelionSurface(address).values[byteOffset >> HELION_RT_WORD_SHIFT];
}

uint helionEntityIndex(uvec4 buffers, uvec4 entity, uint index) {
    if ((entity.w & HELION_RT_SHORT_INDICES) == 0u) {
        return helionEntityWord(buffers.zw, entity.x + index * HELION_RT_WORD_BYTES);
    }
    uint byteOffset = entity.x + index * HELION_RT_SHORT_BYTES;
    uint word = helionEntityWord(buffers.zw, byteOffset);
    return (byteOffset & HELION_RT_HIGH_HALF) != 0u ? word >> HELION_RT_HALF_SHIFT : word & HELION_RT_HALF_MASK;
}

vec4 helionEntityHitColor(rayQueryEXT query) {
    uint geometry = uint(rayQueryGetIntersectionGeometryIndexEXT(query, true));
    if (geometry >= uint(Trace.y)) {
        return HELION_RT_MISS;
    }
    uvec4 buffers = Entities[geometry * HELION_RT_ENTITY_RECORD];
    uvec4 entity = Entities[geometry * HELION_RT_ENTITY_RECORD + 1u];
    uint slot = entity.w & HELION_RT_HALF_MASK;
    if (slot == HELION_RT_NO_TEXTURE) {
        return HELION_RT_MISS;
    }
    uint stride = entity.z & HELION_RT_BYTE_MASK;
    uint colorOffset = (entity.z >> HELION_RT_BYTE_SHIFT) & HELION_RT_BYTE_MASK;
    uint uvOffset = (entity.z >> HELION_RT_UV_SHIFT) & HELION_RT_BYTE_MASK;
    uint lightOffset = entity.z >> HELION_RT_LIGHT_SHIFT;
    uint firstIndex = uint(rayQueryGetIntersectionPrimitiveIndexEXT(query, true)) * HELION_RT_TRIANGLE_CORNERS;
    vec2 barycentrics = rayQueryGetIntersectionBarycentricsEXT(query, true);
    vec3 weights = vec3(1.0 - barycentrics.x - barycentrics.y, barycentrics.x, barycentrics.y);
    vec2 uv = vec2(0.0);
    vec4 tint = vec4(0.0);
    vec2 light = vec2(0.0);
    for (uint corner = 0u; corner < HELION_RT_TRIANGLE_CORNERS; corner++) {
        uint vertexStart = (entity.y + helionEntityIndex(buffers, entity, firstIndex + corner)) * stride;
        uvec2 packedUv = uvec2(helionEntityWord(buffers.xy, vertexStart + uvOffset), helionEntityWord(buffers.xy, vertexStart + uvOffset + HELION_RT_WORD_BYTES));
        uint packedLight = helionEntityWord(buffers.xy, vertexStart + lightOffset);
        uv += uintBitsToFloat(packedUv) * weights[corner];
        tint += unpackUnorm4x8(helionEntityWord(buffers.xy, vertexStart + colorOffset)) * weights[corner];
        light += vec2(packedLight & HELION_RT_HALF_MASK, packedLight >> HELION_RT_HALF_SHIFT) * weights[corner];
    }
    vec4 texel = textureLod(EntityTextures[nonuniformEXT(slot)], uv, 0.0);
    if (texel.a < float(entity.w >> HELION_RT_CUTOUT_SHIFT) * HELION_RT_BYTE_SCALE) {
        return HELION_RT_MISS;
    }
    return vec4(texel.rgb * tint.rgb * helionLightmap(light), 1.0);
}
#else
const uint HELION_RT_TRACED_INSTANCES = HELION_RT_SECTION_INSTANCES;
const int HELION_RT_HIT_ATTEMPTS = 1;
#endif

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
    float start = HELION_RT_RAY_START;
    for (int attempt = 0; attempt < HELION_RT_HIT_ATTEMPTS; attempt++) {
        rayQueryEXT query;
        rayQueryInitializeEXT(query, Scene, gl_RayFlagsNoneEXT, HELION_RT_TRACED_INSTANCES, origin, start, direction, Trace.x);
        helionTraceCutout(query);
        if (rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
            break;
        }
#ifdef HELION_RT_ENTITY_TEXTURES
        if (rayQueryGetIntersectionInstanceCustomIndexEXT(query, true) == HELION_RT_ENTITY_INSTANCE) {
            vec4 entity = helionEntityHitColor(query);
            if (entity.a > 0.0) {
                imageStore(Reflection, pixel, entity);
                return;
            }
            start = rayQueryGetIntersectionTEXT(query, true) + HELION_RT_RAY_START;
            continue;
        }
#endif
        imageStore(Reflection, pixel, helionHitColor(query));
        return;
    }
    imageStore(Reflection, pixel, HELION_RT_MISS);
}
