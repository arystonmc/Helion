#ifndef HELION_VOXEL_PROBE_GLSL
#define HELION_VOXEL_PROBE_GLSL

const int HELION_PROBES_PER_AXIS = 32;
const int HELION_PROBE_ROW = 256;
const float HELION_PROBE_SPACING = 4.0;
const float HELION_SH_BAND0 = 0.282095;
const float HELION_SH_BAND1 = 0.488603;
const float HELION_SH_COSINE0 = 3.14159265;
const float HELION_SH_COSINE1 = 2.09439510;
const float HELION_PI = 3.14159265;
const float HELION_PROBE_PLACED = 0.5;

int helionProbeIndex(ivec3 probe) {
    return probe.x + HELION_PROBES_PER_AXIS * (probe.z + HELION_PROBES_PER_AXIS * probe.y);
}

ivec2 helionProbeTexel(ivec3 probe) {
    int index = helionProbeIndex(probe);
    return ivec2(index % HELION_PROBE_ROW, index / HELION_PROBE_ROW);
}

ivec3 helionProbeAt(ivec2 texel) {
    int index = texel.x + texel.y * HELION_PROBE_ROW;
    return ivec3(index % HELION_PROBES_PER_AXIS, index / (HELION_PROBES_PER_AXIS * HELION_PROBES_PER_AXIS), (index / HELION_PROBES_PER_AXIS) % HELION_PROBES_PER_AXIS);
}

bool helionProbeInGrid(ivec3 probe) {
    return all(greaterThanEqual(probe, ivec3(0))) && all(lessThan(probe, ivec3(HELION_PROBES_PER_AXIS)));
}

vec3 helionProbePlaced(ivec3 probe, vec3 placement) {
    return (vec3(probe) + placement) * HELION_PROBE_SPACING;
}

vec4 helionShBasis(vec3 direction) {
    return vec4(HELION_SH_BAND0, HELION_SH_BAND1 * direction.y, HELION_SH_BAND1 * direction.z, HELION_SH_BAND1 * direction.x);
}

float helionShIrradiance(vec4 coefficients, vec3 normal) {
    vec4 basis = helionShBasis(normal);
    float irradiance = HELION_SH_COSINE0 * coefficients.x * basis.x
        + HELION_SH_COSINE1 * (coefficients.y * basis.y + coefficients.z * basis.z + coefficients.w * basis.w);
    return max(irradiance / HELION_PI, 0.0);
}

#endif
