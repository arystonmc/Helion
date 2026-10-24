# Helion Architecture: Stages

Part of the [Helion architecture](../ARCHITECTURE.md).

## Stages

| Order | Stage | Class | What it does |
|---|---|---|---|
| 1 | clear | `render.scene.ClearStage` | Clears scene color to the fog color and scene depth to the reversed-Z far value |
| 2 | sky | `render.atmosphere.SkyStage` | Draws the sky through `AtmosphereSource`, or with the physical sky on `render.atmosphere.PhysicalSkyStage`, see Physical Sky below |
| 3 | opaque_geometry | `render.geometry.OpaqueGeometryStage` | Prepares fog, chunk sampler, translucent buffers and lighting, then draws opaque terrain and solid features. With the geometry buffer on, only opaque terrain, in its own render pass through Helion's terrain shaders, see Geometry Buffer below |
| 3 | entity_geometry | `render.geometry.EntityGeometryStage` | Only with the geometry buffer on: solid mobs, players, items and block entities with a geometry pipeline, into the scene and the geometry buffer, see Geometry Buffer below |
| 3a | shadow_map, shadow_mask | `render.shadow.ShadowStage` | Only with shadows, lighting, the geometry buffer and the physical sky light on: draws the cascaded shadow map and the screen space shadow mask, see Shadows below |
| 3b | deferred_lighting, deferred_shading | `render.lighting.DeferredLightingStage` | Only with lighting and the geometry buffer on: lights terrain in HDR from the geometry buffer, see Deferred Lighting below |
| 3c | ambient_occlusion | `render.lighting.AmbientOcclusionStage` | Screen space ambient occlusion on the opaque terrain scene, see below. Skipped when disabled or when its shaders failed to compile |
| 4 | solid_features | `render.geometry.SolidFeatureStage` | Only with the geometry buffer on: the after-opaque-blocks event and solid features, drawn after terrain ambient occlusion |
| 4 | water_surface, rt_reflection, ssr, ssr_polished | `render.water.WaterSurfaceStage`, `render.rtx.RtxReflectionStage`, `render.water.ScreenReflectionStage`, `render.water.WaterReflectionStage` | Only with the ray traced water reflections or the screen space reflections setting: the nearest translucent surface with water normals and thickness, the screen space and traced reflections and the composite that transparent geometry draws before fog, see Ray Tracing |
| 4a | cloud_noise, cloud_march, cloud_resolve | `render.clouds.CloudStage` | Only with volumetric clouds and the physical sky on: raymarches the clouds at a reduced resolution and blends them with their history, see Volumetric Clouds |
| 4b | light_shaft_mask, light_shaft_blur | `render.lightshaft.LightShaftStage` | Only with light shafts and the physical sky on and the sun or moon in view: builds the quarter resolution shaft mask from scene depth and the clouds and blurs it toward the light, see Light Shafts |
| 5 | transparent_geometry | `render.geometry.TransparentGeometryStage` | Sorted or order independent transparency, clouds, weather, world border, then outline, see-through and always-on-top features. With the physical sky, also its distance fog, see Physical Sky below, and after it the volumetric clouds in place of the vanilla clouds and then the light shafts |
| 5a | terrain_depth (in stage 3), taa_resolve, taa_apply | `render.temporal.TemporalStage` | Only with temporal anti-aliasing on: blends the jittered frame with the reprojected history, see Temporal Anti-Aliasing below |
| 6 | post | `render.post.PostProcessingStage` | Runs post effects in order: the vanilla entity outline effect, then `render.edge.EdgeSmoothingStage` (see Edge Smoothing below), then `render.motion.MotionBlurStage` (see Motion Blur below), then `render.dof.DepthOfFieldStage` (see Depth of Field below), then `render.post.ExposureStage` (automatic exposure metering), then `render.post.BloomStage`, then `render.post.SharpeningStage`, see Image Pipeline below |
| 7 | present | `render.post.PresentStage` | Copies the scene target into the main target when no image effect is active |
| 7 | composite | `render.post.ImageCompositeStage` | Replaces present when bloom, sharpening, exposure or Filmic tone mapping is active: tone maps the scene (or its sharpened copy) with bloom into the main target and copies depth |
| 8 | geometry_view | `render.geometry.GeometryBufferDebugStage` | Only with a geometry buffer view selected: draws normals, block light, sky light or albedo over the main target |

Planned stages and where they go: atmosphere effects after transparent geometry, further Helion post effects inside `PostProcessingStage` after sharpening.
