#ifndef HELION_GEOMETRY_GLSL
#define HELION_GEOMETRY_GLSL

const float HELION_GEOMETRY_PRESENT = 1.0;
const float HELION_GEOMETRY_PRESENT_THRESHOLD = 0.5;
const float HELION_GEOMETRY_GLOSSY = 0.75;
const float HELION_GEOMETRY_GLOSSY_TOLERANCE = 0.125;
const float HELION_GLOSSY_CELL = 0.5;

bool helionIsGlossyGeometry(float presence) {
    return abs(presence - HELION_GEOMETRY_GLOSSY) < HELION_GEOMETRY_GLOSSY_TOLERANCE;
}
const float HELION_MAX_LIGHT_COORDINATE = 240.0;

vec3 helionEncodeNormal(vec3 normal) {
    return normal * 0.5 + 0.5;
}

const float HELION_CROSS_PLANE_TOLERANCE = 0.2;

bool helionIsCrossPlane(vec3 normal) {
    vec3 size = abs(normal);
    return size.y < HELION_CROSS_PLANE_TOLERANCE && abs(size.x - size.z) < HELION_CROSS_PLANE_TOLERANCE;
}

#endif
