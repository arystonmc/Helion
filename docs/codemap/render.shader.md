# Helion Code Map: `com.aryston.helion.render.shader`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.shader`

#### HelionPipelines
- Path: `src/main/java/com/aryston/helion/render/shader/HelionPipelines.java`
- Role: Registry of every Helion pipeline (temporal, physical sky, clouds, light shafts, image, motion blur, depth of field, edge smoothing, geometry buffer views and water), builder for fullscreen pipelines on the vanilla screen quad, lookup of compiled pipelines.

#### FullscreenPass
- Path: `src/main/java/com/aryston/helion/render/shader/FullscreenPass.java`
- Role: Draws one fullscreen triangle with a pipeline and its bindings into one target, or into several targets at once (one color attachment per target, for pipelines with several color targets), or into a target with its depth attachment (`drawWithDepth`, for pipelines that write depth).
