# Helion Code Map: `com.aryston.helion.render.rtx`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.rtx`

Hardware ray tracing on Minecraft's Vulkan device: the ray tracing foundation (`-Dhelion.rayTracing=true`, see `docs/architecture/ray-tracing.md`) and the RTX feasibility spike (`-Dhelion.rtxSpike=true`, see `docs/architecture/voxel-lighting.md`). Without either property no class but the switches loads, the device is created as before and every stage returns in `isActive`. This is the only Helion code that uses renderpearl backend types (`VulkanDevice`, `VulkanCommandEncoder`, `VulkanGpuTextureView`) and raw Vulkan, the exception to invariant 2.

#### RayTracing, RtxSpike
- Path: `src/main/java/com/aryston/helion/render/rtx/RayTracing.java`, `src/main/java/com/aryston/helion/render/rtx/RtxSpike.java`
- Role: The switches: `RayTracing.ENABLED` (`helion.rayTracing`) and `VIEW` (`helion.rayTracing.view`, draws the traced sun shadow), `RtxSpike.ENABLED` (`helion.rtxSpike`) and `VIEW` (`helion.rtxSpike.view`), `RayTracing.deviceRequested()` when either asks for the ray query device features, and the extra texture usage bit `TEXTURE_USAGE_STORAGE` that `VulkanConstMixin` turns into `VK_IMAGE_USAGE_STORAGE_BIT`.

#### RtxFeature
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxFeature.java`
- Role: The optional renderpearl `FeatureSet` "Helion ray query spike": `VK_KHR_acceleration_structure`, `VK_KHR_ray_query`, `VK_KHR_deferred_host_operations` with `bufferDeviceAddress`, `accelerationStructure` and `rayQuery`. Renderpearl enables it only when the device supports all of it, so other cards start as before.

#### RtxDevice
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxDevice.java`
- Role: The Vulkan device behind renderpearl when it enabled `RtxFeature`: its VMA allocator, scratch and storage buffer alignments, renderpearl's current command buffer and its full memory barrier.
- Depends on: `FrontendGpuDeviceAccessor`, `VulkanDeviceAccessor`, `VulkanCommandEncoderAccessor`.

#### RtxBuffer
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxBuffer.java`
- Role: A buffer from renderpearl's VMA allocator (which `VulkanBackendMixin` creates with buffer device addresses when the ray tracing features are on) with the shader device address usage, device local or mapped host coherent, and its device address.

#### RtxGeometry, RtxAccelerationStructure
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxGeometry.java`, `src/main/java/com/aryston/helion/render/rtx/RtxAccelerationStructure.java`
- Role: Input of one acceleration structure (unit boxes, indexed `R32G32B32_SFLOAT` triangles or instances) and the structure itself: storage buffer, opaque geometry, prefer fast trace, with its own scratch buffer or built with a shared one, recorded into a command buffer; the top level is rebuilt from a new instance address every frame.

#### RtxSectionUpdate, RtxSectionPos, RtxSectionInbox
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxSectionUpdate.java`, `src/main/java/com/aryston/helion/render/rtx/RtxSectionPos.java`, `src/main/java/com/aryston/helion/render/rtx/RtxSectionInbox.java`
- Role: The thread-safe hand-over from the section compile workers: a compiled section's solid and cutout quad positions relative to the section (or a removal), section coordinates with Chebyshev distance, and the queue that accepts sections within `RADIUS` (4) plus one section of the last camera section and hands recompile requests back to the integration. Each update also carries three ints per vertex for reflections (`SURFACE_INTS_PER_VERTEX`): the atlas texture coordinate as two 16-bit fractions, the vertex color as RGBA8 and the block and sky light coordinates as two bytes.

#### RtxQuadIndices, RtxSharedScratch, RtxSectionBlas
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxQuadIndices.java`, `src/main/java/com/aryston/helion/render/rtx/RtxSharedScratch.java`, `src/main/java/com/aryston/helion/render/rtx/RtxSectionBlas.java`
- Role: The shared quad index buffer (two triangles per four vertices, grown to the largest section), the shared build scratch buffer (grown to the builds of one frame) and the bottom level structure of one section: its vertex buffer lives only until the build is recorded. A section also keeps its surface attributes in a device local storage buffer, uploaded through a mapped staging buffer that is copied while the bottom level is recorded and then released; `surfaceAddress()` is its device address.

#### RtxTopLevel, RtxScenePlan, RtxSectionScene, RtxSceneStage
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxTopLevel.java`, `src/main/java/com/aryston/helion/render/rtx/RtxScenePlan.java`, `src/main/java/com/aryston/helion/render/rtx/RtxSectionScene.java`, `src/main/java/com/aryston/helion/render/rtx/RtxSceneStage.java`
- Role: The section scene around the camera. Every frame `RtxSceneStage` (after the opaque geometry) drains the inbox, drops sections more than 2 sections outside the radius, builds up to 16 bottom levels nearest first, asks for at most 8 recompiles of sections in the radius that never arrived, and rebuilds the top level (up to 1024 instances in a ring of four mapped instance buffers, translated relative to the camera section, triangle culling off) in a frame graph pass without culling (`rt_bottom_levels`, `rt_top_level`). The custom index of every instance is its slot, and a ring of four mapped surface tables (`surfaceTable()`) holds the surface buffer address of the section in each slot, so a shader can read the texture coordinate, color and light of any hit.

#### RtxBinding, RtxComputePipeline, RtxShaderSource
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxBinding.java`, `src/main/java/com/aryston/helion/render/rtx/RtxComputePipeline.java`, `src/main/java/com/aryston/helion/render/rtx/RtxShaderSource.java`
- Role: A raw compute pipeline from a `.csh` resource (includes expanded in Java, compiled by shaderc for Vulkan 1.2 and SPIR-V 1.5) with push descriptors described by `RtxBinding` (storage image, acceleration structure, storage buffer, sampled image with the pipeline's nearest sampler) and push constants.

#### RtxImage, RtxMaskPipelines, RtxMaskViewStage
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxImage.java`, `src/main/java/com/aryston/helion/render/rtx/RtxMaskPipelines.java`, `src/main/java/com/aryston/helion/render/rtx/RtxMaskViewStage.java`
- Role: An RGBA8 renderpearl texture with the storage usage bit (half resolution for the masks, full resolution for the reflections), and the last stage that draws a mask over the output: the spike view (`rtx/mask_view.fsh`) or the traced sun shadow (`rtx/sun_shadow_view.fsh`, blocked pixels dark blue).

#### RayTracingResources, RtxSunShadowStage
- Path: `src/main/java/com/aryston/helion/render/rtx/RayTracingResources.java`, `src/main/java/com/aryston/helion/render/rtx/RtxSunShadowStage.java`
- Role: Owned by `HelionRenderCore`. Opens the device, the section scene and the sun shadow pipeline on first use (logs once and stays off when the device has no ray queries) and logs the scene size, CPU and GPU times when the scene first completes and every 600 frames as `Helion ray tracing`. The validation pass `rt_sun_shadow` (after the shadow stage, while it has a light) traces every half resolution pixel within the scene toward the sun or moon with `rtx/sun_shadow.csh` and writes lit or blocked into the mask. It also creates the reflection pipeline (`rtx/reflection.csh`; when it does not compile only reflections stay off), the full resolution reflection image and its push constants.

#### RtxReflectionStage
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxReflectionStage.java`
- Role: Pass `rt_reflection` after the water surface: for every water pixel inside the section scene, reflects the view ray at the wave normal and traces it up to 96 blocks with `rtx/reflection.csh`. A hit is shaded with the block atlas texel at the interpolated texture coordinate, the vertex color and the vanilla lightmap at the interpolated light coordinates and written with alpha 1; a miss writes alpha 0 so the water composite uses the sky. Publishes the image in `WaterResults`.
- Depends on: `RayTracingResources`, `RtxSectionScene`, `RtxComputePipeline`, `RtxImage`, `TerrainSource`, `WaterResults`.

#### RtxArea, RtxBlocks, RtxScene, RtxAgreement, RtxTraceMode, RtxSpikeResources, RtxShadowStage
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxArea.java`, `src/main/java/com/aryston/helion/render/rtx/RtxBlocks.java`, `src/main/java/com/aryston/helion/render/rtx/RtxScene.java`, `src/main/java/com/aryston/helion/render/rtx/RtxAgreement.java`, `src/main/java/com/aryston/helion/render/rtx/RtxTraceMode.java`, `src/main/java/com/aryston/helion/render/rtx/RtxSpikeResources.java`, `src/main/java/com/aryston/helion/render/rtx/RtxShadowStage.java`
- Role: The RTX spike: a CPU copy of the voxel volume, unit boxes of the exposed light-blocking blocks of the 3 x 3 chunks around the camera in one bottom and one top level, and the `rtx/shadow_mask.csh` pass after `voxel_light` that traces the brightest light of each pixel with ray queries and with the voxel grid walk in rotating modes (`rtx_ray_query`, `rtx_grid_walk`, `rtx_compare`) and counts their agreement.
- Depends on: `RtxSpikeResources`, `VoxelLightingResources`.
