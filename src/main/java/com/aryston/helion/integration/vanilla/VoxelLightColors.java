package com.aryston.helion.integration.vanilla;

import com.aryston.helion.Helion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class VoxelLightColors implements ResourceManagerReloadListener {
    public static final Identifier LISTENER = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "voxel_light_colors");
    private static final Identifier COLORS = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "voxel_light/colors.json");
    private static final String COLORS_KEY = "colors";
    private static final float[] WARM_WHITE = {1.0F, 0.8F, 0.6F};
    private static final float RED_WEIGHT = 0.2126F;
    private static final float GREEN_WEIGHT = 0.7152F;
    private static final float BLUE_WEIGHT = 0.0722F;
    private static final float CHANNEL_SCALE = 255.0F;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final double DISPLAY_GAMMA = 2.2;
    private static final List<String> FIRE_PARTS = List.of("torch", "lantern", "campfire", "fire", "candle", "furnace", "smoker");
    private static final List<String> STEADY_PARTS = List.of("redstone", "sea_lantern");

    private static volatile Map<Identifier, float[]> table = Map.of();
    private static volatile int version;

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        table = load(manager.getResource(COLORS));
        version++;
    }

    static int version() {
        return version;
    }

    static boolean flickers(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return FIRE_PARTS.stream().anyMatch(path::contains) && STEADY_PARTS.stream().noneMatch(path::contains);
    }

    static float[] color(BlockState state) {
        float[] listed = table.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (listed != null) {
            return normalized(listed);
        }
        MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (mapColor == MapColor.NONE) {
            return normalized(WARM_WHITE);
        }
        return normalized(new float[] {
            srgbToLinear((mapColor.col >> RED_SHIFT & CHANNEL_MASK) / CHANNEL_SCALE),
            srgbToLinear((mapColor.col >> GREEN_SHIFT & CHANNEL_MASK) / CHANNEL_SCALE),
            srgbToLinear((mapColor.col & CHANNEL_MASK) / CHANNEL_SCALE)
        });
    }

    private static Map<Identifier, float[]> load(Optional<Resource> resource) {
        if (resource.isEmpty()) {
            Helion.LOGGER.warn("Helion voxel light colors are missing; every light uses its map color");
            return Map.of();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject colors = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject(COLORS_KEY);
            Map<Identifier, float[]> loaded = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : colors.entrySet()) {
                JsonArray rgb = entry.getValue().getAsJsonArray();
                loaded.put(Identifier.parse(entry.getKey()), new float[] {rgb.get(RED).getAsFloat(), rgb.get(GREEN).getAsFloat(), rgb.get(BLUE).getAsFloat()});
            }
            return Map.copyOf(loaded);
        } catch (IOException | RuntimeException exception) {
            Helion.LOGGER.warn("Helion could not read the voxel light colors: {}", exception.getMessage());
            return Map.of();
        }
    }

    private static float[] normalized(float[] color) {
        float luminance = color[RED] * RED_WEIGHT + color[GREEN] * GREEN_WEIGHT + color[BLUE] * BLUE_WEIGHT;
        if (luminance <= 0.0F) {
            return normalized(WARM_WHITE);
        }
        return new float[] {color[RED] / luminance, color[GREEN] / luminance, color[BLUE] / luminance};
    }

    private static float srgbToLinear(float channel) {
        return (float) Math.pow(channel, DISPLAY_GAMMA);
    }
}
