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

## Shore Foam (2026-10-24)

Where the water is thinner than 0.35 blocks along the view ray (at walls, shores and blocks standing in water) the composite mixes in drifting foam: two layers of the caustic line pattern at 3 cells per block, thresholded against the shallowness, up to 0.75 of the sky ambient color. Caustics and foam now use the world position (camera position modulo 1024 in the composite uniform), so they no longer slide with the camera.

## Puddle Waves (2026-10-24)

While puddles are filling (puddle level above 0.05) the land cells of the field come alive too: the shore mask stores land as 0x80, water as 0xFF, and a cell only takes neighbors of its own class (others count as its own height, a reflecting wall), so lake waves and puddle waves never mix. Land cells damp at 0.93 per step, so a ring stays small. Entities standing on ground step into puddles (impulse up to 0.02 with their speed) and rain drops land on them like on water. The rain wetness pass samples the field over land (repeat, same world mapping) for the puddle normal instead of its procedural rings, at 3.4 times the field slope.

## Phase 1b: Shores (2026-10-24)

`WaterShoreMask` keeps one byte per block for the 128 x 128 block window (R8 texture, toroidal like the field) and rescans 16 rows of it per frame through `WaveSource.holdsWaves`: a column holds waves when the topmost motion blocking block (client height map) is water, when it is more than 8 blocks above the camera (caves, high roofs: unknown, so waves stay free), or when below a roof of at most 3 blocks and at most 12 blocks of air the first block is water. Cells of land columns stay at zero, so waves reflect from shores and never cross land into the next pond. About 3 to 6 block lookups per column, 2048 columns per frame.

The surface is still only tilted, not displaced: the simulated waves are a few hundredths of a block high, so a displaced near mesh would hardly change the silhouette while it would have to replace the vanilla water geometry. Geometric waves come with the ocean swell (phase 4).
