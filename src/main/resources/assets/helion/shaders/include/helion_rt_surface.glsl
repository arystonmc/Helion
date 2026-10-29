#ifndef HELION_RT_SURFACE_GLSL
#define HELION_RT_SURFACE_GLSL

layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer HelionSurface {
    uint values[];
};

struct HelionSurfacePoint {
    vec2 uv;
    vec4 tint;
    vec2 light;
    vec3 normal;
};

const uint HELION_RT_CUTOUT_GEOMETRY = 1u;
const uint HELION_RT_TRANSLUCENT_GEOMETRY = 2u;
const uint HELION_RT_INTS_PER_VERTEX = 4u;
const uint HELION_RT_NORMAL_INT = 3u;
const uint HELION_RT_VERTICES_PER_QUAD = 4u;
const uint HELION_RT_HALF_MASK = 0xFFFFu;
const uint HELION_RT_HALF_SHIFT = 16u;
const uint HELION_RT_BYTE_MASK = 0xFFu;
const uint HELION_RT_BYTE_SHIFT = 8u;
const float HELION_RT_UV_SCALE = 1.0 / 65536.0;
const float HELION_RT_CUTOUT_ALPHA = 0.5;

vec3 helionUnpackNormal(uint packed) {
    vec2 folded = unpackSnorm2x16(packed);
    vec3 normal = vec3(folded, 1.0 - abs(folded.x) - abs(folded.y));
    if (normal.z < 0.0) {
        normal.xy = (1.0 - abs(normal.yx)) * vec2(normal.x >= 0.0 ? 1.0 : -1.0, normal.y >= 0.0 ? 1.0 : -1.0);
    }
    float length2 = dot(normal, normal);
    return length2 > 0.0 ? normal * inversesqrt(length2) : vec3(0.0, 1.0, 0.0);
}

HelionSurfacePoint helionSurfacePoint(uvec4 section, uint geometry, uint primitive, vec2 barycentrics) {
    HelionSurface surface = HelionSurface(section.xy);
    vec3 weights = vec3(1.0 - barycentrics.x - barycentrics.y, barycentrics.x, barycentrics.y);
    uvec3 corners = (primitive & 1u) == 0u ? uvec3(0u, 1u, 2u) : uvec3(2u, 3u, 0u);
    uint quad = (primitive >> 1u) + (geometry == HELION_RT_CUTOUT_GEOMETRY ? section.z : geometry == HELION_RT_TRANSLUCENT_GEOMETRY ? section.w : 0u);
    uvec3 vertices = quad * HELION_RT_VERTICES_PER_QUAD + corners;
    HelionSurfacePoint point = HelionSurfacePoint(vec2(0.0), vec4(0.0), vec2(0.0), helionUnpackNormal(surface.values[vertices.x * HELION_RT_INTS_PER_VERTEX + HELION_RT_NORMAL_INT]));
    for (int corner = 0; corner < 3; corner++) {
        uint base = vertices[corner] * HELION_RT_INTS_PER_VERTEX;
        uint packedUv = surface.values[base];
        uint packedLight = surface.values[base + 2u];
        point.uv += vec2(packedUv & HELION_RT_HALF_MASK, packedUv >> HELION_RT_HALF_SHIFT) * HELION_RT_UV_SCALE * weights[corner];
        point.tint += unpackUnorm4x8(surface.values[base + 1u]) * weights[corner];
        point.light += vec2(packedLight & HELION_RT_BYTE_MASK, (packedLight >> HELION_RT_BYTE_SHIFT) & HELION_RT_BYTE_MASK) * weights[corner];
    }
    return point;
}

HelionSurfacePoint helionCommittedPoint(rayQueryEXT query) {
    return helionSurfacePoint(
        Sections[rayQueryGetIntersectionInstanceCustomIndexEXT(query, true)],
        uint(rayQueryGetIntersectionGeometryIndexEXT(query, true)),
        uint(rayQueryGetIntersectionPrimitiveIndexEXT(query, true)),
        rayQueryGetIntersectionBarycentricsEXT(query, true)
    );
}

HelionSurfacePoint helionCandidatePoint(rayQueryEXT query) {
    return helionSurfacePoint(
        Sections[rayQueryGetIntersectionInstanceCustomIndexEXT(query, false)],
        uint(rayQueryGetIntersectionGeometryIndexEXT(query, false)),
        uint(rayQueryGetIntersectionPrimitiveIndexEXT(query, false)),
        rayQueryGetIntersectionBarycentricsEXT(query, false)
    );
}

void helionTraceCutout(rayQueryEXT query) {
    while (rayQueryProceedEXT(query)) {
        if (rayQueryGetIntersectionTypeEXT(query, false) == gl_RayQueryCandidateIntersectionTriangleEXT
            && uint(rayQueryGetIntersectionGeometryIndexEXT(query, false)) != HELION_RT_TRANSLUCENT_GEOMETRY
            && textureLod(AtlasSampler, helionCandidatePoint(query).uv, 0.0).a >= HELION_RT_CUTOUT_ALPHA) {
            rayQueryConfirmIntersectionEXT(query);
        }
    }
}

#endif
