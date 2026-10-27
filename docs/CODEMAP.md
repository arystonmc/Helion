# Helion Code Map

Index of the Helion code map. Entries live in `docs/codemap/`, one file per Java package plus tests, resources and build files. The big picture is in `docs/ARCHITECTURE.md`.

## How to Find a Class

1. Find the feature in Quick Lookup below to get the class names.
2. Search the entry from the mod folder: `rg -n "^#### .*\bClassName\b" docs/codemap -A12`.
3. Or open one package file from the Packages table.

A class of package `P` is documented only in `codemap/P.md`, or in `codemap/P--<topic>.md` when the package is split. Update only that file in the same commit as the code (rule D1), and split it at a topic boundary once it grows past 24 KB. `scripts/verify.sh` enforces both.

## Quick Lookup

| I want to change | Go to |
|---|---|
| Stage order of a frame | `FrameStages` |
| What a stage draws | `ClearStage`, `SkyStage`, `PhysicalSkyStage`, `OpaqueGeometryStage`, `EntityGeometryStage`, `RtxSceneStage`, `SolidFeatureStage`, `CloudStage`, `LightShaftStage`, `TransparentGeometryStage`, `TemporalStage`, `PostProcessingStage`, `BloomStage`, `SharpeningStage`, `ImageCompositeStage`, `PresentStage` |
| Bloom, tone mapping and exposure | `ImageResources` (tuning), `shaders/image/`, `shaders/include/helion_color.glsl`, `bloom_prefilter.fsh` (emissive detection) |
| Sharpening | `SharpeningStage`, `shaders/image/sharpen.fsh` |
| Bloom threshold in daylight and caves | `AmbientLightTracker`, `ImageResources` |
| Effect settings per frame | `RenderSettings`, `HelionConfig.renderSettings` |
| Geometry buffer (normals, light, albedo of terrain) | `OpaqueGeometryStage`, `VanillaGeometryPipelines`, `GeometryBufferPipelines`, `shaders/terrain/`, `shaders/geometry/` |
| Water surface and reflections | `WaterSurfaceStage`, `ScreenReflectionStage`, `WaterReflectionStage`, `RtxReflectionStage`, `VanillaWaterPipelines`, `shaders/water/`, `shaders/rtx/reflection.csh` |
| Mobs, players and items in the geometry buffer | `EntityGeometryStage`, `EntityGeometryPipelines`, `EntityGeometryDraws`, `PreparedRenderTypeMixin`, `FeatureGroupMixin`, `shaders/entity/` |
| Temporal anti-aliasing: jitter, history, reprojection | `TemporalStage`, `TemporalResources`, `JitterSequence`, `PreviousView`, `GameRendererMixin`, `shaders/temporal/` |
| Motion blur: tile velocities and gather | `MotionBlurStage`, `MotionBlurResources` (frame rate normalization), `TemporalResources` (camera reprojection), `shaders/motion_blur/` |
| Physical sky: atmosphere, spectral colors, sun and moon light, fog | `PhysicalSkyStage`, `PhysicalSkyResources` (brightness, fog buffer), `AtmosphereSpectrum` (wavelength data), `SkyEnvironment`, `SkyRendererMixin`, `shaders/atmosphere/`, `shaders/include/helion_atmosphere.glsl`, `helion_spectrum.glsl`, `helion_sky_color.glsl` |
| Volumetric clouds: raymarching, lighting, history, vanilla clouds off | `CloudStage`, `CloudResources` (tuning), `CloudQuality`, `VanillaCloudSource`, `HelionClouds`, `CloudRendererMixin`, `shaders/clouds/`, `shaders/include/helion_clouds.glsl`, `helion_cloud_noise.glsl` |
| Light shafts (god rays) of the sun and moon | `LightShaftStage`, `LightShaftView` (which light, screen position, fades), `LightShaftResources` (tuning), `shaders/light_shafts/`, `shaders/include/helion_light_shafts.glsl` |
| GPU time of every preset in one run | `GpuProfile`, `HelionPresetSettings` |
| Ray tracing (setting `rayTracing.enabled`, read at startup) | `RayTracingResources`, `RtxSectionScene`, `RtxSceneStage`, `RtxSectionCapture`, `RtxComputePipeline`, `RtxReflectionStage`, `SectionCompilerMixin`, `VulkanBackendMixin`, `VulkanFeatureSetsMixin`, `shaders/rtx/` |
| Path traced light (setting `rayTracing.pathTracing`) | `RtxPathTracingStage`, `PathTracingPipelines`, `RayTracingResources`, `RtxDenoiseImages`, `RtxLightScene`, `RtxSectionLights`, `LightColors`, `shaders/rtx/path_trace.csh`, `shaders/rtx/denoise_accumulate.csh`, `shaders/rtx/denoise_filter.csh`, `shaders/pathtracing/radiance.fsh` |
| Shader pipelines | `HelionPipelines`, `TemporalPipelines`, `PhysicalSkyPipelines`, `CloudPipelines`, `LightShaftPipelines`, `ImagePipelines` |
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
| Settings codes (copy and paste) | `HelionSharing`, `HelionShareCode` |
| Key bindings | `HelionKeys`, `ParityControl` |
| Debug mode: on-screen values and JSON log | `HelionDebugSnapshot` (every value), `HelionDebugEntry` (screen), `DebugOverlayLines` (line layout), `HelionDebugLog` (`logs/helion-debug.jsonl`) |
| Pixel comparison with vanilla | `ParityCheck`, `FrameCapture`, `ParityResult`, `ParityDifferenceImage` |
| Vanilla hooks | `LevelRendererMixin`, `LevelRendererAccessor`, `ChunkSectionsToRenderAccessor`, `GameRendererMixin`, `RenderSectionMixin`, `SectionCompilerMixin`, `SkyRendererMixin`, `CloudRendererMixin` |
| Mod name, version, loader versions | `gradle.properties` |
| Unit tests | `src/test/java/`, see [codemap/tests.md](codemap/tests.md) |
| Mod list metadata, dependencies, mixin config | `neoforge.mods.toml` |

## Overview

- Side: client only (`dist = Dist.CLIENT`). `displayTest="IGNORE_ALL_VERSION"` lets players join servers without Helion.
- Requires the Vulkan backend. On OpenGL or with Iris installed the core stays passive and vanilla renders; with Sodium installed it runs in Sodium mode (screen effects only).
- Root package: `com.aryston.helion`.

## Packages

| Package | Code map file | Classes | Purpose |
|---|---|---|---|
| `com.aryston.helion` | [root.md](codemap/root.md) | 1 | Entry class with the mod id and logger. |
| `com.aryston.helion.config` | [config.md](codemap/config.md) | 8 | Config spec, presets, settings screen and share codes. |
| `com.aryston.helion.debug` | [debug.md](codemap/debug.md) | 13 | Debug overlay, GPU profile, visual test and pixel parity tools. |
| `com.aryston.helion.integration` | [integration.md](codemap/integration.md) | 6 | Event wiring, key bindings, parity control and the Sodium and Iris guard. |
| `com.aryston.helion.integration.vanilla` | [integration.vanilla.md](codemap/integration.vanilla.md) | 30 | Bridges to vanilla frame, terrain, entity, atmosphere, water and ray tracing capture sources. |
| `com.aryston.helion.mixin` | [mixin.md](codemap/mixin.md) | 20 | Mixins and accessors on vanilla renderers and, for ray tracing, on the renderpearl Vulkan backend. |
| `com.aryston.helion.render.atmosphere` | [render.atmosphere.md](codemap/render.atmosphere.md) | 15 | Physical sky, atmosphere spectrum, sky light and scene fog. |
| `com.aryston.helion.render.clouds` | [render.clouds.md](codemap/render.clouds.md) | 9 | Volumetric clouds stage, resources, settings and pipelines. |
| `com.aryston.helion.render.backend` | [render.backend.md](codemap/render.backend.md) | 1 | GPU device summary. |
| `com.aryston.helion.render.camera` | [render.camera.md](codemap/render.camera.md) | 2 | Helion camera and frustum. |
| `com.aryston.helion.render.dof` | [render.dof.md](codemap/render.dof.md) | 4 | Depth of field stage, resources and pipelines. |
| `com.aryston.helion.render.edge` | [render.edge.md](codemap/render.edge.md) | 2 | Edge smoothing stage and pipelines. |
| `com.aryston.helion.render.geometry` | [render.geometry.md](codemap/render.geometry.md) | 16 | Opaque, entity, solid feature and transparent geometry stages, the geometry buffer and the glossy block mask. |
| `com.aryston.helion.render.graph` | [render.graph.md](codemap/render.graph.md) | 6 | Render stages, frame context and targets, render settings, render graph, GPU timings. |
| `com.aryston.helion.render.lightshaft` | [render.lightshaft.md](codemap/render.lightshaft.md) | 6 | Sun and moon light shafts through clouds, leaves and block gaps. |
| `com.aryston.helion.render` | [render.md](codemap/render.md) | 5 | Render core, passive mode and the frame stage list. |
| `com.aryston.helion.render.motion` | [render.motion.md](codemap/render.motion.md) | 4 | Motion blur stage, resources and pipelines. |
| `com.aryston.helion.render.post` | [render.post.md](codemap/render.post.md) | 22 | Post processing: bloom, exposure, eye adaptation, color grading, tone mapping, sharpening, present. |
| `com.aryston.helion.render.resource` | [render.resource.md](codemap/render.resource.md) | 5 | GPU resources, uniform ring, pixel readback and scene targets. |
| `com.aryston.helion.render.scene` | [render.scene.md](codemap/render.scene.md) | 2 | Scene snapshot and clear stage. |
| `com.aryston.helion.render.shader` | [render.shader.md](codemap/render.shader.md) | 2 | Shared pipelines and the fullscreen pass. |
| `com.aryston.helion.render.water` | [render.water.md](codemap/render.water.md) | 25 | Water surface target, wave normals, screen space reflections, rain wetness and the reflection composite. |
| `com.aryston.helion.render.rtx` | [render.rtx.md](codemap/render.rtx.md) | 28 | Hardware ray tracing: optional ray query features, VMA device address buffers, per-section bottom levels and a per-frame top level from the real block meshes, entities, raw compute passes and the traced reflections and the path traced light. |
| `com.aryston.helion.render.pathtracing` | [render.pathtracing.md](codemap/render.pathtracing.md) | 1 | Render pipelines of the path traced light that need no ray tracing types. |
| `com.aryston.helion.render.temporal` | [render.temporal.md](codemap/render.temporal.md) | 11 | Temporal anti-aliasing, jitter, history, previous view and render scale. |

## Other Files

| Topic | Code map file | Contents |
|---|---|---|
| Tests | [tests.md](codemap/tests.md) | 24 JUnit test files under `src/test/`. |
| Source files and asset folders | [resources.md](codemap/resources.md) | Non-Java files under `src/` and asset folders. |
| Build files | [build.md](codemap/build.md) | Gradle and CI files. |
