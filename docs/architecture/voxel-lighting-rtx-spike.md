# Helion Architecture: Voxel Lighting RTX Spike

Part of the [Helion architecture](../ARCHITECTURE.md), split from [voxel-lighting.md](voxel-lighting.md).

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
- Result for direct block light: the 30 % criterion is missed and the image is the same, so the voxel grid walk stays the tracer for block light, also on RTX cards.
- Decision of the owner (2026-10-22): Helion is a graphics engine, not a shader pack, so RTX becomes an optional quality layer on cards that support it, with the voxel volume as the base for every card. The voxel volume cannot carry textured or distant reflections (one class byte per block, 128 blocks) or thin shapes (fences, leaves, mobs). Order: Phase 3 dynamic lights on the voxel volume; then the RTX foundation (bottom level structures per chunk section built from the real block meshes, including Aervium's compact vertices, on a worker thread); then RTX reflections together with the water update; then RTX shadows of thin shapes and entities; then bounced light (Phase 5) with RTX where available and the voxel volume elsewhere. The spike code stays as the start of the foundation; the foundation itself is in [ray-tracing.md](ray-tracing.md).
