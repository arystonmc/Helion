# Helion Code Map: `com.aryston.helion.render.geometry`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.geometry`

#### OpaqueGeometryStage
- Path: `src/main/java/com/aryston/helion/render/geometry/OpaqueGeometryStage.java`
- Role: Prepares fog, chunk sampler, translucent buffers and lighting, then draws opaque terrain and solid features in one pass, as vanilla does. With the geometry buffer on, creates the normal, light and albedo targets (cleared to zero, so alpha 0 marks pixels without terrain), draws only opaque terrain into them and the scene in a four-attachment render pass with the glossy mask bound, and publishes them in `GeometryBuffer`; while temporal anti-aliasing runs it also copies the terrain-only depth into `helion:terrain_depth` (R32_FLOAT, pass `terrain_depth`) for the moving object detection of `taa_resolve`; `SolidFeatureStage` draws the rest after deferred lighting.
- Depends on: `TerrainSource`, `EntitySource`, `AtmosphereSource`, `GlossyMask`.

#### EntityGeometryStage
- Path: `src/main/java/com/aryston/helion/render/geometry/EntityGeometryStage.java`
- Role: Only when the geometry buffer was written and `EntitySource.supportsGeometryBuffer()`: draws the solid mobs, players, items and block entities that have a geometry pipeline into the scene and the normal, light and albedo targets in a four-attachment pass (pass `entity_geometry`), after the terrain depth copy, so shadows, ambient occlusion, deferred and voxel lighting light them per pixel like terrain. Republishes the targets and marks them as holding entities.
- Depends on: `EntitySource`, `GeometryBuffer`.

#### SolidFeatureStage
- Path: `src/main/java/com/aryston/helion/render/geometry/SolidFeatureStage.java`
- Role: Only when the geometry buffer was written: fires the after-opaque-blocks event and draws solid entities, block entities and particles into the scene, after deferred lighting so the lighting never paints over them. When `EntityGeometryStage` already drew entities into the geometry buffer it draws only the rest (`renderSolidRemainder`).
- Depends on: `TerrainSource`, `EntitySource`.

#### TranslucentFeatureStage
- Path: `src/main/java/com/aryston/helion/render/geometry/TranslucentFeatureStage.java`
- Role: While a water reflection setting is on and order independent transparency is off, draws the translucent entity features (players, slimes and other `entityTranslucent` models) and fires their event right after the solid features, in a pass of its own, so the water surface, refraction copy and screen space reflections see them; `TransparentGeometryStage` then skips them. Vanilla draws them before translucent terrain too, so the draw order of visible things does not change.
- Depends on: `EntitySource`.

#### TransparentGeometryStage
- Path: `src/main/java/com/aryston/helion/render/geometry/TransparentGeometryStage.java`
- Role: Draws sorted transparency (translucent features, unless `TranslucentFeatureStage` already drew them), translucent terrain, particles, clouds, weather, world border) or vanilla order independent transparency, then outline, see-through and always-on-top features. Declares the auxiliary OIT and outline targets. When the physical sky published a `SceneFogPass`, draws it after the sorted translucents and before clouds, weather and world border (a separate render pass on each side), or after order independent transparency. When `CloudStage` published a `SceneCloudPass`, draws the volumetric clouds right after the fog, and the light shafts of `LightShaftStage` right after the clouds. A `SceneReflectionPass` from `WaterResults` is drawn first among those passes, so fog, clouds and light shafts cover the reflections like the water under them.
- Depends on: `TerrainSource`, `EntitySource`, `AtmosphereSource`, `TransparencySource`, `SceneFogPass`, `SceneCloudPass`, `SceneLightShaftPass`.

#### TerrainSource
- Path: `src/main/java/com/aryston/helion/render/geometry/TerrainSource.java`
- Role: Contract for drawing chunk geometry: `prepareFrame`, `supportsGeometryBuffer`, `renderOpaque` (vanilla path with the after-opaque event), `renderOpaqueGeometry` (geometry buffer path), `afterOpaque`, `renderTranslucent`, `supportsWaterSurface` and `renderWaterSurface` (translucent layer and plain glass into the water surface target), `textures()`, `glossyCells()`.

#### TerrainTextures
- Path: `src/main/java/com/aryston/helion/render/geometry/TerrainTextures.java`
- Role: The block atlas, the lightmap and the atlas bounds of the still, flowing and overlay water sprites, for the water surface and the traced reflections.

#### GeometryBuffer
- Path: `src/main/java/com/aryston/helion/render/geometry/GeometryBuffer.java`
- Role: Per-frame holder of the geometry buffer target handles (normal, light, albedo), empty when the geometry buffer was not written this frame, and of the terrain-only depth copy when temporal anti-aliasing runs. `includesEntities()` tells whether `EntityGeometryStage` drew entities into it.

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
- Role: Contract for entities, block entities and particles: lighting setup, solid, translucent, after-terrain and overlay drawing. `supportsGeometryBuffer`, `renderSolidGeometry` (only draws with a geometry pipeline, into the four-attachment pass) and `renderSolidRemainder` (every other solid draw) split the solid drawing for the geometry buffer.

#### TransparencySource
- Path: `src/main/java/com/aryston/helion/render/geometry/TransparencySource.java`
- Role: Contract for order independent transparency.

#### GlossyCells, GlossyMask
- Path: `src/main/java/com/aryston/helion/render/geometry/GlossyCells.java`, `src/main/java/com/aryston/helion/render/geometry/GlossyMask.java`
- Role: Block material mask. `GlossyCells` is one byte per 16 x 16 cell of the block atlas (0 ordinary, 0x20 emissive, 0x40 muddy, 0x80 porous, 0x9F flowing lava, 0xBF dry, 0xDF lava, 0xFF glossy; eight steps in the shaders), keyed by the atlas texture view; `GlossyMask` (owned by `HelionRenderCore`) uploads it into an R8_UNORM texture, again only when the atlas changes, outside any render pass. The terrain geometry pass and the water surface pass bind it as `GlossySampler`: opaque terrain writes its material into the alpha of the light target (1 ordinary, 0.875 porous, 0.75 glossy, 0.625 dry, 0.5625 muddy; crossed plant planes count as porous), and the glass surface pipelines keep only glossy texels; the shadow caster pass and the ray traced sun shadow skip emissive and lava texels.
- Depends on: `GpuResources`.
