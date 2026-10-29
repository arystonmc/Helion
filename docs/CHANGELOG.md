# Helion Changelog

All notable changes to Helion. Each version section is ready to paste as an update note.

Format: sections `Added`, `Changed`, `Fixed`, `Removed`. One short plain English line per change.

## Unreleased

### Added
- Path traced light: nights are dark, lit by the moon in its real strength instead of the vanilla night brightness, so torches and lanterns matter; torches and other light blocks shine stronger
- Plain glass and glass panes are clear real glass with a faint green tint, without the vanilla frame and streaks
- Rain on glass looks like a real wet window: clear drops of two sizes act as lenses with an upside down view, also of water behind the glass, and wavy streaks run down; only glass that rain can reach gets wet
- Path traced light: every light source shines in its own strong color, stained glass colors the light passing through it, also the sky light that lights a room through a stained window, and light fades smoothly and round instead of a hard bright patch
- Path traced light: mobs, dropped items and other entities no longer have the vanilla round black shadow under them; the ray traced shadows replace it
- Path traced light: polished blocks get glossy ray traced reflections, and iron, gold, netherite and copper blocks reflect like real metal
- Experimental path traced light (Ray Tracing section): Helion's new ray traced light, with ray traced sun and moon shadows with soft edges, colored block light with ray traced shadows from every light block, and light that bounces up to three times between surfaces and the sky, cleaned up by a denoiser that keeps edges and textures sharp
- Glass is smooth with an even tint and carries small rain drops of two sizes that act as tiny lenses, bead up and run down in thin trails
- Realistic glass: plain glass slightly bends the view behind it, stained glass tints it like real colored glass, and glass reflects the world
- Realistic water: waves roll in from open water and break on beaches with white foam that runs up the shore
- In third person the player, slimes and other see-through mobs mirror in the water and show through it
- Realistic water is clear, and its color and clarity follow the biome and the depth: murky green swamps, clear turquoise shallows and lakes, dark blue deep ocean
- Realistic water: fine ripples gently wobble the reflections and break the sun into small sparkles; far away they merge into a soft glittering sun path
- Realistic water mirrors the world on screen even with screen space reflections off, and reflects clouds beyond the edge of the screen
- Under water: a teal haze with soft light shafts slanting down from the surface, sharper caustics on the bottom and colors that fade to blue-green with depth
- Realistic water reflects the volumetric clouds
- Realistic water: large crested waves, a depth blurred bottom with a faint color fringe, sun glow through the water and rain gusts
- Lava: dark crust drifts over glowing lava with bright cracks, and the air shimmers above it
- Rain covers every flat surface that holds water with a thin reflecting water layer; puddles stay after the rain
- Realistic rain: thin faint streaks and small splashes instead of the blue vanilla rain
- Under water: longer view, sunlight only where it reaches, pitch dark at night, the sky through Snell's window and realistic caustics on the bottom
- Lakes and oceans roll with a real swell
- Experimental ray tracing: puddles reflect the world beyond the screen
- Dirt, grass and farmland turn muddy with small puddles in heavy rain
- Wet footprints after coming out of the water
- Under water: blue-green depth fog, light shafts from the surface and caustics on the bottom
- Rainbows opposite a low sun after rain
- Rivers carry their waves downstream and waterfalls get falling foam
- Rain runs down walls in glistening streaks
- Lightning flashes light up wet ground, puddles and water
- Realistic water reacts: players, mobs, boats and items leave wakes, falling things make splash rings and raindrops ripple the surface
- Experimental ray tracing: glass and ice reflect the world, reflected places in the shade of the sun are darker, and reflected mobs are shaded and flash red when hurt
- Realistic water: clear water with depth, refraction of the bottom, biome colors, fine waves and rain ripples instead of the flat water texture
- Rain wetness: rain darkens and wets the ground, puddles gather on stone, brick and wood, reflect the world and ripple with the drops
- Experimental volumetric clouds lit by the physical sky, with Low, Medium and High quality
- Light shafts: rays of sunlight and moonlight through clouds, leaves and gaps between blocks, with the physical sky
- Experimental screen space reflections: water reflects what is on screen on every graphics card, mobs and players included
- Ice, stained glass and other see-through blocks reflect their surroundings with the reflection settings
- Plain glass and polished blocks such as polished stone, quartz and prismarine reflect their surroundings faintly with screen space reflections
- Experimental ray tracing for RTX and similar cards: water reflects the world, the sky, the sun and the moon, with waves and darker deep water (in the Experimental preset, takes effect after a restart)
- Experimental ray tracing: mobs, players and dropped items show in water reflections, also when they are off screen

### Changed
- Automatic exposure adapts further in dark places: caves get up to one and a half stops brighter, while nights stay dark
- Path traced light denoising is faster: fewer filter passes now that the bounced light is smoother
- Path traced light runs faster: bounced light is kept per block face and reused across frames
- Volumetric clouds are a little thinner in clear weather
- Needs NeoForge 26.3.0.95 or newer (NeoForge for Minecraft 26.3 is no longer beta)
- Physical sky: a small sun of realistic size with a bright halo that turns orange and red as it sets
- Physical sky: a three-dimensional moon with real phase shadows, seas and craters, drawn larger than the sun
- Physical sky: more cinematic sunrises and sunsets with a horizon glow, a rose afterglow and a pink band opposite the sun, and calmer daytime colors
- Physical sky: a realistic night sky with round, twinkling stars, a faint Milky Way, planets, shooting stars and rare northern lights in snowy biomes, with settings for star density and the Milky Way
- Depth of field focuses on the sky when you look at it, so the sun, the moon and the stars stay sharp

### Fixed
- Rain drops on glass no longer turn into dark flickering specks at glancing angles and along the edge of a pane
- The floor under stained glass next to a light is no longer lit in white
- Light through stained glass no longer shows lines from the glass texture and the seams between blocks, and its color is stronger and even
- Water and glass reflections no longer show stained glass and water as solid vanilla blocks
- Glass underground and under roofs no longer gets rain drops
- Rooms full of lights no longer blind you: the exposure adapts down in very bright scenes
- Clouds show behind stained glass and glass
- Waterfalls and water next to glass no longer look like framed blue glass or solid teal blocks
- The world behind stained glass no longer looks like vanilla: water, fog, rain and light shafts show through it
- Distant water reflections no longer show frozen dotted edges in dry weather
- Realistic water: lamps and torches reflect visibly, the sun no longer glints behind mountains, the moon glint is brighter, vanilla water no longer shows along the shore, rain no longer makes the water tremble, and distant water no longer sparkles
- Realistic water: shore foam removed
- Realistic water no longer glows all over at night, shows a strong sun glint by day, has smoother waves without a checker pattern and looks right from under water
- Plain glass and polished blocks really reflect now, with a soft sky sheen when nothing else is reflected

### Removed
- The whole light and shadow system: ambient occlusion, HDR lighting, sun and moon shadows, dynamic lights, voxel lighting and ray traced shadows. It is being rebuilt from scratch as ray traced (path traced) light; until then the world keeps the vanilla light

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
