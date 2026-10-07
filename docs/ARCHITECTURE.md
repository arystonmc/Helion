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
| 5a | taa_resolve, taa_apply | `render.temporal.TemporalStage` | Only with temporal anti-aliasing on: blends the jittered frame with the reprojected history, see Temporal Anti-Aliasing below |
| 6 | post | `render.post.PostProcessingStage` | Runs post effects in order: the vanilla entity outline effect, then `render.post.BloomStage`, then `render.post.SharpeningStage`, see Image Pipeline below |
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
| `ao_apply` | final occlusion | scene color | `apply.fsh` with multiply blend, or the same shader without blend for the debug view |

Fixed tuning values live in `AmbientOcclusionResources`; per-quality slice, step and denoise counts in `AmbientOcclusionQuality`.

The visibility bitmask method follows Therrien, Levesque and Gilet, "Screen Space Indirect Lighting with Visibility Bitmask" (2023), as an own implementation inside the XeGTAO slice loop: every sample marks the sectors between its front angle and the angle of a point `OCCLUDER_THICKNESS` (0.75 blocks) behind it, sectors are spaced by the sine of the angle to the projected normal so their count is cosine weighted, and samples beyond the effect radius mark nothing. Unlike GTAO it lets light pass behind thin occluders (fences, grass, torches, bars), which removes their dark halos. The noise never changes between frames; animated noise would shimmer without temporal anti-aliasing, and with it on the noise can be animated later so the history averages it out.

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
- **Output selection**: `ImageSettings.needsComposite()` is true for bloom, sharpening, a non-zero exposure or Filmic. Otherwise `PresentStage` copies the scene unchanged, so the foundation and the parity check never run the composite.
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
- `terrain/geometry.vsh` and `.fsh` repeat the vanilla `core/terrain` math line by line for target 0, so the scene color stays identical to vanilla (the parity check runs with the geometry buffer when it is on). The extra targets are filled from data vanilla throws away: the lightmap coordinates before they become a color, the vertex color before light, and the face normal from screen derivatives of the camera relative position.
- GLSL only promises the same `gl_Position` in two programs when both declare it `invariant`, and vanilla does not. The geometry pipelines still produced bit-identical depth on NVIDIA (RTX 4050, driver 616.92) in every visual test scene and in game with ticks frozen. If the parity check ever shows depth-only differences on terrain with ticks frozen on another GPU, leave color and depth to the vanilla terrain pass and fill the geometry buffer in a second pass that tests against the scene depth without writing it, with a small depth bias toward the camera (`DepthStencilState` supports it, vanilla uses it for block cracks).
- Renderpearl requires the color attachment count of a render pass to match the pipeline, so opaque terrain gets its own render pass with four attachments. Solid features, the `AFTER_OPAQUE_BLOCKS` event and other mods keep drawing into the usual one-attachment pass afterwards (`SolidFeatureStage`), in the same order as vanilla, after deferred lighting.
- Per layer pipelines go through `ChunkSectionsToRender.renderLayers` (invoker mixin) with the vanilla override parameters, the same mechanism vanilla uses for wireframe and OIT terrain. Wireframe terrain (F3 debug) and missing shaders fall back to the vanilla path.

| Target | Format | Content |
|---|---|---|
| 0 | scene RGBA8 | vanilla terrain color with fog |
| 1 `helion:geometry_normal` | RGB10A2_UNORM | world space face normal as `n * 0.5 + 0.5`, alpha 1 where terrain was drawn |
| 2 `helion:geometry_light` | RGBA8_UNORM | R block light, G sky light (lightmap coordinate / 240), B chunk fade-in visibility, alpha 1 where terrain was drawn |
| 3 `helion:geometry_albedo` | RGBA8_UNORM | texture color times vertex color (biome tint and vanilla shading), before light and fog; alpha is the vanilla fog amount of the pixel |

Translucent terrain, entities, particles and the sky are not in the geometry buffer yet; their pixels keep alpha 0.

Renderpearl in 26.3 has no compute shaders (`ShaderType` only has vertex and fragment), so every geometry buffer consumer is a fragment pass.

## Deferred Lighting

Helion lights opaque terrain itself from the geometry buffer, in high dynamic range. Off by default (`lighting.enabled`) and only active while the geometry buffer is written. It runs between the terrain pass and the solid features, so entities are never lit twice, and before ambient occlusion and transparent geometry, which work on its result exactly as on vanilla terrain.

| Pass | Reads | Writes | Shader |
|---|---|---|---|
| `deferred_lighting` | geometry light | `helion:light_buffer` RGBA16_FLOAT | `deferred_light.fsh`: ambient, sky and block light computed separately from the vanilla light map formula, each with its own intensity, without the vanilla clamp, in linear space; alpha is the daylight reference, 0 where there is no terrain |
| `deferred_shading` | light buffer, albedo, geometry light | scene color (color channels only) | `deferred_shading.fsh`: albedo times the clamped light gives the vanilla color, which is expanded with the inverse Neutral shoulder and multiplied by how far the light exceeds the reference, then encoded with the Neutral shoulder, faded in and fogged like vanilla |

- **Vanilla first.** `helion_lighting.glsl` repeats `core/lightmap.fsh` term by term (ambient or night vision, sky color times sky brightness, block tint times block brightness, boss fog darkening, darkness effect, brightness option) and interpolates between whole light levels like the bilinear light map lookup. Where the light stays at or below the vanilla cap of 1, the result is the vanilla pixel; the visual test `lighting` variant differs from `vanilla` only on light blocks, their surroundings at night, moving clouds and particles.
- **HDR.** Vanilla cuts light at 1, so a block lit by a torch at level 15 (block factor 1.4) looks the same as one in full daylight. Helion keeps the excess: `radiance = inverseNeutral(vanilla color) × max(light / reference, 1)`. The scene stays RGBA8 and display referred, encoded with the same Neutral shoulder the composite inverts (see Image Pipeline), so bloom and exposure see the real brightness.
- **Daylight reference.** Without eye adaptation, torches would double the brightness of the ground at noon. The reference is the light without block light times `HELION_SKY_ADAPTATION` (8), at least 1: in daylight block light hardly adds anything, at night and in caves it adds fully. Histogram exposure will replace this fixed adaptation.
- **Intensities.** Block light intensity scales the block term (1.0 = vanilla levels, without the cap). Sky light intensity scales the sky term; above 1.0 it brightens shade, dusk and night, full daylight keeps the vanilla brightness because of the reference.
- **Physical sky light.** When the physical sky published a `SkyLight`, the light pass uses the `HELION_PHYSICAL_SKY_LIGHT` variant, which also reads the geometry buffer normals and the 3 x 1 sky light target. Per pixel it adds sun light times `max(n · sun, 0)`, moon light times `max(n · moon, 0)` and the sky dome irradiance, and uses that sum as the color of the sky term, scaled to the luminance of the vanilla sky light color. The brightness stays vanilla (and the daylight reference uses the vanilla color), only the color is physical: warm at dawn on faces toward the sun, blue in shade. The sun and moon directions are in the `HelionLighting` block, zero without the physical sky.
- **Light-only view** (`lighting.lightOnlyView`) shades with white albedo, for checking the light alone.
- The geometry pass stores what the shading needs per pixel (fog amount, chunk fade-in), so the lighting passes never reconstruct positions from depth.

## Temporal Anti-Aliasing

Off by default (`temporalAntiAliasing.enabled`). Every frame the level is drawn with the camera moved by less than a pixel, and each frame is blended with the history of the previous ones, so edges and fine textures are averaged over many sample positions.

1. **Projection.** `GameRendererMixin` hands the level projection that `GameRenderer.renderLevel` uploads (with view bobbing and nausea distortion) to `TemporalResources`. In a Helion frame with temporal anti-aliasing, `VanillaFrameDriver` uploads the same matrix with the jitter of `JitterSequence` (Halton 2, 3, eight offsets) added in device coordinates, and restores vanilla's projection after the frame. Vanilla frames, parity frames and the hand are never jittered.
2. **Motion vectors** come from depth and the camera: `PreviousView.reprojection` builds one matrix from current device coordinates and depth to the previous frame (current jittered view projection inverted, camera movement, previous unjittered view projection, current jitter). Entities have no motion vectors of their own yet; their history is limited by the clipping below, which leaves a faint trail behind fast moving entities.
3. **`taa_resolve`** reads the scene color and depth and the previous history. It takes the motion of the closest depth in the 3x3 neighborhood, so edges reproject with the foreground, samples the history with a five-tap Catmull-Rom filter, clips it to the variance box (mean ± one standard deviation in YCoCg) of the current neighborhood, and blends with weight 0.1 for the current frame, both sides weighted by 1 / (1 + luma) against flicker. History outside the screen or after a reset is replaced by the current frame.
4. **`taa_apply`** copies the result into the scene, so ambient occlusion has already been applied and bloom, sharpening and the composite work on the anti-aliased image. Sharpening is the intended partner against the slight softness.
5. **History** lives in two persistent RGBA16_FLOAT targets (`TemporalHistory`) used in turn and imported into the frame graph. It is dropped after a resize, a camera jump of 16 blocks or more, a frame without temporal anti-aliasing and every vanilla frame.

The techniques follow Karis, "High Quality Temporal Supersampling" (SIGGRAPH 2014), Salvi's variance clipping (GDC 2016) and Jimenez's five-tap Catmull-Rom history filter (Filmic SMAA, SIGGRAPH 2016), implemented from the publications.

## Physical Sky

Off by default (`physicalSky.enabled`). In the Overworld skybox without a custom sky renderer, with the camera in air (not in water, lava or powder snow), the flat vanilla sky color, the sunrise glow and the vanilla fog color are replaced by a sky computed from how sunlight and moonlight scatter in an Earth-like atmosphere. The vanilla sun, moon phases and stars stay and are drawn on top. Everywhere else `SkyStage` draws the vanilla sky.

| Pass | Writes | Shader |
|---|---|---|
| `sky_transmittance` | persistent 256 x 64, four RGBA16_FLOAT targets, written once | `transmittance.fsh`: spectral transmittance to the top of the atmosphere per height and view angle |
| `sky_multiple_scattering` | persistent 32 x 32, four RGBA16_FLOAT targets, written once | `multiple_scattering.fsh`: spectral second order scattering over 64 directions with ground albedo, extended to infinite orders with the geometric series |
| `sky_view_sun`, `sky_view_moon` | frame 192 x 108, two RGBA16_FLOAT targets each | `sky_view.fsh`: spectral scattering per view direction relative to the light, finer near the horizon and the light, converted to linear sRGB; target 0 holds Rayleigh single scattering with its phase plus multiple scattering, target 1 Mie single scattering without its phase |
| `sky_horizon` | frame 1 x 1 RGBA8 | `horizon.fsh`: average sky color of 32 horizontal directions, read back to the CPU for the cloud fog |
| `sky_light` | frame 3 x 1 RGBA16_FLOAT | `sky_light.fsh`: sun light and moon light at the camera height after the atmosphere, and the irradiance of the sky dome on an upward surface, for deferred lighting |
| `sky` | scene color | `sky.fsh`: both tables of a light, the Mie part times the Mie phase of the exact angle between the pixel and the light; sun sky times the sun brightness plus moon sky times the moon brightness, rain dims and greys it, Neutral encoding; afterwards `renderCelestials` draws the vanilla sun, moon, stars and dark disc |
| fog, drawn inside `transparent_geometry` | scene color, blended | `fog.fsh`: vanilla fog distances, physical sky color in the direction of each pixel |

- **Method.** Follows Hillaire, "A Scalable and Production Ready Sky and Atmosphere Rendering Technique" (EGSR 2020), with the transmittance parameterization of Bruneton and Neyret (2008) and Earth parameters (Rayleigh, Mie with g = 0.8, ozone); own implementation from the publications. 1 block is 1 m; the camera height above sea level moves the viewer up in the atmosphere. Sun and moon directions come from the vanilla sun and moon angles exactly as vanilla rotates the celestial bodies, so the vanilla sun sits in the middle of the scattering glow.
- **Spectral color.** Light is computed at 16 wavelengths from 400 to 700 nm (20 nm apart), packed as four RGBA groups, so the lookup tables are written with four color targets in one pass. The sky view passes turn the spectrum into linear sRGB with weights from the CIE 1931 color matching functions and the XYZ to sRGB matrix, white balanced to the solar spectrum, so direct sunlight is white and sunsets get their orange from the atmosphere alone. Rayleigh and ozone coefficients per wavelength, the solar spectrum and the CIE tables are in `AtmosphereSpectrum` (data from Bruneton's Precomputed Atmospheric Scattering, BSD-3, see `THIRD_PARTY_NOTICES.md`); `AtmosphereSpectrumTest` checks them against Hillaire's RGB coefficients within 1 %. The per-wavelength values reach the shaders in the constant `HelionSpectrum` buffer.
- **Brightness.** `PhysicalSkyResources.SUN_ILLUMINANCE` (16) puts the noon zenith at the vanilla brightness after Neutral encoding; `MOON_ILLUMINANCE` (0.08) gives a dark blue moonlit night close to vanilla's darkness. The sky is HDR around the sun, so bloom picks the glow up. Directions below the horizon use the horizon color.
- **Fog.** Vanilla mixes the sunrise color into its fog color when the camera looks toward the sun, so a physical sky blended with vanilla fog changed color as the head turned. Under the physical sky `useFog` replaces the frame's `Fog` buffer (shared through `SceneFog`) with one that has no distance fog, so terrain, entities and translucents are drawn unfogged. The fog draw then fogs everything with depth with the vanilla environmental and render distance fog, but in the physical sky color of the pixel's direction, so fog and sky always meet without a seam. In the sorted path it runs after translucent terrain, entities and particles and before clouds, weather and the world border; in the order independent path after the OIT composite. Sky pixels (depth 0) are left alone.
- **Cloud fog.** Clouds fade with the vanilla cloud fog distance toward the color in the `Fog` buffer. That color is the average horizon color from `sky_horizon`, read back without stalling (`HorizonColorReadback`, three buffers, a few frames old); until the first readback arrives it is the vanilla fog color.
- **Glow around the sun and moon.** Mie scattering with g = 0.8 peaks about fifty times above its average within a few degrees of the light. Baked into the 192 x 108 table, the bilinear lookup moved that peak to the nearest table row (about 0.75° apart near the horizon), and the moon glow sat visibly beside the vanilla moon. The table therefore stores Mie scattering without its phase, and `helion_sky_color.glsl` multiplies it with the phase of the exact per-pixel angle, so the glow is always centered on the vanilla sun and moon.
- **View direction.** The sky and the fog take the direction of a pixel from two points on its ray (device depth 1.0 and 0.01) unprojected with the inverse level view projection. The level projection contains the view bobbing of `GameRenderer.bobView`, which also moves the eye by up to a tenth of a block; a single point unprojected close to the near plane would turn that shift into a large change of direction, and the sky would sway against the world while walking.
- **Sky light.** `sky_light` is published as `SkyLight` for deferred lighting, see below.
- **Cost.** The two constant tables are written once into persistent targets. Each frame costs about 0.8 to 1.6 ms on the development GPU (RTX 4050): `sky_view_sun` and `sky_view_moon` 0.2 to 0.4 ms each, `sky_light` 0.2 to 1.1 ms. `sky_light` integrates the sky dome over 128 directions in only three fragments, so its time is mostly latency and varies a lot; spreading that work over more fragments is an open optimization.
- Aerial perspective (scattering in the air between the camera and terrain) is not part of it yet; the fog pass stands in for it at the vanilla distances.

## Shadows

Off by default (`shadows.enabled`). Cascaded shadow maps from the sun, or from the moon while the sun is below the horizon, applied to the physical sky light of deferred lighting. `ShadowStage` runs after the terrain pass, so it can read scene depth and the geometry buffer normals, and before deferred lighting, which reads its mask. It needs the geometry buffer, deferred lighting and a `SkyLight` from the physical sky, because only the physical sky light separates the sun and moon terms from the sky dome.

| Pass | Writes | Shader |
|---|---|---|
| `shadow_map` | persistent D32_FLOAT atlas, four cascades side by side (`ShadowQuality`: 1024, 1536 or 2048 texels each) | `shadow/caster.vsh`, `caster.fsh`: solid and cutout terrain from the light, depth only, slope and constant depth bias |
| `shadow_mask` | frame R8_UNORM `helion:shadow_mask` | `shadow/mask.fsh`: per terrain pixel the first cascade that holds it, 4 x 4 tent filtered lookup, 1 lit and 0 shadowed |

- **Cascades.** `ShadowCascades` splits the view up to the shadow distance (render distance, at most 160 blocks) at 8, 22, 50 and 100 %. Each slice gets a bounding sphere, so its size does not change when the camera turns, and its orthographic projection is snapped to whole texels of the shadow map in world space, so edges do not crawl when the camera moves. The depth range extends 256 blocks toward the light, so mountains outside the view still cast into it.
- **Casters.** `VanillaShadowCasterSource` collects every built chunk section within the render distance that lies inside or toward the light of a cascade, puts them temporarily into `LevelRenderer.visibleSections` and lets vanilla prepare the draws with the light view, once per cascade, then restores the camera's sections. It must use the same draw path as the camera terrain: vanilla keeps the chunk section data of the direct and the multi-draw indirect path in one storage and closes it when the path changes, which would invalidate the camera's draws. The caster pipelines are built from the vanilla terrain snippets without any color target: a render pass whose first color attachment is unused crashes in `FrontendCommandEncoder.createRenderPass`. Translucent layers, entities and particles do not cast shadows yet.
- **Mask.** The mask pass reconstructs the world position from scene depth, offsets it 1.5 texels along the normal and 1 texel toward the light against acne, and fades to lit over the last 15 % of the shadow distance. Faces turned away from the light stay lit in the mask because the light term is already zero there.
- **Lighting.** The `HELION_SHADOWS` variant of `deferred_light.fsh` multiplies the sun term by `1 - SunShadowStrength × shadow` and the moon term likewise before normalizing the sky light color, so shade keeps the sky dome light. `ShadowLight` fades the strength to zero between 0.12 and 0.02 elevation of the light, so shadows never pop at sunrise, sunset or moonrise. The brightness stays the vanilla sky light brightness; the shadow only changes how much of it comes from the direct light.
- **Cost.** About 2.9 ms for `shadow_map` and 0.02 ms for `shadow_mask` at medium quality and render distance 20 on the development GPU (RTX 4050). Drawing the casters is the main cost; culling sections per cascade more tightly and caching far cascades are open optimizations.

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
| `SkyRendererMixin`, `CelestialOnlySky`, `VanillaAtmosphereSource.renderCelestials` | `SkyRenderer.render`, `renderSkyDisc`, `renderSunriseAndSunset`, `renderSunMoonAndStars`: the physical sky relies on the sky disc and sunrise glow being separate methods of `render` |
| `PhysicalSkyResources.writeFog`, `SceneFog`, `fog.fsh` | the `Fog` uniform block layout written by `FogRenderer.updateBuffer` (`FOG_UBO_SIZE`: color, environmental start and end, render distance start and end, sky end, cloud end) and `total_fog_value` in `fog.glsl` |
| `TransparentGeometryStage` (fog after OIT) | `CloudRenderer.renderOit` and the OIT setup in `LevelRenderer`: OIT clouds write their depth into the separate `cloud_depth` target, never into scene depth, so cloud pixels read as sky in the fog pass and keep their own cloud fog |
| `VanillaTerrainSource.renderOpaqueGeometry` | `ChunkSectionsToRender.renderGroup` and `renderLayers` |
| `VanillaShadowCasterSource` | `LevelRenderer.prepareChunkRenders`, `prepareChunkRendersIndirect` and `extractSectionDrawGroups` (which read `visibleSections`), and `DynamicGpuData.writeChunkSections` and `writeChunkSectionsInstanced`, which close each other's storage |
| `VanillaShadowPipelines`, `shadow/caster.vsh` | `RenderPipelines.TERRAIN_SNIPPET`, `MULTIDRAW_TERRAIN_SNIPPET`, the `ALPHA_CUTOUT` define of `CUTOUT_TERRAIN`, and the vertex inputs of `core/terrain.vsh` |

`SkyRenderer` keeps the render target it was created with, so Helion owns a separate `SkyRenderer` bound to the scene target (`SceneSkyRenderer`).

## Mixins

| Mixin | Target | Why no API is enough |
|---|---|---|
| `LevelRendererMixin.helion$render` | `LevelRenderer.render` | NeoForge can add passes but cannot replace the vanilla pass structure |
| `LevelRendererMixin.helion$redirectMainTarget` | `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` | Vanilla OIT and depth bounds code reads the main target directly instead of the frame graph handle |
| `GameRendererMixin.helion$captureLevelProjection` | `ProjectionMatrixBuffer.getBuffer` inside `GameRenderer.renderLevel` | The final level projection includes view bobbing and nausea distortion and is built inside `renderLevel`; no event exposes it, and temporal anti-aliasing must jitter and reproject exactly this matrix |
| `SkyRendererMixin.helion$skipUnderPhysicalSky` | `SkyRenderer.renderSkyDisc`, `renderSunriseAndSunset` | `SkyRenderer.render` draws the sky color and the celestial bodies in one call; under the physical sky only the sun, moon and stars must stay |
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
- Automated visual test: `launcher/launch.ps1 -World "Helion Visual Test" -Define helion.visualTest=true` loads a copy of a save, clears the area around the player, builds a row of light blocks in front of it, switches to spectator, freezes time and weather, and photographs nine scenes (noon sky, sunrise sun, horizon, light blocks by day, sunset, night sky, light blocks by night, and dawn looking toward and away from the sun) in eleven variants each (vanilla, None, Neutral, Filmic, bloom only, lighting, light only, temporal, physical sky, shadows, shadow light) into `screenshots/helion_<scene>_<variant>.png` at half resolution, then runs the parity check in every scene with ticks frozen and logs whether all pixels match vanilla (a failure is a `WARN` line). Every shot also writes a `Helion visual test` log line with the full debug snapshot as JSON, and the game closes cleanly afterwards. Run it on a copy of a world, never on a real save, because it changes blocks, time and game rules. Development runs only.

## Parity Scenes

Run the parity check (`J` in debug mode) in each scene before committing a change to the render core:

- Overworld at day and at night, during rain, under water
- Nether and End
- Improved transparency on and off (F3 + X)
- A glowing entity (entity outline), spectating a creeper (spectator post effect)
- F1 hidden GUI and third person camera
