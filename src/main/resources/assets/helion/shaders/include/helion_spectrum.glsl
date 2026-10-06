#ifndef HELION_SPECTRUM_GLSL
#define HELION_SPECTRUM_GLSL

#include <helion:helion_atmosphere.glsl>

const int HELION_SPECTRAL_GROUPS = 4;

layout(std140) uniform HelionSpectrum {
    vec4 RayleighScattering[HELION_SPECTRAL_GROUPS];
    vec4 OzoneAbsorption[HELION_SPECTRAL_GROUPS];
    vec4 ToRed[HELION_SPECTRAL_GROUPS];
    vec4 ToGreen[HELION_SPECTRAL_GROUPS];
    vec4 ToBlue[HELION_SPECTRAL_GROUPS];
};

struct HelionDensity {
    float rayleigh;
    float mie;
    float ozone;
};

HelionDensity helionDensity(float height) {
    HelionDensity density;
    density.rayleigh = exp(-height / HELION_RAYLEIGH_SCALE_HEIGHT);
    density.mie = exp(-height / HELION_MIE_SCALE_HEIGHT);
    density.ozone = max(0.0, 1.0 - abs(height - HELION_OZONE_CENTER_HEIGHT) / HELION_OZONE_HALF_WIDTH);
    return density;
}

vec4 helionRayleigh(HelionDensity density, int group) {
    return RayleighScattering[group] * density.rayleigh;
}

vec4 helionScattering(HelionDensity density, int group) {
    return helionRayleigh(density, group) + vec4(HELION_MIE_SCATTERING * density.mie);
}

vec4 helionExtinction(HelionDensity density, int group) {
    return helionRayleigh(density, group) + vec4(HELION_MIE_EXTINCTION * density.mie) + OzoneAbsorption[group] * density.ozone;
}

vec3 helionSpectrumToRgb(vec4 radiance[HELION_SPECTRAL_GROUPS]) {
    vec3 rgb = vec3(0.0);
    for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
        rgb += vec3(dot(ToRed[group], radiance[group]), dot(ToGreen[group], radiance[group]), dot(ToBlue[group], radiance[group]));
    }
    return rgb;
}

#endif
