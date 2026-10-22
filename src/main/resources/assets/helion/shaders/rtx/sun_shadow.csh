#version 460
#extension GL_EXT_ray_query : require

#include <helion:helion_geometry.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba8) uniform writeonly image2D ShadowMask;
layout(set = 0, binding = 1) uniform accelerationStructureEXT Scene;
layout(set = 0, binding = 2) uniform sampler2D DepthSampler;
layout(set = 0, binding = 3) uniform sampler2D GeometryNormalSampler;

layout(push_constant) uniform HelionRaySunShadow {
    mat4 InverseViewProjection;
    vec4 CameraToAnchor;
    vec4 LightDirection;
};

const float HELION_RT_SKY_DEPTH = 0.0;
const float HELION_RT_NORMAL_OFFSET = 0.02;
const float HELION_RT_RAY_START = 1.0e-3;
const float HELION_RT_SECTION_SIZE = 16.0;
const uint HELION_RT_ALL_INSTANCES = 0xFFu;
const vec4 HELION_RT_UNTRACED = vec4(1.0, 0.0, 0.0, 1.0);

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
    if (depth <= HELION_RT_SKY_DEPTH || encodedNormal.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        imageStore(ShadowMask, pixel, HELION_RT_UNTRACED);
        return;
    }
    vec3 normal = normalize(encodedNormal.rgb * 2.0 - 1.0);
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 origin = world.xyz / world.w + CameraToAnchor.xyz + normal * HELION_RT_NORMAL_OFFSET;
    float reach = CameraToAnchor.w;
    if (any(lessThan(origin, vec3(-reach))) || any(greaterThanEqual(origin, vec3(reach + HELION_RT_SECTION_SIZE)))) {
        imageStore(ShadowMask, pixel, HELION_RT_UNTRACED);
        return;
    }
    rayQueryEXT query;
    rayQueryInitializeEXT(
        query, Scene, gl_RayFlagsTerminateOnFirstHitEXT | gl_RayFlagsOpaqueEXT, HELION_RT_ALL_INSTANCES,
        origin, HELION_RT_RAY_START, normalize(LightDirection.xyz), LightDirection.w
    );
    while (rayQueryProceedEXT(query)) {
    }
    bool lit = rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT;
    imageStore(ShadowMask, pixel, vec4(lit ? 1.0 : 0.0, 0.0, 1.0, 1.0));
}
