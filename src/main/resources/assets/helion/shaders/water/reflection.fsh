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
const int HELION_RAIN_COVER_MASK = 63;
const float HELION_RAIN_COVER_REACH = 28.0;
const float HELION_GLASS_FACE_INSET = 0.5;
const float HELION_GLASS_LARGE_CELLS = 8.0;
const float HELION_GLASS_LARGE_DENSITY = 0.45;
const float HELION_GLASS_MEDIUM_CELLS = 18.0;
const float HELION_GLASS_MEDIUM_DENSITY = 0.7;
const float HELION_GLASS_BEAD_SMALL = 0.14;
const float HELION_GLASS_BEAD_LARGE = 0.32;
const float HELION_GLASS_BEAD_SQUASH = 0.85;
const float HELION_GLASS_BEAD_WOBBLE = 0.12;
const float HELION_GLASS_BEAD_LOBES = 3.0;
const float HELION_GLASS_RUN_CELLS = 6.0;
const float HELION_GLASS_RUN_DENSITY = 0.75;
const float HELION_GLASS_RUN_PERIOD = 6.0;
const float HELION_GLASS_RUN_SLOW = 0.06;
const float HELION_GLASS_RUN_FAST = 0.2;
const float HELION_GLASS_RUN_WANDER = 0.3;
const float HELION_GLASS_RUN_WIGGLE = 0.025;
const float HELION_GLASS_RUN_WIGGLE_RATE = 7.0;
const float HELION_GLASS_RUN_MEANDER = 0.05;
const float HELION_GLASS_RUN_MEANDER_RATE = 1.7;
const vec2 HELION_GLASS_RUN_HEAD = vec2(0.03, 0.04);
const float HELION_GLASS_TRAIL_LENGTH = 3.0;
const float HELION_GLASS_TRAIL_TAIL = 0.3;
const float HELION_GLASS_TRAIL_WIDTH = 0.015;
const float HELION_GLASS_TRAIL_THIN = 0.4;
const float HELION_GLASS_TRAIL_SWELL = 0.3;
const float HELION_GLASS_TRAIL_SWELL_RATE = 5.0;
const float HELION_GLASS_TRAIL_BEND = 2.0;
const float HELION_GLASS_TRAIL_CORE = 0.35;
const float HELION_GLASS_TRAIL_RIM_FROM = 0.4;
const float HELION_GLASS_TRAIL_GLINT_SIZE = 0.25;
const float HELION_GLASS_DROP_BEND = 2.5;
const float HELION_GLASS_DROP_SHADE = 0.25;
const float HELION_GLASS_DROP_GLINT = 0.7;
const float HELION_GLASS_DROP_CAUSTIC = 0.6;
const vec2 HELION_GLASS_DROP_HIGHLIGHT = vec2(-0.3, 0.45);
const float HELION_GLASS_DROP_HIGHLIGHT_SIZE = 0.22;
const float HELION_GLASS_DROP_RIM_FROM = 0.6;
const float HELION_GLASS_DROP_CAUSTIC_FROM = 0.45;
const float HELION_GLASS_DROP_MIN_EDGE = 0.05;
const float HELION_GLASS_DROP_MIN_PIXELS = 3.0;
const float HELION_GLASS_DROP_FULL_PIXELS = 6.0;
const float HELION_GLASS_GLINT_PIXELS = 9.0;
const float HELION_GLASS_GLINT_LIFT = 1.3;
const float HELION_GLASS_MIN_FOOTPRINT = 1.0e-5;
const float HELION_GLASS_MIN_FACING = 1.0e-4;
const float HELION_GLASS_UPRIGHT = 0.5;
const vec2 HELION_GLASS_SEED_A = vec2(17.3, 5.1);
const vec2 HELION_GLASS_SEED_B = vec2(41.7, 23.9);
const vec2 HELION_GLASS_SEED_C = vec2(7.1, 61.3);
const vec2 HELION_GLASS_SEED_D = vec2(29.3, 13.7);
const float HELION_GLASS_TURN = 6.2831853;
const float HELION_GLASS_SEED_COLUMN = 3.1;
const float HELION_GLASS_SEED_SPEED = 9.7;
const float HELION_GLASS_SEED_SHIFT = 5.3;
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
    float sunUp = smoothstep(-HELION_SUN_HORIZON, HELION_SUN_HORIZON, SunDirection.y);
    float moonUp = smoothstep(-HELION_SUN_HORIZON, HELION_SUN_HORIZON, MoonDirection.y) * (1.0 - sunUp);
    vec3 sun = helionSunHue() * HELION_SUN_IRRADIANCE * sunUp * helionSpecular(view, normal, SunDirection, roughness);
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
    return mix(behind, helionSkyColor(normalize(position)), clamp(amount, 0.0, 1.0));
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
    float light = glassSurface.w * glassSurface.w;
    vec3 sky = helionReflectedSky(reflect(view, normal), false) * light * (1.0 + LightningFlash * HELION_FLASH_SKY * glassSurface.w);
    if (!traced) {
        return sky;
    }
    vec4 rayHit = TracedReflections > HELION_PRESENT ? texelFetch(ReflectionSampler, pixel, 0) : vec4(0.0);
    vec4 screenHit = ScreenReflections > HELION_PRESENT ? texelFetch(ScreenReflectionSampler, pixel, 0) : vec4(0.0);
    return mix(rayHit.a > HELION_GEOMETRY_PRESENT_THRESHOLD ? rayHit.rgb : sky, screenHit.rgb, screenHit.a);
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

struct HelionGlassDrops {
    vec2 bend;
    float cover;
    float glint;
    float caustic;
};

bool helionRainFalls(ivec3 cell) {
    return texelFetch(RainCoverSampler, cell.xz & HELION_RAIN_COVER_MASK, 0).r <= float(cell.y);
}

float helionGlassWetted(vec3 world, vec3 normal, float distance) {
    if (distance > HELION_RAIN_COVER_REACH) {
        return 0.0;
    }
    ivec3 block = ivec3(floor(world - normal * HELION_GLASS_FACE_INSET));
    if (abs(normal.y) > HELION_GLASS_UPRIGHT) {
        return helionRainFalls(block + ivec3(0, 1, 0)) ? 1.0 : 0.0;
    }
    ivec3 side = ivec3(int(round(normal.x)), 0, int(round(normal.z)));
    return helionRainFalls(block + side) || helionRainFalls(block - side) ? 1.0 : 0.0;
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

void helionAddDrop(inout HelionGlassDrops drops, vec2 local, float size, float footprint, float shape) {
    float pixels = size / max(footprint, HELION_GLASS_MIN_FOOTPRINT);
    float visible = smoothstep(HELION_GLASS_DROP_MIN_PIXELS, HELION_GLASS_DROP_FULL_PIXELS, pixels);
    if (visible <= 0.0) {
        return;
    }
    float distance = length(local) * (1.0 + HELION_GLASS_BEAD_WOBBLE * sin(atan(local.y, local.x) * HELION_GLASS_BEAD_LOBES + shape * HELION_GLASS_TURN));
    float edge = max(1.0 / pixels, HELION_GLASS_DROP_MIN_EDGE);
    float cover = (1.0 - smoothstep(1.0 - edge, 1.0, distance)) * visible;
    if (cover <= 0.0) {
        return;
    }
    drops.bend -= local * size * cover;
    drops.cover = max(drops.cover, cover * smoothstep(HELION_GLASS_DROP_RIM_FROM, 1.0, distance));
    drops.caustic = max(drops.caustic, cover * smoothstep(HELION_GLASS_DROP_CAUSTIC_FROM, 1.0, distance) * max(-local.y, 0.0));
    float highlight = 1.0 - smoothstep(0.0, HELION_GLASS_DROP_HIGHLIGHT_SIZE + edge, length(local - HELION_GLASS_DROP_HIGHLIGHT));
    drops.glint = max(drops.glint, highlight * cover * smoothstep(HELION_GLASS_DROP_FULL_PIXELS, HELION_GLASS_GLINT_PIXELS, pixels));
}

void helionAddBeads(inout HelionGlassDrops drops, vec2 plane, float cells, float density, float footprint) {
    vec2 beads = plane * cells;
    vec2 cell = floor(beads);
    if (helionHash(cell) >= density) {
        return;
    }
    float radius = mix(HELION_GLASS_BEAD_SMALL, HELION_GLASS_BEAD_LARGE, helionHash(cell + HELION_GLASS_SEED_C));
    vec2 offset = (vec2(helionHash(cell + HELION_GLASS_SEED_A), helionHash(cell + HELION_GLASS_SEED_B)) - 0.5) * (1.0 - 2.0 * radius * (1.0 + HELION_GLASS_BEAD_WOBBLE));
    vec2 local = (fract(beads) - 0.5 - offset) / radius;
    local.y /= HELION_GLASS_BEAD_SQUASH;
    helionAddDrop(drops, local, radius / cells, footprint, helionHash(cell + HELION_GLASS_SEED_D));
}

void helionAddRun(inout HelionGlassDrops drops, vec2 plane, float footprint, float seconds, float amount) {
    float column = floor(plane.x * HELION_GLASS_RUN_CELLS);
    float seed = helionHash(vec2(column, HELION_GLASS_SEED_COLUMN));
    if (seed >= amount * HELION_GLASS_RUN_DENSITY) {
        return;
    }
    float speed = mix(HELION_GLASS_RUN_SLOW, HELION_GLASS_RUN_FAST, helionHash(vec2(column, HELION_GLASS_SEED_SPEED)));
    float path = sin(plane.y * HELION_GLASS_RUN_WIGGLE_RATE + seed * HELION_GLASS_TURN) * HELION_GLASS_RUN_WIGGLE
        + helionNoiseValue(vec2(column, plane.y * HELION_GLASS_RUN_MEANDER_RATE)) * HELION_GLASS_RUN_MEANDER;
    float across = (fract(plane.x * HELION_GLASS_RUN_CELLS) - 0.5) / HELION_GLASS_RUN_CELLS
        - (helionHash(vec2(column, HELION_GLASS_SEED_SHIFT)) - 0.5) * HELION_GLASS_RUN_WANDER / HELION_GLASS_RUN_CELLS - path;
    float along = mod(plane.y + seconds * speed + seed * HELION_GLASS_RUN_PERIOD, HELION_GLASS_RUN_PERIOD) - HELION_GLASS_RUN_PERIOD * 0.5;
    vec2 head = vec2(across, along) / HELION_GLASS_RUN_HEAD;
    if (dot(head, head) < 1.0) {
        helionAddDrop(drops, head, HELION_GLASS_RUN_HEAD.x, footprint, seed);
        return;
    }
    if (along <= 0.0 || along >= HELION_GLASS_TRAIL_LENGTH) {
        return;
    }
    float fade = 1.0 - along / HELION_GLASS_TRAIL_LENGTH;
    float swell = 1.0 + HELION_GLASS_TRAIL_SWELL * helionNoiseValue(vec2(plane.y * HELION_GLASS_TRAIL_SWELL_RATE, column));
    float width = HELION_GLASS_TRAIL_WIDTH * mix(HELION_GLASS_TRAIL_THIN, 1.0, fade) * max(swell, HELION_GLASS_TRAIL_THIN);
    float visible = smoothstep(HELION_GLASS_DROP_MIN_PIXELS, HELION_GLASS_DROP_FULL_PIXELS, width / max(footprint, HELION_GLASS_MIN_FOOTPRINT));
    float inside = across / width;
    float trail = (1.0 - smoothstep(1.0 - max(footprint / width, HELION_GLASS_DROP_MIN_EDGE), 1.0, abs(inside))) * visible * smoothstep(0.0, HELION_GLASS_TRAIL_TAIL, fade);
    if (trail <= 0.0) {
        return;
    }
    drops.bend.x -= inside * width * HELION_GLASS_TRAIL_BEND * trail;
    drops.cover = max(drops.cover, trail * mix(HELION_GLASS_TRAIL_CORE, 1.0, smoothstep(HELION_GLASS_TRAIL_RIM_FROM, 1.0, abs(inside))));
    drops.glint = max(drops.glint, trail * (1.0 - smoothstep(0.0, HELION_GLASS_TRAIL_GLINT_SIZE, abs(inside - HELION_GLASS_DROP_HIGHLIGHT.x))));
}

HelionGlassDrops helionGlassDrops(vec2 plane, float footprint, bool upright, float seconds, float amount) {
    HelionGlassDrops drops = HelionGlassDrops(vec2(0.0), 0.0, 0.0, 0.0);
    helionAddBeads(drops, plane, HELION_GLASS_LARGE_CELLS, amount * HELION_GLASS_LARGE_DENSITY, footprint);
    helionAddBeads(drops, plane + HELION_GLASS_SEED_A, HELION_GLASS_MEDIUM_CELLS, amount * HELION_GLASS_MEDIUM_DENSITY, footprint);
    if (upright) {
        helionAddRun(drops, plane, footprint, seconds, amount);
    }
    return drops;
}

vec3 helionGlassHue(vec3 color) {
    float peak = max(color.r, max(color.g, color.b));
    return peak > HELION_GLASS_MIN_CHANNEL ? color / peak : vec3(1.0);
}

vec3 helionOnGlassPlane(vec2 screen, float depth, vec3 position, vec3 normal) {
    vec3 ray = helionPositionAt(screen, depth);
    float facing = dot(ray, normal);
    return ray * (dot(position, normal) / (abs(facing) > HELION_GLASS_MIN_FACING ? facing : HELION_GLASS_MIN_FACING));
}

float helionGlassFootprint(vec2 screen, float depth, vec3 position, vec3 normal) {
    vec2 texel = 1.0 / vec2(textureSize(WaterDepthSampler, 0));
    vec3 across = helionOnGlassPlane(screen + vec2(texel.x, 0.0), depth, position, normal) - position;
    vec3 up = helionOnGlassPlane(screen + vec2(0.0, texel.y), depth, position, normal) - position;
    return max(length(across), length(up));
}

HelionGlassDrops helionWindowDrops(ivec2 pixel, vec2 screen, float depth, vec4 glassSurface, vec3 position) {
    vec3 world = position + WaterCameraPosition.xyz;
    vec3 normal = helionDecodeOctahedral(glassSurface.xy);
    vec2 plane = helionGlassPlane(world, normal);
    float rain = WaterEnvironment.x * helionGlassWetted(world, normal, length(position));
    if (rain <= 0.0 || texelFetch(GlassTintSampler, pixel, 0).a >= HELION_GLASS_FRAME) {
        return HelionGlassDrops(vec2(0.0), 0.0, 0.0, 0.0);
    }
    return helionGlassDrops(plane, helionGlassFootprint(screen, depth, position, normal), abs(normal.y) <= HELION_GLASS_UPRIGHT, WaterSeconds, rain);
}

vec2 helionDropBent(vec2 screen, HelionGlassDrops drops, vec3 position, vec3 normal) {
    vec4 clip = WaterViewProjection * vec4(position + helionGlassAxes(normal) * drops.bend * HELION_GLASS_DROP_BEND, 1.0);
    return clip.w > 0.0 ? clamp(clip.xy / clip.w * 0.5 + 0.5, vec2(0.0), vec2(1.0)) : screen;
}

vec3 helionRealisticGlass(ivec2 pixel, vec2 screen, vec4 glassSurface, vec3 position, vec3 base, vec3 behindPosition, bool alone, HelionGlassDrops drops) {
    vec4 glass = texelFetch(GlassTintSampler, pixel, 0);
    vec3 world = position + WaterCameraPosition.xyz;
    vec3 view = normalize(position);
    vec3 normal = helionDecodeOctahedral(glassSurface.xy);
    bool frame = glass.a >= HELION_GLASS_FRAME;
    bool skyBehind = length(behindPosition - position) >= HELION_WATER_MAX_THICKNESS - HELION_SKY_BEHIND_MARGIN;
    if (alone) {
        vec2 wave = vec2(
            helionNoiseValue(world.xz * HELION_GLASS_WAVE_SCALE + world.y * HELION_GLASS_WAVE_HEIGHT),
            helionNoiseValue(world.zy * HELION_GLASS_WAVE_SCALE - world.x * HELION_GLASS_WAVE_HEIGHT)
        ) * HELION_GLASS_WAVE;
        vec2 bent = clamp(helionDropBent(screen, drops, position, normal) + wave, vec2(0.0), vec2(1.0));
        base = textureLod(RefractionSampler, bent, 0.0).rgb;
        if (skyBehind) {
            base = helionCloudsBehindGlass(base, bent);
        }
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
    surface *= 1.0 - drops.cover * HELION_GLASS_DROP_SHADE;
    surface += transmitted * drops.caustic * HELION_GLASS_DROP_CAUSTIC + max(reflected, vec3(helionLuminance(transmitted) * HELION_GLASS_GLINT_LIFT)) * drops.glint * HELION_GLASS_DROP_GLINT;
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
    vec3 normal = helionDecodeOctahedral(glassSurface.xy);
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
    HelionGlassDrops drops = glassInFront ? helionWindowDrops(pixel, screen, glassDepth, glassSurface, glassPosition) : HelionGlassDrops(vec2(0.0), 0.0, 0.0, 0.0);
    vec3 color;
    if (water) {
        vec2 waterScreen = helionDropBent(screen, drops, glassPosition, helionDecodeOctahedral(glassSurface.xy));
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
        color = helionRealisticGlass(pixel, screen, glassSurface, glassPosition, color, behindPosition, !water, drops);
    }
    fragColor = vec4(color, 1.0);
}
