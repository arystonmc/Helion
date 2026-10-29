#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif
#include <helion:helion_geometry.glsl>
#include <helion:helion_lava.glsl>

uniform sampler2D Sampler0;
uniform sampler2D GlossySampler;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec3 cameraRelativePosition;
layout(location = 6) in vec4 surfaceColor;
layout(location = 7) in vec2 lightLevels;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 geometryNormal;
layout(location = 2) out vec4 geometryLight;
layout(location = 3) out vec4 geometryAlbedo;

const vec3 HELION_FOLIAGE_NORMAL = vec3(0.0, 1.0, 0.0);
const float HELION_SECONDS_PER_DAY = 1200.0;
const int HELION_WORLD_WRAP = 1023;
const float HELION_LAVA_FLAT = 0.9;
const float HELION_LAVA_FLOW_SPEED = 0.25;
const float HELION_MIN_DETERMINANT = 1.0e-12;

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

vec3 derivedNormal(vec3 position) {
    return normalize(cross(dFdx(position), dFdy(position)));
}

vec3 faceNormal(vec3 position, vec3 normal) {
    if (helionIsCrossPlane(normal)) {
        return HELION_FOLIAGE_NORMAL;
    }
    return dot(normal, position) > 0.0 ? -normal : normal;
}

void main() {
    vec4 texel = UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize);
    vec3 derived = derivedNormal(cameraRelativePosition);
    vec2 flow = helionFlowDirection(dFdx(cameraRelativePosition), dFdy(cameraRelativePosition), dFdx(texCoord0), dFdy(texCoord0));
    int cell = helionMaterialCell(texture(GlossySampler, texCoord0).r);
    if (helionIsLavaCell(cell) && abs(derived.y) > HELION_LAVA_FLAT) {
        float seconds = GameTime * HELION_SECONDS_PER_DAY;
        vec2 world = cameraRelativePosition.xz - CameraOffset.xz + vec2(CameraBlockPos.xz & HELION_WORLD_WRAP);
        vec2 drift = cell == HELION_CELL_LAVA_FLOW ? flow * seconds * HELION_LAVA_FLOW_SPEED : vec2(0.0);
        texel.rgb = helionLavaColor(texel.rgb, world - drift, seconds);
    }
    vec4 color = texel * vertexColor;
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif
    #ifdef HELION_SEE_THROUGH_GLASS
    if (helionIsPlainGlassCell(texture(GlossySampler, texCoord0).r)) {
        discard;
    }
    #endif

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    geometryNormal = vec4(helionEncodeNormal(faceNormal(cameraRelativePosition, derived)), HELION_GEOMETRY_PRESENT);
    float fogAmount = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    float material = helionIsCrossPlane(derived) ? HELION_GEOMETRY_POROUS : helionGeometryMaterial(texture(GlossySampler, texCoord0).r);
    geometryLight = vec4(lightLevels, chunkVisibility, material);
    geometryAlbedo = vec4((texel * surfaceColor).rgb, fogAmount);
}
