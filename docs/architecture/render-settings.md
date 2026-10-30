# Helion Architecture: Render Settings

Part of the [Helion architecture](../ARCHITECTURE.md).

## Render Settings

`RenderSettings` is an immutable snapshot of every effect setting, taken from the client config and carried in `FrameContext`. Stages decide in `isActive` whether they run. `RenderSettings.foundation()` turns every effect off and keeps the plain present copy, but keeps the geometry buffer when it is on (without its view), because the geometry buffer is part of the core and must stay identical to vanilla; the parity check renders its Helion frame with it, so the foundation can always be compared with vanilla while effects are on.

## Presets

`HelionPresets` sets groups of options at once in the settings window. Performance and Experimental share the physical sky, light shafts, clouds, the geometry buffer and temporal anti-aliasing at the Quality render scale (67 %, measured with path tracing on an RTX 4050 Laptop at 1080p: day 77 to 97, torch room 81 to 126, night 65 to 106 FPS); Performance uses Low clouds and no ray tracing or screen space reflections, so its GPU frame stays within about 3.5 ms, the share of a 200 FPS frame left for Helion next to other mods. The dynamic render scale and the target frame rate are left as the player set them by every preset.
