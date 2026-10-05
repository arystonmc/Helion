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
   │  FrameStages: clear → sky → opaque geometry → ambient occlusion → transparent geometry → post → present
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
4. `PresentStage` copies scene color and depth into the real main target. Hand rendering, vanilla post effects and the GUI continue unchanged afterwards.

## Stages

| Order | Stage | Class | What it does |
|---|---|---|---|
| 1 | clear | `render.scene.ClearStage` | Clears scene color to the fog color and scene depth to the reversed-Z far value |
| 2 | sky | `render.atmosphere.SkyStage` | Draws the sky through `AtmosphereSource` |
| 3 | opaque_geometry | `render.geometry.OpaqueGeometryStage` | Prepares fog, chunk sampler, translucent buffers and lighting, then draws opaque terrain and solid features |
| 4 | ambient_occlusion | `render.lighting.AmbientOcclusionStage` | Screen space ambient occlusion on the opaque scene, see below. Skipped when disabled or when its shaders failed to compile |
| 5 | transparent_geometry | `render.geometry.TransparentGeometryStage` | Sorted or order independent transparency, clouds, weather, world border, then outline, see-through and always-on-top features |
| 6 | post | `render.post.PostProcessingStage` | Runs post effects in order; the vanilla entity outline effect is the first one |
| 7 | present | `render.post.PresentStage` | Copies the scene target into the main target |

Planned stages and where they go: shadows next to ambient occlusion in `render.lighting`, atmosphere effects after transparent geometry, Helion post effects before present.

## Render Settings

`RenderSettings` is an immutable snapshot of every effect setting, taken from the client config and carried in `FrameContext`. Stages decide in `isActive` whether they run. `RenderSettings.foundation()` turns every effect off; the parity check renders its Helion frame with it, so the foundation can always be compared with vanilla while effects are on.

## Shaders and Pipelines

- Shaders live in `src/main/resources/assets/helion/shaders/<feature>/*.fsh`, shared code in `shaders/include/helion_*.glsl`, included as `#include <helion:helion_view.glsl>`.
- GLSL rules of 26.3: `#version 330`, `GL_ARB_separate_shader_objects`, explicit `layout(location)`, no `layout(binding)`, uniforms bound by name, `gl_VertexIndex`.
- Fullscreen passes use the vanilla `minecraft:core/screenquad` vertex shader and `FullscreenPass.draw` (three vertices, no vertex buffer).
- Every pipeline is listed in `HelionPipelines.all()` and registered as optional through `RegisterRenderPipelinesEvent`. Stages look them up with `HelionPipelines.compiled`; a missing pipeline makes the stage skip itself instead of crashing.
- Never sample a depth or color texture that is attached to the same render pass.

## Ambient Occlusion

GTAO style horizon based ambient occlusion, ported from Intel XeGTAO (MIT, see `THIRD_PARTY_NOTICES.md`) to fragment shaders. It runs after opaque geometry and before transparent geometry, so water, glass, particles and the hand never receive it.

| Pass | Reads | Writes | Shader |
|---|---|---|---|
| `ao_depth` | scene depth | `helion:ao_view_depth` R32_FLOAT | `view_depth.fsh`: reversed-Z device depth to linear view depth, sky marked with `HELION_SKY_VIEW_DEPTH` |
| `ao_main` | view depth | `helion:ao_raw` RG8_UNORM (occlusion, packed edges) | `gtao.fsh`: edge aware normals from depth, slice and step horizon search with fixed Hilbert R2 noise |
| `ao_denoise_N` | occlusion, edges | `helion:ao_denoised_N` R8_UNORM | `denoise.fsh`: XeGTAO edge aware 3x3 blur |
| last `ao_denoise_N` | occlusion, edges, view depth, scene color, Fog | R8_UNORM | `denoise_resolve.fsh`: final blur plus multi-bounce, emissive protection, strength and fog fade |
| `ao_apply` | final occlusion | scene color | `apply.fsh` with multiply blend, or the same shader without blend for the debug view |

Fixed tuning values live in `AmbientOcclusionResources`; per-quality slice, step and denoise counts in `AmbientOcclusionQuality`. The noise never changes between frames because Helion has no temporal accumulation; animated noise would shimmer.

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

`SkyRenderer` keeps the render target it was created with, so Helion owns a separate `SkyRenderer` bound to the scene target (`SceneSkyRenderer`).

## Mixins

| Mixin | Target | Why no API is enough |
|---|---|---|
| `LevelRendererMixin.helion$render` | `LevelRenderer.render` | NeoForge can add passes but cannot replace the vanilla pass structure |
| `LevelRendererMixin.helion$redirectMainTarget` | `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` | Vanilla OIT and depth bounds code reads the main target directly instead of the frame graph handle |
| `LevelRendererAccessor` | private members of `LevelRenderer` | Required to rebuild `render` with vanilla behavior |

## Porting Checklist for a New Minecraft Version

1. Diff `LevelRenderer.render`, `addSkyPass`, `addMainPass`, `executeSolid`, `executeClassicTransparency` against the previous version.
2. Update the classes in the Vanilla Coupling table.
3. Build, start the game with `--graphicsBackend vulkan` and run `/helion parity` in every scene of the Parity Scenes list below.
4. Record anything surprising as a lesson in the workspace `docs/RULES.md`.

## Testing Tools

- `/helion parity`: after warm-up frames on each side, captures one vanilla frame and one Helion foundation frame (all effects off), reads both main targets back from the GPU and reports differing pixels. Use `/tick freeze` and keep the camera still.
- `/helion toggle` or the `H` key: switches between Helion and vanilla rendering at runtime.
- `/helion status`: shows whether the core is active, disabled or passive and why.
- F3 debug screen: backend, GPU, smoothed GPU time per stage, tracked GPU memory and Helion frustum check against vanilla visible sections.

## Parity Scenes

Run `/helion parity` in each scene before committing a change to the render core:

- Overworld at day and at night, during rain, under water
- Nether and End
- Improved transparency on and off (F3 + X)
- A glowing entity (entity outline), spectating a creeper (spectator post effect)
- F1 hidden GUI and third person camera
