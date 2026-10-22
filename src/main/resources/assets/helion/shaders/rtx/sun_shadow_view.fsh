#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D ShadowMaskSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_RT_SET = 0.5;
const vec3 HELION_RT_SHADOW = vec3(0.05, 0.05, 0.25);

void main() {
    vec4 mask = texture(ShadowMaskSampler, texCoord);
    if (mask.b < HELION_RT_SET || mask.r > HELION_RT_SET) {
        discard;
    }
    fragColor = vec4(HELION_RT_SHADOW, 1.0);
}
