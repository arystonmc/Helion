#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_clouds.glsl>

uniform sampler2D CloudColorSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_SKY_DEPTH = 0.0;
const float HELION_MIN_COVER = 1.0e-3;
const float HELION_CLOUD_SATURATION = 1.35;
const float HELION_RAINBOW_RADIUS = 0.7243;
const float HELION_RAINBOW_WIDTH = 0.045;
const float HELION_RAINBOW_CLEARING = 0.7;
const float HELION_SUN_LIT = 0.5;
const float HELION_RAINBOW_EDGE = 0.6;

float helionRainbowWindow(vec3 direction) {
    if (CloudLight.w < HELION_SUN_LIT || CloudOrigin.w <= 0.0) {
        return 0.0;
    }
    float angle = acos(clamp(dot(direction, -CloudLight.xyz), -1.0, 1.0));
    float band = abs(angle - HELION_RAINBOW_RADIUS) / HELION_RAINBOW_WIDTH;
    return (1.0 - smoothstep(HELION_RAINBOW_EDGE, 1.0, band)) * CloudOrigin.w * HELION_RAINBOW_CLEARING * (1.0 - CloudWeather.x);
}

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    if (depth > HELION_SKY_DEPTH && CloudOrigin.y < CloudLayer.x) {
        discard;
    }
    vec4 clouds = texture(CloudColorSampler, texCoord);
    float cover = 1.0 - clamp(clouds.a, 0.0, 1.0);
    if (cover <= HELION_MIN_COVER) {
        discard;
    }
    cover *= 1.0 - helionRainbowWindow(helionCloudViewRay(texCoord));
    vec3 radiance = clouds.rgb / max(1.0 - clamp(clouds.a, 0.0, 1.0), HELION_MIN_COVER);
    radiance = max(mix(vec3(helionLuminance(radiance)), radiance, HELION_CLOUD_SATURATION), 0.0);
    fragColor = vec4(helionDitherEncoded(helionLinearToSrgb(helionNeutral(radiance)), gl_FragCoord.xy), cover);
}
