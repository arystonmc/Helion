package com.aryston.helion.config;

import com.aryston.arkea.api.config.OptionKind;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

final class HelionShareCode {
    static final String PREFIX = "HELION1:";
    static final int VERSION = 1;
    private static final String VERSION_FIELD = "v";
    private static final String OPTIONS_FIELD = "o";

    private HelionShareCode() {
    }

    static String encode(JsonObject options) {
        JsonObject root = new JsonObject();
        root.addProperty(VERSION_FIELD, VERSION);
        root.add(OPTIONS_FIELD, options);
        byte[] json = root.toString().getBytes(StandardCharsets.UTF_8);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    static Optional<JsonObject> decode(String code) {
        String trimmed = code.strip();
        if (!trimmed.startsWith(PREFIX)) {
            return Optional.empty();
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length()));
            JsonElement root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = root.getAsJsonObject();
            if (!isVersion(object.get(VERSION_FIELD)) || !(object.get(OPTIONS_FIELD) instanceof JsonObject options)) {
                return Optional.empty();
            }
            return Optional.of(options);
        } catch (IllegalArgumentException | JsonParseException exception) {
            return Optional.empty();
        }
    }

    static JsonPrimitive write(Object value) {
        return switch (value) {
            case Boolean flag -> new JsonPrimitive(flag);
            case Number number -> new JsonPrimitive(number);
            case Enum<?> constant -> new JsonPrimitive(constant.name());
            default -> new JsonPrimitive(String.valueOf(value));
        };
    }

    @SuppressWarnings("unchecked")
    static <T> Optional<T> read(OptionKind<T> kind, JsonElement element) {
        if (!(element instanceof JsonPrimitive primitive)) {
            return Optional.empty();
        }
        Object value = switch (kind) {
            case OptionKind.Toggle _ -> primitive.isBoolean() ? primitive.getAsBoolean() : null;
            case OptionKind.DoubleRange range -> primitive.isNumber() ? clamp(primitive.getAsDouble(), range.min(), range.max()) : null;
            case OptionKind.IntRange range -> primitive.isNumber() ? (int) clamp(Math.round(primitive.getAsDouble()), range.min(), range.max()) : null;
            case OptionKind.Choice<?> choice -> primitive.isString() ? choice(choice, primitive.getAsString()) : null;
            default -> null;
        };
        return Optional.ofNullable((T) value);
    }

    private static boolean isVersion(JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isNumber() && primitive.getAsDouble() == VERSION;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.clamp(value, min, max);
    }

    private static Object choice(OptionKind.Choice<?> choice, String name) {
        for (Object value : choice.values()) {
            if (value instanceof Enum<?> constant && constant.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }
}
