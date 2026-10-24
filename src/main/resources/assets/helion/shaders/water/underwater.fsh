#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <helion:helion_color.glsl>

uniform sampler2D SceneDepthSampler;

layout(std140) uniform HelionUnderwater {
    mat4 UnderwaterInverseViewProjection;
    vec4 UnderwaterCamera;
    vec4 UnderwaterTime;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_SKY_DEPTH = 0.0;
const float HELION_OPEN_DISTANCE = 48.0;
const vec3 HELION_UNDERWATER_EXTINCTION = vec3(0.16, 0.055, 0.045);
const vec3 HELION_UNDERWATER_TEAL = vec3(0.06, 0.32, 0.34);
const float HELION_TEAL_SHARE = 0.6;
const float HELION_NIGHT_SCATTER = 0.15;
const float HELION_CAUSTIC_STRENGTH = 0.9;
const float HELION_CAUSTIC_REACH = 0.12;
const float HELION_CAUSTIC_HEIGHT = 0.5;
const float HELION_CAUSTIC_SCALE = 1.1;
const float HELION_CAUSTIC_SHARPNESS = 8.0;
const vec2 HELION_CAUSTIC_WARP_FREQUENCY = vec2(1.7, 1.3);
const vec2 HELION_CAUSTIC_WARP_SPEED = vec2(0.9, -0.7);
const float HELION_CAUSTIC_WARP = 0.55;
const vec2 HELION_CAUSTIC_LINES = vec2(2.2, 2.0);
const vec2 HELION_CAUSTIC_BEND = vec2(1.4, 1.1);
const float HELION_CAUSTIC_SECOND_SCALE = 1.7;
const vec2 HELION_CAUSTIC_SECOND_OFFSET = vec2(3.1, 1.7);
const float HELION_CAUSTIC_SECOND_SPEED = 1.3;
const float HELION_SHAFT_STRENGTH = 0.18;
const float HELION_SHAFT_SCALE = 0.35;
const float HELION_SHAFT_SPEED = 0.25;
const float HELION_SHAFT_SHARPNESS = 6.0;
const float HELION_SHAFT_BEND = 0.3;
const float HELION_MIN_DEPTH = 1.0e-6;
const vec3 HELION_AVERAGE = vec3(1.0 / 3.0);

float helionCausticLayer(vec2 position, float seconds) {
    vec2 warped = position + sin(position.yx * HELION_CAUSTIC_WARP_FREQUENCY + seconds * HELION_CAUSTIC_WARP_SPEED) * HELION_CAUSTIC_WARP;
    vec2 waves = sin(warped * HELION_CAUSTIC_LINES + sin(warped.yx * HELION_CAUSTIC_BEND));
    return pow(1.0 - abs(waves.x * waves.y), HELION_CAUSTIC_SHARPNESS);
}

float helionCaustics(vec2 position, float seconds) {
    vec2 scaled = position * HELION_CAUSTIC_SCALE;
    float second = helionCausticLayer(scaled * HELION_CAUSTIC_SECOND_SCALE + HELION_CAUSTIC_SECOND_OFFSET, seconds * HELION_CAUSTIC_SECOND_SPEED);
    return 0.5 * (helionCausticLayer(scaled, seconds) + second);
}

float helionShafts(vec3 direction, float seconds) {
    vec2 across = direction.xz / (abs(direction.y) + HELION_SHAFT_BEND);
    float layer = helionCausticLayer(across * HELION_SHAFT_SCALE + seconds * HELION_SHAFT_SPEED, seconds * HELION_SHAFT_SPEED);
    return pow(layer, HELION_SHAFT_SHARPNESS / HELION_CAUSTIC_SHARPNESS) * max(direction.y, 0.0);
}

void main() {
    float depth = texelFetch(SceneDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    vec4 world = UnderwaterInverseViewProjection * vec4(texCoord * 2.0 - 1.0, max(depth, HELION_SKY_DEPTH + HELION_MIN_DEPTH), 1.0);
    vec3 position = world.xyz / world.w;
    float distanceToSurface = depth <= HELION_SKY_DEPTH ? HELION_OPEN_DISTANCE : min(length(position), HELION_OPEN_DISTANCE);
    vec3 direction = normalize(position);
    float daylight = UnderwaterCamera.w;
    float seconds = UnderwaterTime.x;
    vec3 scatter = mix(helionSrgbToLinear(FogColor.rgb), HELION_UNDERWATER_TEAL, HELION_TEAL_SHARE) * mix(HELION_NIGHT_SCATTER, 1.0, daylight);
    vec3 transmitted = exp(-distanceToSurface * HELION_UNDERWATER_EXTINCTION);
    float kept = dot(transmitted, HELION_AVERAGE);
    vec3 point = position + UnderwaterCamera.xyz;
    float below = 1.0 - smoothstep(-HELION_CAUSTIC_HEIGHT, HELION_CAUSTIC_HEIGHT, position.y);
    float caustics = depth <= HELION_SKY_DEPTH ? 0.0 : helionCaustics(point.xz, seconds) * exp(-distanceToSurface * HELION_CAUSTIC_REACH) * below;
    vec3 light = scatter * (1.0 - transmitted)
        + scatter * (caustics * HELION_CAUSTIC_STRENGTH + helionShafts(direction, seconds) * HELION_SHAFT_STRENGTH) * daylight;
    fragColor = vec4(helionLinearToSrgb(light), 1.0 - kept);
}
