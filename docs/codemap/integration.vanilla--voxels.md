# Helion Code Map: `com.aryston.helion.integration.vanilla` (voxel lighting sources)

Part of the [Helion code map](../CODEMAP.md), split from [integration.vanilla.md](integration.vanilla.md) at the voxel lighting boundary. Every class listed here has its entry here and nowhere else (rule D1).

### `com.aryston.helion.integration.vanilla`

#### VanillaVoxelSource
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VanillaVoxelSource.java`
- Role: `VoxelSource` over the client level, on the render thread: reads the 4096 block states of one chunk section (missing chunks, sections outside the build height and all-air sections become empty bricks) through `VoxelBlockClasses` with biome tinted colors from `VoxelBiomeTints`, reports every block with light emission with its color, drains `VoxelSectionChanges`, and raises the level generation on a new level, reloaded light colors or an overflowed change queue so the volume rebuilds. `use(level, enabled)` also switches the change collection on or off. Owned by `VanillaFrameDriver`.
- Depends on: `VoxelBlockClasses`, `VoxelBiomeTints`, `VoxelSectionChanges`.

#### VoxelBiomeTints
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VoxelBiomeTints.java`
- Role: Biome tint of the color atlas entry for blocks with a vanilla tint source on layer 0 (grass, leaves, vines, ferns, sugar cane): the in-world tint color at the block position, scaled to the luminance of the map color, so bounced light off grass and leaves follows the biome. Blocks without a tint source and white tints (water) keep the map color. Caches the tint source per block state.

#### VoxelBlockClasses
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VoxelBlockClasses.java`
- Role: Cached `VoxelClass` byte, light color and flicker flag per block state: air and invisible blocks empty, solid render blocks and blocks with full light dampening opaque, water and blocks whose model has translucent material translucent, everything else partial, with the light emission in the high bits; stained glass and panes also get `TINTS_LIGHT` and their dye color, brightest channel scaled to 1, in place of the map color.

#### VoxelSectionChanges
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VoxelSectionChanges.java`
- Role: Bounded cross-thread queue of section nodes whose blocks or meshes changed, filled by `LevelExtractorMixin` and `RenderSectionMixin` only while voxel lighting is on, so it costs one volatile read when off. Overflow clears it and asks for a full rebuild.
- Depends on: nothing of this mod.

#### VoxelLightColors
- Path: `src/main/java/com/aryston/helion/integration/vanilla/VoxelLightColors.java`
- Role: Client reload listener for `assets/helion/voxel_light/colors.json` (block id to linear RGB; resource packs can replace it). `color(state)` returns the listed color, else the map color of the block, else warm white, normalized to luminance 1 so every light keeps the vanilla brightness. `flickers(state)` tells fire lights (ids with torch, lantern, campfire, fire, candle, furnace or smoker, not redstone or sea lantern). A reload raises its version so cached colors and the volume are rebuilt.
