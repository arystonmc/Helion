#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_view.glsl>
#include <helion:helion_ambient_occlusion.glsl>

uniform sampler2D ViewDepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265358979;
const float HALF_PI = 1.57079632679490;
const float RADIUS_MULTIPLIER = 1.457;
const float PIXEL_TOO_CLOSE_THRESHOLD = 1.3;
const float SMALL_RADIUS_FADE_START = 10.0;
const float SMALL_RADIUS_FADE_RANGE = 100.0;
const float SMALL_RADIUS_FADE_STRENGTH = 0.5;
const float MIN_VISIBILITY = 0.03;
const float NORMAL_LENGTH_FUDGE = 0.05;
const float EDGE_DEPTH_SCALE = 0.011;
const float EDGE_SHARPNESS = 1.25;
const float ACCEPTED_NORMAL_BIAS = 0.01;
const float FAST_ACOS_SLOPE = -0.156583;
const float GOLDEN_RATIO_FRACTION = 0.6180339887498948;
const vec2 R2_SEQUENCE = vec2(0.75487766624669276, 0.56984029099805327);
const uint NOISE_TILE = 64u;
const uint SECTOR_COUNT = 32u;
const uint ALL_SECTORS = 0xFFFFFFFFu;
const float OCCLUDER_THICKNESS = 0.75;
const float SECTOR_CENTER = 0.5;

float viewDepthAt(ivec2 pixel) {
    ivec2 bounds = textureSize(ViewDepthSampler, 0) - 1;
    return texelFetch(ViewDepthSampler, clamp(pixel, ivec2(0), bounds), 0).r;
}

vec3 viewPositionAt(vec2 pixelCenter, float viewDepth) {
    return helionViewPosition(pixelCenter * ViewportPixelSize, viewDepth);
}

float fastAcos(float value) {
    float absolute = abs(value);
    float result = (FAST_ACOS_SLOPE * absolute + HALF_PI) * sqrt(1.0 - absolute);
    return value >= 0.0 ? result : PI - result;
}

uint hilbertIndex(uint positionX, uint positionY) {
    uint index = 0u;
    for (uint level = NOISE_TILE / 2u; level > 0u; level /= 2u) {
        uint regionX = (positionX & level) > 0u ? 1u : 0u;
        uint regionY = (positionY & level) > 0u ? 1u : 0u;
        index += level * level * ((3u * regionX) ^ regionY);
        if (regionY == 0u) {
            if (regionX == 1u) {
                positionX = NOISE_TILE - 1u - positionX;
                positionY = NOISE_TILE - 1u - positionY;
            }
            uint swapped = positionX;
            positionX = positionY;
            positionY = swapped;
        }
    }
    return index;
}

vec2 spatialNoise(ivec2 pixel) {
    uint index = hilbertIndex(uint(pixel.x) % NOISE_TILE, uint(pixel.y) % NOISE_TILE);
    return fract(0.5 + float(index) * R2_SEQUENCE);
}

vec4 calculateEdges(float centerZ, float leftZ, float rightZ, float topZ, float bottomZ) {
    vec4 edgesLRTB = vec4(leftZ, rightZ, topZ, bottomZ) - centerZ;
    float slopeLR = (edgesLRTB.y - edgesLRTB.x) * 0.5;
    float slopeTB = (edgesLRTB.w - edgesLRTB.z) * 0.5;
    vec4 slopeAdjusted = edgesLRTB + vec4(slopeLR, -slopeLR, slopeTB, -slopeTB);
    edgesLRTB = min(abs(edgesLRTB), abs(slopeAdjusted));
    return clamp(EDGE_SHARPNESS - edgesLRTB / (centerZ * EDGE_DEPTH_SCALE), 0.0, 1.0);
}

vec3 calculateNormal(vec4 edgesLRTB, vec3 center, vec3 left, vec3 right, vec3 top, vec3 bottom) {
    vec4 accepted = clamp(
        vec4(edgesLRTB.x * edgesLRTB.z, edgesLRTB.z * edgesLRTB.y, edgesLRTB.y * edgesLRTB.w, edgesLRTB.w * edgesLRTB.x) + ACCEPTED_NORMAL_BIAS,
        0.0,
        1.0
    );
    vec3 toLeft = normalize(left - center);
    vec3 toRight = normalize(right - center);
    vec3 toTop = normalize(top - center);
    vec3 toBottom = normalize(bottom - center);
    vec3 normal = accepted.x * cross(toLeft, toTop)
        + accepted.y * cross(toTop, toRight)
        + accepted.z * cross(toRight, toBottom)
        + accepted.w * cross(toBottom, toLeft);
    return normalize(normal);
}

float sliceVisibility(ivec2 pixel, vec3 center, vec3 viewVector, vec3 normal, float sliceK, float noiseSample, int slice, float screenRadius, float minS) {
    float effectRadius = EffectRadius * RADIUS_MULTIPLIER;
    float falloffRange = EffectFalloffRange * effectRadius;
    float falloffFrom = effectRadius * (1.0 - EffectFalloffRange);
    float falloffMul = -1.0 / falloffRange;
    float falloffAdd = falloffFrom / falloffRange + 1.0;

    float phi = sliceK * PI;
    vec2 omega = vec2(cos(phi), -sin(phi)) * screenRadius;
    vec3 direction = vec3(cos(phi), sin(phi), 0.0);
    vec3 orthoDirection = direction - dot(direction, viewVector) * viewVector;
    vec3 axis = normalize(cross(orthoDirection, viewVector));
    vec3 projectedNormal = normal - axis * dot(normal, axis);
    float signNormal = sign(dot(orthoDirection, projectedNormal));
    float projectedNormalLength = length(projectedNormal);
    float cosNormal = clamp(dot(projectedNormal, viewVector) / projectedNormalLength, 0.0, 1.0);
    float n = signNormal * fastAcos(cosNormal);

    float lowHorizonCos0 = cos(n + HALF_PI);
    float lowHorizonCos1 = cos(n - HALF_PI);
    float horizonCos0 = lowHorizonCos0;
    float horizonCos1 = lowHorizonCos1;
    vec2 pixelCenter = vec2(pixel) + 0.5;

    for (int step = 0; step < StepsPerSlice; step++) {
        float stepNoise = fract(noiseSample + float(slice + step * StepsPerSlice) * GOLDEN_RATIO_FRACTION);
        float s = (float(step) + stepNoise) / float(StepsPerSlice);
        s = pow(s, SampleDistributionPower) + minS;
        vec2 sampleOffset = round(s * omega);

        vec2 samplePixel0 = pixelCenter + sampleOffset;
        vec2 samplePixel1 = pixelCenter - sampleOffset;
        vec3 delta0 = viewPositionAt(samplePixel0, viewDepthAt(ivec2(floor(samplePixel0)))) - center;
        vec3 delta1 = viewPositionAt(samplePixel1, viewDepthAt(ivec2(floor(samplePixel1)))) - center;

        float distance0 = length(delta0);
        float distance1 = length(delta1);
        vec3 horizon0 = delta0 / distance0;
        vec3 horizon1 = delta1 / distance1;

        float falloffBase0 = length(vec3(delta0.xy, delta0.z * (1.0 + ThinOccluderCompensation)));
        float falloffBase1 = length(vec3(delta1.xy, delta1.z * (1.0 + ThinOccluderCompensation)));
        float weight0 = clamp(falloffBase0 * falloffMul + falloffAdd, 0.0, 1.0);
        float weight1 = clamp(falloffBase1 * falloffMul + falloffAdd, 0.0, 1.0);

        float sampleHorizonCos0 = mix(lowHorizonCos0, dot(horizon0, viewVector), weight0);
        float sampleHorizonCos1 = mix(lowHorizonCos1, dot(horizon1, viewVector), weight1);
        horizonCos0 = max(horizonCos0, sampleHorizonCos0);
        horizonCos1 = max(horizonCos1, sampleHorizonCos1);
    }

    projectedNormalLength = mix(projectedNormalLength, 1.0, NORMAL_LENGTH_FUDGE);
    float h0 = -fastAcos(horizonCos1);
    float h1 = fastAcos(horizonCos0);
    h0 = n + clamp(h0 - n, -HALF_PI, HALF_PI);
    h1 = n + clamp(h1 - n, -HALF_PI, HALF_PI);
    float sinNormal = sin(n);
    float arc0 = (cosNormal + 2.0 * h0 * sinNormal - cos(2.0 * h0 - n)) / 4.0;
    float arc1 = (cosNormal + 2.0 * h1 * sinNormal - cos(2.0 * h1 - n)) / 4.0;
    return projectedNormalLength * (arc0 + arc1);
}

uint countSectors(uint bits) {
    bits = bits - ((bits >> 1u) & 0x55555555u);
    bits = (bits & 0x33333333u) + ((bits >> 2u) & 0x33333333u);
    return (((bits + (bits >> 4u)) & 0x0F0F0F0Fu) * 0x01010101u) >> 24u;
}

float cosineWeightedPosition(float angle, float n) {
    return SECTOR_CENTER + SECTOR_CENTER * sin(clamp(angle - n, -HALF_PI, HALF_PI));
}

uint occludedSectors(vec2 samplePixel, vec3 center, vec3 viewVector, float n, float side, float effectRadius) {
    vec3 delta = viewPositionAt(samplePixel, viewDepthAt(ivec2(floor(samplePixel)))) - center;
    float distance = length(delta);
    if (distance <= 0.0 || distance > effectRadius) {
        return 0u;
    }
    vec3 back = delta - viewVector * OCCLUDER_THICKNESS;
    float frontAngle = side * fastAcos(clamp(dot(delta / distance, viewVector), -1.0, 1.0));
    float backAngle = side * fastAcos(clamp(dot(normalize(back), viewVector), -1.0, 1.0));
    float low = cosineWeightedPosition(min(frontAngle, backAngle), n);
    float high = cosineWeightedPosition(max(frontAngle, backAngle), n);
    uint first = uint(low * float(SECTOR_COUNT));
    uint count = uint(ceil((high - low) * float(SECTOR_COUNT)));
    if (count == 0u || first >= SECTOR_COUNT) {
        return 0u;
    }
    uint span = count >= SECTOR_COUNT ? ALL_SECTORS : (1u << count) - 1u;
    return span << first;
}

float sliceVisibilityBitmask(ivec2 pixel, vec3 center, vec3 viewVector, vec3 normal, float sliceK, float noiseSample, int slice, float screenRadius, float minS) {
    float effectRadius = EffectRadius * RADIUS_MULTIPLIER;
    float phi = sliceK * PI;
    vec2 omega = vec2(cos(phi), -sin(phi)) * screenRadius;
    vec3 direction = vec3(cos(phi), sin(phi), 0.0);
    vec3 orthoDirection = direction - dot(direction, viewVector) * viewVector;
    vec3 axis = normalize(cross(orthoDirection, viewVector));
    vec3 projectedNormal = normal - axis * dot(normal, axis);
    float signNormal = sign(dot(orthoDirection, projectedNormal));
    float projectedNormalLength = length(projectedNormal);
    float cosNormal = clamp(dot(projectedNormal, viewVector) / projectedNormalLength, 0.0, 1.0);
    float n = signNormal * fastAcos(cosNormal);
    vec2 pixelCenter = vec2(pixel) + 0.5;
    uint occluded = 0u;

    for (int step = 0; step < StepsPerSlice; step++) {
        float stepNoise = fract(noiseSample + float(slice + step * StepsPerSlice) * GOLDEN_RATIO_FRACTION);
        float s = (float(step) + stepNoise) / float(StepsPerSlice);
        s = pow(s, SampleDistributionPower) + minS;
        vec2 sampleOffset = round(s * omega);
        occluded |= occludedSectors(pixelCenter + sampleOffset, center, viewVector, n, 1.0, effectRadius);
        occluded |= occludedSectors(pixelCenter - sampleOffset, center, viewVector, n, -1.0, effectRadius);
    }

    projectedNormalLength = mix(projectedNormalLength, 1.0, NORMAL_LENGTH_FUDGE);
    return projectedNormalLength * (1.0 - float(countSectors(occluded)) / float(SECTOR_COUNT));
}

float visibilityAt(ivec2 pixel, vec3 center, vec3 viewVector, vec3 normal) {
    float effectRadius = EffectRadius * RADIUS_MULTIPLIER;
    float pixelViewSize = helionTanHalfFov().x * 2.0 * ViewportPixelSize.x * center.z;
    float screenRadius = effectRadius / pixelViewSize;
    if (screenRadius < PIXEL_TOO_CLOSE_THRESHOLD) {
        return 1.0;
    }
    float visibility = clamp((SMALL_RADIUS_FADE_START - screenRadius) / SMALL_RADIUS_FADE_RANGE, 0.0, 1.0) * SMALL_RADIUS_FADE_STRENGTH;
    screenRadius = min(screenRadius, MaxScreenRadius);
    float minS = PIXEL_TOO_CLOSE_THRESHOLD / screenRadius;
    vec2 noise = spatialNoise(pixel);
    for (int slice = 0; slice < SliceCount; slice++) {
        float sliceK = (float(slice) + noise.x) / float(SliceCount);
        visibility += AlgorithmId == HELION_AO_ALGORITHM_VISIBILITY_BITMASK
            ? sliceVisibilityBitmask(pixel, center, viewVector, normal, sliceK, noise.y, slice, screenRadius, minS)
            : sliceVisibility(pixel, center, viewVector, normal, sliceK, noise.y, slice, screenRadius, minS);
    }
    visibility /= float(SliceCount);
    visibility = pow(clamp(visibility, 0.0, 1.0), FinalValuePower);
    return max(MIN_VISIBILITY, visibility);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float centerZ = viewDepthAt(pixel);
    if (helionIsSky(centerZ)) {
        fragColor = vec4(1.0 / HELION_OCCLUSION_TERM_SCALE, helionPackEdges(vec4(1.0)), 0.0, 1.0);
        return;
    }
    float leftZ = viewDepthAt(pixel + ivec2(-1, 0));
    float rightZ = viewDepthAt(pixel + ivec2(1, 0));
    float topZ = viewDepthAt(pixel + ivec2(0, -1));
    float bottomZ = viewDepthAt(pixel + ivec2(0, 1));
    vec4 edgesLRTB = calculateEdges(centerZ, leftZ, rightZ, topZ, bottomZ);

    vec2 pixelCenter = vec2(pixel) + 0.5;
    vec3 center = viewPositionAt(pixelCenter, centerZ);
    vec3 left = viewPositionAt(pixelCenter + vec2(-1.0, 0.0), leftZ);
    vec3 right = viewPositionAt(pixelCenter + vec2(1.0, 0.0), rightZ);
    vec3 top = viewPositionAt(pixelCenter + vec2(0.0, -1.0), topZ);
    vec3 bottom = viewPositionAt(pixelCenter + vec2(0.0, 1.0), bottomZ);

    vec3 viewVector = normalize(-center);
    vec3 normal = calculateNormal(edgesLRTB, center, left, right, top, bottom);
    if (dot(normal, viewVector) < 0.0) {
        normal = -normal;
    }

    float visibility = visibilityAt(pixel, center, viewVector, normal);
    fragColor = vec4(visibility / HELION_OCCLUSION_TERM_SCALE, helionPackEdges(edgesLRTB), 0.0, 1.0);
}
