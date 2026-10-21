# Helion Architecture: Porting Checklist for a New Minecraft Version

Part of the [Helion architecture](../ARCHITECTURE.md).

## Porting Checklist for a New Minecraft Version

1. Diff `LevelRenderer.render`, `addSkyPass`, `addMainPass`, `executeSolid`, `executeClassicTransparency` against the previous version.
2. Update the classes in the Vanilla Coupling table.
3. Build, start the game with `--graphicsBackend vulkan` and turn on debug mode and run the parity check (`J`) in every scene of the Parity Scenes list below.
4. Record anything surprising as a lesson in the workspace `docs/lessons/`.
