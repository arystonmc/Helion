#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <helion:helion_view.glsl>
#include <helion:helion_water.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_wetness.glsl>
#include <helion:helion_water_waves.glsl>

uniform sampler2D Sampler0;
uniform sampler2D SceneDepthSampler;
uniform sampler2D GlossySampler;
uniform sampler2D WaveSampler;

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
layout(location = 6) in float oceanSwell;

layout(location = 0) out vec4 waterSurface;
layout(location = 1) out vec4 waterTint;

const float HELION_SECONDS_PER_DAY = 1200.0;
const float HELION_FLAT_SURFACE = 0.9;
const float HELION_SPRITE_EDGE = 1.0e-4;
const vec4 HELION_NO_TINT = vec4(0.0);
const float HELION_SIMULATION_TEXELS = 1024.0;
const float HELION_SIMULATION_CELLS_PER_BLOCK = 8.0;
const float HELION_SIMULATION_FADE_START = 44.0;
const float HELION_SIMULATION_FADE_END = 60.0;
const float HELION_SIMULATION_SLOPE_SHARE = 0.85;
const float HELION_SIMULATION_SLOPE_SCALE = HELION_SIMULATION_CELLS_PER_BLOCK / 2.0 * HELION_SIMULATION_SLOPE_SHARE;
const float HELION_SIMULATION_ON = 0.5;
const float HELION_FLOW_SPEED = 1.6;
const float HELION_FAR_CALM = 0.6;
const float HELION_CAPILLARY_RAIN = 0.6;
const float HELION_ROUGHNESS_FROM_VARIANCE = 2.0;
const float HELION_OCEAN_SHADING = 0.35;
const float HELION_WATER_RIPPLES = 1.6;
const float HELION_CALM_FROM = 12.0;
const float HELION_CALM_TO = 80.0;
const float HELION_RIPPLE_DETAIL_FROM = 0.08;
const float HELION_RIPPLE_DETAIL_TO = 0.25;
const float HELION_MIN_DETERMINANT = 1.0e-12;

bool helionInside(vec2 uv, vec4 rect) {
    return all(greaterThanEqual(uv, rect.xy - HELION_SPRITE_EDGE)) && all(lessThanEqual(uv, rect.zw + HELION_SPRITE_EDGE));
}

vec2 helionFlowDirection(vec3 positionX, vec3 positionY, vec2 uvX, vec2 uvY) {
    float determinant = uvX.x * uvY.y - uvY.x * uvX.y;
    if (abs(determinant) < HELION_MIN_DETERMINANT) {
        return vec2(0.0);
    }
    vec2 screen = vec2(-uvY.x, uvX.x) / determinant;
    vec3 along = positionX * screen.x + positionY * screen.y;
    float length2 = dot(along.xz, along.xz);
    return length2 > 0.0 ? along.xz * inversesqrt(length2) : vec2(0.0);
}

vec2 helionSimulatedSlope(vec2 position, float horizontalDistance) {
    if (WaterRain.z < HELION_SIMULATION_ON) {
        return vec2(0.0);
    }
    vec2 uv = position * HELION_SIMULATION_CELLS_PER_BLOCK / HELION_SIMULATION_TEXELS;
    float texelStep = 1.0 / HELION_SIMULATION_TEXELS;
    float right = textureLod(WaveSampler, uv + vec2(texelStep, 0.0), 0.0).r;
    float left = textureLod(WaveSampler, uv - vec2(texelStep, 0.0), 0.0).r;
    float front = textureLod(WaveSampler, uv + vec2(0.0, texelStep), 0.0).r;
    float back = textureLod(WaveSampler, uv - vec2(0.0, texelStep), 0.0).r;
    float fade = 1.0 - smoothstep(HELION_SIMULATION_FADE_START, HELION_SIMULATION_FADE_END, horizontalDistance);
    return vec2(right - left, front - back) * HELION_SIMULATION_SLOPE_SCALE * fade;
}

vec3 helionWaveNormal(vec2 position, float seconds, float horizontalDistance, float footprint, out float roughness) {
    float calm = mix(1.0, HELION_FAR_CALM, smoothstep(HELION_CALM_FROM, HELION_CALM_TO, horizontalDistance));
    float wind = (1.0 + WaterRain.x * HELION_CAPILLARY_RAIN) * helionCapillaryGust(position, seconds);
    vec2 capillary = helionCapillarySlope(position, seconds, footprint) * wind;
    roughness = sqrt(HELION_ROUGHNESS_FROM_VARIANCE * helionCapillaryLostVariance(footprint)) * wind;
    vec2 ocean = oceanSwell > 0.0 ? helionOceanSlope(position, seconds) * oceanSwell * HELION_OCEAN_SHADING : vec2(0.0);
    vec2 slope = (helionSwellSlope(position, seconds, footprint) + ocean) * calm;
    vec2 ripples = WaterRain.x > 0.0 ? helionRainRipples(position, WaterRain.y) * WaterRain.x * HELION_WATER_RIPPLES * (1.0 - smoothstep(HELION_RIPPLE_DETAIL_FROM, HELION_RIPPLE_DETAIL_TO, footprint)) : vec2(0.0);
    vec2 simulated = helionSimulatedSlope(position, horizontalDistance);
    return normalize(vec3(-slope.x * WaveStrength - capillary.x - ripples.x - simulated.x, 1.0, -slope.y * WaveStrength - capillary.y - ripples.y - simulated.y));
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
    vec2 footprintVector = fwidth(wavePosition);
    float footprint = max(footprintVector.x, footprintVector.y);
    vec2 flow = helionFlowDirection(dFdx(cameraRelativePosition), dFdy(cameraRelativePosition), dFdx(texCoord0), dFdy(texCoord0));
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
    bool flowing = helionInside(texCoord0, WaterFlow);
    float seconds = GameTime * HELION_SECONDS_PER_DAY;
    vec2 flowing2d = flowing ? flow * seconds * HELION_FLOW_SPEED : vec2(0.0);
    bool falling = flowing && abs(normal.y) < HELION_FLAT_SURFACE;
    float roughness = 0.0;
    if (normal.y > HELION_FLAT_SURFACE) {
        normal = helionWaveNormal(wavePosition - flowing2d, seconds, length(cameraRelativePosition.xz), footprint, roughness);
    } else if (normal.y < -HELION_FLAT_SURFACE) {
        normal = -helionWaveNormal(wavePosition, seconds, length(cameraRelativePosition.xz), footprint, roughness);
    }
    waterSurface = vec4(helionEncodeOctahedral(normal), helionWaterThickness(sceneDepth), skyLight);
    waterTint = vec4(vertexTint, falling ? HELION_WATER_TINT_FALLING : helionEncodeWaterRoughness(roughness));
}
