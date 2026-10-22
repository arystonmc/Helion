#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D ShadowMaskSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_RTX_SET = 0.5;
const vec3 HELION_RTX_BOTH_LIT = vec3(0.92);
const vec3 HELION_RTX_BOTH_BLOCKED = vec3(0.04);
const vec3 HELION_RTX_ONLY_RAY_QUERY_BLOCKED = vec3(0.95, 0.15, 0.1);
const vec3 HELION_RTX_ONLY_GRID_WALK_BLOCKED = vec3(0.1, 0.85, 0.2);

void main() {
    vec4 mask = texture(ShadowMaskSampler, texCoord);
    if (mask.b < HELION_RTX_SET) {
        discard;
    }
    bool rayLit = mask.r > HELION_RTX_SET;
    bool walkLit = mask.g > HELION_RTX_SET;
    vec3 color = rayLit ? HELION_RTX_BOTH_LIT : HELION_RTX_BOTH_BLOCKED;
    if (rayLit != walkLit) {
        color = walkLit ? HELION_RTX_ONLY_RAY_QUERY_BLOCKED : HELION_RTX_ONLY_GRID_WALK_BLOCKED;
    }
    fragColor = vec4(color, 1.0);
}
