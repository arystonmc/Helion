# Helion Code Map: `com.aryston.helion.render.clouds`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.clouds`

#### CloudStage
- Path: `src/main/java/com/aryston/helion/render/clouds/CloudStage.java`
- Role: Volumetric clouds. Runs after solid features when the setting is on, the physical sky published its `SkyLight` and the `CloudSource` has a `CloudEnvironment`; tells the source every frame whether the vanilla clouds are replaced. Writes the noise atlas once (`cloud_noise`), raymarches one pixel of every 2 x 2 block of the cloud resolution (rotating each frame) into an internal half size color and distance target (`cloud_march`), blends that with the reprojected history into the next history (`cloud_resolve`), renders the 192 x 192 upper hemisphere cloud sky map for water reflections while realistic water, screen space or ray traced water reflections are on (`cloud_sky_map`, 60 % of the steps, every frame) and publishes a `SceneCloudPass` that `TransparentGeometryStage` draws over the scene after the fog. The `HelionClouds` uniform is written once per frame on first use.
- Depends on: `CloudSource`, `CloudResources`, `CloudPrograms`, `CloudPipelines`, `SkyLight`, `AtmosphereResults`, `FullscreenPass`.

#### CloudResources
- Path: `src/main/java/com/aryston/helion/render/clouds/CloudResources.java`
- Role: Persistent cloud state: the 528 x 528 noise atlas (written once), the two color and distance history pairs at the cloud resolution, the uniform ring, the previous level view projection and camera position for reprojection, and the frame index for the dither and the 2 x 2 march order. Holds the cloud tuning: layer from y 600 to 1100, extinction, coverage (0.46 in clear weather), fade from 14 to 40 km. Wraps the camera position into the 24576 block noise period and the time into a 6144 second period, so floats keep their precision and the wind stays seamless. The sun lights the clouds while it is above -0.05, the moon otherwise.
- Depends on: `GpuResources`, `UniformRing`, `HelionCamera`, `CloudPrograms`.

#### CloudPipelines
- Path: `src/main/java/com/aryston/helion/render/clouds/CloudPipelines.java`
- Role: Pipelines of the noise, march, sky map march (`HELION_CLOUD_SKY_MAP`, size 192), resolve (two color targets each for the marches and the resolve) and composite passes, their uniform and sampler names and target formats.
- Depends on: `HelionPipelines`.

#### CloudPrograms
- Path: `src/main/java/com/aryston/helion/render/clouds/CloudPrograms.java`
- Role: The compiled cloud pipelines; empty when any of them is missing.

#### CloudQuality
- Path: `src/main/java/com/aryston/helion/render/clouds/CloudQuality.java`
- Role: Low (quarter resolution, 24 steps, 3 light steps), Medium (third, 40, 4) and High (half, 56, 6, with the cirrus layer).

#### CloudSettings
- Path: `src/main/java/com/aryston/helion/render/clouds/CloudSettings.java`
- Role: Player setting: enabled and quality. `DISABLED` for the foundation and Sodium mode.

#### CloudEnvironment
- Path: `src/main/java/com/aryston/helion/render/clouds/CloudEnvironment.java`
- Role: Per-frame cloud inputs: game time in seconds with the partial tick, and rain strength.

#### CloudSource
- Path: `src/main/java/com/aryston/helion/render/clouds/CloudSource.java`
- Role: Contract for the cloud inputs and for leaving the vanilla clouds out while the volumetric clouds replace them.

#### SceneCloudPass
- Path: `src/main/java/com/aryston/helion/render/clouds/SceneCloudPass.java`
- Role: Contract of the cloud composite: `resolved` is the resolved cloud color and transmittance of this frame (read by `LightShaftStage`), `skyMap` the cloud sky map when it was rendered (read by `WaterReflectionStage`), `declareReads` adds the resolved clouds to the frame graph pass that draws it, `draw` blends them into the scene.
