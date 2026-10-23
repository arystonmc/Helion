# Helion Code Map: `com.aryston.helion.render.voxel`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.voxel`

#### VoxelLightingMode
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLightingMode.java`
- Role: `OFF` (no work at all) or `SHADER` (the voxel volume is kept up to date and block light is traced through it). A later ray traced mode gets its own value.

#### VoxelLightingSettings
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLightingSettings.java`
- Role: Player settings of voxel lighting (mode and quality), part of `RenderSettings`; `enabled()` is true for every mode but `OFF`.

#### VoxelLightingQuality
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLightingQuality.java`
- Role: Lights traced per pixel and resolution divisor: Low 1 at half resolution, Medium 2 at half, High 4 at full.

#### VoxelClass
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelClass.java`
- Role: Byte per block of the volume: occupancy in bits 0 to 1 (`EMPTY`, `OPAQUE` = blocks light, `PARTIAL`, `TRANSLUCENT`) `TINTS_LIGHT` in bit 2 (stained glass), and the light emission 0 to 15 in bits 4 to 7.

#### VoxelCoarseLevel
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelCoarseLevel.java`
- Role: Coarse level of the volume: one byte per 4 x 4 x 4 blocks (`BLOCKED` when any block in it blocks or tints light), an 8 x 8 brick per section (4 slices of 4 x 4 laid 2 x 2) in a 256 x 128 atlas with the same slots as the block atlas. `build` derives it from a block brick.

#### VoxelLight, VoxelLightSink
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLight.java`, `src/main/java/com/aryston/helion/render/voxel/VoxelLightSink.java`
- Role: A light source block found while reading a section (world block position, level, linear color normalized to luminance 1) and the callback that receives them.

#### VoxelLightGrid
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLightGrid.java`
- Role: Pure CPU light grid: keeps the 1024 lights nearest to the camera and lists for every 8 x 8 x 8 cell of the volume (16 x 16 x 16 cells) up to 16 lights whose level reaches the cell by taxicab distance, keeping the strongest when more do.

#### VoxelVolumeLayout
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelVolumeLayout.java`
- Role: Pure addressing math of the 128-block volume: 8 x 8 x 8 sections with ring (toroidal) slots, one 64 x 64 brick per section (16 slices of 16 x 16 laid 4 x 4) in a 2048 x 1024 atlas, block to section and local coordinates, and section keys packed like vanilla section nodes. Mirrored by `shaders/include/helion_voxel.glsl`.

#### VoxelVolumeWindow
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelVolumeWindow.java`
- Role: Which section each ring slot holds and which slots need rebuilding. Recentering marks only the slots whose section left the window; mesh changes mark their slot; `nextPending` returns the pending section nearest to the camera first.
- Depends on: `VoxelVolumeLayout`.

#### VoxelSource
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelSource.java`
- Role: Contract for reading the world: a level generation that changes with the level or the light colors, the queue of changed section keys and `readSection`, which writes all 4096 bytes of one brick and reports its light sources to a `VoxelLightSink`.

#### VoxelVolume
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelVolume.java`
- Role: The block atlas, the coarse atlas and the 32 x 16 ready-slot table (all R8_UNORM) and its window. Once per frame, before the frame graph runs, applies changed sections, recenters on the camera and rebuilds pending sections nearest first within 1 ms of CPU time, each with one `writeToTexture` of its brick and one of its coarse brick; keeps the light sources of every slot, uploads the ready table when a slot changed state and rebuilds the light grid when the window moved or lights changed. Measures the frames from a section change to its upload. Hands every built brick to an optional `VoxelBrickSink`.
- Depends on: `VoxelVolumeWindow`, `VoxelSource`, `VoxelCoarseLevel`, `VoxelLightGrid`, `VoxelLightTextures`, `VoxelBuildStatistics`.

#### VoxelDynamicLight, VoxelOccluder, VoxelDynamicScene
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelDynamicLight.java`, `src/main/java/com/aryston/helion/render/voxel/VoxelOccluder.java`, `src/main/java/com/aryston/helion/render/voxel/VoxelDynamicScene.java`
- Role: The moving part of a frame in world coordinates: dynamic lights (position, level, linear color normalized to luminance 1, at most `MAX_LIGHTS` 16) and entity boxes that block light (at most `MAX_OCCLUDERS` 8).

#### VoxelBrickSink
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelBrickSink.java`
- Role: Receives every section brick right after it was read (4096 class bytes in brick layout), for consumers that need the volume on the CPU, such as the RTX spike.

#### VoxelTexture
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelTexture.java`
- Role: One sampled voxel lighting texture with its view, registered in `GpuResources`, with region and whole uploads.

#### VoxelLightTextures
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLightTextures.java`
- Role: Uploads a `VoxelLightGrid`: the light list as a 1024 x 2 RGBA32_FLOAT texture (row 0 position relative to the volume corner and level, row 1 color) and the grid as a 256 x 256 R16_UNORM texture (light index + 1 per cell slot, 0 for none).

#### VoxelLightPipelines, VoxelLightStage, VoxelLightResults
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLightPipelines.java`, `src/main/java/com/aryston/helion/render/voxel/VoxelLightStage.java`, `src/main/java/com/aryston/helion/render/voxel/VoxelLightResults.java`
- Role: The direct light pass `voxel_light` (`voxel/direct_light.fsh`), right after the shadow stage: per terrain pixel at the resolution of the quality it evaluates the lights of its grid cell with the vanilla falloff of one level per block and the dynamic lights of the frame (`HelionVoxelDynamic`), traces the strongest ones through the volume and against the entity boxes and writes the unshadowed colored block light and its visible share (RGBA16_FLOAT) and a confidence (R8: 0 near the volume edge or where a ray crossed a slot that is not ready). `VoxelLightResults` hands both targets to `DeferredLightingStage`.
- Depends on: `VoxelLightingResources`, `FullscreenPass`.

#### VoxelVolumeView
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelVolumeView.java`
- Role: The texture views of the volume (blocks, coarse level, ready slots, light list, light grid), its lowest block corner and the light count, for passes that read it.

#### VoxelBuildStatistics
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelBuildStatistics.java`
- Role: CPU time of the volume update per frame (last and running average), of the last full fill (also logged as `Helion voxel volume filled` once all 512 sections are built) and of the light grid builds, and the frames from a section change to its upload.

#### VoxelLightingResources
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLightingResources.java`
- Role: Owned by `HelionRenderCore`. `update` runs the volume while the mode is not `OFF` and releases it when the mode turns off; also keeps the uniform rings of the slice view and the direct light pass and warns once when the light pass did not compile. `observeBricks` registers a `VoxelBrickSink`. `useDynamicScene` takes the frame's `VoxelDynamicScene`, written relative to the volume corner into the `HelionVoxelDynamic` uniform ring for the light pass.
- Depends on: `VoxelVolume`, `UniformRing`.

#### VoxelSlicePipelines
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelSlicePipelines.java`
- Role: The slice view pipeline (`voxel/slice.fsh`) with the `HelionVoxelSlice` uniform and `VoxelSampler`.
- Depends on: `HelionPipelines`.

#### VoxelSliceStage
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelSliceStage.java`
- Role: Last stage of a frame. With the geometry buffer view `VOXEL_SLICE` and a voxel volume, draws the volume at the camera's height as a top-down map (north up) in a square over the output: grey solid, green partial, blue translucent, yellow light sources, purple section borders, red the camera column. Timed as `voxel_slice`.
- Depends on: `VoxelLightingResources`, `VoxelSlicePipelines`, `FullscreenPass`.

#### VoxelProbes, VoxelProbeStage
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelProbes.java`, `src/main/java/com/aryston/helion/render/voxel/VoxelProbeStage.java`
- Role: Bounced light (Phase 5). A 32 x 32 x 32 grid of probes, one per 4 x 4 x 4 cell of the voxel volume, stored as L1 spherical harmonics plus a placement in two sets of four RGBA16_FLOAT 256 x 128 targets. Pass `voxel_probes` (before `voxel_light`, only with `voxelLighting.bounce`) moves each probe to a free block of its cell, traces 4, 8 or 16 randomly rotated rays per probe through the volume, lights each hit with the block lights of its grid cell (one shadow ray to the brightest) plus the previous bounce of the probe owning the hit cell, times the block's map color from the voxel color atlas, and blends with the previous set (shifted when the volume moves). `voxel_light` gathers the 8 nearest visible probes into a bounce target (gain 4.2, alpha = share of the 30 % vanilla floor). With bounce off the probe targets are released and the pass does not run. See `docs/architecture/voxel-lighting.md`, Bounced Light.
