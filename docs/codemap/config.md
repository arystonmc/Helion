# Helion Code Map: `com.aryston.helion.config`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.config`

#### HelionConfig
- Path: `src/main/java/com/aryston/helion/config/HelionConfig.java`
- Role: Client config spec: `ENABLED` (render core on at startup), `DEBUG_MODE` (off by default), the `ambientOcclusion` section (enabled, algorithm, quality, strength, radius, debugView) and the `image` section (toneMapper, exposure, dither, the `bloom` subsection with enabled, intensity, threshold, debugView, and the `sharpening` subsection with enabled, strength), the `geometryBuffer` section (enabled, view) the `lighting` section (enabled, blockLightIntensity, skyLightIntensity, lightOnlyView), the `temporalAntiAliasing` section (enabled), the `physicalSky` section (enabled), the `shadows` section (enabled, quality) the `dynamicLights` section (enabled, on by default) and the `voxelLighting` section (mode, `OFF` by default). `renderSettings()` turns the config into a `RenderSettings` snapshot. The values and range limits are package visible for `HelionOptions`. The `rayTracing` section holds `enabled` (needs a game restart, because the device features are chosen at startup) and `waterReflections`. `rayTracing.shadows` turns the ray traced shadows on or off without a restart.
- Depends on: nothing inside the mod.

#### HelionConfigScreen
- Path: `src/main/java/com/aryston/helion/config/HelionConfigScreen.java`
- Role: Settings screen built with the Arkea config API, following the Helion design of the Arkea handoff: Helion logo, sidebar groups Configuration (General with presets and the render core switch, Rendering, Lighting, Effects) and System (Performance with the quality settings, Advanced with debug mode and collapsible debug views), GPU impact meter, Apply and Reset.
- Depends on: `HelionOptions`, `HelionPresets`, `HelionImpact`, `HelionSharing`.
- Notes: Needs Arkea (required dependency in `neoforge.mods.toml`).

#### HelionOptions
- Path: `src/main/java/com/aryston/helion/config/HelionOptions.java`
- Role: One Arkea `ConfigOption` per `HelionConfig` value with icon, short description, the existing config tooltip, BETA tag for experimental features, cost pips, before and after previews, and requirements (shadows need HDR lighting and the physical sky, HDR lighting needs the geometry buffer, every effect needs the render core). Ray tracing (cost 2, BETA, restart marker from the spec) and water reflections (requires ray tracing) sit in their own section on the lighting page. Ray traced shadows require ray tracing and sun shadows.

#### HelionPresets
- Path: `src/main/java/com/aryston/helion/config/HelionPresets.java`
- Role: Vanilla, Balanced, Quality and Experimental presets that set the effect switches and quality levels together. Every preset also sets edge smoothing (Quality only), automatic exposure (Experimental only) dynamic lights (all but Vanilla) and voxel lighting (`OFF` in every preset while it is in development); Vanilla also turns motion blur and depth of field off, the other presets leave those two as the player set them. Ray tracing is on only in Experimental; water reflections stay on in every preset. Rain wetness and realistic water are on in every preset but Vanilla.

#### HelionPresetSettings
- Path: `src/main/java/com/aryston/helion/config/HelionPresetSettings.java`
- Role: Turns every preset into the `RenderSettings` it would produce, by setting its values in memory, reading `HelionConfig.renderSettings()` and restoring every value afterwards without saving the config file. Used by `GpuProfile`.
- Depends on: `HelionOptions`, `HelionPresets`, `HelionConfig`.

#### HelionImpact
- Path: `src/main/java/com/aryston/helion/config/HelionImpact.java`
- Role: Sidebar meter that estimates the GPU impact from the costs of the effects that are on and their quality, using the values shown on screen.

#### HelionShareCode
- Path: `src/main/java/com/aryston/helion/config/HelionShareCode.java`
- Role: Settings code format: `HELION1:` followed by unpadded base64url of the UTF-8 JSON `{"v":1,"o":{...}}`, where `o` maps option keys to booleans, numbers or enum names. Decoding rejects other prefixes, versions and malformed data; reading a value clamps numbers to the option range and ignores values of the wrong type.
- Members: `encode`, `decode`, `write`, `read`.

#### HelionSharing
- Path: `src/main/java/com/aryston/helion/config/HelionSharing.java`
- Role: Copy and Paste buttons of the General page. Copy writes the applied value of every `HelionOptions` option to the clipboard as a settings code. Paste imports the known keys of a code from the clipboard, saves them at once, reopens the settings screen so it shows them and reports the result with an Arkea toast.
- Depends on: `HelionShareCode`, `HelionOptions`, `HelionConfigScreen`.
- Notes: The option key is the `ConfigOption` id without `helion.config.` (`HelionOptions.key`). The Arkea action API has no access to unapplied changes, so codes contain applied values and a paste replaces unapplied changes.
