# Helion Architecture: Rain Wetness

Part of the [Helion architecture](../ARCHITECTURE.md).

On by default (`reflections.rainWetness`), in every preset but Vanilla. Needs the geometry buffer (Helion lighting). Rain darkens and wets surfaces open to the sky and fills puddles on flat blocks that hold water; puddles reflect the screen and the sky and ripple under the drops. The owner chose this mix over a thin water layer on every surface (2026-10-23): everything wet looks darker with a faint sheen, real reflections only in the puddles.

## Flow

1. `RainWetnessStage` (first after the clear stage, no pass) reads `WeatherState` from `VanillaWeatherSource`: the rain level, the game time and whether the camera biome rains (not snows) there. `RainWetness` follows it slowly: surfaces wet in 30 s and dry in 90 s, puddles fill in 90 s (never past the surface wetness) and dry in 240 s. Drying times scale with the downfall of the camera biome, from 0.4 times in dry biomes (desert, savanna) to 2 times in humid ones (jungle, swamp). A game time jump (`/time set`, a new world) skips the frame instead of snapping. The `Wetness` (surfaces, puddles, rain, seconds) is published while it is above 0.002.
2. `ScreenReflectionStage` runs while wetness is published even with screen space reflections off. Its second target gets the surface sheen of every geometry buffer pixel, composited by `ssr_polished` before translucent geometry with premultiplied alpha: `reflection * weight + scene * (1 - darkening) * (1 - weight)`.
3. `include/helion_wetness.glsl` decides per pixel:
   - Exposure: the sky light of the light target from 14/15 to 14.9/15, so places under a roof or under leaves (vanilla sky light below 15) stay dry, with smooth edges from smooth lighting; walls get 60 %. Glass roofs pass sky light 15, so the floor under them gets wet too.
   - Material, from the alpha of the light target (`VanillaGlossyCells`): ordinary (darkening 0.45, sheen 0.5, puddles), porous (sand, dirt, grass, leaves, wool and similar; crossed plant planes and entities: darkening 0.55, sheen 0.08, no puddles), glossy (darkening 0.25, sheen 0.6, puddles), dry (snow, magma: untouched).
   - Puddles: three octaves of value noise (scales 1/4, 1/2 and 1 per block, weights 0.55, 0.3, 0.15) over the world xz position (camera position modulo 1024, so the pattern repeats every 1024 blocks without precision loss), above a threshold that falls from 1.05 (none) to 0.55 (about a third of the floor) with the puddle level; only on faces pointing up. Puddles darken by 0.15 more, reflect with Schlick Fresnel from F0 0.3 (stylized; water is 0.02, but at that value a puddle near the camera reads as a shadow) and get rain drop rings: 9 cells of 1/3 block around the pixel, each with one ring of radius up to 0.3 blocks growing 1.3 times per second, tilting the normal by up to 0.2.
   - The reflection marches the screen only for puddles and polished blocks (48 steps like water); everything else reflects the sky in the reflected direction (physical sky without the sun and moon discs, whose edge needs screen derivatives that are undefined after the branches, lesson L-154; else the fog color) scaled by the square of the sky light.

## Wall Streaks (2026-10-24)

Walls that the rain reaches (not porous, normal below 0.5 up) get running water: value noise stretched along the wall (3 cells per block across, 0.35 per block down) that scrolls down at 0.9 per second, above 0.55 to 0.75 it darkens by 0.25 more and adds 0.7 sheen, scaled by the wetness and the rain level.

## Mud (2026-10-24)

Dirt, grass blocks, podzol, farmland, mud and mycelium are a fifth material class (muddy, 0.5625 in the light target alpha; the material mask now has five steps). They darken by 0.5 when wet with a sheen of 0.25, and in heavy rain (rain level above 0.4) they gather small mud puddles at 0.7 of the puddle level, so fields and paths turn dark and shiny with standing water.

## Wet Footprints (2026-10-24)

Players and mobs that come out of water leave wet prints for 20 seconds, one every 0.6 blocks, alternating feet 0.13 blocks to the side. Up to 32 prints live for 45 seconds and fade; each is an oval of 0.17 by 0.3 blocks that darkens the ground by 0.35 and adds a little sheen, only on flat surfaces that are not dry blocks. Visual test scene `footprints` walks an armor stand out of the pool (summoned in the water, then teleported 0.7 blocks every 3 ticks).

## Rainbow (2026-10-24)

After rain, while surfaces are still wet (`Wetness.surfaces`, passed as `LastRain` to the physical sky block and in `CloudOrigin.w` to the clouds), the rain has cleared (rain brightness above 0.85) and the sun is low (above the horizon, fading out by 0.7 of its height), the sky pass adds a rainbow 41.5 degrees around the antisolar point: a band of 2 times 2 degrees from red outside to violet inside, scaled by the sky brightness, with a faint brighter sky inside the arc. The cloud composite thins the clouds along the arc (up to 70 %), because real rainbows hang in front of the clouds. Visual test scene `rainbow` (300 ticks, facing west, wet but clear).

## Lightning (2026-10-24)

While `ClientLevel.skyFlashTime` runs (and the hide lightning flash option is off), the sky that wet surfaces, puddles and water reflect is five times as bright, so a flash lights up every wet surface for its few ticks.

## Measured (RTX 4050 Laptop, 1920 x 1080, visual test `rain_yard`, 2026-10-24)

- `ssr` 0.30 ms in rain with the whole frame wet (0.08 ms with only polished blocks), `ssr_polished` 0.03 ms.
- Rain fog and the rain streaks drawn later soften the darkening: a black composite at alpha 0.5 lowered the floor by 20 %, not 50 %, so the darkening values are higher than a physical wet albedo would need.

### Ray Traced Puddles (2026-10-24)

With ray traced reflections on, `rt_reflection` runs before `ssr` and also traces every geometry buffer pixel that is a puddle above 0.05 (the shader evaluates `helionWetSurface` with the published puddle level, so the mask matches the screen pass), starting at the scene depth with an up normal. `ssr` reads the traced image (flag in `FootprintCount.y`) and uses it where its own march misses instead of the sky, so puddles show what is off screen or hidden. Bindings 7 to 9 of `rtx/reflection.csh` are the scene depth, geometry normal and geometry light; without the geometry buffer the water color stands in and no puddles are traced.

### Water Film and Realistic Rain (2026-10-25)

The owner asked for the SEUS Renewed look while it rains: every surface that holds water gets a thin water layer, and puddles remain after the rain. `helionWetSurface` now returns a `film`: on faces pointing up of every material but porous (muddy at 0.4), it grows with the surface wetness from 0.5 to 1 and with the rain level up to 0.3, so it forms once the ground is half wet and drains within seconds after the rain stops, leaving the puddles (which dry over minutes). The film reflects like a thin puddle: Fresnel from F0 0.12, the screen space march (and the rain ripple normal), 0.08 extra darkening. Surfaces now wet in 60 s (was 30) and puddles fill in 150 s (was 90), so the ground darkens slowly and puddles appear late in the rain. Ray traced reflections still only trace puddles; the film falls back to the sky where the screen march misses.

Vanilla rain is replaced by texture overrides in `assets/minecraft/textures`: soft, faint, pale streaks at four times the vanilla resolution (the weather UVs are normalized, so a larger texture only sharpens it) and soft droplet splashes, which `WaterDropParticleMixin` blends (the owner found the first, alpha tested splashes broken and the first streaks too dense).

## Not Yet

- Rain exposure from a height map instead of the sky light, so glass roofs keep the floor dry and the biome is checked per pixel instead of at the camera.
- Wet entities beyond the porous darkening, drips from roof edges, puddles that evaporate unevenly.
