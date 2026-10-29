# Helion Features

What the latest version of Helion can do. Written for players and ready to reuse on websites and mod pages. This file always describes the present state; history belongs in `CHANGELOG.md`.

## Summary

Helion is a client-side graphics mod for Minecraft built on the new Vulkan renderer. The whole world is drawn through Helion's own render core, which matches vanilla pixel for pixel on its own, and Helion's graphics features are layered on top of it. Its features range from bloom, tone mapping and sharpening to a physical sky, volumetric clouds, realistic water and ray traced reflections. Helion's own light and shadow is being rebuilt as RTX path tracing; until then the world keeps the vanilla light.

## At a Glance

| | |
|---|---|
| Version | 1.0 |
| Minecraft | 26.3 |
| Loader | NeoForge |
| Graphics API | Vulkan |
| Side | Client only, safe to join any server |
| Requires | Arkea 1.0 or newer |
| Status | Released |

## Features

### Vulkan Render Core
The world is rendered through Helion's own pipeline on Minecraft's Vulkan renderer. With all effects off, the result is pixel for pixel identical to vanilla.

- Terrain, entities, particles, sky, clouds, weather, transparency and outlines all pass through Helion's stages
- Helion draws into its own color and depth buffers before the image reaches the screen
- Works with both transparency modes, including improved transparency (F3 + X)
- Since: 1.0

### Bloom and Tone Mapping
Bright light sources glow softly into the darkness while the colors you know stay the same.

- Lava, torches, glowstone, sea lanterns, beacons, the sun and other bright lights get a soft cinematic glow
- The glow adapts to its surroundings: at night and in caves lights glow clearly, in daylight only truly bright things glow, so snow and sand stay clean
- The moon shines softer than the sun and does not glow like it, so the two are easy to tell apart when both are in the sky
- Tone mapping keeps vanilla colors and only rolls off the brightest highlights; a Filmic option adds film-like contrast, richer colors and whiter cores in bright glows
- Exposure control and invisible dithering that removes color banding in the sky
- Settings: bloom on or off, intensity, threshold, a view that shows only the glow, tone mapping (Neutral, Filmic, None), exposure and dithering
- Since: 1.0

### Automatic Exposure (Experimental)
Your eyes adjust to the light, and now the picture does too.

- Caves, nights and terrain facing a low sun get gently brighter, like eyes getting used to the dark
- Daylight keeps the familiar vanilla brightness, and a bright sky never darkens the ground
- Changes happen smoothly over a second or two, faster when it gets bright than when it gets dark
- Only brightens, by at most half a stop, so daylight keeps the vanilla look and nights stay night
- Settings: on or off in the image settings; off by default while it is experimental
- Since: Unreleased

### Motion Blur (Experimental)
A soft, film-like blur when you turn or move quickly.

- The picture blurs along the direction the camera moves or turns, like a film camera, and stays perfectly sharp when you stand still
- Close objects keep crisp edges instead of smearing into what is behind them
- Looks the same at every frame rate, from 60 to 240 frames per second
- Settings: on or off and its strength in the effects settings; off by default while it is experimental
- Since: Unreleased

### Edge Smoothing
Softer block edges for almost no cost when temporal anti-aliasing is off.

- One quick filter pass that smooths jagged edges, like FXAA
- Used only while temporal anti-aliasing is off; the two never stack
- Settings: on or off in the image settings; off by default
- Since: Unreleased

### Depth of Field (Experimental)
A camera lens look: what you look at stays sharp, the rest softly blurs.

- Focus follows what is at the center of the screen, ignores the sky and tiny changes, and moves smoothly, faster toward something nearer
- The sharp area grows with the focus distance, so looking into the distance keeps the world around you sharp; blur in front and behind is limited and soft
- Close objects blur over the background naturally, and the background never leaves a halo around sharp things in front
- The blur fades in softly with distance, without a visible line between the sharp and the blurred area
- Settings: on or off and its strength in the effects settings; off by default while it is experimental
- Since: Unreleased

### Color Grading
Give the whole picture a mood with one click.

- Presets: Neutral, Warm, Cool, Vivid, Cinematic and Muted, applied like a film grade after everything else
- Neutral keeps the colors exactly as they are
- Settings: the preset in the image settings; Neutral by default
- Since: Unreleased

### Sharpening
Crisper block textures without the halos of ordinary sharpening filters.

- Brings back fine texture detail that looks soft on large or high resolution screens
- Adapts to the picture: flat areas and edges that are already sharp are left alone
- Settings: sharpening on or off and its strength; off by default
- Since: 1.0

### Geometry Buffer (Experimental)
The foundation for Helion's water, rain and reflection effects and its coming path traced light.

- Terrain, mobs, players, items and block entities are drawn by Helion's own shaders, which also record the surface direction, block light, sky light and base color of every pixel
- The picture stays exactly like vanilla on its own; Helion's effects build on top of it
- Settings: on or off, and a view that shows each recorded value for checking; off by default while it is experimental
- Since: 1.0

### Temporal Anti-Aliasing (Experimental)
Smooth block edges and calm, shimmer-free distant textures.

- Render scale: draw the world at 77, 67, 59 or 50 % of the screen resolution and let anti-aliasing rebuild the full picture over a few frames, like FSR or TSR, for a large frame rate boost; menus and the hand stay sharp at full resolution
- Moves the camera by less than a pixel every frame and blends each frame with the previous ones, like modern games do
- Works while you walk and turn; the picture gets a little softer, which the sharpening option makes up for
- Moving mobs, items and particles stay sharp and leave no trail behind them
- Settings: on or off; off by default while it is experimental
- Since: 1.0

### Physical Sky (Experimental)
A sky that behaves like real air: deep blue overhead, a bright glow around the sun and a moonlit night.

- The sky color comes from how sunlight and moonlight scatter in an Earth-like atmosphere instead of a single vanilla color, computed across the visible spectrum for natural blues, golden sunrises and red sunsets
- Distant terrain fades into the real sky color in every direction, so the horizon no longer changes color when you turn your head; clouds fade into the horizon color
- A small sun of realistic size that glows too brightly to show its edge at day, turning orange and red as it sets
- A three-dimensional moon, larger than the sun: its phases are real light and shadow on a sphere, with dark seas, craters that show relief along the shadow line a faint glow on its dark side and a soft halo of light around it; low moons turn golden. The stars keep their vanilla look
- Cinematic sunrises and sunsets: an ember to gold glow along the horizon, a rose afterglow after the sun has set and a pink band opposite the sun, in calm, balanced tones
- Terrain, fog and clouds take the tones of the sky, warm and rosy at sunrise and sunset
- A realistic night sky: round, colored stars that twinkle and appear one by one at dusk, a faint Milky Way, bright planets, an occasional shooting star and rare northern lights in snowy biomes; a bright moon washes out the faint stars and rain hides them
- Star density (Low, Medium, High) and the Milky Way can be set in the sky settings; presets leave both as you set them
- Nights stay a calm dark navy blue instead of turning grey or black
- Rain dims and greys the sky; the glow around the sun can bloom
- Works in the Overworld; other dimensions and views under water keep the vanilla sky
- Settings: on or off; off by default while it is experimental
- Since: 1.0

### Volumetric Clouds (Experimental)
Real 3D clouds that the sun shines through.

- Puffy cumulus clouds with flat bases and rounded tops replace the blocky vanilla clouds, high above the terrain
- Lit by the physical sky: bright silver edges toward the sun, warm orange undersides at sunset, soft shading inside and a faint moonlit glow at night
- They drift and slowly change shape with the wind, thicken and darken in rain, and fade gently into the distant sky
- Clear weather leaves plenty of blue sky between the clouds
- High quality adds a thin layer of high cirrus streaks
- Drawn at a lower resolution and smoothed over a few frames, so they cost little; Low, Medium and High trade detail for speed
- Needs the physical sky; turning clouds off in the vanilla video settings turns them off too, and other dimensions keep their own sky
- Settings: on or off next to the physical sky, quality on the Performance page; on at Medium in the Experimental preset, off elsewhere
- Since: Unreleased

### Light Shafts
Rays of sunlight and moonlight in the air.

- When you look toward the sun or the moon, rays stream through gaps in the volumetric clouds, between leaves and past the edges of hills and buildings
- Light passes through glass and water
- Colored by the sky: white at noon, golden at sunrise and sunset, soft and pale under the moon, and gone in rain
- Drawn at a low resolution and only while the sun or moon is in view, so they cost almost nothing
- Needs the physical sky
- Settings: on or off next to the physical sky; on in the Experimental preset, off elsewhere
- Since: Unreleased

### Screen Space Reflections (Experimental)
Water reflects what is on screen on every graphics card, mobs and players included.

- Reflections take the same light and colors as the world you see
- Mobs, players and items standing at the water show in it
- Ice, glass, stained glass and other see-through blocks reflect a little too, more at a glancing angle; where nothing on screen is reflected they show the sky, only as much as daylight reaches them
- Polished stone, quartz, prismarine, glazed terracotta, smooth stone and packed ice show a faint reflection of what is on screen, strongest when you look along the floor
- With ray tracing on, what is off screen still comes from ray tracing; without it, the sky is reflected there
- Settings: Screen Space Reflections on the Lighting page; on in the Experimental preset, off otherwise

### Realistic Water
Water looks like water instead of a flat blue texture.

- Clear water: the bottom shows through, bent by the waves, and fades into the water color with depth; lakes and shallows are clear turquoise, the deep ocean dark blue
- Bright sun and moon glints that move with the waves and turn gold at sunset; at night only the moonlit spot shines
- Realistic moving light patterns (caustics) on the bottom of shallow water by day, from sunlight bent by the waves
- Rivers carry their waves downstream, and waterfalls are thin, clear sheets with soft white streaks of foam running down
- Raindrops leave rings on the water without making it choppy
- Water reflects the clouds, also those outside the screen
- Fine ripples gently wobble the reflections and break the sun into small sparkles; far away they merge into a soft glittering sun path, and calm and rippled patches drift over larger water
- Large, soft natural waves that stretch the reflections; deep water blurs the bottom, and water glows when you look toward the sun across it
- Rain falls in gusts that sweep across water and wet ground
- From under water you look up through a bright window to the sky and the world above, and the surface around it mirrors the depths
- Under water you see much farther than in vanilla through a teal haze; soft light shafts start where sunlight enters the surface and slant down toward the sun, bright, sharp caustics dance on the bottom where sunlight reaches, colors fade to blue-green with depth, and at night or deep down it turns nearly black
- Lamps, torches and lava leave bright reflections on the water, and the sun only glints where it is not hidden behind terrain
- Lava: dark crust drifts slowly over the glowing lava with bright cracks between the crust plates, and the air above lava shimmers with heat
- The color and clarity follow the biome and the depth: murky green swamps, clear turquoise warm oceans, beaches and lakes, dark blue deep ocean; rain makes water a little cloudier
- Gentle waves with fine ripples, and rings where raindrops hit the water
- Open water rolls with a real swell that moves the surface itself, calm near the shore
- Waves roll in from open water toward beaches, grow and steepen in the shallows and break with white foam that runs up the shore; ponds, pools and deep walls stay calm
- The player in third person and see-through mobs such as slimes mirror in the water and show through it
- Glass: the world behind glass and stained glass keeps the Helion look, with its water, clouds, haze, rain and light shafts; glass looks smooth with an even tint instead of the pixel streaks of its texture, still shows a soft reflection, and in rain carries many small drops of two sizes that bead up, act as tiny lenses with a bright highlight, and run down the pane in thin trails; plain glass and glass panes are clear real glass with a faint green tint, without the vanilla frame and streaks, bend the view a little, stained glass colors it like real tinted glass (dark glass really darkens), and every pane reflects the world, more at a glancing angle
- Living water: players, mobs, boats and items leave wakes, things falling in make splash rings, and every raindrop leaves a ring that spreads and fades; waves bounce back from the shore
- Reflects the sky (with the sun and the moon under the physical sky) and the world on screen: trees, shores, buildings and mobs mirror in the water even with screen space reflections off; with ray tracing also what is off screen
- Works on every graphics card
- Settings: Realistic Water on or off on the Lighting page, on in every preset but Vanilla

### Rain Wetness
Rain makes the world wet instead of only falling past it.

- Ground, walls and roofs that the rain reaches turn darker and get a soft wet sheen; places under a roof or under trees stay dry
- Puddles slowly gather on flat stone, brick, wood and other surfaces that hold water, reflect the sky, lights and the world around them, and ripple where the raindrops land (with ray tracing they also reflect what is off screen); walking through a puddle sends out small rings
- Sand, gravel, leaves and wool only darken, they soak the water up; snow and magma stay dry
- Dirt, grass and farmland turn dark and muddy in the rain and gather small muddy puddles in heavy rain
- Surfaces slowly get wet over a minute of rain and dry over a minute and a half after it; while it rains every flat surface that holds water is covered by a thin reflecting water layer, and puddles fill late and stay for minutes after the rain, drying faster in deserts and savannas, slower in jungles and swamps
- Realistic rain: thin, faint streaks and small droplet splashes instead of the blue vanilla rain
- Only where it rains: snowy and dry biomes stay as they are
- Lightning flashes light up wet ground, puddles and water for a moment
- A rainbow appears opposite a low sun after the rain, in front of the clouds
- Players and mobs coming out of the water leave wet footprints that fade
- Water runs down walls in the rain in darker, glistening streaks
- Settings: Rain Wetness on or off on the Lighting page, on in every preset but Vanilla; needs the geometry buffer

### Ray Tracing (Experimental)
Uses the ray tracing hardware of RTX and similar graphics cards for what shaders cannot do well.

- Water reflects the blocks around it, the sky, the sun and the moon, with gentle animated waves
- Deep water turns dark blue-green so reflections stand out, while you still see the bottom of shallow water
- Reflections come from the blocks within about 64 blocks of you; farther away the sky is reflected
- Leaves, flowers and fences show their holes in reflections too
- Mobs, players, armor stands and dropped items show in ray traced water reflections, even when they are off screen, shaded like in the world and flashing red when hurt (without glowing eyes or armor shine)
- Glass and ice reflect the world with ray tracing too, and reflected places that lie in shadow are darker
- Needs a graphics card with hardware ray tracing; on other cards it stays off and nothing changes
- Path traced light (work in progress): Helion lights the world itself with ray tracing. The sun and the moon cast ray traced shadows with soft edges, light bounces up to three times between surfaces and the open sky lights the world, and torches, lanterns, glowstone and every other light block shine in their own color with ray traced shadows; a denoiser gathers the light over several frames and smooths it along surfaces, so the image stays clean and keeps sharp edges and texture detail. The bounced light is kept per block face and reused across frames, which makes the path traced light about a third cheaper per frame. Polished stone, quartz, prismarine, glazed terracotta and ice get glossy ray traced reflections of the world and the sky with a sun glint, and iron, gold, netherite and copper blocks are real metals that mirror their surroundings in their own color. Mobs, dropped items and other entities lose the vanilla round shadow under them; the traced shadows take its place. It needs the physical sky; without it the world keeps the vanilla light
- Settings: on or off (takes effect after restarting the game), path traced light on or off (needs the geometry buffer), water reflections on or off; on in the Experimental preset, off otherwise
- Since: Unreleased

### Safe by Design
Helion never stands between you and the game.

- On OpenGL, Helion stays inactive and tells you how to switch to Vulkan
- With Sodium installed, Helion runs in Sodium mode: Sodium draws the world and Helion adds its screen effects (tone mapping, bloom, exposure, sharpening, color grading, depth of field, edge smoothing); the sky, anti-aliasing and motion blur are off, and a short notice says so
- With Iris installed, Helion stays inactive to avoid conflicts and tells you why
- If something goes wrong while rendering, Helion steps aside and the game keeps running with vanilla graphics
- Since: 1.0

### Instant Comparison
Switch between Helion and vanilla rendering at any moment.

- Press `H` to switch without restarting
- Since: 1.0

### Debug Mode
Everything Helion knows, on screen and in a log file, only when you ask for it.

- Shows every Helion value on screen without pressing F3: state, GPU and driver, resolution, camera, all effect settings, GPU time of every render stage, GPU memory per resource and culling statistics
- Writes the same values once per second as JSON lines to `logs/helion-debug.jsonl`, together with events such as switching Helion on or off and parity check results
- Press `J` in debug mode to compare a vanilla frame with a Helion frame pixel by pixel. Use `/tick freeze` first; a failed check marks the differing pixels in `screenshots/helion_parity_differences.png` and warns when the game was not frozen
- Off by default and costs nothing while off; turn it on in the settings
- Since: 1.0

### Settings
A clear settings window in the Arkea style, with presets and a live estimate of the GPU cost.

- Pages for General, Rendering, Lighting, Effects, Performance and Advanced, with search across every setting
- Presets: Vanilla, Balanced, Quality and Experimental. Vanilla turns every effect off, including motion blur and depth of field; Quality adds edge smoothing, Experimental adds automatic exposure; motion blur and depth of field otherwise stay as you set them
- Share your settings: Copy code puts a settings code on the clipboard, Paste code applies a code a friend sent you
- Every setting has a short description, the full explanation on hover, and a reset button when it differs from its default
- Before and after previews for bloom and the physical sky
- Settings that need another one (volumetric clouds need the physical sky) stay disabled until it is on and say why
- Changes wait for Apply; leaving with unapplied changes asks first
- The GPU impact meter estimates the cost of the current settings
- Open it from Mods → Helion → Config or from the Arkea options window
- Needs the Arkea library; the game refuses to start without it
- Since: 1.0
