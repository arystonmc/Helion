# Helion Architecture: Ray Tracing

Part of the [Helion architecture](../ARCHITECTURE.md).

## Goal

An optional quality layer for cards with hardware ray tracing, on top of voxel lighting, which stays the base for every card (owner decision of 2026-10-22, see the RTX spike in [voxel-lighting.md](voxel-lighting.md)). Users: reflections on water (first version), later glass, shadows of thin shapes and entities, bounced light. The experimental setting `rayTracing.enabled` (Lighting page, on in the Experimental preset) turns it on after a restart: the device is created before NeoForge loads the client config, so `RayTracingStartup` reads that line from `helion-client.toml` when the Vulkan mixins first ask, and `-Dhelion.rayTracing=true` forces it for tests. Turning it off in game releases the scene at once (`RayTracingResources.suspend`); turning it on again rebuilds it if the device was created with ray tracing. `rayTracing.waterReflections` switches the water reflections without a restart. `-Dhelion.rayTracing.view=true` runs and draws the sun shadow validation view. With the setting off and no property nothing of it loads and the device is created as before.

## Device

1. `VulkanFeatureSetsMixin` adds the optional feature set `RtxFeature` (acceleration structures, ray queries, deferred host operations, buffer device addresses). Renderpearl enables it only when the card supports all of it.
2. `VulkanBackendMixin` then creates renderpearl's VMA allocator with buffer device addresses, so every ray tracing buffer comes from VMA blocks (`RtxBuffer`). The RTX spike used one `vkAllocateMemory` per buffer; with hundreds of sections that would run into the driver's allocation count limit.
3. Commands are recorded into renderpearl's current command buffer inside frame graph passes without culling, outside any render pass, with renderpearl's full memory barrier around them. Renderpearl keeps every image in `GENERAL` layout, so compute passes can read its textures and write storage images (`VulkanConstMixin` adds the storage usage) without layout changes.

## Section Scene

1. `SectionCompilerMixin` sees every compiled section on the compile worker. `RtxSectionCapture` copies the quad positions of the solid and cutout layers (relative to the section; vanilla floats or Aervium's compact 16-bit positions) when the section lies within 5 sections of the camera section, and hands them to `RtxSectionInbox`. Translucent terrain (water, glass, ice) is not in the scene yet.
2. `RtxSceneStage` runs after the opaque geometry. It drains the inbox, keeps sections within 4 sections of the camera section (Chebyshev, 9 x 9 x 9 sections) plus 2 sections of slack, and builds up to 16 bottom level structures per frame, nearest first: indexed triangles (one shared quad index buffer), opaque, prefer fast trace, built with one shared scratch buffer at aligned offsets. The vertex buffer is released right after the build is recorded.
3. Sections in the radius whose mesh was compiled before they came near never arrive by themselves; the scene asks for at most 8 recompiles per frame through `LevelExtractor.setSectionDirty`. A section reset removes its structure.
4. The top level is rebuilt every frame from the sections within the radius (up to 1024 instances, a ring of four mapped instance buffers), translated relative to the camera section so coordinates stay small, with triangle culling off so one-sided quads (plants, leaves) block rays from both sides.
5. Cutout quads count as solid for now: leaves are closed blocks and plants are flat panes.
6. Every section also keeps 12 bytes per vertex of surface attributes (atlas texture coordinate, vertex color, light coordinates) in a device local storage buffer. The instance custom index is the instance slot, and a per-frame surface table maps each slot to that buffer's device address, so a ray query hit reads the attributes of its triangle through `GL_EXT_buffer_reference` (instance, primitive and barycentrics; a quad is the triangles 0-1-2 and 2-3-0). This adds about 15 % to the scene memory.

## Validation View

`RtxSunShadowStage` (`rt_sun_shadow`, only with `helion.rayTracing.view`, after the shadow stage while it has a sun or moon) traces every half resolution terrain pixel inside the scene toward the light with `rayQueryEXT` (terminate on first hit, opaque) and writes lit or blocked into a mask; `rt_sun_shadow_view` paints blocked pixels dark blue over the output. Entities are not in the scene, so they are traced but cast nothing.

## Water Reflections

1. `WaterSurfaceStage` (`water_surface`, after the solid features) draws the translucent terrain layer a second time with `VanillaWaterPipelines` into its own RGBA16_FLOAT target and depth, testing against the opaque scene depth in the shader, so the target holds the nearest translucent surface. A fragment counts as water when its texture coordinate lies inside the atlas bounds of `block/water_still` or `block/water_flow`; it writes the octahedral normal (flat tops get four sine waves whose wave vectors repeat every 1024 blocks, driven by the game time), the thickness to the opaque surface behind (view depth difference along the ray, at most 64 blocks) and the sky light. Other translucent blocks (glass, ice) write zero and hide water behind them. Screen derivatives are taken before any branch, because helper pixels outside a quad can leave the water sprite and branch away (it drew dotted lines along block edges).
2. `RtxReflectionStage` (`rt_reflection`) traces one ray per water pixel inside the section scene from the surface along the reflected view ray, up to 96 blocks. A hit is shaded like vanilla terrain: atlas texel times vertex color times the lightmap at the interpolated light coordinates. Rays from outside the scene count as misses.
3. `WaterReflectionStage` publishes the composite that `TransparentGeometryStage` draws after the translucent geometry and before fog, clouds and light shafts. It mixes the reflection, or on a miss the physical sky with sun and moon in the reflected direction (vanilla fog color without the physical sky), by Schlick Fresnel with F0 0.02, and darkens the water under it by its thickness (absorption 0.15 per block) toward a scattering color lit by the sky above, scaled by the square of the sky light. One premultiplied blend does both: the scene keeps `exp(-0.15 d) (1 - F)`.
4. Underwater views, side faces and steep normals (normal y below 0.05) get no reflection yet.

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

Water reflections (visual test `water_noon`, `water_sunset`, `water_night`, variant `shadows`, window 427 x 240): `water_surface` 0.01 to 0.02 ms, `rt_reflection` 0.03 ms, scene memory 31 MiB for 267 sections with the surface attributes (23 MiB without).

## Next

1. Water: screen space reflections for cards without ray tracing and for water beyond the section scene, refraction of the bottom, glass and ice reflections, reflections seen from under water, sun shadow rays at the hit.
2. Cutout alpha testing, then entities as their own bottom levels (boxes first).
3. Compaction of the bottom levels and a radius chosen by memory, and a settings switch (with a preset entry, lesson L-117) once a visible feature uses the scene.
