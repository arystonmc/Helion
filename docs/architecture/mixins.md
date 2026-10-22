# Helion Architecture: Mixins

Part of the [Helion architecture](../ARCHITECTURE.md).

## Mixins

| Mixin | Target | Why no API is enough |
|---|---|---|
| `LevelRendererMixin.helion$render` | `LevelRenderer.render` | NeoForge can add passes but cannot replace the vanilla pass structure |
| `LevelRendererMixin.helion$redirectMainTarget` | `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` | Vanilla OIT and depth bounds code reads the main target directly instead of the frame graph handle |
| `GameRendererMixin.helion$captureLevelProjection` | `ProjectionMatrixBuffer.getBuffer` inside `GameRenderer.renderLevel` | The final level projection includes view bobbing and nausea distortion and is built inside `renderLevel`; no event exposes it, and temporal anti-aliasing must jitter and reproject exactly this matrix |
| `SkyRendererMixin.helion$skipUnderPhysicalSky` | `SkyRenderer.renderSkyDisc` | `SkyRenderer.render` draws the sky color and the celestial bodies in one call; under the physical sky only the vanilla sunset glow, sun, moon and stars must stay |
| `CloudRendererMixin.helion$skipUnderVolumetricClouds` | `CloudRenderer.render(CloudStatus, RenderPass)` and `renderOit`, named with full descriptors because a private `render(RenderPass, RenderPipeline)` overload exists | The vanilla clouds are drawn inside the vanilla sorted and order independent transparency code; no event or option leaves them out for one frame while the volumetric clouds replace them |
| `EntityRendererMixin.helion$addDynamicLight` | return of `EntityRenderer.getPackedLightCoords` | No event or API changes the light an entity, an item or the first-person hand is drawn with |
| `RenderSectionMixin.helion$meshReplaced`, `helion$meshReset` | `RenderSection.setSectionMesh` and `reset` | No API reports render-section mesh changes; the shadow casters and the voxel volume follow them |
| `LevelExtractorMixin.helion$blockChanged` | head of `LevelExtractor.blockChanged` | No client event reports a changed block; the voxel volume must read the section again in the same frame, before the new mesh is compiled |
| `LevelRendererAccessor` | private members of `LevelRenderer` | Required to rebuild `render` with vanilla behavior |
| `ChunkSectionsToRenderAccessor.helion$renderLayers` | private `ChunkSectionsToRender.renderLayers` | Only this method takes per layer pipeline overrides; the public `renderGroup` applies one override to every layer, and solid and cutout terrain need different pipelines |
| `VulkanFeatureSetsMixin.helion$requestRayQuery` (ray tracing and RTX spike only) | return of `VulkanFeatureSets.optionalFeatureSets` | Renderpearl creates the device before any mod event; the optional feature set list is the only way to ask for device extensions and features |
| `VulkanConstMixin.helion$addStorageUsage` (ray tracing and RTX spike only) | return of `VulkanConst.textureUsageToVk` | Renderpearl texture usages have no storage image flag |
| `VulkanBackendMixin.helion$allowDeviceAddresses` (ray tracing only) | argument of `Vma.vmaCreateAllocator` in `VulkanBackend.createVma` | The allocator is created in a private static method; ray tracing needs device addresses from VMA blocks, own allocations per section would exceed the allocation count limit |
| `SectionCompilerMixin.helion$captureRayTracingGeometry` (ray tracing only) | return of `SectionCompiler.compile` with the additional renderers | No event sees the finished section meshes; the ray tracing scene copies their positions on the worker |
| `FrontendGpuDeviceAccessor`, `VulkanDeviceAccessor`, `VulkanCommandEncoderAccessor` (ray tracing and RTX spike only) | private renderpearl backend members | No API records raw Vulkan commands or reports the enabled device features |
