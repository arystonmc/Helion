package com.aryston.helion.integration;

import com.aryston.helion.debug.HelionDebugLog;
import com.aryston.helion.debug.ParityCheck;
import com.aryston.helion.debug.ParityResult;
import com.aryston.helion.render.HelionRenderCore;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class ParityControl {
    private static final String EVENT = "parity";

    private ParityControl() {
    }

    static void start(Minecraft minecraft, HelionDebugLog log) {
        if (!HelionRenderCore.get().isActive()) {
            chat(minecraft, Component.translatable("helion.parity.inactive"));
            return;
        }
        if (!ParityCheck.get().start(result -> report(minecraft, log, result), () -> reportAborted(minecraft, log))) {
            chat(minecraft, Component.translatable("helion.parity.busy"));
            return;
        }
        chat(minecraft, Component.translatable("helion.parity.started"));
    }

    private static void report(Minecraft minecraft, HelionDebugLog log, ParityResult result) {
        chat(minecraft, result.isIdentical()
            ? Component.translatable("helion.parity.identical", result.pixels())
            : Component.translatable(
                "helion.parity.different",
                result.differentColorPixels(),
                result.pixels(),
                result.maxColorDelta(),
                result.differentDepthPixels()
            ));
        JsonObject data = new JsonObject();
        data.addProperty("identical", result.isIdentical());
        data.addProperty("pixels", result.pixels());
        data.addProperty("differentColorPixels", result.differentColorPixels());
        data.addProperty("maxColorDelta", result.maxColorDelta());
        data.addProperty("differentDepthPixels", result.differentDepthPixels());
        log.event(EVENT, data);
    }

    private static void reportAborted(Minecraft minecraft, HelionDebugLog log) {
        chat(minecraft, Component.translatable("helion.parity.aborted"));
        JsonObject data = new JsonObject();
        data.addProperty("aborted", true);
        log.event(EVENT, data);
    }

    private static void chat(Minecraft minecraft, Component message) {
        minecraft.gui.hud.getChat().addClientSystemMessage(message);
    }
}
