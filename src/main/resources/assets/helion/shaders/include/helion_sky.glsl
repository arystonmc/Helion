#ifndef HELION_SKY_GLSL
#define HELION_SKY_GLSL

layout(std140) uniform HelionSky {
    mat4 InverseViewProjection;
    vec3 SunDirection;
    float SunIlluminance;
    vec3 MoonDirection;
    float MoonIlluminance;
    float ViewHeight;
    float RainBrightness;
    float AerialEnvironmentalStart;
    float AerialEnvironmentalEnd;
    float AerialRenderDistanceStart;
    float AerialRenderDistanceEnd;
};

#endif
