#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_transmittance_lookup.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 scattering0;
layout(location = 1) out vec4 scattering1;
layout(location = 2) out vec4 scattering2;
layout(location = 3) out vec4 scattering3;

const int HELION_DIRECTIONS_PER_AXIS = 8;
const int HELION_SCATTERING_STEPS = 24;
const float HELION_GROUND_OFFSET = 0.01;
const float HELION_MIN_EXTINCTION = 1.0e-7;
const float HELION_MIN_REMAINING = 1.0e-4;

void main() {
    vec2 uv = helionFromSubUv(gl_FragCoord.xy / HELION_MULTIPLE_SCATTERING_SIZE, HELION_MULTIPLE_SCATTERING_SIZE);
    float sunZenithCos = uv.x * 2.0 - 1.0;
    float viewHeight = HELION_BOTTOM_RADIUS + HELION_GROUND_OFFSET + uv.y * (HELION_TOP_RADIUS - HELION_BOTTOM_RADIUS - 2.0 * HELION_GROUND_OFFSET);
    vec3 origin = vec3(0.0, viewHeight, 0.0);
    vec3 sunDirection = vec3(0.0, sunZenithCos, sqrt(max(0.0, 1.0 - sunZenithCos * sunZenithCos)));
    vec4 secondOrder[HELION_SPECTRAL_GROUPS];
    vec4 transfer[HELION_SPECTRAL_GROUPS];
    for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
        secondOrder[group] = vec4(0.0);
        transfer[group] = vec4(0.0);
    }
    for (int i = 0; i < HELION_DIRECTIONS_PER_AXIS; i++) {
        for (int j = 0; j < HELION_DIRECTIONS_PER_AXIS; j++) {
            float azimuth = 2.0 * HELION_PI * (float(i) + 0.5) / float(HELION_DIRECTIONS_PER_AXIS);
            float zenith = acos(1.0 - 2.0 * (float(j) + 0.5) / float(HELION_DIRECTIONS_PER_AXIS));
            vec3 direction = vec3(cos(azimuth) * sin(zenith), cos(zenith), sin(azimuth) * sin(zenith));
            float groundDistance = helionRaySphere(origin, direction, HELION_BOTTOM_RADIUS);
            bool hitsGround = groundDistance > 0.0;
            float distance = hitsGround ? groundDistance : helionRaySphere(origin, direction, HELION_TOP_RADIUS);
            float stepLength = max(distance, 0.0) / float(HELION_SCATTERING_STEPS);
            vec4 throughput[HELION_SPECTRAL_GROUPS];
            for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
                throughput[group] = vec4(1.0);
            }
            for (int step = 0; step < HELION_SCATTERING_STEPS; step++) {
                vec3 position = origin + direction * ((float(step) + 0.5) * stepLength);
                HelionDensity density = helionDensity(length(position) - HELION_BOTTOM_RADIUS);
                vec2 sunUv = helionTransmittanceLookupUv(position, sunDirection);
                float sunVisible = helionGroundShadow(position, sunDirection);
                for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
                    vec4 scattering = helionScattering(density, group);
                    vec4 extinction = helionExtinction(density, group);
                    vec4 stepTransmittance = exp(-extinction * stepLength);
                    vec4 sunLight = helionTransmittanceGroup(group, sunUv) * sunVisible;
                    vec4 integration = (vec4(1.0) - stepTransmittance) / max(extinction, vec4(HELION_MIN_EXTINCTION));
                    secondOrder[group] += throughput[group] * scattering * helionIsotropicPhase() * sunLight * integration;
                    transfer[group] += throughput[group] * scattering * integration;
                    throughput[group] *= stepTransmittance;
                }
            }
            if (hitsGround) {
                vec3 groundPosition = origin + direction * groundDistance;
                float sunCos = clamp(dot(normalize(groundPosition), sunDirection), 0.0, 1.0);
                vec2 groundUv = helionTransmittanceLookupUv(groundPosition, sunDirection);
                for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
                    vec4 groundSun = helionTransmittanceGroup(group, groundUv);
                    secondOrder[group] += throughput[group] * groundSun * sunCos * HELION_GROUND_ALBEDO / HELION_PI;
                }
            }
        }
    }
    float directions = float(HELION_DIRECTIONS_PER_AXIS * HELION_DIRECTIONS_PER_AXIS);
    vec4 result[HELION_SPECTRAL_GROUPS];
    for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
        vec4 secondOrderAverage = secondOrder[group] / directions;
        vec4 transferAverage = transfer[group] / directions;
        result[group] = secondOrderAverage / max(vec4(1.0) - transferAverage, vec4(HELION_MIN_REMAINING));
    }
    scattering0 = result[0];
    scattering1 = result[1];
    scattering2 = result[2];
    scattering3 = result[3];
}
