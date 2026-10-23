# Helion Architecture: Geometry Buffer

Part of the [Helion architecture](../ARCHITECTURE.md).

## Geometry Buffer

Foundation for Helion's own lighting. Off by default (`geometryBuffer.enabled`). Read by deferred lighting and the debug view; shadows will read it too. Its targets are cleared to zero in the geometry pass, so alpha 0 marks pixels without terrain.

- `VanillaGeometryPipelines` copies the vanilla `SOLID_TERRAIN`, `CUTOUT_TERRAIN` and their multi-draw pipelines with `toBuilder()`, so every define (`ALPHA_CUTOUT`), bind group, vertex format and depth state stays vanilla, and replaces the shaders with `helion:terrain/geometry` plus three more color targets.
- Aervium's `compactVertices` module can switch the vanilla solid and cutout terrain to a 16-byte vertex format and adds the define `AERVIUM_COMPACT_VERTICES=1`; the geometry pipelines inherit both through `toBuilder()`, and `helion_terrain_vertex.glsl` decodes it to the same position, texture coordinate and light values, so the rest of both terrain shaders is unchanged. The contract is the define, not shared code (rule S2); `CompactTerrainVertices` turns the geometry buffer and shadows off with a warning for a format version Helion does not know.
- `terrain/geometry.vsh` and `.fsh` repeat the vanilla `core/terrain` math line by line for target 0, so the scene color stays identical to vanilla (the parity check runs with the geometry buffer when it is on). The extra targets are filled from data vanilla throws away: the lightmap coordinates before they become a color, the vertex color before light, and the face normal from screen derivatives of the camera relative position.
- GLSL only promises the same `gl_Position` in two programs when both declare it `invariant`, and vanilla does not. The geometry pipelines still produced bit-identical depth on NVIDIA (RTX 4050, driver 616.92) in every visual test scene and in game with ticks frozen. If the parity check ever shows depth-only differences on terrain with ticks frozen on another GPU, leave color and depth to the vanilla terrain pass and fill the geometry buffer in a second pass that tests against the scene depth without writing it, with a small depth bias toward the camera (`DepthStencilState` supports it, vanilla uses it for block cracks).
- Renderpearl requires the color attachment count of a render pass to match the pipeline, so opaque terrain gets its own render pass with four attachments. Solid features, the `AFTER_OPAQUE_BLOCKS` event and other mods keep drawing into the usual one-attachment pass afterwards (`SolidFeatureStage`), in the same order as vanilla, after deferred lighting.
- Per layer pipelines go through `ChunkSectionsToRender.renderLayers` (invoker mixin) with the vanilla override parameters, the same mechanism vanilla uses for wireframe and OIT terrain. Wireframe terrain (F3 debug) and missing shaders fall back to the vanilla path.

| Target | Format | Content |
|---|---|---|
| 0 | scene RGBA8 | vanilla terrain color with fog |
| 1 `helion:geometry_normal` | RGB10A2_UNORM | world space face normal as `n * 0.5 + 0.5`, straight up for crossed plant planes (grass, flowers, saplings) so plants are lit and shadowed like the ground they stand on, alpha 1 where terrain was drawn |
| 2 `helion:geometry_light` | RGBA8_UNORM | R block light, G sky light (lightmap coordinate / 240), B chunk fade-in visibility, alpha 1 where terrain was drawn |
| 3 `helion:geometry_albedo` | RGBA8_UNORM | texture color times vertex color (biome tint and vanilla shading), before light and fog; alpha is the vanilla fog amount of the pixel |

Solid entities are in the geometry buffer as well (`EntityGeometryStage`, pass `entity_geometry`), so sun shadows, ambient occlusion, deferred and voxel lighting light mobs and players per pixel like the ground they stand on:

- `EntityGeometryPipelines` copies every vanilla `core/entity` and `core/item` pipeline that draws opaque or cutout with depth writes and the lightmap, with `toBuilder()`, and switches it to `helion:entity/geometry`, which repeats the vanilla entity math for target 0 and writes the derivative face normal, the lightmap coordinates (light B is 1, entities have no chunk fade-in) and the shaded texture color as albedo, like terrain. Emissive, glint, dissolve, blended and OIT pipelines stay vanilla.
- The vanilla solid feature phase runs twice. In the entity geometry pass `FeatureGroupMixin` skips every feature group whose renderer does not draw through render types, and `PreparedRenderTypeMixin` swaps each draw's pipeline for its variant or skips it. `SolidFeatureStage` then runs the phase again in the one-attachment pass after deferred lighting and draws exactly what was skipped, plus the after-opaque-features event. Draws stay in vanilla order within each pass; only opaque draws move between passes, so no blending order changes.
- The pass runs after the terrain depth copy, so temporal anti-aliasing still sees entities as moving objects.

Translucent terrain, translucent entities, particles and the sky are not in the geometry buffer yet; their pixels keep alpha 0.

Renderpearl in 26.3 has no compute shaders (`ShaderType` only has vertex and fragment), so every geometry buffer consumer is a fragment pass.
