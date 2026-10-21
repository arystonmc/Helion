# Helion Code Map: `com.aryston.helion.render.edge`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.edge`

#### EdgeSmoothingStage
- Path: `src/main/java/com/aryston/helion/render/edge/EdgeSmoothingStage.java`
- Role: Light single pass anti-aliasing, the first post effect, active only while temporal anti-aliasing is off. `edge_smoothing` writes the filtered image into `helion:edge_smoothed`, `edge_smoothing_apply` copies it back into the scene. Reports missing shaders once.
- Depends on: `EdgeSmoothingPipelines`, `FullscreenPass`, `HelionPipelines`.

#### EdgeSmoothingPipelines
- Path: `src/main/java/com/aryston/helion/render/edge/EdgeSmoothingPipelines.java`
- Role: Pipeline definitions of the smoothing and apply passes, their sampler names and the scene color format.
- Depends on: `HelionPipelines`.
