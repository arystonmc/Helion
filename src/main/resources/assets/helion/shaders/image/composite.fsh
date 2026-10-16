#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_image.glsl>
#ifdef HELION_COLOR_GRADING
#include <helion:helion_color_grading.glsl>
#endif

uniform sampler2D SceneColorSampler;
uniform sampler2D BloomSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 scene = texelFetch(SceneColorSampler, ivec2(gl_FragCoord.xy), 0);
    vec3 base = helionExpandScene(helionSrgbToLinear(scene.rgb)) * ExposureScale;
    vec3 lit = base + texture(BloomSampler, texCoord).rgb * BloomStrength * ExposureScale;
    vec3 display = helionDisplay(base, lit);
    #ifdef HELION_COLOR_GRADING
    display = helionGrade(display);
    #endif
    fragColor = vec4(helionDither(display, gl_FragCoord.xy), scene.a);
}
