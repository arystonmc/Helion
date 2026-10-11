#ifndef HELION_MOTION_BLUR_GLSL
#define HELION_MOTION_BLUR_GLSL

layout(std140) uniform HelionMotionBlur {
    mat4 Reprojection;
    vec2 InverseScreenSize;
    float BlurScale;
    float MaxBlurPixels;
};

const int HELION_MOTION_TILE_SIZE = 16;

float helionMotionNdcDepth(float deviceDepth) {
    #ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    return deviceDepth;
    #else
    return deviceDepth * 2.0 - 1.0;
    #endif
}

vec2 helionBlurVelocity(vec2 pixelCenter, float deviceDepth) {
    vec2 uv = pixelCenter * InverseScreenSize;
    vec4 previous = Reprojection * vec4(uv * 2.0 - 1.0, helionMotionNdcDepth(deviceDepth), 1.0);
    vec2 previousUv = previous.xy / previous.w * 0.5 + 0.5;
    vec2 velocity = (uv - previousUv) / InverseScreenSize * BlurScale;
    float speed = length(velocity);
    return speed > MaxBlurPixels ? velocity * (MaxBlurPixels / speed) : velocity;
}

vec2 helionLonger(vec2 first, vec2 second) {
    return dot(second, second) > dot(first, first) ? second : first;
}

#endif
