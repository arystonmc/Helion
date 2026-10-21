# Helion Architecture: Goal of the Foundation

Part of the [Helion architecture](../ARCHITECTURE.md).

## Goal of the Foundation

Helion routes the vanilla world rendering of Minecraft 26.3 through its own render core on the Vulkan backend while the picture stays identical to vanilla. Every later graphics feature (lighting, shadows, atmosphere, post effects) plugs into this core as a stage or as a replacement source.
