# Helion Architecture: Dynamic Water

Part of the [Helion architecture](../ARCHITECTURE.md).

The owner wants water that behaves like in a graphics engine, not a shader pack (2026-10-23, plan "Helion Dinamik Su Planı" in the project files). The physics are visual only: water blocks, currents and swimming stay vanilla, so it works on every server and never changes gameplay. Part of realistic water (`reflections.realisticWater`).

## Phase 1a: Interactive Surface (2026-10-24)

1. `WaterWaveStage` (pass `water_waves`, before `water_surface`) simulates a height field of 1024 x 1024 cells, 8 per block (512 x 512 and 4 per block before the feedback pass), so 128 x 128 blocks around the camera. A texel holds the cell whose world coordinate is congruent to it modulo 512; when the window follows the camera, texels that now hold a new cell start calm, nothing is copied. The last 24 cells at the window edge damp waves out instead of reflecting them.
2. Fixed 60 Hz steps from a real time backlog, at most 4 per frame: `next = h + (h - previous) * 0.985 + (0.16 + 0.04) * (sum of the four neighbors - 4 h)`, so waves spread at about 3 blocks per second and fall to 40 % in a second.
3. Impulses (`VanillaWaveSource`): entities whose feet touch water and whose head is not deep under it push the surface down along their path with their horizontal speed (up to 0.03 per step) and make one splash in the frame they enter falling (up to 0.25). In rain a cell gets a drop 0.025 deep with a chance of 0.004 per step (about 4 drops per block and second) scaled by the rain level.
4. `water_surface` samples the field with repeat addressing (the chunk-wrapped wave position of the vertex shader repeats every 1024 blocks, a multiple of the 128 block window) and tilts the normal by its slope (0.85 of the true slope) within 44 to 60 blocks; the realistic water composite then refracts and reflects with that normal.
5. Measured (visual test `rain_pool`, RTX 4050 Laptop, 1080p, raining): `water_waves` 0.005 ms, `water_surface` 0.10 ms (0.03 to 0.04 ms before the extra target, refraction copy and wave sampling).

## Owner Feedback Pass (2026-10-24)

The owner tested the first version at night and noon and asked for four fixes, with SEUS Renewed as the look to aim for:

1. Night: the whole surface glowed. The water composite reflected `helionSkyColorWithCelestials`, whose moon halo and disc are wide, so every pixel got some moonlight. Water now reflects the sky without discs (`helionSkyColor`) and adds a separate glint: a sharp and a soft specular lobe around the reflected sun and moon (powers 1200 and 90 for the sun, 4000 and 700 for the moon), only under open sky (sky light 0.85 to 1) and dimmed by rain.
2. Quality and the checker pattern: the eight sine waves interfered into a regular grid. They are replaced by five octaves of rotated gradient noise (`helion_water_waves.glsl`, wavelengths from about 3 blocks down to a sixth of a block, drifting), and the wave simulation now has 8 cells per block (1024 x 1024, same 128 block window), a little viscosity against grid noise and rain drops spread over the four neighbor cells.
3. A weak sun reflection: the old glint came only from the sky color. The sun glint is now its own term in the hue of the sky near the sun, intensity 3 before tone mapping, so it blooms on the water at noon and turns gold at sunset.
4. Under water the surface kept the vanilla look: the composite now shades surfaces seen from below: Snell's window (inside the critical angle, cosine 0.66, the world above shows through, refracted), total internal reflection outside it (deep water color), and extinction by distance. Caustics: two layers of warped interference lines move over the bottom of shallow water by day.

The water color moved toward the teal of the reference (biome tint mixed 55 % with 0.10, 0.55, 0.52; extinction 0.32, 0.075, 0.06 per block).

## Flowing Water and Waterfalls (2026-10-24)

The surface pass reads the flow direction of flowing water from the texture coordinate derivatives (the world direction in which the flow sprite's v grows, taken before any branch) and shifts the swell along it at 1.6 blocks per second, so rivers carry their waves downstream. Steep flowing faces (waterfalls) write 0.5 into the alpha of the tint target; the composite covers them with falling foam streaks (the caustic line pattern stretched along the fall, scrolling down at 4 blocks per second, up to 0.85 strength). The test world has a waterfall from a two block pillar into the far end of the pool.

## Under Water (2026-10-24)

`VanillaWeatherSource` reports when the camera is in water (`Camera.getFluidInCamera`); `RainWetnessStage`, now active every frame, publishes it in `WaterResults`, and `UnderwaterStage` (pass `underwater`, after transparent geometry, before temporal anti-aliasing) draws one fullscreen premultiplied blend over the scene: extinction by distance (0.16, 0.055, 0.045 per block, 48 blocks for the sky) toward the vanilla water fog color mixed 60 % with teal, darkened at night, plus moving caustics on surfaces below the eye within a few blocks and soft light shafts looking up, both scaled by daylight. Visual test scenes `water_under` (looking up) and `water_under_floor` (looking down from the pool floor).

## Storm Waves (2026-10-24)

The swell slope grows with the rain level up to 2.2 times, so water gets choppy in rain and thunderstorms and calms down when it clears.

## Shore Foam (2026-10-24)

Where the water is thinner than 0.35 blocks along the view ray (at walls, shores and blocks standing in water) the composite mixes in drifting foam: two layers of the caustic line pattern at 3 cells per block, thresholded against the shallowness, up to 0.75 of the sky ambient color. Caustics and foam now use the world position (camera position modulo 1024 in the composite uniform), so they no longer slide with the camera.

## Puddle Waves (2026-10-24)

While puddles are filling (puddle level above 0.05) the land cells of the field come alive too: the shore mask stores land as 0x80, water as 0xFF, and a cell only takes neighbors of its own class (others count as its own height, a reflecting wall), so lake waves and puddle waves never mix. Land cells damp at 0.93 per step, so a ring stays small. Entities standing on ground step into puddles (impulse up to 0.02 with their speed) and rain drops land on them like on water. The rain wetness pass samples the field over land (repeat, same world mapping) for the puddle normal instead of its procedural rings, at 3.4 times the field slope.

## Phase 1b: Shores (2026-10-24)

`WaterShoreMask` keeps one byte per block for the 128 x 128 block window (R8 texture, toroidal like the field) and rescans 16 rows of it per frame through `WaveSource.holdsWaves`: a column holds waves when the topmost motion blocking block (client height map) is water, when it is more than 8 blocks above the camera (caves, high roofs: unknown, so waves stay free), or when below a roof of at most 3 blocks and at most 12 blocks of air the first block is water. Cells of land columns stay at zero, so waves reflect from shores and never cross land into the next pond. About 3 to 6 block lookups per column, 2048 columns per frame.

The simulated waves only tilt the surface: they are a few hundredths of a block high, so displacing them would hardly change the silhouette.

## Ocean Swell (2026-10-24)

Open water now moves as geometry. The surface vertex shader lifts every water vertex (still or flowing sprite, so shared corners never crack) by `helionOceanHeight`: four directional waves with wavelengths of about 17, 11, 7.3 and 5.1 blocks and heights 0.11, 0.07, 0.045 and 0.028 (0.25 at most, 0.45 in a full storm; every water block is at least 0.89 deep, so a trough never reaches the bottom), moving with deep water dispersion (gravity 6 blocks per second squared) and phase-warped by gradient noise. Wave vectors are whole cycles per 1024 blocks and the noise repeats every 1024 blocks, so the chunk-wrapped wave position has no seam. The height is scaled by the openness from the shore mask (bound to the surface pass too): 0 within a block of the nearest land column, 1 from 4 blocks on, checked over the 8 x 8 columns around the vertex. So ponds, pools and shores stay flat and side faces (always next to land or air) never move. Beyond 48 to 60 blocks the shore mask window ends and the displacement fades out, while the fragment shader keeps the swell slope there (and the openness slope nearer), so distant open water still shows it in its normals.

Only `water_surface` is displaced; the vanilla water plane stays where it is. Every view ray that hits the flat plane also crosses the displaced surface (it fades to zero at the edges), so the realistic water composite still covers all water pixels, and crests that rise above the plane cover what lies behind them. Visual test `water_lake`, `water_lake_sunset`, `water_lake_storm`.

## Second Owner Feedback Pass (2026-10-25)

The owner tested in a real world (sea, night, rain) and reported the following; each item says what changed.

1. Lights barely showed in the sea: a torch reflected with Fresnel 0.02 is invisible. Where the reflection comes from geometry (screen space hit or traced hit) and is bright (largest channel from 0.6 to 1), the reflectance rises to at least 0.5, so lamps and lava leave a visible streak.
2. Vanilla water showed along the shore: the swell lowered the surface below the flat vanilla plane, which then showed through in the troughs. The swell now only lifts: the vertex shader adds `HELION_OCEAN_RISE` (0.16, the sum of the wave heights, now 0.07, 0.045, 0.03, 0.015) to the height, so the displaced surface is never below the vanilla plane and covers it from above and from below.
3. Shore foam is removed (the owner disliked it); the waterfall foam stays.
4. The sun glint showed while the sun was behind a mountain. The glint is now scaled by one minus the share of the reflection that hit geometry, so wherever the reflected ray meets terrain on screen or in the ray traced scene, there is no glint.
5. The moon glint was too faint: lobes widened (powers 1500 and 220, spread share 0.18) and the moon color raised to about 1.4 to 1.95.
6. Under water: the vanilla underwater fog end is three times as far (at least 72 blocks, `WaterFogEnvironmentMixin`), and the `underwater` pass owns visibility: extinction 0.085, 0.032, 0.026 per block, scattering toward a teal water color scaled by the sun reaching the camera (sky light at the camera minus the vanilla sky darkening, squared, so it is dark at night and in deep or covered water), plus 0.5 per block of extra extinction toward black in the dark, so night water is nearly black. Seen from below, Snell's window shows the physical sky with sun and moon where the refracted ray leaves the world (water thickness at its maximum), and the composite no longer extinguishes the surface a second time.
7. Caustics were random interference lines: they are now physically based. `helion_caustics.glsl` refracts light through a gradient noise surface (three rotated octaves, wavelengths 1, 1/2.1 and 1/4.3 blocks) and takes the inverse area change of the refracted grid (focus 0.5 times the water depth, 1.5 to 4 blocks) as the caustic light, capped at 3. From above it multiplies the refracted bottom (up to 0.75 by day, fading from 24 to 48 blocks); under water it adds the part above 1 in a pale sun color.
8. Rain made the water tremble: the storm multipliers on the swell are removed, rain ripples (the procedural rings) now always show on water in rain, and simulated rain drops on water are half as deep.
9. Quality: the swell octaves fade out with the pixel footprint (`fwidth` of the wave position) once a wavelength covers less than about three pixels, and the ripples fade the same way, so distant water no longer sparkles with aliased normals. All `sin`-based noise hashes (swell, caustics, wetness) use an integer PCG hash (`helion_hash.glsl`); the `sin` hash lost precision at large cell coordinates and drew straight lines.

Visual test scenes for this pass: `water_lake_night` (torch on a pillar), `water_lake_under`, `water_lake_under_floor`, `water_lake_under_night`, `grass_close`, `grass_close_morning`, `grass_close_rain`; the rain yard has a torch north of the pool, short grass and a dropped item in the grass.
