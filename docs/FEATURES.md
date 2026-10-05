# Helion Features

What the latest version of Helion can do. Written for players and ready to reuse on websites and mod pages. This file always describes the present state; history belongs in `CHANGELOG.md`.

## Summary

Helion is a client-side graphics mod for Minecraft built on the new Vulkan renderer. Version 0.1 lays the foundation: the whole world is drawn through Helion's own render core while the picture stays exactly like vanilla, ready for the visual features that follow.

## At a Glance

| | |
|---|---|
| Version | 0.1 |
| Minecraft | 26.3 |
| Loader | NeoForge |
| Graphics API | Vulkan |
| Side | Client only, safe to join any server |
| Status | In development |

## Features

### Vulkan Render Core
The world is rendered through Helion's own pipeline on Minecraft's Vulkan renderer, pixel for pixel identical to vanilla.

- Terrain, entities, particles, sky, clouds, weather, transparency and outlines all pass through Helion's stages
- Helion draws into its own color and depth buffers before the image reaches the screen
- Works with both transparency modes, including improved transparency (F3 + X)
- Since: 0.1

### Safe by Design
Helion never stands between you and the game.

- On OpenGL, Helion stays inactive and tells you how to switch to Vulkan
- With Sodium or Iris installed, Helion stays inactive to avoid conflicts and tells you why
- If something goes wrong while rendering, Helion steps aside and the game keeps running with vanilla graphics
- Since: 0.1

### Instant Comparison
Switch between Helion and vanilla rendering at any moment.

- Press `H` or use `/helion toggle` to switch without restarting
- `/helion status` shows whether Helion is active and why not
- `/helion parity` compares a vanilla frame with a Helion frame pixel by pixel and reports the result
- Since: 0.1

### Performance Insight
See exactly where the GPU spends its time.

- The F3 screen shows the GPU time of every Helion render stage, the active GPU and graphics API, and the GPU memory Helion uses
- GPU time measurement can be turned off in the settings
- Since: 0.1

### Settings
- Mods screen → Helion → Config: turn the render core and GPU time measurement on or off
- Since: 0.1
