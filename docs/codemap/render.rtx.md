# Helion Code Map: `com.aryston.helion.render.rtx`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.rtx`

The RTX feasibility spike: hardware ray queries on Minecraft's Vulkan device, compared with the voxel grid walk. Everything here runs only with the JVM property `-Dhelion.rtxSpike=true`; without it no class but `RtxSpike` is loaded, the device is created as before and the stages return in `isActive`. It is the only Helion code that uses renderpearl backend types (`VulkanDevice`, `VulkanCommandEncoder`, `VulkanGpuTextureView`) and raw Vulkan, an exception to invariant 2 that the planned RTX layer will turn into a proper backend bridge. Results are in `docs/architecture/voxel-lighting.md`.

#### RtxSpike
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxSpike.java`
- Role: The switches: `ENABLED` (`helion.rtxSpike`), `VIEW` (`helion.rtxSpike.view`, draws the mask over the picture) and the extra texture usage bit `TEXTURE_USAGE_STORAGE` that `VulkanConstMixin` turns into `VK_IMAGE_USAGE_STORAGE_BIT`.

#### RtxFeature
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxFeature.java`
- Role: The optional renderpearl `FeatureSet` "Helion ray query spike": `VK_KHR_acceleration_structure`, `VK_KHR_ray_query`, `VK_KHR_deferred_host_operations` with `bufferDeviceAddress`, `accelerationStructure` and `rayQuery`. Renderpearl enables it only when the device supports all of it, so other cards start as before.

#### RtxDevice
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxDevice.java`
- Role: The Vulkan device behind renderpearl when it enabled `RtxFeature`: memory types, scratch and storage buffer alignments, renderpearl's current command buffer and its full memory barrier.
- Depends on: `FrontendGpuDeviceAccessor`, `VulkanDeviceAccessor`, `VulkanCommandEncoderAccessor`.

#### RtxBuffer
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxBuffer.java`
- Role: A buffer with its own `vkAllocateMemory` and `VkMemoryAllocateFlagsInfo` device address flag (renderpearl's VMA has none), device local or mapped host coherent, with its device address.

#### RtxArea, RtxBlocks
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxArea.java`, `src/main/java/com/aryston/helion/render/rtx/RtxBlocks.java`
- Role: CPU copy of the voxel volume (a `VoxelBrickSink`, one byte array per ring slot) and the 3 x 3 chunk area around the camera over the full volume height. `collectExposed` lists the light-blocking blocks of the area with at least one face to a non-blocking block; a section that is not read yet counts as empty and raises the area revision once it arrives.

#### RtxAccelerationStructure, RtxScene
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxAccelerationStructure.java`, `src/main/java/com/aryston/helion/render/rtx/RtxScene.java`
- Role: One bottom level structure of unit box AABBs (opaque, prefer fast trace) and one top level structure with a single identity instance, with their storage and scratch buffers; the box buffer is also read by the shader for the exact box hit. Building is recorded into the command buffer, the bottom level before the top level.

#### RtxShaderSource, RtxShadowPipeline, RtxTraceInputs, RtxTraceMode
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxShaderSource.java`, `src/main/java/com/aryston/helion/render/rtx/RtxShadowPipeline.java`, `src/main/java/com/aryston/helion/render/rtx/RtxTraceInputs.java`, `src/main/java/com/aryston/helion/render/rtx/RtxTraceMode.java`
- Role: The raw compute pipeline of `shaders/rtx/shadow_mask.csh` (includes expanded in Java, compiled by shaderc for Vulkan 1.2 and SPIR-V 1.5), push descriptors (storage image, acceleration structure, two storage buffers, seven sampled textures), 128 bytes of push constants and its own nearest sampler. The mode rotates every frame between ray query only, grid walk only and both (compare), each timed under its own name.

#### RtxAgreement
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxAgreement.java`
- Role: Ring of eight mapped counter slots that the compare frames fill (traced pixels, blocked by ray query, blocked by the grid walk, disagreeing) and that are summed once their slot comes around again.

#### RtxShadowMask
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxShadowMask.java`
- Role: The half resolution RGBA8 mask, a renderpearl texture with the storage usage bit: red lit by ray query, green lit by the grid walk, blue traced.

#### RtxSpikeResources
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxSpikeResources.java`
- Role: Owned by `HelionRenderCore`. Opens the device, pipeline and counters on first use (logs once and stays off when the device has no ray queries), keeps the mask, rebuilds the scene when the area moved or one of its sections changed (at most every 10 frames) and logs the build and every 600 frames the GPU times and the agreement as `Helion RTX spike`.

#### RtxShadowStage
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxShadowStage.java`
- Role: Runs after `voxel_light` while voxel lighting has a volume: a frame graph pass without culling that records the structure builds (`rtx_blas`, `rtx_tlas`) when the scene is new and the compute dispatch (`rtx_ray_query`, `rtx_grid_walk`, `rtx_compare`) outside any render pass, with full barriers around them.
- Depends on: `RtxSpikeResources`, `VoxelLightingResources`.

#### RtxMaskPipelines, RtxMaskViewStage
- Path: `src/main/java/com/aryston/helion/render/rtx/RtxMaskPipelines.java`, `src/main/java/com/aryston/helion/render/rtx/RtxMaskViewStage.java`
- Role: With `helion.rtxSpike.view` the last stage draws the mask over the output (`rtx/mask_view.fsh`): white lit by both, black blocked by both, red blocked only by the ray query, green blocked only by the grid walk; untraced pixels keep the picture.
