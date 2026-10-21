# Helion Architecture: Mixins

Part of the [Helion architecture](../ARCHITECTURE.md).

## Mixins

| Mixin | Target | Why no API is enough |
|---|---|---|
| `LevelRendererMixin.helion$render` | `LevelRenderer.render` | NeoForge can add passes but cannot replace the vanilla pass structure |
| `LevelRendererMixin.helion$redirectMainTarget` | `GameRenderer.mainRenderTarget()` calls inside `LevelRenderer` | Vanilla OIT and depth bounds code reads the main target directly instead of the frame graph handle |
| `GameRendererMixin.helion$captureLevelProjection` | `ProjectionMatrixBuffer.getBuffer` inside `GameRenderer.renderLevel` | The final level projection includes view bobbing and nausea distortion and is built inside `renderLevel`; no event exposes it, and temporal anti-aliasing must jitter and reproject exactly this matrix |
| `SkyRendererMixin.helion$skipUnderPhysicalSky` | `SkyRenderer.renderSkyDisc` | `SkyRenderer.render` draws the sky color and the celestial bodies in one call; under the physical sky only the vanilla sunset glow, sun, moon and stars must stay |
| `CloudRendererMixin.helion$skipUnderVolumetricClouds` | `CloudRenderer.render(CloudStatus, RenderPass)` and `renderOit`, named with full descriptors because a private `render(RenderPass, RenderPipeline)` overload exists | The vanilla clouds are drawn inside the vanilla sorted and order independent transparency code; no event or option leaves them out for one frame while the volumetric clouds replace them |
| `EntityRendererMixin.helion$addDynamicLight` | return of `EntityRenderer.getPackedLightCoords` | No event or API changes the light an entity, an item or the first-person hand is drawn with |
| `LevelRendererAccessor` | private members of `LevelRenderer` | Required to rebuild `render` with vanilla behavior |
| `ChunkSectionsToRenderAccessor.helion$renderLayers` | private `ChunkSectionsToRender.renderLayers` | Only this method takes per layer pipeline overrides; the public `renderGroup` applies one override to every layer, and solid and cutout terrain need different pipelines |
