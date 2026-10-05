# Helion Code Map

Every class and source file of Helion with its purpose. Find the right file here before opening any code. Keep this file in sync with the code in the same commit (rule D1).

## Quick Lookup

| I want to change | Go to |
|---|---|
| Mod id, logger, startup | `Helion` |
| Mod name, version, loader versions, license, authors | `gradle.properties` |
| Mod list metadata and dependencies | `neoforge.mods.toml` |
| Mod logo | `branding/logo.png` |
| Build setup, run configurations | `build.gradle` |

## Overview

- Side: client only. The entry class is annotated with `dist = Dist.CLIENT`, so nothing loads on a dedicated server.
- `displayTest="IGNORE_ALL_VERSION"` lets players join servers that do not have Helion.
- Root package: `com.aryston.helion`.

## Classes

### `com.aryston.helion`

#### Helion
- Path: `src/main/java/com/aryston/helion/Helion.java`
- Role: Client entry point annotated with `@Mod`. Constructed by NeoForge once at client startup.
- Members:
  - `MOD_ID`: the mod id `helion`, used for every namespaced id.
  - `LOGGER`: shared logger for the whole mod.
- Depends on: nothing inside the mod.

## Source Files

| File | Purpose |
|---|---|
| `src/main/templates/META-INF/neoforge.mods.toml` | Mod metadata template. `${...}` values are filled from `gradle.properties` by the `generateModMetadata` task. |

## Build Files

| File | Purpose |
|---|---|
| `build.gradle` | ModDevGradle setup, `client` run configuration, metadata expansion, packs `branding/logo.png` into the jar as `helion.png`, jar name `helion-neoforge-<minecraft_version>-<version>.jar`. |
| `gradle.properties` | Single place for versions and mod metadata. |
| `settings.gradle` | Plugin repositories, Java toolchain resolver, project name. |
