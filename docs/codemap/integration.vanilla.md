# Helion Code Map: `com.aryston.helion.integration.vanilla`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.integration.vanilla`

#### LevelRenderHook
- Path: `src/main/java/com/aryston/helion/integration/vanilla/LevelRenderHook.java`
- Role: Entry from the mixin. Chooses the Helion or vanilla frame and the render settings (`settings().foundation()` while the parity check captures), catches Helion failures, feeds the parity check after every level frame together with whether Helion really rendered it, and pauses GPU timing and drops the temporal history on vanilla frames. In Sodium mode it lets vanilla, and so Sodium, render the whole level and then runs `ScreenEffectsDriver` with `settings().screenOnly()`.
- Depends on: `VanillaFrameDriver`, `ScreenEffectsDriver`, `HelionRenderCore`, `ParityCheck`.

#### LevelFrameRequest
- Path: `src/main/java/com/aryston/helion/integration/vanilla/LevelFrameRequest.java`
- Role: Arguments of one `LevelRenderer.render` call plus typed access to the accessor mixin.

#### ScreenEffectsDriver, ScreenTargets
- Path: `src/main/java/com/aryston/helion/integration/vanilla/ScreenEffectsDriver.java`, `src/main/java/com/aryston/helion/integration/vanilla/ScreenTargets.java`
- Role: Sodium mode. After vanilla and Sodium drew the level into the main target, copies its color and depth into Helion's scene target, builds a frame graph with `ScreenTargets` (scene and output only, no geometry attachments) and runs `FrameStages.buildScreenEffects`: edge smoothing, depth of field, exposure, bloom, sharpening and the image composite (tone mapping, color grade, dither) on the finished image. Sodium replaces the terrain draws `VanillaFrameDriver` uses, so Helion's own terrain passes (shadows, geometry buffer lighting, ambient occlusion, physical sky, temporal anti-aliasing, motion blur) stay off.
- Depends on: `FrameStages`, `HelionRenderCore`, `VanillaFrameDriver` (camera and light environment helpers), `AmbientLightTracker`.

#### VanillaFrameDriver
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaFrameDriver.java`
- Role: Rebuilds `LevelRenderer.render` with Helion stages: the vanilla sky renderer created as `addSkyPass` does (vanilla extracts the sky state only while it exists), feature preparation, targets, NeoForge frame graph event, chunk draw preparation, the scene snapshot with the light map inputs, the camera with the captured level projection, the temporal frame with the jittered projection uploaded for the level (and put into the camera) and restored afterwards, the frame sources sharing one `SceneFog`, the voxel volume update from `VanillaVoxelSource` with the dynamic lights and entity boxes of the frame and the ray tracing recompile requests before the stages are built, stage building, graph execution, then section compile, upload and occlusion update.
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
- Role: `TerrainSource` over vanilla `ChunkSectionsToRender`: fog (the current `SceneFog` buffer, so the physical sky fog reaches terrain), chunk sampler, opaque and translucent layer groups, matching stage events. `renderOpaqueGeometry` draws the solid and cutout layers one by one with the geometry pipelines through `ChunkSectionsToRenderAccessor`; `supportsGeometryBuffer` is false for wireframe terrain or missing shaders. When the frame has dynamic lights it binds their uniform and draws every layer with the dynamic light pipelines (`VanillaDynamicLightPipelines`, or the geometry pipelines with lights), falling back per layer to the vanilla pipelines when a variant is missing; without lights it draws exactly as before. `renderWaterSurface` draws the translucent layer with the water surface pipelines (skipped for wireframe terrain or missing shaders); `renderWaterSurface` also draws the cutout layer with the glass surface pipelines, so plain glass gets a reflecting surface. `textures()` hands the block atlas, the lightmap and the atlas bounds of `block/water_still` and `block/water_flow` to the core; `glossyCells()` the glossy mask cells of the block atlas from `VanillaGlossyCells`.

#### VanillaGlossyCells
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaGlossyCells.java`
- Role: Marks the 16 x 16 cells of the block atlas that hold glossy block textures: every registered block whose id starts with `polished_`, contains `quartz`, `prismarine` or `glass`, or ends with `glazed_terracotta`, `ice` or `smooth_stone` (never ores), looked up as `block/<id>` and its `_top`, `_side`, `_bottom` and `_end` sprites. Cached until the atlas texture changes (resource reload).
- Depends on: `GlossyCells`.

#### VanillaDynamicLightPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaDynamicLightPipelines.java`
- Role: Copies of the vanilla solid, cutout and translucent terrain pipelines (direct and multi-draw) with the vertex shader `helion:terrain/dynamic_light`, the `HELION_DYNAMIC_LIGHTS` defines and the `HelionDynamicLights` uniform; the fragment shader stays vanilla. `withLights` adds the same to any terrain pipeline builder (the geometry pipelines use it). Warns once when a variant did not compile.
- Depends on: `DynamicLightResources`, `HelionPipelines`, `VanillaGeometryPipelines`.

#### DynamicLightTracker
- Path: `src/main/java/com/aryston/helion/integration/vanilla/DynamicLightTracker.java`
- Role: Finds the light sources of a frame: every rendered entity within 64 blocks of the camera that burns or holds or is a glowing item (`DynamicLightEmitters`), at its interpolated position, nearest first, collected once per frame (cached by level, game time and partial tick). Each source keeps the light color of its emitting block (`VoxelLightColors`); `voxelLights` hands the nearest 16 in world coordinates to voxel lighting. `lights` hands the nearest `MAX_LIGHTS` to the frame relative to the camera; `brighten` raises an entity's packed light coordinates to the dynamic level at its light probe position (level minus distance), so mobs, items and the first-person hand are lit too. Does nothing while the core is inactive, in screen effects mode or with the setting off.
- Depends on: `DynamicLightEmitters`, `DynamicLight`, `DynamicLightResources`, `HelionRenderCore`.

#### VanillaWaterPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaWaterPipelines.java`
- Role: Water surface variants of the vanilla translucent terrain pipelines (direct and multi-draw), copied with `toBuilder()` and switched to `helion:water/surface` with one unblended RGBA16_FLOAT target, the `HelionWaterSurface` uniform, the scene depth sampler and the glossy mask. A second pair from the vanilla cutout terrain pipelines adds the define `HELION_GLOSSY_ONLY`: it skips the alpha test and keeps only glossy mask texels, so the see-through center of plain glass is a surface too. Registered as optional pipelines; warns once when they did not compile.
- Depends on: `WaterPipelines`, `HelionPipelines`.

#### RtxSectionCapture
- Path: `src/main/java/com/aryston/helion/integration/vanilla/RtxSectionCapture.java`
- Role: When ray tracing was requested at startup, copies the quad positions of the solid and cutout layers of a compiled section (vanilla `Position` floats or Aervium's compact `PositionLight`, 1/2048 block steps from -8) into a `RtxSectionUpdate` on the compile worker when the section is near the camera, reports reset sections, and once per frame passes the scene's recompile requests to `LevelExtractor.setSectionDirty`. Warns once and skips terrain in an unknown vertex format. Next to the positions it packs the surface attributes of every vertex: `UV0`, `Color` and `UV2` of the vanilla block format, or `PackedUV0`, `Color` and the light in `PositionLight.w` of the compact format.

#### VanillaVoxelOccluders
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaVoxelOccluders.java`
- Role: Collects the interpolated bounding boxes of the nearest 8 living entities within 24 blocks of the camera for voxel lighting, leaving out invisible and spectating entities and the camera entity in first person.

#### DynamicLightEmitters
- Path: `src/main/java/com/aryston/helion/integration/vanilla/DynamicLightEmitters.java`
- Role: Light an entity gives off as an `Emission` (level and the emitting block state for its color): 15 and fire while it burns, the light emission of the default state of a held or dropped block item (torch 14, lantern 15, glowstone 15, ...) and 15 and lava for a lava bucket; the brightest wins.

#### VanillaGeometryPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaGeometryPipelines.java`
- Role: Geometry buffer terrain pipelines per chunk layer (direct and multi-draw), copied from the vanilla terrain pipelines with `toBuilder()` and switched to `helion:terrain/geometry` with three more color targets and the glossy mask sampler. They inherit Aervium's compact terrain vertex format and its `AERVIUM_COMPACT_VERTICES` define with the vanilla pipelines. A second set (`withDynamicLights`) adds the dynamic light uniform and defines. Registered as optional pipelines; warns once when they did not compile or the compact format version is unknown.
- Depends on: `GeometryBufferPipelines`, `HelionPipelines`, `CompactTerrainVertices`.

#### VanillaShadowCasterSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaShadowCasterSource.java`
- Role: `ShadowCasterSource` over vanilla chunk sections. Takes the sections with solid or cutout geometry from `ShadowCasterSections`, keeps those that lie in or toward the light of a cascade drawn this frame, temporarily swaps them into `LevelRenderer.visibleSections` and lets vanilla prepare one `ChunkSectionsToRender` per cascade with the light view, then restores the camera's sections. It prepares through the same path as the camera terrain (multi-draw indirect or direct), because vanilla closes the chunk section buffer of the other path when it switches. Draws the solid and cutout layers with the caster pipelines. `renderEntities` replays `PreparedFrame.executeSolid` of the frame's feature dispatcher, so entities, block entities and items cast shadows with their vanilla pipelines and animation state.
- Depends on: `ShadowCasterSections`, `VanillaShadowPipelines`, `ChunkSectionsToRenderAccessor`, `LevelRendererAccessor`.

#### ShadowCasterSections
- Path: `src/main/java/com/aryston/helion/integration/vanilla/ShadowCasterSections.java`
- Role: Cached list of the render sections within the render distance that hold solid or cutout geometry, the only sections that can cast shadows. Applies changed meshes incrementally and fully scans only a new view area or an overflowed change queue. Owned by `VanillaFrameDriver`, because the caster source is created per frame.
- Depends on: `ShadowCasterChanges`, `ShadowCasterMember`.

#### ShadowCasterChanges
- Path: `src/main/java/com/aryston/helion/integration/vanilla/ShadowCasterChanges.java`
- Role: Bounded cross-thread queue of sections whose meshes changed. Overflow clears the queue and requests one full caster rebuild.
- Depends on: nothing of this mod.

#### ShadowCasterMember
- Path: `src/main/java/com/aryston/helion/integration/vanilla/ShadowCasterMember.java`
- Role: Mixin contract storing a render section's index in the shadow caster list.
- Depends on: nothing of this mod.

#### VanillaShadowPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaShadowPipelines.java`
- Role: Depth-only shadow caster pipelines per chunk layer (direct and multi-draw), built from the vanilla terrain snippets without color targets, with the vanilla alpha cutout define, `helion:shadow/caster` shaders and the caster depth bias. When Aervium switched the vanilla terrain to its compact vertex format, they copy that vertex format and the `AERVIUM_COMPACT_VERTICES` define from the vanilla pipeline, because the snippets keep the vanilla format. Registered as optional pipelines; warns once when they did not compile or the compact format version is unknown.
- Depends on: `ShadowPipelines`, `HelionPipelines`, `VanillaGeometryPipelines.LayerPipelines`, `CompactTerrainVertices`.

#### CompactTerrainVertices
- Path: `src/main/java/com/aryston/helion/integration/vanilla/CompactTerrainVertices.java`
- Role: The shader define contract with Aervium's `compactVertices` module (no shared code, rule S2). `inherit` copies the vertex format of binding 0 and the `AERVIUM_COMPACT_VERTICES` define from a vanilla terrain pipeline to a builder; `supported` is false, with one warning, when the vanilla solid terrain pipeline carries a format version other than 1, so the geometry buffer and shadows turn off instead of reading vertices they do not understand.
- Depends on: nothing of this mod.

#### VanillaEntitySource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaEntitySource.java`
- Role: `EntitySource` over the vanilla prepared feature frame, including outline, see-through and always-on-top passes. Runs the solid phase twice when entities go into the geometry buffer: once through `EntityGeometryDraws` in geometry mode, once for the remainder with the after-opaque-features event.

#### EntityGeometryPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/EntityGeometryPipelines.java`
- Role: Geometry buffer variants of the vanilla entity and item pipelines (`core/entity`, `core/item`): every required or optional vanilla pipeline with one unblended color target, depth writes, the lightmap sampler and an overlay or `NO_OVERLAY`, without emissive, glint, dissolve or OIT defines, is copied with `toBuilder()`, switched to `helion:entity/geometry` (with `HELION_ITEM_LAYOUT` for the item vertex format) and given the three geometry buffer targets. Registered as optional pipelines; a pipeline without a compiled variant stays vanilla.
- Depends on: `GeometryBufferPipelines`, `HelionPipelines`.

#### EntityGeometryDraws
- Path: `src/main/java/com/aryston/helion/integration/vanilla/EntityGeometryDraws.java`
- Role: Render-thread switch for the solid feature phase: in geometry mode only render type features draw, each with its geometry variant, and draws without one are skipped; in remainder mode exactly those skipped draws and the other features (particles, features of other mods) draw. Read by `PreparedRenderTypeMixin` and `FeatureGroupMixin`. `drawn` hands every draw of the geometry pass (quads or triangles with float positions) to `RtxEntityDraws` while ray traced shadows capture entities, with the number of vertices it uses when the draw has the sequential index buffer.
- Depends on: `EntityGeometryPipelines`.

#### VanillaAtmosphereSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaAtmosphereSource.java`
- Role: `AtmosphereSource` over vanilla sky, cloud, weather and world border renderers, honoring NeoForge custom sky, cloud and weather renderers. Builds the `SkyEnvironment` from the vanilla sky state (sun and moon angles, rain, camera height above sea level, vanilla fog values) only for the Overworld skybox without a custom sky renderer and with the camera outside water, lava and powder snow. `useFog` swaps the fog buffer in the shared `SceneFog`, and `renderSky` and `renderCelestials` draw the vanilla sky through `HelionSky` (the second with only the celestial bodies).
- Depends on: `SceneSkyRenderer`, `SceneFog`, `StageEvents`, `HelionSky`, `AuroraNights`.

#### SceneFog
- Path: `src/main/java/com/aryston/helion/integration/vanilla/SceneFog.java`
- Role: The fog buffer of one frame, shared by the terrain and atmosphere sources. Starts as the vanilla terrain fog; the physical sky replaces it with its own buffer, so terrain, entities, sky renderers and clouds drawn later in the frame use it.

#### AuroraNights
- Path: `src/main/java/com/aryston/helion/integration/vanilla/AuroraNights.java`
- Role: Aurora strength for the physical night sky: 1 when the camera stands in a biome cold enough to snow and the current day (overworld clock time over 24000) is one of the rare aurora nights (a 25 percent chance drawn from the day number, so every player sees the same nights), else 0.

#### HelionSky
- Path: `src/main/java/com/aryston/helion/integration/vanilla/HelionSky.java`
- Role: Marks the vanilla sky rendering Helion runs, and whether it only draws the celestial bodies on top of the physical sky, so `SkyRendererMixin` skips the vanilla sky disc, sunrise and sunset glow, sun and moon then. `moonColor` dims the moon to 60 percent while Helion draws the sky, so it no longer blooms like the sun and the two stay easy to tell apart. Render thread only.

#### VanillaCloudSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaCloudSource.java`
- Role: `CloudSource` over the vanilla level: a `CloudEnvironment` (game time with the partial tick in seconds, rain from the sky rain brightness) only for the Overworld skybox without a custom cloud renderer, with clouds on in the video settings and a visible cloud color. Forwards `replaceVanillaClouds` to `HelionClouds`.
- Depends on: `HelionClouds`.

#### HelionClouds
- Path: `src/main/java/com/aryston/helion/integration/vanilla/HelionClouds.java`
- Role: Whether the volumetric clouds replace the vanilla clouds in the current Helion frame, read by `CloudRendererMixin`. `VanillaFrameDriver` clears it when the frame ends, so vanilla frames always keep their clouds.

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

#### VanillaVoxelSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaVoxelSource.java`
- Role: `VoxelSource` over the client level, on the render thread: reads the 4096 block states of one chunk section (missing chunks, sections outside the build height and all-air sections become empty bricks) through `VoxelBlockClasses`, with the block position for biome tinted colors, reports every block with light emission with its color, drains `VoxelSectionChanges`, and raises the level generation on a new level, reloaded light colors or an overflowed change queue so the volume rebuilds. `use(level, enabled)` also switches the change collection on or off. Owned by `VanillaFrameDriver`.
- Depends on: `VoxelBlockClasses`, `VoxelSectionChanges`.

#### VoxelBlockClasses
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VoxelBlockClasses.java`
- Role: Cached `VoxelClass` byte and light color per block state: air and invisible blocks empty, solid render blocks and blocks with full light dampening opaque, water and blocks whose model has translucent material translucent, everything else partial, with the light emission in the high bits; stained glass and panes also get `TINTS_LIGHT` and their dye color, brightest channel scaled to 1, in place of the map color. `albedo(state, voxel, level, pos)` gives opaque blocks with a vanilla tint source (grass blocks) their biome tint at the position, scaled so the tint without a level keeps the luminance of the map color; everything else uses the cached per state color.

#### VoxelSectionChanges
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VoxelSectionChanges.java`
- Role: Bounded cross-thread queue of section nodes whose blocks or meshes changed, filled by `LevelExtractorMixin` and `RenderSectionMixin` only while voxel lighting is on, so it costs one volatile read when off. Overflow clears it and asks for a full rebuild.
- Depends on: nothing of this mod.

#### VoxelLightColors
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VoxelLightColors.java`
- Role: Client reload listener for `assets/helion/voxel_light/colors.json` (block id to linear RGB; resource packs can replace it). `color(state)` returns the listed color, else the map color of the block, else warm white, normalized to luminance 1 so every light keeps the vanilla brightness. A reload raises its version so cached colors and the volume are rebuilt.
