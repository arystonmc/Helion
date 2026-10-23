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
2. `RtxSceneStage` runs after the opaque geometry. It drains the inbox, keeps sections within 4 sections of the camera section (Chebyshev, 9 x 9 x 9 sections) plus 2 sections of slack, and builds up to 16 bottom level structures per frame, nearest first: indexed triangles (one shared quad index buffer), solid opaque and cutout alpha tested, prefer fast trace, built with one shared scratch buffer at aligned offsets. The vertex buffer is released right after the build is recorded.
3. Sections in the radius whose mesh was compiled before they came near never arrive by themselves; the scene asks for at most 8 recompiles per frame through `LevelExtractor.setSectionDirty`. A section reset removes its structure.
4. The top level is rebuilt every frame from the sections within the radius (up to 1024 instances, a ring of four mapped instance buffers), translated relative to the camera section so coordinates stay small, with triangle culling off so one-sided quads (plants, leaves) block rays from both sides.
5. Each section structure has two geometries: solid quads marked opaque and cutout quads not opaque. Rays alpha test cutout candidates against the block atlas (`helionTraceCutout`, alpha at least 0.5 like vanilla's cutout layer), so leaves, flowers and fences let light and reflections through their holes.
6. Every section also keeps 12 bytes per vertex of surface attributes (atlas texture coordinate, vertex color, light coordinates) in a device local storage buffer. The instance custom index is the instance slot, and a per-frame surface table maps each slot to that buffer's device address, so a ray query hit reads the attributes of its triangle through `GL_EXT_buffer_reference` (instance, primitive and barycentrics; a quad is the triangles 0-1-2 and 2-3-0). This adds about 15 % to the scene memory.

## Ray Traced Shadows

With `rayTracing.shadows` (on by default under ray tracing, needs sun shadows), `ShadowStage` asks its `TracedShadows` (`RtxShadowTracer`) before the mask pass. Pass `rt_sun_shadow` traces four rays per half resolution terrain pixel inside the scene toward the stepped light direction, spread over a cone of 0.012 radians (a little wider than the sun, so shadows soften with the distance to their caster) in a Vogel pattern rotated per pixel. It stores the visibility of the decided rays and a weight: a ray that leaves the scene through a side is undecided, because terrain outside the scene may shade it, while one that leaves through the top counts as lit; the weight also fades over the last 8 blocks of the scene. The mask shader mixes the shadow map terrain visibility toward the traced one by that weight, and when the geometry buffer holds entities it also fades the entity shadow map out by that weight, because the traced rays already hit the entities.

Entities: while ray traced shadows run, every draw of the entity geometry pass (mobs, players, armor stands, dropped items, block entities with an entity or item pipeline) is recorded with its renderpearl vertex and index buffer. `VulkanGpuBufferMixin` gives renderpearl's vertex and index buffers the device address and build input usages, so `RtxEntityScene` builds one bottom level straight from the staged buffers, one geometry per draw, with the exact vertex range for sequential index buffers. It is built for fast builds with updates allowed: when the draws have the same shape as last frame (same triangle and vertex counts) it is only updated, with a full rebuild every 30 frames. It is added to the top level as one instance with ray mask 2, translated by the camera position, since entity vertices are camera relative. Reflections trace mask 1 only, so entities do not show in water yet. `rt_sun_shadow_view` (`-Dhelion.rayTracing.view=true`) paints blocked pixels dark blue over the output.

## Water Reflections

1. `WaterSurfaceStage` (`water_surface`, after the solid features) draws the translucent terrain layer a second time with `VanillaWaterPipelines` into its own RGBA16_FLOAT target and depth, testing against the opaque scene depth in the shader, so the target holds the nearest translucent surface. A fragment counts as water when its texture coordinate lies inside the atlas bounds of `block/water_still` or `block/water_flow`; it writes the octahedral normal (flat tops get four sine waves whose wave vectors repeat every 1024 blocks, driven by the game time), the thickness to the opaque surface behind (view depth difference along the ray, at most 64 blocks) and the sky light. Other translucent blocks (glass, ice) write zero and hide water behind them. Screen derivatives are taken before any branch, because helper pixels outside a quad can leave the water sprite and branch away (it drew dotted lines along block edges).
2. `RtxReflectionStage` (`rt_reflection`) traces one ray per water pixel inside the section scene from the surface along the reflected view ray, up to 96 blocks. A hit is shaded like vanilla terrain: atlas texel times vertex color times the lightmap at the interpolated light coordinates. Rays from outside the scene count as misses.
3. `WaterReflectionStage` publishes the composite that `TransparentGeometryStage` draws after the translucent geometry and before fog, clouds and light shafts. It mixes the reflection, or on a miss the physical sky with sun and moon in the reflected direction (vanilla fog color without the physical sky), by Schlick Fresnel with F0 0.02, and darkens the water under it by its thickness (absorption 0.15 per block) toward a scattering color lit by the sky above, scaled by the square of the sky light. One premultiplied blend does both: the scene keeps `exp(-0.15 d) (1 - F)`.
4. Underwater views, side faces and steep normals (normal y below 0.05) get no reflection yet.
5. Glossy translucent blocks (ice, stained glass): the surface pass marks them with thickness -1; the composite gives them Schlick Fresnel with F0 0.04, the screen space reflection where it hit, otherwise the sky scaled by the square of their sky light (no traced reflection, no absorption). Measured with ice in `day_showcase`, `water_noon`, `water_night`: `ssr` 0.06 to 0.07 ms, `water_surface` 0.02 ms.
6. Plain glass is cutout terrain whose center is discarded, so the surface pass also draws the cutout layer with `HELION_GLOSSY_ONLY` (no alpha test, only texels of the glossy mask). Polished opaque blocks (polished stone family, quartz, prismarine, glazed terracotta, smooth stone, packed and blue ice; `VanillaGlossyCells`) are flagged in the geometry buffer and get an on-screen-only screen space reflection at half strength, blended by `ssr_polished` before translucent geometry. Measured in `day_showcase`, `night_showcase`, `water_noon`: `ssr` 0.08 to 0.09 ms, `ssr_polished` 0.008 ms, `water_surface` 0.034 ms (0.02 without the glass draw), `opaque_geometry` unchanged within 0.01 ms. At the 20 to 30 degree view of the test floor the polished reflection changes pixels by only a few levels; it shows at flatter angles.
7. Screen space reflections (`ssr`, own setting, every card) go first: where the screen space ray hit, its color replaces the traced one, so mobs and players on screen and the full world lighting show in the water; the traced reflection fills what is off screen or hidden. Measured in `water_noon`, `water_sunset`, `water_night` (427 x 240 window): `ssr` 0.05 to 0.06 ms, `rt_reflection` 0.02 to 0.03 ms; reflected glowstone and sea lantern sit at the same place in both.

## Measured (RTX 4050 Laptop, 1080p, visual test world, 2026-10-23)

One visual test run (`day_showcase` and `morning_showcase`, variant `shadows`).

| Value | Result |
|---|---|
| Sections with geometry in the scene | 267 built, 183 within the radius at the shot |
| Memory of the bottom levels | 23 MiB |
| Top level build (`rt_top_level`) | 0.08 to 0.10 ms GPU |
| Bottom level builds while sections change (`rt_bottom_levels`) | 0.18 to 0.31 ms GPU in frames that build |
| Sun shadow trace, half resolution, one ray before soft shadows (`rt_sun_shadow`) | 0.11 to 0.13 ms GPU (the shadow map stages took 0.18 to 0.28 ms plus 0.30 ms for the mask) |
| Scene update on the render thread | 0.07 to 0.17 ms CPU per frame, 7 ms in the first frame |

- The traced shadows follow the block shapes: slab overhangs, the pillar and the trees around the area cast them, and thin blocks cast thin shadows.
- No Helion warning or error in the log; the scene was full about 8 seconds after ray tracing started (most sections came through recompile requests).

Water reflections (visual test `water_noon`, `water_sunset`, `water_night`, variant `shadows`, window 427 x 240): `water_surface` 0.01 to 0.02 ms, `rt_reflection` 0.03 ms, scene memory 31 MiB for 267 sections with the surface attributes (23 MiB without).

Ray traced shadows (visual test `day_showcase` and `morning_showcase`, variant `shadows`, window 427 x 240): `rt_sun_shadow` with four rays and cutout alpha testing 0.08 to 0.09 ms, `shadow_mask` 0.06 ms.

Entities (same scenes, about 2,900 entity quads in view across the whole render distance): a full rebuild every frame cost 0.6 ms; with updates `rt_top_level` takes 0.16 ms instead of 0.09 ms without entities.

## Next

1. Water: refraction of the bottom, glass and ice reflections, reflections seen from under water, sun shadow rays at the hit.
2. Entities in reflections (their textures are not in the block atlas), entities only within the section scene, alpha tested entity cutouts.
3. Compaction of the bottom levels and a radius chosen by memory, and a settings switch (with a preset entry, lesson L-117) once a visible feature uses the scene.
