# Helion Code Map: `com.aryston.helion.render.temporal`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.temporal`

#### TemporalStage
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalStage.java`
- Role: Temporal anti-aliasing after transparent geometry. `taa_resolve` blends the jittered scene with the reprojected history into the next history target, using the moving object variant when a terrain depth copy exists, `taa_apply` writes the result back into the scene. With a render scale below native the history and the resolve run at the output size and `taa_apply` instead writes the history and the nearest internal depth into the output sized `helion:upscaled` color and depth target, which becomes the scene for every later stage. Both history targets are imported into the frame graph. Inactive when the temporal frame is inactive or the scene color is not RGBA8.
- Depends on: `TemporalResources`, `TemporalPrograms`, `TemporalPipelines`, `FullscreenPass`.

#### TemporalResources
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalResources.java`
- Role: Persistent temporal state owned by the core: the jitter frame index, the previous view, the history targets, the `HelionTemporal` uniform ring and the one-time missing shader warning. `begin` returns the `TemporalFrame` of a level frame (jittered projection, reprojection, whether the history is usable). With temporal anti-aliasing off but motion blur on it still tracks the previous view and returns an inactive frame with an unjittered camera reprojection; the history is dropped after a resize, a jump of 16 blocks or more, a frame without temporal anti-aliasing and every vanilla frame (`invalidate`).
- Depends on: `TemporalHistory`, `PreviousView`, `JitterSequence`, `TemporalPrograms`, `UniformRing`, `HelionCamera`.

#### TemporalFrame
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalFrame.java`
- Role: Per-frame temporal data: active, the jittered level projection, the reprojection matrix from current device coordinates to the previous frame, history valid, camera motion valid (the reprojection may be used for motion blur). `INACTIVE` for frames without temporal anti-aliasing.

#### PreviousView
- Path: `src/main/java/com/aryston/helion/render/temporal/PreviousView.java`
- Role: Unjittered projection, view rotation and camera position of the previous frame. `reprojection` builds the matrix that maps current device coordinates and depth to the previous frame, including the camera movement and the current jitter.

#### JitterSequence
- Path: `src/main/java/com/aryston/helion/render/temporal/JitterSequence.java`
- Role: Sub-pixel camera offsets from the Halton (2, 3) sequence in internal pixels and in device coordinates. The cycle has 8 offsets at native resolution and 8 times the square of the upscale ratio when upscaling (at most 64), as FSR 2 does, so every output pixel receives samples.

#### TemporalHistory
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalHistory.java`
- Role: Two persistent RGBA16_FLOAT history targets used in turn, recreated on resize and registered in `GpuResources`.
- Depends on: `GpuResources`.

#### TemporalPipelines
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalPipelines.java`
- Role: Pipeline definitions of the resolve pass, its moving object variant (`HELION_MOVING_OBJECTS`, which also binds the terrain depth) and the apply pass, the uniform and sampler names they bind and the target formats.
- Depends on: `HelionPipelines`.

#### TemporalPrograms
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalPrograms.java`
- Role: The compiled temporal pipelines (resolve, moving object resolve, apply); empty when any of them is missing.

#### TemporalSettings
- Path: `src/main/java/com/aryston/helion/render/temporal/TemporalSettings.java`
- Role: Player settings: enabled and the render scale. `effectiveScale` is native while temporal anti-aliasing is off, because the upscaler is the temporal resolve. `DISABLED` for the foundation.

#### RenderScale
- Path: `src/main/java/com/aryston/helion/render/temporal/RenderScale.java`
- Role: Internal resolution presets with the ratios of FidelityFX FSR 2: native, ultra quality (1.3x), quality (1.5x), balanced (1.7x) and performance (2x). `internalSize` rounds a native size down by the factor.

#### FrameSize
- Path: `src/main/java/com/aryston/helion/render/temporal/FrameSize.java`
- Role: Internal and output size of a level frame, the upscale ratio and whether the frame is upscaled.
