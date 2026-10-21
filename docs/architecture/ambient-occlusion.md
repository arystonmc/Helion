# Helion Architecture: Ambient Occlusion

Part of the [Helion architecture](../ARCHITECTURE.md).

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

Fixed tuning values live in `AmbientOcclusionResources`; per-quality slice, step and denoise counts in `AmbientOcclusionQuality`. The horizon search is compiled once per quality and method (`HELION_AO_SLICE_COUNT`, `HELION_AO_STEPS_PER_SLICE`, `HELION_AO_VISIBILITY_BITMASK`), so its loops have fixed bounds the compiler unrolls and the method switch costs nothing; with the counts read from the uniform buffer instead, `ao_main` took 0.80 ms (Medium) and 1.07 ms (High) at 1080p on the development GPU, with fixed bounds 0.61 and 0.87 ms for the same image.

The visibility bitmask method follows Therrien, Levesque and Gilet, "Screen Space Indirect Lighting with Visibility Bitmask" (2023), as an own implementation inside the XeGTAO slice loop: every sample marks the sectors between its front angle and the angle of a point `OCCLUDER_THICKNESS` (0.75 blocks) behind it, sectors are spaced by the sine of the angle to the projected normal so their count is cosine weighted, and samples beyond the effect radius mark nothing. Unlike GTAO it lets light pass behind thin occluders (fences, grass, torches, bars), which removes their dark halos. Without temporal anti-aliasing the noise never changes between frames, because animated noise would shimmer. While temporal anti-aliasing runs, `AmbientOcclusionResources` advances `NoiseIndex` every frame through 64 frames and the shader offsets the Hilbert index by 288 per frame (the spatio-temporal R2 noise of XeGTAO), so the history averages different slice directions and step offsets into smoother occlusion at the same cost.
