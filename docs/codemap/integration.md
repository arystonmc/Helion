# Helion Code Map: `com.aryston.helion.integration`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.integration`

#### ClientEvents
- Path: `src/main/java/com/aryston/helion/integration/ClientEvents.java`
- Role: Wires every listener: client setup (compatibility check, passive notices), pipeline registration (including the geometry, entity geometry and water terrain pipelines), config loading, toggle and parity keys, notices and the debug log on client tick, the parity tick counter, debug log events for toggles and passive mode, shutdown.
- Depends on: `HelionRenderCore`, `HelionKeys`, `ParityControl`, `CompatibilityGuard`, `PassiveModeNotice`, `HelionDebugEntry`, `HelionDebugLog`, `VisualTest`, `LevelRenderHook`.

#### RayTracingStartup
- Path: `src/main/java/com/aryston/helion/integration/RayTracingStartup.java`
- Role: Reads `rayTracing.enabled` straight from `config/helion-client.toml` the first time a Vulkan mixin asks, because the device is created before NeoForge loads the client config, and hands it to `RayTracing.requestAtStartup`. A missing or unreadable file means off.
- Depends on: `RayTracing`.

#### ParityControl
- Path: `src/main/java/com/aryston/helion/integration/ParityControl.java`
- Role: Starts the parity check from the parity key (debug mode only), shows its result in chat and writes it to the debug log. A failed result points to the difference image and, when ticks were not frozen at the start, explains that clouds, particles and entities moved between the frames.
- Depends on: `ParityCheck`, `HelionDebugLog`, `HelionRenderCore`.

#### HelionKeys
- Path: `src/main/java/com/aryston/helion/integration/HelionKeys.java`
- Role: "Helion" key category, the toggle key (default `H`) and the parity key (default `J`, only acts in debug mode).

#### CompatibilityGuard
- Path: `src/main/java/com/aryston/helion/integration/CompatibilityGuard.java`
- Role: Lists installed renderer mods that make Helion passive (Iris) and tells whether Sodium is installed, which starts Sodium mode instead.

#### PassiveModeNotice
- Path: `src/main/java/com/aryston/helion/integration/PassiveModeNotice.java`
- Role: Queues passive reasons and shows them as toasts once the GUI exists; also shows the toggle toast and builds its enabled or disabled text. The queue is thread safe because the incompatible mod check runs on a parallel setup thread. Also shows the Sodium mode notice once the GUI exists (`queueSodiumMode`).
