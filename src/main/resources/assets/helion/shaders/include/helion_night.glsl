#ifndef HELION_NIGHT_GLSL
#define HELION_NIGHT_GLSL

#include <helion:helion_celestial.glsl>

const float HELION_NIGHT_SUN_HIGH = 0.1;
const float HELION_NIGHT_SUN_LOW = -0.2;
const float HELION_DEEP_NIGHT_SUN = -0.15;
const vec3 HELION_NIGHT_GLOW = vec3(0.0015, 0.0025, 0.008);
const float HELION_NIGHT_GLOW_HORIZON = 1.8;

const int HELION_STAR_LAYERS = 2;
const float HELION_STAR_CELLS_BRIGHT = 14.0;
const float HELION_STAR_CELLS_FAINT = 46.0;
const float HELION_STAR_FLUX_BRIGHT = 2.5;
const float HELION_STAR_FLUX_FAINT = 0.12;
const float HELION_STAR_FLUX_POWER = 3.0;
const float HELION_STAR_MARGIN = 0.15;
const float HELION_STAR_SPREAD = 0.7;
const float HELION_STAR_SIGMA_PIXELS = 0.75;
const float HELION_STAR_VISIBLE_LOW = 1.5;
const float HELION_STAR_VISIBLE_HIGH = 5.0;
const float HELION_MIN_BACKGROUND = 1.0e-4;
const float HELION_MILKY_WAY_STAR_BOOST = 0.6;
const float HELION_RAIN_HIDE_LOW = 0.6;
const float HELION_RAIN_HIDE_HIGH = 0.95;

const vec3 HELION_STAR_BLUE = vec3(0.72, 0.82, 1.0);
const vec3 HELION_STAR_WHITE = vec3(1.0, 0.98, 0.95);
const vec3 HELION_STAR_YELLOW = vec3(1.0, 0.9, 0.7);
const vec3 HELION_STAR_ORANGE = vec3(1.0, 0.72, 0.48);
const float HELION_STAR_BLUE_SHARE = 0.25;
const float HELION_STAR_WHITE_SHARE = 0.65;
const float HELION_STAR_YELLOW_SHARE = 0.88;

const float HELION_TWINKLE_SPEED = 9.0;
const float HELION_TWINKLE_SECOND_SPEED = 14.3;
const float HELION_TWINKLE_ZENITH = 0.12;
const float HELION_TWINKLE_HORIZON = 0.45;
const float HELION_TWINKLE_HORIZON_HEIGHT = 0.35;
const float HELION_TWO_PI = 6.28318530718;
const float HELION_TWINKLE_SECOND_PHASE = 3.0;
const float HELION_CUBE_HALF = 0.5;

const vec3 HELION_GALACTIC_POLE = vec3(0.35, 0.55, 0.76);
const vec3 HELION_GALACTIC_CENTER = vec3(0.94, -0.24, -0.25);
const float HELION_MILKY_WAY_WIDTH = 0.17;
const float HELION_MILKY_WAY_RADIANCE = 0.0035;
const float HELION_MILKY_WAY_CORE = 1.6;
const float HELION_MILKY_WAY_CORE_WIDTH = 0.3;
const float HELION_MILKY_WAY_DETAIL = 5.0;
const float HELION_MILKY_WAY_DUST = 0.06;
const float HELION_MILKY_WAY_DUST_DETAIL = 9.0;
const vec3 HELION_MILKY_WAY_COLOR = vec3(0.92, 0.9, 1.0);
const float HELION_MILKY_WAY_DARK_SKY = 0.006;
const float HELION_MILKY_WAY_BRIGHT_SKY = 0.025;

const float HELION_VENUS_TRAIL = 0.75;
const float HELION_VENUS_FLUX = 4.0;
const vec3 HELION_VENUS_COLOR = vec3(1.0, 0.97, 0.88);
const vec3 HELION_JUPITER_LOCAL = vec3(0.06, -0.5, 0.86);
const float HELION_JUPITER_FLUX = 2.0;
const vec3 HELION_JUPITER_COLOR = vec3(1.0, 0.93, 0.8);
const vec3 HELION_MARS_LOCAL = vec3(-0.08, -0.64, -0.76);
const float HELION_MARS_FLUX = 0.9;
const vec3 HELION_MARS_COLOR = vec3(1.0, 0.6, 0.4);
const float HELION_PLANET_SIGMA_PIXELS = 0.9;

const float HELION_METEOR_SLOT_SECONDS = 7.0;
const float HELION_METEOR_CHANCE = 0.18;
const float HELION_METEOR_DURATION = 0.8;
const float HELION_METEOR_ARC = 0.22;
const float HELION_METEOR_TAIL = 0.12;
const float HELION_METEOR_RADIANCE = 1.6;
const float HELION_METEOR_MIN_HEIGHT = 0.35;
const float HELION_METEOR_HEIGHT_RANGE = 0.5;
const float HELION_METEOR_REACH = 0.3;
const vec3 HELION_METEOR_COLOR = vec3(0.9, 1.0, 0.92);
const vec2 HELION_METEOR_SEED = vec2(0.37, 1.91);
const vec2 HELION_METEOR_TWIST = vec2(3.0, 5.0);

const int HELION_AURORA_STEPS = 10;
const float HELION_AURORA_BOTTOM = 0.6;
const float HELION_AURORA_TOP = 2.2;
const float HELION_AURORA_SCALE = 0.35;
const float HELION_AURORA_DRIFT = 0.015;
const float HELION_AURORA_WARP = 2.5;
const float HELION_AURORA_SHARPNESS = 6.0;
const float HELION_AURORA_RADIANCE = 0.012;
const float HELION_AURORA_MIN_HEIGHT = 0.03;
const float HELION_AURORA_FADE_DISTANCE = 0.1;
const vec3 HELION_AURORA_GREEN = vec3(0.15, 1.0, 0.45);
const vec3 HELION_AURORA_VIOLET = vec3(0.7, 0.25, 0.9);
const float HELION_AURORA_WARP_SCALE = 0.5;
const float HELION_AURORA_STRETCH = 0.3;
const float HELION_NOISE_CENTER = 0.5;

float helionNightFactor(float sunHeight) {
    return 1.0 - smoothstep(HELION_NIGHT_SUN_LOW, HELION_NIGHT_SUN_HIGH, sunHeight);
}

vec3 helionNightGlow(vec3 direction, float sunHeight) {
    float horizon = mix(HELION_NIGHT_GLOW_HORIZON, 1.0, clamp(direction.y, 0.0, 1.0));
    return HELION_NIGHT_GLOW * (horizon * helionNightFactor(sunHeight));
}

vec3 helionToStarFrame(vec3 world, float starAngle) {
    vec3 yawed = vec3(world.z, world.y, -world.x);
    float c = cos(starAngle);
    float s = sin(starAngle);
    return vec3(yawed.x, c * yawed.y + s * yawed.z, -s * yawed.y + c * yawed.z);
}

vec3 helionCubeDirection(int face, vec2 uv) {
    if (face == 0) {
        return normalize(vec3(1.0, uv.x, uv.y));
    }
    if (face == 1) {
        return normalize(vec3(-1.0, uv.x, uv.y));
    }
    if (face == 2) {
        return normalize(vec3(uv.x, 1.0, uv.y));
    }
    if (face == 3) {
        return normalize(vec3(uv.x, -1.0, uv.y));
    }
    if (face == 4) {
        return normalize(vec3(uv.x, uv.y, 1.0));
    }
    return normalize(vec3(uv.x, uv.y, -1.0));
}

vec2 helionCubeUv(vec3 direction, out int face) {
    vec3 size = abs(direction);
    if (size.x >= size.y && size.x >= size.z) {
        face = direction.x > 0.0 ? 0 : 1;
        return direction.yz / size.x;
    }
    if (size.y >= size.z) {
        face = direction.y > 0.0 ? 2 : 3;
        return direction.xz / size.y;
    }
    face = direction.z > 0.0 ? 4 : 5;
    return direction.xy / size.z;
}

vec3 helionStarColor(float random) {
    if (random < HELION_STAR_BLUE_SHARE) {
        return HELION_STAR_BLUE;
    }
    if (random < HELION_STAR_WHITE_SHARE) {
        return HELION_STAR_WHITE;
    }
    return random < HELION_STAR_YELLOW_SHARE ? HELION_STAR_YELLOW : HELION_STAR_ORANGE;
}

float helionTwinkle(float seed, float height) {
    float strength = mix(HELION_TWINKLE_HORIZON, HELION_TWINKLE_ZENITH, clamp(height / HELION_TWINKLE_HORIZON_HEIGHT, 0.0, 1.0));
    float phase = seed * HELION_TWO_PI;
    float wave = sin(SkyTime * HELION_TWINKLE_SPEED + phase) * sin(SkyTime * HELION_TWINKLE_SECOND_SPEED + phase * HELION_TWINKLE_SECOND_PHASE);
    return 1.0 + strength * wave;
}

float helionStarVisibility(float peak, float background) {
    float floorBackground = max(background, HELION_MIN_BACKGROUND);
    return smoothstep(HELION_STAR_VISIBLE_LOW * floorBackground, HELION_STAR_VISIBLE_HIGH * floorBackground, peak);
}

float helionPointSpread(vec3 direction, vec3 point, float sigma) {
    float distance = length(direction - point) / sigma;
    return exp(-distance * distance);
}

float helionMilkyWayBand(vec3 local) {
    float latitude = dot(local, normalize(HELION_GALACTIC_POLE)) / HELION_MILKY_WAY_WIDTH;
    return exp(-latitude * latitude);
}

vec3 helionStarLayer(vec3 world, vec3 local, float pixel, float cells, float flux, float density, float background, float layerSeed) {
    int face;
    vec2 uv = helionCubeUv(local, face);
    vec2 cell = floor((uv * HELION_CUBE_HALF + HELION_CUBE_HALF) * cells);
    vec3 light = vec3(0.0);
    float sigma = pixel * HELION_STAR_SIGMA_PIXELS;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec2 neighbor = cell + vec2(float(x), float(y));
            vec3 random = helionHash3(vec3(neighbor, float(face) + layerSeed));
            vec2 starUv = (neighbor + HELION_STAR_MARGIN + HELION_STAR_SPREAD * random.xy) / cells / HELION_CUBE_HALF - 1.0;
            vec3 star = helionCubeDirection(face, starUv);
            float chance = density * (1.0 + HELION_MILKY_WAY_STAR_BOOST * MilkyWay * helionMilkyWayBand(star));
            if (random.z > chance) {
                continue;
            }
            float brightness = helionHash(vec3(neighbor.yx, layerSeed - float(face)));
            float peak = flux * pow(brightness, HELION_STAR_FLUX_POWER) * helionTwinkle(random.x + random.y, world.y);
            float spread = helionPointSpread(local, star, sigma);
            light += helionStarColor(random.x) * (peak * spread * helionStarVisibility(peak, background));
        }
    }
    return light;
}

vec3 helionMilkyWay(vec3 local, float background) {
    float band = helionMilkyWayBand(local);
    float core = 1.0 + HELION_MILKY_WAY_CORE * exp(-(1.0 - dot(local, normalize(HELION_GALACTIC_CENTER))) / HELION_MILKY_WAY_CORE_WIDTH);
    float detail = helionFbm(local * HELION_MILKY_WAY_DETAIL);
    float dust = smoothstep(0.0, HELION_MILKY_WAY_DUST, abs(dot(local, normalize(HELION_GALACTIC_POLE)) + (helionFbm(local * HELION_MILKY_WAY_DUST_DETAIL) - HELION_NOISE_CENTER) * HELION_MILKY_WAY_DUST));
    float darkSky = 1.0 - smoothstep(HELION_MILKY_WAY_DARK_SKY, HELION_MILKY_WAY_BRIGHT_SKY, background);
    return HELION_MILKY_WAY_COLOR * (HELION_MILKY_WAY_RADIANCE * band * core * detail * dust * darkSky);
}

vec3 helionRotateAroundAxis(vec3 vector, vec3 axis, float angle) {
    return vector * cos(angle) + cross(axis, vector) * sin(angle) + axis * dot(axis, vector) * (1.0 - cos(angle));
}

vec3 helionPlanet(vec3 direction, vec3 planet, float flux, vec3 color, float pixel, float background) {
    float visibility = helionStarVisibility(flux, background);
    return color * (flux * visibility * helionPointSpread(direction, planet, pixel * HELION_PLANET_SIGMA_PIXELS));
}

vec3 helionPlanets(vec3 direction, vec3 local, float pixel, float background) {
    vec3 venus = helionRotateAroundAxis(SunDirection, HELION_CELESTIAL_AXIS, -HELION_VENUS_TRAIL);
    vec3 light = helionPlanet(direction, venus, HELION_VENUS_FLUX, HELION_VENUS_COLOR, pixel, background);
    light += helionPlanet(local, normalize(HELION_JUPITER_LOCAL), HELION_JUPITER_FLUX, HELION_JUPITER_COLOR, pixel, background);
    light += helionPlanet(local, normalize(HELION_MARS_LOCAL), HELION_MARS_FLUX, HELION_MARS_COLOR, pixel, background);
    return light;
}

vec3 helionMeteor(vec3 direction, float pixel, float background) {
    float slot = floor(SkyTime / HELION_METEOR_SLOT_SECONDS);
    vec3 random = helionHash3(vec3(slot, slot * HELION_METEOR_SEED.x, slot * HELION_METEOR_SEED.y));
    if (random.z > HELION_METEOR_CHANCE) {
        return vec3(0.0);
    }
    float start = random.x * (HELION_METEOR_SLOT_SECONDS - HELION_METEOR_DURATION);
    float progress = (SkyTime - slot * HELION_METEOR_SLOT_SECONDS - start) / HELION_METEOR_DURATION;
    if (progress <= 0.0 || progress >= 1.0) {
        return vec3(0.0);
    }
    float azimuth = random.y * HELION_TWO_PI;
    float height = HELION_METEOR_MIN_HEIGHT + HELION_METEOR_HEIGHT_RANGE * random.x;
    vec3 origin = normalize(vec3(cos(azimuth), height, sin(azimuth)));
    if (dot(direction, origin) < 1.0 - HELION_METEOR_REACH) {
        return vec3(0.0);
    }
    vec3 travel = normalize(cross(origin, normalize(vec3(sin(azimuth * HELION_METEOR_TWIST.x), random.y, cos(azimuth * HELION_METEOR_TWIST.y)))));
    vec3 across = cross(origin, travel);
    vec3 offset = direction - origin;
    float along = dot(offset, travel);
    float side = dot(offset, across) / pixel;
    float head = progress * HELION_METEOR_ARC;
    float tail = (along - (head - HELION_METEOR_TAIL)) / HELION_METEOR_TAIL;
    if (along > head || tail < 0.0) {
        return vec3(0.0);
    }
    float fade = sin(progress * HELION_PI);
    float radiance = HELION_METEOR_RADIANCE * fade * tail * tail * exp(-side * side);
    return HELION_METEOR_COLOR * (radiance * helionStarVisibility(HELION_METEOR_RADIANCE, background));
}

vec3 helionAurora(vec3 direction, float background) {
    if (AuroraStrength <= 0.0 || direction.y < HELION_AURORA_MIN_HEIGHT) {
        return vec3(0.0);
    }
    vec3 light = vec3(0.0);
    for (int step = 0; step < HELION_AURORA_STEPS; step++) {
        float layer = float(step) / float(HELION_AURORA_STEPS - 1);
        float altitude = mix(HELION_AURORA_BOTTOM, HELION_AURORA_TOP, layer);
        vec2 position = direction.xz / direction.y * altitude * HELION_AURORA_SCALE;
        float warp = helionFbm(vec3(position * HELION_AURORA_WARP_SCALE, SkyTime * HELION_AURORA_DRIFT)) * HELION_AURORA_WARP;
        float ridge = 1.0 - abs(helionValueNoise(vec3(position.x + warp, position.y * HELION_AURORA_STRETCH, SkyTime * HELION_AURORA_DRIFT)) * 2.0 - 1.0);
        float curtain = pow(ridge, HELION_AURORA_SHARPNESS) * (1.0 - layer);
        light += mix(HELION_AURORA_GREEN, HELION_AURORA_VIOLET, layer) * curtain;
    }
    float horizonFade = smoothstep(HELION_AURORA_MIN_HEIGHT, HELION_AURORA_FADE_DISTANCE, direction.y);
    float darkSky = 1.0 - smoothstep(HELION_MILKY_WAY_DARK_SKY, HELION_MILKY_WAY_BRIGHT_SKY * 2.0, background);
    return light * (HELION_AURORA_RADIANCE * AuroraStrength * horizonFade * darkSky / float(HELION_AURORA_STEPS));
}

vec3 helionNightSky(vec3 direction, float pixel, float background) {
    float night = helionNightFactor(SunDirection.y);
    float clearSky = smoothstep(HELION_RAIN_HIDE_LOW, HELION_RAIN_HIDE_HIGH, RainBrightness);
    if (night <= 0.0 || clearSky <= 0.0 || direction.y < -HELION_SUN_ANGULAR_RADIUS) {
        return vec3(0.0);
    }
    vec3 local = helionToStarFrame(direction, StarAngle);
    vec3 light = helionStarLayer(direction, local, pixel, HELION_STAR_CELLS_BRIGHT, HELION_STAR_FLUX_BRIGHT, StarDensity, background, 0.0)
        + helionStarLayer(direction, local, pixel, HELION_STAR_CELLS_FAINT, HELION_STAR_FLUX_FAINT, StarDensity, background, 1.0)
        + helionPlanets(direction, local, pixel, background)
        + helionMeteor(direction, pixel, background);
    if (MilkyWay > 0.0) {
        light += helionMilkyWay(local, background);
    }
    float deepNight = 1.0 - smoothstep(HELION_DEEP_NIGHT_SUN, 0.0, SunDirection.y);
    light += helionAurora(direction, background) * deepNight;
    float aboveHorizon = smoothstep(-HELION_SUN_ANGULAR_RADIUS, HELION_SUN_ANGULAR_RADIUS, direction.y);
    return light * (clearSky * aboveHorizon);
}

#endif
