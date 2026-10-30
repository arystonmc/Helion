#version 460

#include <helion:helion_path_tracing.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D Accumulated;
layout(set = 0, binding = 1, rgba16f) uniform writeonly image2D Moments;
layout(set = 0, binding = 2, rgba16f) uniform writeonly image2D Guide;
layout(set = 0, binding = 3) uniform sampler2D TracedSampler;
layout(set = 0, binding = 4) uniform sampler2D HistorySampler;
layout(set = 0, binding = 5) uniform sampler2D PreviousMomentsSampler;
layout(set = 0, binding = 6) uniform sampler2D PreviousGuideSampler;
layout(set = 0, binding = 7) uniform sampler2D SceneDepthSampler;
layout(set = 0, binding = 8) uniform sampler2D GeometryNormalSampler;

layout(push_constant) uniform HelionDenoiseAccumulate {
    mat4 InverseViewProjection;
    mat4 PreviousViewProjection;
};

const float HELION_MAX_SAMPLES = 24.0;
const float HELION_MOMENT_SHARE = 0.2;
const float HELION_SPATIAL_SAMPLES = 4.0;
const int HELION_SPATIAL_RADIUS = 1;
const float HELION_DISTANCE_TOLERANCE = 0.08;
const float HELION_NORMAL_TOLERANCE = 0.9;
const vec4 HELION_EMPTY = vec4(0.0);

bool helionReprojected(vec3 relative, vec3 normal, float distance, ivec2 size, out ivec2 tap) {
    vec4 previousClip = PreviousViewProjection * vec4(relative, 1.0);
    tap = ivec2(0);
    if (previousClip.w <= 0.0) {
        return false;
    }
    vec2 previousScreen = previousClip.xy / previousClip.w * 0.5 + 0.5;
    if (any(lessThan(previousScreen, vec2(0.0))) || any(greaterThanEqual(previousScreen, vec2(1.0)))) {
        return false;
    }
    tap = min(ivec2(previousScreen * vec2(size)), size - 1);
    vec4 previous = texelFetch(PreviousGuideSampler, tap, 0);
    return previous.w > 0.0 && abs(previous.w - distance) < HELION_DISTANCE_TOLERANCE * distance && dot(previous.xyz, normal) > HELION_NORMAL_TOLERANCE;
}

float helionSpatialVariance(ivec2 pixel, ivec2 size) {
    vec2 moments = vec2(0.0);
    float count = 0.0;
    for (int y = -HELION_SPATIAL_RADIUS; y <= HELION_SPATIAL_RADIUS; y++) {
        for (int x = -HELION_SPATIAL_RADIUS; x <= HELION_SPATIAL_RADIUS; x++) {
            vec4 tap = texelFetch(TracedSampler, clamp(pixel + ivec2(x, y), ivec2(0), size - 1), 0);
            if (tap.a < HELION_PATH_LIT * 0.5 || helionIsEmissiveSignal(tap.a)) {
                continue;
            }
            float luminance = helionLuminance(tap.rgb);
            moments += vec2(luminance, luminance * luminance);
            count += 1.0;
        }
    }
    moments /= max(count, 1.0);
    return max(moments.y - moments.x * moments.x, 0.0);
}

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(Accumulated);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    vec4 traced = texelFetch(TracedSampler, pixel, 0);
    if (traced.a < HELION_PATH_LIT * 0.5) {
        imageStore(Guide, pixel, HELION_EMPTY);
        return;
    }
    ivec2 full = textureSize(SceneDepthSampler, 0);
    ivec2 source = helionTraceSource(pixel, size, full);
    vec4 world = InverseViewProjection * vec4(helionTraceScreen(source, full) * 2.0 - 1.0, texelFetch(SceneDepthSampler, source, 0).r, 1.0);
    vec3 relative = world.xyz / world.w;
    float distance = length(relative);
    vec3 normal = normalize(texelFetch(GeometryNormalSampler, source, 0).rgb * 2.0 - 1.0);
    if (helionIsEmissiveSignal(traced.a)) {
        imageStore(Accumulated, pixel, vec4(traced.rgb, 0.0));
        imageStore(Moments, pixel, HELION_EMPTY);
        imageStore(Guide, pixel, vec4(normal, -distance));
        return;
    }
    float luminance = helionLuminance(traced.rgb);
    vec2 sampleMoments = vec2(luminance, luminance * luminance);
    ivec2 tap;
    vec3 color = traced.rgb;
    vec2 moments = sampleMoments;
    float count = 1.0;
    if (helionReprojected(relative, normal, distance, size, tap)) {
        vec4 previous = texelFetch(PreviousMomentsSampler, tap, 0);
        count = min(previous.b + 1.0, HELION_MAX_SAMPLES);
        color = mix(texelFetch(HistorySampler, tap, 0).rgb, traced.rgb, 1.0 / count);
        moments = mix(previous.rg, sampleMoments, max(1.0 / count, HELION_MOMENT_SHARE));
    }
    float variance = count < HELION_SPATIAL_SAMPLES ? helionSpatialVariance(pixel, size) : max(moments.y - moments.x * moments.x, 0.0);
    imageStore(Accumulated, pixel, vec4(color, variance));
    imageStore(Moments, pixel, vec4(moments, count, 0.0));
    imageStore(Guide, pixel, vec4(normal, distance));
}
