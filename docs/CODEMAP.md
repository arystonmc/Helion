# Helion Code Map

Every class and source file of Helion with its purpose. Find the right file here before opening any code. Keep this file in sync with the code in the same commit (rule D1). The big picture is in `docs/ARCHITECTURE.md`.

## Quick Lookup

| I want to change | Go to |
|---|---|
| Stage order of a frame | `FrameStages` |
| What a stage draws | `ClearStage`, `SkyStage`, `PhysicalSkyStage`, `OpaqueGeometryStage`, `DeferredLightingStage`, `SolidFeatureStage`, `AmbientOcclusionStage`, `TransparentGeometryStage`, `TemporalStage`, `PostProcessingStage`, `BloomStage`, `SharpeningStage`, `ImageCompositeStage`, `PresentStage` |
| Ambient occlusion look and cost | `AmbientOcclusionResources` (tuning), `AmbientOcclusionQuality` (presets), `shaders/ambient_occlusion/` |
| Bloom, tone mapping and exposure | `ImageResources` (tuning), `shaders/image/`, `shaders/include/helion_color.glsl`, `bloom_prefilter.fsh` (emissive detection) |
| Sharpening | `SharpeningStage`, `shaders/image/sharpen.fsh` |
| Bloom threshold in daylight and caves | `AmbientLightTracker`, `ImageResources` |
| Effect settings per frame | `RenderSettings`, `HelionConfig.renderSettings` |
| Geometry buffer (normals, light, albedo of terrain) | `OpaqueGeometryStage`, `VanillaGeometryPipelines`, `GeometryBufferPipelines`, `shaders/terrain/`, `shaders/geometry/` |
| Deferred terrain lighting in HDR (block and sky light) | `DeferredLightingStage`, `DeferredLightingResources`, `LightEnvironment`, `shaders/lighting/`, `shaders/include/helion_lighting.glsl` |
| Temporal anti-aliasing: jitter, history, reprojection | `TemporalStage`, `TemporalResources`, `JitterSequence`, `PreviousView`, `GameRendererMixin`, `shaders/temporal/` |
| Physical sky: atmosphere, spectral colors, sun and moon light, fog | `PhysicalSkyStage`, `PhysicalSkyResources` (brightness, fog buffer), `AtmosphereSpectrum` (wavelength data), `SkyEnvironment`, `SkyRendererMixin`, `shaders/atmosphere/`, `shaders/include/helion_atmosphere.glsl`, `helion_spectrum.glsl`, `helion_sky_color.glsl` |
| Physical sky light on terrain | `SkyLight`, `DeferredLightingStage`, `sky_light.fsh`, `deferred_light.fsh` (`HELION_PHYSICAL_SKY_LIGHT`) |
| Sun and moon shadows: cascades, shadow map, mask | `ShadowStage`, `ShadowCascades` (splits, fitting, texel snapping), `ShadowCascadeCache` (which cascades are drawn this frame), `ShadowLight` (sun or moon, horizon fade), `ShadowResources`, `VanillaShadowCasterSource`, `VanillaShadowPipelines`, `shaders/shadow/`, `shaders/include/helion_shadow.glsl`, `deferred_light.fsh` (`HELION_SHADOWS`) |
| Shader pipelines | `HelionPipelines`, `AmbientOcclusionPipelines`, `DeferredLightingPipelines`, `TemporalPipelines`, `PhysicalSkyPipelines`, `ImagePipelines` |
| How vanilla terrain, entities, sky or OIT are called | `VanillaTerrainSource`, `VanillaEntitySource`, `VanillaAtmosphereSource`, `VanillaTransparencySource` |
| The rebuilt vanilla `LevelRenderer.render` | `VanillaFrameDriver` |
| Frame graph targets and OIT targets | `VanillaFrameTargets` |
| Active, disabled and passive state | `HelionRenderCore`, `PassiveReason` |
| Helion color and depth buffers | `SceneTargets` |
| GPU memory tracking | `GpuResources` |
| GPU time per stage | `GpuTimings` |
| Camera and frustum math | `HelionCamera`, `HelionFrustum` |
| Settings | `HelionConfig`, `lang/*.json` |
| Settings screen (Arkea) | `HelionConfigScreen`, `HelionOptions`, `HelionPresets`, `HelionImpact` |
| Key bindings | `HelionKeys`, `ParityControl` |
| Debug mode: on-screen values and JSON log | `HelionDebugSnapshot` (every value), `HelionDebugEntry` (screen), `DebugOverlayLines` (line layout), `HelionDebugLog` (`logs/helion-debug.jsonl`) |
| Pixel comparison with vanilla | `ParityCheck`, `FrameCapture`, `ParityResult`, `ParityDifferenceImage` |
| Vanilla hooks | `LevelRendererMixin`, `LevelRendererAccessor`, `ChunkSectionsToRenderAccessor`, `GameRendererMixin`, `SkyRendererMixin` |
| Mod name, version, loader versions | `gradle.properties` |
| Unit tests | `src/test/java/`, see Tests below |
| Mod list metadata, dependencies, mixin config | `neoforge.mods.toml` |

## Overview

- Side: client only (`dist = Dist.CLIENT`). `displayTest="IGNORE_ALL_VERSION"` lets players join servers without Helion.
- Requires the Vulkan backend. On OpenGL, or with Sodium or Iris installed, the core stays passive and vanilla renders.
- Root package: `com.aryston.helion`.

## Classes

### `com.aryston.helion`

#### Helion
- Path: `src/main/java/com/aryston/helion/Helion.java`
- Role: Client entry point. Registers the client config, the Arkea config screen (`HelionConfigScreen`) and all event listeners.
- Members: `MOD_ID`, `LOGGER`.
- Depends on: `HelionConfig`, `HelionConfigScreen`, `ClientEvents`.

### `com.aryston.helion.config`

#### HelionConfig
- Path: `src/main/java/com/aryston/helion/config/HelionConfig.java`
- Role: Client config spec: `ENABLED` (render core on at startup), `DEBUG_MODE` (off by default), the `ambientOcclusion` section (enabled, algorithm, quality, strength, radius, debugView) and the `image` section (toneMapper, exposure, dither, the `bloom` subsection with enabled, intensity, threshold, debugView, and the `sharpening` subsection with enabled, strength), the `geometryBuffer` section (enabled, view) the `lighting` section (enabled, blockLightIntensity, skyLightIntensity, lightOnlyView), the `temporalAntiAliasing` section (enabled), the `physicalSky` section (enabled) and the `shadows` section (enabled, quality). `renderSettings()` turns the config into a `RenderSettings` snapshot. The values and range limits are package visible for `HelionOptions`.
- Depends on: nothing inside the mod.

#### HelionConfigScreen
- Path: `src/main/java/com/aryston/helion/config/HelionConfigScreen.java`
- Role: Settings screen built with the Arkea config API, following the Helion design of the Arkea handoff: Helion logo, sidebar groups Configuration (General with presets and the render core switch, Rendering, Lighting, Effects) and System (Performance with the quality settings, Advanced with debug mode and collapsible debug views), GPU impact meter, Apply and Reset.
- Depends on: `HelionOptions`, `HelionPresets`, `HelionImpact`.
- Notes: Needs Arkea (required dependency in `neoforge.mods.toml`).

#### HelionOptions
- Path: `src/main/java/com/aryston/helion/config/HelionOptions.java`
- Role: One Arkea `ConfigOption` per `HelionConfig` value with icon, short description, the existing config tooltip, BETA tag for experimental features, cost pips, before and after previews, and requirements (shadows need HDR lighting and the physical sky, HDR lighting needs the geometry buffer, every effect needs the render core).

#### HelionPresets
- Path: `src/main/java/com/aryston/helion/config/HelionPresets.java`
- Role: Vanilla, Balanced, Quality and Experimental presets that set the effect switches and quality levels together.

#### HelionImpact
- Path: `src/main/java/com/aryston/helion/config/HelionImpact.java`
- Role: Sidebar meter that estimates the GPU impact from the costs of the effects that are on and their quality, using the values shown on screen.

### `com.aryston.helion.render`

#### HelionRenderCore
- Path: `src/main/java/com/aryston/helion/render/HelionRenderCore.java`
- Role: Singleton that owns the core state and shared GPU objects. Decides whether Helion renders, detects the backend on first use, starts and ends level frames, redirects the main target during a level frame, releases everything on shutdown and renders nothing after it, because Minecraft can still draw a frame after the shutdown event. Setting fields are volatile because config reloads may arrive from another thread.
- Members: `get()`, `isActive()`, `toggle()`, `passivate(reason)`, `reportFailure(throwable)`, `beginLevelFrame(main)`, `endLevelFrame()`, `resolveMainTarget(original)`, `recordFrame(camera, scene)`, `lastCamera()`, `lastScene()`, `applySettings(enabled, debugMode, renderSettings)`, `isDebugMode()`, `settings()`, `ambientOcclusion()`, `image()`, `lighting()`, `temporal()`, `sky()`, `shadows()`, `recordLevelProjection(projection)`, `levelProjection()`, `setPassiveListener(listener)`, `shutdown()`.
- Depends on: `GpuDeviceSummary`, `GpuResources`, `SceneTargets`, `GpuTimings`, `HelionCamera`, `AmbientOcclusionResources`, `ImageResources`, `DeferredLightingResources`, `TemporalResources`, `PhysicalSkyResources`, `ShadowResources`.

#### PassiveReason
- Path: `src/main/java/com/aryston/helion/render/PassiveReason.java`
- Role: Why the core stays passive: `NOT_VULKAN`, `INCOMPATIBLE_MOD`, `RENDER_FAILURE`. Each carries the translation key of its player message.

#### PassiveListener
- Path: `src/main/java/com/aryston/helion/render/PassiveListener.java`
- Role: Callback the integration layer registers to show a notice when the core becomes passive.

#### FrameSources
- Path: `src/main/java/com/aryston/helion/render/FrameSources.java`
- Role: Per-frame bundle of the terrain, entity, atmosphere, transparency and shadow caster sources plus the post effects.

#### FrameStages
- Path: `src/main/java/com/aryston/helion/render/FrameStages.java`
- Role: Single place that defines the stage order (clear, sky, opaque geometry, shadows, deferred lighting, solid features, ambient occlusion, transparent geometry, temporal anti-aliasing, post with bloom and sharpening, output, geometry buffer view) and adds active stages to the frame one by one, so a stage can see what earlier stages published (the geometry buffer). The output stage is `ImageCompositeStage` when an image effect is active, otherwise `PresentStage`.
- Depends on: every stage class.

### `com.aryston.helion.render.backend`

#### GpuDeviceSummary
- Path: `src/main/java/com/aryston/helion/render/backend/GpuDeviceSummary.java`
- Role: Immutable copy of the renderpearl `DeviceInfo`: GPU name, vendor, backend, driver, depth range and timestamp period. `isVulkan()` is the backend check.

### `com.aryston.helion.render.resource`

#### GpuResources
- Path: `src/main/java/com/aryston/helion/render/resource/GpuResources.java`
- Role: Registry of every GPU resource Helion owns. `release` frees through `RenderSystem.queueFencedTask` so in-flight frames are safe; `releaseAll` runs on shutdown. Feeds the resource values of the debug snapshot through `live()`.

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
- Role: Everything a stage sees for one frame: graph, targets, camera, scene snapshot, render settings, the geometry buffer, the temporal frame (jittered projection, reprojection), the atmosphere results (sky light and fog pass of the physical sky), the shadow results (shadow mask and light) and the post results that stages hand to each other.

#### FrameTargets
- Path: `src/main/java/com/aryston/helion/render/graph/FrameTargets.java`
- Role: Frame graph handles of the scene and output targets, updated after each `readsAndWrites`, plus `declareGeometryAttachments` for auxiliary targets.

#### RenderSettings
- Path: `src/main/java/com/aryston/helion/render/graph/RenderSettings.java`
- Role: Immutable per-frame effect settings. `OFF` is the startup value; `foundation()` turns every effect off for the parity check, deferred lighting, temporal anti-aliasing, the physical sky and shadows included, but keeps the geometry buffer (without its view) because it belongs to the core.
- Depends on: `AmbientOcclusionSettings`, `ImageSettings`, `GeometryBufferSettings`, `DeferredLightingSettings`, `TemporalSettings`, `PhysicalSkySettings`, `ShadowSettings`.

#### RenderGraph
- Path: `src/main/java/com/aryston/helion/render/graph/RenderGraph.java`
- Role: Thin wrapper around vanilla `FrameGraphBuilder`. Prefixes pass names with `helion:` and wraps pass tasks with GPU timing.
- Depends on: `GpuTimings`.

#### GpuTimings
- Path: `src/main/java/com/aryston/helion/render/graph/GpuTimings.java`
- Role: Timestamp query ring over four frames in flight, up to 32 stages per frame. Reads results without blocking and keeps a smoothed millisecond average per stage. Stages not measured for 120 frames are dropped, so a replaced stage (present and composite) disappears from F3. `pause()` stops measuring and clears every value; the core calls it for frames without GPU timing and the render hook for vanilla frames, so F3 never shows stale numbers.
- Depends on: `GpuResources`.

### `com.aryston.helion.render.scene`

#### SceneSnapshot
- Path: `src/main/java/com/aryston/helion/render/scene/SceneSnapshot.java`
- Role: Immutable per-frame scene facts: fog color, sky visibility, transparency mode, target size, scene color format, smoothed ambient sky light at the camera (0 to 1) and the vanilla light map inputs (`LightEnvironment`).

#### ClearStage
- Path: `src/main/java/com/aryston/helion/render/scene/ClearStage.java`
- Role: Clears scene color to the fog color with zero alpha and depth to the reversed-Z far value `0.0`.

### `com.aryston.helion.render.camera`

#### HelionCamera
- Path: `src/main/java/com/aryston/helion/render/camera/HelionCamera.java`
- Role: Immutable camera: world position, view rotation, the camera projection (used for culling), the level projection the frame is really drawn with (view bobbing, nausea distortion and the temporal jitter included), depth range. Provides `viewProjection()`, `levelViewProjection()`, `withLevelProjection` and `frustum()`.

#### HelionFrustum
- Path: `src/main/java/com/aryston/helion/render/camera/HelionFrustum.java`
- Role: Six-plane frustum built from the render projection. Handles both depth ranges correctly, including the far plane of `[0, 1]` reversed-Z projections. `intersects` tests world-space boxes.

### `com.aryston.helion.render.geometry`

#### OpaqueGeometryStage
- Path: `src/main/java/com/aryston/helion/render/geometry/OpaqueGeometryStage.java`
- Role: Prepares fog, chunk sampler, translucent buffers and lighting, then draws opaque terrain and solid features in one pass, as vanilla does. With the geometry buffer on, creates the normal, light and albedo targets (cleared to zero, so alpha 0 marks pixels without terrain), draws only opaque terrain into them and the scene in a four-attachment render pass and publishes them in `GeometryBuffer`; `SolidFeatureStage` draws the rest after deferred lighting.
- Depends on: `TerrainSource`, `EntitySource`, `AtmosphereSource`.

#### SolidFeatureStage
- Path: `src/main/java/com/aryston/helion/render/geometry/SolidFeatureStage.java`
- Role: Only when the geometry buffer was written: fires the after-opaque-blocks event and draws solid entities, block entities and particles into the scene, after deferred lighting so the lighting never paints over them.
- Depends on: `TerrainSource`, `EntitySource`.

#### TransparentGeometryStage
- Path: `src/main/java/com/aryston/helion/render/geometry/TransparentGeometryStage.java`
- Role: Draws sorted transparency (translucent features, translucent terrain, particles, clouds, weather, world border) or vanilla order independent transparency, then outline, see-through and always-on-top features. Declares the auxiliary OIT and outline targets. When the physical sky published a `SceneFogPass`, draws it after the sorted translucents and before clouds, weather and world border (a separate render pass on each side), or after order independent transparency.
- Depends on: `TerrainSource`, `EntitySource`, `AtmosphereSource`, `TransparencySource`, `SceneFogPass`.

#### TerrainSource
- Path: `src/main/java/com/aryston/helion/render/geometry/TerrainSource.java`
- Role: Contract for drawing chunk geometry: `prepareFrame`, `supportsGeometryBuffer`, `renderOpaque` (vanilla path with the after-opaque event), `renderOpaqueGeometry` (geometry buffer path), `afterOpaque`, `renderTranslucent`.

#### GeometryBuffer
- Path: `src/main/java/com/aryston/helion/render/geometry/GeometryBuffer.java`
- Role: Per-frame holder of the geometry buffer target handles (normal, light, albedo), empty when the geometry buffer was not written this frame.

#### GeometryBufferSettings
- Path: `src/main/java/com/aryston/helion/render/geometry/GeometryBufferSettings.java`
- Role: Player settings: enabled and the debug view. `withoutView()` for the parity check.

#### GeometryBufferView
- Path: `src/main/java/com/aryston/helion/render/geometry/GeometryBufferView.java`
- Role: Debug views `NONE`, `NORMALS`, `BLOCK_LIGHT`, `SKY_LIGHT`, `ALBEDO`, each with the id the debug shader is compiled with.

#### GeometryBufferPipelines
- Path: `src/main/java/com/aryston/helion/render/geometry/GeometryBufferPipelines.java`
- Role: Target indices and formats of the geometry buffer, sampler names and one debug view pipeline per view (`geometry/debug.fsh` with `HELION_GEOMETRY_VIEW`).
- Depends on: `HelionPipelines`.

#### GeometryBufferDebugStage
- Path: `src/main/java/com/aryston/helion/render/geometry/GeometryBufferDebugStage.java`
- Role: Last stage of a frame. When a geometry buffer view is selected and the buffer was written, draws the chosen value over the main target.
- Depends on: `GeometryBufferPipelines`, `FullscreenPass`.

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
- Role: Persistent ambient occlusion state owned by the core: the settings uniform ring, the fixed tuning values (falloff, distribution power, thin occluder compensation, final power, blur beta, max screen radius), the noise frame (advanced every frame through 64 frames while temporal anti-aliasing runs, 0 otherwise) and the one-time missing shader warning.
- Depends on: `UniformRing`.

#### AmbientOcclusionSettings
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionSettings.java`
- Role: Player settings: enabled, algorithm, quality, strength, radius, debug view.

#### AmbientOcclusionQuality
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionQuality.java`
- Role: Quality presets with slice count, steps per slice and denoise passes.

#### AmbientOcclusionAlgorithm
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionAlgorithm.java`
- Role: Horizon method of the main pass: `GTAO` (two horizon angles per slice, occluders of infinite thickness) or `VISIBILITY_BITMASK` (32 sectors per slice, occluders of fixed thickness). Each carries the id the shader switches on.

#### DeferredLightingStage
- Path: `src/main/java/com/aryston/helion/render/lighting/DeferredLightingStage.java`
- Role: Lights terrain from the geometry buffer. `deferred_lighting` writes the RGBA16_FLOAT light buffer `helion:light_buffer` (linear light in vanilla units without the vanilla cap, alpha the daylight reference, 0 where there is no terrain); `deferred_shading` multiplies it with the albedo and writes the scene with fog and chunk fade-in, or the light-only view. When the physical sky published a `SkyLight`, the light pass uses the physical sky variant and also reads the geometry buffer normals and the sky light target; when `ShadowStage` also published a shadow mask, the shading pass uses its shadowed variant, which also reads the mask, the normals and the sky light target and darkens the shaded color after the vanilla brightness curve. Inactive when lighting is off, the geometry buffer was not written, the scene color is not RGBA8 or a shader failed to compile.
- Depends on: `DeferredLightingResources`, `DeferredLightingPrograms`, `DeferredLightingPipelines`, `GeometryBuffer`, `FullscreenPass`.

#### DeferredLightingPipelines
- Path: `src/main/java/com/aryston/helion/render/lighting/DeferredLightingPipelines.java`
- Role: Pipeline definitions of the light pass, its physical sky variant (`HELION_PHYSICAL_SKY_LIGHT`, with the normal and sky light samplers), the shadowed shading and light-only variants (`HELION_PHYSICAL_SKY_LIGHT` and `HELION_SHADOWS`, with the normal, sky light and shadow mask samplers), the shading pass and the light-only view (`HELION_LIGHT_ONLY`), the uniform and sampler names they bind and the target formats.
- Depends on: `HelionPipelines`.

#### DeferredLightingPrograms
- Path: `src/main/java/com/aryston/helion/render/lighting/DeferredLightingPrograms.java`
- Role: The compiled lighting pipelines for one frame; empty when any of them is missing.

#### DeferredLightingResources
- Path: `src/main/java/com/aryston/helion/render/lighting/DeferredLightingResources.java`
- Role: Persistent lighting state owned by the core: the `HelionLighting` uniform ring (light environment, intensities, the sun and moon directions of the physical sky, zero without it, and the sun and moon shadow strengths, zero without shadows) and the one-time missing shader warning.
- Depends on: `UniformRing`, `LightEnvironment`, `DeferredLightingSettings`, `SkyLight`, `ShadowLight`.

#### DeferredLightingSettings
- Path: `src/main/java/com/aryston/helion/render/lighting/DeferredLightingSettings.java`
- Role: Player settings: enabled, block light intensity, sky light intensity, light-only view. `DISABLED` for the foundation.

#### LightEnvironment
- Path: `src/main/java/com/aryston/helion/render/lighting/LightEnvironment.java`
- Role: Per-frame copy of the vanilla light map inputs (sky and block factors, night vision, darkness, boss fog darkening, brightness option, block light tint, sky light, ambient and night vision colors), filled by the integration layer from `LightmapRenderState`.

### `com.aryston.helion.render.temporal`

#### TemporalStage
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalStage.java`
- Role: Temporal anti-aliasing after transparent geometry. `taa_resolve` blends the jittered scene with the reprojected history into the next history target, `taa_apply` writes the result back into the scene. Both history targets are imported into the frame graph. Inactive when the temporal frame is inactive or the scene color is not RGBA8.
- Depends on: `TemporalResources`, `TemporalPrograms`, `TemporalPipelines`, `FullscreenPass`.

#### TemporalResources
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalResources.java`
- Role: Persistent temporal state owned by the core: the jitter frame index, the previous view, the history targets, the `HelionTemporal` uniform ring and the one-time missing shader warning. `begin` returns the `TemporalFrame` of a level frame (jittered projection, reprojection, whether the history is usable); the history is dropped after a resize, a jump of 16 blocks or more, a frame without temporal anti-aliasing and every vanilla frame (`invalidate`).
- Depends on: `TemporalHistory`, `PreviousView`, `JitterSequence`, `TemporalPrograms`, `UniformRing`, `HelionCamera`.

#### TemporalFrame
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalFrame.java`
- Role: Per-frame temporal data: active, the jittered level projection, the reprojection matrix from current device coordinates to the previous frame, history valid. `INACTIVE` for frames without temporal anti-aliasing.

#### PreviousView
- Path: `src/main/java/com/aryston/helion/render/temporal/PreviousView.java`
- Role: Unjittered projection, view rotation and camera position of the previous frame. `reprojection` builds the matrix that maps current device coordinates and depth to the previous frame, including the camera movement and the current jitter.

#### JitterSequence
- Path: `src/main/java/com/aryston/helion/render/temporal/JitterSequence.java`
- Role: Sub-pixel camera offsets from the Halton (2, 3) sequence, eight per cycle, in pixels and in device coordinates.

#### TemporalHistory
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalHistory.java`
- Role: Two persistent RGBA16_FLOAT history targets used in turn, recreated on resize and registered in `GpuResources`.
- Depends on: `GpuResources`.

#### TemporalPipelines
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalPipelines.java`
- Role: Pipeline definitions of the resolve and apply passes, the uniform and sampler names they bind and the target formats.
- Depends on: `HelionPipelines`.

#### TemporalPrograms
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalPrograms.java`
- Role: The compiled temporal pipelines; empty when any of them is missing.

#### TemporalSettings
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalSettings.java`
- Role: Player setting: enabled. `DISABLED` for the foundation.

### `com.aryston.helion.render.shader`

#### HelionPipelines
- Path: `src/main/java/com/aryston/helion/render/shader/HelionPipelines.java`
- Role: Registry of every Helion pipeline (ambient occlusion, deferred lighting, temporal, physical sky, shadow mask, image and geometry buffer views), builder for fullscreen pipelines on the vanilla screen quad, lookup of compiled pipelines.

#### FullscreenPass
- Path: `src/main/java/com/aryston/helion/render/shader/FullscreenPass.java`
- Role: Draws one fullscreen triangle with a pipeline and its bindings into one target, or into several targets at once (one color attachment per target, for pipelines with several color targets).

### `com.aryston.helion.render.atmosphere`

#### AtmosphereSource
- Path: `src/main/java/com/aryston/helion/render/atmosphere/AtmosphereSource.java`
- Role: Contract for sky, clouds, weather and world border. For the physical sky it also provides the `SkyEnvironment` (only in Overworld style skies without a custom sky renderer and with the camera in air), replaces the fog buffer the rest of the frame uses (`useFog`) and draws only the sun, moon and stars (`renderCelestials`).

#### SkyStage
- Path: `src/main/java/com/aryston/helion/render/atmosphere/SkyStage.java`
- Role: Draws the sky into the scene target when the frame renders sky and the source has a sky. Used when the physical sky is off or not available.
- Depends on: `AtmosphereSource`.

#### PhysicalSkyStage
- Path: `src/main/java/com/aryston/helion/render/atmosphere/PhysicalSkyStage.java`
- Role: Physical sky in place of `SkyStage`. Writes the constant transmittance and multiple scattering tables once into persistent targets (four spectral targets each, one multiple render target pass per table), builds the sun and moon sky view tables every frame (two targets each: scattering with its phase and Mie without it), renders the 1 x 1 average horizon color and requests its readback, renders the 3 x 1 sky light target and publishes it as `SkyLight`, hands the fog buffer without distance fog to the source, draws the sky per pixel into the scene with the vanilla sun, moon and stars on top, and publishes the distance fog pass (`SceneFogPass`) for `TransparentGeometryStage`. Inactive when the setting is off, the frame has no sky, the source has no `SkyEnvironment`, the scene color is not RGBA8 or a shader failed to compile.
- Depends on: `AtmosphereSource`, `PhysicalSkyResources`, `PhysicalSkyPrograms`, `PhysicalSkyPipelines`, `AtmosphereResults`, `SkyLight`, `SceneFogPass`, `FullscreenPass`.

#### PhysicalSkyResources
- Path: `src/main/java/com/aryston/helion/render/atmosphere/PhysicalSkyResources.java`
- Role: Persistent sky state owned by the core: the spectral transmittance and multiple scattering targets (written once, registered in `GpuResources`), the constant `HelionSpectrum` buffer (Rayleigh and ozone coefficients and the spectrum to RGB weights from `AtmosphereSpectrum`), the `HelionSky` uniform ring (inverse level view projection, sun and moon directions and brightness, view height, rain, vanilla fog distances), the `Fog` buffer the frame uses under the physical sky (`writeFog`: the read back horizon color, or the vanilla fog color until the first readback, no distance fog, vanilla cloud fog end), the `HorizonColorReadback`, the sun and moon brightness tuning and the one-time missing shader warning. `viewHeight` turns the altitude above sea level into the distance from the planet center.
- Depends on: `UniformRing`, `GpuResources`, `AtmosphereSpectrum`, `HorizonColorReadback`, `SkyEnvironment`, `AtmosphereFog`, `HelionCamera`.
- Notes: the `Fog` buffer has the layout of vanilla `FogRenderer` (`FOG_UBO_SIZE`); terrain, entities and clouds read it through `fog.glsl`.

#### PhysicalSkyPipelines
- Path: `src/main/java/com/aryston/helion/render/atmosphere/PhysicalSkyPipelines.java`
- Role: Pipeline definitions of the lookup table passes (four color targets each), the sun and moon sky view passes (`HELION_MOON_VIEW`, two color targets: scattering and unphased Mie), the sky, fog, horizon and sky light passes, their uniform and sampler names, target sizes and formats.
- Depends on: `HelionPipelines`, `AtmosphereSpectrum`.

#### PhysicalSkyPrograms
- Path: `src/main/java/com/aryston/helion/render/atmosphere/PhysicalSkyPrograms.java`
- Role: The compiled physical sky pipelines; empty when any of them is missing.

#### PhysicalSkySettings
- Path: `src/main/java/com/aryston/helion/render/atmosphere/PhysicalSkySettings.java`
- Role: Player setting: enabled. `DISABLED` for the foundation.

#### SkyEnvironment
- Path: `src/main/java/com/aryston/helion/render/atmosphere/SkyEnvironment.java`
- Role: Per-frame sky inputs: sun and moon directions in world space, rain brightness, camera altitude above sea level in blocks and the vanilla fog values (`AtmosphereFog`). `celestialDirection` turns a vanilla sun or moon angle into the direction vanilla draws the body in.

#### AtmosphereSpectrum
- Path: `src/main/java/com/aryston/helion/render/atmosphere/AtmosphereSpectrum.java`
- Role: Spectral data of the physical sky: 16 wavelengths from 400 to 700 nm in steps of 20 nm, packed in four groups of four. Rayleigh scattering and ozone absorption per wavelength, solar irradiance, CIE 1931 color matching integrated over each 20 nm band, and `toLinearSrgb`, the weights that turn a spectrum into linear sRGB, white balanced so that sunlight is white.
- Notes: the tables and parameters come from Bruneton's Precomputed Atmospheric Scattering, see `THIRD_PARTY_NOTICES.md`.

#### AtmosphereFog
- Path: `src/main/java/com/aryston/helion/render/atmosphere/AtmosphereFog.java`
- Role: Copy of the vanilla fog values of a frame: color, environmental, render distance, sky and cloud fog distances.

#### AtmosphereResults
- Path: `src/main/java/com/aryston/helion/render/atmosphere/AtmosphereResults.java`
- Role: Per-frame results of the physical sky for later stages: the `SkyLight` for deferred lighting and the `SceneFogPass` for transparent geometry, both empty without the physical sky.

#### SkyLight
- Path: `src/main/java/com/aryston/helion/render/atmosphere/SkyLight.java`
- Role: Handle of the 3 x 1 sky light target (sun and moon light that reaches the ground through the atmosphere, and the irradiance of the sky dome on an upward surface) with the sun and moon directions.

#### SceneFogPass
- Path: `src/main/java/com/aryston/helion/render/atmosphere/SceneFogPass.java`
- Role: Contract of the distance fog of the physical sky: `declareReads` adds its inputs to the frame graph pass that draws it, `draw` blends the fog into the scene.

#### HorizonColorReadback
- Path: `src/main/java/com/aryston/helion/render/atmosphere/HorizonColorReadback.java`
- Role: Reads the 1 x 1 horizon color back to the CPU without stalling, through three mappable buffers used in turn; `latest` is the newest finished value, a few frames old. The color feeds the `Fog` buffer, so clouds fade into the horizon color.
- Depends on: `GpuResources`.

### `com.aryston.helion.render.shadow`

#### ShadowStage
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowStage.java`
- Role: Sun and moon shadows. `shadow_map` draws the solid and cutout terrain of every cascade that `ShadowCascadeCache` marks as due into that cascade's own depth-only target, one render pass each without a color attachment, and leaves the other targets as they are; `shadow_mask` reconstructs every terrain pixel from scene depth, picks the first cascade that holds it (with the matrix the cascade was drawn with, moved by the camera movement since then), offsets it along the normal and toward the light, filters the shadow map with a 4 x 4 tent and writes the R8 visibility mask `helion:shadow_mask` (1 lit, 0 shadowed), fading to lit toward the shadow distance. Publishes the mask and the `ShadowLight` in `ShadowResults`. Inactive when shadows or lighting are off, the geometry buffer was not written, the physical sky published no `SkyLight`, the sun and moon are both below the horizon, the scene color is not RGBA8, the source cannot cast shadows or a shader failed to compile.
- Depends on: `ShadowCasterSource`, `ShadowResources`, `ShadowCascades`, `ShadowLight`, `ShadowPipelines`, `ShadowResults`, `GeometryBuffer`, `FullscreenPass`.

#### ShadowCascades
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowCascades.java`
- Role: Splits the view up to the shadow distance (the render distance, at most 160 blocks) into four cascades at 8, 22, 50 and 100 %, fits a bounding sphere around each slice (radius rounded to 1/16 block so it does not change while turning), and builds an orthographic light projection whose center is snapped to whole shadow map texels in world space, so shadow edges never crawl while the camera moves. The depth range reaches 256 blocks toward the light, so casters outside the view still cast.
- Members: `COUNT`, `compute(...)`, `distance(renderDistanceBlocks)`.

#### ShadowCascadeCache
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowCascadeCache.java`
- Role: Remembers every cascade as it was last drawn (cascade, camera position, light direction) and decides which cascades are drawn in a frame: the two near ones every frame, the third every second and the far one every fourth frame, plus any cascade that was never drawn, whose radius changed, whose center moved by more than a quarter of its radius or whose light turned by more than 0.5°; a change of quality or between sun and moon redraws all. Gives the mask the matrix of each cascade moved by the camera movement since it was drawn.
- Members: `update(cascades, camera, light, resolution)` returns the indices to draw and records them, `maskMatrix(index, camera)`, `texelSize(index)`, `clear()`.
- Depends on: `ShadowCascade`, `ShadowCascades`, `ShadowLight`.

#### ShadowCascade
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowCascade.java`
- Role: One cascade: light view, orthographic projection, light space center, radius, caster reach and texel size. `containsLightSpace` tells whether a chunk section is inside it or between it and the light; `expandCameraRelativeBounds` grows a camera relative box to hold everything `containsLightSpace` accepts, so the caster search scans only those sections.

#### ShadowLight
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowLight.java`
- Role: The light that casts shadows this frame: the sun while it is above the horizon, otherwise the moon. Its strength fades smoothly to zero between 0.12 and 0.02 of elevation, so shadows never pop at sunrise and sunset; `sunStrength` and `moonStrength` feed the lighting block.

#### ShadowCasterSource
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowCasterSource.java`
- Role: Contract for drawing shadow casters: `supportsShadows`, `renderDistanceBlocks`, `prepare(cascades)` once per frame with the cascades drawn in that frame and `renderCascade(slot, pass)` inside the shadow map pass, where `slot` is the position in that list.

#### ShadowResources
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowResources.java`
- Role: Persistent shadow state owned by the core: one D32_FLOAT shadow map per cascade (recreated when the quality changes, which also clears the cache, registered in `GpuResources`), the `ShadowCascadeCache`, one projection uniform ring per cascade, the `HelionShadow` uniform ring (inverse level view projection, the cached cascade matrices and texel sizes, light direction, shadow distance, cascade resolution) and the one-time missing shader warning.
- Depends on: `GpuResources`, `UniformRing`, `ShadowCascades`, `ShadowCascadeCache`, `HelionCamera`.

#### ShadowPipelines
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowPipelines.java`
- Role: The shadow mask pipeline, the caster depth state with slope and constant depth bias, the uniform and sampler names and the shadow map and mask formats.
- Depends on: `HelionPipelines`.

#### ShadowQuality
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowQuality.java`
- Role: Cascade resolution presets: `LOW` 1024, `MEDIUM` 1536, `HIGH` 2048 texels per cascade.

#### ShadowSettings
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowSettings.java`
- Role: Shadow settings snapshot: enabled and quality.

#### ShadowResults
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowResults.java`
- Role: What `ShadowStage` hands to deferred lighting in one frame: the shadow mask handle and the `ShadowLight`.

### `com.aryston.helion.render.post`

#### PostProcessingStage
- Path: `src/main/java/com/aryston/helion/render/post/PostProcessingStage.java`
- Role: Runs its active post effects in order: the effects of the frame sources (vanilla entity outline) first, then `BloomStage`, then `SharpeningStage`.

#### PresentStage
- Path: `src/main/java/com/aryston/helion/render/post/PresentStage.java`
- Role: Copies scene color and depth into the real main target at the end of the frame. Used when no image effect is active, so foundation frames stay identical to vanilla.

#### BloomStage
- Path: `src/main/java/com/aryston/helion/render/post/BloomStage.java`
- Role: Post effect that builds the bloom mip chain: `bloom_prefilter` (scene to half resolution with emissive detection, adaptive threshold and Karis average), `bloom_down_N` (13-tap downsample) and `bloom_up_N` (tent upsample added onto the next larger mip). Publishes the half resolution result in `PostResults`. Inactive when bloom is off, the scene color is not RGBA8 or a shader failed to compile.
- Depends on: `ImageResources`, `ImagePrograms`, `ImagePipelines`, `PostSampling`, `FullscreenPass`.

#### ImageCompositeStage
- Path: `src/main/java/com/aryston/helion/render/post/ImageCompositeStage.java`
- Role: Output stage used instead of `PresentStage` when bloom, sharpening, exposure or Filmic tone mapping is active. Expands the RGBA8 scene (the sharpened copy when sharpening is on) to HDR, adds bloom, applies exposure, tone mapping and dither, writes the main target in one fullscreen pass and copies depth. Shows only the bloom in the debug view.
- Depends on: `ImageResources`, `ImagePrograms`, `ImagePipelines`, `PostSampling`, `FullscreenPass`.

#### ImagePipelines
- Path: `src/main/java/com/aryston/helion/render/post/ImagePipelines.java`
- Role: Pipeline definitions of the bloom, sharpen and composite passes, the uniform and sampler names they bind and the target formats (RGBA16_FLOAT bloom mips, RGBA8 output).
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
- Role: Player image settings: tone mapper, exposure in stops, dither, bloom and sharpening. `needsComposite()` tells whether the output must go through `ImageCompositeStage`. `FOUNDATION` keeps the plain copy.
- Depends on: `ToneMapper`, `BloomSettings`.

#### BloomSettings
- Path: `src/main/java/com/aryston/helion/render/post/BloomSettings.java`
- Role: Player bloom settings: enabled, intensity, threshold, debug view.

#### SharpeningSettings
- Path: `src/main/java/com/aryston/helion/render/post/SharpeningSettings.java`
- Role: Player sharpening settings: enabled, strength. Off by default.

#### SharpeningStage
- Path: `src/main/java/com/aryston/helion/render/post/SharpeningStage.java`
- Role: Post effect that writes a contrast adaptive sharpened copy of the scene into `helion:sharpened` (full resolution, RGBA8) and publishes it in `PostResults`, so `ImageCompositeStage` reads it instead of the scene. Inactive when sharpening is off, the scene color is not RGBA8 or a shader failed to compile.
- Depends on: `ImageResources`, `ImagePrograms`, `ImagePipelines`, `PostSampling`, `FullscreenPass`.

#### ToneMapper
- Path: `src/main/java/com/aryston/helion/render/post/ToneMapper.java`
- Role: Tone mapping choices `NEUTRAL` (Khronos PBR Neutral), `FILMIC` (Neutral plus a film grade) and `NONE`, each with the id the shaders switch on.

#### PostResults
- Path: `src/main/java/com/aryston/helion/render/post/PostResults.java`
- Role: Per-frame results that post stages hand to each other: the bloom target handle, the sharpened scene handle and the image uniform buffer written this frame.

#### PostSampling
- Path: `src/main/java/com/aryston/helion/render/post/PostSampling.java`
- Role: Texture view and clamp-to-edge sampler helpers shared by the image stages.

### `com.aryston.helion.integration`

#### ClientEvents
- Path: `src/main/java/com/aryston/helion/integration/ClientEvents.java`
- Role: Wires every listener: client setup (compatibility check, passive notices), pipeline registration (including the geometry and shadow caster terrain pipelines), config loading, toggle and parity keys, notices and the debug log on client tick, the parity tick counter, debug log events for toggles and passive mode, shutdown.
- Depends on: `HelionRenderCore`, `HelionKeys`, `ParityControl`, `CompatibilityGuard`, `PassiveModeNotice`, `HelionDebugEntry`, `HelionDebugLog`, `VisualTest`, `LevelRenderHook`.

#### ParityControl
- Path: `src/main/java/com/aryston/helion/integration/ParityControl.java`
- Role: Starts the parity check from the parity key (debug mode only), shows its result in chat and writes it to the debug log. A failed result points to the difference image and, when ticks were not frozen at the start, explains that clouds, particles and entities moved between the frames.
- Depends on: `ParityCheck`, `HelionDebugLog`, `HelionRenderCore`.

#### HelionKeys
- Path: `src/main/java/com/aryston/helion/integration/HelionKeys.java`
- Role: "Helion" key category, the toggle key (default `H`) and the parity key (default `J`, only acts in debug mode).

#### CompatibilityGuard
- Path: `src/main/java/com/aryston/helion/integration/CompatibilityGuard.java`
- Role: Lists installed renderer mods (Sodium, Iris) that make Helion passive.

#### PassiveModeNotice
- Path: `src/main/java/com/aryston/helion/integration/PassiveModeNotice.java`
- Role: Queues passive reasons and shows them as toasts once the GUI exists; also shows the toggle toast and builds its enabled or disabled text. The queue is thread safe because the incompatible mod check runs on a parallel setup thread.

### `com.aryston.helion.integration.vanilla`

#### LevelRenderHook
- Path: `src/main/java/com/aryston/helion/integration/vanilla/LevelRenderHook.java`
- Role: Entry from the mixin. Chooses the Helion or vanilla frame and the render settings (`settings().foundation()` while the parity check captures), catches Helion failures, feeds the parity check after every level frame together with whether Helion really rendered it, and pauses GPU timing and drops the temporal history on vanilla frames.
- Depends on: `VanillaFrameDriver`, `HelionRenderCore`, `ParityCheck`.

#### LevelFrameRequest
- Path: `src/main/java/com/aryston/helion/integration/vanilla/LevelFrameRequest.java`
- Role: Arguments of one `LevelRenderer.render` call plus typed access to the accessor mixin.

#### VanillaFrameDriver
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaFrameDriver.java`
- Role: Rebuilds `LevelRenderer.render` with Helion stages: the vanilla sky renderer created as `addSkyPass` does (vanilla extracts the sky state only while it exists), feature preparation, targets, NeoForge frame graph event, chunk draw preparation, the scene snapshot with the light map inputs, the camera with the captured level projection, the temporal frame with the jittered projection uploaded for the level (and put into the camera) and restored afterwards, the frame sources sharing one `SceneFog`, stage building, graph execution, then section compile, upload and occlusion update.
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
- Role: `TerrainSource` over vanilla `ChunkSectionsToRender`: fog (the current `SceneFog` buffer, so the physical sky fog reaches terrain), chunk sampler, opaque and translucent layer groups, matching stage events. `renderOpaqueGeometry` draws the solid and cutout layers one by one with the geometry pipelines through `ChunkSectionsToRenderAccessor`; `supportsGeometryBuffer` is false for wireframe terrain or missing shaders.

#### VanillaGeometryPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaGeometryPipelines.java`
- Role: Geometry buffer terrain pipelines per chunk layer (direct and multi-draw), copied from the vanilla terrain pipelines with `toBuilder()` and switched to `helion:terrain/geometry` with three more color targets. Registered as optional pipelines; warns once when they did not compile.
- Depends on: `GeometryBufferPipelines`, `HelionPipelines`.

#### VanillaShadowCasterSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaShadowCasterSource.java`
- Role: `ShadowCasterSource` over vanilla chunk sections. Collects every built section within the render distance that lies in or toward the light of a cascade drawn this frame, scanning only the sections inside the bounds of those cascades, temporarily swaps them into `LevelRenderer.visibleSections` and lets vanilla prepare one `ChunkSectionsToRender` per cascade with the light view, then restores the camera's sections. It prepares through the same path as the camera terrain (multi-draw indirect or direct), because vanilla closes the chunk section buffer of the other path when it switches. Draws the solid and cutout layers with the caster pipelines.
- Depends on: `VanillaShadowPipelines`, `ChunkSectionsToRenderAccessor`, `LevelRendererAccessor`.

#### VanillaShadowPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaShadowPipelines.java`
- Role: Depth-only shadow caster pipelines per chunk layer (direct and multi-draw), built from the vanilla terrain snippets without color targets, with the vanilla alpha cutout define, `helion:shadow/caster` shaders and the caster depth bias. Registered as optional pipelines; warns once when they did not compile.
- Depends on: `ShadowPipelines`, `HelionPipelines`, `VanillaGeometryPipelines.LayerPipelines`.

#### VanillaEntitySource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaEntitySource.java`
- Role: `EntitySource` over the vanilla prepared feature frame, including outline, see-through and always-on-top passes.

#### VanillaAtmosphereSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaAtmosphereSource.java`
- Role: `AtmosphereSource` over vanilla sky, cloud, weather and world border renderers, honoring NeoForge custom sky, cloud and weather renderers. Builds the `SkyEnvironment` from the vanilla sky state (sun and moon angles, rain, camera height above sea level, vanilla fog values) only for the Overworld skybox without a custom sky renderer and with the camera outside water, lava and powder snow. `useFog` swaps the fog buffer in the shared `SceneFog`, and `renderSky` and `renderCelestials` draw the vanilla sky through `HelionSky` (the second with only the celestial bodies).
- Depends on: `SceneSkyRenderer`, `SceneFog`, `StageEvents`, `HelionSky`.

#### SceneFog
- Path: `src/main/java/com/aryston/helion/integration/vanilla/SceneFog.java`
- Role: The fog buffer of one frame, shared by the terrain and atmosphere sources. Starts as the vanilla terrain fog; the physical sky replaces it with its own buffer, so terrain, entities, sky renderers and clouds drawn later in the frame use it.

#### HelionSky
- Path: `src/main/java/com/aryston/helion/integration/vanilla/HelionSky.java`
- Role: Marks the vanilla sky rendering Helion runs, and whether it only draws the celestial bodies on top of the physical sky, so `SkyRendererMixin` skips the vanilla sky disc and sunrise glow then. `moonColor` dims the moon to 60 percent while Helion draws the sky, so it no longer blooms like the sun and the two stay easy to tell apart. Render thread only.

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

#### HelionDebugSnapshot
- Path: `src/main/java/com/aryston/helion/debug/HelionDebugSnapshot.java`
- Role: Single source of every debug value, as a JSON object with one section per topic: `core` (version, active, enabled, debug mode, passive reason, parity running), `device`, `frame` (fps, window and scene size, color format, sky, improved transparency, ambient light, fog color), `camera`, `ambientOcclusion`, `image`, `bloom` (with the effective threshold), `sharpening`, `geometryBuffer`, `lighting`, `temporal`, `physicalSky`, `shadows`, `gpu` (total), `gpuStages` (milliseconds per pass), `resources`, `resourceList` (MiB per label) and `frustum` (Helion versus vanilla visible sections). Numbers are rounded to three decimals.
- Depends on: `HelionRenderCore`, `ParityCheck`, `ImageResources`.

#### HelionDebugEntry
- Path: `src/main/java/com/aryston/helion/debug/HelionDebugEntry.java`
- Role: Debug screen entry registered as always on. Shows nothing unless debug mode is on; then shows every value of `HelionDebugSnapshot` on screen without F3.
- Depends on: `HelionDebugSnapshot`, `DebugOverlayLines`.
- Notes: the entry id is `helion:debug_mode`; players can still hide it in the vanilla debug options screen.

#### DebugOverlayLines
- Path: `src/main/java/com/aryston/helion/debug/DebugOverlayLines.java`
- Role: Turns a snapshot into screen lines: `Helion <section>: key=value, …` with at most four values per line and indented continuation lines.

#### HelionDebugLog
- Path: `src/main/java/com/aryston/helion/debug/HelionDebugLog.java`
- Role: JSON Lines writer for debug mode. Writes a `snapshot` line every 20 client ticks while a world is open and event lines (`toggle`, `passive`, `parity`) when they happen, each as `{"time", "type", "data"}`, to `logs/helion-debug.jsonl` in the game folder. The file is recreated on the first line of every game start. Does nothing while debug mode is off; stops writing after an I/O error and says so once in the game log.
- Depends on: `HelionDebugSnapshot`, `HelionRenderCore`.

#### VisualTest
- Path: `src/main/java/com/aryston/helion/debug/VisualTest.java`
- Role: Automated visual test that runs when the JVM property `helion.visualTest=true` is set in a development run. Before preparing the world it checks that Helion extracted the sky state of the dimension although no vanilla frame was drawn yet (a `WARN` otherwise, because Helion would draw no sky), then prepares the world, then for every scene and variant sets the time, holds the camera, applies the variant's settings, waits for the frame to settle, saves a half resolution screenshot and logs the full debug snapshot as JSON. Debug mode is on during the test so GPU times are measured. The parity variant freezes ticks and runs `ParityCheck` instead, logging a pass or a `WARN` with the differing pixels. Restores the settings and the HUD and closes the game cleanly at the end.
- Depends on: `VisualTestScene`, `VisualTestVariant`, `VisualTestWorld`, `ParityCheck`, `HelionDebugSnapshot`, `HelionRenderCore`, `HelionConfig`, `LevelRendererAccessor`.
- Notes: changes blocks, time and game rules of the loaded world; run it on a copy of a save only.

#### VisualTestScene
- Path: `src/main/java/com/aryston/helion/debug/VisualTestScene.java`
- Role: The photographed scenes: name, time of day, camera yaw and pitch. `dawn_sun` and `dawn_away` look toward and away from the rising sun, so a sky color that depends on the view direction shows up as a difference between them. `day_showcase` and `morning_showcase` show the platform at noon and in the morning, so shadows fall under the roof and beside the pillar.

#### VisualTestVariant
- Path: `src/main/java/com/aryston/helion/debug/VisualTestVariant.java`
- Role: Render variants per scene: vanilla, None, Neutral, Filmic, bloom only, lighting and light only (geometry buffer and deferred lighting on, every other effect off), temporal (only temporal anti-aliasing on), physical sky (only the physical sky on), shadows and shadow light (geometry buffer, deferred lighting, physical sky and medium shadows on, the second as light-only view), all for comparison with vanilla, and parity, each turning the core on or off and setting default image settings.

#### VisualTestWorld
- Path: `src/main/java/com/aryston/helion/debug/VisualTestWorld.java`
- Role: Server commands of the visual test: freezes time and weather, stops mob spawning, switches to spectator, clears the area around and in front of the player (so things built around the save spawn never block the view), builds a stone platform with light blocks in front of the player plus a stone pillar and a floating stone roof that cast shadows away from any light block, sets the time per scene and freezes ticks for the parity check.

#### ParityCheck
- Path: `src/main/java/com/aryston/helion/debug/ParityCheck.java`
- Role: State machine behind the parity key and the visual test: renders three warm-up vanilla frames and three warm-up Helion frames, then captures a vanilla frame and the Helion frame right after it, compares when both readbacks finish. Both captures must come from the same client tick, because the torch flicker changes the light map every tick even with ticks frozen; if a tick passed in between, the pair is captured again (up to 20 times). `clientTick()` is called from the client tick event. Warm-up keeps cold first-use resources out of the comparison. Helion frames use `RenderSettings.foundation()`, so effects never count as differences. If a Helion frame is not rendered by Helion (core turned off or a render failure), the check aborts and reports that instead of comparing two vanilla frames. A failed comparison writes `ParityDifferenceImage` to `screenshots/` on the IO pool.

#### FrameCapture
- Path: `src/main/java/com/aryston/helion/debug/FrameCapture.java`
- Role: Copies a render target's color and depth into readback buffers and keeps the bytes and the frame size. `differencesTo` builds the difference image of two captures.

#### ParityResult
- Path: `src/main/java/com/aryston/helion/debug/ParityResult.java`
- Role: Pixel comparison result: total pixels, differing color pixels, largest channel difference, differing depth pixels, largest depth difference in steps of the depth format (units in the last place for `D32_FLOAT`), and how many color differences also differ in depth.

#### ParityDifferenceImage
- Path: `src/main/java/com/aryston/helion/debug/ParityDifferenceImage.java`
- Role: Writes `helion_parity_differences.png`: the vanilla frame darkened, pixels that differ only in color red, in color and depth yellow, only in depth blue.
- Depends on: `ParityResult`.

### `com.aryston.helion.mixin`

#### LevelRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/LevelRendererMixin.java`
- Role: Wraps `LevelRenderer.render` to hand the frame to `LevelRenderHook`, and wraps `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` so they resolve to the scene target during a Helion frame.

#### GameRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/GameRendererMixin.java`
- Role: Hands the level projection matrix that `GameRenderer.renderLevel` uploads (camera projection with view bobbing and nausea distortion) to `HelionRenderCore`, unchanged; frames read it as `HelionCamera.levelProjection`.
- Notes: no event exposes the final level projection, and temporal anti-aliasing must jitter and reproject exactly the matrix the level is drawn with.

#### SkyRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/SkyRendererMixin.java`
- Role: Cancels `SkyRenderer.renderSkyDisc` and `renderSunriseAndSunset` while `HelionSky` draws only the celestial bodies, so only the sun, moon, stars and dark disc are drawn over the physical sky, and dims the color of `renderMoon` through `HelionSky.moonColor` whenever Helion draws the sky.
- Notes: `SkyRenderer.render` draws the sky color and the celestial bodies in one call; no API draws the celestial bodies alone, and keeping the vanilla sun, moon phases and stars avoids copying their textures and geometry.

#### ChunkSectionsToRenderAccessor
- Path: `src/main/java/com/aryston/helion/mixin/ChunkSectionsToRenderAccessor.java`
- Role: Invoker for the private `ChunkSectionsToRender.renderLayers`, the only vanilla entry that takes per layer pipeline overrides.

#### LevelRendererAccessor
- Path: `src/main/java/com/aryston/helion/mixin/LevelRendererAccessor.java`
- Role: Accessors and invokers for private `LevelRenderer` fields, constants and methods needed to rebuild `render`, including the setter of the vanilla `skyRenderer` that `VanillaFrameDriver` creates like vanilla does, and the visible sections, view area and multi-draw state the shadow casters need.

## Tests

Plain JUnit 5 tests without a running game, run by `./gradlew build` and the CI. Each test class sits in the package of the class it checks.

| File | What it checks |
|---|---|
| `src/test/java/com/aryston/helion/LanguageFilesTest.java` | Every language file has the same keys, every config entry has a `.tooltip` and every config section a `.button` translation (lesson L-011). |
| `src/test/java/com/aryston/helion/render/graph/RenderSettingsTest.java` | `foundation()` turns every effect off including deferred lighting, never needs the composite and keeps the geometry buffer without its view; `OFF` renders nothing extra. |
| `src/test/java/com/aryston/helion/render/post/ImageSettingsTest.java` | `needsComposite()` for every tone mapper and effect combination. |
| `src/test/java/com/aryston/helion/render/post/ImageResourcesTest.java` | Adaptive bloom threshold: half in darkness, eight times in daylight, rising with ambient light. |
| `src/test/java/com/aryston/helion/render/post/ToneMapperTest.java` | Tone mapper shader ids are unique and match the constants in `helion_image.glsl`. |
| `src/test/java/com/aryston/helion/render/camera/HelionFrustumTest.java` | Frustum culling with OpenGL, zero to one and reversed-Z projections. |
| `src/test/java/com/aryston/helion/render/atmosphere/AtmosphereSpectrumTest.java` | The wavelengths cover 400 to 700 nm, Rayleigh and ozone coefficients agree with Hillaire's published RGB values within 1 %, sunlight converts to white, short wavelengths lean blue and long ones red, and the color matching bands integrate the CIE table. |
| `src/test/java/com/aryston/helion/render/atmosphere/PhysicalSkyTest.java` | Celestial directions (overhead at angle zero, rising and setting on opposite sides), view height above the planet from the altitude, and the planet radius and lookup table sizes match `helion_atmosphere.glsl`. |
| `src/test/java/com/aryston/helion/render/shadow/ShadowCascadeCacheTest.java` | Near cascades are drawn every frame and far cascades take turns, a waiting cascade still shadows the same place in the world after the camera moved, and a camera jump, a turned light or switching from the sun to the moon redraws every cascade. |
| `src/test/java/com/aryston/helion/render/shadow/ShadowCascadesTest.java` | Every cascade holds its slice of the view, a moving camera shifts the map by whole texels, the camera relative bounds hold every section a cascade selects, casters toward the light stay inside the depth range, sections near a cascade are selected, the shadow distance follows the render distance, the sun casts by day and the moon by night, and shadows fade near the horizon. |
| `src/test/java/com/aryston/helion/render/temporal/JitterSequenceTest.java` | Halton values, jitter offsets inside the pixel, repetition after eight frames, mean near the pixel center, device coordinate scale. |
| `src/test/java/com/aryston/helion/render/temporal/PreviousViewTest.java` | The reprojection matrix maps a point seen by a moved and turned camera to where the previous camera saw it (plus the jitter), and keeps every pixel in place for a still camera. |
| `src/test/java/com/aryston/helion/render/shader/ShaderCompilationTest.java` | Every vertex and fragment shader compiles with `glslangValidator` in three variants (default, zero to one depth, multi-draw with alpha cutout), with Helion and vanilla includes inlined from the classpath. Skipped when `glslangValidator` is not installed; the CI installs it. |
| `src/test/java/com/aryston/helion/render/lighting/AmbientOcclusionQualityTest.java` | Higher quality presets never use fewer samples or denoise passes. |
| `src/test/java/com/aryston/helion/render/lighting/AmbientOcclusionAlgorithmTest.java` | Algorithm shader ids are unique and match the constants in `helion_ambient_occlusion.glsl`. |
| `src/test/java/com/aryston/helion/debug/ParityResultTest.java` | Pixel comparison counts color and depth differences, the largest channel and depth differences and color differences that also differ in depth. |
| `src/test/java/com/aryston/helion/debug/DebugOverlayLinesTest.java` | Debug screen lines: one line per section, wrapping after four values, empty sections and plain values. |

## Source Files

| File | Purpose |
|---|---|
| `src/main/templates/META-INF/neoforge.mods.toml` | Mod metadata template filled from `gradle.properties`: dependencies (Arkea required, so the game refuses to start without it), discouraged Sodium and Iris, mixin config. |
| `src/main/resources/helion.mixins.json` | Mixin configuration listing `ChunkSectionsToRenderAccessor`, `GameRendererMixin`, `LevelRendererAccessor`, `LevelRendererMixin` and `SkyRendererMixin`. |

## Asset Folders

| Folder | Contents |
|---|---|
| `src/main/resources/assets/helion/lang/` | `en_us.json` and `tr_tr.json`: config, settings screen (`helion.config.*`), key, toast and parity check texts. |
| `src/main/resources/assets/helion/textures/gui/` | `logo.png` (48 x 48, linear filtered through `logo.png.mcmeta`) for the settings sidebar, and `preview/`: placeholder before and after images of ambient occlusion, bloom, shadows and the physical sky from the design handoff, to be replaced with real captures. |
| `src/main/resources/assets/helion/shaders/ambient_occlusion/` | Ambient occlusion fragment shaders: `view_depth`, `gtao`, `denoise`, `denoise_resolve`, `apply`. |
| `src/main/resources/assets/helion/shaders/image/` | Bloom and image fragment shaders: `bloom_prefilter`, `bloom_downsample`, `bloom_upsample`, `composite`, `bloom_debug`, `sharpen`. |
| `src/main/resources/assets/helion/shaders/terrain/` | Geometry buffer terrain shaders `geometry.vsh` and `geometry.fsh`: vanilla terrain color plus normal, light (with chunk fade-in) and albedo (with the fog amount in alpha) targets. |
| `src/main/resources/assets/helion/shaders/geometry/` | `debug.fsh`: geometry buffer debug views. |
| `src/main/resources/assets/helion/shaders/lighting/` | Deferred lighting fragment shaders: `deferred_light` (light buffer from the geometry buffer light levels; with `HELION_PHYSICAL_SKY_LIGHT` the sky light color follows the physical sky light per pixel normal at the vanilla brightness,) and `deferred_shading` (albedo times light, HDR above the vanilla cap, fog and chunk fade-in; light-only view with `HELION_LIGHT_ONLY`; with `HELION_SHADOWS` the color is darkened by the shadow transmission after the vanilla brightness curve). |
| `src/main/resources/assets/helion/shaders/temporal/` | Temporal anti-aliasing fragment shaders: `resolve` (closest-depth reprojection, Catmull-Rom history sample, YCoCg variance clipping, luminance weighted blend) and `apply` (history back into the scene). |
| `src/main/resources/assets/helion/shaders/atmosphere/` | Physical sky fragment shaders: `transmittance` and `multiple_scattering` (constant spectral lookup tables, four targets each), `sky_view` (spectral single and multiple scattering per view direction, converted to RGB, for the sun or the moon with `HELION_MOON_VIEW`; Mie single scattering goes to a second target without its phase), `sky` (sky color per pixel), `fog` (distance fog in the physical horizon color over everything with depth, with the vanilla fog distances), `horizon` (average horizon color over 32 directions into a 1 x 1 target) and `sky_light` (sun light, moon light and sky dome irradiance into a 3 x 1 target). |
| `src/main/resources/assets/helion/shaders/shadow/` | Shadow shaders: `caster.vsh` and `caster.fsh` (terrain positions only, alpha cutout for cutout layers, depth only) and `mask.fsh` (cascade selection, normal and light offset, distance fade, R8 visibility mask). |
| `src/main/resources/assets/helion/shaders/include/` | Shared GLSL: `helion_shadow` (`HelionShadow` block, one shadow map sampler per cascade, 4 x 4 tent filtered shadow map lookup), `helion_geometry` (geometry buffer constants and normal encoding), `helion_view` (depth and view position), `helion_atmosphere` (Earth atmosphere constants, phase functions, ray-sphere intersection, ground shadow, lookup table sizes and coordinate mappings), `helion_spectrum` (`HelionSpectrum` block, densities, spectral scattering and extinction, spectrum to RGB), `helion_transmittance_lookup` and `helion_scattering_lookup` (samplers and per group lookup of the spectral tables), `helion_sky` (sky settings block), `helion_sky_color` (sun and moon sky view lookup with the Mie phase applied per pixel, rain, Neutral encoding, the camera relative position of a pixel from its depth and the view ray of a pixel from two points on it, so view bobbing never moves the sky against the world), `helion_lighting` (lighting settings block, the vanilla light map terms with bilinear level interpolation, ambient term, boss and darkness adjustment and the brightness option), `helion_sky_light` (physical sky light parts per pixel normal, and the shadow transmission: lit sun, moon and dome light against the shadowed sum, weighted by the share of sky light in the pixel), `helion_ambient_occlusion` (settings block, edge packing), `helion_ambient_occlusion_denoise` (edge aware blur), `helion_color` (sRGB conversion, saturation, the Neutral highlight curve and its inverse, film grade, dither noise), `helion_image` (image settings block including the sharpening strength, scene expansion, tone mapper switch, Filmic highlight bleach, bloom threshold, dither), `helion_bloom_downsample` (13-tap downsample with optional Karis average over a `helionBloomTap` function the including shader defines). |

## Build Files

| File | Purpose |
|---|---|
| `build.gradle` | ModDevGradle setup, JUnit 5 for `src/test/java` with the Minecraft classpath of `main` and skipped or failed tests in the log, `client` run forced to Vulkan, metadata expansion, packs `branding/logo.png` into the jar as `helion.png`, jar name `helion-neoforge-<minecraft_version>-<version>.jar`. Depends on Arkea (`arkea_version`) from the Arkea Maven repository on GitHub. |
| `gradle.properties` | Single place for versions and mod metadata, including `arkea_version`, `arkea_version_range` and `arkea_path`. |
| `settings.gradle` | Plugin repositories, Java toolchain resolver, project name. When an Arkea checkout exists at `arkea_path` (`../arkea` in the workspace, `-Parkea_path=arkea` in CI) it is included as a composite build, so Helion compiles and runs against the local Arkea sources. |
| `.github/workflows/build.yml` | CI on every push and pull request: JDK 25, installs `glslangValidator` for the shader test, runs `./gradlew build` and uploads the jar. |
| `.github/workflows/release.yml` | On a `v*` tag: checks the tag against `mod_version`, builds against the published Arkea (no checkout), creates the GitHub release with the changelog section, and uploads to Modrinth and CurseForge with Arkea as a required dependency when their secrets exist. |
