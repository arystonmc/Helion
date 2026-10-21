# Helion Code Map: `com.aryston.helion.render.atmosphere`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.atmosphere`

#### AtmosphereSource
- Path: `src/main/java/com/aryston/helion/render/atmosphere/AtmosphereSource.java`
- Role: Contract for sky, clouds, weather and world border. For the physical sky it also provides the `SkyEnvironment` (only in Overworld style skies without a custom sky renderer and with the camera in air), replaces the fog buffer the rest of the frame uses (`useFog`) and draws only the sun, moon and stars (`renderCelestials`).

#### SkyStage
- Path: `src/main/java/com/aryston/helion/render/atmosphere/SkyStage.java`
- Role: Draws the sky into the scene target when the frame renders sky and the source has a sky. Used when the physical sky is off or not available.
- Depends on: `AtmosphereSource`.

#### PhysicalSkyStage
- Path: `src/main/java/com/aryston/helion/render/atmosphere/PhysicalSkyStage.java`
- Role: Physical sky in place of `SkyStage`. Writes the constant transmittance and multiple scattering tables once into persistent targets (four spectral targets each, one multiple render target pass per table), redraws the persistent sun and moon sky view tables only when `SkyViewCache` says their light height or the camera altitude changed (two targets each: scattering with its phase and Mie without it), renders the 1 x 1 average horizon color and requests its readback, renders the 3 x 1 sky light target and publishes it as `SkyLight`, hands the fog buffer without distance fog to the source, draws the sky per pixel into the scene with the vanilla sun, moon and stars on top, and publishes the distance fog pass (`SceneFogPass`) for `TransparentGeometryStage`. Inactive when the setting is off, the frame has no sky, the source has no `SkyEnvironment`, the scene color is not RGBA8 or a shader failed to compile.
- Depends on: `AtmosphereSource`, `PhysicalSkyResources`, `PhysicalSkyPrograms`, `PhysicalSkyPipelines`, `AtmosphereResults`, `SkyLight`, `SceneFogPass`, `FullscreenPass`.

#### PhysicalSkyResources
- Path: `src/main/java/com/aryston/helion/render/atmosphere/PhysicalSkyResources.java`
- Role: Persistent sky state owned by the core: the spectral transmittance and multiple scattering targets (written once, registered in `GpuResources`), the sun and moon sky view targets with their `SkyViewCache`, the constant `HelionSpectrum` buffer (Rayleigh and ozone coefficients and the spectrum to RGB weights from `AtmosphereSpectrum`), the `HelionSky` uniform ring (inverse level view projection, sun and moon directions and brightness, view height, rain, vanilla fog distances), the `Fog` buffer the frame uses under the physical sky (`writeFog`: the read back horizon color, or the vanilla fog color until the first readback, no distance fog, vanilla cloud fog end), the `HorizonColorReadback`, the sun and moon brightness tuning and the one-time missing shader warning. `viewHeight` turns the altitude above sea level into the distance from the planet center.
- Depends on: `UniformRing`, `GpuResources`, `AtmosphereSpectrum`, `PixelReadback` (horizon color for the cloud fog), `SkyEnvironment`, `AtmosphereFog`, `HelionCamera`.
- Notes: the `Fog` buffer has the layout of vanilla `FogRenderer` (`FOG_UBO_SIZE`); terrain, entities and clouds read it through `fog.glsl`.

#### PhysicalSkyPipelines
- Path: `src/main/java/com/aryston/helion/render/atmosphere/PhysicalSkyPipelines.java`
- Role: Pipeline definitions of the lookup table passes (four color targets each), the sun and moon sky view passes (`HELION_MOON_VIEW`, two color targets: scattering and unphased Mie), the sky, fog, horizon and sky light passes, their uniform and sampler names, target sizes and formats.
- Depends on: `HelionPipelines`, `AtmosphereSpectrum`.

#### PhysicalSkyPrograms
- Path: `src/main/java/com/aryston/helion/render/atmosphere/PhysicalSkyPrograms.java`
- Role: The compiled physical sky pipelines; empty when any of them is missing.

#### PhysicalSkySettings
- Path: `src/main/java/com/aryston/helion/render/atmosphere/PhysicalSkySettings.java`
- Role: Player setting: enabled. `DISABLED` for the foundation.

#### SkyEnvironment
- Path: `src/main/java/com/aryston/helion/render/atmosphere/SkyEnvironment.java`
- Role: Per-frame sky inputs: sun and moon directions in world space, rain brightness, camera altitude above sea level in blocks and the vanilla fog values (`AtmosphereFog`). `celestialDirection` turns a vanilla sun or moon angle into the direction vanilla draws the body in.

#### AtmosphereSpectrum
- Path: `src/main/java/com/aryston/helion/render/atmosphere/AtmosphereSpectrum.java`
- Role: Spectral data of the physical sky: 16 wavelengths from 400 to 700 nm in steps of 20 nm, packed in four groups of four. Rayleigh scattering and ozone absorption per wavelength, solar irradiance, CIE 1931 color matching integrated over each 20 nm band, and `toLinearSrgb`, the weights that turn a spectrum into linear sRGB, white balanced so that sunlight is white.
- Notes: the tables and parameters come from Bruneton's Precomputed Atmospheric Scattering, see `THIRD_PARTY_NOTICES.md`.

#### AtmosphereFog
- Path: `src/main/java/com/aryston/helion/render/atmosphere/AtmosphereFog.java`
- Role: Copy of the vanilla fog values of a frame: color, environmental, render distance, sky and cloud fog distances.

#### AtmosphereResults
- Path: `src/main/java/com/aryston/helion/render/atmosphere/AtmosphereResults.java`
- Role: Per-frame results of the physical sky for later stages: the `SkyLight` for deferred lighting and the `SceneFogPass` for transparent geometry, both empty without the physical sky, and the `SceneCloudPass` that `CloudStage` publishes for transparent geometry.

#### SkyLight
- Path: `src/main/java/com/aryston/helion/render/atmosphere/SkyLight.java`
- Role: Handle of the 3 x 1 sky light target (sun and moon light that reaches the ground through the atmosphere, and the irradiance of the sky dome on an upward surface) with the sun and moon directions.

#### SceneFogPass
- Path: `src/main/java/com/aryston/helion/render/atmosphere/SceneFogPass.java`
- Role: Contract of the distance fog of the physical sky: `declareReads` adds its inputs to the frame graph pass that draws it, `draw` blends the fog into the scene.

#### SkyViewCache
- Path: `src/main/java/com/aryston/helion/render/atmosphere/SkyViewCache.java`
- Role: Remembers the light height and the view height one sky view table was drawn with. `isCurrent` holds while the light height differs by at most 2.5 x 10^-4 and the view height by at most one block; `invalidate` forgets the table when its targets are recreated or closed.
