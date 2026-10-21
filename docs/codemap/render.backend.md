# Helion Code Map: `com.aryston.helion.render.backend`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.render.backend`

#### GpuDeviceSummary
- Path: `src/main/java/com/aryston/helion/render/backend/GpuDeviceSummary.java`
- Role: Immutable copy of the renderpearl `DeviceInfo`: GPU name, vendor, backend, driver, depth range and timestamp period. `isVulkan()` is the backend check.
