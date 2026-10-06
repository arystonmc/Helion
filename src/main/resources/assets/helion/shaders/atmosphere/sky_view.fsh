#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_transmittance_lookup.glsl>
#include <helion:helion_scattering_lookup.glsl>
#include <helion:helion_sky.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 scatteringColor;
layout(location = 1) out vec4 unphasedMieColor;

const int HELION_SKY_VIEW_STEPS = 40;
const float HELION_MIN_EXTINCTION = 1.0e-7;
const float HELION_STEP_DISTRIBUTION = 2.0;

vec3 helionLightDirection() {
    #ifdef HELION_MOON_VIEW
    return MoonDirection;
    #else
    return SunDirection;
    #endif
}

float helionStepPosition(int step) {
    return pow(float(step) / float(HELION_SKY_VIEW_STEPS), HELION_STEP_DISTRIBUTION);
}

void main() {
    vec2 uv = helionFromSubUv(gl_FragCoord.xy / HELION_SKY_VIEW_SIZE, HELION_SKY_VIEW_SIZE);
    float viewZenithAngle;
    float azimuth;
    helionSkyViewParameters(uv, ViewHeight, viewZenithAngle, azimuth);
    vec3 light = helionLightDirection();
    float lightZenithCos = clamp(light.y, -1.0, 1.0);
    vec3 lightDirection = vec3(sqrt(max(0.0, 1.0 - lightZenithCos * lightZenithCos)), lightZenithCos, 0.0);
    vec3 viewDirection = vec3(sin(viewZenithAngle) * cos(azimuth), cos(viewZenithAngle), sin(viewZenithAngle) * sin(azimuth));
    vec3 origin = vec3(0.0, ViewHeight, 0.0);

    float groundDistance = helionRaySphere(origin, viewDirection, HELION_BOTTOM_RADIUS);
    float distance = max(groundDistance > 0.0 ? groundDistance : helionRaySphere(origin, viewDirection, HELION_TOP_RADIUS), 0.0);
    float cosTheta = dot(viewDirection, lightDirection);
    float rayleighPhase = helionRayleighPhase(cosTheta);
    vec4 radiance[HELION_SPECTRAL_GROUPS];
    vec4 unphasedMie[HELION_SPECTRAL_GROUPS];
    vec4 throughput[HELION_SPECTRAL_GROUPS];
    for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
        radiance[group] = vec4(0.0);
        unphasedMie[group] = vec4(0.0);
        throughput[group] = vec4(1.0);
    }
    for (int step = 0; step < HELION_SKY_VIEW_STEPS; step++) {
        float near = helionStepPosition(step) * distance;
        float far = helionStepPosition(step + 1) * distance;
        float stepLength = far - near;
        vec3 position = origin + viewDirection * (0.5 * (near + far));
        HelionDensity density = helionDensity(length(position) - HELION_BOTTOM_RADIUS);
        vec2 lightUv = helionTransmittanceLookupUv(position, lightDirection);
        vec2 scatteringUv = helionMultipleScatteringLookupUv(position, lightDirection);
        float lightVisible = helionGroundShadow(position, lightDirection);
        for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
            vec4 rayleigh = helionRayleigh(density, group);
            vec4 extinction = helionExtinction(density, group);
            vec4 stepTransmittance = exp(-extinction * stepLength);
            vec4 lightReaching = helionTransmittanceGroup(group, lightUv) * lightVisible;
            vec4 rayleighSingle = rayleigh * rayleighPhase * lightReaching;
            vec4 mieSingle = vec4(HELION_MIE_SCATTERING * density.mie) * lightReaching;
            vec4 multiple = helionScattering(density, group) * helionMultipleScatteringGroup(group, scatteringUv);
            vec4 integration = (vec4(1.0) - stepTransmittance) / max(extinction, vec4(HELION_MIN_EXTINCTION));
            radiance[group] += throughput[group] * (rayleighSingle + multiple) * integration;
            unphasedMie[group] += throughput[group] * mieSingle * integration;
            throughput[group] *= stepTransmittance;
        }
    }
    scatteringColor = vec4(helionSpectrumToRgb(radiance), 1.0);
    unphasedMieColor = vec4(helionSpectrumToRgb(unphasedMie), 1.0);
}
