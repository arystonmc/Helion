# Helion Architecture: Edge Smoothing

Part of the [Helion architecture](../ARCHITECTURE.md).

## Edge Smoothing

Off by default (`image.edgeSmoothing`). A light anti-aliasing filter for players who do not use temporal anti-aliasing, own implementation from the description of FXAA 3.11 (Lottes, NVIDIA 2011), no code taken: a pixel is skipped when the luma range of its four neighbors is below 1/8 of the brightest neighbor (at least 0.0312); otherwise the edge direction comes from the second derivatives across the 3 x 3 neighborhood, the stronger side picks the edge, a search of up to 10 steps each way along the edge finds its ends, and the pixel is resampled across the edge by how close it is to the nearer end, or by a sub-pixel term for single pixel features, whichever is larger. It runs on the display referred scene before motion blur, exposure and bloom, and it is skipped while temporal anti-aliasing runs, which already smooths edges. It costs about 0.1 ms at 854 x 480.
