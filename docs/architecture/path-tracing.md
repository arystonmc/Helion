# Path Tracing

Path traced lighting on ray tracing hardware, the direction the owner chose on 2026-10-27: build RTX path traced lighting directly instead of improving the voxel volume; cards without ray queries do not get Helion's lighting. Classes and passes are in the code map (`RtxIndirectStage`, `RtxBlockLightStage`, `DeferredLightingStage`).

## Frame

1. `voxel_light` still runs: its light list and grid are the block light registry, and its confidence target fades the volume edge.
2. `rt_block_light` traces block light and dynamic light visibility with hard shadows.
3. `rt_indirect` traces one indirect ray per half resolution pixel, accumulates it over time and filters it.
4. `deferred_shading` uses the path traced variant: ambient + (shadowed sun or moon + path traced indirect light), then the shaped block light.

## Units

The deferred shading works in vanilla light units, close to gamma space. The path tracer works in linear light where open flat ground under the full sky equals `pow(luminance(vanilla full sky light), 2.2)`; the sun, moon and dome of the sky light target are scaled together so their flat ground sum hits that value. Block light at a hit is the vanilla brightness of its level raised to 2.2, and the sky dome at a hit is scaled by the sky level raised to 2.2. Deferred shading adds the shadowed direct sky light in the same linear units to the path traced light and raises the sum to 1/2.2 before it joins the rest. Mixing the units makes the path traced light far too dark or too bright (lesson L-175).

## Ambient Occlusion

GTAO multiplies whatever the indirect share covers. With path tracing the indirect light already holds the occlusion of the real geometry, so the share only covers the ambient term and half of the path traced light (fine contact detail the half resolution trace misses). Block light and direct sun stay out of it; before this the floor around glowstone and other solid light blocks was darkened by the occlusion of the block itself (lesson L-176).

## Block Light Facing

The wrapped Lambert term of a block light aims at the face of the light block that looks the same way as the surface, not at its center. A floor next to a glowstone sees the top of the block, so it is lit like next to a lantern; aiming at the center made the light graze the floor.

## Measured (RTX 4050 Laptop, 1080p, visual test world, 2026-10-27)

`rt_indirect` 0.5 to 0.74 ms, `rt_block_light` 0.07 to 0.9 ms.

## Next

1. RTX required: without ray queries Helion's lighting stays off; drop the voxel lighting path and settings.
2. Soft block light shadows through a denoiser instead of jitter noise.
3. Path traced reflections on glossy surfaces.
