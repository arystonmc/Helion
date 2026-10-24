#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D WaveSampler;
uniform sampler2D ShoreSampler;

layout(std140) uniform HelionWaterWaves {
    ivec4 WaveOrigin;
    vec4 WaveParams;
    ivec4 WaveCounts;
    vec4 WaveImpulses[32];
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 waveState;

const int HELION_WAVE_SIZE = 1024;
const int HELION_WAVE_MASK = HELION_WAVE_SIZE - 1;
const int HELION_WAVE_LAST = HELION_WAVE_SIZE - 1;
const float HELION_WAVE_EDGE = 48.0;
const float HELION_WAVE_VISCOSITY = 0.04;
const float HELION_DROP_SPREAD = 0.5;
const float HELION_CELL_CENTER = 0.5;
const uint HELION_HASH_MULTIPLIER = 747796405u;
const uint HELION_HASH_INCREMENT = 2891336453u;
const uint HELION_HASH_SHIFT_BASE = 28u;
const uint HELION_HASH_SHIFT_OFFSET = 4u;
const uint HELION_HASH_MIX = 277803737u;
const uint HELION_HASH_FINAL_SHIFT = 22u;
const uint HELION_HASH_Z_SCALE = 1973u;
const uint HELION_HASH_SEED_SCALE = 9277u;
const float HELION_HASH_RANGE = 4294967295.0;
const vec4 HELION_CALM = vec4(0.0);
const int HELION_SHORE_MASK = 127;
const int HELION_CELLS_PER_BLOCK_SHIFT = 3;
const float HELION_SHORE_WATER = 0.75;
const float HELION_LAND_DAMPING = 0.93;
const int HELION_PUDDLES_ON = 1;
const float HELION_WATER_DROP_SHARE = 0.5;

uint helionWavePcg(uint value) {
    uint state = value * HELION_HASH_MULTIPLIER + HELION_HASH_INCREMENT;
    uint word = ((state >> ((state >> HELION_HASH_SHIFT_BASE) + HELION_HASH_SHIFT_OFFSET)) ^ state) * HELION_HASH_MIX;
    return (word >> HELION_HASH_FINAL_SHIFT) ^ word;
}

float helionWaveRandom(ivec2 cell, int seed) {
    uint mixed = uint(cell.x) + uint(cell.y) * HELION_HASH_Z_SCALE + uint(seed) * HELION_HASH_SEED_SCALE;
    return float(helionWavePcg(helionWavePcg(mixed))) / HELION_HASH_RANGE;
}

float helionDrop(ivec2 cell, int seed, float chance) {
    float here = helionWaveRandom(cell, seed) < chance ? 1.0 : 0.0;
    float around = (helionWaveRandom(cell + ivec2(1, 0), seed) < chance ? 1.0 : 0.0) + (helionWaveRandom(cell - ivec2(1, 0), seed) < chance ? 1.0 : 0.0)
        + (helionWaveRandom(cell + ivec2(0, 1), seed) < chance ? 1.0 : 0.0) + (helionWaveRandom(cell - ivec2(0, 1), seed) < chance ? 1.0 : 0.0);
    return here + around * HELION_DROP_SPREAD;
}

bool helionIsWaterCell(ivec2 cell) {
    return texelFetch(ShoreSampler, (cell >> HELION_CELLS_PER_BLOCK_SHIFT) & HELION_SHORE_MASK, 0).r > HELION_SHORE_WATER;
}

float helionNeighborHeight(ivec2 texel, ivec2 cell, ivec2 local, ivec2 offset, bool water) {
    ivec2 neighbor = local + offset;
    if (any(lessThan(neighbor, ivec2(0))) || any(greaterThan(neighbor, ivec2(HELION_WAVE_LAST))) || helionIsWaterCell(cell + offset) != water) {
        return texelFetch(WaveSampler, texel, 0).r;
    }
    return texelFetch(WaveSampler, (texel + offset) & HELION_WAVE_MASK, 0).r;
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    ivec2 cell = WaveOrigin.xy + ((texel - WaveOrigin.xy) & HELION_WAVE_MASK);
    ivec2 previousCell = WaveOrigin.zw + ((texel - WaveOrigin.zw) & HELION_WAVE_MASK);
    bool water = helionIsWaterCell(cell);
    if (WaveCounts.z != 0 || cell != previousCell || !water && WaveCounts.w != HELION_PUDDLES_ON) {
        waveState = HELION_CALM;
        return;
    }
    ivec2 local = cell - WaveOrigin.xy;
    vec2 state = texelFetch(WaveSampler, texel, 0).rg;
    float around = helionNeighborHeight(texel, cell, local, ivec2(1, 0), water) + helionNeighborHeight(texel, cell, local, ivec2(-1, 0), water)
        + helionNeighborHeight(texel, cell, local, ivec2(0, 1), water) + helionNeighborHeight(texel, cell, local, ivec2(0, -1), water);
    float velocity = (state.r - state.g) * (water ? WaveParams.y : HELION_LAND_DAMPING);
    float curvature = around - 4.0 * state.r;
    float next = state.r + velocity + WaveParams.x * curvature + HELION_WAVE_VISCOSITY * curvature;
    vec2 center = vec2(local) + HELION_CELL_CENTER;
    for (int index = 0; index < WaveCounts.x; index++) {
        vec4 impulse = WaveImpulses[index];
        float distanceToImpulse = distance(center, impulse.xy);
        if (distanceToImpulse < impulse.z) {
            float falloff = 1.0 - distanceToImpulse / impulse.z;
            next -= impulse.w * falloff * falloff;
        }
    }
    next -= WaveParams.w * (water ? HELION_WATER_DROP_SHARE : 1.0) * helionDrop(cell, WaveCounts.y, WaveParams.z);
    float edge = float(min(min(local.x, local.y), min(HELION_WAVE_LAST - local.x, HELION_WAVE_LAST - local.y)));
    next *= smoothstep(0.0, HELION_WAVE_EDGE, edge);
    waveState = vec4(next, state.r, 0.0, 0.0);
}
