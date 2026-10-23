#version 330
#extension GL_ARB_separate_shader_objects : require

#include <helion:helion_geometry.glsl>
#include <helion:helion_voxel.glsl>
#include <helion:helion_voxel_probe.glsl>

layout(std140) uniform HelionVoxelLight {
    mat4 InverseViewProjection;
    vec4 CameraToVolume;
    ivec4 VolumeMin;
    ivec4 Trace;
};

uniform sampler2D DepthSampler;
uniform sampler2D GeometryNormalSampler;
uniform sampler2D VoxelSampler;
uniform sampler2D VoxelCoarseSampler;
uniform sampler2D VoxelReadySampler;
uniform sampler2D VoxelLightGridSampler;
uniform sampler2D ProbeRedSampler;
uniform sampler2D ProbeGreenSampler;
uniform sampler2D ProbeBlueSampler;
uniform sampler2D ProbePlacementSampler;

#include <helion:helion_voxel_trace.glsl>
#include <helion:helion_voxel_probe_gather.glsl>

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 bounce;

const float HELION_BOUNCE_GAIN = 4.2;
const float HELION_VANILLA_FLOOR_WITH_BOUNCE = 0.0;
const float HELION_VANILLA_FLOOR_WITHOUT_BOUNCE = 1.0;

void main() {
    bounce = vec4(0.0, 0.0, 0.0, HELION_VANILLA_FLOOR_WITHOUT_BOUNCE);
    if (Trace.z <= 0) {
        return;
    }
    ivec2 fullSize = textureSize(DepthSampler, 0);
    ivec2 pixel = min(ivec2(texCoord * vec2(fullSize)), fullSize - 1);
    float depth = texelFetch(DepthSampler, pixel, 0).r;
    vec4 encodedNormal = texelFetch(GeometryNormalSampler, pixel, 0);
    if (depth <= HELION_SKY_DEPTH || encodedNormal.a < HELION_GEOMETRY_PRESENT_THRESHOLD) {
        return;
    }
    vec3 normal = normalize(encodedNormal.rgb * 2.0 - 1.0);
    vec3 surface = helionVoxelSurface(texCoord, depth, normal);
    ivec3 block = ivec3(floor(surface));
    if (helionEdgeDistance(surface, CameraToVolume.w) <= 0.0 || !helionVoxelSlotReady(VoxelReadySampler, VolumeMin.xyz + block)) {
        return;
    }
    vec3 gathered = helionGatherProbes(surface, normal, ivec3(0), ProbeRedSampler, ProbeGreenSampler, ProbeBlueSampler, ProbePlacementSampler);
    bounce = vec4(gathered * HELION_BOUNCE_GAIN, HELION_VANILLA_FLOOR_WITH_BOUNCE);
}