# Helion Code Map: `com.aryston.helion.render`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render`

#### HelionRenderCore
- Path: `src/main/java/com/aryston/helion/render/HelionRenderCore.java`
- Role: Singleton that owns the core state and shared GPU objects. Decides whether Helion renders, detects the backend on first use, starts and ends level frames, redirects the main target during a level frame, releases everything on shutdown and renders nothing after it, because Minecraft can still draw a frame after the shutdown event. Setting fields are volatile because config reloads may arrive from another thread. With Sodium installed `useScreenEffectsOnly()` switches it to Sodium mode (`screenEffectsOnly()`).
- Members: `get()`, `isActive()`, `toggle()`, `passivate(reason)`, `reportFailure(throwable)`, `beginLevelFrame(main)`, `endLevelFrame()`, `resolveMainTarget(original)`, `recordFrame(camera, scene)`, `lastCamera()`, `lastScene()`, `applySettings(enabled, debugMode, renderSettings)`, `isDebugMode()`, `settings()`, `ambientOcclusion()`, `image()`, `lighting()`, `temporal()`, `motionBlur()`, `sky()`, `clouds()`, `shadows()`, `recordLevelProjection(projection)`, `levelProjection()`, `setPassiveListener(listener)`, `shutdown()`.
- Depends on: `GpuDeviceSummary`, `GpuResources`, `SceneTargets`, `GpuTimings`, `HelionCamera`, `AmbientOcclusionResources`, `ImageResources`, `DeferredLightingResources`, `TemporalResources`, `MotionBlurResources`, `PhysicalSkyResources`, `CloudResources`, `ShadowResources`.

#### PassiveReason
- Path: `src/main/java/com/aryston/helion/render/PassiveReason.java`
- Role: Why the core stays passive: `NOT_VULKAN`, `INCOMPATIBLE_MOD`, `RENDER_FAILURE`. Each carries the translation key of its player message.

#### PassiveListener
- Path: `src/main/java/com/aryston/helion/render/PassiveListener.java`
- Role: Callback the integration layer registers to show a notice when the core becomes passive.

#### FrameSources
- Path: `src/main/java/com/aryston/helion/render/FrameSources.java`
- Role: Per-frame bundle of the terrain, entity, atmosphere, cloud, transparency and shadow caster sources plus the post effects.

#### FrameStages
- Path: `src/main/java/com/aryston/helion/render/FrameStages.java`
- Role: Single place that defines the stage order (clear, sky, opaque geometry, shadows, deferred lighting, ambient occlusion, solid features, transparent geometry, temporal anti-aliasing, post with motion blur, exposure metering, bloom and sharpening, output, geometry buffer view) and adds active stages to the frame one by one, so a stage can see what earlier stages published (the geometry buffer). The output stage is `ImageCompositeStage` when an image effect is active, otherwise `PresentStage`. `buildScreenEffects` adds only the post effects and the output for Sodium mode, from the same list.
- Depends on: every stage class.
