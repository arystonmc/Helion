package com.aryston.helion.integration;

import static com.aryston.helion.Helion.LOGGER;

import com.aryston.helion.Helion;
import com.aryston.helion.render.rtx.RayTracing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import net.neoforged.fml.loading.FMLPaths;

public final class RayTracingStartup {
    private static final String CONFIG_FILE = Helion.MOD_ID + "-client.toml";
    private static final String SECTION = "[rayTracing]";
    private static final String SECTION_START = "[";
    private static final String COMMENT_START = "#";
    private static final Pattern ENABLED = Pattern.compile("enabled\\s*=\\s*true");
    private static boolean read;

    private RayTracingStartup() {
    }

    public static synchronized void ensureRequested() {
        if (read) {
            return;
        }
        read = true;
        RayTracing.requestAtStartup(configured(FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE)));
    }

    static boolean configured(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            return configured(Files.readAllLines(file));
        } catch (IOException exception) {
            LOGGER.warn("Helion could not read {} for the ray tracing setting: {}", file, exception.getMessage());
            return false;
        }
    }

    static boolean configured(List<String> lines) {
        boolean inSection = false;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith(COMMENT_START)) {
                continue;
            }
            if (line.startsWith(SECTION_START)) {
                inSection = line.equals(SECTION);
            } else if (inSection && ENABLED.matcher(line).matches()) {
                return true;
            }
        }
        return false;
    }
}
