# Helion Architecture: Rain Wetness

Part of the [Helion architecture](../ARCHITECTURE.md).

On by default (`reflections.rainWetness`), in every preset but Vanilla. Needs the geometry buffer (Helion lighting). Rain darkens and wets surfaces open to the sky and fills puddles on flat blocks that hold water; puddles reflect the screen and the sky and ripple under the drops. The owner chose this mix over a thin water layer on every surface (2026-10-23): everything wet looks darker with a faint sheen, real reflections only in the puddles.

## Flow

1. `RainWetnessStage` (first after the clear stage, no pass) reads `WeatherState` from `VanillaWeatherSource`: the rain level, the game time and whether the camera biome rains (not snows) there. `RainWetness` follows it slowly: surfaces wet in 30 s and dry in 90 s, puddles fill in 90 s (never past the surface wetness) and dry in 240 s. A game time jump (`/time set`, a new world) skips the frame instead of snapping. The `Wetness` (surfaces, puddles, rain, seconds) is published while it is above 0.002.
2. `ScreenReflectionStage` runs while wetness is published even with screen space reflections off. Its second target gets the surface sheen of every geometry buffer pixel, composited by `ssr_polished` before translucent geometry with premultiplied alpha: `reflection * weight + scene * (1 - darkening) * (1 - weight)`.
3. `include/helion_wetness.glsl` decides per pixel:
   - Exposure: the sky light of the light target from 14/15 to 14.9/15, so places under a roof or under leaves (vanilla sky light below 15) stay dry, with smooth edges from smooth lighting; walls get 60 %. Glass roofs pass sky light 15, so the floor under them gets wet too.
   - Material, from the alpha of the light target (`VanillaGlossyCells`): ordinary (darkening 0.45, sheen 0.5, puddles), porous (sand, dirt, grass, leaves, wool and similar; crossed plant planes and entities: darkening 0.55, sheen 0.08, no puddles), glossy (darkening 0.25, sheen 0.6, puddles), dry (snow, magma: untouched).
   - Puddles: three octaves of value noise (scales 1/4, 1/2 and 1 per block, weights 0.55, 0.3, 0.15) over the world xz position (camera position modulo 1024, so the pattern repeats every 1024 blocks without precision loss), above a threshold that falls from 1.05 (none) to 0.55 (about a third of the floor) with the puddle level; only on faces pointing up. Puddles darken by 0.15 more, reflect with Schlick Fresnel from F0 0.3 (stylized; water is 0.02, but at that value a puddle near the camera reads as a shadow) and get rain drop rings: 9 cells of 1/3 block around the pixel, each with one ring of radius up to 0.3 blocks growing 1.3 times per second, tilting the normal by up to 0.2.
   - The reflection marches the screen only for puddles and polished blocks (48 steps like water); everything else reflects the sky in the reflected direction (physical sky without the sun and moon discs, whose edge needs screen derivatives that are undefined after the branches, lesson L-154; else the fog color) scaled by the square of the sky light.

## Lightning (2026-10-24)

While `ClientLevel.skyFlashTime` runs (and the hide lightning flash option is off), the sky that wet surfaces, puddles and water reflect is five times as bright, so a flash lights up every wet surface for its few ticks.

## Measured (RTX 4050 Laptop, 1920 x 1080, visual test `rain_yard`, 2026-10-24)

- `ssr` 0.30 ms in rain with the whole frame wet (0.08 ms with only polished blocks), `ssr_polished` 0.03 ms.
- Rain fog and the rain streaks drawn later soften the darkening: a black composite at alpha 0.5 lowered the floor by 20 %, not 50 %, so the darkening values are higher than a physical wet albedo would need.

## Not Yet

- Ray traced puddle reflections (puddles reflect only what is on screen and the sky).
- Rain exposure from a height map instead of the sky light, so glass roofs keep the floor dry and the biome is checked per pixel instead of at the camera.
- Wet entities beyond the porous darkening, drips from roof edges, puddles that evaporate unevenly.
