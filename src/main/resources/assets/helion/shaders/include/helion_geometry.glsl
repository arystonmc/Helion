#ifndef HELION_GEOMETRY_GLSL
#define HELION_GEOMETRY_GLSL

const float HELION_GEOMETRY_PRESENT = 1.0;
const float HELION_GEOMETRY_PRESENT_THRESHOLD = 0.5;
const float HELION_GEOMETRY_POROUS = 0.875;
const float HELION_GEOMETRY_GLOSSY = 0.75;
const float HELION_GEOMETRY_DRY = 0.625;
const float HELION_GEOMETRY_MUDDY = 0.5625;
const float HELION_GEOMETRY_LAVA = 0.6875;
const float HELION_GEOMETRY_MATERIAL_TOLERANCE = 0.03;
const float HELION_GLOSSY_CELL = 0.9;
const float HELION_MATERIAL_CELL_STEPS = 8.0;
const int HELION_CELL_EMISSIVE = 1;
const int HELION_CELL_MUDDY = 2;
const int HELION_CELL_POROUS = 4;
const int HELION_CELL_LAVA_FLOW = 5;
const int HELION_CELL_DRY = 6;
const int HELION_CELL_LAVA = 7;
const int HELION_CELL_GLOSSY = 8;

int helionMaterialCell(float cell) {
    return int(cell * HELION_MATERIAL_CELL_STEPS + 0.5);
}

bool helionIsLavaCell(int material) {
    return material == HELION_CELL_LAVA || material == HELION_CELL_LAVA_FLOW;
}

bool helionIsEmissiveCell(int material) {
    return material == HELION_CELL_EMISSIVE || helionIsLavaCell(material);
}

float helionGeometryMaterial(float cell) {
    int material = helionMaterialCell(cell);
    if (helionIsLavaCell(material)) {
        return HELION_GEOMETRY_LAVA;
    }
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

bool helionIsDryGeometry(float presence) {
    return helionIsGeometryMaterial(presence, HELION_GEOMETRY_DRY) || helionIsGeometryMaterial(presence, HELION_GEOMETRY_LAVA);
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
