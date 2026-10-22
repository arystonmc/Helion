# Helion Code Map: `com.aryston.helion.render.voxel`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.voxel`

#### VoxelLightingMode
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLightingMode.java`
- Role: `OFF` (no work at all) or `SHADER` (the voxel volume is kept up to date). A later ray traced mode gets its own value.

#### VoxelLightingSettings
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLightingSettings.java`
- Role: Player setting of voxel lighting, part of `RenderSettings`; `enabled()` is true for every mode but `OFF`.

#### VoxelClass
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelClass.java`
- Role: Byte per block of the volume: occupancy in bits 0 to 1 (`EMPTY`, `OPAQUE`, `PARTIAL`, `TRANSLUCENT`) and the light emission 0 to 15 in bits 4 to 7.

#### VoxelVolumeLayout
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelVolumeLayout.java`
- Role: Pure addressing math of the 128-block volume: 8 x 8 x 8 sections with ring (toroidal) slots, one 64 x 64 brick per section (16 slices of 16 x 16 laid 4 x 4) in a 2048 x 1024 atlas, block to section and local coordinates, and section keys packed like vanilla section nodes. Mirrored by `shaders/include/helion_voxel.glsl`.

#### VoxelVolumeWindow
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelVolumeWindow.java`
- Role: Which section each ring slot holds and which slots need rebuilding. Recentering marks only the slots whose section left the window; mesh changes mark their slot; `nextPending` returns the pending section nearest to the camera first.
- Depends on: `VoxelVolumeLayout`.

#### VoxelSource
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelSource.java`
- Role: Contract for reading the world: a level generation that changes with the level, the queue of changed section keys and `readSection`, which writes all 4096 bytes of one brick.

#### VoxelVolume
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelVolume.java`
- Role: The R8_UNORM atlas texture (registered in `GpuResources`) and its window. Once per frame, before the frame graph runs, applies changed sections, recenters on the camera and rebuilds pending sections nearest first within 1 ms of CPU time, each with one `writeToTexture` of its brick.
- Depends on: `VoxelVolumeWindow`, `VoxelSource`, `VoxelBuildStatistics`.

#### VoxelVolumeView
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelVolumeView.java`
- Role: The atlas texture view and the lowest block corner of the volume, for passes that read it.

#### VoxelBuildStatistics
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelBuildStatistics.java`
- Role: CPU time of the volume update per frame (last and running average) and of the last full fill, which it also logs as `Helion voxel volume filled` once all 512 sections are built.

#### VoxelLightingResources
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelLightingResources.java`
- Role: Owned by `HelionRenderCore`. `update` runs the volume while the mode is not `OFF` and releases it when the mode turns off; also keeps the uniform ring of the slice view.
- Depends on: `VoxelVolume`, `UniformRing`.

#### VoxelSlicePipelines
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelSlicePipelines.java`
- Role: The slice view pipeline (`voxel/slice.fsh`) with the `HelionVoxelSlice` uniform and `VoxelSampler`.
- Depends on: `HelionPipelines`.

#### VoxelSliceStage
- Path: `src/main/java/com/aryston/helion/render/voxel/VoxelSliceStage.java`
- Role: Last stage of a frame. With the geometry buffer view `VOXEL_SLICE` and a voxel volume, draws the volume at the camera's height as a top-down map (north up) in a square over the output: grey solid, green partial, blue translucent, yellow light sources, purple section borders, red the camera column. Timed as `voxel_slice`.
- Depends on: `VoxelLightingResources`, `VoxelSlicePipelines`, `FullscreenPass`.
