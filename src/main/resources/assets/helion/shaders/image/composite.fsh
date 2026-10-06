#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_image.glsl>

uniform sampler2D SceneColorSampler;
uniform sampler2D BloomSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 scene = texelFetch(SceneColorSampler, ivec2(gl_FragCoord.xy), 0);
    vec3 bloom = texture(BloomSampler, texCoord).rgb * BloomStrength;
    vec3 color = (helionExpandScene(helionSrgbToLinear(scene.rgb)) + bloom) * ExposureScale;
    vec3 encoded = helionLinearToSrgb(helionToneMap(color));
    fragColor = vec4(helionDither(encoded, gl_FragCoord.xy), scene.a);
}
