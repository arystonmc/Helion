# Helion Architecture

How the Helion render core is built, how a frame flows through it and which rules keep it maintainable. Read this before changing anything under `render/`, `integration/` or `mixin/`.

The sections live in `docs/architecture/`, one file per topic. Read the invariants below, then open only the section your task needs.

## Invariants

1. The render core never imports `net.minecraft` or `net.neoforged`, with one exception: `net.minecraft.resources.Identifier`, which the renderpearl API itself requires. Vanilla access lives in `integration` and `mixin` only.
2. Renderpearl is used through `api` and `frontend` types. Backend packages (`renderpearl.backend.*`) are not used, except by the ray tracing code in `render.rtx` and its mixins, which only run with `-Dhelion.rayTracing=true` or `-Dhelion.rtxSpike=true`.
3. Helion never crashes the game. A runtime exception in the Helion frame passivates the core (`RENDER_FAILURE`) and vanilla rendering continues from the next frame.
4. Every GPU resource Helion creates is registered in `GpuResources` with a label and size, and released through it.
5. NeoForge events (`FrameGraphSetupEvent`, every `RenderLevelStageEvent`, `PrepareRenderBuffersEvent`) fire in the same order and with the same render pass as vanilla.
6. Depth is reversed-Z: clear value `0.0`, near maps to `1.0`.
7. Everything runs on the render thread.
8. Any new mixin is added to this document and the code map with the reason no event or API covers it.

## Sections

| Section | File | Covers |
|---|---|---|
| Goal of the Foundation | [goal-of-the-foundation.md](architecture/goal-of-the-foundation.md) | Helion routes the vanilla world rendering of Minecraft 26.3 through its own render core on the Vulkan backend while the picture stays ... |
| Layers | [layers.md](architecture/layers.md) | Layer diagram from the vanilla level renderer down to the render core. |
| Frame Flow | [frame-flow.md](architecture/frame-flow.md) | `LevelRendererMixin` wraps `LevelRenderer.render`. |
| Stages | [stages.md](architecture/stages.md) | Planned stages and where they go: atmosphere effects after transparent geometry, further Helion post effects inside `PostProcessingStage` ... |
| Render Settings | [render-settings.md](architecture/render-settings.md) | `RenderSettings` is an immutable snapshot of every effect setting, taken from the client config and carried in `FrameContext`. |
| Shaders and Pipelines | [shaders-and-pipelines.md](architecture/shaders-and-pipelines.md) | Shaders live in `src/main/resources/assets/helion/shaders/<feature>/*.fsh`, shared code in `shaders/include/helion_*.glsl`, included as ... |
| Ambient Occlusion | [ambient-occlusion.md](architecture/ambient-occlusion.md) | GTAO style horizon based ambient occlusion, ported from Intel XeGTAO (MIT, see `THIRD_PARTY_NOTICES.md`) to fragment shaders. |
| Image Pipeline | [image-pipeline.md](architecture/image-pipeline.md) | Why There Is No Real HDR Scene Target |
| Geometry Buffer | [geometry-buffer.md](architecture/geometry-buffer.md) | Foundation for Helion's own lighting. |
| Deferred Lighting | [deferred-lighting.md](architecture/deferred-lighting.md) | Helion lights opaque terrain itself from the geometry buffer, in high dynamic range. |
| Dynamic Lights | [dynamic-lights.md](architecture/dynamic-lights.md) | On by default (`dynamicLights.enabled`), off in the Vanilla preset, the foundation and the screen effects mode. |
| Temporal Anti-Aliasing | [temporal-anti-aliasing.md](architecture/temporal-anti-aliasing.md) | Off by default (`temporalAntiAliasing.enabled`). |
| Motion Blur | [motion-blur.md](architecture/motion-blur.md) | Off by default (`image.motionBlur.enabled`, experimental). |
| Edge Smoothing | [edge-smoothing.md](architecture/edge-smoothing.md) | Off by default (`image.edgeSmoothing`). |
| Depth of Field | [depth-of-field.md](architecture/depth-of-field.md) | Off by default (`image.depthOfField.enabled`, experimental). |
| Physical Sky | [physical-sky.md](architecture/physical-sky.md) | Off by default (`physicalSky.enabled`). |
| Volumetric Clouds | [volumetric-clouds.md](architecture/volumetric-clouds.md) | Off by default (`clouds.enabled`, experimental), on in the Experimental preset at Medium quality. |
| Light Shafts | [light-shafts.md](architecture/light-shafts.md) | Sun and moon god rays in screen space, on wherever the physical sky runs (`physicalSky.lightShafts`). |
| Shadows | [shadows.md](architecture/shadows.md) | Off by default (`shadows.enabled`). |
| Voxel Lighting | [voxel-lighting.md](architecture/voxel-lighting.md) | Experimental, off by default (`voxelLighting.mode`). Renderpearl API findings, the block volume around the camera, light grid, traced colored block light with shadows, bounced light, tinted light and the voxel slice view. |
| Voxel Lighting RTX Spike | [voxel-lighting-rtx-spike.md](architecture/voxel-lighting-rtx-spike.md) | The 2026-10-22 test of hardware ray queries against the voxel grid walk and the owner's decision for an optional RTX layer. |
| Ray Tracing | [ray-tracing.md](architecture/ray-tracing.md) | Optional hardware ray tracing layer, experimental setting `rayTracing.enabled` (restart) with water reflections: device features, VMA buffers, per-section bottom levels from the block meshes, per-frame top level, sun shadow validation view. |
| Rain Wetness | [rain-wetness.md](architecture/rain-wetness.md) | On by default (`reflections.rainWetness`): wet, darker surfaces in rain, puddles with screen space reflections and rain ripples on blocks that hold water, material classes from the block atlas mask. |
| Sources | [sources.md](architecture/sources.md) | Stages never call vanilla code directly. |
| Vanilla Coupling | [vanilla-coupling.md](architecture/vanilla-coupling.md) | Places that copy or depend on vanilla internals. |
| Mixins | [mixins.md](architecture/mixins.md) | Every mixin with its target and why no API is enough. |
| Porting Checklist for a New Minecraft Version | [porting-checklist.md](architecture/porting-checklist.md) | Diff `LevelRenderer.render`, `addSkyPass`, `addMainPass`, `executeSolid`, `executeClassicTransparency` against the previous version. |
| Testing Tools | [testing-tools.md](architecture/testing-tools.md) | Debug mode (Mods → Helion → Config → Debug Mode, off by default): shows every value of `HelionDebugSnapshot` on screen without F3 and ... |
| Parity Scenes | [parity-scenes.md](architecture/parity-scenes.md) | Run the parity check (`J` in debug mode) in each scene before committing a change to the render core: |
