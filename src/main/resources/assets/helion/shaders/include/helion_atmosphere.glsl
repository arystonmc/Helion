#ifndef HELION_ATMOSPHERE_GLSL
#define HELION_ATMOSPHERE_GLSL

const float HELION_PI = 3.14159265358979;
const float HELION_BOTTOM_RADIUS = 6360.0;
const float HELION_TOP_RADIUS = 6460.0;
const float HELION_RAYLEIGH_SCALE_HEIGHT = 8.0;
const float HELION_MIE_SCATTERING = 3.996e-3;
const float HELION_MIE_EXTINCTION = 4.40e-3;
const float HELION_MIE_SCALE_HEIGHT = 1.2;
const float HELION_MIE_ASYMMETRY = 0.8;
const float HELION_OZONE_CENTER_HEIGHT = 25.0;
const float HELION_OZONE_HALF_WIDTH = 15.0;
const float HELION_GROUND_ALBEDO = 0.3;
const float HELION_MIN_VIEW_HEIGHT = 0.001;

const vec2 HELION_TRANSMITTANCE_SIZE = vec2(256.0, 64.0);
const vec2 HELION_MULTIPLE_SCATTERING_SIZE = vec2(32.0, 32.0);
const vec2 HELION_SKY_VIEW_SIZE = vec2(192.0, 108.0);
const float HELION_NO_HIT = -1.0;

float helionRaySphere(vec3 origin, vec3 direction, float radius) {
    float b = dot(origin, direction);
    float c = dot(origin, origin) - radius * radius;
    float discriminant = b * b - c;
    if (discriminant < 0.0) {
        return HELION_NO_HIT;
    }
    float root = sqrt(discriminant);
    float near = -b - root;
    float far = -b + root;
    if (far < 0.0) {
        return HELION_NO_HIT;
    }
    return near > 0.0 ? near : far;
}

float helionRayleighPhase(float cosTheta) {
    return 3.0 / (16.0 * HELION_PI) * (1.0 + cosTheta * cosTheta);
}

float helionMiePhase(float cosTheta) {
    float g = HELION_MIE_ASYMMETRY;
    float g2 = g * g;
    float denominator = pow(max(1.0 + g2 - 2.0 * g * cosTheta, 1.0e-4), 1.5);
    return 3.0 / (8.0 * HELION_PI) * (1.0 - g2) * (1.0 + cosTheta * cosTheta) / ((2.0 + g2) * denominator);
}

float helionIsotropicPhase() {
    return 1.0 / (4.0 * HELION_PI);
}

vec2 helionFromSubUv(vec2 uv, vec2 size) {
    return (uv - 0.5 / size) / (1.0 - 1.0 / size);
}

vec2 helionToSubUv(vec2 uv, vec2 size) {
    return 0.5 / size + uv * (1.0 - 1.0 / size);
}

float helionHorizonDistance() {
    return sqrt(HELION_TOP_RADIUS * HELION_TOP_RADIUS - HELION_BOTTOM_RADIUS * HELION_BOTTOM_RADIUS);
}

void helionTransmittanceParameters(vec2 uv, out float viewHeight, out float viewZenithCos) {
    float horizon = helionHorizonDistance();
    float rho = horizon * uv.y;
    viewHeight = sqrt(rho * rho + HELION_BOTTOM_RADIUS * HELION_BOTTOM_RADIUS);
    float minDistance = HELION_TOP_RADIUS - viewHeight;
    float maxDistance = rho + horizon;
    float distance = minDistance + uv.x * (maxDistance - minDistance);
    viewZenithCos = distance == 0.0 ? 1.0 : (horizon * horizon - rho * rho - distance * distance) / (2.0 * viewHeight * distance);
    viewZenithCos = clamp(viewZenithCos, -1.0, 1.0);
}

vec2 helionTransmittanceUv(float viewHeight, float viewZenithCos) {
    float horizon = helionHorizonDistance();
    float rho = sqrt(max(0.0, viewHeight * viewHeight - HELION_BOTTOM_RADIUS * HELION_BOTTOM_RADIUS));
    float discriminant = viewHeight * viewHeight * (viewZenithCos * viewZenithCos - 1.0) + HELION_TOP_RADIUS * HELION_TOP_RADIUS;
    float distance = max(0.0, -viewHeight * viewZenithCos + sqrt(max(discriminant, 0.0)));
    float minDistance = HELION_TOP_RADIUS - viewHeight;
    float maxDistance = rho + horizon;
    return vec2((distance - minDistance) / (maxDistance - minDistance), rho / horizon);
}

vec2 helionTransmittanceLookupUv(vec3 position, vec3 direction) {
    float height = length(position);
    return helionToSubUv(helionTransmittanceUv(height, dot(position / height, direction)), HELION_TRANSMITTANCE_SIZE);
}

vec2 helionMultipleScatteringLookupUv(vec3 position, vec3 lightDirection) {
    float height = length(position);
    vec2 uv = vec2(
        dot(position / height, lightDirection) * 0.5 + 0.5,
        clamp((height - HELION_BOTTOM_RADIUS) / (HELION_TOP_RADIUS - HELION_BOTTOM_RADIUS), 0.0, 1.0)
    );
    return helionToSubUv(uv, HELION_MULTIPLE_SCATTERING_SIZE);
}

float helionGroundShadow(vec3 position, vec3 direction) {
    return helionRaySphere(position, direction, HELION_BOTTOM_RADIUS) > 0.0 ? 0.0 : 1.0;
}

float helionHorizonZenithAngle(float viewHeight) {
    float ratio = HELION_BOTTOM_RADIUS / viewHeight;
    return HELION_PI - asin(clamp(ratio, 0.0, 1.0));
}

void helionSkyViewParameters(vec2 uv, float viewHeight, out float viewZenithAngle, out float azimuth) {
    float horizonAngle = helionHorizonZenithAngle(viewHeight);
    if (uv.y < 0.5) {
        float fromHorizon = 1.0 - 2.0 * uv.y;
        viewZenithAngle = horizonAngle * (1.0 - fromHorizon * fromHorizon);
    } else {
        float belowHorizon = 2.0 * uv.y - 1.0;
        viewZenithAngle = horizonAngle + (HELION_PI - horizonAngle) * belowHorizon * belowHorizon;
    }
    azimuth = HELION_PI * uv.x * uv.x;
}

vec2 helionSkyViewUv(float viewHeight, float viewZenithAngle, float azimuth) {
    float horizonAngle = helionHorizonZenithAngle(viewHeight);
    float v;
    if (viewZenithAngle < horizonAngle) {
        float above = clamp(viewZenithAngle / horizonAngle, 0.0, 1.0);
        v = 0.5 * (1.0 - sqrt(1.0 - above));
    } else {
        float below = clamp((viewZenithAngle - horizonAngle) / (HELION_PI - horizonAngle), 0.0, 1.0);
        v = 0.5 + 0.5 * sqrt(below);
    }
    return vec2(sqrt(clamp(azimuth / HELION_PI, 0.0, 1.0)), v);
}

#endif
