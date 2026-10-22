# Helion Code Map: `com.aryston.helion.render.lightshaft`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.lightshaft`

#### LightShaftStage
- Path: `src/main/java/com/aryston/helion/render/lightshaft/LightShaftStage.java`
- Role: Sun and moon light shafts. Runs after `CloudStage` when the setting is on, the physical sky published its `SkyLight` and `LightShaftView` finds the light in front of the camera. `light_shaft_mask` writes, at a quarter of the scene resolution, how much bright sky around the light each texel shows (four scene depth taps before translucent terrain, so glass and water let light through, times the resolved cloud transmittance when clouds run); `light_shaft_blur` blurs the mask radially toward the light. Publishes a `SceneLightShaftPass` that `TransparentGeometryStage` draws over the scene right after the clouds. The `HelionLightShafts` uniform is written once per frame on first use.
- Depends on: `LightShaftResources`, `LightShaftPrograms`, `LightShaftPipelines`, `LightShaftView`, `SkyLight`, `SceneCloudPass`, `AtmosphereResults`, `FullscreenPass`.

#### LightShaftView
- Path: `src/main/java/com/aryston/helion/render/lightshaft/LightShaftView.java`
- Role: Where the shaft light is this frame: the sun while it is above -0.05, the moon otherwise (like the clouds), its screen position from the level view projection and a visibility that fades out once the light is more than 0.6 screens off the edge, below the horizon and in rain (rain brightness to the fourth power). Empty behind the camera or when nothing would show, so the stage costs nothing then.

#### LightShaftResources
- Path: `src/main/java/com/aryston/helion/render/lightshaft/LightShaftResources.java`
- Role: The uniform ring, the one-time missing shader warning and the tuning: quarter resolution, 48 blur samples with a decay of 0.975 over 85 % of the way to the light, a glow of cos^40 around the light (plus a cos^6 halo in the shader), and the shaft strength of the sun and the moon.
- Depends on: `GpuResources`, `UniformRing`, `HelionCamera`, `LightShaftPrograms`.

#### LightShaftPipelines
- Path: `src/main/java/com/aryston/helion/render/lightshaft/LightShaftPipelines.java`
- Role: Pipelines of the mask (with and without the cloud sampler, `HELION_CLOUD_SHAFTS`), blur and composite passes, their uniform and sampler names and formats (R8 mask, RGBA16_FLOAT shafts). The composite screen blends (`ONE`, `ONE_MINUS_SRC_COLOR`) into the RGBA8 scene, so shafts brighten without clipping.
- Depends on: `HelionPipelines`.

#### LightShaftPrograms
- Path: `src/main/java/com/aryston/helion/render/lightshaft/LightShaftPrograms.java`
- Role: The compiled light shaft pipelines; empty when any of them is missing.

#### SceneLightShaftPass
- Path: `src/main/java/com/aryston/helion/render/lightshaft/SceneLightShaftPass.java`
- Role: Contract of the shaft composite: `declareReads` adds the blurred shafts and the sky light to the frame graph pass that draws it, `draw` adds the shafts, colored by the sun or moon light through the atmosphere, to the scene.
