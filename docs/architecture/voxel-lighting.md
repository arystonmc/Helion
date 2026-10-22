# Helion Architecture: Voxel Lighting

Part of the [Helion architecture](../ARCHITECTURE.md).

## Voxel Lighting

Experimental, off by default (`voxelLighting.mode`: `OFF` or `SHADER`; the RTX spike below found no reason for a ray traced mode; `voxelLighting.quality` Low, Medium, High). `SHADER` at Medium is in the Experimental preset, every other preset sets `OFF`. Needs HDR lighting. Block light is traced through a block volume around the camera instead of taken from the vanilla light level, so it takes the color of its source and solid blocks cast shadows. Later phases add held and entity lights, bounced light and reflections on the same volume. With `OFF` no texture exists, nothing is read and the change hooks return after one volatile read.

## Renderpearl API Findings (Minecraft 26.3)

Checked in the extracted `minecraft-patched-26.3.0.51-beta-sources.jar` (renderpearl ships inside it).

| Question | Answer |
|---|---|
| Textures | `GpuDevice.createTexture(label, usage, GpuFormat, width, height, depthOrLayers, mipLevels)`. `VulkanGpuTexture` always creates `VK_IMAGE_TYPE_2D` and passes `depthOrLayers` as array layers, so there are no 3D textures. |
| Formats | `GpuFormat` has `R8_UNORM`, `RG8_UNORM`, `R8_UINT`, `R16_UINT`, `R32_UINT` (and the SINT, SNORM and wider variants), `R16_FLOAT` to `RGBA32_FLOAT`, `RGBA16_FLOAT`, `RGB10A2_UNORM`, `RG11B10_FLOAT`. |
| Sub-rectangle upload | `CommandEncoder.writeToTexture(texture, ByteBuffer, mipLevel, depthOrLayer, destX, destY, width, height)` writes any rectangle at an offset; it throws inside an open render pass (lesson L-085). `copyBufferToTexture` and `copyTextureToTexture` also take offsets. |
| Texture views | `createTextureView(texture)` and `createTextureView(texture, baseMip, mipCount)` only. `VulkanGpuTextureView` makes a 2D view of layer 0 (a cube view for cube maps), so a single array layer cannot be bound. |
| Uniform types | `UniformType` is `COMBINED_IMAGE_SAMPLER`, `UNIFORM_BUFFER`, `TEXEL_BUFFER` (a uniform texel buffer with a `GpuFormat`, buffer usage `USAGE_UNIFORM_TEXEL_BUFFER`). No storage buffers or storage images; the enum is marked non-exhaustive for them. `DeviceLimits` exposes no uniform buffer or texel buffer size limit; Vulkan guarantees 16 KiB per uniform buffer range and 65536 texel buffer elements. |
| Shader stages | Vertex and fragment only (`ShaderType`), compiled by shaderc for the Vulkan 1.2 environment, so SPIR-V 1.5 and `GL_EXT_ray_query` would compile. Raw Vulkan compute and ray query pipelines can still be recorded into renderpearl's command buffer between render passes through backend accessors (RTX spike). |
| Device extensions | `FeatureSet(name, Set<String> extensions, Set<VulkanFeature> features[, condition])`: the first set is device extension names. `VulkanFeatureSets.optionalFeatureSets()` returns a mutable set that is enabled per device when supported (Aervium adds a set the same way); `VulkanPNextStruct` takes any LWJGL feature struct, so `VK_KHR_ray_query`, `VK_KHR_acceleration_structure`, `VK_KHR_deferred_host_operations` with `rayQuery`, `accelerationStructure` and the Vulkan 1.2 `bufferDeviceAddress` feature can be requested as optional. |
| Memory | `VulkanBackend.createVma` sets no allocator flags, so no `VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT`, and `VulkanConst.bufferUsageToVk` never adds `SHADER_DEVICE_ADDRESS`. Acceleration structure buffers need their own allocation with `VkMemoryAllocateFlagsInfo` device address, or a mixin that adds the flag only when the optional ray tracing set was enabled. |
| Section compile | `SectionRenderDispatcher.RenderSection.CompileTask.doTask` calls `SectionCompiler.compile(sectionPos, RenderSectionRegion, …, additionalRenderers)` on worker threads; it iterates `region.getBlockState(pos)` for the 16 x 16 x 16 blocks. NeoForge's `AddSectionGeometryEvent` renderers receive the same `RenderSectionRegion` on the worker, an API hook that sees every block state of the section. Aervium wraps calls inside `CompileTask.doTask` and the layer format in `SectionCompiler.getOrBeginLayer` but never replaces `compile`, so both paths stay. |

## Data Layout

- Volume: 128 x 128 x 128 blocks, 8 x 8 x 8 chunk sections, from 4 sections below to 3 above the camera's section on every axis.
- Byte per block (`VoxelClass`): bits 0 to 1 occupancy (`EMPTY`, `OPAQUE` for solid render blocks, `PARTIAL` for every other model or fluid, `TRANSLUCENT` for water and blocks whose model has translucent material), bits 4 to 7 the light emission.
- Brick per section: 64 x 64 texels, the 16 horizontal slices of 16 x 16 laid out 4 x 4 (slice `y` at `(y % 4 * 16, y / 4 * 16)`, `x` right, `z` down).
- Atlas: one 2048 x 1024 `R8_UNORM` texture (2 MiB), 32 x 16 bricks.
- Occupancy: `OPAQUE` means the block stops light (solid render or full light dampening, as vanilla light does); plants, slabs, glass, leaves and fluids are `PARTIAL` or `TRANSLUCENT` and let light through for now.
- Coarse level: one byte per 4 x 4 x 4 blocks, 255 when a light blocker is inside, 8 x 8 per section in a 256 x 128 atlas with the same slots. Shadow rays read it first and fetch the block atlas only in blocked cells.
- Ready slots: a 32 x 16 table, 255 when a slot holds the section the window expects. A ray that crosses an unknown slot gives up and the pixel keeps the vanilla light.
- Lights: every block with light emission found while reading a section, with its color from `assets/helion/voxel_light/colors.json` (block id to linear RGB, resource packs can replace it; unlisted blocks use their map color, else warm white), normalized to luminance 1 so a light keeps the vanilla brightness. The nearest 1024 go into a 1024 x 2 RGBA32_FLOAT list, and a 16 x 16 x 16 grid of 8-block cells lists up to 16 lights that reach each cell (256 x 256 R16_UNORM). A section goes to slot `(x & 7) + 8 * ((y & 7) + 8 * (z & 7))` of its section coordinates (ring addressing), so moving one section only rebuilds the slab of 64 sections that entered. `VoxelVolumeLayout` and `helion_voxel.glsl` hold the same math.

## Update

1. `LevelExtractorMixin` reports every changed block and `RenderSectionMixin` every replaced or reset section mesh to `VoxelSectionChanges` (only while the mode is on), so a broken or placed block is read again in the next frame.
2. `VanillaFrameDriver` calls `VoxelLightingResources.update` once per frame before the stages are built, so every upload is recorded before any render pass opens.
3. `VoxelVolume` marks changed sections pending, recenters the window on the camera's section, then rebuilds pending sections nearest first until 1 ms of CPU time has passed: `VanillaVoxelSource` reads the level chunk section on the render thread (missing chunks and all-air sections become empty bricks and are rebuilt when their mesh arrives), and one `writeToTexture` uploads the brick.
4. A slot whose section left the window is marked unknown in the ready table until it is rebuilt.
5. The light grid is rebuilt when the window moved or a rebuilt section had lights, at most every 10 frames while sections are still pending, once more when the volume is complete.
6. Section reading stays on the render thread: once the volume is full it costs about 0.01 ms per frame, a changed section about 0.1 ms. Moving it to a worker (NeoForge `AddSectionGeometryEvent` sees the same block states) pays off only if walking shows in frame times.

## Direct Light

1. `voxel_light` runs right after the shadow stage, at half resolution (Low, Medium) or full (High). Per terrain pixel it takes the point half a block in front of the surface, the block vanilla reads the light of.
2. Every light of its grid cell gives `level - taxicab distance`, the vanilla falloff of one level per block, and the vanilla level brightness curve (`helion_light_level.glsl`) times the light color.
3. The brightest is traced first (3D grid walk, coarse level first) toward the face of the light block that faces the pixel and has no light-blocking neighbor, so a lava pool in the floor lights the floor. If it is blocked the next brightest is traced, up to 1 (Low), 2 (Medium) or 4 (High) lights; untraced lights count as visible. Like vanilla, the brightest visible light sets the level; the color is the blend of all lights by their share.
4. It writes the unshadowed colored light and the visible share (RGBA16_FLOAT) and a confidence (R8): 0 for unknown slots, fading to 0 over the last 16 blocks before the volume edge.
5. `deferred_shading` with `HELION_VOXEL_LIGHT` samples both bilinearly and replaces the vanilla block term by `max(traced, 30 % of vanilla)` mixed with vanilla by the confidence. The 30 % floor stands in for light around corners until bounced light exists. The shadow is applied after the vanilla brightness curve and the display clamp, as the ratio of the displayed light with and without the blocked share (lessons L-030, L-146), so it hardly matters at noon.
6. Not yet: soft shadow edges, temporal and edge-aware filtering (the walk is deterministic, so there is no noise to filter yet), tinted glass, held and entity lights (they keep only the vanilla floor of their light), light beyond the volume.

## Voxel Slice View

`geometryBuffer.view = VOXEL_SLICE` (Advanced page, needs the geometry buffer) runs `VoxelSliceStage` as the last stage: a square of 90 % of the screen height shows the volume at the camera's block height from above, north up, one block per cell: dark empty, grey opaque, green partial, blue translucent, yellow light sources, purple section borders, red the camera column. Its GPU time is listed as `voxel_slice`.

## RTX Feasibility Spike (2026-10-22)

Question: can Helion run hardware ray queries on Minecraft's Vulkan device, and are they at least 30 % faster than the grid walk, or sharper or longer reaching at the same cost? Code in `render.rtx` (see its code map), only with `-Dhelion.rtxSpike=true` (`helion.rtxSpike.view=true` draws the mask); without the property no class of it loads and the device is created as before.

How it works:

1. `VulkanFeatureSetsMixin` adds an optional renderpearl feature set with `VK_KHR_acceleration_structure`, `VK_KHR_ray_query`, `VK_KHR_deferred_host_operations`, `bufferDeviceAddress`, `accelerationStructure` and `rayQuery`. Renderpearl enables it only when the device supports all of it; otherwise the spike logs once and stays off.
2. Memory: every acceleration structure, scratch, box, instance and counter buffer has its own `vkAllocateMemory` with `VkMemoryAllocateFlagsInfo` `DEVICE_ADDRESS`, released through `GpuResources` (fenced). Renderpearl's VMA is unchanged.
3. Geometry: `VoxelVolume` hands every read brick to a `VoxelBrickSink`; the spike keeps a CPU copy and lists the light-blocking blocks of the 3 x 3 chunks around the camera (full 128-block volume height) that have a face to a non-blocking block, as unit AABBs in one bottom level structure (opaque, prefer fast trace) with one identity instance in one top level structure. It rebuilds when the area moves or one of its sections changes, at most every 10 frames.
4. Trace: a raw Vulkan compute pipeline (`rtx/shadow_mask.csh`, compiled by shaderc for Vulkan 1.2 and SPIR-V 1.5, push descriptors, 128 bytes of push constants) is recorded into renderpearl's command buffer inside a frame graph pass without culling, outside any render pass, with renderpearl's full memory barrier before and after the builds and the dispatch. Per half resolution pixel it picks the brightest light of the grid cell exactly like `voxel_light` and its open light face, then traces it with `rayQueryEXT` (AABB candidates confirmed with an exact box test against the box buffer) or with the same grid walk as `voxel_light` (shared through `helion_voxel_trace.glsl`). The mode rotates per frame: ray query only, grid walk only, both; each is timed on its own. Pixels whose surface or light lies outside the 3 x 3 area are skipped by both tracers.
5. The mask is a renderpearl RGBA8 texture with the storage usage added by `VulkanConstMixin` (renderpearl keeps every image in `GENERAL` layout, so no layout change is needed): red lit by ray query, green lit by the grid walk.

Measured on the RTX 4050 Laptop, 1080p, one visual test run (`night_showcase` and `day_showcase`, variants `voxel_light` and `voxel_light_low`, driver 616.92):

| Value | Day | Night |
|---|---|---|
| Ray query trace (`rtx_ray_query`) | 0.090 to 0.101 ms | 0.107 to 0.109 ms |
| Grid walk trace, same compute pass (`rtx_grid_walk`) | 0.094 to 0.106 ms | 0.113 to 0.115 ms |
| Both in one pass (`rtx_compare`) | 0.105 to 0.119 ms | 0.128 to 0.130 ms |
| `voxel_light` fragment pass, Medium (2 lights, whole volume) | 0.152 to 0.156 ms | 0.170 to 0.171 ms |
| Bottom level build (`rtx_blas`, 18404 boxes) | 0.20 ms GPU | 0.23 ms GPU |
| Top level build (`rtx_tlas`) | 0.06 to 0.07 ms GPU | 0.07 ms GPU |
| Box gathering on the render thread | 5 to 8 ms CPU | 7 ms CPU |
| Buffer and structure creation | 2 to 7 ms CPU (25 ms the first time) | 2 to 4 ms CPU |
| Memory of one scene | 2.4 MiB | 2.4 MiB |
| Mask pixels where the tracers disagree | 0 to 4 of 518400 | 4 to 6 of 518400 |

- Yes, it runs: the device enabled the feature set, the pipeline compiled and every frame traced without a validation or Helion error, and a card without ray queries keeps starting as before because the set is optional.
- Ray query is only about 5 % faster than the grid walk in the same pass, because shadow rays of block light are short (at most 15 blocks, mostly through the coarse level) and the pass time is mostly the shared work: depth, normal, light grid and light face lookups. Both give the same hard shadows (the few differing pixels are lava and flame pixels at box edges).
- The scene costs a rebuild whenever a block in the area changes: 0.27 ms of GPU and 7 to 15 ms of render thread CPU in this spike; an incremental build would need per-section bottom level structures and a worker thread.
- Decision: the 30 % criterion is missed and the image is the same, so RTX is dropped for direct block light and Phase 7 (RTX layer) is not recommended. Hardware ray queries may pay off later only for long rays (bounced light, reflections, sky visibility over hundreds of blocks), where the grid walk cost grows with distance; Phases 5 and 6 should measure their own grid walk first and reuse this spike only if the walk is too slow.

## Measured, Phase 2 (RTX 4050 Laptop, 1080p, visual test world, 2026-10-22)

One visual test run (`helion.visualTest.scenes=night_showcase+day_showcase`, `helion.visualTest.variants=lighting+voxel_light+voxel_light_low`); the test waits until the volume is full before a voxel shot.

| Value | Medium | Low |
|---|---|---|
| `voxel_light` GPU | 0.16 ms | 0.15 ms |
| `deferred_shading` GPU | 0.19 ms (0.12 ms without voxel light) | 0.19 ms |
| Open floor at night, luminance against lighting without voxel light | 89 to 95 % | 89 to 95 % |
| Floor behind the pillar at night | 86 % (shadow) | 89 % |
| Noon scene | identical | identical |

- 516 lights in the volume (natural world around the test area). Light grid builds took 1.8 to 3.0 ms, the first one 6.8 ms; with the throttle they run once per window move or light change.
- Full fill: 512 sections in 62 frames, 100 ms CPU, worst frame 8.6 ms (the first frame, with cold block class caches and the first grid build). Once full: 0.01 ms per frame.

## Measured, Phase 0 (RTX 4050 Laptop, 1080p, `aervium-benchmark`, 2026-10-22)

One run with the Experimental-like settings of the owner and debug mode on.

| Case | Sections | Frames | CPU total | Wall time | Worst frame |
|---|---|---|---|---|---|
| First fill after joining, camera at y 365 (mostly empty sections) | 521 | 13 | 13.2 ms | 0.81 s | 1.46 ms |
| Teleport to the forest floor (y 66), full refill | 512 | 45 | 49.2 ms | 0.36 s | 1.64 ms |
| Teleport back up | 512 | 3 | 2.2 ms | 14 ms | 1.00 ms |

- About 0.1 ms of CPU per section with blocks, a few microseconds per all-air section. A frame stops building once it passed the 1 ms budget, so the worst frame is the budget plus one section.
- With nothing pending the update costs about 0.005 ms per frame.
- The slice pass takes 0.03 ms of GPU time. The log showed no `Couldn't compile pipeline` and no Helion warning.
