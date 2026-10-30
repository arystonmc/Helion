#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif
#include <helion:helion_geometry.glsl>
#include <helion:helion_terrain_vertex.glsl>
#include <helion:helion_water_waves.glsl>

uniform sampler2D ShoreSampler;

layout(std140) uniform HelionWaterSurface {
    vec4 WaterStill;
    vec4 WaterFlow;
    vec4 WaterRain;
    float WaveStrength;
    vec4 WaterOverlay;
};

#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec3 cameraRelativePosition;
layout(location = 2) out vec2 wavePosition;
layout(location = 3) out float vertexAlpha;
layout(location = 4) out float skyLight;
layout(location = 5) out vec3 vertexTint;
layout(location = 6) out float oceanSwell;
layout(location = 7) out vec3 worldPosition;

const int HELION_WAVE_WRAP = 1023;
const float HELION_SECONDS_PER_DAY = 1200.0;
const float HELION_SIMULATION_ON = 0.5;
const float HELION_SWELL_NEAR = 48.0;
const float HELION_SWELL_FAR = 60.0;
const float HELION_SPRITE_EDGE = 1.0e-4;
const float HELION_GLASS_PASS_ON = 0.5;
const vec4 HELION_OUTSIDE_VIEW = vec4(2.0, 2.0, 2.0, 1.0);

bool helionInsideSprite(vec2 uv, vec4 rect) {
    return all(greaterThanEqual(uv, rect.xy - HELION_SPRITE_EDGE)) && all(lessThanEqual(uv, rect.zw + HELION_SPRITE_EDGE));
}

void main() {
    vec3 local = helion_vertex_position();
    vec3 pos = local + (ChunkPosition - CameraBlockPos) + CameraOffset;
    texCoord0 = helion_vertex_uv();
    wavePosition = vec2(ChunkPosition.xz & HELION_WAVE_WRAP) + local.xz;
    worldPosition = vec3(ChunkPosition) + local;
    bool water = helionInsideSprite(texCoord0, WaterStill) || helionInsideSprite(texCoord0, WaterFlow);
    if ((water || helionInsideSprite(texCoord0, WaterOverlay)) == (WaterRain.w > HELION_GLASS_PASS_ON)) {
        gl_Position = HELION_OUTSIDE_VIEW;
        return;
    }
    float far = smoothstep(HELION_SWELL_NEAR, HELION_SWELL_FAR, length(pos.xz));
    bool simulated = water && WaterRain.z > HELION_SIMULATION_ON;
    float open = simulated && far < 1.0 ? helionOceanOpenness(ShoreSampler, wavePosition) : 0.0;
    float displaced = open * (1.0 - far);
    if (displaced > 0.0) {
        pos.y += (helionOceanHeight(wavePosition, GameTime * HELION_SECONDS_PER_DAY) + HELION_OCEAN_RISE) * displaced;
    }
    oceanSwell = simulated ? mix(open, 1.0, far) : 0.0;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    cameraRelativePosition = pos;
    vertexAlpha = Color.a;
    vertexTint = Color.rgb;
    skyLight = clamp(float(helion_vertex_light().y) / HELION_MAX_LIGHT_COORDINATE, 0.0, 1.0);
}
