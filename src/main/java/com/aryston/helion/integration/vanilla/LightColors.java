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

public final class LightColors implements ResourceManagerReloadListener {
    public static final Identifier LISTENER = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "light_colors");
    private static final Identifier COLORS = Identifier.fromNamespaceAndPath(Helion.MOD_ID, "light/colors.json");
    private static final String COLORS_KEY = "colors";
    private static final String STRENGTHS_KEY = "strengths";
    private static final float FULL_STRENGTH = 1.0F;
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
    private static final float WHITE_SHARE = 0.06F;
    private static final float CHANNEL_LIMIT = 3.5F;
    private static final float PACKED_RANGE = 4.0F;
    private static final int FLICKER_BIT = 1 << 24;
    private static final List<String> FIRE_PARTS = List.of("torch", "lantern", "campfire", "fire", "candle", "furnace", "smoker");
    private static final List<String> STEADY_PARTS = List.of("redstone", "sea_lantern");

    private static volatile Table table = new Table(Map.of(), Map.of());

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        table = load(manager.getResource(COLORS));
    }

    static int packed(BlockState state) {
        float[] color = color(state);
        int packed = channel(color[RED]) << RED_SHIFT | channel(color[GREEN]) << GREEN_SHIFT | channel(color[BLUE]);
        return flickers(state) ? packed | FLICKER_BIT : packed;
    }

    private static int channel(float value) {
        return Math.clamp(Math.round(value / PACKED_RANGE * CHANNEL_SCALE), 0, CHANNEL_MASK);
    }

    static float strength(BlockState state) {
        return table.strengths().getOrDefault(BuiltInRegistries.BLOCK.getKey(state.getBlock()), FULL_STRENGTH);
    }

    private static boolean flickers(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return FIRE_PARTS.stream().anyMatch(path::contains) && STEADY_PARTS.stream().noneMatch(path::contains);
    }

    private static float[] color(BlockState state) {
        float[] listed = table.colors().get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
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

    private static Table load(Optional<Resource> resource) {
        if (resource.isEmpty()) {
            Helion.LOGGER.warn("Helion light colors are missing; every light uses its map color");
            return new Table(Map.of(), Map.of());
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<Identifier, float[]> colors = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(COLORS_KEY).entrySet()) {
                JsonArray rgb = entry.getValue().getAsJsonArray();
                colors.put(Identifier.parse(entry.getKey()), new float[] {rgb.get(RED).getAsFloat(), rgb.get(GREEN).getAsFloat(), rgb.get(BLUE).getAsFloat()});
            }
            Map<Identifier, Float> strengths = new HashMap<>();
            if (root.has(STRENGTHS_KEY)) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(STRENGTHS_KEY).entrySet()) {
                    strengths.put(Identifier.parse(entry.getKey()), entry.getValue().getAsFloat());
                }
            }
            return new Table(Map.copyOf(colors), Map.copyOf(strengths));
        } catch (IOException | RuntimeException exception) {
            Helion.LOGGER.warn("Helion could not read the light colors: {}", exception.getMessage());
            return new Table(Map.of(), Map.of());
        }
    }

    private static float[] normalized(float[] color) {
        float luminance = color[RED] * RED_WEIGHT + color[GREEN] * GREEN_WEIGHT + color[BLUE] * BLUE_WEIGHT;
        if (luminance <= 0.0F) {
            return normalized(WARM_WHITE);
        }
        float[] balanced = new float[WARM_WHITE.length];
        for (int channel = RED; channel <= BLUE; channel++) {
            balanced[channel] = Math.min(color[channel] / luminance * (1.0F - WHITE_SHARE) + WHITE_SHARE, CHANNEL_LIMIT);
        }
        return balanced;
    }

    private static float srgbToLinear(float channel) {
        return (float) Math.pow(channel, DISPLAY_GAMMA);
    }

    private record Table(Map<Identifier, float[]> colors, Map<Identifier, Float> strengths) {
    }
}
