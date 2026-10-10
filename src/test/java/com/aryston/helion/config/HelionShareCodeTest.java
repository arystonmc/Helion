package com.aryston.helion.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aryston.arkea.api.config.ChoiceStyle;
import com.aryston.arkea.api.config.OptionKind;
import com.aryston.helion.render.post.ToneMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class HelionShareCodeTest {
    private static final OptionKind.DoubleRange EXPOSURE = new OptionKind.DoubleRange(-2.0, 2.0, 0.1, value -> Component.empty());
    private static final OptionKind.Choice<ToneMapper> TONE_MAPPER = new OptionKind.Choice<>(Arrays.asList(ToneMapper.values()),
        value -> Component.empty(), ChoiceStyle.AUTO);

    @Test
    void codesRoundTrip() {
        JsonObject options = new JsonObject();
        options.addProperty("bloom", true);
        options.addProperty("exposure", 0.5);

        String code = HelionShareCode.encode(options);

        assertTrue(code.startsWith(HelionShareCode.PREFIX));
        assertFalse(code.contains("="));
        assertEquals(Optional.of(options), HelionShareCode.decode("  " + code + "\n"));
    }

    @Test
    void codesUseTheDocumentedJson() {
        String code = HelionShareCode.encode(new JsonObject());
        byte[] json = Base64.getUrlDecoder().decode(code.substring(HelionShareCode.PREFIX.length()));

        assertEquals("{\"v\":1,\"o\":{}}", new String(json, StandardCharsets.UTF_8));
    }

    @Test
    void badCodesAreRejected() {
        assertTrue(HelionShareCode.decode("").isEmpty());
        assertTrue(HelionShareCode.decode("HELION2:e30").isEmpty());
        assertTrue(HelionShareCode.decode("HELION1:not base64!").isEmpty());
        assertTrue(HelionShareCode.decode(HelionShareCode.PREFIX + encode("{\"v\":2,\"o\":{}}")).isEmpty());
        assertTrue(HelionShareCode.decode(HelionShareCode.PREFIX + encode("{\"v\":1,\"o\":[]}")).isEmpty());
        assertTrue(HelionShareCode.decode(HelionShareCode.PREFIX + encode("[1]")).isEmpty());
    }

    @Test
    void numbersAreClampedToTheOptionRange() {
        assertEquals(Optional.of(2.0), HelionShareCode.read(EXPOSURE, new JsonPrimitive(9.0)));
        assertEquals(Optional.of(-2.0), HelionShareCode.read(EXPOSURE, new JsonPrimitive(-9.0)));
        assertEquals(Optional.of(0.5), HelionShareCode.read(EXPOSURE, new JsonPrimitive(0.5)));
        assertTrue(HelionShareCode.read(EXPOSURE, new JsonPrimitive("bright")).isEmpty());
    }

    @Test
    void enumsAreReadByName() {
        ToneMapper last = ToneMapper.values()[ToneMapper.values().length - 1];

        assertEquals(new JsonPrimitive(last.name()), HelionShareCode.write(last));
        assertEquals(Optional.of(last), HelionShareCode.read(TONE_MAPPER, HelionShareCode.write(last)));
        assertTrue(HelionShareCode.read(TONE_MAPPER, new JsonPrimitive("UNKNOWN")).isEmpty());
    }

    @Test
    void togglesNeedBooleans() {
        OptionKind.Toggle toggle = new OptionKind.Toggle();

        assertEquals(Optional.of(true), HelionShareCode.read(toggle, new JsonPrimitive(true)));
        assertTrue(HelionShareCode.read(toggle, new JsonPrimitive(1)).isEmpty());
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
