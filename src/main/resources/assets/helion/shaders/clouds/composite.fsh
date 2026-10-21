#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_color.glsl>
#include <helion:helion_clouds.glsl>

uniform sampler2D CloudColorSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_SKY_DEPTH = 0.0;
const float HELION_MIN_COVER = 1.0e-3;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    if (depth > HELION_SKY_DEPTH && CloudOrigin.y < CloudLayer.x) {
        discard;
    }
    vec4 clouds = texture(CloudColorSampler, texCoord);
    float cover = 1.0 - clamp(clouds.a, 0.0, 1.0);
    if (cover <= HELION_MIN_COVER) {
        discard;
    }
    vec3 radiance = clouds.rgb / cover;
    fragColor = vec4(helionLinearToSrgb(helionNeutral(radiance)), cover);
}
