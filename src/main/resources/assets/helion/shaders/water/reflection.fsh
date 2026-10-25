#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_geometry.glsl>
#include <helion:helion_water.glsl>
#include <helion:helion_water_optics.glsl>
#include <helion:helion_color.glsl>
#include <helion:helion_caustics.glsl>
#include <helion:helion_cloud_sky_map.glsl>
#include <helion:helion_water_waves.glsl>
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
uniform sampler2D ReflectedCloudSampler;
uniform sampler2D SkyCloudSampler;
uniform sampler2D ShoreDistanceSampler;

layout(std140) uniform HelionWaterReflection {
    mat4 WaterInverseViewProjection;
    mat4 WaterViewProjection;
    float ReflectionStrength;
    float TracedReflections;
    float ScreenReflections;
    float RealisticWater;
    float WaterSeconds;
    float LightningFlash;
    vec4 WaterCameraPosition;
    vec4 WaterEnvironment;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_GLINT_PI = 3.14159265;
const float HELION_WATER_F0 = 0.02;
const float HELION_GLOSSY_F0 = 0.04;
const float HELION_FRESNEL_POWER = 5.0;
const float HELION_MIN_FACING = 0.05;
const float HELION_WATER_ABSORPTION = 0.15;
const vec3 HELION_WATER_SCATTER = vec3(0.08, 0.2, 0.22);
const vec3 HELION_UP = vec3(0.0, 1.0, 0.0);
const float HELION_PRESENT = 0.5;
const float HELION_SKY_MAP_PRESENT = 1.5;
const float HELION_SCATTER_STRENGTH = 0.4;
const float HELION_SKY_HUE_SHARE = 0.35;
const float HELION_SUN_DIFFUSE = 0.45;
const vec3 HELION_LUMA = vec3(0.2126, 0.7152, 0.0722);
const float HELION_DOWNWELLING = 1.0;
const float HELION_UNDER_COLUMN = 8.0;
const float HELION_REFRACTION_STRENGTH = 0.035;
const float HELION_REFRACTION_DEPTH = 2.0;
const float HELION_REFRACTION_DISTANCE = 0.05;
const float HELION_WATER_IOR = 1.33;
const float HELION_CRITICAL_COSINE = 0.6585;
const float HELION_WINDOW_EDGE = 0.12;
const float HELION_OPEN_SKY_FROM = 0.85;
const float HELION_BASE_ROUGHNESS = 0.012;
const float HELION_SUN_IRRADIANCE = 4.0;
const float HELION_MOON_IRRADIANCE = 0.03;
const float HELION_GLINT_MAX = 16.0;
const float HELION_MIN_CHANNEL = 1.0e-4;
const float HELION_MIN_DENOMINATOR = 1.0e-5;
const float HELION_SUN_HORIZON = 0.06;
const vec3 HELION_MOON_GLINT = vec3(1.4, 1.55, 1.95);
const float HELION_CAUSTIC_STRENGTH = 0.85;
const float HELION_CAUSTIC_FADE = 0.25;
const float HELION_CAUSTIC_NEAR = 24.0;
const float HELION_CAUSTIC_FAR = 48.0;
const float HELION_FOAM_SHARPNESS = 8.0;
const vec2 HELION_FOAM_WARP_FREQUENCY = vec2(1.7, 1.3);
const vec2 HELION_FOAM_WARP_SPEED = vec2(0.9, -0.7);
const float HELION_FOAM_WARP = 0.55;
const vec2 HELION_FOAM_LINES = vec2(2.2, 2.0);
const vec2 HELION_FOAM_BEND = vec2(1.4, 1.1);
const float HELION_BRIGHT_FROM = 0.6;
const float HELION_SKY_BEHIND_MARGIN = 0.01;
const float HELION_CLOUD_EDGE = 0.08;
const float HELION_MIN_CLOUD_COVER = 1.0e-3;
const int HELION_BLUR_TAPS = 6;
const float HELION_BLUR_GOLDEN = 2.39996323;
const float HELION_BLUR_PER_BLOCK = 1.2;
const float HELION_BLUR_MAX = 6.0;
const vec3 HELION_DISPERSION = vec3(0.92, 1.0, 1.1);
const float HELION_SCATTER_G = 0.7;
const float HELION_SUN_SCATTER = 0.3;
const float HELION_BRIGHT_REFLECTANCE = 0.5;
const float HELION_DAYLIGHT_FROM = 0.0;
const float HELION_DAYLIGHT_TO = 0.25;
const float HELION_FOG_DAYLIGHT = 0.35;
const float HELION_FLASH_SKY = 4.0;
const float HELION_FOAM_FROM = 0.25;
const float HELION_FOAM_TO = 0.6;
const float HELION_FALL_FOAM_SCALE = 3.0;
const float HELION_FALL_FOAM_STRETCH = 0.35;
const float HELION_FALL_FOAM_SPEED = 4.0;
const float HELION_FALL_FOAM = 0.85;
const float HELION_UNDER_GLOW = 0.35;
const float HELION_UNDER_SUN = 0.6;
const float HELION_DARK_WATER_BODY = 0.45;
const float HELION_SHORE_FOAM_STRENGTH = 0.9;
const float HELION_SHORE_FOAM_LIGHT = 0.9;
const float HELION_SHORE_NEAR = 44.0;
const float HELION_SHORE_FAR = 60.0;
const float HELION_SHORE_FOAM_SHALLOW = 2.0;
const float HELION_SHORE_FOAM_DEEP = 6.0;
const float HELION_DARK_WATER_GLOW = 0.2;
const float HELION_CAUSTIC_FLAT_FROM = 0.35;
const float HELION_CAUSTIC_FLAT_TO = 0.8;

vec3 helionReflectedClouds(vec3 sky, vec3 direction) {
    if (WaterCameraPosition.w < HELION_PRESENT || direction.y <= 0.0) {
        return sky;
    }
    vec4 clouds = vec4(0.0, 0.0, 0.0, 1.0);
    if (WaterCameraPosition.w > HELION_SKY_MAP_PRESENT) {
        clouds = textureLod(SkyCloudSampler, helionCloudSkyMapUv(direction), 0.0);
    }
    vec4 clip = WaterViewProjection * vec4(direction, 0.0);
    if (clip.w > 0.0) {
        vec2 screen = clip.xy / clip.w * 0.5 + 0.5;
        vec2 inside = min(screen, 1.0 - screen);
        float edge = smoothstep(0.0, HELION_CLOUD_EDGE, min(inside.x, inside.y));
        if (edge > 0.0) {
            clouds = mix(clouds, textureLod(ReflectedCloudSampler, screen, 0.0), edge);
        }
    }
    float cover = 1.0 - clamp(clouds.a, 0.0, 1.0);
    vec3 radiance = clouds.rgb / max(cover, HELION_MIN_CLOUD_COVER);
    return mix(sky, helionLinearToSrgb(helionNeutral(radiance)), cover);
}

vec3 helionReflectedSky(vec3 direction, bool water) {
    #ifdef HELION_PHYSICAL_SKY
    return helionReflectedClouds(water ? helionSkyColor(direction) : helionSkyColorWithCelestials(direction), direction);
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

float helionCombinedRoughness(float filtered) {
    return sqrt(HELION_BASE_ROUGHNESS * HELION_BASE_ROUGHNESS + filtered * filtered);
}

float helionSchlick(float baseReflectance, float facing, float roughness) {
    float grazing = max(1.0 - roughness, baseReflectance);
    return baseReflectance + (grazing - baseReflectance) * pow(1.0 - facing, HELION_FRESNEL_POWER);
}

float helionSpecular(vec3 view, vec3 normal, vec3 light, float roughness) {
    float facingLight = dot(normal, light);
    float facingView = dot(normal, -view);
    if (facingLight <= 0.0 || facingView <= 0.0) {
        return 0.0;
    }
    vec3 halfway = normalize(light - view);
    float alpha2 = roughness * roughness;
    float cosine = max(dot(normal, halfway), 0.0);
    float denominator = cosine * cosine * (alpha2 - 1.0) + 1.0;
    float distribution = alpha2 / (HELION_GLINT_PI * denominator * denominator);
    float visibility = 0.5 / max(facingLight * sqrt(facingView * facingView * (1.0 - alpha2) + alpha2)
        + facingView * sqrt(facingLight * facingLight * (1.0 - alpha2) + alpha2), HELION_MIN_DENOMINATOR);
    float fresnel = helionSchlick(HELION_WATER_F0, max(dot(halfway, -view), 0.0), 0.0);
    return distribution * visibility * fresnel * facingLight;
}

vec3 helionSunHue() {
    #ifdef HELION_PHYSICAL_SKY
    vec3 nearSun = helionSkyRadiance(SunDirection);
    return nearSun / max(max(nearSun.r, nearSun.g), max(nearSun.b, HELION_MIN_CHANNEL));
    #else
    return vec3(1.0);
    #endif
}

vec3 helionCelestialGlint(vec3 view, vec3 normal, float roughness, float skyLight) {
    #ifdef HELION_PHYSICAL_SKY
    float open = smoothstep(HELION_OPEN_SKY_FROM, 1.0, skyLight) * RainBrightness;
    float sunUp = smoothstep(-HELION_SUN_HORIZON, HELION_SUN_HORIZON, SunDirection.y);
    float moonUp = smoothstep(-HELION_SUN_HORIZON, HELION_SUN_HORIZON, MoonDirection.y) * (1.0 - sunUp);
    vec3 sun = helionSunHue() * HELION_SUN_IRRADIANCE * sunUp * helionSpecular(view, normal, SunDirection, roughness);
    vec3 moon = HELION_MOON_GLINT * HELION_MOON_IRRADIANCE * moonUp * helionSpecular(view, normal, MoonDirection, roughness);
    return min(sun + moon, vec3(HELION_GLINT_MAX)) * open;
    #else
    return vec3(0.0);
    #endif
}

float helionFoamLayer(vec2 position, float seconds) {
    vec2 warped = position + sin(position.yx * HELION_FOAM_WARP_FREQUENCY + seconds * HELION_FOAM_WARP_SPEED) * HELION_FOAM_WARP;
    vec2 waves = sin(warped * HELION_FOAM_LINES + sin(warped.yx * HELION_FOAM_BEND));
    float lines = abs(waves.x * waves.y);
    return pow(1.0 - lines, HELION_FOAM_SHARPNESS);
}

vec2 helionRefractedScreen(vec2 screen, vec3 normal, float thickness, float distanceToWater) {
    float depthShare = clamp(thickness / HELION_REFRACTION_DEPTH, 0.0, 1.0);
    vec2 refracted = screen + normal.xz * HELION_REFRACTION_STRENGTH * depthShare / (1.0 + distanceToWater * HELION_REFRACTION_DISTANCE);
    vec4 refractedSurface = textureLod(WaterSurfaceSampler, refracted, 0.0);
    return helionIsWater(refractedSurface) ? refracted : screen;
}

vec3 helionRefractedBottom(vec2 screen, vec2 refracted, float thickness) {
    vec2 offset = refracted - screen;
    vec2 texel = 1.0 / vec2(textureSize(RefractionSampler, 0));
    float radius = min(thickness * HELION_BLUR_PER_BLOCK, HELION_BLUR_MAX);
    vec3 sum = vec3(0.0);
    for (int tap = 0; tap < HELION_BLUR_TAPS; tap++) {
        float angle = float(tap) * HELION_BLUR_GOLDEN + WaterSeconds;
        vec2 jitter = vec2(cos(angle), sin(angle)) * radius * sqrt((float(tap) + 0.5) / float(HELION_BLUR_TAPS)) * texel;
        vec2 base = screen + jitter;
        sum += vec3(
            textureLod(RefractionSampler, clamp(base + offset * HELION_DISPERSION.r, vec2(0.0), vec2(1.0)), 0.0).r,
            textureLod(RefractionSampler, clamp(base + offset * HELION_DISPERSION.g, vec2(0.0), vec2(1.0)), 0.0).g,
            textureLod(RefractionSampler, clamp(base + offset * HELION_DISPERSION.b, vec2(0.0), vec2(1.0)), 0.0).b
        );
    }
    return helionSrgbToLinear(sum / float(HELION_BLUR_TAPS));
}

float helionPhase(float cosine) {
    return (1.0 - HELION_SCATTER_G * HELION_SCATTER_G) / pow(1.0 + HELION_SCATTER_G * HELION_SCATTER_G - 2.0 * HELION_SCATTER_G * cosine, 1.5);
}

vec3 helionSunScatter(vec3 inside) {
    #ifdef HELION_PHYSICAL_SKY
    return helionSunHue() * helionPhase(dot(inside, SunDirection)) * HELION_SUN_SCATTER * smoothstep(-HELION_SUN_HORIZON, HELION_SUN_HORIZON, SunDirection.y) * RainBrightness;
    #else
    return vec3(0.0);
    #endif
}

vec3 helionBottomAt(ivec2 pixel, vec2 size, out bool valid) {
    vec4 surface = texelFetch(WaterSurfaceSampler, pixel, 0);
    valid = helionIsWater(surface);
    vec2 screen = (vec2(pixel) + 0.5) / size;
    vec4 world = WaterInverseViewProjection * vec4(screen * 2.0 - 1.0, texelFetch(WaterDepthSampler, pixel, 0).r, 1.0);
    vec3 position = world.xyz / world.w;
    return position + normalize(position) * surface.z;
}

float helionBottomFacing(ivec2 pixel, vec3 bottom) {
    vec2 size = vec2(textureSize(WaterDepthSampler, 0));
    bool besideX;
    bool besideY;
    vec3 alongX = helionBottomAt(pixel + ivec2(1, 0), size, besideX) - bottom;
    vec3 alongY = helionBottomAt(pixel + ivec2(0, 1), size, besideY) - bottom;
    vec3 across = cross(alongX, alongY);
    float area = length(across);
    if (!besideX || !besideY || area <= 0.0) {
        return 1.0;
    }
    return abs(across.y) / area;
}

vec3 helionWaterLight() {
    vec3 sky = helionSrgbToLinear(helionWaterAmbient());
    return mix(vec3(dot(sky, HELION_LUMA)), sky, HELION_SKY_HUE_SHARE) + helionSunHue() * helionDaylight() * HELION_SUN_DIFFUSE;
}

vec3 helionRealisticWater(ivec2 pixel, vec2 screen, vec4 surface, vec3 normal, vec3 position, vec3 reflection, float hit) {
    vec4 tintSample = texelFetch(WaterTintSampler, pixel, 0);
    bool falling = helionIsFallingWater(tintSample.a);
    float roughness = helionCombinedRoughness(falling ? 0.0 : helionDecodeWaterRoughness(tintSample.a));
    vec3 view = normalize(position);
    float distanceToWater = length(position);
    vec2 refracted = helionRefractedScreen(screen, normal, surface.z, distanceToWater);
    float thickness = textureLod(WaterSurfaceSampler, refracted, 0.0).z;
    vec3 inside = refract(view, normal, 1.0 / HELION_WATER_IOR);
    float column = thickness * abs(inside.y);
    HelionWaterOptics optics = helionWaterOptics(tintSample.rgb, column, WaterEnvironment.x);
    vec3 bottom = helionRefractedBottom(screen, refracted, thickness);
    vec3 bottomPosition = position + view * thickness + WaterCameraPosition.xyz;
    float light = surface.w * surface.w;
    float causticShare = HELION_CAUSTIC_STRENGTH * helionDaylight() * light * exp(-column * HELION_CAUSTIC_FADE) * (1.0 - smoothstep(HELION_CAUSTIC_NEAR, HELION_CAUSTIC_FAR, distanceToWater));
    causticShare *= smoothstep(HELION_CAUSTIC_FLAT_FROM, HELION_CAUSTIC_FLAT_TO, helionBottomFacing(pixel, position + view * surface.z));
    bottom *= helionCausticGain(helionCausticLight(bottomPosition.xz, column, WaterSeconds), causticShare);
    vec3 transmitted = exp(-optics.extinction * (max(thickness, HELION_WATER_MIN_THICKNESS) + column * HELION_DOWNWELLING));
    vec3 lit = (helionWaterLight() + helionSunScatter(inside)) * light;
    vec3 scattered = optics.albedo * lit * HELION_SCATTER_STRENGTH * (1.0 - exp(-optics.extinction * thickness));
    vec3 water = (bottom * transmitted + scattered) * mix(HELION_DARK_WATER_BODY, 1.0, helionDaylight());
    float facing = clamp(dot(-view, normal), 0.0, 1.0);
    float fresnel = helionSchlick(HELION_WATER_F0, facing, roughness) * ReflectionStrength;
    vec3 mirrored = helionSrgbToLinear(reflection);
    float bright = smoothstep(HELION_BRIGHT_FROM, 1.0, max(max(mirrored.r, mirrored.g), mirrored.b)) * hit * (1.0 - helionDaylight());
    vec3 surfaceColor = mix(water, mirrored, max(fresnel, bright * HELION_BRIGHT_REFLECTANCE)) + helionCelestialGlint(view, normal, roughness, surface.w) * (1.0 - hit);
    vec3 world = position + WaterCameraPosition.xyz;
    float foam = 0.0;
    if (falling) {
        vec2 fallingPosition = vec2((world.x + world.z) * HELION_FALL_FOAM_SCALE, (world.y + WaterSeconds * HELION_FALL_FOAM_SPEED) * HELION_FALL_FOAM_STRETCH);
        foam = smoothstep(HELION_FOAM_FROM, HELION_FOAM_TO, helionFoamLayer(fallingPosition, WaterSeconds)) * HELION_FALL_FOAM;
    }
    surfaceColor = mix(surfaceColor, helionSrgbToLinear(helionWaterAmbient()) * light, foam);
    if (WaterEnvironment.z > HELION_PRESENT && !falling) {
        float shoreFoam = helionShoreWave(ShoreDistanceSampler, world.xz, WaterEnvironment.y).foam * (1.0 - smoothstep(HELION_SHORE_NEAR, HELION_SHORE_FAR, length(position.xz))) * (1.0 - smoothstep(HELION_SHORE_FOAM_SHALLOW, HELION_SHORE_FOAM_DEEP, column));
        surfaceColor = mix(surfaceColor, helionWaterLight() * light * HELION_SHORE_FOAM_LIGHT, shoreFoam * HELION_SHORE_FOAM_STRENGTH);
    }
    return helionLinearToSrgb(surfaceColor);
}

vec3 helionUnderwaterSurface(ivec2 pixel, vec2 screen, vec4 surface, vec3 normal, vec3 view) {
    vec3 tint = texelFetch(WaterTintSampler, pixel, 0).rgb;
    HelionWaterOptics optics = helionWaterOptics(tint, HELION_UNDER_COLUMN, WaterEnvironment.x);
    float light = surface.w * surface.w;
    vec3 down = reflect(view, normal);
    vec3 inside = -down;
    vec3 glow = optics.albedo * (helionWaterLight() * HELION_UNDER_GLOW + helionSunScatter(inside) * HELION_UNDER_SUN) * light * mix(HELION_DARK_WATER_GLOW, 1.0, helionDaylight());
    vec3 outgoing = refract(view, normal, HELION_WATER_IOR);
    float facing = clamp(dot(-view, normal), 0.0, 1.0);
    float escaping = smoothstep(HELION_CRITICAL_COSINE, HELION_CRITICAL_COSINE + HELION_WINDOW_EDGE, facing) * (1.0 - HELION_WATER_F0);
    vec2 refracted = clamp(screen + (outgoing.xz - view.xz) * HELION_REFRACTION_STRENGTH, vec2(0.0), vec2(1.0));
    vec3 above = helionSrgbToLinear(textureLod(RefractionSampler, refracted, 0.0).rgb);
    #ifdef HELION_PHYSICAL_SKY
    float skyBehind = step(HELION_WATER_MAX_THICKNESS - HELION_SKY_BEHIND_MARGIN, surface.z);
    above = mix(above, helionSrgbToLinear(helionSkyColorWithCelestials(outgoing)), skyBehind);
    #endif
    return helionLinearToSrgb(mix(glow, above, escaping));
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
        fragColor = vec4(helionUnderwaterSurface(pixel, screen, surface, normal, view), 1.0);
        return;
    }
    vec3 reflected = reflect(view, normal);
    float facing = clamp(dot(-view, normal), 0.0, 1.0);
    float baseReflectance = water ? HELION_WATER_F0 : HELION_GLOSSY_F0;
    float fresnel = helionSchlick(baseReflectance, facing, 0.0) * ReflectionStrength;
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
        float hit = max(screenHit.a, traced.a > HELION_GEOMETRY_PRESENT_THRESHOLD ? 1.0 : 0.0);
        fragColor = vec4(helionRealisticWater(pixel, screen, surface, normal, position, reflection, hit), 1.0);
        return;
    }
    float transmitted = exp(-surface.z * HELION_WATER_ABSORPTION);
    vec3 scattered = HELION_WATER_SCATTER * helionWaterAmbient() * surface.w * surface.w;
    float kept = transmitted * (1.0 - fresnel);
    fragColor = vec4(reflection * fresnel + scattered * (1.0 - transmitted) * (1.0 - fresnel), 1.0 - kept);
}
