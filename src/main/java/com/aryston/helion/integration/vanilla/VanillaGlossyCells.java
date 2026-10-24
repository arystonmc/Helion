package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.geometry.GlossyCells;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class VanillaGlossyCells {
    private static final int BASE_LEVEL = 0;
    private static final String TEXTURE_FOLDER = "block/";
    private static final List<String> FACE_SUFFIXES = List.of("", "_top", "_side", "_bottom", "_end");
    private static final List<String> GLOSSY_PREFIXES = List.of("polished_");
    private static final List<String> GLOSSY_PARTS = List.of("quartz", "prismarine", "glass");
    private static final List<String> GLOSSY_SUFFIXES = List.of("glazed_terracotta", "ice", "smooth_stone");
    private static final String ORE = "ore";
    private static final List<String> DRY_PARTS = List.of("snow", "magma");
    private static final List<String> POROUS_PARTS = List.of(
        "sand", "gravel", "dirt", "grass_block", "podzol", "mycelium", "farmland", "wool", "carpet", "moss", "leaves", "hay_block", "sponge",
        "soul_soil", "nylium", "wart_block", "concrete_powder"
    );
    private static final List<String> MUDDY_PARTS = List.of("dirt", "grass_block", "podzol", "farmland", "mud", "mycelium");
    private static final List<String> NOT_MUDDY_PARTS = List.of("dirt_path", "brick", "packed");
    private static final List<String> HOLDS_WATER_PARTS = List.of("sandstone", "dirt_path", "mossy");

    private static final Logger LOGGER = LogUtils.getLogger();

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
            byte material = material(block.getPath());
            if (material != GlossyCells.ORDINARY) {
                FACE_SUFFIXES.forEach(suffix -> mark(atlas, block.withPath(TEXTURE_FOLDER + block.getPath() + suffix), cells, width, height, material));
            }
        }
        GlossyCells built = new GlossyCells(view, width, height, cells);
        LOGGER.info(
            "Helion material mask: {} glossy, {} muddy, {} porous and {} dry of {} block atlas cells",
            count(cells, GlossyCells.GLOSSY), count(cells, GlossyCells.MUDDY), count(cells, GlossyCells.POROUS), count(cells, GlossyCells.DRY), cells.length
        );
        cached = built;
        return built;
    }

    private static int count(byte[] cells, byte material) {
        int marked = 0;
        for (byte cell : cells) {
            if (cell == material) {
                marked++;
            }
        }
        return marked;
    }

    private static int cell(float coordinate, int cells) {
        return Math.clamp(Math.round(coordinate * cells), 0, cells - 1);
    }

    private static byte material(String path) {
        if (glossy(path)) {
            return GlossyCells.GLOSSY;
        }
        if (DRY_PARTS.stream().anyMatch(path::contains)) {
            return GlossyCells.DRY;
        }
        if (MUDDY_PARTS.stream().anyMatch(path::contains) && NOT_MUDDY_PARTS.stream().noneMatch(path::contains)) {
            return GlossyCells.MUDDY;
        }
        boolean porous = POROUS_PARTS.stream().anyMatch(path::contains) && HOLDS_WATER_PARTS.stream().noneMatch(path::contains);
        return porous ? GlossyCells.POROUS : GlossyCells.ORDINARY;
    }

    private static boolean glossy(String path) {
        if (path.contains(ORE)) {
            return false;
        }
        return GLOSSY_PREFIXES.stream().anyMatch(path::startsWith)
            || GLOSSY_PARTS.stream().anyMatch(path::contains)
            || GLOSSY_SUFFIXES.stream().anyMatch(path::endsWith);
    }

    private static void mark(TextureAtlas atlas, Identifier texture, byte[] cells, int width, int height, byte material) {
        TextureAtlasSprite sprite = atlas.getSprite(texture);
        if (sprite == atlas.missingSprite()) {
            return;
        }
        int firstX = cell(sprite.getU0(), width);
        int firstY = cell(sprite.getV0(), height);
        int lastX = Math.max(firstX, cell(sprite.getU1(), width) - 1);
        int lastY = Math.max(firstY, cell(sprite.getV1(), height) - 1);
        for (int y = firstY; y <= lastY; y++) {
            for (int x = firstX; x <= lastX; x++) {
                cells[y * width + x] = material;
            }
        }
    }
}