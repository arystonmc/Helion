# Helion Architecture: Path Tracing

Part of the [Helion architecture](../ARCHITECTURE.md).

## Goal

Helion's light and shadow, rebuilt from scratch as RTX path tracing (decision of 2026-10-27, see Ray Tracing, Light and Shadows). Ideas come from the Radiance mod; no code is taken from it (rule T1). Quality first: about 30 to 40 FPS on an RTX 4050 Laptop at 1080p, full resolution, 3 bounces by default. Cards without hardware ray queries keep the vanilla light; every other Helion effect works there. The plan with all phases is `plans/helion-path-tracing-plan.md` in the workspace.

## Setting

`rayTracing.pathTracing` (Lighting page, Ray Tracing section, BETA, on in the Experimental preset). It needs `rayTracing.enabled`, which chooses the device features at startup, and the geometry buffer; without either the stage stays off and terrain keeps the vanilla light. Quality levels and light strength sliders come with the phases that use them.

## Frame

1. The geometry pass rasterizes the primary surfaces as before: depth, face normal, light levels with the block material, and albedo with the fog amount (see Geometry Buffer). Primary rays stay rasterized; they are cheaper and already exist.
2. `RtxSceneStage` builds the section and entity acceleration structures.
3. `RtxPathTracingStage`, at the frame position where deferred shading ran before the removal:
   - pass `path_trace` dispatches `rtx/path_trace.csh` into a full resolution RGBA16F work image (`RtxDenoiseImages`), the demodulated linear HDR irradiance of every geometry buffer pixel (see Direct Light and Denoiser);
   - pass `path_denoise` accumulates it over frames and filters it (see Denoiser);
   - pass `path_traced_light` multiplies the result by the unshaded linear albedo and encodes it into the RGBA8 scene with the Neutral curve (the same encoding the image composite expands with `helionExpandScene`), then applies the chunk fade (light target blue) and the fog (albedo alpha) as the geometry pass did, only where geometry was written.
4. Everything after it (solid features, water, reflections, light shafts, transparency, post effects) is unchanged.

## Surface Data of Hits

Each section vertex in the surface buffer has four ints: texture coordinate, vertex color, light coordinates and the octahedral face normal of its quad (see `RtxSectionUpdate`). Hit shading reads the exact normal instead of guessing it. The material class of a hit will be read from the glossy block mask at its texture coordinate, so the capture needs no atlas on the compile workers.

## Direct Light (Phase 3, 2026-10-27)

The stage needs the physical sky (`SkyLight`: sun, moon and dome irradiance in a 3x1 target) and a scene anchor; without the sky terrain keeps the vanilla light.

- Units (L-175): linear light where open flat ground under the full sky equals the vanilla full sky light linearized. Every frame the factor `scale` maps sun times its height plus moon times its height plus dome onto the daylight full sky light (1.0) minus the vanilla darkest light, which stays as the ambient floor, but never divides by less than 3.0 (an open sky a little after sunrise): by day the light matches vanilla, from dusk on it falls with the physical sky, so a moonlit night is the moon to sun ratio of the sky (about 5 stops under day before eye adaptation) and no longer follows the vanilla night or the Brightness option (2026-10-29). Pixels outside the traced scene keep the vanilla light, scaled on their sky part by the same night ratio (`vanillaShare`), so distant terrain darkens with the near world.
- Albedo: the geometry albedo is divided by the vanilla directional shade of its face (up 1, down 0.5, north and south 0.8, east and west 0.6; cross plants keep theirs) and linearized. The vanilla ambient occlusion in the vertex color stays for now.
- Sun and moon: one shadow ray each (only above the horizon) toward a direction sampled in a disk of about four times the real angular radius, masks 1 and 2 with the cutout test, from an origin offset along the normal by a constant plus a share of the distance (L-173). A pixel with sky level 0 gets no sun or moon, so rays that leave the scene above a deep cave do not light it.
- Block lights: `RtxSectionLights` scans every compiled section for light emitting blocks (box of the shape, `LightColors` color, range and strength from the level); `RtxLightScene` puts the lights of each section and its neighbors into a 9x9x9 cell grid around the anchor (up to 32 per cell). Each candidate is weighed unshadowed with a windowed, softened inverse square falloff 1 / (d² + 1) from a sphere at the center of its box (radius of its smallest half extent; the nearest point of the box made a flat bright patch where a wall torch box touches the wall) and a wrapped Lambert term (0.35) aimed at the face of the light that looks the same way as the surface (L-176). Shadow rays pass through translucent glass and ice (glossy material cells of the translucent geometry) and take on its hue, 160 % of its alpha per face (clamped to the full hue), so stained glass colors sun, moon and block light; bounce rays (`helionTraceStained`) collect up to four glossy translucent faces and apply those nearer than the committed hit, so the open sky seen through a stained window lights the room in its color too (the owner saw no tint in game because only direct sun was filtered); water and other translucent blocks pass light unchanged. The brightest light gets a shadow ray, and one other light is picked in proportion to its weight with weighted reservoir sampling and traced; its estimate adds only a saturating share (L-171). Shadow rays end where they enter the light box, and faces of the light itself count as lit (L-174). Fire lights flicker. Block light fades by day where the sun, moon and sky light the pixel (L-170).
- Emissive texels (glossy mask cell 1, now geometry material `HELION_GEOMETRY_EMISSIVE`, 0.8125) and lava stay full bright. Pixels within 8 blocks of the scene edge blend to the vanilla light.

Deferred: temporal and spatial reservoir reuse (with the reprojection and history of Phase 5), dynamic lights of held and burning things, separate diffuse and specular signals (no consumer before Phase 5), GGX and Burley shading (Phase 6 materials), ambient occlusion only on the ambient term, and sun shadows on emissive faces.

## Indirect Light (Phase 4, 2026-10-27)

One path per pixel at full resolution, up to 3 bounces, in the same pass and units as the direct light; it replaced the sky dome stand-in and the vanilla block light fill of Phase 3.

- Each bounce samples a cosine weighted direction and traces the sections (mask 1) with the cutout test; plant planes start their rays 0.3 blocks out.
- A miss adds the sky dome (0.3 of it for rays that leave downward), gated by the sky level of the surface the ray left, so rays that leave the scene through rock around a deep cave add nothing.
- A hit reads the surface data: the exact face normal, the atlas texel times the vertex tint, divided by the vanilla shade and linearized, and the sky level. The path then adds the hit's direct light: the sun and moon shadow rays (only with sky level above 0), one block light picked in proportion to its unshadowed weight from the grid with one shadow ray, scaled to the same saturating combination as the primary surface, and the vanilla darkest light.
- A hit on an emissive texel (material mask cell 1, read from `GlossyMask`) ends the path without adding light: those texels belong to registered light blocks, whose light the shadow ray sampling already counts.
- Russian roulette from the third bounce keeps the path with the probability of its brightest throughput channel (at least 0.1). Where a path ends early or at the last bounce, its last hit adds the dome times its sky level raised to 2.2 as the tail of the missing bounces.
- The indirect light of a pixel is limited to a luminance of 4 against fireflies.

Entities are not hit by bounce rays yet; they still cast direct shadows. The denoiser of Phase 5 smooths the noise of the single path per pixel.

## Denoiser (Phase 5, 2026-10-27)

An SVGF style filter over the demodulated irradiance, at full resolution, in pass `path_denoise`.

- Demodulation: `path_trace.csh` writes the light before the surface albedo (the unshaded linear albedo of `helion_path_tracing.glsl`), so texture detail never blurs; `pathtracing/radiance.fsh` multiplies it back. The alpha carries a signal: 0 no geometry, 1 lit, 2 emissive. Emissive and lava pixels write 1 and are never filtered; pixels near the scene edge write the vanilla light divided by the albedo (at most 4).
- Accumulation (`rtx/denoise_accumulate.csh`): the pixel position from the scene depth is projected with the previous frame's level view projection, translated by the camera move (`RtxDenoiseImages.reprojection`). The history at that pixel is kept when the previous guide matches: distance within 8 % and normals with a dot product above 0.9. Up to 24 samples blend with weight 1 / count; the luminance moments blend with at least 0.2. The variance comes from the moments, or from the 3x3 neighborhood while a pixel has fewer than 4 samples, and is stored in alpha.
- Guide: normal and view distance per pixel, in a pair that swaps every frame; emissive pixels store a negative distance so filters skip them.
- Filter (`rtx/denoise_filter.csh`): three à-trous iterations of a 5x5 B-spline kernel at steps 1, 2 and 4 (five, up to step 16, until the face light cache smoothed the signal; three cut `path_denoise` from 3.0 to 3.9 ms to 1.9 to 2.4 ms on 2026-10-29 with the same picture). Each tap is weighted by the normal (power 128), the distance (relative to 3 % of the distance times the step) and the luminance, against 4 standard deviations of the filtered variance; the variance is filtered with squared weights. The output of the first iteration is the next frame's history, as in SVGF.
- The work images ping-pong: trace into work 0, accumulate into work 1, then work 1, history, work 0 and finally work 1, which the radiance pass reads.

Deferred: temporal anti-aliasing of the result, motion vectors of moving entities (the history follows the camera only), and a separate specular signal (Phase 6).

## Face Light Cache (2026-10-28)

Always on with the path traced light since 2026-10-28, after a spike on the branch `spike/face-light-cache`; plan and measurements in the workspace `plans/helion-face-light-cache-spike.md`.

- Indirect light is kept per face cell in world space (`RtxLightCache`): the key is the face direction, the face plane (1/16 block steps up to level 1, whole blocks beyond), the cell column and row and a level. Level 0 cells are 1/4 block and the level grows by one per doubling of the view distance beyond 12 blocks, up to 16 block cells.
- Trace: a pixel with an axis aligned normal claims its cell (linear probing over 8 slots, a second hash as the check, entries unused for 600 frames are taken over); the first pixel of the frame in a cell stores its position in the cell and its sky gate and queues the cell. Once the cell has 8 samples the pixel takes the bilinear blend of the four nearest cells on its plane; before that, and for other normals (plants, entities), it traces its own indirect path as before. Direct light stays per pixel.
- Update, before the trace: one thread per cell queued last frame traces the indirect path from the stored position and blends it in with weight samples / count, up to 64. The two queues swap every frame; the update clears the one the trace fills next.
- The push constants carry the anchor section and a frame counter instead of the scene reach (now a shader constant) and the random seed.
- Measured on 2026-10-28 (RTX 4050 Laptop, 1080p, static camera): `path_trace` including the update fell from 2.2 to 5.9 ms to 1.2 to 2.1 ms in the five test scenes, with the same picture; with a moving camera 2.1 against 5.2 ms (day) and 1.5 against 3.5 ms (torch room), and a removed torch loses its light within a second as without the cache; the denoiser (2.8 to 3.9 ms) became the largest cost and was cut to three iterations (see Denoiser).
- Tried and dropped on 2026-10-29: reading the cache at bounce hits (adding the cached indirect light of the hit face and ending the path, the update pass queuing the cells it hits) for infinite bounces. The picture did not change (the three bounces and the sky tail already carry nearly all the light) and the trace got slower: 9.8 instead of 2.2 ms in the cave when every hit refreshed its cell each frame, still 2.0 to 2.9 against 1.3 to 2.2 ms with hit cells of at least one block refreshed every 8 frames. The probing and the four cell reads per hit cost more than the bounces they save.

## Specular and Materials (Phase 6, 2026-10-29)

- Materials: the material mask gained a metal class (0x60, geometry light alpha 0.9375) for iron, gold, netherite and unweathered copper blocks. Glossy blocks (polished stone, quartz, prismarine, glazed terracotta, ice, smooth stone) are a dielectric with F0 0.04 and roughness 0.2; metal blocks use the unshaded linear albedo as F0 with roughness 0.35 and no diffuse light. Every other class has no specular.
- Trace: for glossy and metal pixels `path_trace.csh` writes a second signal, specular radiance (not demodulated). The sun and the moon add an analytic GGX lobe (Smith masking, Schlick Fresnel) toward a point picked on their disk with a shadow ray, scaled by the sky gate. One reflection ray is sampled from the GGX distribution of visible normals and weighted by Fresnel and the masking of the outgoing direction; a hit is shaded like the last indirect bounce (block light, sun and moon with shadow rays, ambient and the sky tail, times the hit albedo), an emissive texel returns its own linear color, and a miss reads the sky radiance map (rows 1 to 8 of the sky light target, written by `atmosphere/sky_light.fsh` with the visible sky radiance, bilinear) times the sky gate. Sampled specular is limited to luminance 8.
- Denoise: `rtx/specular_accumulate.csh` reprojects the specular history with the same camera reprojection and guide test as the irradiance, clamps it to 1.5 standard deviations around the 3x3 neighborhood mean and blends up to 16 samples; without history it starts from the neighborhood mean.
- Composite: the radiance pass adds the accumulated specular to the remodulated diffuse light (diffuse is zero for metal). While the path tracer runs, screen space reflections leave out the polished reflection of glossy blocks; rain wetness stays in screen space reflections.
- Measured 2026-10-29 (RTX 4050 Laptop, 1080p): `path_trace` 1.5 to 2.4 ms in the test scenes, about 0.1 to 0.2 ms more than before; denoising unchanged within noise.

Not yet: wet surfaces in the path tracer, water and glass folded into it (they keep `RtxReflectionStage`), LabPBR materials.

## Exposure and Finish (Phase 7, 2026-10-29)

- The path traced light is anchored to vanilla in daylight, so `EyeAdaptation` keeps its -4.8 target; enclosed and night scenes meter darker than under vanilla light (night showcase -5.8, moonlit terrain -8.5 after the physical night, torch room -5.4, cave -8.0). Eye adaptation now brightens by half of the difference to the target, up to +1.5 stops, so caves become readable while night stays dark and its sky black (see `docs/architecture/image-pipeline.md`).
- Light shafts, clouds and underwater read texels 0 to 2 of the sky light target as before; the sky radiance map only adds rows below them.

## Next

1. Open items: wet surfaces, water and glass in the path tracer, separate direct and indirect signals, entity motion vectors for the history, LabPBR materials.
