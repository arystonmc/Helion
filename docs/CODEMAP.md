# Helion Code Map

Every class and source file of Helion with its purpose. Find the right file here before opening any code. Keep this file in sync with the code in the same commit (rule D1). The big picture is in `docs/ARCHITECTURE.md`.

## Quick Lookup

| I want to change | Go to |
|---|---|
| Stage order of a frame | `FrameStages` |
| What a stage draws | `ClearStage`, `SkyStage`, `OpaqueGeometryStage`, `AmbientOcclusionStage`, `TransparentGeometryStage`, `PostProcessingStage`, `BloomStage`, `ImageCompositeStage`, `PresentStage` |
| Ambient occlusion look and cost | `AmbientOcclusionResources` (tuning), `AmbientOcclusionQuality` (presets), `shaders/ambient_occlusion/` |
| Bloom, tone mapping and exposure | `ImageResources` (tuning), `shaders/image/`, `shaders/include/helion_color.glsl`, `bloom_prefilter.fsh` (emissive detection) |
| Bloom threshold in daylight and caves | `AmbientLightTracker`, `ImageResources` |
| Effect settings per frame | `RenderSettings`, `HelionConfig.renderSettings` |
| Shader pipelines | `HelionPipelines`, `AmbientOcclusionPipelines`, `ImagePipelines` |
| How vanilla terrain, entities, sky or OIT are called | `VanillaTerrainSource`, `VanillaEntitySource`, `VanillaAtmosphereSource`, `VanillaTransparencySource` |
| The rebuilt vanilla `LevelRenderer.render` | `VanillaFrameDriver` |
| Frame graph targets and OIT targets | `VanillaFrameTargets` |
| Active, disabled and passive state | `HelionRenderCore`, `PassiveReason` |
| Helion color and depth buffers | `SceneTargets` |
| GPU memory tracking | `GpuResources` |
| GPU time per stage | `GpuTimings` |
| Camera and frustum math | `HelionCamera`, `HelionFrustum` |
| Settings | `HelionConfig`, `lang/*.json` |
| Key bindings and commands | `HelionKeys`, `HelionCommands` |
| F3 lines and development stats in the log | `HelionDebugEntry`, `HelionStatsLog`, `HelionStatsLines` |
| Pixel comparison with vanilla | `ParityCheck`, `FrameCapture`, `ParityResult` |
| Vanilla hooks | `LevelRendererMixin`, `LevelRendererAccessor` |
| Mod name, version, loader versions | `gradle.properties` |
| Mod list metadata, dependencies, mixin config | `neoforge.mods.toml` |

## Overview

- Side: client only (`dist = Dist.CLIENT`). `displayTest="IGNORE_ALL_VERSION"` lets players join servers without Helion.
- Requires the Vulkan backend. On OpenGL, or with Sodium or Iris installed, the core stays passive and vanilla renders.
- Root package: `com.aryston.helion`.

## Classes

### `com.aryston.helion`

#### Helion
- Path: `src/main/java/com/aryston/helion/Helion.java`
- Role: Client entry point. Registers the client config, the config screen and all event listeners.
- Members: `MOD_ID`, `LOGGER`.
- Depends on: `HelionConfig`, `ClientEvents`.

### `com.aryston.helion.config`

#### HelionConfig
- Path: `src/main/java/com/aryston/helion/config/HelionConfig.java`
- Role: Client config spec: `ENABLED` (render core on at startup), `GPU_TIMINGS`, the `ambientOcclusion` section (enabled, quality, strength, radius, debugView) and the `image` section (toneMapper, exposure, dither, and the `bloom` subsection with enabled, intensity, threshold, debugView). `renderSettings()` turns the config into a `RenderSettings` snapshot.
- Depends on: nothing inside the mod.

### `com.aryston.helion.render`

#### HelionRenderCore
- Path: `src/main/java/com/aryston/helion/render/HelionRenderCore.java`
- Role: Singleton that owns the core state and shared GPU objects. Decides whether Helion renders, detects the backend on first use, starts and ends level frames, redirects the main target during a level frame, releases everything on shutdown. Setting fields are volatile because config reloads may arrive from another thread.
- Members: `get()`, `isActive()`, `toggle()`, `passivate(reason)`, `reportFailure(throwable)`, `beginLevelFrame(main)`, `endLevelFrame()`, `resolveMainTarget(original)`, `recordFrame(camera, scene)`, `lastCamera()`, `lastScene()`, `applySettings(enabled, gpuTimings, renderSettings)`, `settings()`, `ambientOcclusion()`, `image()`, `setPassiveListener(listener)`, `shutdown()`.
- Depends on: `GpuDeviceSummary`, `GpuResources`, `SceneTargets`, `GpuTimings`, `HelionCamera`, `AmbientOcclusionResources`, `ImageResources`.

#### PassiveReason
- Path: `src/main/java/com/aryston/helion/render/PassiveReason.java`
- Role: Why the core stays passive: `NOT_VULKAN`, `INCOMPATIBLE_MOD`, `RENDER_FAILURE`. Each carries the translation key of its player message.

#### PassiveListener
- Path: `src/main/java/com/aryston/helion/render/PassiveListener.java`
- Role: Callback the integration layer registers to show a notice when the core becomes passive.

#### FrameSources
- Path: `src/main/java/com/aryston/helion/render/FrameSources.java`
- Role: Per-frame bundle of the terrain, entity, atmosphere and transparency sources plus the post effects.

#### FrameStages
- Path: `src/main/java/com/aryston/helion/render/FrameStages.java`
- Role: Single place that defines the stage order (clear, sky, geometry, ambient occlusion, post with bloom, output) and adds active stages to the frame. The output stage is `ImageCompositeStage` when an image effect is active, otherwise `PresentStage`.
- Depends on: every stage class.

### `com.aryston.helion.render.backend`

#### GpuDeviceSummary
- Path: `src/main/java/com/aryston/helion/render/backend/GpuDeviceSummary.java`
- Role: Immutable copy of the renderpearl `DeviceInfo`: GPU name, vendor, backend, driver, depth range and timestamp period. `isVulkan()` is the backend check.

### `com.aryston.helion.render.resource`

#### GpuResources
- Path: `src/main/java/com/aryston/helion/render/resource/GpuResources.java`
- Role: Registry of every GPU resource Helion owns. `release` frees through `RenderSystem.queueFencedTask` so in-flight frames are safe; `releaseAll` runs on shutdown. Feeds the F3 resource line.

#### UniformRing
- Path: `src/main/java/com/aryston/helion/render/resource/UniformRing.java`
- Role: Three mappable uniform buffers used in turn, each guarded by a fence so the CPU never overwrites data the GPU still reads. Registered in `GpuResources`.

#### TrackedResource
- Path: `src/main/java/com/aryston/helion/render/resource/TrackedResource.java`
- Role: Label, size in bytes and release action of one tracked resource.

#### SceneTargets
- Path: `src/main/java/com/aryston/helion/render/resource/SceneTargets.java`
- Role: Owns the persistent "Helion Scene" render target. Matches the main target's formats and size every frame, resizes in place and recreates it on format change.
- Depends on: `GpuResources`.

### `com.aryston.helion.render.graph`

#### RenderStage
- Path: `src/main/java/com/aryston/helion/render/graph/RenderStage.java`
- Role: Contract of a stage or post effect: `name()`, `isActive(frame)`, `addTo(frame)`.

#### FrameContext
- Path: `src/main/java/com/aryston/helion/render/graph/FrameContext.java`
- Role: Everything a stage sees for one frame: graph, targets, camera, scene snapshot, render settings and the post results that stages hand to each other.

#### FrameTargets
- Path: `src/main/java/com/aryston/helion/render/graph/FrameTargets.java`
- Role: Frame graph handles of the scene and output targets, updated after each `readsAndWrites`, plus `declareGeometryAttachments` for auxiliary targets.

#### RenderSettings
- Path: `src/main/java/com/aryston/helion/render/graph/RenderSettings.java`
- Role: Immutable per-frame effect settings. `foundation()` turns every effect off for the parity check.
- Depends on: `AmbientOcclusionSettings`, `ImageSettings`.

#### RenderGraph
- Path: `src/main/java/com/aryston/helion/render/graph/RenderGraph.java`
- Role: Thin wrapper around vanilla `FrameGraphBuilder`. Prefixes pass names with `helion:` and wraps pass tasks with GPU timing.
- Depends on: `GpuTimings`.

#### GpuTimings
- Path: `src/main/java/com/aryston/helion/render/graph/GpuTimings.java`
- Role: Timestamp query ring over four frames in flight, up to 32 stages per frame. Reads results without blocking and keeps a smoothed millisecond average per stage. Stages not measured for 120 frames are dropped, so a replaced stage (present and composite) disappears from F3.
- Depends on: `GpuResources`.

### `com.aryston.helion.render.scene`

#### SceneSnapshot
- Path: `src/main/java/com/aryston/helion/render/scene/SceneSnapshot.java`
- Role: Immutable per-frame scene facts: fog color, sky visibility, transparency mode, target size, scene color format, smoothed ambient sky light at the camera (0 to 1).

#### ClearStage
- Path: `src/main/java/com/aryston/helion/render/scene/ClearStage.java`
- Role: Clears scene color to the fog color with zero alpha and depth to the reversed-Z far value `0.0`.

### `com.aryston.helion.render.camera`

#### HelionCamera
- Path: `src/main/java/com/aryston/helion/render/camera/HelionCamera.java`
- Role: Immutable camera: world position, view rotation, projection, depth range. Provides `viewProjection()` and `frustum()`.

#### HelionFrustum
- Path: `src/main/java/com/aryston/helion/render/camera/HelionFrustum.java`
- Role: Six-plane frustum built from the render projection. Handles both depth ranges correctly, including the far plane of `[0, 1]` reversed-Z projections. `intersects` tests world-space boxes.

### `com.aryston.helion.render.geometry`

#### OpaqueGeometryStage
- Path: `src/main/java/com/aryston/helion/render/geometry/OpaqueGeometryStage.java`
- Role: Prepares fog, chunk sampler, translucent buffers and lighting, then draws opaque terrain and solid features in one render pass.
- Depends on: `TerrainSource`, `EntitySource`, `AtmosphereSource`.

#### TransparentGeometryStage
- Path: `src/main/java/com/aryston/helion/render/geometry/TransparentGeometryStage.java`
- Role: Draws sorted transparency (translucent features, translucent terrain, particles, clouds, weather, world border) or vanilla order independent transparency, then outline, see-through and always-on-top features. Declares the auxiliary OIT and outline targets.
- Depends on: `TerrainSource`, `EntitySource`, `AtmosphereSource`, `TransparencySource`.

#### TerrainSource
- Path: `src/main/java/com/aryston/helion/render/geometry/TerrainSource.java`
- Role: Contract for drawing chunk geometry: `prepareFrame`, `renderOpaque`, `renderTranslucent`.

#### EntitySource
- Path: `src/main/java/com/aryston/helion/render/geometry/EntitySource.java`
- Role: Contract for entities, block entities and particles: lighting setup, solid, translucent, after-terrain and overlay drawing.

#### TransparencySource
- Path: `src/main/java/com/aryston/helion/render/geometry/TransparencySource.java`
- Role: Contract for order independent transparency.

### `com.aryston.helion.render.lighting`

#### AmbientOcclusionStage
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionStage.java`
- Role: Adds the ambient occlusion passes (`ao_depth`, `ao_main`, `ao_denoise_N`, `ao_apply`) with their internal targets to the frame graph. Inactive when disabled, when the scene color is not RGBA8 or when a shader failed to compile.
- Depends on: `AmbientOcclusionResources`, `AmbientOcclusionPrograms`, `AmbientOcclusionPipelines`, `FullscreenPass`.

#### AmbientOcclusionPipelines
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionPipelines.java`
- Role: Pipeline definitions of every ambient occlusion pass, the uniform and sampler names they bind and the target formats.
- Depends on: `HelionPipelines`.

#### AmbientOcclusionPrograms
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionPrograms.java`
- Role: The compiled pipelines for one frame; empty when any of them is missing.

#### AmbientOcclusionResources
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionResources.java`
- Role: Persistent ambient occlusion state owned by the core: the settings uniform ring, the fixed tuning values (falloff, distribution power, thin occluder compensation, final power, blur beta, max screen radius) and the one-time missing shader warning.
- Depends on: `UniformRing`.

#### AmbientOcclusionSettings
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionSettings.java`
- Role: Player settings: enabled, quality, strength, radius, debug view.

#### AmbientOcclusionQuality
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionQuality.java`
- Role: Quality presets with slice count, steps per slice and denoise passes.

### `com.aryston.helion.render.shader`

#### HelionPipelines
- Path: `src/main/java/com/aryston/helion/render/shader/HelionPipelines.java`
- Role: Registry of every Helion pipeline (ambient occlusion and image), builder for fullscreen pipelines on the vanilla screen quad, lookup of compiled pipelines.

#### FullscreenPass
- Path: `src/main/java/com/aryston/helion/render/shader/FullscreenPass.java`
- Role: Draws one fullscreen triangle into a target with a pipeline and its bindings.

### `com.aryston.helion.render.atmosphere`

#### AtmosphereSource
- Path: `src/main/java/com/aryston/helion/render/atmosphere/AtmosphereSource.java`
- Role: Contract for sky, clouds, weather and world border.

#### SkyStage
- Path: `src/main/java/com/aryston/helion/render/atmosphere/SkyStage.java`
- Role: Draws the sky into the scene target when the frame renders sky and the source has a sky.
- Depends on: `AtmosphereSource`.

### `com.aryston.helion.render.post`

#### PostProcessingStage
- Path: `src/main/java/com/aryston/helion/render/post/PostProcessingStage.java`
- Role: Runs its active post effects in order: the effects of the frame sources (vanilla entity outline) first, then `BloomStage`.

#### PresentStage
- Path: `src/main/java/com/aryston/helion/render/post/PresentStage.java`
- Role: Copies scene color and depth into the real main target at the end of the frame. Used when no image effect is active, so foundation frames stay identical to vanilla.

#### BloomStage
- Path: `src/main/java/com/aryston/helion/render/post/BloomStage.java`
- Role: Post effect that builds the bloom mip chain: `bloom_prefilter` (scene to half resolution with emissive detection, adaptive threshold and Karis average), `bloom_down_N` (13-tap downsample) and `bloom_up_N` (tent upsample added onto the next larger mip). Publishes the half resolution result in `PostResults`. Inactive when bloom is off, the scene color is not RGBA8 or a shader failed to compile.
- Depends on: `ImageResources`, `ImagePrograms`, `ImagePipelines`, `PostSampling`, `FullscreenPass`.

#### ImageCompositeStage
- Path: `src/main/java/com/aryston/helion/render/post/ImageCompositeStage.java`
- Role: Output stage used instead of `PresentStage` when bloom, exposure or Filmic tone mapping is active. Expands the RGBA8 scene to HDR, adds bloom, applies exposure, tone mapping and dither, writes the main target in one fullscreen pass and copies depth. Shows only the bloom in the debug view.
- Depends on: `ImageResources`, `ImagePrograms`, `ImagePipelines`, `PostSampling`, `FullscreenPass`.

#### ImagePipelines
- Path: `src/main/java/com/aryston/helion/render/post/ImagePipelines.java`
- Role: Pipeline definitions of the bloom and composite passes, the uniform and sampler names they bind and the target formats (RGBA16_FLOAT bloom mips, RGBA8 output).
- Depends on: `HelionPipelines`.

#### ImagePrograms
- Path: `src/main/java/com/aryston/helion/render/post/ImagePrograms.java`
- Role: The compiled image pipelines for one frame; empty when any of them is missing.

#### ImageResources
- Path: `src/main/java/com/aryston/helion/render/post/ImageResources.java`
- Role: Persistent image state owned by the core: the `HelionImage` uniform ring, the fixed tuning values (bloom mip count, dark and daylight threshold scales, threshold knee) and the one-time missing shader warning. Writes the uniforms once per frame and checks whether the image stages can run. `bloomThreshold` computes the effective threshold for the current ambient light.
- Depends on: `UniformRing`, `ImagePrograms`, `PostResults`.

#### ImageSettings
- Path: `src/main/java/com/aryston/helion/render/post/ImageSettings.java`
- Role: Player image settings: tone mapper, exposure in stops, dither and bloom. `needsComposite()` tells whether the output must go through `ImageCompositeStage`. `FOUNDATION` keeps the plain copy.
- Depends on: `ToneMapper`, `BloomSettings`.

#### BloomSettings
- Path: `src/main/java/com/aryston/helion/render/post/BloomSettings.java`
- Role: Player bloom settings: enabled, intensity, threshold, debug view.

#### ToneMapper
- Path: `src/main/java/com/aryston/helion/render/post/ToneMapper.java`
- Role: Tone mapping choices `NEUTRAL` (Khronos PBR Neutral), `FILMIC` (Neutral plus a film grade) and `NONE`, each with the id the shaders switch on.

#### PostResults
- Path: `src/main/java/com/aryston/helion/render/post/PostResults.java`
- Role: Per-frame results that post stages hand to each other: the bloom target handle and the image uniform buffer written this frame.

#### PostSampling
- Path: `src/main/java/com/aryston/helion/render/post/PostSampling.java`
- Role: Texture view and clamp-to-edge sampler helpers shared by the image stages.

### `com.aryston.helion.integration`

#### ClientEvents
- Path: `src/main/java/com/aryston/helion/integration/ClientEvents.java`
- Role: Wires every listener: client setup (compatibility check, passive notices), pipeline registration, config loading, key handling and notices on client tick, client commands, shutdown.
- Depends on: `HelionRenderCore`, `HelionKeys`, `HelionCommands`, `CompatibilityGuard`, `PassiveModeNotice`, `HelionDebugEntry`, `HelionStatsLog`, `VisualTest`, `LevelRenderHook`.

#### HelionCommands
- Path: `src/main/java/com/aryston/helion/integration/HelionCommands.java`
- Role: Client commands `/helion status`, `/helion toggle`, `/helion parity` and their chat messages.
- Depends on: `HelionRenderCore`, `ParityCheck`.

#### HelionKeys
- Path: `src/main/java/com/aryston/helion/integration/HelionKeys.java`
- Role: "Helion" key category and the toggle key (default `H`).

#### CompatibilityGuard
- Path: `src/main/java/com/aryston/helion/integration/CompatibilityGuard.java`
- Role: Lists installed renderer mods (Sodium, Iris) that make Helion passive.

#### PassiveModeNotice
- Path: `src/main/java/com/aryston/helion/integration/PassiveModeNotice.java`
- Role: Queues passive reasons and shows them as toasts once the GUI exists; also shows the toggle toast.

### `com.aryston.helion.integration.vanilla`

#### LevelRenderHook
- Path: `src/main/java/com/aryston/helion/integration/vanilla/LevelRenderHook.java`
- Role: Entry from the mixin. Chooses the Helion or vanilla frame and the render settings (foundation settings while the parity check captures), catches Helion failures, feeds the parity check after every level frame.
- Depends on: `VanillaFrameDriver`, `HelionRenderCore`, `ParityCheck`.

#### LevelFrameRequest
- Path: `src/main/java/com/aryston/helion/integration/vanilla/LevelFrameRequest.java`
- Role: Arguments of one `LevelRenderer.render` call plus typed access to the accessor mixin.

#### VanillaFrameDriver
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaFrameDriver.java`
- Role: Rebuilds `LevelRenderer.render` with Helion stages: feature preparation, targets, NeoForge frame graph event, chunk draw preparation, stage building, graph execution, then section compile, upload and occlusion update.
- Depends on: every class in this package, `FrameStages`, `HelionRenderCore`.
- Notes: mirrors vanilla code; see Vanilla Coupling in `docs/ARCHITECTURE.md`.

#### VanillaFrameTargets
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaFrameTargets.java`
- Role: `FrameTargets` over vanilla `LevelTargetBundle`. Imports scene and output targets, creates OIT and always-on-top targets, declares them for the geometry pass.

#### StageEvents
- Path: `src/main/java/com/aryston/helion/integration/vanilla/StageEvents.java`
- Role: Posts every `RenderLevelStageEvent` with the same arguments vanilla uses.

#### VanillaTerrainSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaTerrainSource.java`
- Role: `TerrainSource` over vanilla `ChunkSectionsToRender`: fog, chunk sampler, opaque and translucent layer groups, matching stage events.

#### VanillaEntitySource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaEntitySource.java`
- Role: `EntitySource` over the vanilla prepared feature frame, including outline, see-through and always-on-top passes.

#### VanillaAtmosphereSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaAtmosphereSource.java`
- Role: `AtmosphereSource` over vanilla sky, cloud, weather and world border renderers, honoring NeoForge custom sky, cloud and weather renderers.
- Depends on: `SceneSkyRenderer`, `StageEvents`.

#### VanillaTransparencySource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaTransparencySource.java`
- Role: `TransparencySource` that runs vanilla order independent transparency.

#### VanillaEntityOutlineEffect
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaEntityOutlineEffect.java`
- Role: Post effect that adds the vanilla entity outline post chain to the frame graph.

#### SceneSkyRenderer
- Path: `src/main/java/com/aryston/helion/integration/vanilla/SceneSkyRenderer.java`
- Role: Helion's own vanilla `SkyRenderer` bound to the scene target, recreated when the target or the sky reset flag changes.

#### AmbientLightTracker
- Path: `src/main/java/com/aryston/helion/integration/vanilla/AmbientLightTracker.java`
- Role: Sky light that reaches the camera block after the time of day and weather darkening, smoothed over frame time (1.5 s). Feeds `SceneSnapshot.ambientLight`, which raises the bloom threshold in daylight and lowers it in caves and at night.

### `com.aryston.helion.debug`

#### HelionDebugEntry
- Path: `src/main/java/com/aryston/helion/debug/HelionDebugEntry.java`
- Role: F3 lines: core state, GPU and backend, total and per stage GPU time, image state (tone mapper, exposure, ambient light, bloom threshold), tracked resources, Helion frustum versus vanilla visible sections.
- Depends on: `HelionStatsLines`.

#### HelionStatsLog
- Path: `src/main/java/com/aryston/helion/debug/HelionStatsLog.java`
- Role: Writes `Helion stats`, `Helion stages` and `Helion image` lines to the log every 100 client ticks while a world is open, so test runs can be measured without reading F3. Only in development runs (`FMLEnvironment.isProduction()` is false), never in player installs.
- Depends on: `HelionStatsLines`, `HelionRenderCore`.

#### HelionStatsLines
- Path: `src/main/java/com/aryston/helion/debug/HelionStatsLines.java`
- Role: Text shared by the F3 entry and the stats log: per stage timings, their total and the image state line.
- Depends on: `HelionRenderCore`, `ImageResources`.

#### VisualTest
- Path: `src/main/java/com/aryston/helion/debug/VisualTest.java`
- Role: Automated visual test that runs when the JVM property `helion.visualTest=true` is set in a development run. Prepares the world, then for every scene and variant sets the time, holds the camera, applies the variant's settings, waits for the frame to settle, saves a half resolution screenshot and logs frame rate, GPU time and image state. The parity variant freezes ticks and runs `ParityCheck` instead, logging a pass or a `WARN` with the differing pixels. Restores the settings and the HUD and closes the game cleanly at the end.
- Depends on: `VisualTestScene`, `VisualTestVariant`, `VisualTestWorld`, `ParityCheck`, `HelionStatsLines`, `HelionRenderCore`, `HelionConfig`.
- Notes: changes blocks, time and game rules of the loaded world; run it on a copy of a save only.

#### VisualTestScene
- Path: `src/main/java/com/aryston/helion/debug/VisualTestScene.java`
- Role: The photographed scenes: name, time of day, camera yaw and pitch.

#### VisualTestVariant
- Path: `src/main/java/com/aryston/helion/debug/VisualTestVariant.java`
- Role: Render variants per scene: vanilla, None, Neutral, Filmic, bloom only and parity, each turning the core on or off and setting default image settings.

#### VisualTestWorld
- Path: `src/main/java/com/aryston/helion/debug/VisualTestWorld.java`
- Role: Server commands of the visual test: freezes time and weather, stops mob spawning, switches to spectator, builds a stone platform with light blocks in front of the player, sets the time per scene and freezes ticks for the parity check.

#### ParityCheck
- Path: `src/main/java/com/aryston/helion/debug/ParityCheck.java`
- Role: State machine behind `/helion parity`: renders three warm-up vanilla frames, captures the fourth, renders three warm-up Helion frames, captures the fourth, compares when both readbacks finish. Warm-up keeps cold first-use resources out of the comparison. Helion frames use `RenderSettings.foundation()`, so effects never count as differences.

#### FrameCapture
- Path: `src/main/java/com/aryston/helion/debug/FrameCapture.java`
- Role: Copies a render target's color and depth into readback buffers and keeps the bytes.

#### ParityResult
- Path: `src/main/java/com/aryston/helion/debug/ParityResult.java`
- Role: Pixel comparison result: total pixels, differing color pixels, largest channel difference, differing depth pixels.

### `com.aryston.helion.mixin`

#### LevelRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/LevelRendererMixin.java`
- Role: Wraps `LevelRenderer.render` to hand the frame to `LevelRenderHook`, and wraps `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` so they resolve to the scene target during a Helion frame.

#### LevelRendererAccessor
- Path: `src/main/java/com/aryston/helion/mixin/LevelRendererAccessor.java`
- Role: Accessors and invokers for private `LevelRenderer` fields, constants and methods needed to rebuild `render`.

## Source Files

| File | Purpose |
|---|---|
| `src/main/templates/META-INF/neoforge.mods.toml` | Mod metadata template filled from `gradle.properties`: dependencies, discouraged Sodium and Iris, mixin config. |
| `src/main/resources/helion.mixins.json` | Mixin configuration listing `LevelRendererAccessor` and `LevelRendererMixin`. |

## Asset Folders

| Folder | Contents |
|---|---|
| `src/main/resources/assets/helion/lang/` | `en_us.json` and `tr_tr.json`: config, key, toast and command texts. |
| `src/main/resources/assets/helion/shaders/ambient_occlusion/` | Ambient occlusion fragment shaders: `view_depth`, `gtao`, `denoise`, `denoise_resolve`, `apply`. |
| `src/main/resources/assets/helion/shaders/image/` | Bloom and image fragment shaders: `bloom_prefilter`, `bloom_downsample`, `bloom_upsample`, `composite`, `bloom_debug`. |
| `src/main/resources/assets/helion/shaders/include/` | Shared GLSL: `helion_view` (depth and view position), `helion_ambient_occlusion` (settings block, edge packing), `helion_ambient_occlusion_denoise` (edge aware blur), `helion_color` (sRGB conversion, saturation, the Neutral highlight curve and its inverse, film grade, dither noise), `helion_image` (image settings block, scene expansion, tone mapper switch, Filmic highlight bleach, bloom threshold, dither), `helion_bloom_downsample` (13-tap downsample with optional Karis average over a `helionBloomTap` function the including shader defines). |

## Build Files

| File | Purpose |
|---|---|
| `build.gradle` | ModDevGradle setup, `client` run forced to Vulkan, metadata expansion, packs `branding/logo.png` into the jar as `helion.png`, jar name `helion-neoforge-<minecraft_version>-<version>.jar`. |
| `gradle.properties` | Single place for versions and mod metadata. |
| `settings.gradle` | Plugin repositories, Java toolchain resolver, project name. |
