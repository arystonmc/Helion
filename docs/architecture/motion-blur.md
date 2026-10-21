# Helion Architecture: Motion Blur

Part of the [Helion architecture](../ARCHITECTURE.md).

## Motion Blur

Off by default (`image.motionBlur.enabled`, experimental). Camera motion blur after the approach of McGuire, Hennessy, Bukowski and Osman, "A Reconstruction Filter for Plausible Motion Blur" (I3D 2012), the method behind the motion blur of UE5 and most current engines; own implementation from the publication.

1. **Velocity.** Every pixel gets the screen distance it moved since the last frame from its depth and the camera reprojection of `TemporalResources` (the same matrix temporal anti-aliasing uses; with anti-aliasing off it is tracked without jitter). Entities have no velocity of their own yet, so the blur follows camera movement and turning only. The vector is scaled by the strength and by 1/30 s divided by the smoothed frame time (at most 8 frames), so the blur looks the same at 60 and at 240 frames per second, and limited to 48 pixels.
2. **`motion_blur_tiles`** keeps the longest vector of 16 samples per 16 x 16 pixel tile, **`motion_blur_neighbors`** the longest of the 3 x 3 tiles around, so blur reaches across the edges of moving areas.
3. **`motion_blur_gather`** skips pixels whose neighborhood moves less than half a pixel, so a still camera leaves the image untouched. Otherwise it takes 12 taps along the tile vector with interleaved gradient noise against banding and weights each tap by soft depth comparison, cone and cylinder terms of both speeds, so a sharp foreground edge never smears into the background behind it.
4. **`motion_blur_apply`** copies the result into the scene. The stage runs after temporal anti-aliasing, so the history stays sharp, and before exposure metering and bloom.

Measured on the visual test platform turning at 60° per second: ground edge detail drops by 11 % with the default strength 0.5, a still camera gives the same image as without blur, and the four passes cost about 0.1 ms at 854 x 480 (about 0.5 ms at 1080p).
