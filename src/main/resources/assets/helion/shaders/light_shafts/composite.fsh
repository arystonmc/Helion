#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_light_shafts.glsl>

uniform sampler2D ShaftSampler;
uniform sampler2D SkyLightSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const ivec2 HELION_SUN_TEXEL = ivec2(0, 0);
const ivec2 HELION_MOON_TEXEL = ivec2(1, 0);
const float HELION_MIN_SHAFT = 1.0e-4;
const float HELION_DITHER_STEP = 1.0 / 255.0;

void main() {
    float shaft = texture(ShaftSampler, texCoord).r * ShaftLight.w * ShaftScreen.z;
    vec3 light = texelFetch(SkyLightSampler, ShaftScreen.w > 0.5 ? HELION_SUN_TEXEL : HELION_MOON_TEXEL, 0).rgb;
    vec3 radiance = light * shaft;
    if (helionMaxComponent(radiance) <= HELION_MIN_SHAFT) {
        discard;
    }
    vec3 encoded = helionLinearToSrgb(helionNeutral(radiance)) + helionTriangularNoise(gl_FragCoord.xy) * HELION_DITHER_STEP;
    fragColor = vec4(clamp(encoded, 0.0, 1.0), 1.0);
}
