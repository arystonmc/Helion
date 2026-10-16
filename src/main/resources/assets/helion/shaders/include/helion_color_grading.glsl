#ifndef HELION_COLOR_GRADING_GLSL
#define HELION_COLOR_GRADING_GLSL

uniform sampler2D GradingLutSampler;

const float HELION_LUT_SIZE = 32.0;

vec3 helionGrade(vec3 display) {
    vec3 color = clamp(display, 0.0, 1.0);
    float blue = color.b * (HELION_LUT_SIZE - 1.0);
    float slice = floor(blue);
    float sliceFraction = blue - slice;
    vec2 texel = vec2(color.r * (HELION_LUT_SIZE - 1.0) + 0.5 + slice * HELION_LUT_SIZE, color.g * (HELION_LUT_SIZE - 1.0) + 0.5);
    vec2 lutSize = vec2(HELION_LUT_SIZE * HELION_LUT_SIZE, HELION_LUT_SIZE);
    vec3 lower = texture(GradingLutSampler, texel / lutSize).rgb;
    vec3 upper = texture(GradingLutSampler, (texel + vec2(HELION_LUT_SIZE, 0.0)) / lutSize).rgb;
    return mix(lower, upper, sliceFraction);
}

#endif
