#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <helion:helion_color.glsl>
#include <helion:helion_caustics.glsl>

uniform sampler2D SceneDepthSampler;

layout(std140) uniform HelionUnderwater {
    mat4 UnderwaterInverseViewProjection;
    vec4 UnderwaterCamera;
    vec4 UnderwaterTime;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_SKY_DEPTH = 0.0;
const float HELION_OPEN_DISTANCE = 64.0;
const vec3 HELION_UNDERWATER_EXTINCTION = vec3(0.085, 0.032, 0.026);
const vec3 HELION_UNDERWATER_TEAL = vec3(0.05, 0.36, 0.42);
const float HELION_TEAL_SHARE = 0.65;
const float HELION_SCATTER_STRENGTH = 0.85;
const float HELION_NIGHT_EXTINCTION = 0.5;
const float HELION_CAUSTIC_STRENGTH = 0.22;
const float HELION_CAUSTIC_REACH = 0.08;
const float HELION_CAUSTIC_HEIGHT_RANGE = 0.5;
const float HELION_CAUSTIC_DEPTH = 2.5;
const vec3 HELION_SUN_UNDERWATER = vec3(0.75, 0.95, 0.9);
const float HELION_SHAFT_STRENGTH = 0.32;
const float HELION_SHAFT_SCALE = 0.35;
const float HELION_SHAFT_SPEED = 0.25;
const float HELION_SHAFT_SHARPNESS = 6.0;
const float HELION_SHAFT_BEND = 0.3;
const float HELION_SHAFT_LINE_SHARPNESS = 8.0;
const vec2 HELION_SHAFT_WARP_FREQUENCY = vec2(1.7, 1.3);
const vec2 HELION_SHAFT_WARP_SPEED = vec2(0.9, -0.7);
const float HELION_SHAFT_WARP = 0.55;
const vec2 HELION_SHAFT_LINES = vec2(2.2, 2.0);
const vec2 HELION_SHAFT_LINE_BEND = vec2(1.4, 1.1);
const float HELION_MIN_DEPTH = 1.0e-6;
const vec3 HELION_AVERAGE = vec3(1.0 / 3.0);

float helionShaftLayer(vec2 position, float seconds) {
    vec2 warped = position + sin(position.yx * HELION_SHAFT_WARP_FREQUENCY + seconds * HELION_SHAFT_WARP_SPEED) * HELION_SHAFT_WARP;
    vec2 waves = sin(warped * HELION_SHAFT_LINES + sin(warped.yx * HELION_SHAFT_LINE_BEND));
    return pow(1.0 - abs(waves.x * waves.y), HELION_SHAFT_LINE_SHARPNESS);
}

float helionShafts(vec3 direction, float seconds) {
    vec2 across = direction.xz / (abs(direction.y) + HELION_SHAFT_BEND);
    float layer = helionShaftLayer(across * HELION_SHAFT_SCALE + seconds * HELION_SHAFT_SPEED, seconds * HELION_SHAFT_SPEED);
    return pow(layer, HELION_SHAFT_SHARPNESS / HELION_SHAFT_LINE_SHARPNESS) * max(direction.y, 0.0);
}

void main() {
    float depth = texelFetch(SceneDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    vec4 world = UnderwaterInverseViewProjection * vec4(texCoord * 2.0 - 1.0, max(depth, HELION_SKY_DEPTH + HELION_MIN_DEPTH), 1.0);
    vec3 position = world.xyz / world.w;
    bool sky = depth <= HELION_SKY_DEPTH;
    float travelled = sky ? HELION_OPEN_DISTANCE : min(length(position), HELION_OPEN_DISTANCE);
    vec3 direction = normalize(position);
    float seconds = UnderwaterTime.x;
    float sunlit = UnderwaterCamera.w * UnderwaterTime.y * UnderwaterTime.y;
    vec3 scatter = mix(helionSrgbToLinear(FogColor.rgb), HELION_UNDERWATER_TEAL, HELION_TEAL_SHARE) * HELION_SCATTER_STRENGTH * sunlit;
    vec3 transmitted = exp(-travelled * (HELION_UNDERWATER_EXTINCTION + HELION_NIGHT_EXTINCTION * (1.0 - sunlit)));
    float kept = dot(transmitted, HELION_AVERAGE);
    vec3 point = position + UnderwaterCamera.xyz;
    float below = 1.0 - smoothstep(-HELION_CAUSTIC_HEIGHT_RANGE, HELION_CAUSTIC_HEIGHT_RANGE, position.y);
    float causticShare = sky ? 0.0 : exp(-travelled * HELION_CAUSTIC_REACH) * below * HELION_CAUSTIC_STRENGTH;
    float caustics = causticShare > 0.0 ? max(helionCausticLight(point.xz, HELION_CAUSTIC_DEPTH, seconds) - 1.0, 0.0) * causticShare : 0.0;
    vec3 light = scatter * (1.0 - transmitted)
        + HELION_SUN_UNDERWATER * (caustics * kept + helionShafts(direction, seconds) * HELION_SHAFT_STRENGTH) * sunlit;
    fragColor = vec4(helionLinearToSrgb(light), 1.0 - kept);
}
