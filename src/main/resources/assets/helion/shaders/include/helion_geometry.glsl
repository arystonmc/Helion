#ifndef HELION_GEOMETRY_GLSL
#define HELION_GEOMETRY_GLSL

const float HELION_GEOMETRY_PRESENT = 1.0;
const float HELION_GEOMETRY_PRESENT_THRESHOLD = 0.5;
const float HELION_GEOMETRY_POROUS = 0.875;
const float HELION_GEOMETRY_GLOSSY = 0.75;
const float HELION_GEOMETRY_DRY = 0.625;
const float HELION_GEOMETRY_MUDDY = 0.5625;
const float HELION_GEOMETRY_MATERIAL_TOLERANCE = 0.03;
const float HELION_GLOSSY_CELL = 0.9;
const float HELION_MATERIAL_CELL_STEPS = 4.0;
const int HELION_CELL_MUDDY = 1;
const int HELION_CELL_POROUS = 2;
const int HELION_CELL_DRY = 3;
const int HELION_CELL_GLOSSY = 4;

float helionGeometryMaterial(float cell) {
    int material = int(cell * HELION_MATERIAL_CELL_STEPS + 0.5);
    if (material == HELION_CELL_GLOSSY) {
        return HELION_GEOMETRY_GLOSSY;
    }
    if (material == HELION_CELL_DRY) {
        return HELION_GEOMETRY_DRY;
    }
    if (material == HELION_CELL_MUDDY) {
        return HELION_GEOMETRY_MUDDY;
    }
    return material == HELION_CELL_POROUS ? HELION_GEOMETRY_POROUS : HELION_GEOMETRY_PRESENT;
}

bool helionIsGeometryMaterial(float presence, float material) {
    return abs(presence - material) < HELION_GEOMETRY_MATERIAL_TOLERANCE;
}

bool helionIsGlossyGeometry(float presence) {
    return helionIsGeometryMaterial(presence, HELION_GEOMETRY_GLOSSY);
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
