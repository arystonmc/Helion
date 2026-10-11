#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D OcclusionSampler;
#ifdef HELION_INDIRECT_SHARE
uniform sampler2D IndirectShareSampler;
#endif

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float visibility = texelFetch(OcclusionSampler, pixel, 0).r;
    #ifdef HELION_INDIRECT_SHARE
    visibility = mix(1.0, visibility, texelFetch(IndirectShareSampler, pixel, 0).r);
    #endif
    fragColor = vec4(vec3(visibility), 1.0);
}
