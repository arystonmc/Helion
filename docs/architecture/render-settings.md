# Helion Architecture: Render Settings

Part of the [Helion architecture](../ARCHITECTURE.md).

## Render Settings

`RenderSettings` is an immutable snapshot of every effect setting, taken from the client config and carried in `FrameContext`. Stages decide in `isActive` whether they run. `RenderSettings.foundation()` turns every effect off and keeps the plain present copy, but keeps the geometry buffer when it is on (without its view), because the geometry buffer is part of the core and must stay identical to vanilla; the parity check renders its Helion frame with it, so the foundation can always be compared with vanilla while effects are on.
