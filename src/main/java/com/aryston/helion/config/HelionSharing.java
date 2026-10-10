package com.aryston.helion.config;

import com.aryston.arkea.api.config.ConfigOption;
import com.aryston.arkea.ui.overlay.ArkToasts;
import com.aryston.arkea.ui.overlay.ToastTone;
import com.aryston.helion.Helion;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class HelionSharing {
    private static final String KEY = "helion.config.share.";

    private HelionSharing() {
    }

    static void copy(HelionOptions options) {
        JsonObject values = new JsonObject();
        for (ConfigOption<?> option : options.all()) {
            values.add(options.key(option), HelionShareCode.write(option.binding().get()));
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(HelionShareCode.encode(values));
        ArkToasts.show(ToastTone.SUCCESS, Component.translatable(KEY + "copied"));
    }

    static void paste(HelionOptions options, Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        Optional<JsonObject> values = HelionShareCode.decode(minecraft.keyboardHandler.getClipboard());
        int imported = values.map(found -> importValues(options, found)).orElse(0);
        if (imported == 0) {
            ArkToasts.show(ToastTone.ERROR, Component.translatable(KEY + "invalid"));
            return;
        }
        Helion.LOGGER.info("Imported {} Helion settings from a settings code", imported);
        minecraft.gui.setScreen(HelionConfigScreen.create(parent));
        ArkToasts.show(ToastTone.SUCCESS, Component.translatable(KEY + "imported", imported));
    }

    private static int importValues(HelionOptions options, JsonObject values) {
        int imported = 0;
        for (ConfigOption<?> option : options.all()) {
            JsonElement element = values.get(options.key(option));
            if (element != null && importValue(option, element)) {
                imported++;
            }
        }
        return imported;
    }

    private static <T> boolean importValue(ConfigOption<T> option, JsonElement element) {
        Optional<T> value = HelionShareCode.read(option.kind(), element);
        value.ifPresent(found -> {
            option.binding().set(found);
            option.binding().save();
        });
        return value.isPresent();
    }
}
