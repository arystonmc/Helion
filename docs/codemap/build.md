# Helion Code Map: Build Files

Part of the [Helion code map](../CODEMAP.md).

## Build Files

| File | Purpose |
|---|---|
| `build.gradle` | ModDevGradle setup, JUnit 5 for `src/test/java` with the Minecraft classpath of `main` and skipped or failed tests in the log, `client` run forced to Vulkan, metadata expansion, packs `branding/logo.png` into the jar as `helion.png`, jar name `helion-neoforge-<minecraft_version>-<version>.jar`. Depends on Arkea (`arkea_version`) from the Arkea Maven repository on GitHub. |
| `gradle.properties` | Single place for versions and mod metadata, including `arkea_version`, `arkea_version_range` and `arkea_path`. |
| `settings.gradle` | Plugin repositories, Java toolchain resolver, project name. When an Arkea checkout exists at `arkea_path` (`../arkea` in the workspace, `-Parkea_path=arkea` in CI) it is included as a composite build, so Helion compiles and runs against the local Arkea sources. |
| `.github/workflows/build.yml` | CI on every push and pull request: JDK 25, installs `glslangValidator` for the shader test, runs `./gradlew build` and uploads the jar. |
| `.github/workflows/release.yml` | On a `v*` tag: checks the tag against `mod_version`, builds against the published Arkea (no checkout), creates the GitHub release with the changelog section, and uploads to Modrinth and CurseForge with Arkea as a required dependency when their secrets exist. |
