# Helion Architecture: Mixins

Part of the [Helion architecture](../ARCHITECTURE.md).

## Mixins

| Mixin | Target | Why no API is enough |
|---|---|---|
| `LevelRendererMixin.helion$render` | `LevelRenderer.render` | NeoForge can add passes but cannot replace the vanilla pass structure |
| `LevelRendererMixin.helion$redirectMainTarget` | `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` | Vanilla OIT and depth bounds code reads the main target directly instead of the frame graph handle |
| `GameRendererMixin.helion$captureLevelProjection` | `ProjectionMatrixBuffer.getBuffer` inside `GameRenderer.renderLevel` | The final level projection includes view bobbing and nausea distortion and is built inside `renderLevel`; no event exposes it, and temporal anti-aliasing must jitter and reproject exactly this matrix |
| `PreparedRenderTypeMixin` | `PreparedRenderType.draw` | Entities go into the geometry buffer only if their draws can switch to a four-target pipeline; no API changes the pipeline of a prepared render type or skips a draw |
| `FeatureGroupMixin` | `FeatureRenderDispatcher.PreparedGroup.execute` | Feature renderers that set their own pipeline (particles, other mods) cannot draw into a four-attachment pass; no API filters feature groups |
| `SkyRendererMixin.helion$skipUnderPhysicalSky` | `SkyRenderer.renderSkyDisc` | `SkyRenderer.render` draws the sky color and the celestial bodies in one call; under the physical sky only the vanilla sunset glow, sun, moon and stars must stay |
| `CloudRendererMixin.helion$skipUnderVolumetricClouds` | `CloudRenderer.render(CloudStatus, RenderPass)` and `renderOit`, named with full descriptors because a private `render(RenderPass, RenderPipeline)` overload exists | The vanilla clouds are drawn inside the vanilla sorted and order independent transparency code; no event or option leaves them out for one frame while the volumetric clouds replace them |
| `RenderSectionMixin.helion$meshReset` | `RenderSection.reset` | No API reports render-section mesh resets; the ray tracing scene drops the section |
| `LevelRendererAccessor` | private members of `LevelRenderer` | Required to rebuild `render` with vanilla behavior |
| `ChunkSectionsToRenderAccessor.helion$renderLayers` | private `ChunkSectionsToRender.renderLayers` | Only this method takes per layer pipeline overrides; the public `renderGroup` applies one override to every layer, and solid and cutout terrain need different pipelines |
| `VulkanFeatureSetsMixin.helion$requestRayQuery` (ray tracing only) | return of `VulkanFeatureSets.optionalFeatureSets` | Renderpearl creates the device before any mod event; the optional feature set list is the only way to ask for device extensions and features |
| `VulkanConstMixin.helion$addStorageUsage` (ray tracing only) | return of `VulkanConst.textureUsageToVk` | Renderpearl texture usages have no storage image flag |
| `VulkanBackendMixin.helion$allowDeviceAddresses` (ray tracing only) | argument of `Vma.vmaCreateAllocator` in `VulkanBackend.createVma` | The allocator is created in a private static method; ray tracing needs device addresses from VMA blocks, own allocations per section would exceed the allocation count limit |
| `SectionCompilerMixin.helion$captureRayTracingGeometry` (ray tracing only) | return of `SectionCompiler.compile` with the additional renderers | No event sees the finished section meshes; the ray tracing scene copies their positions on the worker |
| `FrontendGpuDeviceAccessor`, `VulkanDeviceAccessor`, `VulkanCommandEncoderAccessor` (ray tracing only) | private renderpearl backend members | No API records raw Vulkan commands or reports the enabled device features |
| `ClientLevelAccessor` | private `ClientLevel.skyFlashTime` | The getter is private; the flash is only visible mixed into other environment attributes |
