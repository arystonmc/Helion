# Helion Code Map: `com.aryston.helion.mixin`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.mixin`

#### LevelExtractorMixin
- Path: `src/main/java/com/aryston/helion/mixin/LevelExtractorMixin.java`
- Role: Reports the section of every changed block to `VoxelSectionChanges` at the head of `LevelExtractor.blockChanged`, so the voxel volume reads it again in the same frame.
- Notes: No client event reports a changed block.

#### LevelRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/LevelRendererMixin.java`
- Role: Wraps `LevelRenderer.render` to hand the frame to `LevelRenderHook`, and wraps `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` so they resolve to the scene target during a Helion frame.

#### GameRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/GameRendererMixin.java`
- Role: Hands the level projection matrix that `GameRenderer.renderLevel` uploads (camera projection with view bobbing and nausea distortion) to `HelionRenderCore`, unchanged; frames read it as `HelionCamera.levelProjection`.
- Notes: no event exposes the final level projection, and temporal anti-aliasing must jitter and reproject exactly the matrix the level is drawn with.

#### PreparedRenderTypeMixin
- Path: `src/main/java/com/aryston/helion/mixin/PreparedRenderTypeMixin.java`
- Role: In `PreparedRenderType.draw` skips the draws `EntityGeometryDraws` leaves to the other pass and swaps the pipeline for its geometry buffer variant in the entity geometry pass. It also reports every draw it keeps in the entity geometry pass to `EntityGeometryDraws.drawn` with the render type textures.
- Notes: No API lets a mod change the pipeline of a prepared render type or skip one draw of the feature dispatcher.

#### FeatureGroupMixin
- Path: `src/main/java/com/aryston/helion/mixin/FeatureGroupMixin.java`
- Role: Skips feature groups whose renderer does not draw through render types (particles, features added by other mods) in the entity geometry pass, at the head of `FeatureRenderDispatcher.PreparedGroup.execute`, so they draw in the one-attachment pass as before.
- Notes: A renderer that sets its own pipeline would fail in a four-attachment pass; no API filters feature groups.

#### EntityRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/EntityRendererMixin.java`
- Role: Skips vanilla's round entity shadow (`EntityRenderer.extractShadow`) while the core is active and drew sun shadows in the last frame, so mobs and items show only their real sun shadow; with Helion off or its shadows off vanilla draws the round shadow as before. Also saves the block scan vanilla runs under every entity for it. Raises the return value of `EntityRenderer.getPackedLightCoords` through `DynamicLightTracker.brighten`, so entities and the first-person hand near a dynamic light are lit by it.
- Notes: No event lets a mod drop the entity shadow pieces before they are submitted, and none lets it change the light an entity is drawn with.

#### CloudRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/CloudRendererMixin.java`
- Role: Cancels `CloudRenderer.render` and `renderOit` while `HelionClouds` says the volumetric clouds replace the vanilla clouds this frame.
- Notes: the vanilla clouds are drawn inside the vanilla transparency code, in the order independent path too; no event or option skips them for one frame.

#### SkyRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/SkyRendererMixin.java`
- Role: Cancels `SkyRenderer.renderSkyDisc`, `renderSunriseAndSunset`, `renderSun`, `renderMoon` and `renderStars` while `HelionSky` draws only the celestial bodies, so only the vanilla dark disc is drawn over the physical sky (which draws its own sun, moon, stars and twilight glow), and dims the color of `renderMoon` through `HelionSky.moonColor` whenever Helion draws the sky.
- Notes: `SkyRenderer.render` draws the sky color and the celestial bodies in one call; no API draws the celestial bodies alone or sets their size, and keeping the vanilla moon phases and stars avoids copying their textures and geometry.

#### ChunkSectionsToRenderAccessor
- Path: `src/main/java/com/aryston/helion/mixin/ChunkSectionsToRenderAccessor.java`
- Role: Invoker for the private `ChunkSectionsToRender.renderLayers`, the only vanilla entry that takes per layer pipeline overrides.

#### RenderSectionMixin
- Path: `src/main/java/com/aryston/helion/mixin/RenderSectionMixin.java`
- Role: Stores the shadow-caster list index on each render section and queues it after its mesh is replaced or reset, for the shadow casters and, by its section node, for the voxel volume (`VoxelSectionChanges`); a reset section also leaves the ray tracing scene (`RtxSectionCapture.removed`).
- Notes: No API reports render-section mesh changes.

#### LevelRendererAccessor
- Path: `src/main/java/com/aryston/helion/mixin/LevelRendererAccessor.java`
- Role: Accessors and invokers for private `LevelRenderer` fields, constants and methods needed to rebuild `render`, including the setter of the vanilla `skyRenderer` that `VanillaFrameDriver` creates like vanilla does, and the visible sections, view area and multi-draw state the shadow casters need.

#### VulkanFeatureSetsMixin
- Path: `src/main/java/com/aryston/helion/mixin/VulkanFeatureSetsMixin.java`
- Role: Adds `RtxFeature.FEATURE_SET` to `VulkanFeatureSets.optionalFeatureSets()` when ray tracing was asked for at startup (`RayTracingStartup`, the setting or `helion.rayTracing`) or `helion.rtxSpike` is set, so renderpearl enables ray queries on devices that support them. It also adds `RtxFeature.ENTITY_TEXTURES`.
- Notes: The device is created by renderpearl before any mod event; the optional feature set list is the only way to add extensions.

#### VulkanConstMixin
- Path: `src/main/java/com/aryston/helion/mixin/VulkanConstMixin.java`
- Role: Adds `VK_IMAGE_USAGE_STORAGE_BIT` to `VulkanConst.textureUsageToVk` for textures created with `RtxSpike.TEXTURE_USAGE_STORAGE`, so the RTX spike mask can be written by a compute shader and sampled by a fragment shader.
- Notes: Renderpearl texture usages have no storage flag.

#### FrontendGpuDeviceAccessor, VulkanDeviceAccessor, VulkanCommandEncoderAccessor
- Path: `src/main/java/com/aryston/helion/mixin/FrontendGpuDeviceAccessor.java`, `src/main/java/com/aryston/helion/mixin/VulkanDeviceAccessor.java`, `src/main/java/com/aryston/helion/mixin/VulkanCommandEncoderAccessor.java`
- Role: The renderpearl backend behind the frontend device, its enabled feature set and the command buffer being recorded, for `RtxDevice`.
- Notes: Renderpearl has no API for raw Vulkan work; used only by the RTX spike.

#### VulkanGpuBufferMixin
- Path: `src/main/java/com/aryston/helion/mixin/VulkanGpuBufferMixin.java`
- Role: When ray tracing was requested and the device enabled acceleration structures, adds the shader device address and acceleration structure build input usages to every renderpearl vertex and index buffer, so the entity bottom level is built straight from the staged vertex buffers of the frame.
- Notes: Renderpearl chooses the Vulkan usage of its buffers inside the constructor of `VulkanGpuBuffer.Direct`; the handler is static because it runs before the super constructor call.

#### VulkanBackendMixin
- Path: `src/main/java/com/aryston/helion/mixin/VulkanBackendMixin.java`
- Role: When ray tracing is asked for and the device enabled `VK_KHR_acceleration_structure`, creates renderpearl's VMA allocator with `VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT`, so ray tracing buffers come from VMA's blocks instead of one `vkAllocateMemory` each.
- Notes: Renderpearl creates the allocator in a private static method; hundreds of section structures would exceed the driver's allocation count limit with own allocations.

#### SectionCompilerMixin
- Path: `src/main/java/com/aryston/helion/mixin/SectionCompilerMixin.java`
- Role: Hands the result of every `SectionCompiler.compile` (on the section compile worker) to `RtxSectionCapture`, which copies the solid and cutout quad positions for ray tracing.
- Notes: NeoForge's `AddSectionGeometryEvent` adds geometry but never sees the finished meshes; selected with the full descriptor because a shorter overload calls this one.
