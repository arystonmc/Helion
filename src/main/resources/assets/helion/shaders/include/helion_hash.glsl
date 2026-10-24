#ifndef HELION_HASH_GLSL
#define HELION_HASH_GLSL

const uint HELION_HASH_PCG_MULTIPLIER = 747796405u;
const uint HELION_HASH_PCG_INCREMENT = 2891336453u;
const uint HELION_HASH_PCG_SHIFT_BASE = 28u;
const uint HELION_HASH_PCG_SHIFT_OFFSET = 4u;
const uint HELION_HASH_PCG_MIX = 277803737u;
const uint HELION_HASH_PCG_FINAL_SHIFT = 22u;
const uint HELION_HASH_ROW = 1973u;
const float HELION_HASH_RANGE = 4294967295.0;

uint helionPcg(uint value) {
    uint state = value * HELION_HASH_PCG_MULTIPLIER + HELION_HASH_PCG_INCREMENT;
    uint word = ((state >> ((state >> HELION_HASH_PCG_SHIFT_BASE) + HELION_HASH_PCG_SHIFT_OFFSET)) ^ state) * HELION_HASH_PCG_MIX;
    return (word >> HELION_HASH_PCG_FINAL_SHIFT) ^ word;
}

float helionHash(vec2 cell) {
    ivec2 whole = ivec2(floor(cell));
    return float(helionPcg(helionPcg(uint(whole.x)) + uint(whole.y) * HELION_HASH_ROW)) / HELION_HASH_RANGE;
}

#endif
