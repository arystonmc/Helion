package com.aryston.helion.integration;

import java.util.List;
import net.neoforged.fml.ModList;

final class CompatibilityGuard {
    private static final List<String> RENDERER_MODS = List.of("iris");
    private static final String SODIUM_MOD_ID = "sodium";

    private CompatibilityGuard() {
    }

    static boolean sodiumLoaded() {
        return ModList.get().isLoaded(SODIUM_MOD_ID);
    }

    static List<String> loadedRendererMods() {
        return RENDERER_MODS.stream()
            .filter(ModList.get()::isLoaded)
            .toList();
    }
}
