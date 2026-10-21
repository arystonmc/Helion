# Helion Code Map: `com.aryston.helion.render.resource`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.resource`

#### GpuResources
- Path: `src/main/java/com/aryston/helion/render/resource/GpuResources.java`
- Role: Registry of every GPU resource Helion owns. `release` frees through `RenderSystem.queueFencedTask` so in-flight frames are safe; `releaseAll` runs on shutdown. Feeds the resource values of the debug snapshot through `live()`.

#### UniformRing
- Path: `src/main/java/com/aryston/helion/render/resource/UniformRing.java`
- Role: Three mappable uniform buffers used in turn, each guarded by a fence so the CPU never overwrites data the GPU still reads. Registered in `GpuResources`.

#### PixelReadback
- Path: `src/main/java/com/aryston/helion/render/resource/PixelReadback.java`
- Role: Reads a small texture back to the CPU without stalling, through three mappable buffers used in turn, and decodes it with the given function (little endian); `latest` is the newest finished value, a few frames old. Used for the horizon color of the cloud fog and the metered luminance of automatic exposure.
- Depends on: `GpuResources`, `TrackedResource`.

#### TrackedResource
- Path: `src/main/java/com/aryston/helion/render/resource/TrackedResource.java`
- Role: Label, size in bytes and release action of one tracked resource.

#### SceneTargets
- Path: `src/main/java/com/aryston/helion/render/resource/SceneTargets.java`
- Role: Owns the persistent "Helion Scene" render target. Matches the main target's formats every frame and its size scaled by the render scale, resizes in place and recreates it on format change.
- Depends on: `GpuResources`.
