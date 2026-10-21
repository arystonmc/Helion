#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_cloud_noise.glsl>
#include <helion:helion_clouds.glsl>

uniform sampler2D CloudNoiseSampler;
uniform sampler2D SkyLightSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 cloudColor;
layout(location = 1) out vec4 cloudDistance;

const float HELION_PI = 3.14159265358979;
const float HELION_PLANET_RADIUS = 6360000.0;
const float HELION_SHAPE_PERIOD = 3072.0;
const float HELION_DETAIL_PERIOD = 384.0;
const float HELION_WEATHER_PERIOD = 12288.0;
const float HELION_WEATHER_SLICE = 0.37;
const float HELION_DETAIL_EROSION = 0.3;
const float HELION_MIN_TRANSMITTANCE = 0.01;
const float HELION_LIGHT_STEP = 36.0;
const float HELION_LIGHT_STEP_GROWTH = 1.8;
const float HELION_FORWARD_ASYMMETRY = 0.75;
const float HELION_BACK_ASYMMETRY = -0.25;
const float HELION_BACK_SHARE = 0.25;
const float HELION_SILVER_LINING = 0.6;
const float HELION_AMBIENT_STRENGTH = 0.9;
const float HELION_CIRRUS_HEIGHT = 7000.0;
const float HELION_CIRRUS_PERIOD = 24576.0;
const float HELION_CIRRUS_OPACITY = 0.45;
const int HELION_SCATTERING_OCTAVES = 3;
const float HELION_RAIN_COVERAGE = 0.85;
const float HELION_RAIN_DARKENING = 0.45;
const ivec2 HELION_SUN_TEXEL = ivec2(0, 0);
const ivec2 HELION_MOON_TEXEL = ivec2(1, 0);
const ivec2 HELION_HEMISPHERE_TEXEL = ivec2(2, 0);
const float HELION_NO_CLOUD_DISTANCE = 1.0e5;

float helionHenyeyGreenstein(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * HELION_PI * pow(max(1.0 + g2 - 2.0 * g * cosTheta, 1.0e-4), 1.5));
}

float helionCloudPhase(float cosTheta, float scale) {
    float forward = helionHenyeyGreenstein(cosTheta, HELION_FORWARD_ASYMMETRY * scale);
    float silver = helionHenyeyGreenstein(cosTheta, 0.99 * scale);
    float back = helionHenyeyGreenstein(cosTheta, HELION_BACK_ASYMMETRY * scale);
    return mix(mix(forward, silver, HELION_SILVER_LINING * 0.15), back, HELION_BACK_SHARE);
}

vec2 helionShellHits(float height, float upward, float radius) {
    float b = (HELION_PLANET_RADIUS + height) * upward;
    float c = (height - radius) * (2.0 * HELION_PLANET_RADIUS + height + radius);
    float discriminant = b * b - c;
    if (discriminant < 0.0) {
        return vec2(-1.0);
    }
    float root = sqrt(discriminant);
    float far = b < 0.0 ? -b + root : -c / (b + root);
    float near = b > 0.0 ? -b - root : c / (-b + root);
    return vec2(near, far);
}

vec2 helionLayerSegment(float height, float upward, float bottom, float top) {
    vec2 bottomHits = helionShellHits(height, upward, bottom);
    vec2 topHits = helionShellHits(height, upward, top);
    if (height < bottom) {
        return vec2(bottomHits.y, topHits.y);
    }
    if (height < top) {
        float exit = topHits.y;
        if (bottomHits.x > 0.0) {
            exit = bottomHits.x;
        }
        return vec2(0.0, exit);
    }
    if (topHits.x < 0.0) {
        return vec2(-1.0);
    }
    return vec2(topHits.x, bottomHits.x > 0.0 ? bottomHits.x : topHits.y);
}

float helionHeightAbove(vec3 relative) {
    vec3 center = vec3(0.0, -(HELION_PLANET_RADIUS + CloudOrigin.y), 0.0);
    return length(relative - center) - HELION_PLANET_RADIUS;
}

float helionCoverage() {
    return mix(CloudLayer.w, HELION_RAIN_COVERAGE, CloudWeather.x);
}

float helionCloudDensity(vec3 relative, float height, bool detail) {
    float fraction = (height - CloudLayer.x) / (CloudLayer.y - CloudLayer.x);
    if (fraction <= 0.0 || fraction >= 1.0) {
        return 0.0;
    }
    vec3 position = CloudOrigin.xyz + relative;
    position.y = height;
    float wind = CloudWeather.y;
    vec2 weatherUv = (position.xz + vec2(wind * 2.0, 0.0)) / HELION_WEATHER_PERIOD;
    float weather = helionCloudNoiseSlice(CloudNoiseSampler, weatherUv, floor(HELION_WEATHER_SLICE * HELION_CLOUD_NOISE_SIZE)).r;
    float coverage = clamp(helionCloudRemap(weather, 1.0 - helionCoverage(), 1.0, 0.0, 1.0), 0.0, 1.0);
    if (coverage <= 0.0) {
        return 0.0;
    }
    float rounding = clamp(helionCloudRemap(fraction, 0.0, 0.08, 0.0, 1.0), 0.0, 1.0)
        * clamp(helionCloudRemap(fraction, 0.35 + 0.5 * coverage, 1.0, 1.0, 0.0), 0.0, 1.0);
    vec4 shape = helionCloudNoise(CloudNoiseSampler, (position + vec3(wind * 6.0, -wind * 0.5, 0.0)) / HELION_SHAPE_PERIOD);
    float erosion = shape.g * 0.625 + shape.b * 0.25 + shape.a * 0.125;
    float base = clamp(helionCloudRemap(shape.r, erosion - 1.0, 1.0, 0.0, 1.0), 0.0, 1.0) * rounding;
    float density = clamp(helionCloudRemap(base, 1.0 - coverage, 1.0, 0.0, 1.0), 0.0, 1.0) * coverage;
    if (detail && density > 0.0) {
        vec4 small = helionCloudNoise(CloudNoiseSampler, (position + vec3(wind * 9.0, 0.0, wind * 2.0)) / HELION_DETAIL_PERIOD);
        float wisps = small.g * 0.625 + small.b * 0.25 + small.a * 0.125;
        float erode = mix(wisps, 1.0 - wisps, clamp(fraction * 4.0, 0.0, 1.0)) * HELION_DETAIL_EROSION;
        density = clamp(helionCloudRemap(density, erode, 1.0, 0.0, 1.0), 0.0, 1.0);
    }
    return density;
}

float helionLightOpticalDepth(vec3 relative, vec3 light, float noise) {
    float depth = 0.0;
    float stepSize = HELION_LIGHT_STEP;
    float travelled = 0.0;
    int steps = int(CloudQuality.y);
    for (int index = 0; index < steps; index++) {
        float rayDistance = travelled + stepSize * (index == 0 ? noise : 0.5);
        vec3 point = relative + light * rayDistance;
        depth += helionCloudDensity(point, helionHeightAbove(point), index < 2) * stepSize;
        travelled += stepSize;
        stepSize *= HELION_LIGHT_STEP_GROWTH;
    }
    return depth * CloudLayer.z;
}

float helionScattering(float opticalDepth, float cosTheta, float height) {
    float sum = 0.0;
    float strength = 1.0;
    float attenuation = 1.0;
    float eccentricity = 1.0;
    for (int octave = 0; octave < HELION_SCATTERING_OCTAVES; octave++) {
        sum += strength * exp(-opticalDepth * attenuation) * helionCloudPhase(cosTheta, eccentricity);
        strength *= 0.5;
        attenuation *= 0.4;
        eccentricity *= 0.5;
    }
    float powder = 1.0 - exp(-opticalDepth * 2.0 - 0.15);
    return sum * mix(1.0, powder, 0.5 * (1.0 - height));
}

float helionDistanceFade(float rayDistance) {
    return 1.0 - smoothstep(CloudWeather.w, CloudWeather.z, rayDistance);
}

void helionCirrus(vec3 ray, vec3 light, vec3 lightColor, vec3 ambient, inout vec3 radiance, inout float transmittance,
    inout float weightedDistance, inout float weights) {
    vec2 hits = helionShellHits(CloudOrigin.y, ray.y, HELION_CIRRUS_HEIGHT);
    float rayDistance = CloudOrigin.y < HELION_CIRRUS_HEIGHT ? hits.y : hits.x;
    if (rayDistance <= 0.0) {
        return;
    }
    vec3 position = CloudOrigin.xyz + ray * rayDistance;
    vec2 uv = (position.xz + vec2(CloudWeather.y * 12.0, 0.0)) / HELION_CIRRUS_PERIOD;
    vec4 streaks = helionCloudNoiseSlice(CloudNoiseSampler, uv * vec2(1.0, 3.0), 11.0);
    vec4 breakup = helionCloudNoiseSlice(CloudNoiseSampler, uv * 4.0, 29.0);
    float density = clamp(helionCloudRemap(streaks.r * 0.7 + breakup.g * 0.3, 0.55 - 0.15 * helionCoverage(), 1.0, 0.0, 1.0), 0.0, 1.0);
    float opacity = density * HELION_CIRRUS_OPACITY * helionDistanceFade(rayDistance * 0.25);
    if (opacity <= 0.0) {
        return;
    }
    float phase = helionCloudPhase(dot(ray, light), 0.6);
    vec3 lit = lightColor * phase * 2.0 + ambient;
    radiance += transmittance * opacity * lit;
    weightedDistance += rayDistance * transmittance * opacity;
    weights += transmittance * opacity;
    transmittance *= 1.0 - opacity;
}

void main() {
    vec3 ray = helionCloudViewRay(texCoord);
    vec3 light = CloudLight.xyz;
    vec3 lightColor = texelFetch(SkyLightSampler, CloudLight.w > 0.5 ? HELION_SUN_TEXEL : HELION_MOON_TEXEL, 0).rgb;
    vec3 ambient = texelFetch(SkyLightSampler, HELION_HEMISPHERE_TEXEL, 0).rgb / HELION_PI * HELION_AMBIENT_STRENGTH;
    float rain = CloudWeather.x;
    lightColor *= mix(1.0, HELION_RAIN_DARKENING, rain);

    vec2 pixel = gl_FragCoord.xy;
    float frame = CloudQuality.w;
    float noise = helionInterleavedGradientNoise(pixel + frame * HELION_NOISE_SECOND_OFFSET);
    float lightNoise = helionInterleavedGradientNoise(pixel.yx + frame * 3.1);

    vec3 radiance = vec3(0.0);
    float transmittance = 1.0;
    float weightedDistance = 0.0;
    float weights = 0.0;

    vec2 segment = helionLayerSegment(CloudOrigin.y, ray.y, CloudLayer.x, CloudLayer.y);
    float start = max(segment.x, 0.0);
    float end = min(segment.y, CloudWeather.z);
    if (segment.y > 0.0 && end > start) {
        int steps = int(CloudQuality.x);
        float horizontal = 1.0 - abs(ray.y);
        steps = int(float(steps) * mix(1.0, 1.5, horizontal * horizontal));
        float stepLength = (end - start) / float(steps);
        float cosTheta = dot(ray, light);
        for (int index = 0; index < steps; index++) {
            float rayDistance = start + (float(index) + noise) * stepLength;
            vec3 relative = ray * rayDistance;
            float height = helionHeightAbove(relative);
            float density = helionCloudDensity(relative, height, true);
            if (density <= 0.0) {
                continue;
            }
            float extinction = density * CloudLayer.z * helionDistanceFade(rayDistance);
            if (extinction <= 0.0) {
                continue;
            }
            float fraction = clamp((height - CloudLayer.x) / (CloudLayer.y - CloudLayer.x), 0.0, 1.0);
            float lightDepth = helionLightOpticalDepth(relative, light, lightNoise);
            vec3 inScattered = lightColor * helionScattering(lightDepth, cosTheta, fraction)
                + ambient * mix(0.45, 1.0, fraction);
            float stepTransmittance = exp(-extinction * stepLength);
            vec3 integrated = inScattered * (1.0 - stepTransmittance);
            radiance += transmittance * integrated;
            float contribution = transmittance * (1.0 - stepTransmittance);
            weightedDistance += rayDistance * contribution;
            weights += contribution;
            transmittance *= stepTransmittance;
            if (transmittance < HELION_MIN_TRANSMITTANCE) {
                break;
            }
        }
    }
    if (CloudQuality.z > 0.5 && transmittance > HELION_MIN_TRANSMITTANCE && ray.y > -0.05) {
        helionCirrus(ray, light, lightColor, ambient, radiance, transmittance, weightedDistance, weights);
    }
    float rayDistance = weights > 0.0 ? weightedDistance / weights : HELION_NO_CLOUD_DISTANCE;
    cloudColor = vec4(radiance, transmittance);
    cloudDistance = vec4(rayDistance, 0.0, 0.0, 1.0);
}
