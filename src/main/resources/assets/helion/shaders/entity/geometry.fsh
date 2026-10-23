#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <helion:helion_geometry.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
layout(location = 2) in vec4 vertexPerFaceColorBack;
layout(location = 3) in vec4 vertexPerFaceColorFront;
#else
layout(location = 2) in vec4 vertexColor;
#endif
layout(location = 4) in vec4 lightMapColor;
#ifndef NO_OVERLAY
layout(location = 5) in vec4 overlayColor;
#endif
layout(location = 6) in vec2 texCoord0;
layout(location = 7) in vec3 cameraRelativePosition;
layout(location = 8) in vec2 lightLevels;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 geometryNormal;
layout(location = 2) out vec4 geometryLight;
layout(location = 3) out vec4 geometryAlbedo;

const float HELION_ENTITY_FULLY_VISIBLE = 1.0;

vec3 faceNormal(vec3 position) {
    vec3 normal = normalize(cross(dFdx(position), dFdy(position)));
    return dot(normal, position) > 0.0 ? -normal : normal;
}

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    #ifdef ALPHA_CUTOUT
    if (texel.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
    #else
    vec4 faceVertexColor = vertexColor;
    #endif

    vec4 surface = texel * faceVertexColor * ColorModulator;
    #ifndef NO_OVERLAY
    surface.rgb = mix(overlayColor.rgb, surface.rgb, overlayColor.a);
    #endif

    fragColor = apply_fog(surface * lightMapColor, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    geometryNormal = vec4(helionEncodeNormal(faceNormal(cameraRelativePosition)), HELION_GEOMETRY_PRESENT);
    geometryLight = vec4(lightLevels, HELION_ENTITY_FULLY_VISIBLE, HELION_GEOMETRY_POROUS);
    float fogAmount = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    geometryAlbedo = vec4(surface.rgb, fogAmount);
}
