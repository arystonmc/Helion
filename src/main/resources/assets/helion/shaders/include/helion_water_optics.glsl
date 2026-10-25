#ifndef HELION_WATER_OPTICS_GLSL
#define HELION_WATER_OPTICS_GLSL

const vec3 HELION_OPTICS_PURE_ABSORPTION = vec3(0.36, 0.058, 0.05);
const vec3 HELION_OPTICS_ORGANIC_ABSORPTION = vec3(0.05, 0.13, 0.36);
const vec3 HELION_OPTICS_PARTICLE_SHAPE = vec3(0.82, 1.0, 1.08);
const float HELION_OPTICS_LAKE_PARTICLES = 0.025;
const float HELION_OPTICS_OCEAN_PARTICLES = 0.015;
const float HELION_OPTICS_SWAMP_PARTICLES = 0.15;
const float HELION_OPTICS_TROPICAL_CLARITY = 0.75;
const float HELION_OPTICS_SWAMP_ORGANIC = 1.6;
const float HELION_OPTICS_LAKE_ORGANIC = 0.06;
const float HELION_OPTICS_TURBID_PARTICLES = 0.09;
const float HELION_OPTICS_OPEN_FROM = 4.0;
const float HELION_OPTICS_OPEN_TO = 16.0;
const float HELION_OPTICS_MURK_FROM = -0.04;
const float HELION_OPTICS_MURK_TO = 0.06;
const float HELION_OPTICS_TROPICAL_FROM = 0.62;
const float HELION_OPTICS_TROPICAL_TO = 0.86;
const float HELION_OPTICS_HUE_SHARE = 0.35;
const float HELION_OPTICS_MURKY_HUE_SHARE = 1.0;
const float HELION_OPTICS_MIN_CHANNEL = 1.0e-3;

struct HelionWaterOptics {
    vec3 absorption;
    vec3 scattering;
    vec3 extinction;
    vec3 albedo;
};

float helionOpticsMurk(vec3 tintSrgb) {
    return 1.0 - smoothstep(HELION_OPTICS_MURK_FROM, HELION_OPTICS_MURK_TO, tintSrgb.b - max(tintSrgb.r, tintSrgb.g));
}

float helionOpticsTropical(vec3 tintSrgb) {
    return smoothstep(HELION_OPTICS_TROPICAL_FROM, HELION_OPTICS_TROPICAL_TO, tintSrgb.g / max(tintSrgb.b, HELION_OPTICS_MIN_CHANNEL));
}

HelionWaterOptics helionWaterOptics(vec3 tintSrgb, float columnDepth, float turbidity) {
    float murk = helionOpticsMurk(tintSrgb);
    float tropical = helionOpticsTropical(tintSrgb) * (1.0 - murk);
    float open = smoothstep(HELION_OPTICS_OPEN_FROM, HELION_OPTICS_OPEN_TO, columnDepth);
    float clearParticles = mix(HELION_OPTICS_LAKE_PARTICLES, HELION_OPTICS_OCEAN_PARTICLES, open) * (1.0 - HELION_OPTICS_TROPICAL_CLARITY * tropical);
    float particles = mix(clearParticles, HELION_OPTICS_SWAMP_PARTICLES, murk) + turbidity * HELION_OPTICS_TURBID_PARTICLES;
    float organic = mix(HELION_OPTICS_LAKE_ORGANIC * (1.0 - open) * (1.0 - tropical), HELION_OPTICS_SWAMP_ORGANIC, murk);
    vec3 hue = tintSrgb / max(max(tintSrgb.r, tintSrgb.g), max(tintSrgb.b, HELION_OPTICS_MIN_CHANNEL));
    HelionWaterOptics optics;
    optics.absorption = HELION_OPTICS_PURE_ABSORPTION + organic * HELION_OPTICS_ORGANIC_ABSORPTION;
    optics.scattering = particles * HELION_OPTICS_PARTICLE_SHAPE * mix(vec3(1.0), hue, mix(HELION_OPTICS_HUE_SHARE, HELION_OPTICS_MURKY_HUE_SHARE, murk));
    optics.extinction = optics.absorption + optics.scattering;
    optics.albedo = optics.scattering / optics.extinction;
    return optics;
}

#endif
