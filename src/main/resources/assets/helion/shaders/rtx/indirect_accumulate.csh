#version 460

#include <helion:helion_geometry.glsl>

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(set = 0, binding = 0, rgba16f) uniform writeonly image2D History;
layout(set = 0, binding = 1, rgba16f) uniform writeonly image2D Guide;
layout(set = 0, binding = 2) uniform sampler2D RawSampler;
layout(set = 0, binding = 3) uniform sampler2D PreviousHistorySampler;
layout(set = 0, binding = 4) uniform sampler2D PreviousGuideSampler;
layout(set = 0, binding = 5) uniform sampler2D DepthSampler;
layout(set = 0, binding = 6) uniform sampler2D GeometryNormalSampler;

layout(push_constant) uniform HelionIndirectAccumulate {
    mat4 InverseViewProjection;
    mat4 PreviousViewProjection;
};

const float HELION_PT_SKY_DEPTH = 0.0;
const float HELION_PT_MAX_SAMPLES = 24.0;
const float HELION_PT_DEPTH_TOLERANCE = 0.08;
const float HELION_PT_NORMAL_TOLERANCE = 0.9;
const float HELION_PT_EDGE = 1.0e-3;
const vec4 HELION_PT_EMPTY = vec4(0.0);

void main() {
    ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(History);
    if (any(greaterThanEqual(pixel, size))) {
        return;
    }
    vec2 screen = (vec2(pixel) + 0.5) / vec2(size);
    ivec2 fullSize = textureSize(DepthSampler, 0);
    ivec2 fullPixel = min(ivec2(screen * vec2(fullSize)), fullSize - 1);
    float depth = texelFetch(DepthSampler, fullPixel, 0).r;
    vec4 encodedNormal = texelFetch(GeometryNormalSampler, fullPixel, 0);
    if (depth <= HELION_PT_SKY_DEPTH || encodedNormal.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        imageStore(History, pixel, HELION_PT_EMPTY);
        imageStore(Guide, pixel, HELION_PT_EMPTY);
        return;
    }
    vec3 normal = normalize(encodedNormal.rgb * 2.0 - 1.0);
    vec4 world = InverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 relative = world.xyz / world.w;
    float distanceToCamera = length(relative);
    vec3 raw = texelFetch(RawSampler, pixel, 0).rgb;
    vec4 previousClip = PreviousViewProjection * vec4(relative, 1.0);
    vec4 accumulated = vec4(raw, 1.0);
    if (previousClip.w > 0.0) {
        vec2 previousScreen = previousClip.xy / previousClip.w * 0.5 + 0.5;
        if (all(greaterThan(previousScreen, vec2(HELION_PT_EDGE))) && all(lessThan(previousScreen, vec2(1.0 - HELION_PT_EDGE)))) {
            vec4 previousGuide = textureLod(PreviousGuideSampler, previousScreen, 0.0);
            bool sameDepth = abs(previousGuide.w - distanceToCamera) < HELION_PT_DEPTH_TOLERANCE * distanceToCamera;
            bool sameNormal = dot(previousGuide.xyz, normal) > HELION_PT_NORMAL_TOLERANCE;
            if (sameDepth && sameNormal) {
                vec4 history = textureLod(PreviousHistorySampler, previousScreen, 0.0);
                float samples = min(history.a + 1.0, HELION_PT_MAX_SAMPLES);
                accumulated = vec4(mix(history.rgb, raw, 1.0 / samples), samples);
            }
        }
    }
    imageStore(History, pixel, accumulated);
    imageStore(Guide, pixel, vec4(normal, distanceToCamera));
}
