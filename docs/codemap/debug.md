# Helion Code Map: `com.aryston.helion.debug`

Part of the [Helion code map](../CODEMAP.md). Every class of this package has its entry here and nowhere else (rule D1).

### `com.aryston.helion.debug`

#### HelionDebugSnapshot
- Path: `src/main/java/com/aryston/helion/debug/HelionDebugSnapshot.java`
- Role: Single source of every debug value, as a JSON object with one section per topic: `core` (version, active, enabled, debug mode, passive reason, parity running), `device`, `frame` (fps, window and scene size, color format, sky, improved transparency, ambient light, fog color), `camera`, `ambientOcclusion`, `image`, `bloom` (with the effective threshold), `sharpening`, `geometryBuffer`, `lighting`, `temporal`, `physicalSky`, `shadows`, `voxelLighting` (mode, pending sections, CPU time of the volume update per frame and of the last full fill), `gpu` (total), `gpuStages` (milliseconds per pass), `resources`, `resourceList` (MiB per label) and `frustum` (Helion versus vanilla visible sections). Numbers are rounded to three decimals.
- Depends on: `HelionRenderCore`, `ParityCheck`, `ImageResources`.

#### HelionDebugEntry
- Path: `src/main/java/com/aryston/helion/debug/HelionDebugEntry.java`
- Role: Debug screen entry registered as always on. Shows nothing unless debug mode is on; then shows every value of `HelionDebugSnapshot` on screen without F3.
- Depends on: `HelionDebugSnapshot`, `DebugOverlayLines`.
- Notes: the entry id is `helion:debug_mode`; players can still hide it in the vanilla debug options screen.

#### DebugOverlayLines
- Path: `src/main/java/com/aryston/helion/debug/DebugOverlayLines.java`
- Role: Turns a snapshot into screen lines: `Helion <section>: key=value, …` with at most four values per line and indented continuation lines.

#### HelionDebugLog
- Path: `src/main/java/com/aryston/helion/debug/HelionDebugLog.java`
- Role: JSON Lines writer for debug mode. Writes a `snapshot` line every 20 client ticks while a world is open and event lines (`toggle`, `passive`, `parity`) when they happen, each as `{"time", "type", "data"}`, to `logs/helion-debug.jsonl` in the game folder. The file is recreated on the first line of every game start. Does nothing while debug mode is off; stops writing after an I/O error and says so once in the game log.
- Depends on: `HelionDebugSnapshot`, `HelionRenderCore`.

#### VisualTest
- Path: `src/main/java/com/aryston/helion/debug/VisualTest.java`
- Role: Automated visual test that runs when the JVM property `helion.visualTest=true` is set in a development run; `helion.visualTest.fullSize=true` saves the screenshots at full window size instead of half; `helion.visualTest.scenes` and `helion.visualTest.variants` (names joined with `+`) limit it to some scenes and variants, and a shot waits while the voxel volume still has pending sections. Before preparing the world it checks that Helion extracted the sky state of the dimension although no vanilla frame was drawn yet (a `WARN` otherwise, because Helion would draw no sky), then prepares the world, then for every scene and variant sets the time, holds the camera, applies the variant's settings, waits for the frame to settle, saves a half resolution screenshot and logs the full debug snapshot as JSON. Debug mode is on during the test so GPU times are measured. The parity variant freezes ticks and runs `ParityCheck` instead, logging a pass or a `WARN` with the differing pixels. Teleports the camera back to its start position, so the save never keeps it in the test cave, then restores the settings and the HUD and closes the game cleanly.
- Depends on: `VisualTestScene`, `VisualTestVariant`, `VisualTestWorld`, `ParityCheck`, `HelionDebugSnapshot`, `HelionRenderCore`, `HelionConfig`, `LevelRendererAccessor`.
- Notes: changes blocks, time and game rules of the loaded world; run it on a copy of a save only.

#### GpuProfile
- Path: `src/main/java/com/aryston/helion/debug/GpuProfile.java`
- Role: Automated GPU profile that runs when the JVM property `helion.gpuProfile=true` is set in a development run: 15 s after joining it hides the HUD, holds the camera, then runs Helion off and every preset (`HelionPresetSettings`) for 5 s to settle and 10 s to measure, and logs one `Helion GPU profile <preset>` line each with the average frame rate, the sum of the measured GPU stage times and every stage time. Restores the settings and the HUD and closes the game.
- Depends on: `HelionPresetSettings`, `HelionRenderCore`, `HelionConfig`.

#### VisualTestScene
- Path: `src/main/java/com/aryston/helion/debug/VisualTestScene.java`
- Role: The photographed scenes: name, time of day, camera yaw and pitch. `dawn_sun` and `dawn_away` look toward and away from the rising sun, so a sky color that depends on the view direction shows up as a difference between them. `day_showcase` and `morning_showcase` show the platform at noon and in the morning, so shadows fall under the roof and beside the pillar. `dusk` (12700 ticks, facing the sun just after it set) checks that the orange sunset glow stays. `moonrise` (13500 ticks, facing east) shows the rising moon of the physical sky about 22 degrees above the horizon, `moon_overhead` (midnight, looking straight up) the moon in the middle of the frame. `water_noon`, `water_sunset` and `water_night` look north along the pool at 15 degrees down, for the water reflections. Rain scenes set the weather to rain and start fully wet: `rain_yard` (noon, 25 degrees down), `rain_yard_low` (noon, 8 degrees) and `rain_yard_dusk` (sunset, 8 degrees) look north over the rain yard. `rain_pool` (noon, 35 degrees) looks at the rain yard pool from close by; rain scenes drop a cobblestone item into it 18 ticks before the shot, so a splash shows. `water_mob` (noon) stands at the south end of the pool and looks north along the water at 8 degrees down, so mobs show in the reflection at a grazing angle. `water_close` (noon), `water_night_close` (midnight) look down at the pool at 45 degrees from its south end, `water_glint` (morning, facing east from its west side) puts the sun glint in the frame, and `water_under` looks up at the surface from the pool floor. `cave_corner` (midnight) moves the camera by its offset into the closed stone L corridor 20 blocks below, looking along the leg without the torch, for bounced light; `wall_leak` (midnight, facing east) puts the camera in a dark closed room whose one block east wall has four lights right behind it, so any light on that wall is a leak; `tinted_glass` (midnight, facing east, 20 degrees down) looks through a red and a blue stained glass wall at two sea lanterns; `savanna_room` (midnight, facing east, 30 degrees down) looks at a grass floor and an oak leaf wall lit by a torch, in a box turned savanna with `/fillbiome`; every other scene has offset zero and keeps the start position.

#### VisualTestVariant
- Path: `src/main/java/com/aryston/helion/debug/VisualTestVariant.java`
- Role: Render variants per scene: vanilla, None, Neutral, Filmic, bloom only, voxel light, voxel light low and voxel light high (the lighting variant with voxel lighting at Medium, Low and High, bounced light on), voxel bounce off (Medium without bounced light), screen reflections and hybrid reflections (the shadows variant with screen space reflections, without and with ray tracing), lighting and light only (geometry buffer and deferred lighting on, every other effect off), temporal (only temporal anti-aliasing on), physical sky (only the physical sky and volumetric clouds at Medium on), shadows and shadow light (geometry buffer, deferred lighting, physical sky, volumetric clouds and medium shadows on, the second as light-only view), all for comparison with vanilla, and parity, each turning the core on or off and setting default image settings.

#### VisualTestWorld
- Path: `src/main/java/com/aryston/helion/debug/VisualTestWorld.java`
- Role: Server commands of the visual test: freezes time and weather, stops mob spawning, switches to spectator, unless the JVM property `helion.visualTest.natural=true` asks for the untouched world around the player, clears the area around and in front of the player (so things built around the save spawn never block the view), builds a stone platform with light blocks in front of the player (with a torch next to the pillar, a soul lantern and a redstone torch for voxel lighting) plus a stone pillar and a floating stone roof that cast shadows away from any light block, summons a motionless armor stand and cow in the sun so entity shadows can be checked, and a second cow on the far rim of the pool for entity reflections, builds a rain yard east of the platform (stone bricks with a sand and a grass strip, oak planks, a lantern and a slab roof on a pillar, a two block deep pool under part of the roof, set to the plains biome so it rains there), sets the time per scene and freezes ticks for the parity check. A pool of water three blocks deep and ten blocks long, in a stone basin, runs along the left side of the platform under the stone roof and next to the glowstone and sea lantern. An oak leaf canopy sits on top of the pillar and an oak fence runs in front of the platform, so cutout and thin shadows are in view. A 3 x 2 patch of ice in the floor next to the cow a light blue stained glass column on the right edge, a plain glass column behind the cow and a 2 x 2 polished andesite patch in the floor carry the glossy reflections (the torch beside the pillar melts one ice block into water over time). East of and 20 blocks below the player it fills a 21 x 15 x 23 stone box (after removing every entity in it) and carves a 3 x 3 corridor with an L turn, a torch in one leg, plus two 3 block high rooms split by a one block wall with two torches, glowstone and a sea lantern against the wall on the far side, plus a room split by a half red, half blue stained glass wall with two sea lanterns behind it, and a savanna room (grass floor, oak leaf wall, torch, biome set with `fillbiome`); `placeCamera` teleports the camera into the other leg for `cave_corner` and back to its exact start position.

#### ParityCheck
- Path: `src/main/java/com/aryston/helion/debug/ParityCheck.java`
- Role: State machine behind the parity key and the visual test: renders three warm-up vanilla frames and three warm-up Helion frames, then captures a vanilla frame and the Helion frame right after it, compares when both readbacks finish. Both captures must come from the same client tick, because the torch flicker changes the light map every tick even with ticks frozen; if a tick passed in between, the pair is captured again (up to 20 times). `clientTick()` is called from the client tick event. Warm-up keeps cold first-use resources out of the comparison. Helion frames use `RenderSettings.foundation()`, so effects never count as differences. If a Helion frame is not rendered by Helion (core turned off or a render failure), the check aborts and reports that instead of comparing two vanilla frames. A failed comparison writes `ParityDifferenceImage` to `screenshots/` on the IO pool.

#### FrameCapture
- Path: `src/main/java/com/aryston/helion/debug/FrameCapture.java`
- Role: Copies a render target's color and depth into readback buffers and keeps the bytes and the frame size. `differencesTo` builds the difference image of two captures.

#### ParityResult
- Path: `src/main/java/com/aryston/helion/debug/ParityResult.java`
- Role: Pixel comparison result: total pixels, differing color pixels, largest channel difference, differing depth pixels, largest depth difference in steps of the depth format (units in the last place for `D32_FLOAT`), and how many color differences also differ in depth.

#### ParityDifferenceImage
- Path: `src/main/java/com/aryston/helion/debug/ParityDifferenceImage.java`
- Role: Writes `helion_parity_differences.png`: the vanilla frame darkened, pixels that differ only in color red, in color and depth yellow, only in depth blue.
- Depends on: `ParityResult`.
