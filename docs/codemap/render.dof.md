# Helion Code Map: `com.aryston.helion.render.dof`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.dof`

#### DepthOfFieldStage
- Path: `src/main/java/com/aryston/helion/render/dof/DepthOfFieldStage.java`
- Role: Depth of field after motion blur. `dof_focus` writes the smoothed focus distance into a persistent 1 x 1 target: the nearer quarter of 25 view depths in a small disc at the screen center (sky ignored, up to 16384 blocks so distant terrain from Aervium can take the focus), kept when it changes by less than 4 % and otherwise followed in log distance, faster when the new focus is nearer; `dof_downsample` writes half resolution color with the signed circle of confusion (negative in front of the focus) in alpha, `dof_gather` blurs it with 32 golden-angle taps scaled by the circle, where a sample only counts when its own blur reaches the pixel and samples behind the pixel are limited by the pixel's own blur, searching at least as far as the largest near blur so a near object blurs over the sharp pixels behind it, and carries that near spread in alpha; and `dof_blend` blends the blurred image over the sharp scene by the pixel's own blur or the near spread, never by the background's blur, so no halo forms around sharp objects, fading in smoothly from no blur to the largest blur on that side of the focus. The circle of confusion (`helion_depth_of_field.glsl`) grows with the log of depth over focus beyond a sharp band that widens with the focus distance, starts with a soft knee, and is capped at 40 % of the largest blur behind the focus and 35 % in front of it.
- Depends on: `DepthOfFieldResources`, `DepthOfFieldPrograms`, `DepthOfFieldPipelines`, `FullscreenPass`.

#### DepthOfFieldResources
- Path: `src/main/java/com/aryston/helion/render/dof/DepthOfFieldResources.java`
- Role: Persistent depth of field state owned by the core: the two 1 x 1 R32_FLOAT focus targets used in turn, the `HelionDepthOfField` uniform ring written once per frame (inverse screen size, largest blur of 1.5 % of the screen height times the strength, focus blend, aperture of 0.6 times the strength) and the one-time missing shader warning. `focusBlend` moves the focus 4 per second toward the new distance; the first frame after a reset takes it at once.
- Depends on: `UniformRing`, `DepthOfFieldPrograms`.

#### DepthOfFieldPipelines
- Path: `src/main/java/com/aryston/helion/render/dof/DepthOfFieldPipelines.java`
- Role: Pipeline definitions of the focus, downsample, gather and blend passes (blend with source alpha over the scene), their uniform and sampler names and formats.
- Depends on: `HelionPipelines`.

#### DepthOfFieldPrograms
- Path: `src/main/java/com/aryston/helion/render/dof/DepthOfFieldPrograms.java`
- Role: The compiled depth of field pipelines; empty when any of them is missing.
