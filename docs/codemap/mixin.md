# Helion Code Map: `com.aryston.helion.mixin`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.mixin`

#### LevelExtractorMixin
- Path: `src/main/java/com/aryston/helion/mixin/LevelExtractorMixin.java`
- Role: Reports the section of every changed block to `VoxelSectionChanges` at the head of `LevelExtractor.blockChanged`, so the voxel volume reads it again in the same frame.
- Notes: No client event reports a changed block.

#### LevelRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/LevelRendererMixin.java`
- Role: Wraps `LevelRenderer.render` to hand the frame to `LevelRenderHook`, and wraps `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` so they resolve to the scene target during a Helion frame.

#### GameRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/GameRendererMixin.java`
- Role: Hands the level projection matrix that `GameRenderer.renderLevel` uploads (camera projection with view bobbing and nausea distortion) to `HelionRenderCore`, unchanged; frames read it as `HelionCamera.levelProjection`.
- Notes: no event exposes the final level projection, and temporal anti-aliasing must jitter and reproject exactly the matrix the level is drawn with.

#### EntityRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/EntityRendererMixin.java`
- Role: Skips vanilla's round entity shadow (`EntityRenderer.extractShadow`) while the core is active and drew sun shadows in the last frame, so mobs and items show only their real sun shadow; with Helion off or its shadows off vanilla draws the round shadow as before. Also saves the block scan vanilla runs under every entity for it. Raises the return value of `EntityRenderer.getPackedLightCoords` through `DynamicLightTracker.brighten`, so entities and the first-person hand near a dynamic light are lit by it.
- Notes: No event lets a mod drop the entity shadow pieces before they are submitted, and none lets it change the light an entity is drawn with.

#### CloudRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/CloudRendererMixin.java`
- Role: Cancels `CloudRenderer.render` and `renderOit` while `HelionClouds` says the volumetric clouds replace the vanilla clouds this frame.
- Notes: the vanilla clouds are drawn inside the vanilla transparency code, in the order independent path too; no event or option skips them for one frame.

#### SkyRendererMixin
- Path: `src/main/java/com/aryston/helion/mixin/SkyRendererMixin.java`
- Role: Cancels `SkyRenderer.renderSkyDisc`, `renderSunriseAndSunset`, `renderSun`, `renderMoon` and `renderStars` while `HelionSky` draws only the celestial bodies, so only the vanilla dark disc is drawn over the physical sky (which draws its own sun, moon, stars and twilight glow), and dims the color of `renderMoon` through `HelionSky.moonColor` whenever Helion draws the sky.
- Notes: `SkyRenderer.render` draws the sky color and the celestial bodies in one call; no API draws the celestial bodies alone or sets their size, and keeping the vanilla moon phases and stars avoids copying their textures and geometry.

#### ChunkSectionsToRenderAccessor
- Path: `src/main/java/com/aryston/helion/mixin/ChunkSectionsToRenderAccessor.java`
- Role: Invoker for the private `ChunkSectionsToRender.renderLayers`, the only vanilla entry that takes per layer pipeline overrides.

#### RenderSectionMixin
- Path: `src/main/java/com/aryston/helion/mixin/RenderSectionMixin.java`
- Role: Stores the shadow-caster list index on each render section and queues it after its mesh is replaced or reset, for the shadow casters and, by its section node, for the voxel volume (`VoxelSectionChanges`).
- Notes: No API reports render-section mesh changes.

#### LevelRendererAccessor
- Path: `src/main/java/com/aryston/helion/mixin/LevelRendererAccessor.java`
- Role: Accessors and invokers for private `LevelRenderer` fields, constants and methods needed to rebuild `render`, including the setter of the vanilla `skyRenderer` that `VanillaFrameDriver` creates like vanilla does, and the visible sections, view area and multi-draw state the shadow casters need.
