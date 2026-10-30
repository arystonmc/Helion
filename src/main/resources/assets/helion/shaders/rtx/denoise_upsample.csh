#version 460

#include <helion:helion_path_tracing.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D Irradiance;
layout(set = 0, binding = 1, rgba16f) uniform writeonly image2D Specular;
layout(set = 0, binding = 2) uniform sampler2D TracedSampler;
layout(set = 0, binding = 3) uniform sampler2D TracedSpecularSampler;
layout(set = 0, binding = 4) uniform sampler2D GuideSampler;
layout(set = 0, binding = 5) uniform sampler2D SceneDepthSampler;
layout(set = 0, binding = 6) uniform sampler2D GeometryNormalSampler;
layout(set = 0, binding = 7) uniform sampler2D GeometryLightSampler;

layout(push_constant) uniform HelionDenoiseUpsample {
    mat4 InverseViewProjection;
};

const float HELION_SKY_DEPTH = 0.0;
const int HELION_RADIUS = 1;
const float HELION_NORMAL_POWER = 32.0;
const float HELION_PLANE_TOLERANCE = 0.02;
const float HELION_PLANE_DISTANCE_SHARE = 0.004;
const float HELION_MIN_WEIGHT = 1.0e-4;
const vec4 HELION_EMPTY = vec4(0.0);

vec3 helionRelative(ivec2 pixel, ivec2 full) {
    vec4 world = InverseViewProjection * vec4(helionTraceScreen(pixel, full) * 2.0 - 1.0, texelFetch(SceneDepthSampler, pixel, 0).r, 1.0);
    return world.xyz / world.w;
}

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 full = imageSize(Irradiance);
    if (any(greaterThanEqual(pixel, full))) {
        return;
    }
    float material = texelFetch(GeometryLightSampler, pixel, 0).a;
    if (material < HELION_GEOMETRY_PRESENT_THRESHOLD || texelFetch(SceneDepthSampler, pixel, 0).r <= HELION_SKY_DEPTH) {
        return;
    }
    if (helionIsEmissiveGeometry(material)) {
        imageStore(Irradiance, pixel, vec4(vec3(1.0), HELION_PATH_EMISSIVE));
        imageStore(Specular, pixel, HELION_EMPTY);
        return;
    }
    ivec2 traced = textureSize(TracedSampler, 0);
    ivec2 divisor = (full + traced - 1) / traced;
    vec3 relative = helionRelative(pixel, full);
    vec3 normal = normalize(texelFetch(GeometryNormalSampler, pixel, 0).rgb * 2.0 - 1.0);
    float tolerance = HELION_PLANE_TOLERANCE + HELION_PLANE_DISTANCE_SHARE * length(relative);
    ivec2 home = pixel / divisor;
    vec4 irradiance = vec4(0.0);
    vec4 specular = vec4(0.0);
    float weights = 0.0;
    float bestMatch = -1.0;
    ivec2 best = clamp(home, ivec2(0), traced - 1);
    for (int y = -HELION_RADIUS; y <= HELION_RADIUS; y++) {
        for (int x = -HELION_RADIUS; x <= HELION_RADIUS; x++) {
            ivec2 tap = home + ivec2(x, y);
            if (any(lessThan(tap, ivec2(0))) || any(greaterThanEqual(tap, traced))) {
                continue;
            }
            vec4 guide = texelFetch(GuideSampler, tap, 0);
            if (guide.w <= 0.0) {
                continue;
            }
            ivec2 source = helionTraceSource(tap, traced, full);
            float plane = abs(dot(helionRelative(source, full) - relative, normal));
            float match = pow(max(dot(guide.xyz, normal), 0.0), HELION_NORMAL_POWER) * exp(-plane / tolerance);
            if (match > bestMatch) {
                bestMatch = match;
                best = tap;
            }
            vec2 offset = abs(vec2(pixel - source)) / vec2(divisor);
            float weight = max(1.0 - offset.x, 0.0) * max(1.0 - offset.y, 0.0) * match;
            irradiance += texelFetch(TracedSampler, tap, 0) * weight;
            specular += texelFetch(TracedSpecularSampler, tap, 0) * weight;
            weights += weight;
        }
    }
    if (weights > HELION_MIN_WEIGHT) {
        imageStore(Irradiance, pixel, irradiance / weights);
        imageStore(Specular, pixel, specular / weights);
        return;
    }
    imageStore(Irradiance, pixel, texelFetch(TracedSampler, best, 0));
    imageStore(Specular, pixel, texelFetch(TracedSpecularSampler, best, 0));
}
