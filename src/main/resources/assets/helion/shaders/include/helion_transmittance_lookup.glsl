#ifndef HELION_TRANSMITTANCE_LOOKUP_GLSL
#define HELION_TRANSMITTANCE_LOOKUP_GLSL

#include <helion:helion_spectrum.glsl>

uniform sampler2D TransmittanceSampler0;
uniform sampler2D TransmittanceSampler1;
uniform sampler2D TransmittanceSampler2;
uniform sampler2D TransmittanceSampler3;

vec4 helionTransmittanceGroup(int group, vec2 uv) {
    if (group == 0) {
        return texture(TransmittanceSampler0, uv);
    }
    if (group == 1) {
        return texture(TransmittanceSampler1, uv);
    }
    if (group == 2) {
        return texture(TransmittanceSampler2, uv);
    }
    return texture(TransmittanceSampler3, uv);
}

#endif
