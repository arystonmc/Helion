#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_geometry.glsl>
#include <helion:helion_lighting.glsl>

uniform sampler2D GeometryLightSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_NO_LIGHT = 0.0;
const float HELION_MIN_REFERENCE_LIGHT = 1.0;
const float HELION_SKY_ADAPTATION = 8.0;

vec3 helionAdjustLight(vec3 light) {
    light = mix(light, light * HELION_BOSS_DARKENING_TINT, BossOverlayDarkening);
    return helionSrgbToLinear(max(light - vec3(DarknessScale), 0.0));
}

void main() {
    vec4 levels = texelFetch(GeometryLightSampler, ivec2(gl_FragCoord.xy), 0);
    if (levels.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        fragColor = vec4(HELION_NO_LIGHT);
        return;
    }
    vec3 ambient = max(AmbientColor, NightVisionColor * NightVisionFactor);
    vec3 sky = helionInterpolatedSkyTerm(levels.g);
    vec3 block = helionInterpolatedBlockTerm(levels.r);
    vec3 light = helionAdjustLight(ambient + sky * SkyLightIntensity + block * BlockLightIntensity);
    float reference = max(helionMaxComponent(helionAdjustLight(ambient + sky)) * HELION_SKY_ADAPTATION, HELION_MIN_REFERENCE_LIGHT);
    fragColor = vec4(light, reference);
}
