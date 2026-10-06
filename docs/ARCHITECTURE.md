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
   │  FrameStages: clear → sky → opaque geometry → ambient occlusion → transparent geometry → post (bloom, sharpen) → present or image composite → geometry buffer view
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
| 2 | sky | `render.atmosphere.SkyStage` | Draws the sky through `AtmosphereSource` |
| 3 | opaque_geometry | `render.geometry.OpaqueGeometryStage` | Prepares fog, chunk sampler, translucent buffers and lighting, then draws opaque terrain and solid features. With the geometry buffer on, opaque terrain is drawn first in its own render pass through Helion's terrain shaders, see Geometry Buffer below |
| 4 | ambient_occlusion | `render.lighting.AmbientOcclusionStage` | Screen space ambient occlusion on the opaque scene, see below. Skipped when disabled or when its shaders failed to compile |
| 5 | transparent_geometry | `render.geometry.TransparentGeometryStage` | Sorted or order independent transparency, clouds, weather, world border, then outline, see-through and always-on-top features |
| 6 | post | `render.post.PostProcessingStage` | Runs post effects in order: the vanilla entity outline effect, then `render.post.BloomStage`, then `render.post.SharpeningStage`, see Image Pipeline below |
| 7 | present | `render.post.PresentStage` | Copies the scene target into the main target when no image effect is active |
| 7 | composite | `render.post.ImageCompositeStage` | Replaces present when bloom, sharpening, exposure or Filmic tone mapping is active: tone maps the scene (or its sharpened copy) with bloom into the main target and copies depth |
| 8 | geometry_view | `render.geometry.GeometryBufferDebugStage` | Only with a geometry buffer view selected: draws normals, block light, sky light or albedo over the main target |

Planned stages and where they go: shadows next to ambient occlusion in `render.lighting`, atmosphere effects after transparent geometry, further Helion post effects inside `PostProcessingStage` after sharpening.

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

The visibility bitmask method follows Therrien, Levesque and Gilet, "Screen Space Indirect Lighting with Visibility Bitmask" (2023), as an own implementation inside the XeGTAO slice loop: every sample marks the sectors between its front angle and the angle of a point `OCCLUDER_THICKNESS` (0.75 blocks) behind it, sectors are spaced by the sine of the angle to the projected normal so their count is cosine weighted, and samples beyond the effect radius mark nothing. Unlike GTAO it lets light pass behind thin occluders (fences, grass, torches, bars), which removes their dark halos. The noise never changes between frames because Helion has no temporal accumulation; animated noise would shimmer.

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

Foundation for Helion's own lighting. Off by default (`geometryBuffer.enabled`). Nothing reads it yet except its debug view; deferred lighting, sky light and shadows will.

- `VanillaGeometryPipelines` copies the vanilla `SOLID_TERRAIN`, `CUTOUT_TERRAIN` and their multi-draw pipelines with `toBuilder()`, so every define (`ALPHA_CUTOUT`), bind group, vertex format and depth state stays vanilla, and replaces the shaders with `helion:terrain/geometry` plus three more color targets.
- `terrain/geometry.vsh` and `.fsh` repeat the vanilla `core/terrain` math line by line for target 0, so the scene color stays identical to vanilla (the parity check runs with the geometry buffer when it is on). The extra targets are filled from data vanilla throws away: the lightmap coordinates before they become a color, the vertex color before light, and the face normal from screen derivatives of the camera relative position.
- Renderpearl requires the color attachment count of a render pass to match the pipeline, so opaque terrain gets its own render pass with four attachments. Solid features, the `AFTER_OPAQUE_BLOCKS` event and other mods keep drawing into the usual one-attachment pass afterwards, in the same order as vanilla.
- Per layer pipelines go through `ChunkSectionsToRender.renderLayers` (invoker mixin) with the vanilla override parameters, the same mechanism vanilla uses for wireframe and OIT terrain. Wireframe terrain (F3 debug) and missing shaders fall back to the vanilla path.

| Target | Format | Content |
|---|---|---|
| 0 | scene RGBA8 | vanilla terrain color with fog |
| 1 `helion:geometry_normal` | RGB10A2_UNORM | world space face normal as `n * 0.5 + 0.5`, alpha 1 where terrain was drawn |
| 2 `helion:geometry_light` | RGBA8_UNORM | R block light, G sky light (lightmap coordinate / 240), alpha 1 where terrain was drawn |
| 3 `helion:geometry_albedo` | RGBA8_UNORM | texture color times vertex color (biome tint and vanilla shading), before light and fog |

Translucent terrain, entities, particles and the sky are not in the geometry buffer yet; their pixels keep alpha 0.

Renderpearl in 26.3 has no compute shaders (`ShaderType` only has vertex and fragment), so every geometry buffer consumer is a fragment pass.

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
| `VanillaAtmosphereSource.hasSky`, `renderSky` | `LevelRenderer.addSkyPass` |
| `ClearStage` | the `clear` pass in `LevelRenderer.render` |
| `LevelRendererAccessor` | private fields and methods of `LevelRenderer` |
| `terrain/geometry.vsh`, `terrain/geometry.fsh` | `core/terrain.vsh`, `core/terrain.fsh` (non-OIT path) |
| `VanillaGeometryPipelines` | `RenderPipelines.SOLID_TERRAIN`, `CUTOUT_TERRAIN` and their multi-draw variants |
| `VanillaTerrainSource.renderOpaqueGeometry` | `ChunkSectionsToRender.renderGroup` and `renderLayers` |

`SkyRenderer` keeps the render target it was created with, so Helion owns a separate `SkyRenderer` bound to the scene target (`SceneSkyRenderer`).

## Mixins

| Mixin | Target | Why no API is enough |
|---|---|---|
| `LevelRendererMixin.helion$render` | `LevelRenderer.render` | NeoForge can add passes but cannot replace the vanilla pass structure |
| `LevelRendererMixin.helion$redirectMainTarget` | `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` | Vanilla OIT and depth bounds code reads the main target directly instead of the frame graph handle |
| `LevelRendererAccessor` | private members of `LevelRenderer` | Required to rebuild `render` with vanilla behavior |
| `ChunkSectionsToRenderAccessor.helion$renderLayers` | private `ChunkSectionsToRender.renderLayers` | Only this method takes per layer pipeline overrides; the public `renderGroup` applies one override to every layer, and solid and cutout terrain need different pipelines |

## Porting Checklist for a New Minecraft Version

1. Diff `LevelRenderer.render`, `addSkyPass`, `addMainPass`, `executeSolid`, `executeClassicTransparency` against the previous version.
2. Update the classes in the Vanilla Coupling table.
3. Build, start the game with `--graphicsBackend vulkan` and turn on debug mode and run the parity check (`J`) in every scene of the Parity Scenes list below.
4. Record anything surprising as a lesson in the workspace `docs/RULES.md`.

## Testing Tools

- Debug mode (Mods → Helion → Config → Debug Mode, off by default): shows every value of `HelionDebugSnapshot` on screen without F3 and writes it as one JSON line per second to `logs/helion-debug.jsonl`, together with `toggle`, `passive` and `parity` events. GPU time per stage is only measured in debug mode (up to 32 timed passes per frame, including every `bloom_*` pass and `composite`). While it is off the debug entry, the JSON log and the timestamp queries do no work at all.
- Parity check, `J` key in debug mode: after warm-up frames on each side, captures one vanilla frame and one Helion foundation frame (all effects off), reads both main targets back from the GPU and reports differing pixels in chat and in the JSON log. It aborts with a message when a Helion frame could not be rendered by Helion. Use `/tick freeze` and keep the camera still.
- `H` key: switches between Helion and vanilla rendering at runtime.
- Automated visual test: `launcher/launch.ps1 -World "Helion Visual Test" -Define helion.visualTest=true` loads a copy of a save, builds a row of light blocks in front of the player, switches to spectator, freezes time and weather, and photographs seven scenes (noon sky, sunrise sun, horizon, light blocks by day, sunset, night sky, light blocks by night) in five variants each (vanilla, None, Neutral, Filmic, bloom only) into `screenshots/helion_<scene>_<variant>.png` at half resolution, then runs the parity check in every scene with ticks frozen and logs whether all pixels match vanilla (a failure is a `WARN` line). Every shot also writes a `Helion visual test` log line with the full debug snapshot as JSON, and the game closes cleanly afterwards. Run it on a copy of a world, never on a real save, because it changes blocks, time and game rules. Development runs only.

## Parity Scenes

Run the parity check (`J` in debug mode) in each scene before committing a change to the render core:

- Overworld at day and at night, during rain, under water
- Nether and End
- Improved transparency on and off (F3 + X)
- A glowing entity (entity outline), spectating a creeper (spectator post effect)
- F1 hidden GUI and third person camera
