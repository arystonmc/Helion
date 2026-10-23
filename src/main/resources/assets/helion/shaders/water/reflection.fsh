#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_geometry.glsl>
#include <helion:helion_water.glsl>
#include <helion:helion_color.glsl>
#ifdef HELION_PHYSICAL_SKY
#include <helion:helion_sky_color.glsl>
#else
#include <minecraft:fog.glsl>
#endif

uniform sampler2D WaterSurfaceSampler;
uniform sampler2D WaterDepthSampler;
uniform sampler2D ReflectionSampler;
uniform sampler2D ScreenReflectionSampler;
uniform sampler2D WaterTintSampler;
uniform sampler2D RefractionSampler;

layout(std140) uniform HelionWaterReflection {
    mat4 WaterInverseViewProjection;
    float ReflectionStrength;
    float TracedReflections;
    float ScreenReflections;
    float RealisticWater;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_WATER_F0 = 0.02;
const float HELION_GLOSSY_F0 = 0.04;
const float HELION_FRESNEL_POWER = 5.0;
const float HELION_MIN_FACING = 0.05;
const float HELION_WATER_ABSORPTION = 0.15;
const vec3 HELION_WATER_SCATTER = vec3(0.08, 0.2, 0.22);
const vec3 HELION_UP = vec3(0.0, 1.0, 0.0);
const float HELION_PRESENT = 0.5;
const vec3 HELION_CLEAR_EXTINCTION = vec3(0.30, 0.07, 0.05);
const float HELION_TINT_EXTINCTION = 0.35;
const float HELION_MIN_EXTINCTION = 0.02;
const float HELION_TINT_SHARE = 0.5;
const float HELION_SCATTER_STRENGTH = 0.25;
const float HELION_REFRACTION_STRENGTH = 0.03;
const float HELION_REFRACTION_DEPTH = 2.0;
const float HELION_REFRACTION_DISTANCE = 0.05;

vec3 helionReflectedSky(vec3 direction) {
    #ifdef HELION_PHYSICAL_SKY
    return helionSkyColorWithCelestials(direction);
    #else
    return FogColor.rgb;
    #endif
}

vec3 helionWaterAmbient() {
    #ifdef HELION_PHYSICAL_SKY
    return helionSkyColor(HELION_UP);
    #else
    return FogColor.rgb;
    #endif
}

vec2 helionRefractedScreen(vec2 screen, vec3 normal, float thickness, float distanceToWater) {
    float depthShare = clamp(thickness / HELION_REFRACTION_DEPTH, 0.0, 1.0);
    vec2 refracted = screen + normal.xz * HELION_REFRACTION_STRENGTH * depthShare / (1.0 + distanceToWater * HELION_REFRACTION_DISTANCE);
    vec4 refractedSurface = textureLod(WaterSurfaceSampler, refracted, 0.0);
    return helionIsWater(refractedSurface) ? refracted : screen;
}

vec3 helionRealisticWater(ivec2 pixel, vec2 screen, vec4 surface, vec3 normal, float distanceToWater, vec3 reflection, float fresnel) {
    vec3 tint = helionSrgbToLinear(texelFetch(WaterTintSampler, pixel, 0).rgb);
    vec2 refracted = helionRefractedScreen(screen, normal, surface.z, distanceToWater);
    float thickness = textureLod(WaterSurfaceSampler, refracted, 0.0).z;
    vec3 bottom = helionSrgbToLinear(textureLod(RefractionSampler, refracted, 0.0).rgb);
    vec3 extinction = mix(HELION_CLEAR_EXTINCTION, (1.0 - tint) * HELION_TINT_EXTINCTION + HELION_MIN_EXTINCTION, HELION_TINT_SHARE);
    vec3 transmitted = exp(-max(thickness, HELION_WATER_MIN_THICKNESS) * extinction);
    vec3 ambient = helionSrgbToLinear(helionWaterAmbient()) * surface.w * surface.w;
    vec3 scattered = tint * ambient * HELION_SCATTER_STRENGTH;
    vec3 water = bottom * transmitted + scattered * (1.0 - transmitted);
    return helionLinearToSrgb(mix(water, helionSrgbToLinear(reflection), fresnel));
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 surface = texelFetch(WaterSurfaceSampler, pixel, 0);
    bool water = helionIsWater(surface);
    if (!water && !helionIsGlossy(surface)) {
        discard;
    }
    vec3 normal = helionDecodeOctahedral(surface.xy);
    bool realistic = water && RealisticWater > HELION_PRESENT;
    if (water && (realistic ? normal.y < -HELION_MIN_FACING : normal.y < HELION_MIN_FACING)) {
        discard;
    }
    float depth = texelFetch(WaterDepthSampler, pixel, 0).r;
    vec2 screen = (vec2(pixel) + 0.5) / vec2(textureSize(WaterDepthSampler, 0));
    vec4 world = WaterInverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 view = normalize(world.xyz / world.w);
    vec3 reflected = reflect(view, normal);
    float facing = clamp(dot(-view, normal), 0.0, 1.0);
    float baseReflectance = water ? HELION_WATER_F0 : HELION_GLOSSY_F0;
    float fresnel = (baseReflectance + (1.0 - baseReflectance) * pow(1.0 - facing, HELION_FRESNEL_POWER)) * ReflectionStrength;
    vec4 traced = TracedReflections > HELION_PRESENT ? texelFetch(ReflectionSampler, pixel, 0) : vec4(0.0);
    vec3 sky = helionReflectedSky(reflected) * (water ? 1.0 : surface.w * surface.w);
    vec3 fallback = traced.a > HELION_GEOMETRY_PRESENT_THRESHOLD ? traced.rgb : sky;
    vec4 screenHit = ScreenReflections > HELION_PRESENT ? texelFetch(ScreenReflectionSampler, pixel, 0) : vec4(0.0);
    vec3 reflection = mix(fallback, screenHit.rgb, screenHit.a);
    if (!water) {
        fragColor = vec4(reflection * fresnel, fresnel);
        return;
    }
    if (realistic) {
        fragColor = vec4(helionRealisticWater(pixel, screen, surface, normal, length(world.xyz / world.w), reflection, fresnel), 1.0);
        return;
    }
    float transmitted = exp(-surface.z * HELION_WATER_ABSORPTION);
    vec3 scattered = HELION_WATER_SCATTER * helionWaterAmbient() * surface.w * surface.w;
    float kept = transmitted * (1.0 - fresnel);
    fragColor = vec4(reflection * fresnel + scattered * (1.0 - transmitted) * (1.0 - fresnel), 1.0 - kept);
}
