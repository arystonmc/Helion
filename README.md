<p align="center">
  <img src="branding/logo.png" alt="Helion Logo" width="160">
</p>

<h1 align="center">Helion</h1>

<p align="center">
  <strong>Next-generation graphics enhancement for Minecraft, built natively on the Vulkan renderer.</strong>
</p>

<p align="center">
  <a href="https://modrinth.com/mod/helion"><img src="https://img.shields.io/badge/Download_on-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Download on Modrinth"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/helion"><img src="https://img.shields.io/badge/Download_on-CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white" alt="Download on CurseForge"></a>
  <a href="https://lureid.com/mods/helion"><img src="https://img.shields.io/badge/Website-Aryston-3C8527?style=for-the-badge" alt="Aryston website"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.3-5B8C5A?style=flat-square" alt="Minecraft 26.3">
  <img src="https://img.shields.io/badge/Loader-NeoForge-E87B35?style=flat-square" alt="NeoForge">
  <img src="https://img.shields.io/badge/Backend-Vulkan-ED2224?style=flat-square&logo=vulkan&logoColor=white" alt="Vulkan">
  <img src="https://img.shields.io/badge/License-Aryston%20Custom-3C8527?style=flat-square" alt="License">
</p>

---

## Overview

Unlike traditional shader packs that wrap around legacy OpenGL pipelines, **Helion** runs natively on Minecraft's Vulkan rendering architecture.

With all effects turned off, Helion matches vanilla Minecraft pixel-for-pixel. When enabled, Helion layers modern post-processing and rendering stages on top of the world: Ground Truth Ambient Occlusion, natural adaptive bloom, tone mapping, atmospheric lighting, and block shadows.

## Features

- 🌑 **Ground Truth Ambient Occlusion (GTAO):** Soft, natural contact shadows in corners, under stairs, fences, and around mobs. Light sources and emissive blocks stay bright.
- 💡 **Physically-Inspired Bloom:** Soft, cinematic glow around torches, lava, lanterns, and beacons that naturally adapts to daylight and darkness.
- 🎨 **PBR Neutral & Filmic Tone Mapping:** Preserves original Minecraft textures and colors while smoothly compressing bright highlights.
- 🗡️ **Contrast Adaptive Sharpening:** Restores crisp texture clarity on high-resolution displays without halos.
- 🌀 **Temporal Anti-Aliasing (Experimental):** Smooth block edges and calm, shimmer-free distant textures.
- ☀️ **Physical Atmosphere & Dynamic Shadows (Experimental):** Atmospheric scattering model for natural golden sunsets, paired with real sun and moon block shadows.
- 🔮 **HDR Lighting & Geometry Buffer (Experimental):** Unconstrained emissive lights with per-pixel normals and separate block and sky lighting.
- ⚙️ **Arkea-Powered Configuration:** Press **H** to toggle Helion instantly. Configure all settings in-game with Vanilla, Balanced, Quality and Experimental presets, before/after previews and a real-time GPU impact meter.
- 🛡️ **Safe by Design:** On OpenGL, with Sodium or Iris, or after a rendering error, Helion steps aside and the game keeps running with vanilla graphics.
- 🔍 **Debug Mode:** Every Helion value on screen and in `logs/helion-debug.jsonl`, GPU time per render stage, and a pixel-by-pixel parity check against vanilla.

For full technical and player details, see the [Helion Feature Guide](docs/FEATURES.md).

## Requirements

| Requirement | Supported Version |
|---|---|
| **Minecraft** | 26.3 |
| **Loader** | NeoForge 26.3.0.51-beta or newer |
| **Java** | 25 |
| **Graphics API** | Vulkan (`Video Settings` → `Graphics API` → `Prefer Vulkan`) |
| **Side** | Client only (safe to join any multiplayer server) |
| **Required Library** | [Arkea](https://modrinth.com/mod/arkea) 0.1 or newer ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/arkea)) |

## Compatibility & Troubleshooting

- **Server Safe:** 100% client-side. Safe to join any vanilla or modded server.
- **Sodium / Iris:** Helion automatically deactivates itself when Sodium or Iris is detected to prevent rendering conflicts.
- **OpenGL Fallback:** If launched under OpenGL, Helion remains inactive and provides instructions on enabling Vulkan.
- **Startup Crash Workaround:** If the game closes immediately on launch, set `earlyWindowControl = false` in `config/fml.toml`.
- **Compatibility List:** See which mods, shaders and resource packs work with Helion on the [compatibility page](https://lureid.com/mods/helion/compatibility).
- **Crash Helper:** Paste your crash report into the [crash helper](https://lureid.com/support/crash) to see known fixes.

## Community & Contributing

- **Website:** [lureid.com/mods/helion](https://lureid.com/mods/helion) with the [wiki](https://lureid.com/wiki/helion), [roadmap](https://lureid.com/roadmap?mod=helion) and [support](https://lureid.com/support?mod=helion).
- Found a bug or have a suggestion? Read [SUPPORT.md](SUPPORT.md) or open an issue on our [Issue Tracker](https://github.com/arystonmc/Helion/issues).
- Want to compile or contribute code? Read [CONTRIBUTING.md](CONTRIBUTING.md).

## License

This project is licensed under the [Aryston Source-Available License](LICENSE.md).  
Third-party notices and component licenses are detailed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

*Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.*
