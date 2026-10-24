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
    float WaterSeconds;
    float LightningFlash;
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
const vec3 HELION_CLEAR_EXTINCTION = vec3(0.32, 0.075, 0.06);
const float HELION_TINT_EXTINCTION = 0.3;
const float HELION_MIN_EXTINCTION = 0.02;
const float HELION_TINT_SHARE = 0.4;
const vec3 HELION_TEAL = vec3(0.10, 0.55, 0.52);
const float HELION_TEAL_SHARE = 0.55;
const float HELION_SCATTER_STRENGTH = 0.3;
const float HELION_REFRACTION_STRENGTH = 0.035;
const float HELION_REFRACTION_DEPTH = 2.0;
const float HELION_REFRACTION_DISTANCE = 0.05;
const float HELION_WATER_IOR = 1.33;
const float HELION_CRITICAL_COSINE = 0.6585;
const float HELION_WINDOW_EDGE = 0.12;
const float HELION_OPEN_SKY_FROM = 0.85;
const float HELION_SUN_SHARP = 1200.0;
const float HELION_SUN_SPREAD = 90.0;
const float HELION_SUN_SPREAD_SHARE = 0.35;
const float HELION_SUN_GLINT_INTENSITY = 3.0;
const float HELION_MIN_CHANNEL = 1.0e-4;
const float HELION_SUN_HORIZON = 0.06;
const float HELION_MOON_SHARP = 4000.0;
const float HELION_MOON_SPREAD = 700.0;
const float HELION_MOON_SPREAD_SHARE = 0.05;
const vec3 HELION_MOON_GLINT = vec3(0.55, 0.62, 0.78);
const float HELION_CAUSTIC_STRENGTH = 1.6;
const float HELION_CAUSTIC_FADE = 0.3;
const float HELION_CAUSTIC_SCALE = 1.1;
const float HELION_CAUSTIC_SHARPNESS = 8.0;
const vec2 HELION_CAUSTIC_WARP_FREQUENCY = vec2(1.7, 1.3);
const vec2 HELION_CAUSTIC_WARP_SPEED = vec2(0.9, -0.7);
const float HELION_CAUSTIC_WARP = 0.55;
const vec2 HELION_CAUSTIC_LINES = vec2(2.2, 2.0);
const vec2 HELION_CAUSTIC_BEND = vec2(1.4, 1.1);
const float HELION_CAUSTIC_SECOND_SCALE = 1.7;
const vec2 HELION_CAUSTIC_SECOND_OFFSET = vec2(3.1, 1.7);
const float HELION_CAUSTIC_SECOND_SPEED = 1.3;
const float HELION_DAYLIGHT_FROM = 0.0;
const float HELION_DAYLIGHT_TO = 0.25;
const float HELION_FOG_DAYLIGHT = 0.35;
const float HELION_FLASH_SKY = 4.0;

vec3 helionReflectedSky(vec3 direction, bool water) {
    #ifdef HELION_PHYSICAL_SKY
    return water ? helionSkyColor(direction) : helionSkyColorWithCelestials(direction);
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

float helionDaylight() {
    #ifdef HELION_PHYSICAL_SKY
    return smoothstep(HELION_DAYLIGHT_FROM, HELION_DAYLIGHT_TO, SunDirection.y) * RainBrightness;
    #else
    return smoothstep(0.0, HELION_FOG_DAYLIGHT, helionLuminance(FogColor.rgb));
    #endif
}

vec3 helionCelestialGlint(vec3 reflected, float skyLight) {
    #ifdef HELION_PHYSICAL_SKY
    float open = smoothstep(HELION_OPEN_SKY_FROM, 1.0, skyLight) * RainBrightness;
    float sunFacing = max(dot(reflected, SunDirection), 0.0);
    float sunLobe = pow(sunFacing, HELION_SUN_SHARP) + HELION_SUN_SPREAD_SHARE * pow(sunFacing, HELION_SUN_SPREAD);
    vec3 nearSun = helionSkyRadiance(SunDirection);
    vec3 sunHue = nearSun / max(max(nearSun.r, nearSun.g), max(nearSun.b, HELION_MIN_CHANNEL));
    vec3 sunColor = sunHue * HELION_SUN_GLINT_INTENSITY * smoothstep(-HELION_SUN_HORIZON, HELION_SUN_HORIZON, SunDirection.y);
    float moonFacing = max(dot(reflected, MoonDirection), 0.0);
    float moonLobe = pow(moonFacing, HELION_MOON_SHARP) + HELION_MOON_SPREAD_SHARE * pow(moonFacing, HELION_MOON_SPREAD);
    vec3 moonColor = HELION_MOON_GLINT * smoothstep(-HELION_SUN_HORIZON, HELION_SUN_HORIZON, MoonDirection.y) * (1.0 - smoothstep(-HELION_SUN_HORIZON, HELION_SUN_HORIZON, SunDirection.y));
    return (sunColor * sunLobe + moonColor * moonLobe) * open;
    #else
    return vec3(0.0);
    #endif
}

float helionCausticLayer(vec2 position, float seconds) {
    vec2 warped = position + sin(position.yx * HELION_CAUSTIC_WARP_FREQUENCY + seconds * HELION_CAUSTIC_WARP_SPEED) * HELION_CAUSTIC_WARP;
    vec2 waves = sin(warped * HELION_CAUSTIC_LINES + sin(warped.yx * HELION_CAUSTIC_BEND));
    float lines = abs(waves.x * waves.y);
    return pow(1.0 - lines, HELION_CAUSTIC_SHARPNESS);
}

float helionCaustics(vec2 position) {
    vec2 scaled = position * HELION_CAUSTIC_SCALE;
    float second = helionCausticLayer(scaled * HELION_CAUSTIC_SECOND_SCALE + HELION_CAUSTIC_SECOND_OFFSET, WaterSeconds * HELION_CAUSTIC_SECOND_SPEED);
    return 0.5 * (helionCausticLayer(scaled, WaterSeconds) + second);
}

vec3 helionWaterColor(vec3 tint) {
    return mix(tint, HELION_TEAL, HELION_TEAL_SHARE);
}

vec3 helionWaterExtinction(vec3 tint) {
    return mix(HELION_CLEAR_EXTINCTION, (1.0 - tint) * HELION_TINT_EXTINCTION + HELION_MIN_EXTINCTION, HELION_TINT_SHARE);
}

vec2 helionRefractedScreen(vec2 screen, vec3 normal, float thickness, float distanceToWater) {
    float depthShare = clamp(thickness / HELION_REFRACTION_DEPTH, 0.0, 1.0);
    vec2 refracted = screen + normal.xz * HELION_REFRACTION_STRENGTH * depthShare / (1.0 + distanceToWater * HELION_REFRACTION_DISTANCE);
    vec4 refractedSurface = textureLod(WaterSurfaceSampler, refracted, 0.0);
    return helionIsWater(refractedSurface) ? refracted : screen;
}

vec3 helionRealisticWater(ivec2 pixel, vec2 screen, vec4 surface, vec3 normal, vec3 position, vec3 reflected, vec3 reflection, float fresnel) {
    vec3 tint = helionSrgbToLinear(texelFetch(WaterTintSampler, pixel, 0).rgb);
    float distanceToWater = length(position);
    vec2 refracted = helionRefractedScreen(screen, normal, surface.z, distanceToWater);
    float thickness = textureLod(WaterSurfaceSampler, refracted, 0.0).z;
    vec3 bottom = helionSrgbToLinear(textureLod(RefractionSampler, refracted, 0.0).rgb);
    vec3 bottomPosition = position + normalize(position) * thickness;
    float light = surface.w * surface.w;
    bottom *= 1.0 + helionCaustics(bottomPosition.xz) * HELION_CAUSTIC_STRENGTH * helionDaylight() * light * exp(-thickness * HELION_CAUSTIC_FADE);
    vec3 transmitted = exp(-max(thickness, HELION_WATER_MIN_THICKNESS) * helionWaterExtinction(tint));
    vec3 scattered = helionWaterColor(tint) * helionSrgbToLinear(helionWaterAmbient()) * light * HELION_SCATTER_STRENGTH;
    vec3 water = bottom * transmitted + scattered * (1.0 - transmitted);
    vec3 surfaceColor = mix(water, helionSrgbToLinear(reflection), fresnel) + helionCelestialGlint(reflected, surface.w);
    return helionLinearToSrgb(surfaceColor);
}

vec3 helionUnderwaterSurface(ivec2 pixel, vec2 screen, vec4 surface, vec3 normal, vec3 view, float distanceToWater) {
    vec3 tint = helionSrgbToLinear(texelFetch(WaterTintSampler, pixel, 0).rgb);
    vec3 deep = helionWaterColor(tint) * helionSrgbToLinear(helionWaterAmbient()) * surface.w * surface.w * HELION_SCATTER_STRENGTH;
    vec3 outgoing = refract(view, normal, HELION_WATER_IOR);
    float facing = clamp(dot(-view, normal), 0.0, 1.0);
    float escaping = smoothstep(HELION_CRITICAL_COSINE, HELION_CRITICAL_COSINE + HELION_WINDOW_EDGE, facing) * (1.0 - HELION_WATER_F0);
    vec2 refracted = clamp(screen + (outgoing.xz - view.xz) * HELION_REFRACTION_STRENGTH, vec2(0.0), vec2(1.0));
    vec3 above = helionSrgbToLinear(textureLod(RefractionSampler, refracted, 0.0).rgb);
    vec3 seen = mix(deep, above, escaping);
    vec3 transmitted = exp(-distanceToWater * helionWaterExtinction(tint));
    return helionLinearToSrgb(seen * transmitted + deep * (1.0 - transmitted));
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
    if (water && !realistic && normal.y < HELION_MIN_FACING) {
        discard;
    }
    float depth = texelFetch(WaterDepthSampler, pixel, 0).r;
    vec2 screen = (vec2(pixel) + 0.5) / vec2(textureSize(WaterDepthSampler, 0));
    vec4 world = WaterInverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 position = world.xyz / world.w;
    vec3 view = normalize(position);
    if (realistic && normal.y < -HELION_MIN_FACING) {
        fragColor = vec4(helionUnderwaterSurface(pixel, screen, surface, normal, view, length(position)), 1.0);
        return;
    }
    vec3 reflected = reflect(view, normal);
    float facing = clamp(dot(-view, normal), 0.0, 1.0);
    float baseReflectance = water ? HELION_WATER_F0 : HELION_GLOSSY_F0;
    float fresnel = (baseReflectance + (1.0 - baseReflectance) * pow(1.0 - facing, HELION_FRESNEL_POWER)) * ReflectionStrength;
    vec4 traced = TracedReflections > HELION_PRESENT ? texelFetch(ReflectionSampler, pixel, 0) : vec4(0.0);
    vec3 sky = helionReflectedSky(reflected, realistic) * (water ? 1.0 : surface.w * surface.w) * (1.0 + LightningFlash * HELION_FLASH_SKY * surface.w);
    vec3 fallback = traced.a > HELION_GEOMETRY_PRESENT_THRESHOLD ? traced.rgb : sky;
    vec4 screenHit = ScreenReflections > HELION_PRESENT ? texelFetch(ScreenReflectionSampler, pixel, 0) : vec4(0.0);
    vec3 reflection = mix(fallback, screenHit.rgb, screenHit.a);
    if (!water) {
        fragColor = vec4(reflection * fresnel, fresnel);
        return;
    }
    if (realistic) {
        fragColor = vec4(helionRealisticWater(pixel, screen, surface, normal, position, reflected, reflection, fresnel), 1.0);
        return;
    }
    float transmitted = exp(-surface.z * HELION_WATER_ABSORPTION);
    vec3 scattered = HELION_WATER_SCATTER * helionWaterAmbient() * surface.w * surface.w;
    float kept = transmitted * (1.0 - fresnel);
    fragColor = vec4(reflection * fresnel + scattered * (1.0 - transmitted) * (1.0 - fresnel), 1.0 - kept);
}
