# Contributing to Helion

Thank you for your interest in contributing to Helion.

## Building from Source

### Prerequisites
- Java 25 JDK
- Vulkan-compatible graphics hardware and drivers
- `glslangValidator` (optional, for offline shader verification; ships with the Vulkan SDK)

### Build Command

```bash
./gradlew build
```

Helion depends on Arkea. If an Arkea repository checkout exists next to Helion (`../arkea`), it is built directly from source; otherwise the published Arkea library artifact is downloaded. Use `-Parkea_path=<folder>` to specify an alternate checkout path.

The finished jar is written to `build/libs/`.

## Running the Client in Development

```bash
./gradlew runClient
```

The game client automatically runs with the `--graphicsBackend vulkan` argument.

### Troubleshooting Development Starts
If the client closes immediately upon startup, open `run/config/fml.toml` and verify `earlyWindowControl = false`. The NeoForge loading screen uses OpenGL, and handoff to Vulkan can fail on certain display configurations.

## Architecture and Guidelines
- Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) to understand rendering stages, resource lifecycles, and invariants before proposing changes.
- Read [docs/CODEMAP.md](docs/CODEMAP.md) for a map of every class. Update this document whenever adding, modifying, or removing files.
- Code style: clean, intention-revealing, no dead code, and no comments in code files.

## Pull Requests and License Agreement
All contributions submitted via Pull Requests are subject to the [Aryston Source-Available License](LICENSE.md). By submitting a pull request, you agree that your contribution may be licensed and distributed under these terms.

