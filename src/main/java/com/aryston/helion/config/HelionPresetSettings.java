package com.aryston.helion.config;

import com.aryston.arkea.api.config.Binding;
import com.aryston.arkea.api.config.ConfigOption;
import com.aryston.arkea.api.config.ConfigPreset;
import com.aryston.helion.render.graph.RenderSettings;
import java.util.ArrayList;
import java.util.List;

public final class HelionPresetSettings {
    private HelionPresetSettings() {
    }

    public static List<PresetSettings> capture() {
        HelionOptions options = new HelionOptions();
        List<SavedValue<?>> saved = options.all().stream().<SavedValue<?>>map(SavedValue::of).toList();
        List<PresetSettings> presets = new ArrayList<>();
        try {
            for (ConfigPreset preset : HelionPresets.create(options)) {
                saved.forEach(SavedValue::restore);
                preset.values().forEach(HelionPresetSettings::apply);
                presets.add(new PresetSettings(preset.name().getString(), HelionConfig.renderSettings()));
            }
        } finally {
            saved.forEach(SavedValue::restore);
        }
        return presets;
    }

    private static <T> void apply(ConfigPreset.Value<T> value) {
        value.option().binding().set(value.value());
    }

    public record PresetSettings(String name, RenderSettings settings) {
    }

    private record SavedValue<T>(Binding<T> binding, T value) {
        static <T> SavedValue<T> of(ConfigOption<T> option) {
            return new SavedValue<>(option.binding(), option.binding().get());
        }

        void restore() {
            binding.set(value);
        }
    }
}
