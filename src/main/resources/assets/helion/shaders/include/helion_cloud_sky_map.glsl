#ifndef HELION_CLOUD_SKY_MAP_GLSL
#define HELION_CLOUD_SKY_MAP_GLSL

const float HELION_CLOUD_SKY_MAP_SIZE = 192.0;
const float HELION_CLOUD_SKY_MAP_MIN_UP = 0.002;

vec3 helionCloudSkyMapDirection(vec2 uv) {
    vec2 square = uv * 2.0 - 1.0;
    vec2 diamond = vec2(square.x + square.y, square.x - square.y) * 0.5;
    return normalize(vec3(diamond.x, max(1.0 - abs(diamond.x) - abs(diamond.y), HELION_CLOUD_SKY_MAP_MIN_UP), diamond.y));
}

vec2 helionCloudSkyMapUv(vec3 direction) {
    vec3 folded = direction / (abs(direction.x) + abs(direction.y) + abs(direction.z));
    vec2 square = vec2(folded.x + folded.z, folded.x - folded.z);
    return clamp(square * 0.5 + 0.5, vec2(0.5 / HELION_CLOUD_SKY_MAP_SIZE), vec2(1.0 - 0.5 / HELION_CLOUD_SKY_MAP_SIZE));
}

#endif
