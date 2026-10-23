#ifndef HELION_RT_ENTITY_GLSL
#define HELION_RT_ENTITY_GLSL

layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer HelionEntityWords {
    uint words[];
};

struct HelionEntityPoint {
    vec4 color;
    vec2 light;
};

const uint HELION_RT_ENTITY_INDEX = 0xFFFFFFu;
const uint HELION_RT_ENTITY_HEADER = 1u;
const uint HELION_RT_ENTITY_RECORD = 3u;
const uint HELION_RT_ENTITY_NO_TEXTURE = 0xFFFFFFFFu;
const uint HELION_RT_ENTITY_OFFSET_MASK = 0xFFu;
const uint HELION_RT_ENTITY_UV_SHIFT = 8u;
const uint HELION_RT_ENTITY_LIGHT_SHIFT = 16u;
const uint HELION_RT_ENTITY_FLAG_SHIFT = 24u;
const uint HELION_RT_ENTITY_SHORT_INDICES = 1u;
const uint HELION_RT_ENTITY_WORD_BYTES = 4u;
const uint HELION_RT_ENTITY_SHORT_BYTES = 2u;
const uint HELION_RT_ENTITY_BITS_PER_BYTE = 8u;
const uint HELION_RT_ENTITY_SHORT_MASK = 0xFFFFu;
const int HELION_RT_ENTITY_SHORT_BITS = 16;
const float HELION_RT_ENTITY_CUTOUT_ALPHA = 0.1;
const vec2 HELION_RT_ENTITY_FULL_LIGHT = vec2(240.0);
const HelionEntityPoint HELION_RT_ENTITY_NOTHING = HelionEntityPoint(vec4(0.0), vec2(0.0));

bool helionIsEntity(uint customIndex) {
    return customIndex == HELION_RT_ENTITY_INDEX;
}

uint helionEntityWord(uvec2 address, uint byteOffset) {
    return HelionEntityWords(address).words[byteOffset / HELION_RT_ENTITY_WORD_BYTES];
}

uint helionEntityIndex(uvec2 address, uint byteOffset, bool shortIndices) {
    uint word = helionEntityWord(address, byteOffset);
    if (!shortIndices) {
        return word;
    }
    return (word >> ((byteOffset % HELION_RT_ENTITY_WORD_BYTES) * HELION_RT_ENTITY_BITS_PER_BYTE)) & HELION_RT_ENTITY_SHORT_MASK;
}

vec2 helionEntityLight(uint word) {
    return vec2(int(word << uint(HELION_RT_ENTITY_SHORT_BITS)) >> HELION_RT_ENTITY_SHORT_BITS, int(word) >> HELION_RT_ENTITY_SHORT_BITS);
}

HelionEntityPoint helionEntityPoint(uint geometry, uint primitive, vec2 barycentrics) {
    if (geometry >= Entities[0].x) {
        return HELION_RT_ENTITY_NOTHING;
    }
    uint record = HELION_RT_ENTITY_HEADER + geometry * HELION_RT_ENTITY_RECORD;
    uvec4 addresses = Entities[record];
    uvec4 vertexLayout = Entities[record + 1u];
    uint indexOffset = Entities[record + 2u].x;
    uint slot = vertexLayout.w;
    if (slot == HELION_RT_ENTITY_NO_TEXTURE) {
        return HELION_RT_ENTITY_NOTHING;
    }
    uint colorOffset = vertexLayout.z & HELION_RT_ENTITY_OFFSET_MASK;
    uint uvOffset = (vertexLayout.z >> HELION_RT_ENTITY_UV_SHIFT) & HELION_RT_ENTITY_OFFSET_MASK;
    uint lightOffset = (vertexLayout.z >> HELION_RT_ENTITY_LIGHT_SHIFT) & HELION_RT_ENTITY_OFFSET_MASK;
    bool shortIndices = ((vertexLayout.z >> HELION_RT_ENTITY_FLAG_SHIFT) & HELION_RT_ENTITY_SHORT_INDICES) != 0u;
    uint indexBytes = shortIndices ? HELION_RT_ENTITY_SHORT_BYTES : HELION_RT_ENTITY_WORD_BYTES;
    vec3 weights = vec3(1.0 - barycentrics.x - barycentrics.y, barycentrics.x, barycentrics.y);
    vec2 uv = vec2(0.0);
    vec4 tint = vec4(0.0);
    vec2 light = vec2(0.0);
    for (uint corner = 0u; corner < 3u; corner++) {
        uint index = helionEntityIndex(addresses.zw, indexOffset + (primitive * 3u + corner) * indexBytes, shortIndices);
        uint vertexByte = (index + vertexLayout.x) * vertexLayout.y;
        tint += unpackUnorm4x8(helionEntityWord(addresses.xy, vertexByte + colorOffset)) * weights[corner];
        uv += vec2(
            uintBitsToFloat(helionEntityWord(addresses.xy, vertexByte + uvOffset)),
            uintBitsToFloat(helionEntityWord(addresses.xy, vertexByte + uvOffset + HELION_RT_ENTITY_WORD_BYTES))
        ) * weights[corner];
        light += (lightOffset == HELION_RT_ENTITY_OFFSET_MASK
            ? HELION_RT_ENTITY_FULL_LIGHT
            : helionEntityLight(helionEntityWord(addresses.xy, vertexByte + lightOffset))) * weights[corner];
    }
    return HelionEntityPoint(textureLod(EntityTextures[nonuniformEXT(slot)], uv, 0.0) * tint, light);
}

bool helionEntityCandidateHit(rayQueryEXT query) {
    HelionEntityPoint point = helionEntityPoint(
        uint(rayQueryGetIntersectionGeometryIndexEXT(query, false)),
        uint(rayQueryGetIntersectionPrimitiveIndexEXT(query, false)),
        rayQueryGetIntersectionBarycentricsEXT(query, false)
    );
    return point.color.a >= HELION_RT_ENTITY_CUTOUT_ALPHA;
}

HelionEntityPoint helionEntityCommittedPoint(rayQueryEXT query) {
    return helionEntityPoint(
        uint(rayQueryGetIntersectionGeometryIndexEXT(query, true)),
        uint(rayQueryGetIntersectionPrimitiveIndexEXT(query, true)),
        rayQueryGetIntersectionBarycentricsEXT(query, true)
    );
}

#endif
