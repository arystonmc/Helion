package com.aryston.helion.integration;

import java.util.List;
import net.neoforged.fml.ModList;

final class CompatibilityGuard {
    private static final List<String> RENDERER_MODS = List.of("sodium", "iris");

    private CompatibilityGuard() {
    }

    static List<String> loadedRendererMods() {
        return RENDERER_MODS.stream()
            .filter(ModList.get()::isLoaded)
            .toList();
    }
}
