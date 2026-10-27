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
   - pass `path_trace` dispatches `rtx/path_trace.csh` over a full resolution RGBA16F radiance image (`RayTracingResources.radiance`), linear HDR radiance for every geometry buffer pixel (see Direct Light);
   - pass `path_traced_light` encodes it into the RGBA8 scene with the Neutral curve (the same encoding the image composite expands with `helionExpandScene`), then applies the chunk fade (light target blue) and the fog (albedo alpha) as the geometry pass did, only where geometry was written.
4. Everything after it (solid features, water, reflections, light shafts, transparency, post effects) is unchanged.

## Surface Data of Hits

Each section vertex in the surface buffer has four ints: texture coordinate, vertex color, light coordinates and the octahedral face normal of its quad (see `RtxSectionUpdate`). Hit shading reads the exact normal instead of guessing it. The material class of a hit will be read from the glossy block mask at its texture coordinate, so the capture needs no atlas on the compile workers.

## Direct Light (Phase 3, 2026-10-27)

The stage needs the physical sky (`SkyLight`: sun, moon and dome irradiance in a 3x1 target) and a scene anchor; without the sky terrain keeps the vanilla light.

- Units (L-175): linear light where open flat ground under the full sky equals the vanilla full sky light linearized. Every frame the factor `scale` maps sun times its height plus moon times its height plus dome onto the vanilla full sky light minus the vanilla darkest light, which stays as the ambient floor. Day and night brightness therefore follow vanilla while the shape of the light is traced.
- Albedo: the geometry albedo is divided by the vanilla directional shade of its face (up 1, down 0.5, north and south 0.8, east and west 0.6; cross plants keep theirs) and linearized. The vanilla ambient occlusion in the vertex color stays for now.
- Sun and moon: one shadow ray each (only above the horizon) toward a direction sampled in a disk of about four times the real angular radius, masks 1 and 2 with the cutout test, from an origin offset along the normal by a constant plus a share of the distance (L-173). A pixel with sky level 0 gets no sun or moon, so rays that leave the scene above a deep cave do not light it.
- Block lights: `RtxSectionLights` scans every compiled section for light emitting blocks (box of the shape, `LightColors` color, range and strength from the level); `RtxLightScene` puts the lights of each section and its neighbors into a 9x9x9 cell grid around the anchor (up to 32 per cell). Each candidate is weighed unshadowed with a windowed inverse square falloff from the nearest point of its box and a Lambert term aimed at the face of the light that looks the same way as the surface (L-176). The brightest light gets a shadow ray, and one other light is picked in proportion to its weight with weighted reservoir sampling and traced; its estimate adds only a saturating share (L-171). Shadow rays end where they enter the light box, and faces of the light itself count as lit (L-174). Fire lights flicker. Block light fades by day where the sun, moon and sky light the pixel (L-170).
- Emissive texels (glossy mask cell 1, now geometry material `HELION_GEOMETRY_EMISSIVE`, 0.8125) and lava stay full bright. Pixels within 8 blocks of the scene edge blend to the vanilla light.

Deferred: temporal and spatial reservoir reuse (with the reprojection and history of Phase 5), dynamic lights of held and burning things, the stained glass tint, separate diffuse and specular signals (no consumer before Phase 5), GGX and Burley shading (Phase 6 materials), ambient occlusion only on the ambient term, and sun shadows on emissive faces.

## Indirect Light (Phase 4, 2026-10-27)

One path per pixel at full resolution, up to 3 bounces, in the same pass and units as the direct light; it replaced the sky dome stand-in and the vanilla block light fill of Phase 3.

- Each bounce samples a cosine weighted direction and traces the sections (mask 1) with the cutout test; plant planes start their rays 0.3 blocks out.
- A miss adds the sky dome (0.3 of it for rays that leave downward), gated by the sky level of the surface the ray left, so rays that leave the scene through rock around a deep cave add nothing.
- A hit reads the surface data: the exact face normal, the atlas texel times the vertex tint, divided by the vanilla shade and linearized, and the sky level. The path then adds the hit's direct light: the sun and moon shadow rays (only with sky level above 0), one block light picked in proportion to its unshadowed weight from the grid with one shadow ray, scaled to the same saturating combination as the primary surface, and the vanilla darkest light.
- A hit on an emissive texel (material mask cell 1, read from `GlossyMask`) ends the path without adding light: those texels belong to registered light blocks, whose light the shadow ray sampling already counts.
- Russian roulette from the third bounce keeps the path with the probability of its brightest throughput channel (at least 0.1). Where a path ends early or at the last bounce, its last hit adds the dome times its sky level raised to 2.2 as the tail of the missing bounces.
- The indirect light of a pixel is limited to a luminance of 4 against fireflies.

Entities are not hit by bounce rays yet; they still cast direct shadows. There is no accumulation or filter before Phase 5, so the indirect light is noisy, most of all in rooms lit by block light.

## Next

1. Phase 5, the denoiser; Phase 6, materials and traced specular; Phase 7, exposure calibration and the finish.
