#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <helion:helion_view.glsl>
#include <helion:helion_water.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_wetness.glsl>

uniform sampler2D Sampler0;
uniform sampler2D SceneDepthSampler;
uniform sampler2D GlossySampler;

layout(std140) uniform HelionWaterSurface {
    vec4 WaterStill;
    vec4 WaterFlow;
    vec4 WaterRain;
    float WaveStrength;
};

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec3 cameraRelativePosition;
layout(location = 2) in vec2 wavePosition;
layout(location = 3) in float vertexAlpha;
layout(location = 4) in float skyLight;
layout(location = 5) in vec3 vertexTint;

layout(location = 0) out vec4 waterSurface;
layout(location = 1) out vec4 waterTint;

const float HELION_SECONDS_PER_DAY = 1200.0;
const float HELION_WAVE_PERIOD = 1024.0;
const float HELION_FLAT_SURFACE = 0.9;
const float HELION_SPRITE_EDGE = 1.0e-4;
const float HELION_TAU = 6.28318531;
const int HELION_WAVE_COUNT = 8;
const vec2 HELION_WAVE_CYCLES[HELION_WAVE_COUNT] = vec2[](
    vec2(97.0, 41.0), vec2(-63.0, 131.0), vec2(173.0, -89.0), vec2(-211.0, -157.0),
    vec2(521.0, 307.0), vec2(-397.0, 613.0), vec2(733.0, -251.0), vec2(-887.0, -509.0)
);
const float HELION_WAVE_HEIGHT[HELION_WAVE_COUNT] = float[](0.030, 0.022, 0.012, 0.007, 0.0040, 0.0030, 0.0022, 0.0015);
const float HELION_WAVE_SPEED[HELION_WAVE_COUNT] = float[](1.1, 1.4, 1.9, 2.6, 3.4, 3.9, 4.6, 5.3);
const vec4 HELION_NO_TINT = vec4(0.0);
const float HELION_TINT_PRESENT = 1.0;

bool helionInside(vec2 uv, vec4 rect) {
    return all(greaterThanEqual(uv, rect.xy - HELION_SPRITE_EDGE)) && all(lessThanEqual(uv, rect.zw + HELION_SPRITE_EDGE));
}

vec3 helionWaveNormal(vec2 position, float seconds) {
    vec2 slope = vec2(0.0);
    for (int wave = 0; wave < HELION_WAVE_COUNT; wave++) {
        vec2 frequency = HELION_WAVE_CYCLES[wave] * (HELION_TAU / HELION_WAVE_PERIOD);
        float phase = dot(frequency, position) + HELION_WAVE_SPEED[wave] * seconds;
        slope += frequency * (HELION_WAVE_HEIGHT[wave] * cos(phase));
    }
    vec2 ripples = WaterRain.x > 0.0 ? helionRainRipples(position, WaterRain.y) * WaterRain.x : vec2(0.0);
    return normalize(vec3(-slope.x * WaveStrength - ripples.x, 1.0, -slope.y * WaveStrength - ripples.y));
}

float helionWaterThickness(float sceneDepth) {
    if (sceneDepth <= HELION_SKY_DEVICE_DEPTH) {
        return HELION_WATER_MAX_THICKNESS;
    }
    float surfaceDepth = helionLinearDepth(gl_FragCoord.z);
    float behind = helionLinearDepth(sceneDepth) - surfaceDepth;
    return clamp(behind * length(cameraRelativePosition) / surfaceDepth, HELION_WATER_MIN_THICKNESS, HELION_WATER_MAX_THICKNESS);
}

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    vec3 normal = normalize(cross(dFdx(cameraRelativePosition), dFdy(cameraRelativePosition)));
    #ifdef HELION_GLOSSY_ONLY
    if (texture(GlossySampler, texCoord0).r < HELION_GLOSSY_CELL) {
        discard;
    }
    #elif defined(ALPHA_CUTOUT)
    if (texel.a * vertexAlpha < ALPHA_CUTOUT) {
        discard;
    }
    #endif
    float sceneDepth = texelFetch(SceneDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    if (gl_FragCoord.z < sceneDepth) {
        discard;
    }
    if (dot(normal, cameraRelativePosition) > 0.0) {
        normal = -normal;
    }
    if (!helionInside(texCoord0, WaterStill) && !helionInside(texCoord0, WaterFlow)) {
        waterSurface = vec4(helionEncodeOctahedral(normal), HELION_GLOSSY_MARKER, skyLight);
        waterTint = HELION_NO_TINT;
        return;
    }
    if (normal.y > HELION_FLAT_SURFACE) {
        normal = helionWaveNormal(wavePosition, GameTime * HELION_SECONDS_PER_DAY);
    }
    waterSurface = vec4(helionEncodeOctahedral(normal), helionWaterThickness(sceneDepth), skyLight);
    waterTint = vec4(vertexTint, HELION_TINT_PRESENT);
}
