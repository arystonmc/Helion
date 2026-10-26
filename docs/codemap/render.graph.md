# Helion Code Map: `com.aryston.helion.render.graph`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.graph`

#### RenderStage
- Path: `src/main/java/com/aryston/helion/render/graph/RenderStage.java`
- Role: Contract of a stage or post effect: `name()`, `isActive(frame)`, `addTo(frame)`.

#### FrameContext
- Path: `src/main/java/com/aryston/helion/render/graph/FrameContext.java`
- Role: Everything a stage sees for one frame: graph, targets, camera, scene snapshot, render settings, the geometry buffer, the temporal frame (jittered projection, reprojection), the atmosphere results (sky light and fog pass of the physical sky), the post results and the water results (water surface, traced reflections, reflection pass) that stages hand to each other.

#### FrameTargets
- Path: `src/main/java/com/aryston/helion/render/graph/FrameTargets.java`
- Role: Frame graph handles of the scene and output targets, updated after each `readsAndWrites`, plus `declareGeometryAttachments` for auxiliary targets.

#### RenderSettings
- Path: `src/main/java/com/aryston/helion/render/graph/RenderSettings.java`
- Role: Immutable per-frame effect settings. `OFF` is the startup value; `foundation()` turns every effect off for the parity check, temporal anti-aliasing and the physical sky included, but keeps the geometry buffer (without its view) because it belongs to the core. `screenOnly()` keeps only what works on a finished image (image settings without motion blur) and turns the geometry buffer, temporal anti-aliasing, the physical sky and volumetric clouds off, for Sodium mode. Ray tracing is off in `OFF`, `screenOnly()` and `foundation()`.
- Depends on: `ImageSettings`, `GeometryBufferSettings`, `TemporalSettings`, `PhysicalSkySettings`, `CloudSettings`, `RayTracingSettings`, `ReflectionSettings`.

#### RenderGraph
- Path: `src/main/java/com/aryston/helion/render/graph/RenderGraph.java`
- Role: Thin wrapper around vanilla `FrameGraphBuilder`. Prefixes pass names with `helion:` and wraps pass tasks with GPU timing.
- Depends on: `GpuTimings`.

#### GpuTimings
- Path: `src/main/java/com/aryston/helion/render/graph/GpuTimings.java`
- Role: Timestamp query ring over four frames in flight, up to 32 stages per frame. Reads results without blocking and keeps a smoothed millisecond average per stage. Stages not measured for 120 frames are dropped, so a replaced stage (present and composite) disappears from F3. `pause()` stops measuring and clears every value; the core calls it for frames without GPU timing and the render hook for vanilla frames, so F3 never shows stale numbers.
- Depends on: `GpuResources`.
