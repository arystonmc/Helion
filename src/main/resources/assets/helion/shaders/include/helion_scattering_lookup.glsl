#ifndef HELION_SCATTERING_LOOKUP_GLSL
#define HELION_SCATTERING_LOOKUP_GLSL

#include <helion:helion_spectrum.glsl>

uniform sampler2D MultipleScatteringSampler0;
uniform sampler2D MultipleScatteringSampler1;
uniform sampler2D MultipleScatteringSampler2;
uniform sampler2D MultipleScatteringSampler3;

vec4 helionMultipleScatteringGroup(int group, vec2 uv) {
    if (group == 0) {
        return texture(MultipleScatteringSampler0, uv);
    }
    if (group == 1) {
        return texture(MultipleScatteringSampler1, uv);
    }
    if (group == 2) {
        return texture(MultipleScatteringSampler2, uv);
    }
    return texture(MultipleScatteringSampler3, uv);
}

#endif
