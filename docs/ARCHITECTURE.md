# Helion Architecture

How the Helion render core is built, how a frame flows through it and which rules keep it maintainable. Read this before changing anything under `render/`, `integration/` or `mixin/`.

## Goal of the Foundation

Helion routes the vanilla world rendering of Minecraft 26.3 through its own render core on the Vulkan backend while the picture stays identical to vanilla. Every later graphics feature (lighting, shadows, atmosphere, post effects) plugs into this core as a stage or as a replacement source.

## Layers

```
Minecraft LevelRenderer.render
   │  LevelRendererMixin (@WrapMethod): Helion active → Helion frame, otherwise vanilla
   ▼
integration/vanilla          Minecraft adapter: builds the frame, owns every vanilla call
   │  LevelRenderHook → VanillaFrameDriver → Vanilla*Source, VanillaFrameTargets, StageEvents
   ▼
render/                      Helion render core: stages, graph, resources, camera
   │  FrameStages: clear → sky → opaque geometry → deferred lighting → solid features → ambient occlusion → transparent geometry → temporal anti-aliasing → post (bloom, sharpen) → present or image composite → geometry buffer view
   ▼
renderpearl api + blaze3d    Mojang GPU abstraction (Vulkan backend)
```

| Layer | Packages | May use |
|---|---|---|
| Render core | `render`, `render.*` | Java, JOML, `com.mojang.blaze3d.*`, `com.mojang.renderpearl.api.*`, and `net.minecraft.resources.Identifier` because the renderpearl API itself uses it for pipeline and shader ids |
| Minecraft adapter | `integration`, `integration.vanilla` | Everything, including vanilla internals through `LevelRendererAccessor` |
| Mixins | `mixin` | Only the hooks listed below |
| Tools | `debug`, `config` | Render core and Minecraft client APIs |

## Frame Flow

1. `LevelRendererMixin` wraps `LevelRenderer.render`. `LevelRenderHook` decides between the Helion frame and the original method.
2. `VanillaFrameDriver` rebuilds the vanilla `render` method step by step:
   - camera reposition, feature submission and `prepareFrame`, exactly as vanilla
   - `HelionRenderCore.beginLevelFrame` resizes or recreates the scene target and starts GPU timing
   - `VanillaFrameTargets` imports the scene target as `bundle.main`, the real main target as the output, and creates the OIT targets
   - `ClientHooks.fireFrameGraphSetup` fires with the scene target as main, so other mods draw into Helion's buffers
   - `FrameStages.build` adds the Helion stages to the frame graph
   - the frame graph executes, then section compile, terrain upload and occlusion update run as in vanilla
3. While the level frame runs, every `GameRenderer.mainRenderTarget()` call made by `LevelRenderer` returns the scene target. This keeps vanilla OIT code that reads the main target directly working on Helion's buffers.
4. `PresentStage` copies scene color and depth into the real main target, or `ImageCompositeStage` writes the tone mapped image into it when an image effect is active. Hand rendering, vanilla post effects and the GUI continue unchanged afterwards.

## Stages

| Order | Stage | Class | What it does |
|---|---|---|---|
| 1 | clear | `render.scene.ClearStage` | Clears scene color to the fog color and scene depth to the reversed-Z far value |
| 2 | sky | `render.atmosphere.SkyStage` | Draws the sky through `AtmosphereSource`, or with the physical sky on `render.atmosphere.PhysicalSkyStage`, see Physical Sky below |
| 3 | opaque_geometry | `render.geometry.OpaqueGeometryStage` | Prepares fog, chunk sampler, translucent buffers and lighting, then draws opaque terrain and solid features. With the geometry buffer on, only opaque terrain, in its own render pass through Helion's terrain shaders, see Geometry Buffer below |
| 3a | shadow_map, shadow_mask | `render.shadow.ShadowStage` | Only with shadows, lighting, the geometry buffer and the physical sky light on: draws the cascaded shadow map and the screen space shadow mask, see Shadows below |
| 3b | deferred_lighting, deferred_shading | `render.lighting.DeferredLightingStage` | Only with lighting and the geometry buffer on: lights terrain in HDR from the geometry buffer, see Deferred Lighting below |
| 3c | solid_features | `render.geometry.SolidFeatureStage` | Only with the geometry buffer on: the after-opaque-blocks event and solid features, in the same order as vanilla |
| 4 | ambient_occlusion | `render.lighting.AmbientOcclusionStage` | Screen space ambient occlusion on the opaque scene, see below. Skipped when disabled or when its shaders failed to compile |
| 5 | transparent_geometry | `render.geometry.TransparentGeometryStage` | Sorted or order independent transparency, clouds, weather, world border, then outline, see-through and always-on-top features. With the physical sky, also its distance fog, see Physical Sky below |
| 5a | terrain_depth (in stage 3), taa_resolve, taa_apply | `render.temporal.TemporalStage` | Only with temporal anti-aliasing on: blends the jittered frame with the reprojected history, see Temporal Anti-Aliasing below |
| 6 | post | `render.post.PostProcessingStage` | Runs post effects in order: the vanilla entity outline effect, then `render.edge.EdgeSmoothingStage` (see Edge Smoothing below), then `render.motion.MotionBlurStage` (see Motion Blur below), then `render.dof.DepthOfFieldStage` (see Depth of Field below), then `render.post.ExposureStage` (automatic exposure metering), then `render.post.BloomStage`, then `render.post.SharpeningStage`, see Image Pipeline below |
| 7 | present | `render.post.PresentStage` | Copies the scene target into the main target when no image effect is active |
| 7 | composite | `render.post.ImageCompositeStage` | Replaces present when bloom, sharpening, exposure or Filmic tone mapping is active: tone maps the scene (or its sharpened copy) with bloom into the main target and copies depth |
| 8 | geometry_view | `render.geometry.GeometryBufferDebugStage` | Only with a geometry buffer view selected: draws normals, block light, sky light or albedo over the main target |

Planned stages and where they go: atmosphere effects after transparent geometry, further Helion post effects inside `PostProcessingStage` after sharpening.

## Render Settings

`RenderSettings` is an immutable snapshot of every effect setting, taken from the client config and carried in `FrameContext`. Stages decide in `isActive` whether they run. `RenderSettings.foundation()` turns every effect off and keeps the plain present copy, but keeps the geometry buffer when it is on (without its view), because the geometry buffer is part of the core and must stay identical to vanilla; the parity check renders its Helion frame with it, so the foundation can always be compared with vanilla while effects are on.

## Shaders and Pipelines

- Shaders live in `src/main/resources/assets/helion/shaders/<feature>/*.fsh`, shared code in `shaders/include/helion_*.glsl`, included as `#include <helion:helion_view.glsl>`.
- GLSL rules of 26.3: `#version 330`, `GL_ARB_separate_shader_objects`, explicit `layout(location)`, no `layout(binding)`, uniforms bound by name, `gl_VertexIndex`.
- Fullscreen passes use the vanilla `minecraft:core/screenquad` vertex shader and `FullscreenPass.draw` (three vertices, no vertex buffer).
- Every pipeline is listed in `HelionPipelines.all()` and registered as optional through `RegisterRenderPipelinesEvent`. Stages look them up with `HelionPipelines.compiled`; a missing pipeline makes the stage skip itself instead of crashing.
- Never sample a depth or color texture that is attached to the same render pass.
- `ShaderCompilationTest` compiles every fragment shader with `glslangValidator` on every build where it is installed (the CI installs it; on Windows it comes with the Vulkan SDK), so a broken shader fails the build instead of only logging a missing pipeline in game.

## Ambient Occlusion

GTAO style horizon based ambient occlusion, ported from Intel XeGTAO (MIT, see `THIRD_PARTY_NOTICES.md`) to fragment shaders. It runs after opaque geometry and before transparent geometry, so water, glass, particles and the hand never receive it.

| Pass | Reads | Writes | Shader |
|---|---|---|---|
| `ao_depth` | scene depth | `helion:ao_view_depth` R32_FLOAT | `view_depth.fsh`: reversed-Z device depth to linear view depth, sky marked with `HELION_SKY_VIEW_DEPTH` |
| `ao_main` | view depth | `helion:ao_raw` RG8_UNORM (occlusion, packed edges) | `gtao.fsh`: edge aware normals from depth, slice and step horizon search with fixed Hilbert R2 noise; GTAO keeps two horizon angles per slice, the visibility bitmask marks 32 cosine weighted sectors per slice |
| `ao_denoise_N` | occlusion, edges | `helion:ao_denoised_N` R8_UNORM | `denoise.fsh`: XeGTAO edge aware 3x3 blur |
| last `ao_denoise_N` | occlusion, edges, view depth, scene color, Fog | R8_UNORM | `denoise_resolve.fsh`: final blur plus multi-bounce, emissive protection, strength and fog fade |
| `ao_apply` | final occlusion, with shadows also `helion:indirect_share` | scene color | `apply.fsh` with multiply blend (`apply_indirect` scales the occlusion by the indirect share of the pixel), or the same shader without blend for the debug view |

**Only indirect light is occluded.** Ambient occlusion describes how much of the sky and the bounce light a point sees; direct sunlight is shadowed by the shadow maps, not by occlusion. With shadows on, the shadowed shading pass of deferred lighting also writes `helion:indirect_share` (R8, cleared to 1): one minus the share of the pixel's light that comes straight from the sun or moon after shadowing. `ao_apply` then uses `mix(1, occlusion, share)`, as UE5 applies ambient occlusion to sky light and indirect lighting only. Fully sunlit ground keeps its brightness, shaded corners keep their occlusion. Measured in a savanna at noon with Ultra Visibility Bitmask occlusion: the ground went from 60 to 72 (76 without occlusion), the rest of the gap to vanilla was automatic exposure darkening, which no longer goes below 0.

Fixed tuning values live in `AmbientOcclusionResources`; per-quality slice, step and denoise counts in `AmbientOcclusionQuality`.

The visibility bitmask method follows Therrien, Levesque and Gilet, "Screen Space Indirect Lighting with Visibility Bitmask" (2023), as an own implementation inside the XeGTAO slice loop: every sample marks the sectors between its front angle and the angle of a point `OCCLUDER_THICKNESS` (0.75 blocks) behind it, sectors are spaced by the sine of the angle to the projected normal so their count is cosine weighted, and samples beyond the effect radius mark nothing. Unlike GTAO it lets light pass behind thin occluders (fences, grass, torches, bars), which removes their dark halos. Without temporal anti-aliasing the noise never changes between frames, because animated noise would shimmer. While temporal anti-aliasing runs, `AmbientOcclusionResources` advances `NoiseIndex` every frame through 64 frames and the shader offsets the Hilbert index by 288 per frame (the spatio-temporal R2 noise of XeGTAO), so the history averages different slice directions and step offsets into smoother occlusion at the same cost.

## Image Pipeline

Bloom, tone mapping, exposure and dither. The scene stays RGBA8 and display referred; HDR is rebuilt in post.

```
scene RGBA8 ──► bloom_prefilter ──► bloom_down_1 … bloom_down_5 ──► bloom_up_4 … bloom_up_0 ──► PostResults.bloom
     │            (half resolution, RGBA16_FLOAT mips)                                              │
     └──────────────────────────────► composite: expand, + bloom, exposure, tone map, dither ◄──────┘ ──► main RGBA8
```

| Pass | Reads | Writes | Shader |
|---|---|---|---|
| `bloom_prefilter` | scene color and depth | `helion:bloom_mip_0` half resolution | `bloom_prefilter.fsh`: sRGB to linear, inverse Neutral shoulder, emissive weight with fog rejection, adaptive soft threshold, negative values clamped, 13-tap downsample with Karis average |
| `bloom_down_N` | mip N-1 | `helion:bloom_mip_N` | `bloom_downsample.fsh`: 13-tap downsample |
| `bloom_up_N` | mip N+1 | mip N, added with `ONE, ONE` blend | `bloom_upsample.fsh`: 3x3 tent filter |
| `sharpen` | scene color | `helion:sharpened` RGBA8, full resolution | `sharpen.fsh`: contrast adaptive sharpening on the display-referred scene |
| `composite` | scene color (or `helion:sharpened`), mip 0 | main color, depth copied with `copyDepthFrom` | `composite.fsh`, or `bloom_debug.fsh` for the bloom-only view |

- **Bloom** follows Jimenez, "Next Generation Post Processing in Call of Duty: Advanced Warfare" (SIGGRAPH 2014): six mips from half resolution, Karis average on the first downsample against flickering single bright pixels, tent upsample. The result is added to the HDR scene with `intensity / mip count`.
- **Emissive detection** without a G-buffer: weight = brightness of the largest encoded channel inside a window times a saturation term, so lava, torch flames and glowstone glow more than white blocks. The window, the saturation floor and an emissive boost follow `Daylight` (the squared ambient light): in daylight only near-clipped pixels pass (window 0.75 to 0.95, floor 0.25, no boost); at night and in caves light blocks that vanilla draws at 200 to 230 pass too (window 0.6 to 0.85, floor 0.5, boost 8). Pixels close to the fog color get no weight, because distant terrain and the horizon are bright only through fog. Sky pixels (device depth `0.0`) only pass when very bright and almost unsaturated, which keeps the sun and moon disks and drops the sky gradient.
- **Adaptive threshold**: `AmbientLightTracker` reads the sky light at the camera after time and weather darkening and smooths it over 1.5 s. `ImageResources.bloomThreshold` scales the threshold from half its value in caves to eight times in full daylight with the square of it, so lights glow clearly at night and in caves while snow and sand stay clean in daylight. The same idea as `eyeBrightnessSmooth` in shader packs.
- **Tone mapping**: `NEUTRAL` uses the highlight shoulder of Khronos PBR Neutral on the largest channel and scales the color by the same factor. The scene is expanded with the exact inverse of that curve, so a frame without bloom and with 0 EV returns the vanilla pixels for every 8-bit color, and hue and saturation never change. The full PBR Neutral with its black offset and highlight desaturation is not used: its inverse is only valid for colors the forward curve can produce, and saturated display colors such as the sky blue gave negative channels that made bloom flash blue. `FILMIC` starts from the same Neutral result and adds a film look: bright cores that bloom pushes above the scene bleach gently toward white, then a gentle S-curve contrast in display space and a vibrance boost that lifts less saturated colors more. AgX was tried first and dropped: it is built for scene-referred light, so on the display-referred vanilla image it turned the sky white (the blue channel sits at 255) and removed about a third of the saturation everywhere. `NONE` skips expansion and clips. Exposure multiplies the HDR value by `2^EV` before tone mapping. A fixed triangular dither of one 8-bit step follows the sRGB encoding.
- **Sharpening** follows the idea of AMD FidelityFX Contrast Adaptive Sharpening (own implementation, no code taken): a negative lobe on the four direct neighbors whose weight shrinks where the local minimum or maximum is close to black or white, so flat areas and already sharp edges are left alone and no halos appear. `SharpenStrength` blends the lobe peak from `-1/8` to `-1/5`. It runs on the RGBA8 scene before the composite, so bloom never gets sharpened and the Neutral round trip keeps the result exact. Off by default until it has been checked in game.
- **Automatic exposure** (off by default, experimental) works like the eye adaptation of UE5 with a log average meter: `ExposureStage` meters the anti-aliased scene before bloom. `exposure_meter` expands 16 samples per cell of a 64 x 36 grid back to linear HDR, skips sky pixels (device depth 0), so a bright sky or the sun never darkens the ground and backlit terrain at sunset gets brighter, and sums their log2 luminance with a center weight (up to 3 in the middle). `exposure_average` reduces the grid to one RG32_FLOAT texel that `PixelReadback` brings to the CPU a few frames later without stalling. `EyeAdaptation` targets -4.8, the metered value of noon terrain in a savanna, so daylight keeps the vanilla brightness, limits the result to 0 to +0.5 stops (+1 lifted the black of night skies into grey, and darkening made bright sunny terrain darker than vanilla) and adapts at 1 stop per second brightening and 3 darkening; the composite multiplies by `2^(exposure + adapted)`. Measured in a savanna: noon +0.1, morning +0.15, sunset toward the sun +0.3, dawn away from the sun +0.5, night +0.5, looking at the sky 0. The meter costs a few hundredths of a millisecond. The exposure pipelines compile separately from the other image pipelines, so a failed exposure shader cannot switch bloom off, and the average pass disables frame graph culling because only the readback consumes it.
- **Color grading** (`image.colorGrade`, neutral by default) applies a 32 x 32 x 32 lookup table after tone mapping, the way UE5 bakes its grading into a LUT: `ColorGradingLut` builds the table on the CPU from the preset (white balance, saturation, contrast around middle grey, lift, gamma, gain in linear light) and uploads it only when the preset changes, and the `composite_graded` pipeline (the composite with `HELION_COLOR_GRADING`) samples it trilinearly before the dither. Neutral keeps the plain composite, so the picture without a grade stays exactly as before.
- **Output selection**: `ImageSettings.needsComposite()` is true for bloom, sharpening, automatic exposure, a color grade, a non-zero exposure or Filmic. Otherwise `PresentStage` copies the scene unchanged, so the foundation and the parity check never run the composite.
- Ported code (the PBR Neutral shoulder and its inverse) is listed in `THIRD_PARTY_NOTICES.md`. Fixed tuning values live in `ImageResources` and at the top of `bloom_prefilter.fsh`.

### Why There Is No Real HDR Scene Target

- Renderpearl requires the pipeline color format to match the attachment format in every case. All vanilla world pipelines are RGBA8.
- A NeoForge pipeline modifier could switch them to RGBA16F, but it breaks the entity outline post chain and RGBA8 passes of other mods, and recompiles synchronously on every resource reload.
- Vanilla lights everything in gamma space inside `[0, 1]`. A float target alone would produce almost the same picture. Real HDR needs changed terrain and entity shaders and arrives with Helion's own lighting.
- Until then HDR is reconstructed in post as described above, and bloom runs on RGBA16_FLOAT internal targets of the frame graph.
- The composite cannot use `copyTextureToTexture` from a float target into the RGBA8 main target because `vkCmdCopyImage` needs matching texel sizes, so it draws a fullscreen pass into main.

## Geometry Buffer

Foundation for Helion's own lighting. Off by default (`geometryBuffer.enabled`). Read by deferred lighting and the debug view; shadows will read it too. Its targets are cleared to zero in the geometry pass, so alpha 0 marks pixels without terrain.

- `VanillaGeometryPipelines` copies the vanilla `SOLID_TERRAIN`, `CUTOUT_TERRAIN` and their multi-draw pipelines with `toBuilder()`, so every define (`ALPHA_CUTOUT`), bind group, vertex format and depth state stays vanilla, and replaces the shaders with `helion:terrain/geometry` plus three more color targets.
- Aervium's `compactVertices` module can switch the vanilla solid and cutout terrain to a 16-byte vertex format and adds the define `AERVIUM_COMPACT_VERTICES=1`; the geometry pipelines inherit both through `toBuilder()`, and `helion_terrain_vertex.glsl` decodes it to the same position, texture coordinate and light values, so the rest of both terrain shaders is unchanged. The contract is the define, not shared code (rule S2); `CompactTerrainVertices` turns the geometry buffer and shadows off with a warning for a format version Helion does not know.
- `terrain/geometry.vsh` and `.fsh` repeat the vanilla `core/terrain` math line by line for target 0, so the scene color stays identical to vanilla (the parity check runs with the geometry buffer when it is on). The extra targets are filled from data vanilla throws away: the lightmap coordinates before they become a color, the vertex color before light, and the face normal from screen derivatives of the camera relative position.
- GLSL only promises the same `gl_Position` in two programs when both declare it `invariant`, and vanilla does not. The geometry pipelines still produced bit-identical depth on NVIDIA (RTX 4050, driver 616.92) in every visual test scene and in game with ticks frozen. If the parity check ever shows depth-only differences on terrain with ticks frozen on another GPU, leave color and depth to the vanilla terrain pass and fill the geometry buffer in a second pass that tests against the scene depth without writing it, with a small depth bias toward the camera (`DepthStencilState` supports it, vanilla uses it for block cracks).
- Renderpearl requires the color attachment count of a render pass to match the pipeline, so opaque terrain gets its own render pass with four attachments. Solid features, the `AFTER_OPAQUE_BLOCKS` event and other mods keep drawing into the usual one-attachment pass afterwards (`SolidFeatureStage`), in the same order as vanilla, after deferred lighting.
- Per layer pipelines go through `ChunkSectionsToRender.renderLayers` (invoker mixin) with the vanilla override parameters, the same mechanism vanilla uses for wireframe and OIT terrain. Wireframe terrain (F3 debug) and missing shaders fall back to the vanilla path.

| Target | Format | Content |
|---|---|---|
| 0 | scene RGBA8 | vanilla terrain color with fog |
| 1 `helion:geometry_normal` | RGB10A2_UNORM | world space face normal as `n * 0.5 + 0.5`, straight up for crossed plant planes (grass, flowers, saplings) so plants are lit and shadowed like the ground they stand on, alpha 1 where terrain was drawn |
| 2 `helion:geometry_light` | RGBA8_UNORM | R block light, G sky light (lightmap coordinate / 240), B chunk fade-in visibility, alpha 1 where terrain was drawn |
| 3 `helion:geometry_albedo` | RGBA8_UNORM | texture color times vertex color (biome tint and vanilla shading), before light and fog; alpha is the vanilla fog amount of the pixel |

Translucent terrain, entities, particles and the sky are not in the geometry buffer yet; their pixels keep alpha 0.

Renderpearl in 26.3 has no compute shaders (`ShaderType` only has vertex and fragment), so every geometry buffer consumer is a fragment pass.

## Deferred Lighting

Helion lights opaque terrain itself from the geometry buffer, in high dynamic range. Off by default (`lighting.enabled`) and only active while the geometry buffer is written. It runs between the terrain pass and the solid features, so entities are never lit twice, and before ambient occlusion and transparent geometry, which work on its result exactly as on vanilla terrain.

| Pass | Reads | Writes | Shader |
|---|---|---|---|
| `deferred_lighting` | geometry light | `helion:light_buffer` RGBA16_FLOAT | `deferred_light.fsh`: ambient, sky and block light computed separately from the vanilla light map formula, each with its own intensity, without the vanilla clamp, in linear space; alpha is the daylight reference, 0 where there is no terrain |
| `deferred_shading` | light buffer, albedo, geometry light; with shadows also normals, sky light and shadow mask | scene color (color channels only) | `deferred_shading.fsh`: albedo times the clamped light gives the vanilla color, which is expanded with the inverse Neutral shoulder and multiplied by how far the light exceeds the reference, then encoded with the Neutral shoulder, faded in and fogged like vanilla |

- **Vanilla first.** `helion_lighting.glsl` repeats `core/lightmap.fsh` term by term (ambient or night vision, sky color times sky brightness, block tint times block brightness, boss fog darkening, darkness effect, brightness option) and interpolates between whole light levels like the bilinear light map lookup. Where the light stays at or below the vanilla cap of 1, the result is the vanilla pixel; the visual test `lighting` variant differs from `vanilla` only on light blocks, their surroundings at night, moving clouds and particles.
- **HDR.** Vanilla cuts light at 1, so a block lit by a torch at level 15 (block factor 1.4) looks the same as one in full daylight. Helion keeps the excess: `radiance = inverseNeutral(vanilla color) × max(light / reference, 1)`. The scene stays RGBA8 and display referred, encoded with the same Neutral shoulder the composite inverts (see Image Pipeline), so bloom and exposure see the real brightness.
- **Daylight reference.** Without eye adaptation, torches would double the brightness of the ground at noon. The reference is the light without block light times `HELION_SKY_ADAPTATION` (8), at least 1: in daylight block light hardly adds anything, at night and in caves it adds fully. Histogram exposure will replace this fixed adaptation.
- **Intensities.** Block light intensity scales the block term (1.0 = vanilla levels, without the cap). Sky light intensity scales the sky term; above 1.0 it brightens shade, dusk and night, full daylight keeps the vanilla brightness because of the reference.
- **Physical sky light.** When the physical sky published a `SkyLight`, the light pass uses the `HELION_PHYSICAL_SKY_LIGHT` variant, which also reads the geometry buffer normals and the 3 x 1 sky light target. Per pixel it adds sun light times `max(n · sun, 0)`, moon light times `max(n · moon, 0)` and an ambient term, and uses that sum as the color of the sky term, scaled to the luminance of the vanilla sky light color. The ambient term blends, by how far the normal points up, the sky dome irradiance with its saturation cut to 35 % (toward the sky) and a grey ground bounce of 30 % of the light that reaches the ground (toward the ground). The full blue of the dome lit every face turned away from the sun in pure sky blue at vanilla brightness: yellow savanna grass became green, acacia logs became blue. The brightness stays vanilla (and the daylight reference uses the vanilla color), only the color is physical: warm at dawn on faces toward the sun, slightly cool in shade. The sun and moon directions are in the `HelionLighting` block, zero without the physical sky.
- **Night light.** As the sun sinks from 0.05 to -0.1 of elevation, the color of the physical sky light blends into the vanilla sky light color, so moonlit terrain keeps the blue of vanilla nights instead of the neutral grey of the physical moon and the desaturated dome (night ground hue 108 to 119, vanilla 115).
- **Light-only view** (`lighting.lightOnlyView`) shades with white albedo, for checking the light alone.
- The geometry pass stores what the shading needs per pixel (fog amount, chunk fade-in), so the lighting passes never reconstruct positions from depth.

## Temporal Anti-Aliasing

Off by default (`temporalAntiAliasing.enabled`). Every frame the level is drawn with the camera moved by less than a pixel, and each frame is blended with the history of the previous ones, so edges and fine textures are averaged over many sample positions.

1. **Projection.** `GameRendererMixin` hands the level projection that `GameRenderer.renderLevel` uploads (with view bobbing and nausea distortion) to `TemporalResources`. In a Helion frame with temporal anti-aliasing, `VanillaFrameDriver` uploads the same matrix with the jitter of `JitterSequence` (Halton 2, 3, eight offsets) added in device coordinates, and restores vanilla's projection after the frame. Vanilla frames, parity frames and the hand are never jittered.
2. **Motion vectors** come from depth and the camera: `PreviousView.reprojection` builds one matrix from current device coordinates and depth to the previous frame (current jittered view projection inverted, camera movement, previous unjittered view projection, current jitter). Entities have no motion vectors of their own yet; step 3 handles them through depth instead.
3. **`taa_resolve`** reads the scene color and depth and the previous history. It takes the motion of the closest depth in the 3x3 neighborhood, so edges reproject with the foreground, samples the history with a five-tap Catmull-Rom filter, clips it to the variance box (mean ± one standard deviation in YCoCg) of the current neighborhood, and blends with weight 0.1 for the current frame, both sides weighted by 1 / (1 + luma) against flicker. History outside the screen or after a reset is replaced by the current frame. With the geometry buffer on, `terrain_depth` (in `OpaqueGeometryStage`) copies the depth right after the terrain pass, so every pixel whose final depth is nearer than the terrain belongs to something drawn later (entities, their shadow spots, particles, water, clouds), a moving object. The history stores the depth of such pixels in its alpha channel (0 for terrain and sky). Where the current pixel shows no object but the 3x3 history around its previous position held an object more than 10 % nearer than the expected depth, the object has moved away and the history is replaced by the current frame (depth based disocclusion as in AMD FSR 2), which removes the trail mobs left behind. Object pixels blend at least half of the current frame (a reactive weight like the FSR 2 reactive mask), because without their own motion vectors their history comes from the wrong place and smeared them. Comparing against terrain depth instead of the full depth keeps distant thin foliage, which appears and disappears with the jitter, out of the rejection, so terrain keeps its full anti-aliasing. Measured with a cow crossing the test platform at 6 blocks per second: the trailing ghost is gone and its textures are visibly sharper; `terrain_depth` costs 0.01 ms.
4. **`taa_apply`** copies the result into the scene, so ambient occlusion has already been applied and bloom, sharpening and the composite work on the anti-aliased image. Sharpening is the intended partner against the slight softness.
5. **History** lives in two persistent RGBA16_FLOAT targets (`TemporalHistory`) used in turn and imported into the frame graph. It is dropped after a resize, a camera jump of 16 blocks or more, a frame without temporal anti-aliasing and every vanilla frame.
6. **Render scale (temporal upscaling).** With a render scale below native (`temporalAntiAliasing.renderScale`: ultra quality 77 %, quality 67 %, balanced 59 %, performance 50 %, the ratios of AMD FSR 2) the scene target is created at the internal size, so every vanilla and Helion pass up to temporal anti-aliasing renders fewer pixels, while the history and `taa_resolve` run at the output size. The resolve finds the jittered position of each output pixel in the internal image (output coordinate times internal size plus the jitter in internal pixels) and rebuilds the current color from the 3 x 3 internal pixels around it with the weight `exp(-2.29 d²)` (the Blackman-Harris fit of UE TAAU); the weight of the nearest sample also scales how much the current frame counts (at least a quarter), so output pixels that no sample hit this frame lean on the history. The jitter cycle grows to 8 times the square of the ratio (FSR 2), so samples cover every output pixel. `taa_apply` writes the history and the nearest internal depth into `helion:upscaled`, an output sized color and depth target that becomes the scene, so motion blur, exposure, bloom, sharpening and the composite run at full resolution; the hand and the interface are drawn by vanilla after the level at full resolution. Without temporal anti-aliasing, or when its shaders are missing, the scale stays native. At native size the resolve does exactly what it did before.

The techniques follow Karis, "High Quality Temporal Supersampling" (SIGGRAPH 2014), Salvi's variance clipping (GDC 2016) and Jimenez's five-tap Catmull-Rom history filter (Filmic SMAA, SIGGRAPH 2016), implemented from the publications.

## Motion Blur

Off by default (`image.motionBlur.enabled`, experimental). Camera motion blur after the approach of McGuire, Hennessy, Bukowski and Osman, "A Reconstruction Filter for Plausible Motion Blur" (I3D 2012), the method behind the motion blur of UE5 and most current engines; own implementation from the publication.

1. **Velocity.** Every pixel gets the screen distance it moved since the last frame from its depth and the camera reprojection of `TemporalResources` (the same matrix temporal anti-aliasing uses; with anti-aliasing off it is tracked without jitter). Entities have no velocity of their own yet, so the blur follows camera movement and turning only. The vector is scaled by the strength and by 1/30 s divided by the smoothed frame time (at most 8 frames), so the blur looks the same at 60 and at 240 frames per second, and limited to 48 pixels.
2. **`motion_blur_tiles`** keeps the longest vector of 16 samples per 16 x 16 pixel tile, **`motion_blur_neighbors`** the longest of the 3 x 3 tiles around, so blur reaches across the edges of moving areas.
3. **`motion_blur_gather`** skips pixels whose neighborhood moves less than half a pixel, so a still camera leaves the image untouched. Otherwise it takes 12 taps along the tile vector with interleaved gradient noise against banding and weights each tap by soft depth comparison, cone and cylinder terms of both speeds, so a sharp foreground edge never smears into the background behind it.
4. **`motion_blur_apply`** copies the result into the scene. The stage runs after temporal anti-aliasing, so the history stays sharp, and before exposure metering and bloom.

Measured on the visual test platform turning at 60° per second: ground edge detail drops by 11 % with the default strength 0.5, a still camera gives the same image as without blur, and the four passes cost about 0.1 ms at 854 x 480 (about 0.5 ms at 1080p).

## Edge Smoothing

Off by default (`image.edgeSmoothing`). A light anti-aliasing filter for players who do not use temporal anti-aliasing, own implementation from the description of FXAA 3.11 (Lottes, NVIDIA 2011), no code taken: a pixel is skipped when the luma range of its four neighbors is below 1/8 of the brightest neighbor (at least 0.0312); otherwise the edge direction comes from the second derivatives across the 3 x 3 neighborhood, the stronger side picks the edge, a search of up to 10 steps each way along the edge finds its ends, and the pixel is resampled across the edge by how close it is to the nearer end, or by a sub-pixel term for single pixel features, whichever is larger. It runs on the display referred scene before motion blur, exposure and bloom, and it is skipped while temporal anti-aliasing runs, which already smooths edges. It costs about 0.1 ms at 854 x 480.

## Depth of Field

Off by default (`image.depthOfField.enabled`, experimental). A gather depth of field in the spirit of Jimenez, "Next Generation Post Processing in Call of Duty: Advanced Warfare" (SIGGRAPH 2014) and the UE5 cinematic depth of field, own implementation, running after motion blur at the output size.

1. **Focus.** `dof_focus` takes the nearer quarter of 25 view depths in a small disc at the screen center (the sky ignored, at most 256 blocks), keeps the stored focus when the target differs by less than 4 % and otherwise moves it in log distance at 4 per second (1 - e^(-4 t)), faster toward a nearer target, in a persistent 1 x 1 target used in turn with its predecessor, so focus follows the crosshair smoothly instead of jumping.
2. **Circle of confusion.** The log of depth over focus, minus a sharp band of 0.25 that widens by 0.08 per log of the focus distance, so looking far keeps the ground around the player sharp. The part beyond the band starts with a quadratic knee over 0.4 and then grows linearly, so the blur starts without a visible edge; it is multiplied by the aperture and limited to 0.35 in front of the focus (negative) and 0.4 behind it. The aperture is 0.6 times the strength and the largest blur 1.5 % of the screen height times the strength, so the default (0.5) gives a gentle lens; a first version with four times the aperture blurred everything off the focus, even the ground in front of the player. Measured cost on the test platform: about 0.07 ms at 854 x 480.
3. **`dof_downsample`** halves the image and stores the circle of the 2 x 2 pixel with the largest blur in alpha. **`dof_gather`** samples 32 golden-angle taps over the circle of the pixel; a tap counts only when its own circle reaches the pixel, so a blurred foreground spreads over sharp things behind it, and a tap behind the pixel counts only within the pixel's own circle, so a blurred background never bleeds over a sharp foreground.
4. **`dof_blend`** draws the blurred half resolution image over the sharp scene with an alpha that rises smoothly (smoothstep) from no blur to the largest blur on that side of the focus (at least one pixel), so the focused area stays untouched and the blur fades in over a range of depths. Before, the alpha reached one at 1.5 pixels of blur and the blur started without a knee: looking into the distance with the default strength, the switch from the full resolution scene to the half resolution blurred image happened within about 24 pixel rows of the ground (17 to 26 blocks away) and showed as a line; now it spans about 76 rows (10 to 24 blocks).

## Physical Sky

Off by default (`physicalSky.enabled`). In the Overworld skybox without a custom sky renderer, with the camera in air (not in water, lava or powder snow), the flat vanilla sky color, the sunrise glow and the vanilla fog color are replaced by a sky computed from how sunlight and moonlight scatter in an Earth-like atmosphere. The vanilla sun, moon phases and stars stay and are drawn on top. Everywhere else `SkyStage` draws the vanilla sky.

| Pass | Writes | Shader |
|---|---|---|
| `sky_transmittance` | persistent 256 x 64, four RGBA16_FLOAT targets, written once | `transmittance.fsh`: spectral transmittance to the top of the atmosphere per height and view angle |
| `sky_multiple_scattering` | persistent 32 x 32, four RGBA16_FLOAT targets, written once | `multiple_scattering.fsh`: spectral second order scattering over 64 directions with ground albedo, extended to infinite orders with the geometric series |
| `sky_view_sun`, `sky_view_moon` | persistent 192 x 108, two RGBA16_FLOAT targets each, redrawn only when the light height or the camera altitude changed | `sky_view.fsh`: spectral scattering per view direction relative to the light, finer near the horizon and the light, converted to linear sRGB; target 0 holds Rayleigh single scattering with its phase plus multiple scattering, target 1 Mie single scattering without its phase |
| `sky_horizon` | frame 1 x 1 RGBA8 | `horizon.fsh`: average sky color of 32 horizontal directions, read back to the CPU for the cloud fog |
| `sky_light` | frame 3 x 1 RGBA16_FLOAT | `sky_light.fsh`: sun light and moon light at the camera height after the atmosphere, and the irradiance of the sky dome on an upward surface, for deferred lighting |
| `sky` | scene color | `sky.fsh`: both tables of a light, the Mie part times the Mie phase of the exact angle between the pixel and the light; sun sky times the sun brightness plus moon sky times the moon brightness, rain dims and greys it, Neutral encoding; afterwards `renderCelestials` draws the vanilla sun, moon, stars and dark disc |
| fog, drawn inside `transparent_geometry` | scene color, blended | `fog.fsh`: vanilla fog distances, physical sky color in the direction of each pixel |

- **Method.** Follows Hillaire, "A Scalable and Production Ready Sky and Atmosphere Rendering Technique" (EGSR 2020), with the transmittance parameterization of Bruneton and Neyret (2008) and Earth parameters (Rayleigh, Mie with g = 0.8, ozone); own implementation from the publications. 1 block is 1 m; the camera height above sea level moves the viewer up in the atmosphere. Sun and moon directions come from the vanilla sun and moon angles exactly as vanilla rotates the celestial bodies, so the vanilla sun sits in the middle of the scattering glow.
- **Spectral color.** Light is computed at 16 wavelengths from 400 to 700 nm (20 nm apart), packed as four RGBA groups, so the lookup tables are written with four color targets in one pass. The sky view passes turn the spectrum into linear sRGB with weights from the CIE 1931 color matching functions and the XYZ to sRGB matrix, white balanced to the solar spectrum, so direct sunlight is white and sunsets get their orange from the atmosphere alone. Rayleigh and ozone coefficients per wavelength, the solar spectrum and the CIE tables are in `AtmosphereSpectrum` (data from Bruneton's Precomputed Atmospheric Scattering, BSD-3, see `THIRD_PARTY_NOTICES.md`); `AtmosphereSpectrumTest` checks them against Hillaire's RGB coefficients within 1 %. The per-wavelength values reach the shaders in the constant `HelionSpectrum` buffer.
- **Brightness.** `PhysicalSkyResources.SUN_ILLUMINANCE` (16) puts the noon zenith at the vanilla brightness after Neutral encoding; `MOON_ILLUMINANCE` (0.08) gives a dark blue moonlit night close to vanilla's darkness. The sky is HDR around the sun, so bloom picks the glow up. Directions below the horizon use the horizon color.
- **Color next to vanilla.** Three art choices keep the sky close to what players know, measured against vanilla in a savanna (saturation of the upper image half): `helionSkyColor` raises the saturation of the displayed sky and fog by 1.35 (noon 28 to 37, vanilla 37), the aerosol (Mie) density is half of Hillaire's Earth value for cleaner air with a less white horizon, and the vanilla sunrise and sunset glow is drawn over the physical sky (dusk 8.8 to 12.1, vanilla 15.4), because the physical model alone keeps almost no orange once the sun is below the horizon. The saturation only changes what is displayed; the sky light that lights terrain uses the physical radiance.
- **Fog.** Vanilla mixes the sunrise color into its fog color when the camera looks toward the sun, so a physical sky blended with vanilla fog changed color as the head turned. Under the physical sky `useFog` replaces the frame's `Fog` buffer (shared through `SceneFog`) with one that has no distance fog, so terrain, entities and translucents are drawn unfogged. The fog draw then fogs everything with depth with the vanilla environmental and render distance fog, but in the physical sky color of the pixel's direction, so fog and sky always meet without a seam. In the sorted path it runs after translucent terrain, entities and particles and before clouds, weather and the world border; in the order independent path after the OIT composite. Sky pixels (depth 0) are left alone.
- **Cloud fog.** Clouds fade with the vanilla cloud fog distance toward the color in the `Fog` buffer. That color is the average horizon color from `sky_horizon`, read back without stalling (`HorizonColorReadback`, three buffers, a few frames old); until the first readback arrives it is the vanilla fog color.
- **Glow around the sun and moon.** Mie scattering with g = 0.8 peaks about fifty times above its average within a few degrees of the light. Baked into the 192 x 108 table, the bilinear lookup moved that peak to the nearest table row (about 0.75° apart near the horizon), and the moon glow sat visibly beside the vanilla moon. The table therefore stores Mie scattering without its phase, and `helion_sky_color.glsl` multiplies it with the phase of the exact per-pixel angle, so the glow is always centered on the vanilla sun and moon.
- **View direction.** The sky and the fog take the direction of a pixel from two points on its ray (device depth 1.0 and 0.01) unprojected with the inverse level view projection. The level projection contains the view bobbing of `GameRenderer.bobView`, which also moves the eye by up to a tenth of a block; a single point unprojected close to the near plane would turn that shift into a large change of direction, and the sky would sway against the world while walking.
- **Sky light.** `sky_light` is published as `SkyLight` for deferred lighting, see below.
- **Cost.** The two constant tables are written once into persistent targets. The sky view tables depend only on the height of their light above the horizon and on the camera altitude, so they are persistent too and `SkyViewCache` redraws a table only when the height of its light changed by more than 2.5 x 10^-4 (about one tick of sun travel) or the camera moved more than one block up or down since it was drawn; before that they cost 0.37 and 0.28 ms every frame. Each frame costs about 0.8 to 1.6 ms on the development GPU (RTX 4050) when the tables are drawn: `sky_view_sun` and `sky_view_moon` 0.2 to 0.4 ms each, `sky_light` 0.2 to 1.1 ms. `sky_light` integrates the sky dome over 128 directions in only three fragments, so its time is mostly latency and varies a lot; spreading that work over more fragments is an open optimization.
- Aerial perspective (scattering in the air between the camera and terrain) is not part of it yet; the fog pass stands in for it at the vanilla distances.

## Shadows

Off by default (`shadows.enabled`). Cascaded shadow maps from the sun, or from the moon while the sun is below the horizon, applied by the shading pass of deferred lighting. `ShadowStage` runs after the terrain pass, so it can read scene depth and the geometry buffer normals, and before deferred lighting, whose shading pass reads its mask. It needs the geometry buffer, deferred lighting and a `SkyLight` from the physical sky, because only the physical sky light separates the sun and moon terms from the sky dome.

| Pass | Writes | Shader |
|---|---|---|
| `shadow_map` | four persistent D32_FLOAT targets, one per cascade (`ShadowQuality`: 1024, 1536 or 2048 texels square), only the cascades due this frame | `shadow/caster.vsh`, `caster.fsh`: solid and cutout terrain from the light, depth only, slope and constant depth bias |
| `shadow_mask` | frame R8_UNORM `helion:shadow_mask` | `shadow/mask.fsh`: per terrain pixel the first cascade that holds it, 4 x 4 tent filtered lookup, 1 lit and 0 shadowed |

- **Cascades.** `ShadowCascades` splits the view up to the shadow distance (render distance, at most 160 blocks) at 8, 22, 50 and 100 %. Each slice gets a bounding sphere, so its size does not change when the camera turns, and its orthographic projection is snapped to whole texels of the shadow map in world space, so edges do not crawl when the camera moves. The light direction of the shadows turns in steps of 0.015° (`ShadowLight.stepped`), the distance the sun travels in one game tick, so shadows follow the sun smoothly. Steps of 0.25° (one every 0.8 s) were tried first against shimmer in the dappled shade under trees, but every step made the shadows jump visibly, worst for long shadows of hills at a low sun. With one step per tick the shadow map changes at most 20 times per second and each change moves a shadow by a fraction of a shadow texel. The depth range extends 256 blocks toward the light, so mountains outside the view still cast into it.
- **Update schedule.** `ShadowCascadeCache` decides every frame which cascades are drawn. A cascade is drawn again when the light took a step, when it is older than its refresh time (50 ms for the two near cascades, 100 ms for the third and 200 ms for the far one, so changed blocks reach the shadows within one to four ticks) or when the camera moved or turned so far that its center shifted by more than a quarter of its radius. The near cascades are drawn at once; the third only on even and the far one only on odd frames, so no frame draws both. A cascade is drawn at once in any frame when it has never been drawn, the quality or the light (sun or moon) changed, the light turned by more than 0.5° or its radius changed (field of view or render distance). A cascade that waits keeps its shadow map, its matrices and the camera position it was drawn with; the mask moves its matrix by the camera movement since then, and because cascades are snapped to texels in world space the waiting map still shadows exactly the same places. Entity shadows are drawn every frame with that same moved matrix, so they line up with the cached terrain map. Drawing the near cascades every frame cost a frame rate that grew with the frame rate itself; with the schedule a frame at 200 FPS draws a cascade only about every tenth frame.
- **Casters.** `VanillaShadowCasterSource` collects every built chunk section within the render distance that lies inside or toward the light of a cascade drawn this frame, scanning only the sections inside the world space bounds of those cascades, puts them temporarily into `LevelRenderer.visibleSections` and lets vanilla prepare the draws with the light view, once per cascade, then restores the camera's sections. It must use the same draw path as the camera terrain: vanilla keeps the chunk section data of the direct and the multi-draw indirect path in one storage and closes it when the path changes, which would invalidate the camera's draws. Crossed plant planes (grass, ferns, flowers, saplings, detected from the face normal in `caster.fsh`) cast no shadow: they are thinner than a shadow map texel at most distances, so their shadows covered the ground in speckles that shimmered with every camera move. The section list comes from `ShadowCasterSections`, which keeps only sections with solid or cutout geometry and rescans the view area only when the camera enters another section, the view area changes or 20 frames passed; before it, the far cascade prepared about 47,000 mostly empty sections (6.4 ms of CPU time every fourth frame) and the scan itself took 1.8 ms per frame. The caster pipelines are built from the vanilla terrain snippets without any color target: a render pass whose first color attachment is unused crashes in `FrontendCommandEncoder.createRenderPass`. Translucent layers and particles do not cast shadows yet; entities do, see Entities below.
- **Entities.** Entities, block entities and items are dynamic casters, drawn every frame into a separate target per near cascade while the cached terrain maps stay untouched (the static and dynamic split of UE5 virtual shadow maps). Their vanilla pipelines test reversed depth and need a color target, so each entity target has an RGBA8 color and a D32 depth attachment at half the cascade resolution, cleared to 0, and `shadow_entities` replays `PreparedFrame.executeSolid` with the projection swapped for `ShadowCascades.entityProjection`: the cascade's light projection and view, times the inverse of the camera rotation that the entity draws carry as their model view, with depth mirrored. The mask turns the stored depth back with `1 - depth` and keeps the darker of terrain and entity visibility. Only the two near cascades (about 35 blocks) get entity shadows, and only entities the camera sees cast them. Measured on the test platform with an armor stand and a cow: 0.02 ms.
- **Targets.** Each cascade has its own depth target and its own render pass that clears only it, so a cascade that waits keeps its content. The mask binds the four targets as `ShadowMapSampler0` to `3`. An atlas with one viewport per cascade is not possible: `RenderPass.setViewport` in the Vulkan backend allocates the new viewport with zeroed memory and never sets its depth range, so every depth written through a custom viewport is 0 and the whole world reads as shadowed; and a shared atlas can only be cleared as a whole.
- **Mask.** The mask pass reconstructs the world position from scene depth, offsets it 1.5 texels along the normal and 1 texel toward the light against acne, and fades to lit over the last 15 % of the shadow distance. Faces turned away from the light stay lit in the mask because the light term is already zero there.
- **Lighting.** Shadows are applied in `deferred_shading`, not in the light buffer. The shadowed variant (`HELION_SHADOWS`) computes a transmission per color channel: the sun, moon and sky dome light of the pixel's normal with the sun term times `1 - SunShadowStrength × shadow` (moon likewise), divided by the unshadowed sum, then blended toward 1 by how much of the pixel's vanilla light comes from the sky term (so torches and lava fill shadows). The linear shaded color is multiplied by it after the vanilla brightness curve. Applied before that curve, the shadow vanished: the Brightness option lifts every light value with `1 - (1 - x)^4`, which turns 0.3 into 0.97. Lit pixels keep a transmission of exactly 1 and stay identical to the image without shadows; shadowed pixels keep the blue dome light. `ShadowLight` fades the strength to zero between 0.12 and 0.02 elevation of the light, so shadows never pop at sunrise, sunset or moonrise. The visual test platform has a stone pillar and a floating roof away from the light blocks, because block light fills shadows next to emitters.
- **Cost.** Measured on the development GPU at render distance 12 with every effect on (2026-10-16): `shadow_map` 0.73 ms per frame on average with the near cascades drawn every frame and 0.45 ms with the time-based schedule; the benchmark rose from 201.2 / 141.1 / 93.8 to 227.2 / 152.6 / 103.7 FPS (average / 1% low / 0.1% low). Before the first update schedule `shadow_map` took about 2.9 ms at medium quality and render distance 20 on the development GPU (RTX 4050), mostly in the far cascade, which covers about 450 x 450 blocks; `shadow_mask` takes about 0.02 ms. With the schedule a frame draws the two near cascades plus at most one far cascade, and the CPU only prepares the sections of the cascades it draws. In a savanna at render distance 20 with every effect on, the caster preparation fell from about 4.5 ms to under 1 ms per frame on average once empty sections were skipped and the section list was cached, and the frame rate rose from 118 to 181. Culling sections that cannot reach any lit receiver is an open optimization.

## Sources

Stages never call vanilla code directly. They talk to sources:

| Source | Vanilla implementation | Replace it to |
|---|---|---|
| `TerrainSource` | `VanillaTerrainSource` (chunk sections, direct and multi-draw indirect) | render terrain with Helion's own meshes |
| `EntitySource` | `VanillaEntitySource` (feature render dispatcher) | render entities with Helion materials |
| `AtmosphereSource` | `VanillaAtmosphereSource` (sky, clouds, weather, border) | render Helion sky and weather |
| `TransparencySource` | `VanillaTransparencySource` (vanilla OIT) | use Helion transparency |

Sources are created per frame in `VanillaFrameDriver.sources`.

## Invariants

1. The render core never imports `net.minecraft` or `net.neoforged`, with one exception: `net.minecraft.resources.Identifier`, which the renderpearl API itself requires. Vanilla access lives in `integration` and `mixin` only.
2. Renderpearl is used through `api` and `frontend` types. Backend packages (`renderpearl.backend.*`) are not used.
3. Helion never crashes the game. A runtime exception in the Helion frame passivates the core (`RENDER_FAILURE`) and vanilla rendering continues from the next frame.
4. Every GPU resource Helion creates is registered in `GpuResources` with a label and size, and released through it.
5. NeoForge events (`FrameGraphSetupEvent`, every `RenderLevelStageEvent`, `PrepareRenderBuffersEvent`) fire in the same order and with the same render pass as vanilla.
6. Depth is reversed-Z: clear value `0.0`, near maps to `1.0`.
7. Everything runs on the render thread.
8. Any new mixin is added to this document and the code map with the reason no event or API covers it.

## Vanilla Coupling

Places that copy or depend on vanilla internals. Check each of them first when Minecraft or NeoForge updates.

| Helion code | Vanilla code it mirrors |
|---|---|
| `VanillaFrameDriver.renderLevel`, `finishLevel` | `LevelRenderer.render` |
| `VanillaFrameTargets.create`, `declareGeometryAttachments` | OIT target creation in `LevelRenderer.render`, resource declarations in `LevelRenderer.addMainPass` |
| `OpaqueGeometryStage.render`, `TransparentGeometryStage.render`, `VanillaTerrainSource`, `VanillaEntitySource`, `VanillaAtmosphereSource` | `addMainPass`, `executeSolid`, `executeClassicTransparency` |
| `VanillaAtmosphereSource.hasSky`, `isSkyVisible`, `renderSky` | `LevelRenderer.addSkyPass` |
| `VanillaFrameDriver.keepVanillaSkyExtracted` | the lazy creation of `LevelRenderer.skyRenderer` in `addSkyPass`; `LevelExtractor` extracts the sky state only while that renderer exists, so without it the skybox stays `NONE` and no sky is drawn |
| `ClearStage` | the `clear` pass in `LevelRenderer.render` |
| `LevelRendererAccessor` | private fields and methods of `LevelRenderer` |
| `terrain/geometry.vsh`, `terrain/geometry.fsh` | `core/terrain.vsh`, `core/terrain.fsh` (non-OIT path) |
| `VanillaGeometryPipelines` | `RenderPipelines.SOLID_TERRAIN`, `CUTOUT_TERRAIN` and their multi-draw variants |
| `helion_lighting.glsl`, `VanillaFrameDriver.lightEnvironment` | `core/lightmap.fsh`, `sample_lightmap.glsl`, `Lightmap`, `LightmapRenderStateExtractor` |
| `GameRendererMixin`, `VanillaFrameDriver` (jittered projection), `helionViewRay` in `helion_sky_color.glsl` | the level projection upload in `GameRenderer.renderLevel`, including the translation and rotation of `bobHurt` and `bobView` |
| `VanillaAtmosphereSource.skyEnvironment`, `SkyEnvironment.celestialDirection` | `SkyRenderer.extractRenderState` (sun and moon angles, rain), `FogData`, `FogRenderer.setupFog` |
| `SkyRendererMixin`, `HelionSky`, `VanillaAtmosphereSource.renderSky` and `renderCelestials` | `SkyRenderer.render`, `renderSkyDisc`, `renderSunriseAndSunset`, `renderSunMoonAndStars`: the physical sky relies on the sky disc and sunrise glow being separate methods of `render`; the moon dimming relies on `renderMoon` passing its color to `DynamicGpuData.writeTransform` |
| `PhysicalSkyResources.writeFog`, `SceneFog`, `fog.fsh` | the `Fog` uniform block layout written by `FogRenderer.updateBuffer` (`FOG_UBO_SIZE`: color, environmental start and end, render distance start and end, sky end, cloud end) and `total_fog_value` in `fog.glsl` |
| `TransparentGeometryStage` (fog after OIT) | `CloudRenderer.renderOit` and the OIT setup in `LevelRenderer`: OIT clouds write their depth into the separate `cloud_depth` target, never into scene depth, so cloud pixels read as sky in the fog pass and keep their own cloud fog |
| `VanillaTerrainSource.renderOpaqueGeometry` | `ChunkSectionsToRender.renderGroup` and `renderLayers` |
| `VanillaShadowCasterSource` | `LevelRenderer.prepareChunkRenders`, `prepareChunkRendersIndirect` and `extractSectionDrawGroups` (which read `visibleSections`), and `DynamicGpuData.writeChunkSections` and `writeChunkSectionsInstanced`, which close each other's storage |
| `VanillaShadowPipelines`, `shadow/caster.vsh` | `RenderPipelines.TERRAIN_SNIPPET`, `MULTIDRAW_TERRAIN_SNIPPET`, the `ALPHA_CUTOUT` define of `CUTOUT_TERRAIN`, the vertex format and `AERVIUM_COMPACT_VERTICES` define of `SOLID_TERRAIN` and `CUTOUT_TERRAIN`, and the vertex inputs of `core/terrain.vsh` |
| `helion_terrain_vertex.glsl`, `CompactTerrainVertices` | the compact vertex layout of Aervium's `compactVertices` module (format version 1: position and light as four unsigned shorts, color, texture coordinates as two unsigned shorts over 65536) |

`SkyRenderer` keeps the render target it was created with, so Helion owns a separate `SkyRenderer` bound to the scene target (`SceneSkyRenderer`).

## Mixins

| Mixin | Target | Why no API is enough |
|---|---|---|
| `LevelRendererMixin.helion$render` | `LevelRenderer.render` | NeoForge can add passes but cannot replace the vanilla pass structure |
| `LevelRendererMixin.helion$redirectMainTarget` | `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` | Vanilla OIT and depth bounds code reads the main target directly instead of the frame graph handle |
| `GameRendererMixin.helion$captureLevelProjection` | `ProjectionMatrixBuffer.getBuffer` inside `GameRenderer.renderLevel` | The final level projection includes view bobbing and nausea distortion and is built inside `renderLevel`; no event exposes it, and temporal anti-aliasing must jitter and reproject exactly this matrix |
| `SkyRendererMixin.helion$skipUnderPhysicalSky` | `SkyRenderer.renderSkyDisc` | `SkyRenderer.render` draws the sky color and the celestial bodies in one call; under the physical sky only the vanilla sunset glow, sun, moon and stars must stay |
| `LevelRendererAccessor` | private members of `LevelRenderer` | Required to rebuild `render` with vanilla behavior |
| `ChunkSectionsToRenderAccessor.helion$renderLayers` | private `ChunkSectionsToRender.renderLayers` | Only this method takes per layer pipeline overrides; the public `renderGroup` applies one override to every layer, and solid and cutout terrain need different pipelines |

## Porting Checklist for a New Minecraft Version

1. Diff `LevelRenderer.render`, `addSkyPass`, `addMainPass`, `executeSolid`, `executeClassicTransparency` against the previous version.
2. Update the classes in the Vanilla Coupling table.
3. Build, start the game with `--graphicsBackend vulkan` and turn on debug mode and run the parity check (`J`) in every scene of the Parity Scenes list below.
4. Record anything surprising as a lesson in the workspace `docs/RULES.md`.

## Testing Tools

- Debug mode (Mods → Helion → Config → Debug Mode, off by default): shows every value of `HelionDebugSnapshot` on screen without F3 and writes it as one JSON line per second to `logs/helion-debug.jsonl`, together with `toggle`, `passive` and `parity` events. GPU time per stage is only measured in debug mode (up to 32 timed passes per frame, including every `bloom_*` pass and `composite`). While it is off the debug entry, the JSON log and the timestamp queries do no work at all.
- Parity check, `J` key in debug mode: after warm-up frames on each side, captures one vanilla frame and the Helion foundation frame right after it (all effects off) within the same client tick, because the torch flicker changes the light map every tick even with ticks frozen, reads both main targets back from the GPU and reports differing pixels in chat and in the JSON log. It aborts with a message when a Helion frame could not be rendered by Helion. Use `/tick freeze` and keep the camera still: without it clouds, particles and entities move between the two captured frames, and the chat says so. A failed check reports the largest depth difference in steps of the depth format and writes `screenshots/helion_parity_differences.png` (red color only, yellow color and depth, blue depth only), so the location of a difference is known before its cause is guessed.
- `H` key: switches between Helion and vanilla rendering at runtime.
- Automated visual test: `launcher/launch.ps1 -World "Helion Visual Test" -Define helion.visualTest=true` loads a copy of a save, clears the area around the player (with `-Define "helion.visualTest=true;helion.visualTest.natural=true"` it keeps the world as it is, to photograph real terrain such as a savanna), builds a row of light blocks in front of it, switches to spectator, freezes time and weather, and photographs eleven scenes (noon sky, sunrise sun, horizon, light blocks at noon and in the morning, so their shadows fall to the side, sunset, dusk with the orange glow just after sunset, night sky, light blocks by night, and dawn looking toward and away from the sun) in eleven variants each (vanilla, None, Neutral, Filmic, bloom only, lighting, light only, temporal, physical sky, shadows, shadow light) into `screenshots/helion_<scene>_<variant>.png` at half resolution, then runs the parity check in every scene with ticks frozen and logs whether all pixels match vanilla (a failure is a `WARN` line). Every shot also writes a `Helion visual test` log line with the full debug snapshot as JSON, and the game closes cleanly afterwards. Run it on a copy of a world, never on a real save, because it changes blocks, time and game rules. Development runs only.

## Parity Scenes

Run the parity check (`J` in debug mode) in each scene before committing a change to the render core:

- Overworld at day and at night, during rain, under water
- Nether and End
- Improved transparency on and off (F3 + X)
- A glowing entity (entity outline), spectating a creeper (spectator post effect)
- F1 hidden GUI and third person camera
