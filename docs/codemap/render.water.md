# Helion Code Map: `com.aryston.helion.render.water`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.water`

#### WaterSurfaceStage
- Path: `src/main/java/com/aryston/helion/render/water/WaterSurfaceStage.java`
- Role: Pass `water_surface` after the solid features, only when ray traced water reflections, screen space reflections or realistic water are on and the terrain supports it (binds the glossy mask for the glass pipelines): draws the translucent terrain layer again into `helion:water_surface` (RGBA16_FLOAT color cleared to zero plus its own depth), with the opaque scene depth as a sampler, so it holds the nearest translucent surface in front of the opaque scene. Water pixels get the octahedral normal (animated waves on flat surfaces), the thickness to the opaque surface behind and the sky light; other translucent blocks (ice, stained glass) get their face normal, the glossy marker -1 in place of the thickness and the sky light. Publishes the target in `WaterResults`.
- Waves: binds the latest wave state (repeat, linear) and adds its slope to the wave normal within 60 blocks, fading from 44; without it rain rings come from `helionRainRipples` instead.
- Targets: The surface pass also writes `helion:water_tint` (RGBA8, the vertex color of water, that is its biome color, alpha 1; zero elsewhere) and, with realistic water (`reflections.realisticWater`), first copies the opaque scene color into `helion:water_refraction` with `copyTextureToTexture`, so the composite can show what lies under the water. Surface uniforms carry the rain level and game seconds for rain ripples on water.
- Depends on: `TerrainSource`, `WaterResources`, `WaterPipelines`, `WaterResults`, `GlossyMask`.

#### ScreenReflectionStage, ReflectionSettings
- Path: `src/main/java/com/aryston/helion/render/water/ScreenReflectionStage.java`, `src/main/java/com/aryston/helion/render/water/ReflectionSettings.java`
- Role: Screen space reflections (`reflections.screenSpace`, on in the Experimental preset). The stage also runs without that setting while rain wetness is published (then only the surface sheen target is used and nothing is published for the water composite). For every terrain pixel of the geometry buffer the second target gets the surface sheen, composited by `ssr_polished` with premultiplied alpha: the polished reflection of glossy blocks (with the setting) and the rain wetness of `include/helion_wetness.glsl` (darkening per material, a sky sheen by Fresnel, puddles with rain ripples that march the reflected ray, the physical sky or the fog color where it misses). Two pipelines: with the vanilla fog and with the physical sky color. Pass `ssr` after the water surface: for every flat water pixel and every glossy translucent pixel marches the reflected view ray in camera relative world space (48 growing steps up to about 190 blocks, 5 refinement steps) against the depth of the lit opaque scene, and writes the scene color at the hit with an edge fade as alpha into `helion:screen_reflection` (RGBA16_FLOAT, full resolution). Opaque glossy pixels (light target alpha 0.75, only with the geometry buffer) without a translucent surface in front march from the scene depth along the geometry normal and write the reflection (Schlick, F0 0.15, strength 0.4, faded over 24 blocks, the first on-screen sky pixel when nothing is hit) with alpha 0 into `helion:polished_reflection`, which pass `ssr_polished` adds over the scene before translucent geometry. Mobs and players are in the opaque scene, so they show in the water when they are on screen. Publishes the target in `WaterResults`.
- Depends on: `WaterResources`, `WaterPipelines`, `WaterResults`, `FullscreenPass`.

#### RainWetnessStage, RainWetness, Wetness, WeatherSource, WeatherState
- Path: `src/main/java/com/aryston/helion/render/water/RainWetnessStage.java`, `src/main/java/com/aryston/helion/render/water/RainWetness.java`, `src/main/java/com/aryston/helion/render/water/Wetness.java`, `src/main/java/com/aryston/helion/render/water/WeatherSource.java`, `src/main/java/com/aryston/helion/render/water/WeatherState.java`
- Role: Rain wetness (`reflections.rainWetness`). `RainWetnessStage`, right after the clear stage, every frame and without a pass, publishes whether the camera is in water and, with the setting, feeds the `WeatherState` of the frame (rain level, game seconds, whether it rains in the camera biome, lightning flash) into `RainWetness` (owned by `WaterResources`), which wets surfaces in 30 s and dries them in 90 s, fills puddles in 90 s and dries them in 240 s, and skips frames after a time jump; the `Wetness` (surfaces, puddles, rain, seconds) is published in `WaterResults` while it is visible. `saturate()` lets the visual test start wet.
- Depends on: `WaterResources`, `WaterResults`.

#### Footprints, Footprint, FootprintSource
- Path: `src/main/java/com/aryston/helion/render/water/Footprints.java`, `src/main/java/com/aryston/helion/render/water/Footprint.java`, `src/main/java/com/aryston/helion/render/water/FootprintSource.java`
- Role: Wet footprints. `RainWetnessStage` asks the `FootprintSource` for the steps of the frame (world position, yaw, foot, game seconds) and keeps the newest 32 in `Footprints` (owned by `WaterResources`) for 45 seconds; the visible ones go to `WaterResults` and into the screen reflection uniform, where `helionFootprints` darkens and wets a small oval per print, fading with its age.
- Depends on: `WaterResources`, `WaterResults`.

#### UnderwaterStage
- Path: `src/main/java/com/aryston/helion/render/water/UnderwaterStage.java`
- Role: Pass `underwater` (with realistic water, only while the camera is in water): one fullscreen premultiplied blend of `water/underwater.fsh` over the scene with its depth: distance extinction toward a teal-shifted water fog color, caustics below the eye and light shafts looking up, scaled by the daylight from the physical sky light (0.5 without it).
- Depends on: `WaterResources`, `WaterPipelines`, `WaterResults`, `FullscreenPass`.

#### WaterWaveStage, WaterWaves, WaterShoreMask, WaveSource, WaveImpulse
- Path: `src/main/java/com/aryston/helion/render/water/WaterWaveStage.java`, `src/main/java/com/aryston/helion/render/water/WaterWaves.java`, `src/main/java/com/aryston/helion/render/water/WaveSource.java`, `src/main/java/com/aryston/helion/render/water/WaveImpulse.java`, `src/main/java/com/aryston/helion/render/water/WaterShoreMask.java`
- Role: Interactive water surface (with `reflections.realisticWater`). `WaterWaves` (owned by `WaterResources`) keeps two persistent 1024 x 1024 RGBA16_FLOAT targets (height and previous height, 8 cells per block, 128 x 128 blocks around the camera, addressed by world cell modulo 512 so the window scrolls without copies), runs fixed 60 Hz steps from a time backlog (at most 4 per frame, one reset step after a time jump), one uniform ring per step, and queues splashes until a step consumes them. Pass `water_waves` (before the water surface) draws `water/wave_step.fsh` once per step, ping-ponging the two targets, and publishes the latest state in `WaterResults`. `WaveSource` gives the `WaveImpulse`s of the frame (world position, radius, strength, splash or continuous). `WaterShoreMask` (owned by `WaterWaves`) is the R8 block mask of columns that hold waves, 16 of its 128 rows rescanned per frame through `WaveSource.holdsWaves` and uploaded before the steps; land cells (0x80) only move while puddles fill, never mixing with water cells, so waves reflect from shores.
- Depends on: `WaterResources`, `WaterPipelines`, `WaterResults`, `FullscreenPass`.

#### WaterReflectionStage
- Path: `src/main/java/com/aryston/helion/render/water/WaterReflectionStage.java`
- Role: When the water surface and traced or screen space reflections exist, publishes a `SceneReflectionPass` that `TransparentGeometryStage` draws over the scene after the translucent geometry: the screen space reflection where it hit, else the traced reflection (or the physical sky in the reflected direction with sun and moon, or the vanilla fog color) weighted by Schlick Fresnel, over water darkened by its thickness toward a scattering color lit by the sky. With realistic water it instead replaces every water pixel; surfaces seen from below get Snell's window and total internal reflection, surfaces seen from above: the opaque scene copy, offset by the wave normal by up to 3 % of the screen where the offset pixel is still water, attenuated per channel by the thickness (extinction from clear water 0.30, 0.07, 0.05 per block mixed half and half with the inverse of the linear biome tint), plus the teal-shifted biome tint times the sky above scattered into the lost part, caustics on shallow bottoms by day, mixed with the reflection by Fresnel, plus sun and moon glints.
- Depends on: `WaterResources`, `WaterPipelines`, `SceneSkyColor`, `FullscreenPass`.

#### WaterPipelines
- Path: `src/main/java/com/aryston/helion/render/water/WaterPipelines.java`
- Role: Water formats, uniform and sampler names, the screen space reflection pipelines (two targets, with the vanilla fog or the physical sky color), the polished composite and the two water composite pipelines (`water/reflection` with the vanilla fog and `water/reflection_physical_sky` with the sky color bindings), blended with premultiplied alpha.
- Depends on: `HelionPipelines`, `PhysicalSkyPipelines`.

#### WaterResources
- Path: `src/main/java/com/aryston/helion/render/water/WaterResources.java`
- Role: Owned by `HelionRenderCore`: the rain wetness state, the uniform rings of the surface pass (water sprite bounds, wave strength), the screen pass (view projection and its inverse, flags, wetness, camera position modulo 1024 for the puddle noise) and the composite (inverse view projection, reflection strength), and the compiled composite program with a one-time warning when it is missing.

#### WaterResults, SceneReflectionPass
- Path: `src/main/java/com/aryston/helion/render/water/WaterResults.java`, `src/main/java/com/aryston/helion/render/water/SceneReflectionPass.java`
- Role: Per-frame results of the water stages (surface target, traced reflection image, reflection pass, wetness) and the contract of the pass that `TransparentGeometryStage` draws.
