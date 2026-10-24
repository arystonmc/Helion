#ifndef HELION_LAVA_GLSL
#define HELION_LAVA_GLSL

#include <helion:helion_hash.glsl>

const int HELION_LAVA_WAVES = 3;
const vec2 HELION_LAVA_CYCLES[HELION_LAVA_WAVES] = vec2[](vec2(41.0, 17.0), vec2(-12.0, 58.0), vec2(73.0, -40.0));
const float HELION_LAVA_SPEED[HELION_LAVA_WAVES] = float[](0.35, 0.45, 0.6);
const float HELION_LAVA_WEIGHT[HELION_LAVA_WAVES] = float[](0.5, 0.3, 0.2);
const float HELION_LAVA_PERIOD = 1024.0;
const float HELION_LAVA_TAU = 6.28318531;
const float HELION_LAVA_CELLS_PER_BLOCK = 2.0;
const float HELION_LAVA_CELL_PERIOD = HELION_LAVA_PERIOD * HELION_LAVA_CELLS_PER_BLOCK;
const float HELION_LAVA_DRIFT = 0.35;
const float HELION_LAVA_DRIFT_SPEED = 0.15;
const vec2 HELION_LAVA_HASH_OTHER = vec2(4099.0, 7919.0);
const float HELION_LAVA_FAR = 8.0;
const float HELION_LAVA_CRACK_WIDTH = 0.09;
const float HELION_LAVA_CRUST_FROM = 0.25;
const float HELION_LAVA_CRUST_TO = 0.65;
const float HELION_LAVA_CRUST_SHARE = 0.85;
const float HELION_LAVA_CREST_COOLING = 0.45;
const vec3 HELION_LAVA_CRUST = vec3(0.3, 0.12, 0.07);
const vec3 HELION_LAVA_HOT = vec3(1.0, 0.82, 0.42);
const float HELION_LAVA_HOT_SHARE = 0.55;

float helionLavaSwell(vec2 position, float seconds) {
    float height = 0.0;
    for (int wave = 0; wave < HELION_LAVA_WAVES; wave++) {
        vec2 vector = HELION_LAVA_CYCLES[wave] * HELION_LAVA_TAU / HELION_LAVA_PERIOD;
        height += sin(dot(vector, position) - HELION_LAVA_SPEED[wave] * seconds) * HELION_LAVA_WEIGHT[wave];
    }
    return height;
}

vec2 helionLavaHash(vec2 cell) {
    vec2 wrapped = mod(cell, HELION_LAVA_CELL_PERIOD);
    return vec2(helionHash(wrapped), helionHash(wrapped + HELION_LAVA_HASH_OTHER));
}

vec2 helionLavaCells(vec2 position, float seconds) {
    vec2 scaled = position * HELION_LAVA_CELLS_PER_BLOCK;
    vec2 cell = floor(scaled);
    float nearest = HELION_LAVA_FAR;
    float second = HELION_LAVA_FAR;
    float nearestSeed = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            vec2 neighbor = cell + vec2(x, y);
            vec2 seed = helionLavaHash(neighbor);
            vec2 point = neighbor + 0.5 + (seed - 0.5) * (1.0 - HELION_LAVA_DRIFT) + sin(seconds * HELION_LAVA_DRIFT_SPEED + seed * HELION_LAVA_TAU) * HELION_LAVA_DRIFT;
            float distance = length(point - scaled);
            if (distance < nearest) {
                second = nearest;
                nearest = distance;
                nearestSeed = seed.x;
            } else if (distance < second) {
                second = distance;
            }
        }
    }
    return vec2(second - nearest, nearestSeed);
}

vec3 helionLavaColor(vec3 molten, vec2 position, float seconds) {
    float swell = helionLavaSwell(position, seconds);
    vec2 cells = helionLavaCells(position, seconds);
    float crusted = smoothstep(HELION_LAVA_CRUST_FROM, HELION_LAVA_CRUST_TO, cells.y + swell * HELION_LAVA_CREST_COOLING) * HELION_LAVA_CRUST_SHARE;
    float crack = 1.0 - smoothstep(0.0, HELION_LAVA_CRACK_WIDTH, cells.x);
    vec3 hot = mix(molten, HELION_LAVA_HOT, crack * crusted * HELION_LAVA_HOT_SHARE);
    return mix(hot, molten * HELION_LAVA_CRUST, crusted * (1.0 - crack));
}

#endif
