#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_view.glsl>
#include <helion:helion_ambient_occlusion_denoise.glsl>
#include <minecraft:fog.glsl>

uniform sampler2D ViewDepthSampler;
uniform sampler2D SceneColorSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const vec3 LUMINANCE_WEIGHTS = vec3(0.2126, 0.7152, 0.0722);
const float EMISSIVE_START = 0.8;
const float EMISSIVE_END = 1.0;

float multiBounce(float visibility, float albedo) {
    float a = 2.0404 * albedo - 0.3324;
    float b = -4.7951 * albedo + 0.6417;
    float c = 2.7552 * albedo + 0.6903;
    return max(visibility, ((visibility * a + b) * visibility + c) * visibility);
}

float resolve(ivec2 pixel, float visibility) {
    float viewDepth = texelFetch(ViewDepthSampler, pixel, 0).r;
    if (helionIsSky(viewDepth)) {
        return 1.0;
    }
    vec3 color = texelFetch(SceneColorSampler, pixel, 0).rgb;
    float luminance = dot(color, LUMINANCE_WEIGHTS);
    visibility = multiBounce(visibility, luminance);
    visibility = mix(visibility, 1.0, smoothstep(EMISSIVE_START, EMISSIVE_END, luminance));
    visibility = clamp(1.0 - (1.0 - visibility) * Strength, 0.0, 1.0);
    vec3 viewPosition = helionViewPosition((vec2(pixel) + 0.5) * ViewportPixelSize, viewDepth);
    float viewDistance = length(viewPosition);
    float fog = total_fog_value(
        viewDistance, viewDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd
    );
    return mix(visibility, 1.0, fog);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float visibility = clamp(helionDenoise(pixel, true) * HELION_OCCLUSION_TERM_SCALE, 0.0, 1.0);
    fragColor = vec4(resolve(pixel, visibility), 0.0, 0.0, 1.0);
}
