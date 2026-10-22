#ifndef HELION_CELESTIAL_GLSL
#define HELION_CELESTIAL_GLSL

#include <helion:helion_color.glsl>
#include <helion:helion_twilight.glsl>

const vec3 HELION_CELESTIAL_AXIS = vec3(0.0, 0.0, 1.0);
const float HELION_MIN_DISC_EDGE = 1.0e-5;

const float HELION_SUN_ANGULAR_RADIUS = 0.0079;
const float HELION_SUN_DISC_RADIANCE = 6.0;
const vec3 HELION_SUN_LIMB_DARKENING = vec3(0.45, 0.6, 0.72);
const float HELION_SUN_GLARE = 0.6;
const float HELION_SUN_GLARE_WIDTH = 0.005;
const float HELION_SUN_AUREOLE = 0.04;
const float HELION_SUN_AUREOLE_WIDTH = 0.012;
const float HELION_SUN_BLEACH_LOW = 1.0;
const float HELION_SUN_BLEACH_HIGH = 8.0;

const float HELION_MOON_ANGULAR_RADIUS = 0.026;
const float HELION_MOON_RADIANCE = 0.9;
const vec3 HELION_MOON_HIGHLAND = vec3(0.95, 0.93, 0.88);
const vec3 HELION_MOON_MARE = vec3(0.5, 0.51, 0.55);
const vec3 HELION_EARTHSHINE = vec3(0.012, 0.016, 0.024);
const float HELION_MOON_HALO = 0.08;
const float HELION_MOON_HALO_WIDTH = 0.035;
const vec3 HELION_MOON_HALO_COLOR = vec3(0.85, 0.9, 1.0);
const float HELION_MOON_LIGHT_OFFSET = 0.2;
const float HELION_MOON_LIGHT_TILT = 0.15;
const float HELION_MOON_LAMBERT_SHARE = 0.45;
const float HELION_MARE_SCALE = 1.6;
const float HELION_MARE_LOW = 0.48;
const float HELION_MARE_HIGH = 0.62;
const float HELION_REGOLITH_SCALE = 9.0;
const float HELION_REGOLITH_CONTRAST = 0.18;
const float HELION_LARGE_CRATER_SCALE = 3.5;
const float HELION_SMALL_CRATER_SCALE = 9.0;
const float HELION_CRATER_CHANCE = 0.55;
const float HELION_CRATER_MIN_RADIUS = 0.18;
const float HELION_CRATER_RADIUS_RANGE = 0.22;
const float HELION_CRATER_DEPTH = 0.35;
const float HELION_CRATER_RIM = 0.25;
const float HELION_CRATER_RIM_WIDTH = 0.15;
const float HELION_CRATER_FLOOR_DARKENING = 0.05;
const float HELION_CRATER_RIM_BRIGHTENING = 0.04;
const float HELION_BUMP_STEP = 0.01;
const float HELION_BUMP_STRENGTH = 0.12;
const float HELION_MIN_LUNAR_ANGLE = 1.0e-4;
const int HELION_FBM_OCTAVES = 4;
const float HELION_FBM_LACUNARITY = 2.03;
const float HELION_FBM_GAIN = 0.5;
const float HELION_HASH_SCALE = 0.3183099;
const float HELION_HASH_OFFSET = 0.1;
const float HELION_HASH_SPREAD = 17.0;
const vec3 HELION_HASH_CHANNEL_OFFSETS = vec3(0.0, 31.7, 67.3);
const float HELION_SMALL_CRATER_WEIGHT = 0.5;
const float HELION_REGOLITH_MEAN = 0.5;
const float HELION_LOMMEL_SEELIGER_NORMALIZATION = 2.0;

float helionHash(vec3 point) {
    point = fract(point * HELION_HASH_SCALE + HELION_HASH_OFFSET);
    point *= HELION_HASH_SPREAD;
    return fract(point.x * point.y * point.z * (point.x + point.y + point.z));
}

vec3 helionHash3(vec3 point) {
    return vec3(helionHash(point + HELION_HASH_CHANNEL_OFFSETS.x), helionHash(point + HELION_HASH_CHANNEL_OFFSETS.y), helionHash(point + HELION_HASH_CHANNEL_OFFSETS.z));
}

float helionValueNoise(vec3 point) {
    vec3 cell = floor(point);
    vec3 local = fract(point);
    vec3 blend = local * local * (3.0 - 2.0 * local);
    float bottom = mix(
        mix(helionHash(cell), helionHash(cell + vec3(1.0, 0.0, 0.0)), blend.x),
        mix(helionHash(cell + vec3(0.0, 1.0, 0.0)), helionHash(cell + vec3(1.0, 1.0, 0.0)), blend.x),
        blend.y
    );
    float top = mix(
        mix(helionHash(cell + vec3(0.0, 0.0, 1.0)), helionHash(cell + vec3(1.0, 0.0, 1.0)), blend.x),
        mix(helionHash(cell + vec3(0.0, 1.0, 1.0)), helionHash(cell + vec3(1.0, 1.0, 1.0)), blend.x),
        blend.y
    );
    return mix(bottom, top, blend.z);
}

float helionFbm(vec3 point) {
    float sum = 0.0;
    float amplitude = HELION_FBM_GAIN;
    for (int octave = 0; octave < HELION_FBM_OCTAVES; octave++) {
        sum += amplitude * helionValueNoise(point);
        point *= HELION_FBM_LACUNARITY;
        amplitude *= HELION_FBM_GAIN;
    }
    return sum;
}

vec2 helionCraters(vec3 point, float scale) {
    vec3 scaled = point * scale;
    vec3 cell = floor(scaled);
    float height = 0.0;
    float albedo = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                vec3 neighbor = cell + vec3(float(x), float(y), float(z));
                vec3 random = helionHash3(neighbor);
                if (random.z > HELION_CRATER_CHANCE) {
                    continue;
                }
                float radius = HELION_CRATER_MIN_RADIUS + HELION_CRATER_RADIUS_RANGE * random.x;
                float distance = length(scaled - neighbor - random) / radius;
                float rimOffset = (distance - 1.0) / HELION_CRATER_RIM_WIDTH;
                float rim = exp(-rimOffset * rimOffset);
                float bowl = max(1.0 - distance * distance, 0.0);
                height += HELION_CRATER_RIM * rim - HELION_CRATER_DEPTH * bowl;
                albedo += HELION_CRATER_RIM_BRIGHTENING * rim - HELION_CRATER_FLOOR_DARKENING * bowl;
            }
        }
    }
    return vec2(height, albedo);
}

vec2 helionMoonSurface(vec3 point) {
    vec2 large = helionCraters(point, HELION_LARGE_CRATER_SCALE);
    vec2 small = helionCraters(point, HELION_SMALL_CRATER_SCALE) * HELION_SMALL_CRATER_WEIGHT;
    float regolith = helionFbm(point * HELION_REGOLITH_SCALE);
    return vec2(large.x + small.x + regolith, large.y + small.y + (regolith - HELION_REGOLITH_MEAN) * HELION_REGOLITH_CONTRAST);
}

vec3 helionMoonAlbedo(vec3 point, float albedoShift) {
    float mare = smoothstep(HELION_MARE_LOW, HELION_MARE_HIGH, helionFbm(point * HELION_MARE_SCALE));
    return mix(HELION_MOON_HIGHLAND, HELION_MOON_MARE, mare) * max(1.0 + albedoShift, 0.0);
}

float helionDiscCoverage(float distance, float radius) {
    float edge = max(fwidth(distance), HELION_MIN_DISC_EDGE);
    return 1.0 - smoothstep(radius - edge, radius + edge, distance);
}

vec3 helionSunDisc(vec3 direction, vec3 sun, float illuminance) {
    float angle = length(direction - sun);
    float coverage = helionDiscCoverage(angle, HELION_SUN_ANGULAR_RADIUS);
    float radius = min(angle / HELION_SUN_ANGULAR_RADIUS, 1.0);
    vec3 limb = 1.0 - HELION_SUN_LIMB_DARKENING * (1.0 - sqrt(1.0 - radius * radius));
    float outside = max(angle - HELION_SUN_ANGULAR_RADIUS, 0.0);
    float aureole = HELION_SUN_GLARE * exp(-outside / HELION_SUN_GLARE_WIDTH) + HELION_SUN_AUREOLE * exp(-outside / HELION_SUN_AUREOLE_WIDTH);
    float aboveHorizon = smoothstep(-HELION_SUN_ANGULAR_RADIUS, HELION_SUN_ANGULAR_RADIUS, direction.y);
    vec3 light = limb * coverage + vec3(aureole * (1.0 - coverage));
    vec3 radiance = light * helionCelestialTransmittance(sun.y) * (aboveHorizon * illuminance * HELION_SUN_DISC_RADIANCE);
    float bleach = smoothstep(HELION_SUN_BLEACH_LOW, HELION_SUN_BLEACH_HIGH, helionMinComponent(radiance));
    return mix(radiance, vec3(helionMaxComponent(radiance)), bleach);
}

vec3 helionMoonHalo(float angle, float phaseAngle) {
    float lit = 0.5 + 0.5 * cos(phaseAngle);
    float outside = max(angle - HELION_MOON_ANGULAR_RADIUS, 0.0);
    return HELION_MOON_HALO_COLOR * (HELION_MOON_HALO * lit * exp(-outside / HELION_MOON_HALO_WIDTH));
}

vec3 helionMoonSphere(vec2 disc, float phaseAngle) {
    float radiusSquared = min(dot(disc, disc), 1.0);
    vec3 surface = vec3(disc, sqrt(1.0 - radiusSquared));
    vec3 tangentX = normalize(vec3(surface.z, 0.0, -surface.x) + vec3(HELION_MIN_LUNAR_ANGLE, 0.0, 0.0));
    vec3 tangentY = normalize(cross(surface, tangentX));
    vec2 center = helionMoonSurface(surface);
    float slopeX = (helionMoonSurface(normalize(surface + tangentX * HELION_BUMP_STEP)).x - center.x) / HELION_BUMP_STEP;
    float slopeY = (helionMoonSurface(normalize(surface + tangentY * HELION_BUMP_STEP)).x - center.x) / HELION_BUMP_STEP;
    vec3 normal = normalize(surface - HELION_BUMP_STRENGTH * (slopeX * tangentX + slopeY * tangentY));
    float lightAngle = phaseAngle + HELION_MOON_LIGHT_OFFSET;
    vec3 light = vec3(sin(lightAngle), HELION_MOON_LIGHT_TILT, cos(lightAngle));
    light = normalize(light);
    float incidence = max(dot(normal, light), 0.0);
    float emergence = max(normal.z, HELION_MIN_LUNAR_ANGLE);
    float lommelSeeliger = HELION_LOMMEL_SEELIGER_NORMALIZATION * incidence / (incidence + emergence);
    float shading = mix(lommelSeeliger, incidence, HELION_MOON_LAMBERT_SHARE);
    vec3 albedo = helionMoonAlbedo(surface, center.y);
    return albedo * (shading * HELION_MOON_RADIANCE + HELION_EARTHSHINE);
}

vec3 helionMoonDisc(vec3 direction, vec3 moon, float phaseAngle) {
    vec3 offset = direction - moon;
    float angle = length(offset);
    float coverage = helionDiscCoverage(angle, HELION_MOON_ANGULAR_RADIUS);
    vec3 radiance = helionMoonHalo(angle, phaseAngle) * (1.0 - coverage);
    if (coverage > 0.0) {
        vec3 along = normalize(cross(HELION_CELESTIAL_AXIS, moon));
        vec3 across = cross(-moon, along);
        vec2 disc = vec2(dot(offset, along), dot(offset, across)) / HELION_MOON_ANGULAR_RADIUS;
        radiance += helionMoonSphere(disc, phaseAngle) * coverage;
    }
    float aboveHorizon = smoothstep(-HELION_MOON_ANGULAR_RADIUS, HELION_MOON_ANGULAR_RADIUS, direction.y);
    return radiance * helionCelestialTransmittance(moon.y) * aboveHorizon;
}

#endif
