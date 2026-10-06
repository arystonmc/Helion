#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_spectrum.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 transmittance0;
layout(location = 1) out vec4 transmittance1;
layout(location = 2) out vec4 transmittance2;
layout(location = 3) out vec4 transmittance3;

const int HELION_TRANSMITTANCE_STEPS = 64;

void main() {
    vec2 uv = helionFromSubUv(gl_FragCoord.xy / HELION_TRANSMITTANCE_SIZE, HELION_TRANSMITTANCE_SIZE);
    float viewHeight;
    float viewZenithCos;
    helionTransmittanceParameters(uv, viewHeight, viewZenithCos);
    vec3 origin = vec3(0.0, viewHeight, 0.0);
    vec3 direction = vec3(sqrt(max(0.0, 1.0 - viewZenithCos * viewZenithCos)), viewZenithCos, 0.0);
    float distance = max(helionRaySphere(origin, direction, HELION_TOP_RADIUS), 0.0);
    float stepLength = distance / float(HELION_TRANSMITTANCE_STEPS);
    vec4 opticalDepth[HELION_SPECTRAL_GROUPS];
    for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
        opticalDepth[group] = vec4(0.0);
    }
    for (int step = 0; step < HELION_TRANSMITTANCE_STEPS; step++) {
        vec3 position = origin + direction * ((float(step) + 0.5) * stepLength);
        HelionDensity density = helionDensity(length(position) - HELION_BOTTOM_RADIUS);
        for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
            opticalDepth[group] += helionExtinction(density, group) * stepLength;
        }
    }
    transmittance0 = exp(-opticalDepth[0]);
    transmittance1 = exp(-opticalDepth[1]);
    transmittance2 = exp(-opticalDepth[2]);
    transmittance3 = exp(-opticalDepth[3]);
}
