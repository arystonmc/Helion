#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_sky_color.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(helionDitherEncoded(helionSkyColorWithCelestials(helionViewRay(texCoord)), gl_FragCoord.xy), 1.0);
}
