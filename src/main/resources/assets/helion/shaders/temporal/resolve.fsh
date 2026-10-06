#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform HelionTemporal {
    mat4 Reprojection;
    vec2 InverseScreenSize;
    float CurrentWeight;
    float HistoryValid;
};

uniform sampler2D SceneColorSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HistorySampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int HELION_NEIGHBORHOOD_RADIUS = 1;
const float HELION_NEIGHBORHOOD_SIZE = 9.0;
const float HELION_VARIANCE_CLIP_GAMMA = 1.0;
const float HELION_CLIP_EPSILON = 1.0e-5;
const float HELION_HISTORY_VALID_THRESHOLD = 0.5;
const float HELION_WEIGHT_EPSILON = 1.0e-5;
const float HELION_NO_DEPTH = -1.0;
const vec3 HELION_LUMA_ROW = vec3(0.25, 0.5, 0.25);
const vec3 HELION_ORANGE_CHROMA_ROW = vec3(0.5, 0.0, -0.5);
const vec3 HELION_GREEN_CHROMA_ROW = vec3(-0.25, 0.5, -0.25);

vec3 helionToYCoCg(vec3 color) {
    return vec3(
        dot(color, HELION_LUMA_ROW),
        dot(color, HELION_ORANGE_CHROMA_ROW),
        dot(color, HELION_GREEN_CHROMA_ROW)
    );
}

vec3 helionFromYCoCg(vec3 color) {
    return vec3(color.x + color.y - color.z, color.x + color.z, color.x - color.y - color.z);
}

vec3 helionClipToBox(vec3 color, vec3 boxMin, vec3 boxMax) {
    vec3 center = 0.5 * (boxMax + boxMin);
    vec3 extent = 0.5 * (boxMax - boxMin) + HELION_CLIP_EPSILON;
    vec3 offset = color - center;
    vec3 units = abs(offset / extent);
    float largest = max(units.x, max(units.y, units.z));
    return largest > 1.0 ? center + offset / largest : color;
}

vec3 helionHistoryTap(vec2 uv) {
    return texture(HistorySampler, uv).rgb;
}

vec3 helionSampleHistoryCatmullRom(vec2 uv) {
    vec2 position = uv / InverseScreenSize;
    vec2 center = floor(position - 0.5) + 0.5;
    vec2 f = position - center;
    vec2 weight0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 weight1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 weight2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 weight3 = f * f * (-0.5 + 0.5 * f);
    vec2 weight12 = weight1 + weight2;
    vec2 uv0 = (center - 1.0) * InverseScreenSize;
    vec2 uv3 = (center + 2.0) * InverseScreenSize;
    vec2 uv12 = (center + weight2 / weight12) * InverseScreenSize;
    vec3 sum = helionHistoryTap(vec2(uv12.x, uv0.y)) * (weight12.x * weight0.y)
        + helionHistoryTap(vec2(uv0.x, uv12.y)) * (weight0.x * weight12.y)
        + helionHistoryTap(uv12) * (weight12.x * weight12.y)
        + helionHistoryTap(vec2(uv3.x, uv12.y)) * (weight3.x * weight12.y)
        + helionHistoryTap(vec2(uv12.x, uv3.y)) * (weight12.x * weight3.y);
    float total = weight12.x * weight0.y + weight0.x * weight12.y + weight12.x * weight12.y
        + weight3.x * weight12.y + weight12.x * weight3.y;
    return max(sum / total, 0.0);
}

float helionNdcDepth(float deviceDepth) {
    #ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    return deviceDepth;
    #else
    return deviceDepth * 2.0 - 1.0;
    #endif
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec3 current = texelFetch(SceneColorSampler, pixel, 0).rgb;
    if (HistoryValid < HELION_HISTORY_VALID_THRESHOLD) {
        fragColor = vec4(current, 1.0);
        return;
    }
    ivec2 lastPixel = textureSize(SceneColorSampler, 0) - 1;
    vec3 sum = vec3(0.0);
    vec3 sumOfSquares = vec3(0.0);
    float nearestDepth = HELION_NO_DEPTH;
    ivec2 nearestPixel = pixel;
    for (int y = -HELION_NEIGHBORHOOD_RADIUS; y <= HELION_NEIGHBORHOOD_RADIUS; y++) {
        for (int x = -HELION_NEIGHBORHOOD_RADIUS; x <= HELION_NEIGHBORHOOD_RADIUS; x++) {
            ivec2 neighbor = clamp(pixel + ivec2(x, y), ivec2(0), lastPixel);
            vec3 color = helionToYCoCg(texelFetch(SceneColorSampler, neighbor, 0).rgb);
            sum += color;
            sumOfSquares += color * color;
            float depth = texelFetch(DepthSampler, neighbor, 0).r;
            if (depth > nearestDepth) {
                nearestDepth = depth;
                nearestPixel = neighbor;
            }
        }
    }
    vec3 mean = sum / HELION_NEIGHBORHOOD_SIZE;
    vec3 deviation = sqrt(max(sumOfSquares / HELION_NEIGHBORHOOD_SIZE - mean * mean, 0.0));
    vec3 boxMin = mean - HELION_VARIANCE_CLIP_GAMMA * deviation;
    vec3 boxMax = mean + HELION_VARIANCE_CLIP_GAMMA * deviation;

    vec2 nearestUv = (vec2(nearestPixel) + 0.5) * InverseScreenSize;
    vec4 previousClip = Reprojection * vec4(nearestUv * 2.0 - 1.0, helionNdcDepth(nearestDepth), 1.0);
    vec2 previousUv = previousClip.xy / previousClip.w * 0.5 + 0.5;
    vec2 historyUv = gl_FragCoord.xy * InverseScreenSize - (nearestUv - previousUv);
    if (any(lessThan(historyUv, vec2(0.0))) || any(greaterThan(historyUv, vec2(1.0)))) {
        fragColor = vec4(current, 1.0);
        return;
    }

    vec3 history = helionClipToBox(helionToYCoCg(helionSampleHistoryCatmullRom(historyUv)), boxMin, boxMax);
    vec3 currentYCoCg = helionToYCoCg(current);
    float currentWeight = CurrentWeight / (1.0 + currentYCoCg.x);
    float historyWeight = (1.0 - CurrentWeight) / (1.0 + history.x);
    vec3 resolved = (currentYCoCg * currentWeight + history * historyWeight) / max(currentWeight + historyWeight, HELION_WEIGHT_EPSILON);
    fragColor = vec4(max(helionFromYCoCg(resolved), 0.0), 1.0);
}
