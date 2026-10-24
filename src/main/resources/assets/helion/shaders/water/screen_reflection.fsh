#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_water.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_wetness.glsl>
#ifdef HELION_PHYSICAL_SKY
#include <helion:helion_sky_color.glsl>
#else
#include <minecraft:fog.glsl>
#endif

uniform sampler2D WaterSurfaceSampler;
uniform sampler2D WaterDepthSampler;
uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D GeometryNormalSampler;
uniform sampler2D GeometryLightSampler;
uniform sampler2D WaveSampler;

layout(std140) uniform HelionScreenReflection {
    mat4 ReflectionViewProjection;
    mat4 ReflectionInverseViewProjection;
    vec4 ReflectionFlags;
    vec4 Wetness;
    vec4 WetnessOrigin;
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
const float HELION_WET_F0 = 0.02;
const float HELION_PUDDLE_F0 = 0.3;
const float HELION_PUDDLE_MARCH = 0.05;
const float HELION_WET_REACH = 48.0;
const float HELION_PUDDLE_WAVE_TEXELS = 1024.0;
const float HELION_PUDDLE_WAVE_CELLS = 8.0;
const float HELION_PUDDLE_WAVE_SLOPE = 3.4;
const float HELION_FLASH_SKY = 4.0;

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

vec3 helionReflectedSky(vec3 direction) {
    #ifdef HELION_PHYSICAL_SKY
    return helionSkyColor(direction);
    #else
    return FogColor.rgb;
    #endif
}

float helionFresnel(float baseReflectance, vec3 view, vec3 normal) {
    float facing = clamp(dot(-view, normal), 0.0, 1.0);
    return baseReflectance + (1.0 - baseReflectance) * pow(1.0 - facing, HELION_FRESNEL_POWER);
}

vec2 helionPuddleWaveSlope(vec2 position) {
    vec2 uv = position * HELION_PUDDLE_WAVE_CELLS / HELION_PUDDLE_WAVE_TEXELS;
    float texel = 1.0 / HELION_PUDDLE_WAVE_TEXELS;
    float right = textureLod(WaveSampler, uv + vec2(texel, 0.0), 0.0).r;
    float left = textureLod(WaveSampler, uv - vec2(texel, 0.0), 0.0).r;
    float front = textureLod(WaveSampler, uv + vec2(0.0, texel), 0.0).r;
    float back = textureLod(WaveSampler, uv - vec2(0.0, texel), 0.0).r;
    return vec2(right - left, front - back) * HELION_PUDDLE_WAVE_SLOPE;
}

vec4 helionSurfaceSheen(ivec2 pixel, vec2 screen) {
    if (ReflectionFlags.x < HELION_FLAG_SET) {
        return vec4(0.0);
    }
    vec4 light = texelFetch(GeometryLightSampler, pixel, 0);
    float depth = texelFetch(SceneDepthSampler, pixel, 0).r;
    if (light.a < HELION_GEOMETRY_PRESENT_THRESHOLD || depth <= HELION_SKY_DEPTH) {
        return vec4(0.0);
    }
    bool polished = ReflectionFlags.z > HELION_FLAG_SET && helionIsGlossyGeometry(light.a);
    vec3 surfaceNormal = normalize(texelFetch(GeometryNormalSampler, pixel, 0).rgb * 2.0 - 1.0);
    vec3 start = helionWorldAt(screen, depth);
    vec3 worldPosition = start + WetnessOrigin.xyz;
    bool simulated = ReflectionFlags.w > HELION_FLAG_SET;
    vec2 waveSlope = simulated ? helionPuddleWaveSlope(worldPosition.xz) : vec2(0.0);
    HelionWetSurface wet = helionWetSurface(light, surfaceNormal, worldPosition, Wetness, waveSlope, simulated);
    if (!polished && wet.sheen <= 0.0) {
        return vec4(0.0);
    }
    vec3 view = normalize(start);
    vec3 normal = wet.normal;
    float polishedWeight = polished ? helionFresnel(HELION_POLISHED_F0, view, normal) * HELION_POLISHED_STRENGTH : 0.0;
    float wetWeight = helionFresnel(mix(HELION_WET_F0, HELION_PUDDLE_F0, wet.puddle), view, normal) * wet.sheen;
    vec3 sky = helionReflectedSky(reflect(view, normal)) * light.g * light.g * (1.0 + WetnessOrigin.w * HELION_FLASH_SKY);
    vec3 reflection = sky;
    float reach = 0.0;
    if (polished || wet.puddle > HELION_PUDDLE_MARCH) {
        vec4 skySeen;
        vec4 hit = helionMarch(start, normal, reach, skySeen);
        vec3 missed = polished && skySeen.a > 0.0 ? mix(sky, skySeen.rgb, skySeen.a) : sky;
        float fade = clamp(1.0 - reach / (polished ? HELION_POLISHED_REACH : HELION_WET_REACH), 0.0, 1.0);
        reflection = mix(missed, hit.rgb, hit.a * fade);
    }
    float kept = (1.0 - wet.darkening) * (1.0 - wetWeight);
    return vec4(reflection * (polishedWeight + wetWeight), 1.0 - kept);
}

void main() {
    fragColor = vec4(0.0);
    polishedColor = vec4(0.0);
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec2 screen = (vec2(pixel) + 0.5) / vec2(textureSize(WaterDepthSampler, 0));
    vec4 surface = texelFetch(WaterSurfaceSampler, pixel, 0);
    bool water = helionIsWater(surface);
    if (ReflectionFlags.y < HELION_FLAG_SET || !water && !helionIsGlossy(surface)) {
        polishedColor = helionSurfaceSheen(pixel, screen);
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