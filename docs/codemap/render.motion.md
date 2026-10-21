# Helion Code Map: `com.aryston.helion.render.motion`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.motion`

#### MotionBlurStage
- Path: `src/main/java/com/aryston/helion/render/motion/MotionBlurStage.java`
- Role: Camera motion blur, the first post effect, after temporal anti-aliasing so its history stays sharp. `motion_blur_tiles` keeps the longest blur vector of 16 sampled pixels per 16 x 16 tile, `motion_blur_neighbors` the longest of the 3 x 3 tiles around, `motion_blur_gather` blurs each pixel with 12 jittered taps along that vector, weighted by depth and speed so sharp foreground edges do not smear into the background, and `motion_blur_apply` copies the result into the scene. Inactive without a valid camera reprojection (first frame, teleport) or with a scene format other than RGBA8.
- Depends on: `MotionBlurResources`, `MotionBlurPrograms`, `MotionBlurPipelines`, `FullscreenPass`.

#### MotionBlurResources
- Path: `src/main/java/com/aryston/helion/render/motion/MotionBlurResources.java`
- Role: Persistent motion blur state owned by the core: the `HelionMotionBlur` uniform ring written once per frame (reprojection, inverse screen size, blur scale, 48 pixel limit), the smoothed frame time and the one-time missing shader warning. `blurScale` turns the strength into a blur that looks the same at every frame rate: strength times 1/30 s divided by the frame time, limited to 1 to 8 frames.
- Depends on: `UniformRing`, `MotionBlurPrograms`.

#### MotionBlurPipelines
- Path: `src/main/java/com/aryston/helion/render/motion/MotionBlurPipelines.java`
- Role: Pipeline definitions of the tile, neighbor, gather and apply passes, uniform and sampler names, the RG16_FLOAT velocity format and the tile size.
- Depends on: `HelionPipelines`.

#### MotionBlurPrograms
- Path: `src/main/java/com/aryston/helion/render/motion/MotionBlurPrograms.java`
- Role: The compiled motion blur pipelines; empty when any of them is missing.
