# Helion Changelog

All notable changes to Helion. Each version section is ready to paste as an update note.

Format: sections `Added`, `Changed`, `Fixed`, `Removed`. One short plain English line per change.

## Unreleased

### Added
- Experimental volumetric clouds lit by the physical sky, with Low, Medium and High quality
- Light shafts: rays of sunlight and moonlight through clouds, leaves and gaps between blocks, with the physical sky
- Experimental voxel lighting: colored block light with real shadows from walls and pillars, in the Experimental preset
- Experimental voxel lighting: held and burning lights are colored and cast shadows, and mobs and players cast shadows from lights
- Experimental screen space reflections: water reflects what is on screen on every graphics card, mobs and players included
- Ice, stained glass and other see-through blocks reflect their surroundings with the reflection settings
- Experimental voxel lighting: light through stained glass takes the color of the glass
- Plain glass and polished blocks such as polished stone, quartz and prismarine reflect their surroundings faintly with screen space reflections
- Experimental voxel lighting: bounced light carries torch and lava light around corners and into shaded corners in the color of the walls
- Mobs, players and items are lit like the blocks around them: colored voxel light, sun shadows and ambient occlusion now reach them
- Experimental ray tracing for RTX and similar cards: water reflects the world, the sky, the sun and the moon, with waves and darker deep water (in the Experimental preset, takes effect after a restart)
- Experimental ray traced shadows: exact soft sun and moon shadows near you, with leaves and fences casting shadows through their holes, and mobs and players casting exact shadows

### Changed
- Needs NeoForge 26.3.0.95 or newer (NeoForge for Minecraft 26.3 is no longer beta)
- Physical sky: a small sun of realistic size with a bright halo that turns orange and red as it sets
- Physical sky: a three-dimensional moon with real phase shadows, seas and craters, drawn larger than the sun
- Physical sky: more cinematic sunrises and sunsets with a horizon glow, a rose afterglow and a pink band opposite the sun, and calmer daytime colors
- Physical sky: terrain takes the warm tones of sunrise and sunset, and moonlit skies look a deeper blue
- Physical sky: a realistic night sky with round, twinkling stars, a faint Milky Way, planets, shooting stars and rare northern lights in snowy biomes, with settings for star density and the Milky Way
- Depth of field focuses on the sky when you look at it, so the sun, the moon and the stars stay sharp

### Fixed
- Experimental voxel lighting: several lights right behind a wall no longer shine through it
- Experimental voxel lighting: bounced light no longer makes rooms next to bright lights brighter than vanilla
- Experimental voxel lighting: High quality costs about a third more than Medium instead of three to four times as much
- Plain glass and polished blocks really reflect now, with a soft sky sheen when nothing else is reflected

## [1.0] - 2026-10-21

### Added
- Dynamic lights: torches, lanterns and other glowing items light their surroundings while held or dropped, and burning mobs light the area around them
- Edge smoothing: a light anti-aliasing filter for when temporal anti-aliasing is off
- Experimental depth of field that keeps the center of the screen in focus
- Color grading presets: Warm, Cool, Vivid, Cinematic and Muted
- Render scale with temporal upscaling for more frames per second at a lower internal resolution
- Shadows from mobs, players, chests, item frames and dropped items near you
- Settings codes: copy your Helion settings as a code and paste a code to apply someone else's
- Experimental automatic exposure that brightens caves, nights and backlit terrain like eyes adapting
- Experimental motion blur when the camera moves or turns, with a strength setting
- New settings window built with Arkea: pages, presets, search, previews and a GPU impact meter
- Initial mod setup for Minecraft 26.3 on NeoForge
- Vulkan render core that draws the whole world through Helion's own stages with a picture identical to vanilla
- Own color and depth buffers for the world image
- Automatic fallback to vanilla rendering on OpenGL, with Sodium or Iris, or after a rendering error
- Toggle key (H) to switch between Helion and vanilla rendering
- Debug mode that shows every Helion value on screen, logs it as JSON and runs the parity check with the J key
- Parity check image that marks every differing pixel, plus a warning when the game was not frozen during the check
- Parity check that compares two frames from the same tick, so flickering torch light no longer counts as a difference
- Settings screen with render core and debug mode options
- Ambient occlusion with quality presets, strength, radius and a shadow-only view
- Visibility bitmask ambient occlusion as an alternative method that keeps thin objects from casting dark halos
- Experimental geometry buffer: terrain drawn by Helion shaders that also record normals, block light, sky light and base color
- Experimental HDR lighting for terrain with separate block and sky light and intensity settings
- Experimental temporal anti-aliasing that smooths block edges and distant textures
- Experimental physical sky with spectral sky colors and fog that matches the sky in every direction
- Experimental sun and moon shadows from blocks with quality presets
- Bloom around bright light sources that adapts to daylight, caves and night, with intensity, threshold and a glow-only view
- Neutral and Filmic tone mapping, exposure control and dithering against color banding
- Optional contrast adaptive sharpening with a strength setting
- English and Turkish translations

### Changed
- Make ambient occlusion about a fifth faster with the same look
- Presets now also set edge smoothing, automatic exposure and dynamic lights, and the Vanilla preset turns motion blur and depth of field off too, so it really draws the vanilla picture
- Make HDR lighting faster by computing the light of every pixel in the shading pass instead of a pass of its own
- Make sun shadows faster by skipping the shadow lookup for terrain beyond the shadow distance
- Reduce short stutters with shadows on, especially while flying or at high render distance
- With Sodium installed Helion now keeps its screen effects (Sodium mode) instead of switching itself off
- Mobs and dropped items no longer show vanilla's round shadow spot under them while Helion's sun shadows are on
- Shadows move smoothly with the sun instead of jumping or sliding back and forth
- Make shadows faster by redrawing them only when the sun moved or you moved far enough, and the physical sky faster by redrawing its sky tables only when the sun, the moon or your height changed: about a quarter more frames per second with every effect on
- Depth of field blur fades in softly over a wide range of distances instead of starting at a sharp line
- Depth of field focuses more steadily, keeps a wider area sharp when you look far away and no longer leaves a halo around sharp objects
- Draw terrain from Aervium's compact chunk vertices when its compactVertices module is on
- Make sun and moon shadows much faster by redrawing far shadows less often
- Make ambient occlusion smoother while temporal anti-aliasing is on
- Adopt the Aryston Source-Available License
- Draw the moon dimmer than the sun so it no longer glows like a second sun

### Fixed
- Depth of field can now focus on distant terrain instead of always blurring land beyond 256 blocks
- Fix mobs and entities appearing semi-transparent from background indirect light and ambient occlusion leaking through them
- Fix shaded sides, plants and tree trunks turning blue or green under the physical sky light
- Fix grass and flowers covering the ground in flickering shadow speckles
- Fix stutter with shadows on by preparing only chunks that can cast shadows
- Fix tree leaf shadows shimmering while the sun moves
- Fix the trail fast mobs left behind with temporal anti-aliasing and keep moving mobs sharp
- Fix sunsets losing their orange glow, nights looking grey and a washed out sky with the physical sky
- Fix ambient occlusion and automatic exposure darkening sunlit ground below vanilla
