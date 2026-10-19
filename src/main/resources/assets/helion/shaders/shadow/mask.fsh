#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_geometry.glsl>
#include <helion:helion_shadow.glsl>

uniform sampler2D DepthSampler;
uniform sampler2D GeometryNormalSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_SKY_DEPTH = 0.0;
const float HELION_NORMAL_OFFSET_TEXELS = 1.5;
const float HELION_LIGHT_OFFSET_TEXELS = 1.0;
const float HELION_CASCADE_BORDER_TEXELS = 2.0;
const float HELION_FADE_START = 0.85;
const float HELION_FULLY_LIT = 1.0;

vec2 helionShadowVisibility(bool previous, vec3 position, vec3 normal) {
    float border = 1.0 - 2.0 * HELION_CASCADE_BORDER_TEXELS / CascadeResolution;
    vec3 lightDirection = previous ? PreviousLightDirection : LightDirection;
    for (int cascade = 0; cascade < HELION_SHADOW_CASCADES; cascade++) {
        float texelSize = previous ? PreviousTexelSizes[cascade] : CascadeTexelSizes[cascade];
        mat4 cascadeMatrix = previous ? PreviousCascadeMatrices[cascade] : CascadeMatrices[cascade];
        vec3 offset = position + normal * texelSize * HELION_NORMAL_OFFSET_TEXELS + lightDirection * texelSize * HELION_LIGHT_OFFSET_TEXELS;
        vec3 clip = (cascadeMatrix * vec4(offset, 1.0)).xyz;
        if (abs(clip.x) <= border && abs(clip.y) <= border && clip.z <= 1.0) {
            float entities = !previous && cascade < HELION_ENTITY_CASCADES ? helionEntityVisibility(cascade, clip) : HELION_FULLY_LIT;
            return vec2(helionCascadeVisibility(previous, cascade, clip), entities);
        }
    }
    return vec2(HELION_FULLY_LIT);
}

float helionBlendedVisibility(vec3 position, vec3 normal) {
    vec2 current = helionShadowVisibility(false, position, normal);
    float terrain = current.x;
    if (PreviousWeight > 0.0) {
        terrain = mix(terrain, helionShadowVisibility(true, position, normal).x, PreviousWeight);
    }
    return min(terrain, current.y);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(DepthSampler, pixel, 0).r;
    vec4 encodedNormal = texelFetch(GeometryNormalSampler, pixel, 0);
    if (depth <= HELION_SKY_DEPTH || encodedNormal.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        fragColor = vec4(HELION_FULLY_LIT);
        return;
    }
    vec3 normal = normalize(encodedNormal.rgb * 2.0 - 1.0);
    if (dot(normal, LightDirection) <= 0.0) {
        fragColor = vec4(HELION_FULLY_LIT);
        return;
    }
    vec4 world = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth, 1.0);
    vec3 position = world.xyz / world.w;
    float distanceToCamera = length(position);
    if (distanceToCamera >= ShadowDistance) {
        fragColor = vec4(HELION_FULLY_LIT);
        return;
    }
    float fade = smoothstep(ShadowDistance * HELION_FADE_START, ShadowDistance, distanceToCamera);
    fragColor = vec4(mix(helionBlendedVisibility(position, normal), HELION_FULLY_LIT, fade));
}
