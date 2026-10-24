#ifndef HELION_WETNESS_GLSL
#define HELION_WETNESS_GLSL

#include <helion:helion_geometry.glsl>
#include <helion:helion_hash.glsl>

struct HelionWetSurface {
    float wetness;
    float puddle;
    float film;
    float darkening;
    float sheen;
    vec3 normal;
};

const float HELION_WET_NOISE_PERIOD = 1024.0;
const float HELION_WET_EXPOSED_FROM = 14.0 / 15.0;
const float HELION_WET_EXPOSED_TO = 14.9 / 15.0;
const float HELION_WET_WALL_SHARE = 0.6;
const float HELION_WET_FLAT_FROM = 0.9;
const float HELION_WET_FLAT_TO = 0.98;
const float HELION_PUDDLE_LARGE_SCALE = 1.0 / 4.0;
const float HELION_PUDDLE_MEDIUM_SCALE = 1.0 / 2.0;
const float HELION_PUDDLE_SMALL_SCALE = 1.0;
const vec3 HELION_PUDDLE_SHARES = vec3(0.55, 0.3, 0.15);
const float HELION_PUDDLE_DRY_THRESHOLD = 1.05;
const float HELION_PUDDLE_FULL_THRESHOLD = 0.55;
const float HELION_PUDDLE_EDGE = 0.03;
const float HELION_ORDINARY_DARKENING = 0.45;
const float HELION_POROUS_DARKENING = 0.55;
const float HELION_GLOSSY_DARKENING = 0.25;
const float HELION_PUDDLE_DARKENING = 0.15;
const float HELION_ORDINARY_SHEEN = 0.5;
const float HELION_POROUS_SHEEN = 0.08;
const float HELION_GLOSSY_SHEEN = 0.6;
const float HELION_RIPPLE_DENSITY = 3.0;
const float HELION_RIPPLE_RATE = 1.3;
const float HELION_RIPPLE_RADIUS = 0.3;
const float HELION_RIPPLE_FREQUENCY = 40.0;
const float HELION_RIPPLE_WIDTH = 18.0;
const float HELION_RIPPLE_STRENGTH = 0.2;
const float HELION_RIPPLE_MIN_DISTANCE = 1.0e-3;
const vec2 HELION_RIPPLE_OFFSET_X = vec2(3.1, 1.7);
const vec2 HELION_RIPPLE_OFFSET_Z = vec2(7.7, 5.3);
const float HELION_STREAK_WIDTH = 3.0;
const float HELION_STREAK_LENGTH = 0.35;
const float HELION_STREAK_SPEED = 0.9;
const float HELION_STREAK_FROM = 0.55;
const float HELION_STREAK_TO = 0.75;
const float HELION_STREAK_DARKENING = 0.25;
const float HELION_STREAK_SHEEN = 0.7;
const float HELION_WALL_UP = 0.5;
const float HELION_MUD_DARKENING = 0.5;
const float HELION_MUD_SHEEN = 0.25;
const float HELION_MUD_PUDDLES = 0.7;
const float HELION_MUD_RAIN = 0.4;
const float HELION_FILM_WET_FROM = 0.5;
const float HELION_FILM_RAIN = 0.3;
const float HELION_MUD_FILM = 0.4;
const float HELION_FILM_DARKENING = 0.08;
const HelionWetSurface HELION_DRY_SURFACE = HelionWetSurface(0.0, 0.0, 0.0, 0.0, 0.0, vec3(0.0, 1.0, 0.0));

float helionWetHash(vec2 cell, float periodCells) {
    vec2 wrapped = mod(cell, periodCells);
    return helionHash(wrapped);
}

float helionWetNoise(vec2 position, float scale) {
    float periodCells = HELION_WET_NOISE_PERIOD * scale;
    vec2 scaled = position * scale;
    vec2 cell = floor(scaled);
    vec2 inside = fract(scaled);
    vec2 smoothed = inside * inside * (3.0 - 2.0 * inside);
    float corner00 = helionWetHash(cell, periodCells);
    float corner10 = helionWetHash(cell + vec2(1.0, 0.0), periodCells);
    float corner01 = helionWetHash(cell + vec2(0.0, 1.0), periodCells);
    float corner11 = helionWetHash(cell + vec2(1.0, 1.0), periodCells);
    return mix(mix(corner00, corner10, smoothed.x), mix(corner01, corner11, smoothed.x), smoothed.y);
}

float helionPuddleMask(vec2 position, float puddles) {
    float noise = dot(HELION_PUDDLE_SHARES, vec3(
        helionWetNoise(position, HELION_PUDDLE_LARGE_SCALE),
        helionWetNoise(position, HELION_PUDDLE_MEDIUM_SCALE),
        helionWetNoise(position, HELION_PUDDLE_SMALL_SCALE)
    ));
    float threshold = mix(HELION_PUDDLE_DRY_THRESHOLD, HELION_PUDDLE_FULL_THRESHOLD, puddles);
    return smoothstep(threshold, threshold + HELION_PUDDLE_EDGE, noise);
}

vec2 helionRainRipples(vec2 position, float seconds) {
    float periodCells = HELION_WET_NOISE_PERIOD * HELION_RIPPLE_DENSITY;
    vec2 home = floor(position * HELION_RIPPLE_DENSITY);
    vec2 tilt = vec2(0.0);
    for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
            vec2 cell = home + vec2(x, z);
            vec2 jitter = vec2(helionWetHash(cell + HELION_RIPPLE_OFFSET_X, periodCells), helionWetHash(cell + HELION_RIPPLE_OFFSET_Z, periodCells));
            vec2 offset = position - (cell + jitter) / HELION_RIPPLE_DENSITY;
            float distanceToDrop = length(offset);
            float age = fract(seconds * HELION_RIPPLE_RATE + helionWetHash(cell, periodCells));
            float fromRing = distanceToDrop - age * HELION_RIPPLE_RADIUS;
            float ring = sin(fromRing * HELION_RIPPLE_FREQUENCY) * exp(-fromRing * fromRing * HELION_RIPPLE_WIDTH * HELION_RIPPLE_WIDTH) * (1.0 - age);
            tilt += distanceToDrop > HELION_RIPPLE_MIN_DISTANCE ? offset / distanceToDrop * ring : vec2(0.0);
        }
    }
    return tilt * HELION_RIPPLE_STRENGTH;
}

float helionWallStreaks(vec3 normal, vec3 worldPosition, float seconds) {
    vec2 across = normalize(vec2(-normal.z, normal.x) + vec2(1.0e-4, 0.0));
    vec2 position = vec2(dot(worldPosition.xz, across) * HELION_STREAK_WIDTH, worldPosition.y * HELION_STREAK_LENGTH + seconds * HELION_STREAK_SPEED);
    return smoothstep(HELION_STREAK_FROM, HELION_STREAK_TO, helionWetNoise(position, 1.0));
}

HelionWetSurface helionWetSurface(vec4 light, vec3 normal, vec3 worldPosition, vec4 wetness, vec2 simulatedSlope, bool simulated) {
    if (wetness.x <= 0.0 && wetness.y <= 0.0 || helionIsDryGeometry(light.a)) {
        return HELION_DRY_SURFACE;
    }
    bool porous = helionIsGeometryMaterial(light.a, HELION_GEOMETRY_POROUS);
    bool muddy = helionIsGeometryMaterial(light.a, HELION_GEOMETRY_MUDDY);
    bool glossy = helionIsGeometryMaterial(light.a, HELION_GEOMETRY_GLOSSY);
    float facingUp = smoothstep(HELION_WET_FLAT_FROM, HELION_WET_FLAT_TO, normal.y);
    float exposure = smoothstep(HELION_WET_EXPOSED_FROM, HELION_WET_EXPOSED_TO, light.g) * mix(HELION_WET_WALL_SHARE, 1.0, facingUp);
    float wet = wetness.x * exposure;
    float streak = porous || normal.y > HELION_WALL_UP ? 0.0 : helionWallStreaks(normal, worldPosition, wetness.w) * wet * wetness.z;
    float mudPuddles = wetness.y * HELION_MUD_PUDDLES * smoothstep(0.0, HELION_MUD_RAIN, wetness.z);
    float puddle = porous ? 0.0 : helionPuddleMask(worldPosition.xz, muddy ? mudPuddles : wetness.y) * facingUp * exposure;
    float film = porous ? 0.0 : smoothstep(HELION_FILM_WET_FROM, 1.0, wetness.x) * smoothstep(0.0, HELION_FILM_RAIN, wetness.z) * facingUp * exposure * (muddy ? HELION_MUD_FILM : 1.0);
    float layer = max(puddle, film);
    float darkening = muddy ? HELION_MUD_DARKENING : porous ? HELION_POROUS_DARKENING : glossy ? HELION_GLOSSY_DARKENING : HELION_ORDINARY_DARKENING;
    float sheen = muddy ? HELION_MUD_SHEEN : porous ? HELION_POROUS_SHEEN : glossy ? HELION_GLOSSY_SHEEN : HELION_ORDINARY_SHEEN;
    vec2 ripples = layer <= 0.0 ? vec2(0.0) : simulated ? simulatedSlope : helionRainRipples(worldPosition.xz, wetness.w) * wetness.z;
    vec3 puddleNormal = normalize(vec3(-ripples.x, 1.0, -ripples.y));
    return HelionWetSurface(
        wet,
        puddle,
        film,
        min(wet * darkening + puddle * HELION_PUDDLE_DARKENING + film * HELION_FILM_DARKENING + streak * HELION_STREAK_DARKENING, 1.0),
        mix(wet * sheen + streak * HELION_STREAK_SHEEN, 1.0, layer),
        normalize(mix(normal, puddleNormal, layer))
    );
}

#endif
