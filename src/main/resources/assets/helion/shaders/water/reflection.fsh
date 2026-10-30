#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_geometry.glsl>
#include <helion:helion_water.glsl>
#include <helion:helion_water_optics.glsl>
#include <helion:helion_color.glsl>
#include <helion:helion_caustics.glsl>
#include <helion:helion_cloud_sky_map.glsl>
#include <helion:helion_water_waves.glsl>
#include <helion:helion_hash.glsl>
#include <minecraft:fog.glsl>
#ifdef HELION_PHYSICAL_SKY
#include <helion:helion_sky_color.glsl>
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
uniform sampler2D GlassSurfaceSampler;
uniform sampler2D GlassDepthSampler;
uniform sampler2D GlassTintSampler;
uniform sampler2D RainCoverSampler;
uniform sampler2D GlassBeadSampler;
uniform sampler2D GlassStreakSampler;
uniform sampler2D GlassMistSampler;

#include <helion:helion_rain_cover.glsl>

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
const float HELION_FOAM_FROM = 0.55;
const float HELION_FOAM_TO = 0.8;
const float HELION_FALL_FOAM_ACROSS = 4.0;
const float HELION_FALL_FOAM_ALONG = 0.6;
const float HELION_FALL_FOAM_SPEED = 2.2;
const float HELION_FALL_FOAM_DETAIL = 2.3;
const float HELION_FALL_FOAM_DETAIL_SHARE = 0.35;
const vec2 HELION_FALL_FOAM_DETAIL_SHIFT = vec2(17.0, 31.0);
const float HELION_FALL_FOAM = 0.55;
const float HELION_MIN_DIRECTION_FOAM = 1.0e-4;
const float HELION_UNDER_GLOW = 0.35;
const float HELION_UNDER_SUN = 0.6;
const float HELION_DARK_WATER_BODY = 0.45;
const float HELION_GLASS_WAVE_SCALE = 1.3;
const float HELION_GLASS_WAVE_HEIGHT = 0.7;
const float HELION_GLASS_WAVE = 0.0025;
const float HELION_GLASS_FRAME = 0.9;
const float HELION_GLASS_MIN_CHANNEL = 1.0e-3;
const float HELION_GLASS_SATURATED = 0.25;
const float HELION_GLASS_STAIN = 1.0;
const float HELION_GLASS_CLEAR_TINT = 0.06;
const float HELION_GLASS_DARK_FROM = 0.25;
const float HELION_GLASS_DARK_SHADE = 0.55;
const float HELION_GLASS_FRAME_SHARE = 0.3;
const float HELION_GLASS_MIN_REFLECTANCE = 0.08;
const float HELION_GLASS_FACE_INSET = 0.5;
const float HELION_GLASS_RAIN_FADE_FROM = 20.0;
const float HELION_GLASS_BEAD_TILE = 0.5;
const float HELION_GLASS_BEAD_TEXELS = 256.0;
const float HELION_GLASS_BEAD_REACH = 0.046875;
const float HELION_GLASS_BEAD_DENSITY = 0.8;
const float HELION_GLASS_FINE_TILE = 1.0;
const float HELION_GLASS_FINE_TEXELS = 512.0;
const float HELION_GLASS_FINE_REACH = 0.0234375;
const float HELION_GLASS_FINE_DENSITY = 0.55;
const vec2 HELION_GLASS_FINE_MIRROR = vec2(-1.0, 1.0);
const vec2 HELION_GLASS_FINE_SHIFT = vec2(0.37, 0.61);
const float HELION_GLASS_BEAD_RADIUS = 0.02;
const float HELION_GLASS_BEAD_LIFE = 75.0;
const float HELION_GLASS_BEAD_SPREAD = 13.7;
const float HELION_GLASS_BEAD_GROW = 0.12;
const float HELION_GLASS_BEAD_FADE = 0.02;
const float HELION_GLASS_SEED_SOFT = 0.04;
const float HELION_GLASS_EDGE_SOFT = 0.06;
const float HELION_GLASS_SHARP_LOD = 0.5;
const float HELION_GLASS_SOFT_LOD = 2.0;
const float HELION_GLASS_BEAD_FAR_GAIN = 2.2;
const float HELION_GLASS_FAR_RIM = 0.5;
const vec2 HELION_GLASS_STREAK_TILE = vec2(1.0, 0.5);
const float HELION_GLASS_STREAK_TEXELS = 256.0;
const float HELION_GLASS_STREAK_REACH = 0.0234375;
const float HELION_GLASS_STREAK_HALF_WIDTH = 0.018;
const float HELION_GLASS_STREAK_SEED_LEVELS = 255.0;
const float HELION_GLASS_STREAK_SEED_ROW = 7.0;
const float HELION_GLASS_STREAK_SEED_SCALE = 997.0;
const float HELION_GLASS_RUN_DENSITY = 0.7;
const float HELION_GLASS_RUN_PERIOD = 8.0;
const float HELION_GLASS_RUN_SLOW = 0.12;
const float HELION_GLASS_RUN_FAST = 0.4;
const float HELION_GLASS_RUN_STICK = 0.7;
const float HELION_GLASS_RUN_STICK_RATE = 1.7;
const float HELION_GLASS_RUN_HEAD = 0.035;
const float HELION_GLASS_RUN_HEAD_LENS = 0.6;
const float HELION_GLASS_TRAIL_LENGTH = 2.5;
const float HELION_GLASS_TRAIL_WIDTH = 0.45;
const float HELION_GLASS_TRAIL_FADE_FROM = 0.35;
const float HELION_GLASS_TRAIL_FAR_GAIN = 2.0;
const float HELION_GLASS_WIPE_EDGE = 0.15;
const float HELION_GLASS_WIPE_HOLD = 0.2;
const float HELION_GLASS_DROP_BEND = 0.45;
const float HELION_GLASS_DROP_STEEP = 0.8;
const float HELION_GLASS_DROP_RIM_FROM = 0.5;
const float HELION_GLASS_DROP_RIM_DARK = 0.5;
const float HELION_GLASS_DROP_CRESCENT_FROM = 0.35;
const float HELION_GLASS_DROP_CRESCENT = 0.9;
const float HELION_GLASS_DROP_SKY = 2.5;
const float HELION_GLASS_DROP_ROUGHNESS = 0.06;
const float HELION_GLASS_FAR_ROUGHNESS = 0.35;
const float HELION_GLASS_MIN_LENS = 0.15;
const float HELION_GLASS_DROP_GLINT = 0.6;
const float HELION_GLASS_DROP_GATHER_REACH = 0.3;
const float HELION_GLASS_DROP_GATHER_FROM = 0.3;
const float HELION_GLASS_DROP_GATHER = 0.8;
const float HELION_GLASS_MIST = 0.7;
const float HELION_GLASS_MIST_PATCH_SCALE = 0.7;
const float HELION_GLASS_MIST_PATCH_LOW = 0.55;
const float HELION_GLASS_MIST_MICRO_TILE = 3.0;
const float HELION_GLASS_MIST_MICRO_LOW = 0.75;
const float HELION_GLASS_MIST_MICRO_GAIN = 1.2;
const float HELION_GLASS_MIST_HAZE = 0.45;
const float HELION_GLASS_MIST_AMBIENT = 0.4;
const float HELION_GLASS_MIST_LIFT = 1.06;
const float HELION_GLASS_MIST_VEIL = 0.06;
const float HELION_GLASS_MAX_STEP = 0.5;
const float HELION_GLASS_MIN_LENGTH = 1.0e-4;
const float HELION_GLASS_MIN_FACING = 1.0e-4;
const float HELION_GLASS_UPRIGHT = 0.5;
const float HELION_GLASS_AXIS_SNAP = 0.9;
const float HELION_GLASS_FACE_GRID = 16.0;
const float HELION_GLASS_TURN = 6.2831853;
const float HELION_GLASS_CLOUD_SATURATION = 1.35;
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
    if (open <= 0.0) {
        return vec3(0.0);
    }
    float sunUp = smoothstep(-HELION_SUN_HORIZON, HELION_SUN_HORIZON, SunDirection.y);
    float moonUp = smoothstep(-HELION_SUN_HORIZON, HELION_SUN_HORIZON, MoonDirection.y) * (1.0 - sunUp);
    float sunShine = sunUp * helionSpecular(view, normal, SunDirection, roughness);
    vec3 sun = sunShine > 0.0 ? helionSunHue() * HELION_SUN_IRRADIANCE * sunShine : vec3(0.0);
    vec3 moon = HELION_MOON_GLINT * HELION_MOON_IRRADIANCE * moonUp * helionSpecular(view, normal, MoonDirection, roughness);
    return min(sun + moon, vec3(HELION_GLINT_MAX)) * open;
    #else
    return vec3(0.0);
    #endif
}

float helionFallingFoam(vec3 world, vec3 normal, float seconds) {
    vec2 across = normalize(vec2(-normal.z, normal.x) + vec2(HELION_MIN_DIRECTION_FOAM, 0.0));
    vec2 position = vec2(dot(world.xz, across) * HELION_FALL_FOAM_ACROSS, world.y * HELION_FALL_FOAM_ALONG + seconds * HELION_FALL_FOAM_SPEED);
    float streaks = mix(helionNoiseValue(position), helionNoiseValue(position * HELION_FALL_FOAM_DETAIL + HELION_FALL_FOAM_DETAIL_SHIFT), HELION_FALL_FOAM_DETAIL_SHARE);
    return smoothstep(HELION_FOAM_FROM, HELION_FOAM_TO, streaks);
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

vec3 helionGlassFog(vec3 behind, vec3 position) {
    #ifdef HELION_PHYSICAL_SKY
    float amount = total_fog_value(
        fog_spherical_distance(position),
        fog_cylindrical_distance(position),
        AerialEnvironmentalStart,
        AerialEnvironmentalEnd,
        AerialRenderDistanceStart,
        AerialRenderDistanceEnd
    );
    return amount > 0.0 ? mix(behind, helionSkyColor(normalize(position)), clamp(amount, 0.0, 1.0)) : behind;
    #else
    return behind;
    #endif
}

vec3 helionPositionAt(vec2 screen, float depth) {
    vec4 world = WaterInverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    return world.xyz / world.w;
}

float helionGlassFresnel(vec3 view, vec3 normal) {
    return helionSchlick(HELION_GLOSSY_F0, clamp(dot(-view, normal), 0.0, 1.0), 0.0) * ReflectionStrength;
}

vec3 helionGlassReflection(ivec2 pixel, vec4 glassSurface, vec3 view, vec3 normal, bool traced) {
    vec4 rayHit = traced && TracedReflections > HELION_PRESENT ? texelFetch(ReflectionSampler, pixel, 0) : vec4(0.0);
    vec4 screenHit = traced && ScreenReflections > HELION_PRESENT ? texelFetch(ScreenReflectionSampler, pixel, 0) : vec4(0.0);
    if (rayHit.a > HELION_GEOMETRY_PRESENT_THRESHOLD) {
        return mix(rayHit.rgb, screenHit.rgb, screenHit.a);
    }
    if (screenHit.a >= 1.0) {
        return screenHit.rgb;
    }
    float light = glassSurface.w * glassSurface.w;
    vec3 sky = helionReflectedSky(reflect(view, normal), false) * light * (1.0 + LightningFlash * HELION_FLASH_SKY * glassSurface.w);
    return mix(sky, screenHit.rgb, screenHit.a);
}

vec3 helionCloudsBehindGlass(vec3 behind, vec2 screen) {
    if (WaterCameraPosition.w < HELION_PRESENT) {
        return behind;
    }
    vec4 clouds = textureLod(ReflectedCloudSampler, screen, 0.0);
    float cover = 1.0 - clamp(clouds.a, 0.0, 1.0);
    if (cover <= HELION_MIN_CLOUD_COVER) {
        return behind;
    }
    vec3 radiance = clouds.rgb / cover;
    radiance = max(mix(vec3(helionLuminance(radiance)), radiance, HELION_GLASS_CLOUD_SATURATION), 0.0);
    return mix(behind, helionLinearToSrgb(helionNeutral(radiance)), cover);
}

struct HelionGlassRain {
    vec2 bend;
    vec2 facing;
    float cover;
    float rim;
    float detail;
    float wiped;
    float mist;
};

const HelionGlassRain HELION_DRY_GLASS = HelionGlassRain(vec2(0.0), vec2(0.0), 0.0, 0.0, 0.0, 0.0, 0.0);

vec3 helionGlassNormal(vec4 glassSurface) {
    vec3 normal = helionDecodeOctahedral(glassSurface.xy);
    vec3 size = abs(normal);
    float largest = max(size.x, max(size.y, size.z));
    if (largest < HELION_GLASS_AXIS_SNAP) {
        return normal;
    }
    return sign(normal) * step(vec3(largest), size);
}

float helionGlassWetted(vec3 world, vec3 normal, float distance) {
    if (distance > HELION_RAIN_COVER_REACH) {
        return 0.0;
    }
    ivec3 block = ivec3(floor(world - normal * HELION_GLASS_FACE_INSET));
    float fade = 1.0 - smoothstep(HELION_GLASS_RAIN_FADE_FROM, HELION_RAIN_COVER_REACH, distance);
    if (abs(normal.y) > HELION_GLASS_UPRIGHT) {
        return helionRainFalls(block + ivec3(0, 1, 0)) ? fade : 0.0;
    }
    ivec3 side = ivec3(int(round(normal.x)), 0, int(round(normal.z)));
    return helionRainFalls(block + side) || helionRainFalls(block - side) ? fade : 0.0;
}

mat2x3 helionGlassAxes(vec3 normal) {
    if (abs(normal.y) > HELION_GLASS_UPRIGHT) {
        return mat2x3(vec3(1.0, 0.0, 0.0), vec3(0.0, 0.0, 1.0));
    }
    vec2 across = normalize(vec2(-normal.z, normal.x));
    return mat2x3(vec3(across.x, 0.0, across.y), HELION_UP);
}

vec2 helionGlassPlane(vec3 world, vec3 normal) {
    mat2x3 axes = helionGlassAxes(normal);
    return vec2(dot(world, axes[0]), dot(world, axes[1]));
}

vec3 helionOnGlassPlane(vec2 screen, float depth, vec3 position, vec3 normal) {
    vec3 ray = helionPositionAt(screen, depth);
    float facing = dot(ray, normal);
    return ray * (dot(position, normal) / (abs(facing) > HELION_GLASS_MIN_FACING ? facing : HELION_GLASS_MIN_FACING));
}

vec2 helionGlassStep(vec3 offset, mat2x3 axes) {
    vec2 step = vec2(dot(offset, axes[0]), dot(offset, axes[1]));
    float size = length(step);
    return size > HELION_GLASS_MAX_STEP ? step * (HELION_GLASS_MAX_STEP / size) : step;
}

mat2 helionGlassPlaneSteps(vec2 screen, float depth, vec3 position, vec3 normal) {
    vec2 texel = 1.0 / vec2(textureSize(WaterDepthSampler, 0));
    mat2x3 axes = helionGlassAxes(normal);
    vec3 across = helionOnGlassPlane(screen + vec2(texel.x, 0.0), depth, position, normal) - position;
    vec3 up = helionOnGlassPlane(screen + vec2(0.0, texel.y), depth, position, normal) - position;
    return mat2(helionGlassStep(across, axes), helionGlassStep(up, axes));
}

float helionGlassLod(float pixel, float texels) {
    return log2(max(pixel * texels, 1.0));
}

float helionGlassFar(float pixel, float texels) {
    return smoothstep(HELION_GLASS_SHARP_LOD, HELION_GLASS_SOFT_LOD, helionGlassLod(pixel, texels));
}

void helionTakeWater(inout HelionGlassRain rain, float cover, vec2 lens, float reach, float rim, float detail) {
    if (cover <= rain.cover) {
        return;
    }
    float size = length(lens);
    rain.cover = cover;
    rain.rim = rim;
    rain.detail = detail;
    rain.facing = lens / max(size, HELION_GLASS_MIN_LENS);
    rain.bend = -lens * reach * HELION_GLASS_DROP_BEND * cover;
}

void helionAddBeads(inout HelionGlassRain rain, vec2 uv, mat2 steps, vec2 mirror, float texels, float reach, float limit, float pixel, float seconds) {
    vec4 bead = textureGrad(GlassBeadSampler, uv, steps[0], steps[1]);
    float present = 1.0 - smoothstep(limit - HELION_GLASS_SEED_SOFT, limit + HELION_GLASS_SEED_SOFT, bead.a);
    float life = fract(seconds / HELION_GLASS_BEAD_LIFE + bead.a * HELION_GLASS_BEAD_SPREAD);
    float size = smoothstep(0.0, HELION_GLASS_BEAD_GROW, life) * (1.0 - smoothstep(1.0 - HELION_GLASS_BEAD_FADE, 1.0, life)) * present;
    float threshold = 1.0 - size;
    float soft = clamp(pixel / HELION_GLASS_BEAD_RADIUS, HELION_GLASS_EDGE_SOFT, 1.0);
    float far = helionGlassFar(pixel, texels);
    float cover = mix(smoothstep(threshold, threshold + soft, bead.b), clamp(bead.b * size * HELION_GLASS_BEAD_FAR_GAIN, 0.0, 1.0), far);
    cover *= 1.0 - rain.wiped;
    float rim = mix(clamp(1.0 - (bead.b - threshold) / max(size, HELION_GLASS_MIN_LENGTH), 0.0, 1.0), HELION_GLASS_FAR_RIM, far);
    helionTakeWater(rain, cover, (bead.rg * 2.0 - 1.0) * mirror, reach, rim, 1.0 - far);
}

void helionAddRun(inout HelionGlassRain rain, vec2 plane, mat2 steps, float pixel, float seconds, float amount) {
    vec2 uv = plane * HELION_GLASS_STREAK_TILE;
    vec4 streak = textureGrad(GlassStreakSampler, uv, steps[0] * HELION_GLASS_STREAK_TILE, steps[1] * HELION_GLASS_STREAK_TILE);
    float column = floor(uv.x);
    float seed = helionHash(vec2(column * HELION_GLASS_STREAK_SEED_LEVELS + floor(streak.a * HELION_GLASS_STREAK_SEED_LEVELS + 0.5), HELION_GLASS_STREAK_SEED_ROW));
    if (seed >= amount * HELION_GLASS_RUN_DENSITY) {
        return;
    }
    float speed = mix(HELION_GLASS_RUN_SLOW, HELION_GLASS_RUN_FAST, helionHash(vec2(seed * HELION_GLASS_STREAK_SEED_SCALE, column)));
    float travel = speed * (seconds + HELION_GLASS_RUN_STICK / HELION_GLASS_RUN_STICK_RATE * sin(seconds * HELION_GLASS_RUN_STICK_RATE + seed * HELION_GLASS_TURN));
    float along = mod(plane.y + travel + seed * HELION_GLASS_RUN_PERIOD, HELION_GLASS_RUN_PERIOD);
    float fromHead = (along - HELION_GLASS_RUN_HEAD) / HELION_GLASS_RUN_HEAD;
    float head = sqrt(max(1.0 - fromHead * fromHead, 0.0));
    float behind = along - HELION_GLASS_RUN_HEAD;
    float trail = behind > 0.0 ? HELION_GLASS_TRAIL_WIDTH * (1.0 - smoothstep(HELION_GLASS_TRAIL_FADE_FROM, 1.0, behind / HELION_GLASS_TRAIL_LENGTH)) : 0.0;
    float since = behind / (HELION_GLASS_RUN_PERIOD - HELION_GLASS_RUN_HEAD);
    rain.wiped = behind > -HELION_GLASS_RUN_HEAD * 0.5 ? smoothstep(0.0, HELION_GLASS_WIPE_EDGE, streak.b) * (1.0 - smoothstep(HELION_GLASS_WIPE_HOLD, 1.0, since)) : 0.0;
    float width = max(head, trail);
    if (width <= 0.0) {
        return;
    }
    float threshold = 1.0 - width;
    float soft = clamp(pixel / HELION_GLASS_STREAK_HALF_WIDTH, HELION_GLASS_EDGE_SOFT, 1.0);
    float far = helionGlassFar(pixel, HELION_GLASS_STREAK_TEXELS);
    float cover = mix(smoothstep(threshold, threshold + soft, streak.b), clamp(streak.b * width * HELION_GLASS_TRAIL_FAR_GAIN, 0.0, 1.0), far);
    float rim = mix(clamp(1.0 - (streak.b - threshold) / width, 0.0, 1.0), HELION_GLASS_FAR_RIM, far);
    vec2 lens = vec2(streak.r * 2.0 - 1.0, head > trail ? fromHead * head * HELION_GLASS_RUN_HEAD_LENS : 0.0);
    helionTakeWater(rain, cover, lens, HELION_GLASS_STREAK_REACH, rim, 1.0 - far);
}

float helionGlassMistAmount(vec2 plane, mat2 steps, float amount) {
    float patch = mix(HELION_GLASS_MIST_PATCH_LOW, 1.0, clamp(0.5 + helionNoiseValue(plane * HELION_GLASS_MIST_PATCH_SCALE), 0.0, 1.0));
    float micro = textureGrad(GlassBeadSampler, plane * HELION_GLASS_MIST_MICRO_TILE, steps[0] * HELION_GLASS_MIST_MICRO_TILE, steps[1] * HELION_GLASS_MIST_MICRO_TILE).b;
    return clamp(amount * HELION_GLASS_MIST * patch * (HELION_GLASS_MIST_MICRO_LOW + micro * HELION_GLASS_MIST_MICRO_GAIN), 0.0, 1.0);
}

HelionGlassRain helionGlassRain(vec2 plane, mat2 steps, bool upright, float seconds, float amount) {
    HelionGlassRain rain = HELION_DRY_GLASS;
    float pixel = max(length(steps[0]), length(steps[1]));
    if (upright) {
        helionAddRun(rain, plane, steps, pixel, seconds, amount);
    }
    helionAddBeads(rain, plane * HELION_GLASS_BEAD_TILE, steps * HELION_GLASS_BEAD_TILE, vec2(1.0), HELION_GLASS_BEAD_TEXELS, HELION_GLASS_BEAD_REACH, amount * HELION_GLASS_BEAD_DENSITY, pixel, seconds);
    vec2 fine = plane * HELION_GLASS_FINE_MIRROR * HELION_GLASS_FINE_TILE + HELION_GLASS_FINE_SHIFT;
    mat2 fineSteps = mat2(steps[0] * HELION_GLASS_FINE_MIRROR, steps[1] * HELION_GLASS_FINE_MIRROR) * HELION_GLASS_FINE_TILE;
    helionAddBeads(rain, fine, fineSteps, HELION_GLASS_FINE_MIRROR, HELION_GLASS_FINE_TEXELS, HELION_GLASS_FINE_REACH, amount * HELION_GLASS_FINE_DENSITY, pixel, seconds + HELION_GLASS_BEAD_LIFE * 0.5);
    rain.mist = helionGlassMistAmount(plane, steps, amount);
    return rain;
}

vec3 helionGlassHue(vec3 color) {
    float peak = max(color.r, max(color.g, color.b));
    return peak > HELION_GLASS_MIN_CHANNEL ? color / peak : vec3(1.0);
}

vec3 helionOnGlassFace(vec3 position, vec3 normal) {
    float facing = dot(position, normal);
    if (abs(dot(abs(normal), vec3(1.0)) - 1.0) > HELION_GLASS_MIN_LENGTH || abs(facing) < HELION_GLASS_MIN_FACING) {
        return position;
    }
    float camera = dot(WaterCameraPosition.xyz, normal);
    float face = round((dot(position, normal) + camera) * HELION_GLASS_FACE_GRID) / HELION_GLASS_FACE_GRID;
    return position * ((face - camera) / facing);
}

HelionGlassRain helionWindowRain(ivec2 pixel, vec2 screen, float depth, vec4 glassSurface, vec3 measured) {
    vec3 normal = helionGlassNormal(glassSurface);
    vec3 position = helionOnGlassFace(measured, normal);
    vec3 world = position + WaterCameraPosition.xyz;
    float rain = WaterEnvironment.x * helionGlassWetted(world, normal, length(position));
    if (rain <= 0.0 || texelFetch(GlassTintSampler, pixel, 0).a >= HELION_GLASS_FRAME) {
        return HELION_DRY_GLASS;
    }
    mat2 steps = helionGlassPlaneSteps(screen, depth, position, normal);
    return helionGlassRain(helionGlassPlane(world, normal), steps, abs(normal.y) <= HELION_GLASS_UPRIGHT, WaterSeconds, rain);
}

vec2 helionDropBent(vec2 screen, HelionGlassRain rain, vec3 position, vec3 normal) {
    vec4 clip = WaterViewProjection * vec4(position + helionGlassAxes(normal) * rain.bend, 1.0);
    return clip.w > 0.0 ? clamp(clip.xy / clip.w * 0.5 + 0.5, vec2(0.0), vec2(1.0)) : screen;
}

vec3 helionGlassMist(vec3 base, vec2 screen, float skyLight, bool blurred) {
    vec3 misted = blurred ? textureLod(GlassMistSampler, screen, 0.0).rgb : base;
    vec3 haze = mix(vec3(helionLuminance(misted)), helionWaterAmbient() * skyLight, HELION_GLASS_MIST_AMBIENT);
    return min(mix(misted, haze, HELION_GLASS_MIST_HAZE) * HELION_GLASS_MIST_LIFT + HELION_GLASS_MIST_VEIL, vec3(1.0));
}

vec3 helionDropGathered(vec2 screen, HelionGlassRain rain, vec3 position, vec3 normal) {
    vec4 clip = WaterViewProjection * vec4(position - helionGlassAxes(normal) * (rain.facing * HELION_GLASS_DROP_GATHER_REACH), 1.0);
    vec2 far = clip.w > 0.0 ? clamp(clip.xy / clip.w * 0.5 + 0.5, vec2(0.0), vec2(1.0)) : screen;
    return helionSrgbToLinear(textureLod(RefractionSampler, far, 0.0).rgb);
}

vec3 helionWetDrop(vec3 surface, vec3 transmitted, vec3 tint, vec2 screen, vec3 position, vec3 view, vec3 normal, float skyLight, HelionGlassRain rain) {
    float rim = rain.rim * rain.detail;
    float tilt = rim * HELION_GLASS_DROP_STEEP;
    vec3 dropNormal = normalize(normal * sqrt(max(1.0 - tilt * tilt, 0.0)) + helionGlassAxes(normal) * (rain.facing * tilt));
    if (dot(dropNormal, view) > 0.0) {
        dropNormal = -dropNormal;
    }
    float facing = clamp(dot(-view, dropNormal), 0.0, 1.0);
    float sky = max(helionSchlick(HELION_WATER_F0, facing, 0.0) - helionSchlick(HELION_WATER_F0, clamp(dot(-view, normal), 0.0, 1.0), 0.0), 0.0) * HELION_GLASS_DROP_SKY;
    vec3 mirrored = sky > 0.0 ? helionSrgbToLinear(helionReflectedSky(reflect(view, dropNormal), false)) * skyLight * skyLight * sky : vec3(0.0);
    float shade = 1.0 - HELION_GLASS_DROP_RIM_DARK * smoothstep(HELION_GLASS_DROP_RIM_FROM, 1.0, rain.rim);
    float crescent = smoothstep(HELION_GLASS_DROP_CRESCENT_FROM, 1.0, rim) * max(-rain.facing.y, 0.0) * HELION_GLASS_DROP_CRESCENT;
    vec3 glint = helionCelestialGlint(view, dropNormal, mix(HELION_GLASS_FAR_ROUGHNESS, HELION_GLASS_DROP_ROUGHNESS, rain.detail), skyLight) * HELION_GLASS_DROP_GLINT;
    float gather = smoothstep(HELION_GLASS_DROP_GATHER_FROM, 1.0, rim) * HELION_GLASS_DROP_GATHER;
    vec3 gathered = gather > 0.0 ? helionDropGathered(screen, rain, position, normal) * tint * gather : vec3(0.0);
    vec3 drop = surface * shade + transmitted * crescent + gathered + mirrored + glint;
    return mix(surface, drop, rain.cover);
}

vec3 helionRealisticGlass(ivec2 pixel, vec2 screen, vec4 glassSurface, vec3 position, vec3 base, vec3 behindPosition, bool alone, HelionGlassRain rain) {
    vec4 glass = texelFetch(GlassTintSampler, pixel, 0);
    vec3 world = position + WaterCameraPosition.xyz;
    vec3 view = normalize(position);
    vec3 normal = helionGlassNormal(glassSurface);
    bool frame = glass.a >= HELION_GLASS_FRAME;
    bool skyBehind = length(behindPosition - position) >= HELION_WATER_MAX_THICKNESS - HELION_SKY_BEHIND_MARGIN;
    if (alone) {
        vec2 wave = vec2(
            helionNoiseValue(world.xz * HELION_GLASS_WAVE_SCALE + world.y * HELION_GLASS_WAVE_HEIGHT),
            helionNoiseValue(world.zy * HELION_GLASS_WAVE_SCALE - world.x * HELION_GLASS_WAVE_HEIGHT)
        ) * HELION_GLASS_WAVE;
        vec2 bent = clamp(helionDropBent(screen, rain, position, normal) + wave, vec2(0.0), vec2(1.0));
        base = textureLod(RefractionSampler, bent, 0.0).rgb;
        if (skyBehind) {
            base = helionCloudsBehindGlass(base, bent);
        }
    }
    float mist = rain.mist * (1.0 - max(rain.cover, rain.wiped));
    if (mist > 0.0) {
        base = mix(base, helionGlassMist(base, screen, glassSurface.w, alone && !skyBehind), mist);
    }
    if (!frame && !skyBehind) {
        base = helionGlassFog(base, behindPosition);
    }
    vec3 color = helionSrgbToLinear(glass.rgb);
    vec3 hue = helionGlassHue(color);
    float saturation = 1.0 - min(hue.r, min(hue.g, hue.b));
    float darkness = 1.0 - smoothstep(0.0, HELION_GLASS_DARK_FROM, max(color.r, max(color.g, color.b)));
    vec3 tint = mix(vec3(1.0), hue, mix(HELION_GLASS_CLEAR_TINT, HELION_GLASS_STAIN, smoothstep(0.0, HELION_GLASS_SATURATED, saturation)));
    tint *= mix(1.0, HELION_GLASS_DARK_SHADE, darkness);
    vec3 transmitted = helionSrgbToLinear(base) * tint;
    if (frame) {
        transmitted = mix(transmitted, color, HELION_GLASS_FRAME_SHARE);
    }
    vec3 reflected = helionSrgbToLinear(helionGlassReflection(pixel, glassSurface, view, normal, alone));
    float fresnel = max(helionGlassFresnel(view, normal), HELION_GLASS_MIN_REFLECTANCE);
    vec3 surface = mix(transmitted, reflected, fresnel);
    if (rain.cover > 0.0) {
        surface = helionWetDrop(surface, transmitted, tint, screen, position, view, normal, glassSurface.w, rain);
    }
    return helionLinearToSrgb(surface);
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
        foam = helionFallingFoam(world, normal, WaterSeconds) * HELION_FALL_FOAM;
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

vec4 helionWaterPixel(ivec2 pixel, vec2 screen, vec4 surface, float depth, bool realistic) {
    vec3 normal = helionDecodeOctahedral(surface.xy);
    if (!realistic && normal.y < HELION_MIN_FACING) {
        discard;
    }
    vec3 position = helionPositionAt(screen, depth);
    vec3 view = normalize(position);
    if (realistic && normal.y < -HELION_MIN_FACING) {
        return vec4(helionUnderwaterSurface(pixel, screen, surface, normal, view), 1.0);
    }
    vec3 reflected = reflect(view, normal);
    float facing = clamp(dot(-view, normal), 0.0, 1.0);
    float fresnel = helionSchlick(HELION_WATER_F0, facing, 0.0) * ReflectionStrength;
    vec4 traced = TracedReflections > HELION_PRESENT ? texelFetch(ReflectionSampler, pixel, 0) : vec4(0.0);
    vec3 sky = helionReflectedSky(reflected, realistic) * (1.0 + LightningFlash * HELION_FLASH_SKY * surface.w);
    vec3 fallback = traced.a > HELION_GEOMETRY_PRESENT_THRESHOLD ? traced.rgb : sky;
    vec4 screenHit = ScreenReflections > HELION_PRESENT ? texelFetch(ScreenReflectionSampler, pixel, 0) : vec4(0.0);
    vec3 reflection = mix(fallback, screenHit.rgb, screenHit.a);
    if (realistic) {
        float hit = max(screenHit.a, traced.a > HELION_GEOMETRY_PRESENT_THRESHOLD ? 1.0 : 0.0);
        return vec4(helionRealisticWater(pixel, screen, surface, normal, position, reflection, hit), 1.0);
    }
    float transmitted = exp(-surface.z * HELION_WATER_ABSORPTION);
    vec3 scattered = HELION_WATER_SCATTER * helionWaterAmbient() * surface.w * surface.w;
    float kept = transmitted * (1.0 - fresnel);
    return vec4(reflection * fresnel + scattered * (1.0 - transmitted) * (1.0 - fresnel), 1.0 - kept);
}

vec4 helionPlainGlass(ivec2 pixel, vec4 glassSurface, vec2 screen, float depth, bool alone) {
    vec3 view = normalize(helionPositionAt(screen, depth));
    vec3 normal = helionGlassNormal(glassSurface);
    float fresnel = helionGlassFresnel(view, normal);
    return vec4(helionGlassReflection(pixel, glassSurface, view, normal, alone) * fresnel, fresnel);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 surface = texelFetch(WaterSurfaceSampler, pixel, 0);
    vec4 glassSurface = texelFetch(GlassSurfaceSampler, pixel, 0);
    bool water = helionIsWater(surface);
    bool glass = helionIsGlossy(glassSurface);
    if (!water && !glass) {
        discard;
    }
    vec2 screen = (vec2(pixel) + 0.5) / vec2(textureSize(WaterDepthSampler, 0));
    float waterDepth = texelFetch(WaterDepthSampler, pixel, 0).r;
    float glassDepth = texelFetch(GlassDepthSampler, pixel, 0).r;
    bool glassInFront = glass && (!water || glassDepth >= waterDepth);
    bool realistic = RealisticWater > HELION_PRESENT;
    if (!realistic) {
        fragColor = glassInFront ? helionPlainGlass(pixel, glassSurface, screen, glassDepth, !water) : helionWaterPixel(pixel, screen, surface, waterDepth, false);
        return;
    }
    vec3 glassPosition = helionPositionAt(screen, glassDepth);
    HelionGlassRain rain = glassInFront ? helionWindowRain(pixel, screen, glassDepth, glassSurface, glassPosition) : HELION_DRY_GLASS;
    vec3 color;
    if (water) {
        vec2 waterScreen = helionDropBent(screen, rain, glassPosition, helionGlassNormal(glassSurface));
        ivec2 waterPixel = ivec2(waterScreen * vec2(textureSize(WaterDepthSampler, 0)));
        vec4 bentSurface = texelFetch(WaterSurfaceSampler, waterPixel, 0);
        bool bent = glassInFront && helionIsWater(bentSurface);
        color = bent
            ? helionWaterPixel(waterPixel, waterScreen, bentSurface, texelFetch(WaterDepthSampler, waterPixel, 0).r, true).rgb
            : helionWaterPixel(pixel, screen, surface, waterDepth, true).rgb;
    } else {
        color = textureLod(RefractionSampler, screen, 0.0).rgb;
    }
    if (glassInFront) {
        vec3 behindPosition = water ? helionPositionAt(screen, waterDepth) : glassPosition + normalize(glassPosition) * helionGlossyThickness(glassSurface);
        color = helionRealisticGlass(pixel, screen, glassSurface, glassPosition, color, behindPosition, !water, rain);
    }
    fragColor = vec4(color, 1.0);
}
