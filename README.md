<p align="center">
  <img src="branding/logo.png" alt="Helion logo" width="160">
</p>

<h1 align="center">Helion</h1>

<p align="center">A client-side graphics mod for Minecraft, built on the Vulkan renderer.</p>

## Requirements

| | |
|---|---|
| Minecraft | 26.3 |
| Loader | NeoForge 26.3.0.51-beta or newer |
| Java | 25 |
| Graphics API | Vulkan (Video Settings → Graphics API → Prefer Vulkan) |
| Side | Client only |
| Library | [Arkea](https://github.com/lureidcom/Arkea) 0.1 or newer (required) |

## Features

See [docs/FEATURES.md](docs/FEATURES.md).

## Changelog

See [docs/CHANGELOG.md](docs/CHANGELOG.md).

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Troubleshooting

If the game closes right after start on Vulkan, open `config/fml.toml` in the game folder and set `earlyWindowControl = false`. The NeoForge loading screen uses OpenGL and its handoff to Vulkan can fail on some systems.

## Building

```
./gradlew build
```

Helion depends on Arkea. With an Arkea checkout next to Helion (`../arkea`) it is built from source; otherwise the published Arkea version is downloaded. Use `-Parkea_path=<folder>` for another location.

The jar is written to `build/libs/`.

## License

[LGPL-3.0-only](COPYING.LESSER)
