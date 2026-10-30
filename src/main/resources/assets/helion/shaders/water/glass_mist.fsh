#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_water.glsl>

uniform sampler2D RefractionSampler;
uniform sampler2D GlassSurfaceSampler;

layout(location = 0) out vec4 fragColor;

const int HELION_MIST_TAPS = 16;
const float HELION_MIST_RADIUS = 0.02;
const float HELION_MIST_GOLDEN_ANGLE = 2.39996323;
const float HELION_MIST_TURN = 6.2831853;
const vec2 HELION_MIST_NOISE_STEP = vec2(0.06711056, 0.00583715);
const float HELION_MIST_NOISE_SCALE = 52.9829189;
const int HELION_MIST_SCALE = 2;

bool helionGlassInTexel(ivec2 pixel) {
    ivec2 last = textureSize(GlassSurfaceSampler, 0) - 1;
    ivec2 corner = pixel * HELION_MIST_SCALE;
    for (int y = 0; y < HELION_MIST_SCALE; y++) {
        for (int x = 0; x < HELION_MIST_SCALE; x++) {
            if (helionIsGlossy(texelFetch(GlassSurfaceSampler, min(corner + ivec2(x, y), last), 0))) {
                return true;
            }
        }
    }
    return false;
}

float helionMistNoise(ivec2 pixel) {
    return fract(HELION_MIST_NOISE_SCALE * fract(dot(vec2(pixel), HELION_MIST_NOISE_STEP)));
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec2 size = vec2(textureSize(RefractionSampler, 0));
    vec2 screen = gl_FragCoord.xy * float(HELION_MIST_SCALE) / size;
    if (!helionGlassInTexel(pixel)) {
        fragColor = vec4(textureLod(RefractionSampler, screen, 0.0).rgb, 1.0);
        return;
    }
    float spin = helionMistNoise(pixel) * HELION_MIST_TURN;
    vec3 sum = vec3(0.0);
    for (int tap = 0; tap < HELION_MIST_TAPS; tap++) {
        float along = sqrt((float(tap) + 0.5) / float(HELION_MIST_TAPS)) * HELION_MIST_RADIUS * size.y;
        float angle = float(tap) * HELION_MIST_GOLDEN_ANGLE + spin;
        sum += textureLod(RefractionSampler, clamp(screen + vec2(cos(angle), sin(angle)) * along / size, vec2(0.0), vec2(1.0)), 0.0).rgb;
    }
    fragColor = vec4(sum / float(HELION_MIST_TAPS), 1.0);
}
