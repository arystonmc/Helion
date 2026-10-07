#ifndef HELION_SHADOW_GLSL
#define HELION_SHADOW_GLSL

const int HELION_SHADOW_CASCADES = 4;

layout(std140) uniform HelionShadow {
    mat4 InverseViewProjection;
    mat4 CascadeMatrices[HELION_SHADOW_CASCADES];
    vec4 CascadeTexelSizes;
    vec3 LightDirection;
    float ShadowDistance;
    float CascadeResolution;
};

uniform sampler2D ShadowMapSampler;

const int HELION_PCF_FIRST = -1;
const int HELION_PCF_LAST = 2;

float helionPcfWeight(int offset, float fraction) {
    if (offset == HELION_PCF_FIRST) {
        return 1.0 - fraction;
    }
    return offset == HELION_PCF_LAST ? fraction : 1.0;
}

float helionCascadeVisibility(int cascade, vec3 clip) {
    vec2 texel = (clip.xy * 0.5 + 0.5) * CascadeResolution - 0.5;
    vec2 base = floor(texel);
    vec2 fraction = texel - base;
    int resolution = int(CascadeResolution);
    float visible = 0.0;
    float total = 0.0;
    for (int y = HELION_PCF_FIRST; y <= HELION_PCF_LAST; y++) {
        float weightY = helionPcfWeight(y, fraction.y);
        for (int x = HELION_PCF_FIRST; x <= HELION_PCF_LAST; x++) {
            float weight = helionPcfWeight(x, fraction.x) * weightY;
            ivec2 coordinate = clamp(ivec2(base) + ivec2(x, y), ivec2(0), ivec2(resolution - 1));
            coordinate.x += cascade * resolution;
            float occluder = texelFetch(ShadowMapSampler, coordinate, 0).r;
            visible += clip.z <= occluder ? weight : 0.0;
            total += weight;
        }
    }
    return visible / total;
}

#endif
