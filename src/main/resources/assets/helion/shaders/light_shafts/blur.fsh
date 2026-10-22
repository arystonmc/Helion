#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_light_shafts.glsl>

uniform sampler2D ShaftMaskSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

bool helionOnScreen(vec2 uv) {
    return all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)));
}

void main() {
    int samples = int(ShaftMarch.x);
    vec2 stepUv = (ShaftScreen.xy - texCoord) * ShaftMarch.z / float(samples);
    vec2 position = texCoord + stepUv * helionInterleavedGradientNoise(gl_FragCoord.xy);
    float weight = 1.0;
    float sum = 0.0;
    for (int index = 0; index < samples; index++) {
        if (!helionOnScreen(position)) {
            break;
        }
        sum += texture(ShaftMaskSampler, position).r * weight;
        weight *= ShaftMarch.y;
        position += stepUv;
    }
    fragColor = vec4(sum / float(samples), 0.0, 0.0, 1.0);
}
