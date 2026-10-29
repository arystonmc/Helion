#ifndef HELION_RAIN_COVER_GLSL
#define HELION_RAIN_COVER_GLSL

const int HELION_RAIN_COVER_MASK = 63;
const float HELION_RAIN_COVER_REACH = 28.0;
const float HELION_RAIN_CELL_LIFT = 0.5;

bool helionRainFalls(ivec3 cell) {
    return texelFetch(RainCoverSampler, cell.xz & HELION_RAIN_COVER_MASK, 0).r <= float(cell.y);
}

float helionRainReaches(vec3 world, vec3 normal, float distance) {
    if (distance > HELION_RAIN_COVER_REACH) {
        return 1.0;
    }
    return helionRainFalls(ivec3(floor(world + normal * HELION_RAIN_CELL_LIFT))) ? 1.0 : 0.0;
}

#endif
