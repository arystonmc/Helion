#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_depth_of_field.glsl>

uniform sampler2D BlurredSampler;
uniform sampler2D DepthSampler;
uniform sampler2D FocusSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_SHARP_PIXELS = 0.5;

void main() {
    float focus = texelFetch(FocusSampler, ivec2(0), 0).r;
    float circle = helionCircleOfConfusion(helionDofViewDepth(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r), focus);
    vec4 blurred = texture(BlurredSampler, texCoord);
    float blurPixels = max(abs(circle), abs(blurred.a)) * MaxBlurPixels;
    fragColor = vec4(blurred.rgb, clamp(blurPixels - HELION_SHARP_PIXELS, 0.0, 1.0));
}
