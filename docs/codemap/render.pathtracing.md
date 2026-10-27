# Helion Code Map: `com.aryston.helion.render.pathtracing`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.pathtracing`

The render pipelines of the path traced light that need no ray tracing types, so they register with the other Helion pipelines without loading the ray tracing package. The traced passes themselves live in `render.rtx` (`RtxPathTracingStage`); see `docs/architecture/path-tracing.md`.

#### PathTracingPipelines
- Path: `src/main/java/com/aryston/helion/render/pathtracing/PathTracingPipelines.java`
- Role: `RADIANCE`, the fullscreen pipeline of `pathtracing/radiance.fsh` that multiplies the denoised path traced irradiance by the albedo and encodes it into the RGBA8 scene target (color only): the `Fog` block and the irradiance, albedo, geometry light and geometry normal samplers. Listed in `HelionPipelines.all()`.
