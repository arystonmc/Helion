# Helion Architecture: Voxel Lighting

Part of the [Helion architecture](../ARCHITECTURE.md).

## Voxel Lighting

In development, off by default and in every preset (`voxelLighting.mode`: `OFF` or `SHADER`; a ray traced mode will be a third value). Goal: light that bounces between blocks, traced against a coarse block volume around the camera instead of the screen. Phase 0 builds the volume and a debug view; nothing in the picture changes yet. With `OFF` no texture exists, nothing is read and the mesh change hook returns after one volatile read.

## Renderpearl API Findings (Minecraft 26.3)

Checked in the extracted `minecraft-patched-26.3.0.51-beta-sources.jar` (renderpearl ships inside it).

| Question | Answer |
|---|---|
| Textures | `GpuDevice.createTexture(label, usage, GpuFormat, width, height, depthOrLayers, mipLevels)`. `VulkanGpuTexture` always creates `VK_IMAGE_TYPE_2D` and passes `depthOrLayers` as array layers, so there are no 3D textures. |
| Formats | `GpuFormat` has `R8_UNORM`, `RG8_UNORM`, `R8_UINT`, `R16_UINT`, `R32_UINT` (and the SINT, SNORM and wider variants), `R16_FLOAT` to `RGBA32_FLOAT`, `RGBA16_FLOAT`, `RGB10A2_UNORM`, `RG11B10_FLOAT`. |
| Sub-rectangle upload | `CommandEncoder.writeToTexture(texture, ByteBuffer, mipLevel, depthOrLayer, destX, destY, width, height)` writes any rectangle at an offset; it throws inside an open render pass (lesson L-085). `copyBufferToTexture` and `copyTextureToTexture` also take offsets. |
| Texture views | `createTextureView(texture)` and `createTextureView(texture, baseMip, mipCount)` only. `VulkanGpuTextureView` makes a 2D view of layer 0 (a cube view for cube maps), so a single array layer cannot be bound. |
| Uniform types | `UniformType` is `COMBINED_IMAGE_SAMPLER`, `UNIFORM_BUFFER`, `TEXEL_BUFFER` (a uniform texel buffer with a `GpuFormat`, buffer usage `USAGE_UNIFORM_TEXEL_BUFFER`). No storage buffers or storage images; the enum is marked non-exhaustive for them. `DeviceLimits` exposes no uniform buffer or texel buffer size limit; Vulkan guarantees 16 KiB per uniform buffer range and 65536 texel buffer elements. |
| Shader stages | Vertex and fragment only (`ShaderType`), compiled by shaderc for the Vulkan 1.2 environment, so SPIR-V 1.5 and `GL_EXT_ray_query` would compile. |
| Device extensions | `FeatureSet(name, Set<String> extensions, Set<VulkanFeature> features[, condition])`: the first set is device extension names. `VulkanFeatureSets.optionalFeatureSets()` returns a mutable set that is enabled per device when supported (Aervium adds a set the same way); `VulkanPNextStruct` takes any LWJGL feature struct, so `VK_KHR_ray_query`, `VK_KHR_acceleration_structure`, `VK_KHR_deferred_host_operations` with `rayQuery`, `accelerationStructure` and the Vulkan 1.2 `bufferDeviceAddress` feature can be requested as optional. |
| Memory | `VulkanBackend.createVma` sets no allocator flags, so no `VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT`, and `VulkanConst.bufferUsageToVk` never adds `SHADER_DEVICE_ADDRESS`. Acceleration structure buffers need their own allocation with `VkMemoryAllocateFlagsInfo` device address, or a mixin that adds the flag only when the optional ray tracing set was enabled. |
| Section compile | `SectionRenderDispatcher.RenderSection.CompileTask.doTask` calls `SectionCompiler.compile(sectionPos, RenderSectionRegion, …, additionalRenderers)` on worker threads; it iterates `region.getBlockState(pos)` for the 16 x 16 x 16 blocks. NeoForge's `AddSectionGeometryEvent` renderers receive the same `RenderSectionRegion` on the worker, an API hook that sees every block state of the section. Aervium wraps calls inside `CompileTask.doTask` and the layer format in `SectionCompiler.getOrBeginLayer` but never replaces `compile`, so both paths stay. |

## Data Layout

- Volume: 128 x 128 x 128 blocks, 8 x 8 x 8 chunk sections, from 4 sections below to 3 above the camera's section on every axis.
- Byte per block (`VoxelClass`): bits 0 to 1 occupancy (`EMPTY`, `OPAQUE` for solid render blocks, `PARTIAL` for every other model or fluid, `TRANSLUCENT` for water and blocks whose model has translucent material), bits 4 to 7 the light emission.
- Brick per section: 64 x 64 texels, the 16 horizontal slices of 16 x 16 laid out 4 x 4 (slice `y` at `(y % 4 * 16, y / 4 * 16)`, `x` right, `z` down).
- Atlas: one 2048 x 1024 `R8_UNORM` texture (2 MiB), 32 x 16 bricks. A section goes to slot `(x & 7) + 8 * ((y & 7) + 8 * (z & 7))` of its section coordinates (ring addressing), so moving one section only rebuilds the slab of 64 sections that entered. `VoxelVolumeLayout` and `helion_voxel.glsl` hold the same math.

## Update

1. `RenderSectionMixin` reports every replaced or reset section mesh to `VoxelSectionChanges` (only while the mode is on).
2. `VanillaFrameDriver` calls `VoxelLightingResources.update` once per frame before the stages are built, so every upload is recorded before any render pass opens.
3. `VoxelVolume` marks changed sections pending, recenters the window on the camera's section, then rebuilds pending sections nearest first until 1 ms of CPU time has passed: `VanillaVoxelSource` reads the level chunk section on the render thread (missing chunks and all-air sections become empty bricks and are rebuilt when their mesh arrives), and one `writeToTexture` uploads the brick.
4. A slot whose section left the window shows the old section until it is rebuilt; with the 1 ms budget this lasts a few frames. Phase 1 should add a per slot ready flag before tracing against it.

## Voxel Slice View

`geometryBuffer.view = VOXEL_SLICE` (Advanced page, needs the geometry buffer) runs `VoxelSliceStage` as the last stage: a square of 90 % of the screen height shows the volume at the camera's block height from above, north up, one block per cell: dark empty, grey opaque, green partial, blue translucent, yellow light sources, purple section borders, red the camera column. Its GPU time is listed as `voxel_slice`.

## Measured (RTX 4050 Laptop, 1080p, `aervium-benchmark`, 2026-10-22)

One run with the Experimental-like settings of the owner and debug mode on.

| Case | Sections | Frames | CPU total | Wall time | Worst frame |
|---|---|---|---|---|---|
| First fill after joining, camera at y 365 (mostly empty sections) | 521 | 13 | 13.2 ms | 0.81 s | 1.46 ms |
| Teleport to the forest floor (y 66), full refill | 512 | 45 | 49.2 ms | 0.36 s | 1.64 ms |
| Teleport back up | 512 | 3 | 2.2 ms | 14 ms | 1.00 ms |

- About 0.1 ms of CPU per section with blocks, a few microseconds per all-air section. A frame stops building once it passed the 1 ms budget, so the worst frame is the budget plus one section.
- With nothing pending the update costs about 0.005 ms per frame.
- The slice pass takes 0.03 ms of GPU time. The log showed no `Couldn't compile pipeline` and no Helion warning.
