#version 460

#include <helion:helion_path_tracing.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D Accumulated;
layout(set = 0, binding = 1) uniform sampler2D SpecularSampler;
layout(set = 0, binding = 2) uniform sampler2D HistorySampler;
layout(set = 0, binding = 3) uniform sampler2D PreviousGuideSampler;
layout(set = 0, binding = 4) uniform sampler2D SceneDepthSampler;
layout(set = 0, binding = 5) uniform sampler2D GeometryNormalSampler;

layout(push_constant) uniform HelionSpecularAccumulate {
    mat4 InverseViewProjection;
    mat4 PreviousViewProjection;
};

const float HELION_MAX_SAMPLES = 16.0;
const int HELION_NEIGHBORHOOD_RADIUS = 1;
const float HELION_CLAMP_DEVIATIONS = 1.5;
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

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(Accumulated);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    vec4 current = texelFetch(SpecularSampler, pixel, 0);
    if (current.a <= 0.0) {
        imageStore(Accumulated, pixel, HELION_EMPTY);
        return;
    }
    vec3 mean = vec3(0.0);
    vec3 square = vec3(0.0);
    float taps = 0.0;
    for (int y = -HELION_NEIGHBORHOOD_RADIUS; y <= HELION_NEIGHBORHOOD_RADIUS; y++) {
        for (int x = -HELION_NEIGHBORHOOD_RADIUS; x <= HELION_NEIGHBORHOOD_RADIUS; x++) {
            vec4 tap = texelFetch(SpecularSampler, clamp(pixel + ivec2(x, y), ivec2(0), size - 1), 0);
            if (tap.a <= 0.0) {
                continue;
            }
            mean += tap.rgb;
            square += tap.rgb * tap.rgb;
            taps += 1.0;
        }
    }
    mean /= taps;
    vec3 deviation = sqrt(max(square / taps - mean * mean, vec3(0.0)));
    vec2 screen = (vec2(pixel) + 0.5) / vec2(size);
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, texelFetch(SceneDepthSampler, pixel, 0).r, 1.0);
    vec3 relative = world.xyz / world.w;
    vec3 normal = normalize(texelFetch(GeometryNormalSampler, pixel, 0).rgb * 2.0 - 1.0);
    ivec2 tap;
    vec3 color = mean;
    float count = 1.0;
    if (helionReprojected(relative, normal, length(relative), size, tap)) {
        vec4 history = texelFetch(HistorySampler, tap, 0);
        if (history.a > 0.0) {
            count = min(history.a + 1.0, HELION_MAX_SAMPLES);
            vec3 kept = clamp(history.rgb, mean - deviation * HELION_CLAMP_DEVIATIONS, mean + deviation * HELION_CLAMP_DEVIATIONS);
            color = mix(kept, current.rgb, 1.0 / count);
        }
    }
    imageStore(Accumulated, pixel, vec4(color, count));
}
