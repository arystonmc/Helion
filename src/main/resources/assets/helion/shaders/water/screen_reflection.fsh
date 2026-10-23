#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_water.glsl>

uniform sampler2D WaterSurfaceSampler;
uniform sampler2D WaterDepthSampler;
uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;

layout(std140) uniform HelionScreenReflection {
    mat4 ReflectionViewProjection;
    mat4 ReflectionInverseViewProjection;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

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
        return -1.0;
    }
    return length(position) - length(helionWorldAt(uv, depth));
}

float helionEdgeFade(vec2 uv) {
    vec2 edge = min(uv, 1.0 - uv);
    return clamp(min(edge.x, edge.y) / HELION_EDGE_FADE, 0.0, 1.0);
}

void main() {
    fragColor = vec4(0.0);
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 surface = texelFetch(WaterSurfaceSampler, pixel, 0);
    bool water = helionIsWater(surface);
    if (!water && !helionIsGlossy(surface)) {
        return;
    }
    vec3 normal = helionDecodeOctahedral(surface.xy);
    if (water && normal.y < HELION_MIN_FACING) {
        return;
    }
    vec2 screen = (vec2(pixel) + 0.5) / vec2(textureSize(WaterDepthSampler, 0));
    vec3 start = helionWorldAt(screen, texelFetch(WaterDepthSampler, pixel, 0).r);
    vec3 direction = reflect(normalize(start), normal);
    vec3 origin = start + normal * HELION_SURFACE_LIFT;
    float previous = 0.0;
    float travelled = HELION_FIRST_STEP;
    float step = HELION_FIRST_STEP;
    for (int index = 0; index < HELION_MARCH_STEPS; index++) {
        vec3 position = origin + direction * travelled;
        HelionScreenPoint point = helionProject(position);
        if (!point.valid) {
            return;
        }
        float behind = helionBehind(position, point.uv);
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
            fragColor = vec4(textureLod(SceneColorSampler, hit, 0.0).rgb, helionEdgeFade(hit));
            return;
        }
        previous = travelled;
        step *= HELION_STEP_GROWTH;
        travelled += step;
    }
}
