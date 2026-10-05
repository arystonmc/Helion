package com.aryston.helion.integration;

import com.aryston.helion.Helion;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public final class HelionKeys {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Helion.MOD_ID, Helion.MOD_ID));

    public static final KeyMapping TOGGLE = new KeyMapping(
        "key.helion.toggle",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_H,
        CATEGORY
    );

    private HelionKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(TOGGLE);
    }
}
