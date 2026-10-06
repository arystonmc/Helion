# Helion Features

What the latest version of Helion can do. Written for players and ready to reuse on websites and mod pages. This file always describes the present state; history belongs in `CHANGELOG.md`.

## Summary

Helion is a client-side graphics mod for Minecraft built on the new Vulkan renderer. The whole world is drawn through Helion's own render core, which matches vanilla pixel for pixel on its own, and Helion's graphics features are layered on top of it. The first ones are ambient occlusion, bloom with tone mapping and sharpening.

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
The world is rendered through Helion's own pipeline on Minecraft's Vulkan renderer. With all effects off, the result is pixel for pixel identical to vanilla.

- Terrain, entities, particles, sky, clouds, weather, transparency and outlines all pass through Helion's stages
- Helion draws into its own color and depth buffers before the image reaches the screen
- Works with both transparency modes, including improved transparency (F3 + X)
- Since: 0.1

### Ambient Occlusion
Soft, natural shadows wherever surfaces meet: in corners, under fences and stairs, around doors, beneath mobs and deep in caves.

- Based on GTAO, the technique used in modern games, adapted to Minecraft blocks
- Bright light sources, lava and glowing blocks stay bright; water, glass and particles are not darkened
- Fades out with fog and distance so far terrain stays clean
- Settings: on or off, method (GTAO or Visibility Bitmask, which lets light pass behind thin objects like fences, grass and torches), quality (Low, Medium, High, Ultra), strength, radius in blocks, and a view that shows only the shadows
- Since: 0.1

### Bloom and Tone Mapping
Bright light sources glow softly into the darkness while the colors you know stay the same.

- Lava, torches, glowstone, sea lanterns, beacons, the sun and other bright lights get a soft cinematic glow
- The glow adapts to its surroundings: at night and in caves lights glow clearly, in daylight only truly bright things glow, so snow and sand stay clean
- Tone mapping keeps vanilla colors and only rolls off the brightest highlights; a Filmic option adds film-like contrast, richer colors and whiter cores in bright glows
- Exposure control and invisible dithering that removes color banding in the sky
- Settings: bloom on or off, intensity, threshold, a view that shows only the glow, tone mapping (Neutral, Filmic, None), exposure and dithering
- Since: 0.1

### Sharpening
Crisper block textures without the halos of ordinary sharpening filters.

- Brings back fine texture detail that looks soft on large or high resolution screens
- Adapts to the picture: flat areas and edges that are already sharp are left alone
- Settings: sharpening on or off and its strength; off by default
- Since: 0.1

### Geometry Buffer (Experimental)
The foundation for Helion's own lighting, sky and shadows.

- Terrain is drawn by Helion's own shaders, which also record the surface direction, block light, sky light and base color of every pixel
- The picture stays exactly like vanilla; nothing changes until Helion's lighting is built on top of it
- Settings: on or off, and a view that shows each recorded value for checking; off by default while it is experimental
- Since: 0.1

### Safe by Design
Helion never stands between you and the game.

- On OpenGL, Helion stays inactive and tells you how to switch to Vulkan
- With Sodium or Iris installed, Helion stays inactive to avoid conflicts and tells you why
- If something goes wrong while rendering, Helion steps aside and the game keeps running with vanilla graphics
- Since: 0.1

### Instant Comparison
Switch between Helion and vanilla rendering at any moment.

- Press `H` to switch without restarting
- Since: 0.1

### Debug Mode
Everything Helion knows, on screen and in a log file, only when you ask for it.

- Shows every Helion value on screen without pressing F3: state, GPU and driver, resolution, camera, all effect settings, GPU time of every render stage, GPU memory per resource and culling statistics
- Writes the same values once per second as JSON lines to `logs/helion-debug.jsonl`, together with events such as switching Helion on or off and parity check results
- Press `J` in debug mode to compare a vanilla frame with a Helion frame pixel by pixel
- Off by default and costs nothing while off; turn it on in the settings
- Since: 0.1

### Settings
- Mods screen → Helion → Config: turn the render core and debug mode on or off, and adjust ambient occlusion, bloom, sharpening, tone mapping, exposure and the geometry buffer
- Since: 0.1
