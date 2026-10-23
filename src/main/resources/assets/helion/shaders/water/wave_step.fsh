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

const int HELION_WAVE_SIZE = 512;
const int HELION_WAVE_MASK = HELION_WAVE_SIZE - 1;
const int HELION_WAVE_LAST = HELION_WAVE_SIZE - 1;
const float HELION_WAVE_EDGE = 24.0;
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
const int HELION_CELLS_PER_BLOCK_SHIFT = 2;
const float HELION_SHORE_WATER = 0.5;

uint helionWavePcg(uint value) {
    uint state = value * HELION_HASH_MULTIPLIER + HELION_HASH_INCREMENT;
    uint word = ((state >> ((state >> HELION_HASH_SHIFT_BASE) + HELION_HASH_SHIFT_OFFSET)) ^ state) * HELION_HASH_MIX;
    return (word >> HELION_HASH_FINAL_SHIFT) ^ word;
}

float helionWaveRandom(ivec2 cell, int seed) {
    uint mixed = uint(cell.x) + uint(cell.y) * HELION_HASH_Z_SCALE + uint(seed) * HELION_HASH_SEED_SCALE;
    return float(helionWavePcg(helionWavePcg(mixed))) / HELION_HASH_RANGE;
}

bool helionHoldsWaves(ivec2 cell) {
    return texelFetch(ShoreSampler, (cell >> HELION_CELLS_PER_BLOCK_SHIFT) & HELION_SHORE_MASK, 0).r > HELION_SHORE_WATER;
}

float helionNeighborHeight(ivec2 texel, ivec2 local, ivec2 offset) {
    ivec2 neighbor = local + offset;
    if (any(lessThan(neighbor, ivec2(0))) || any(greaterThan(neighbor, ivec2(HELION_WAVE_LAST)))) {
        return 0.0;
    }
    return texelFetch(WaveSampler, (texel + offset) & HELION_WAVE_MASK, 0).r;
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    ivec2 cell = WaveOrigin.xy + ((texel - WaveOrigin.xy) & HELION_WAVE_MASK);
    ivec2 previousCell = WaveOrigin.zw + ((texel - WaveOrigin.zw) & HELION_WAVE_MASK);
    if (WaveCounts.z != 0 || cell != previousCell || !helionHoldsWaves(cell)) {
        waveState = HELION_CALM;
        return;
    }
    ivec2 local = cell - WaveOrigin.xy;
    vec2 state = texelFetch(WaveSampler, texel, 0).rg;
    float around = helionNeighborHeight(texel, local, ivec2(1, 0)) + helionNeighborHeight(texel, local, ivec2(-1, 0))
        + helionNeighborHeight(texel, local, ivec2(0, 1)) + helionNeighborHeight(texel, local, ivec2(0, -1));
    float velocity = (state.r - state.g) * WaveParams.y;
    float next = state.r + velocity + WaveParams.x * (around - 4.0 * state.r);
    vec2 center = vec2(local) + HELION_CELL_CENTER;
    for (int index = 0; index < WaveCounts.x; index++) {
        vec4 impulse = WaveImpulses[index];
        float distanceToImpulse = distance(center, impulse.xy);
        if (distanceToImpulse < impulse.z) {
            float falloff = 1.0 - distanceToImpulse / impulse.z;
            next -= impulse.w * falloff * falloff;
        }
    }
    if (helionWaveRandom(cell, WaveCounts.y) < WaveParams.z) {
        next -= WaveParams.w;
    }
    float edge = float(min(min(local.x, local.y), min(HELION_WAVE_LAST - local.x, HELION_WAVE_LAST - local.y)));
    next *= smoothstep(0.0, HELION_WAVE_EDGE, edge);
    waveState = vec4(next, state.r, 0.0, 0.0);
}
