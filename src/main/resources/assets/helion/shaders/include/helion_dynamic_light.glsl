#ifndef HELION_DYNAMIC_LIGHT_GLSL
#define HELION_DYNAMIC_LIGHT_GLSL

#ifdef HELION_DYNAMIC_LIGHTS
layout(std140) uniform HelionDynamicLights {
    vec4 DynamicLights[HELION_MAX_DYNAMIC_LIGHTS];
    int DynamicLightCount;
};

const float HELION_MAX_LIGHT_LEVEL = 15.0;
const float HELION_LIGHT_COORDINATES_PER_LEVEL = 16.0;

ivec2 helion_dynamic_light(ivec2 light, vec3 cameraRelativePosition) {
    float level = 0.0;
    for (int index = 0; index < DynamicLightCount; index++) {
        vec4 source = DynamicLights[index];
        level = max(level, source.w - distance(cameraRelativePosition, source.xyz));
    }
    int blockLight = int(clamp(level, 0.0, HELION_MAX_LIGHT_LEVEL) * HELION_LIGHT_COORDINATES_PER_LEVEL);
    return ivec2(max(light.x, blockLight), light.y);
}
#else
ivec2 helion_dynamic_light(ivec2 light, vec3 cameraRelativePosition) {
    return light;
}
#endif

#endif
