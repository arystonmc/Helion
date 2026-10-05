# Helion Changelog

All notable changes to Helion. Each version section is ready to paste as an update note.

Format: sections `Added`, `Changed`, `Fixed`, `Removed`. One short plain English line per change.

## Unreleased

### Added
- Initial mod setup for Minecraft 26.3 on NeoForge
- Vulkan render core that draws the whole world through Helion's own stages with a picture identical to vanilla
- Own color and depth buffers for the world image
- Automatic fallback to vanilla rendering on OpenGL, with Sodium or Iris, or after a rendering error
- Toggle key (H) and the commands /helion status, /helion toggle and /helion parity
- GPU time per render stage, active GPU and Helion memory use on the F3 screen
- Settings screen with render core and GPU timing options
- Ambient occlusion with quality presets, strength, radius and a shadow-only view
- English and Turkish translations
