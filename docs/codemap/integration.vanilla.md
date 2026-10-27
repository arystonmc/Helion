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
- Role: Sodium mode. After vanilla and Sodium drew the level into the main target, copies its color and depth into Helion's scene target, builds a frame graph with `ScreenTargets` (scene and output only, no geometry attachments) and runs `FrameStages.buildScreenEffects`: edge smoothing, depth of field, exposure, bloom, sharpening and the image composite (tone mapping, color grade, dither) on the finished image. Sodium replaces the terrain draws `VanillaFrameDriver` uses, so Helion's own terrain passes (geometry buffer, physical sky, temporal anti-aliasing, motion blur) stay off.
- Depends on: `FrameStages`, `HelionRenderCore`, `VanillaFrameDriver` (camera helper), `AmbientLightTracker`.

#### VanillaFrameDriver
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaFrameDriver.java`
- Role: Rebuilds `LevelRenderer.render` with Helion stages: the vanilla sky renderer created as `addSkyPass` does (vanilla extracts the sky state only while it exists), feature preparation, targets, NeoForge frame graph event, chunk draw preparation, the scene snapshot, the camera with the captured level projection, the temporal frame with the jittered projection uploaded for the level (and put into the camera) and restored afterwards, the frame sources sharing one `SceneFog`, the ray tracing recompile requests before the stages are built, stage building, graph execution, then section compile, upload and occlusion update.
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
- Role: `TerrainSource` over vanilla `ChunkSectionsToRender`: fog (the current `SceneFog` buffer, so the physical sky fog reaches terrain), chunk sampler, opaque and translucent layer groups, matching stage events. `renderOpaqueGeometry` draws the solid and cutout layers one by one with the geometry pipelines through `ChunkSectionsToRenderAccessor`; `supportsGeometryBuffer` is false for wireframe terrain or missing shaders. `renderWaterSurface` draws the translucent layer with the water surface pipelines and the cutout layer with the glass surface pipelines (plain glass reflects). `textures()` hands the block atlas, the lightmap and the atlas bounds of `block/water_still` and `block/water_flow` to the core; `glossyCells()` the glossy mask cells of the block atlas from `VanillaGlossyCells`.

#### VanillaFootprintSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaFootprintSource.java`
- Role: `FootprintSource` over the client level, kept by `VanillaFrameDriver`: every living entity within 32 blocks that was in water during the last 20 seconds leaves a print on the ground every 0.6 blocks it walks, alternating feet.
- Depends on: `FootprintSource`.

#### VanillaWaveSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaWaveSource.java`
- Role: `WaveSource` over the client level, kept by `VanillaFrameDriver` across frames: every rendered entity within the wave window whose feet are in water and whose head is not 0.5 blocks under it makes a continuous wake proportional to its horizontal speed (from `xo`, `zo`), and a splash proportional to its falling speed in the frame it starts touching the surface. `holdsWaves` classifies a column for the shore mask from the client height map with a short scan below roofs.
- Depends on: `WaveSource`.

#### VanillaWeatherSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaWeatherSource.java`
- Role: `WeatherSource` over the client level: the rain level with the partial tick, the game time in seconds, the lightning flash (`ClientLevelAccessor`, off with the hide lightning flash option), whether the biome at the camera has rain there, the `UnderwaterView` while the camera is in water (camera sky light minus the sky darkening, water surface above and floor below the eye from block scans of up to 48 blocks, the averaged biome water color), and, every 10 frames, whether a section within 2 sections of the camera may hold lava (palette check).
- Depends on: `WeatherSource`.

#### VanillaGlossyCells
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaGlossyCells.java`
- Role: Marks the 16 x 16 cells of the block atlas that hold glossy block textures: every registered block whose id starts with `polished_`, contains `quartz`, `prismarine` or `glass`, or ends with `glazed_terracotta`, `ice` or `smooth_stone` (never ores), and the material of rain wetness: dry blocks (`snow`, `magma`), muddy blocks (ids containing dirt, grass_block, podzol, farmland, mud or mycelium, except dirt_path, bricks and packed mud) and porous blocks that darken but hold no puddles (ids containing sand, gravel, dirt, grass_block, podzol, mycelium, farmland, wool, carpet, moss, leaves, hay_block, sponge, soul_soil, nylium, wart_block or concrete_powder, except sandstone, dirt_path and mossy blocks), looked up as `block/<id>` and its face sprites from their UV bounds (the atlas pads sprites, so `getX()` is not where the UVs start), plus `block/lava_still` (lava) and `block/lava_flow` (flowing lava), and emissive cells: the sprites of the baked models (all sides, seed 42) of every block state with light emission that no state without light emission uses, only on cells without another material. Cached per atlas texture.
- Depends on: `GlossyCells`.

#### VanillaWaterPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaWaterPipelines.java`
- Role: Water surface variants of the vanilla translucent terrain pipelines (direct and multi-draw), copied with `toBuilder()` and switched to `helion:water/surface` with one unblended RGBA16_FLOAT target, the `HelionWaterSurface` uniform, the scene depth sampler and the glossy mask. A second pair from the vanilla cutout terrain pipelines adds the define `HELION_GLOSSY_ONLY`: it skips the alpha test and keeps only glossy mask texels, so the see-through center of plain glass is a surface too. Registered as optional pipelines; warns once when they did not compile. Since 2026-10-24 every surface pipeline has a second target, the water tint (RGBA8). Every surface pipeline has a second color target, the water tint (RGBA8).
- Depends on: `WaterPipelines`, `HelionPipelines`.

#### RtxSectionCapture
- Path: `src/main/java/com/aryston/helion/integration/vanilla/RtxSectionCapture.java`
- Role: When ray tracing was requested at startup, copies the quad positions of the solid and cutout layers of a compiled section (vanilla `Position` floats or Aervium's compact `PositionLight`, 1/2048 block steps from -8) into a `RtxSectionUpdate` on the compile worker when the section is near the camera, reports reset sections, and once per frame passes the scene's recompile requests to `LevelExtractor.setSectionDirty`. Warns once and skips terrain in an unknown vertex format. Next to the positions it packs the surface attributes of every vertex: `UV0`, `Color` and `UV2` of the vanilla block format, or `PackedUV0`, `Color` and the light in `PositionLight.w` of the compact format. Each quad also carries its octahedral face normal, and the section carries the block lights `RtxSectionLights` found in it.

#### RtxSectionLights
- Path: `src/main/java/com/aryston/helion/integration/vanilla/RtxSectionLights.java`
- Role: Scans the 16x16x16 blocks of a compiled section on the compile worker for blocks with a light emission above 0 and writes one record of eight ints per light: the section relative box of its shape (a full block for an empty shape), its `LightColors` color and a half float pair of range (level plus one half) and strength (the level linearized with the power 2.2). Above 32 lights it keeps an even stride of them with a strength raised by the share it dropped.
- Depends on: `LightColors`, `RtxSectionUpdate`.

#### LightColors
- Path: `src/main/java/com/aryston/helion/integration/vanilla/LightColors.java`
- Role: Client reload listener for `light/colors.json`, the color of every light source block. A color is normalized to luminance 1, mixed a quarter toward white and its brightest channel capped at 1.25 (L-172); `packed` returns it as RGB8 over a range of 2 with bit 24 set for fire lights that flicker.
- Depends on: nothing of this mod.

#### VanillaGeometryPipelines
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaGeometryPipelines.java`
- Role: Geometry buffer terrain pipelines per chunk layer (direct and multi-draw), copied from the vanilla terrain pipelines with `toBuilder()` and switched to `helion:terrain/geometry` with three more color targets and the glossy mask sampler. They inherit Aervium's compact terrain vertex format and its `AERVIUM_COMPACT_VERTICES` define with the vanilla pipelines. Registered as optional pipelines; warns once when they did not compile or the compact format version is unknown.
- Depends on: `GeometryBufferPipelines`, `HelionPipelines`, `CompactTerrainVertices`.

#### CompactTerrainVertices
- Path: `src/main/java/com/aryston/helion/integration/vanilla/CompactTerrainVertices.java`
- Role: The shader define contract with Aervium's `compactVertices` module (no shared code, rule S2). `supported` is false, with one warning, when the vanilla solid terrain pipeline carries a format version other than 1, so the geometry buffer turns off instead of reading vertices they do not understand.
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
- Role: Render-thread switch for the solid feature phase: in geometry mode only render type features draw, each with its geometry variant, and draws without one are skipped; in remainder mode exactly those skipped draws and the other features (particles, features of other mods) draw. Read by `PreparedRenderTypeMixin` and `FeatureGroupMixin`. `drawn` hands every draw of the geometry pass (quads or triangles with float positions) to `RtxEntityDraws` while the ray tracing scene captures entities, with its `Sampler0` texture and the offsets of its RGBA8 color, float UV0 and 16-bit light attributes when it has them, with the number of vertices it uses when the draw has the sequential index buffer.
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

#### HelionUnderwaterFog
- Path: `src/main/java/com/aryston/helion/integration/vanilla/HelionUnderwaterFog.java`
- Role: Underwater fog extension for realistic water (set each frame by `VanillaWeatherSource`, read by `WaterFogEnvironmentMixin`): eight times the vanilla end, at least 384 blocks, so the `underwater` pass owns the look; expires a second after the last frame.

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
