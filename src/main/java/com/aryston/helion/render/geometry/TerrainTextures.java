package com.aryston.helion.render.geometry;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.joml.Vector4fc;

public record TerrainTextures(GpuTextureView atlas, GpuTextureView lightmap, Vector4fc waterStill, Vector4fc waterFlow, Vector4fc waterOverlay) {
}
