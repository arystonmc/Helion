package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.geometry.GlossyCells;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

final class VanillaGlossyCells {
    private static final int BASE_LEVEL = 0;
    private static final String TEXTURE_FOLDER = "block/";
    private static final List<String> FACE_SUFFIXES = List.of("", "_top", "_side", "_bottom", "_end");
    private static final List<String> GLOSSY_PREFIXES = List.of("polished_");
    private static final List<String> GLOSSY_PARTS = List.of("quartz", "prismarine", "glass");
    private static final List<String> GLOSSY_SUFFIXES = List.of("glazed_terracotta", "ice", "smooth_stone");
    private static final String ORE = "ore";

    private static @Nullable GlossyCells cached;

    private VanillaGlossyCells() {
    }

    static GlossyCells of(TextureAtlas atlas) {
        GpuTextureView view = atlas.getTextureView();
        GlossyCells current = cached;
        if (current != null && current.atlas() == view) {
            return current;
        }
        int width = Math.ceilDiv(view.getWidth(BASE_LEVEL), GlossyCells.CELL_SIZE);
        int height = Math.ceilDiv(view.getHeight(BASE_LEVEL), GlossyCells.CELL_SIZE);
        byte[] cells = new byte[width * height];
        for (Identifier block : BuiltInRegistries.BLOCK.keySet()) {
            if (glossy(block.getPath())) {
                FACE_SUFFIXES.forEach(suffix -> mark(atlas, block.withPath(TEXTURE_FOLDER + block.getPath() + suffix), cells, width));
            }
        }
        GlossyCells built = new GlossyCells(view, width, height, cells);
        cached = built;
        return built;
    }

    private static boolean glossy(String path) {
        if (path.contains(ORE)) {
            return false;
        }
        return GLOSSY_PREFIXES.stream().anyMatch(path::startsWith)
            || GLOSSY_PARTS.stream().anyMatch(path::contains)
            || GLOSSY_SUFFIXES.stream().anyMatch(path::endsWith);
    }

    private static void mark(TextureAtlas atlas, Identifier texture, byte[] cells, int width) {
        TextureAtlasSprite sprite = atlas.getSprite(texture);
        if (sprite == atlas.missingSprite()) {
            return;
        }
        int firstX = sprite.getX() / GlossyCells.CELL_SIZE;
        int firstY = sprite.getY() / GlossyCells.CELL_SIZE;
        int lastX = (sprite.getX() + sprite.contents().width() - 1) / GlossyCells.CELL_SIZE;
        int lastY = (sprite.getY() + sprite.contents().height() - 1) / GlossyCells.CELL_SIZE;
        for (int y = firstY; y <= lastY; y++) {
            for (int x = firstX; x <= lastX; x++) {
                cells[y * width + x] = GlossyCells.GLOSSY;
            }
        }
    }
}