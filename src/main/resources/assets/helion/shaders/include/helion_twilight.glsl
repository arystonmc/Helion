#ifndef HELION_TWILIGHT_GLSL
#define HELION_TWILIGHT_GLSL

#include <helion:helion_atmosphere.glsl>

const vec3 HELION_RAYLEIGH_OPTICAL_DEPTH = vec3(5.802e-3, 13.558e-3, 33.1e-3) * HELION_RAYLEIGH_SCALE_HEIGHT;
const vec3 HELION_OZONE_OPTICAL_DEPTH = vec3(0.650e-3, 1.881e-3, 0.085e-3) * HELION_OZONE_HALF_WIDTH;
const float HELION_MIE_OPTICAL_DEPTH = HELION_MIE_EXTINCTION * HELION_MIE_SCALE_HEIGHT;
const float HELION_AIR_MASS_SLOPE = 0.50572;
const float HELION_AIR_MASS_OFFSET_DEGREES = 6.07995;
const float HELION_AIR_MASS_EXPONENT = -1.6364;
const float HELION_LOWEST_AIR_MASS_DEGREES = -1.0;
const float HELION_MAX_AIR_MASS = 60.0;

const float HELION_GOLDEN_HOUR_HEIGHT = 0.3;
const float HELION_TWILIGHT_CENTER = -0.01;
const float HELION_TWILIGHT_WIDTH_ABOVE = 0.18;
const float HELION_TWILIGHT_WIDTH_BELOW = 0.05;
const float HELION_TWILIGHT_GLOW = 0.06;
const float HELION_GLOW_FOCUS = 4.0;
const float HELION_GLOW_HEIGHT = 0.14;
const float HELION_GLOW_GOLD_HEIGHT = 0.3;
const vec3 HELION_GLOW_EMBER = vec3(1.0, 0.3, 0.07);
const vec3 HELION_GLOW_GOLD = vec3(1.0, 0.62, 0.26);
const vec3 HELION_GLOW_ROSE = vec3(0.8, 0.32, 0.6);
const float HELION_ROSE_STRENGTH = 0.12;
const float HELION_ROSE_HEIGHT = 0.2;
const float HELION_ROSE_WIDTH = 0.08;
const float HELION_ROSE_SUN_HIGH = 0.02;
const float HELION_ROSE_SUN_LOW = -0.07;
const vec3 HELION_BELT_COLOR = vec3(0.95, 0.6, 0.7);
const float HELION_BELT_STRENGTH = 0.25;
const float HELION_BELT_HEIGHT = 0.08;
const float HELION_BELT_WIDTH = 0.06;
const float HELION_BELT_FOCUS = 2.0;
const float HELION_MIN_TWILIGHT = 1.0e-3;
const float HELION_MIN_HORIZONTAL = 1.0e-5;

float helionGoldenHour(float sunHeight) {
    return 1.0 - smoothstep(0.0, HELION_GOLDEN_HOUR_HEIGHT, abs(sunHeight));
}

float helionAirMass(float elevationSin) {
    float elevation = max(degrees(asin(clamp(elevationSin, -1.0, 1.0))), HELION_LOWEST_AIR_MASS_DEGREES);
    float denominator = sin(radians(elevation)) + HELION_AIR_MASS_SLOPE * pow(elevation + HELION_AIR_MASS_OFFSET_DEGREES, HELION_AIR_MASS_EXPONENT);
    return min(1.0 / max(denominator, 1.0 / HELION_MAX_AIR_MASS), HELION_MAX_AIR_MASS);
}

vec3 helionCelestialTransmittance(float elevationSin) {
    vec3 opticalDepth = HELION_RAYLEIGH_OPTICAL_DEPTH + vec3(HELION_MIE_OPTICAL_DEPTH) + HELION_OZONE_OPTICAL_DEPTH;
    return exp(-opticalDepth * helionAirMass(elevationSin));
}

float helionFacing(vec3 direction, vec3 sun) {
    if (length(direction.xz) < HELION_MIN_HORIZONTAL || length(sun.xz) < HELION_MIN_HORIZONTAL) {
        return 0.5;
    }
    return dot(normalize(direction.xz), normalize(sun.xz)) * 0.5 + 0.5;
}

float helionBand(float height, float center, float width) {
    float offset = (height - center) / width;
    return exp(-offset * offset);
}

vec3 helionTwilightGlow(vec3 direction, vec3 sun, float illuminance) {
    float width = sun.y < HELION_TWILIGHT_CENTER ? HELION_TWILIGHT_WIDTH_BELOW : HELION_TWILIGHT_WIDTH_ABOVE;
    float strength = helionBand(sun.y, HELION_TWILIGHT_CENTER, width);
    if (strength < HELION_MIN_TWILIGHT) {
        return vec3(0.0);
    }
    float height = max(direction.y, 0.0);
    float facing = helionFacing(direction, sun);
    float towardSun = pow(facing, HELION_GLOW_FOCUS);
    float awayFromSun = pow(1.0 - facing, HELION_BELT_FOCUS);
    float sunAbove = smoothstep(HELION_TWILIGHT_CENTER, HELION_GOLDEN_HOUR_HEIGHT, sun.y);
    vec3 horizonColor = mix(HELION_GLOW_EMBER, HELION_GLOW_GOLD, max(smoothstep(0.0, HELION_GLOW_GOLD_HEIGHT, height), sunAbove));
    vec3 glow = horizonColor * (towardSun * exp(-height / HELION_GLOW_HEIGHT));
    float afterglow = smoothstep(HELION_ROSE_SUN_HIGH, HELION_ROSE_SUN_LOW, sun.y);
    glow += HELION_GLOW_ROSE * (HELION_ROSE_STRENGTH * afterglow * towardSun * helionBand(height, HELION_ROSE_HEIGHT, HELION_ROSE_WIDTH));
    glow += HELION_BELT_COLOR * (HELION_BELT_STRENGTH * awayFromSun * helionBand(height, HELION_BELT_HEIGHT, HELION_BELT_WIDTH));
    return glow * (strength * illuminance * HELION_TWILIGHT_GLOW);
}

#endif
