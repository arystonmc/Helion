#version 460
#extension GL_EXT_ray_query : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require

#include <helion:helion_geometry.glsl>
#include <helion:helion_light_level.glsl>
#include <helion:helion_hash.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D Indirect;
layout(set = 0, binding = 1) uniform accelerationStructureEXT Scene;
layout(set = 0, binding = 2) uniform sampler2D DepthSampler;
layout(set = 0, binding = 3) uniform sampler2D GeometryNormalSampler;
layout(set = 0, binding = 4) uniform sampler2D AtlasSampler;
layout(set = 0, binding = 5, std430) readonly buffer HelionSurfaceTable {
    uvec4 Sections[];
};
layout(set = 0, binding = 6) uniform sampler2D MaterialSampler;
layout(set = 0, binding = 7) uniform sampler2D SkyLightSampler;

layout(push_constant) uniform HelionRayIndirect {
    mat4 InverseViewProjection;
    vec4 CameraToAnchor;
    vec4 Sun;
    vec4 Moon;
    vec4 SkyFull;
};

#include <helion:helion_rt_surface.glsl>

const float HELION_PT_SKY_DEPTH = 0.0;
const float HELION_PT_NORMAL_OFFSET = 0.03;
const float HELION_PT_DISTANCE_OFFSET = 0.002;
const float HELION_PT_RAY_START = 1.0e-3;
const float HELION_PT_PLANT_START = 0.3;
const float HELION_PT_HIT_OFFSET = 0.01;
const float HELION_PT_SECTION_SIZE = 16.0;
const float HELION_PT_FACE_TOLERANCE = 2.0e-3;
const float HELION_PT_LIGHT_COORDINATE = 240.0;
const float HELION_PT_TAU = 6.28318531;
const float HELION_PT_UNLIT = 1.0e-6;
const float HELION_PT_GROUND_SHARE = 0.3;
const float HELION_PT_EMISSION = 1.0;
const float HELION_PT_SKY_GATE = 0.2;
const float HELION_PT_GAMMA = 2.2;
const uint HELION_PT_SECTION_INSTANCES = 0x01u;
const uint HELION_PT_FRAME_SEED = 1013u;
const ivec2 HELION_PT_SUN_TEXEL = ivec2(0, 0);
const ivec2 HELION_PT_MOON_TEXEL = ivec2(1, 0);
const ivec2 HELION_PT_DOME_TEXEL = ivec2(2, 0);
const vec3 HELION_PT_LUMA = vec3(0.2126, 0.7152, 0.0722);
const vec3 HELION_PT_BLOCK_COLOR = vec3(1.0, 0.86, 0.68);
const vec4 HELION_PT_NOTHING = vec4(0.0, 0.0, 0.0, 0.0);

struct HelionSkySplit {
    vec3 sun;
    vec3 moon;
    vec3 dome;
    float scale;
};

float helionUnitHash(uint seed) {
    return float(helionPcg(seed)) / HELION_HASH_RANGE;
}

vec3 helionSrgbToLinear(vec3 color) {
    return pow(max(color, vec3(0.0)), vec3(2.2));
}

HelionSkySplit helionSkySplit() {
    vec3 sun = texelFetch(SkyLightSampler, HELION_PT_SUN_TEXEL, 0).rgb;
    vec3 moon = texelFetch(SkyLightSampler, HELION_PT_MOON_TEXEL, 0).rgb;
    vec3 dome = texelFetch(SkyLightSampler, HELION_PT_DOME_TEXEL, 0).rgb;
    vec3 ground = sun * max(Sun.y, 0.0) + moon * max(Moon.y, 0.0) + dome;
    float scale = max(dot(ground, HELION_PT_LUMA), HELION_PT_UNLIT);
    float full = pow(dot(SkyFull.rgb, HELION_PT_LUMA), HELION_PT_GAMMA);
    return HelionSkySplit(sun * (full / scale), moon * (full / scale), dome * (full / scale), scale);
}

vec3 helionCosineDirection(vec3 normal, float first, float second) {
    float radius = sqrt(first);
    float angle = second * HELION_PT_TAU;
    vec3 tangent = normalize(cross(normal, abs(normal.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 bitangent = cross(normal, tangent);
    return normalize(tangent * (radius * cos(angle)) + bitangent * (radius * sin(angle)) + normal * sqrt(max(1.0 - first, 0.0)));
}

vec3 helionHitNormal(vec3 point, vec3 direction) {
    vec3 offset = abs(point - round(point));
    if (offset.x < HELION_PT_FACE_TOLERANCE && offset.x <= offset.y && offset.x <= offset.z) {
        return vec3(-sign(direction.x), 0.0, 0.0);
    }
    if (offset.y < HELION_PT_FACE_TOLERANCE && offset.y <= offset.z) {
        return vec3(0.0, -sign(direction.y), 0.0);
    }
    if (offset.z < HELION_PT_FACE_TOLERANCE) {
        return vec3(0.0, 0.0, -sign(direction.z));
    }
    return -direction;
}

bool helionSunVisible(vec3 origin, vec3 toLight) {
    rayQueryEXT query;
    rayQueryInitializeEXT(query, Scene, gl_RayFlagsTerminateOnFirstHitEXT, HELION_PT_SECTION_INSTANCES, origin, HELION_PT_RAY_START, toLight, Moon.w);
    helionTraceCutout(query);
    return rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT;
}

vec3 helionMissRadiance(vec3 direction, HelionSkySplit sky) {
    vec3 dome = sky.dome;
    return direction.y > 0.0 ? dome : dome * HELION_PT_GROUND_SHARE;
}

vec3 helionHitRadiance(rayQueryEXT query, vec3 origin, vec3 direction, HelionSkySplit sky) {
    HelionSurfacePoint point = helionCommittedPoint(query);
    vec4 texel = textureLod(AtlasSampler, point.uv, 0.0);
    vec3 albedo = helionSrgbToLinear(texel.rgb * point.tint.rgb);
    if (helionIsEmissiveCell(helionMaterialCell(textureLod(MaterialSampler, point.uv, 0.0).r))) {
        return albedo * HELION_PT_EMISSION;
    }
    float distanceToHit = rayQueryGetIntersectionTEXT(query, true);
    vec3 hit = origin + direction * distanceToHit;
    vec3 normal = helionHitNormal(hit, direction);
    float blockLevel = point.light.x / HELION_PT_LIGHT_COORDINATE;
    float skyLevel = point.light.y / HELION_PT_LIGHT_COORDINATE;
    vec3 light = HELION_PT_BLOCK_COLOR * pow(helionLevelBrightness(clamp(blockLevel, 0.0, 1.0)) * SkyFull.w, HELION_PT_GAMMA);
    light += sky.dome * pow(helionLevelBrightness(clamp(skyLevel, 0.0, 1.0)), HELION_PT_GAMMA);
    if (skyLevel > HELION_PT_SKY_GATE) {
        vec3 start = hit + normal * HELION_PT_HIT_OFFSET;
        if (Sun.y > 0.0 && dot(normal, Sun.xyz) > 0.0 && helionSunVisible(start, Sun.xyz)) {
            light += sky.sun * dot(normal, Sun.xyz);
        } else if (Moon.y > 0.0 && dot(normal, Moon.xyz) > 0.0 && helionSunVisible(start, Moon.xyz)) {
            light += sky.moon * dot(normal, Moon.xyz);
        }
    }
    return albedo * light;
}

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(Indirect);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    vec2 screen = (vec2(pixel) + 0.5) / vec2(size);
    ivec2 fullSize = textureSize(DepthSampler, 0);
    ivec2 fullPixel = min(ivec2(screen * vec2(fullSize)), fullSize - 1);
    float depth = texelFetch(DepthSampler, fullPixel, 0).r;
    vec4 encodedNormal = texelFetch(GeometryNormalSampler, fullPixel, 0);
    if (depth <= HELION_PT_SKY_DEPTH || encodedNormal.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        imageStore(Indirect, pixel, HELION_PT_NOTHING);
        return;
    }
    vec3 normal = normalize(encodedNormal.rgb * 2.0 - 1.0);
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 relative = world.xyz / world.w;
    vec3 origin = relative + CameraToAnchor.xyz + normal * (HELION_PT_NORMAL_OFFSET + length(relative) * HELION_PT_DISTANCE_OFFSET);
    float start = helionIsCrossPlane(normal) ? HELION_PT_PLANT_START : HELION_PT_RAY_START;
    uint seed = helionPcg(uint(pixel.x) + helionPcg(uint(pixel.y) + uint(Sun.w) * HELION_PT_FRAME_SEED));
    vec3 direction = helionCosineDirection(normal, helionUnitHash(seed), helionUnitHash(seed + 1u));
    HelionSkySplit sky = helionSkySplit();
    rayQueryEXT query;
    rayQueryInitializeEXT(query, Scene, gl_RayFlagsNoneEXT, HELION_PT_SECTION_INSTANCES, origin, start, direction, CameraToAnchor.w);
    helionTraceCutout(query);
    vec3 radiance;
    float hitDistance = CameraToAnchor.w;
    if (rayQueryGetIntersectionTypeEXT(query, true) == gl_RayQueryCommittedIntersectionNoneEXT) {
        radiance = helionMissRadiance(direction, sky);
    } else {
        radiance = helionHitRadiance(query, origin, direction, sky);
        hitDistance = rayQueryGetIntersectionTEXT(query, true);
    }
    imageStore(Indirect, pixel, vec4(radiance, hitDistance));
}
