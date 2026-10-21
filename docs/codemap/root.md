# Helion Code Map: `com.aryston.helion`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion`

#### Helion
- Path: `src/main/java/com/aryston/helion/Helion.java`
- Role: Client entry point. Registers the client config, the Arkea config screen (`HelionConfigScreen`) and all event listeners.
- Members: `MOD_ID`, `LOGGER`.
- Depends on: `HelionConfig`, `HelionConfigScreen`, `ClientEvents`.
