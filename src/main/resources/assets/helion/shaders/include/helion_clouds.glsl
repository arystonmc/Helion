#ifndef HELION_CLOUDS_GLSL
#define HELION_CLOUDS_GLSL

layout(std140) uniform HelionClouds {
    mat4 CloudInverseViewProjection;
    mat4 PreviousViewProjection;
    vec4 CloudOrigin;
    vec4 CameraMotion;
    vec4 CloudLight;
    vec4 CloudLayer;
    vec4 CloudQuality;
    vec4 CloudWeather;
    vec4 CloudTarget;
    vec4 CloudCheckerboard;
};

const float HELION_CLOUD_RAY_NEAR_DEPTH = 1.0;
const float HELION_CLOUD_RAY_FAR_DEPTH = 0.01;

vec3 helionCloudUnproject(vec2 screenUv, float deviceDepth) {
    vec4 world = CloudInverseViewProjection * vec4(screenUv * 2.0 - 1.0, deviceDepth, 1.0);
    return world.xyz / world.w;
}

vec3 helionCloudViewRay(vec2 screenUv) {
    return normalize(helionCloudUnproject(screenUv, HELION_CLOUD_RAY_FAR_DEPTH) - helionCloudUnproject(screenUv, HELION_CLOUD_RAY_NEAR_DEPTH));
}

#endif
