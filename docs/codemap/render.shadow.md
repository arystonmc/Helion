# Helion Code Map: `com.aryston.helion.render.shadow`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.shadow`

#### ShadowStage
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowStage.java`
- Role: Sun and moon shadows. `shadow_map` draws the solid and cutout terrain of every cascade that `ShadowCascadeCache` marks as due into that cascade's own depth-only target, one render pass each without a color attachment, and leaves the other targets as they are; `shadow_entities` draws the solid entities, block entities and items the camera sees into their own color plus reversed depth target for the two near cascades every frame, by replaying the vanilla feature draws with the projection swapped for the matrix the cascade was drawn with, moved by the camera movement since then (`ShadowCascades.entityProjection`); `shadow_mask` reconstructs every terrain pixel from scene depth, picks the first cascade that holds it (with the matrix the cascade was drawn with, moved by the camera movement since then), offsets it along the normal and toward the light, filters the shadow map with a 4 x 4 tent (in the near cascades also the entity map, keeping the darker result) and writes the R8 visibility mask `helion:shadow_mask` (1 lit, 0 shadowed), fading to lit toward the shadow distance. Publishes the mask and the `ShadowLight` in `ShadowResults`. Inactive when shadows or lighting are off, the geometry buffer was not written, the physical sky published no `SkyLight`, the sun and moon are both below the horizon, the scene color is not RGBA8, the source cannot cast shadows or a shader failed to compile. With a `TracedShadows` that returns an image (`RtxShadowTracer` with ray traced shadows on), the mask pass uses `MASK_TRACED`, which mixes the terrain visibility of the shadow map toward the traced visibility by its weight, sampled bilinearly from half resolution, before taking the darker of terrain and entities.
- Depends on: `ShadowCasterSource`, `ShadowResources`, `ShadowCascades`, `ShadowLight`, `ShadowPipelines`, `ShadowResults`, `GeometryBuffer`, `FullscreenPass`.

#### TracedShadows
- Path: `src/main/java/com/aryston/helion/render/shadow/TracedShadows.java`
- Role: Contract of a ray traced terrain shadow source: adds its passes before the mask pass and returns the image, or nothing when it does not trace this frame.

#### ShadowCascades
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowCascades.java`
- Role: Splits the view up to the shadow distance (the render distance, at most 160 blocks) into four cascades at 8, 22, 50 and 100 %, fits a bounding sphere around each slice (radius rounded to 1/16 block so it does not change while turning), and builds an orthographic light projection whose center is snapped to whole shadow map texels in world space, so shadow edges never crawl while the camera moves. The depth range reaches 256 blocks toward the light, so casters outside the view still cast.
- Members: `COUNT`, `compute(...)`, `distance(renderDistanceBlocks)`, `entityProjection(lightViewProjection, viewRotation, zeroToOneDepth)` (the projection that turns entity draws, whose model view is the camera rotation, into a cascade's light space with reversed depth).

#### ShadowCascadeCache
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowCascadeCache.java`
- Role: Keeps two sets of cascades, each cascade as it was last drawn (cascade, camera position, light direction, time). When the light steps and the current set is complete the sets swap, the new current set is drawn for the new direction, and once it is complete `previousWeight` fades the previous set out over the time between the last two steps. In the current set a cascade whose light differs or that is older than its refresh time (100, 100, 200 and 400 ms) is drawn at once for the two near ones and on its turn for the far ones (the third on even, the far one on odd frames); a cascade that was never drawn, whose radius changed, whose center moved by more than a quarter of its radius or whose light turned by more than 0.5° is drawn at once; a turn of more than 0.5°, a change of quality or between sun and moon starts over without a fade; the previous set is dropped when the camera moved a quarter of the near cascade away. Gives the mask and the entity shadows the matrix of each cascade moved by the camera movement since it was drawn.
- Members: `update(cascades, camera, light, resolution, now)` returns the `Slot`s (set and cascade) to draw and records them, `currentSet()`, `previousSet()`, `previousWeight(now)`, `maskMatrix(set, index, camera)`, `texelSize(set, index)`, `lightDirection(set)`, `clear()`.
- Depends on: `ShadowCascade`, `ShadowCascades`, `ShadowLight`.

#### ShadowCascade
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowCascade.java`
- Role: One cascade: light view, orthographic projection, light space center, radius, caster reach and texel size. `containsLightSpace` tells whether a chunk section is inside it or between it and the light.

#### ShadowLight
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowLight.java`
- Role: The light that casts shadows this frame: the sun while it is above the horizon, otherwise the moon. Its strength fades smoothly to zero between 0.12 and 0.02 of elevation, so shadows never pop at sunrise and sunset; `sunStrength` and `moonStrength` feed the lighting block. Its direction turns in steps of 0.1° of elevation and azimuth (`stepped`); `ShadowCascadeCache` fades between the shadow maps of two steps, so shadows move smoothly while the texel grid stays put between steps.

#### ShadowCasterSource
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowCasterSource.java`
- Role: Contract for drawing shadow casters: `supportsShadows`, `renderDistanceBlocks`, `prepare(cascades)` once per frame with the cascades drawn in that frame and `renderCascade(slot, pass)` inside the shadow map pass, where `slot` is the position in that list. `renderEntities(pass)` draws the solid entities, block entities and items of the frame into the current pass with the current projection.

#### ShadowResources
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowResources.java`
- Role: Persistent shadow state owned by the core: two sets of one D32_FLOAT shadow map per cascade (target `set x 4 + cascade`, recreated when the quality changes, which also clears the cache, registered in `GpuResources`), the `ShadowCascadeCache`, one projection uniform ring per cascade, two entity shadow targets (RGBA8 color plus D32_FLOAT depth at half the cascade resolution, because the vanilla entity pipelines need a color target) with their own projection rings, the `HelionShadow` uniform ring (inverse level view projection, the cached cascade matrices and texel sizes, light direction, shadow distance, cascade resolution) and the one-time missing shader warning. `drawnLastFrame` tells whether `ShadowStage` drew sun shadows in the last frame; `EntityRendererMixin` reads it.
- Depends on: `GpuResources`, `UniformRing`, `ShadowCascades`, `ShadowCascadeCache`, `HelionCamera`.

#### ShadowPipelines
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowPipelines.java`
- Role: The shadow mask pipeline, the caster depth state with slope and constant depth bias, the uniform and sampler names (including the two entity shadow samplers), the shadow map, entity color and mask formats, the number of entity cascades, their resolution divisor and their reversed clear depth. `MASK_TRACED` is the mask with `HELION_TRACED_SHADOWS` and the `TracedShadowSampler`.
- Depends on: `HelionPipelines`.

#### ShadowQuality
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowQuality.java`
- Role: Cascade resolution presets: `LOW` 1024, `MEDIUM` 1536, `HIGH` 2048 texels per cascade.

#### ShadowSettings
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowSettings.java`
- Role: Shadow settings snapshot: enabled and quality.

#### ShadowResults
- Path: `src/main/java/com/aryston/helion/render/shadow/ShadowResults.java`
- Role: What `ShadowStage` hands to deferred lighting in one frame: the shadow mask handle and the `ShadowLight`, and the indirect share target that the shadowed shading publishes for ambient occlusion.
