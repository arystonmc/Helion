# Helion Code Map: `com.aryston.helion.render`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render`

#### HelionRenderCore
- Path: `src/main/java/com/aryston/helion/render/HelionRenderCore.java`
- Role: Singleton that owns the core state and shared GPU objects. Decides whether Helion renders, detects the backend on first use, starts and ends level frames, redirects the main target during a level frame, releases everything on shutdown and renders nothing after it, because Minecraft can still draw a frame after the shutdown event. Setting fields are volatile because config reloads may arrive from another thread. With Sodium installed `useScreenEffectsOnly()` switches it to Sodium mode (`screenEffectsOnly()`). Also owns the water resources (`water()`) and the glossy block mask (`glossy()`).
- Members: `get()`, `isActive()`, `toggle()`, `passivate(reason)`, `reportFailure(throwable)`, `beginLevelFrame(main)`, `endLevelFrame()`, `resolveMainTarget(original)`, `recordFrame(camera, scene)`, `lastCamera()`, `lastScene()`, `applySettings(enabled, debugMode, renderSettings)`, `isDebugMode()`, `settings()`, `image()`, `temporal()`, `motionBlur()`, `depthOfField()`, `sky()`, `clouds()`, `lightShafts()`, `rayTracing()`, `water()`, `glossy()`, `recordLevelProjection(projection)`, `levelProjection()`, `tracesLight()` (Helion active and the path traced light ran in the last frame, read by `EntityRendererMixin`), `setPassiveListener(listener)`, `shutdown()`.
- Depends on: `GpuDeviceSummary`, `GpuResources`, `SceneTargets`, `GpuTimings`, `HelionCamera`, `ImageResources`, `TemporalResources`, `MotionBlurResources`, `DepthOfFieldResources`, `PhysicalSkyResources`, `CloudResources`, `LightShaftResources`, `RayTracingResources`, `WaterResources`, `GlossyMask`.

#### PassiveReason
- Path: `src/main/java/com/aryston/helion/render/PassiveReason.java`
- Role: Why the core stays passive: `NOT_VULKAN`, `INCOMPATIBLE_MOD`, `RENDER_FAILURE`. Each carries the translation key of its player message.

#### PassiveListener
- Path: `src/main/java/com/aryston/helion/render/PassiveListener.java`
- Role: Callback the integration layer registers to show a notice when the core becomes passive.

#### FrameSources
- Path: `src/main/java/com/aryston/helion/render/FrameSources.java`
- Role: Per-frame bundle of the terrain, entity, atmosphere, cloud, weather, waves, footprints, transparency sources plus the post effects.

#### FrameStages
- Path: `src/main/java/com/aryston/helion/render/FrameStages.java`
- Role: Single place that defines the stage order (clear, rain wetness, sky, opaque geometry, entity geometry, ray tracing scene, solid features, translucent features, clouds, water waves, water surface, ray traced reflections, screen space reflections, water reflection, light shafts, transparent geometry, heat shimmer, underwater, temporal anti-aliasing, post with motion blur, exposure metering, bloom and sharpening, output, geometry buffer view). Terrain keeps the vanilla light: the geometry pass writes the vanilla terrain color into the scene and nothing relights it until the path traced light pass arrives and adds active stages to the frame one by one, so a stage can see what earlier stages published (the geometry buffer). The output stage is `ImageCompositeStage` when an image effect is active, otherwise `PresentStage`. `buildScreenEffects` adds only the post effects and the output for Sodium mode, from the same list.
- Depends on: every stage class.
