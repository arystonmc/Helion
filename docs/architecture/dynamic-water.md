# Helion Architecture: Dynamic Water

Part of the [Helion architecture](../ARCHITECTURE.md).

The owner wants water that behaves like in a graphics engine, not a shader pack (2026-10-23, plan "Helion Dinamik Su Planı" in the project files). The physics are visual only: water blocks, currents and swimming stay vanilla, so it works on every server and never changes gameplay. Part of realistic water (`reflections.realisticWater`).

## Phase 1a: Interactive Surface (2026-10-24)

1. `WaterWaveStage` (pass `water_waves`, before `water_surface`) simulates a height field of 512 x 512 cells, 4 per block, so 128 x 128 blocks around the camera. A texel holds the cell whose world coordinate is congruent to it modulo 512; when the window follows the camera, texels that now hold a new cell start calm, nothing is copied. The last 24 cells at the window edge damp waves out instead of reflecting them.
2. Fixed 60 Hz steps from a real time backlog, at most 4 per frame: `next = h + (h - previous) * 0.985 + 0.05 * (sum of the four neighbors - 4 h)`, so waves spread at about 3 blocks per second and fall to 40 % in a second.
3. Impulses (`VanillaWaveSource`): entities whose feet touch water and whose head is not deep under it push the surface down along their path with their horizontal speed (up to 0.03 per step) and make one splash in the frame they enter falling (up to 0.25). In rain a cell gets a drop 0.025 deep with a chance of 0.004 per step (about 4 drops per block and second) scaled by the rain level.
4. `water_surface` samples the field with repeat addressing (the chunk-wrapped wave position of the vertex shader repeats every 1024 blocks, a multiple of the 128 block window) and tilts the normal by its slope (0.85 of the true slope) within 44 to 60 blocks; the realistic water composite then refracts and reflects with that normal.
5. Measured (visual test `rain_pool`, RTX 4050 Laptop, 1080p, raining): `water_waves` 0.005 ms, `water_surface` 0.10 ms (0.03 to 0.04 ms before the extra target, refraction copy and wave sampling).

## Phase 1b: Shores (2026-10-24)

`WaterShoreMask` keeps one byte per block for the 128 x 128 block window (R8 texture, toroidal like the field) and rescans 16 rows of it per frame through `WaveSource.holdsWaves`: a column holds waves when the topmost motion blocking block (client height map) is water, when it is more than 8 blocks above the camera (caves, high roofs: unknown, so waves stay free), or when below a roof of at most 3 blocks and at most 12 blocks of air the first block is water. Cells of land columns stay at zero, so waves reflect from shores and never cross land into the next pond. About 3 to 6 block lookups per column, 2048 columns per frame.

The surface is still only tilted, not displaced: the simulated waves are a few hundredths of a block high, so a displaced near mesh would hardly change the silhouette while it would have to replace the vanilla water geometry. Geometric waves come with the ocean swell (phase 4).
