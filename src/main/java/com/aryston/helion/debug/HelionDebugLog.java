package com.aryston.helion.debug;

import com.aryston.helion.render.HelionRenderCore;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class HelionDebugLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_FOLDER = "logs";
    private static final String FILE_NAME = "helion-debug.jsonl";
    private static final String SNAPSHOT = "snapshot";
    private static final int SNAPSHOT_INTERVAL_TICKS = 20;

    private @Nullable BufferedWriter writer;
    private boolean failed;
    private int ticksUntilSnapshot;

    public void tick(Minecraft minecraft) {
        if (!HelionRenderCore.get().isDebugMode() || minecraft.level == null) {
            return;
        }
        ticksUntilSnapshot--;
        if (ticksUntilSnapshot > 0) {
            return;
        }
        ticksUntilSnapshot = SNAPSHOT_INTERVAL_TICKS;
        write(SNAPSHOT, HelionDebugSnapshot.capture(minecraft));
    }

    public void event(String type, JsonObject data) {
        if (HelionRenderCore.get().isDebugMode()) {
            write(type, data);
        }
    }

    public synchronized void close() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException exception) {
            LOGGER.warn("Helion could not close the debug log", exception);
        }
        writer = null;
    }

    private synchronized void write(String type, JsonObject data) {
        BufferedWriter output = open();
        if (output == null) {
            return;
        }
        JsonObject line = new JsonObject();
        line.addProperty("time", Instant.now().toString());
        line.addProperty("type", type);
        line.add("data", data);
        try {
            output.write(line.toString());
            output.newLine();
            output.flush();
        } catch (IOException exception) {
            fail(exception);
        }
    }

    private @Nullable BufferedWriter open() {
        if (writer != null || failed) {
            return writer;
        }
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(LOG_FOLDER).resolve(FILE_NAME);
        try {
            Files.createDirectories(file.getParent());
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            LOGGER.info("Helion debug mode writes JSON lines to {}", file);
        } catch (IOException exception) {
            fail(exception);
        }
        return writer;
    }

    private void fail(IOException exception) {
        failed = true;
        LOGGER.warn("Helion debug log is turned off because it could not be written", exception);
        close();
    }
}
