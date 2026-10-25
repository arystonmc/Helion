# Helion Architecture: Water Optics

Part of the [Helion architecture](../ARCHITECTURE.md). Realistic water (`reflections.realisticWater`) after the SEUS Renewed comparison of 2026-10-25: the owner asked for open water, reflections and the underwater view to match SEUS Renewed and go beyond it. A first version with strong capillary waves and milky water looked stylized to the owner ("like Team Fortress 2 or Valorant"); they want clear water with detailed reflections, so the waves below are gentle and the water clear. SEUS Renewed was studied from its archive and screenshots for ideas only (rule T1); every technique below is Helion's own code with its own parameters.

## Capillary Waves and Filtered Roughness

1. `helionCapillarySlope` (`helion_water_waves.glsl`) adds four octaves of analytic gradient noise slopes on top of the swell: wavelengths of about 0.87, 0.48, 0.27 and 0.16 blocks, slope amplitudes 0.022 to 0.012, each turned, stretched across the wind (0.5 to 0.9) and drifting fast (0.4 to 1 noise cells per second). A large gust noise (about 22 blocks, drifting) scales them from 0.6 to 1.15, so calm and rippled patches alternate, and rain raises them by up to 60 %. They only wobble the reflections and split the sun into small glints; at 0.075 they broke the sky reflection into hard white patches that looked painted.
2. Each octave fades out once its wavelength covers fewer than about three pixels (the same footprint test as the swell), and the slope variance it loses (`helionCapillaryLostVariance`) becomes GGX roughness, `sqrt(2 variance)`. Distant water therefore turns into a glittering path instead of aliased sparkles or a flat mirror. The surface pass writes the roughness into the alpha of `helion:water_tint` (0.5 to 1 for 0 to 0.5 roughness, 0.25 marks falling water, 0 no water; `helion_water.glsl`).
3. The swell octaves are plain gradient noise again (the crested ridges are gone, they drew cartoon-like lines), and their slope is analytic (`helionNoiseSlope` through the turn, shear and stretch of each octave) instead of four height samples, which cut the hashes per water pixel from about 96 to 24. The far calm of the swell is weaker (60 % instead of 35 % beyond 80 blocks), because the filtered roughness now keeps distant water from sparkling.
4. `helion_noise_slope.glsl` holds the analytic gradient noise and its slope, shared by the capillaries, the caustics and the underwater light shafts.

## Reflections

1. Fresnel is Schlick with F0 0.02 (water, IOR 1.33), lowered at grazing angles by the roughness (`max(1 - roughness, F0)` as the grazing value), so rough distant water does not turn into a white mirror.
2. Sun and moon glints are a GGX specular term (Smith visibility, Fresnel on the half vector) with the combined roughness of a base 0.012 (about the size of the sun disc) and the filtered roughness, times an irradiance of 4 for the sun in the hue of the sky near it and 0.03 for the moon, capped at 16 before tone mapping. Close capillaries make sharp sparkles, distant water a broad glitter path, and the glint still vanishes where the reflected ray hit geometry.
3. Realistic water always runs the screen space reflection march for water pixels, also with the screen space reflections setting off (that setting still decides polished blocks), so trees, shores and mobs mirror in the water instead of only the sky. Measured cost: see below.
4. Screen space reflections start their march with a per pixel jitter of 0.5 to 1.5 times the first step, which temporal anti-aliasing turns into smooth reflections instead of steps.

## Clouds Beyond the Screen

`CloudStage` renders a cloud sky map whenever a water reflection setting is on (`cloud_sky_map`, 192 x 192 RGBA16_FLOAT, `clouds/march.fsh` with `HELION_CLOUD_SKY_MAP`): the upper hemisphere in an octahedral layout (`helion_cloud_sky_map.glsl`), marched with 60 % of the primary steps of the cloud quality, every frame. The water composite uses the on-screen cloud target where the reflected direction is on screen (faded over the outer 8 %) and the sky map elsewhere, so reflected clouds no longer stop at the screen edge. This works on every card; ray tracing does not help here, because the clouds are a volume, not geometry in the scene.

## Water Color and Clarity

`helion_water_optics.glsl` derives absorption and scattering per channel from the biome tint, the depth of the water column and the turbidity; the surface composite and the underwater pass both use it.

1. Pure water absorbs 0.36, 0.058 and 0.05 per block (red, green, blue; blue close to green, so deep and underwater views turn teal rather than navy). Dissolved organic matter adds (0.05, 0.13, 0.36) times its amount, suspended particles scatter (0.82, 1, 1.08) times theirs, slightly tinted toward the biome color.
2. The biome is read from the tint: swamp water (green above blue) gets more particles (0.15) and much organic matter (1.6, absorbing blue most), so it is dark, murky olive green; turquoise tints (warm and lukewarm oceans: green close to blue) are up to 75 % clearer; ordinary water has 0.025 particles in shallow water, falling to 0.015 between 4 and 16 blocks of depth, so lakes and rivers are clear turquoise over their bottom and deep ocean is dark blue.
3. Rain adds 0.09 particles (the published rain level).
4. From above, the bottom is attenuated along the view ray plus the depth of the column (light coming down), and the water adds `albedo * light * 0.4 * (1 - exp(-extinction * thickness))`, where the light is the sky ambient (35 % of its hue, the rest its luminance), 0.45 of the sun in its sky hue and forward sun scattering, so the color of the water comes from its own albedo and not from the blue sky. . Caustics on the bottom seen from above use the same `helion_caustics.glsl`, but only on bottom surfaces that face up: the facing comes from the bottom points of the neighbor pixels, so walls and steps under water get no vertical caustic streaks.
5. Caustics on the bottom seen from above use the same `helion_caustics.glsl`, but only on bottom surfaces that face up: the facing comes from the bottom points of the neighbor pixels, so walls and steps under water get no vertical caustic streaks.

## Under Water

`UnderwaterStage` copies the scene and redraws it (`water/underwater.fsh`, no blending), so the extinction is per channel.

1. `VanillaWeatherSource` reports an `UnderwaterView`: the camera sky light, the height of the water surface above the eye and of the floor below it (block scans of up to 48 blocks) and the biome water color at the camera. The vanilla underwater fog is pushed out to at least 384 blocks; Helion owns the underwater look.
2. Light shafts: 12 jittered steps along the view ray (up to 40 blocks). Each step takes the sun or moon light refracted into the water, attenuated by the water above the step along that direction, and a shaft pattern: one octave of ridged gradient noise (4 blocks across, sharpness 6) of the point where the refracted light ray entered the surface, wider and softer with depth, so beams of focused light slant down from the surface and drift with the waves. A Henyey-Greenstein phase (g 0.65) makes them bright toward the sun. Ambient sky light adds isotropic scattering, and the rest of the ray beyond 40 blocks is integrated in closed form.
3. Surfaces keep their lighting but their color shifts with the water above them (absorption normalized to the same luminance), and surfaces facing the refracted sun get caustics from `helion_caustics.glsl` at the depth of the point below the surface: three octaves of moving noise refracted and focused at 0.55 times the depth, from 0.2 to 6 times the light (octave heights 0.08, 0.02, 0.004).
4. Everything is attenuated by `exp(-extinction * distance)` up to the surface; the surface seen from below glows with the water's scattering color and shows the bright Snell window.

## Clouds

The clear weather cloud coverage is 0.46 (was 0.55), so open skies show more blue between the clouds; rain still raises it toward 0.85.

## Night

The eye barely sees into water at night, so the water body seen from above (bottom and scattering) keeps 45 % of its light without daylight and the glow of the surface seen from below 20 %; under water the moon lights the shafts with 0.025 to 0.05 per channel. Lamps still reflect through the screen space and traced reflections.

## Measured (RTX 4050 Laptop, 1920 x 1080, owner's settings, visual test, 2026-10-25)

- `cloud_sky_map` 0.18 ms.
- `water_surface` 0.4 to 1.0 ms with the lake or ocean filling the view (0.7 to 2.5 ms before the analytic swell slope).
- `ssr` 0.13 ms with little water on screen, 1.1 to 1.2 ms when water fills the view; it now always runs with realistic water.
- `underwater` 1.3 to 1.5 ms (about 3 ms with 20 steps and two shaft octaves).
