#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_water.glsl>
#include <helion:helion_geometry.glsl>

uniform sampler2D WaterSurfaceSampler;
uniform sampler2D WaterDepthSampler;
uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D GeometryNormalSampler;
uniform sampler2D GeometryLightSampler;

layout(std140) uniform HelionScreenReflection {
    mat4 ReflectionViewProjection;
    mat4 ReflectionInverseViewProjection;
    vec4 ReflectionFlags;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 polishedColor;

const int HELION_MARCH_STEPS = 48;
const int HELION_REFINE_STEPS = 5;
const float HELION_FIRST_STEP = 0.2;
const float HELION_STEP_GROWTH = 1.1;
const float HELION_SURFACE_LIFT = 0.05;
const float HELION_THICKNESS_SHARE = 0.15;
const float HELION_MIN_THICKNESS = 0.5;
const float HELION_EDGE_FADE = 0.06;
const float HELION_MIN_FACING = 0.05;
const float HELION_SKY_DEPTH = 0.0;
const float HELION_MIN_CLIP_W = 1.0e-4;
const float HELION_SKY_BEHIND = -1.0e9;
const float HELION_SKY_MARK = -1.0e8;
const float HELION_ADDITIVE = 0.0;
const float HELION_FLAG_SET = 0.5;
const float HELION_POLISHED_F0 = 0.15;
const float HELION_POLISHED_STRENGTH = 0.4;
const float HELION_POLISHED_REACH = 24.0;
const float HELION_FRESNEL_POWER = 5.0;

struct HelionScreenPoint {
    bool valid;
    vec2 uv;
};

vec3 helionWorldAt(vec2 uv, float depth) {
    vec4 world = ReflectionInverseViewProjection * vec4(uv * 2.0 - 1.0, depth, 1.0);
    return world.xyz / world.w;
}

HelionScreenPoint helionProject(vec3 position) {
    vec4 clip = ReflectionViewProjection * vec4(position, 1.0);
    if (clip.w <= HELION_MIN_CLIP_W) {
        return HelionScreenPoint(false, vec2(0.0));
    }
    vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
    bool inside = all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)));
    return HelionScreenPoint(inside, uv);
}

float helionBehind(vec3 position, vec2 uv) {
    float depth = textureLod(SceneDepthSampler, uv, 0.0).r;
    if (depth <= HELION_SKY_DEPTH) {
        return HELION_SKY_BEHIND;
    }
    return length(position) - length(helionWorldAt(uv, depth));
}

float helionEdgeFade(vec2 uv) {
    vec2 edge = min(uv, 1.0 - uv);
    return clamp(min(edge.x, edge.y) / HELION_EDGE_FADE, 0.0, 1.0);
}

vec4 helionMarch(vec3 start, vec3 normal, out float distanceToHit, out vec4 skySeen) {
    distanceToHit = 0.0;
    skySeen = vec4(0.0);
    vec3 direction = reflect(normalize(start), normal);
    vec3 origin = start + normal * HELION_SURFACE_LIFT;
    float previous = 0.0;
    float travelled = HELION_FIRST_STEP;
    float step = HELION_FIRST_STEP;
    for (int index = 0; index < HELION_MARCH_STEPS; index++) {
        vec3 position = origin + direction * travelled;
        HelionScreenPoint point = helionProject(position);
        if (!point.valid) {
            return vec4(0.0);
        }
        float behind = helionBehind(position, point.uv);
        if (behind < HELION_SKY_MARK && skySeen.a <= 0.0) {
            skySeen = vec4(textureLod(SceneColorSampler, point.uv, 0.0).rgb, helionEdgeFade(point.uv));
        }
        float thickness = max(HELION_MIN_THICKNESS, travelled * HELION_THICKNESS_SHARE);
        if (behind > 0.0 && behind < thickness) {
            float near = previous;
            float far = travelled;
            vec2 hit = point.uv;
            for (int refine = 0; refine < HELION_REFINE_STEPS; refine++) {
                float middle = 0.5 * (near + far);
                vec3 probe = origin + direction * middle;
                HelionScreenPoint probed = helionProject(probe);
                if (!probed.valid) {
                    break;
                }
                if (helionBehind(probe, probed.uv) > 0.0) {
                    far = middle;
                    hit = probed.uv;
                } else {
                    near = middle;
                }
            }
            distanceToHit = far;
            return vec4(textureLod(SceneColorSampler, hit, 0.0).rgb, helionEdgeFade(hit));
        }
        previous = travelled;
        step *= HELION_STEP_GROWTH;
        travelled += step;
    }
    return vec4(0.0);
}

vec4 helionPolished(ivec2 pixel, vec2 screen) {
    if (ReflectionFlags.x < HELION_FLAG_SET || !helionIsGlossyGeometry(texelFetch(GeometryLightSampler, pixel, 0).a)) {
        return vec4(0.0);
    }
    float depth = texelFetch(SceneDepthSampler, pixel, 0).r;
    if (depth <= HELION_SKY_DEPTH) {
        return vec4(0.0);
    }
    vec3 normal = normalize(texelFetch(GeometryNormalSampler, pixel, 0).rgb * 2.0 - 1.0);
    vec3 start = helionWorldAt(screen, depth);
    float reach;
    vec4 sky;
    vec4 hit = helionMarch(start, normal, reach, sky);
    float facing = clamp(dot(-normalize(start), normal), 0.0, 1.0);
    float fresnel = (HELION_POLISHED_F0 + (1.0 - HELION_POLISHED_F0) * pow(1.0 - facing, HELION_FRESNEL_POWER)) * HELION_POLISHED_STRENGTH;
    if (hit.a <= 0.0) {
        return vec4(sky.rgb * fresnel * sky.a, HELION_ADDITIVE);
    }
    float weight = fresnel * hit.a * clamp(1.0 - reach / HELION_POLISHED_REACH, 0.0, 1.0);
    return vec4(hit.rgb * weight, HELION_ADDITIVE);
}

void main() {
    fragColor = vec4(0.0);
    polishedColor = vec4(0.0);
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec2 screen = (vec2(pixel) + 0.5) / vec2(textureSize(WaterDepthSampler, 0));
    vec4 surface = texelFetch(WaterSurfaceSampler, pixel, 0);
    bool water = helionIsWater(surface);
    if (!water && !helionIsGlossy(surface)) {
        polishedColor = helionPolished(pixel, screen);
        return;
    }
    vec3 normal = helionDecodeOctahedral(surface.xy);
    if (water && normal.y < HELION_MIN_FACING) {
        return;
    }
    float reach;
    vec4 sky;
    fragColor = helionMarch(helionWorldAt(screen, texelFetch(WaterDepthSampler, pixel, 0).r), normal, reach, sky);
}