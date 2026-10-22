#ifndef HELION_LIGHT_LEVEL_GLSL
#define HELION_LIGHT_LEVEL_GLSL

const float HELION_LIGHT_LEVELS = 15.0;
const float HELION_BRIGHTNESS_CURVE = 3.0;

float helionLevelBrightness(float level) {
    return level / (HELION_BRIGHTNESS_CURVE + 1.0 - HELION_BRIGHTNESS_CURVE * level);
}

#endif
