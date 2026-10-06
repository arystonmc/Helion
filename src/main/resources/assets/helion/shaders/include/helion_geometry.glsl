#ifndef HELION_GEOMETRY_GLSL
#define HELION_GEOMETRY_GLSL

const float HELION_GEOMETRY_PRESENT = 1.0;
const float HELION_GEOMETRY_PRESENT_THRESHOLD = 0.5;
const float HELION_MAX_LIGHT_COORDINATE = 240.0;

vec3 helionEncodeNormal(vec3 normal) {
    return normal * 0.5 + 0.5;
}

#endif
