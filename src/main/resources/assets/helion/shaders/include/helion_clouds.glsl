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
};

// CloudOrigin: xyz the camera position inside the repeating noise period (y is the real height), w unused.
// CameraMotion: xyz camera position now minus last frame, w 1 when the history can be used.
// CloudLight: xyz direction of the light that lights the clouds, w 1 for the sun and 0 for the moon.
// CloudLayer: bottom and top height of the cumulus layer, extinction per block, coverage.
// CloudQuality: primary steps, light steps, 1 with the high cirrus layer, frame index.
// CloudWeather: rain strength, time in seconds, maximum distance, distance where the clouds start to fade.
// CloudTarget: size of the cloud target and its inverse.

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
