#ifndef HELION_PATH_TRACING_GLSL
#define HELION_PATH_TRACING_GLSL

#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>

const vec3 HELION_VANILLA_SHADE = vec3(0.6, 1.0, 0.8);
const float HELION_VANILLA_SHADE_DOWN = 0.5;
const float HELION_PATH_NOTHING = 0.0;
const float HELION_PATH_LIT = 1.0;
const float HELION_PATH_EMISSIVE = 2.0;
const float HELION_PATH_SIGNAL_SPLIT = 1.5;
const float HELION_PATH_MIN_ALBEDO = 1.0e-3;
const float HELION_PATH_VANILLA_LIMIT = 4.0;

vec3 helionUnshaded(vec3 albedo, vec3 normal) {
    if (helionIsCrossPlane(normal)) {
        return albedo;
    }
    vec3 share = normal * normal;
    float shade = share.x * HELION_VANILLA_SHADE.x + share.z * HELION_VANILLA_SHADE.z + share.y * (normal.y > 0.0 ? HELION_VANILLA_SHADE.y : HELION_VANILLA_SHADE_DOWN);
    return min(albedo / shade, vec3(1.0));
}

vec3 helionSurfaceAlbedo(vec3 albedo, vec3 normal) {
    return helionSrgbToLinear(helionUnshaded(albedo, normal));
}

bool helionIsEmissiveGeometry(float material) {
    return helionIsGeometryMaterial(material, HELION_GEOMETRY_EMISSIVE) || helionIsGeometryMaterial(material, HELION_GEOMETRY_LAVA);
}

bool helionIsEmissiveSignal(float signal) {
    return signal > HELION_PATH_SIGNAL_SPLIT;
}

ivec2 helionTraceSource(ivec2 pixel, ivec2 traced, ivec2 full) {
    return min(pixel * ((full + traced - 1) / traced), full - 1);
}

vec2 helionTraceScreen(ivec2 source, ivec2 full) {
    return (vec2(source) + 0.5) / vec2(full);
}

#endif
