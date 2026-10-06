package com.aryston.helion.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DebugOverlayLines {
    private static final String PREFIX = "Helion ";
    private static final String SEPARATOR = ": ";
    private static final String PAIR = "=";
    private static final String JOINER = ", ";
    private static final String CONTINUATION = "    ";
    private static final String EMPTY_SECTION = "-";
    private static final int VALUES_PER_LINE = 4;

    private DebugOverlayLines() {
    }

    static List<String> format(JsonObject snapshot) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : snapshot.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                addSection(lines, entry.getKey(), entry.getValue().getAsJsonObject());
            } else {
                lines.add(PREFIX + entry.getKey() + SEPARATOR + text(entry.getValue()));
            }
        }
        return lines;
    }

    private static void addSection(List<String> lines, String name, JsonObject values) {
        List<String> pairs = values.entrySet().stream()
            .map(entry -> entry.getKey() + PAIR + text(entry.getValue()))
            .toList();
        if (pairs.isEmpty()) {
            lines.add(PREFIX + name + SEPARATOR + EMPTY_SECTION);
            return;
        }
        for (int start = 0; start < pairs.size(); start += VALUES_PER_LINE) {
            String chunk = String.join(JOINER, pairs.subList(start, Math.min(start + VALUES_PER_LINE, pairs.size())));
            lines.add(start == 0 ? PREFIX + name + SEPARATOR + chunk : CONTINUATION + chunk);
        }
    }

    private static String text(JsonElement value) {
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }
}
