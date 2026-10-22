# Helion Architecture: Ray Tracing

Part of the [Helion architecture](../ARCHITECTURE.md).

## Goal

An optional quality layer for cards with hardware ray tracing, on top of voxel lighting, which stays the base for every card (owner decision of 2026-10-22, see the RTX spike in [voxel-lighting.md](voxel-lighting.md)). Planned users: reflections on water and glass with the water update, shadows of thin shapes and entities, bounced light. Today it is the foundation only, behind `-Dhelion.rayTracing=true`; `-Dhelion.rayTracing.view=true` draws the validation view. Without the property nothing of it loads and the device is created as before.

## Device

1. `VulkanFeatureSetsMixin` adds the optional feature set `RtxFeature` (acceleration structures, ray queries, deferred host operations, buffer device addresses). Renderpearl enables it only when the card supports all of it.
2. `VulkanBackendMixin` then creates renderpearl's VMA allocator with buffer device addresses, so every ray tracing buffer comes from VMA blocks (`RtxBuffer`). The RTX spike used one `vkAllocateMemory` per buffer; with hundreds of sections that would run into the driver's allocation count limit.
3. Commands are recorded into renderpearl's current command buffer inside frame graph passes without culling, outside any render pass, with renderpearl's full memory barrier around them. Renderpearl keeps every image in `GENERAL` layout, so compute passes can read its textures and write storage images (`VulkanConstMixin` adds the storage usage) without layout changes.

## Section Scene

1. `SectionCompilerMixin` sees every compiled section on the compile worker. `RtxSectionCapture` copies the quad positions of the solid and cutout layers (relative to the section; vanilla floats or Aervium's compact 16-bit positions) when the section lies within 5 sections of the camera section, and hands them to `RtxSectionInbox`. Translucent terrain (water, glass, ice) is not in the scene yet.
2. `RtxSceneStage` runs after the opaque geometry. It drains the inbox, keeps sections within 4 sections of the camera section (Chebyshev, 9 x 9 x 9 sections) plus 2 sections of slack, and builds up to 16 bottom level structures per frame, nearest first: indexed triangles (one shared quad index buffer), opaque, prefer fast trace, built with one shared scratch buffer at aligned offsets. The vertex buffer is released right after the build is recorded.
3. Sections in the radius whose mesh was compiled before they came near never arrive by themselves; the scene asks for at most 8 recompiles per frame through `LevelExtractor.setSectionDirty`. A section reset removes its structure.
4. The top level is rebuilt every frame from the sections within the radius (up to 1024 instances, a ring of four mapped instance buffers), translated relative to the camera section so coordinates stay small, with triangle culling off so one-sided quads (plants, leaves) block rays from both sides.
5. Cutout quads count as solid for now: leaves are closed blocks and plants are flat panes. Alpha testing them needs their texture coordinates in the scene.

## Validation View

`RtxSunShadowStage` (`rt_sun_shadow`, after the shadow stage while it has a sun or moon) traces every half resolution terrain pixel inside the scene toward the light with `rayQueryEXT` (terminate on first hit, opaque) and writes lit or blocked into a mask; `rt_sun_shadow_view` paints blocked pixels dark blue over the output. Entities are not in the scene, so they are traced but cast nothing.

## Measured (RTX 4050 Laptop, 1080p, visual test world, 2026-10-23)

One visual test run (`day_showcase` and `morning_showcase`, variant `shadows`).

| Value | Result |
|---|---|
| Sections with geometry in the scene | 267 built, 183 within the radius at the shot |
| Memory of the bottom levels | 23 MiB |
| Top level build (`rt_top_level`) | 0.08 to 0.10 ms GPU |
| Bottom level builds while sections change (`rt_bottom_levels`) | 0.18 to 0.31 ms GPU in frames that build |
| Sun shadow trace, half resolution (`rt_sun_shadow`) | 0.11 to 0.13 ms GPU (the shadow map stages took 0.18 to 0.28 ms plus 0.30 ms for the mask) |
| Scene update on the render thread | 0.07 to 0.17 ms CPU per frame, 7 ms in the first frame |

- The traced shadows follow the block shapes: slab overhangs, the pillar and the trees around the area cast them, and thin blocks cast thin shadows.
- No Helion warning or error in the log; the scene was full about 8 seconds after ray tracing started (most sections came through recompile requests).

## Next

1. Reflections with the water update: a reflection pass that traces from water and glass pixels; needs the hit's color, so the scene must keep texture coordinates (or the block id) per quad.
2. Cutout alpha testing, then entities as their own bottom levels (boxes first).
3. Compaction of the bottom levels and a radius chosen by memory, and a settings switch (with a preset entry, lesson L-117) once a visible feature uses the scene.
