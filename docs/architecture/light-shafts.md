# Helion Architecture: Light Shafts

Part of the [Helion architecture](../ARCHITECTURE.md).

## Light Shafts

On by default (`physicalSky.lightShafts`) wherever the physical sky runs, so in practice only in the Experimental preset; every other preset turns it off. Screen space light shafts (god rays) of the sun, or of the moon while the sun is below -0.05 elevation, after Mitchell ("Volumetric Light Scattering as a Post-Process", GPU Gems 3, 2007). `LightShaftStage` runs after `CloudStage` and before transparent geometry.

| Pass | Writes | Shader |
|---|---|---|
| `light_shaft_mask` | frame R8_UNORM at a quarter of the scene resolution | `light_shafts/mask.fsh`: the fraction of four scene depth taps that are sky, times a glow of cos^40 plus a 0.2 weighted cos^6 halo of the angle to the light (the halo lets gaps in clouds farther from the light start shafts), times the resolved cloud transmittance (`HELION_CLOUD_SHAFTS`), faded out below the horizon |
| `light_shaft_blur` | frame RGBA16_FLOAT at a quarter of the scene resolution | `light_shafts/blur.fsh`: 48 dithered mask samples toward the light's screen position over 85 % of the distance, each weighted 0.975 times the previous one, stopping at the screen edge |
| light shafts, drawn inside `transparent_geometry` | scene color, screen blended | `light_shafts/composite.fsh`: bilinear upsampling, times the sun or moon `SkyLight` texel (already colored by the atmosphere and dimmed by rain), Neutral encoding and dither |

- **Why screen space.** The mask is made from what the camera sees, so shafts come through every gap that shows sky: between leaves (cutout geometry has real holes in depth), past block edges and through the thin parts of volumetric clouds, with no shadow map. Shadows stay optional and the shafts need nothing but the physical sky.
- **Glass and water.** The mask reads scene depth before translucent terrain is drawn, so glass, stained glass and water let the light through.
- **Clouds.** With volumetric clouds the mask multiplies the sky by the transmittance of this frame's resolved clouds, so thick clouds block the light and thin edges and gaps let shafts through. Vanilla clouds are drawn later and do not block.
- **Cost control.** `LightShaftView` skips the whole stage when the light is behind the camera, more than 0.6 screens outside it, below the horizon or hidden by rain (rain brightness to the fourth power). Both passes run at a quarter of the resolution. Measured on the development GPU at 1080p during the visual test (2026-10-22): `light_shaft_mask` about 0.02 ms, `light_shaft_blur` about 0.04 ms; the composite is one fullscreen draw inside `transparent_geometry`.
- **Composite.** Screen blending (`ONE`, `ONE_MINUS_SRC_COLOR`) adds light to the display encoded scene without clipping it.
- **Uniform block `HelionLightShafts`** (`include/helion_light_shafts.glsl`): `ShaftInverseViewProjection` for view rays; `ShaftLight` xyz light direction, w strength (0.14 for the sun, 1.6 for the moon, whose light is about 200 times weaker); `ShaftScreen` xy screen position of the light, z visibility, w 1 for the sun and 0 for the moon; `ShaftTarget` size of the shaft target and its inverse; `ShaftMarch` samples, decay, streak length, glow sharpness; `ShaftScene` scene size and resolution divisor.
- Not part of it yet: shafts from a light outside the screen (that needs a volumetric march through the shadow map) and colored light through stained glass.
