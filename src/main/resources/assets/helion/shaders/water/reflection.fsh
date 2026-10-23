#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_geometry.glsl>
#include <helion:helion_water.glsl>
#ifdef HELION_PHYSICAL_SKY
#include <helion:helion_sky_color.glsl>
#else
#include <minecraft:fog.glsl>
#endif

uniform sampler2D WaterSurfaceSampler;
uniform sampler2D WaterDepthSampler;
uniform sampler2D ReflectionSampler;

layout(std140) uniform HelionWaterReflection {
    mat4 WaterInverseViewProjection;
    float ReflectionStrength;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_WATER_F0 = 0.02;
const float HELION_FRESNEL_POWER = 5.0;
const float HELION_MIN_FACING = 0.05;
const float HELION_WATER_ABSORPTION = 0.15;
const vec3 HELION_WATER_SCATTER = vec3(0.08, 0.2, 0.22);
const vec3 HELION_UP = vec3(0.0, 1.0, 0.0);

vec3 helionReflectedSky(vec3 direction) {
    #ifdef HELION_PHYSICAL_SKY
    return helionSkyColorWithCelestials(direction);
    #else
    return FogColor.rgb;
    #endif
}

vec3 helionWaterAmbient() {
    #ifdef HELION_PHYSICAL_SKY
    return helionSkyColor(HELION_UP);
    #else
    return FogColor.rgb;
    #endif
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 surface = texelFetch(WaterSurfaceSampler, pixel, 0);
    if (!helionIsWater(surface)) {
        discard;
    }
    vec3 normal = helionDecodeOctahedral(surface.xy);
    if (normal.y < HELION_MIN_FACING) {
        discard;
    }
    float depth = texelFetch(WaterDepthSampler, pixel, 0).r;
    vec2 screen = (vec2(pixel) + 0.5) / vec2(textureSize(WaterDepthSampler, 0));
    vec4 world = WaterInverseViewProjection * vec4(screen * 2.0 - 1.0, depth, 1.0);
    vec3 view = normalize(world.xyz / world.w);
    vec3 reflected = reflect(view, normal);
    float facing = clamp(dot(-view, normal), 0.0, 1.0);
    float fresnel = (HELION_WATER_F0 + (1.0 - HELION_WATER_F0) * pow(1.0 - facing, HELION_FRESNEL_POWER)) * ReflectionStrength;
    vec4 traced = texelFetch(ReflectionSampler, pixel, 0);
    vec3 reflection = traced.a > HELION_GEOMETRY_PRESENT_THRESHOLD ? traced.rgb : helionReflectedSky(reflected);
    float transmitted = exp(-surface.z * HELION_WATER_ABSORPTION);
    vec3 scattered = HELION_WATER_SCATTER * helionWaterAmbient() * surface.w * surface.w;
    float kept = transmitted * (1.0 - fresnel);
    fragColor = vec4(reflection * fresnel + scattered * (1.0 - transmitted) * (1.0 - fresnel), 1.0 - kept);
}
