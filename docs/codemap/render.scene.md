# Helion Code Map: `com.aryston.helion.render.scene`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.scene`

#### SceneSnapshot
- Path: `src/main/java/com/aryston/helion/render/scene/SceneSnapshot.java`
- Role: Immutable per-frame scene facts: fog color, sky visibility, transparency mode, internal and output size (`size()`), scene color format, smoothed ambient sky light at the camera (0 to 1) and the vanilla light map inputs (`LightEnvironment`).

#### ClearStage
- Path: `src/main/java/com/aryston/helion/render/scene/ClearStage.java`
- Role: Clears scene color to the fog color with zero alpha and depth to the reversed-Z far value `0.0`.
