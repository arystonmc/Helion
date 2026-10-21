#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_clouds.glsl>

uniform sampler2D CloudColorSampler;
uniform sampler2D CloudDistanceSampler;
uniform sampler2D HistoryColorSampler;
uniform sampler2D HistoryDistanceSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 resolvedColor;
layout(location = 1) out vec4 resolvedDistance;

const float HELION_CURRENT_WEIGHT = 0.12;
const float HELION_CLAMP_MARGIN = 0.25;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 last = ivec2(CloudTarget.xy) - 1;
    vec4 current = texelFetch(CloudColorSampler, pixel, 0);
    float currentDistance = texelFetch(CloudDistanceSampler, pixel, 0).r;
    vec4 low = current;
    vec4 high = current;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec4 neighbor = texelFetch(CloudColorSampler, clamp(pixel + ivec2(x, y), ivec2(0), last), 0);
            low = min(low, neighbor);
            high = max(high, neighbor);
        }
    }
    if (CameraMotion.w < 0.5) {
        resolvedColor = current;
        resolvedDistance = vec4(currentDistance, 0.0, 0.0, 1.0);
        return;
    }
    vec3 relative = helionCloudViewRay(texCoord) * currentDistance + CameraMotion.xyz;
    vec4 clip = PreviousViewProjection * vec4(relative, 1.0);
    vec2 previousUv = clip.xy / clip.w * 0.5 + 0.5;
    if (clip.w <= 0.0 || any(lessThan(previousUv, vec2(0.0))) || any(greaterThan(previousUv, vec2(1.0)))) {
        resolvedColor = current;
        resolvedDistance = vec4(currentDistance, 0.0, 0.0, 1.0);
        return;
    }
    vec4 history = texture(HistoryColorSampler, previousUv);
    float historyDistance = texture(HistoryDistanceSampler, previousUv).r;
    vec4 margin = (high - low) * HELION_CLAMP_MARGIN;
    history = clamp(history, low - margin, high + margin);
    resolvedColor = mix(history, current, HELION_CURRENT_WEIGHT);
    resolvedDistance = vec4(mix(historyDistance, currentDistance, HELION_CURRENT_WEIGHT), 0.0, 0.0, 1.0);
}
