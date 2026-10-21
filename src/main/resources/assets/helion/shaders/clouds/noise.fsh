#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_cloud_noise.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

uint helionHash(uvec3 cell) {
    uint h = cell.x * 1597334677u ^ cell.y * 3812015801u ^ cell.z * 2798796415u;
    h ^= h >> 16;
    h *= 2246822519u;
    h ^= h >> 13;
    h *= 3266489917u;
    h ^= h >> 16;
    return h;
}

vec3 helionHash3(uvec3 cell) {
    uint h = helionHash(cell);
    return vec3(h & 1023u, (h >> 10) & 1023u, (h >> 20) & 1023u) / 1023.0;
}

uvec3 helionWrap(ivec3 cell, int period) {
    return uvec3((cell % period + period) % period);
}

float helionGradientNoise(vec3 position, int period) {
    vec3 scaled = position * float(period);
    ivec3 cell = ivec3(floor(scaled));
    vec3 local = fract(scaled);
    vec3 fade = local * local * local * (local * (local * 6.0 - 15.0) + 10.0);
    float corners[8];
    for (int index = 0; index < 8; index++) {
        ivec3 offset = ivec3(index & 1, (index >> 1) & 1, (index >> 2) & 1);
        vec3 gradient = normalize(helionHash3(helionWrap(cell + offset, period)) * 2.0 - 1.0 + 1.0e-4);
        corners[index] = dot(gradient, local - vec3(offset));
    }
    float x00 = mix(corners[0], corners[1], fade.x);
    float x10 = mix(corners[2], corners[3], fade.x);
    float x01 = mix(corners[4], corners[5], fade.x);
    float x11 = mix(corners[6], corners[7], fade.x);
    return mix(mix(x00, x10, fade.y), mix(x01, x11, fade.y), fade.z);
}

float helionWorley(vec3 position, int period) {
    vec3 scaled = position * float(period);
    ivec3 cell = ivec3(floor(scaled));
    vec3 local = fract(scaled);
    float nearest = 1.0;
    for (int z = -1; z <= 1; z++) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                ivec3 offset = ivec3(x, y, z);
                vec3 point = vec3(offset) + helionHash3(helionWrap(cell + offset, period));
                vec3 difference = point - local;
                nearest = min(nearest, dot(difference, difference));
            }
        }
    }
    return 1.0 - sqrt(nearest);
}

float helionPerlinFbm(vec3 position, int period) {
    float sum = 0.0;
    float amplitude = 0.5;
    float total = 0.0;
    for (int octave = 0; octave < 5; octave++) {
        sum += helionGradientNoise(position, period) * amplitude;
        total += amplitude;
        amplitude *= 0.5;
        period *= 2;
    }
    return sum / total * 0.5 + 0.5;
}

float helionWorleyFbm(vec3 position, int period) {
    return helionWorley(position, period) * 0.625
        + helionWorley(position, period * 2) * 0.25
        + helionWorley(position, period * 4) * 0.125;
}

void main() {
    vec2 pixel = floor(gl_FragCoord.xy);
    vec2 tile = floor(pixel / HELION_CLOUD_NOISE_TILE);
    float slice = tile.y * HELION_CLOUD_NOISE_GRID + tile.x;
    vec2 local = mod(pixel - tile * HELION_CLOUD_NOISE_TILE - 1.0 + HELION_CLOUD_NOISE_SIZE, HELION_CLOUD_NOISE_SIZE);
    vec3 position = vec3(local + 0.5, slice + 0.5) / HELION_CLOUD_NOISE_SIZE;

    float perlin = helionPerlinFbm(position, 4);
    float worley = helionWorleyFbm(position, 4);
    float perlinWorley = clamp(helionCloudRemap(perlin, worley - 1.0, 1.0, 0.0, 1.0), 0.0, 1.0);
    fragColor = vec4(
        perlinWorley,
        helionWorleyFbm(position, 4),
        helionWorleyFbm(position, 8),
        helionWorleyFbm(position, 16)
    );
}
