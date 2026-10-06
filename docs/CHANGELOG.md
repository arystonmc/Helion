# Helion Changelog

All notable changes to Helion. Each version section is ready to paste as an update note.

Format: sections `Added`, `Changed`, `Fixed`, `Removed`. One short plain English line per change.

## Unreleased

### Added
- Initial mod setup for Minecraft 26.3 on NeoForge
- Vulkan render core that draws the whole world through Helion's own stages with a picture identical to vanilla
- Own color and depth buffers for the world image
- Automatic fallback to vanilla rendering on OpenGL, with Sodium or Iris, or after a rendering error
- Toggle key (H) to switch between Helion and vanilla rendering
- Debug mode that shows every Helion value on screen, logs it as JSON and runs the parity check with the J key
- Settings screen with render core and debug mode options
- Ambient occlusion with quality presets, strength, radius and a shadow-only view
- Visibility bitmask ambient occlusion as an alternative method that keeps thin objects from casting dark halos
- Bloom around bright light sources that adapts to daylight, caves and night, with intensity, threshold and a glow-only view
- Neutral and Filmic tone mapping, exposure control and dithering against color banding
- Optional contrast adaptive sharpening with a strength setting
- English and Turkish translations
