# Helion Code Map: `com.aryston.helion.render.water`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.water`

#### WaterSurfaceStage
- Path: `src/main/java/com/aryston/helion/render/water/WaterSurfaceStage.java`
- Role: Pass `water_surface` after the solid features, only when ray tracing and water reflections are on and the terrain supports it: draws the translucent terrain layer again into `helion:water_surface` (RGBA16_FLOAT color cleared to zero plus its own depth), with the opaque scene depth as a sampler, so it holds the nearest translucent surface in front of the opaque scene. Water pixels get the octahedral normal (animated waves on flat surfaces), the thickness to the opaque surface behind and the sky light; other translucent blocks write zero. Publishes the target in `WaterResults`.
- Depends on: `TerrainSource`, `WaterResources`, `WaterPipelines`, `WaterResults`.

#### WaterReflectionStage
- Path: `src/main/java/com/aryston/helion/render/water/WaterReflectionStage.java`
- Role: When the water surface and traced reflections exist, publishes a `SceneReflectionPass` that `TransparentGeometryStage` draws over the scene after the translucent geometry: the reflection (or the physical sky in the reflected direction with sun and moon, or the vanilla fog color) weighted by Schlick Fresnel, over water darkened by its thickness toward a scattering color lit by the sky.
- Depends on: `WaterResources`, `WaterPipelines`, `SceneSkyColor`, `FullscreenPass`.

#### WaterPipelines
- Path: `src/main/java/com/aryston/helion/render/water/WaterPipelines.java`
- Role: Water formats, uniform and sampler names, and the two composite pipelines (`water/reflection` with the vanilla fog and `water/reflection_physical_sky` with the sky color bindings), blended with premultiplied alpha.
- Depends on: `HelionPipelines`, `PhysicalSkyPipelines`.

#### WaterResources
- Path: `src/main/java/com/aryston/helion/render/water/WaterResources.java`
- Role: Owned by `HelionRenderCore`: the uniform rings of the surface pass (water sprite bounds, wave strength) and the composite (inverse view projection, reflection strength), and the compiled composite program with a one-time warning when it is missing.

#### WaterResults, SceneReflectionPass
- Path: `src/main/java/com/aryston/helion/render/water/WaterResults.java`, `src/main/java/com/aryston/helion/render/water/SceneReflectionPass.java`
- Role: Per-frame results of the water stages (surface target, traced reflection image, reflection pass) and the contract of the pass that `TransparentGeometryStage` draws.
