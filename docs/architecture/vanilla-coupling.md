# Helion Architecture: Vanilla Coupling

Part of the [Helion architecture](../ARCHITECTURE.md).

## Vanilla Coupling

Places that copy or depend on vanilla internals. Check each of them first when Minecraft or NeoForge updates.

| Helion code | Vanilla code it mirrors |
|---|---|
| `VanillaFrameDriver.renderLevel`, `finishLevel` | `LevelRenderer.render` |
| `VanillaFrameTargets.create`, `declareGeometryAttachments` | OIT target creation in `LevelRenderer.render`, resource declarations in `LevelRenderer.addMainPass` |
| `OpaqueGeometryStage.render`, `TransparentGeometryStage.render`, `VanillaTerrainSource`, `VanillaEntitySource`, `VanillaAtmosphereSource` | `addMainPass`, `executeSolid`, `executeClassicTransparency` |
| `VanillaAtmosphereSource.hasSky`, `isSkyVisible`, `renderSky` | `LevelRenderer.addSkyPass` |
| `VanillaFrameDriver.keepVanillaSkyExtracted` | the lazy creation of `LevelRenderer.skyRenderer` in `addSkyPass`; `LevelExtractor` extracts the sky state only while that renderer exists, so without it the skybox stays `NONE` and no sky is drawn |
| `ClearStage` | the `clear` pass in `LevelRenderer.render` |
| `LevelRendererAccessor` | private fields and methods of `LevelRenderer` |
| `terrain/geometry.vsh`, `terrain/geometry.fsh` | `core/terrain.vsh`, `core/terrain.fsh` (non-OIT path) |
| `terrain/dynamic_light.vsh` | `core/terrain.vsh` (non-OIT path) |
| `VanillaGeometryPipelines` | `RenderPipelines.SOLID_TERRAIN`, `CUTOUT_TERRAIN` and their multi-draw variants |
| `GameRendererMixin`, `VanillaFrameDriver` (jittered projection), `helionViewRay` in `helion_sky_color.glsl` | the level projection upload in `GameRenderer.renderLevel`, including the translation and rotation of `bobHurt` and `bobView` |
| `VanillaAtmosphereSource.skyEnvironment`, `SkyEnvironment.celestialDirection` | `SkyRenderer.extractRenderState` (sun and moon angles, rain), `FogData`, `FogRenderer.setupFog` |
| `SkyRendererMixin`, `HelionSky`, `VanillaAtmosphereSource.renderSky` and `renderCelestials` | `SkyRenderer.render`, `renderSkyDisc`, `renderSunriseAndSunset`, `renderSunMoonAndStars`: the physical sky relies on the sky disc and sunrise glow being separate methods of `render`; the moon dimming relies on `renderMoon` passing its color to `DynamicGpuData.writeTransform` |
| `PhysicalSkyResources.writeFog`, `SceneFog`, `fog.fsh` | the `Fog` uniform block layout written by `FogRenderer.updateBuffer` (`FOG_UBO_SIZE`: color, environmental start and end, render distance start and end, sky end, cloud end) and `total_fog_value` in `fog.glsl` |
| `TransparentGeometryStage` (fog after OIT) | `CloudRenderer.renderOit` and the OIT setup in `LevelRenderer`: OIT clouds write their depth into the separate `cloud_depth` target, never into scene depth, so cloud pixels read as sky in the fog pass and keep their own cloud fog |
| `CloudRendererMixin`, `VanillaCloudSource` | `CloudRenderer.render` and `renderOit` (the only places that draw vanilla clouds), the cloud status option, `LevelRenderState.cloudColor` and `customCloudsRenderer` |
| `VanillaTerrainSource.renderOpaqueGeometry` | `ChunkSectionsToRender.renderGroup` and `renderLayers` |
| `helion_terrain_vertex.glsl`, `CompactTerrainVertices` | the compact vertex layout of Kinetic's `compactVertices` module (format version 1: position and light as four unsigned shorts, color, texture coordinates as two unsigned shorts over 65536) |

`SkyRenderer` keeps the render target it was created with, so Helion owns a separate `SkyRenderer` bound to the scene target (`SceneSkyRenderer`).
