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
- Role: `TerrainSource` over vanilla `ChunkSectionsToRender`: fog (the current `SceneFog` buffer, so the physical sky fog reaches terrain), chunk sampler, opaque and translucent layer groups, matching stage events. `renderOpaqueGeometry` draws the solid and cutout layers one by one with the geometry pipelines through `ChunkSectionsToRenderAccessor`; `supportsGeometryBuffer` is false for wireframe terrain or missing shaders. When the frame has dynamic lights it binds their uniform and draws every layer with the dynamic light pipelines (`VanillaDynamicLightPipelines`, or the geometry pipelines with lights), falling back per layer to the vanilla pipelines when a variant is missing; without lights it draws exactly as before.

#### VanillaDynamicLightPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaDynamicLightPipelines.java`
- Role: Copies of the vanilla solid, cutout and translucent terrain pipelines (direct and multi-draw) with the vertex shader `helion:terrain/dynamic_light`, the `HELION_DYNAMIC_LIGHTS` defines and the `HelionDynamicLights` uniform; the fragment shader stays vanilla. `withLights` adds the same to any terrain pipeline builder (the geometry pipelines use it). Warns once when a variant did not compile.
- Depends on: `DynamicLightResources`, `HelionPipelines`, `VanillaGeometryPipelines`.

#### DynamicLightTracker
- Path: `src/main/java/com/aryston/helion/integration/vanilla/DynamicLightTracker.java`
- Role: Finds the light sources of a frame: every rendered entity within 64 blocks of the camera that burns or holds or is a glowing item (`DynamicLightEmitters`), at its interpolated position, nearest first, collected once per frame (cached by level, game time and partial tick). `lights` hands the nearest `MAX_LIGHTS` to the frame relative to the camera; `brighten` raises an entity's packed light coordinates to the dynamic level at its light probe position (level minus distance), so mobs, items and the first-person hand are lit too. Does nothing while the core is inactive, in screen effects mode or with the setting off.
- Depends on: `DynamicLightEmitters`, `DynamicLight`, `DynamicLightResources`, `HelionRenderCore`.

#### DynamicLightEmitters
- Path: `src/main/java/com/aryston/helion/integration/vanilla/DynamicLightEmitters.java`
- Role: Light level an entity gives off: 15 while it burns, the light emission of the default state of a held or dropped block item (torch 14, lantern 15, glowstone 15, ...) and 15 for a lava bucket.

#### VanillaGeometryPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaGeometryPipelines.java`
- Role: Geometry buffer terrain pipelines per chunk layer (direct and multi-draw), copied from the vanilla terrain pipelines with `toBuilder()` and switched to `helion:terrain/geometry` with three more color targets. They inherit Aervium's compact terrain vertex format and its `AERVIUM_COMPACT_VERTICES` define with the vanilla pipelines. A second set (`withDynamicLights`) adds the dynamic light uniform and defines. Registered as optional pipelines; warns once when they did not compile or the compact format version is unknown.
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
- Role: Marks the vanilla sky rendering Helion runs, and whether it only draws the celestial bodies on top of the physical sky, so `SkyRendererMixin` skips the vanilla sky disc then (the sunrise and sunset glow stays). `moonColor` dims the moon to 60 percent while Helion draws the sky, so it no longer blooms like the sun and the two stay easy to tell apart. Render thread only.

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
