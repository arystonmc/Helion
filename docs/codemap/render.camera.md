# Helion Code Map: `com.aryston.helion.render.camera`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.camera`

#### HelionCamera
- Path: `src/main/java/com/aryston/helion/render/camera/HelionCamera.java`
- Role: Immutable camera: world position, view rotation, the camera projection (used for culling), the level projection the frame is really drawn with (view bobbing, nausea distortion and the temporal jitter included), depth range. Provides `viewProjection()`, `levelViewProjection()`, `withLevelProjection` and `frustum()`.

#### HelionFrustum
- Path: `src/main/java/com/aryston/helion/render/camera/HelionFrustum.java`
- Role: Six-plane frustum built from the render projection. Handles both depth ranges correctly, including the far plane of `[0, 1]` reversed-Z projections. `intersects` tests world-space boxes.
