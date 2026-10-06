#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_sky_color.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(helionSkyColor(helionViewRay(texCoord)), 1.0);
}
