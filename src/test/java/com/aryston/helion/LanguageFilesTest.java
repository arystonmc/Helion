package com.aryston.helion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LanguageFilesTest {
    private static final String LANGUAGE_FOLDER = "/assets/helion/lang/";
    private static final String ENGLISH = "en_us.json";
    private static final String TURKISH = "tr_tr.json";
    private static final String CONFIG_PREFIX = "helion.configuration.";
    private static final String SCREEN_PREFIX = "helion.configuration.section.";
    private static final String SCREEN_TITLE = "helion.configuration.title";
    private static final String TOOLTIP = ".tooltip";
    private static final String BUTTON = ".button";

    @Test
    void everyLanguageHasTheSameKeys() throws IOException {
        assertEquals(keys(ENGLISH), keys(TURKISH));
    }

    @Test
    void everyConfigEntryHasATooltip() throws IOException {
        Set<String> keys = keys(ENGLISH);
        for (String entry : configEntries(keys)) {
            assertTrue(keys.contains(entry + TOOLTIP), entry + TOOLTIP);
        }
    }

    @Test
    void everyConfigSectionHasAButton() throws IOException {
        Set<String> keys = keys(ENGLISH);
        Set<String> entries = configEntries(keys);
        for (String entry : entries) {
            boolean section = entries.stream().anyMatch(other -> other.startsWith(entry + "."));
            if (section) {
                assertTrue(keys.contains(entry + BUTTON), entry + BUTTON);
            }
        }
    }

    private static Set<String> configEntries(Set<String> keys) {
        return keys.stream()
            .filter(key -> key.startsWith(CONFIG_PREFIX))
            .filter(key -> !key.startsWith(SCREEN_PREFIX) && !key.equals(SCREEN_TITLE))
            .filter(key -> !key.endsWith(TOOLTIP) && !key.endsWith(BUTTON))
            .collect(Collectors.toSet());
    }

    private static Set<String> keys(String file) throws IOException {
        try (InputStream stream = LanguageFilesTest.class.getResourceAsStream(LANGUAGE_FOLDER + file)) {
            assertNotNull(stream, file);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject language = JsonParser.parseReader(reader).getAsJsonObject();
                return Set.copyOf(language.keySet());
            }
        }
    }
}
