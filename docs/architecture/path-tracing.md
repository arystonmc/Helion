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
   - pass `path_trace` dispatches `rtx/path_trace.csh` over a full resolution RGBA16F radiance image (`RayTracingResources.radiance`), linear HDR radiance for every geometry buffer pixel;
   - pass `path_traced_light` encodes it into the RGBA8 scene with the Neutral curve (the same encoding the image composite expands with `helionExpandScene`), then applies the chunk fade (light target blue) and the fog (albedo alpha) as the geometry pass did, only where geometry was written.
4. Everything after it (solid features, water, reflections, light shafts, transparency, post effects) is unchanged.

## Surface Data of Hits

Each section vertex in the surface buffer has four ints: texture coordinate, vertex color, light coordinates and the octahedral face normal of its quad (see `RtxSectionUpdate`). Hit shading reads the exact normal instead of guessing it. The material class of a hit will be read from the glossy block mask at its texture coordinate, so the capture needs no atlas on the compile workers.

## State (Phase 2 of the plan, 2026-10-27)

The foundation: setting, radiance image, both passes and the face normal in the surface data. The radiance is still the vanilla light (albedo times the bilinear lightmap at the stored levels, through the inverse Neutral curve), so the picture matches vanilla; the visual test variant `path_tracing` checks that. The face normal is used by the shadow ray of the traced reflections.

Moved to Phase 3, where their first user arrives (rule C4): the emitter registry of light emitting blocks per section, the light buffer with its section neighborhood table and the dynamic lights of held and burning things.

## Next

1. Phase 3, direct light: sun and moon next event estimation with a soft disk, ReSTIR DI for block and dynamic lights, stained glass tint, separate diffuse and specular signals.
2. Phase 4, multi-bounce indirect light; Phase 5, the denoiser; Phase 6, materials and traced specular; Phase 7, exposure calibration and the finish.
