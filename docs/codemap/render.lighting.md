# Helion Code Map: `com.aryston.helion.render.lighting`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.lighting`

#### AmbientOcclusionStage
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionStage.java`
- Role: Adds the ambient occlusion passes (`ao_depth`, `ao_main`, `ao_denoise_N`, `ao_apply`) with their internal targets to the frame graph. Inactive when disabled, when the scene color is not RGBA8 or when a shader failed to compile. With an indirect share published, `ao_apply` uses the `apply_indirect` pipeline so occlusion darkens only indirect light.
- Depends on: `AmbientOcclusionResources`, `AmbientOcclusionPrograms`, `AmbientOcclusionPipelines`, `FullscreenPass`.

#### AmbientOcclusionPipelines
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionPipelines.java`
- Role: Pipeline definitions of every ambient occlusion pass, the uniform and sampler names they bind and the target formats, including one horizon search pipeline per method and quality (`horizonSearch`, with the slice and step counts and the visibility bitmask switch as shader defines) and `APPLY_INDIRECT` (`HELION_INDIRECT_SHARE`), compiled on its own so a missing variant falls back to the plain apply.
- Depends on: `HelionPipelines`.

#### AmbientOcclusionPrograms
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionPrograms.java`
- Role: The compiled pipelines for one frame; empty when any of them is missing.

#### AmbientOcclusionResources
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionResources.java`
- Role: Persistent ambient occlusion state owned by the core: the settings uniform ring, the fixed tuning values (falloff, distribution power, thin occluder compensation, final power, blur beta, max screen radius), the noise frame (advanced every frame through 64 frames while temporal anti-aliasing runs, 0 otherwise) and the one-time missing shader warning.
- Depends on: `UniformRing`.

#### AmbientOcclusionSettings
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionSettings.java`
- Role: Player settings: enabled, algorithm, quality, strength, radius, debug view.

#### AmbientOcclusionQuality
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionQuality.java`
- Role: Quality presets with slice count, steps per slice and denoise passes.

#### AmbientOcclusionAlgorithm
- Path: `src/main/java/com/aryston/helion/render/lighting/AmbientOcclusionAlgorithm.java`
- Role: Horizon method of the main pass: `GTAO` (two horizon angles per slice, occluders of infinite thickness) or `VISIBILITY_BITMASK` (32 sectors per slice, occluders of fixed thickness). Each has its own compiled horizon search pipeline.

#### DeferredLightingStage
- Path: `src/main/java/com/aryston/helion/render/lighting/DeferredLightingStage.java`
- Role: Lights terrain from the geometry buffer in one pass, `deferred_shading`: per pixel it computes the light (linear light in vanilla units without the vanilla cap and the daylight reference, `helion_deferred_light.glsl`), multiplies it with the albedo and writes the scene with fog and chunk fade-in, or the light-only view. It used to write the light into an RGBA16_FLOAT light buffer in a pass of its own first; computing it in place saves that full screen write and read. When the physical sky published a `SkyLight`, the pass uses the physical sky variant and also reads the geometry buffer normals and the sky light target; when `ShadowStage` also published a shadow mask, it uses its shadowed variant, which also reads the mask and darkens the shaded color after the vanilla brightness curve. Inactive when lighting is off, the geometry buffer was not written, the scene color is not RGBA8 or a shader failed to compile. With shadows, the shading pass also writes `helion:indirect_share` (R8, cleared to 1) as a second target and publishes it in `ShadowResults`.
- Depends on: `DeferredLightingResources`, `DeferredLightingPrograms`, `DeferredLightingPipelines`, `GeometryBuffer`, `FullscreenPass`.

#### DeferredLightingPipelines
- Path: `src/main/java/com/aryston/helion/render/lighting/DeferredLightingPipelines.java`
- Role: Pipeline definitions of the shading pass and the light-only view (`HELION_LIGHT_ONLY`), their physical sky variants (`HELION_PHYSICAL_SKY_LIGHT`, with the normal and sky light samplers), the shadowed variants (`HELION_PHYSICAL_SKY_LIGHT` and `HELION_SHADOWS`, with the normal, sky light and shadow mask samplers), the uniform and sampler names they bind and the target formats. The shadowed shading variants have a second R8 color target for the indirect share.
- Depends on: `HelionPipelines`.

#### DeferredLightingPrograms
- Path: `src/main/java/com/aryston/helion/render/lighting/DeferredLightingPrograms.java`
- Role: The compiled lighting pipelines for one frame; empty when any of them is missing.

#### DeferredLightingResources
- Path: `src/main/java/com/aryston/helion/render/lighting/DeferredLightingResources.java`
- Role: Persistent lighting state owned by the core: the `HelionLighting` uniform ring (light environment, intensities, the sun and moon directions of the physical sky, zero without it, and the sun and moon shadow strengths, zero without shadows) and the one-time missing shader warning.
- Depends on: `UniformRing`, `LightEnvironment`, `DeferredLightingSettings`, `SkyLight`, `ShadowLight`.

#### DeferredLightingSettings
- Path: `src/main/java/com/aryston/helion/render/lighting/DeferredLightingSettings.java`
- Role: Player settings: enabled, block light intensity, sky light intensity, light-only view. `DISABLED` for the foundation.

#### DynamicLight
- Path: `src/main/java/com/aryston/helion/render/lighting/DynamicLight.java`
- Role: One moving light of a frame: position relative to the camera and light level (0 to 15).

#### DynamicLightSettings
- Path: `src/main/java/com/aryston/helion/render/lighting/DynamicLightSettings.java`
- Role: Player setting: dynamic lights on or off. `DISABLED` for the foundation and the screen effects mode.

#### DynamicLightResources
- Path: `src/main/java/com/aryston/helion/render/lighting/DynamicLightResources.java`
- Role: The `HelionDynamicLights` uniform ring (up to `MAX_LIGHTS` positions with levels and the count), the shader define names and `upload`, which writes the frame's lights before the frame graph runs and returns nothing when there are no lights, so terrain keeps the vanilla pipelines. `finishFrame` rotates the ring after a frame that wrote it.
- Depends on: `UniformRing`, `DynamicLight`.

#### LightEnvironment
- Path: `src/main/java/com/aryston/helion/render/lighting/LightEnvironment.java`
- Role: Per-frame copy of the vanilla light map inputs (sky and block factors, night vision, darkness, boss fog darkening, brightness option, block light tint, sky light, ambient and night vision colors), filled by the integration layer from `LightmapRenderState`.
