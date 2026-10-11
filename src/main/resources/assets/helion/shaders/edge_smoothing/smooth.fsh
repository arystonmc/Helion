#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D SceneColorSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const vec3 HELION_LUMA_WEIGHTS = vec3(0.299, 0.587, 0.114);
const float HELION_EDGE_THRESHOLD = 0.125;
const float HELION_EDGE_THRESHOLD_MIN = 0.0312;
const float HELION_SUBPIXEL_QUALITY = 0.75;
const int HELION_SEARCH_STEPS = 10;
const float HELION_SEARCH_GRADIENT_SCALE = 0.25;
const float HELION_HALF = 0.5;
const float HELION_DIAGONAL_WEIGHT = 2.0;
const float HELION_NEIGHBOR_WEIGHT_SUM = 12.0;
const float HELION_SMOOTHSTEP_CUBIC = 3.0;

float helionLuma(vec2 uv) {
    return dot(texture(SceneColorSampler, uv).rgb, HELION_LUMA_WEIGHTS);
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(SceneColorSampler, 0));
    vec4 center = texture(SceneColorSampler, texCoord);
    float lumaCenter = dot(center.rgb, HELION_LUMA_WEIGHTS);
    float lumaDown = helionLuma(texCoord + vec2(0.0, -texel.y));
    float lumaUp = helionLuma(texCoord + vec2(0.0, texel.y));
    float lumaLeft = helionLuma(texCoord + vec2(-texel.x, 0.0));
    float lumaRight = helionLuma(texCoord + vec2(texel.x, 0.0));
    float lumaMin = min(lumaCenter, min(min(lumaDown, lumaUp), min(lumaLeft, lumaRight)));
    float lumaMax = max(lumaCenter, max(max(lumaDown, lumaUp), max(lumaLeft, lumaRight)));
    float range = lumaMax - lumaMin;
    if (range < max(HELION_EDGE_THRESHOLD_MIN, lumaMax * HELION_EDGE_THRESHOLD)) {
        fragColor = center;
        return;
    }
    float lumaDownLeft = helionLuma(texCoord - texel);
    float lumaUpRight = helionLuma(texCoord + texel);
    float lumaUpLeft = helionLuma(texCoord + vec2(-texel.x, texel.y));
    float lumaDownRight = helionLuma(texCoord + vec2(texel.x, -texel.y));
    float downUp = lumaDown + lumaUp;
    float leftRight = lumaLeft + lumaRight;
    float leftCorners = lumaDownLeft + lumaUpLeft;
    float downCorners = lumaDownLeft + lumaDownRight;
    float rightCorners = lumaDownRight + lumaUpRight;
    float upCorners = lumaUpRight + lumaUpLeft;
    float edgeHorizontal = abs(leftCorners - HELION_DIAGONAL_WEIGHT * lumaLeft)
        + abs(downUp - HELION_DIAGONAL_WEIGHT * lumaCenter) * HELION_DIAGONAL_WEIGHT
        + abs(rightCorners - HELION_DIAGONAL_WEIGHT * lumaRight);
    float edgeVertical = abs(upCorners - HELION_DIAGONAL_WEIGHT * lumaUp)
        + abs(leftRight - HELION_DIAGONAL_WEIGHT * lumaCenter) * HELION_DIAGONAL_WEIGHT
        + abs(downCorners - HELION_DIAGONAL_WEIGHT * lumaDown);
    bool horizontal = edgeHorizontal >= edgeVertical;

    float lumaNegative = horizontal ? lumaDown : lumaLeft;
    float lumaPositive = horizontal ? lumaUp : lumaRight;
    float gradientNegative = abs(lumaNegative - lumaCenter);
    float gradientPositive = abs(lumaPositive - lumaCenter);
    float stepLength = horizontal ? texel.y : texel.x;
    bool negativeSide = gradientNegative >= gradientPositive;
    float edgeLuma = HELION_HALF * (lumaCenter + (negativeSide ? lumaNegative : lumaPositive));
    float scaledGradient = HELION_SEARCH_GRADIENT_SCALE * max(gradientNegative, gradientPositive);
    if (negativeSide) {
        stepLength = -stepLength;
    }

    vec2 edgeUv = texCoord;
    if (horizontal) {
        edgeUv.y += stepLength * HELION_HALF;
    } else {
        edgeUv.x += stepLength * HELION_HALF;
    }
    vec2 offset = horizontal ? vec2(texel.x, 0.0) : vec2(0.0, texel.y);
    vec2 uvNegative = edgeUv - offset;
    vec2 uvPositive = edgeUv + offset;
    float endNegative = helionLuma(uvNegative) - edgeLuma;
    float endPositive = helionLuma(uvPositive) - edgeLuma;
    bool reachedNegative = abs(endNegative) >= scaledGradient;
    bool reachedPositive = abs(endPositive) >= scaledGradient;
    for (int step = 0; step < HELION_SEARCH_STEPS && !(reachedNegative && reachedPositive); step++) {
        if (!reachedNegative) {
            uvNegative -= offset;
            endNegative = helionLuma(uvNegative) - edgeLuma;
            reachedNegative = abs(endNegative) >= scaledGradient;
        }
        if (!reachedPositive) {
            uvPositive += offset;
            endPositive = helionLuma(uvPositive) - edgeLuma;
            reachedPositive = abs(endPositive) >= scaledGradient;
        }
    }

    float distanceNegative = horizontal ? texCoord.x - uvNegative.x : texCoord.y - uvNegative.y;
    float distancePositive = horizontal ? uvPositive.x - texCoord.x : uvPositive.y - texCoord.y;
    bool closerNegative = distanceNegative < distancePositive;
    float closest = min(distanceNegative, distancePositive);
    float edgeLength = distanceNegative + distancePositive;
    bool centerSmaller = lumaCenter < edgeLuma;
    bool correctVariation = ((closerNegative ? endNegative : endPositive) < 0.0) != centerSmaller;
    float edgeOffset = correctVariation ? HELION_HALF - closest / edgeLength : 0.0;

    float average = (HELION_DIAGONAL_WEIGHT * (downUp + leftRight) + leftCorners + rightCorners) / HELION_NEIGHBOR_WEIGHT_SUM;
    float subpixel = clamp(abs(average - lumaCenter) / range, 0.0, 1.0);
    float subpixelCurve = (-HELION_DIAGONAL_WEIGHT * subpixel + HELION_SMOOTHSTEP_CUBIC) * subpixel * subpixel;
    float subpixelOffset = subpixelCurve * subpixelCurve * HELION_SUBPIXEL_QUALITY;

    float finalOffset = max(edgeOffset, subpixelOffset);
    vec2 finalUv = texCoord;
    if (horizontal) {
        finalUv.y += finalOffset * stepLength;
    } else {
        finalUv.x += finalOffset * stepLength;
    }
    fragColor = vec4(texture(SceneColorSampler, finalUv).rgb, center.a);
}
