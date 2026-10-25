#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_caustics.glsl>
#include <helion:helion_water_optics.glsl>

uniform sampler2D SceneDepthSampler;
uniform sampler2D SceneColorSampler;

layout(std140) uniform HelionUnderwater {
    mat4 UnderwaterInverseViewProjection;
    vec4 UnderwaterCamera;
    vec4 UnderwaterTime;
    vec4 UnderwaterLight;
    vec4 UnderwaterTint;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_UNDER_PI = 3.14159265;
const float HELION_SKY_DEPTH = 0.0;
const float HELION_MIN_DEPTH = 1.0e-6;
const float HELION_OPEN_DISTANCE = 96.0;
const float HELION_MARCH_DISTANCE = 32.0;
const int HELION_MARCH_STEPS = 16;
const float HELION_WATER_IOR = 1.33;
const vec3 HELION_UP = vec3(0.0, 1.0, 0.0);
const float HELION_MIN_UPWARD = 0.05;
const float HELION_LIGHT_LEVELS = 15.0;
const vec3 HELION_SUN_COLOR = vec3(1.0, 0.97, 0.9);
const vec3 HELION_MOON_COLOR = vec3(0.025, 0.032, 0.05);
const vec3 HELION_AMBIENT_COLOR = vec3(0.42, 0.58, 0.72);
const float HELION_MOON_AMBIENT = 0.04;
const float HELION_AMBIENT_SLANT = 1.25;
const float HELION_SCATTER_GAIN = 7.0;
const float HELION_SHAFT_GAIN = 6.0;
const float HELION_SHAFT_FLOOR = 0.06;
const float HELION_SHAFT_MEAN = 0.2;
const float HELION_SHAFT_LENGTH = 8.0;
const float HELION_SHAFT_REACH = 10.0;
const float HELION_SHAFT_ALONG_FROM = 0.75;
const float HELION_SHAFT_ALONG_TO = 0.97;
const float HELION_PHASE_G = 0.65;
const float HELION_ISOTROPIC = 1.0 / (4.0 * HELION_UNDER_PI);
const float HELION_SHAFT_SCALE = 0.25;
const float HELION_SHAFT_SPREAD = 0.06;
const float HELION_SHAFT_RIDGE = 3.0;
const float HELION_SHAFT_SHARPNESS = 6.0;
const vec2 HELION_SHAFT_DRIFT = vec2(0.11, 0.07);
const float HELION_CAUSTIC_STRENGTH = 0.9;
const float HELION_CAUSTIC_REACH = 0.04;
const float HELION_HUE_FLOOR = 1.0e-3;
const vec3 HELION_LUMA = vec3(0.2126, 0.7152, 0.0722);
const float HELION_UNDER_NOISE_SCALE = 52.9829189;
const vec2 HELION_UNDER_NOISE_PIXEL = vec2(0.06711056, 0.00583715);
const float HELION_UNDER_NOISE_SPEED = 7.0;

float helionPhase(float cosine) {
    float g2 = HELION_PHASE_G * HELION_PHASE_G;
    return (1.0 - g2) / (4.0 * HELION_UNDER_PI * pow(1.0 + g2 - 2.0 * HELION_PHASE_G * cosine, 1.5));
}

float helionShaftPattern(vec2 position, float depth, float seconds) {
    float spread = 1.0 / (1.0 + depth * HELION_SHAFT_SPREAD);
    vec2 coarse = position * HELION_SHAFT_SCALE * spread;
    float noise = helionNoiseValue(coarse + HELION_SHAFT_DRIFT * seconds);
    float ridge = clamp(1.0 - abs(noise) * HELION_SHAFT_RIDGE * spread, 0.0, 1.0);
    return HELION_SHAFT_FLOOR + pow(ridge, HELION_SHAFT_SHARPNESS);
}

vec3 helionHueShift(vec3 absorption, float depth) {
    vec3 filtered = exp(-absorption * depth);
    return filtered / max(dot(filtered, HELION_LUMA), HELION_HUE_FLOOR);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(SceneDepthSampler, pixel, 0).r;
    vec4 world = UnderwaterInverseViewProjection * vec4(texCoord * 2.0 - 1.0, max(depth, HELION_SKY_DEPTH + HELION_MIN_DEPTH), 1.0);
    vec3 position = world.xyz / world.w;
    vec3 tangentX = dFdx(position);
    vec3 tangentY = dFdy(position);
    vec3 scene = helionSrgbToLinear(texelFetch(SceneColorSampler, pixel, 0).rgb);
    bool sky = depth <= HELION_SKY_DEPTH;
    vec3 direction = normalize(position);
    float surface = max(UnderwaterTime.z, 0.0);
    float column = surface + UnderwaterTint.w;
    HelionWaterOptics optics = helionWaterOptics(UnderwaterTint.rgb, column, UnderwaterTime.w);
    float seconds = UnderwaterTime.x;
    float open = clamp(UnderwaterTime.y + surface / HELION_LIGHT_LEVELS, 0.0, 1.0);
    open *= open;
    bool moonLit = UnderwaterLight.w > 0.5;
    vec3 lightColor = (moonLit ? HELION_MOON_COLOR : HELION_SUN_COLOR * UnderwaterCamera.w) * open;
    vec3 ambient = HELION_AMBIENT_COLOR * (UnderwaterCamera.w + (moonLit ? HELION_MOON_AMBIENT : 0.0)) * open;
    vec3 toLight = -refract(-normalize(UnderwaterLight.xyz), HELION_UP, 1.0 / HELION_WATER_IOR);
    toLight.y = max(toLight.y, HELION_MIN_UPWARD);
    toLight = normalize(toLight);

    float travelled = sky ? HELION_OPEN_DISTANCE : min(length(position), HELION_OPEN_DISTANCE);
    if (direction.y > 0.0) {
        travelled = min(travelled, surface / direction.y);
    }
    float marched = min(travelled, HELION_MARCH_DISTANCE);
    float stepLength = marched / float(HELION_MARCH_STEPS);
    float jitter = fract(HELION_UNDER_NOISE_SCALE * fract(dot(gl_FragCoord.xy + seconds * HELION_UNDER_NOISE_SPEED, HELION_UNDER_NOISE_PIXEL)));
    float phase = helionPhase(dot(direction, toLight));
    float across = 1.0 - smoothstep(HELION_SHAFT_ALONG_FROM, HELION_SHAFT_ALONG_TO, abs(dot(direction, toLight)));
    vec3 inscattered = vec3(0.0);
    for (int index = 0; index < HELION_MARCH_STEPS; index++) {
        float along = (float(index) + jitter) * stepLength;
        vec3 point = direction * along;
        float below = max(surface - point.y, 0.0);
        vec3 viewTransmittance = exp(-optics.extinction * along);
        vec3 sunAtPoint = lightColor * exp(-optics.extinction * below / toLight.y);
        vec3 ambientAtPoint = ambient * exp(-optics.extinction * below * HELION_AMBIENT_SLANT);
        vec2 entry = point.xz + UnderwaterCamera.xz + toLight.xz / toLight.y * below;
        float shaft = mix(HELION_SHAFT_MEAN, helionShaftPattern(entry, below, seconds), exp(-below / HELION_SHAFT_LENGTH - along / HELION_SHAFT_REACH) * across);
        inscattered += viewTransmittance * optics.scattering * (sunAtPoint * phase * shaft * HELION_SHAFT_GAIN + ambientAtPoint * HELION_ISOTROPIC) * stepLength;
    }
    if (travelled > marched) {
        float rest = travelled - marched;
        vec3 restTransmittance = exp(-optics.extinction * marched);
        vec3 restLight = ambient * exp(-optics.extinction * surface) * HELION_ISOTROPIC + lightColor * exp(-optics.extinction * surface / toLight.y) * phase * HELION_SHAFT_MEAN;
        inscattered += restTransmittance * optics.albedo * restLight * (1.0 - exp(-optics.extinction * rest));
    }
    inscattered *= HELION_SCATTER_GAIN;

    vec3 surfaceNormal = normalize(cross(tangentX, tangentY));
    if (dot(surfaceNormal, direction) > 0.0) {
        surfaceNormal = -surfaceNormal;
    }
    if (!sky) {
        float pointBelow = max(surface - position.y, 0.0);
        scene *= helionHueShift(optics.absorption, pointBelow);
        float facing = max(dot(surfaceNormal, toLight), 0.0);
        float causticShare = facing * open * UnderwaterCamera.w * HELION_CAUSTIC_STRENGTH * exp(-travelled * HELION_CAUSTIC_REACH) * (moonLit ? 0.0 : 1.0);
        if (causticShare > 0.0) {
            vec2 entry = position.xz + UnderwaterCamera.xz + toLight.xz / toLight.y * pointBelow;
            float caustic = helionCausticLight(entry, pointBelow, seconds);
            scene *= helionCausticGain(caustic, causticShare);
        }
    }
    vec3 color = scene * exp(-optics.extinction * travelled) + inscattered;
    fragColor = vec4(helionLinearToSrgb(color), 1.0);
}
