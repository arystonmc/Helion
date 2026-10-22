#ifndef HELION_LIGHT_SHAFTS_GLSL
#define HELION_LIGHT_SHAFTS_GLSL

layout(std140) uniform HelionLightShafts {
    mat4 ShaftInverseViewProjection;
    vec4 ShaftLight;
    vec4 ShaftScreen;
    vec4 ShaftTarget;
    vec4 ShaftMarch;
    vec4 ShaftScene;
};

const float HELION_SHAFT_RAY_NEAR_DEPTH = 1.0;
const float HELION_SHAFT_RAY_FAR_DEPTH = 0.01;

vec3 helionShaftUnproject(vec2 screenUv, float deviceDepth) {
    vec4 world = ShaftInverseViewProjection * vec4(screenUv * 2.0 - 1.0, deviceDepth, 1.0);
    return world.xyz / world.w;
}

vec3 helionShaftViewRay(vec2 screenUv) {
    return normalize(helionShaftUnproject(screenUv, HELION_SHAFT_RAY_FAR_DEPTH) - helionShaftUnproject(screenUv, HELION_SHAFT_RAY_NEAR_DEPTH));
}

#endif
