#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_transmittance_lookup.glsl>
#include <helion:helion_sky_color.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_SUN_TEXEL = 0;
const int HELION_MOON_TEXEL = 1;
const int HELION_DOME_TEXEL = 2;
const int HELION_SKY_MAP_WIDTH = 16;
const int HELION_SKY_MAP_ROWS = 8;
const int HELION_HEMISPHERE_AZIMUTHS = 16;
const int HELION_HEMISPHERE_ELEVATIONS = 8;

vec3 helionDirectLight(vec3 light, float illuminance) {
    vec3 position = vec3(0.0, ViewHeight, 0.0);
    vec2 uv = helionTransmittanceLookupUv(position, light);
    vec4 transmittance[HELION_SPECTRAL_GROUPS];
    for (int group = 0; group < HELION_SPECTRAL_GROUPS; group++) {
        transmittance[group] = helionTransmittanceGroup(group, uv);
    }
    float visible = helionGroundShadow(position, light);
    vec3 direct = helionSpectrumToRgb(transmittance) * illuminance * visible;
    float luminance = helionLuminance(direct);
    return mix(vec3(luminance * HELION_RAIN_DARKENING), direct, RainBrightness);
}

vec3 helionHemisphereLight() {
    vec3 sum = vec3(0.0);
    float weights = 0.0;
    for (int i = 0; i < HELION_HEMISPHERE_AZIMUTHS; i++) {
        for (int j = 0; j < HELION_HEMISPHERE_ELEVATIONS; j++) {
            float azimuth = 2.0 * HELION_PI * (float(i) + 0.5) / float(HELION_HEMISPHERE_AZIMUTHS);
            float cosZenith = (float(j) + 0.5) / float(HELION_HEMISPHERE_ELEVATIONS);
            float sinZenith = sqrt(1.0 - cosZenith * cosZenith);
            vec3 direction = vec3(cos(azimuth) * sinZenith, cosZenith, sin(azimuth) * sinZenith);
            sum += helionSkyRadiance(direction) * cosZenith;
            weights += cosZenith;
        }
    }
    return HELION_PI * sum / weights;
}

vec3 helionSkyMapDirection(ivec2 cell) {
    float azimuth = 2.0 * HELION_PI * (float(cell.x) + 0.5) / float(HELION_SKY_MAP_WIDTH);
    float height = (float(cell.y) + 0.5) / float(HELION_SKY_MAP_ROWS) * 2.0 - 1.0;
    float across = sqrt(max(1.0 - height * height, 0.0));
    return vec3(cos(azimuth) * across, height, sin(azimuth) * across);
}

void main() {
    int texel = int(gl_FragCoord.x);
    int row = int(gl_FragCoord.y);
    if (row > 0) {
        fragColor = vec4(helionSkyRadiance(helionSkyMapDirection(ivec2(texel, row - 1))), 1.0);
        return;
    }
    vec3 light;
    if (texel == HELION_SUN_TEXEL) {
        light = helionDirectLight(SunDirection, SunIlluminance);
    } else if (texel == HELION_MOON_TEXEL) {
        light = helionDirectLight(MoonDirection, MoonIlluminance);
    } else if (texel == HELION_DOME_TEXEL) {
        light = helionHemisphereLight();
    } else {
        light = vec3(0.0);
    }
    fragColor = vec4(light, 1.0);
}
