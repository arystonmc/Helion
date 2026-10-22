# Helion Code Map: `com.aryston.helion.render.geometry`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.geometry`

#### OpaqueGeometryStage
- Path: `src/main/java/com/aryston/helion/render/geometry/OpaqueGeometryStage.java`
- Role: Prepares fog, chunk sampler, translucent buffers and lighting, then draws opaque terrain and solid features in one pass, as vanilla does. With the geometry buffer on, creates the normal, light and albedo targets (cleared to zero, so alpha 0 marks pixels without terrain), draws only opaque terrain into them and the scene in a four-attachment render pass and publishes them in `GeometryBuffer`; while temporal anti-aliasing runs it also copies the terrain-only depth into `helion:terrain_depth` (R32_FLOAT, pass `terrain_depth`) for the moving object detection of `taa_resolve`; `SolidFeatureStage` draws the rest after deferred lighting.
- Depends on: `TerrainSource`, `EntitySource`, `AtmosphereSource`.

#### SolidFeatureStage
- Path: `src/main/java/com/aryston/helion/render/geometry/SolidFeatureStage.java`
- Role: Only when the geometry buffer was written: fires the after-opaque-blocks event and draws solid entities, block entities and particles into the scene, after deferred lighting so the lighting never paints over them.
- Depends on: `TerrainSource`, `EntitySource`.

#### TransparentGeometryStage
- Path: `src/main/java/com/aryston/helion/render/geometry/TransparentGeometryStage.java`
- Role: Draws sorted transparency (translucent features, translucent terrain, particles, clouds, weather, world border) or vanilla order independent transparency, then outline, see-through and always-on-top features. Declares the auxiliary OIT and outline targets. When the physical sky published a `SceneFogPass`, draws it after the sorted translucents and before clouds, weather and world border (a separate render pass on each side), or after order independent transparency. When `CloudStage` published a `SceneCloudPass`, draws the volumetric clouds right after the fog, and the light shafts of `LightShaftStage` right after the clouds.
- Depends on: `TerrainSource`, `EntitySource`, `AtmosphereSource`, `TransparencySource`, `SceneFogPass`, `SceneCloudPass`, `SceneLightShaftPass`.

#### TerrainSource
- Path: `src/main/java/com/aryston/helion/render/geometry/TerrainSource.java`
- Role: Contract for drawing chunk geometry: `prepareFrame`, `supportsGeometryBuffer`, `renderOpaque` (vanilla path with the after-opaque event), `renderOpaqueGeometry` (geometry buffer path), `afterOpaque`, `renderTranslucent`.

#### GeometryBuffer
- Path: `src/main/java/com/aryston/helion/render/geometry/GeometryBuffer.java`
- Role: Per-frame holder of the geometry buffer target handles (normal, light, albedo), empty when the geometry buffer was not written this frame, and of the terrain-only depth copy when temporal anti-aliasing runs.

#### GeometryBufferSettings
- Path: `src/main/java/com/aryston/helion/render/geometry/GeometryBufferSettings.java`
- Role: Player settings: enabled and the debug view. `withoutView()` for the parity check.

#### GeometryBufferView
- Path: `src/main/java/com/aryston/helion/render/geometry/GeometryBufferView.java`
- Role: Debug views `NONE`, `NORMALS`, `BLOCK_LIGHT`, `SKY_LIGHT`, `ALBEDO`, each with the id the debug shader is compiled with, and `VOXEL_SLICE`, which `VoxelSliceStage` draws instead of the geometry buffer debug shader.

#### GeometryBufferPipelines
- Path: `src/main/java/com/aryston/helion/render/geometry/GeometryBufferPipelines.java`
- Role: Target indices and formats of the geometry buffer, sampler names and one debug view pipeline per view except `VOXEL_SLICE` (`geometry/debug.fsh` with `HELION_GEOMETRY_VIEW`) and the terrain depth copy pipeline (`geometry/terrain_depth.fsh`).
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
