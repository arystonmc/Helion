# Helion Code Map: `com.aryston.helion.render.post`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.post`

#### PostProcessingStage
- Path: `src/main/java/com/aryston/helion/render/post/PostProcessingStage.java`
- Role: Runs its active post effects in order: the effects of the frame sources (vanilla entity outline) first, then `BloomStage`, then `SharpeningStage`.

#### PresentStage
- Path: `src/main/java/com/aryston/helion/render/post/PresentStage.java`
- Role: Copies scene color and depth into the real main target at the end of the frame. Used when no image effect is active, so foundation frames stay identical to vanilla.

#### BloomStage
- Path: `src/main/java/com/aryston/helion/render/post/BloomStage.java`
- Role: Post effect that builds the bloom mip chain: `bloom_prefilter` (scene to half resolution with emissive detection, adaptive threshold and Karis average), `bloom_down_N` (13-tap downsample) and `bloom_up_N` (tent upsample added onto the next larger mip). Publishes the half resolution result in `PostResults`. Inactive when bloom is off, the scene color is not RGBA8 or a shader failed to compile.
- Depends on: `ImageResources`, `ImagePrograms`, `ImagePipelines`, `PostSampling`, `FullscreenPass`.

#### ImageCompositeStage
- Path: `src/main/java/com/aryston/helion/render/post/ImageCompositeStage.java`
- Role: Output stage used instead of `PresentStage` when bloom, sharpening, exposure or Filmic tone mapping is active. Expands the RGBA8 scene (the sharpened copy when sharpening is on) to HDR, adds bloom, applies exposure, tone mapping and dither, writes the main target in one fullscreen pass and copies depth. Shows only the bloom in the debug view.
- Depends on: `ImageResources`, `ImagePrograms`, `ImagePipelines`, `PostSampling`, `FullscreenPass`.

#### ImagePipelines
- Path: `src/main/java/com/aryston/helion/render/post/ImagePipelines.java`
- Role: Pipeline definitions of the bloom, sharpen, composite and exposure meter and average passes, the uniform and sampler names they bind and the target formats (RGBA16_FLOAT bloom mips, RGBA8 output, RG32_FLOAT exposure meter of 64 x 36 and its 1 x 1 average).
- Depends on: `HelionPipelines`.

#### ImagePrograms
- Path: `src/main/java/com/aryston/helion/render/post/ImagePrograms.java`
- Role: The compiled image pipelines for one frame; empty when any of them is missing.

#### ImageResources
- Path: `src/main/java/com/aryston/helion/render/post/ImageResources.java`
- Role: Persistent image state owned by the core: the `HelionImage` uniform ring, the `EyeAdaptation` and the readback of the metered luminance, the color grading table, the fixed tuning values (bloom mip count, dark and daylight threshold scales, threshold knee) and the one-time missing shader warning. Writes the uniforms once per frame and checks whether the image stages can run. `bloomThreshold` computes the effective threshold for the current ambient light. With automatic exposure on, each frame feeds the newest metered luminance into the adaptation and adds the adapted stops to the exposure setting.
- Depends on: `UniformRing`, `PixelReadback`, `EyeAdaptation`, `ImagePrograms`, `PostResults`.

#### ExposureStage
- Path: `src/main/java/com/aryston/helion/render/post/ExposureStage.java`
- Role: Automatic exposure metering, the first post effect. `exposure_meter` writes the center weighted sum of log2 luminance of the terrain (sky excluded) into a 64 x 36 target, `exposure_average` reduces it to one texel and requests its readback. The average pass never gets culled although nothing reads its output. Resets the adaptation while automatic exposure is off.
- Depends on: `ImageResources`, `ExposurePrograms`, `ImagePipelines`, `FullscreenPass`.

#### ExposurePrograms
- Path: `src/main/java/com/aryston/helion/render/post/ExposurePrograms.java`
- Role: The compiled exposure meter and average pipelines, separate from `ImagePrograms` so a missing exposure shader never switches bloom and tone mapping off.

#### EyeAdaptation
- Path: `src/main/java/com/aryston/helion/render/post/EyeAdaptation.java`
- Role: Adapted exposure in stops: the target is the difference between the calibrated log2 luminance of a noon scene (-4.8) and the metered one, limited to 0 to +0.5 stops (and down to -1.5 stops for scenes metered brighter than -3), approached exponentially (1 per second brightening, 3 per second darkening, steps longer than 0.25 s cut). The first measurement applies at once.

#### ColorGrade
- Path: `src/main/java/com/aryston/helion/render/post/ColorGrade.java`
- Role: Color grading presets (neutral, warm, cool, vivid, cinematic, muted), each with temperature, tint, saturation, contrast, lift, gamma and gain.

#### ColorGradingLut
- Path: `src/main/java/com/aryston/helion/render/post/ColorGradingLut.java`
- Role: Builds the 32 x 32 x 32 grading table on the CPU as a 1024 x 32 RGBA8 strip (red across, blue in 32 slices, green down). `grade` decodes the display color to linear, applies white balance, saturation around the luminance, contrast around middle grey in log2 space and lift, gamma and gain, and encodes it back.

#### ColorGradingResources
- Path: `src/main/java/com/aryston/helion/render/post/ColorGradingResources.java`
- Role: The grading table texture, registered in `GpuResources` and uploaded again only when the selected grade changes.
- Depends on: `ColorGradingLut`, `GpuResources`.

#### ImageSettings
- Path: `src/main/java/com/aryston/helion/render/post/ImageSettings.java`
- Role: Player image settings: tone mapper, exposure in stops, automatic exposure, dither, bloom and sharpening and motion blur and the color grade and depth of field and edge smoothing. `needsComposite()` tells whether the output must go through `ImageCompositeStage`. `FOUNDATION` keeps the plain copy. `withoutMotionBlur()` copies them with motion blur off.
- Depends on: `ToneMapper`, `BloomSettings`.

#### BloomSettings
- Path: `src/main/java/com/aryston/helion/render/post/BloomSettings.java`
- Role: Player bloom settings: enabled, intensity, threshold, debug view.

#### SharpeningSettings
- Path: `src/main/java/com/aryston/helion/render/post/SharpeningSettings.java`
- Role: Player sharpening settings: enabled, strength. Off by default.

#### DepthOfFieldSettings
- Path: `src/main/java/com/aryston/helion/render/post/DepthOfFieldSettings.java`
- Role: Player depth of field settings: enabled, strength (0.5 by default). Off by default while it is experimental. Part of `ImageSettings`.

#### MotionBlurSettings
- Path: `src/main/java/com/aryston/helion/render/post/MotionBlurSettings.java`
- Role: Player motion blur settings: enabled, strength (0.5 by default). Off by default while it is experimental. Part of `ImageSettings`, read by `MotionBlurStage` and by `VanillaFrameDriver`, which keeps the camera reprojection while it is on.

#### SharpeningStage
- Path: `src/main/java/com/aryston/helion/render/post/SharpeningStage.java`
- Role: Post effect that writes a contrast adaptive sharpened copy of the scene into `helion:sharpened` (full resolution, RGBA8) and publishes it in `PostResults`, so `ImageCompositeStage` reads it instead of the scene. Inactive when sharpening is off, the scene color is not RGBA8 or a shader failed to compile.
- Depends on: `ImageResources`, `ImagePrograms`, `ImagePipelines`, `PostSampling`, `FullscreenPass`.

#### ToneMapper
- Path: `src/main/java/com/aryston/helion/render/post/ToneMapper.java`
- Role: Tone mapping choices `NEUTRAL` (Khronos PBR Neutral), `FILMIC` (Neutral plus a film grade) and `NONE`, each with the id the shaders switch on.

#### PostResults
- Path: `src/main/java/com/aryston/helion/render/post/PostResults.java`
- Role: Per-frame results that post stages hand to each other: the bloom target handle, the sharpened scene handle and the image uniform buffer written this frame.

#### PostSampling
- Path: `src/main/java/com/aryston/helion/render/post/PostSampling.java`
- Role: Texture view and clamp-to-edge sampler helpers shared by the image stages.
