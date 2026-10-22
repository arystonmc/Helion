#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_light_shafts.glsl>

uniform sampler2D DepthSampler;
#ifdef HELION_CLOUD_SHAFTS
uniform sampler2D CloudColorSampler;
#endif

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float HELION_SKY_DEPTH = 0.0;
const int HELION_DEPTH_TAPS = 4;
const ivec2 HELION_TAP_GRID[HELION_DEPTH_TAPS] = ivec2[](ivec2(0, 0), ivec2(1, 0), ivec2(0, 1), ivec2(1, 1));
const float HELION_BELOW_HORIZON_FADE = -0.05;
const float HELION_HALO_SHARPNESS = 6.0;
const float HELION_HALO_WEIGHT = 0.2;

float helionSkyCoverage() {
    int divisor = int(ShaftScene.z);
    int spacing = max(divisor / 2, 1);
    ivec2 origin = ivec2(gl_FragCoord.xy) * divisor + ivec2(spacing / 2);
    ivec2 last = ivec2(ShaftScene.xy) - 1;
    float sky = 0.0;
    for (int tap = 0; tap < HELION_DEPTH_TAPS; tap++) {
        ivec2 pixel = min(origin + HELION_TAP_GRID[tap] * spacing, last);
        sky += texelFetch(DepthSampler, pixel, 0).r <= HELION_SKY_DEPTH ? 1.0 : 0.0;
    }
    return sky / float(HELION_DEPTH_TAPS);
}

float helionCloudTransmittance() {
#ifdef HELION_CLOUD_SHAFTS
    return clamp(texture(CloudColorSampler, texCoord).a, 0.0, 1.0);
#else
    return 1.0;
#endif
}

void main() {
    float sky = helionSkyCoverage();
    if (sky <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 ray = helionShaftViewRay(texCoord);
    float alignment = max(dot(ray, ShaftLight.xyz), 0.0);
    float glow = pow(alignment, ShaftMarch.w) + HELION_HALO_WEIGHT * pow(alignment, HELION_HALO_SHARPNESS);
    float horizon = smoothstep(HELION_BELOW_HORIZON_FADE, 0.0, ray.y);
    fragColor = vec4(sky * glow * horizon * helionCloudTransmittance(), 0.0, 0.0, 1.0);
}
