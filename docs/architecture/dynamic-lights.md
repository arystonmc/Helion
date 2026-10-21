# Helion Architecture: Dynamic Lights

Part of the [Helion architecture](../ARCHITECTURE.md).

## Dynamic Lights

On by default (`dynamicLights.enabled`), off in the Vanilla preset, the foundation and the screen effects mode. Glowing items held by mobs and players, dropped glowing items and burning entities light the terrain and the entities around them, the way LambDynamicLights does it, but without rebuilding a single chunk mesh.

1. **Sources.** `DynamicLightTracker` walks the rendered entities once per frame and keeps those within 64 blocks of the camera that give off light (`DynamicLightEmitters`: 15 while burning, the light emission of a held or dropped block item, 15 for a lava bucket), at their interpolated position 0.6 of their height up, nearest first.
2. **Terrain.** The nearest 16 go into the `HelionDynamicLights` uniform (positions relative to the camera, levels) before the frame graph runs. With at least one light, `VanillaTerrainSource` draws solid, cutout and sorted translucent terrain with copies of the vanilla pipelines whose vertex shader (`terrain/dynamic_light.vsh`, or `terrain/geometry.vsh` with `HELION_DYNAMIC_LIGHTS` for the geometry buffer) raises the block light of every vertex to the strongest `level - distance` of the lights before the vanilla light map lookup. Vanilla light falls off by one level per block, so a torch lights a sphere of about 14 blocks like a placed torch. Because only the light coordinate changes, the geometry buffer, HDR lighting, bloom and shadows see the dynamic light exactly like block light. Without lights the vanilla pipelines are used, so the cost is zero and the picture stays identical to vanilla.
3. **Entities.** `EntityRendererMixin` raises the light coordinates `EntityRenderer.getPackedLightCoords` returns with the same formula at the entity's light probe position, which covers mobs, players, items and the first-person hand (it uses the player's render state light).
4. **Limits.** Order independent transparency (improved transparency) draws translucent terrain through vanilla's OIT path, which keeps vanilla light; block entities (chests, signs) keep their vanilla light.
