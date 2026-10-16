#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_depth_of_field.glsl>

uniform sampler2D BlurredSampler;
uniform sampler2D DepthSampler;
uniform sampler2D FocusSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_MIN_BLEND_PIXELS = 1.0;

void main() {
    float focus = texelFetch(FocusSampler, ivec2(0), 0).r;
    float circle = helionDofCircle(texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r, texCoord, focus);
    vec4 blurred = texture(BlurredSampler, texCoord);
    float nearSpread = blurred.a < 0.0 ? -blurred.a : 0.0;
    float blurPixels = max(abs(circle), nearSpread) * MaxBlurPixels;
    bool behind = circle > 0.0 && circle >= nearSpread;
    float fullBlur = max(MaxBlurPixels * (behind ? HELION_DOF_FAR_LIMIT : HELION_DOF_NEAR_LIMIT), HELION_MIN_BLEND_PIXELS);
    fragColor = vec4(blurred.rgb, smoothstep(0.0, fullBlur, blurPixels));
}
